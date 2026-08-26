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

    /**
     * SETUP-phase seat editing only — mid-game changes go through
     * `Identity.changeCharacter`.
     *
     * Re-assigning a seat drops the tokens and the "Believes they are…" note the
     * abandoned character owned, so a seat that stops being the Drunk stops
     * carrying `Is The Drunk`.
     *
     * It also clears [Player.tokenShownAt] — exactly as `Seats.deal` and
     * `Identity.changeCharacter` do — so the seat goes back into the hand-out
     * queue: whatever the storyteller showed this player is no longer true.
     */
    fun assignCharacter(
        state: GameState,
        playerId: Long,
        characterId: String?,
        isTraveller: Boolean = false,
    ): GameState {
        val previous = state.player(playerId)?.characterId?.let(Character::normalizeId)
        val next = characterId?.let(Character::normalizeId)
        return state.updatePlayer(playerId) { seat ->
            seat.copy(
                characterId = characterId,
                shownCharacterId = null,
                isTraveller = isTraveller,
                reminders = if (previous == null || previous == next) {
                    seat.reminders
                } else {
                    seat.reminders.filterNot { Character.normalizeId(it.sourceId) == previous }
                },
                notes = if (previous == null || previous == next) {
                    seat.notes
                } else {
                    seat.notes.filterNot { it.text.startsWith("Believes they are", true) }
                },
                tokenShownAt = null,
            )
        }
    }

    /**
     * The token this seat BELIEVES it holds (the Drunk's Townsfolk, the
     * Lunatic's Demon). Clears [Player.tokenShownAt] too: the seat must be
     * handed the new token before the game starts.
     */
    fun setShownCharacter(state: GameState, playerId: Long, shownCharacterId: String?): GameState =
        state.updatePlayer(playerId) {
            it.copy(shownCharacterId = shownCharacterId, tokenShownAt = null)
        }

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
     *
     * When a [lookup] is supplied, the deal also places every unambiguous setup
     * token the dealt character declares (`Is The Drunk`, `Is The Marionette`)
     * and records one `IdentityRecord(reason = DEAL, pendingReveal = true)` per
     * seat, so the hand-out flow is the natural next step.
     */
    fun deal(
        state: GameState,
        bag: List<String>,
        random: Random = Random,
        lookup: (String) -> Character? = { null },
    ): GameState {
        val recipientCount = state.players.count { !it.isTraveller }
        require(bag.size == recipientCount) {
            "Bag has ${bag.size} characters for $recipientCount non-Traveller players"
        }
        val shuffled = bag.shuffled(random)
        var i = 0
        var next = state.copy(
            players = state.players.map { p ->
                if (p.isTraveller) {
                    p
                } else {
                    p.copy(
                        characterId = shuffled[i++],
                        shownCharacterId = null,
                        reminders = emptyList(),
                        grants = emptyList(),
                        tokenShownAt = null,
                    )
                }
            },
            identityLog = emptyList(),
        )
        for (seat in next.players.filterNot { it.isTraveller }) {
            val character = seat.characterId?.let(lookup)
            // "Is The …" is the character's own declaration that this seat is
            // secretly them; official Title Case, straight from the data.
            val identityToken = character?.remindersGlobal
                ?.firstOrNull { it.startsWith("Is The ", ignoreCase = true) }
            if (identityToken != null) {
                next = Effects.addReminder(
                    next,
                    seat.id,
                    PlacedReminder(character.id, identityToken, placedCycle = next.cycle),
                )
            }
            next = next.copy(
                identityLog = next.identityLog + IdentityRecord(
                    playerId = seat.id,
                    cycle = next.cycle,
                    atNight = false,
                    fromCharacterId = null,
                    toCharacterId = seat.characterId,
                    fromEvil = false,
                    toEvil = seat.isEvil(lookup),
                    reason = ChangeReason.DEAL,
                    pendingReveal = true,
                ),
            )
        }
        return next
    }
}
