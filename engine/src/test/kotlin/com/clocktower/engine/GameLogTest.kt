package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GameLog.rows` is the ONE transcript the log dialog renders (C-10, C-11).
 *
 * The dialog used to rebuild its own list from `deaths` + `nominations`, so a
 * recorded statement never appeared anywhere and an execution that killed
 * nobody left no trace at all — the record the Undertaker, the Mayor, the
 * Vortox and the Zombuul all hinge on. These tests pin what the log must carry
 * for the screen that shows it.
 */
class GameLogTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    private fun day1(players: Int = 8): GameState =
        GameActions.advancePhase(
            GameActions.advancePhase(GameActions.newGame(tb, (1..players).map { "P$it" })),
        )

    private fun texts(state: GameState) = GameLog.rows(state, lookup).map { it.text }

    @Test
    fun `a recorded statement reaches the log`() {
        var state = day1()
        state = Ledger.statement(
            state,
            speakerId = 2L,
            sourceId = Ledger.Sources.CLAIM,
            text = "P6 is the Imp",
            targetIds = listOf(5L),
        )
        val row = texts(state).single()
        assertTrue("P3 says" in row, row)
        assertTrue("P6 is the Imp" in row, row)
        assertTrue("about P6" in row, row)

        val entry = GameLog.rows(state, lookup).single()
        assertEquals(state.cycle, entry.cycle)
        assertTrue(!entry.atNight, "it was said in the day")
    }

    @Test
    fun `an execution that killed nobody is still in the log`() {
        var state = day1()
        state = Execution.execute(
            state,
            lookup,
            playerId = 5L,
            outcome = ExecutionOutcome.SURVIVED,
            preventedBy = "the storyteller",
        )
        assertTrue(state.deaths.isEmpty(), "nobody died — the old log had nothing to show")
        val row = texts(state).single { "executed" in it }
        assertTrue("P6 is executed and survives" in row, row)
    }

    @Test
    fun `a day closed with no execution says so`() {
        val state = Execution.noExecution(day1())
        assertTrue(state.deaths.isEmpty())
        assertEquals(listOf("No execution today."), texts(state))
    }

    @Test
    fun `the transcript is ordered by phase, with the ledger before the vote`() {
        var state = day1()
        state = Ledger.statement(state, 0L, Ledger.Sources.CLAIM, "I am the Chef")
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = 1L,
                nomineeId = 5L,
                voterIds = listOf(0L, 1L, 2L, 3L, 4L),
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
        state = Execution.execute(
            state,
            lookup,
            playerId = 5L,
            nominatorId = 1L,
            nominationIndex = state.nominations.size - 1,
        )
        val rows = GameLog.rows(state, lookup)
        assertEquals(
            listOf(true, true, true, true),
            rows.map { it.cycle == 1 && !it.atNight },
            "everything happened on day 1",
        )
        assertTrue("I am the Chef" in rows[0].text, rows[0].text)
        assertTrue("nominates" in rows[1].text, rows[1].text)
        assertTrue("executed" in rows[2].text, rows[2].text)
        assertTrue("dies" in rows[3].text, rows[3].text)
        // The voters are named — the log is the only place they are recorded.
        assertTrue("P1, P2, P3" in rows[1].text, rows[1].text)
    }

    @Test
    fun `announcements, rulings and notes all reach the log`() {
        var state = day1()
        state = Ledger.announce(state, "P4 died in the night.")
        state = Ledger.ruling(state, "recluse", playerId = 4L, text = "P5 registers as the Imp")
        state = Ledger.note(state, "claimed Chef, then Empath", playerId = 0L)
        val rows = texts(state)
        assertTrue(rows.any { "Announce:" in it && "died in the night" in it }, rows.toString())
        assertTrue(rows.any { it.startsWith("Ruling —") }, rows.toString())
        assertTrue(rows.any { "claimed Chef" in it }, rows.toString())
    }

    @Test
    fun `an empty game has an empty transcript`() {
        assertEquals(emptyList(), GameLog.rows(day1(), lookup))
    }
}
