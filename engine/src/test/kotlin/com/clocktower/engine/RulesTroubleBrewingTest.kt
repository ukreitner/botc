package com.clocktower.engine

import com.clocktower.engine.rules.TB_RULES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WP7-TB acceptance: the Trouble Brewing registry, driven through the real
 * pipeline — `NightPlan.build` / `NightPlan.resolve`, `Deaths.attempt`,
 * `Execution.execute` and `DayRules.checkNomination` — never by reading the
 * data class back.
 *
 * One Given/When/Then per P0 of the group's audit cards, plus the two table
 * tests the package's acceptance criteria name.
 */
class RulesTroubleBrewingTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    /** The 22 Trouble Brewing residents this package owns. */
    private val scope: List<Character> = data.characters.filter {
        it.edition == "tb" && it.team.isTownResident
    }

    // ---- fixtures ---------------------------------------------------------

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(tb, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    private fun atNight(state: GameState, n: Int): GameState =
        state.copy(cycle = n, nightStepsDone = emptySet())

    private fun step(state: GameState, abilityId: String, holderId: Long? = null): NightStep =
        assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull {
                it.abilityId == abilityId && (holderId == null || it.holderId == holderId)
            },
            "no $abilityId step: ${NightPlan.build(state, lookup).steps.map { it.key.token }}",
        )

    private fun run(state: GameState, abilityId: String, vararg targets: Long): GameState =
        NightPlan.resolve(
            state,
            lookup,
            step(state, abilityId).key,
            NightInput(playerIds = targets.toList()),
        )

    private fun alive(state: GameState, id: Long): Boolean =
        assertNotNull(state.player(id)).alive

    private fun carries(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val seat = assertNotNull(state.player(playerId))
        return seat.reminders.any { Tokens.key(it) == key } ||
            state.effects.any { it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key }
    }

    private fun poison(state: GameState, target: Long, source: Long): GameState = Effects.place(
        state = state,
        target = target,
        kind = EffectKind.POISONED,
        sourceCharacterId = "poisoner",
        sourcePlayerId = source,
        until = Until.DUSK,
        label = "Poisoned",
    ).state

    // ======================================================================
    // Coverage and data parity (the two table tests WP7 acceptance names)
    // ======================================================================

    @Test
    fun `every Trouble Brewing resident has a registry row`() {
        assertEquals(22, scope.size, "the edition's resident roster: ${scope.map { it.id }}")
        val rows = TB_RULES.map { Character.normalizeId(it.id) }.toSet()
        val missing = scope.map { it.id }.filterNot { Character.normalizeId(it) in rows }
        assertTrue(missing.isEmpty(), "Trouble Brewing characters with no CharacterRule: $missing")
        assertEquals(TB_RULES.size, rows.size, "no id is declared twice")
        // And the registry is what the engine actually resolves.
        for (character in scope) {
            assertEquals(
                Character.normalizeId(character.id),
                CharacterRules.of(character.id, character).id,
                "${character.id} resolves to a foreign row",
            )
        }
    }

    @Test
    fun `every declared token exists in characters json with the declared copy count`() {
        val wrong = mutableListOf<String>()
        for (rule in TB_RULES) {
            for (token in rule.tokens) {
                assertEquals(
                    Character.normalizeId(rule.id),
                    Character.normalizeId(token.sourceId),
                    "a row may only declare its own tokens",
                )
                val character = assertNotNull(lookup(token.sourceId), token.sourceId)
                val copies = character.allReminders.count { it.trim().equals(token.label.trim(), true) }
                if (copies != token.copies) {
                    wrong += "${token.sourceId}/${token.label}: rule says ${token.copies}, " +
                        "characters.json has $copies (${character.allReminders})"
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    // ======================================================================
    // Townsfolk
    // ======================================================================

    @Test
    fun `the three start-knowing steps run on the first night only and keep their tokens`() {
        val state = game("imp", "poisoner", "washerwoman", "librarian", "investigator", "mayor")
        for (id in listOf("washerwoman", "librarian", "investigator")) {
            val row = step(state, id)
            assertEquals(WakeStyle.FIRST_NIGHT, row.style)
            assertEquals(StepGate.Fire, row.gate, "$id wakes on the first night")
            assertEquals(0, assertIs<ShowInfo>(row.action).targetsNeeded, "$id points, it does not pick")
            // lead D9: night-1 information marks are cleared on demand, never swept.
            for (label in listOf("Townsfolk", "Outsider", "Minion", "Wrong")) {
                Tokens.rule(id, label)?.let { assertEquals(Until.MANUAL, it.until, "$id/$label") }
            }
        }
        val night2 = atNight(state, 2)
        assertTrue(
            NightPlan.build(night2, lookup).steps.none { it.abilityId == "washerwoman" },
            "and never again",
        )
    }

    @Test
    fun `a dead Empath is skipped, auto-ticked, and never blocks dawn`() {
        var state = atNight(game("imp", "poisoner", "empath", "chef", "monk", "mayor"), 2)
        assertEquals(StepGate.Fire, step(state, "empath").gate)

        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state

        val row = step(state, "empath")
        assertTrue(row.gate is StepGate.Skip, "a dead Empath has no ability: ${row.gate}")
        assertFalse(row.required)
        assertTrue(row.isDone(emptySet()))
        assertTrue(NightPlan.build(state, lookup).unfinished(emptySet()).none { it.abilityId == "empath" })
    }

    @Test
    fun `the Fortune Teller picks two players, dead or themselves, and the pick is recorded`() {
        var state = atNight(game("imp", "poisoner", "fortuneteller", "chef", "monk", "mayor"), 2)
        state = Deaths.attempt(state, lookup, 3L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state

        val row = step(state, "fortuneteller")
        val action = assertIs<ShowInfo>(row.action)
        assertEquals(2, action.targetsNeeded)

        // A dead seat and the Fortune Teller's own seat are both legal.
        state = run(state, "fortuneteller", 3L, 2L)
        val choice = assertNotNull(
            state.ledger.lastOrNull { it.kind == LedgerKind.CHOICE && it.sourceId == "fortuneteller" },
            "the pick is recorded, so tonight's step can show last night's question",
        )
        assertEquals(listOf(3L, 2L), choice.targetIds)
        assertEquals(2L, choice.actorId)
    }

    @Test
    fun `the Undertaker wakes only when an execution actually killed someone`() {
        var state = game("imp", "poisoner", "undertaker", "chef", "monk", "mayor")

        // Given no execution on day 1.
        state = GameActions.advancePhase(state)
        var night2 = atNight(state, 2).copy(phase = Phase.NIGHT)
        var gate = step(night2, "undertaker").gate
        assertTrue(gate is StepGate.Skip, "nobody was executed: $gate")

        // Given an execution nobody died from.
        val survived = Execution.execute(
            state, lookup, playerId = 3L, outcome = ExecutionOutcome.SURVIVED,
        )
        night2 = atNight(survived, 2).copy(phase = Phase.NIGHT)
        gate = step(night2, "undertaker").gate
        assertTrue(gate is StepGate.Skip, "executed, but did not die: $gate")

        // Given an execution that killed the Chef.
        val died = Execution.execute(state, lookup, playerId = 3L)
        assertFalse(alive(died, 3L))
        assertTrue(carries(died, 3L, "undertaker", "Died Today"), "the execution funnel marks the corpse")
        night2 = atNight(died, 2).copy(phase = Phase.NIGHT)
        assertEquals(StepGate.Fire, step(night2, "undertaker").gate)
        assertEquals(0, assertIs<ShowInfo>(step(night2, "undertaker").action).targetsNeeded)
    }

    @Test
    fun `a sober Monk stops the Demon and a poisoned one does not`() {
        // Given a sober Monk protecting the Chef.
        var sober = atNight(game("imp", "poisoner", "monk", "chef", "mayor", "butler"), 2)
        sober = run(sober, "monk", 3L)
        assertTrue(carries(sober, 3L, "monk", "Safe"))

        // When the Imp attacks the protected seat.
        sober = run(sober, "imp", 3L)

        // Then nobody dies, and the prevented attack is on the record.
        assertTrue(alive(sober, 3L), "the Monk's protection held")
        assertTrue(sober.deaths.isEmpty())
        assertTrue(sober.ledger.any { it.kind == LedgerKind.RULING && it.actorId == 3L })

        // Given the same board with the Monk poisoned first.
        var poisoned = atNight(game("imp", "poisoner", "monk", "chef", "mayor", "butler"), 2)
        poisoned = run(poisoned, "poisoner", 2L)
        poisoned = run(poisoned, "monk", 3L)
        assertTrue(carries(poisoned, 3L, "monk", "Safe"), "the token is still placed — the grimoire must look normal")

        poisoned = run(poisoned, "imp", 3L)
        assertFalse(alive(poisoned, 3L), "a poisoned Monk protects nobody")
    }

    @Test
    fun `a Monk-protected Imp that targets itself neither dies nor passes the star`() {
        var state = atNight(game("imp", "scarletwoman", "monk", "chef", "mayor", "butler"), 2)
        state = run(state, "monk", 0L)

        state = run(state, "imp", 0L)

        assertTrue(alive(state, 0L), "the Imp survives its own attack")
        assertTrue(state.deaths.isEmpty())
        assertTrue(state.prompts.none { it.sourceId == "imp" }, "and no heir is asked for")
    }

    @Test
    fun `the Ravenkeeper fires on the night they die, whatever killed them, and never otherwise`() {
        val start = atNight(game("imp", "poisoner", "ravenkeeper", "chef", "monk", "mayor"), 2)

        // Given they are alive.
        assertTrue(step(start, "ravenkeeper").gate is StepGate.Skip)

        // When a non-Demon night death takes them (a Mayor bounce, an Assassin…).
        val dead = Deaths.attempt(
            start, lookup, 2L, KillCause(DeathCause.EVIL_ABILITY, "poisoner", 1L),
        ).state
        val row = step(dead, "ravenkeeper")
        assertEquals(StepGate.Fire, row.gate, "they died tonight — they wake")
        assertTrue("died tonight" in row.badges)
        assertEquals(1, assertIs<ShowInfo>(row.action).targetsNeeded)

        // Then dying yesterday is not enough.
        val yesterday = atNight(dead, 3)
        assertTrue(step(yesterday, "ravenkeeper").gate is StepGate.Skip, "only the night they die")
    }

    @Test
    fun `the Virgin executes a Townsfolk nominator, spends the ability and closes the day`() {
        var state = GameActions.advancePhase(game("imp", "poisoner", "virgin", "chef", "monk", "mayor"))
        assertEquals(Phase.DAY, state.phase)

        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
                .triggers.find { it.sourceId == "virgin" },
        )
        assertEquals(TriggerKind.AUTO_EXECUTION, trigger.kind)
        assertFalse(trigger.impaired)

        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_EXECUTE)

        assertFalse(alive(state, 3L), "the Chef is executed immediately")
        val record = assertNotNull(DayRules.executionToday(state))
        assertEquals(ExecutionVia.VIRGIN, record.via)
        assertEquals(ExecutionOutcome.DIED, record.outcome)
        assertTrue(Memory.isSpent(state, "virgin", 2L))
        assertTrue(DayRules.nominationsClosed(state, lookup))
    }

    @Test
    fun `a poisoned Virgin kills nobody and is still spent`() {
        var state = GameActions.advancePhase(game("imp", "poisoner", "virgin", "chef", "monk", "mayor"))
        state = poison(state, 2L, 1L)

        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
                .triggers.find { it.sourceId == "virgin" },
        )
        assertEquals(TriggerKind.WARN, trigger.kind, "an impaired Virgin never auto-executes")
        assertTrue(trigger.impaired)
        assertEquals(
            DayRules.OPTION_REGISTERS_GOOD,
            assertNotNull(trigger.options.firstOrNull { it.isDefault }).id,
            "and the default is 'nothing happens'",
        )

        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_REGISTERS_GOOD)
        assertTrue(alive(state, 3L), "the nominator lives")
        assertTrue(state.executions.isEmpty())
        assertTrue(Memory.isSpent(state, "virgin", 2L), "but the ability is used up")
    }

    @Test
    fun `a Drunk nominator is an Outsider, so the Virgin does not fire`() {
        var state = game("imp", "poisoner", "virgin", "drunk", "monk", "mayor")
        state = GameActions.setShownCharacter(state, 3L, "chef")
        state = GameActions.advancePhase(state)

        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
                .triggers.find { it.sourceId == "virgin" },
        )
        assertEquals(
            TriggerKind.CHOICE,
            trigger.kind,
            "they believe they are the Chef, but they are the Drunk — an Outsider",
        )
        assertEquals(
            DayRules.OPTION_REGISTERS_GOOD,
            assertNotNull(trigger.options.firstOrNull { it.isDefault }).id,
        )
    }

    @Test
    fun `the Slayer shot kills the Demon, is not an execution, and is once per game`() {
        var state = GameActions.advancePhase(game("imp", "poisoner", "slayer", "chef", "monk", "mayor"))
        val ability = assertNotNull(CharacterRules.of("slayer", lookup("slayer")).day?.ability)
        val slayer = assertNotNull(state.player(2L))
        assertTrue(ability.available(state, lookup, slayer), "an alive, unspent, sober Slayer may shoot")

        state = Deaths.attempt(
            state, lookup, 0L, KillCause(DeathCause.DAY_ABILITY, "slayer", slayer.id),
        ).state

        assertFalse(alive(state, 0L), "the Imp dies")
        assertEquals(DeathCause.DAY_ABILITY, assertNotNull(state.deaths.lastOrNull()).cause)
        assertTrue(state.executions.isEmpty(), "a slay is not an execution — the Undertaker learns nothing")

        val spent = Effects.place(
            state = state,
            target = slayer.id,
            kind = EffectKind.SPENT,
            sourceCharacterId = "slayer",
            sourcePlayerId = slayer.id,
            until = Until.FOREVER,
            label = assertNotNull(lookup("slayer")).spentLabel,
        ).state
        assertFalse(
            ability.available(spent, lookup, assertNotNull(spent.player(2L))),
            "the shot is once per game",
        )
    }

    @Test
    fun `a poisoned Slayer's shot misses and a Recluse can still be shot`() {
        var state = GameActions.advancePhase(game("imp", "poisoner", "slayer", "recluse", "monk", "mayor"))
        state = poison(state, 2L, 1L)
        val ability = assertNotNull(CharacterRules.of("slayer", lookup("slayer")).day?.ability)
        assertFalse(
            ability.available(state, lookup, assertNotNull(state.player(2L))),
            "an impaired Slayer's shot does nothing — say 'Nothing happens'",
        )
        // The Recluse may be ruled to register as the Demon, and then dies.
        val ruled = Effects.place(
            state = state,
            target = 3L,
            kind = EffectKind.REGISTERS_AS,
            sourceCharacterId = "recluse",
            sourcePlayerId = 3L,
            until = Until.FOREVER,
            label = "",
            characterId = "imp",
        ).state
        assertTrue(Team.DEMON in Registration.registersAs(ruled, lookup, assertNotNull(ruled.player(3L))))
    }

    @Test
    fun `the Soldier is safe from the Demon only, and only while sober`() {
        // Given an alive, unimpaired Soldier.
        var state = atNight(game("imp", "poisoner", "soldier", "chef", "monk", "mayor"), 2)
        assertTrue(
            Status.protections(state, lookup, 2L).any { it.kind == EffectKind.SAFE_FROM_DEMON },
            "the protection is innate and derived, never a token",
        )
        assertTrue(
            assertNotNull(state.player(2L)).reminders.isEmpty(),
            "and it renders no reminder",
        )

        // When the Imp attacks them.
        state = run(state, "imp", 2L)
        assertTrue(alive(state, 2L), "nobody dies")

        // Then an execution still kills them.
        val executed = Execution.execute(
            state.copy(phase = Phase.DAY, cycle = 2), lookup, playerId = 2L,
        )
        assertFalse(alive(executed, 2L))

        // And a poisoned Soldier dies to the Demon.
        var poisoned = atNight(game("imp", "poisoner", "soldier", "chef", "monk", "mayor"), 2)
        poisoned = run(poisoned, "poisoner", 2L)
        assertTrue(Status.protections(poisoned, lookup, 2L).none { it.kind == EffectKind.SAFE_FROM_DEMON })
        poisoned = run(poisoned, "imp", 2L)
        assertFalse(alive(poisoned, 2L), "a poisoned Soldier is not safe")
    }

    @Test
    fun `the Mayor's night death is a storyteller choice, not a silent kill`() {
        val state = atNight(game("imp", "poisoner", "mayor", "chef", "monk", "butler"), 2)

        val outcome = Deaths.killOutcome(
            state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L),
        )
        val choice = assertIs<KillOutcome.Choice>(outcome, "three buttons, never two")
        assertTrue(choice.options.any { it.id == Deaths.OPTION_DIES })
        assertTrue(choice.options.any { it.id == Deaths.OPTION_REDIRECT })

        // Unanswered, the night step changes nothing at all.
        val unanswered = run(state, "imp", 2L)
        assertTrue(alive(unanswered, 2L))
        assertTrue(unanswered.deaths.isEmpty())
    }

    // ======================================================================
    // Outsiders
    // ======================================================================

    @Test
    fun `the Butler marks a Master, never themselves, and the Master gates their vote`() {
        var state = atNight(game("imp", "poisoner", "butler", "chef", "monk", "mayor"), 2)

        // Self is not a legal Master: an illegal pick is dropped at resolve time.
        val selfPick = run(state, "butler", 2L)
        assertFalse(carries(selfPick, 2L, "butler", "Master"), "the Butler may not choose themselves")

        state = run(state, "butler", 3L)
        assertTrue(carries(state, 3L, "butler", "Master"))
        assertEquals(3L, DayRules.masterOf(state, 2L))

        val day = state.copy(phase = Phase.DAY)
        assertTrue(
            DayRules.butlerVotingIllegally(day, lookup, 2L, voterIds = listOf(2L)),
            "the Butler's hand is up but the Master's is not",
        )
        assertFalse(DayRules.butlerVotingIllegally(day, lookup, 2L, voterIds = listOf(2L, 3L)))
    }

    @Test
    fun `the Master mark lasts all day and is gone by the next night`() {
        var state = atNight(game("imp", "poisoner", "butler", "chef", "monk", "mayor"), 2)
        state = run(state, "butler", 3L)

        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.DAY, state.phase)
        assertTrue(carries(state, 3L, "butler", "Master"), "tomorrow's vote needs it")

        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.NIGHT, state.phase)
        assertFalse(carries(state, 3L, "butler", "Master"), "and it is cleared at dusk")
    }

    @Test
    fun `everything a Drunk-as-Monk places is inert`() {
        var state = game("imp", "poisoner", "drunk", "chef", "mayor", "butler")
        state = GameActions.setShownCharacter(state, 2L, "monk")
        state = atNight(state, 2)

        val row = step(state, "monk", holderId = 2L)
        assertEquals("drunk", row.sourceId, "they wake on the Monk's row")
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(3L)))
        assertTrue(carries(state, 3L, "monk", "Safe"), "the token is placed — a Spy must see a normal grimoire")

        state = run(state, "imp", 3L)
        assertFalse(alive(state, 3L), "but a Drunk protects nobody")
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.MALFUNCTION && it.actorId == 2L },
            "and the malfunction is on the record for the Mathematician",
        )
    }

    @Test
    fun `the Recluse and the Spy keep their ability after death`() {
        val state = game("imp", "poisoner", "recluse", "spy", "monk", "mayor")
        for (id in listOf("recluse", "spy")) {
            assertTrue(
                assertNotNull(CharacterRules.all[id]).keepsAbilityWhenDead,
                "$id: 'even if dead' is part of the ability",
            )
        }
        val dead = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.EXECUTION)).state
        assertTrue(
            Status.hasAbility(dead, lookup, 2L),
            "a dead Recluse still registers as evil when the storyteller rules it",
        )
    }

    @Test
    fun `the Saint is flagged on the nomination, before anyone votes`() {
        var state = GameActions.advancePhase(game("imp", "poisoner", "saint", "chef", "monk", "mayor"))

        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
                .triggers.find { it.sourceId == "saint" },
            "nothing warned the storyteller before the execution",
        )
        assertEquals(TriggerKind.WARN, trigger.kind)
        assertFalse(trigger.impaired)
        assertTrue("SAINT" in trigger.headline)

        // A poisoned Saint is safe to execute, and the row says so privately.
        state = poison(state, 2L, 1L)
        val impaired = assertNotNull(
            DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 2L)
                .triggers.find { it.sourceId == "saint" },
        )
        assertTrue(impaired.impaired)
    }

    // ======================================================================
    // Minions and the Demon
    // ======================================================================

    @Test
    fun `the Poisoner's target is poisoned tonight and all of tomorrow`() {
        var state = atNight(game("imp", "poisoner", "chef", "empath", "monk", "mayor"), 2)
        state = run(state, "poisoner", 3L)
        assertTrue(Status.isImpaired(state, lookup, 3L))

        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.DAY, state.phase)
        assertTrue(Status.isImpaired(state, lookup, 3L), "…and tomorrow day")

        state = Phases.advancePhase(state, lookup)
        assertFalse(Status.isImpaired(state, lookup, 3L), "cleared at dusk")
    }

    @Test
    fun `the Spy wakes every night to see the grimoire and is asked nothing`() {
        val state = game("imp", "poisoner", "spy", "chef", "monk", "mayor")
        val first = step(state, "spy")
        assertEquals(StepGate.Fire, first.gate)
        assertEquals(null, first.action, "there is no question — the storyteller shows the grimoire")
        assertTrue("Grimoire" in first.prompt)
        assertEquals(StepGate.Fire, step(atNight(state, 2), "spy").gate)
    }

    @Test
    fun `the Scarlet Woman catches the Demon at five alive and wakes to be told`() {
        var state = atNight(
            game("imp", "scarletwoman", "poisoner", "chef", "monk", "mayor", "butler", "empath"),
            2,
        )
        assertTrue(step(state, "scarletwoman").gate is StepGate.Skip, "she does not wake before the catch")

        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.DAY_ABILITY, "slayer", 3L)).state

        assertTrue(carries(state, 1L, "scarletwoman", "Is The Demon"), "she is marked")
        val prompt = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "scarletwoman" && !it.resolved },
            "and the storyteller owes the change of character",
        )
        assertEquals(1L, prompt.subjectPlayerId)
        assertEquals(listOf("imp"), prompt.characterIds, "she becomes the Demon that died")
        assertEquals(StepGate.Fire, step(state, "scarletwoman").gate, "now she wakes, to be shown the token")
    }

    @Test
    fun `the Scarlet Woman does not catch below five residents, and Travellers do not count`() {
        var state = game("imp", "scarletwoman", "chef", "monk")
        state = GameActions.addSeat(state, "T1")
        val traveller = assertNotNull(state.players.lastOrNull()).id
        state = GameActions.assignCharacter(state, traveller, "beggar", isTraveller = true)
        state = atNight(state, 2)
        assertEquals(4, state.aliveCountResidents)

        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.DAY_ABILITY, "slayer", 2L)).state

        assertFalse(carries(state, 1L, "scarletwoman", "Is The Demon"), "four residents is not five")
        assertTrue(state.prompts.none { it.sourceId == "scarletwoman" })
    }

    @Test
    fun `a poisoned Scarlet Woman does not become the Demon`() {
        var state = atNight(
            game("imp", "scarletwoman", "poisoner", "chef", "monk", "mayor", "butler", "empath"),
            2,
        )
        state = poison(state, 1L, 2L)

        state = Execution.execute(state.copy(phase = Phase.DAY), lookup, playerId = 0L)

        assertFalse(alive(state, 0L), "the Demon was executed")
        assertFalse(carries(state, 1L, "scarletwoman", "Is The Demon"))
        assertTrue(state.prompts.none { it.sourceId == "scarletwoman" })
    }

    @Test
    fun `the Imp kills, and a dead player is a legal but pointless choice`() {
        var state = atNight(game("imp", "poisoner", "chef", "empath", "monk", "mayor"), 2)

        state = run(state, "imp", 2L)
        assertFalse(alive(state, 2L))
        val death = assertNotNull(state.deaths.lastOrNull())
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("imp", death.killerCharacterId)
        assertEquals(0L, death.killerPlayerId)

        // Night 3: choosing the corpse is allowed and kills nobody again.
        state = atNight(state, 3)
        state = run(state, "imp", 2L)
        assertEquals(1, state.deaths.size)
    }

    @Test
    fun `an Imp that chooses itself is asked for an heir`() {
        var state = atNight(game("imp", "scarletwoman", "poisoner", "chef", "monk", "mayor"), 2)

        state = run(state, "imp", 0L)

        assertFalse(alive(state, 0L), "the Imp dies")
        val starPass = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "imp" && !it.resolved },
            "a Minion becomes the Imp: ${state.prompts.map { it.sourceId }}",
        )
        assertEquals(PromptKind.CHOOSE_PLAYER, starPass.kind)
        // With five or more alive the Scarlet Woman catch fires too — she is the heir.
        assertTrue(state.prompts.any { it.sourceId == "scarletwoman" })
    }

    @Test
    fun `a silenced Imp still wakes, is reduced, and kills nobody`() {
        var state = atNight(game("imp", "poisoner", "chef", "empath", "monk", "mayor"), 2)
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "exorcist",
            sourcePlayerId = 4L,
            until = Until.DAWN,
            label = "Chosen",
        ).state

        val row = step(state, "imp")
        val gate = assertIs<StepGate.Reduced>(row.gate, "a silenced Demon is never skipped")
        assertFalse(StepGate.CHOOSE in gate.allow)

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(2L)))
        assertTrue(alive(state, 2L), "nothing they choose kills tonight")
    }

    @Test
    fun `the Baron changes the bag and nothing else`() {
        val baron = assertNotNull(CharacterRules.all["baron"])
        assertEquals(null, baron.firstNight, "the Baron never wakes")
        assertEquals(null, baron.otherNight)
        assertTrue(baron.tokens.isEmpty())

        val state = game("imp", "baron", "chef", "empath", "monk", "butler", "drunk")
        assertTrue(
            NightPlan.build(state, lookup).steps.none { it.abilityId == "baron" },
            "and has no row on any night sheet",
        )
    }
}
