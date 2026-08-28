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
    /**
     * Emit one night step per holder (Village Idiot, Snitch's per-Minion wake, Legion).
     *
     * This is ALREADY the planner's default (lead D23: "per-holder rendering is
     * achieved by emitting one NightStep per holder"), so a registry row only
     * needs it for documentation. Its opposite, [groupStep], is the exception.
     */
    val perHolder: Boolean = false,
    /**
     * All holders wake together on ONE row with `holderIds` filled in — the
     * Lil' Monsta babysitters, Legion, Riot. The planner's default is the
     * opposite: one row per acting role.
     */
    val groupStep: Boolean = false,
    /**
     * This ability fires even though the holder is dead (Ravenkeeper, Sage,
     * Farmer, Barber…).
     *
     * DECLARATIVE. What actually enforces it is the row's own `gate` —
     * `Gates.actsWhileDead`, or a narrower one like `Gates.diedTonight()`. The
     * flag says so in one place a reader can grep, and it is not inferred from
     * the gate because the two are not the same statement: a Ravenkeeper acts
     * ONLY on the night it dies.
     */
    val actsWhileDead: Boolean = false,
    /** The ability itself survives death (Recluse, Spy, Heretic, Zealot, Politician…). */
    val keepsAbilityWhenDead: Boolean = false,
    /**
     * The DeathCause this character's kills carry.
     *
     * `NightEffect.Attack.cause` wins when it says something other than the
     * default `DEMON_KILL`; this fills in for a row that did not, so a Gossip's
     * or a Gunslinger's kill can never be counted as a Demon's by accident
     * (`NightPlan.declaredKillCause`, W7G).
     */
    val killCause: DeathCause = DeathCause.STORYTELLER,
    /**
     * The wiki does not rule whether these count as Demon kills — the panel
     * asks. Rendered as a badge on every non-skipped step of this character
     * (W7G), so the storyteller sees the question before they tap.
     */
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
     * Jinx-gated extra behaviour. Key = the other character's id; the rule
     * applies only when that character is on the SCRIPT — not because it was
     * dealt (lead D19, the Djinn rule).
     *
     * A jinx row overrides only what it declares, so the King's Leviathan jinx
     * can change nothing but its gate and keep the King's own prompt. Several
     * can apply at once (a Leviathan script with a Farmer AND a Sage): the gates
     * combine worst-of and the prompts are joined.
     *
     * OTHER nights only. Every jinx action the official set defines is an "each
     * night*" ability, and there is no place here to say "first night".
     */
    val jinxRules: Map<String, NightRule> = emptyMap(),
) {
    /** The rule for tonight, or null when this character does not act on such a night. */
    fun nightRule(isFirstNight: Boolean): NightRule? = if (isFirstNight) firstNight else otherNight
}

