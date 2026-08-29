package com.clocktower.engine

import kotlinx.serialization.Serializable

/**
 * The complete cause taxonomy (lead D29). Serialised by name — append only.
 * The first five values are the legacy set and MUST keep their spelling.
 */
@Serializable
enum class DeathCause {
    EXECUTION,

    @Deprecated("Use DEMON_KILL")
    DEMON,

    @Deprecated("Use DEMON_KILL / EVIL_ABILITY / DAY_ABILITY")
    OTHER_NIGHT_DEATH,
    EXILE,
    STORYTELLER,

    // ---- added ----
    /** Any Demon's own ability, including deferred harm (Pukka, No Dashii, Vigormortis). */
    DEMON_KILL,

    /** Assassin, Godfather, Witch, Mezepheles, Harpy, Boomdandy, Fearmonger. */
    EVIL_ABILITY,

    /** Gossip, Lycanthrope, Moonchild, Gambler, Tinker, Harlot, Sage-adjacent. */
    GOOD_ABILITY,

    /** Slayer, Psychopath, Golem, Virgin's collateral, Gangster, Gunslinger, Judge. */
    DAY_ABILITY,

    /** Traveller-only powers where the distinction matters. */
    TRAVELLER_ABILITY,
}

/** The input to the kill funnel. */
@Serializable
data class KillCause(
    val cause: DeathCause,
    val sourceCharacterId: String? = null,
    val sourcePlayerId: Long? = null,
    /** Assassin only: nothing stops it. */
    val ignoresProtection: Boolean = false,
    /**
     * Set on Lil' Monsta / Legion / Riot / Yaggababble / Al-Hadikhia kills, where the
     * wiki does not rule whether Sage / Grandmother / Choirboy fire. The kill panel
     * shows one toggle, defaulting to yes. See ARCHITECTURE §6 Q3.
     */
    val demonKillUncertain: Boolean = false,
    /**
     * This death resolves an attack made on an EARLIER night — the Pukka's
     * standing victim — so tonight's suppression of the source reaches it only
     * if the suppression is "the Demon does not kill tonight" (lead D68).
     * `NightEffect.Attack.deferred` and `DeferredDeath.deferred` carry it.
     */
    val deferred: Boolean = false,
)

/** The complete record of one death. Supersedes `DeathRecord` (kept as a typealias). */
@Serializable
data class DeathEvent(
    val id: Long = 0,
    val playerId: Long,
    /** Cycle number; keeps the legacy field name so old saves decode. */
    val day: Int,
    val atNight: Boolean,
    val cause: DeathCause,
    val killerCharacterId: String = "",
    val killerPlayerId: Long? = null,
    /** Snapshots — later character changes must never rewrite a death. */
    val characterIdAtDeath: String? = null,
    val teamAtDeath: Team? = null,
    val evilAtDeath: Boolean = false,
    val abilityImpairedAtDeath: Boolean? = null,
    /** Restored by `revive`. */
    val ghostVoteUsedBeforeDeath: Boolean = false,
    /** Zombuul's first death: stored dead, but the game is not over (lead D6). */
    val registeredOnly: Boolean = false,
    /** Legacy flag, kept for old saves. New code reads [resurrectedAtCycle]. */
    val resurrected: Boolean = false,
    val resurrectedAtCycle: Int? = null,
)

/** Kept for one wave so existing call sites keep compiling. */
typealias DeathRecord = DeathEvent

/** What the funnel decided. Rendered by KillSheet BEFORE it is applied. */
sealed interface KillOutcome {
    /** Nothing stops it. */
    data class Dies(val reason: String = "") : KillOutcome

    /** Deterministic block. [announce] is the exact line to say out loud. */
    data class Prevented(val by: Effect?, val reason: String, val announce: String) : KillOutcome

    /** The Zombuul's first death: stored dead, registers dead, game continues. */
    data class RegistersDead(val reason: String) : KillOutcome

    /** Mayor bounce, Scapegoat substitution: the death moves. */
    data class Redirect(val to: List<Long>, val reason: String, val mandatory: Boolean) : KillOutcome

    /** A "might" ability — Pacifist, Mayor, Scapegoat, Deviant. The ST decides EVERY time. */
    data class Choice(val question: String, val options: List<KillChoiceOption>) : KillOutcome

    /** The Fool: wraps a Prevented and spends the ability. */
    data class Spends(val inner: KillOutcome, val sourceId: String) : KillOutcome

