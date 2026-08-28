package com.clocktower.grimoire.ui.screens.day

import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.GameState
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Player
import com.clocktower.engine.VoteRules
import com.clocktower.engine.Voting
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the storyteller has picked this seat as. */
enum class SeatPick { NONE, NOMINATOR, NOMINEE }

/**
 * One seat on the pinned nomination ring. Ineligible seats are DIMMED but
 * still tappable — a dead Banshee, a Butcher's second nomination, a Riot
 * re-nomination and the Bishop are all legal somewhere, so a hard-disabled
 * chip with no reason is always wrong (ux/day-screen finding 14).
 */
data class RingSeat(
    val id: Long,
    val name: String,
    /** 1-based seat number, as the table counts them. */
    val seatNumber: Int,
    val alive: Boolean,
    val isTraveller: Boolean,
    val ghostVoteSpent: Boolean,
    val pick: SeatPick,
    /** False when the rules say no; the tap still works, with a reason. */
    val allowed: Boolean,
    /** Storyteller-voice reason a dimmed seat is dimmed. Empty when allowed. */
    val reason: String,
    /** Fractional position on the ring, 0f..1f in both axes. */
    val x: Float,
    val y: Float,
)

/** Everything the vote panel renders, already decided. */
data class VoteView(
    val nomineeId: Long?,
    val nomineeName: String,
    val nominatorName: String,
    val isExile: Boolean,
    /** Weighted tally — what the rules use. */
    val tally: Int,
    val threshold: Int,
    val result: NominationResult,
    /** "Fay is about to die", "Tie at 5 — …", "Fay is safe". */
    val outcomeLine: String,
    /** Voters in the order hands are counted: clockwise from the nominee's left. */
    val order: List<Long>,
    /** Seats that may not vote at all, with the reason. */
    val ineligible: Map<Long, String>,
    /** Hands that are up but do not count (a Butler without their Master). */
    val uncounted: Map<Long, String>,
    /** Seats that must have a hand up (a Zealot at 5+ alive). */
    val mustVote: List<Long>,
    /** Per-voter weight where it is not 1: `×3`, `−1`, `×2`. */
    val weights: Map<Long, Int>,
    /** One line per modifier the snapshot applied. */
    val reasons: List<String>,
    /** A sober living Organ Grinder: the count, the verdict and the block are secret. */
    val secret: Boolean,
    val rules: VoteRules,
)

object NominationModel {

    /** The Atheist's and the Bishop's non-seat nominee. */
    const val STORYTELLER_SEAT_ID: Long = GameState.STORYTELLER_SEAT_ID

    /**
     * The ring, in seat order, laid out on a circle starting at the top and
     * running clockwise — the same spatial arrangement as the table, which is
     * what makes "two taps, no name hunt" possible (§E).
     */
    fun ring(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): List<RingSeat> {
        val seats = state.seats
        return seats.mapIndexed { index, player ->
            val pick = when (player.id) {
                nominatorId -> SeatPick.NOMINATOR
                nomineeId -> SeatPick.NOMINEE
                else -> SeatPick.NONE
            }
            // The role a fresh tap would assign decides which rule to consult.
            val asNominee = nominatorId != null && pick != SeatPick.NOMINATOR
            val right = if (asNominee) {
                DayRules.canBeNominated(state, lookup, player.id)
            } else {
                DayRules.canNominate(state, lookup, player.id)
            }
            val (x, y) = position(index, seats.size)
            RingSeat(
                id = player.id,
                name = player.name,
                seatNumber = index + 1,
                alive = player.alive,
                isTraveller = player.isTraveller,
                ghostVoteSpent = !player.alive && player.ghostVoteUsed,
                pick = pick,
                allowed = right.allowed,
                reason = if (right.allowed) "" else right.reason,
                x = x,
                y = y,
            )
        }
    }

    /**
     * Fractional ring position for seat [index] of [count], clockwise from the
     * top. Pure geometry so the layout is measurable without a composition.
     */
    fun position(index: Int, count: Int): Pair<Float, Float> {
        if (count <= 0) return 0.5f to 0.5f
        val angle = -PI / 2 + 2 * PI * index / count
        return (0.5f + RADIUS * cos(angle).toFloat()) to (0.5f + RADIUS * sin(angle).toFloat())
    }

    /** Hands are counted clockwise starting to the nominee's left (wiki). */
    fun voteOrder(state: GameState, nomineeId: Long?): List<Player> {
        val seats = state.seats
        if (seats.isEmpty()) return emptyList()
        val start = seats.indexOfFirst { it.id == nomineeId }
        if (start < 0) return seats
        return (1..seats.size).map { seats[(start + it) % seats.size] }
    }

