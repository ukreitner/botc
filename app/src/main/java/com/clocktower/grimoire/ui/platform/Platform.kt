package com.clocktower.grimoire.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Safe-area insets that this platform's Compose `WindowInsets` do NOT already
 * report, for the overlay layers (`Dialog`, `Popup`, `ModalBottomSheet`) that
 * sit outside the shell's own inset padding.
 *
 * Zero on Android, and that is the point: `MainActivity` calls
 * `enableEdgeToEdge()`, so Compose carries the real system-bar insets and
 * `Modifier.windowInsetsPadding` — which is consumption-aware — is the whole
 * story. The browser build has no such thing and returns the shell-measured
 * `env(safe-area-inset-*)` instead. See `ui/components/SafeArea.kt`; there is
 * exactly one inset source per platform and this is Android's.
 */
@Composable
fun shellSafeTopDp(): Dp = 0.dp

/** The bottom half of [shellSafeTopDp]: zero here, the home indicator on the web. */
@Composable
fun shellSafeBottomDp(): Dp = 0.dp

/**
 * How far this platform's own full-screen `Dialog` window pushes its content
 * down, WITHOUT telling Compose about it.
 *
 * Android's dialog windows fit system windows by default: the platform places
 * the content below the status bar but still measures it against the full
 * screen height, so a dialog laid out to 2400 px is drawn from y=136 and its
 * last 136 px fall off the bottom. Compose inside that window reports no
 * insets at all, which is why `Modifier.windowInsetsPadding` cannot see any of
 * it (playtest A-2). Whatever the window took off the top has to be given back
 * at the bottom — see `SafeArea.rememberDialogInsets`.
 *
 * The browser hosts dialogs at the scene root with no offset whatsoever, so
 * the web seam returns zero.
 */
@Composable
fun dialogDecorTopDp(): Dp =
    WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

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