    /** "A dead player cannot die again." Still counts as the day's execution. */
    data object AlreadyDead : KillOutcome
}

data class KillChoiceOption(val id: String, val label: String, val outcome: KillOutcome)

/** The result of applying the funnel. */
data class DeathAttempt(
    val state: GameState,
    val outcome: KillOutcome,
    /** Null when nobody died. */
    val event: DeathEvent? = null,
    /** Obligations the death created, already queued in [state]. */
    val prompts: List<Prompt> = emptyList(),
)

/**
 * The one kill funnel (lead D24). Every path that ends a life goes through
 * [attempt]; [killOutcome] is the same decision as a pure preview, so the kill
 * sheet renders exactly what the button will do.
 */
object Deaths {

    /** Every night cause — the set the Innkeeper's "can't die tonight" covers. */
    private val NIGHT_CAUSES: Set<DeathCause> = setOf(
        DeathCause.DEMON_KILL, DeathCause.EVIL_ABILITY, DeathCause.GOOD_ABILITY,
        DeathCause.TRAVELLER_ABILITY, DeathCause.STORYTELLER,
        @Suppress("DEPRECATION") DeathCause.DEMON,
        @Suppress("DEPRECATION") DeathCause.OTHER_NIGHT_DEATH,
    )

    /** Causes that can only happen in daylight. */
    private val DAY_CAUSES: Set<DeathCause> =
        setOf(DeathCause.EXECUTION, DeathCause.EXILE, DeathCause.DAY_ABILITY)

    /** A Demon's own ability, including the legacy spelling of the same thing. */
    private val DEMON_CAUSES: Set<DeathCause> =
        setOf(DeathCause.DEMON_KILL, @Suppress("DEPRECATION") DeathCause.DEMON)

    /**
     * Which causes each protective effect blocks (lead D29). The table, not prose.
     *
     * NOTE FOR THE LEAD: D29 summarises "Monk / Soldier / Innkeeper -> DEMON_KILL
     * only", but the wiki and status-model §C are explicit that the Innkeeper's
     * SAFE covers *every* night death ("also safe from death caused by Outsiders,
     * Minions, Townsfolk, and Travellers"), and status-model test 23 requires it to
     * block a Godfather kill. ARCHITECTURE §2.6 step 7 agrees — it gates on
     * `atNight`, not on the cause. That is what is implemented here.
     */
    val PROTECTS: Map<EffectKind, Set<DeathCause>> = mapOf(
        EffectKind.SAFE_FROM_DEMON to DEMON_CAUSES,
        EffectKind.CANT_DIE_TONIGHT to NIGHT_CAUSES,
        EffectKind.CANT_DIE to DeathCause.entries.toSet(),
        EffectKind.ONLY_EXECUTION_KILLS to (DeathCause.entries.toSet() - DeathCause.EXECUTION),
        EffectKind.SURVIVES_EXECUTION to setOf(DeathCause.EXECUTION),
        EffectKind.DAY_IMMUNE to DAY_CAUSES,
        EffectKind.DEATH_TIED_TO to DeathCause.entries.toSet(),
        EffectKind.DEMON_CANNOT_KILL to DEMON_CAUSES,
    )

    // ---- option ids answering a KillOutcome.Choice ----

    /** The target dies after all. */
    const val OPTION_DIES: String = "dies"

    /** The target lives (Pacifist, Deviant). */
    const val OPTION_LIVES: String = "lives"

    /** The Mayor bounce / Scapegoat substitution: someone else dies. */
    const val OPTION_REDIRECT: String = "redirect"

