package com.clocktower.grimoire.uicheck

import com.clocktower.engine.DayRules
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.Phase
import com.clocktower.engine.Seats
import com.clocktower.grimoire.ui.screens.day.DayStage
import com.clocktower.grimoire.ui.screens.expandedDayStage
import com.clocktower.grimoire.ui.screens.nightRowWindow
import com.clocktower.grimoire.ui.screens.nominationPairAfterTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseScreenStateTest {
    @Test
    fun `collapsing the automatic day stage stays collapsed until another stage is chosen`() {
        assertEquals(DayStage.DAWN, expandedDayStage(DayStage.DAWN, null, stageChosen = false))
        assertEquals(DayStage.BRIEFING, expandedDayStage(DayStage.BRIEFING, null, stageChosen = false))
        assertNull(expandedDayStage(DayStage.DAWN, null, stageChosen = true))
        assertNull(expandedDayStage(DayStage.BRIEFING, null, stageChosen = true))
        assertEquals(DayStage.NOMINATIONS, expandedDayStage(DayStage.DAWN, DayStage.NOMINATIONS, stageChosen = true))
        assertEquals(DayStage.SAID, expandedDayStage(DayStage.DAWN, DayStage.SAID, stageChosen = true))
    }

    @Test
    fun `two taps on one player produce a legal self nomination`() {
        val data = GameData.loadDefault()
        val script = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(script, listOf("Ana", "Bo", "Cai", "Dee", "Eve"))
        for ((player, role) in state.players.zip(listOf("chef", "empath", "monk", "poisoner", "imp"))) {
            state = Seats.assignCharacter(state, player.id, role)
        }
        state = state.copy(phase = Phase.DAY, cycle = 1)
        val ana = state.players.first().id
        val firstTap = nominationPairAfterTap(null, null, ana)
        val secondTap = nominationPairAfterTap(firstTap.first, firstTap.second, ana)

        assertEquals(ana to null, firstTap)
        assertEquals(ana to ana, secondTap)
        val check = DayRules.checkNomination(state, data::character, secondTap.first, secondTap.second)
        assertTrue("The ring must permit this legal nomination: ${check.blockers}", check.legal)
    }

    @Test
    fun `an existing pair can change either player without losing the other`() {
        val pair = nominationPairAfterTap(1, null, 2)
        assertEquals(1L to 2L, pair)
        val clearedNominator = nominationPairAfterTap(pair.first, pair.second, 1)
        assertEquals(null to 2L, clearedNominator)
        assertEquals(3L to 2L, nominationPairAfterTap(clearedNominator.first, clearedNominator.second, 3))
        assertEquals(1L to null, nominationPairAfterTap(pair.first, pair.second, 2))
        assertEquals(1L to 3L, nominationPairAfterTap(pair.first, pair.second, 3))
    }

    @Test
    fun `a late night card scrolls to its visible index instead of past the compact list`() {
        val window = nightRowWindow(rowCount = 15, activeIndex = 10, expanded = false, radius = 3)
        assertEquals(7..13, window)
        val visibleRows = (0 until 15).filter { it in window }
        val scrollIndex = 10 - window.first
        assertEquals(3, scrollIndex)
        assertEquals(10, visibleRows[scrollIndex])
        assertFalse("The old full-plan index is outside the displayed list", 10 in visibleRows.indices)
    }

    @Test
    fun `walking every night row keeps its window valid at both ends and when expanded`() {
        for (rowCount in 1..20) {
            for (activeIndex in 0 until rowCount) {
                for (expanded in listOf(false, true)) {
                    val window = nightRowWindow(rowCount, activeIndex, expanded, radius = 3)
                    assertTrue(activeIndex in window)
                    assertTrue(window.first >= 0)
                    assertTrue(window.last < rowCount)
                    val scrollIndex = activeIndex - window.first
                    assertEquals(activeIndex, window.elementAt(scrollIndex))
                    if (expanded) assertEquals(activeIndex, scrollIndex)
                }
            }
        }
        assertTrue(nightRowWindow(0, -1, expanded = false, radius = 3).isEmpty())
    }
}
