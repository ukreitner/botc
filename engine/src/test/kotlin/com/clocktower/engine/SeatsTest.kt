package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Seat editing during SETUP. `Player.tokenShownAt` is the hand-out queue's only
 * state, so every verb that changes WHAT a seat will be shown has to clear it —
 * otherwise a re-assigned seat silently keeps its "already handed out" tick and
 * the storyteller shows nobody the new token.
 */
class SeatsTest {

    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().first { it.id == "tb" }

    /** A seated game in which every seat has already been handed its token. */
    private fun handedOut(vararg characterIds: String): GameState {
        var state = Seats.newGame(script, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { index, id ->
            state = Seats.assignCharacter(state, index.toLong(), id)
        }
        return state.copy(players = state.players.map { it.copy(tokenShownAt = 1_000L) })
    }

    @Test
    fun `re-assigning a seat puts it back in the hand-out queue`() {
        var state = handedOut("imp", "baron", "drunk", "recluse", "chef")
        assertNotNull(state.player(2)?.tokenShownAt)

        state = Seats.assignCharacter(state, 2, "fortuneteller")
        assertNull(state.player(2)?.tokenShownAt, "a re-assigned seat owes a new hand-out")
        // Only that seat: the rest of the table was not disturbed.
        assertEquals(
            listOf(0L, 1L, 3L, 4L),
            state.players.filter { it.tokenShownAt != null }.map { it.id },
        )
    }

    @Test
    fun `marking a seat a Traveller re-queues it too`() {
        var state = handedOut("imp", "baron", "drunk", "recluse", "chef")
        state = Seats.assignCharacter(state, 4, "scapegoat", isTraveller = true)
        assertNull(state.player(4)?.tokenShownAt)
    }

    @Test
    fun `changing the token a seat believes re-queues it`() {
        var state = handedOut("imp", "baron", "drunk", "recluse", "chef")
        val drunk = state.players.first { it.characterId == "drunk" }.id
        assertNotNull(state.player(drunk)?.tokenShownAt)

        state = Seats.setShownCharacter(state, drunk, "washerwoman")
        assertEquals("washerwoman", state.player(drunk)?.shownCharacterId)
        assertNull(state.player(drunk)?.tokenShownAt, "the Drunk must be shown the new token")
    }
}
