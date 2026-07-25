@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.activity

import android.os.Bundle

open class ComponentActivity : android.content.Context() {
    protected open fun onCreate(savedInstanceState: Bundle?) {}
}

fun ComponentActivity.enableEdgeToEdge() {}
