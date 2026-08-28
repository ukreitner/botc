package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-EXP-M — the fifteen experimental Minions, through the real pipeline.
 *
 * Every case builds a real `GameState`, runs `NightPlan.build` / `.resolve`,
 * `Execution.consequences`, `DayRules.checkNomination` or `Status`, and asserts
 * on the result — never on the registry row's own fields.
 */
class RulesExpMinionsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** Every character this package owns, straight from the data. */
    private val scope: List<Character> =
        data.characters.filter { it.edition == "exp" && it.team == Team.MINION }

    // ---- fixtures ----------------------------------------------------------

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun night(vararg characterIds: String): GameState {
        var state = GameActions.newGame(tb, characterIds.indices.map { "P${it + 1}" })
        characterIds.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        return Phases.advancePhase(state, lookup)
    }

    private fun day(vararg characterIds: String): GameState =
        Phases.advancePhase(night(*characterIds), lookup)

    private fun next(state: GameState): GameState = Phases.advancePhase(state, lookup)

    private fun GameState.seat(characterId: String): Long =
        players.first { it.characterId == characterId }.id

    private fun plan(state: GameState): NightPlan = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String): NightStep? =
        plan(state).steps.firstOrNull { it.abilityId == abilityId }

    private fun run(state: GameState, abilityId: String, input: NightInput = NightInput()): GameState {
        val row = assertNotNull(step(state, abilityId), "no $abilityId step in tonight's plan")
        return NightPlan.resolve(state, lookup, row.key, input)
    }

    private fun token(state: GameState, seat: Long, sourceId: String, label: String): GameState =
        GameActions.addReminder(state, seat, PlacedReminder(sourceId, label, placedCycle = state.cycle))

    private fun holds(state: GameState, seat: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val player = state.player(seat) ?: return false
        return player.reminders.any { Tokens.key(it) == key } ||
            Status.effectsOn(state, lookup, seat)
                .any { Tokens.key(it.sourceCharacterId, it.label) == key }
    }

    // ==================================================================
    // Coverage and the token table
    // ==================================================================

    @Test
    fun `every experimental Minion has a registry row`() {
        assertEquals(15, scope.size, "scope drifted: ${scope.map { it.id }}")
        val missing = scope.map { it.id }.filter { it !in CharacterRules.all }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
    }

    @Test
    fun `every token this package declares is an official reminder with the official copy count`() {
        val problems = mutableListOf<String>()
        for (character in scope) {
            val rule = CharacterRules.all[character.id] ?: continue
            for (declared in rule.tokens) {
                if (Character.normalizeId(declared.sourceId) != character.id) {
                    problems += "${character.id} declares a token sourced ${declared.sourceId}"
                    continue
                }
                val copies = character.allReminders
                    .count { it.trim().equals(declared.label.trim(), ignoreCase = true) }
                if (copies == 0) {
                    problems += "${character.id}/${declared.label} is not in characters.json " +
                        "(has ${character.allReminders})"
                } else if (copies != declared.copies) {
                    problems += "${character.id}/${declared.label}: rule says ${declared.copies}, " +
                        "data has $copies"
                }
                // The registry row must be the one `Tokens.all` resolves.
                val live = Tokens.rule(declared.sourceId, declared.label)
                if (live != declared) {
                    problems += "${character.id}/${declared.label}: Tokens.rule resolves to $live"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the Wizard's two question-mark tokens are new, permanent and can coexist`() {
        val rule = assertNotNull(Tokens.rule("wizard", "?"))
        assertEquals(2, rule.copies)
        assertEquals(Until.FOREVER, rule.until)

        var state = night("wizard", "imp", "chef", "empath", "mayor")
        state = token(state, 2L, "wizard", "?")
        state = token(state, 3L, "wizard", "?")
        assertTrue(holds(state, 2L, "wizard", "?"))
        assertTrue(holds(state, 3L, "wizard", "?"))
        state = next(next(state))
        assertTrue(holds(state, 2L, "wizard", "?"), "a '?' token never expires")
    }

    // ==================================================================
    // Boffin
    // ==================================================================

    @Test
    fun `Given a Boffin granting the Chambermaid, When night 1 is planned, Then both seats wake with the grant card`() {
        var state = night("boffin", "imp", "chef", "empath", "mayor")
        state = Decisions.set(state, Decisions.BOFFIN_GRANT, "chambermaid")

        val row = assertNotNull(step(state, "boffin"), "the Boffin wakes on night 1")
        assertIs<StepGate.Fire>(row.gate)
        assertTrue(
            row.cards.any { it.card == ShowCardSpec.CharacterCard("THE DEMON HAS THIS ABILITY", "chambermaid") },
            "the granted token is pre-filled, never a picker: ${row.cards.map { it.label }}",
        )
        assertTrue(row.cards.any { "SELECTED YOU" in it.label })
    }

    @Test
    fun `Given a Boffin whose grant is set, When the Boffin dies, Then a dawn note names the lost ability`() {
        var state = night("boffin", "imp", "chef", "empath", "mayor")
        state = Decisions.set(state, Decisions.BOFFIN_GRANT, "chambermaid")
        state = Deaths.attempt(
            state, lookup, state.seat("boffin"), KillCause(DeathCause.STORYTELLER, "st"),
        ).state

        val note = assertNotNull(
            Prompts.due(state, BriefingSlot.DAWN).firstOrNull { it.sourceId == "boffin" },
            "the Boffin's death must be surfaced: ${state.prompts.map { it.title }}",
        )
        assertTrue("chambermaid" in note.title, note.title)
        assertTrue("loses" in note.title, note.title)
    }

    // ==================================================================
    // Boomdandy
    // ==================================================================

    @Test
    fun `Given a Boomdandy executed but SAVED, When consequences are computed, Then it still explodes`() {
        val state = day("boomdandy", "imp", "chef", "empath", "mayor")
        val seat = state.seat("boomdandy")
        val record = ExecutionRecord(
            day = state.cycle,
            outcome = ExecutionOutcome.SURVIVED,
            playerId = seat,
            preventedBy = "devilsadvocate",
        )
        val row = assertNotNull(
            Execution.consequences(state, lookup, record).firstOrNull { it.sourceId == "boomdandy" },
            "the explosion is the headline rule and does not depend on the outcome",
        )
        assertTrue("exploded" in row.headline, row.headline)
        assertTrue("did not die" in row.detail, row.detail)
    }

    @Test
    fun `Given a Boomdandy who died at night, When the day closes with no execution, Then nothing explodes`() {
        val state = day("boomdandy", "imp", "chef", "empath", "mayor")
        val record = ExecutionRecord(day = state.cycle, outcome = ExecutionOutcome.NO_EXECUTION)
        assertTrue(
            Execution.consequences(state, lookup, record).none { it.sourceId == "boomdandy" },
            "a night death is not an execution",
        )
    }

    @Test
    fun `Given a poisoned Boomdandy is executed, Then the explosion defaults to OFF with an override`() {
        var state = day("boomdandy", "imp", "poisoner", "chef", "empath", "mayor")
        state = token(state, state.seat("boomdandy"), "poisoner", "Poisoned")
        val record = ExecutionRecord(
            day = state.cycle,
            outcome = ExecutionOutcome.DIED,
            playerId = state.seat("boomdandy"),
        )
        val row = assertNotNull(
            Execution.consequences(state, lookup, record).firstOrNull { it.sourceId == "boomdandy" },
        )
        assertTrue(row.impaired)
        assertEquals(
            "boomdandy-no-explosion",
            row.options.first { it.isDefault }.id,
            "the wiki is silent: no explosion, with a one-tap override",
        )
    }

    // ==================================================================
    // Fearmonger
    // ==================================================================

    @Test
    fun `Given a Fearmonger, When they choose on night 1, Then Fear is placed and the announcement is owed`() {
        var state = night("fearmonger", "imp", "chef", "empath", "mayor")
        state = run(state, "fearmonger", NightInput(playerIds = listOf(4L)))

        assertTrue(holds(state, 4L, "fearmonger", "Fear"))
        val owed = Memory.pendingAnnouncements(state)
        assertEquals(1, owed.size, "night 1 is always a new player")
        assertTrue("has chosen a player" in owed.single().text, owed.single().text)
    }

    @Test
    fun `Given the Fearmonger chose P5 last night, When night 2 is planned, Then the line names last night's pick`() {
        var state = night("fearmonger", "imp", "chef", "empath", "mayor")
        state = run(state, "fearmonger", NightInput(playerIds = listOf(4L)))
        state = next(next(state))
        assertEquals(2, state.cycle)

        state = run(state, "fearmonger", NightInput(playerIds = listOf(2L)))
        val text = Memory.pendingAnnouncements(state).last().text
        assertTrue("P5" in text, "the storyteller must be told last night's pick: $text")
        assertTrue("NEW" in text, text)
        // The Fear token survives NIGHT -> DAY -> NIGHT and moved with the choice.
        assertTrue(holds(state, 2L, "fearmonger", "Fear"))
        assertFalse(holds(state, 4L, "fearmonger", "Fear"), "only one Fear token exists")
    }

    @Test
    fun `Given a dead Fearmonger, When the night is planned, Then the step says to leave the token alone`() {
        var state = night("fearmonger", "imp", "chef", "empath", "mayor")
        state = run(state, "fearmonger", NightInput(playerIds = listOf(4L)))
        state = Deaths.attempt(
            state, lookup, state.seat("fearmonger"), KillCause(DeathCause.STORYTELLER, "st"),
        ).state
        state = next(next(state))

        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "fearmonger")).gate)
        assertTrue("leave the Fear token" in gate.reason, gate.reason)
        assertTrue(holds(state, 4L, "fearmonger", "Fear"), "the token stays where it is")
    }

    // ==================================================================
    // Goblin
    // ==================================================================

    @Test
    fun `Given a living Goblin is nominated, Then the claim question comes first and the win is warned about`() {
        var state = day("goblin", "imp", "chef", "empath", "mayor", "monk", "virgin", "butler")
        val goblin = state.seat("goblin")
        val triggers = DayRules.checkNomination(state, lookup, 1L, goblin).triggers
            .filter { it.sourceId == "goblin" }

        assertEquals(TriggerKind.CHOICE, triggers.first().kind, "WP3's claim row must stay first")
        assertTrue(triggers.any { it.kind == TriggerKind.WARN && "EVIL WINS" in it.headline })

        // And the claim still records the way the day engine expects.
        state = DayRules.applyTrigger(state, lookup, triggers.first(), DayRules.OPTION_APPLY)
        assertTrue(DayRules.hasToken(state, goblin, "goblin", "Claimed"))

        val after = DayRules.checkNomination(state, lookup, 2L, goblin).triggers
            .filter { it.sourceId == "goblin" }
        assertTrue(
            after.any { "already claimed" in it.headline },
            after.map { it.headline }.toString(),
        )
    }

    @Test
    fun `Given a claim on day 1, When the day ends, Then Claimed is gone on day 2`() {
        var state = day("goblin", "imp", "chef", "empath", "mayor")
        val goblin = state.seat("goblin")
        val claim = DayRules.checkNomination(state, lookup, 1L, goblin).triggers
            .first { it.sourceId == "goblin" }
        state = DayRules.applyTrigger(state, lookup, claim, DayRules.OPTION_APPLY)
        assertTrue(holds(state, goblin, "goblin", "Claimed"))

        state = next(next(state))
        assertFalse(holds(state, goblin, "goblin", "Claimed"), "'claimed TODAY' is what expiry means")
    }

    // ==================================================================
    // Harpy
    // ==================================================================

    @Test
    fun `Given a Harpy, When they choose two players, Then Mad and 2nd land in pick order with a day obligation`() {
        var state = night("harpy", "imp", "chef", "empath", "mayor")
        state = run(state, "harpy", NightInput(playerIds = listOf(2L, 4L)))

        assertTrue(holds(state, 2L, "harpy", "Mad"), "the 1st player goes mad")
        assertTrue(holds(state, 4L, "harpy", "2nd"), "the 2nd player is the accused")
        assertFalse(holds(state, 4L, "harpy", "Mad"))

        val due = assertNotNull(
            Prompts.due(state, BriefingSlot.DAY_START).firstOrNull { it.sourceId == "harpy" },
            "the madness binds TOMORROW",
        )
        assertEquals(2L, due.subjectPlayerId)
        assertTrue("mad that the 2nd is evil" in due.title, due.title)
    }

    @Test
    fun `Given Harpy madness, When the mad player nominates, Then the warning names the 2nd seat, not the Cerenovus`() {
        var state = day("harpy", "imp", "chef", "empath", "mayor", "monk", "virgin", "butler")
        state = token(state, 2L, "harpy", "Mad")
        state = token(state, 4L, "harpy", "2nd")

        val triggers = DayRules.checkNomination(state, lookup, 2L, 6L).triggers
        val row = assertNotNull(triggers.firstOrNull { it.sourceId == "harpy" })
        assertTrue("P5" in row.headline, "the 2nd player must be named: ${row.headline}")
        assertTrue("Harpy" in row.headline, row.headline)
        assertTrue(
            triggers.none { "Cerenovus" in it.headline },
            "a Harpy Mad token must never name the Cerenovus",
        )

        // And it fires for the NOMINEE too, not only the nominator.
        val asNominee = DayRules.checkNomination(state, lookup, 6L, 2L).triggers
        assertTrue(asNominee.any { it.sourceId == "harpy" })
    }

    // ==================================================================
    // Marionette
    // ==================================================================

    @Test
    fun `Given a Marionette, Then they wake at the believed character's step with no ability of their own`() {
        var state = night("imp", "marionette", "chef", "empath", "mayor", "monk", "virgin", "butler")
        state = Seats.setShownCharacter(state, 1L, "empath")

        assertTrue(Status.isImpaired(state, lookup, 1L), "the Marionette is just as if drunk")
        assertNull(step(state, "marionette")?.holderId, "the marionette slot wakes the DEMON, not them")
        val believed = plan(state).steps.filter { it.abilityId == "empath" }
        assertTrue(
            believed.any { it.holderId == 1L },
            "they wake at the Empath's step: ${believed.map { it.holderId }}",
        )
    }

    // ==================================================================
    // Mezepheles
    // ==================================================================

    @Test
    fun `Given nobody said the word, When a later night is planned, Then the Mezepheles step is skipped`() {
        val state = next(next(night("mezepheles", "imp", "chef", "empath", "mayor")))
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "mezepheles")).gate)
        assertTrue("secret word" in gate.reason, gate.reason)
    }

    @Test
    fun `Given a marked player and a poisoned Mezepheles, When they stay good, Then the ability is spent anyway`() {
        var state = night("mezepheles", "imp", "poisoner", "chef", "empath", "mayor")
        val mez = state.seat("mezepheles")
        state = next(next(state))
        state = token(state, 4L, "mezepheles", "Turns Evil")
        state = token(state, mez, "poisoner", "Poisoned")
        assertFalse(Status.hasAbility(state, lookup, mez))

        assertIs<StepGate.Fire>(assertNotNull(step(state, "mezepheles")).gate)
        state = run(state, "mezepheles", NightInput(yes = false))

        assertFalse(holds(state, 4L, "mezepheles", "Turns Evil"), "the mark is consumed either way")
        assertTrue(holds(state, mez, "mezepheles", "No Ability"), "spent either way")
        assertTrue(Memory.isSpent(state, "mezepheles", mez))

        state = next(next(state))
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "mezepheles")).gate)
        assertTrue("once per game" in gate.reason, gate.reason)
    }

    @Test
    fun `Given a healthy Mezepheles and a marked player, When they turn evil, Then the alignment change is queued`() {
        var state = night("mezepheles", "imp", "chef", "empath", "mayor")
        state = next(next(state))
        state = token(state, 3L, "mezepheles", "Turns Evil")
        state = run(state, "mezepheles", NightInput(yes = true))

        val prompt = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "mezepheles" && !it.resolved },
        )
        assertEquals(3L, prompt.subjectPlayerId)
        assertTrue("EVIL" in prompt.title, prompt.title)
        assertTrue("keep their character" in prompt.title, prompt.title)
        assertTrue(Memory.isSpent(state, "mezepheles", state.seat("mezepheles")))
    }

    // ==================================================================
    // Organ Grinder
    // ==================================================================

    @Test
    fun `Given an Organ Grinder who nods, Then voting is public today and the Drunk token is gone by night 2`() {
        var state = night("organgrinder", "imp", "chef", "empath", "mayor")
        val og = state.seat("organgrinder")
        assertTrue(DayRules.secretVoting(state, lookup), "a sober Organ Grinder closes every eye")

        state = run(state, "organgrinder", NightInput(yes = true))
        assertTrue(holds(state, og, "organgrinder", "Drunk"))
        assertFalse(DayRules.secretVoting(state, lookup), "nodding yes gives the town a normal day")

        state = next(next(state))
        assertFalse(holds(state, og, "organgrinder", "Drunk"), "the token expires at dusk")
        assertTrue(DayRules.secretVoting(state, lookup))
    }

    @Test
    fun `Given an Organ Grinder who nodded and then shakes, Then the token is removed again`() {
        var state = night("organgrinder", "imp", "chef", "empath", "mayor")
        val og = state.seat("organgrinder")
        state = run(state, "organgrinder", NightInput(yes = true))
        state = next(next(state))
        state = run(state, "organgrinder", NightInput(yes = false))
        assertFalse(holds(state, og, "organgrinder", "Drunk"))
        assertTrue(DayRules.secretVoting(state, lookup))
    }

    // ==================================================================
    // Psychopath
    // ==================================================================

    @Test
    fun `Given a Psychopath before nominations, Then the public kill is offered, and refused once a nomination lands`() {
        var state = day("psychopath", "imp", "chef", "empath", "mayor", "monk", "virgin", "butler")
        val ability = assertNotNull(CharacterRules.all["psychopath"]?.day?.ability)
        val holder = assertNotNull(state.player(state.seat("psychopath")))
        assertEquals("Public kill", ability.label)
        assertTrue(ability.available(state, lookup, holder), "the window is open before nominations")

        state = DayRules.record(state, lookup, Nomination(state.cycle, 1L, 2L))
        assertFalse(
            ability.available(state, lookup, assertNotNull(state.player(holder.id))),
            "nominations have opened — the window is closed",
        )
    }

    @Test
    fun `Given a poisoned Psychopath, Then the public kill is not offered, and the roshambo row still is`() {
        var state = day("psychopath", "imp", "poisoner", "chef", "empath", "mayor")
        val seat = state.seat("psychopath")
        state = token(state, seat, "poisoner", "Poisoned")
        val ability = assertNotNull(CharacterRules.all["psychopath"]?.day?.ability)
        assertFalse(ability.available(state, lookup, assertNotNull(state.player(seat))))

        val record = ExecutionRecord(day = state.cycle, outcome = ExecutionOutcome.DIED, playerId = seat)
        val row = assertNotNull(
            Execution.consequences(state, lookup, record).firstOrNull { it.sourceId == "psychopath" },
        )
        assertTrue(row.impaired, "the storyteller still decides")
    }

    // ==================================================================
    // Summoner
    // ==================================================================

    @Test
    fun `Given a Summoner, When night 1 runs, Then Night 1 is placed and the counter reaches Night 3 on night 3`() {
        var state = night("summoner", "poisoner", "chef", "empath", "mayor", "monk", "virgin", "butler")
        val summoner = state.seat("summoner")
        state = run(state, "summoner")
        assertTrue(holds(state, summoner, "summoner", "Night 1"))

        state = next(next(state))
        assertTrue(holds(state, summoner, "summoner", "Night 2"))
        assertIs<StepGate.Skip>(assertNotNull(step(state, "summoner")).gate)

        state = next(next(state))
        assertEquals(3, state.cycle)
        assertTrue(holds(state, summoner, "summoner", "Night 3"))
        assertIs<StepGate.Fire>(assertNotNull(step(state, "summoner")).gate)
    }

    @Test
    fun `Given the Summoner's 3rd night, When they summon an Imp, Then that seat is an evil Imp who acts tonight`() {
        var state = night("summoner", "poisoner", "chef", "empath", "mayor", "monk", "virgin", "butler")
        val summoner = state.seat("summoner")
        state = run(state, "summoner")
        state = next(next(next(next(state))))
        assertEquals(3, state.cycle)

        state = run(
            state,
            "summoner",
            NightInput(playerIds = listOf(3L), characterIds = listOf("imp")),
        )

        val victim = assertNotNull(state.player(3L))
        assertEquals("imp", victim.characterId)
        assertTrue(victim.isEvil(lookup), "they become an EVIL Demon")
        assertTrue(Memory.isSpent(state, "summoner", summoner), "once per game")
        assertFalse(holds(state, summoner, "summoner", "Night 3"))
        assertTrue(
            plan(state).steps.any { it.abilityId == "imp" && it.holderId == 3L },
            "the new Demon acts the same night: ${plan(state).steps.map { it.abilityId }}",
        )
        assertTrue(
            state.identityLog.any { it.playerId == 3L && it.reason == ChangeReason.SUMMONER },
        )
    }

    @Test
    fun `Given a Summoner game with no Demon, When night 1 is planned, Then no Demon info step exists`() {
        val state = night(
            "summoner", "poisoner", "chef", "empath", "mayor", "monk", "virgin", "butler",
        )
        val slots = plan(state).steps.map { it.slotId }
        assertFalse(NightMarkers.DEMON_INFO in slots, "there is no Demon to wake yet")
        assertTrue(NightMarkers.MINION_INFO in slots)
    }

    // ==================================================================
    // Vizier
    // ==================================================================

    @Test
    fun `Given a Vizier who LOST their ability, Then they still cannot die during the day`() {
        var state = day("vizier", "courtier", "chef", "empath", "mayor", "imp")
        val vizier = state.seat("vizier")
        state = token(state, vizier, "courtier", "Drunk 1")
        assertFalse(Status.hasAbility(state, lookup, vizier), "the Courtier jinx: the ability is gone")

        // The jinx is explicit: losing the ability does not restore day mortality.
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(state, lookup, vizier, KillCause(DeathCause.EXECUTION)),
        )
        assertTrue(DayRules.immuneToDayDeath(state, lookup, vizier))
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(state, lookup, vizier, KillCause(DeathCause.DAY_ABILITY, "psychopath")),
        )
    }

    @Test
    fun `Given a Vizier, Then they die normally at night and the first night declares them publicly`() {
        var state = night("vizier", "courtier", "chef", "empath", "mayor", "imp")
        val vizier = state.seat("vizier")
        state = token(state, vizier, "courtier", "Drunk 1")
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(state, lookup, vizier, KillCause(DeathCause.DEMON_KILL, "imp")),
        )

        val row = assertNotNull(step(state, "vizier"), "the Vizier is declared after the first night")
        assertTrue(
            row.cards.any { it.card == ShowCardSpec.Message("THE VIZIER IS", "P1") },
            "the card is pre-filled from the grimoire: ${row.cards.map { it.label }}",
        )
        assertEquals(WakeCount.NONE, row.wakeCounts, "nobody is woken — it is an announcement")
    }

    // ==================================================================
    // Widow
    // ==================================================================

    @Test
    fun `Given a Widow, When they point at a player, Then that player is poisoned and the step never returns`() {
        var state = night("widow", "imp", "chef", "empath", "mayor")
        state = run(state, "widow", NightInput(playerIds = listOf(2L)))

        assertTrue(holds(state, 2L, "widow", "Poisoned"))
        assertTrue(Status.isImpaired(state, lookup, 2L))

        // "On YOUR 1st night" — the gate closes the moment the choice is recorded.
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "widow")).gate)
        assertTrue("1st night" in gate.reason, gate.reason)

        state = next(next(state))
        assertTrue(Status.isImpaired(state, lookup, 2L), "the poison lasts until the Widow dies")
        // WP6C put `widow` into the otherNight order, so a mid-game Widow now has
        // a step. This Widow has already chosen, so the row is present and skipped
        // rather than absent.
        val other = assertIs<StepGate.Skip>(assertNotNull(step(state, "widow")).gate)
        assertTrue("1st night" in other.reason, other.reason)
    }

    @Test
    fun `Given a Widow's victim, When the Widow dies, Then the poison ends and a dawn note says so`() {
        var state = night("widow", "imp", "chef", "empath", "mayor")
        state = run(state, "widow", NightInput(playerIds = listOf(2L)))
        assertTrue(Status.isImpaired(state, lookup, 2L))

        state = Deaths.attempt(
            state, lookup, state.seat("widow"), KillCause(DeathCause.STORYTELLER, "st"),
        ).state

        assertFalse(Status.isImpaired(state, lookup, 2L), "the poison ends with its source")
        val note = assertNotNull(
            Prompts.due(state, BriefingSlot.DAWN).firstOrNull { it.sourceId == "widow" },
        )
        assertEquals(2L, note.subjectPlayerId)
        assertTrue("no longer poisoned" in note.title, note.title)
    }

    @Test
    fun `Given a Widow who is drunk, Then their victim's poison PAUSES and resumes`() {
        var state = night("widow", "imp", "innkeeper", "chef", "empath", "mayor")
        val widow = state.seat("widow")
        state = run(state, "widow", NightInput(playerIds = listOf(3L)))
        assertTrue(Status.isImpaired(state, lookup, 3L))

        state = token(state, widow, "innkeeper", "Drunk")
        assertFalse(Status.isImpaired(state, lookup, 3L), "the wiki's Innkeeper example")

        state = state.updatePlayer(widow) { p -> p.copy(reminders = emptyList()) }
        assertTrue(Status.isImpaired(state, lookup, 3L), "and it resumes")
    }

    // ==================================================================
    // Wizard
    // ==================================================================

    @Test
    fun `Given a declined wish, Then the Wizard may wish again, and only a granted one spends the step`() {
        var state = night("wizard", "imp", "chef", "empath", "mayor")
        val wizard = state.seat("wizard")
        assertIs<StepGate.Fire>(assertNotNull(step(state, "wizard")).gate)

        state = run(state, "wizard", NightInput(yes = false))
        state = next(next(state))
        assertIs<StepGate.Fire>(
            assertNotNull(step(state, "wizard")).gate,
        )

        state = run(state, "wizard", NightInput(yes = true))
        assertTrue(Memory.isSpent(state, "wizard", wizard))
        assertTrue(
            state.prompts.any { it.sourceId == "wizard" && "price" in it.title },
            "the wish, its price and the clue must be recorded",
        )

        state = next(next(state))
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "wizard")).gate)
        assertTrue("once per game" in gate.reason, gate.reason)
    }

    // ==================================================================
    // Wraith
    // ==================================================================

    @Test
    fun `Given a Wraith, Then they get a marker step on both nights that does not count for the Chambermaid`() {
        var state = night("wraith", "imp", "chef", "empath", "mayor")
        val first = assertNotNull(step(state, "wraith"))
        assertIs<StepGate.Fire>(first.gate)
        assertEquals(WakeCount.INFORMED, first.wakeCounts)
        assertNull(first.action, "the Wraith chooses nothing")

        state = next(next(state))
        assertNotNull(step(state, "wraith"), "and every other night too")
    }

    // ==================================================================
    // Xaan
    // ==================================================================

    @Test
    fun `Given X equals 1, When night 1 runs, Then every Townsfolk is poisoned and nobody else is`() {
        var state = night(
            "xaan", "imp", "poisoner", "butler", "chef", "empath", "mayor", "monk",
        )
        state = Decisions.set(state, Decisions.XAAN_X, "1")

        val townsfolk = state.seats.filter { it.characterId?.let(lookup)?.team == Team.TOWNSFOLK }
        assertEquals(4, townsfolk.size)
        for (seat in townsfolk) {
            assertTrue(Status.isImpaired(state, lookup, seat.id), "${seat.characterId} must be poisoned")
        }
        // The Outsider, the Minions and the Demon are not — team is by the TRUE character.
        assertFalse(Status.isImpaired(state, lookup, state.seat("butler")))
        assertFalse(Status.isImpaired(state, lookup, state.seat("imp")))
        assertFalse(Status.isImpaired(state, lookup, state.seat("poisoner")))
    }

    @Test
    fun `Given X equals 1, When the Xaan's step runs, Then the X token and the dawn note are placed`() {
        var state = night("xaan", "imp", "poisoner", "chef", "empath", "mayor", "monk", "butler")
        state = Decisions.set(state, Decisions.XAAN_X, "1")
        val xaan = state.seat("xaan")

        assertIs<StepGate.Fire>(assertNotNull(step(state, "xaan")).gate)
        state = run(state, "xaan")

        assertTrue(holds(state, xaan, "xaan", "X"))
        val note = assertNotNull(Prompts.due(state, BriefingSlot.DAWN).firstOrNull { it.sourceId == "xaan" })
        assertTrue("false" in note.title, note.title)
    }

    @Test
    fun `Given X equals 2, Then night 1 is skipped, night 2 poisons, and night 3 is over`() {
        var state = night("xaan", "imp", "poisoner", "chef", "empath", "mayor", "monk", "butler")
        state = Decisions.set(state, Decisions.XAAN_X, "2")
        val chef = state.seat("chef")

        val n1 = assertIs<StepGate.Skip>(assertNotNull(step(state, "xaan")).gate)
        assertTrue("does nothing tonight" in n1.reason, n1.reason)
        assertFalse(Status.isImpaired(state, lookup, chef))

        state = next(next(state))
        assertIs<StepGate.Fire>(assertNotNull(step(state, "xaan")).gate)
        assertTrue(Status.isImpaired(state, lookup, chef))
        // The poison runs through the whole day.
        assertTrue(Status.isImpaired(next(state), lookup, chef))

        state = next(next(state))
        val n3 = assertIs<StepGate.Skip>(assertNotNull(step(state, "xaan")).gate)
        assertTrue("no further effect" in n3.reason, n3.reason)
        assertFalse(Status.isImpaired(state, lookup, chef))
    }

    @Test
    fun `Given a dead Xaan on night X, Then nothing is poisoned`() {
        var state = night("xaan", "imp", "poisoner", "chef", "empath", "mayor", "monk", "butler")
        state = Decisions.set(state, Decisions.XAAN_X, "1")
        state = Deaths.attempt(
            state, lookup, state.seat("xaan"), KillCause(DeathCause.STORYTELLER, "st"),
        ).state

        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "xaan")).gate)
        assertTrue("dead" in gate.reason, gate.reason)
        assertFalse(Status.isImpaired(state, lookup, state.seat("chef")))
    }

    @Test
    fun `Given X is unset, Then the storyteller is told rather than silently skipped`() {
        val state = night("xaan", "imp", "poisoner", "chef", "empath", "mayor", "monk", "butler")
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(state, "xaan")).gate)
        assertTrue("has not been chosen" in gate.reason, gate.reason)
    }
}
