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
    /** A sober living Organ Grinder — or the house rule: the count is secret. */
    val secret: Boolean,
    val rules: VoteRules,
    /**
     * What to say while the eyes are closed. Never names the Organ Grinder when
     * the secrecy is only a house rule — the storyteller would be handing the
     * table a bluff they cannot back up.
     */
    val secretLine: String = "",
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
            )
        }
    }

    /** Clockwise from the top: seat 1 is at 12 o'clock, as the table sits. */
    private fun angle(index: Int, count: Int): Double =
        -PI / 2 + 2 * PI * index / count

    /**
     * The widest a seat may be at this vertical radius — no wider than the
     * horizontal gap between any adjacent pair the ring's height cannot pull
     * apart on its own. Unclamped, so [ringRadiusYDp] can tell "too narrow to
     * read" from "wide enough".
     *
     * The ring is an ellipse, not a circle: the box is far wider than it is
     * tall. Near 12 and 6 o'clock the seats sit side by side and their WIDTH is
     * what has to fit; near 3 and 9 o'clock they stack and their 48 dp HEIGHT
     * is. The old width was an arc length round a circle of the horizontal
     * radius, which is longer than either — so from about ten seats up every
     * pair on the diagonals overlapped, which is what `ui.py audit` reported as
     * "OVERLAPPING CLICKABLES", worst case 41 % (playtest D-6).
     */
    private fun widestSeatDp(count: Int, boxWidthDp: Float, radiusYDp: Float): Float {
        var widest = MAX_SEAT_DP
        for (i in 0 until count) {
            val a = angle(i, count)
            val b = angle((i + 1) % count, count)
            val dy = radiusYDp * kotlin.math.abs(sin(a) - sin(b)).toFloat()
            // Far enough apart vertically: their width is free.
            if (dy >= SEAT_HIT_DP + SEAT_CLEAR_DP) continue
            val dx = RADIUS * boxWidthDp * kotlin.math.abs(cos(a) - cos(b)).toFloat()
            widest = minOf(widest, dx - SEAT_CLEAR_DP)
        }
        return widest
    }

    /**
     * The ring's VERTICAL radius in dp: the smallest that leaves room for a
     * readable seat. A bigger table needs a taller ring, and nothing else will
     * do — 48 dp of touch target has to go somewhere.
     */
    fun ringRadiusYDp(
        count: Int,
        boxWidthDp: Float,
        maxRadiusYDp: Float = MAX_RADIUS_Y_DP,
    ): Float {
        val ceiling = maxOf(maxRadiusYDp, MIN_RADIUS_Y_DP)
        if (count <= 2 || boxWidthDp <= 0f) return MIN_RADIUS_Y_DP
        var ry = MIN_RADIUS_Y_DP
        while (ry < ceiling) {
            if (widestSeatDp(count, boxWidthDp, ry) >= MIN_SEAT_DP) return ry
            ry += RADIUS_STEP_DP
        }
        return ceiling
    }

    /**
     * How tall the pinned ring box has to be: the ellipse, one seat's hit
     * target, and a clear strip under the lowest seat so a list item scrolled
     * up against the ring can never share a pixel with it.
     */
    fun ringHeightDp(
        count: Int,
        boxWidthDp: Float,
        maxRadiusYDp: Float = MAX_RADIUS_Y_DP,
    ): Float = 2f * ringRadiusYDp(count, boxWidthDp, maxRadiusYDp) + SEAT_HIT_DP + RING_GAP_DP

    /**
     * The centre of seat [index], in dp from the ring box's top-left. By
     * construction the topmost seat's hit rect starts at y = 0 and the lowest
     * one's ends [RING_GAP_DP] above the bottom of the box.
     */
    fun seatCentreDp(
        index: Int,
        count: Int,
        boxWidthDp: Float,
        maxRadiusYDp: Float = MAX_RADIUS_Y_DP,
    ): Pair<Float, Float> {
        if (count <= 0) {
            return (boxWidthDp / 2f) to (ringHeightDp(count, boxWidthDp, maxRadiusYDp) / 2f)
        }
        val a = angle(index, count)
        val ry = ringRadiusYDp(count, boxWidthDp, maxRadiusYDp)
        return (boxWidthDp * (0.5f + RADIUS * cos(a).toFloat())) to
            (SEAT_HIT_DP / 2f + ry + ry * sin(a).toFloat())
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
        // Which hands actually counted, straight from the engine — the tally,
        // the outcome line and this list can never disagree (C-1).
        val counted = DayRules.countedVoters(state, lookup, orderedVoters, isExile, rules)
        val uncounted = orderedVoters
            .filterNot { it in counted }
            .associateWith { id ->
                val name = state.player(id)?.name ?: "That seat"
                ineligible[id]
                    ?: "$name's hand is up but does not count — their Master's hand is down."
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
            secretLine = if (!secret) "" else secretLine(state, lookup),
        )
    }

    /** The eyes-closed line, sourced from whatever actually closed the eyes. */
    fun secretLine(state: GameState, lookup: (String) -> Character?): String =
        if (DayRules.organGrinder(state, lookup) != null) {
            "Eyes closed, everyone. (If asked: an Organ Grinder is in play.)"
        } else {
            "Eyes closed, everyone. (House rule: every vote is secret.)"
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

            NominationResult.TIED -> DayModel.tieLine(state, nomineeName)
            NominationResult.WITHDRAWN -> "Withdrawn."
            NominationResult.SAFE -> "$who is safe — $tally of $threshold."
        }
    }

    /**
     * The pinned ring takes no taps while the day is closed and the storyteller
     * has not said otherwise — a nomination taken now would be dropped by
     * `DayRules.record` and never appear anywhere (C-4).
     */
    fun ringLocked(closedReason: String, reopened: Boolean): Boolean =
        closedReason.isNotBlank() && !reopened

    /**
     * True once the ring has done its job and should get out of the way (C-15,
     * C2-9).
     *
     * The ring is pinned so the two taps never cost a scroll — but it kept its
     * whole share of the screen afterwards, leaving the list a 760 px viewport
     * for a card far taller than that: the vote chips landed at y 1925–1938 (13
     * visible pixels of 126) and **Lock in** was not rendered at all. Every
     * nomination therefore cost one deliberate swipe between "who nominates"
     * and a countable tally, with the table waiting.
     *
     * Shrinking the ring is not available: `ringRadiusYDp` already returns the
     * SMALLEST radius that keeps two 48 dp targets apart, so anything less
     * overlaps. So the ring collapses to the one line it was showing in its
     * centre anyway — the pair — with the taps one [Change] away.
     */
    fun ringCollapsed(nominatorId: Long?, nomineeId: Long?, forcedOpen: Boolean): Boolean =
        nominatorId != null && nomineeId != null && !forcedOpen

    /**
     * True when **Lock in** would write nothing: `DayRules.record` refuses an
     * illegal nomination and returns the state untouched, so the button must
     * say so instead of clearing the draft.
     */
    fun lockInRefused(blockers: List<String>, force: Boolean): Boolean =
        blockers.isNotEmpty() && !force

    /** The one-line prompt above the ring. */
    fun ringPrompt(nominatorId: Long?, nomineeId: Long?, nominatorName: String?): String = when {
        nominatorId == null -> "Tap who nominates, then who they nominate."
        nomineeId == null -> "${nominatorName ?: "They"} nominates… tap the nominee."
        else -> "Check this, then call the vote."
    }

    /**
     * The ⊘ lines under the chip row: one per seat that may not vote at all,
     * capped so a Voudon day does not print one for every living seat. The
     * per-seat chip carries its own ⊘ and is not tappable, so this is the
     * explanation, not the only signal.
     */
    fun ineligibleLines(view: VoteView, max: Int = MAX_INELIGIBLE_LINES): List<String> {
        val all = view.order.mapNotNull { view.ineligible[it] }
        if (all.size <= max) return all
        return all.take(max) + "…and ${all.size - max} more may not vote."
    }

    private fun ineligibleReason(player: Player): String = when {
        !player.alive && player.ghostVoteUsed -> "${player.name} — ghost vote already spent."
        !player.alive -> "${player.name} — dead."
        else -> "${player.name} may not vote."
    }

    /**
     * How wide one seat may be, in dp, so the ring never overlaps itself:
     * the horizontal room its neighbours leave it at the height the ring
     * actually has, clamped so a seven-seat table does not grow absurd chips
     * and a fifteen-seat one still fits a short name.
     */
    fun seatWidthDp(
        count: Int,
        boxWidthDp: Float,
        maxRadiusYDp: Float = MAX_RADIUS_Y_DP,
    ): Float {
        if (count <= 0 || boxWidthDp <= 0f) return MAX_SEAT_DP
        val ry = ringRadiusYDp(count, boxWidthDp, maxRadiusYDp)
        return widestSeatDp(count, boxWidthDp, ry).coerceIn(MIN_SEAT_DP, MAX_SEAT_DP)
    }

    /** How far out from the centre the seats sit, as a fraction of the WIDTH. */
    const val RADIUS: Float = 0.40f

    /** Never narrower than this: a name plus a seat number has to fit (§3.4.7). */
    const val MIN_SEAT_DP: Float = 46f
    const val MAX_SEAT_DP: Float = 78f

    /** The drawn seat card. */
    const val SEAT_HEIGHT_DP: Float = 40f

    /** What a seat actually occupies: Compose grows a small target to 48 dp. */
    const val SEAT_HIT_DP: Float = 48f

    /** Air between two adjacent hit targets, so "touching" is never "overlapping". */
    const val SEAT_CLEAR_DP: Float = 2f

    /** The clear strip under the lowest seat, between the ring and the list. */
    const val RING_GAP_DP: Float = 12f

    /** A small table still looks like a table, not a squashed line. */
    const val MIN_RADIUS_Y_DP: Float = 96f

    /** Past this the ring would own the screen; above ~16 seats it densifies. */
    const val MAX_RADIUS_Y_DP: Float = 175f

    /** The search step for the vertical radius: sub-dp precision buys nothing. */
    private const val RADIUS_STEP_DP: Float = 1f

    /** The most of the day screen the pinned ring may ever take. */
    const val RING_SHARE_OF_SCREEN: Float = 0.55f

    /**
     * The vertical radius cap for a screen with [availableHeightDp] left under
     * the stat strip: a fifteen-seat ring is tall, but it never gets to squeeze
     * the vote panel off the phone.
     */
    fun maxRadiusYFor(availableHeightDp: Float): Float {
        if (!availableHeightDp.isFinite() || availableHeightDp <= 0f) return MAX_RADIUS_Y_DP
        val budget = availableHeightDp * RING_SHARE_OF_SCREEN - SEAT_HIT_DP - RING_GAP_DP
        return (budget / 2f).coerceIn(MIN_RADIUS_Y_DP, MAX_RADIUS_Y_DP)
    }

    /** How many "may not vote" lines the panel prints before it summarises. */
    const val MAX_INELIGIBLE_LINES: Int = 3
}
