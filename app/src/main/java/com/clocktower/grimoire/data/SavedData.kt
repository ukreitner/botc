package com.clocktower.grimoire.data

import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.NotesState
import com.clocktower.engine.SCHEMA_VERSION
import com.clocktower.engine.Script
import com.clocktower.engine.Time
import com.clocktower.engine.migrated
import kotlinx.serialization.Serializable

/** A remembered table: the seat names of a game that was actually started. */
@Serializable
data class Roster(
    val names: List<String> = emptyList(),
    val usedAt: Long = 0L,
) {
    val size: Int get() = names.size
}

/**
 * Everything the app persists between launches — platform-neutral.
 *
 * **Compatibility contract (ARCHITECTURE §5.5):** every property has a default,
 * so a save written by ANY earlier build decodes. Both platforms read with
 * `ignoreUnknownKeys = true`, so a save written by a *newer* build also decodes
 * here. Never remove a property and never drop a default.
 */
@Serializable
data class SavedData(
    val game: GameState? = null,
    val importedScripts: List<Script> = emptyList(),
    /** Player-notes session — independent of the storyteller game. */
    val notes: NotesState? = null,
    /**
     * Save-file schema. `0` marks a save written before WP11 stamped it; the
     * app-level migration ([migratedSavedData]) raises it to
     * [com.clocktower.engine.SCHEMA_VERSION].
     */
    val schemaVersion: Int = 0,
    /**
     * Finished or replaced games, newest first, capped at [ARCHIVE_LIMIT].
     * Starting or ending a game archives it instead of destroying it (S9 #38).
     * Undo history is in-memory only, so nothing has to be stripped here.
     */
    val archivedGames: List<GameState> = emptyList(),
    /** Recently used seat rosters, newest first, capped at [ROSTER_LIMIT]. */
    val recentRosters: List<Roster> = emptyList(),
) {
    companion object {
        /** ARCHITECTURE §5.4: archived games are capped at the last 10. */
        const val ARCHIVE_LIMIT = 10

        /** setup-and-home §S2: five remembered rosters is plenty for one group. */
        const val ROSTER_LIMIT = 5
    }
}

/**
 * Files [game] into [SavedData.archivedGames] instead of dropping it.
 *
 * Re-archiving a game the list already holds (same [GameState.id]) REPLACES the
 * older snapshot rather than duplicating it, so "end game" after "start new
 * game" cannot produce two half-copies of one night.
 */
fun SavedData.archiving(game: GameState?): SavedData {
    if (game == null || game.players.isEmpty()) return this
    val stamped = if (game.updatedAt == 0L) game.copy(updatedAt = Time.epochMillis()) else game
    val others = archivedGames.filterNot { it.sameArchivedGameAs(stamped) }
    return copy(archivedGames = (listOf(stamped) + others).take(SavedData.ARCHIVE_LIMIT))
}

/** Removes one archived game (the storyteller deleting a past record). */
fun SavedData.discardingArchived(game: GameState): SavedData =
    copy(archivedGames = archivedGames.filterNot { it.sameArchivedGameAs(game) })

/**
 * Two archived snapshots are the same game when they carry the same stamped
 * [GameState.id]. Saves written before WP0 stamped ids fall back to identity.
 */
private fun GameState.sameArchivedGameAs(other: GameState): Boolean =
    if (id.isNotBlank() && other.id.isNotBlank()) id == other.id else this == other

/**
 * Remembers a roster so the next game can offer "⟲ Last game (12)". Blank
 * placeholder names are dropped; an all-blank roster is not worth remembering.
 */
fun SavedData.remembering(names: List<String>): SavedData {
    val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return this
    val fresh = Roster(cleaned, Time.epochMillis())
    val others = recentRosters.filterNot { it.names == cleaned }
    return copy(recentRosters = (listOf(fresh) + others).take(SavedData.ROSTER_LIMIT))
}

/**
 * The app-level half of the load migration (ARCHITECTURE §5.1 step 8).
 *
 * `Migrations.kt` is the engine's frozen entry point and knows only about
 * `GameState`; the fields WP11 added live on `SavedData`, so the wrapper is
 * migrated here — running the engine migration over the live game AND over
 * every archived game, then stamping the schema version.
 *
 * Idempotent: running it twice is the same as running it once.
 */
fun SavedData.migratedSavedData(lookup: (String) -> Character? = { null }): SavedData = copy(
    game = game?.migrated(lookup),
    archivedGames = archivedGames.map { it.migrated(lookup) }.take(SavedData.ARCHIVE_LIMIT),
    recentRosters = recentRosters.take(SavedData.ROSTER_LIMIT),
    schemaVersion = SCHEMA_VERSION,
)
