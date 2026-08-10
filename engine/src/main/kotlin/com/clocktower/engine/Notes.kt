package com.clocktower.engine

import kotlinx.serialization.Serializable

/**
 * Player-notes mode: an "empty grimoire" tracking what a PLAYER believes
 * during a game someone else is running — claims, suspicions, trust,
 * deaths, and relationship lines between seats. Nothing here is ground
 * truth; it's one player's picture of the game.
 */

/** How much you trust a seat. Drives the ring color around their token. */
@Serializable
enum class Trust { UNKNOWN, TRUSTED, SUSPICIOUS, EVIL }

/** A public character claim. Claims are history, not a single value. */
@Serializable
data class NoteClaim(val characterId: String, val day: Int)

/**
 * A line between two seats. ACCUSES / DEFENDS / INFO are directed
 * (drawn with an arrowhead at the target); SAME_TEAM / OPPOSITE_TEAM are
 * undirected pair facts.
 */
@Serializable
enum class LinkKind(val directed: Boolean) {
    ACCUSES(true),
    DEFENDS(true),
    /** "Gave info about" — e.g. Fortune Teller ping on the target. */
    INFO(true),
    SAME_TEAM(false),
    OPPOSITE_TEAM(false),
}

@Serializable
data class NoteLink(
    val id: Long,
    val fromSeatId: Long,
    val toSeatId: Long,
    val kind: LinkKind,
    /** Optional detail: "says he's the Imp", "FT: YES", ... */
    val label: String = "",
    val day: Int,
)

/** One entry in my private info log ("Empath said 1", night 2). */
@Serializable
data class NoteInfo(val day: Int, val text: String)

@Serializable
data class NoteSeat(
    val id: Long,
    val name: String,
    val claims: List<NoteClaim> = emptyList(),
    /** Characters I suspect they really are. */
    val suspectIds: List<String> = emptyList(),
    val trust: Trust = Trust.UNKNOWN,
    val alive: Boolean = true,
    val deathDay: Int? = null,
    /** True = executed, false = died at night/other, null = unknown. */
    val executed: Boolean? = null,
    val ghostVoteUsed: Boolean = false,
    val note: String = "",
) {
    val currentClaimId: String? get() = claims.lastOrNull()?.characterId
}

@Serializable
data class NotesState(
    val script: Script,
    val seats: List<NoteSeat> = emptyList(),
    val links: List<NoteLink> = emptyList(),
    val day: Int = 1,
    val mySeatId: Long? = null,
    /** My real character — shown only behind a hold-to-reveal. */
    val myCharacterId: String? = null,
    val infoLog: List<NoteInfo> = emptyList(),
    /** Free-form scratchpad for the whole game (not tied to a seat). */
    val generalNotes: String = "",
    val nextId: Long = 1,
    val updatedAt: Long = 0,
) {
    fun seat(id: Long): NoteSeat? = seats.find { it.id == id }

    fun updateSeat(id: Long, transform: (NoteSeat) -> NoteSeat): NotesState =
        copy(seats = seats.map { if (it.id == id) transform(it) else it })
}

object NotesActions {

    fun newNotes(script: Script, seatNames: List<String>): NotesState {
        val seats = seatNames.mapIndexed { index, name ->
            NoteSeat(id = index + 1L, name = name.ifBlank { "Seat ${index + 1}" })
        }
        return NotesState(script = script, seats = seats, nextId = seats.size + 1L)
    }

    // ---- Seats ----------------------------------------------------------

    fun addSeat(state: NotesState, name: String): NotesState {
        val seat = NoteSeat(id = state.nextId, name = name.ifBlank { "Seat ${state.seats.size + 1}" })
        return state.copy(seats = state.seats + seat, nextId = state.nextId + 1)
    }

    /** Removing a seat also drops every line touching it. */
    fun removeSeat(state: NotesState, seatId: Long): NotesState = state.copy(
        seats = state.seats.filterNot { it.id == seatId },
        links = state.links.filterNot { it.fromSeatId == seatId || it.toSeatId == seatId },
        mySeatId = state.mySeatId.takeUnless { it == seatId },
    )

    fun renameSeat(state: NotesState, seatId: Long, name: String): NotesState =
        state.updateSeat(seatId) { it.copy(name = name) }

    /** Moves a seat one position around the circle, wrapping at the ends. */
    fun moveSeat(state: NotesState, seatId: Long, delta: Int): NotesState {
        val index = state.seats.indexOfFirst { it.id == seatId }
        if (index < 0 || state.seats.size < 2) return state
        val target = ((index + delta) % state.seats.size + state.seats.size) % state.seats.size
        val mutable = state.seats.toMutableList()
        val seat = mutable.removeAt(index)
        mutable.add(target, seat)
        return state.copy(seats = mutable)
    }

    // ---- Beliefs --------------------------------------------------------

    /** Records a claim; appends to history unless it repeats the current one. */
    fun setClaim(state: NotesState, seatId: Long, characterId: String): NotesState =
        state.updateSeat(seatId) { seat ->
            if (seat.currentClaimId == characterId) {
                seat
            } else {
                seat.copy(claims = seat.claims + NoteClaim(characterId, state.day))
            }
        }

