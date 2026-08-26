package com.clocktower.grimoire.ui.screens.day

import com.clocktower.engine.Briefing
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.Briefings
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameState
import com.clocktower.engine.Ledger
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Voting

/**
 * The day timeline's model — pure Kotlin, no Compose, so every line of it is
 * testable from `tools/uicheck/src/test` (ux/day-screen §0/§A).
 *
 * The Day tab is one screen, one *stage*, one primary action: stages appear in
 * the order the storyteller lives them and collapse to a single summary line
 * once they are done. Nothing here branches on a character id — the per-
 * character content arrives already rendered inside a [Briefing] or a
 * `NominationTrigger` (I1).
 */
enum class DayStage {
    /** What must be said out loud before the floor opens. */
    DAWN,

    /** What is true today: protections, madness, clocks, secret voting. */
    BRIEFING,

    /** Public statements and claims — works with nothing in play. */
    SAID,

    /** Nomination ring, the pre-flight check, the vote. */
    NOMINATIONS,

    /** Close the day: execute, or record that nobody was. */
    DUSK,
}

/** How loudly a stage row asks to be looked at. */
enum class StageTone { QUIET, ACTION, ALERT }

/** One collapsible row of the timeline. */
data class StageRow(
    val stage: DayStage,
    val title: String,
    /** The one-line summary shown while the stage is collapsed. */
    val summary: String,
    /** Count badge; 0 renders none. */
    val badge: Int = 0,
    val tone: StageTone = StageTone.QUIET,
    /** True when everything this stage asks for has been done. */
    val complete: Boolean = false,
)

/** The sticky strip above the timeline. */
data class DayStats(
    val headline: String,
    val detail: String,
    /** "On the block: Fay — 5 votes", or the reason nobody is. */
    val blockLine: String,
    val onBlockId: Long?,
    /** A sober living Organ Grinder hides every tally on this tab. */
    val secret: Boolean,
)

object DayModel {

    /** The whole timeline, in order. */
    fun stages(
        state: GameState,
        lookup: (String) -> Character?,
        dawn: Briefing?,
        dayStart: Briefing,
        dismissed: Set<String>,
    ): List<StageRow> = listOf(
        dawnRow(dawn, dismissed),
        briefingRow(dayStart),
        saidRow(state, dayStart),
        nominationsRow(state, lookup),
        duskRow(state, lookup),
    )

    /**
     * Which stage opens by default: the first incomplete one that is asking for
     * something. Dawn while it still owes sentences, then the day's business.
     */
    fun autoExpanded(rows: List<StageRow>): DayStage =
        rows.firstOrNull { !it.complete && it.tone != StageTone.QUIET }?.stage
            ?: rows.firstOrNull { !it.complete }?.stage
            ?: DayStage.NOMINATIONS

    fun stats(state: GameState, lookup: (String) -> Character?): DayStats {
        val alive = state.aliveCountWithTravellers
        val threshold = Voting.executionThreshold(alive)
        val ghosts = state.seats.count { !it.alive && !it.ghostVoteUsed }
        val secret = DayRules.secretVoting(state, lookup)
        val highest = DayRules.highestVotesToday(state)
        val onBlockId = DayRules.aboutToDie(state)
        val onBlock = onBlockId?.let { state.player(it) }

        val detail = buildString {
            append("$alive alive · $threshold to execute")
            if (ghosts > 0) append(" · $ghosts ghost ${if (ghosts == 1) "vote" else "votes"}")
            // The tally to beat is part of the secret an Organ Grinder keeps.
            if (!secret && highest > 0) append(" · $highest to beat")
        }
        val blockLine = when {
            onBlock != null && secret -> "Someone is about to die."
            onBlock != null -> "On the block: ${onBlock.name}" +
                (highest.takeIf { it > 0 }?.let { " — $it votes" } ?: "")

            secret -> "Nobody is about to die."
            highest > 0 -> tieLine(state)
            else -> "No one is about to die."
        }
        return DayStats(
            headline = "Day ${state.cycle}",
            detail = detail,
            blockLine = blockLine,
            onBlockId = onBlockId,
            secret = secret,
        )
    }

    /** Finding 24: name the tie and the number needed to beat it. */
    fun tieLine(state: GameState): String {
        val highest = DayRules.highestVotesToday(state)
        if (highest <= 0) return "No one is about to die."
        val tied = state.nominations
            .filter { it.day == state.cycle && !it.isExile && it.votes == highest }
            .filter { it.result == NominationResult.TIED || it.result == NominationResult.ABOUT_TO_DIE }
            .mapNotNull { state.player(it.nomineeId)?.name }
            .distinct()
        val who = when (tied.size) {
            0 -> ""
            1 -> " — ${tied.first()}"
            else -> " — " + tied.dropLast(1).joinToString(", ") + " and " + tied.last()
        }
        return "Tie at $highest$who. No one is about to die. ${highest + 1} to beat it."
    }

