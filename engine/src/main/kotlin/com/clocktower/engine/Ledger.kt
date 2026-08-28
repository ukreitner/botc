package com.clocktower.engine

import kotlinx.serialization.Serializable

/** The coarse taxonomy. The fine one is [LedgerEntry.sourceId] — there is no second enum. */
@Serializable
enum class LedgerKind {
    /** A character chose someone/something at night. Powers "different to last night". */
    CHOICE,

    /** Information actually delivered to a player — true or false. */
    TOLD,

    /** Something said in public during the day (Gossip, Juggler, Slayer, a plain claim). */
    STATEMENT,

    /** A private day-time storyteller conversation (Savant, Artist, Fisherman, Amnesiac). */
    PRIVATE,

    /** A storyteller decision that must stay consistent (misregistration, madness, malfunction). */
    RULING,

    /** Something the storyteller owes the table. `announcePending` until said out loud. */
    ANNOUNCE,

    /** A once-per-game ability was used. */
    SPENT,

    /** A seat woke tonight. `byStoryteller`/`genuine` distinguish own-ability wakes. */
    WOKE,

    /** An ability malfunctioned (Mathematician's count). */
    MALFUNCTION,

    /** Opens/closes an impairment window for one seat (lead D41). */
    IMPAIRMENT_SPAN,

    /** Free text with no other structure. */
    NOTE,
}

/** UNJUDGED until the storyteller rules. TRUE doubles as "correct" for guesses. */
@Serializable
enum class Verdict {
    UNJUDGED, TRUE, FALSE,

    /** Savant / Artist two-sided entries. */
    A_TRUE, B_TRUE, BOTH_TRUE, NEITHER_TRUE,

    /** The storyteller exercised a free choice; neither true nor false. */
    ST_CHOICE,
}

/**
 * ONE record type for everything that happened or was said. Replaces ChoiceRecord,
 * NightRecord, DayEntry, DayAct, PublicStatement, PublicClaim, Announcement,
 * Misregistration, Malfunction, WakeEvent and SeatEvent.
 *
 * The whole ledger MUST work as free text with everything else empty — recording
 * "Bo said Fay is the Imp" in a game with no Gossip in play is the point.
 *
 * [id], [cycle] and [atNight] are stamped by [Ledger.record]; a caller building an
 * entry by hand leaves them at their defaults.
 */
@Serializable
data class LedgerEntry(
    val id: Long = 0L,
    /** Same numbering as [DeathEvent]: day N follows night N. */
    val cycle: Int = 0,
    val atNight: Boolean = false,
    val kind: LedgerKind = LedgerKind.NOTE,
    /**
     * Character id ("gossip", "devilsadvocate"), a night marker ("DAWN"), or a
     * pseudo-source: "claim", "misregister", "malfunction", "note", "st".
     * This is the fine-grained taxonomy — there is no second enum.
     */
    val sourceId: String = "",
    /** Seat that acted / spoke / was told. Null for storyteller-only entries. */
    val actorId: Long? = null,
    /** Seats the entry is about: the DA's target, the Juggler's guessed seats. */
    val targetIds: List<Long> = emptyList(),
    /** Second seat list where a kind needs two: Alsaahir's Minions vs Demons (lead D42). */
    val targetIdsB: List<Long> = emptyList(),
    /** Characters named. Parallel to [targetIds] where both are used (Juggler guess i). */
    val characterIds: List<String> = emptyList(),
    /** The words: the Gossip's statement, the Artist's question, the Savant's statement A. */
    val text: String = "",
    /** Second half of a two-sided entry: Savant statement B, the Artist's answer. */
    val textB: String = "",
    /** What the storyteller actually showed: "3", "YES", "Ravenkeeper", "warm". */
    val shown: String = "",
    val verdict: Verdict = Verdict.UNJUDGED,
    /** Integer payload: Juggler correct count, Yaggababble phrase count. */
    val count: Int? = null,
    /** Whether the ACTOR's ability was malfunctioning when this happened. Snapshot, not live. */
    val impaired: Boolean = false,
    /** True when the app believes the actor really holds [sourceId]; false for a bluffing claimant. */
    val genuine: Boolean = true,
    /**
     * The STORYTELLER made this choice, not the player. The Goon needs it
     * ("the 1st player to choose you each night") and so does every
     * storyteller-substituted pick (lead D1).
     */
    val byStoryteller: Boolean = false,
    /** Cycle on which a later step consumed this entry (Gossip resolved, Juggler revealed). */
    val resolvedCycle: Int? = null,
    /** ANNOUNCE only: true while the storyteller still owes the table this sentence. */
    val announcePending: Boolean = false,
)

