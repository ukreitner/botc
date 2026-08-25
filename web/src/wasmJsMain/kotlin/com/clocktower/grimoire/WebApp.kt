package com.clocktower.grimoire

import com.clocktower.engine.GameData
import com.clocktower.engine.migrated
import com.clocktower.engine.migrationLookup
import com.clocktower.grimoire.data.SavedData
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/** Browser-global app state: the dataset, loaded once by Main. */
object WebApp {
    lateinit var gameData: GameData

    /** Null until Main has fetched the dataset — the load migration degrades gracefully. */
    val gameDataOrNull: GameData? get() = if (::gameData.isInitialized) gameData else null
}

/** localStorage-backed persistence with the same SavedData shape. */
object WebStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private const val KEY = "clocktower-grimoire"

    fun load(): SavedData = try {
        val saved = localStorage.getItem(KEY)
            ?.let { json.decodeFromString(SavedData.serializer(), it) }
            ?: SavedData()
        // The ONE migration entry point on the web (ARCHITECTURE §5.1).
        saved.copy(
            game = saved.game?.let { it.migrated(it.migrationLookup(WebApp.gameDataOrNull)) },
        )
    } catch (e: Exception) {
        SavedData()
    }

    fun save(data: SavedData) {
        try {
            localStorage.setItem(KEY, json.encodeToString(SavedData.serializer(), data))
        } catch (e: Exception) {
            // Quota/serialization problems shouldn't crash the table.
        }
    }
}
