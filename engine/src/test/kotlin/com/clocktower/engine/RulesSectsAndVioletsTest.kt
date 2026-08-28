package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-SV — the Sects & Violets registry.
 *
 * Every case is Given/When/Then and is driven through the real entry points:
 * `NightPlan.build` / `NightPlan.resolve`, `Deaths.attempt`, `Execution.execute`,
 * `DayRules.checkNomination` / `applyTrigger` and `Phases.advancePhase`. Nothing
 * here reaches into a registry lambda directly except the two table tests.
 */
class RulesSectsAndVioletsTest {

    private val data = GameData.loadDefault()
    private val sv = data.builtInScripts().first { it.id == "sv" }
    private val lookup: (String) -> Character? = data::character

    /** The 25 non-Traveller Sects & Violets characters this package owns. */
    private val scope = listOf(
        "clockmaker", "dreamer", "snakecharmer", "mathematician", "flowergirl", "towncrier",
        "oracle", "savant", "seamstress", "philosopher", "artist", "juggler", "sage",
        "mutant", "sweetheart", "barber", "klutz",
        "eviltwin", "witch", "cerenovus", "pithag",
        "fanggu", "vigormortis", "nodashii", "vortox",
    )

    // ---- fixtures ---------------------------------------------------------

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(sv, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    /** Straight to night [n] without walking the days. */
    private fun atNight(state: GameState, n: Int): GameState = state.copy(cycle = n)

    private fun day(state: GameState): GameState = Phases.advancePhase(state, lookup)

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String, holderId: Long? = null): NightStep? =
        plan(state).steps.firstOrNull {
            it.abilityId == abilityId && (holderId == null || it.holderId == holderId)
        }

    private fun fire(state: GameState, abilityId: String, holderId: Long? = null): NightStep {
        val s = assertNotNull(step(state, abilityId, holderId), "$abilityId has no step tonight")
        assertTrue(s.gate is StepGate.Fire, "$abilityId is gated: ${s.gate}")
        return s
    }

    private fun resolve(state: GameState, s: NightStep, input: NightInput): GameState =
        NightPlan.resolve(state, lookup, s.key, input)

    private fun picks(vararg ids: Long) = NightInput(playerIds = ids.toList())

    /**
     * A storyteller-placed poison. The generic token has UNLIMITED copies, so two
     * of them coexist — a `poisoner/Poisoned` has exactly one physical copy and the
     * second placement would displace the first.
     */
    private fun poison(state: GameState, seat: Long): GameState = Effects.place(
        state = state,
        target = seat,
        kind = EffectKind.POISONED,
        sourceCharacterId = Tokens.STORYTELLER_SOURCE,
        sourcePlayerId = null,
        until = Until.FOREVER,
        label = "Poisoned",
    ).state

    private fun alive(state: GameState, seat: Long): Boolean =
        assertNotNull(state.player(seat)).alive

    private fun promptsFor(state: GameState, sourceId: String, at: BriefingSlot): List<Prompt> =
        Prompts.due(state, at).filter { Character.normalizeId(it.sourceId) == sourceId }

    private fun rule(id: String): CharacterRule =
        assertNotNull(CharacterRules.all[id], "$id has no registry row")

    // ==================================================================
    // Coverage and the token table
    // ==================================================================

    @Test
    fun `every Sects and Violets character has a registry row`() {
        val missing = scope.filter { CharacterRules.all[it] == null }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
        // And the data agrees on what the scope is.
        val fromData = data.characters
            .filter { it.edition == "sv" && it.team != Team.TRAVELLER }
            .map { Character.normalizeId(it.id) }
            .sorted()
        assertEquals(scope.sorted(), fromData, "the registry and characters.json disagree")
    }

