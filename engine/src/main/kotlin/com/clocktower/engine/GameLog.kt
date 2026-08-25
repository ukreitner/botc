package com.clocktower.engine

/** One flat, totally ordered transcript, shared by both platforms (WP3). */
object GameLog {

    data class Row(val cycle: Int, val atNight: Boolean, val seq: Long, val text: String)

    /**
     * Merges deaths, nominations (with VOTER NAMES), executions, identity changes and
     * the whole ledger, ordered by (cycle, night-before-day, seq). A total order.
     */
    fun rows(state: GameState, lookup: (String) -> Character?): List<Row> = TODO("WP3")

    fun toMarkdown(state: GameState, lookup: (String) -> Character?): String = TODO("WP3")
}
