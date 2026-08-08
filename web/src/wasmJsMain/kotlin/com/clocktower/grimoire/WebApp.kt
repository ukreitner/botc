package com.clocktower.grimoire

import com.clocktower.engine.GameData
import com.clocktower.grimoire.data.SavedData
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/** Browser-global app state: the dataset, loaded once by Main. */
object WebApp {
    lateinit var gameData: GameData
}

/** localStorage-backed persistence with the same SavedData shape. */
object WebStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private const val KEY = "clocktower-grimoire"

    fun load(): SavedData = try {
        localStorage.getItem(KEY)
            ?.let { json.decodeFromString(SavedData.serializer(), it) }
            ?: SavedData()
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