/**
 * Append-only writers (WP3). Each stamps cycle/atNight from the state and
 * allocates the id, so no caller ever touches [GameState.nextLedgerId].
 *
 * Nothing here is destructive: [edit] and [delete] exist for storyteller
 * corrections and are the only ways an entry changes shape.
 */
object Ledger {

    /** Pseudo-sources. A character id is always preferred where one exists. */
    object Sources {
        /** A plain "I am the Empath" with no ability behind it. */
        const val CLAIM: String = "claim"

        /** A misregistration ruling (lead D10). */
        const val MISREGISTER: String = "misregister"

        /** A Mathematician-visible malfunction. */
        const val MALFUNCTION: String = "malfunction"

        /** Free text with no ability behind it. */
        const val NOTE: String = "note"

        /** The storyteller speaking as themself. */
        const val STORYTELLER: String = "st"

        /** [LedgerKind.IMPAIRMENT_SPAN] rows, written by `Effects.reconcile`. */
        const val STATUS: String = "status"
    }

    /** Appends [entry], stamping id, cycle and atNight from [state]. */
    fun record(state: GameState, entry: LedgerEntry): GameState {
        val id = state.nextLedgerId
        return state.copy(
            ledger = state.ledger + entry.copy(
                id = id,
                cycle = state.cycle,
                atNight = state.phase != Phase.DAY,
            ),
            nextLedgerId = id + 1,
        )
    }

    fun choice(
        state: GameState,
        sourceId: String,
        actorId: Long?,
        targetIds: List<Long>,
        characterIds: List<String> = emptyList(),
        impaired: Boolean = false,
        byStoryteller: Boolean = false,
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.CHOICE,
            sourceId = sourceId,
            actorId = actorId,
            targetIds = targetIds,
            characterIds = characterIds,
            impaired = impaired,
            byStoryteller = byStoryteller,
        ),
    )

    fun told(
        state: GameState,
        playerId: Long,
        sourceId: String,
        shown: String,
        impaired: Boolean = false,
        text: String = "",
        targetIds: List<Long> = emptyList(),
        characterIds: List<String> = emptyList(),
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.TOLD,
            sourceId = sourceId,
            actorId = playerId,
            targetIds = targetIds,
            characterIds = characterIds,
            shown = shown,
            text = text,
            impaired = impaired,
        ),
    )

    /**
     * Something said in public. This is the one the user asked for by name:
     * it works with **nothing** in play — `sourceId` may be [Sources.CLAIM]
     * and every other field empty.
     */
    fun statement(
        state: GameState,
        speakerId: Long?,
        sourceId: String,
        text: String,
        targetIds: List<Long> = emptyList(),
        characterIds: List<String> = emptyList(),
        genuine: Boolean = true,
        targetIdsB: List<Long> = emptyList(),
        textB: String = "",
        count: Int? = null,
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.STATEMENT,
            sourceId = sourceId.ifEmpty { Sources.CLAIM },
            actorId = speakerId,
            targetIds = targetIds,
            targetIdsB = targetIdsB,
            characterIds = characterIds,
            text = text,
            textB = textB,
            count = count,
            genuine = genuine,
        ),
    )

    fun private(
        state: GameState,
        playerId: Long,
        sourceId: String,
        text: String,
        shown: String,
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.PRIVATE,
            sourceId = sourceId,
            actorId = playerId,
            text = text,
            shown = shown,
        ),
    )

    /**
     * A storyteller decision that must stay consistent. [shown] carries the exact
     * line to say out loud when the ruling has one (a prevented death does).
     */
    fun ruling(
        state: GameState,
        sourceId: String,
        playerId: Long?,
        text: String,
        characterIds: List<String> = emptyList(),
        shown: String = "",
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.RULING,
            sourceId = sourceId,
            actorId = playerId,
            characterIds = characterIds,
            text = text,
            shown = shown,
        ),
    )

    /** Something the storyteller still owes the table. Pending until [markAnnounced]. */
    fun announce(
        state: GameState,
        text: String,
        sourceId: String = Sources.STORYTELLER,
        actorId: Long? = null,
        detail: String = "",
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.ANNOUNCE,
            sourceId = sourceId.ifEmpty { Sources.STORYTELLER },
            actorId = actorId,
            text = text,
            textB = detail,
            announcePending = true,
        ),
    )

    fun woke(state: GameState, playerId: Long, sourceId: String, ownAbility: Boolean): GameState =
        record(
            state,
            LedgerEntry(
                kind = LedgerKind.WOKE,
                sourceId = sourceId,
                actorId = playerId,
                genuine = ownAbility,
                byStoryteller = !ownAbility,
            ),
        )

    fun malfunction(
        state: GameState,
        playerId: Long,
        sourceId: String,
        reason: String,
    ): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.MALFUNCTION,
            sourceId = sourceId,
            actorId = playerId,
            text = reason,
            impaired = true,
        ),
    )

    fun spent(state: GameState, sourceId: String, actorId: Long): GameState = record(
        state,
        LedgerEntry(kind = LedgerKind.SPENT, sourceId = sourceId, actorId = actorId),
    )

    /**
     * Opens an impairment window for one seat (lead D41). Closed by [resolve].
     * Written only by `Effects.reconcile` — never by a screen.
     */
    fun impairmentSpan(state: GameState, playerId: Long, reason: String): GameState = record(
        state,
        LedgerEntry(
            kind = LedgerKind.IMPAIRMENT_SPAN,
            sourceId = Sources.STATUS,
            actorId = playerId,
            text = reason,
            impaired = true,
        ),
    )

    /** Free text with no other structure. */
    fun note(state: GameState, text: String, playerId: Long? = null): GameState = record(
        state,
        LedgerEntry(kind = LedgerKind.NOTE, sourceId = Sources.NOTE, actorId = playerId, text = text),
    )

    fun markAnnounced(state: GameState, id: Long): GameState =
        edit(state, id) { it.copy(announcePending = false) }

    fun setVerdict(state: GameState, id: Long, verdict: Verdict): GameState =
        edit(state, id) { it.copy(verdict = verdict) }

    /** Sets `resolvedCycle = state.cycle`. */
    fun resolve(state: GameState, id: Long): GameState =
        edit(state, id) { it.copy(resolvedCycle = state.cycle) }

    fun edit(state: GameState, id: Long, transform: (LedgerEntry) -> LedgerEntry): GameState {
        if (state.ledger.none { it.id == id }) return state
        return state.copy(
            ledger = state.ledger.map { if (it.id == id) transform(it).copy(id = id) else it },
        )
    }

    fun delete(state: GameState, id: Long): GameState =
        state.copy(ledger = state.ledger.filterNot { it.id == id })
}

