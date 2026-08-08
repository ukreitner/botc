package com.clocktower.grimoire.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

private fun requestWakeLockJs(): Unit =
    js("{ try { if (navigator.wakeLock) { navigator.wakeLock.request('screen'); } } catch (e) {} }")

/** Best-effort wake lock — supported in Safari 16.4+/Chrome. */
@Composable
fun KeepScreenOn() {
    LaunchedEffect(Unit) { requestWakeLockJs() }
}

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
