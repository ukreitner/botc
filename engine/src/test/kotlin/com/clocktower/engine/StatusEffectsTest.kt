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
