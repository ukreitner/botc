package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one append-only record (WP3, ARCHITECTURE §2.5; records-and-memory §A, §B).
 *
 * Two of these are the user's own complaints, tested directly:
 *  - "make it easy to write down all the gossips even if Gossip isn't in play",
 *  - the Devil's Advocate, whose "different to last night" was destroyed by the
 *    token sweep before it could be used.
 */
class LedgerTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }

    private fun newGame(script: Script = tb, players: Int = 8): GameState =
        GameActions.newGame(script, (1..players).map { "P$it" })

    private fun day1(state: GameState): GameState =
        GameActions.advancePhase(GameActions.advancePhase(state))

    // ------------------------------------------------------------------
    // The Gossip complaint: a statement in a game with nothing in play
    // ------------------------------------------------------------------

    @Test
    fun `a statement is recordable in a plain eight-player game with no Gossip`() {
        var state = day1(newGame())
        assertEquals(Phase.DAY, state.phase)

        state = Ledger.statement(
            state = state,
            speakerId = 2,
            sourceId = "gossip",
            text = "There is no Minion sitting next to me.",
        )

        val recorded = Memory.statementsOn(state, day = state.cycle)
        assertEquals(1, recorded.size)
        val entry = recorded.single()
        assertEquals(1L, entry.id, "the first entry takes id 1")
        assertEquals(state.cycle, entry.cycle, "the entry is stamped with the current day")
        assertFalse(entry.atNight)
        assertEquals(2L, entry.actorId)
        assertEquals(LedgerKind.STATEMENT, entry.kind)
        assertEquals(Verdict.UNJUDGED, entry.verdict, "the storyteller judges it later, not now")
        assertEquals(2L, state.nextLedgerId)

        // Nobody in this game is the Gossip. The record exists anyway.
        assertTrue(state.players.none { it.characterId == "gossip" })
    }

    @Test
    fun `a bare claim needs nothing but a speaker and a sentence`() {
        var state = day1(newGame())
        state = Ledger.statement(state, speakerId = 5, sourceId = "", text = "P3 is the Imp.")
        val entry = Memory.statementsOn(state, state.cycle).single()
        assertEquals(Ledger.Sources.CLAIM, entry.sourceId, "an empty source is a plain claim")
        assertEquals("P3 is the Imp.", entry.text)
        assertTrue(entry.targetIds.isEmpty())
        assertTrue(entry.characterIds.isEmpty())
    }

    @Test
    fun `statements are queryable by day, source and speaker, and judged later`() {
        var state = day1(newGame())
        state = Ledger.statement(state, 2, "gossip", "Day 1 statement.")
        state = Ledger.statement(state, 3, Ledger.Sources.CLAIM, "I am the Chef.")
        // Night 2, then day 2.
        state = day1(state)
        state = Ledger.statement(state, 2, "gossip", "Day 2 statement.")

        assertEquals(2, Memory.statementsOn(state, day = 1).size)
        assertEquals(1, Memory.statementsOn(state, day = 2).size)
        assertEquals(1, Memory.statementsOn(state, day = 1, sourceId = "gossip").size)
        assertEquals(1, Memory.statementsOn(state, day = 1, speakerId = 2).size)
        assertEquals(1, Memory.statementsOn(state, day = 1, speakerId = 3).size)

        val day2 = Memory.statementsOn(state, day = 2, sourceId = "gossip").single()
        state = Ledger.setVerdict(state, day2.id, Verdict.TRUE)
        assertEquals(
            Verdict.TRUE,
            Memory.statementsOn(state, day = 2, sourceId = "gossip").single().verdict,
        )

        // Resolving retires it from the unresolved queue but keeps it in the log.
        assertEquals(1, Memory.unresolved(state, "gossip", day = 2).size)
        state = Ledger.resolve(state, day2.id)
        assertTrue(Memory.unresolved(state, "gossip", day = 2).isEmpty())
        assertEquals(state.cycle, state.ledger.first { it.id == day2.id }.resolvedCycle)
        assertEquals(3, Memory.statementsOn(state, 1).size + Memory.statementsOn(state, 2).size)
    }

    @Test
    fun `a bluffed statement is recorded and changes nothing`() {
        var state = day1(newGame())
        state = Ledger.statement(
            state,
            speakerId = 4,
            sourceId = "slayer",
            text = "I shoot P1.",
            targetIds = listOf(1L),
            genuine = false,
        )
        val entry = Memory.statementsOn(state, state.cycle).single()
        assertFalse(entry.genuine, "the app does not believe the speaker holds the Slayer")
        assertTrue(assertNotNull(state.player(1)).alive)
        assertTrue(state.players.all { it.reminders.isEmpty() })
        assertTrue(state.deaths.isEmpty())
    }

    // ------------------------------------------------------------------
    // The Devil's Advocate complaint: choices outlive tokens
    // ------------------------------------------------------------------

    @Test
    fun `the Devil's Advocate choice survives two advancePhase calls and the token sweep`() {
        var state = newGame(bmr, 8)
        state = GameActions.assignCharacter(state, 0, "devilsadvocate")
        state = GameActions.advancePhase(state) // night 1
        assertEquals(Phase.NIGHT, state.phase)

        // The storyteller runs the step: the token goes down, the choice is logged.
        state = Effects.place(
            state = state,
            target = 3L,
            kind = EffectKind.SURVIVES_EXECUTION,
            sourceCharacterId = "devilsadvocate",
            sourcePlayerId = 0L,
            until = Until.DUSK,
            label = "Survives Execution",
        ).state
        state = Ledger.choice(state, "devilsadvocate", actorId = 0L, targetIds = listOf(3L))

        state = GameActions.advancePhase(state) // day 1
        state = GameActions.advancePhase(state) // night 2
        assertEquals(2, state.cycle)

        // The token is gone…
        assertTrue(
            Status.live(state, data::character, 3L, EffectKind.SURVIVES_EXECUTION).isEmpty(),
            "Survives Execution expires at dusk",
        )
        // …and the memory is not.
        val remembered = assertNotNull(
            Memory.lastChoice(state, "devilsadvocate", holderId = 0L),
            "the ledger remembers the choice after the token expired",
        )
        assertEquals(listOf(3L), remembered.targetIds)
        assertEquals(1, remembered.cycle)
        assertEquals(setOf(3L), Memory.forbiddenTargets(state, "devilsadvocate", 0L))
    }

    @Test
    fun `forbiddenTargets slides forward exactly one night`() {
        var state = newGame(bmr, 8)
        state = GameActions.assignCharacter(state, 0, "devilsadvocate")
        state = GameActions.advancePhase(state) // night 1
        state = Ledger.choice(state, "devilsadvocate", 0L, listOf(3L))

        state = day1(state) // day 1, night 2
        assertEquals(setOf(3L), Memory.forbiddenTargets(state, "devilsadvocate", 0L))

        // Tonight's own choice does not forbid itself — "strictly before".
        state = Ledger.choice(state, "devilsadvocate", 0L, listOf(4L))
        assertEquals(
            setOf(3L),
            Memory.forbiddenTargets(state, "devilsadvocate", 0L),
            "night 2 is still constrained by night 1, not by its own pick",
        )

        state = day1(state) // day 2, night 3
        assertEquals(
            setOf(4L),
            Memory.forbiddenTargets(state, "devilsadvocate", 0L),
            "exactly one night forward: night 1's target is free again",
        )
        assertEquals(setOf(3L, 4L), Memory.everChosen(state, "devilsadvocate", 0L))
    }

    @Test
    fun `lastChoice is per holder so two Village Idiots do not collide`() {
        var state = GameActions.advancePhase(newGame())
        state = Ledger.choice(state, "villageidiot", actorId = 1L, targetIds = listOf(5L))
        state = Ledger.choice(state, "villageidiot", actorId = 2L, targetIds = listOf(6L))
        state = day1(state) // day 1, night 2

        assertEquals(listOf(5L), assertNotNull(Memory.lastChoice(state, "villageidiot", 1L)).targetIds)
        assertEquals(listOf(6L), assertNotNull(Memory.lastChoice(state, "villageidiot", 2L)).targetIds)
        // Without a holder the query is the whole character's history.
        assertEquals(listOf(6L), assertNotNull(Memory.lastChoice(state, "villageidiot")).targetIds)
        assertEquals(setOf(5L), Memory.forbiddenTargets(state, "villageidiot", 1L))
    }

    @Test
    fun `choseNobodyLastNight only fires when the step actually ran`() {
        var state = GameActions.advancePhase(newGame())
        state = Ledger.choice(state, "po", actorId = 0L, targetIds = emptyList())
        state = day1(state) // day 1, night 2
        assertTrue(Memory.choseNobodyLastNight(state, "po", 0L))

        var never = GameActions.advancePhase(newGame())
        never = day1(never)
        assertFalse(
            Memory.choseNobodyLastNight(never, "po", 0L),
            "a night the Po never woke is not a 'chose nobody' night",
        )
    }

    // ------------------------------------------------------------------
    // The other writers and queries
    // ------------------------------------------------------------------

    @Test
    fun `announcements queue, clear, and stay in the log`() {
        var state = GameActions.advancePhase(newGame())
        state = Ledger.announce(state, "The Banshee has awoken.")
        val pending = Memory.pendingAnnouncements(state)
        assertEquals(1, pending.size)
        assertTrue(pending.single().announcePending)

        state = Ledger.markAnnounced(state, pending.single().id)
        assertTrue(Memory.pendingAnnouncements(state).isEmpty())
        assertEquals(1, state.ledger.count { it.kind == LedgerKind.ANNOUNCE }, "it stays for the log")
    }

    @Test
    fun `spent is a ledger fact, not a token`() {
        var state = day1(newGame())
        assertFalse(Memory.isSpent(state, "slayer"))
        state = Ledger.spent(state, "slayer", actorId = 4L)
        assertTrue(Memory.isSpent(state, "slayer"))
        assertTrue(Memory.isSpent(state, "slayer", actorId = 4L))
        assertFalse(Memory.isSpent(state, "slayer", actorId = 5L))
        assertFalse(Memory.isSpent(state, "virgin"))
    }

    @Test
    fun `told, woke, private, ruling and malfunction all land on one list`() {
        var state = GameActions.advancePhase(newGame())
        state = Ledger.told(state, playerId = 1L, sourceId = "empath", shown = "0")
        state = Ledger.woke(state, playerId = 1L, sourceId = "empath", ownAbility = true)
        state = Ledger.woke(state, playerId = 2L, sourceId = "exorcist", ownAbility = false)
        state = Ledger.malfunction(state, 1L, "empath", "poisoned by the Poisoner")
        state = GameActions.advancePhase(state) // day 1
        state = Ledger.private(state, 3L, "artist", "Is P1 the Demon?", "no")
        state = Ledger.ruling(state, Ledger.Sources.MISREGISTER, 6L, "The Recluse registers as the Imp.")

        assertEquals(6, state.ledger.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), state.ledger.map { it.id })
        assertEquals(4, state.ledger.count { it.atNight })
        assertEquals(1, Memory.by(state, LedgerKind.WOKE, "empath").size)
        assertEquals(
            "The Recluse registers as the Imp.",
            assertNotNull(Memory.ruling(state, 6L, askedBy = "investigator")).text,
        )
        assertEquals(3, Memory.forPlayer(state, 1L).size, "everything about seat 1")
    }

    @Test
    fun `edit and delete are the only ways an entry changes`() {
        var state = day1(newGame())
        state = Ledger.note(state, "Everyone claimed Chef.")
        val id = state.ledger.single().id
        state = Ledger.edit(state, id) { it.copy(text = "Everyone claimed Chef, loudly.") }
        assertEquals("Everyone claimed Chef, loudly.", state.ledger.single().text)
        assertEquals(id, state.ledger.single().id, "an edit never renumbers")

        // An unknown id is a no-op, never a crash.
        assertEquals(state, Ledger.edit(state, 99L) { it.copy(text = "x") })
        assertEquals(state, Ledger.markAnnounced(state, 99L))

        state = Ledger.delete(state, id)
        assertTrue(state.ledger.isEmpty())
        assertEquals(2L, state.nextLedgerId, "ids are never reused")
    }

    @Test
    fun `impairment spans answer 'was this seat impaired during that window'`() {
        var state = GameActions.advancePhase(newGame())
        state = GameActions.assignCharacter(state, 1, "empath")
        state = GameActions.assignCharacter(state, 2, "poisoner")

        assertFalse(Memory.wasImpairedDuring(state, 1L, 1, 9))
        state = Effects.place(
            state = state,
            target = 1L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 2L,
            until = Until.DAWN,
            label = "Poisoned",
        ).state
        state = Effects.reconcile(state, data::character)
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.IMPAIRMENT_SPAN && it.actorId == 1L },
            "reconcile opened the span through Ledger",
        )
        assertTrue(Memory.wasImpairedDuring(state, 1L, 1, 1))

        state = GameActions.advancePhase(state) // day 1 — the poison expires at dawn
        assertTrue(Memory.wasImpairedDuring(state, 1L, 1, 1), "the window is still on record")
        assertFalse(Memory.wasImpairedDuring(state, 1L, 5, 9), "and it does not leak forward")
    }

    @Test
    fun `cyclesSince counts from the cycle a token was placed`() {
        var state = GameActions.advancePhase(newGame())
        assertNull(Memory.cyclesSince(state, 1L, "grandmother", "Grandchild"))
        state = GameActions.addReminder(
            state, 1L, PlacedReminder("grandmother", "Grandchild", placedCycle = state.cycle),
        )
        assertEquals(0, Memory.cyclesSince(state, 1L, "grandmother", "Grandchild"))
        state = GameActions.advancePhase(GameActions.advancePhase(state))
        assertEquals(1, Memory.cyclesSince(state, 1L, "grandmother", "Grandchild"))
        // Case never matters (lead D5).
        assertEquals(1, Memory.cyclesSince(state, 1L, "GRANDMOTHER", "grandchild"))
    }

    @Test
    fun `typesSeen reports the teams a seat has already been shown`() {
        var state = GameActions.advancePhase(newGame())
        state = GameActions.assignCharacter(state, 4, "chef")
        state = GameActions.assignCharacter(state, 5, "imp")
        state = Ledger.told(state, 1L, "balloonist", "P5", targetIds = listOf(4L))
        state = Ledger.told(state, 1L, "balloonist", "P6", targetIds = listOf(5L))
        assertEquals(listOf(Team.TOWNSFOLK, Team.DEMON), Memory.typesSeen(state, data::character, 1L))
        assertTrue(Memory.typesSeen(state, data::character, 2L).isEmpty())
    }

    @Test
    fun `a ledger survives round-trip serialisation and old saves still load`() {
        var state = day1(newGame())
        state = Ledger.statement(state, 2, "gossip", "A statement.")
        state = Execution.noExecution(state)
        state = DayRules.record(
            state,
            data::character,
            Nomination(day = state.cycle, nominatorId = 0, nomineeId = 3, votes = 4),
            force = true,
        )

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val text = json.encodeToString(GameState.serializer(), state)
        val back = json.decodeFromString(GameState.serializer(), text)
        assertEquals(state.ledger, back.ledger)
        assertEquals(state.executions, back.executions)
        assertEquals(state.nominations, back.nominations)

        // A save written before any of these fields existed still decodes.
        val legacy = """{"script":${json.encodeToString(Script.serializer(), tb)},"players":[]}"""
        val old = json.decodeFromString(GameState.serializer(), legacy)
        assertTrue(old.ledger.isEmpty())
        assertTrue(old.executions.isEmpty())
        assertEquals(1L, old.nextLedgerId)
    }
}
