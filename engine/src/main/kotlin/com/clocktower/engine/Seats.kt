package com.clocktower.engine

import kotlin.random.Random

/**
 * Seat bookkeeping: who sits where, what they hold, what they believe.
 * WP0 moved these out of `GameActions` verbatim; WP4 owns the file afterwards.
 */
object Seats {

    fun newGame(script: Script, playerNames: List<String>): GameState = GameState(
        script = script,
        players = playerNames.mapIndexed { index, name ->
            Player(id = index.toLong(), name = name.ifBlank { "Player ${index + 1}" })
        },
    )

    /** Adds a seat (e.g. a traveller arriving mid-game) after [afterId], or at the end. */
    fun addSeat(state: GameState, name: String, afterId: Long? = null): GameState {
        val id = (state.players.maxOfOrNull { it.id } ?: -1L) + 1
        val player = Player(id = id, name = name.ifBlank { "Player ${state.players.size + 1}" })
        val index = afterId?.let { anchor -> state.players.indexOfFirst { it.id == anchor } } ?: -1
        val players = state.players.toMutableList()
        if (index >= 0) players.add(index + 1, player) else players.add(player)
        return state.copy(players = players)
    }

    /** Removes a seat entirely (only sensible during setup or for departed travellers). */
    fun removeSeat(state: GameState, playerId: Long): GameState =
        state.copy(players = state.players.filterNot { it.id == playerId })

    /** Moves a seat one position around the circle (for seat swaps). */
    fun moveSeat(state: GameState, playerId: Long, delta: Int): GameState {
        val players = state.players.toMutableList()
        val from = players.indexOfFirst { it.id == playerId }
        if (from < 0 || players.size < 2) return state
        val to = ((from + delta) % players.size + players.size) % players.size
        val p = players.removeAt(from)
        players.add(to, p)
        return state.copy(players = players)
    }

    fun rename(state: GameState, playerId: Long, name: String): GameState =
        state.updatePlayer(playerId) { it.copy(name = name) }

    /** SETUP-phase seat editing only — mid-game changes go through `Identity.changeCharacter`. */
    fun assignCharacter(
        state: GameState,
        playerId: Long,
        characterId: String?,
        isTraveller: Boolean = false,
    ): GameState =
        state.updatePlayer(playerId) {
            it.copy(
                characterId = characterId,
                shownCharacterId = null,
                isTraveller = isTraveller,
            )
        }

    fun setShownCharacter(state: GameState, playerId: Long, shownCharacterId: String?): GameState =
        state.updatePlayer(playerId) { it.copy(shownCharacterId = shownCharacterId) }

    /**
     * Explicit alignment override. Null restores the character's natural side.
     */
    fun setAlignment(state: GameState, playerId: Long, alignment: Alignment?): GameState =
        state.updatePlayer(playerId) { it.copy(alignment = alignment, legacyAlignmentFlipped = false) }

    /**
     * Toggles this seat between its character's natural alignment and the
     * opposite one. WP0 move of `GameActions.flipAlignment`, re-expressed on
     * the explicit [Player.alignment] field.
     */
    fun flipAlignment(
        state: GameState,
        playerId: Long,
        lookup: (String) -> Character? = { null },
    ): GameState {
        val player = state.player(playerId) ?: return state
        // Already overridden: flipping back restores the character's natural side.
        if (player.alignment != null) return setAlignment(state, playerId, null)
        val nowEvil = player.isEvil(lookup)
        return setAlignment(state, playerId, if (nowEvil) Alignment.GOOD else Alignment.EVIL)
    }

    /**
     * Replaces this seat's storyteller notes with [note].
     *
     * WP0 move of `GameActions.setNote` onto the append-only [SeatNote] list;
     * WP10 replaces it with a genuine append.
     */
    fun setNote(state: GameState, playerId: Long, note: String): GameState =
        state.updatePlayer(playerId) { player ->
            player.copy(
                notes = if (note.isBlank()) {
                    emptyList()
                } else {
                    listOf(SeatNote(cycle = state.cycle, phase = state.phase, text = note))
                },
                legacyNote = "",
            )
        }

    /**
     * Randomly deals characters to non-Traveller seats from [bag]. A size
     * mismatch is rejected before any assignments are produced, preventing a
     * partial re-deal from retaining stale characters on leftover seats.
     */
    fun deal(state: GameState, bag: List<String>, random: Random = Random): GameState {
        val recipientCount = state.players.count { !it.isTraveller }
        require(bag.size == recipientCount) {
            "Bag has ${bag.size} characters for $recipientCount non-Traveller players"
        }
        val shuffled = bag.shuffled(random)
        var i = 0
        return state.copy(
            players = state.players.map { p ->
                if (p.isTraveller) {
                    p
                } else {
                    p.copy(characterId = shuffled[i++], shownCharacterId = null)
                }
            },
        )
    }
}
