package com.clocktower.grimoire.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object SavedDataSerializer : Serializer<SavedData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: SavedData = SavedData()

    override suspend fun readFrom(input: InputStream): SavedData = try {
        json.decodeFromString(SavedData.serializer(), input.readBytes().decodeToString())
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