    // ---- individual rows -------------------------------------------------

    private fun dawnRow(dawn: Briefing?, dismissed: Set<String>): StageRow {
        val items = dawn?.items.orEmpty()
        val owed = items.filter {
            it.kind == BriefingKind.ANNOUNCE || it.kind == BriefingKind.TODO_ASK
        }
        val left = owed.count { it.key !in dismissed }
        return StageRow(
            stage = DayStage.DAWN,
            title = "Dawn",
            summary = when {
                items.isEmpty() -> "Nobody died last night. Say so."
                left == 0 -> "All said."
                else -> "$left thing${if (left == 1) "" else "s"} to announce"
            },
            badge = left,
            tone = if (left > 0) StageTone.ACTION else StageTone.QUIET,
            complete = left == 0,
        )
    }

    private fun briefingRow(dayStart: Briefing): StageRow {
        val standing = dayStart.of(BriefingKind.STANDING_FACT).size
        val asks = dayStart.of(BriefingKind.TODO_ASK).size
        val alerts = dayStart.items.count { it.severity == BriefingSeverity.ALERT }
        return StageRow(
            stage = DayStage.BRIEFING,
            title = "Morning briefing",
            summary = when {
                standing == 0 && asks == 0 -> "Nothing constrains today."
                asks == 0 -> "$standing standing fact${if (standing == 1) "" else "s"}"
                else -> "$standing standing · $asks to collect"
            },
            badge = asks,
            tone = when {
                alerts > 0 -> StageTone.ALERT
                asks > 0 -> StageTone.ACTION
                else -> StageTone.QUIET
            },
            complete = asks == 0,
        )
    }

    private fun saidRow(state: GameState, dayStart: Briefing): StageRow {
        val today = state.ledger.count {
            it.kind == LedgerKind.STATEMENT && it.cycle == state.cycle && !it.atNight
        }
        // The collect list is the engine's own "you have not written this down
        // yet" nudge — one TODO_ASK per unrecorded Gossip / Juggler / Savant row.
        val owed = dayStart.items.count { it.actionId.startsWith(Briefings.ACTION_RECORD) }
        return StageRow(
            stage = DayStage.SAID,
            title = "What was said",
            summary = when {
                today == 0 && owed == 0 -> "Tap a seat, then type or dictate one line."
                owed > 0 -> "$today recorded · $owed still to collect"
                else -> "$today recorded"
            },
            badge = today,
            tone = if (owed > 0) StageTone.ACTION else StageTone.QUIET,
            complete = owed == 0,
        )
    }

    private fun nominationsRow(state: GameState, lookup: (String) -> Character?): StageRow {
        val today = state.nominations.filter { it.day == state.cycle }
        val closed = DayRules.nominationsClosed(state, lookup)
        return StageRow(
            stage = DayStage.NOMINATIONS,
            title = "Nominations",
            summary = when {
                closed -> DayRules.nominationsClosedReason(state, lookup)
                today.isEmpty() -> "Tap who nominates, then who they nominate."
                else -> today.joinToString(" · ") { n ->
                    val nominee = state.player(n.nomineeId)?.name ?: "?"
                    val nominator = state.player(n.nominatorId)?.name ?: "?"
                    "$nominator » $nominee"
                }
            },
            badge = today.size,
            tone = if (closed) StageTone.QUIET else StageTone.ACTION,
            complete = closed,
        )
    }

    private fun duskRow(state: GameState, lookup: (String) -> Character?): StageRow {
        val record = DayRules.executionToday(state)
        val onBlock = DayRules.aboutToDie(state)?.let { state.player(it) }
        return StageRow(
            stage = DayStage.DUSK,
            title = "Dusk",
            summary = when {
                record?.outcome == ExecutionOutcome.NO_EXECUTION ->
                    "No execution today — the day is over."

                record != null -> DayRules.nominationsClosedReason(state, lookup)
                onBlock != null -> "On the block: ${onBlock.name}."
                else -> "No one is about to die. There is no execution today."
            },
            tone = when {
                record != null -> StageTone.QUIET
                onBlock != null -> StageTone.ALERT
                else -> StageTone.ACTION
            },
            complete = record != null,
        )
    }

    /**
     * Ledger sources whose statements the storyteller still owes today, from the
     * DAY_START collect list. Never a hard-coded character list (I1).
     */
    fun collectSources(dayStart: Briefing): List<String> = dayStart.items
        .filter { it.actionId.startsWith(Briefings.ACTION_RECORD) }
        .map { it.actionId.removePrefix(Briefings.ACTION_RECORD) }
        .filter { it.isNotBlank() }
        .distinct()

    /** Statement source ids offered as chips: the collect list, then a plain claim. */
    fun statementSources(dayStart: Briefing): List<String> =
        (collectSources(dayStart) + Ledger.Sources.CLAIM).distinct()
}
