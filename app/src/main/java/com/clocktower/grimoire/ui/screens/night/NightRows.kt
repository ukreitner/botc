package com.clocktower.grimoire.ui.screens.night

import com.clocktower.engine.Answer
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.NightAction
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightPlan
import com.clocktower.engine.NightStep
import com.clocktower.engine.Sequence
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.StepGate
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.Team
import com.clocktower.engine.YesNo

/**
 * The night screen's presentation logic, as plain Kotlin.
 *
 * Everything here answers a question the composable would otherwise answer
 * inline — *what does this row say on its right-hand side, what colour is its
 * gutter, what does the primary button promise, does it need a hold* — so it
 * can be measured by `tools/uicheck/src/test/kotlin/NightRowsTest.kt` instead
 * of by eye at one in the morning.
 *
 * Two rules bind every function in this file:
 *
 * 1. **No character id may appear anywhere** (invariant I1). Every per-character
 *    difference reaches the screen as data on the [NightStep] the registry
 *    built — a gate, a banner, a token label, a `KillOutcome`.
 * 2. **Nothing renders below [NIGHT_MIN_SP]** (ux/night-screen §H). The night
 *    screen is read at 50 cm, in the dark, by someone who is standing up.
 */

/** The hard floor for text on the night screen, in sp (ux/night-screen §H). */
const val NIGHT_MIN_SP: Float = 14f

/** Clamps a requested text size to the night screen's floor. */
fun nightSp(requested: Float): Float = if (requested < NIGHT_MIN_SP) NIGHT_MIN_SP else requested

/** How a row is drawn in the collapsed list's left gutter. */
enum class RowMark(val glyph: String) {
    /** Resolved, or ticked by hand. */
    DONE("✓"),

    /** The card that is open. */
    CURRENT("▶"),

    /** Gated off by the engine: grey, pre-ticked, still visible, still runnable. */
    SKIPPED("⊘"),

    /** Not reached yet. */
    PENDING("·"),
}

/**
 * Semantic colour of a piece of night UI. The screen maps these onto the WP10
 * theme tokens; keeping them abstract is what makes the mapping testable.
 */
enum class Tone {
    /** Ordinary parchment text. */
    NORMAL,

    /** Grey: done, skipped, or otherwise out of the way. */
    MUTED,

    /** Gold: this is where the storyteller is. */
    ACTIVE,

    /** Ember: this will change the game, or this ability is not working. */
    ALERT,
}

/** One line of the collapsed sheet: result if it is done, ask if it is not. */
data class RowView(
    /** [com.clocktower.engine.StepKey.token] — stable across rebuilds and undo. */
    val token: String,
    /** 1-based position in tonight's sheet. */
    val ordinal: Int,
    val title: String,
    /** Seat names, already joined: "Gus", "Ana, Dan, Ivy". */
    val holders: String,
    /** The right-hand column: `→ Ben poisoned` when done, `2 picks` when not. */
    val right: String,
    val mark: RowMark,
    val tone: Tone,
    /** Why the engine gated this row off. Empty unless [mark] is [RowMark.SKIPPED]. */
    val reason: String,
    /** True when the row offers `[Run anyway]`. */
    val runAnyway: Boolean,
)

/** A gated row's badge, above the instructions. Null when the step simply fires. */
data class GateBadge(val text: String, val tone: Tone)

/**
 * The badge a [StepGate] earns. A `Skip` is never silently removed and a
 * `Reduced` is never a `Skip` — an Exorcised Demon's deferred kill still
 * happens (lead D24/D37/D60).
 */
fun gateBadge(gate: StepGate): GateBadge? = when (gate) {
    StepGate.Fire -> null
    is StepGate.Skip -> GateBadge("NOTHING TO DO — ${gate.reason}", Tone.MUTED)
    is StepGate.Reduced -> GateBadge("PART OF THIS STEP ONLY — ${gate.reason}", Tone.ALERT)
    is StepGate.Conditional -> GateBadge(gate.question, Tone.ACTIVE)
}

