package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusEffectsTest {

    private val data = GameData.loadDefault()
    private val sv = data.builtInScripts().first { it.id == "sv" }

    @Test
    fun `no dashii poisons nearest townsfolk neighbours skipping others`() {
        // Seats: 0 nodashii, 1 eviltwin (minion), 2 clockmaker (TF),
        //        3 dreamer (TF), 4 mutant (outsider), 5 sage (TF).
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E", "F"))
        listOf("nodashii", "eviltwin", "clockmaker", "dreamer", "mutant", "sage")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        val poisoned = StatusEffects.derivedPoison(state, data::character)
        // Clockwise from seat 0: skips evil twin, lands on clockmaker (2).
        // Anticlockwise: skips mutant (outsider), lands on sage (5).
        assertEquals(setOf(2L, 5L), poisoned.keys)
        assertTrue(StatusEffects.isImpaired(state, data::character, state.player(2)!!))
        // Dead No Dashii poisons no one.
        val dead = GameActions.kill(GameActions.advancePhase(state), 0, DeathCause.EXECUTION)
        assertTrue(StatusEffects.derivedPoison(dead, data::character).isEmpty())
    }

    @Test
    fun `death notes surface protection and consequences`() {
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        listOf("nodashii", "witch", "sage", "clockmaker", "sweetheart")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.addReminder(state, 3, PlacedReminder("monk", "Safe"))

        assertTrue(StatusEffects.deathNotes(state, data::character, 3).any { "Safe" in it })
        assertTrue(StatusEffects.deathNotes(state, data::character, 2).any { "Sage" in it })
        assertTrue(StatusEffects.deathNotes(state, data::character, 4).any { "Sweetheart" in it })
    }

    @Test
    fun `nomination warnings cover witch curse and virgin`() {
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E"))
        listOf("imp", "witch", "virgin", "chef", "mayor")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.addReminder(state, 3, PlacedReminder("witch", "Cursed"))

        val notes = StatusEffects.nominationWarnings(state, data::character, nominatorId = 3, nomineeId = 2)
        assertTrue(notes.any { "Witch-cursed" in it })
        assertTrue(notes.any { "Virgin" in it })
        // Spent virgin no longer warns.
        val spent = GameActions.addReminder(state, 2, PlacedReminder("virgin", "No ability"))
        assertTrue(StatusEffects.nominationWarnings(spent, data::character, 3, 2).none { "Virgin" in it })
    }

    @Test
    fun `exorcist chosen demon step is annotated`() {
        val bmr = data.builtInScripts().first { it.id == "bmr" }
        var state = GameActions.newGame(bmr, listOf("A", "B", "C", "D", "E"))
        listOf("zombuul", "exorcist", "chambermaid", "grandmother", "tealady")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state)
        state = state.copy(cycle = 2)
        state = GameActions.addReminder(state, 0, PlacedReminder("exorcist", "Chosen"))
        val steps = data.nightOrder.otherNight(state, data::character)
        val demonStep = steps.first { it.id == "zombuul" }
        assertTrue("EXORCIST" in demonStep.detail, demonStep.detail)
    }

    @Test
    fun `demon death notes mention scarlet woman`() {
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E", "F"))
        listOf("imp", "scarletwoman", "chef", "empath", "soldier", "mayor")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        val notes = StatusEffects.deathNotes(state, data::character, 0)
        assertTrue(notes.any { "Scarlet Woman" in it })
        assertTrue(notes.any { "Imp self-kill" in it })
    }
}
