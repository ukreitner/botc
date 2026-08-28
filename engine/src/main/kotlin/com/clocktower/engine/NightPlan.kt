package com.clocktower.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    companion object {
        /** The half of a [Reduced] step that a choice belongs to. */
        const val CHOOSE = "choose"

        /** The half that runs anyway: a deferred kill, a standing token. */
        const val PENDING = "pending"

        /** Passive consequences of the step that need no input. */
        const val PASSIVE = "passive"
    }
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
    /** Whether waking here counts for the Chambermaid (lead D13). */
    val wakeCounts: WakeCount = WakeCount.ACT,
) {
    val required: Boolean get() = gate !is StepGate.Skip
    val holderId: Long? get() = key.holderId
    val abilityId: String get() = key.abilityId

    /** True when this row still owes the storyteller something. A Skip is done by definition. */
    fun isDone(done: Set<String>): Boolean = !required || key.token in done

    /**
     * Every seat this row wakes: the group for a group step ([holderIds]),
     * otherwise the single holder. WP8 replaced the deprecated `playerIds`
     * alias with this name; `id` (an alias for [slotId]) is gone.
     */
    val wakes: List<Long> get() = holderIds.ifEmpty { listOfNotNull(key.holderId) }
}

/** What the storyteller entered on a step. */
@Serializable
data class NightInput(
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    /**
     * (seat, character) pairs for a [ChoosePlayersAndCharacters] step — the
     * Engineer's rebuild, where each seat gets its OWN character.
     *
     * Kept separate from the parallel [playerIds] / [characterIds] lists so a
     * partially-filled answer can never pair a seat with the wrong character.
     */
    val assignments: List<Pair<Long, String>> = emptyList(),
    val yes: Boolean? = null,
    val number: Int? = null,
    /** The "they chose nobody / were not woken" answer — a REAL answer, recorded. */
    val none: Boolean = false,
    val optionId: String = "",
    /**
     * One answer per [Options] stage of a [Sequence], in stage order — the
     * Al-Hadikhia's three independent live/die answers. A stage with no entry
     * falls back to [optionId], so a plain single-[Options] step is unchanged.
     */
    val optionIds: List<String> = emptyList(),
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

    fun step(key: StepKey): NightStep? =
        steps.firstOrNull { it.key == key } ?: steps.firstOrNull { it.key.token == key.token }

    companion object {

        // ---- construction ------------------------------------------------

        /**
         * Pure. Rebuild after every mutation; never cache (invariant I6).
         *
         * 1. the base list, `order = index * 100` so insertions fit between;
         * 2. one row per [ActingRole] whose `slotId` matches, in seat order;
         * 3. the registry's gate decides fire / reduced / conditional / skip;
         * 4. derived rows — `Prompt(at = TONIGHT)`, first-night re-runs and
         *    characters created tonight — are inserted;
         * 5. anything that became true after the cursor is re-stamped there and
         *    badged "out of order".
         */
        fun build(state: GameState, lookup: (String) -> Character?): NightPlan {
            val ctx = PlanContext(state, lookup)
            val steps = mutableListOf<NightStep>()
            val roles = Identity.allActingRoles(state, lookup)
            val bySlot = roles.groupBy { it.slotId }
            val order = ctx.order

            for ((index, slot) in order.withIndex()) {
                val at = index * BASE_SPACING
                if (NightInfo.owns(slot)) {
                    steps += NightInfo.steps(
                        MarkerContext(state, lookup, slot, at, ctx.style, ctx.isFirstNight, ctx.bluffs),
                    )
                    continue
                }
                val slotRoles = bySlot[slot].orEmpty()
                if (slotRoles.isEmpty()) {
                    if (slot in state.fabledIds) {
                        steps += seatlessStep(ctx, slot, at, ctx.fabledHolders(slot), WakeCount.NONE)
                    } else if (slot in ctx.seatless) {
                        // In play, but the bag seated nobody: a Lil' Monsta game
                        // has no Demon seat at all (lead D18). One group row, no
                        // holder — the registry row does the waking.
                        steps += seatlessStep(ctx, slot, at, emptyList(), WakeCount.ACT)
                    }
                    continue
                }
                val rule = CharacterRules.of(slotRoles.first().abilityId, lookup(slotRoles.first().abilityId))
                // A bluff set owed TO this slot's own holder is handed out on
                // their row; one owed to somebody else (the Snitch's Minions)
                // becomes a row of its own.
                val owed = if (!ctx.isFirstNight) {
                    emptyList()
                } else {
                    ctx.bluffs.filter { it.stepSlotId == slot && it.recipientId != null }
                }
                val holders = slotRoles.map { it.playerId }.toSet()
                val (folded, separate) = owed.partition { it.recipientId in holders }
                if (rule.groupStep) {
                    steps += roleStep(ctx, slotRoles.first(), slot, at, group = slotRoles.map { it.playerId })
                } else {
                    slotRoles.forEachIndexed { n, role ->
                        steps += withBluffs(
                            ctx,
                            roleStep(ctx, role, slot, at + n),
                            folded.filter { it.recipientId == role.playerId },
                        )
                    }
                }
                steps += bluffSteps(ctx, separate, at)
            }

            steps += homebrewSteps(ctx, roles, order, steps)
            val base = steps.sortedBy { it.order }
            val inserted = insertions(ctx, base)
            return NightPlan(
                cycle = state.cycle,
                isFirstNight = ctx.isFirstNight,
                steps = beforeDawn(restamp(ctx, base, base + inserted)).sortedBy { it.order },
            )
        }

        /**
         * Dawn closes the night, so nothing may sort after it.
         *
         * Two paths could put a row there. `restamp` stamps an out-of-order row
         * at `cursor + 0.5`, and the cursor is the Dawn marker itself once
         * everything before it is done or skipped; and `positionOf` falls back to
         * "past the end of the list" for a slot tonight's order does not carry.
         * Either way the Professor's re-run of a resurrected Grandmother's first
         * night landed AFTER the Dawn card, which is also the card whose primary
         * button opens the day (playtest D, P1-8).
         *
         * Relative order among the moved rows is kept; they land just before Dawn
         * and after every base row, which is where "insert after the cursor"
         * meant to put them.
         */
        private fun beforeDawn(steps: List<NightStep>): List<NightStep> {
            val dawn = steps.firstOrNull { it.slotId == NightMarkers.DAWN } ?: return steps
            val over = steps
                .filter { it.slotId != NightMarkers.DAWN && it.order >= dawn.order }
                .sortedBy { it.order }
            if (over.isEmpty()) return steps
            val gap = 0.5 / (over.size + 1)
            val moved = over
                .mapIndexed { n, step -> step.key.token to (dawn.order - 0.5 + gap * (n + 1)) }
                .toMap()
            return steps.map { step -> moved[step.key.token]?.let { step.copy(order = it) } ?: step }
        }

        // ---- resolution ---------------------------------------------------

        /**
         * Applies a step's input: validates constraints AT RESOLVE TIME, applies
         * `perTarget` effects ONE TARGET AT A TIME re-deriving impairment, positional
         * poison and protections between each, appends CHOICE / WOKE / MALFUNCTION
         * ledger entries, and ticks the step.
         *
         * A [StepGate.Reduced] step still runs its `pending` half — that is the
         * Exorcised Pukka's standing victim (lead D24).
         */
        fun resolve(
            state: GameState,
            lookup: (String) -> Character?,
            key: StepKey,
            input: NightInput,
        ): GameState {
            // No such row tonight: the plan has moved on (a prompt row that was
            // discharged, an insertion that has been consumed). Resolving is
            // still "this step is finished", never "un-finish it" — the primary
            // button is idempotent (fix wave 1, Fix-B).
            val step = build(state, lookup).step(key) ?: return markDone(state, key.token)
            val ctx = PlanContext(state, lookup)
            val nightCtx = ctx.nightContext(step)
            val rule = CharacterRules.of(step.abilityId, lookup(step.abilityId))
            val nightRule = rule.nightRule(step.style == WakeStyle.FIRST_NIGHT)
            val action = step.action
            val holderId = step.holderId
            // "Malfunctioning" is about the ABILITY, not about being dead: a
            // Ravenkeeper acts *because* they died, and a Boffin-granted row works
            // through poison. Only a live holder whose role does not work counts.
            val holder = holderId?.let { state.player(it) }
            val impaired = holder != null && holder.alive && !(
                nightCtx.role?.let { Status.roleWorks(state, lookup, it) }
                    ?: Status.hasAbility(state, lookup, holder.id)
                )

            // A seat running an ability it does not have (lead D70). Nothing the
            // believed row declares may change the game — including its DEFERRED
            // half, which is the one an Exorcised Demon would still run. A
            // believed Pukka's standing victim must not die a night late.
            val illusion = nightCtx.role?.alwaysFalse == true
            val ownMark = illusionMarker(holder)

            // The deferred half is computed from the state BEFORE the choice lands,
            // and applied after it, so "poison the new target, then the old one
            // dies" is the order the rules ask for (lead D4).
            val declaredPending = nightRule?.pending?.invoke(nightCtx).orEmpty()
            val pending = if (illusion) {
                inert(declaredPending, keepMarkers = ownMark == null)
            } else {
                declaredPending
            }

            val targets = resolvedTargets(state, lookup, step, action, input)
            // A per-pair answer names its characters in `assignments` and an
            // answer-set branch in `ActionOption.characterIds`; the whole-step
            // list is what everything downstream reads.
            val chosenCharacters = input.characterIds
                .ifEmpty { input.assignments.map { it.second } }
                .ifEmpty { (action as? Options)?.let { chosenOption(it, input) }?.characterIds.orEmpty() }
            val scope = EffectScope(
                sourceId = holderId,
                sourceCharacterId = step.abilityId,
                targets = targets,
                characterIds = chosenCharacters,
                previous = Memory.forbiddenTargets(state, step.abilityId, holderId),
            )

            var next = state
            val choiceAllowed = when (val gate = step.gate) {
                is StepGate.Reduced -> StepGate.CHOOSE in gate.allow
                else -> true
            }
            if (choiceAllowed && action != null) {
                next = applyAction(next, lookup, action, input, targets, scope)
                // Nobody was chosen BY AN ABILITY here: a Goon a Lunatic pointed
                // at was not chosen at all, so the reactive half stays asleep.
                if (!illusion) next = fireOnChosen(next, state, lookup, step, targets)
            }
            if (illusion && ownMark != null) {
                // The illusion is still drawn in the grimoire — and it is the
                // BELIEVER'S own marker, never the believed character's.
                next = applyEffects(next, lookup, listOf(ownMark), scope)
            }
            next = applyEffects(next, lookup, pending, scope)

            next = recordChoice(next, step, targets, input, chosenCharacters, impaired)
            next = recordWakes(next, step, targets)
            next = recordInformed(next, lookup, step)
            if (impaired && step.wakeCounts == WakeCount.ACT && step.required) {
                next = recordMalfunction(next, step, holderId)
            }
            next = discharge(next, step)
            next = markDone(next, step.key.token)
            return Effects.reconcile(next, lookup)
        }

        /**
         * Runs `CharacterRule.onChosen` for every seat this step just picked
         * (W7I) — the reactive half of the registry the Goon needed.
         *
         * The effects are applied with the CHOSEN seat as the source, so a row
         * writes `Ref.Source` for itself and `Ref.Target` for whoever chose it.
         * The chooser's own effects have already landed: the Goon's drunkenness
         * arrives after the choice it is reacting to, which is the order the
         * table plays it in.
         */
        private fun fireOnChosen(
            state: GameState,
            before: GameState,
            lookup: (String) -> Character?,
            step: NightStep,
            targets: List<Long>,
        ): GameState {
            var next = state
            for (targetId in targets) {
                val chosen = next.player(targetId) ?: continue
                val id = chosen.characterId?.let(Character::normalizeId) ?: continue
                val hook = CharacterRules.all[id]?.onChosen ?: continue
                val effects = hook(
                    ChosenContext(
                        state = next,
                        before = before,
                        lookup = lookup,
                        night = next.cycle,
                        holder = chosen,
                        chooser = step.holderId?.let { next.player(it) },
                        chooserAbilityId = step.abilityId,
                    ),
                )
                if (effects.isEmpty()) continue
                next = applyEffects(
                    next,
                    lookup,
                    effects,
                    EffectScope(
                        sourceId = chosen.id,
                        sourceCharacterId = id,
                        targets = listOfNotNull(step.holderId),
                        characterIds = emptyList(),
                        current = step.holderId,
                    ),
                )
            }
            return next
        }

        /**
         * Whether the row for [characterId] on [holderId]'s seat gives its
         * information TONIGHT.
         *
         * `NightRule.infoId = ""` says "this step computes no information" and
         * the planner has always honoured it for the cards it builds itself. A
         * screen that derives the calculator key from the ability alone does not
         * — which is why the Godfather's first-night "these Outsiders are in
         * play" block, plus its four SHOW buttons, was rendered again on every
         * later night (playtest D, P0-4). The engine is the authority; ask it.
         *
         * Deliberately conservative: only an explicit `infoId = ""` on the rule
         * that runs tonight suppresses anything. No row, no rule, or a rule that
         * leaves `infoId` at its default keeps whatever the caller asked for.
         */
        fun givesInfoTonight(
            state: GameState,
            lookup: (String) -> Character?,
            characterId: String,
            holderId: Long? = null,
        ): Boolean {
            val id = Character.normalizeId(characterId)
            val step = build(state, lookup).steps.firstOrNull {
                it.abilityId == id && (holderId == null || it.holderId == holderId)
            } ?: return true
            val rule = CharacterRules.of(step.abilityId, lookup(step.abilityId))
            val nightRule = rule.nightRule(step.style == WakeStyle.FIRST_NIGHT) ?: return true
            return nightRule.infoId != ""
        }

        /**
         * Un-ticks a ticked row and ticks an un-ticked one.
         *
         * This is the storyteller CORRECTING themselves — the "undo this step,
         * put it back on the sheet" affordance in the card's drawer and the
         * collapsed list. It is never what a primary button does: see
         * [markDone].
         */
        fun toggleDone(state: GameState, token: String): GameState = toggleDone(state, token, false)

        /**
         * Ticks a row and leaves a ticked row ticked.
         *
         * Every "do it" path is idempotent (playtest fix wave 1, Fix-B): a
         * storyteller who presses the primary twice — on a slow frame, on a row
         * whose card is still open, on a step the plan has since dropped
         * (a discharged prompt row, a consumed insertion) — must not silently
         * put the step back on the sheet. Before this, [resolve]'s own fallback
         * for "this key is no longer in the plan" called the TOGGLE, so pressing
         * the primary again un-ticked the very row it had just finished.
         */
        fun markDone(state: GameState, token: String): GameState = toggleDone(state, token, true)

        private fun toggleDone(state: GameState, token: String, forceDone: Boolean): GameState =
            state.copy(
                nightStepsDone = if (token in state.nightStepsDone && !forceDone) {
                    state.nightStepsDone - token
                } else {
                    state.nightStepsDone + token
                },
            )

        // ---- the two derived counts ---------------------------------------

        /**
         * Chambermaid: how many of [targets] woke for their OWN ability tonight
         * (lead D13). Wakes already recorded in the ledger count; so does the
         * un-run tail of tonight's plan, which is the whole reason the Chambermaid
         * sits third from last. `INFORMED` wakes (Minion info, an Exorcist's
         * target) and a `Reduced` Demon count for nothing — that is the wiki's own
         * Exorcist/Shabaloth example.
         */
        fun wokeCount(
            state: GameState,
            lookup: (String) -> Character?,
            targets: List<Long>,
        ): Int {
            val woke = state.ledger
                .filter {
                    it.kind == LedgerKind.WOKE && it.cycle == state.cycle && it.atNight && it.genuine
                }
                .mapNotNull { it.actorId }
                .toSet()
            val projected = build(state, lookup).steps
                .filter { it.wakeCounts == WakeCount.ACT && it.gate !is StepGate.Skip }
                .filter { it.gate !is StepGate.Reduced }
                .flatMap { it.wakes }
                .toSet()
            return targets.distinct().count { it in woke || it in projected }
        }

        /**
         * Mathematician: how many abilities malfunctioned tonight, counted per
         * seat. Pass the Mathematician's own seat as [excluding] — the wiki is
         * explicit that they do not detect their own ability failing.
         */
        fun malfunctionCount(state: GameState, night: Int, excluding: Long? = null): Int =
            state.ledger
                .filter { it.kind == LedgerKind.MALFUNCTION && it.cycle == night && it.atNight }
                .mapNotNull { it.actorId }
                .filterNot { it == excluding }
                .distinct()
                .size

        // ==================================================================
        // internals
        // ==================================================================

        /** Base list entries are 100 apart so insertions always fit between. */
        private const val BASE_SPACING = 100.0

        /** Bluff hand-out rows sit just after the character that owes them. */
        private const val BLUFF_OFFSET = 50.0

        private val json = Json { ignoreUnknownKeys = true }

        /** The canonical wake orders, read once from the bundled dataset. */
        private val orders: NightAndJinxes? by lazy {
            BotcResources.readOrNull("/botc/data/night_and_jinxes.json")
                ?.let { json.decodeFromString<NightAndJinxes>(it) }
        }

        private fun normalise(ids: List<String>): List<String> =
            ids.map { if (it in NightMarkers.all) it else Character.normalizeId(it) }

        private val firstNightOrder: List<String> by lazy { normalise(orders?.firstNight.orEmpty()) }
        private val otherNightOrder: List<String> by lazy { normalise(orders?.otherNight.orEmpty()) }

        // ---- one build's shared reads -------------------------------------

        /** Everything one `build` reads more than once, computed exactly once. */
        private class PlanContext(
            val state: GameState,
            val lookup: (String) -> Character?,
        ) {
            val isFirstNight: Boolean = state.cycle <= 1
            val style: WakeStyle =
                if (isFirstNight) WakeStyle.FIRST_NIGHT else WakeStyle.OTHER_NIGHT
            val order: List<String> = if (isFirstNight) firstNightOrder else otherNightOrder

            /** During a night, "today" is the day that has just ended. */
            private val today: Int = if (state.phase == Phase.DAY) state.cycle else state.cycle - 1

            val diedTonight: Set<Long> = state.deaths
                .filter { it.day == state.cycle && it.atNight && it.resurrectedAtCycle == null }
                .map { it.playerId }
                .toSet()

            val diedToday: Set<Long> = state.deaths
                .filter { it.day == today && !it.atNight && it.resurrectedAtCycle == null }
                .map { it.playerId }
                .toSet()

            val deathsToday: List<DeathEvent> = state.deaths.filter { it.day == today && !it.atNight }

            val executedToday: ExecutionRecord? = state.executions.lastOrNull { it.day == today }

            val resurrectedTonight: Set<Long> = state.deaths
                .filter { it.resurrectedAtCycle == state.cycle }
                .map { it.playerId }
                .toSet()

            val residentCount: Int = state.seats.count { !it.isTraveller }
            val totalSeatCount: Int = state.seats.size

            val bluffs: List<BluffRequirement> by lazy { Bluffs.requirements(state, lookup) }

            /** In play, but the bag seated nobody — a Lil' Monsta game (lead D18). */
            val seatless: Set<String> by lazy { Setup.seatlessInPlayIds(state).toSet() }

            /**
             * Every character on THIS GAME'S SCRIPT. A jinx applies because it is
             * on the script, not because it was dealt (lead D19, the Djinn rule).
             */
            val scriptIds: Set<String> by lazy {
                state.script.characterIds.map(Character::normalizeId).toSet()
            }

            /** The seats an in-play Fabled points at, if it points at any. */
            fun fabledHolders(slot: String): List<Long> = state.fabled
                .firstOrNull { Character.normalizeId(it.id) == slot }
                ?.playerIds
                .orEmpty()

            /** Seats whose character changed tonight — the Pit-Hag's victim, an heir. */
            val changedTonight: List<IdentityRecord> = state.identityLog
                .filter { it.cycle == state.cycle && it.atNight }

            fun holder(role: ActingRole?): Player? = role?.let { state.player(it.playerId) }

            fun wakeContext(role: ActingRole?, holder: Player?) = WakeContext(
                state = state,
                lookup = lookup,
                night = state.cycle,
                holder = holder,
                role = role,
                diedTonight = diedTonight,
                diedToday = diedToday,
                executedToday = executedToday,
                resurrectedTonight = resurrectedTonight,
                residentCount = residentCount,
                totalSeatCount = totalSeatCount,
                deathsToday = deathsToday,
            )

            fun nightContext(role: ActingRole?, holder: Player?, firstNightRules: Boolean) =
                NightContext(
                    state = state,
                    lookup = lookup,
                    night = state.cycle,
                    isFirstNight = firstNightRules,
                    holder = holder,
                    role = role,
                    diedTonight = diedTonight,
                    diedToday = diedToday,
                    executedToday = executedToday,
                    resurrectedTonight = resurrectedTonight,
                )

            fun nightContext(step: NightStep): NightContext {
                val holder = step.holderId?.let { state.player(it) }
                val role = holder
                    ?.let { Identity.actingRoles(state, lookup, it) }
                    ?.firstOrNull { it.abilityId == step.abilityId }
                return nightContext(role, holder, step.style == WakeStyle.FIRST_NIGHT)
            }
        }

        // ---- rows ----------------------------------------------------------

        private fun roleStep(
            ctx: PlanContext,
            role: ActingRole,
            slot: String,
            at: Double,
            group: List<Long> = emptyList(),
            variant: StepVariant = StepVariant.NORMAL,
            promptId: Long? = null,
            extraBadges: List<String> = emptyList(),
        ): NightStep {
            val firstNightRules = variant == StepVariant.FIRST || ctx.isFirstNight
            val style = if (firstNightRules) WakeStyle.FIRST_NIGHT else WakeStyle.OTHER_NIGHT
            val character = ctx.lookup(role.abilityId)
            val rule = CharacterRules.of(role.abilityId, character)
            // A believed ability is not on the script twice: nothing about it is
            // real, so no jinx of the character they THINK they are can bite.
            val jinxed = if (role.alwaysFalse) {
                emptyList()
            } else {
                jinxesOn(rule, ctx.scriptIds, firstNightRules)
            }
            val nightRule = jinxOver(rule.nightRule(firstNightRules), rule, jinxed)
            val holder = ctx.holder(role)
            val nightCtx = ctx.nightContext(role, holder, firstNightRules)
            // The BELIEVER'S own registry row, for a seat running an ability it
            // does not have (lead D70). A Lunatic shown the Po believes in an
            // ability with no first night at all, and before this the row was
            // gated "no ability on this night" and auto-ticked — which threw the
            // whole hand-over away (playtest D, P0-2). Night 1 is when the
            // illusion is handed over: the Demon token, the fake Minions and the
            // Lunatic's own bluffs. What to say is per-character knowledge and
            // stays in the registry (§3.4.3); the planner only merges it in.
            val believerRule = role.sourceId
                ?.takeIf { role.alwaysFalse }
                ?.let { CharacterRules.of(it, ctx.lookup(it)).nightRule(firstNightRules) }
            val gate = when {
                nightRule != null -> nightRule.gate.gate(ctx.wakeContext(role, holder))
                // A believer's row is never "nothing to do".
                believerRule != null -> believerRule.gate.gate(ctx.wakeContext(role, holder))
                else -> StepGate.Skip("no ability on this night")
            }
            val chosen = nightRule?.action?.invoke(nightCtx) ?: infoAction(role.abilityId, nightRule)
            // The picker shape is kept — a believed Shabaloth still takes two,
            // a charged believed Po still takes three — and every consequence
            // is stripped out of it.
            val action = if (role.alwaysFalse) {
                illusory(chosen, keepMarkers = illusionMarker(holder) == null)
            } else {
                chosen
            }
            // "The Demon knows … who you choose at night": what another seat's
            // card owes this holder tonight, declared by that seat's own row.
            val briefing = NightInfo.choiceBriefings(ctx.state, ctx.lookup, holder)
                .joinToString(" ") { it.text }
            val name = character?.name ?: role.abilityId
            val sourceName = role.sourceId?.let { ctx.lookup(it)?.name ?: it }
            val badges = buildList {
                addAll(extraBadges)
                if (holder != null && holder.id in ctx.diedTonight) add("died tonight")
                if (holder != null && !holder.alive && gate !is StepGate.Skip) add("dead — acts anyway")
                if (variant == StepVariant.FIRST) add("first night, again")
                if (ctx.changedTonight.any { it.playerId == holder?.id }) add("new character")
                if (role.alwaysFalse) add("nothing they do has any effect")
                for (other in jinxed) {
                    add("jinx: ${ctx.lookup(other)?.name ?: other} is on the script")
                }
                // W7G: `CharacterRule.demonKillUncertain` had no consumer, so the
                // panel never actually asked. It is the wiki declining to rule
                // whether a Sage / Grandmother / Choirboy fires on this kill.
                if (rule.demonKillUncertain && gate !is StepGate.Skip) {
                    add("the wiki does not rule whether this counts as a Demon kill — you decide")
                }
            }
            return NightStep(
                key = StepKey(role.abilityId, holder?.id, variant),
                slotId = slot,
                order = at,
                title = buildString {
                    append(name)
                    holder?.let { append(" — ").append(it.name) }
                    sourceName?.let { append(" (via the ").append(it).append(")") }
                },
                detail = withEvidence(
                    withEvidence(
                        withEvidence(
                            detailFor(character, firstNightRules),
                            nightRule?.detail?.invoke(nightCtx).orEmpty(),
                        ),
                        believerRule?.detail?.invoke(nightCtx).orEmpty(),
                    ),
                    briefing,
                ),
                sourceId = role.sourceId,
                holderIds = group,
                style = style,
                gate = gate,
                // The planner's own banner (impaired, silenced, dead-but-acts)
                // comes FIRST: a row must never hide the reason its ability will
                // not work tonight. Everything else is appended rather than
                // merged away — the real Demon must see the Lunatic's picks even
                // on a night they are silenced, and an Exorcised Pukka's standing
                // victim still dies, so the row still has to name them (P1-9).
                banner = withEvidence(
                    withEvidence(
                        withEvidence(
                            bannerFor(ctx, role, holder, gate),
                            nightRule?.banner?.invoke(nightCtx).orEmpty(),
                        ),
                        // The hand-over is the point of a believer's first night,
                        // so it goes in ember rather than in the drawer nobody
                        // opens — even when the believed ability has a row of its
                        // own to run underneath it.
                        believerRule?.banner?.invoke(nightCtx).orEmpty(),
                    ),
                    briefing,
                ),
                prompt = nightRule?.prompt.orEmpty()
                    .ifEmpty { believerRule?.prompt.orEmpty() }
                    .ifEmpty { NightGuide.forStep(role.abilityId, style)?.instructions.orEmpty() },
                action = action,
                badges = badges,
                cards = nightRule?.cards?.invoke(nightCtx).orEmpty() + infoCards(ctx, role, nightRule),
                promptId = promptId,
                wakeCounts = nightRule?.wakeCounts ?: WakeCount.ACT,
            )
        }

        // ---- the illusion: `ActingRole.alwaysFalse` (lead D70) -------------

        /**
         * The believed ability with every consequence removed.
         *
         * A Lunatic runs the BELIEVED Demon's registry row, a Drunk or a
         * Marionette the row of whatever token they were handed. The storyteller
         * must still run the real prompt — the picker shape (how many targets,
         * which are legal, how they sort) is the whole illusion — but nothing it
         * declares may CHANGE the game: no attack, no resurrection, no alignment
         * change, no character change or swap, no granted ability, no queued
         * prompt, no announcement, no counter and no once-per-game spend.
         *
         * Markers are the one exception, and only for a believer whose own row
         * declares no [CharacterRule.illusionToken]:
         *
         *  - a Drunk-as-Monk still places `monk/Safe`, because a Spy reading the
         *    grimoire must see an ordinary Monk. The token protects nobody —
         *    `Status.roleWorks` returns false for an `alwaysFalse` role, so every
         *    effect it sourced is dead on arrival — and that inertness is the
         *    status model's job, not the planner's;
         *  - a Lunatic declares `lunatic/Chosen`, so the believed Demon's own
         *    markers go too and the Lunatic's three official ones are placed
         *    instead. A "Dead" token from a kill that never happened would be a
         *    lie the grimoire tells its own storyteller.
         *
         * Stripping the ACTION rather than filtering at apply time is what makes
         * this visible: the screen renders a picker that carries no kill, and a
         * test can read the shape it will run.
         *
         * [ShowInfo] is left alone deliberately. It carries no effects, and the
         * truthful answer behind it is what the storyteller needs in order to lie
         * — a drunk Empath's row must still compute the real count.
         */
        private fun illusory(action: NightAction?, keepMarkers: Boolean): NightAction? =
            when (action) {
                null -> null
                is ChoosePlayers -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    // "Chose nobody" is always a legal answer for a believer: the
                    // real Demon has to be told when the fake attack never came.
                    allowNone = true,
                    perTarget = inert(action.perTarget, keepMarkers),
                    onResolve = inert(action.onResolve, keepMarkers),
                    onNone = inert(action.onNone, keepMarkers),
                )

                is ChooseCharacter -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    allowNone = true,
                    onResolve = inert(action.onResolve, keepMarkers),
                    onNone = inert(action.onNone, keepMarkers),
                )

                is ChoosePlayerAndCharacter -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    onResolve = inert(action.onResolve, keepMarkers),
                    onNone = inert(action.onNone, keepMarkers),
                )

                is ChoosePlayersAndCharacters -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    allowNone = true,
                    perPair = inert(action.perPair, keepMarkers),
                    onResolve = inert(action.onResolve, keepMarkers),
                    onNone = inert(action.onNone, keepMarkers),
                )

                is YesNo -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    onYes = inert(action.onYes, keepMarkers),
                    onNo = inert(action.onNo, keepMarkers),
                )

                is Options -> action.copy(
                    prompt = illusionPrompt(action.prompt),
                    options = action.options.map { it.copy(effects = inert(it.effects, keepMarkers)) },
                    onNone = inert(action.onNone, keepMarkers),
                )

                // Every stage of the Al-Hadikhia's three-pick sequence, one at a time.
                is Sequence ->
                    action.copy(stages = action.stages.mapNotNull { illusory(it, keepMarkers) })

                is ShowInfo -> action
            }

        /**
         * [effects] with everything that changes the game removed. See [illusory]
         * for why the markers may survive.
         */
        private fun inert(effects: List<NightEffect>, keepMarkers: Boolean): List<NightEffect> {
            if (!keepMarkers) return emptyList()
            return effects.mapNotNull { effect ->
                when (effect) {
                    is NightEffect.PlaceToken, is NightEffect.RemoveToken -> effect
                    is NightEffect.When -> effect
                        .copy(
                            then = inert(effect.then, true),
                            otherwise = inert(effect.otherwise, true),
                        )
                        .takeIf { it.then.isNotEmpty() || it.otherwise.isNotEmpty() }

                    else -> null
                }
            }
        }

        private fun illusionPrompt(prompt: String): String =
            if (prompt.isEmpty()) prompt else "$prompt (nothing happens)"

        /**
         * The mark a believer's fake choices leave INSTEAD of the believed
         * ability's, declared by the believer's own registry row
         * (`CharacterRule.illusionToken`).
         *
         * The Lunatic owns three official `Chosen` tokens and the grimoire is
         * meant to show them; the Drunk and the Marionette declare none, so they
         * keep drawing the ability they think they have. No character id appears
         * here — the registry row is the whole statement.
         */
        private fun illusionMarker(holder: Player?): NightEffect.PlaceToken? {
            val own = holder?.characterId?.let(Character::normalizeId) ?: return null
            val token = CharacterRules.all[own]?.illusionToken ?: return null
            return NightEffect.PlaceToken(token.sourceId, token.label, Ref.AllTargets)
        }

        /**
         * The jinxed character ids whose rule bites tonight, sorted.
         *
         * A jinx applies because the other character is on the SCRIPT, not
         * because it was dealt (lead D19, the Djinn rule), and on the nights it
         * declares: other nights by default, night 1 as well when
         * `JinxRule.firstNight` says so (W7b). Every official jinx is an "each
         * night*" ability, so the flag is off everywhere in the shipped
         * registry — the slot exists for homebrew and for the next official set.
         */
        internal fun jinxesOn(
            rule: CharacterRule,
            scriptIds: Set<String>,
            firstNightRules: Boolean,
        ): List<String> = rule.jinxRules
            .filterKeys { Character.normalizeId(it) in scriptIds }
            .filterValues { !firstNightRules || it.firstNight }
            .keys
            .sorted()

        /**
         * Applies every `CharacterRule.jinxRules` entry whose character is on the
         * script (lead D19) over tonight's rule.
         *
         * A jinx row OVERRIDES only what it declares: the King's Leviathan jinx
         * sets nothing but a gate, and must keep the King's own prompt. Two rows
         * can apply at once (a Leviathan script with both a Farmer and a Sage),
         * so the prompts are joined rather than one silently winning.
         */
        private fun jinxOver(
            base: NightRule?,
            rule: CharacterRule,
            jinxed: List<String>,
        ): NightRule? {
            if (jinxed.isEmpty()) return base
            val rules = jinxed.mapNotNull { rule.jinxRules[it]?.rule }
            if (rules.isEmpty()) return base
            return NightRule(
                // Every jinx row declares its own gate; the strictest wins.
                gate = Gates.all(*rules.map { it.gate }.toTypedArray()),
                action = { ctx ->
                    rules.firstNotNullOfOrNull { it.action(ctx) } ?: base?.action?.invoke(ctx)
                },
                pending = { ctx ->
                    base?.pending?.invoke(ctx).orEmpty() + rules.flatMap { it.pending(ctx) }
                },
                prompt = rules.map { it.prompt }.filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .ifEmpty { base?.prompt.orEmpty() },
                banner = { ctx ->
                    rules.map { it.banner(ctx) }.firstOrNull { it.isNotEmpty() }
                        ?: base?.banner?.invoke(ctx).orEmpty()
                },
                detail = { ctx ->
                    rules.map { it.detail(ctx) }.filter { it.isNotEmpty() }.joinToString(" ")
                        .ifEmpty { base?.detail?.invoke(ctx).orEmpty() }
                },
                cards = { ctx ->
                    rules.flatMap { it.cards(ctx) }.ifEmpty { base?.cards?.invoke(ctx).orEmpty() }
                },
                infoId = rules.firstNotNullOfOrNull { it.infoId } ?: base?.infoId,
                wakeCounts = rules.first().wakeCounts,
            )
        }

        /** The character's own night reminder, plus whatever evidence the row quotes. */
        private fun withEvidence(detail: String, evidence: String): String = when {
            evidence.isEmpty() -> detail
            detail.isEmpty() -> evidence
            else -> "$detail $evidence"
        }

        private fun detailFor(character: Character?, firstNight: Boolean): String {
            character ?: return ""
            val reminder =
                if (firstNight) character.firstNightReminder else character.otherNightReminder
            return reminder.ifEmpty { character.ability }
        }

        /** The one derived fact worth ember: why this ability will not work tonight. */
        private fun bannerFor(
            ctx: PlanContext,
            role: ActingRole,
            holder: Player?,
            gate: StepGate,
        ): String {
            if (gate is StepGate.Reduced) return gate.reason
            if (role.alwaysFalse) {
                return "Everything here is an illusion — nothing they choose has any effect."
            }
            holder ?: return ""
            val impairment = Status.impairment(ctx.state, ctx.lookup, holder.id)
            if (impairment.isNotEmpty() && !role.worksWhileImpaired) {
                return "IMPAIRED — " + impairment.joinToString("; ") { it.text } +
                    ". Their ability does not work tonight."
            }
            if (!holder.alive && gate !is StepGate.Skip) {
                return "Dead — this ability fires anyway. Wake them."
            }
            return ""
        }

        /**
         * A supported information step still gets its picker when no rule
         * declares one. `infoId = ""` suppresses it outright (W7H); `null` — the
         * default — falls back to the ability's own id.
         */
        private fun infoAction(abilityId: String, nightRule: NightRule?): NightAction? {
            val infoId = nightRule?.infoId ?: abilityId
            if (!InfoCalc.supports(infoId)) return null
            val needed = InfoCalc.targetsNeeded(infoId)
            return ShowInfo(
                sourceId = infoId,
                prompt = if (needed == 0) "SHOW THEM" else "WHO DID THEY CHOOSE?",
                targetsNeeded = needed,
            )
        }

        /**
         * Pre-filled show cards for the information the grimoire already knows —
         * truthful first, then the lies the engine can generate (friction §10).
         * Only for answers that need no target picking; the rest are built by the
         * screen once the storyteller has picked.
         */
        private fun infoCards(
            ctx: PlanContext,
            role: ActingRole,
            nightRule: NightRule?,
        ): List<CardOffer> {
            val infoId = nightRule?.infoId ?: role.abilityId
            if (!InfoCalc.supports(infoId) || InfoCalc.targetsNeeded(infoId) > 0) return emptyList()
            val result = InfoCalc.compute(ctx.state, ctx.lookup, infoId, role.playerId) ?: return emptyList()
            return cardsFor(ctx.state, result)
        }

        /** Turns a typed answer into show-card offers; lies are labelled as lies. */
        /**
         * Turns a typed answer into show-card offers; lies are labelled as lies.
         *
         * W7G: `Answer.Players` and a multi-character answer produce real cards
         * now — `ShowCardSpec.PointCard` and `MultiTokenCard`. [state] is what
         * resolves the names and the seat numbers the card prints, so the engine
         * decides WHAT to show and the renderer only decides how it looks.
         */
        fun cardsFor(
            state: GameState,
            result: InfoResult,
            nameOf: (String) -> String = { it },
        ): List<CardOffer> = buildList {
            cardFor(state, result.answer, result.cardPrefix)
                ?.let { add(CardOffer("SHOW: ${labelFor(state, result.answer, nameOf)}", it, true)) }
            for (alternative in result.alternatives) {
                val card = cardFor(state, alternative, result.cardPrefix) ?: continue
                add(CardOffer("LIE · SHOW ${labelFor(state, alternative, nameOf)}", card, false))
            }
        }

        private fun labelFor(
            state: GameState,
            answer: Answer,
            nameOf: (String) -> String = { it },
        ): String = when (answer) {
            is Answer.Count -> answer.n.toString()
            is Answer.YesNoAnswer -> if (answer.yes) "YES" else "NO"
            is Answer.Characters -> answer.ids.joinToString { nameOf(it) }.uppercase()
            // The character is what makes a "1 of 2 players" card mean
            // anything: two offers that differ only in their token used to
            // carry the SAME label (playtest B P2 #16).
            is Answer.Players -> {
                val names = answer.ids.mapNotNull { state.player(it)?.name }
                    .joinToString().ifEmpty { "THEM" }
                answer.characterId?.let { "${nameOf(it).uppercase()} — $names" } ?: names
            }
            is Answer.Message -> answer.text.take(24).uppercase()
        }

        private fun cardFor(
            state: GameState,
            answer: Answer,
            prefix: String = "",
        ): ShowCardSpec? = when (answer) {
            is Answer.Count -> ShowCardSpec.NumberCard(answer.n)
            is Answer.YesNoAnswer -> ShowCardSpec.Message(if (answer.yes) "YES" else "NO")
            is Answer.Characters -> when {
                answer.ids.isEmpty() -> null
                answer.ids.size == 1 ->
                    ShowCardSpec.CharacterCard(prefix.ifBlank { "THIS CHARACTER" }, answer.ids.first())
                else -> ShowCardSpec.MultiTokenCard(
                    prefix.ifBlank { "THESE CHARACTERS" },
                    answer.ids,
                )
            }

            is Answer.Players -> pointCard(state, answer)
            is Answer.Message -> ShowCardSpec.Message(answer.text)
        }

        /** "Point at these players", with the seat numbers the player checks. */
        private fun pointCard(state: GameState, answer: Answer.Players): ShowCardSpec? {
            if (answer.ids.isEmpty()) return null
            val seats = answer.ids.mapNotNull { id ->
                val index = state.seats.indexOfFirst { it.id == id }
                if (index < 0) null else (index + 1) to state.player(id)
            }
            if (seats.isEmpty()) return null
            return ShowCardSpec.PointCard(
                prefix = ShowCardSpec.pointPrefix(answer.characterId != null, seats.size),
                playerNames = seats.mapNotNull { it.second?.name },
                seatNumbers = seats.map { it.first },
                characterId = answer.characterId,
            )
        }

        /**
         * A character that is in play with a night-order slot but no seat of its
         * own: an in-play Fabled, and a `groupStep` character the bag never
         * seated (`Setup.seatlessInPlayIds` — lead D18/D59).
         *
         * The registry row is honoured in full — gate, action, prompt, cards,
         * `infoId`, `wakeCounts` — exactly as for a seated row. The only
         * difference is that `holder` is null, so a gate like `Gates.aliveHolder`
         * fires by construction and a board-reading gate (the Duchess's "3
         * visitors are marked", the Toymaker's "the attack could end the game")
         * decides instead.
         */
        private fun seatlessStep(
            ctx: PlanContext,
            slot: String,
            at: Double,
            holderIds: List<Long>,
            wakeDefault: WakeCount,
        ): List<NightStep> {
            val character = ctx.lookup(slot) ?: return emptyList()
            val rule = CharacterRules.of(slot, character)
            val nightRule = rule.nightRule(ctx.isFirstNight)
            val nightCtx = ctx.nightContext(role = null, holder = null, firstNightRules = ctx.isFirstNight)
            val gate = nightRule?.gate?.gate(ctx.wakeContext(null, null))
                ?: StepGate.Skip("no ability on this night")
            val action = nightRule?.action?.invoke(nightCtx)
                ?: infoAction(slot, nightRule)
            return listOf(
                NightStep(
                    key = StepKey(slot),
                    slotId = slot,
                    order = at,
                    title = character.name,
                    detail = withEvidence(
                        detailFor(character, ctx.isFirstNight),
                        nightRule?.detail?.invoke(nightCtx).orEmpty(),
                    ),
                    holderIds = holderIds,
                    style = ctx.style,
                    gate = gate,
                    banner = (gate as? StepGate.Reduced)?.reason.orEmpty()
                        .ifEmpty { nightRule?.banner?.invoke(nightCtx).orEmpty() },
                    prompt = nightRule?.prompt.orEmpty()
                        .ifEmpty { NightGuide.forStep(slot, ctx.style)?.instructions.orEmpty() },
                    action = action,
                    cards = nightRule?.cards?.invoke(nightCtx).orEmpty() +
                        seatlessInfoCards(ctx, nightRule),
                    wakeCounts = nightRule?.wakeCounts ?: wakeDefault,
                ),
            )
        }

        /** Pre-filled cards for a seatless information step (the Duchess). */
        private fun seatlessInfoCards(ctx: PlanContext, nightRule: NightRule?): List<CardOffer> {
            val infoId = nightRule?.infoId.orEmpty()
            if (infoId.isEmpty() || !InfoCalc.supports(infoId)) return emptyList()
            if (InfoCalc.targetsNeeded(infoId) > 0) return emptyList()
            val result = InfoCalc.compute(ctx.state, ctx.lookup, infoId, null) ?: return emptyList()
            return cardsFor(ctx.state, result)
        }

        /** A bluff set the holder of this row receives themselves (Lunatic, Summoner). */
        private fun withBluffs(
            ctx: PlanContext,
            step: NightStep,
            owed: List<BluffRequirement>,
        ): NightStep {
            if (owed.isEmpty()) return step
            val cards = owed.mapNotNull { requirement ->
                val chosen = ctx.state.bluffSets[requirement.key].orEmpty()
                if (chosen.isEmpty()) {
                    null
                } else {
                    CardOffer("SHOW: BLUFFS", ShowCardSpec.BluffsCard(chosen), truthful = true)
                }
            }
            val missing = owed.filter { ctx.state.bluffSets[it.key].orEmpty().size < it.size }
            return step.copy(
                // On the card, next to the row's own words — not in a `detail`
                // that a marker row does not print any more.
                prompt = step.prompt + owed.joinToString(" ", prefix = " ") {
                    "Also hand out: ${it.label} — ${it.reason}"
                },
                cards = step.cards + cards,
                badges = step.badges + missing.map { "no bluffs picked: ${it.label}" },
            )
        }

        /**
         * One row per bluff set owed to somebody OTHER than this slot's holder —
         * the Snitch's per-Minion hand-out is three independent sets and therefore
         * three rows (lead D38).
         */
        private fun bluffSteps(
            ctx: PlanContext,
            owed: List<BluffRequirement>,
            at: Double,
        ): List<NightStep> {
            if (!ctx.isFirstNight || owed.isEmpty()) return emptyList()
            return owed.mapIndexed { n, requirement ->
                val chosen = ctx.state.bluffSets[requirement.key].orEmpty()
                NightStep(
                    key = StepKey(NightMarkers.MINION_BLUFFS, requirement.recipientId),
                    slotId = NightMarkers.MINION_BLUFFS,
                    order = at + BLUFF_OFFSET + n,
                    title = requirement.label,
                    detail = requirement.reason,
                    sourceId = requirement.sourceId,
                    holderIds = listOfNotNull(requirement.recipientId),
                    style = ctx.style,
                    gate = if (chosen.size >= requirement.size || !requirement.required) {
                        StepGate.Fire
                    } else {
                        StepGate.Conditional(
                            question = "No bluffs picked yet for this set.",
                            yesLabel = "Pick them now",
                            noLabel = "Improvise",
                        )
                    },
                    prompt = "Show ${requirement.size} not-in-play good characters.",
                    cards = if (chosen.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(CardOffer("SHOW: BLUFFS", ShowCardSpec.BluffsCard(chosen), true))
                    },
                    wakeCounts = WakeCount.INFORMED,
                )
            }
        }

        /**
         * Homebrew characters are not on the canonical order lists; they slot in
         * before dawn, ordered by the night index their script JSON declared.
         */
        private fun homebrewSteps(
            ctx: PlanContext,
            roles: List<ActingRole>,
            order: List<String>,
            placed: List<NightStep>,
        ): List<NightStep> {
            val known = order.toSet()
            val unknown = roles.filter { it.slotId !in known }
            if (unknown.isEmpty()) return emptyList()
            val dawnAt = placed.firstOrNull { it.slotId == NightMarkers.DAWN }?.order
                ?: (order.size * BASE_SPACING)
            return unknown
                .mapNotNull { role -> ctx.lookup(role.abilityId)?.let { role to it } }
                .filter { (_, c) ->
                    val reminder =
                        if (ctx.isFirstNight) c.firstNightReminder else c.otherNightReminder
                    val index = if (ctx.isFirstNight) c.firstNight else c.otherNight
                    reminder.isNotBlank() || index > 0
                }
                .sortedBy { (_, c) -> if (ctx.isFirstNight) c.firstNight else c.otherNight }
                .mapIndexed { n, (role, _) ->
                    roleStep(ctx, role, role.slotId, dawnAt - BLUFF_OFFSET + n)
                        .let { it.copy(title = it.title + " (homebrew)") }
                }
        }

        // ---- derived insertions --------------------------------------------

        /**
         * Rows that exist because of something that happened tonight: a `Prompt`
         * due TONIGHT (a resurrection's first-night re-run), and a character
         * created tonight whose own slot is not on tonight's list at all.
         *
         * Anything landing before the cursor is re-stamped after it and badged
         * "out of order" — which the Abilities page explicitly licenses.
         */
        private fun insertions(ctx: PlanContext, base: List<NightStep>): List<NightStep> {
            val out = mutableListOf<NightStep>()
            val taken = base.map { it.key.token }.toMutableSet()
            // A group step is ONE row for every holder, and its key names only the
            // first of them. Without this, converting three Minions to the Riot on
            // night 3 produced the group row plus one duplicate per convert.
            val grouped = base
                .filter { CharacterRules.of(it.abilityId, ctx.lookup(it.abilityId)).groupStep }
                .map { it.abilityId }
                .toSet()
            for (prompt in Prompts.forTonight(ctx.state)) {
                val step = promptStep(ctx, prompt, taken) ?: continue
                if (step.abilityId in grouped) continue
                out += step
                taken += step.key.token
            }
            for (record in ctx.changedTonight) {
                val holder = ctx.state.player(record.playerId) ?: continue
                if (record.reason == ChangeReason.FARMER) continue // no first-night info
                for (role in Identity.actingRoles(ctx.state, ctx.lookup, holder)) {
                    if (role.abilityId in grouped) continue
                    val step = createdStep(ctx, role, record, taken) ?: continue
                    out += step
                    taken += step.key.token
                }
            }
            for (step in actsTwiceSteps(ctx, base, taken)) {
                out += step
                taken += step.key.token
            }
            return out
        }

        /**
         * A seat whose ability works TWICE tonight gets a second row (W7I).
         *
         * `StepVariant.AGAIN` existed from WP2 and nothing emitted it but a
         * `Prompt`. The trigger is a live `EffectKind.ACTS_TWICE` — the Barista's
         * token — so the planner never names a character; the second row sits
         * immediately after the first rather than at the end of the sheet,
         * because it is the same wake happening twice.
         */
        private fun actsTwiceSteps(
            ctx: PlanContext,
            base: List<NightStep>,
            taken: Set<String>,
        ): List<NightStep> {
            val doubled = ctx.state.seats
                .filter {
                    Status.live(ctx.state, ctx.lookup, it.id, EffectKind.ACTS_TWICE).isNotEmpty()
                }
                .map { it.id }
                .toSet()
            if (doubled.isEmpty()) return emptyList()
            return base.mapNotNull { row ->
                if (row.holderId !in doubled) return@mapNotNull null
                if (row.key.variant != StepVariant.NORMAL) return@mapNotNull null
                // Nothing to run twice: a skipped row, or a marker with no action.
                if (row.gate is StepGate.Skip || row.action == null) return@mapNotNull null
                val key = row.key.copy(variant = StepVariant.AGAIN)
                if (key.token in taken) return@mapNotNull null
                row.copy(
                    key = key,
                    order = row.order + 0.5,
                    badges = row.badges + "acts twice — this is the second run",
                )
            }
        }

        /**
         * The insert-after-cursor rule (§2.10 step 5): a row that became true
         * AFTER its own slot went by is re-stamped just after the cursor and
         * badged — which is exactly what the Abilities page licenses. A mid-night
         * Scarlet Woman promotion is the canonical case: the Demon's slot has
         * already passed when the promotion happens.
         */
        private fun restamp(
            ctx: PlanContext,
            base: List<NightStep>,
            steps: List<NightStep>,
        ): List<NightStep> {
            val changed = ctx.changedTonight.map { it.playerId }.toSet()
            if (changed.isEmpty() && steps.none { it.key.variant != StepVariant.NORMAL }) {
                return steps
            }
            val done = ctx.state.nightStepsDone
            fun dynamic(step: NightStep): Boolean = step.key.variant != StepVariant.NORMAL ||
                step.promptId != null ||
                step.holderId in changed
            // The cursor is where the STORYTELLER is, so a row that only exists
            // because of tonight's events never counts as the cursor itself.
            // With nothing left to run, "after the cursor" means after the sheet.
            val cursorAt = base
                .firstOrNull { it.required && it.key.token !in done && !dynamic(it) }
                ?.order
                ?: ((base.lastOrNull()?.order ?: 0.0) + 1)
            var n = 0
            return steps.map { step ->
                if (!dynamic(step) || step.order >= cursorAt || step.key.token in done) {
                    step
                } else {
                    step.copy(
                        order = cursorAt + 0.5 + (n++) * 0.01,
                        badges = step.badges + OUT_OF_ORDER,
                    )
                }
            }
        }

        /** Badge on a row the night order has already walked past. */
        const val OUT_OF_ORDER = "out of order — this became true after their slot"

        /** A `Prompt(at = TONIGHT)` becomes a step at its `stepSlotId` (§2.10 step 4). */
        private fun promptStep(
            ctx: PlanContext,
            prompt: Prompt,
            taken: Set<String>,
        ): NightStep? {
            val holder = prompt.subjectPlayerId?.let { ctx.state.player(it) } ?: return null
            val slot = prompt.stepSlotId.ifEmpty { prompt.sourceId }
                .let(Character::normalizeId)
                .ifEmpty { return null }
            val rerun = prompt.kind == PromptKind.RUN_FIRST_NIGHT
            val variant = if (rerun) StepVariant.FIRST else StepVariant.AGAIN
            val role = Identity.actingRoles(ctx.state, ctx.lookup, holder)
                .firstOrNull { it.abilityId == slot || it.slotId == slot }
                ?: ActingRole(
                    playerId = holder.id,
                    abilityId = slot,
                    slotId = slot,
                    sourceId = null,
                    alwaysFalse = false,
                    worksWhileImpaired = false,
                )
            if (StepKey(role.abilityId, holder.id, variant).token in taken) return null
            // A first-night re-run belongs where that character sits on the FIRST
            // night, scaled into tonight's ordering.
            val at = positionOf(ctx, role.slotId, firstNight = rerun)
            return roleStep(
                ctx = ctx,
                role = role,
                slot = role.slotId,
                at = at,
                variant = variant,
                promptId = prompt.id,
            )
        }

        /** A character created tonight acts tonight, even off its own night list. */
        private fun createdStep(
            ctx: PlanContext,
            role: ActingRole,
            record: IdentityRecord,
            taken: Set<String>,
        ): NightStep? {
            if (StepKey(role.abilityId, role.playerId, StepVariant.NORMAL).token in taken) return null
            val character = ctx.lookup(role.abilityId) ?: return null
            // A promoted Demon does not get first-night information (Summoner,
            // verbatim); everyone else runs the new character's first night.
            val promotion = record.reason in PROMOTIONS
            val firstNightRules = !promotion && character.firstNightReminder.isNotBlank()
            val variant = if (firstNightRules && !ctx.isFirstNight) {
                StepVariant.FIRST
            } else {
                StepVariant.NORMAL
            }
            if (StepKey(role.abilityId, role.playerId, variant).token in taken) return null
            val acts = if (firstNightRules) {
                character.firstNightReminder.isNotBlank() || role.slotId in firstNightOrder
            } else {
                character.otherNightReminder.isNotBlank() || role.slotId in otherNightOrder
            }
            if (!acts) return null
            return roleStep(
                ctx = ctx,
                role = role,
                slot = role.slotId,
                at = positionOf(ctx, role.slotId, firstNight = firstNightRules),
                variant = variant,
                extraBadges = listOf("new character"),
            )
        }

        /** Character changes that make a new Demon rather than a new character. */
        private val PROMOTIONS = setOf(
            ChangeReason.SCARLET_WOMAN,
            ChangeReason.STAR_PASS,
            ChangeReason.STAR_PASS_TOKEN_SWAP,
            ChangeReason.FANG_GU_JUMP,
            ChangeReason.LORD_OF_TYPHON,
        )

        /** Where [slot] sits tonight, scaling a first-night index into tonight's list. */
        private fun positionOf(ctx: PlanContext, slot: String, firstNight: Boolean): Double {
            val tonight = ctx.order.indexOf(slot)
            if (tonight >= 0) return tonight * BASE_SPACING + 1
            val source = if (firstNight) firstNightOrder else otherNightOrder
            val index = source.indexOf(slot)
            if (index < 0 || source.isEmpty()) return ctx.order.size * BASE_SPACING - 1
            val fraction = index.toDouble() / source.size
            return fraction * ctx.order.size * BASE_SPACING
        }

        // ---- applying one step ---------------------------------------------

        /** The seats and characters one resolution addresses. */
        private class EffectScope(
            val sourceId: Long?,
            val sourceCharacterId: String?,
            val targets: List<Long>,
            val characterIds: List<String>,
            /** What this ability chose on its previous wake — [Ref.PreviousTarget]. */
            val previous: Set<Long> = emptySet(),
            var current: Long? = null,
            /**
             * The character paired with [current] on a per-pair action
             * ([ChoosePlayersAndCharacters]). Null everywhere else, where the
             * step's single answer in [characterIds] is the whole story.
             */
            var currentCharacterId: String? = null,
        ) {
            /** The character an empty payload means: this pair's, else the step's. */
            fun character(): String? =
                currentCharacterId?.ifEmpty { null } ?: characterIds.firstOrNull()
        }

        /**
         * Constraints are checked AT RESOLVE TIME, not only at pick time: a target
         * that has become illegal since the chip was tapped is dropped rather than
         * throwing (§2.1.3 — no engine function throws on UI input).
         */
        private fun resolvedTargets(
            state: GameState,
            lookup: (String) -> Character?,
            step: NightStep,
            action: NightAction?,
            input: NightInput,
        ): List<Long> {
            if (input.none) return emptyList()
            // A per-pair answer carries its seats in `assignments`, an answer-set
            // branch in `ActionOption.targetIds`; everything else reads `playerIds`.
            val picked = when {
                action is ChoosePlayersAndCharacters && input.assignments.isNotEmpty() ->
                    input.assignments.map { it.first }

                action is Options && input.playerIds.isEmpty() ->
                    chosenOption(action, input)?.targetIds.orEmpty()

                else -> input.playerIds
            }
            val constraints = when (action) {
                is ChoosePlayers -> action.constraints
                is ChoosePlayerAndCharacter -> action.playerConstraints
                is ChoosePlayersAndCharacters -> action.playerConstraints
                is ShowInfo -> action.constraints
                else -> emptyList()
            }
            val max = when (action) {
                is ChoosePlayers -> action.max
                is ChoosePlayersAndCharacters -> action.max
                is ShowInfo -> if (action.targetsNeeded > 0) action.targetsNeeded else picked.size
                else -> picked.size
            }
            return picked
                .distinct()
                .filter { allowed(state, lookup, step, constraints, it) }
                .take(max.coerceAtLeast(0))
        }

        /** The branch the storyteller tapped, or null for an unrecognised id. */
        private fun chosenOption(action: Options, input: NightInput): ActionOption? =
            action.options.firstOrNull { it.id == input.optionId }

        private fun allowed(
            state: GameState,
            lookup: (String) -> Character?,
            step: NightStep,
            constraints: List<TargetConstraint>,
            targetId: Long,
        ): Boolean {
            val target = state.player(targetId) ?: return false
            val team = target.characterId?.let(lookup)?.team
            val holderId = step.holderId
            return constraints.all { constraint ->
                when (constraint) {
                    TargetConstraint.ALIVE -> target.alive || state.isTrulyAlive(targetId)
                    TargetConstraint.DEAD -> !target.alive
                    TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED -> true
                    TargetConstraint.NOT_SELF -> targetId != holderId
                    TargetConstraint.NOT_TRAVELLER -> !target.isTraveller
                    TargetConstraint.TOWNSFOLK -> team == Team.TOWNSFOLK
                    TargetConstraint.OUTSIDER -> team == Team.OUTSIDER
                    TargetConstraint.MINION -> team == Team.MINION
                    TargetConstraint.DEMON -> team == Team.DEMON
                    TargetConstraint.NOT_DEMON -> team != Team.DEMON
                    TargetConstraint.GOOD -> !target.isEvil(lookup)
                    TargetConstraint.EVIL -> target.isEvil(lookup)
                    TargetConstraint.DIFFERENT_FROM_LAST_NIGHT ->
                        targetId !in Memory.forbiddenTargets(state, step.abilityId, holderId)
                    TargetConstraint.NOT_CHOSEN_BEFORE ->
                        targetId !in Memory.everChosen(state, step.abilityId, holderId)
                    TargetConstraint.NEIGHBOUR_OF_SOURCE ->
                        holderId == null || targetId in state.seatNeighbours(holderId).map { it.id }
                    // A Zombuul's first death REGISTERS dead while the seat is
                    // still in the game, so this is stricter than ALIVE.
                    TargetConstraint.NOT_REGISTERS_DEAD -> target.alive
                    TargetConstraint.DIFFERENT_TYPE_FROM_LAST_NIGHT -> {
                        val last = Memory.lastChoice(state, step.abilityId, holderId)
                            ?.targetIds
                            ?.mapNotNull { state.player(it)?.characterId?.let(lookup)?.team }
                            ?.toSet()
                            .orEmpty()
                        team == null || team !in last
                    }
                }
            }
        }

        private fun applyAction(
            state: GameState,
            lookup: (String) -> Character?,
            action: NightAction,
            input: NightInput,
            targets: List<Long>,
            scope: EffectScope,
        ): GameState {
            var next = state
            when (action) {
                is ChoosePlayers -> {
                    if (input.none || targets.isEmpty()) {
                        next = applyEffects(next, lookup, action.onNone, scope)
                    } else {
                        // ONE TARGET AT A TIME: impairment, positional poison and
                        // protections are re-derived between each (§2.11 step 2).
                        for (target in targets) {
                            scope.current = target
                            next = applyEffects(next, lookup, action.perTarget, scope)
                            next = Effects.reconcile(next, lookup)
                        }
                        scope.current = targets.lastOrNull()
                        next = applyEffects(next, lookup, action.onResolve, scope)
                    }
                }

                // A head-shake is a real answer: it runs `onNone` and never
                // `onResolve`, which would otherwise fire with nothing picked.
                is ChooseCharacter -> {
                    scope.current = targets.firstOrNull()
                    val none = input.none || input.characterIds.isEmpty()
                    next = applyEffects(
                        next,
                        lookup,
                        if (none) action.onNone else action.onResolve,
                        scope,
                    )
                }

                is ChoosePlayerAndCharacter -> {
                    scope.current = targets.firstOrNull()
                    val none = input.none || input.characterIds.isEmpty() || targets.isEmpty()
                    next = applyEffects(
                        next,
                        lookup,
                        if (none) action.onNone else action.onResolve,
                        scope,
                    )
                }

                // N pairs at once — the Engineer's rebuild. Each pair carries its
                // OWN character, so `scope.currentCharacterId` moves with the seat
                // and an empty payload can never take the first pick's character.
                is ChoosePlayersAndCharacters -> {
                    val pairs = input.assignments
                        .filter { it.first in targets && it.second.isNotBlank() }
                        .distinctBy { it.first }
                    if (input.none || pairs.isEmpty()) {
                        next = applyEffects(next, lookup, action.onNone, scope)
                    } else {
                        for ((seat, characterId) in pairs) {
                            scope.current = seat
                            scope.currentCharacterId = characterId
                            next = applyEffects(next, lookup, action.perPair, scope)
                            next = Effects.reconcile(next, lookup)
                        }
                        next = applyEffects(next, lookup, action.onResolve, scope)
                    }
                    scope.currentCharacterId = null
                }

                is YesNo -> {
                    scope.current = targets.firstOrNull()
                    next = applyEffects(
                        next,
                        lookup,
                        if (input.yes == true) action.onYes else action.onNo,
                        scope,
                    )
                }

                // Each [Options] stage consumes its OWN answer from
                // `input.optionIds`, in stage order — the Al-Hadikhia's three
                // independent live/die questions in one resolve. A stage with no
                // entry falls back to `input.optionId`, so a Sequence with a
                // single Options stage behaves exactly as before.
                is Sequence -> {
                    var answered = 0
                    for (stage in action.stages) {
                        val stageInput = if (stage is Options) {
                            val id = input.optionIds.getOrNull(answered) ?: input.optionId
                            answered++
                            input.copy(optionId = id)
                        } else {
                            input
                        }
                        next = applyAction(next, lookup, stage, stageInput, targets, scope)
                    }
                }

                is Options -> {
                    scope.current = targets.firstOrNull()
                    // An unrecognised id applies `onNone`, never a branch picked
                    // by position: the storyteller's tap is the only authority.
                    val chosen = chosenOption(action, input)
                    next = applyEffects(
                        next,
                        lookup,
                        chosen?.effects ?: action.onNone,
                        scope,
                    )
                }

                is ShowInfo -> Unit
                else -> Unit
            }
            return next
        }

        private fun applyEffects(
            state: GameState,
            lookup: (String) -> Character?,
            effects: List<NightEffect>,
            scope: EffectScope,
        ): GameState {
            var next = state
            for (effect in effects) next = applyEffect(next, lookup, effect, scope)
            return next
        }

        private fun applyEffect(
            state: GameState,
            lookup: (String) -> Character?,
            effect: NightEffect,
            scope: EffectScope,
        ): GameState {
            var next = state
            when (effect) {
                is NightEffect.PlaceToken -> {
                    val rule = Tokens.rule(effect.sourceId, effect.label)
                    val kind = effect.kind
                        ?: rule?.effect
                        ?: if (rule?.impairs == true) EffectKind.POISONED else EffectKind.MARKER
                    // An empty payload falls back to the character picked on this
                    // step, so a Cerenovus's Mad token names what it is mad about.
                    val payload = effect.characterId?.ifEmpty { scope.character() }
                    val linked = effect.linkedPlayerId
                        ?.let { seats(next, lookup, it, scope).firstOrNull() }
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = Effects.place(
                            state = next,
                            target = target,
                            kind = kind,
                            sourceCharacterId = effect.sourceId,
                            sourcePlayerId = scope.sourceId,
                            until = rule?.until ?: effect.until,
                            label = effect.label,
                            note = effect.note,
                            characterId = payload,
                            linkedPlayerId = linked,
                            endsWithSource = effect.endsWithSource
                                ?: rule?.endsWithSource
                                ?: true,
                            suppression = effect.suppression,
                        ).state
                    }
                }

                is NightEffect.RemoveToken -> {
                    val key = Tokens.key(effect.sourceId, effect.label)
                    for (target in seats(next, lookup, effect.from, scope)) {
                        next = next.copy(
                            effects = next.effects.filterNot {
                                it.targetId == target && Tokens.key(it.sourceCharacterId, it.label) == key
                            },
                        ).updatePlayer(target) { seat ->
                            seat.copy(reminders = seat.reminders.filterNot { Tokens.key(it) == key })
                        }
                    }
                }

                is NightEffect.Attack -> {
                    // A deferred death resolves an attack made on an EARLIER
                    // night, and whether tonight's suppression reaches it depends
                    // on WHICH suppression it is (lead D68):
                    //
                    //  - SILENCED (Exorcist): "the Pukka does not wake to attack
                    //    tonight, but a player still dies because of the Pukka's
                    //    attack during the previous night" — the source seat is
                    //    dropped from the cause so the funnel does not veto its
                    //    own past attack. Attribution stays on the character.
                    //  - NO_KILL_TONIGHT (Lycanthrope Faux Paw, Princess,
                    //    Toymaker's final night): "the Demon doesn't kill
                    //    tonight" — the seat is KEPT, so `Deaths` blocks it.
                    val suppressions = scope.sourceId
                        ?.let { Status.live(next, lookup, it, EffectKind.DEMON_CANNOT_KILL) }
                        .orEmpty()
                    val stopsDeferred =
                        suppressions.any { it.suppression == KillSuppression.NO_KILL_TONIGHT }
                    val silencedNow =
                        effect.deferred && suppressions.isNotEmpty() && !stopsDeferred
                    // W7G: `CharacterRule.killCause` is the safety net. Every row
                    // that means something other than a Demon kill should say so
                    // on the effect, but `Attack.cause` defaults to DEMON_KILL, so
                    // a row that forgot would have miscounted every protection.
                    val cause = declaredKillCause(scope.sourceCharacterId, effect.cause)
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = Deaths.attempt(
                            state = next,
                            lookup = lookup,
                            targetId = target,
                            cause = KillCause(
                                cause = cause,
                                sourceCharacterId = scope.sourceCharacterId,
                                sourcePlayerId = if (silencedNow) null else scope.sourceId,
                                ignoresProtection = !effect.respectProtection,
                            ),
                        ).state
                    }
                }

                is NightEffect.Resurrect ->
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = Deaths.resurrect(next, lookup, target)
                    }

                is NightEffect.BecomeCharacter -> {
                    // An empty id means "the character picked on this step". With
                    // no pick to fall back on the effect does NOTHING: passing
                    // null through would clear the seat's character, which is not
                    // a rule any character has.
                    val newId = effect.characterId.ifEmpty { scope.character() }
                    if (!newId.isNullOrBlank()) {
                        for (target in seats(next, lookup, effect.on, scope)) {
                            next = Identity.changeCharacter(
                                state = next,
                                lookup = lookup,
                                playerId = target,
                                newCharacterId = newId,
                                reason = effect.reason,
                                // Null keeps the seat's alignment (lead D67).
                                newEvil = effect.evil,
                            )
                        }
                    }
                }

                is NightEffect.SwapCharacters -> {
                    val a = seats(next, lookup, effect.a, scope).firstOrNull()
                    val b = seats(next, lookup, effect.b, scope).firstOrNull()
                    if (a != null && b != null) next = Identity.swapCharacters(next, lookup, a, b)
                }

                is NightEffect.MarkSpent -> {
                    val holder = scope.sourceId
                    val label = lookup(effect.sourceId)?.spentLabel.orEmpty()
                    if (holder != null) {
                        next = Effects.place(
                            state = next,
                            target = holder,
                            kind = EffectKind.SPENT,
                            sourceCharacterId = effect.sourceId,
                            sourcePlayerId = holder,
                            until = Until.FOREVER,
                            label = label,
                            endsWithSource = false,
                        ).state
                        next = ledger(
                            next,
                            LedgerKind.SPENT,
                            sourceId = effect.sourceId,
                            actorId = holder,
                        )
                    }
                }

                // The whole action is recorded once, by `recordChoice`.
                is NightEffect.RecordChoice -> Unit

                is NightEffect.QueuePrompt -> {
                    val on = effect.on?.let { seats(next, lookup, it, scope).firstOrNull() }
                    next = Prompts.queue(
                        next,
                        Prompt(
                            id = 0,
                            at = effect.at,
                            kind = effect.kind,
                            sourceId = effect.sourceId,
                            subjectPlayerId = on ?: scope.sourceId,
                            title = effect.title,
                            stepSlotId = effect.stepSlotId,
                        ),
                    )
                }

                is NightEffect.Announce ->
                    next = ledger(
                        next,
                        LedgerKind.ANNOUNCE,
                        sourceId = "st",
                        text = effect.text,
                        announcePending = true,
                    )

                is NightEffect.NoteMalfunction ->
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = ledger(
                            next,
                            LedgerKind.MALFUNCTION,
                            sourceId = scope.sourceCharacterId.orEmpty(),
                            actorId = target,
                            text = effect.reason,
                            impaired = true,
                        )
                    }

                is NightEffect.ShowCardTo ->
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = ledger(
                            next,
                            LedgerKind.TOLD,
                            sourceId = scope.sourceCharacterId.orEmpty(),
                            actorId = target,
                            shown = effect.card,
                        )
                        next = ledger(
                            next,
                            LedgerKind.WOKE,
                            sourceId = scope.sourceCharacterId.orEmpty(),
                            actorId = target,
                            genuine = false,
                        )
                    }

                is NightEffect.SetAlignment -> {
                    val side = if (effect.evil) Alignment.EVIL else Alignment.GOOD
                    for (target in seats(next, lookup, effect.on, scope)) {
                        val seat = next.player(target) ?: continue
                        if (seat.isEvil(lookup) == effect.evil && seat.alignment == side) continue
                        next = next
                            .updatePlayer(target) {
                                it.copy(alignment = side, legacyAlignmentFlipped = false)
                            }
                        next = Ledger.ruling(
                            state = next,
                            sourceId = scope.sourceCharacterId.orEmpty(),
                            playerId = target,
                            text = effect.note.ifEmpty {
                                "${seat.name} now plays for ${if (effect.evil) "evil" else "good"}."
                            },
                        )
                    }
                }

                is NightEffect.GrantAbility -> {
                    val abilityId = effect.abilityId.ifEmpty { scope.character() }
                        ?.let(Character::normalizeId)
                        .orEmpty()
                    if (abilityId.isNotEmpty()) {
                        val on = effect.on
                        if (on == null) {
                            val floating = FloatingGrant(
                                abilityId = abilityId,
                                sourceId = effect.sourceId,
                                holder = effect.floatingHolder,
                                worksWhileImpaired = effect.worksWhileImpaired,
                            )
                            if (floating !in next.floatingGrants) {
                                next = next.copy(floatingGrants = next.floatingGrants + floating)
                            }
                        } else {
                            val grant = AbilityGrant(
                                abilityId = abilityId,
                                sourceId = effect.sourceId,
                                mode = effect.mode,
                                slotId = effect.slotId,
                                worksWhileImpaired = effect.worksWhileImpaired,
                                cycle = next.cycle,
                            )
                            for (target in seats(next, lookup, on, scope)) {
                                next = next.updatePlayer(target) { seat ->
                                    // One grant per (ability, source): re-running a
                                    // step must not stack two copies of the same gift.
                                    val kept = seat.grants.filterNot {
                                        Character.normalizeId(it.sourceId) ==
                                            Character.normalizeId(effect.sourceId) &&
                                            Character.normalizeId(it.abilityId) == abilityId
                                    }
                                    seat.copy(grants = kept + grant)
                                }
                            }
                        }
                    }
                }

                is NightEffect.When -> {
                    val matched = seats(next, lookup, effect.on, scope)
                        .partition { holds(next, lookup, effect.predicate, it, scope) }
                    // The branch runs per seat, with `current` pointing at it, so
                    // `Ref.Target` inside a branch means "this seat".
                    val before = scope.current
                    for (seat in matched.first) {
                        scope.current = seat
                        next = applyEffects(next, lookup, effect.then, scope)
                    }
                    for (seat in matched.second) {
                        scope.current = seat
                        next = applyEffects(next, lookup, effect.otherwise, scope)
                    }
                    scope.current = before
                }

                is NightEffect.MarkConsumed -> next = Ledger.resolve(next, effect.ledgerId)

                is NightEffect.SetCounter -> next = Counters.set(next, effect.key, effect.value)
            }
            return next
        }

        /**
         * The cause an attack from [sourceCharacterId] carries.
         *
         * `NightEffect.Attack.cause` defaults to `DEMON_KILL` and every row that
         * means otherwise passes its own; this fills in for one that did not,
         * from the registry's `killCause`. It never overrides an explicit
         * non-default cause, and it never invents one for a Demon.
         */
        private fun declaredKillCause(
            sourceCharacterId: String?,
            declaredOnEffect: DeathCause,
        ): DeathCause {
            if (declaredOnEffect != DeathCause.DEMON_KILL) return declaredOnEffect
            val fromRegistry = sourceCharacterId
                ?.let { CharacterRules.all[Character.normalizeId(it)]?.killCause }
                ?: return declaredOnEffect
            // STORYTELLER is the schema's default, not a declaration.
            return if (fromRegistry == DeathCause.STORYTELLER) declaredOnEffect else fromRegistry
        }

        /** Answers one [SeatPredicate] about one seat, right now. */
        private fun holds(
            state: GameState,
            lookup: (String) -> Character?,
            predicate: SeatPredicate,
            seatId: Long,
            scope: EffectScope,
        ): Boolean {
            val seat = state.player(seatId) ?: return false
            val team = seat.characterId?.let(lookup)?.team
            return when (predicate) {
                SeatPredicate.IS_TOWNSFOLK -> team == Team.TOWNSFOLK
                SeatPredicate.IS_OUTSIDER -> team == Team.OUTSIDER
                SeatPredicate.IS_MINION -> team == Team.MINION
                SeatPredicate.IS_DEMON -> team == Team.DEMON
                SeatPredicate.IS_GOOD -> !seat.isEvil(lookup)
                SeatPredicate.IS_EVIL -> seat.isEvil(lookup)
                SeatPredicate.IS_ALIVE -> seat.alive
                SeatPredicate.IS_DEAD -> !seat.alive
                SeatPredicate.REGISTERS_MINION ->
                    Team.MINION in Registration.registersAs(state, lookup, seat)
                SeatPredicate.REGISTERS_DEMON ->
                    Team.DEMON in Registration.registersAs(state, lookup, seat)
                SeatPredicate.REGISTERS_TOWNSFOLK ->
                    Team.TOWNSFOLK in Registration.registersAs(state, lookup, seat)
                SeatPredicate.REGISTERS_EVIL -> Registration.registersEvil(state, lookup, seat)
                SeatPredicate.HAS_ABILITY -> Status.hasAbility(state, lookup, seatId)
                SeatPredicate.IS_IMPAIRED -> Status.isImpaired(state, lookup, seatId)
                SeatPredicate.WAS_IMPAIRED_TONIGHT -> seatId in state.nightImpaired
                SeatPredicate.IS_SOURCE -> seatId == scope.sourceId
            }
        }

        /** Which seats one [Ref] addresses right now. */
        private fun seats(
            state: GameState,
            lookup: (String) -> Character?,
            ref: Ref,
            scope: EffectScope,
        ): List<Long> = when (ref) {
            Ref.Source -> listOfNotNull(scope.sourceId)
            Ref.Target -> listOfNotNull(scope.current)
            Ref.AllTargets -> scope.targets
            Ref.PreviousTarget -> scope.previous.toList()
            is Ref.TargetN -> listOfNotNull(scope.targets.getOrNull(ref.index))
            is Ref.Seat -> listOf(ref.playerId)
            Ref.TownsfolkNeighbourOfTarget -> {
                val target = scope.current
                if (target == null) {
                    emptyList()
                } else {
                    townsfolkNeighbours(state, lookup, target)
                }
            }
        }

        /** Nearest Townsfolk each way — the No Dashii's two victims. */
        private fun townsfolkNeighbours(
            state: GameState,
            lookup: (String) -> Character?,
            of: Long,
        ): List<Long> {
            val seats = state.seats
            val index = seats.indexOfFirst { it.id == of }
            if (index < 0) return emptyList()
            fun walk(direction: Int): Long? {
                var i = (index + direction + seats.size) % seats.size
                while (i != index) {
                    val seat = seats[i]
                    if (seat.characterId?.let(lookup)?.team == Team.TOWNSFOLK) return seat.id
                    i = (i + direction + seats.size) % seats.size
                }
                return null
            }
            return listOfNotNull(walk(-1), walk(+1)).distinct()
        }

        // ---- ledger -------------------------------------------------------

        private fun recordChoice(
            state: GameState,
            step: NightStep,
            targets: List<Long>,
            input: NightInput,
            characterIds: List<String>,
            impaired: Boolean,
        ): GameState {
            if (step.action == null && targets.isEmpty() && !input.none) return state
            return ledger(
                state,
                LedgerKind.CHOICE,
                sourceId = step.abilityId,
                actorId = step.holderId,
                targetIds = targets,
                characterIds = characterIds,
                // "They chose nobody" is a REAL answer and is recorded as one.
                text = if (input.none) NO_CHOICE else "",
                // An `Options` answer is otherwise invisible: the branch id is
                // the whole answer for a judgement step (the General's side, the
                // Wizard's wish, the Cult Leader's alignment).
                shown = if (input.none) "" else input.optionId,
                impaired = impaired,
                byStoryteller = input.byStoryteller,
            )
        }

        private fun recordWakes(state: GameState, step: NightStep, targets: List<Long>): GameState {
            var next = state
            val ownAbility = step.wakeCounts == WakeCount.ACT && step.gate !is StepGate.Reduced
            if (step.wakeCounts != WakeCount.NONE) {
                for (holder in step.wakes) {
                    next = ledger(
                        next,
                        LedgerKind.WOKE,
                        sourceId = step.abilityId,
                        actorId = holder,
                        genuine = ownAbility,
                    )
                }
            }
            return next
        }

        /**
         * TOLD rows for what this step's sleepers were shown about somebody
         * ELSE's choice — the Lunatic's fake attack, handed to the real Demon
         * (`CharacterRule.informsChoiceTo`).
         *
         * Written when the row is ticked, so the ledger records what the Demon
         * was actually told rather than what the sheet offered to tell them. A
         * dead informer, or one whose own row has not come round yet, produces a
         * line on the sheet but no entry: there is nothing to have been told.
         */
        private fun recordInformed(
            state: GameState,
            lookup: (String) -> Character?,
            step: NightStep,
        ): GameState {
            var next = state
            for (seatId in step.wakes) {
                val seat = next.player(seatId) ?: continue
                for (line in NightInfo.choiceBriefings(next, lookup, seat)) {
                    if (!line.reported) continue
                    next = ledger(
                        next,
                        LedgerKind.TOLD,
                        sourceId = line.sourceId,
                        actorId = seatId,
                        targetIds = line.targetIds,
                        text = line.text,
                    )
                }
            }
            return next
        }

        private fun recordMalfunction(
            state: GameState,
            step: NightStep,
            holderId: Long?,
        ): GameState = ledger(
            state,
            LedgerKind.MALFUNCTION,
            sourceId = step.abilityId,
            actorId = holderId,
            text = "their ability did not work",
            impaired = true,
        )

        /** Retires the obligation this row existed to discharge. */
        private fun discharge(state: GameState, step: NightStep): GameState =
            step.promptId?.let { Prompts.resolve(state, it) } ?: state

        /**
         * One append-only ledger write, routed through `Ledger.record` (WP6),
         * which owns id allocation and the cycle / atNight stamp. Nothing in
         * this file touches `GameState.nextLedgerId`.
         */
        @Suppress("LongParameterList")
        private fun ledger(
            state: GameState,
            kind: LedgerKind,
            sourceId: String,
            actorId: Long? = null,
            targetIds: List<Long> = emptyList(),
            characterIds: List<String> = emptyList(),
            text: String = "",
            shown: String = "",
            impaired: Boolean = false,
            genuine: Boolean = true,
            byStoryteller: Boolean = false,
            announcePending: Boolean = false,
        ): GameState = Ledger.record(
            state,
            LedgerEntry(
                kind = kind,
                sourceId = sourceId,
                actorId = actorId,
                targetIds = targetIds,
                characterIds = characterIds,
                text = text,
                shown = shown,
                impaired = impaired,
                genuine = genuine,
                byStoryteller = byStoryteller,
                announcePending = announcePending,
            ),
        )

        /** `LedgerEntry.text` for a recorded "they chose nobody". */
        const val NO_CHOICE = "chose nobody"
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
    /** The full records of today's deaths, for team-filtered gates (Godfather). */
    val deathsToday: List<DeathEvent> = emptyList(),
)