    /**
     * PURE preview of what would happen. Rendered by KillSheet, the night step's
     * consequence line and the execution confirmation sheet. No state change.
     *
     * Implements the precedence table of ARCHITECTURE §2.6 exactly, first match
     * wins. Every protection is only considered when its source still has their
     * ability, which `Status.protections` gives for free.
     */
    fun killOutcome(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
    ): KillOutcome {
        val target = state.player(targetId) ?: return KillOutcome.AlreadyDead
        val name = target.name
        val atNight = state.phase != Phase.DAY
        val isExecution = cause.cause == DeathCause.EXECUTION
        val isDemonKill = cause.cause in DEMON_CAUSES

        // 0. A dead player cannot die again — unless they are a Zombuul who only
        //    registers as dead, in which case a real second death is allowed.
        if (!target.alive && !state.isTrulyAlive(targetId)) return KillOutcome.AlreadyDead

        // 1. The Assassin: "even if for some reason they could not". Nothing else runs.
        if (cause.ignoresProtection) {
            return KillOutcome.Dies("Nothing can prevent this death.")
        }

        val protections = Status.protections(state, lookup, targetId)
        fun protection(kind: EffectKind): Effect? = protections.firstOrNull { it.kind == kind }
        fun blocks(kind: EffectKind): Boolean =
            PROTECTS[kind]?.contains(cause.cause) == true

        // 2. The SOURCE is silenced (Lycanthrope, Princess, Exorcised Demon,
        //    Toymaker) — checked before any target protection so a deferred Pukka
        //    kill obeys it too (lead D36), SCOPED by lead D63/D68:
        //
        //     - an Exorcised (SILENCED) source still lands a death it set up on
        //       an earlier night — "the Pukka does not wake to attack tonight,
        //       but a player still dies because of the Pukka's attack during the
        //       previous night";
        //     - a NO_KILL_TONIGHT source (Lycanthrope's Faux Paw, Princess,
        //       Toymaker's final night) stops even that.
        //
        //    The preview and the resolution used to disagree here: the card said
        //    "Dev dies now", the button said "DEV SURVIVES — NOBODY DIES", and
        //    holding it killed Dev (playtest D2-1).
        if (isDemonKill) {
            val sourceId = cause.sourcePlayerId
            val silenced = sourceId
                ?.let { Status.protections(state, lookup, it) }
                .orEmpty()
                .filter { it.kind == EffectKind.DEMON_CANNOT_KILL }
                .filter { !cause.deferred || it.suppression == KillSuppression.NO_KILL_TONIGHT }
                .firstOrNull()
            if (silenced != null) {
                return KillOutcome.Prevented(
                    by = silenced,
                    reason = "The Demon cannot kill tonight.",
                    announce = "Nobody dies — the Demon could not kill tonight.",
                )
            }
        }

        // 3. Lleech: "You die if & only if they are dead."
        protection(EffectKind.DEATH_TIED_TO)?.let { tie ->
            val host = tie.linkedPlayerId?.let { state.player(it) }
            if (host != null && host.alive) {
                return KillOutcome.Prevented(
                    by = tie,
                    reason = "$name's life is tied to ${host.name}, who is still alive.",
                    announce = prevention(name, isExecution, "the Lleech's host is alive"),
                )
            }
        }

        // 4. Vizier: "You cannot die during the day."
        if (!atNight) {
            protection(EffectKind.DAY_IMMUNE)?.let {
                if (blocks(EffectKind.DAY_IMMUNE)) {
                    return KillOutcome.Prevented(
                        by = it,
                        reason = "$name cannot die during the day.",
                        announce = prevention(name, isExecution, "the Vizier cannot die by day"),
                    )
                }
            }
        }

        // 5. Storm Catcher: "they can only die by execution".
        protection(EffectKind.ONLY_EXECUTION_KILLS)?.let {
            if (blocks(EffectKind.ONLY_EXECUTION_KILLS)) {
                return KillOutcome.Prevented(
                    by = it,
                    reason = "$name can only die by execution.",
                    announce = prevention(name, isExecution, "only an execution can kill them"),
                )
            }
        }

        // 6. Sailor, Tea Lady: they can't die, day or night.
        protection(EffectKind.CANT_DIE)?.let {
            return KillOutcome.Prevented(
                by = it,
                reason = "$name can't die.",
                announce = prevention(name, isExecution, byWhom(it, lookup)),
            )
        }

        // 7. Innkeeper: safe from every death tonight, but not from an execution.
        if (atNight) {
            protection(EffectKind.CANT_DIE_TONIGHT)?.let {
                if (blocks(EffectKind.CANT_DIE_TONIGHT)) {
                    return KillOutcome.Prevented(
                        by = it,
                        reason = "$name can't die tonight.",
                        announce = prevention(name, isExecution, byWhom(it, lookup)),
                    )
                }
            }
        }

        // 8. Monk, Soldier: safe from the Demon's own ability.
        if (isDemonKill) {
            protection(EffectKind.SAFE_FROM_DEMON)?.let {
                return KillOutcome.Prevented(
                    by = it,
                    reason = "$name is safe from the Demon.",
                    announce = prevention(name, isExecution, byWhom(it, lookup)),
                )
            }
        }

        // 9. Devil's Advocate: "if executed tomorrow, they don't die".
        if (isExecution) {
            protection(EffectKind.SURVIVES_EXECUTION)?.let {
                return KillOutcome.Prevented(
                    by = it,
                    reason = "$name survives execution today.",
                    announce = prevention(name, true, byWhom(it, lookup)),
                )
            }
        }

        // 10. Pacifist: "Executed good players MIGHT not die." Always ask.
        if (isExecution && !Registration.registersEvil(state, lookup, target) &&
            holderWithAbility(state, lookup, "pacifist") != null
        ) {
            return KillOutcome.Choice(
                question = "$name is good and was executed. Do they die?",
                options = listOf(
                    KillChoiceOption(OPTION_DIES, "They die", KillOutcome.Dies("The Pacifist did not save them.")),
                    KillChoiceOption(
                        OPTION_LIVES,
                        "They survive — say nothing",
                        KillOutcome.Prevented(
                            by = null,
                            reason = "The Pacifist saved $name.",
                            announce = "Say: '$name was executed… and remains alive.' Do not say why.",
                        ),
                    ),
                ),
            )
        }

        // 11. Deviant: "If you were funny today, you cannot die by exile."
        if (cause.cause == DeathCause.EXILE && target.characterId?.let(Character::normalizeId) == "deviant") {
            return KillOutcome.Choice(
                question = "Was ${name} funny today? A funny Deviant cannot die by exile.",
                options = listOf(
                    KillChoiceOption(OPTION_DIES, "Not funny — they die", KillOutcome.Dies()),
                    KillChoiceOption(
                        OPTION_LIVES,
                        "Funny — they survive",
                        KillOutcome.Prevented(
                            by = null,
                            reason = "The Deviant was funny today.",
                            announce = "$name is exiled but remains alive.",
                        ),
                    ),
                ),
            )
        }

        // 12. Mayor: "If you die at night, another player MIGHT die instead."
        //     After the blocks, so a Monk-protected Mayor gives "nobody dies".
        if (atNight && !isExecution &&
            target.characterId?.let(Character::normalizeId) == "mayor" &&
            Status.hasAbility(state, lookup, targetId)
        ) {
            val others = state.alivePlayers.filter { it.id != targetId }.map { it.id }
            return KillOutcome.Choice(
                question = "The Mayor would die tonight. Who dies instead?",
                options = listOf(
                    KillChoiceOption(OPTION_DIES, "The Mayor dies", KillOutcome.Dies()),
                    KillChoiceOption(
                        OPTION_REDIRECT,
                        "Someone else dies",
                        KillOutcome.Redirect(others, "The Mayor's ability bounced the death.", false),
                    ),
                ),
            )
        }

        // 13. Scapegoat: "If a player of your alignment is executed, you MIGHT be
        //     executed instead."
        if (isExecution) {
            val targetEvil = Registration.registersEvil(state, lookup, target)
            val scapegoat = state.alivePlayers.firstOrNull {
                it.characterId?.let(Character::normalizeId) == "scapegoat" &&
                    it.id != targetId &&
                    Registration.registersEvil(state, lookup, it) == targetEvil &&
                    Status.hasAbility(state, lookup, it.id)
            }
            if (scapegoat != null) {
                return KillOutcome.Choice(
                    question = "${scapegoat.name} is a Scapegoat of $name's alignment. " +
                        "Who is executed?",
                    options = listOf(
                        KillChoiceOption(OPTION_DIES, "$name dies", KillOutcome.Dies()),
                        KillChoiceOption(
                            OPTION_REDIRECT,
                            "${scapegoat.name} dies instead",
                            KillOutcome.Redirect(
                                listOf(scapegoat.id),
                                "The Scapegoat was executed instead.",
                                true,
                            ),
                        ),
                    ),
                )
            }
        }

        // 14. Zombuul: "The 1st time you die, you live but register as dead."
        if (target.characterId?.let(Character::normalizeId) == "zombuul" &&
            state.deaths.none { it.playerId == targetId } &&
            Status.hasAbility(state, lookup, targetId)
        ) {
            return KillOutcome.RegistersDead(
                "Declare that the Zombuul died — but do not shroud them. " +
                    "They register as dead from now on.",
            )
        }

        // 15. Fool: "The 1st time you die, you don't." LAST, so other protections
        //     take precedence and the once-per-game is not consumed.
        if (target.characterId?.let(Character::normalizeId) == "fool" &&
            Status.live(state, lookup, targetId, EffectKind.SPENT).isEmpty() &&
            Status.hasAbility(state, lookup, targetId)
        ) {
            return KillOutcome.Spends(
                inner = KillOutcome.Prevented(
                    by = null,
                    reason = "The Fool's first death does not happen.",
                    announce = "$name doesn't die — the Fool's ability is now spent.",
                ),
                sourceId = "fool",
            )
        }

        // 16. Nothing stops it.
        return KillOutcome.Dies()
    }