/** True when the storyteller must answer the gate's question before acting. */
fun needsGateAnswer(gate: StepGate, answered: Boolean): Boolean =
    gate is StepGate.Conditional && !answered

/**
 * Whether a row counts as skipped right now. `forced` is the storyteller
 * pressing `[Run anyway]`, which is always allowed — the engine advises, the
 * storyteller rules.
 */
fun isSkipped(step: NightStep, forced: Boolean): Boolean = step.gate is StepGate.Skip && !forced

/** The gutter mark for one row. */
fun rowMark(step: NightStep, done: Set<String>, current: Boolean, forced: Boolean): RowMark = when {
    isSkipped(step, forced) -> RowMark.SKIPPED
    step.key.token in done -> RowMark.DONE
    current -> RowMark.CURRENT
    else -> RowMark.PENDING
}

/** The colour of one row, from its mark and its gate. */
fun rowTone(mark: RowMark, gate: StepGate): Tone = when {
    mark == RowMark.CURRENT -> Tone.ACTIVE
    mark == RowMark.SKIPPED || mark == RowMark.DONE -> Tone.MUTED
    gate is StepGate.Reduced -> Tone.ALERT
    else -> Tone.NORMAL
}

/**
 * The right-hand column of a collapsed row.
 *
 * A done row shows the RESULT, a pending row shows the ASK. That is the whole
 * information density the old checklist was missing: `✓ 2 Poisoner Gus → Ben
 * poisoned` versus `6 Fortune Teller Cleo 2 picks`.
 */
fun rowRight(step: NightStep, mark: RowMark, result: String): String = when (mark) {
    RowMark.SKIPPED -> "skipped"
    RowMark.DONE -> result.ifBlank { "done" }
    else -> askSummary(step.action)
}

/** "2 picks", "a character", "yes / no" — what the row will ask for. */
fun askSummary(action: NightAction?): String = when (action) {
    null -> ""
    is ChoosePlayers -> when {
        action.max <= 0 -> ""
        action.max == 1 -> "1 pick"
        else -> "${action.max} picks"
    }
    is ChooseCharacter -> "a character"
    is ChoosePlayerAndCharacter -> "a seat + a character"
    is YesNo -> "yes / no"
    is ShowInfo -> if (action.targetsNeeded > 0) {
        if (action.targetsNeeded == 1) "1 pick" else "${action.targetsNeeded} picks"
    } else {
        "show them"
    }
    is Sequence -> "several stages"
    else -> ""
}

/** Every row of tonight's sheet, in order, ready to draw. */
fun rowViews(
    plan: NightPlan,
    done: Set<String>,
    activeToken: String?,
    forced: Set<String>,
    /** Seat names per row, supplied by the screen (the engine deals in ids). */
    holderNames: (NightStep) -> String,
    /** What this row recorded tonight, supplied by the screen from the ledger. */
    results: (NightStep) -> String,
): List<RowView> = plan.steps.mapIndexed { index, step ->
    val forcedHere = step.key.token in forced
    val mark = rowMark(step, done, step.key.token == activeToken, forcedHere)
    RowView(
        token = step.key.token,
        ordinal = index + 1,
        title = step.title,
        holders = holderNames(step),
        right = rowRight(step, mark, results(step)),
        mark = mark,
        tone = rowTone(mark, step.gate),
        reason = (step.gate as? StepGate.Skip)?.reason.orEmpty(),
        runAnyway = mark == RowMark.SKIPPED,
    )
}

// ---------------------------------------------------------------------------
// The primary button
// ---------------------------------------------------------------------------

/**
 * The label of the one primary button — it states the **outcome**, never the
 * verb (ux/night-screen §B7). "EVE SURVIVES — NOBODY DIES", not "Confirm".
 *
 * Every input is data the registry or the kill funnel produced, so this
 * function names no character and no rule.
 */
