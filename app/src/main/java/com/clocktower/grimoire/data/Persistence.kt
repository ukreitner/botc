package com.clocktower.grimoire.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.clocktower.engine.GameData
import com.clocktower.engine.migrated
import com.clocktower.engine.migrationLookup
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

    override suspend fun readFrom(input: InputStream): SavedData = try {
        val saved = json.decodeFromString(SavedData.serializer(), input.readBytes().decodeToString())
        // The ONE migration entry point on Android (ARCHITECTURE §5.1).
        saved.copy(game = saved.game?.let { it.migrated(it.migrationLookup(dataset)) })
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
