package com.clocktower.engine

/**
 * One flat, totally ordered transcript, shared by both platforms (WP3).
 *
 * Everything that happened is already stored: deaths, nominations, executions,
 * identity changes and the ledger. This merges them into a single order —
 * `(cycle, night before day, source, position)` — so the log dialog, the export
 * and the seat sheet all read the same list.
 */
object GameLog {

    data class Row(val cycle: Int, val atNight: Boolean, val seq: Long, val text: String)

    /**
     * Merges deaths, nominations (with VOTER NAMES), executions, identity changes
     * and the whole ledger, ordered by (cycle, night-before-day, seq).
     */
    fun rows(state: GameState, lookup: (String) -> Character?): List<Row> {
        fun name(id: Long?): String = when (id) {
            null -> "someone"
            GameState.STORYTELLER_SEAT_ID -> "the storyteller"
            else -> state.player(id)?.name ?: "seat $id"
        }

        fun names(ids: Collection<Long>): String =
            if (ids.isEmpty()) "nobody" else ids.joinToString(", ") { name(it) }

        fun character(id: String?): String =
            id?.let { lookup(Character.normalizeId(it))?.name ?: it }.orEmpty()

        // (cycle, atNight, sourceRank, position, text). The sort is stable, so the
        // per-source append order survives inside one phase.
        data class Draft(
            val cycle: Int,
            val atNight: Boolean,
            val rank: Int,
            val position: Int,
            val text: String,
        )

        val drafts = mutableListOf<Draft>()

        state.ledger.forEachIndexed { i, entry ->
            ledgerText(entry, ::name, ::names, ::character)?.let {
                drafts += Draft(entry.cycle, entry.atNight, RANK_LEDGER, i, it)
            }
        }

        state.identityLog.forEachIndexed { i, record ->
            drafts += Draft(
                record.cycle, record.atNight, RANK_IDENTITY, i,
                "${name(record.playerId)} is now " +
                    "${character(record.toCharacterId).ifEmpty { "nobody" }} " +
                    "(was ${character(record.fromCharacterId).ifEmpty { "nobody" }}) — " +
                    record.reason.name.lowercase().replace('_', ' '),
            )
        }

        state.nominations.forEachIndexed { i, nomination ->
            val verb = if (nomination.isExile) "calls for the exile of" else "nominates"
            val voters = if (nomination.voterIds.isEmpty()) {
                ""
            } else {
                " (${names(nomination.voterIds)})"
            }
            val verdict = when (nomination.result) {
                NominationResult.ABOUT_TO_DIE -> "about to die"
                NominationResult.TIED -> "tied — nobody is on the block"
                NominationResult.WITHDRAWN -> "withdrawn"
                NominationResult.SAFE -> "safe"
            }
            val threshold = nomination.voteRules?.threshold
            drafts += Draft(
                nomination.day, false, RANK_NOMINATION, i,
                "${name(nomination.nominatorId)} $verb ${name(nomination.nomineeId)} — " +
                    "${nomination.votes} vote${if (nomination.votes == 1) "" else "s"}$voters" +
                    (threshold?.let { ", needed $it" } ?: "") + ": $verdict",
            )
        }

        state.executions.forEachIndexed { i, record ->
            drafts += Draft(record.day, false, RANK_EXECUTION, i, executionText(record, ::name))
        }

        state.deaths.forEachIndexed { i, death ->
            val how = deathText(death)
            val extra = when {
                death.registeredOnly -> " (registers dead — still alive)"
                death.resurrectedAtCycle != null -> " (alive again on ${death.resurrectedAtCycle})"
                death.resurrected -> " (alive again)"
                else -> ""
            }
            drafts += Draft(
                death.day, death.atNight, RANK_DEATH, i,
                "${name(death.playerId)} dies — $how$extra",
            )
        }

        return drafts
            .sortedWith(
                compareBy<Draft> { it.cycle }
                    .thenByDescending { it.atNight }
                    .thenBy { it.rank }
                    .thenBy { it.position },
            )
            .mapIndexed { i, d -> Row(d.cycle, d.atNight, i.toLong(), d.text) }
    }

    /** The whole transcript as markdown, grouped by phase. */
    fun toMarkdown(state: GameState, lookup: (String) -> Character?): String {
        val out = StringBuilder()
        out.append("# Game log\n")
        var heading = ""
        for (row in rows(state, lookup)) {
            val next = if (row.atNight) "Night ${row.cycle}" else "Day ${row.cycle}"
            if (next != heading) {
                heading = next
                out.append("\n## ").append(heading).append('\n')
            }
            out.append("- ").append(row.text).append('\n')
        }
        if (heading.isEmpty()) out.append("\nNothing has happened yet.\n")
        return out.toString()
    }