@Suppress("LongParameterList", "ReturnCount")
fun primaryLabel(
    /** Seats the storyteller picked, in pick order. */
    picked: List<String> = emptyList(),
    /** Reminder-token labels this action will place, from the registry. */
    places: List<String> = emptyList(),
    /** The kill funnel's own words when the action ends in a death attempt. */
    deathLine: String = "",
    /** The information to be shown, already formatted ("1", "YES", "POISONER"). */
    answer: String = "",
    /** Who is being woken — "SHOW 1 TO BEN". */
    holder: String = "",
    /** The storyteller answered "they chose nobody" — a real answer, recorded. */
    none: Boolean = false,
    /** This row is gated off and is being run anyway. */
    skipped: Boolean = false,
    /** The last row of the sheet opens the day. */
    dawn: Boolean = false,
): String {
    if (dawn) return "OPEN THE DAY →"
    if (none) return "THEY CHOSE NOBODY"
    if (deathLine.isNotBlank()) return deathLine.uppercase()
    if (answer.isNotBlank()) {
        val shown = "SHOW “${answer.uppercase()}”"
        return if (holder.isBlank()) shown else "$shown TO ${holder.uppercase()}"
    }
    if (picked.isNotEmpty()) {
        val names = picked.joinToString(", ") { it.uppercase() }
        if (places.isNotEmpty()) return "$names — ${places.joinToString(" + ") { it.uppercase() }}"
        return "CONFIRM: $names"
    }
    if (skipped) return "RUN IT ANYWAY"
    return "DONE — NEXT STEP"
}

/**
 * The kill funnel's outcome as one line the button can wear.
 *
 * `KillOutcome.Choice` returns empty deliberately: a choice cannot be stated as
 * an outcome, so the screen hands it to the shared `KillSheet` instead of
 * promising something it might not do.
 */
fun deathHeadline(outcome: KillOutcome, name: String): String = when (outcome) {
    is KillOutcome.Dies -> "$name dies"
    is KillOutcome.Prevented -> "$name survives — nobody dies"
    is KillOutcome.Spends -> "$name survives — and it is spent"
    is KillOutcome.RegistersDead -> "$name registers as dead"
    is KillOutcome.Redirect -> "someone else dies instead"
    is KillOutcome.Choice -> ""
    KillOutcome.AlreadyDead -> "$name is already dead"
}

/** Every effect one action can apply, flattened — including a Sequence's stages. */
fun actionEffects(action: NightAction?): List<NightEffect> = when (action) {
    null -> emptyList()
    is ChoosePlayers -> action.perTarget + action.onResolve + action.onNone
    is ChooseCharacter -> action.onResolve
    is ChoosePlayerAndCharacter -> action.onResolve
    is YesNo -> action.onYes + action.onNo
    is Sequence -> action.stages.flatMap { actionEffects(it) }
    else -> emptyList()
}

/** The token labels an action places — the outcome half of the button's label. */
fun placedLabels(effects: List<NightEffect>): List<String> =
    effects.filterIsInstance<NightEffect.PlaceToken>().map { it.label }.distinct()

/**
 * True when the primary button must be **held** for
 * [HOLD_CONFIRM_MILLIS] rather than tapped (ux/night-screen §H, defect #23):
 * anything that kills, resurrects, rewrites a character or spends a
 * once-per-game ability.
 */
fun isDestructive(effects: List<NightEffect>): Boolean = effects.any {
    it is NightEffect.Attack ||
        it is NightEffect.Resurrect ||
        it is NightEffect.BecomeCharacter ||
        it is NightEffect.SwapCharacters ||
        it is NightEffect.MarkSpent
}

/** How long a destructive primary must be held before it fires, in ms. */
const val HOLD_CONFIRM_MILLIS: Int = 400

/**
 * A typed answer as the button and the card label say it. Names are injected so
 * this stays pure: the engine deals in ids, the screen knows the names.
 */
fun answerLabel(
    answer: Answer,
    characterName: (String) -> String = { it },
    playerName: (Long) -> String = { it.toString() },
): String = when (answer) {
    is Answer.Count -> answer.n.toString()
    is Answer.YesNoAnswer -> if (answer.yes) "YES" else "NO"
    is Answer.Characters -> answer.ids.joinToString(", ") { characterName(it) }
    is Answer.Players -> answer.ids.joinToString(", ") { playerName(it) }
    is Answer.Message -> answer.text
}