    /**
     * THE kill funnel. Every path that ends a life calls this — day execution,
     * dusk guard, seat sheet, night action, on-death chains. Applies the outcome,
     * records the death (or, for a prevented one, a ledger RULING), runs
     * `Effects.reconcile`, and fires every on-death trigger exactly once.
     *
     * [optionId] answers a previous `KillOutcome.Choice`; pass "" the first time.
     * A `Choice` returned with no [optionId] leaves the state untouched — the UI
     * must ask, then call again.
     */
    fun attempt(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
        optionId: String = "",
    ): DeathAttempt {
        val decided = killOutcome(state, lookup, targetId, cause)
        val outcome = if (decided is KillOutcome.Choice && optionId.isNotEmpty()) {
            decided.options.firstOrNull { it.id == optionId }?.outcome ?: decided
        } else {
            decided
        }
        return apply(state, lookup, targetId, cause, outcome)
    }

    private fun apply(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
        outcome: KillOutcome,
    ): DeathAttempt = when (outcome) {
        is KillOutcome.Choice -> DeathAttempt(state, outcome)

        KillOutcome.AlreadyDead -> DeathAttempt(state, outcome)

        is KillOutcome.Prevented -> DeathAttempt(
            state = Effects.reconcile(recordPrevented(state, targetId, cause, outcome), lookup),
            outcome = outcome,
        )

        is KillOutcome.Spends -> {
            val spent = Effects.place(
                state = recordPrevented(state, targetId, cause, outcome.inner as? KillOutcome.Prevented),
                target = targetId,
                kind = EffectKind.SPENT,
                sourceCharacterId = outcome.sourceId,
                sourcePlayerId = targetId,
                until = Until.FOREVER,
                label = spentLabel(outcome.sourceId, lookup),
                note = "Used to survive a death.",
            ).state
            DeathAttempt(Effects.reconcile(spent, lookup), outcome)
        }

        is KillOutcome.RegistersDead -> {
            val (next, event) = record(state, lookup, targetId, cause, registeredOnly = true)
            DeathAttempt(Effects.reconcile(next, lookup), outcome, event)
        }

        is KillOutcome.Redirect -> {
            // `to` is a CANDIDATE list when the storyteller still has to pick: the
            // Mayor's bounce offers every other seat, and killing all of them would
            // end the game. Only a settled redirect — one named seat, or a mandatory
            // substitution like the Scapegoat's — is applied here.
            val settled = outcome.to.singleOrNull()
                ?: outcome.to.takeIf { outcome.mandatory && it.size == 1 }?.single()
            if (settled == null) {
                DeathAttempt(state, outcome)
            } else {
                val r = attempt(state, lookup, settled, cause)
                DeathAttempt(r.state, outcome, r.event, r.prompts)
            }
        }

        is KillOutcome.Dies -> {
            val (recorded, event) = record(state, lookup, targetId, cause, registeredOnly = false)
            val triggered = fireDeathTriggers(recorded, lookup, event)
            DeathAttempt(
                state = Effects.reconcile(triggered.first, lookup),
                outcome = outcome,
                event = event,
                prompts = triggered.second,
            )
        }
    }

