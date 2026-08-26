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
        val spent = GameActions.addReminder(state, 2, PlacedReminder("virgin", "No Ability"))
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
        val steps = NightPlan.build(state, data::character).steps
        val demonStep = steps.first { it.slotId == "zombuul" }
        // WP2: the Exorcist annotation became a REDUCED gate plus a banner —
        // the deferred half of a silenced Demon still runs (lead D24).
        assertTrue(demonStep.gate is StepGate.Reduced, demonStep.gate.toString())
        assertTrue("Exorcist" in demonStep.banner, demonStep.banner)
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

class EndgameTest {
    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }

    @kotlin.test.Test
    fun `resurrect keeps the death record marked`() {
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E"))
        state = GameActions.advancePhase(state)
        state = GameActions.kill(state, 1, DeathCause.DEMON, data::character)
        state = GameActions.resurrect(state, 1)
        kotlin.test.assertTrue(state.player(1)!!.alive)
        kotlin.test.assertEquals(1, state.deaths.size)
        kotlin.test.assertTrue(state.deaths.single().resurrected)
        // Undo-style revive removes the newest (unresurrected) record and
        // leaves the resurrection history intact.
        state = GameActions.kill(state, 1, DeathCause.DEMON, data::character)
        state = GameActions.revive(state, 1)
        kotlin.test.assertEquals(0, state.deaths.count { !it.resurrected })
        kotlin.test.assertEquals(1, state.deaths.count { it.resurrected })
    }

    @kotlin.test.Test
    fun `mastermind day resolves on execution and suppresses demon-dead advisory`() {
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E", "F"))
        listOf("imp", "mastermind", "chef", "empath", "soldier", "mayor")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state) // night 1
        state = GameActions.advancePhase(state) // day 1
        state = GameActions.kill(state, 0, DeathCause.EXECUTION, data::character)
        state = state.copy(mastermindDayActive = true)
        kotlin.test.assertEquals(null, WinCheck.check(state, data::character), "advisory suppressed mid extra day")
        // Next day: a good player is executed — good loses.
        state = GameActions.advancePhase(state)
        state = GameActions.advancePhase(state)
        state = GameActions.kill(state, 3, DeathCause.EXECUTION, data::character)
        val advisory = kotlin.test.assertNotNull(WinCheck.check(state, data::character))
        kotlin.test.assertEquals(false, advisory.goodWins)
        kotlin.test.assertTrue("Mastermind day" in advisory.reason)
    }
}
