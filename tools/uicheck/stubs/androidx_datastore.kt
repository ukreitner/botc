@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch", "RedundantSuspendModifier")

package androidx.datastore.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface DataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (t: T) -> T): T
}

interface Serializer<T> {
    val defaultValue: T
    suspend fun readFrom(input: InputStream): T
    suspend fun writeTo(t: T, output: OutputStream)
}

class CorruptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface CorruptionHandler<T>

object DataStoreFactory {
    fun <T> create(
        serializer: Serializer<T>,
        corruptionHandler: CorruptionHandler<T>? = null,
        produceFile: () -> File,
    ): DataStore<T> = object : DataStore<T> {
        override val data: Flow<T> = emptyFlow()
        override suspend fun updateData(transform: suspend (t: T) -> T): T =
            transform(serializer.defaultValue)
    }
}