/** Whether a step fires tonight, and why not. */
fun interface WakePredicate {
    fun gate(ctx: WakeContext): StepGate
}

/**
 * Composable wake predicates; the per-character choice lives in the registry.
 *
 * None of these names a character: a dead holder that still acts is one carrying
 * a live `HAS_ABILITY` effect (Vigormortis, Bone Collector), and a silenced Demon
 * is one carrying `DEMON_CANNOT_KILL` (Exorcist, Princess, Toymaker).
 */
object Gates {

    /** Alive, or dead with an ability that still works. Otherwise skipped. */
    val aliveHolder: WakePredicate = WakePredicate { ctx ->
        val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
        val taken = abilityTakenAway(ctx, holder)
        when {
            // An ability that has been TAKEN AWAY is not an impairment: a
            // preached Minion has nothing left to wake for. Drunkenness and
            // poison are different — those seats still wake and are lied to.
            taken != null -> StepGate.Skip("their ability was taken away by the $taken")
            holder.alive -> StepGate.Fire
            ctx.role != null && Status.roleWorks(ctx.state, ctx.lookup, ctx.role) ->
                StepGate.Fire
            else -> StepGate.Skip("dead — no ability")
        }
    }

    /**
     * The name of the character that removed this seat's ability outright, or
     * null (W7G).
     *
     * Only a FOREIGN `NO_ABILITY` counts. The Drunk, the Marionette and the
     * Lunatic carry one sourced by their own character, and they must still
     * wake for the ability they believe they have — that is the whole point of
     * `ActingRole.alwaysFalse`.
     */
    private fun abilityTakenAway(ctx: WakeContext, holder: Player): String? {
        if (ctx.role?.alwaysFalse == true) return null
        val own = holder.characterId?.let(Character::normalizeId)
        val acting = ctx.role?.abilityId?.let(Character::normalizeId)
        val taken = Status.live(ctx.state, ctx.lookup, holder.id, EffectKind.NO_ABILITY)
            .firstOrNull {
                val source = Character.normalizeId(it.sourceCharacterId)
                source.isNotEmpty() && source != own && source != acting
            }
            ?: return null
        return ctx.lookup(taken.sourceCharacterId)?.name ?: taken.sourceCharacterId
    }

