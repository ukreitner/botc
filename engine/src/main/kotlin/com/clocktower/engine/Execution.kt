package com.clocktower.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }

/** How the execution was decided — for the log and for rules that bypass the tally. */
@Serializable
enum class ExecutionVia { VOTE, VIRGIN, VIZIER, JUDGE, PSYCHOPATH, RIOT, STORYTELLER }

/**
 * Every execution, INCLUDING days on which nobody was executed. This list is the
 * single "day is closed" signal (lead D30) — there is no boolean anywhere.
 *
 * An execution that kills nobody is still an execution: Vortox, Mayor, Leviathan,
 * Goblin, Boomdandy and the Undertaker all hinge on the distinction.
 */
@Serializable
data class ExecutionRecord(
    val day: Int,
    val outcome: ExecutionOutcome,
    /** Null only when outcome == NO_EXECUTION. May be [GameState.STORYTELLER_SEAT_ID]. */
    val playerId: Long? = null,
    /** Who nominated them — Fearmonger, Psychopath roshambo, Town Crier, the log. */
    val nominatorId: Long? = null,
    /** Index into `state.nominations` for the nomination this resolved. */
    val nominationIndex: Int? = null,
    /** The DeathEvent this execution produced, when it killed someone. */
    val deathEventId: Long? = null,
    /**
     * Character credited with the save, for SURVIVED: "devilsadvocate", "pacifist",
     * "fool", "sailor", "tealady", "vizier", "zombuul", "psychopath", "mayor",
     * "scapegoat", "alreadyDead". "" for a bare storyteller decision.
     */
    val preventedBy: String = "",
    /** Seat that died instead (Scapegoat). The execution still belongs to [playerId]. */
    val diedInsteadId: Long? = null,
    val via: ExecutionVia = ExecutionVia.VOTE,
    /** Snapshots so later character/alignment changes cannot rewrite history. */
    val characterIdAtExecution: String? = null,
    val wasEvilAtExecution: Boolean? = null,
    val abilityImpairedAtExecution: Boolean? = null,
    /** Weighted tally and threshold at the moment of the decision. */
    val tally: Int = 0,
    val threshold: Int = 0,
)

/** A consequence the storyteller must confirm after an execution resolves. */
@Serializable
data class ExecutionConsequence(
    val sourceId: String,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work (drunk/poisoned/dead/spent) — the ST decides anyway. */
    val impaired: Boolean = false,
)

/** The one execution funnel (WP3). */
object Execution {

    /**
     * THE execution funnel. Every "Execute" button in the app calls this:
     * DayScreen block banner, DayScreen nomination row, GameShell dusk guard,
     * SeatSheet, and any registry-driven auto-execution (Virgin, Vizier, Judge).
     *
     * Order of operations:
     *  1. Refuse for a Traveller (travellers are exiled, never executed).
     *  2. Refuse when `executionSpent` and not `secondExecutionAllowed`, unless [force].
     *  3. Snapshot character/alignment/impairment/tally/threshold.
     *  4. Append the ExecutionRecord ALWAYS — before any kill, so an aborted kill
     *     still leaves the execution recorded.
     *  5. Route the death through `Deaths.attempt(cause = EXECUTION)`; the funnel's
     *     outcome decides DIED vs SURVIVED and fills `preventedBy`/`diedInsteadId`.
     *  6. Place ("undertaker", "Died Today") on the seat that actually died, if any.
     */
    fun execute(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        nominatorId: Long? = null,
        via: ExecutionVia = ExecutionVia.VOTE,
        nominationIndex: Int? = null,
        force: Boolean = false,
    ): GameState = TODO("WP3")

    /** Records that today had no execution. Idempotent; replaced if an execution follows. */
    fun noExecution(state: GameState): GameState = TODO("WP3")

    /** Exile a Traveller. Never an execution; never affected by any ability. */
    fun exile(state: GameState, lookup: (String) -> Character?, playerId: Long): GameState =
        TODO("WP3")

    /**
     * What the storyteller must confirm now. Covers at minimum: Devil's Advocate,
     * Pacifist, Fool, Sailor, Tea Lady, Zombuul first death, Vizier immunity,
     * Psychopath roshambo, Scapegoat substitution, Mayor bounce, Saint, Goblin claim,
     * Fearmonger, Evil Twin, Minstrel, Mastermind, Leviathan counter, Boomdandy,
     * Cannibal Lunch, Undertaker Died Today, Godfather "an Outsider died today".
     */
    fun consequences(
        state: GameState,
        lookup: (String) -> Character?,
        record: ExecutionRecord,
    ): List<ExecutionConsequence> = TODO("WP3")
}
