@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package android.content

import android.net.Uri
import java.io.File
import java.io.InputStream

class ContentResolver {
    fun openInputStream(uri: Uri): InputStream? = null
}

open class Context {
    val filesDir: File get() = File(".")
    val contentResolver: ContentResolver get() = ContentResolver()
}
