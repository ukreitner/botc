@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

// Android-only composition locals; desktop Compose has no equivalent.
val LocalContext = staticCompositionLocalOf<Context> { Context() }
val LocalView = staticCompositionLocalOf<android.view.View> { android.view.View() }
