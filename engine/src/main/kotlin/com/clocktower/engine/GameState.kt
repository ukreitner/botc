package com.clocktower.engine

import kotlinx.serialization.Serializable

/** A reminder token placed on a player's seat. */
@Serializable
data class PlacedReminder(
    /** Character id the token belongs to, or "" for generic tokens. */
    val sourceId: String,
    val label: String,
)

/** One seat in the grimoire circle. */
@Serializable
data class Player(
    val id: Long,
    val name: String,
    val characterId: String? = null,
    /**
     * Character identity presented to this player when it differs from the
     * truth (Drunk, Lunatic, Marionette). Null means show [characterId].
     */
    val shownCharacterId: String? = null,
    /** True when this player's alignment differs from their character's default. */
    val alignmentFlipped: Boolean = false,
    val alive: Boolean = true,
    /** Dead players hold one ghost vote until they spend it. */
    val ghostVoteUsed: Boolean = false,
    val isTraveller: Boolean = false,
    val reminders: List<PlacedReminder> = emptyList(),
    val note: String = "",
) {
    val characterShownToPlayerId: String? get() = shownCharacterId ?: characterId

    /**
     * Drunk and Marionette wake as the good character they believe they are.
     * A Lunatic keeps its own dedicated wake row, despite seeing a Demon token.
     */
    val nightRoleId: String?
        get() = if (characterId == "drunk" || characterId == "marionette") {
            shownCharacterId ?: characterId
        } else {
            characterId
        }

    fun team(lookup: (String) -> Character?): Team? =
        characterId?.let { lookup(it)?.team }

    fun isEvil(lookup: (String) -> Character?): Boolean {
        val base = team(lookup)?.isEvil ?: false
        return base != alignmentFlipped
    }
}

@Serializable
enum class Phase { SETUP, NIGHT, DAY }

@Serializable
enum class NominationResult { ABOUT_TO_DIE, SAFE, TIED, WITHDRAWN }

/** A nomination and its vote tally. */
@Serializable
data class Nomination(
    val day: Int,
    val nominatorId: Long,
    val nomineeId: Long,
    val votes: Int = 0,
    /** Player ids that raised hands, in clock order (for the record). */
    val voterIds: List<Long> = emptyList(),
    val result: NominationResult = NominationResult.SAFE,
    val isExile: Boolean = false,
)

@Serializable
enum class DeathCause { EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE, STORYTELLER }

@Serializable
data class DeathRecord(
    val playerId: Long,
    /** Day/night number on which the death happened. */
    val day: Int,
    val atNight: Boolean,
    val cause: DeathCause,
    /** Snapshots prevent later character changes from rewriting a death. */
    val characterIdAtDeath: String? = null,
    /** Null only for saves created before impairment snapshots existed. */
    val abilityImpairedAtDeath: Boolean? = null,
    /** True when this death happened but was later undone in-game. */
    val resurrected: Boolean = false,
)

/** Everything the storyteller tracks for one game. */
@Serializable
data class GameState(
    val script: Script,
    /** Custom characters carried by the script, id -> character. */
    val players: List<Player> = emptyList(),
    val fabledIds: List<String> = emptyList(),
    val phase: Phase = Phase.SETUP,
    /** Night 1 is the first night; day N follows night N. */
    val cycle: Int = 1,
    val demonBluffIds: List<String> = emptyList(),
    val nominations: List<Nomination> = emptyList(),
    val deaths: List<DeathRecord> = emptyList(),
    /** Ids of night-order steps already completed tonight. */
    val nightStepsDone: Set<String> = emptySet(),
    /**
     * True while the Mastermind's extra day is being played out after the
     * Demon died by execution: if anyone is executed, their team loses.
     */
    val mastermindDayActive: Boolean = false,
    val storytellerNotes: String = "",
    /** Millis timestamp of last modification, for save management. */
    val updatedAt: Long = 0L,
) {
    val alivePlayers: List<Player> get() = players.filter { it.alive }
    val aliveNonTravellers: List<Player> get() = players.filter { it.alive && !it.isTraveller }

    fun player(id: Long): Player? = players.find { it.id == id }

    fun updatePlayer(id: Long, transform: (Player) -> Player): GameState =
        copy(players = players.map { if (it.id == id) transform(it) else it })

    /** Votes needed for an execution: at least half of alive players, rounded up. */
    val executionThreshold: Int get() = (alivePlayers.size + 1) / 2

    /**
     * Votes needed to exile a traveller: at least half of ALL players
     * (alive and dead), rounded up.
     */
    val exileThreshold: Int get() = (players.size + 1) / 2
}

object Voting {
    /** Threshold for an execution among [aliveCount] living players. */
    fun executionThreshold(aliveCount: Int): Int = (aliveCount + 1) / 2

    /** Threshold for a traveller exile among [totalCount] players. */
    fun exileThreshold(totalCount: Int): Int = (totalCount + 1) / 2

    /**
     * Whether [votes] makes the nominee about-to-die, given the current
     * highest tally [currentHighest] today (0 if none) and the threshold.
     * Equal to the highest is a tie (no one dies); beating it marks the new
     * about-to-die player.
     */
    fun outcome(votes: Int, threshold: Int, currentHighest: Int): NominationResult = when {
        votes < threshold -> NominationResult.SAFE
        votes > currentHighest -> NominationResult.ABOUT_TO_DIE
        votes == currentHighest -> NominationResult.TIED
        else -> NominationResult.SAFE // below today's highest tally
    }
}