    fun removeClaim(state: NotesState, seatId: Long, index: Int): NotesState =
        state.updateSeat(seatId) { seat ->
            seat.copy(claims = seat.claims.filterIndexed { i, _ -> i != index })
        }

    fun setSuspects(state: NotesState, seatId: Long, characterIds: List<String>): NotesState =
        state.updateSeat(seatId) { it.copy(suspectIds = characterIds.distinct()) }

    fun setTrust(state: NotesState, seatId: Long, trust: Trust): NotesState =
        state.updateSeat(seatId) { it.copy(trust = trust) }

    fun setNote(state: NotesState, seatId: Long, note: String): NotesState =
        state.updateSeat(seatId) { it.copy(note = note) }

    // ---- Life & death ---------------------------------------------------

    fun markDead(state: NotesState, seatId: Long, executed: Boolean?): NotesState =
        state.updateSeat(seatId) {
            it.copy(alive = false, deathDay = state.day, executed = executed, ghostVoteUsed = false)
        }

    fun revive(state: NotesState, seatId: Long): NotesState =
        state.updateSeat(seatId) {
            it.copy(alive = true, deathDay = null, executed = null, ghostVoteUsed = false)
        }

    fun toggleGhostVote(state: NotesState, seatId: Long): NotesState =
        state.updateSeat(seatId) { it.copy(ghostVoteUsed = !it.ghostVoteUsed) }

    // ---- Links ----------------------------------------------------------

    /**
     * Adds a line between two seats. Undirected kinds are normalized so
     * A↔B and B↔A collapse; adding an identical line again is a no-op.
     */
    fun addLink(
        state: NotesState,
        fromSeatId: Long,
        toSeatId: Long,
        kind: LinkKind,
        label: String = "",
    ): NotesState {
        if (fromSeatId == toSeatId) return state
        if (state.seat(fromSeatId) == null || state.seat(toSeatId) == null) return state
        val (a, b) = if (!kind.directed && fromSeatId > toSeatId) {
            toSeatId to fromSeatId
        } else {
            fromSeatId to toSeatId
        }
        val duplicate = state.links.any {
            it.fromSeatId == a && it.toSeatId == b && it.kind == kind && it.label == label
        }
        if (duplicate) return state
        val link = NoteLink(state.nextId, a, b, kind, label, state.day)
        return state.copy(links = state.links + link, nextId = state.nextId + 1)
    }

    fun removeLink(state: NotesState, linkId: Long): NotesState =
        state.copy(links = state.links.filterNot { it.id == linkId })

    // ---- Me & the day ---------------------------------------------------

    fun setDay(state: NotesState, day: Int): NotesState = state.copy(day = day.coerceAtLeast(1))

    fun setMySeat(state: NotesState, seatId: Long?): NotesState = state.copy(mySeatId = seatId)

    fun setMyCharacter(state: NotesState, characterId: String?): NotesState =
        state.copy(myCharacterId = characterId)

    fun setGeneralNotes(state: NotesState, text: String): NotesState =
        state.copy(generalNotes = text)

    fun addInfo(state: NotesState, text: String): NotesState =
        if (text.isBlank()) state else state.copy(infoLog = state.infoLog + NoteInfo(state.day, text))

    fun removeInfo(state: NotesState, index: Int): NotesState =
        state.copy(infoLog = state.infoLog.filterIndexed { i, _ -> i != index })

    // ---- Derived deduction aids ----------------------------------------

    /**
     * The claim matrix: for each script character, who currently claims it.
     * Double claims mean someone is lying; unclaimed good characters are
     * bluff candidates.
     */
    fun claimants(state: NotesState): Map<String, List<NoteSeat>> =
        state.seats
            .filter { it.currentClaimId != null }
            .groupBy { it.currentClaimId!! }

    /** Chronological feed of everything recorded, grouped by day. */
    fun timeline(state: NotesState, lookup: (String) -> Character?): List<Pair<Int, String>> {
        val events = mutableListOf<Pair<Int, String>>()
        for (seat in state.seats) {
            for (claim in seat.claims) {
                events += claim.day to "${seat.name} claimed ${lookup(claim.characterId)?.name ?: claim.characterId}"
            }
            if (!seat.alive && seat.deathDay != null) {
                val how = when (seat.executed) {
                    true -> "was executed"
                    false -> "died in the night"
                    null -> "died"
                }
                events += seat.deathDay to "${seat.name} $how"
            }
        }
        for (link in state.links) {
            val from = state.seat(link.fromSeatId)?.name ?: "?"
            val to = state.seat(link.toSeatId)?.name ?: "?"
            val verb = when (link.kind) {
                LinkKind.ACCUSES -> "accused"
                LinkKind.DEFENDS -> "defended"
                LinkKind.INFO -> "gave info about"
                LinkKind.SAME_TEAM -> "same team as"
                LinkKind.OPPOSITE_TEAM -> "opposite team from"
            }
            val suffix = if (link.label.isBlank()) "" else " (${link.label})"
            events += link.day to "$from $verb $to$suffix"
        }
        for (info in state.infoLog) {
            events += info.day to "My info: ${info.text}"
        }
        return events.sortedBy { it.first }
    }
}
