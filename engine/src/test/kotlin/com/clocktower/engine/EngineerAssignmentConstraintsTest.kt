package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineerAssignmentConstraintsTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private fun game(): GameState {
        val roles = listOf("engineer", "imp", "poisoner", "baron", "chef")
        val script = Script("engineer-constraints", "Engineer", characterIds = roles + listOf("spy", "godfather", "vortox"))
        var state = GameActions.newGame(script, roles.indices.map { "P$it" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    private fun row(state: GameState) = NightPlan.build(state, lookup).steps.first { it.abilityId == "engineer" }
    private fun resolve(state: GameState, input: NightInput) = NightPlan.resolve(state, lookup, row(state).key, input)

    @Test fun `illegal ordinary rebuilds do not spend or mutate the game`() {
        val state = game()
        for (assignments in listOf(
            listOf(1L to "spy"), // Demon becomes Minion.
            listOf(1L to "vortox", 2L to "spy"), // Mixed group.
            listOf(2L to "spy"), // Missing the other Minion.
            listOf(2L to "baron", 3L to "spy"), // Moving an in-play character.
            listOf(2L to "spy", 3L to "spy"), // Duplicate new character.
            listOf(4L to "spy"), // Good character becomes Minion.
        )) {
            assertEquals(state, resolve(state, NightInput(assignments = assignments)), assignments.toString())
        }
    }

    @Test fun `one Demon or complete Minion group can rebuild and spend`() {
        val state = game()
        val minions = resolve(state, NightInput(assignments = listOf(2L to "spy", 3L to "godfather")))
        assertEquals("spy", minions.player(2)!!.characterId)
        assertEquals("godfather", minions.player(3)!!.characterId)
        assertEquals("imp", minions.player(1)!!.characterId)
        assertTrue(Memory.isSpent(minions, "engineer", 0))
        val demon = resolve(state, NightInput(assignments = listOf(1L to "vortox")))
        assertEquals("vortox", demon.player(1)!!.characterId)
        assertEquals("poisoner", demon.player(2)!!.characterId)
    }

    @Test fun `unchanged in-play Minion keeps its owner and good alignment remains valid`() {
        val state = game().updatePlayer(2) { it.copy(alignment = Alignment.GOOD) }
        val rebuilt = resolve(state, NightInput(assignments = listOf(2L to "poisoner", 3L to "spy")))
        assertEquals("poisoner", rebuilt.player(2)!!.characterId)
        assertEquals(Alignment.GOOD, rebuilt.player(2)!!.alignment)
        assertEquals("spy", rebuilt.player(3)!!.characterId)
    }

    @Test fun `explicit storyteller adjudication and pass bypass ordinary group rules`() {
        val state = game()
        val ruled = resolve(state, NightInput(assignments = listOf(2L to "spy"), overrideConstraints = true))
        assertEquals("spy", ruled.player(2)!!.characterId)
        assertTrue(Memory.isSpent(ruled, "engineer", 0))
        val passed = resolve(state, NightInput(none = true))
        assertFalse(Memory.isSpent(passed, "engineer", 0))
        val action = row(state).action as ChoosePlayersAndCharacters
        assertNull(assignmentConstraintError(state, lookup, action, NightInput(none = true)))
    }
}
