package com.clocktower.grimoire.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The Android side of the tiny platform seam. The web build substitutes
 * this single file (browser wake lock, <input type=file>), letting every
 * screen compile unchanged on Android, the JVM checker and WebAssembly.
 */

/** Keeps the device awake while in composition — table phones must not sleep. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Returns an action that opens the platform file picker and reads the
 * chosen file as text; the callback gets null when unreadable/cancelled.
 */
@Composable
fun rememberImportFileOpener(onText: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onText(
                try {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                },
            )
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
}
