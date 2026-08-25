package com.clocktower.engine

import kotlinx.serialization.Serializable

/** Special (non-character) entries in the night order. */
object NightMarkers {
    const val DUSK = "DUSK"
    const val MINION_INFO = "MINION_INFO"
    const val DEMON_INFO = "DEMON_INFO"
    const val DAWN = "DAWN"

    /** Per-Minion bluff hand-out (Snitch). */
    const val MINION_BLUFFS = "MINION_BLUFFS"

    /** The Demon-only bluff step (Poppy Grower). */
    const val DEMON_BLUFFS_ONLY = "DEMON_BLUFFS_ONLY"

    val all = setOf(DUSK, MINION_INFO, DEMON_INFO, DAWN, MINION_BLUFFS, DEMON_BLUFFS_ONLY)
}

/** Which run of a step this is. */
@Serializable
enum class StepVariant {
    /** The normal step for tonight. */
    NORMAL,

    /** Run this seat's FIRST-night version tonight (resurrection, new character). */
    FIRST,

    /** A second run for the same holder tonight (Barista). */
    AGAIN,
}

/** Identity of one night step. [token] is what goes in [GameState.nightStepsDone]. */
@Serializable
data class StepKey(
    /** Which ability runs. NOT the night-order slot — see [NightStep.slotId]. */
    val abilityId: String,
    /** The single seat this row is for. Null for group steps and markers. */
    val holderId: Long? = null,
    val variant: StepVariant = StepVariant.NORMAL,
) {
    /** Degrades to the bare ability id for simple steps, so old saves keep working. */
    val token: String
        get() = buildString {
            append(abilityId)
            holderId?.let { append('#').append(it) }
            if (variant != StepVariant.NORMAL) append('@').append(variant.name.lowercase())
        }
}

@Serializable
enum class WakeStyle { FIRST_NIGHT, OTHER_NIGHT }

/** Whether and how a step runs tonight, and why. */
@Serializable
sealed interface StepGate {
    /** Runs normally. */
    @Serializable
    data object Fire : StepGate

    /**
     * Runs, but only part of it. [allow] names the halves that still run:
     * "pending" / "passive" for an Exorcised Demon (its deferred death still
     * happens), never including "choose". NEVER use [Skip] for an Exorcised Demon.
     */
    @Serializable
    data class Reduced(val reason: String, val allow: Set<String>) : StepGate

    /** The engine cannot decide alone: ask [question] first, then offer the action. */
    @Serializable
    data class Conditional(
        val question: String,
        val yesLabel: String,
        val noLabel: String,
    ) : StepGate

    /** Nothing to do. Rendered collapsed and grey, auto-ticked, with [reason] and [Run anyway]. */
    @Serializable
    data class Skip(val reason: String) : StepGate
}

/** A card the storyteller can show, already populated. Never a picker for a known answer. */
@Serializable
data class CardOffer(
    /** Button text: "SHOW: POISONER", "LIE · SHOW 2 TO BEN". */
    val label: String,
    val card: ShowCardSpec,
    val truthful: Boolean,
    /** Long-press opens the free-text editor. */
    val editable: Boolean = true,
)

/**
 * One row of tonight's sheet. This IS the UI's view model — there is no
 * NightStepView, no NightHolder and no NightAsk. Per-holder rendering is
 * achieved by emitting one NightStep per holder.
 */
@Serializable
data class NightStep(
    val key: StepKey,
    /**
     * Night-order position id. Defaults to `key.abilityId`; differs for the Lunatic,
     * an Alchemist-Poisoner, a Cannibal at the executee's index (lead D43).
     */
    val slotId: String,
    /** Sort position. Base list entries get index * 100 so insertions fit between. */
    val order: Double,
    /** "Chambermaid — Ana (via the Boffin)", "Pukka — Cai (LUNATIC — nothing happens)". */
    val title: String,
    val detail: String,
    /** Which grant produced this row ("boffin", "philosopher", "lunatic", "drunk"). */
    val sourceId: String? = null,
    /** Group steps only (MINION_INFO, DEMON_INFO, lilmonsta, legion, riot). */
    val holderIds: List<Long> = emptyList(),
    val style: WakeStyle,
    val gate: StepGate,
    /** The single most important derived fact, shown in ember ABOVE the instructions. */
    val banner: String = "",
    /** Imperative, storyteller voice, at most two lines. */
    val prompt: String = "",
    val action: NightAction? = null,
    /** "died tonight", "spent on night 2", "new character", "out of order". */
    val badges: List<String> = emptyList(),
    /** Pre-filled cards this step offers. */
    val cards: List<CardOffer> = emptyList(),
    /** Prompt this step exists to discharge, if any. */
    val promptId: Long? = null,
) {
    val required: Boolean get() = gate !is StepGate.Skip
    val holderId: Long? get() = key.holderId
    val abilityId: String get() = key.abilityId
}

