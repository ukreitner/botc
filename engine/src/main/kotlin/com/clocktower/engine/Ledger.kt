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
 */
@Serializable
data class LedgerEntry(
    val id: Long,
    /** Same numbering as [DeathEvent]: day N follows night N. */
    val cycle: Int,
    val atNight: Boolean,
    val kind: LedgerKind,
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

/** Append-only writers. Each stamps cycle/atNight from the state and allocates the id (WP3). */
object Ledger {

    fun record(state: GameState, entry: LedgerEntry): GameState = TODO("WP3")

    fun choice(
        state: GameState,
        sourceId: String,
        actorId: Long?,
        targetIds: List<Long>,
        characterIds: List<String> = emptyList(),
        impaired: Boolean = false,
        byStoryteller: Boolean = false,
    ): GameState = TODO("WP3")

    fun told(
        state: GameState,
        playerId: Long,
        sourceId: String,
        shown: String,
        impaired: Boolean = false,
        text: String = "",
    ): GameState = TODO("WP3")

    fun statement(
        state: GameState,
        speakerId: Long?,
        sourceId: String,
        text: String,
        targetIds: List<Long> = emptyList(),
        characterIds: List<String> = emptyList(),
        genuine: Boolean = true,
    ): GameState = TODO("WP3")

    fun private(
        state: GameState,
        playerId: Long,
        sourceId: String,
        text: String,
        shown: String,
    ): GameState = TODO("WP3")

    fun ruling(
        state: GameState,
        sourceId: String,
        playerId: Long?,
        text: String,
        characterIds: List<String> = emptyList(),
    ): GameState = TODO("WP3")

    fun announce(state: GameState, text: String, sourceId: String = ""): GameState = TODO("WP3")

    fun woke(state: GameState, playerId: Long, sourceId: String, ownAbility: Boolean): GameState =
        TODO("WP3")

    fun malfunction(
        state: GameState,
        playerId: Long,
        sourceId: String,
        reason: String,
    ): GameState = TODO("WP3")

    fun spent(state: GameState, sourceId: String, actorId: Long): GameState = TODO("WP3")

    fun markAnnounced(state: GameState, id: Long): GameState = TODO("WP3")

    fun setVerdict(state: GameState, id: Long, verdict: Verdict): GameState = TODO("WP3")

    /** Sets `resolvedCycle = state.cycle`. */
    fun resolve(state: GameState, id: Long): GameState = TODO("WP3")

    fun edit(state: GameState, id: Long, transform: (LedgerEntry) -> LedgerEntry): GameState =
        TODO("WP3")

    fun delete(state: GameState, id: Long): GameState = TODO("WP3")
}

/** Read-only queries. Nothing here is stored; everything derives from the ledger (WP3). */
object Memory {

    /** The most recent CHOICE by [sourceId] (optionally by one holder) strictly before [beforeCycle]. */
    fun lastChoice(
        state: GameState,
        sourceId: String,
        holderId: Long? = null,
        beforeCycle: Int = state.cycle,
    ): LedgerEntry? = TODO("WP3")

    /** Seats [sourceId] may NOT pick tonight because of a "different to last night" clause. */
    fun forbiddenTargets(state: GameState, sourceId: String, holderId: Long? = null): Set<Long> =
        TODO("WP3")

    /** Every seat [sourceId] has ever chosen — "cannot learn the same evil player twice". */
    fun everChosen(state: GameState, sourceId: String, holderId: Long? = null): Set<Long> =
        TODO("WP3")

    fun choseNobodyLastNight(state: GameState, sourceId: String, holderId: Long? = null): Boolean =
        TODO("WP3")

    fun isSpent(state: GameState, sourceId: String, actorId: Long? = null): Boolean = TODO("WP3")

    fun statementsOn(
        state: GameState,
        day: Int,
        sourceId: String? = null,
        speakerId: Long? = null,
    ): List<LedgerEntry> = TODO("WP3")

    fun unresolved(state: GameState, sourceId: String, day: Int): List<LedgerEntry> = TODO("WP3")

    fun pendingAnnouncements(state: GameState): List<LedgerEntry> = TODO("WP3")

    /**
     * Everything ever told to, chosen by, or said by one seat — merged with deaths,
     * nominations, votes and executions. This is the seat sheet's History section.
     */
    fun forPlayer(state: GameState, playerId: Long): List<LedgerEntry> = TODO("WP3")

    fun ruling(state: GameState, playerId: Long, askedBy: String): LedgerEntry? = TODO("WP3")

    fun typesSeen(state: GameState, lookup: (String) -> Character?, actorId: Long): List<Team> =
        TODO("WP3")

    fun cyclesSince(state: GameState, playerId: Long, sourceId: String, label: String): Int? =
        TODO("WP3")

    /** Was this seat impaired at any point in [fromCycle]..[toCycle]? Reads IMPAIRMENT_SPAN. */
    fun wasImpairedDuring(
        state: GameState,
        playerId: Long,
        fromCycle: Int,
        toCycle: Int,
    ): Boolean = TODO("WP3")
}