    /** Death is not a reason to skip this one (Ravenkeeper, Sage, Farmer, Barber…). */
    val actsWhileDead: WakePredicate = WakePredicate { StepGate.Fire }

    /** Skipped while the ability is not working at all. Most info steps do NOT use this. */
    val hasAbility: WakePredicate = WakePredicate { ctx ->
        val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
        if (Status.hasAbility(ctx.state, ctx.lookup, holder.id)) {
            StepGate.Fire
        } else {
            StepGate.Skip("their ability is not working")
        }
    }

    /** Reads `Character.spentLabel` (lead D49) — never the "Once per game" text. */
    fun notSpent(): WakePredicate = WakePredicate { ctx ->
        val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
        // A believer has nothing to use up: the planner strips their `MarkSpent`
        // along with every other effect, so a Lunatic who "used" a once-per-game
        // Demon power last night must be offered it again tonight (lead D70).
        if (ctx.role?.alwaysFalse == true) return@WakePredicate StepGate.Fire
        val ability = ctx.role?.abilityId ?: return@WakePredicate StepGate.Fire
        val label = ctx.lookup(ability)?.spentLabel.orEmpty()
        val key = if (label.isEmpty()) null else Tokens.key(ability, label)
        val live = Status.live(ctx.state, ctx.lookup, holder.id)
        val spent = live.any {
            it.kind == EffectKind.SPENT &&
                Character.normalizeId(it.sourceCharacterId) == Character.normalizeId(ability)
        } ||
            (key != null && live.any { Tokens.key(it.sourceCharacterId, it.label) == key }) ||
            (key != null && holder.reminders.any { Tokens.key(it) == key })
        if (spent) StepGate.Skip("spent — this ability is once per game") else StepGate.Fire
    }