    /** Writes the DeathEvent and shrouds the seat. */
    private fun record(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
        registeredOnly: Boolean,
    ): Pair<GameState, DeathEvent> {
        val player = state.player(targetId)!!
        val event = DeathEvent(
            id = state.nextDeathId,
            playerId = targetId,
            day = state.cycle,
            atNight = state.phase != Phase.DAY,
            cause = cause.cause,
            killerCharacterId = cause.sourceCharacterId.orEmpty(),
            killerPlayerId = cause.sourcePlayerId,
            characterIdAtDeath = player.characterId,
            teamAtDeath = player.team(lookup),
            evilAtDeath = player.isEvil(lookup),
            abilityImpairedAtDeath = Status.isImpaired(state, lookup, targetId),
            ghostVoteUsedBeforeDeath = player.ghostVoteUsed,
            registeredOnly = registeredOnly,
        )
        val next = state
            .updatePlayer(targetId) { it.copy(alive = false, ghostVoteUsed = false) }
            .copy(deaths = state.deaths + event, nextDeathId = state.nextDeathId + 1)
        return next to event
    }

    /**
     * A death that did not happen is still a fact the Vortox, the Undertaker, the
     * Mayor and the Leviathan need. It is recorded as a ledger RULING, never as a
     * DeathEvent (lead D24).
     */
    private fun recordPrevented(
        state: GameState,
        targetId: Long,
        cause: KillCause,
        outcome: KillOutcome.Prevented?,
    ): GameState {
        val name = state.player(targetId)?.name ?: return state
        // WP3: routed through Ledger.ruling, which owns id/cycle/atNight stamping.
        return Ledger.ruling(
            state = state,
            sourceId = outcome?.by?.sourceCharacterId ?: cause.sourceCharacterId.orEmpty(),
            playerId = targetId,
            text = outcome?.reason ?: "$name did not die.",
            shown = outcome?.announce.orEmpty(),
        )
    }

