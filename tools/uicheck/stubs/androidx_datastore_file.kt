@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.datastore

import android.content.Context
import java.io.File

fun Context.dataStoreFile(fileName: String): File = File(filesDir, fileName)
