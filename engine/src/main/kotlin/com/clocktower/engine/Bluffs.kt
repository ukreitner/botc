package com.clocktower.engine

import kotlin.random.Random

/** One set of bluffs this game owes someone. A LIST, not a map (lead D38). */
data class BluffRequirement(
    /** Key into [GameState.bluffSets]. Source-qualified so one seat can hold two sets. */
    val key: String,
    /** Seat that receives them; null for the Demon set in a multi-Demon game. */
    val recipientId: Long?,
    /** "Demon bluffs", "Snitch bluffs — Ana (Poisoner)", "Lunatic bluffs — Bo". */
    val label: String,
    val size: Int = 3,
    /** Only the Lunatic (and an impaired Snitch) may be shown in-play characters. */
    val allowInPlay: Boolean = false,
    /** Night-order step where the card is shown. */
    val stepSlotId: String,
    val sourceId: String,
    /** The rules sentence surfaced under the picker. */
    val reason: String = "",
    /** false = offer it, never block on it (Legion: "bluffs are optional"). */
    val required: Boolean = true,
) {
    companion object {
        const val DEMON_KEY = "demon"
    }
}

data class BluffCandidate(
    val character: Character,
    val inPlay: Boolean,
    /** "the Drunk believes this", "the Boffin gave the Demon this", "the Alchemist has this". */
    val inUseBy: String? = null,
)

/**
 * How one character contributes bluff requirements. Referenced by
 * `CharacterRule.bluffs`; WP4 fills in the registry side.
 */
data class BluffRule(
    val produce: (state: GameState, lookup: (String) -> Character?, holder: Player) ->
    List<BluffRequirement>,
)

/**
 * Bluff sets, per requirement key (WP4). WP0 moved `setBluffs` / `suggestBluffs` /
 * `setFabled` here verbatim.
 */
object Bluffs {

    fun requirements(state: GameState, lookup: (String) -> Character?): List<BluffRequirement> =
        TODO("WP4")

    fun candidates(
        state: GameState,
        script: List<Character>,
        req: BluffRequirement,
    ): List<BluffCandidate> = TODO("WP4")

    fun suggest(
        state: GameState,
        script: List<Character>,
        req: BluffRequirement,
        random: Random,
    ): List<String> = TODO("WP4")

    /**
     * Suggests 3 demon bluffs: not-in-play good characters from the script,
     * preferring two townsfolk and one outsider like most storytellers.
     *
     * WP0: moved verbatim from `GameActions.suggestBluffs`; WP4 folds it into
     * the per-requirement [suggest].
     */
    fun suggestBluffs(
        available: List<Character>,
        state: GameState,
        random: Random = Random,
    ): List<String> {
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        val townsfolk = available.filter { it.team == Team.TOWNSFOLK && it.id !in inPlay }.shuffled(random)
        val outsiders = available.filter { it.team == Team.OUTSIDER && it.id !in inPlay }.shuffled(random)
        val picks = (townsfolk.take(2) + outsiders.take(1) + townsfolk.drop(2) + outsiders.drop(1))
        return picks.take(3).map { it.id }
    }

    /** Stores one bluff set under its requirement key. */
    fun set(state: GameState, key: String, ids: List<String>): GameState =
        state.copy(bluffSets = state.bluffSets + (key to ids))

    fun clear(state: GameState, key: String): GameState =
        state.copy(bluffSets = state.bluffSets - key)

    /** WP0 move of `GameActions.setBluffs`: the Demon's three. */
    fun setDemonBluffs(state: GameState, bluffIds: List<String>): GameState =
        set(state, BluffRequirement.DEMON_KEY, bluffIds.take(3))

    /**
     * WP0 move of `GameActions.setFabled`: replaces the active Fabled list,
     * keeping the per-Fabled state of entries that stay in play.
     */
    fun setFabled(state: GameState, fabledIds: List<String>): GameState {
        val existing = state.fabled.associateBy { it.id }
        return state.copy(
            fabled = fabledIds.map { id ->
                existing[id] ?: FabledEntry(id = id, addedOnCycle = state.cycle)
            },
            legacyFabledIds = emptyList(),
        )
    }

    /** "Fisherman is one of the Demon's bluffs and is now in play." */
    fun conflicts(state: GameState, lookup: (String) -> Character?): List<String> = TODO("WP4")
}
