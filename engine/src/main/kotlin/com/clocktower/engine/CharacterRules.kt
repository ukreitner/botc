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

    /**
     * Fires when ANOTHER ability chooses this seat (W7I).
     *
     * The Goon is the whole reason it exists: "each night, the 1st player to
     * choose you with their ability is drunk until dusk, and you become their
     * alignment". Nothing else in the schema is reactive — every other hook is
     * something this character does on its own row — so before wave 7 the Goon
     * had no behaviour at all.
     *
     * The effects are applied with the CHOSEN seat as the source: `Ref.Source` is
     * this rule's holder and `Ref.Target` is whoever chose them.
     */
    val onChosen: ((ChosenContext) -> List<NightEffect>)? = null,

    // ---- standing / tokens / death ----
    val standing: StandingRule? = null,
    val tokens: List<TokenRule> = emptyList(),
    val onDeath: List<DeathTrigger> = emptyList(),

    // ---- believing you hold somebody else's ability (`ActingRole.alwaysFalse`) ----
    /**
     * The marker this character's FAKE choices leave on the board.
     *
     * A seat running an ability it does not have — a Lunatic, a Drunk or a
     * Marionette shown a Demon token — has every effect of that ability dropped
     * by the planner (lead D70). The Lunatic is the one character whose illusion
     * is still drawn in the grimoire: the official `Chosen` ×3. The token is
     * declared HERE, on the believer's own row, so `NightPlan` places it without
     * ever naming a character.
     *
     * A row that declares none leaves nothing behind, which is exactly the Drunk
     * and the Marionette: they own no marker in the official data.
     */
    val illusionToken: TokenRule? = null,

    /**
     * "The Demon knows … who you choose at night" — this character's nightly
     * choice is shown to every seat on that team.
     *
     * The Lunatic is the only official card that says it. The planner renders it
     * on the informed seats' own rows (and on `DEMON_INFO`) from the CHOICE
     * ledger, and writes them a TOLD row when the step is ticked; which team is
     * told is per-character knowledge and lives here (§3.4.3).
     */
    val informsChoiceTo: Team? = null,

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
     * Other nights by default; [JinxRule.firstNight] opts one in to night 1 as
     * well. Every jinx the official set defines today is an "each night*"
     * ability, so nothing sets it — the slot exists so a homebrew or a future
     * official first-night jinx does not need a second map.
     */
    val jinxRules: Map<String, JinxRule> = emptyMap(),
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

/**
 * One entry of [CharacterRule.jinxRules]: the behaviour a jinx adds, and which
 * nights it is on.
 *
 * Split out of the bare [NightRule] in W7b. The map used to say "other nights"
 * by construction, which was true of all 131 official jinxes and untrue of the
 * schema: a jinx that only bites on night 1 (a setup-time pairing, a homebrew)
 * had nowhere to say so, and `NightPlan.build` skipped the whole map on the
 * first night.
 */
data class JinxRule(
    val rule: NightRule,
    /**
     * Apply on the FIRST night as well as the others. Default false, which is
     * every official jinx: they are all "each night*" abilities.
     */
    val firstNight: Boolean = false,
)

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
    /**
     * A [GameState.counters] key this ability adds one to each time it is used
     * (lead D72) — the Yaggababble's "for each time you said it publicly today".
     * Empty = this ability counts nothing. `DayAbilities.use` does the bumping.
     */
    val counterKey: String = "",
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

