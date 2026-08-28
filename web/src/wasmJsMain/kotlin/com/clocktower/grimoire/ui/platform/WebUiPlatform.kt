package com.clocktower.grimoire.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * The browser side of the platform seam, file-for-file with the Android
 * `Platform.kt`.
 *
 * **Ownership: WP0 only.** Every function WP8-WP11 needs is declared here
 * up front (ARCHITECTURE §3.1 / lead D40); unsupported capabilities return
 * null or no-op rather than being absent.
 */

private fun requestWakeLockJs(): Unit =
    js("{ try { if (navigator.wakeLock) { navigator.wakeLock.request('screen'); } } catch (e) {} }")

private fun beepJs(): Unit =
    js(
        """{ try {
              var ctx = new (window.AudioContext || window.webkitAudioContext)();
              var osc = ctx.createOscillator();
              var gain = ctx.createGain();
              osc.frequency.value = 880;
              gain.gain.value = 0.08;
              osc.connect(gain); gain.connect(ctx.destination);
              osc.start(); osc.stop(ctx.currentTime + 0.2);
            } catch (e) {} }""",
    )

private fun vibrateJs(): Unit =
    js("{ try { if (navigator.vibrate) { navigator.vibrate(120); } } catch (e) {} }")

private fun setBrightnessJs(level: Double): Unit =
    js("{ try { window.__setGrimoireBrightness && window.__setGrimoireBrightness(level); } catch (e) {} }")

/** The shell's measured safe area — the same globals `Main.kt` reads. */
private fun safeTopJs(): Double = js("(window.__safeTop || 0)")

private fun safeBottomJs(): Double = js("(window.__safeBottom || 0)")

private fun keyboardInsetJs(): Double =
    js(
        """(function () {
             try {
               var vv = window.visualViewport;
               if (!vv) return 0;
               return Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
             } catch (e) { return 0; }
           })()""",
    )

/** Best-effort wake lock — supported in Safari 16.4+/Chrome. */
@Composable
fun KeepScreenOn() {
    LaunchedEffect(Unit) { requestWakeLockJs() }
}

/**
 * A browser wake lock is dropped whenever the tab is hidden, so it has to be
 * asked for again. Android's window flag survives, which is why this is a
 * seam rather than a call inside [KeepScreenOn].
 */
@Composable
fun RequestWakeLockOnResume() {
    LaunchedEffect(Unit) { requestWakeLockJs() }
}

/**
 * Speech-to-text for the "What was said" recorder (WP9). Null while the
 * browser has no SpeechRecognition, so the caller renders no microphone.
 */
@Composable
fun rememberDictation(onText: (String) -> Unit): (() -> Unit)? = null

/** A short attention signal at the table — a beep plus a vibration pulse. */
@Composable
fun rememberAlertAtTable(): () -> Unit = remember {
    {
        beepJs()
        vibrateJs()
    }
}

/**
 * Sets the screen brightness, 0f..1f, or restores the default with null.
 * The browser cannot touch real brightness; the shell page dims a full-screen
 * overlay instead (WP8 supplies `window.__setGrimoireBrightness`).
 */
@Composable
fun rememberScreenBrightness(): (Float?) -> Unit = remember {
    { level: Float? -> setBrightnessJs((level ?: 1f).toDouble()) }
}

/** Extra bottom inset the on-screen keyboard covers, in dp (iOS Safari). */
@Composable
fun keyboardInsetDp(): Float = keyboardInsetJs().toFloat()

/**
 * Safe-area insets that Compose does NOT know about on wasm, for the overlay
 * layers (`Dialog`, `Popup`, `ModalBottomSheet`) that escape the app root's
 * padding by being hosted at the scene root.
 *
 * These are the SAME numbers `Main.kt` pads the root with — `index.html`
 * measures `env(safe-area-inset-*)` into `window.__safeTop` / `__safeBottom`
 * once and everything reads them from there. Polled rather than remembered:
 * rotating the phone moves the safe area, and an overlay may well be open
 * while it happens.
 */
@Composable
fun shellSafeTopDp(): Dp = pollInsetDp { safeTopJs() }

/** The bottom half of [shellSafeTopDp] — the home indicator's strip. */
@Composable
fun shellSafeBottomDp(): Dp = pollInsetDp { safeBottomJs() }

/**
 * Reads one shell-measured inset and keeps reading it, writing state only when
 * the value actually changes, so a still device costs no recompositions —
 * the same discipline `Main.kt` uses for the root padding.
 */
@Composable
private fun pollInsetDp(read: () -> Double): Dp {
    var value by remember { mutableStateOf(read()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = read()
            if (next != value) value = next
            delay(INSET_POLL_MILLIS)
        }
    }
    return value.dp
}

private const val INSET_POLL_MILLIS = 250L

/** Opens a browser file picker and reads the chosen file as text. */
@Composable
fun rememberImportFileOpener(onText: (String?) -> Unit): () -> Unit {
    val currentOnText by rememberUpdatedState(onText)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".json,application/json,text/plain"
        input.onchange = {
            val file = input.files?.get(0)
            if (file != null) {
                val reader = FileReader()
                reader.onload = {
                    currentOnText(reader.result?.toString())
                    null
                }
                reader.onerror = {
                    currentOnText(null)
                    null
                }
                reader.readAsText(file)
            }
            null
        }
        input.click()
    }
}
