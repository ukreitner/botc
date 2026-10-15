package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Alignment
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Deaths
import com.clocktower.engine.Execution
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Phases
import com.clocktower.engine.Seats
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameActionsApi
import com.clocktower.grimoire.ui.screens.day.NominationModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravellerFlowTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private fun day(): GameState {
        val roles = listOf("imp", "poisoner", "chef", "monk", "zealot", "bishop")
        var state = Seats.newGame(data.builtInScripts().first { it.id == "tb" }, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { index, role ->
            state = Seats.assignCharacter(state, index.toLong(), role, lookup(role)?.team == Team.TRAVELLER)
        }
        return Phases.advancePhase(Phases.advancePhase(state, lookup), lookup)
    }

    private inner class Actions(var state: GameState) : GameActionsApi {
        override val gameData = data
        override fun characterById(id: String?): Character? = id?.let(data::character)
        override fun update(transform: (GameState) -> GameState) { state = transform(state) }
    }

    @Test
    fun `arrival requires a named Traveller character and records chosen alignment once`() {
        val actions = Actions(day())
        val initial = actions.state
        actions.joinTraveller("Guest", 1, null, evil = false)
        assertEquals(initial, actions.state)
        actions.joinTraveller("Guest", 1, "imp", evil = true)
        assertEquals(initial, actions.state)
        actions.joinTraveller(" ", 1, "beggar", evil = false)
        assertEquals(initial, actions.state)
        actions.joinTraveller("Guest", 1, "beggar", evil = true, announce = "Guest joins as the Beggar")
        val guest = actions.state.seats.single { it.name == "Guest" }
        assertEquals(Alignment.EVIL, guest.alignment)
        assertEquals("evil", actions.state.decisions[SetupRequirements.travellerAlignmentKey(guest.id)])
        assertTrue(SetupRequirements.unmet(actions.state, lookup).none { it.id == "traveller.alignment:${guest.id}" })
        assertEquals(guest.id, actions.state.seats[2].id)
        actions.leaveTraveller(guest.id)
        assertFalse(actions.state.seats.any { it.id == guest.id })
        assertEquals("Guest", actions.state.player(guest.id)?.name)
    }

    @Test
    fun `exile ring permits dead caller after execution while resident nomination stays blocked`() {
        var state = day()
        state = Deaths.kill(state, 2, DeathCause.STORYTELLER, lookup)
        state = Execution.execute(state, lookup, 3)
        val closed = DayRules.nominationsClosedReason(state, lookup)
        assertFalse(NominationModel.ringLocked(closed, reopened = false, exileAvailable = true))
        val callers = NominationModel.ring(state, lookup, null, null)
        assertTrue(callers.single { it.id == 2L }.allowed)
        val targets = NominationModel.ring(state, lookup, 2, null)
        assertTrue(targets.single { it.id == 5L }.allowed)
        assertFalse(targets.single { it.id == 4L }.allowed)
        assertTrue(DayRules.checkNomination(state, lookup, 2, 5).legal)
        assertFalse(DayRules.checkNomination(state, lookup, 2, 4).legal)
    }

    @Test
    fun `Zealot can abstain from exile while ordinary voting still requires their hand`() {
        val state = day()
        val exile = NominationModel.voteView(state, lookup, 2, 5, emptySet())
        assertTrue(exile.isExile)
        assertTrue(exile.mustVote.isEmpty())
        val execution = NominationModel.voteView(state, lookup, 2, 3, emptySet())
        assertEquals(listOf(4L), execution.mustVote)
    }
}
