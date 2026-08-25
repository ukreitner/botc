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
 * The one kill funnel (lead D24). WP0 moved the legacy `kill` / `revive` /
 * `resurrect` / `toggleGhostVote` here verbatim; WP1 implements the rest and
 * routes every caller through [attempt].
 */
object Deaths {

    /**
     * PURE preview of what would happen. Rendered by KillSheet, the night step's
     * consequence line and the execution confirmation sheet. No state change.
     */
    fun killOutcome(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
    ): KillOutcome = TODO("WP1")

    /**
     * THE kill funnel. Every path that ends a life calls this — day execution,
     * dusk guard, seat sheet, night action, on-death chains. Applies the outcome,
     * writes the DeathEvent (even for a prevented death, as a ledger RULING),
     * runs `Effects.reconcile`, and fires every on-death trigger exactly once.
     *
     * [optionId] answers a previous `KillOutcome.Choice`; pass "" the first time.
     */
    fun attempt(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
        optionId: String = "",
    ): DeathAttempt = TODO("WP1")

    /**
     * Kills a player, recording the cause. Dead players gain a ghost vote.
     *
     * WP0: moved verbatim from `GameActions.kill`. WP1 replaces every caller
     * with [attempt] and this becomes private to the funnel.
     */
    fun kill(
        state: GameState,
        playerId: Long,
        cause: DeathCause,
        lookup: (String) -> Character? = { null },
    ): GameState {
        val player = state.player(playerId) ?: return state
        if (!player.alive) return state
        return state
            .updatePlayer(playerId) { it.copy(alive = false, ghostVoteUsed = false) }
            .copy(
                deaths = state.deaths + DeathEvent(
                    playerId = playerId,
                    day = state.cycle,
                    atNight = state.phase == Phase.NIGHT,
                    cause = cause,
                    characterIdAtDeath = player.characterId,
                    abilityImpairedAtDeath = StatusEffects.isImpaired(state, lookup, player),
                ),
            )
    }

    /**
     * In-game resurrection (Professor, Shabaloth regurgitation, Bone
     * Collector...): the player lives again but the death record STAYS in
     * the log, marked resurrected — Undertaker/Cannibal history survives.
     *
     * WP0: moved verbatim from `GameActions.resurrect`. WP1 adds the rules of
     * §2.6 (clearing SPENT marks, the RUN_FIRST_NIGHT prompt, the dawn
     * announcement) around this core.
     */
    fun resurrect(
        state: GameState,
        lookup: (String) -> Character? = { null },
        playerId: Long,
    ): GameState {
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId && !it.resurrected }
        return state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(
                deaths = state.deaths.mapIndexed { i, d ->
                    if (i == lastDeath) d.copy(resurrected = true) else d
                },
            )
    }

    /**
     * Undo a mistaken death: the most recent death record is DROPPED, as if
     * it never happened. For in-game resurrection use [resurrect].
     *
     * WP0: moved verbatim from `GameActions.revive`. WP1 additionally drops
     * every Effect and Prompt stamped with the death's `causeEventId`.
     */
    fun revive(state: GameState, playerId: Long): GameState {
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId }
        return state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(deaths = state.deaths.filterIndexed { i, _ -> i != lastDeath })
    }

    fun toggleGhostVote(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(ghostVoteUsed = !it.ghostVoteUsed) }

    /** Which causes each protective effect blocks (lead D29). The table, not prose. */
    val PROTECTS: Map<EffectKind, Set<DeathCause>>
        get() = TODO("WP1")
}
