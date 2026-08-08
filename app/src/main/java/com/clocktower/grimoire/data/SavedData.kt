package com.clocktower.grimoire.data

import com.clocktower.engine.GameState
import com.clocktower.engine.NotesState
import com.clocktower.engine.Script
import kotlinx.serialization.Serializable

/** Everything the app persists between launches — platform-neutral. */
@Serializable
data class SavedData(
    val game: GameState? = null,
    val importedScripts: List<Script> = emptyList(),
    /** Player-notes session — independent of the storyteller game. */
    val notes: NotesState? = null,
)