/**
 * Read-only queries over the ledger (WP3). Nothing here is stored; a map of
 * "last night's choice" would break with two Village Idiots and would need its
 * own undo discipline, which an append-only list gets for free (lead D3).
 */
object Memory {

    private fun sameSource(entry: LedgerEntry, sourceId: String): Boolean =
        Character.normalizeId(entry.sourceId) == Character.normalizeId(sourceId)

    /** Every entry of [kind] written by [sourceId], oldest first. */
    fun by(state: GameState, kind: LedgerKind, sourceId: String, holderId: Long? = null): List<LedgerEntry> =
        state.ledger.filter {
            it.kind == kind && sameSource(it, sourceId) && (holderId == null || it.actorId == holderId)
        }

    /**
     * The most recent CHOICE by [sourceId] (optionally by one holder) strictly
     * before [beforeCycle]. This is what survives the token sweep: the Devil's
     * Advocate's `Survives Execution` token is gone by dusk, the choice is not.
     */
    fun lastChoice(
        state: GameState,
        sourceId: String,
        holderId: Long? = null,
        beforeCycle: Int = state.cycle,
    ): LedgerEntry? = by(state, LedgerKind.CHOICE, sourceId, holderId).lastOrNull { it.cycle < beforeCycle }

    /**
     * The CHOICE one SEAT has already made tonight, whatever ability made it.
     *
     * By actor rather than by source: a Lunatic records their choice under the
     * BELIEVED Demon's id (lead D70), so "what did that seat choose tonight?"
     * cannot be asked with a character id. The Demon's briefing needs exactly
     * this question, and only the seat is known to it.
     */
    fun choiceTonight(state: GameState, actorId: Long): LedgerEntry? = state.ledger.lastOrNull {
        it.kind == LedgerKind.CHOICE &&
            it.atNight &&
            it.cycle == state.cycle &&
            it.actorId == actorId
    }

    /** Seats [sourceId] may NOT pick tonight because of a "different to last night" clause. */
    fun forbiddenTargets(state: GameState, sourceId: String, holderId: Long? = null): Set<Long> =
        lastChoice(state, sourceId, holderId)?.targetIds?.toSet().orEmpty()

    /** Every seat [sourceId] has ever chosen — "cannot learn the same evil player twice". */
    fun everChosen(state: GameState, sourceId: String, holderId: Long? = null): Set<Long> =
        by(state, LedgerKind.CHOICE, sourceId, holderId).flatMap { it.targetIds }.toSet()

