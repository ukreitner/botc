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
                steps = restamp(ctx, base, base + inserted).sortedBy { it.order },
            )
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
            val step = build(state, lookup).step(key) ?: return toggleDone(state, key.token)
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

            // The deferred half is computed from the state BEFORE the choice lands,
            // and applied after it, so "poison the new target, then the old one
            // dies" is the order the rules ask for (lead D4).
            val pending = nightRule?.pending?.invoke(nightCtx).orEmpty()

            val targets = resolvedTargets(state, lookup, step, action, input)
            val scope = EffectScope(
                sourceId = holderId,
                sourceCharacterId = step.abilityId,
                targets = targets,
                characterIds = input.characterIds,
                previous = Memory.forbiddenTargets(state, step.abilityId, holderId),
            )

            var next = state
            val choiceAllowed = when (val gate = step.gate) {
                is StepGate.Reduced -> StepGate.CHOOSE in gate.allow
                else -> true
            }
            if (choiceAllowed && action != null) {
                next = applyAction(next, lookup, action, input, targets, scope)
            }
            next = applyEffects(next, lookup, pending, scope)

            next = recordChoice(next, step, targets, input, impaired)
            next = recordWakes(next, step, targets)
            if (impaired && step.wakeCounts == WakeCount.ACT && step.required) {
                next = recordMalfunction(next, step, holderId)
            }
            next = discharge(next, step)
            next = toggleDone(next, step.key.token, forceDone = true)
            return Effects.reconcile(next, lookup)
        }

        fun toggleDone(state: GameState, token: String): GameState = toggleDone(state, token, false)

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
            val nightRule = rule.nightRule(firstNightRules)
            val holder = ctx.holder(role)
            val nightCtx = ctx.nightContext(role, holder, firstNightRules)
            val gate = when {
                nightRule == null -> StepGate.Skip("no ability on this night")
                else -> nightRule.gate.gate(ctx.wakeContext(role, holder))
            }
            val action = nightRule?.action?.invoke(nightCtx) ?: infoAction(role.abilityId, nightRule)
            val name = character?.name ?: role.abilityId
            val sourceName = role.sourceId?.let { ctx.lookup(it)?.name ?: it }
            val badges = buildList {
                addAll(extraBadges)
                if (holder != null && holder.id in ctx.diedTonight) add("died tonight")
                if (holder != null && !holder.alive && gate !is StepGate.Skip) add("dead — acts anyway")
                if (variant == StepVariant.FIRST) add("first night, again")
                if (ctx.changedTonight.any { it.playerId == holder?.id }) add("new character")
                if (role.alwaysFalse) add("nothing they do has any effect")
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
                    detailFor(character, firstNightRules),
                    nightRule?.detail?.invoke(nightCtx).orEmpty(),
                ),
                sourceId = role.sourceId,
                holderIds = group,
                style = style,
                gate = gate,
                // The planner's own banner (impaired, silenced, dead-but-acts)
                // always wins: a row must never hide the reason its ability will
                // not work tonight behind the registry's evidence quote.
                banner = bannerFor(ctx, role, holder, gate)
                    .ifEmpty { nightRule?.banner?.invoke(nightCtx).orEmpty() },
                prompt = nightRule?.prompt.orEmpty()
                    .ifEmpty { NightGuide.forStep(role.abilityId, style)?.instructions.orEmpty() },
                action = action,
                badges = badges,
                cards = nightRule?.cards?.invoke(nightCtx).orEmpty() + infoCards(ctx, role, nightRule),
                promptId = promptId,
                wakeCounts = nightRule?.wakeCounts ?: WakeCount.ACT,
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

        /** A supported information step still gets its picker when no rule declares one. */
        private fun infoAction(abilityId: String, nightRule: NightRule?): NightAction? {
            val infoId = nightRule?.infoId.orEmpty().ifEmpty { abilityId }
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
            val infoId = nightRule?.infoId.orEmpty().ifEmpty { role.abilityId }
            if (!InfoCalc.supports(infoId) || InfoCalc.targetsNeeded(infoId) > 0) return emptyList()
            val result = InfoCalc.compute(ctx.state, ctx.lookup, infoId, role.playerId) ?: return emptyList()
            return cardsFor(result)
        }

        /** Turns a typed answer into show-card offers; lies are labelled as lies. */
        fun cardsFor(result: InfoResult): List<CardOffer> = buildList {
            cardFor(result.answer)?.let { add(CardOffer("SHOW: ${labelFor(result.answer)}", it, true)) }
            for (alternative in result.alternatives) {
                val card = cardFor(alternative) ?: continue
                add(CardOffer("LIE · SHOW ${labelFor(alternative)}", card, false))
            }
        }

        private fun labelFor(answer: Answer): String = when (answer) {
            is Answer.Count -> answer.n.toString()
            is Answer.YesNoAnswer -> if (answer.yes) "YES" else "NO"
            is Answer.Characters -> answer.ids.joinToString().uppercase()
            is Answer.Players -> "THEM"
            is Answer.Message -> answer.text.take(24).uppercase()
        }

        private fun cardFor(answer: Answer): ShowCardSpec? = when (answer) {
            is Answer.Count -> ShowCardSpec.NumberCard(answer.n)
            is Answer.YesNoAnswer -> ShowCardSpec.Message(if (answer.yes) "YES" else "NO")
            is Answer.Characters -> answer.ids.firstOrNull()
                ?.let { ShowCardSpec.CharacterCard("THIS CHARACTER", it) }
            // A "point at these players" card is WP8's PointCard; nothing to
            // pre-fill until it exists.
            is Answer.Players -> null
            is Answer.Message -> ShowCardSpec.Message(answer.text)
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
            return cardsFor(result)
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
                detail = step.detail + owed.joinToString(" ", prefix = " ") {
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
            return out
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
        )

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
            val constraints = when (action) {
                is ChoosePlayers -> action.constraints
                is ChoosePlayerAndCharacter -> action.playerConstraints
                is ShowInfo -> action.constraints
                else -> emptyList()
            }
            val max = when (action) {
                is ChoosePlayers -> action.max
                is ShowInfo -> if (action.targetsNeeded > 0) action.targetsNeeded else input.playerIds.size
                else -> input.playerIds.size
            }
            return input.playerIds
                .distinct()
                .filter { allowed(state, lookup, step, constraints, it) }
                .take(max.coerceAtLeast(0))
        }

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

                is YesNo -> {
                    scope.current = targets.firstOrNull()
                    next = applyEffects(
                        next,
                        lookup,
                        if (input.yes == true) action.onYes else action.onNo,
                        scope,
                    )
                }

                is Sequence -> {
                    for (stage in action.stages) {
                        next = applyAction(next, lookup, stage, input, targets, scope)
                    }
                }

                is Options -> {
                    scope.current = targets.firstOrNull()
                    // An unrecognised id applies `onNone`, never a branch picked
                    // by position: the storyteller's tap is the only authority.
                    val chosen = action.options.firstOrNull { it.id == input.optionId }
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
                    val payload = effect.characterId?.ifEmpty { scope.characterIds.firstOrNull() }
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
                    // A deferred death resolves an attack made on an EARLIER night,
                    // so a suppression placed TONIGHT must not veto it (wiki: an
                    // Exorcised Pukka's standing victim still dies). Attribution
                    // stays on the character; only the seat is dropped, and only
                    // when it would otherwise cancel its own past attack.
                    val silencedNow = effect.deferred && scope.sourceId != null &&
                        Status.live(next, lookup, scope.sourceId, EffectKind.DEMON_CANNOT_KILL)
                            .isNotEmpty()
                    for (target in seats(next, lookup, effect.on, scope)) {
                        next = Deaths.attempt(
                            state = next,
                            lookup = lookup,
                            targetId = target,
                            cause = KillCause(
                                cause = effect.cause,
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
                    val newId = effect.characterId.ifEmpty { scope.characterIds.firstOrNull() }
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
                    val abilityId = effect.abilityId.ifEmpty { scope.characterIds.firstOrNull() }
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
            }
            return next
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
                SeatPredicate.REGISTERS_EVIL -> Registration.registersEvil(state, lookup, seat)
                SeatPredicate.HAS_ABILITY -> Status.hasAbility(state, lookup, seatId)
                SeatPredicate.IS_IMPAIRED -> Status.isImpaired(state, lookup, seatId)
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
            impaired: Boolean,
        ): GameState {
            if (step.action == null && targets.isEmpty() && !input.none) return state
            return ledger(
                state,
                LedgerKind.CHOICE,
                sourceId = step.abilityId,
                actorId = step.holderId,
                targetIds = targets,
                characterIds = input.characterIds,
                // "They chose nobody" is a REAL answer and is recorded as one.
                text = if (input.none) NO_CHOICE else "",
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
        when {
            holder.alive -> StepGate.Fire
            ctx.role != null && Status.roleWorks(ctx.state, ctx.lookup, ctx.role) ->
                StepGate.Fire
            else -> StepGate.Skip("dead — no ability")
        }
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
                    question = "Did a ${team?.displayName ?: "player"} die today?",
                    yesLabel = "Yes — they act tonight",
                    noLabel = "No — skip",
                )
            }
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
