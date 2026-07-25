@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package android.content

import java.io.File

open class Context {
    val filesDir: File get() = File(".")
}