    /**
     * Fires `CharacterRule.onDeath` for every seat that holds one, and for every
     * Fabled in play (lead D35).
     *
     * A Fabled has no seat, so its rows are walked with
     * [CharacterRules.GRIMOIRE_HOLDER] — the Angel's responsibility question and
     * the Hindu's reincarnation are declared in `RulesFabled.kt` and were inert
     * until this loop stopped being seats-only.
     *
     */
    private fun fireDeathTriggers(
        state: GameState,
        lookup: (String) -> Character?,
        event: DeathEvent,
    ): Pair<GameState, List<Prompt>> {
        var next = state
        val queued = mutableListOf<Prompt>()
        val rows = state.players.mapNotNull { holder ->
            val id = holder.characterId?.let(Character::normalizeId) ?: return@mapNotNull null
            CharacterRules.all[id]?.let { it to holder }
        } + CharacterRules.fabledRows(state).map { it to CharacterRules.GRIMOIRE_HOLDER }
        for ((rule, holder) in rows) {
            for (trigger in rule.onDeath) {
                if (!trigger.gate(state, lookup, event, holder)) continue
                val result = trigger.produce(state, lookup, event, holder)
                for (p in result.prompts) {
                    next = Prompts.queue(next, p.copy(causeEventId = event.id))
                    queued += next.prompts.last()
                }
                // Stamp ids the same way Prompts.queue does: a registry row writes
                // `Effect(id = 0, ...)` and the funnel owns the numbering. Effect.id
                // is the resolution-order key and the identity used for removal and
                // rollback, so two unstamped effects would be indistinguishable.
                var nextEffectId = next.nextEffectId
                val stamped = result.effects.map {
                    it.copy(id = nextEffectId++, causeEventId = event.id)
                }
                if (stamped.isNotEmpty()) {
                    next = next.copy(
                        effects = next.effects + stamped,
                        nextEffectId = nextEffectId,
                    )
                }
            }
        }
        return next to queued
    }

    /** The official spent-mark label for [characterId], or a safe fallback. */
    private fun spentLabel(characterId: String, lookup: (String) -> Character?): String =
        lookup(characterId)?.spentLabel?.ifEmpty { null }
            ?: Tokens.all.firstOrNull {
                Character.normalizeId(it.sourceId) == Character.normalizeId(characterId) &&
                    it.effect == EffectKind.SPENT
            }?.label
            ?: "No Ability"

    /** The exact line to say out loud when a death is prevented (status-model §7). */
    private fun prevention(name: String, isExecution: Boolean, by: String): String =
        if (isExecution) {
            "Say: '$name was executed… and remains alive.' Do not say why."
        } else {
            "Nobody dies — $by protected $name."
        }

    private fun byWhom(effect: Effect, lookup: (String) -> Character?): String =
        lookup(effect.sourceCharacterId)?.name?.let { "the $it" } ?: "an ability"