    /**
     * True when [sourceId] was asked last night and picked nobody (the Po's charge).
     * A night on which the step never ran is not a "chose nobody" night.
     */
    fun choseNobodyLastNight(state: GameState, sourceId: String, holderId: Long? = null): Boolean {
        val lastNight = by(state, LedgerKind.CHOICE, sourceId, holderId)
            .filter { it.cycle == state.cycle - 1 && it.atNight }
        return lastNight.isNotEmpty() && lastNight.all { it.targetIds.isEmpty() }
    }

    /** Has this once-per-game ability been used? Reads SPENT rows, never a token. */
    fun isSpent(state: GameState, sourceId: String, actorId: Long? = null): Boolean =
        by(state, LedgerKind.SPENT, sourceId, actorId).isNotEmpty()

    /**
     * Everything said in public on [day]. With `sourceId = null` this is the whole
     * "what was said today" card — which must work in a game with nothing in play.
     */
    fun statementsOn(
        state: GameState,
        day: Int,
        sourceId: String? = null,
        speakerId: Long? = null,
    ): List<LedgerEntry> = state.ledger.filter {
        it.kind == LedgerKind.STATEMENT &&
            it.cycle == day &&
            (sourceId == null || sameSource(it, sourceId)) &&
            (speakerId == null || it.actorId == speakerId)
    }

    /** Entries by [sourceId] on [day] a later step has not consumed yet. */
    fun unresolved(state: GameState, sourceId: String, day: Int): List<LedgerEntry> =
        state.ledger.filter {
            sameSource(it, sourceId) && it.cycle == day && it.resolvedCycle == null
        }

    fun pendingAnnouncements(state: GameState): List<LedgerEntry> =
        state.ledger.filter { it.kind == LedgerKind.ANNOUNCE && it.announcePending }

    /**
     * Everything ever told to, chosen by, or said by one seat. Merged with deaths,
     * nominations and executions by [GameLog]; this is the ledger half.
     */
    fun forPlayer(state: GameState, playerId: Long): List<LedgerEntry> =
        state.ledger.filter {
            it.actorId == playerId || playerId in it.targetIds || playerId in it.targetIdsB
        }

    /** The standing ruling for how [playerId] registers to [askedBy], if any. */
    fun ruling(state: GameState, playerId: Long, askedBy: String): LedgerEntry? {
        val rulings = state.ledger.filter { it.kind == LedgerKind.RULING && it.actorId == playerId }
        return rulings.lastOrNull { sameSource(it, askedBy) }
            ?: rulings.lastOrNull { sameSource(it, Ledger.Sources.MISREGISTER) }
    }

    /** Character types this seat has already been shown — the Balloonist's memory. */
    fun typesSeen(state: GameState, lookup: (String) -> Character?, actorId: Long): List<Team> {
        val out = linkedSetOf<Team>()
        for (entry in state.ledger) {
            if (entry.actorId != actorId) continue
            if (entry.kind != LedgerKind.TOLD && entry.kind != LedgerKind.CHOICE) continue
            for (id in entry.characterIds) lookup(Character.normalizeId(id))?.team?.let { out += it }
            for (seat in entry.targetIds) {
                state.player(seat)?.characterId?.let(lookup)?.team?.let { out += it }
            }
        }
        return out.toList()
    }

    /**
     * Cycles elapsed since `(sourceId, label)` was placed on [playerId] — the
     * label-independent way to run any countdown. Null when it is not placed.
     */
    fun cyclesSince(state: GameState, playerId: Long, sourceId: String, label: String): Int? {
        val key = Tokens.key(sourceId, label)
        val placed = state.player(playerId)?.reminders?.firstOrNull { Tokens.key(it) == key }
        if (placed != null) return state.cycle - placed.placedCycle
        val effect = state.effects.firstOrNull {
            it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
        }
        return effect?.let { state.cycle - it.createdCycle }
    }

    /** Was this seat impaired at any point in [fromCycle]..[toCycle]? Reads IMPAIRMENT_SPAN. */
    fun wasImpairedDuring(
        state: GameState,
        playerId: Long,
        fromCycle: Int,
        toCycle: Int,
    ): Boolean = state.ledger.any {
        it.kind == LedgerKind.IMPAIRMENT_SPAN &&
            it.actorId == playerId &&
            it.cycle <= toCycle &&
            (it.resolvedCycle ?: Int.MAX_VALUE) >= fromCycle
    }
}