/**
 * True when the primary button may fire: every ask has an answer, or the
 * storyteller has said "they chose nobody" — which is itself a real answer and
 * is recorded as one.
 */
fun primaryEnabled(action: NightAction?, pick: PickState): Boolean = when {
    action == null -> true
    pick.none -> true
    action is YesNo -> pick.yes != null
    action is ChooseCharacter -> pick.characterIds.isNotEmpty() || action.allowNone
    action is ChoosePlayerAndCharacter ->
        pick.playerIds.isNotEmpty() && pick.characterIds.isNotEmpty()
    else -> pick.playerIds.size >= picksNeeded(action)
}

/** The three answer shapes a step can be in, as data the tests can build. */
data class PickState(
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val yes: Boolean? = null,
    val none: Boolean = false,
)

/** How long the exit control on a full-screen card must be held, in ms. */
const val HOLD_CARD_EXIT_MILLIS: Int = 1200

// ---------------------------------------------------------------------------
// The one player picker
// ---------------------------------------------------------------------------

/**
 * One seat as the picker sees it. Built by the screen from `GameState`; every
 * decision the picker makes about it is made by the two pure functions below,
 * so "the Devil's Advocate may not repeat last night's target" is a test rather
 * than a paragraph of prose the storyteller has to read at the table.
 */
data class SeatOption(
    val id: Long,
    /** 1-based position in the circle — the storyteller is about to walk there. */
    val seat: Int,
    val name: String,
    val alive: Boolean,
    val self: Boolean,
    val team: Team?,
    val evil: Boolean,
    val traveller: Boolean,
    /** This ability chose them on its previous wake. */
    val chosenLastNight: Boolean,
    /** This ability has chosen them at some point. */
    val chosenBefore: Boolean,
    /** A physical neighbour of the acting seat. */
    val neighbour: Boolean,
)

/**
 * Null when this seat may be picked; otherwise the reason, in the storyteller's
 * words. An ineligible seat is never rendered as a tappable chip that turns out
 * to do nothing (defect #14) — it goes under the disclosure with its reason.
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
fun blockedBecause(option: SeatOption, constraints: List<TargetConstraint>): String? {
    for (constraint in constraints) {
        val reason = when (constraint) {
            TargetConstraint.ALIVE -> if (option.alive) null else "dead"
            TargetConstraint.DEAD -> if (!option.alive) null else "still alive"
            TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED -> null
            TargetConstraint.NOT_SELF -> if (option.self) "themselves" else null
            TargetConstraint.NOT_TRAVELLER -> if (option.traveller) "a Traveller" else null
            TargetConstraint.TOWNSFOLK -> if (option.team == Team.TOWNSFOLK) null else "not a Townsfolk"
            TargetConstraint.OUTSIDER -> if (option.team == Team.OUTSIDER) null else "not an Outsider"
            TargetConstraint.MINION -> if (option.team == Team.MINION) null else "not a Minion"
            TargetConstraint.DEMON -> if (option.team == Team.DEMON) null else "not the Demon"
            TargetConstraint.NOT_DEMON -> if (option.team == Team.DEMON) "the Demon" else null
            TargetConstraint.GOOD -> if (option.evil) "evil" else null
            TargetConstraint.EVIL -> if (option.evil) null else "good"
            TargetConstraint.DIFFERENT_FROM_LAST_NIGHT ->
                if (option.chosenLastNight) "chosen last night" else null
            TargetConstraint.NOT_CHOSEN_BEFORE ->
                if (option.chosenBefore) "chosen before" else null
            TargetConstraint.NEIGHBOUR_OF_SOURCE ->
                if (option.neighbour) null else "not a neighbour"
        }
        if (reason != null) return reason
    }
    return null
}

/** Seat order by default; every other order is the registry's explicit request. */
fun sortOptions(options: List<SeatOption>, sort: TargetSort): List<SeatOption> = when (sort) {
    TargetSort.SEAT_ORDER -> options
    TargetSort.ALIVE_FIRST -> options.sortedByDescending { it.alive }
    TargetSort.DEAD_FIRST -> options.sortedBy { it.alive }
    TargetSort.DEMON_FIRST -> options.sortedByDescending { it.team == Team.DEMON }
    TargetSort.MINION_FIRST -> options.sortedByDescending { it.team == Team.MINION }
    TargetSort.OUTSIDER_FIRST -> options.sortedByDescending { it.team == Team.OUTSIDER }
    TargetSort.TOWNSFOLK_FIRST -> options.sortedByDescending { it.team == Team.TOWNSFOLK }
}