    /** The alive holder of [characterId] whose ability is working, if any. */
    private fun holderWithAbility(
        state: GameState,
        lookup: (String) -> Character?,
        characterId: String,
    ): Player? = state.alivePlayers.firstOrNull {
        it.characterId?.let(Character::normalizeId) == characterId &&
            Status.hasAbility(state, lookup, it.id)
    }

    /**
     * Kills a player, recording the cause. Dead players gain a ghost vote.
     *
     * Compatibility shim: it routes through [attempt] with `ignoresProtection`,
     * so the behaviour is the legacy "this player dies, full stop" while the
     * DeathEvent, the effect reconcile and the on-death triggers all come from
     * the one funnel. New call sites must use [attempt] with a real [KillCause].
     */
    @Deprecated(
        "Use Deaths.attempt(state, lookup, targetId, KillCause(...)) — protections are skipped here.",
        ReplaceWith("Deaths.attempt(state, lookup, playerId, KillCause(cause)).state"),
    )
    fun kill(
        state: GameState,
        playerId: Long,
        cause: DeathCause,
        lookup: (String) -> Character? = { null },
    ): GameState {
        val player = state.player(playerId) ?: return state
        if (!player.alive) return state
        return attempt(
            state = state,
            lookup = lookup,
            targetId = playerId,
            cause = KillCause(cause = cause, ignoresProtection = true),
        ).state
    }

    /**
     * In-game resurrection (Professor, Shabaloth regurgitation, Bone
     * Collector...): the player lives again but the death record STAYS in
     * the log, marked resurrected — Undertaker/Cannibal history survives.
     *
     * They regain their ability, INCLUDING a spent once-per-game (Glossary),
     * except the Virgin's first-nomination flag, which is a historical fact
     * (lead D7). Queues RUN_FIRST_NIGHT for tonight and an ANNOUNCE the
     * storyteller still owes the table.
     */
    fun resurrect(
        state: GameState,
        lookup: (String) -> Character? = { null },
        playerId: Long,
    ): GameState {
        val player = state.player(playerId) ?: return state
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId && !it.resurrected }
        var next = state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(
                deaths = state.deaths.mapIndexed { i, d ->
                    if (i == lastDeath) d.copy(resurrected = true, resurrectedAtCycle = state.cycle) else d
                },
            )

        // The ability comes back, spent once-per-game included — but the Virgin's
        // first nomination already happened and stays spent.
        next = next.copy(
            effects = next.effects.filterNot {
                it.targetId == playerId && it.kind == EffectKind.SPENT &&
                    Character.normalizeId(it.sourceCharacterId) != "virgin"
            },
        )
        next = next.updatePlayer(playerId) { p ->
            p.copy(
                reminders = p.reminders.filterNot { r ->
                    val rule = Tokens.rule(r)
                    (rule?.effect == EffectKind.SPENT || rule?.label.equals("Dead", true)) &&
                        Character.normalizeId(r.sourceId) != "virgin"
                },
            )
        }

        next = Prompts.queue(
            next,
            Prompt(
                id = 0,
                at = BriefingSlot.TONIGHT,
                kind = PromptKind.RUN_FIRST_NIGHT,
                sourceId = player.characterId.orEmpty(),
                subjectPlayerId = playerId,
                title = "${player.name} is alive again — run their FIRST-NIGHT step tonight",
                detail = "A resurrected player's \"you start knowing\" ability functions tonight.",
                stepSlotId = player.characterId.orEmpty(),
            ),
        )
        // WP3: routed through Ledger.announce, which owns id/cycle/atNight stamping.
        next = Ledger.announce(
            state = next,
            text = "${player.name} is alive again.",
            actorId = playerId,
            detail = "Do not say why.",
        )
        return Effects.reconcile(next, lookup)
    }

    /**
     * Undo a mistaken death: the most recent death record is DROPPED, as if
     * it never happened, along with every Effect and Prompt it created.
     * For in-game resurrection use [resurrect].
     */
    fun revive(state: GameState, playerId: Long): GameState {
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId }
        val event = state.deaths.getOrNull(lastDeath)
        var next = state.updatePlayer(playerId) {
            it.copy(alive = true, ghostVoteUsed = event?.ghostVoteUsedBeforeDeath ?: false)
        }.copy(deaths = state.deaths.filterIndexed { i, _ -> i != lastDeath })
        if (event != null && event.id != 0L) next = Effects.rollback(next, event.id)
        return next
    }

    fun toggleGhostVote(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(ghostVoteUsed = !it.ghostVoteUsed) }
}
