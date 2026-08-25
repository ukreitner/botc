package com.clocktower.engine

/**
 * The phase pipeline. WP0 moved `GameActions.advancePhase` and its two expiry
 * tables here verbatim; WP1 replaces the tables with [Tokens] / [Effects] and
 * adds the briefing steps of §2.15 in this exact order:
 *
 * NIGHT -> DAY:
 *   1. dawn = Briefings.at(state, lookup, DAWN)   // BEFORE any sweep
 *   2. expire Until.DAWN effects and their tokens
 *   3. Tokens.advanceCountdowns(state, Until.DAWN)
 *   4. Effects.reconcile(...)
 *   5. copy(phase = DAY, lastDawn = dawn)
 *
 * DAY -> NIGHT:
 *   1. dusk = Briefings.at(state, lookup, DUSK)
 *   2. expire Until.DUSK effects; advance dusk countdowns
 *   3. Effects.reconcile(...)
 *   4. copy(phase = NIGHT, cycle = cycle + 1, nightStepsDone = emptySet(), lastDusk = dusk)
 */
object Phases {

    /**
     * Reminder tokens whose effect only lasts the night: removed at dawn,
     * exactly like sweeping them off the physical grimoire.
     */
    private val EXPIRES_AT_DAWN: Set<Pair<String, String>> = setOf(
        "monk" to "Safe",
        "innkeeper" to "Protected",
        "exorcist" to "Chosen",
        "lunatic" to "Attack 1",
        "lunatic" to "Attack 2",
        "lunatic" to "Attack 3",
    )

    /**
     * Reminder tokens that last "tonight and tomorrow day": removed at
     * dusk, right before their source picks a new target.
     */
    private val EXPIRES_AT_DUSK: Set<Pair<String, String>> = setOf(
        "poisoner" to "Poisoned",
        "sailor" to "Drunk",
        "innkeeper" to "Drunk",
        "butler" to "Master",
        "devilsadvocate" to "Survives execution",
        "witch" to "Cursed",
        "cerenovus" to "Mad",
        "harpy" to "Mad",
        "harpy" to "2nd",
        "goblin" to "Claimed",
    )

    private fun clearEphemeral(state: GameState, table: Set<Pair<String, String>>): GameState =
        state.copy(
            players = state.players.map { player ->
                player.copy(
                    reminders = player.reminders.filterNot { (it.sourceId to it.label) in table },
                )
            },
        )

    /**
     * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> DAY 2 -> ...
     * Day- and night-scoped reminder tokens expire automatically at the
     * transition (undoable, and re-placeable by hand like everything else).
     */
    fun advancePhase(state: GameState, lookup: (String) -> Character? = { null }): GameState =
        when (state.phase) {
            Phase.SETUP -> state.copy(phase = Phase.NIGHT, cycle = 1, nightStepsDone = emptySet())
            Phase.NIGHT -> clearEphemeral(state, EXPIRES_AT_DAWN).copy(phase = Phase.DAY)
            Phase.DAY -> clearEphemeral(state, EXPIRES_AT_DUSK)
                .copy(phase = Phase.NIGHT, cycle = state.cycle + 1, nightStepsDone = emptySet())
        }
}
