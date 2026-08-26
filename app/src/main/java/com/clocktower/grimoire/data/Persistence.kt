package com.clocktower.grimoire.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.clocktower.engine.Character
import com.clocktower.engine.GameData
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object SavedDataSerializer : Serializer<SavedData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Bundled dataset, used only to resolve characters during the load migration. */
    private val dataset by lazy { runCatching { GameData.loadDefault() }.getOrNull() }

    override val defaultValue: SavedData = SavedData()

    /** Resolves an id against the bundled dataset plus a save's own homebrew. */
    private fun lookupFor(saved: SavedData): (String) -> Character? {
        val custom = (listOfNotNull(saved.game) + saved.archivedGames)
            .flatMap { it.script.customCharacters }
            .associateBy { it.id }
        return { id -> custom[id] ?: dataset?.character(id) }
    }

    override suspend fun readFrom(input: InputStream): SavedData = try {
        val saved = json.decodeFromString(SavedData.serializer(), input.readBytes().decodeToString())
        // The ONE migration entry point on Android (ARCHITECTURE §5.1); the
        // SavedData wrapper's own step (schemaVersion, archived games) is
        // WP11's, because Migrations.kt is frozen and sees only GameState.
        saved.migratedSavedData(lookupFor(saved))
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read saved grimoire", e)
    }

    override suspend fun writeTo(t: SavedData, output: OutputStream) {
        output.write(json.encodeToString(SavedData.serializer(), t).encodeToByteArray())
    }
}

fun createDataStore(context: Context): DataStore<SavedData> =
    DataStoreFactory.create(
        serializer = SavedDataSerializer,
        corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { SavedData() },
        produceFile = { context.dataStoreFile("grimoire.json") },
    )