    /** Ravenkeeper: only on the night they die. */
    fun diedTonight(): WakePredicate = WakePredicate { ctx ->
        val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
        if (holder.id in ctx.diedTonight) {
            StepGate.Fire
        } else {
            StepGate.Skip("they are alive — this ability only fires on the night they die")
        }
    }

    /**
     * Zombuul(false), Godfather(true, Team.OUTSIDER). When deaths happened today
     * but none of them registered as [team], the storyteller is ASKED rather than
     * overruled — misregistration is theirs to rule on.
     */
    fun someoneDiedToday(expected: Boolean, team: Team? = null): WakePredicate =
        WakePredicate { ctx ->
            val deaths = ctx.deathsToday
            val matching = if (team == null) {
                deaths
            } else {
                deaths.filter { it.teamAtDeath == team }
            }
            when {
                matching.isNotEmpty() == expected -> StepGate.Fire
                !expected -> StepGate.Skip("someone died today")
                deaths.isEmpty() -> StepGate.Skip("nobody died today")
                else -> StepGate.Conditional(
                    question = "Did ${article(team?.displayName ?: "player")} die today?",
                    yesLabel = "Yes — they act tonight",
                    noLabel = "No — skip",
                )
            }
        }

    /** "a player" / "an Outsider" — the gate question read "Did a Outsider…" (P2-14). */
    private fun article(noun: String): String =
        if (noun.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) {
            "an $noun"
        } else {
            "a $noun"
        }