/** How this character behaves on one kind of night. */
data class NightRule(
    /** FIRE / REDUCED / CONDITIONAL / SKIP, with a reason the storyteller can read. */
    val gate: WakePredicate = Gates.aliveHolder,
    /** What the storyteller is asked. Null = an information-only or marker step. */
    val action: (NightContext) -> NightAction? = { null },
    /**
     * Effects that run whenever the step resolves — INCLUDING when the gate is
     * [StepGate.Reduced], which is exactly the Exorcised Pukka's deferred kill
     * (lead D24: never `Skip` an Exorcised Demon, its pending half still fires).
     *
     * Computed from the state as it was BEFORE the action's own effects, and
     * applied AFTER them, so "the new poison is placed before the previous
     * victim dies" (lead D4) falls out of the ordering.
     */
    val pending: (NightContext) -> List<NightEffect> = { emptyList() },
    /** Imperative, storyteller voice, at most two lines. */
    val prompt: String = "",
    /**
     * The one derived fact worth ember, ABOVE the instructions: the Gossip's
     * recorded statement, the Devil's Advocate's last pick, the Shabaloth's
     * regurgitation candidates.
     *
     * Returning "" leaves the planner's own banner in place (impairment, a
     * silenced Demon, "dead — this ability fires anyway"), which always wins:
     * a row must never hide the reason its ability will not work behind a quote.
     */
    val banner: (NightContext) -> String = { "" },
    /**
     * Extra evidence appended to the step's detail line, under the character's
     * own night reminder. Same contract as [banner]: "" changes nothing.
     */
    val detail: (NightContext) -> String = { "" },
    /** Pre-filled cards this step offers — never a search box for an answer we know. */
    val cards: (NightContext) -> List<CardOffer> = { emptyList() },
    /**
     * InfoCalc key.
     *
     *  - `null` (the default) — use the ability's own id, so a character with a
     *    calculator gets its picker and its pre-filled cards for free;
     *  - `""` — this step computes NO information. The planner offers no
     *    `ShowInfo` fallback and no cards. Use it for a genuinely free-text
     *    answer the engine cannot compute (W7H);
     *  - anything else — that calculator, at that step (`king.demon`).
     */
    val infoId: String? = null,
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
 *
 * `lookup` was added in wave 7: without it a row could not ask a team question
 * or a status question, so the Scarlet Woman read stored effects directly and
 * the Imp's heir check was "anyone else alive" rather than "a Minion is alive".
 * For a Fabled, `holder` is [CharacterRules.GRIMOIRE_HOLDER] — there is no seat.
 */
data class DeathTrigger(
    val gate: (
        state: GameState,
        lookup: (String) -> Character?,
        event: DeathEvent,
        holder: Player,
    ) -> Boolean,
    val produce: (
        state: GameState,
        lookup: (String) -> Character?,
        event: DeathEvent,
        holder: Player,
    ) -> TriggerResult,
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

    /**
     * The rule for [id]: a WP7 registry row, else one derived from
     * `characters.json` so homebrew and not-yet-written characters still get a
     * working step.
     *
     * The WP2 stopgap map that used to sit between the two is gone (lead D64):
     * every one of its five rows — ravenkeeper, zombuul, godfather, pukka,
     * chambermaid — is now a real registry row.
     */
    fun of(id: String, character: Character?): CharacterRule {
        val key = Character.normalizeId(id)
        all[key]?.let { return it }
        return generic(key, character)
    }

    val standingRules: List<StandingRule> get() = all.values.mapNotNull { it.standing }

    val tokenRules: List<TokenRule> get() = all.values.flatMap { it.tokens }

    // ------------------------------------------------------------------
    // Fabled — registry rows with no seat (WP7 wave 7)
    // ------------------------------------------------------------------

    /**
     * Seat id of [GRIMOIRE_HOLDER]. Distinct from
     * [GameState.STORYTELLER_SEAT_ID] (-1), which is a real nominee.
     */
    const val GRIMOIRE_SEAT_ID: Long = -2L

    /**
     * The stand-in "seat" a Fabled's registry row is handed.
     *
     * A Fabled holds no seat — it lives in [GameState.fabled] and its rules act
     * on the grimoire as a whole — but `onDeath`, `day.onNomination`,
     * `day.onExecution`, `day.briefing` and `standing` are all written against a
     * holder. Fabled rows never read it (they read `state.fabled`); this value
     * exists so the engine can walk them with the same loop that walks seated
     * rows.
     *
     * `leftGame = true` keeps it out of `state.seats` and `state.player(id)`
     * returns null for it, so a row that reached for it fails visibly rather
     * than corrupting a real seat.
     */
    val GRIMOIRE_HOLDER: Player = Player(
        id = GRIMOIRE_SEAT_ID,
        name = "the grimoire",
        leftGame = true,
    )

    /**
     * The registry rows of every Fabled in play, in the order the storyteller
     * added them. Fabled have no seat, so every engine loop that walks
     * `state.seats` looking for rows must walk this too (WP7-FAB's load-bearing
     * follow-up).
     */
    fun fabledRows(state: GameState): List<CharacterRule> =
        state.fabled.mapNotNull { all[Character.normalizeId(it.id)] }

    // ------------------------------------------------------------------
    // Generic fallback — no character id, only `characters.json` data
    // ------------------------------------------------------------------

    private val genericCache = HashMap<String, CharacterRule>()

    /**
     * A character the registry does not know still gets a step: it wakes when the
     * night order says it does, it is skipped when its holder is dead, and an
     * Exorcised Demon is REDUCED rather than skipped (lead D24).
     */
    private fun generic(key: String, character: Character?): CharacterRule =
        genericCache.getOrPut(key) {
            val demon = character?.team == Team.DEMON
            val gate = if (demon) Gates.all(Gates.aliveHolder, Gates.notExorcised) else Gates.aliveHolder
            val rule = NightRule(
                gate = gate,
                prompt = "",
                infoId = if (InfoCalc.supports(key)) key else "",
            )
            // A character the registry does not know keeps nothing in the
            // grave: the WP1 stopgap id set is gone (lead D64) and every real
            // row declares `keepsAbilityWhenDead` for itself.
            CharacterRule(id = key, firstNight = rule, otherNight = rule)
        }
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

// ---------------------------------------------------------------------------
// The one shared MINION_INFO / DEMON_INFO builder (lead D37/D60)
// ---------------------------------------------------------------------------

/** What the shared builder needs to draw the group rows for one night-order slot. */
internal class MarkerContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val slot: String,
    val order: Double,
    val style: WakeStyle,
    val isFirstNight: Boolean,
    /** Every bluff set this game owes, from `Bluffs.requirements`. */
    val bluffs: List<BluffRequirement>,
)

/**
 * The Minion/Demon information steps, which are the one place several characters
 * change ONE row rather than owning one (lead D37): the Marionette is left out of
 * the Minion list and pointed out to the Demon, the Poppy Grower replaces the
 * Demon's row with a bluffs-only row, a Summoner or Lil' Monsta game has no Demon
 * seat to wake, and the Lunatic's fake attacks are shown to the real Demon.
 *
 * It lives beside the registry, not in `NightPlan.kt`, because it is per-character
 * knowledge (§3.4.3) — the planner only asks [owns] and renders what comes back.
 */
internal object NightInfo {

    private const val MARIONETTE = "marionette"
    private const val LUNATIC = "lunatic"

    /** Night-order slots this builder owns outright. */
    fun owns(slot: String): Boolean = slot in NightMarkers.all || slot == MARIONETTE

    fun steps(ctx: MarkerContext): List<NightStep> = when (ctx.slot) {
        NightMarkers.DUSK -> listOf(
            marker(
                ctx,
                title = "Dusk",
                detail = "Everyone closes their eyes. Wait for quiet.",
                gate = StepGate.Fire,
            ),
        )

        NightMarkers.DAWN -> listOf(
            marker(
                ctx,
                title = "Dawn",
                detail = "Wait a few seconds. Everyone opens their eyes. Announce who died.",
                gate = StepGate.Fire,
            ),
        )

        NightMarkers.MINION_INFO -> minionInfo(ctx)
        NightMarkers.DEMON_INFO -> demonInfo(ctx)
        NightMarkers.MINION_BLUFFS, NightMarkers.DEMON_BLUFFS_ONLY -> emptyList()
        MARIONETTE -> marionetteInfo(ctx)
        else -> emptyList()
    }

    // ---- the two information rows ------------------------------------

    private fun minionInfo(ctx: MarkerContext): List<NightStep> {
        if (!ctx.isFirstNight || !infoStepsApply(ctx.state)) return emptyList()
        val minions = minionSeats(ctx)
        val demons = demonSeats(ctx)
        if (minions.isEmpty()) return emptyList()
        // A Poppy Grower keeps the evil team apart until they die.
        val poppy = poppyGrowerActive(ctx)
        val detail = buildString {
            append("Wake all Minions")
            if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
            append(". They see each other, then point out the Demon")
            if (demons.isNotEmpty()) append(" (${demons.joinToString { it.name }})")
            append(".")
            if (demons.isEmpty()) {
                append(" There is no Demon seat yet — they see each other only.")
            }
        }
        return listOf(
            marker(
                ctx,
                title = "Minion info",
                detail = detail,
                gate = if (poppy) {
                    StepGate.Skip("a Poppy Grower is in play — the evil team stays apart")
                } else {
                    StepGate.Fire
                },
                holderIds = minions.map { it.id },
                wakeCounts = WakeCount.INFORMED,
            ),
        )
    }

    private fun demonInfo(ctx: MarkerContext): List<NightStep> {
        if (!ctx.isFirstNight || !infoStepsApply(ctx.state)) return emptyList()
        val demons = demonSeats(ctx)
        val bluffSlot = ctx.bluffs.firstOrNull { it.key == BluffRequirement.DEMON_KEY }?.stepSlotId
        val bluffsOnly = bluffSlot == NightMarkers.DEMON_BLUFFS_ONLY
        if (demons.isEmpty()) return emptyList()
        val minions = minionSeats(ctx)
        val marionettes = seatsOf(ctx, MARIONETTE)
        val lunatics = seatsOf(ctx, LUNATIC)
        val bluffs = ctx.state.demonBluffIds.mapNotNull { ctx.lookup(it)?.name }
        val detail = buildString {
            append("Wake the Demon")
            if (demons.isNotEmpty()) append(" (${demons.joinToString { it.name }})")
            if (bluffsOnly) {
                append(". A Poppy Grower is in play: show the bluffs ONLY — no Minions")
            } else {
                append(". Point out the Minions")
                if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
                if (marionettes.isNotEmpty()) {
                    append(". Point out the Marionette")
                    append(" (${marionettes.joinToString { it.name }})")
                }
            }
            append(", then show 3 not-in-play good characters as bluffs")
            if (bluffs.isNotEmpty()) {
                append(": ${bluffs.joinToString()}")
            } else {
                append(" — no bluffs chosen yet! Pick them from the menu")
            }
            append(".")
            if (lunatics.isNotEmpty()) {
                append(
                    " Also show the Demon who the LUNATIC is " +
                        "(${lunatics.joinToString { it.name }}) — the Demon can mirror their fake kills.",
                )
            }
        }
        return listOf(
            marker(
                ctx,
                title = if (bluffsOnly) "Demon bluffs" else "Demon info",
                slotId = if (bluffsOnly) NightMarkers.DEMON_BLUFFS_ONLY else NightMarkers.DEMON_INFO,
                detail = detail,
                gate = StepGate.Fire,
                holderIds = demons.map { it.id },
                cards = if (bluffs.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        CardOffer(
                            label = "SHOW: BLUFFS",
                            card = ShowCardSpec.BluffsCard(ctx.state.demonBluffIds),
                            truthful = true,
                        ),
                    )
                },
                wakeCounts = WakeCount.INFORMED,
            ),
        )
    }

    /**
     * Teensyville only: with no Demon info step, the Demon is still shown who the
     * Marionette is (`characters.json` marionette, verbatim).
     */
    private fun marionetteInfo(ctx: MarkerContext): List<NightStep> {
        if (!ctx.isFirstNight || infoStepsApply(ctx.state)) return emptyList()
        val marionettes = seatsOf(ctx, MARIONETTE)
        if (marionettes.isEmpty()) return emptyList()
        val demons = demonSeats(ctx)
        val detail = buildString {
            append("Wake the Demon")
            if (demons.isNotEmpty()) append(" (${demons.joinToString { it.name }})")
            append(". Show the “This player is” and Marionette tokens, then point to")
            append(" ${marionettes.joinToString { it.name }}.")
        }
        return listOf(
            marker(
                ctx,
                title = "Marionette info",
                detail = detail,
                gate = StepGate.Fire,
                holderIds = demons.map { it.id },
                wakeCounts = WakeCount.INFORMED,
            ),
        )
    }

    // ---- helpers -----------------------------------------------------

    /**
     * "In games of 7 or more players" — counted over resident seats, with the
     * storyteller's one-tap override for Traveller-inflated tables (lead D8).
     */
    private fun infoStepsApply(state: GameState): Boolean {
        val countTravellers = Decisions.bool(state, Decisions.COUNT_TRAVELLERS_FOR_INFO)
        val n = if (countTravellers) state.seats.size else state.seats.count { !it.isTraveller }
        return n >= 7
    }

    private fun poppyGrowerActive(ctx: MarkerContext): Boolean =
        ctx.bluffs.any { it.stepSlotId == NightMarkers.DEMON_BLUFFS_ONLY }

    private fun seatsOf(ctx: MarkerContext, id: String): List<Player> =
        ctx.state.seats.filter { Character.normalizeId(it.characterId.orEmpty()) == id }

    private fun demonSeats(ctx: MarkerContext): List<Player> =
        ctx.state.seats.filter { it.characterId?.let(ctx.lookup)?.team == Team.DEMON }

    /** The Marionette never appears in the Minion list — they must not know. */
    private fun minionSeats(ctx: MarkerContext): List<Player> = ctx.state.seats.filter {
        Character.normalizeId(it.characterId.orEmpty()) != MARIONETTE &&
            it.characterId?.let(ctx.lookup)?.team == Team.MINION
    }

    private fun marker(
        ctx: MarkerContext,
        title: String,
        detail: String,
        gate: StepGate,
        slotId: String = ctx.slot,
        holderIds: List<Long> = emptyList(),
        cards: List<CardOffer> = emptyList(),
        wakeCounts: WakeCount = WakeCount.NONE,
    ): NightStep = NightStep(
        key = StepKey(slotId),
        slotId = slotId,
        order = ctx.order,
        title = title,
        detail = detail,
        holderIds = holderIds,
        style = ctx.style,
        gate = gate,
        prompt = NightGuide.forStep(slotId, ctx.style)?.instructions.orEmpty(),
        cards = cards,
        badges = if (wakeCounts == WakeCount.INFORMED) {
            listOf("does not count for the Chambermaid")
        } else {
            emptyList()
        },
        wakeCounts = wakeCounts,
    )
}