/**
 * What one tap does to the current picks: toggle off, add, or — once [max] is
 * reached — drop the oldest so the newest pick always lands. A picker that
 * silently ignores a tap is a picker the storyteller stops trusting.
 */
fun togglePick(picked: List<Long>, id: Long, max: Int): List<Long> = when {
    id in picked -> picked - id
    max <= 0 -> picked + id
    picked.size < max -> picked + id
    max == 1 -> listOf(id)
    else -> picked.drop(1) + id
}

/** How many seats an action wants picked before its primary button is armed. */
fun picksNeeded(action: NightAction?): Int = when (action) {
    is ChoosePlayers -> action.min
    is ShowInfo -> action.targetsNeeded
    is ChoosePlayerAndCharacter -> 1
    else -> 0
}

/** The most seats an action accepts. 0 = no player picking at all. */
fun picksAllowed(action: NightAction?): Int = when (action) {
    is ChoosePlayers -> action.max
    is ShowInfo -> action.targetsNeeded
    is ChoosePlayerAndCharacter -> 1
    else -> 0
}

// ---------------------------------------------------------------------------
// The progress strip
// ---------------------------------------------------------------------------

/** What the fixed strip at the top of the night screen says. */
data class Progress(
    /** 1-based position of the step being run. */
    val ordinal: Int,
    val total: Int,
    val done: Int,
    val skipped: Int,
) {
    /** "step 6 / 11". */
    val label: String get() = "step $ordinal / $total"
}

/** The progress strip's numbers for tonight's sheet. */
fun progress(plan: NightPlan, done: Set<String>, activeToken: String?): Progress {
    val steps = plan.steps
    val index = steps.indexOfFirst { it.key.token == activeToken }
        .takeIf { it >= 0 }
        ?: plan.cursor(done).takeIf { it >= 0 }
        ?: (steps.size - 1)
    return Progress(
        ordinal = (index + 1).coerceAtLeast(if (steps.isEmpty()) 0 else 1),
        total = steps.size,
        done = steps.count { it.required && it.key.token in done },
        skipped = steps.count { !it.required },
    )
}

/** The segment bar: one tone per step, in order. */
fun segmentTones(
    plan: NightPlan,
    done: Set<String>,
    activeToken: String?,
    forced: Set<String>,
): List<Tone> = plan.steps.map { step ->
    rowTone(rowMark(step, done, step.key.token == activeToken, step.key.token in forced), step.gate)
}

// ---------------------------------------------------------------------------
// The dim control
// ---------------------------------------------------------------------------

/** How many dim levels the ⏻ control cycles through: 100 %, 55 %, 25 %. */
const val DIM_LEVELS: Int = 3

/** The next level the ⏻ control moves to. */
fun nextDimLevel(level: Int): Int = (level.coerceIn(0, DIM_LEVELS - 1) + 1) % DIM_LEVELS

/**
 * The scrim's opacity for a dim level. The scrim is the ONLY lever the PWA
 * has, which is why it must cover the top bar and the navigation bar too
 * (ux/night-screen §H, defect #21).
 */
fun dimAlpha(level: Int): Float = when (level) {
    1 -> 0.45f
    2 -> 0.75f
    else -> 0f
}

/** The window brightness for a dim level; null restores the system default. */
fun screenBrightness(level: Int): Float? = when (level) {
    1 -> 0.55f
    2 -> 0.25f
    else -> null
}

/** "100 %", "55 %", "25 %" — what the ⏻ control currently means. */
fun dimLabel(level: Int): String = when (level) {
    1 -> "55 %"
    2 -> "25 %"
    else -> "100 %"
}