    /** Undertaker: only when the day closed with an execution. */
    fun executedToday(): WakePredicate = WakePredicate { ctx ->
        val record = ctx.executedToday
        if (record != null && record.outcome == ExecutionOutcome.DIED) {
            StepGate.Fire
        } else {
            StepGate.Skip("nobody was executed today")
        }
    }

    /** Summoner 3, Xaan X. */
    fun nightIs(n: Int): WakePredicate = WakePredicate { ctx ->
        if (ctx.night == n) StepGate.Fire else StepGate.Skip("this ability fires on night $n")
    }

    /**
     * The 7+ threshold (ARCHITECTURE §6 Q1). Counted over resident seats, with the
     * storyteller's `teensyville.countTravellers` override (lead D8).
     */
    fun minPlayers(n: Int): WakePredicate = WakePredicate { ctx ->
        val countTravellers =
            Decisions.bool(ctx.state, Decisions.COUNT_TRAVELLERS_FOR_INFO)
        val players = if (countTravellers) ctx.totalSeatCount else ctx.residentCount
        if (players >= n) {
            StepGate.Fire
        } else {
            StepGate.Skip("$players players — this ability needs $n")
        }
    }

    /** [n] OTHER alive players: the Chambermaid needs two. */
    fun minAlive(n: Int): WakePredicate = WakePredicate { ctx ->
        val others = ctx.state.alivePlayers.count { it.id != ctx.holder?.id }
        if (others >= n) {
            StepGate.Fire
        } else {
            StepGate.Skip("fewer than $n other alive players to choose")
        }
    }

