package com.clocktower.grimoire

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater for the sideloaded APK: compares this build's commit (baked
 * in by CI as BUILD_SHA) against the rolling "latest-apk" GitHub release,
 * and when a newer build is up there, downloads it and hands it to the
 * system installer. Android still asks for one confirmation tap — silent
 * installs are reserved for app stores — but that's the whole ceremony.
 *
 * Requires every published APK to carry the same signature (CI signs with
 * the persistent keystore secret); Android refuses mismatched updates.
 */
object UpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/ukreitner/botc/releases/tags/latest-apk"

    sealed interface State {
        data object Hidden : State
        data class Available(val sha: String, val apkUrl: String) : State
        data class Downloading(val percent: Int) : State
        data class Failed(val message: String, val retry: Available) : State
    }

    var state by mutableStateOf<State>(State.Hidden)

    private var checked = false

    /**
     * True on an Android emulator (goldfish/ranchu/QEMU), by the properties the
     * emulator image sets and a device never does.
     *
     * The playtest harness drives real, CI-signed debug APKs on headless
     * emulators with the network up, so the banner appeared on first launch and
     * took ~126 px off the bottom of *every* screen — enough that
     * `tools/emu/scenarios/C_day_repro` stopped finding "Start empty"
     * (docs/audit/STATUS.md, HARNESS NOTE). A phone in a storyteller's hand is
     * unaffected: this is checked only together with [BuildConfig.DEBUG], so a
     * release build always asks, and the web build never compiles this file.
     */
    private val onEmulator: Boolean by lazy {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("generic") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            Build.BRAND.orEmpty().startsWith("generic") ||
            Build.MODEL.orEmpty().contains("Emulator") ||
            Build.MODEL.orEmpty().contains("Android SDK built for") ||
            Build.PRODUCT.orEmpty().contains("sdk_gphone") ||
            Build.PRODUCT.orEmpty() == "google_sdk"
    }

    /**
     * One check per process; local dev builds (BUILD_SHA == "dev") skip it, and
     * so does a debug build running on an emulator — see [onEmulator].
     */
    fun checkOnce() {
        if (checked || BuildConfig.BUILD_SHA == "dev") return
        if (BuildConfig.DEBUG && onEmulator) {
            checked = true
            return
        }
        checked = true
        Thread {
            try {
                val conn = URL(RELEASE_API).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val body = conn.inputStream.bufferedReader().readText()
                val release = Json.parseToJsonElement(body).jsonObject
                val notes = release["body"]?.jsonPrimitive?.content ?: ""
                val sha = Regex("built from ([0-9a-f]{7,40})").find(notes)?.groupValues?.get(1)
                val apkUrl = release["assets"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["name"]?.jsonPrimitive?.content == "clocktower-grimoire.apk" }
                    ?.get("browser_download_url")?.jsonPrimitive?.content
                if (sha != null && apkUrl != null &&
                    !sha.startsWith(BuildConfig.BUILD_SHA) && !BuildConfig.BUILD_SHA.startsWith(sha)
                ) {
                    state = State.Available(sha, apkUrl)
                }
            } catch (e: Exception) {
                // No network or API hiccup — stay hidden; next launch retries.
            }
        }.start()
    }

    fun downloadAndInstall(context: Context, update: State.Available) {
        state = State.Downloading(0)
        val appContext = context.applicationContext
        Thread {
            try {
                val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "update.apk")
                val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    apk.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            copied += n
                            if (total > 0) {
                                state = State.Downloading((copied * 100 / total).toInt())
                            }
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(
                    appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", apk,
                )
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(install)
                state = State.Hidden
            } catch (e: Exception) {
                state = State.Failed(e.message ?: "download failed", update)
            }
        }.start()
    }
}

/** Small bottom banner shown over the app whenever a newer build exists. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateManager.checkOnce() }

    val state = UpdateManager.state
    if (state is UpdateManager.State.Hidden) return

    Surface(
        modifier = modifier.padding(12.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
        ) {
            when (state) {
                is UpdateManager.State.Available -> {
                    Text(
                        "New build available (${state.sha.take(7)})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { UpdateManager.downloadAndInstall(context, state) }) {
                        Text("Update")
                    }
                    DismissUpdateButton()
                }
                is UpdateManager.State.Downloading -> Text(
                    "Downloading update… ${state.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                )
                is UpdateManager.State.Failed -> {
                    Text(
                        "Update failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { UpdateManager.downloadAndInstall(context, state.retry) }) {
                        Text("Retry")
                    }
                    DismissUpdateButton()
                }
                is UpdateManager.State.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun DismissUpdateButton() {
    IconButton(onClick = { UpdateManager.state = UpdateManager.State.Hidden }) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss update banner",
            modifier = Modifier.size(18.dp),
        )
    }
}
