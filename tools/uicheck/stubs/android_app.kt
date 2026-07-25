// Android framework stand-ins so the app's sources type-check on the JVM.
// Signatures mirror the real APIs the app uses; implementations are inert.
@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package android.app

import android.content.Context

open class Application : Context() {
    open fun onCreate() {}
}