    /**
     * A silenced Demon is REDUCED, never skipped: the choice half is suppressed,
     * the deferred half still happens (lead D24 — the Exorcised Pukka's standing
     * victim still dies).
     */
    val notExorcised: WakePredicate = WakePredicate { ctx ->
        val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
        val silenced = Status.live(ctx.state, ctx.lookup, holder.id, EffectKind.DEMON_CANNOT_KILL)
            .firstOrNull() ?: return@WakePredicate StepGate.Fire
        val source = ctx.lookup(silenced.sourceCharacterId)?.name ?: "storyteller"
        StepGate.Reduced(
            reason = "The $source silenced them: no choice tonight. " +
                "Anything they set up on a previous night still happens.",
            allow = setOf(StepGate.PENDING, StepGate.PASSIVE),
        )
    }

    /** Worst-of: a Skip beats a Reduced beats a Conditional beats Fire. */
    fun all(vararg p: WakePredicate): WakePredicate = WakePredicate { ctx ->
        val gates = p.map { it.gate(ctx) }
        gates.firstOrNull { it is StepGate.Skip }
            ?: gates.firstOrNull { it is StepGate.Reduced }
            ?: gates.firstOrNull { it is StepGate.Conditional }
            ?: StepGate.Fire
    }

    /** A question only the storyteller can answer. */
    fun ask(question: String, yes: String = "Yes", no: String = "No"): WakePredicate =
        WakePredicate { StepGate.Conditional(question, yes, no) }

    /** Never runs, and says why. */
    fun never(reason: String): WakePredicate = WakePredicate { StepGate.Skip(reason) }
}