/** What the storyteller entered on a step. */
@Serializable
data class NightInput(
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val yes: Boolean? = null,
    val number: Int? = null,
    /** The "they chose nobody / were not woken" answer — a REAL answer, recorded. */
    val none: Boolean = false,
    val optionId: String = "",
    /** True when the storyteller made the choice rather than the player (Goon, lead D1). */
    val byStoryteller: Boolean = false,
)

/**
 * Tonight's sheet: a pure function of state (I6). Never cached — rebuild after
 * every mutation.
 */
@Serializable
data class NightPlan(
    val cycle: Int,
    val isFirstNight: Boolean,
    val steps: List<NightStep>,
) {
    /** Index of the first required step not in `nightStepsDone`. */
    fun cursor(done: Set<String>): Int = steps.indexOfFirst { it.required && it.key.token !in done }

    fun unfinished(done: Set<String>): List<NightStep> =
        steps.filter { it.required && it.key.token !in done }

    companion object {
        /** Pure. Rebuild after every mutation; never cache. */
        fun build(state: GameState, lookup: (String) -> Character?): NightPlan = TODO("WP2")

        /**
         * Applies a step's input: validates constraints AT RESOLVE TIME, applies
         * `perTarget` effects ONE TARGET AT A TIME re-deriving impairment, positional
         * poison and protections between each, appends CHOICE / WOKE / MALFUNCTION
         * ledger entries, and ticks the step.
         */
        fun resolve(
            state: GameState,
            lookup: (String) -> Character?,
            key: StepKey,
            input: NightInput,
        ): GameState = TODO("WP2")

        fun toggleDone(state: GameState, token: String): GameState =
            state.copy(
                nightStepsDone = if (token in state.nightStepsDone) {
                    state.nightStepsDone - token
                } else {
                    state.nightStepsDone + token
                },
            )

        /** Chambermaid: how many of [targets] woke for their OWN ability tonight (lead D13). */
        fun wokeCount(
            state: GameState,
            lookup: (String) -> Character?,
            targets: List<Long>,
        ): Int = TODO("WP2")

        /** Mathematician: how many abilities malfunctioned tonight. Excludes the Mathematician. */
        fun malfunctionCount(state: GameState, night: Int): Int = TODO("WP2")
    }
}

/** Everything a wake predicate may look at. */
class WakeContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val night: Int,
    val holder: Player?,
    val role: ActingRole?,
    val diedTonight: Set<Long>,
    val diedToday: Set<Long>,
    val executedToday: ExecutionRecord?,
    val resurrectedTonight: Set<Long>,
    val residentCount: Int,
    val totalSeatCount: Int,
)

/** Whether a step fires tonight, and why not. */
fun interface WakePredicate {
    fun gate(ctx: WakeContext): StepGate
}

/** Composable wake predicates; per-character choice lives in the registry (WP2). */
object Gates {
    val aliveHolder: WakePredicate = WakePredicate { TODO("WP2") }
    val actsWhileDead: WakePredicate = WakePredicate { TODO("WP2") }
    val hasAbility: WakePredicate = WakePredicate { TODO("WP2") }

    /** Reads `Character.spentLabel` (lead D49). */
    fun notSpent(): WakePredicate = WakePredicate { TODO("WP2") }

    fun diedTonight(): WakePredicate = WakePredicate { TODO("WP2") }

    /** Zombuul(false), Godfather(true). */
    fun someoneDiedToday(expected: Boolean): WakePredicate = WakePredicate { TODO("WP2") }

    fun executedToday(): WakePredicate = WakePredicate { TODO("WP2") }

    /** Summoner 3, Xaan X. */
    fun nightIs(n: Int): WakePredicate = WakePredicate { TODO("WP2") }

    /** The 7+ threshold — see ARCHITECTURE §6 Q1. */
    fun minPlayers(n: Int): WakePredicate = WakePredicate { TODO("WP2") }

    /** Chambermaid needs 2 other alive players. */
    fun minAlive(n: Int): WakePredicate = WakePredicate { TODO("WP2") }

    /** Produces [StepGate.Reduced], NEVER [StepGate.Skip]. */
    val notExorcised: WakePredicate = WakePredicate { TODO("WP2") }

    fun all(vararg p: WakePredicate): WakePredicate = WakePredicate { TODO("WP2") }
}