    @Test
    fun `every declared token is an official reminder with the official copy count`() {
        val problems = mutableListOf<String>()
        for (id in scope) {
            for (token in rule(id).tokens) {
                val owner = assertNotNull(lookup(token.sourceId), "${token.sourceId} is not a character")
                val copies = owner.allReminders.count { it.trim().equals(token.label.trim(), true) }
                if (copies == 0) {
                    problems += "${token.sourceId}/${token.label} is not in ${owner.allReminders}"
                } else if (copies != token.copies) {
                    problems += "${token.sourceId}/${token.label}: rule says ${token.copies}, " +
                        "data has $copies"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        // The registry's rows are the ones actually in force.
        assertEquals(5, assertNotNull(Tokens.rule("juggler", "Correct")).copies)
        assertEquals(3, assertNotNull(Tokens.rule("vigormortis", "Has Ability")).copies)
        assertEquals(2, assertNotNull(Tokens.rule("nodashii", "Poisoned")).copies)
    }

    @Test
    fun `the death-triggered Outsiders keep their ability in the grave`() {
        for (id in listOf("sweetheart", "barber", "klutz")) {
            assertTrue(rule(id).keepsAbilityWhenDead, "$id must keep its ability when dead")
        }
    }

    // ==================================================================
    // Townsfolk
    // ==================================================================

    @Test
    fun `the Clockmaker wakes on the first night and never again`() {
        // Given a Clockmaker in play,
        val night1 = game("vortox", "witch", "clockmaker", "chef", "oracle", "artist")
        // When the sheet is built on night 1 and on night 2,
        // Then the step exists once and only on the first night.
        assertNotNull(step(night1, "clockmaker"), "night 1 wakes the Clockmaker")
        assertNull(step(atNight(night1, 2), "clockmaker"), "the Clockmaker never wakes again")
    }

    @Test
    fun `the Dreamer may not point at themselves or at a Traveller`() {
        // Given a Dreamer at seat 2 and a Traveller at seat 5,
        var state = game("vortox", "witch", "dreamer", "chef", "oracle", "artist")
        state = GameActions.assignCharacter(state, 5L, "scapegoat", isTraveller = true)
        val dreamer = fire(state, "dreamer", 2L)

        // When the storyteller taps the Dreamer's own seat and the Traveller,
        val self = resolve(state, dreamer, picks(2L))
        val traveller = resolve(state, dreamer, picks(5L))

        // Then neither is recorded as a choice, and a legal seat is.
        assertTrue(choices(self, "dreamer").single().targetIds.isEmpty(), "self is refused")
        assertTrue(choices(traveller, "dreamer").single().targetIds.isEmpty(), "Travellers are refused")
        val legal = resolve(state, dreamer, picks(3L))
        assertEquals(listOf(3L), choices(legal, "dreamer").single().targetIds)
    }

    @Test
    fun `a working Snake Charmer raises the swap decision and a poisoned one does not`() {
        // Given a sober Snake Charmer and a living Demon,
        val sober = game("vortox", "witch", "snakecharmer", "chef", "oracle", "artist")
        // When they point at somebody,
        val after = resolve(sober, fire(sober, "snakecharmer", 2L), picks(0L))
        // Then the choice is recorded and the swap is raised as a decision,
        assertEquals(listOf(0L), choices(after, "snakecharmer").single().targetIds)
        val decision = promptsFor(after, "snakecharmer", BriefingSlot.NOW).single()
        assertEquals(PromptKind.DECIDE, decision.kind)
        assertEquals(0L, decision.subjectPlayerId, "the decision names the seat they pointed at")

        // Given the same charmer, poisoned,
        val poisoned = poison(sober, 2L)
        // When they point at the Demon,
        val nothing = resolve(poisoned, fire(poisoned, "snakecharmer", 2L), picks(0L))
        // Then the choice is still recorded but nothing is raised.
        assertEquals(listOf(0L), choices(nothing, "snakecharmer").single().targetIds)
        assertTrue(promptsFor(nothing, "snakecharmer", BriefingSlot.NOW).isEmpty())
    }

    @Test
    fun `the Mathematician counts malfunctioning players and never counts themselves`() {
        // Given a poisoned Oracle and a poisoned Mathematician on night 2,
        var state = atNight(game("vortox", "witch", "mathematician", "oracle", "chef", "artist"), 2)
        state = poison(state, 3L)
        state = poison(state, 2L)

        // When both steps are run,
        state = resolve(state, fire(state, "oracle", 3L), NightInput())
        state = resolve(state, fire(state, "mathematician", 2L), NightInput())

        // Then both malfunctions are recorded, but the Mathematician's own is excluded.
        assertEquals(2, NightPlan.malfunctionCount(state, night = 2))
        assertEquals(1, NightPlan.malfunctionCount(state, night = 2, excluding = 2L))
    }

    @Test
    fun `the Flowergirl's two markers can never coexist and reset at dawn`() {
        // Given a Flowergirl carrying "Demon Not Voted",
        var state = game("vortox", "witch", "flowergirl", "chef", "oracle", "artist")
        state = place(state, 2L, "flowergirl", "Demon Not Voted")
        // When a Demon votes and "Demon Voted" is placed,
        state = place(state, 2L, "flowergirl", "Demon Voted")
        // Then exactly one Flowergirl marker is on the seat (lead D52),
        assertEquals(listOf("Demon Voted"), labels(state, 2L, "flowergirl"))
        // and at dawn it resets to the resting state rather than vanishing.
        state = day(state)
        assertEquals(listOf("Demon Not Voted"), labels(state, 2L, "flowergirl"))
    }

    @Test
    fun `the Town Crier is warned by a Minion nomination and not by an exile`() {
        // Given a living Town Crier and a Witch,
        var state = day(game("vortox", "witch", "towncrier", "chef", "oracle", "artist"))
        state = GameActions.assignCharacter(state, 5L, "scapegoat", isTraveller = true)

        // When the Witch nominates a resident,
        val nomination = DayRules.checkNomination(state, lookup, nominatorId = 1L, nomineeId = 3L)
        // Then the Town Crier row says tonight's answer is YES.
        val warn = assertNotNull(nomination.triggers.firstOrNull { it.sourceId == "towncrier" })
        assertEquals(TriggerKind.WARN, warn.kind)
        assertTrue("YES" in warn.headline, warn.headline)

        // When the same Minion calls an exile instead, there is no row: exiles are
        // never affected by character abilities.
        val exile = DayRules.checkNomination(state, lookup, nominatorId = 1L, nomineeId = 5L)
        assertNull(exile.triggers.firstOrNull { it.sourceId == "towncrier" })
    }

    @Test
    fun `the Oracle never wakes on the first night`() {
        val state = game("vortox", "witch", "oracle", "chef", "artist", "juggler")
        assertNull(step(state, "oracle"), "the Oracle has no first-night step")
        assertNotNull(step(atNight(state, 2), "oracle"), "and wakes from night 2")
    }

    @Test
    fun `the Seamstress spends the ability by choosing and keeps it by passing`() {
        // Given an unspent Seamstress,
        val state = game("vortox", "witch", "seamstress", "chef", "oracle", "artist")
        val seamstress = fire(state, "seamstress", 2L)

        // When they pass,
        val passed = resolve(state, seamstress, NightInput(none = true))
        // Then the ability is not spent and they wake again tomorrow.
        assertFalse(Memory.isSpent(passed, "seamstress", 2L), "passing keeps the ability")
        assertTrue(step(atNight(passed, 2), "seamstress")!!.gate is StepGate.Fire)

        // When they choose two players instead,
        val used = resolve(state, seamstress, picks(3L, 4L))
        // Then exactly one SPENT mark exists and the row is gone tomorrow.
        assertTrue(Memory.isSpent(used, "seamstress", 2L))
        assertEquals(
            1,
            used.effects.count { it.kind == EffectKind.SPENT && it.sourceCharacterId == "seamstress" },
        )
        val tomorrow = assertNotNull(step(atNight(used, 2), "seamstress"))
        assertTrue(tomorrow.gate is StepGate.Skip, "a spent Seamstress does not wake: ${tomorrow.gate}")
    }

    @Test
    fun `the Philosopher wakes until they gain an ability and never after`() {
        // Given a Philosopher who shakes their head on night 1,
        val state = game("vortox", "witch", "philosopher", "chef", "oracle", "artist")
        val passed = resolve(state, fire(state, "philosopher", 2L), NightInput(none = true))
        // Then they wake again on night 2 — a head-shake spends nothing.
        assertTrue(step(atNight(passed, 2), "philosopher")!!.gate is StepGate.Fire)

        // When they point at a good character instead,
        val gained = resolve(
            state,
            fire(state, "philosopher", 2L),
            NightInput(characterIds = listOf("chef")),
        )
        // Then the gain is raised as an obligation and the row never returns.
        assertEquals(listOf("chef"), choices(gained, "philosopher").single().characterIds)
        assertTrue(promptsFor(gained, "philosopher", BriefingSlot.NOW).isNotEmpty())
        val tomorrow = assertNotNull(step(atNight(gained, 2), "philosopher"))
        assertTrue(tomorrow.gate is StepGate.Skip, "once per game: ${tomorrow.gate}")
    }

    @Test
    fun `the Artist day card closes the moment the ability is spent`() {
        val state = day(game("vortox", "witch", "artist", "chef", "oracle", "juggler"))
        val ability = assertNotNull(rule("artist").day?.ability)
        val artist = assertNotNull(state.player(2))
        // Given a living, unspent Artist, the card is offered,
        assertTrue(ability.available(state, lookup, artist))
        // and once the "No Ability" mark is on the seat it is not.
        val spent = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.SPENT,
            sourceCharacterId = "artist",
            sourcePlayerId = 2L,
            until = Until.FOREVER,
            label = "No Ability",
        ).state
        assertFalse(ability.available(spent, lookup, assertNotNull(spent.player(2))))
        // A dead Artist never gets it.
        val dead = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state
        assertFalse(ability.available(dead, lookup, assertNotNull(dead.player(2))))
    }

    @Test
    fun `the Savant may visit once a day`() {
        val state = day(game("vortox", "witch", "savant", "chef", "oracle", "juggler"))
        val ability = assertNotNull(rule("savant").day?.ability)
        assertTrue(ability.available(state, lookup, assertNotNull(state.player(2))))
        // Given a visit recorded today, the card is closed for the rest of the day.
        val visited = Ledger.private(state, 2L, "savant", text = "two things", shown = "")
        assertFalse(ability.available(visited, lookup, assertNotNull(visited.player(2))))
        // The Savant is on neither night order list.
        assertNull(step(state, "savant"))
    }

    @Test
    fun `the Juggler only wakes on the night after a genuine guess`() {
        // Given a Juggler who has never juggled,
        val state = atNight(game("vortox", "witch", "juggler", "chef", "oracle", "artist"), 2)
        val quiet = assertNotNull(step(state, "juggler", 2L))
        assertTrue(quiet.gate is StepGate.Skip, "no guesses, no step: ${quiet.gate}")

        // When guesses are recorded on day 1,
        val guessed = Ledger.statement(
            state.copy(cycle = 1, phase = Phase.DAY),
            speakerId = 2L,
            sourceId = "juggler",
            text = "3 guesses",
            targetIds = listOf(0L, 1L, 3L),
            characterIds = listOf("vortox", "witch", "chef"),
        ).copy(cycle = 2, phase = Phase.NIGHT)

        // Then night 2 wakes them, and night 3 does not.
        assertTrue(fire(guessed, "juggler", 2L).gate is StepGate.Fire)
        val later = assertNotNull(step(atNight(guessed, 3), "juggler", 2L))
        assertTrue(later.gate is StepGate.Skip, "the reveal happens once: ${later.gate}")
    }

    @Test
    fun `the Sage wakes when the Demon kills them and not when a Pit-Hag does`() {
        val base = atNight(game("vortox", "pithag", "sage", "chef", "oracle", "artist"), 3)
        // Given an alive Sage, there is no step at all.
        val quiet = assertNotNull(step(base, "sage", 2L))
        assertTrue(quiet.gate is StepGate.Skip, quiet.gate.toString())

        // When the Demon kills them tonight,
        val byDemon = Deaths.attempt(
            base, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "vortox", 0L),
        ).state
        // Then the Sage wakes, dead, and the row says so.
        val sage = fire(byDemon, "sage", 2L)
        assertFalse(alive(byDemon, 2L))
        assertTrue(sage.badges.any { "dead" in it }, sage.badges.toString())

        // When the Pit-Hag kills them instead (wiki example 3),
        val byPitHag = Deaths.attempt(
            base, lookup, 2L, KillCause(DeathCause.EVIL_ABILITY, "pithag", 1L),
        ).state
        // Then the Sage does not wake.
        val skipped = assertNotNull(step(byPitHag, "sage", 2L))
        assertTrue(skipped.gate is StepGate.Skip, skipped.gate.toString())
        assertTrue("Demon" in (skipped.gate as StepGate.Skip).reason, (skipped.gate as StepGate.Skip).reason)
    }

    @Test
    fun `the Sage's pair is spent, so a second night is silent`() {
        var state = atNight(game("vortox", "pithag", "sage", "chef", "oracle", "artist"), 3)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "vortox", 0L)).state
        state = resolve(state, fire(state, "sage", 2L), picks(0L, 1L))
        assertEquals(listOf(0L, 1L), choices(state, "sage").last().targetIds)
        assertTrue(Memory.isSpent(state, "sage", 2L), "the Sage's wake is once per game")
    }

    // ==================================================================
    // Outsiders
    // ==================================================================

    @Test
    fun `a storyteller execution of the Mutant is the day's execution`() {
        // Given a living Mutant on day 1,
        val state = day(game("vortox", "witch", "mutant", "chef", "oracle", "artist"))
        assertFalse(DayRules.executionSpent(state))
        // When the storyteller executes them for madness,
        val after = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        // Then the day's execution is spent and the Mutant is dead.
        assertTrue(DayRules.executionSpent(after))
        assertEquals(ExecutionOutcome.DIED, after.executions.last().outcome)
        assertEquals(ExecutionVia.STORYTELLER, after.executions.last().via)
        assertFalse(alive(after, 2L))
        assertTrue(DayRules.nominationsClosed(after, lookup))
    }

    @Test
    fun `an executed Sweetheart owes a drunk that outlives them`() {
        // Given a Sweetheart executed on day 1,
        var state = day(game("vortox", "witch", "sweetheart", "chef", "oracle", "artist"))
        state = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        // Then the obligation is raised during the day, not at night.
        assertEquals(1, promptsFor(state, "sweetheart", BriefingSlot.NOW).size)

        // When the night comes and the storyteller picks the Chef,
        state = day(state)
        val sweetheart = fire(state, "sweetheart", 2L)
        assertEquals(WakeCount.NONE, sweetheart.wakeCounts, "nobody is woken for this")
        state = resolve(state, sweetheart, picks(3L))

        // Then the Chef is drunk, and stays drunk through a full day and night.
        assertTrue(Status.isImpaired(state, lookup, 3L), "the Chef is drunk from now on")
        state = day(day(state))
        assertTrue(Status.isImpaired(state, lookup, 3L), "the drunk outlives the Sweetheart")
        // And the obligation does not come back.
        val again = assertNotNull(step(atNight(state, 4), "sweetheart", 2L))
        assertTrue(again.gate is StepGate.Skip, again.gate.toString())
    }

    @Test
    fun `a Sweetheart who died drunk or poisoned makes nobody drunk`() {
        // Given a poisoned Sweetheart,
        var state = day(game("vortox", "witch", "sweetheart", "chef", "oracle", "artist"))
        state = poison(state, 2L)
        // When they are executed,
        state = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        // Then nothing is owed, at once or at night.
        assertTrue(promptsFor(state, "sweetheart", BriefingSlot.NOW).isEmpty())
        val night = assertNotNull(step(day(state), "sweetheart", 2L))
        assertTrue(night.gate is StepGate.Skip, night.gate.toString())
    }

    @Test
    fun `an executed Barber arms the haircut and the Demon swaps two characters`() {
        // Given a Barber executed on day 1,
        var state = day(game("vortox", "witch", "barber", "chef", "oracle", "artist"))
        state = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        // Then the official token is on the corpse.
        assertTrue(DayRules.hasToken(state, 2L, "barber", "Haircuts Tonight"))

        // When night 2 comes and the Demon swaps the Chef and the Oracle,
        state = day(state)
        val barber = fire(state, "barber", 2L)
        state = resolve(state, barber, picks(3L, 4L))

        // Then their characters have exchanged, their alignments have not, and the
        // token is gone.
        assertEquals("oracle", assertNotNull(state.player(3)).characterId)
        assertEquals("chef", assertNotNull(state.player(4)).characterId)
        assertFalse(assertNotNull(state.player(3)).isEvil(lookup))
        assertFalse(assertNotNull(state.player(4)).isEvil(lookup))
        assertFalse(DayRules.hasToken(state, 2L, "barber", "Haircuts Tonight"))
    }

    @Test
    fun `a Barber who died impaired arms nothing`() {
        var state = day(game("vortox", "witch", "barber", "chef", "oracle", "artist"))
        state = poison(state, 2L)
        state = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        assertFalse(DayRules.hasToken(state, 2L, "barber", "Haircuts Tonight"))
        val night = assertNotNull(step(day(state), "barber", 2L))
        assertTrue(night.gate is StepGate.Skip, night.gate.toString())
    }

    @Test
    fun `a night-killed Klutz owes a public choice at day start`() {
        // Given a Klutz killed at night,
        var state = atNight(game("vortox", "witch", "klutz", "chef", "oracle", "artist"), 2)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "vortox", 0L)).state
        // Then the obligation waits for the day, when they learn they died,
        assertTrue(promptsFor(state, "klutz", BriefingSlot.NOW).isEmpty())
        val owed = promptsFor(state, "klutz", BriefingSlot.DAY_START).single()
        assertEquals(PromptKind.CHOOSE_PLAYER, owed.kind)
        // and it survives the phase change until it is answered.
        assertEquals(1, promptsFor(day(state), "klutz", BriefingSlot.DAY_START).size)

        // A corpse that only becomes the Klutz later never owes anything.
        var other = atNight(game("vortox", "witch", "chef", "oracle", "artist", "juggler"), 2)
        other = Deaths.attempt(other, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "vortox", 0L)).state
        other = GameActions.assignCharacter(other, 2L, "klutz")
        assertTrue(promptsFor(other, "klutz", BriefingSlot.DAY_START).isEmpty())
    }

    @Test
    fun `an executed Klutz is asked during that same day`() {
        var state = day(game("vortox", "witch", "klutz", "chef", "oracle", "artist"))
        state = Execution.execute(state, lookup, playerId = 2L, via = ExecutionVia.STORYTELLER)
        assertEquals(1, promptsFor(state, "klutz", BriefingSlot.NOW).size)
    }

    // ==================================================================
    // Minions
    // ==================================================================

    @Test
    fun `the Evil Twin wakes on night 1 with its twin and warns on their nomination`() {
        // Given an Evil Twin whose good twin is the Chef,
        var state = game("vortox", "eviltwin", "chef", "oracle", "artist", "juggler")
        // Before the twin is chosen the row asks for one rather than firing.
        assertTrue(step(state, "eviltwin", 1L)!!.gate is StepGate.Conditional)
        state = place(state, 2L, "eviltwin", "Twin")

        // Then the first night wakes them with two pre-filled cards and no picker.
        val twins = fire(state, "eviltwin", 1L)
        assertNull(twins.action, "the pair is already known — no search grid")
        assertEquals(2, twins.cards.size)
        assertTrue(twins.cards.any { (it.card as? ShowCardSpec.CharacterCard)?.characterId == "chef" })

        // And nominating the good twin is flagged before the vote.
        val day = day(state)
        val warn = assertNotNull(
            DayRules.checkNomination(day, lookup, 3L, 2L).triggers.firstOrNull {
                it.sourceId == "eviltwin"
            },
        )
        assertEquals(TriggerKind.WARN, warn.kind)
        assertTrue("evil" in warn.headline, warn.headline)
    }

    @Test
    fun `a cursed nominator dies, but an exile call and a poisoned Witch kill nobody`() {
        // Given a Witch who cursed the Oracle, and a Traveller in the game,
        var state = day(game("vortox", "witch", "chef", "oracle", "artist", "juggler"))
        state = GameActions.assignCharacter(state, 5L, "scapegoat", isTraveller = true)
        state = place(state, 3L, "witch", "Cursed")

        // When the cursed Oracle calls an exile, nothing happens.
        val exile = DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 5L)
        val exileRow = assertNotNull(exile.triggers.firstOrNull { it.sourceId == "witch" })
        assertEquals(TriggerKind.WARN, exileRow.kind, "exiles never trigger abilities")
        assertNull(exileRow.targetId, "a warning must never be applied as a kill")

        // When the cursed Oracle nominates a resident, they die — and it is not an
        // execution, so the day's execution is still available.
        val real = DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
        val trigger = assertNotNull(real.triggers.firstOrNull { it.sourceId == "witch" })
        assertEquals(TriggerKind.AUTO_DEATH, trigger.kind)
        val after = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)
        assertFalse(alive(after, 3L))
        assertFalse(DayRules.executionSpent(after), "a Witch death is not an execution")
        assertEquals(DeathCause.EVIL_ABILITY, after.deaths.last().cause)

        // When the Witch is poisoned instead, the curse is inert.
        val sick = poison(state, 1L)
        val sickRow = assertNotNull(
            DayRules.checkNomination(sick, lookup, 3L, 2L).triggers.firstOrNull {
                it.sourceId == "witch"
            },
        )
        assertEquals(TriggerKind.WARN, sickRow.kind)
        assertTrue(sickRow.impaired)
    }

    @Test
    fun `the Witch loses the ability when only three players live`() {
        // Given a six-seat game cut down to three living residents,
        var state = atNight(game("vortox", "witch", "chef", "oracle", "artist", "juggler"), 2)
        assertTrue(fire(state, "witch", 1L).gate is StepGate.Fire)
        for (seat in listOf(3L, 4L, 5L)) {
            state = Deaths.attempt(state, lookup, seat, KillCause(DeathCause.STORYTELLER)).state
        }
        assertEquals(3, state.aliveCountResidents)
        // Then the Witch has no night step at all,
        val gate = assertNotNull(step(state, "witch", 1L)).gate
        assertTrue(gate is StepGate.Skip, gate.toString())
        // and a curse placed earlier triggers nothing.
        val day = place(day(state), 2L, "witch", "Cursed")
        assertNull(DayRules.checkNomination(day, lookup, 2L, 0L).triggers.firstOrNull {
            it.sourceId == "witch"
        })
    }

    @Test
    fun `the Witch curses without waking anybody`() {
        val state = game("vortox", "witch", "chef", "oracle", "artist", "juggler")
        val after = resolve(state, fire(state, "witch", 1L), picks(3L))
        assertTrue(DayRules.hasToken(after, 3L, "witch", "Cursed"))
        assertEquals(listOf(3L), choices(after, "witch").single().targetIds)
        // The curse is a one-day marker: it is gone by the next night.
        assertFalse(DayRules.hasToken(day(day(after)), 3L, "witch", "Cursed"))
    }

    @Test
    fun `the Cerenovus records the madness and keeps acting from the grave`() {
        // Given a Cerenovus, when they make the Oracle mad as the Chef,
        var state = game("vigormortis", "cerenovus", "chef", "oracle", "artist", "juggler")
        state = resolve(
            state,
            fire(state, "cerenovus", 1L),
            NightInput(playerIds = listOf(3L), characterIds = listOf("chef")),
        )
        // Then the Mad token is on the Oracle and only on the Oracle,
        assertTrue(DayRules.hasToken(state, 3L, "cerenovus", "Mad"))
        assertFalse(DayRules.hasToken(state, 2L, "cerenovus", "Mad"))
        // and the character the madness is about is in the record.
        assertEquals(listOf("chef"), choices(state, "cerenovus").single().characterIds)

        // Given the Vigormortis killed the Cerenovus and it kept its ability,
        var dead = atNight(state, 2)
        dead = Effects.place(
            state = dead,
            target = 1L,
            kind = EffectKind.HAS_ABILITY,
            sourceCharacterId = "vigormortis",
            sourcePlayerId = 0L,
            until = Until.FOREVER,
            label = "Has Ability",
        ).state
        dead = Deaths.attempt(dead, lookup, 1L, KillCause(DeathCause.DEMON_KILL, "vigormortis", 0L)).state
        // Then the step still fires — it must never read "usually skip".
        assertFalse(alive(dead, 1L))
        assertTrue(fire(dead, "cerenovus", 1L).gate is StepGate.Fire)
    }

    /**
     * W7D / lead D67 retires this row's old behaviour. `BecomeCharacter.evil` is
     * nullable now, so "keep the alignment" is expressible and the Pit-Hag
     * really changes the seat instead of only raising a prompt about it.
     */
    @Test
    fun `the Pit-Hag changes the seat and keeps its alignment`() {
        // Given a Pit-Hag on night 2 and a GOOD Oracle,
        var state = atNight(game("vortox", "pithag", "chef", "oracle", "artist", "juggler"), 2)
        assertFalse(assertNotNull(state.player(3)).isEvil(lookup))
        // When they point at the Oracle and at the Clockmaker,
        state = resolve(
            state,
            fire(state, "pithag", 1L),
            NightInput(playerIds = listOf(3L), characterIds = listOf("clockmaker")),
        )
        // Then the seat IS the Clockmaker, and is still good.
        assertEquals("clockmaker", assertNotNull(state.player(3)).characterId)
        assertFalse(assertNotNull(state.player(3)).isEvil(lookup))
        assertEquals(ChangeReason.PIT_HAG, state.identityLog.last().reason)
        // The obligation still names the seat, so the token is handed over.
        val owed = promptsFor(state, "pithag", BriefingSlot.NOW).single()
        assertEquals(3L, owed.subjectPlayerId)
        assertEquals(listOf("clockmaker"), choices(state, "pithag").single().characterIds)
        // The Pit-Hag never acts on the first night.
        assertNull(step(game("vortox", "pithag", "chef", "oracle", "artist", "juggler"), "pithag"))
    }

    @Test
    fun `an impaired Pit-Hag points, and nothing changes`() {
        var state = atNight(game("vortox", "pithag", "chef", "oracle", "artist", "juggler"), 2)
        state = Effects.place(
            state, 1L, EffectKind.POISONED, "storyteller", null, Until.DAWN, "",
        ).state
        val before = assertNotNull(state.player(3)).characterId
        state = resolve(
            state,
            NightPlan.build(state, lookup).steps.first { it.abilityId == "pithag" },
            NightInput(playerIds = listOf(3L), characterIds = listOf("clockmaker")),
        )
        assertEquals(before, assertNotNull(state.player(3)).characterId)
        assertTrue(promptsFor(state, "pithag", BriefingSlot.NOW).single().title.contains("change nothing"))
    }

    @Test
    fun `a Pit-Hag head-shake changes nobody and queues nothing`() {
        var state = atNight(game("vortox", "pithag", "chef", "oracle", "artist", "juggler"), 2)
        val before = state.players.map { it.characterId }
        state = resolve(state, fire(state, "pithag", 1L), NightInput(none = true))
        assertEquals(before, state.players.map { it.characterId }, "nothing changes on a head-shake")
        assertTrue(promptsFor(state, "pithag", BriefingSlot.NOW).isEmpty())
    }

    // ==================================================================
    // Demons
    // ==================================================================

    @Test
    fun `the Fang Gu kills, and offers the jump only while it is unused`() {
        // Given a Fang Gu and a living Outsider on night 2,
        var state = atNight(game("fanggu", "witch", "sweetheart", "chef", "oracle", "artist"), 2)
        val fangGu = fire(state, "fanggu", 0L)
        assertTrue("jump" in assertIs<ChoosePlayers>(fangGu.action).noneLabel)

        // When the storyteller declines the ordinary kill, the jump is raised.
        val jumping = resolve(state, fangGu, NightInput(none = true))
        assertEquals(1, promptsFor(jumping, "fanggu", BriefingSlot.NOW).size)

        // Given the jump is already spent (its record is a grimoire-centre token),
        state = state.copy(
            storytellerReminders = listOf(PlacedReminder("fanggu", "Once", placedCycle = 1)),
        )
        val spent = fire(state, "fanggu", 0L)
        assertFalse("jump" in assertIs<ChoosePlayers>(spent.action).noneLabel)
        assertTrue(promptsFor(resolve(state, spent, NightInput(none = true)), "fanggu", BriefingSlot.NOW).isEmpty())

        // And an ordinary kill still goes through the funnel.
        val killed = resolve(state, spent, picks(3L))
        assertFalse(alive(killed, 3L))
        assertEquals(DeathCause.DEMON_KILL, killed.deaths.last().cause)
        assertEquals("fanggu", killed.deaths.last().killerCharacterId)
    }

    @Test
    fun `a Demon silenced by the Exorcist is reduced, not skipped`() {
        var state = atNight(game("vortox", "witch", "chef", "oracle", "artist", "juggler"), 2)
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "exorcist",
            sourcePlayerId = null,
            until = Until.DAWN,
            label = "Chosen",
        ).state
        val gate = assertNotNull(step(state, "vortox", 0L)).gate
        assertTrue(gate is StepGate.Reduced, gate.toString())
    }

    @Test
    fun `a Vigormortis kill raises the keeps-ability obligation, and its death the teardown`() {
        // Given a Vigormortis and a living Minion on night 2,
        var state = atNight(game("vigormortis", "witch", "chef", "oracle", "artist", "juggler"), 2)
        // When the Vigormortis kills the Witch,
        state = resolve(state, fire(state, "vigormortis", 0L), picks(1L))
        // Then the Minion is dead and the two markers are raised as an obligation.
        assertFalse(alive(state, 1L))
        assertEquals(DeathCause.DEMON_KILL, state.deaths.last().cause)
        val owed = promptsFor(state, "vigormortis", BriefingSlot.NOW).single()
        assertEquals(1L, owed.subjectPlayerId)
        assertTrue("Has Ability" in owed.title, owed.title)

        // Given the markers are placed and the Vigormortis then dies,
        state = Effects.place(
            state = state,
            target = 1L,
            kind = EffectKind.HAS_ABILITY,
            sourceCharacterId = "vigormortis",
            sourcePlayerId = 0L,
            until = Until.FOREVER,
            label = "Has Ability",
        ).state
        assertTrue(Status.hasAbility(state, lookup, 1L), "a preserved Minion still acts")
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state

        // Then the teardown is raised and the preserved Minion goes inert.
        assertTrue(
            promptsFor(state, "vigormortis", BriefingSlot.NOW).any { "dead" in it.title },
            "the teardown obligation is missing",
        )
        assertFalse(Status.hasAbility(state, lookup, 1L), "the preserved ability ends with its source")
    }

    @Test
    fun `the No Dashii poisons both Townsfolk neighbours and killing one does not move it`() {
        // Given a No Dashii between a Chef and (past a Minion) a Juggler,
        var state = atNight(game("nodashii", "chef", "oracle", "artist", "juggler", "witch"), 2)
        assertTrue(Status.isImpaired(state, lookup, 1L), "the clockwise Townsfolk is poisoned")
        assertTrue(Status.isImpaired(state, lookup, 4L), "the anticlockwise Townsfolk is poisoned")
        assertFalse(Status.isImpaired(state, lookup, 2L), "only the NEAREST each way")

        // When the No Dashii kills its poisoned neighbour,
        state = resolve(state, fire(state, "nodashii", 0L), picks(1L))
        // Then the corpse is still the nearest Townsfolk and the poison does not move.
        assertFalse(alive(state, 1L))
        assertTrue(Status.isImpaired(state, lookup, 1L), "a dead Townsfolk still counts")
        assertFalse(Status.isImpaired(state, lookup, 2L))
    }

    @Test
    fun `an impaired No Dashii poisons nobody`() {
        var state = atNight(game("nodashii", "chef", "oracle", "artist", "juggler", "witch"), 2)
        state = poison(state, 0L)
        assertFalse(Status.isImpaired(state, lookup, 1L), "no ability, no poison")
        assertFalse(Status.isImpaired(state, lookup, 4L))
    }

    @Test
    fun `the Vortox kills, and dusk without an execution hands the game to evil`() {
        // Given a Vortox on night 2,
        var state = atNight(game("vortox", "witch", "chef", "oracle", "artist", "juggler"), 2)
        // When it points at the Chef,
        state = resolve(state, fire(state, "vortox", 0L), picks(2L))
        assertFalse(alive(state, 2L))
        assertEquals("vortox", state.deaths.last().killerCharacterId)

        // When the day ends with no execution,
        val day = day(state)
        val advisories = WinCheck.duskCheck(day, lookup)
        // Then the dusk check blocks on the Vortox clause.
        val vortox = assertNotNull(advisories.firstOrNull { it.ruleId == WinCheck.RULE_VORTOX_DUSK })
        assertEquals(false, vortox.goodWins)
        assertTrue(vortox.blocking)

        // And an execution that killed nobody still satisfies it.
        val executed = Execution.execute(
            day, lookup, playerId = 3L, via = ExecutionVia.STORYTELLER,
            outcome = ExecutionOutcome.SURVIVED,
        )
        assertNull(
            WinCheck.duskCheck(executed, lookup).firstOrNull { it.ruleId == WinCheck.RULE_VORTOX_DUSK },
        )
    }

    // ---- small readers ----------------------------------------------------

    private fun choices(state: GameState, sourceId: String): List<LedgerEntry> = state.ledger.filter {
        it.kind == LedgerKind.CHOICE && Character.normalizeId(it.sourceId) == sourceId
    }

    private fun place(state: GameState, seat: Long, sourceId: String, label: String): GameState =
        Effects.addReminder(
            state,
            seat,
            PlacedReminder(sourceId, label, placedCycle = state.cycle),
        ).let { withRule(it, seat, sourceId, label) }

    /**
     * A hand-placed token is also projected as an effect, exactly as
     * `Standing.projectTokens` does at query time, so `Effects.place`'s mutex and
     * copy handling is what these tests exercise.
     */
    private fun withRule(state: GameState, seat: Long, sourceId: String, label: String): GameState {
        val rule = Tokens.rule(sourceId, label) ?: return state
        return Effects.place(
            state = state,
            target = seat,
            kind = rule.effect ?: EffectKind.MARKER,
            sourceCharacterId = sourceId,
            sourcePlayerId = null,
            until = rule.until,
            label = label,
            endsWithSource = false,
        ).state
    }

    private fun labels(state: GameState, seat: Long, sourceId: String): List<String> {
        val id = Character.normalizeId(sourceId)
        return state.effects
            .filter { it.targetId == seat && Character.normalizeId(it.sourceCharacterId) == id }
            .map { it.label }
            .distinct()
    }
}
