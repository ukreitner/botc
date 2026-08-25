package com.clocktower.engine

import com.clocktower.engine.rules.BMR_RULES
import com.clocktower.engine.rules.EXP_DEMON_RULES
import com.clocktower.engine.rules.EXP_MINION_RULES
import com.clocktower.engine.rules.EXP_OUTSIDER_RULES
import com.clocktower.engine.rules.EXP_TOWNSFOLK_RULES
import com.clocktower.engine.rules.FABLED_RULES
import com.clocktower.engine.rules.SV_RULES
import com.clocktower.engine.rules.TB_RULES
import com.clocktower.engine.rules.TRAVELLER_RULES

/**
 * Everything the engine needs to know about one character, in one value.
 *
 * This is the single place per-character behaviour lives (I1). No screen, and
 * no engine function outside `engine/rules/`, may branch on a character id.
 * Never serialised — it holds lambdas.
 */
data class CharacterRule(
    val id: String,

    // ---- shape ----
    /** Emit one night step per holder (Village Idiot, Snitch's per-Minion wake, Legion). */
    val perHolder: Boolean = false,
    /** This ability fires even though the holder is dead (Ravenkeeper, Sage, Farmer, Barber…). */
    val actsWhileDead: Boolean = false,
    /** The ability itself survives death (Recluse, Spy, Heretic, Zealot, Politician…). */
    val keepsAbilityWhenDead: Boolean = false,
    /** The DeathCause this character's kills carry. */
    val killCause: DeathCause = DeathCause.STORYTELLER,
    /** The wiki does not rule whether these count as Demon kills — the panel asks. */
    val demonKillUncertain: Boolean = false,

    // ---- night ----
    val firstNight: NightRule? = null,
    val otherNight: NightRule? = null,

    // ---- standing / tokens / death ----
    val standing: StandingRule? = null,
    val tokens: List<TokenRule> = emptyList(),
    val onDeath: List<DeathTrigger> = emptyList(),

    // ---- day ----
    val day: DayRule? = null,

    // ---- setup ----
    val setup: List<SetupRequirement> = emptyList(),
    val bluffs: BluffRule? = null,
    /** Bag override, computed against the base distribution for the player count. */
    val bagShape: ((base: Distribution, playerCount: Int) -> BagShape?)? = null,
    /**
     * Jinx-gated extra behaviour. Key = the other character's id; the rule applies
     * only when that character is on the script (lead D19).
     */
    val jinxRules: Map<String, NightRule> = emptyMap(),
)

/** How this character behaves on one kind of night. */
data class NightRule(
    /** FIRE / REDUCED / CONDITIONAL / SKIP, with a reason the storyteller can read. */
    val gate: WakePredicate = Gates.aliveHolder,
    /** What the storyteller is asked. Null = an information-only or marker step. */
    val action: (NightContext) -> NightAction? = { null },
    /** Imperative, storyteller voice, at most two lines. */
    val prompt: String = "",
    /** Pre-filled cards this step offers — never a search box for an answer we know. */
    val cards: (NightContext) -> List<CardOffer> = { emptyList() },
    /** InfoCalc key. "" = this step computes no information. */
    val infoId: String = "",
    /**
     * Does waking for this step count for the Chambermaid? (lead D13)
     * ACT = yes; INFORMED = woken but not for their own ability (Minion info,
     * an Exorcist's target); NONE = never woken.
     */
    val wakeCounts: WakeCount = WakeCount.ACT,
)

enum class WakeCount { ACT, INFORMED, NONE }

/** Day-phase behaviour. */
data class DayRule(
    /** A once-per-day / once-per-game day power the Day tab offers as a button. */
    val ability: DayAbility? = null,
    val onNomination: ((NominationContext) -> List<NominationTrigger>)? = null,
    val onExecution: ((ExecutionContext) -> List<ExecutionConsequence>)? = null,
    val briefing: ((BriefingContext) -> List<BriefingItem>)? = null,
)

/** One row of the Day tab's abilities strip. */
data class DayAbility(
    /** "Slayer shot", "Statement", "Public kill". */
    val label: String,
    val oncePerGame: Boolean = false,
    val oncePerDay: Boolean = false,
    /** The ledger sourceId this ability writes and later consumes. */
    val recordsAs: String = "",
    val available: (state: GameState, lookup: (String) -> Character?, holder: Player) -> Boolean,
)

/**
 * Fires when someone dies. [gate] must include `Status.hasAbility(holder)` unless
 * the character's text says "even if dead" (lead D35).
 */
data class DeathTrigger(
    val gate: (state: GameState, event: DeathEvent, holder: Player) -> Boolean,
    val produce: (state: GameState, event: DeathEvent, holder: Player) -> TriggerResult,
)

data class TriggerResult(
    val prompts: List<Prompt> = emptyList(),
    val effects: List<Effect> = emptyList(),
)

/** The registry: schema and concatenation only (WP2); entries live in `rules/` (WP7). */
object CharacterRules {

    /** Concatenation of the per-edition registry files. Built once, lazily. */
    val all: Map<String, CharacterRule> by lazy {
        (
            TB_RULES + BMR_RULES + SV_RULES +
                EXP_TOWNSFOLK_RULES + EXP_OUTSIDER_RULES + EXP_MINION_RULES + EXP_DEMON_RULES +
                TRAVELLER_RULES + FABLED_RULES
            ).associateBy { Character.normalizeId(it.id) }
    }

    /** The rule for [id], or a generic one derived from `characters.json`. */
    fun of(id: String, character: Character?): CharacterRule = TODO("WP2")

    val standingRules: List<StandingRule> get() = all.values.mapNotNull { it.standing }

    val tokenRules: List<TokenRule> get() = all.values.flatMap { it.tokens }
}

// ---- the contexts a registry lambda receives (all read-only) ----

/** Everything a night lambda may look at. Never mutate; return values instead. */
class NightContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val night: Int,
    val isFirstNight: Boolean,
    /** The seat this row is for. Null for group steps and markers. */
    val holder: Player?,
    val role: ActingRole?,
    val diedTonight: Set<Long>,
    val diedToday: Set<Long>,
    val executedToday: ExecutionRecord?,
    val resurrectedTonight: Set<Long>,
)

class NominationContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val nominatorId: Long?,
    val nomineeId: Long?,
    /** The seat holding this rule's character. */
    val holder: Player,
)

class ExecutionContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val record: ExecutionRecord,
    val holder: Player,
)

class BriefingContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val slot: BriefingSlot,
    val holder: Player,
)
