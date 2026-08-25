package com.clocktower.engine

import kotlinx.serialization.Serializable

/** A frozen snapshot of how ONE nomination was voted. Persisted on the Nomination (lead D27). */
@Serializable
data class VoteRules(
    val eligibleVoterIds: List<Long>,
    val threshold: Int,
    /** False under a sober Voudon and for every exile. */
    val spendsGhostVotes: Boolean,
    /** Per-voter weight. Absent = 1. Bureaucrat 3, Thief -1, Banshee 2. */
    val weights: Map<Long, Int> = emptyMap(),
    /** One line per modifier applied, for the log and the tally explanation. */
    val reasons: List<String> = emptyList(),
) {
    fun weightOf(playerId: Long): Int = weights[playerId] ?: 1

    fun tally(voterIds: Collection<Long>): Int = voterIds.sumOf { weightOf(it) }
}

@Serializable
enum class NominationResult { ABOUT_TO_DIE, SAFE, TIED, WITHDRAWN }

/** The Judge forces one nomination to pass or fail. */
@Serializable
enum class JudgeForce { PASS, FAIL }

/** A nomination and its vote tally. */
@Serializable
data class Nomination(
    val day: Int,
    val nominatorId: Long,
    /** May be [GameState.STORYTELLER_SEAT_ID] in an Atheist game (lead D44). */
    val nomineeId: Long,
    /** The WEIGHTED tally — what the rules use. */
    val votes: Int = 0,
    /** Raw hands raised, clock order from the nominee's left. Never weighted. */
    val voterIds: List<Long> = emptyList(),
    val result: NominationResult = NominationResult.SAFE,
    val isExile: Boolean = false,

    // ---- added ----
    /** The FULL rules snapshot at the moment of the tally. Never recompute from live state. */
    val voteRules: VoteRules? = null,
    /** Extra hands one voter raised (the awoken Banshee's second). */
    val extraVotes: Map<Long, Int> = emptyMap(),
    /** Registration snapshot (lead D51) — Town Crier and Flowergirl read THIS. */
    val nominatorCharacterId: String? = null,
    val nominatorTeams: Set<Team> = emptySet(),
    val demonIdsAtRecord: List<Long> = emptyList(),
    val registersRuling: String = "",
    /** The nominee publicly claimed Goblin before votes were called. */
    val goblinClaim: Boolean = false,
    val judgeForced: JudgeForce? = null,
    /** Ability triggers that fired on this nomination, for the log. */
    val triggersFired: List<String> = emptyList(),
)

/** What a nomination trigger does to the day. */
@Serializable
enum class TriggerKind {
    /** The engine kills someone the moment the nomination is declared. */
    AUTO_DEATH,

    /** The engine executes someone immediately (consuming the day's execution). */
    AUTO_EXECUTION,

    /** No more nominations today. */
    END_DAY,

    /** Changes how this vote is tallied or who may vote. */
    VOTE_MODIFIER,

    /** The storyteller must decide something before votes are called. */
    CHOICE,

    /** Information only. */
    WARN,
}

@Serializable
data class TriggerOption(val id: String, val label: String, val isDefault: Boolean = false)

@Serializable
data class NominationTrigger(
    val kind: TriggerKind,
    val sourceId: String,
    val actorId: Long? = null,
    val targetId: Long? = null,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work — surfaced as a caution, never as suppression. */
    val impaired: Boolean = false,
)

@Serializable
data class NominationCheck(
    val legal: Boolean,
    /** Hard rule violations: "Dana has already nominated today". */
    val blockers: List<String> = emptyList(),
    /** Legal but unusual: "Nominating a dead player — allowed, no ghost vote at stake". */
    val cautions: List<String> = emptyList(),
    val triggers: List<NominationTrigger> = emptyList(),
)

/** Vote thresholds. Kept from the pre-split engine; [DayRules.voteRules] supersedes it. */
object Voting {
    /** Threshold for an execution among [aliveCount] living players. */
    fun executionThreshold(aliveCount: Int): Int = (aliveCount + 1) / 2

    /** Threshold for a traveller exile among [totalCount] players. */
    fun exileThreshold(totalCount: Int): Int = (totalCount + 1) / 2

    /**
     * Whether [votes] makes the nominee about-to-die, given the current
     * highest tally [currentHighest] today (0 if none) and the threshold.
     * Equal to the highest is a tie (no one dies); beating it marks the new
     * about-to-die player.
     */
    fun outcome(votes: Int, threshold: Int, currentHighest: Int): NominationResult = when {
        votes < threshold -> NominationResult.SAFE
        votes > currentHighest -> NominationResult.ABOUT_TO_DIE
        votes == currentHighest -> NominationResult.TIED
        else -> NominationResult.SAFE // below today's highest tally
    }
}

