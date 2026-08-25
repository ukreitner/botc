package com.clocktower.engine

import kotlin.random.Random

/**
 * COMPATIBILITY FAÇADE ONLY — frozen as of WP0.
 *
 * Every verb below is a one-line delegate to the object that now owns it
 * ([Seats], [Effects], [Deaths], [Phases], [DayRules], [Setup], [Bluffs],
 * [Identity], [NightPlan]). It exists so the pre-split call sites and tests
 * keep compiling while the screens migrate.
 *
 * **No new verb is ever added here.** New engine functions live on the owning
 * object and reach the UI through `GameActionsApi`.
 */
object GameActions {

    // ---- seats (-> Seats.kt) ----

    fun newGame(script: Script, playerNames: List<String>): GameState =
        Seats.newGame(script, playerNames)

    fun addSeat(state: GameState, name: String, afterId: Long? = null): GameState =
        Seats.addSeat(state, name, afterId)

    fun removeSeat(state: GameState, playerId: Long): GameState =
        Seats.removeSeat(state, playerId)

    fun moveSeat(state: GameState, playerId: Long, delta: Int): GameState =
        Seats.moveSeat(state, playerId, delta)

    fun rename(state: GameState, playerId: Long, name: String): GameState =
        Seats.rename(state, playerId, name)

    fun assignCharacter(
        state: GameState,
        playerId: Long,
        characterId: String?,
        isTraveller: Boolean = false,
    ): GameState = Seats.assignCharacter(state, playerId, characterId, isTraveller)

    fun setShownCharacter(state: GameState, playerId: Long, shownCharacterId: String?): GameState =
        Seats.setShownCharacter(state, playerId, shownCharacterId)

    fun flipAlignment(
        state: GameState,
        playerId: Long,
        lookup: (String) -> Character? = { null },
    ): GameState = Seats.flipAlignment(state, playerId, lookup)

    fun setNote(state: GameState, playerId: Long, note: String): GameState =
        Seats.setNote(state, playerId, note)

    fun deal(state: GameState, bag: List<String>, random: Random = Random): GameState =
        Seats.deal(state, bag, random)

    // ---- identity (-> Identity.kt) ----

    fun snakeCharmerSwap(state: GameState, charmerId: Long, demonPlayerId: Long): GameState =
        Identity.snakeCharmerSwap(state, charmerId, demonPlayerId)

    fun starPass(
        state: GameState,
        demonPlayerId: Long,
        heirPlayerId: Long,
        lookup: (String) -> Character? = { null },
    ): GameState = Identity.starPass(
        state = state,
        lookup = lookup,
        demonPlayerId = demonPlayerId,
        heirPlayerId = heirPlayerId,
    )

    fun swapCharacters(state: GameState, id1: Long, id2: Long): GameState =
        Identity.swapCharacters(state, { null }, id1, id2)

    // ---- deaths (-> Deaths.kt) ----

    fun kill(
        state: GameState,
        playerId: Long,
        cause: DeathCause,
        lookup: (String) -> Character? = { null },
    ): GameState = Deaths.kill(state, playerId, cause, lookup)

    fun revive(state: GameState, playerId: Long): GameState = Deaths.revive(state, playerId)

    fun resurrect(state: GameState, playerId: Long): GameState =
        Deaths.resurrect(state, playerId = playerId)

    fun toggleGhostVote(state: GameState, playerId: Long): GameState =
        Deaths.toggleGhostVote(state, playerId)

    // ---- grimoire tokens (-> Effects.kt) ----

    fun addReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState =
        Effects.addReminder(state, playerId, reminder)

    fun placeExclusiveReminder(
        state: GameState,
        playerId: Long,
        reminder: PlacedReminder,
    ): GameState = Effects.placeExclusiveReminder(state, playerId, reminder)

    fun removeReminder(state: GameState, playerId: Long, index: Int): GameState =
        Effects.removeReminder(state, playerId, index)

    // ---- bluffs and fabled (-> Bluffs.kt) ----

    fun suggestBluffs(
        available: List<Character>,
        state: GameState,
        random: Random = Random,
    ): List<String> = Bluffs.suggestBluffs(available, state, random)

    fun setBluffs(state: GameState, bluffIds: List<String>): GameState =
        Bluffs.setDemonBluffs(state, bluffIds)

    fun setFabled(state: GameState, fabledIds: List<String>): GameState =
        Bluffs.setFabled(state, fabledIds)

    // ---- phases and the night checklist (-> Phases.kt / NightPlan.kt) ----

    fun advancePhase(state: GameState, lookup: (String) -> Character? = { null }): GameState =
        Phases.advancePhase(state, lookup)

    fun toggleNightStep(state: GameState, stepId: String): GameState =
        NightPlan.toggleDone(state, stepId)

    // ---- the day (-> DayRules.kt) ----

    fun recordNomination(state: GameState, nomination: Nomination): GameState =
        DayRules.recordNomination(state, nomination)

    fun highestVotesToday(state: GameState): Int = DayRules.highestVotesToday(state)

    fun hasNominatedToday(state: GameState, playerId: Long): Boolean =
        DayRules.hasNominatedToday(state, playerId)

    fun hasBeenNominatedToday(state: GameState, playerId: Long): Boolean =
        DayRules.hasBeenNominatedToday(state, playerId)

    fun aboutToDie(state: GameState): Long? = DayRules.aboutToDie(state)

    // ---- the bag (-> Setup.kt) ----

    fun randomBag(
        available: List<Character>,
        playerCount: Int,
        random: Random = Random,
        attempts: Int = 200,
    ): List<Character>? = Setup.randomBag(available, playerCount, random, attempts)

    /** Ids that may legally appear multiple times in a bag. */
    val DUPLICABLE: Set<String> get() = Setup.DUPLICABLE

    fun validateBag(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: Collection<String> = emptyList(),
        allowAnyDuplicates: Boolean = false,
    ): List<String> = Setup.validateBag(bag, playerCount, fabledIds, allowAnyDuplicates)

    fun validateSetupState(state: GameState, lookup: (String) -> Character?): List<String> =
        Setup.validateSetupState(state, lookup)
}