    /** Everything the vote panel needs, computed from the engine's own snapshot. */
    @Suppress("LongParameterList")
    fun voteView(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
        voterIds: Set<Long>,
    ): VoteView {
        val nominee = nomineeId?.let { state.player(it) }
        val isExile = nominee?.isTraveller == true
        val rules = DayRules.voteRules(state, lookup, isExile)
        val order = voteOrder(state, nomineeId).map { it.id }
        val orderedVoters = order.filter { it in voterIds }
        val tally = DayRules.tally(state, lookup, orderedVoters, isExile)
        val secret = !isExile && DayRules.secretVoting(state, lookup)

        val ineligible = state.seats
            .filterNot { it.id in rules.eligibleVoterIds }
            .associate { it.id to ineligibleReason(it) }
        val uncounted = orderedVoters
            .filter { DayRules.butlerVotingIllegally(state, lookup, it, orderedVoters) }
            .associateWith { id ->
                val name = state.player(id)?.name ?: "That seat"
                if (secret) {
                    "$name's hand is up but doesn't count — their Master's hand is down."
                } else {
                    "$name's Master is not voting — tally it anyway, then check."
                }
            }

        val highest = DayRules.highestVotesToday(state)
        val result = when {
            isExile -> if (tally >= rules.threshold) {
                NominationResult.ABOUT_TO_DIE
            } else {
                NominationResult.SAFE
            }

            else -> Voting.outcome(tally, rules.threshold, highest)
        }
        return VoteView(
            nomineeId = nomineeId,
            nomineeName = nominee?.name ?: "?",
            nominatorName = nominatorId?.let { state.player(it)?.name } ?: "?",
            isExile = isExile,
            tally = tally,
            threshold = rules.threshold,
            result = result,
            outcomeLine = outcomeLine(state, nominee?.name, isExile, result, tally, rules.threshold),
            order = order,
            ineligible = ineligible,
            uncounted = uncounted,
            mustVote = DayRules.mustVote(state, lookup),
            weights = rules.weights.filterValues { it != 1 },
            reasons = rules.reasons,
            secret = secret,
            rules = rules,
        )
    }

    /** The "Lock in" label states the RESULT, never an abstraction (§F). */
    fun lockInLabel(view: VoteView): String = when {
        view.secret -> "Lock in silently"
        view.isExile && view.result == NominationResult.ABOUT_TO_DIE ->
            "Lock in: ${view.nomineeName} is EXILED (${view.tally} of ${view.threshold})"

        view.result == NominationResult.ABOUT_TO_DIE ->
            "Lock in: ${view.nomineeName} is ON THE BLOCK (${view.tally} of ${view.threshold})"

        view.result == NominationResult.TIED ->
            "Lock in: TIE at ${view.tally} — nobody is about to die"

        else -> "Lock in: ${view.nomineeName} is SAFE (${view.tally} of ${view.threshold})"
    }

    @Suppress("LongParameterList")
    fun outcomeLine(
        state: GameState,
        nomineeName: String?,
        isExile: Boolean,
        result: NominationResult,
        tally: Int,
        threshold: Int,
    ): String {
        val who = nomineeName ?: "They"
        return when (result) {
            NominationResult.ABOUT_TO_DIE ->
                if (isExile) "$who is exiled — $tally of $threshold." else "$who is about to die."

            NominationResult.TIED -> DayModel.tieLine(state)
            NominationResult.WITHDRAWN -> "Withdrawn."
            NominationResult.SAFE -> "$who is safe — $tally of $threshold."
        }
    }

    /** The one-line prompt above the ring. */
    fun ringPrompt(nominatorId: Long?, nomineeId: Long?, nominatorName: String?): String = when {
        nominatorId == null -> "Tap who nominates, then who they nominate."
        nomineeId == null -> "${nominatorName ?: "They"} nominates… tap the nominee."
        else -> "Check this, then call the vote."
    }

    private fun ineligibleReason(player: Player): String = when {
        !player.alive && player.ghostVoteUsed -> "${player.name} — ghost vote already spent."
        !player.alive -> "${player.name} — dead."
        else -> "${player.name} may not vote."
    }

    /**
     * How wide one seat may be, in dp, so a twenty-seat ring does not overlap
     * itself: the arc each seat owns, clamped so a seven-seat table does not
     * grow absurd chips and a twenty-seat one still fits a short name.
     */
    fun seatWidthDp(count: Int, boxWidthDp: Float): Float {
        if (count <= 0 || boxWidthDp <= 0f) return MAX_SEAT_DP
        val circumference = 2f * PI.toFloat() * RADIUS * boxWidthDp
        return (circumference / count).coerceIn(MIN_SEAT_DP, MAX_SEAT_DP)
    }

    /** How far out from the centre the seats sit, as a fraction of the box. */
    const val RADIUS: Float = 0.40f

    /** Never narrower than this: a name plus a seat number has to fit (§3.4.7). */
    const val MIN_SEAT_DP: Float = 46f
    const val MAX_SEAT_DP: Float = 78f
}
