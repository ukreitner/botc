package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Briefings and the phase flow (WP6, ARCHITECTURE §2.12 / §2.15).
 *
 * Two of the user's own complaints are pinned here end to end:
 *  - *"When Professor brings someone back it should remind in the morning and
 *    rerun the 1st night for that"* — the dawn card announces it, after the
 *    deaths and without a reason, and tonight's sheet grows the step;
 *  - *"make it easy to write down all the gossips even if Gossip isn't in
 *    play"* — the day briefing asks for the statement that has not been
 *    recorded yet.
 */
class BriefingsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val sv = data.builtInScripts().first { it.id == "sv" }

    private fun newGame(script: Script, names: List<String>): GameState =
        GameActions.newGame(script, names)

    private fun seat(state: GameState, name: String): Long =
        assertNotNull(state.players.find { it.name == name }, name).id

    private fun assign(state: GameState, name: String, characterId: String): GameState =
        Seats.assignCharacter(state, seat(state, name), characterId)

    /** SETUP -> night 1. */
    private fun night(state: GameState): GameState = Phases.advancePhase(state, lookup)

    /** SETUP -> night 1 -> day 1. */
    private fun day(state: GameState): GameState = night(night(state))

    private fun texts(briefing: Briefing, kind: BriefingKind): List<String> =
        briefing.of(kind).map { it.text }

    // ==================================================================
    // The dawn card's "your notes"
    // ==================================================================

    @Test
    fun `the dawn notes carry what the night did and what does not lift`() {
        var state = newGame(tb, listOf("Ana", "Ben", "Cleo", "Dan", "Eve", "Fay", "Gus", "Hal"))
        for ((name, id) in listOf(
            "Ana" to "imp", "Ben" to "poisoner", "Cleo" to "empath", "Dan" to "monk",
            "Eve" to "chef", "Fay" to "mayor", "Gus" to "butler", "Hal" to "virgin",
        )) {
            state = assign(state, name, id)
        }
        state = night(state)
        val cleo = seat(state, "Cleo")
        val ben = seat(state, "Ben")

        // The Poisoner chose Cleo — a mark that outlives the dawn sweep.
        state = Effects.place(
            state = state,
            target = cleo,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = ben,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        state = Ledger.choice(state, "poisoner", ben, listOf(cleo))
        state = Ledger.told(state, cleo, "empath", shown = "1", impaired = true)

        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        val notes = dawn.items.map { it.text }

        assertTrue(
            notes.any { "Cleo" in it && "1" in it },
            "the card shown to Cleo is in the notes: ${'$'}notes",
        )
        assertTrue(
            notes.any { "Ben" in it && "Cleo" in it },
            "the Poisoner's choice is in the notes: ${'$'}notes",
        )
        assertTrue(
            notes.any { "Cleo" in it && "does not lift at dawn" in it },
            "a poisoning that runs to dusk is still standing at dawn: ${'$'}notes",
        )
        assertTrue(
            dawn.items.any { it.kind == BriefingKind.PRIVATE },
            "and none of it is said out loud",
        )
    }

    // ==================================================================
    // The user's Professor request, end to end
    // ==================================================================

    /**
     * A twelve-seat Bad Moon Rising table. Bo is the seat the Professor brings
     * back; the Grandmother is the ability whose FIRST night has to run again.
     */
    private fun professorTable(): GameState {
        var state = newGame(
            bmr,
            listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal", "Ivy", "Jo", "Kit", "Lex"),
        )
        state = assign(state, "Ana", "professor")
        state = assign(state, "Bo", "grandmother")
        state = assign(state, "Cai", "devilsadvocate")
        state = assign(state, "Dee", "gossip")
        state = assign(state, "Eve", "sailor")
        state = assign(state, "Fay", "chambermaid")
        state = assign(state, "Gus", "exorcist")
        state = assign(state, "Hal", "fool")
        state = assign(state, "Ivy", "tealady")
        state = assign(state, "Jo", "goon")
        state = assign(state, "Kit", "godfather")
        state = assign(state, "Lex", "pukka")
        return state
    }

    @Test
    fun `resurrecting queues an ANNOUNCE entry and a first-night re-run`() {
        var state = day(professorTable())
        val bo = seat(state, "Bo")

        state = Deaths.attempt(state, lookup, bo, KillCause(DeathCause.EXECUTION)).state
        assertFalse(assertNotNull(state.player(bo)).alive)

        state = Phases.advancePhase(state, lookup) // night 2
        state = Deaths.resurrect(state, lookup, bo)

        assertTrue(assertNotNull(state.player(bo)).alive, "a dead player comes back")

        // (a) the sentence the storyteller now owes the table…
        val owed = assertNotNull(
            Memory.pendingAnnouncements(state).find { it.actorId == bo },
            "an ANNOUNCE ledger entry: ${state.ledger}",
        )
        assertEquals(LedgerKind.ANNOUNCE, owed.kind)
        assertTrue(owed.announcePending)
        assertEquals("Bo is alive again.", owed.text)

        // (b) …and the obligation to run their first night, tonight.
        val rerun = assertNotNull(
            Prompts.forTonight(state).find {
                it.kind == PromptKind.RUN_FIRST_NIGHT && it.subjectPlayerId == bo
            },
            "a RUN_FIRST_NIGHT prompt: ${state.prompts}",
        )
        assertEquals("grandmother", rerun.stepSlotId)
    }

    @Test
    fun `the dawn card announces the resurrection after the deaths and without a reason`() {
        var state = day(professorTable())
        val bo = seat(state, "Bo")
        val dee = seat(state, "Dee")

        state = Deaths.attempt(state, lookup, bo, KillCause(DeathCause.EXECUTION)).state
        state = Phases.advancePhase(state, lookup) // night 2

        // Someone dies tonight, and Bo comes back the same night.
        state = Deaths.attempt(
            state,
            lookup,
            dee,
            KillCause(DeathCause.DEMON_KILL, sourceCharacterId = "pukka"),
        ).state
        state = Deaths.resurrect(state, lookup, bo)

        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        val announce = texts(dawn, BriefingKind.ANNOUNCE)

        val deathLine = announce.indexOf("Announce: Dee died.")
        val backLine = announce.indexOf("Announce: Bo is alive again.")
        assertTrue(deathLine >= 0, "the death is announced: $announce")
        assertTrue(backLine >= 0, "the resurrection is announced: $announce")
        assertTrue(backLine > deathLine, "resurrections come AFTER the deaths: $announce")

        // "Do not say why" — the line itself must carry no reason (lead D7).
        val line = assertNotNull(dawn.announce.find { Briefings.ALIVE_AGAIN in it.text })
        assertEquals("Announce: Bo is alive again.", line.text)
        for (word in listOf("Professor", "professor", "because", "resurrect")) {
            assertFalse(word in line.text, "the announcement must not say why: ${line.text}")
        }
        assertTrue(
            dawn.private.any { "Do not say why" in it.text && it.playerId == bo },
            "the reason is a storyteller-only note: ${texts(dawn, BriefingKind.PRIVATE)}",
        )
        // Saying it ticks the ledger entry off, so the item carries its id.
        assertNotNull(line.ledgerId, "the announce line links its ledger entry")
        assertTrue(line.actionId.startsWith(Briefings.ACTION_MARK_ANNOUNCED))
    }

    @Test
    fun `tonight's sheet grows the resurrected seat's first-night step`() {
        var state = day(professorTable())
        val bo = seat(state, "Bo")

        state = Deaths.attempt(state, lookup, bo, KillCause(DeathCause.EXECUTION)).state
        state = Phases.advancePhase(state, lookup) // night 2
        state = Deaths.resurrect(state, lookup, bo)

        val plan = NightPlan.build(state, lookup)
        val inserted = assertNotNull(
            plan.steps.find {
                it.key.abilityId == "grandmother" && it.key.variant == StepVariant.FIRST
            },
            "the Grandmother's FIRST-night step is inserted: ${plan.steps.map { it.key.token }}",
        )
        assertEquals(WakeStyle.FIRST_NIGHT, inserted.style)
        assertEquals(bo, inserted.holderId)

        // And the dawn card still nags for it while the prompt is unresolved.
        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertTrue(
            dawn.todo.any { it.actionId == "${Briefings.ACTION_RERUN_FIRST_NIGHT}$bo" },
            "dawn owes the re-run: ${texts(dawn, BriefingKind.TODO_ASK)}",
        )
    }

    // ==================================================================
    // The Zombuul's first death
    // ==================================================================

    @Test
    fun `a Zombuul's first death is announced as a real death and is private a lie`() {
        var state = newGame(bmr, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus"))
        state = assign(state, "Ana", "zombuul")
        state = assign(state, "Bo", "sailor")
        state = assign(state, "Cai", "chambermaid")
        state = assign(state, "Dee", "gossip")
        state = assign(state, "Eve", "exorcist")
        state = assign(state, "Fay", "fool")
        state = assign(state, "Gus", "goon")
        state = night(state)
        val ana = seat(state, "Ana")

        val attempt = Deaths.attempt(
            state,
            lookup,
            ana,
            KillCause(DeathCause.GOOD_ABILITY, sourceCharacterId = "gossip"),
        )
        assertTrue(attempt.outcome is KillOutcome.RegistersDead, "${attempt.outcome}")
        state = attempt.state
        assertTrue(state.isTrulyAlive(ana), "the Zombuul is alive by the rules (lead D6)")

        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertTrue(
            "Announce: Ana died." in texts(dawn, BriefingKind.ANNOUNCE),
            "a registered-only death is announced as a REAL death: " +
                texts(dawn, BriefingKind.ANNOUNCE),
        )
        val secret = assertNotNull(
            dawn.private.find { it.playerId == ana },
            "and the truth is storyteller-only: ${texts(dawn, BriefingKind.PRIVATE)}",
        )
        assertTrue("secretly alive" in secret.text, secret.text)
        assertEquals(BriefingSeverity.ALERT, secret.severity)
    }

    @Test
    fun `dusk warns that the Zombuul kills tonight when nobody died today`() {
        var state = newGame(bmr, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus"))
        state = assign(state, "Ana", "zombuul")
        state = assign(state, "Bo", "sailor")
        state = assign(state, "Cai", "chambermaid")
        state = assign(state, "Dee", "gossip")
        state = assign(state, "Eve", "exorcist")
        state = assign(state, "Fay", "fool")
        state = assign(state, "Gus", "goon")
        state = day(state)

        val dusk = Briefings.at(state, lookup, BriefingSlot.DUSK)
        val item = assertNotNull(
            dusk.items.find { "Nobody died today — the Zombuul kills tonight." == it.text },
            "the dusk sheet must say it: ${dusk.items.map { it.text }}",
        )
        // `goodWins == null`: an advisory that is a BRIEFING, not an ending.
        assertEquals(BriefingKind.STANDING_FACT, item.kind)
        assertEquals(BriefingSeverity.INFO, item.severity)
        // The dismissal key is the rule id, never the prose (lead D21).
        assertEquals(WinCheck.RULE_ZOMBUUL_NIGHT, item.sourceId)
        assertTrue(item.key.endsWith(WinCheck.RULE_ZOMBUUL_NIGHT), item.key)
    }

    // ==================================================================
    // DAY_START
    // ==================================================================

    /** A Sects & Violets table with a Cerenovus, plus a Gossip and a Devil's Advocate. */
    private fun dayStartTable(): GameState {
        var state = newGame(
            sv,
            listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"),
        )
        state = assign(state, "Ana", "cerenovus")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "gossip")
        state = assign(state, "Dee", "devilsadvocate")
        state = assign(state, "Eve", "clockmaker")
        state = assign(state, "Fay", "dreamer")
        state = assign(state, "Gus", "seamstress")
        state = assign(state, "Hal", "vortox")
        return state
    }

    @Test
    fun `day start carries the protection, the madness and the collect list`() {
        var state = day(dayStartTable())
        val bo = seat(state, "Bo")
        val cai = seat(state, "Cai")
        val eve = seat(state, "Eve")

        // The Devil's Advocate kept Eve alive through today's execution…
        state = Effects.place(
            state = state,
            target = eve,
            kind = EffectKind.SURVIVES_EXECUTION,
            sourceCharacterId = "devilsadvocate",
            sourcePlayerId = seat(state, "Dee"),
            until = Until.DUSK,
            label = "Survives Execution",
        ).state
        // …and the Cerenovus made Bo mad that they are the Chef.
        state = Effects.place(
            state = state,
            target = bo,
            kind = EffectKind.MAD,
            sourceCharacterId = "cerenovus",
            sourcePlayerId = seat(state, "Ana"),
            until = Until.DUSK,
            label = "Mad",
            characterId = "chef",
        ).state

        val briefing = Briefings.at(state, lookup, BriefingSlot.DAY_START)

        val protection = assertNotNull(
            briefing.standing.find { it.playerId == eve && it.sourceId == "devilsadvocate" },
            "the standing protection: ${texts(briefing, BriefingKind.STANDING_FACT)}",
        )
        assertTrue("survives execution today" in protection.text, protection.text)

        // Madness names the character they are mad about (§2.12).
        val madness = assertNotNull(
            briefing.private.find { it.playerId == bo && it.sourceId == "cerenovus" },
            "the madness: ${texts(briefing, BriefingKind.PRIVATE)}",
        )
        assertTrue("Chef" in madness.text, "madness must name the character: ${madness.text}")
        assertTrue("Cerenovus" in madness.text, madness.text)
        assertEquals(BriefingSeverity.ALERT, madness.severity)

        // One TODO_ASK per Gossip statement the day still owes.
        val collect = briefing.todo.filter { it.sourceId == "gossip" }
        assertEquals(1, collect.size, "one row per unrecorded statement: $collect")
        assertEquals(cai, collect.single().playerId)
        assertEquals("${Briefings.ACTION_RECORD}gossip", collect.single().actionId)

        // Record it, and the row is gone.
        state = Ledger.statement(
            state = state,
            speakerId = cai,
            sourceId = "gossip",
            text = "There is no Minion sitting next to me.",
        )
        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.DAY_START).todo.none { it.sourceId == "gossip" },
            "a recorded statement retires the row",
        )
    }

    @Test
    fun `two Gossips owe two rows and one recorded statement retires only its own`() {
        var state = newGame(sv, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "gossip")
        state = assign(state, "Bo", "gossip")
        state = assign(state, "Cai", "empath")
        state = assign(state, "Dee", "clockmaker")
        state = assign(state, "Eve", "dreamer")
        state = assign(state, "Fay", "seamstress")
        state = assign(state, "Gus", "cerenovus")
        state = assign(state, "Hal", "vortox")
        state = day(state)
        val ana = seat(state, "Ana")
        val bo = seat(state, "Bo")

        assertEquals(
            2,
            Briefings.at(state, lookup, BriefingSlot.DAY_START).todo.count { it.sourceId == "gossip" },
        )

        state = Ledger.statement(state, speakerId = ana, sourceId = "gossip", text = "No Minion.")
        val left = Briefings.at(state, lookup, BriefingSlot.DAY_START).todo
            .filter { it.sourceId == "gossip" }
        assertEquals(1, left.size)
        assertEquals(bo, left.single().playerId, "the OTHER Gossip still owes theirs")
    }

    @Test
    fun `an alive sober Vortox demands an execution and dusk says so`() {
        val state = day(dayStartTable())

        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.DAY_START).standing.any {
                it.sourceId == "vortox" && "must be executed" in it.text
            },
        )
        val dusk = Briefings.at(state, lookup, BriefingSlot.DUSK)
        val vortox = assertNotNull(
            dusk.items.find { it.sourceId == WinCheck.RULE_VORTOX_DUSK },
            "the blocking dusk advisory: ${dusk.items.map { it.text }}",
        )
        // Blocking: the storyteller must answer it before the night begins.
        assertEquals(BriefingKind.TODO_ASK, vortox.kind)
        assertEquals(BriefingSeverity.ALERT, vortox.severity)
    }

    // ==================================================================
    // Ordering: the dawn briefing is computed BEFORE the sweep
    // ==================================================================

    @Test
    fun `the dawn briefing sees the Monk token that the sweep is about to take`() {
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "monk")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "chef")
        state = assign(state, "Dee", "washerwoman")
        state = assign(state, "Eve", "librarian")
        state = assign(state, "Fay", "investigator")
        state = assign(state, "Gus", "butler")
        state = assign(state, "Hal", "imp")
        state = night(night(state)) // day 1
        state = Phases.advancePhase(state, lookup) // night 2
        val ana = seat(state, "Ana")
        val bo = seat(state, "Bo")

        state = Effects.place(
            state = state,
            target = bo,
            kind = EffectKind.SAFE_FROM_DEMON,
            sourceCharacterId = "monk",
            sourcePlayerId = ana,
            until = Until.DAWN,
            label = "Safe",
        ).state

        // The Imp attacks the protected seat: nothing happens, and the reason is
        // a fact the table must NOT hear.
        val attempt = Deaths.attempt(
            state,
            lookup,
            bo,
            KillCause(DeathCause.DEMON_KILL, sourceCharacterId = "imp", sourcePlayerId = seat(state, "Hal")),
        )
        assertTrue(attempt.outcome is KillOutcome.Prevented, "${attempt.outcome}")
        state = attempt.state

        val advanced = Phases.advancePhase(state, lookup)
        val dawn = assertNotNull(advanced.lastDawn, "advancePhase freezes the dawn briefing")

        // The token is gone from the grimoire…
        assertTrue(
            advanced.effects.none { it.targetId == bo && it.kind == EffectKind.SAFE_FROM_DEMON },
            "the Monk's token is swept at dawn",
        )
        // …but the briefing computed before the sweep still saw it.
        assertTrue(
            dawn.of(BriefingKind.SWEPT).any { it.playerId == bo && "Safe" in it.text },
            "the sweep list needs the token that is about to go: " +
                dawn.of(BriefingKind.SWEPT).map { it.text },
        )
        assertTrue(
            dawn.private.any { it.playerId == bo },
            "and the silent save is a storyteller-only line: ${texts(dawn, BriefingKind.PRIVATE)}",
        )
        assertTrue(
            dawn.announce.any { it.text == "Announce: nobody died." },
            "nobody died: ${texts(dawn, BriefingKind.ANNOUNCE)}",
        )
    }

    @Test
    fun `advancePhase stores lastDawn and lastDusk and the state round-trips through Json`() {
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "monk")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "chef")
        state = assign(state, "Dee", "washerwoman")
        state = assign(state, "Eve", "librarian")
        state = assign(state, "Fay", "investigator")
        state = assign(state, "Gus", "butler")
        state = assign(state, "Hal", "imp")

        state = Phases.advancePhase(state, lookup) // night 1
        assertNull(state.lastDawn, "SETUP -> NIGHT computes no dawn")

        state = Phases.advancePhase(state, lookup) // day 1
        val dawn = assertNotNull(state.lastDawn, "NIGHT -> DAY stores the dawn briefing")
        assertEquals(BriefingSlot.DAWN, dawn.slot)
        assertEquals(1, dawn.cycle)

        state = Phases.advancePhase(state, lookup) // night 2
        val dusk = assertNotNull(state.lastDusk, "DAY -> NIGHT stores the dusk briefing")
        assertEquals(BriefingSlot.DUSK, dusk.slot)
        assertEquals(1, dusk.cycle, "the dusk of day 1, taken before the cycle advanced")
        assertEquals(2, state.cycle)

        // The whole state, briefings included, survives a save/load round trip.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val decoded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
        assertEquals(state.lastDawn, decoded.lastDawn)
        assertEquals(state.lastDusk, decoded.lastDusk)
        assertEquals(state, decoded)
    }

    // ==================================================================
    // NOMINATION and EXECUTION
    // ==================================================================

    @Test
    fun `the nomination slot carries the trigger list for the pending pair`() {
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "virgin")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "chef")
        state = assign(state, "Dee", "washerwoman")
        state = assign(state, "Eve", "librarian")
        state = assign(state, "Fay", "investigator")
        state = assign(state, "Gus", "butler")
        state = assign(state, "Hal", "imp")
        state = day(state)
        val ana = seat(state, "Ana")
        val bo = seat(state, "Bo")

        // The live pre-flight, as the pinned seat ring calls it on each tap.
        val preflight = Briefings.forNomination(state, lookup, nominatorId = bo, nomineeId = ana)
        assertEquals(BriefingSlot.NOMINATION, preflight.slot)
        val virgin = assertNotNull(
            preflight.items.find { it.sourceId == "virgin" },
            "the Virgin's first nomination: ${preflight.items.map { it.text }}",
        )
        assertEquals(BriefingKind.TODO_ASK, virgin.kind, "an AUTO_EXECUTION is not a warning")
        assertEquals(BriefingSeverity.ALERT, virgin.severity)
        assertEquals(
            DayRules.checkNomination(state, lookup, bo, ana).triggers.size,
            preflight.items.count { it.sourceId != Ledger.Sources.STORYTELLER },
            "every trigger reaches the card",
        )

        // Recording it consumes the Virgin — "the 1st time you are nominated"
        // is spent by the nomination itself — but the default pair of `at` is
        // the recorded one, and a standing trigger still fires from it.
        state = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = bo, nomineeId = ana, votes = 0),
        )
        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.NOMINATION).items
                .none { it.sourceId == "virgin" },
            "the Virgin's first nomination has happened",
        )
    }

    @Test
    fun `the nomination slot defaults to the pair recorded today`() {
        var state = newGame(sv, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "witch")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "gossip")
        state = assign(state, "Dee", "clockmaker")
        state = assign(state, "Eve", "dreamer")
        state = assign(state, "Fay", "seamstress")
        state = assign(state, "Gus", "seamstress")
        state = assign(state, "Hal", "vortox")
        state = day(state)
        val bo = seat(state, "Bo")
        val cai = seat(state, "Cai")

        // The Witch cursed Bo, and Bo nominates anyway.
        state = Effects.addReminder(state, bo, PlacedReminder("witch", "Cursed"))
        state = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = bo, nomineeId = cai, votes = 0),
        )

        val briefing = Briefings.at(state, lookup, BriefingSlot.NOMINATION)
        val witch = assertNotNull(
            briefing.items.find { it.sourceId == "witch" },
            "the recorded pair is the default: ${briefing.items.map { it.text }}",
        )
        assertEquals(bo, witch.playerId, "the cursed nominator dies")
        assertEquals(BriefingSeverity.ALERT, witch.severity)
    }

    @Test
    fun `the execution slot carries the consequences of today's execution`() {
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay", "Gus", "Hal"))
        state = assign(state, "Ana", "saint")
        state = assign(state, "Bo", "empath")
        state = assign(state, "Cai", "chef")
        state = assign(state, "Dee", "undertaker")
        state = assign(state, "Eve", "librarian")
        state = assign(state, "Fay", "investigator")
        state = assign(state, "Gus", "butler")
        state = assign(state, "Hal", "imp")
        state = day(state)
        val ana = seat(state, "Ana")

        state = Execution.execute(state, lookup, ana)
        val briefing = Briefings.at(state, lookup, BriefingSlot.EXECUTION)
        val saint = assertNotNull(
            briefing.items.find { it.sourceId == "saint" },
            "the Saint's consequence: ${briefing.items.map { it.text }}",
        )
        assertEquals(BriefingSeverity.ALERT, saint.severity, "EVIL WINS is an alert")
        assertTrue(
            briefing.items.any { it.sourceId == "undertaker" },
            "and the Undertaker learns them tonight: ${briefing.items.map { it.text }}",
        )
    }

    // ==================================================================
    // Shape invariants every slot must hold
    // ==================================================================

    @Test
    fun `every slot returns its own stamped briefing with unique keys and no blank text`() {
        var state = day(professorTable())
        val bo = seat(state, "Bo")
        state = Deaths.attempt(state, lookup, bo, KillCause(DeathCause.EXECUTION)).state

        for (slot in BriefingSlot.entries) {
            val briefing = Briefings.at(state, lookup, slot)
            assertEquals(slot, briefing.slot)
            assertEquals(state.cycle, briefing.cycle)
            assertEquals(
                briefing.items.size,
                briefing.items.map { it.key }.distinct().size,
                "$slot has duplicate keys: ${briefing.items.map { it.key }}",
            )
            assertTrue(
                briefing.items.none { it.text.isBlank() },
                "$slot has a blank line: ${briefing.items}",
            )
        }
    }

    @Test
    fun `a briefing on an empty game says nobody died and asks for nothing`() {
        val state = night(newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve")))
        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertEquals(listOf("Announce: nobody died."), texts(dawn, BriefingKind.ANNOUNCE))
        assertTrue(dawn.todo.isEmpty(), "${texts(dawn, BriefingKind.TODO_ASK)}")
    }

    @Test
    fun `a pending announcement stays on the card until it is said`() {
        var state = day(professorTable())
        state = Ledger.announce(state, text = "Fay is exiled.", actorId = seat(state, "Fay"))
        val entry = Memory.pendingAnnouncements(state).single()

        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.DAY_START).announce
                .any { it.ledgerId == entry.id },
        )
        state = Ledger.markAnnounced(state, entry.id)
        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.DAY_START).announce
                .none { it.ledgerId == entry.id },
            "once said, it is off the card",
        )
    }

    // ==================================================================
    // W7G — `CharacterRule.day.briefing` has a consumer
    // ==================================================================

    @Test
    fun `a Fabled's reference note reaches the day-start card`() {
        // The Djinn is pure prose: no token, no hook, one STANDING_FACT line.
        // Before wave 7 `day.briefing` had no consumer at all, so every Fabled
        // reference note was written and never shown.
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve"))
        for ((name, id) in listOf(
            "Ana" to "imp", "Bo" to "poisoner", "Cai" to "chef",
            "Dee" to "empath", "Eve" to "mayor",
        )) {
            state = assign(state, name, id)
        }
        state = GameActions.setFabled(state, listOf("djinn"))
        val withDjinn = day(state)
        assertTrue(
            Briefings.at(withDjinn, lookup, BriefingSlot.DAY_START)
                .of(BriefingKind.STANDING_FACT)
                .any { it.sourceId == "djinn" },
            "the Djinn's rule must be on the day card",
        )

        // Out of play, nothing.
        val without = day(GameActions.setFabled(state, emptyList()))
        assertTrue(
            Briefings.at(without, lookup, BriefingSlot.DAY_START)
                .of(BriefingKind.STANDING_FACT)
                .none { it.sourceId == "djinn" },
        )
    }

    @Test
    fun `a seated character's day briefing reaches the day-start card`() {
        var state = newGame(tb, listOf("Ana", "Bo", "Cai", "Dee", "Eve", "Fay"))
        for ((name, id) in listOf(
            "Ana" to "imp", "Bo" to "poisoner", "Cai" to "chef",
            "Dee" to "empath", "Eve" to "mayor",
        )) {
            state = assign(state, name, id)
        }
        state = Seats.assignCharacter(state, seat(state, "Fay"), "beggar", isTraveller = true)
        val items = Briefings.at(day(state), lookup, BriefingSlot.DAY_START).items
        assertTrue(
            items.any { it.sourceId == "beggar" && "vote token" in it.text },
            "the Beggar's day-start line: ${items.map { it.sourceId }}",
        )
    }
}