/** Everything a [CharacterRule.onChosen] lambda may look at. */
class ChosenContext(
    /** The board AFTER the choosing ability's own effects landed. */
    val state: GameState,
    /**
     * The board as it was AT THE MOMENT OF THE CHOICE, before the chooser's
     * effects landed.
     *
     * The Goon needs exactly this: a Poisoner who poisons the Goon still wakes
     * the Goon's ability, because the Goon was sober when they were chosen.
     * Asking [state] would let the Poisoner cancel the reaction to itself.
     */
    val before: GameState,
    val lookup: (String) -> Character?,
    val night: Int,
    /** The seat that was chosen — this rule's holder. */
    val holder: Player,
    /** The seat whose ability chose them. Null for a seatless or group step. */
    val chooser: Player?,
    /** The ability that did the choosing: "monk", "poisoner", "fortuneteller". */
    val chooserAbilityId: String,
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
    private const val MAGICIAN = "magician"
    private const val VIZIER = "vizier"
    private const val LEGION = "legion"

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
        // W7I: "Minions think you are a Demon" — the Magician is shown to the
        // Minions ALONGSIDE the real Demon, and they are not told which is which.
        val magicians = if (magicianWorks(ctx)) seatsOf(ctx, MAGICIAN) else emptyList()
        val shown = (demons + magicians).sortedBy { seatIndex(ctx, it) }
        val detail = buildString {
            append("Wake all Minions")
            if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
            append(". They see each other, then point out the Demon")
            if (shown.isNotEmpty()) append(" (${shown.joinToString { it.name }})")
            append(".")
            if (magicians.isNotEmpty()) {
                append(
                    " A MAGICIAN is in play: point out the Magician too, in seat order, and " +
                        "say nothing about which is which. The Minions are not told there are two.",
                )
            }
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
                // The two classic info tokens, pre-filled. The row used to
                // offer no card at all, so the storyteller had to find "THIS IS
                // THE DEMON" three taps away in the generic card catalogue and
                // then point by hand (playtest B P1 #7).
                cards = pointCards(ctx, "THIS IS THE DEMON", shown),
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
        // W7I, the Magician's three clauses:
        //  - "The Demon thinks you are a Minion": the Magician is pointed out
        //    with the Minions, interleaved in seat order, not appended;
        //  - the Marionette clause is SUPPRESSED — the Demon must not be able to
        //    subtract the Marionette from the list and find the Magician;
        //  - the Vizier jinx: a Vizier knows who the Demon is, so the Magician
        //    fools nobody and the row says so instead of pretending.
        val magic = magicianWorks(ctx)
        val magicians = if (magic) seatsOf(ctx, MAGICIAN) else emptyList()
        val vizier = magic && seatsOf(ctx, VIZIER).isNotEmpty()
        val pointOut = (minions + magicians).sortedBy { seatIndex(ctx, it) }
        // "You register as both a Minion and a Demon": a Legion game has no
        // separate Minion list to show, so the row says what it really is.
        val legion = seatsOf(ctx, LEGION)
        val detail = buildString {
            append("Wake the Demon")
            if (demons.isNotEmpty()) append(" (${demons.joinToString { it.name }})")
            if (bluffsOnly) {
                append(". A Poppy Grower is in play: show the bluffs ONLY — no Minions")
            } else if (legion.isNotEmpty()) {
                append(". LEGION: almost every player is Legion and they all know it. ")
                append("Point out every Legion (${legion.joinToString { it.name }}) — ")
                append("there are no Minions to show separately")
            } else {
                append(". Point out the Minions")
                if (pointOut.isNotEmpty()) append(" (${pointOut.joinToString { it.name }})")
                if (magicians.isNotEmpty()) {
                    append(
                        ". A MAGICIAN is in play: they are in that list, in seat order, and the " +
                            "Demon is NOT told which. Do not point out the Marionette",
                    )
                } else if (marionettes.isNotEmpty()) {
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
            if (vizier) {
                append(
                    " JINX: a Vizier is in play and knows who the Demon is, so the Magician's " +
                        "confusion fools nobody — the Vizier may say so out loud.",
                )
            }
            if (lunatics.isNotEmpty()) {
                append(
                    " Also show the Demon who the LUNATIC is " +
                        "(${lunatics.joinToString { it.name }}) — the Demon can mirror their fake kills.",
                )
            }
            // …and, when the informer's slot comes BEFORE this one on the
            // nightsheet, what they chose tonight. The Lunatic's first-night
            // slot is 16 and DEMON_INFO is 17, so a believed Pukka has already
            // acted by the time the real Demon opens their eyes.
            for (line in choiceBriefings(ctx.state, ctx.lookup, demons.firstOrNull())) {
                if (!line.reported) continue
                append(" ").append(line.text)
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
                cards = buildList {
                    if (!bluffsOnly) {
                        addAll(
                            pointCards(
                                ctx,
                                if (legion.isNotEmpty()) "THESE PLAYERS ARE LEGION" else "THESE ARE YOUR MINIONS",
                                if (legion.isNotEmpty()) legion else pointOut,
                            ),
                        )
                    }
                    if (bluffs.isNotEmpty()) {
                        add(
                            CardOffer(
                                label = "SHOW: BLUFFS",
                                card = ShowCardSpec.BluffsCard(ctx.state.demonBluffIds),
                                truthful = true,
                            ),
                        )
                    }
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

    // ---- somebody else's choice, shown to a whole team ----------------

    /**
     * One "you are told who they chose" line, owed to a seat because ANOTHER
     * character's card says so.
     *
     * The Lunatic is the only official one — *"The Demon knows who you are & who
     * you choose at night"* — and the Lunatic's registry row is what declares it
     * (`CharacterRule.informsChoiceTo`). The planner renders these and writes the
     * TOLD rows; it never names a character.
     */
    internal data class ChoiceBriefing(
        /** The informer's own character id: "lunatic". */
        val sourceId: String,
        /** The seat whose choice is being reported. */
        val informerId: Long,
        /** Who they picked tonight. Empty for "chose nobody" and for a dead informer. */
        val targetIds: List<Long> = emptyList(),
        /** One line, storyteller voice. */
        val text: String = "",
        /**
         * True when a CHOICE was actually recorded tonight, so there is something
         * to write a TOLD row about. A dead informer, or one who has not reached
         * their row yet, produces a line but no ledger entry.
         */
        val reported: Boolean = false,
    )

    /**
     * Every briefing [seat] is owed tonight, in seat order.
     *
     * Keyed off the INFORMER'S OWN character (a Lunatic is a good Outsider) and
     * the informed seat's team, so a Lunatic never briefs themselves and a Drunk
     * or Marionette holding a Demon token briefs nobody.
     */
    internal fun choiceBriefings(
        state: GameState,
        lookup: (String) -> Character?,
        seat: Player?,
    ): List<ChoiceBriefing> {
        seat ?: return emptyList()
        val team = seat.characterId?.let(lookup)?.team ?: return emptyList()
        return state.seats.mapNotNull { other ->
            if (other.id == seat.id) return@mapNotNull null
            val id = other.characterId?.let(Character::normalizeId) ?: return@mapNotNull null
            val rule = CharacterRules.all[id] ?: return@mapNotNull null
            if (rule.informsChoiceTo == null || rule.informsChoiceTo != team) return@mapNotNull null
            briefing(state, lookup, rule.id, other)
        }
    }

    private fun briefing(
        state: GameState,
        lookup: (String) -> Character?,
        sourceId: String,
        informer: Player,
    ): ChoiceBriefing {
        val name = lookup(sourceId)?.name ?: sourceId
        if (!informer.alive) {
            return ChoiceBriefing(
                sourceId = sourceId,
                informerId = informer.id,
                text = "The $name (${informer.name}) is dead — no fake attack tonight.",
            )
        }
        val choice = Memory.choiceTonight(state, informer.id)
            ?: return ChoiceBriefing(
                sourceId = sourceId,
                informerId = informer.id,
                text = "The $name (${informer.name}) has not chosen anybody yet tonight.",
            )
        val chosen = choice.targetIds.mapNotNull { state.player(it)?.name }
        return ChoiceBriefing(
            sourceId = sourceId,
            informerId = informer.id,
            targetIds = choice.targetIds,
            text = if (chosen.isEmpty()) {
                "The $name (${informer.name}) chose nobody tonight."
            } else {
                "The $name (${informer.name}) chose ${joinNames(chosen)}."
            },
            reported = true,
        )
    }

    private fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> names.dropLast(1).joinToString() + " and " + names.last()
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

    /**
     * The two ways to say "one of these players is X" across a table: the bare
     * info token the storyteller points with, and the same words over the
     * players' names and seat numbers.
     */
    private fun pointCards(ctx: MarkerContext, prefix: String, who: List<Player>): List<CardOffer> {
        if (who.isEmpty()) return emptyList()
        return listOf(
            CardOffer(
                label = "SHOW: “$prefix”",
                card = ShowCardSpec.Message(prefix),
                truthful = true,
            ),
            CardOffer(
                label = "SHOW: $prefix — ${who.joinToString { it.name }}",
                card = ShowCardSpec.PointCard(
                    prefix = prefix,
                    playerNames = who.map { it.name },
                    seatNumbers = who.map { seatIndex(ctx, it) + 1 },
                ),
                truthful = true,
            ),
        )
    }

    private fun seatIndex(ctx: MarkerContext, p: Player): Int =
        ctx.state.seats.indexOfFirst { it.id == p.id }

    /**
     * True when a Magician is in play with a WORKING ability (W7I).
     *
     * A drunk or poisoned Magician confuses nobody: the Demon sees the real
     * Minions and the Minions see the real Demon, which is exactly the shape a
     * content transform has to be able to turn off.
     */
    private fun magicianWorks(ctx: MarkerContext): Boolean =
        seatsOf(ctx, MAGICIAN).any { Status.hasAbility(ctx.state, ctx.lookup, it.id) }

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
        // The row's OWN words are the prompt — they name this game's seats
        // ("Wake all Minions (Ben) … point out the Demon (Fay)"). The generic
        // run-book, twelve paragraphs of characters that cannot be in the
        // running script, stays in `NightGuide` and reaches the storyteller
        // through the card's drawer only, once (playtest B P2 #11).
        detail = "",
        holderIds = holderIds,
        style = ctx.style,
        gate = gate,
        prompt = detail,
        cards = cards,
        badges = if (wakeCounts == WakeCount.INFORMED) {
            listOf("does not count for the Chambermaid")
        } else {
            emptyList()
        },
        wakeCounts = wakeCounts,
    )
}