/**
 * Nomination, voting and day predicates (WP3). WP0 moved the existing
 * bookkeeping helpers here verbatim; everything else is WP3's.
 */
object DayRules {

    /** May this player do this, and why not. */
    data class Right(val allowed: Boolean, val reason: String = "")

    // ---- who may nominate / be nominated ----

    /**
     * Bishop: only the ST nominates. Butcher: one extra after the day's first execution.
     * Banshee (awoken): twice per day, and may nominate while dead. Golem: once per game.
     */
    fun canNominate(state: GameState, lookup: (String) -> Character?, playerId: Long): Right =
        TODO("WP3")

    /** Anyone not nominated today, DEAD INCLUDED (rules: dead players may be executed). */
    fun canBeNominated(state: GameState, lookup: (String) -> Character?, playerId: Long): Right =
        TODO("WP3")

    /** Pure pre-flight, called on every chip tap so the UI renders live. */
    fun checkNomination(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): NominationCheck = TODO("WP3")

    /** Applies a trigger the ST accepted (or declined with optionId = "skip"). */
    fun applyTrigger(
        state: GameState,
        lookup: (String) -> Character?,
        trigger: NominationTrigger,
        optionId: String,
    ): GameState = TODO("WP3")

    /** Records the nomination. Refuses an illegal one unless [force] — the ST always wins. */
    fun record(
        state: GameState,
        lookup: (String) -> Character?,
        nomination: Nomination,
        force: Boolean = false,
    ): GameState = TODO("WP3")

    /** WP0 move of `GameActions.recordNomination` — appends with no checks. */
    fun recordNomination(state: GameState, nomination: Nomination): GameState =
        state.copy(nominations = state.nominations + nomination)

    // ---- voting ----

    /** Computes the snapshot to freeze on the Nomination. */
    fun voteRules(state: GameState, lookup: (String) -> Character?, isExile: Boolean): VoteRules =
        TODO("WP3")

    /** Zealot seats that must have a hand up (5+ alive). */
    fun mustVote(state: GameState, lookup: (String) -> Character?): List<Long> = TODO("WP3")

    /** A sober living Organ Grinder: eyes-closed voting, tally and block hidden. */
    fun secretVoting(state: GameState, lookup: (String) -> Character?): Boolean = TODO("WP3")

    /** Legion: an execution fails if only evil players voted. */
    fun executionFailsOnlyEvilVoted(
        state: GameState,
        lookup: (String) -> Character?,
        voterIds: List<Long>,
    ): Boolean = TODO("WP3")

    // ---- derived day state (no stored flags) ----

    fun executionToday(state: GameState): ExecutionRecord? =
        state.executions.lastOrNull { it.day == state.cycle }

    /** True when the day's one execution has been spent. SURVIVED counts. */
    fun executionSpent(state: GameState): Boolean =
        state.executions.any { it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION }

    /** Derived from the executions list — there is no stored day-closed boolean (lead D30). */
    fun nominationsClosed(state: GameState, lookup: (String) -> Character?): Boolean = TODO("WP3")

    fun secondExecutionAllowed(state: GameState, lookup: (String) -> Character?): Boolean =
        TODO("WP3")

    fun immuneToDayDeath(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): Boolean = TODO("WP3")

    // ---- existing helpers, moved here from GameActions in WP0 ----

    /** Highest passing vote tally so far today (for tie/beat logic). */
    fun highestVotesToday(state: GameState): Int =
        state.nominations
            .filter { it.day == state.cycle && !it.isExile }
            .filter { it.result == NominationResult.ABOUT_TO_DIE || it.result == NominationResult.TIED }
            .maxOfOrNull { it.votes } ?: 0

    /** Players a nominator hasn't yet nominated today, per one-nomination rules. */
    fun hasNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nominatorId == playerId && !it.isExile }

    fun hasBeenNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nomineeId == playerId && !it.isExile }

    /**
     * Who is currently on the block today, derived from the nomination
     * sequence: a passing tally that beats the previous highest puts its
     * nominee on the block; a later equal tally clears the block (tie).
     */
    fun aboutToDie(state: GameState): Long? {
        var onBlock: Long? = null
        for (n in state.nominations.filter { it.day == state.cycle && !it.isExile }) {
            when (n.result) {
                NominationResult.ABOUT_TO_DIE -> onBlock = n.nomineeId
                NominationResult.TIED -> onBlock = null
                else -> Unit
            }
        }
        return onBlock
    }
}
