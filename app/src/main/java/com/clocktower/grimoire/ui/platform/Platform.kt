package com.clocktower.grimoire.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * The Android side of the tiny platform seam. The web build substitutes
 * this single file (browser wake lock, <input type=file>), letting every
 * screen compile unchanged on Android, the JVM checker and WebAssembly.
 *
 * **Ownership: WP0 only.** Every function WP8-WP11 needs is declared here up
 * front (ARCHITECTURE §3.1 / lead D40); feature packages only call them.
 * A capability this platform cannot offer returns null or no-ops — the caller
 * must never test for the platform.
 *
 * Note for whoever fills these in: `tools/uicheck` compiles this file against
 * hand-written Android stubs, so anything reaching into `android.os`,
 * `android.media` or `android.view.WindowManager` needs a stub added there in
 * the same change.
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
 * Re-requests the wake lock after the app was backgrounded. A no-op here,
 * where [KeepScreenOn] is a window flag that survives; the browser drops its
 * lock whenever the tab is hidden, which is why this is a seam at all.
 */
@Composable
fun RequestWakeLockOnResume() {
    // Nothing to do on Android.
}

/**
 * Speech-to-text for the "What was said" recorder (WP9) and seat notes (WP10).
 *
 * Returns null when the platform cannot dictate, and the caller then renders
 * no microphone button. WP0 ships the declaration so no feature package has to
 * add a seam of its own.
 */
@Composable
fun rememberDictation(onText: (String) -> Unit): (() -> Unit)? = null

/**
 * A short attention signal at the table: used by the day timer (WP9) and the
 * night sheet's "wake them" cue (WP8). Haptic on Android, beep + vibrate in
 * the browser.
 */
@Composable
fun rememberAlertAtTable(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}

/**
 * Sets the screen brightness for this window, 0f..1f, or restores the system
 * default with null — the night screen's dim control (WP8).
 *
 * No-op for now: the real implementation sets `window.attributes
 * .screenBrightness`, which needs an `android.view.WindowManager` stub in
 * `tools/uicheck`. Until then the night screen dims with its own scrim.
 */
@Composable
fun rememberScreenBrightness(): (Float?) -> Unit = remember { { _: Float? -> } }

/**
 * Extra bottom inset the on-screen keyboard covers, in dp. Compose handles
 * this on Android; iOS Safari needs `visualViewport` (WP11).
 */
@Composable
fun keyboardInsetDp(): Float = 0f

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
