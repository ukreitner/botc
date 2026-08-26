package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InfoCalcTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** 8 seats: 0 imp, 1 poisoner, 2 washerwoman, 3 empath, 4 chef, 5 recluse, 6 spy, 7 mayor. */
    private fun game(): GameState {
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E", "F", "G", "H"))
        listOf("imp", "poisoner", "washerwoman", "empath", "chef", "recluse", "spy", "mayor")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    @Test
    fun `chef counts adjacent evil pairs with misregistration caveats`() {
        // Evil seats: 0 (imp), 1 (poisoner), 6 (spy). Pairs: 0+1, and 7-0 wraps? 7 is mayor (good).
        // 6(spy evil)+7(mayor good) no; 5(recluse good)+6 no. So exactly 1 true pair.
        val result = assertNotNull(InfoCalc.compute(data, game(), "chef", holderId = 4))
        assertTrue(result.headline.startsWith("1 pair"), result.headline)
        assertTrue(result.caveats.any { "Spy" in it })
        assertTrue(result.caveats.any { "Recluse" in it })
    }

    @Test
    fun `empath counts evil alive neighbours and skips the dead`() {
        var state = game()
        // Empath at seat 3; neighbours 2 (washerwoman, good) and 4 (chef, good) -> 0.
        var result = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 3))
        assertTrue(result.headline.startsWith("0"), result.headline)
        // Kill seats 2 and 1: alive neighbours become 0 (imp, evil) and 4 -> 1.
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        state = GameActions.kill(state, 1, DeathCause.DEMON)
        result = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 3))
        assertTrue(result.headline.startsWith("1"), result.headline)
    }

    @Test
    fun `empath caveats flag poisoning`() {
        var state = game()
        state = GameActions.addReminder(state, 3, PlacedReminder("poisoner", "Poisoned"))
        val result = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 3))
        assertTrue(result.caveats.any { "POISONED" in it }, result.caveats.toString())
    }

    @Test
    fun `drunk character is flagged`() {
        var state = game()
        state = GameActions.assignCharacter(state, 3, "drunk")
        val result = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 3))
        assertTrue(result.caveats.any { "IS the Drunk" in it }, result.caveats.toString())
    }

    @Test
    fun `clockmaker measures demon to nearest minion`() {
        // Demon seat 0, poisoner seat 1 -> 1 step; spy seat 6 -> 2 steps (wrap). Nearest = 1.
        val result = assertNotNull(InfoCalc.compute(data, game(), "clockmaker", holderId = null))
        assertTrue(result.headline.startsWith("1 step"), result.headline)
    }

    @Test
    fun `shugenja reports nearest evil direction`() {
        // Holder empath seat 3: clockwise 4,5,6(spy evil)=3 steps; anti 2,1(poisoner)=2 steps.
        val result = assertNotNull(InfoCalc.compute(data, game(), "shugenja", holderId = 3))
        assertTrue("ANTI-CLOCKWISE" in result.headline, result.headline)
    }

    @Test
    fun `fortune teller sees demon or red herring`() {
        var state = game()
        var result = assertNotNull(InfoCalc.compute(data, state, "fortuneteller", 3, targets = listOf(0, 7)))
        assertEquals("YES", result.headline)
        result = assertNotNull(InfoCalc.compute(data, state, "fortuneteller", 3, targets = listOf(2, 7)))
        assertEquals("NO", result.headline)
        // Label comparisons are case-insensitive everywhere (lead D5), so the
        // caveat is matched case-insensitively too.
        assertTrue(
            result.caveats.any { "red herring" in it.lowercase() },
            "warns when no herring assigned",
        )
        state = GameActions.addReminder(state, 7, PlacedReminder("fortuneteller", "Red Herring"))
        result = assertNotNull(InfoCalc.compute(data, state, "fortuneteller", 3, targets = listOf(2, 7)))
        assertEquals("YES", result.headline)
    }

    @Test
    fun `undertaker names today's executee`() {
        var state = game()
        state = GameActions.advancePhase(state) // day 1
        state = GameActions.kill(state, 1, DeathCause.EXECUTION)
        state = GameActions.advancePhase(state) // night 2 — "today" = day 1
        val result = assertNotNull(InfoCalc.compute(data, state, "undertaker", holderId = null))
        assertTrue(result.headline.contains("Poisoner"), result.headline)
    }

    @Test
    fun `town crier and flowergirl read today's nominations`() {
        var state = game()
        state = GameActions.advancePhase(state) // day 1
        state = GameActions.recordNomination(
            state,
            Nomination(day = 1, nominatorId = 1, nomineeId = 7, votes = 4, voterIds = listOf(0, 1, 5, 6), result = NominationResult.ABOUT_TO_DIE),
        )
        val crier = assertNotNull(InfoCalc.compute(data, state, "towncrier", holderId = null))
        assertTrue(crier.headline.startsWith("YES"), crier.headline)
        val flower = assertNotNull(InfoCalc.compute(data, state, "flowergirl", holderId = null))
        assertTrue(flower.headline.startsWith("YES"), flower.headline)
    }

    @Test
    fun `vortox caveat appears for townsfolk info`() {
        var state = game()
        state = GameActions.assignCharacter(state, 0, "vortox")
        val result = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 3))
        assertTrue(result.caveats.any { "VORTOX" in it }, result.caveats.toString())
    }

    @Test
    fun `seamstress compares alignments including flips`() {
        var state = game()
        state = GameActions.flipAlignment(state, 7) // mayor turned evil
        val result = assertNotNull(InfoCalc.compute(data, state, "seamstress", 3, targets = listOf(0, 7)))
        assertTrue(result.headline.startsWith("YES"), result.headline)
    }

    @Test
    fun `two-target calculators reject missing stale duplicate and extra selections`() {
        val invalidSelections = listOf(
            listOf(0L),
            listOf(0L, 999L),
            listOf(0L, 0L),
            listOf(0L, 1L, 2L),
        )
        for (role in listOf("fortuneteller", "seamstress", "chambermaid")) {
            for (targets in invalidSelections) {
                val result = assertNotNull(InfoCalc.compute(data, game(), role, 3, targets))
                assertTrue(
                    result.headline.startsWith("Pick"),
                    "$role should reject $targets, got ${result.headline}",
                )
            }
        }
    }

    @Test
    fun `one-target calculators reject stale duplicate and extra selections`() {
        val invalidSelections = listOf(
            emptyList(),
            listOf(999L),
            listOf(0L, 0L),
            listOf(0L, 1L),
        )
        for (role in listOf("dreamer", "villageidiot", "ravenkeeper", "grandmother")) {
            for (targets in invalidSelections) {
                val result = assertNotNull(InfoCalc.compute(data, game(), role, 3, targets))
                assertTrue(
                    result.headline.startsWith("Pick"),
                    "$role should reject $targets, got ${result.headline}",
                )
            }
        }
    }
}
