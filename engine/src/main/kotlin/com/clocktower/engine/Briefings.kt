package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What kind of line this is. */
@Serializable
enum class BriefingKind {
    /** Say this out loud, in this order. */
    ANNOUNCE,

    /** For the storyteller only — never say it. */
    PRIVATE,

    /** A standing fact that constrains today. */
    STANDING_FACT,

    /** Something the storyteller must still do or collect. */
    TODO_ASK,

    /** A token that was just swept off the grimoire. */
    SWEPT,
}

@Serializable
enum class BriefingSeverity { INFO, ACTION, ALERT }

@Serializable
data class BriefingItem(
    /** Stable key for the ticked-off set; survives recomposition and undo. */
    val key: String,
    val kind: BriefingKind,
    val severity: BriefingSeverity = BriefingSeverity.INFO,
    val sourceId: String = "",
    /** Imperative, storyteller voice, ready to read aloud. */
    val text: String,
    val playerId: Long? = null,
    /** The prompt or ledger entry this discharges, if any. */
    val promptId: Long? = null,
    val ledgerId: Long? = null,
    /**
     * One-tap follow-through, as a stable string the UI maps to a handler:
     * "open-seat:7", "rerun-first-night:7", "record:gossip", "show-card:<spec>",
     * "resolve-prompt:12", "mark-announced:9".
     */
    val actionId: String = "",
)

/** One briefing. Serialisable so `lastDawn` / `lastDusk` can be frozen on the state. */
@Serializable
data class Briefing(
    val slot: BriefingSlot,
    val cycle: Int,
    val items: List<BriefingItem> = emptyList(),
) {
    fun of(kind: BriefingKind): List<BriefingItem> = items.filter { it.kind == kind }

    val announce: List<BriefingItem> get() = of(BriefingKind.ANNOUNCE)
    val private: List<BriefingItem> get() = of(BriefingKind.PRIVATE)
    val standing: List<BriefingItem> get() = of(BriefingKind.STANDING_FACT)
    val todo: List<BriefingItem> get() = of(BriefingKind.TODO_ASK)
}

/** Derived views over prompts + effects + ledger + deaths + executions (WP6). */
object Briefings {

    /**
     * Pure. NOTHING here is stored; [GameState.lastDawn] is a frozen snapshot,
     * not a source.
     */
    fun at(state: GameState, lookup: (String) -> Character?, slot: BriefingSlot): Briefing =
        TODO("WP6")
}
