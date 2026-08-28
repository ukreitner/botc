package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.Effects
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Script
import com.clocktower.engine.Seats
import com.clocktower.engine.Voting
import com.clocktower.grimoire.ui.screens.grimoireHeaderLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grimoire tab's header used to recompute the execution threshold from
 * `GameState.executionThreshold` and count ghost votes itself, so a sober
 * Voudon left it printing numbers the nomination panel disagreed with
 * (FOLLOWUPS, Voudon fix wave). It now reads [DayRules.voteRules], the same
 * snapshot the day stat strip reads — measured here because the header line is
 * a pure function under `app/`, which `:engine`'s tests cannot see.
 */
class GrimoireHeaderTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb: Script = data.builtInScripts().first { it.id == "tb" }

    private val table = listOf(
        "Ana" to "washerwoman",
        "Bo" to "librarian",
        "Cai" to "investigator",
        "Dee" to "chef",
        "Eve" to "empath",
        "Fay" to "fortuneteller",
        "Gus" to "undertaker",
        "Hal" to "monk",
        "Ivy" to "butler",
        "Jo" to "saint",
        "Kit" to "poisoner",
        "Lex" to "imp",
    )

    private fun seated(): GameState {
        var state = GameActions.newGame(tb, table.map { it.first })
        for ((name, characterId) in table) {
            state = Seats.assignCharacter(state, seat(state, name), characterId)
        }
        return state
    }

    /** Day 1, with the night run through so the phase is right. */
    private fun day(): GameState {
        val night = Phases.advancePhase(seated(), lookup)
        return Phases.advancePhase(night, lookup)
    }

    private fun seat(state: GameState, name: String): Long =
        state.players.first { it.name == name }.id

    @Test
    fun `an ordinary day header names the phase, the count and the threshold`() {
        var state = day()
        state = state.updatePlayer(seat(state, "Cai")) { it.copy(alive = false) }

        val ordinary = Voting.executionThreshold(state.aliveCountWithTravellers)
        val header = grimoireHeaderLine(state, lookup)
        assertTrue("the phase is named: '${header.facts}'", header.facts.startsWith("Day ${state.cycle}"))
        assertTrue("the alive count is there: '${header.facts}'", header.facts.contains("11 alive"))
        assertTrue("the engine's threshold: '${header.facts}'", header.facts.contains("$ordinary to execute"))
        assertTrue("Cai's ghost vote is counted: '${header.facts}'", header.facts.contains("1 ghost vote"))
        assertEquals("nothing rewrote the vote", "", header.voteNote)
    }

    @Test
    fun `the header reads the engine's vote rules under a sober Voudon`() {
        var state = day()
        // Ana takes a traveller seat as the Voudon; Cai is dead with a ghost
        // vote in hand — under a Voudon no ghost vote is ever spent.
        state = GameActions.assignCharacter(state, seat(state, "Ana"), "voudon", isTraveller = true)
        state = state.updatePlayer(seat(state, "Cai")) { it.copy(alive = false) }

        val rules = DayRules.voteRules(state, lookup, isExile = false)
        assertEquals("the engine says one vote is enough", 1, rules.threshold)
        assertFalse(rules.spendsGhostVotes)

        val header = grimoireHeaderLine(state, lookup)
        assertTrue("the header shows the engine's threshold: '${header.facts}'", header.facts.contains("1 to execute"))
        assertFalse(
            "no ghost-vote count when none is spent: '${header.facts}'",
            header.facts.contains("ghost"),
        )
        assertTrue("and it says why: '${header.voteNote}'", header.voteNote.contains("Voudon"))
        assertTrue("naming the one living voter: '${header.voteNote}'", header.voteNote.contains("Ana"))
    }

    @Test
    fun `a poisoned Voudon leaves the header on the ordinary threshold`() {
        var state = day()
        val ana = seat(state, "Ana")
        state = GameActions.assignCharacter(state, ana, "voudon", isTraveller = true)
        state = Effects.addReminder(state, ana, PlacedReminder("poisoner", "Poisoned", placedCycle = state.cycle))
        state = state.updatePlayer(seat(state, "Cai")) { it.copy(alive = false) }

        val ordinary = Voting.executionThreshold(state.aliveCountWithTravellers)
        assertEquals(
            "a poisoned Voudon changes nothing",
            ordinary,
            DayRules.voteRules(state, lookup, isExile = false).threshold,
        )

        val header = grimoireHeaderLine(state, lookup)
        assertTrue("the header agrees: '${header.facts}'", header.facts.contains("$ordinary to execute"))
        assertTrue("and the ghost vote is countable again: '${header.facts}'", header.facts.contains("ghost"))
        assertFalse("with no Voudon note: '${header.voteNote}'", header.voteNote.contains("Voudon"))
    }

    @Test
    fun `the night header still carries the numbers`() {
        val night = Phases.advancePhase(seated(), lookup)
        val header = grimoireHeaderLine(night, lookup)
        assertTrue("the night is named: '${header.facts}'", header.facts.startsWith("Night ${night.cycle}"))
        assertTrue("with the threshold: '${header.facts}'", header.facts.contains("to execute"))
    }
}