    // ---- ordering ranks within one phase ----
    private const val RANK_LEDGER = 0
    private const val RANK_IDENTITY = 1
    private const val RANK_NOMINATION = 2
    private const val RANK_EXECUTION = 3
    private const val RANK_DEATH = 4

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun ledgerText(
        entry: LedgerEntry,
        name: (Long?) -> String,
        names: (Collection<Long>) -> String,
        character: (String?) -> String,
    ): String? {
        val source = character(entry.sourceId).ifEmpty { entry.sourceId }
        val impaired = if (entry.impaired) " (their ability was not working)" else ""
        return when (entry.kind) {
            LedgerKind.CHOICE -> {
                val picked = if (entry.targetIds.isEmpty() && entry.characterIds.isEmpty()) {
                    "nobody"
                } else {
                    (
                        entry.targetIds.map { name(it) } +
                            entry.characterIds.map { character(it) }
                        ).joinToString(", ")
                }
                "${name(entry.actorId)} ($source) chooses $picked$impaired"
            }

            LedgerKind.TOLD ->
                "${name(entry.actorId)} ($source) is shown ${entry.shown.ifEmpty { "nothing" }}" +
                    impaired

            LedgerKind.STATEMENT -> {
                val about = if (entry.targetIds.isEmpty()) "" else " about ${names(entry.targetIds)}"
                val bluff = if (!entry.genuine) " (bluffing)" else ""
                val verdict = verdictText(entry.verdict)
                "${name(entry.actorId)} says$about: \"${entry.text}\"$bluff$verdict"
            }

            LedgerKind.PRIVATE ->
                "${name(entry.actorId)} ($source), privately: \"${entry.text}\" -> " +
                    entry.shown.ifEmpty { "nothing" }

            LedgerKind.RULING -> {
                val who = entry.actorId?.let { "${name(it)}: " }.orEmpty()
                "Ruling — $who${entry.text}"
            }

            LedgerKind.ANNOUNCE -> {
                val owed = if (entry.announcePending) " (not yet said)" else ""
                "Announce: \"${entry.text}\"$owed"
            }

            LedgerKind.SPENT -> "$source is spent (${name(entry.actorId)})"

            LedgerKind.WOKE ->
                "${name(entry.actorId)} wakes" + if (entry.genuine) " for $source" else " (shown to)"

            LedgerKind.MALFUNCTION ->
                "${name(entry.actorId)}'s $source ability malfunctioned: ${entry.text}"

            // A span is a status window, not an event; the seat sheet renders it.
            LedgerKind.IMPAIRMENT_SPAN -> null

            LedgerKind.NOTE ->
                entry.actorId?.let { "${name(it)}: ${entry.text}" } ?: "Note: ${entry.text}"
        }
    }

    private fun verdictText(verdict: Verdict): String = when (verdict) {
        Verdict.UNJUDGED -> ""
        Verdict.TRUE -> " [true]"
        Verdict.FALSE -> " [false]"
        Verdict.A_TRUE -> " [A is true]"
        Verdict.B_TRUE -> " [B is true]"
        Verdict.BOTH_TRUE -> " [both true]"
        Verdict.NEITHER_TRUE -> " [neither true]"
        Verdict.ST_CHOICE -> " [storyteller's choice]"
    }

    private fun executionText(record: ExecutionRecord, name: (Long?) -> String): String =
        when (record.outcome) {
            ExecutionOutcome.NO_EXECUTION -> "No execution today."
            ExecutionOutcome.DIED -> {
                val instead = record.diedInsteadId
                    ?.let { " — ${name(it)} dies instead" }
                    .orEmpty()
                "${name(record.playerId)} is executed$instead (${viaText(record.via)})"
            }

            ExecutionOutcome.SURVIVED -> {
                val by = record.preventedBy.ifEmpty { "the storyteller" }
                "${name(record.playerId)} is executed and survives ($by)"
            }
        }

    private fun viaText(via: ExecutionVia): String = when (via) {
        ExecutionVia.VOTE -> "vote"
        ExecutionVia.VIRGIN -> "Virgin"
        ExecutionVia.VIZIER -> "Vizier"
        ExecutionVia.JUDGE -> "Judge"
        ExecutionVia.PSYCHOPATH -> "Psychopath"
        ExecutionVia.RIOT -> "Riot"
        ExecutionVia.STORYTELLER -> "storyteller"
    }

    private fun deathText(death: DeathEvent): String = when (death.cause) {
        DeathCause.EXECUTION -> "executed"
        DeathCause.EXILE -> "exiled"
        DeathCause.DEMON_KILL -> "killed by the Demon"
        DeathCause.EVIL_ABILITY -> "killed by an evil ability"
        DeathCause.GOOD_ABILITY -> "killed by a good ability"
        DeathCause.DAY_ABILITY -> "killed by a day ability"
        DeathCause.TRAVELLER_ABILITY -> "killed by a Traveller"
        DeathCause.STORYTELLER -> "the storyteller's decision"
        else -> "died at night"
    }
}
