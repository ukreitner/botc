package com.clocktower.grimoire.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.clocktower.engine.BotcResources
import com.clocktower.engine.GameData
import com.clocktower.engine.ScriptLink
import com.clocktower.engine.ScriptParser
import com.clocktower.grimoire.WebApp
import com.clocktower.grimoire.WebStore
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.IconStore
import com.clocktower.grimoire.ui.platform.KeepScreenOn
import com.clocktower.grimoire.ui.platform.RequestWakeLockOnResume
import com.clocktower.grimoire.ui.screens.GameShell
import com.clocktower.grimoire.ui.screens.HomeScreen
import com.clocktower.grimoire.ui.screens.LibraryScreen
import com.clocktower.grimoire.ui.screens.NotesScreen
import com.clocktower.grimoire.ui.screens.NotesSetupScreen
import com.clocktower.grimoire.ui.screens.SetupScreen
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.BloodRed
import com.clocktower.grimoire.ui.theme.GrimoireTheme
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.Twilight
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// Safe-area insets measured by the shell (CSS px == dp for Compose).
private fun jsSafeTop(): Double = js("(window.__safeTop || 0)")
private fun jsSafeBottom(): Double = js("(window.__safeBottom || 0)")

/**
 * How much of the layout viewport the software keyboard is covering
 * (setup-and-home §S3). iOS Safari overlays the keyboard instead of resizing
 * the viewport, so nothing else can see it; `index.html` keeps this in sync
 * from `visualViewport`.
 */
private fun jsKeyboardInset(): Double = js("(window.__keyboardInset || 0)")

/** The build this shell was deployed from, stamped into index.html by CI. */
private fun jsBuildLabel(): String = js("(window.__BUILD__ || '')")

/** True once a newer service worker is installed and waiting to take over. */
private fun jsUpdateReady(): Boolean = js("(window.__updateReady === true)")

/** Tells the waiting worker to activate; the page reloads on controllerchange. */
private fun jsApplyUpdate(): Unit = js("{ try { window.__applyUpdate(); } catch (e) {} }")

/** The raw query string, for `?script=`, `?resume=1` and `?new=1`. */
private fun jsSearch(): String = js("(window.location.search || '')")

/** Drops the query string so a reload does not re-run the launch intent. */
private fun jsClearSearch(): Unit =
    js("{ try { history.replaceState(null, '', window.location.pathname); } catch (e) {} }")

/** True when the app is running installed (standalone), not in a browser tab. */
private fun jsStandalone(): Boolean =
    js(
        """(function () {
             try {
               return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) ||
                 window.navigator.standalone === true;
             } catch (e) { return true; }
           })()""",
    )

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    appScope.launch {
        BotcResources.preloaded["/botc/data/characters.json"] = fetchText("data/characters.json")
        BotcResources.preloaded["/botc/data/night_and_jinxes.json"] = fetchText("data/night_and_jinxes.json")
        try {
            BotcResources.preloaded["/botc/data/night_guide.json"] = fetchText("data/night_guide.json")
        } catch (e: Exception) {
            // The guide is an enhancement; the app runs without it.
        }
        WebApp.gameData = GameData.loadDefault()
        // Ask the browser to keep this origin's storage. On iOS a PWA that is
        // NOT installed is capped at 7 days of non-use (ARCHITECTURE §5.4).
        WebStore.requestPersistence()
        IconStore.remoteLoader = { _, url, done ->
            appScope.launch { done(fetchBitmap(url)) }
        }
        startIconPrefetch()
        kotlinx.browser.document.getElementById("boot")?.remove()
        // ComposeViewport (CMP 1.10+) owns its canvas inside the container
        // div and ships the rewritten browser input pipeline — no more
        // stuck hover/pressed states or swallowed taps.
        ComposeViewport(kotlinx.browser.document.getElementById("compose")!!) {
            WebRoot()
        }
    }
}

private suspend fun fetchText(url: String): String {
    val response = window.fetch(url).await<Response>()
    if (!response.ok) error("fetch $url -> ${response.status}")
    return response.text().await<JsString>().toString()
}

/** Fetches [url] and decodes it into a Compose bitmap, or null. */
private suspend fun fetchBitmap(url: String): androidx.compose.ui.graphics.ImageBitmap? = try {
    val response = window.fetch(url).await<Response>()
    if (!response.ok) {
        null
    } else {
        val buffer = response.arrayBuffer().await<ArrayBuffer>()
        val i8 = Int8Array(buffer)
        val bytes = ByteArray(i8.length) { i8[it] }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }
} catch (e: Exception) {
    null
}

/** Fetches every character's art in the background; tokens fill in live. */
private fun startIconPrefetch() {
    for (character in WebApp.gameData.characters) {
        appScope.launch {
            for (ext in listOf("png", "webp")) {
                val bitmap = fetchBitmap("icons/${character.id}.$ext")
                if (bitmap != null) {
                    IconStore.ready[character.id] = bitmap
                    return@launch
                }
                // Missing art falls back to the monogram token.
            }
        }
    }
}

/** One decoded launch intent: a shortcut, or a shared script link. */
private class LaunchIntent(val route: String?, val scriptId: String?)

/**
 * Reads `?resume=1` / `?new=1` (manifest shortcuts) and `?script=…` (the
 * share target), imports a shared script, then clears the query string so a
 * reload does not repeat the intent.
 */
private fun consumeLaunchIntent(viewModel: GameViewModel, hasGame: Boolean): LaunchIntent {
    val search = jsSearch()
    if (search.isBlank()) return LaunchIntent(null, null)
    val params = search.removePrefix("?").split("&")
        .mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to decodeComponent(pair.substring(i + 1))
        }
        .toMap()
    jsClearSearch()

    val shared = params["script"]?.takeIf { it.isNotBlank() }
    var scriptId: String? = null
    if (shared != null) {
        // The share sheet hands over either a script-tool link or raw JSON;
        // the view model's importer already understands both.
        if (viewModel.importScript(shared) == null) {
            scriptId = runCatching {
                val jsonText =
                    if (ScriptLink.isLink(shared)) ScriptLink.decode(shared) ?: shared else shared
                ScriptParser.parse(jsonText).id
            }.getOrNull()
        }
    }
    val route = when {
        scriptId != null -> "setup"
        params["new"] == "1" -> "setup"
        params["resume"] == "1" && hasGame -> "game"
        else -> null
    }
    return LaunchIntent(route, scriptId)
}

private fun decodeComponent(raw: String): String =
    runCatching { decodeUriJs(raw.replace('+', ' ')) }.getOrDefault(raw)

private fun decodeUriJs(value: String): String = js("decodeURIComponent(value)")

/**
 * Route state stands in for Android's NavHost; same screens, same flow.
 */
@Composable
private fun WebRoot() {
    val viewModel = remember { GameViewModel() }
    val game by viewModel.game.collectAsState()
    val notes by viewModel.notes.collectAsState()
    var route by rememberSaveable { mutableStateOf("home") }
    var sharedScriptId by rememberSaveable { mutableStateOf<String?>(null) }

    // The table's phone must not sleep — hoisted to the app root so the setup
    // wizard and hand-out mode are covered too (setup-and-home #41).
    KeepScreenOn()
    RequestWakeLockOnResume()

    // A manifest shortcut or a shared script link, consumed exactly once.
    LaunchedEffect(Unit) {
        val intent = consumeLaunchIntent(viewModel, viewModel.game.value != null)
        intent.scriptId?.let { sharedScriptId = it }
        intent.route?.let { route = it }
    }

    // The keyboard inset is polled on the frame clock but only WRITTEN when it
    // changes, so a still keyboard costs no recompositions.
    var keyboardInset by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        while (true) {
            val measured = jsKeyboardInset()
            if (measured != keyboardInset) keyboardInset = measured
            delay(100)
        }
    }
    LaunchedEffect(Unit) {
        // The browser answers storage.persist() asynchronously.
        repeat(10) {
            delay(500)
            WebStore.refreshPersistence()
        }
    }

    GrimoireTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The background paints edge-to-edge (under notch and home
            // indicator); the content stays out of both, and out from under
            // the software keyboard.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = jsSafeTop().dp,
                        bottom = (jsSafeBottom() + keyboardInset).dp,
                    ),
            ) {
                WebRoutes(route, viewModel, game, notes, sharedScriptId) { route = it }
                ShellBanners(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * The three things the browser can tell the storyteller that the app cannot
 * work out for itself: saving is broken, a new build is ready, and this
 * origin's storage is evictable (setup-and-home #39, #40).
 */
@Composable
private fun ShellBanners(modifier: Modifier = Modifier) {
    val saveFailed by WebStore.saveFailed.collectAsState()
    val persisted by WebStore.storagePersisted.collectAsState()
    var updateReady by remember { mutableStateOf(false) }
    var hintDismissed by rememberSaveable { mutableStateOf(false) }
    val standalone = remember { runCatching { jsStandalone() }.getOrDefault(true) }

    LaunchedEffect(Unit) {
        while (!updateReady) {
            updateReady = runCatching { jsUpdateReady() }.getOrDefault(false)
            delay(2000)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (saveFailed) {
            Banner(
                background = BloodRed,
                text = "Not saving — browser storage is full. Copy the game log now.",
            )
        }
        if (updateReady) {
            Banner(
                background = Twilight,
                text = "New version ready.",
                actionLabel = "Reload",
                onAction = { jsApplyUpdate() },
            )
        }
        if (!persisted && !standalone && !hintDismissed) {
            Banner(
                background = Twilight,
                text = "Add to Home Screen so iOS keeps your saved game.",
                actionLabel = "Got it",
                onAction = { hintDismissed = true },
            )
        }
    }
}

@Composable
private fun Banner(
    background: Color,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = Parchment,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = AgedGold) }
        }
    }
}

@Composable
private fun WebRoutes(
    route: String,
    viewModel: GameViewModel,
    game: com.clocktower.engine.GameState?,
    notes: com.clocktower.engine.NotesState?,
    sharedScriptId: String?,
    onRoute: (String) -> Unit,
) {
    when (route) {
        "setup" -> SetupScreen(
            viewModel = viewModel,
            onGameStarted = { onRoute("game") },
            onBack = { onRoute("home") },
            preselectedScriptId = sharedScriptId,
        )
        "game" -> {
            val state = game
            if (state == null) {
                Home(viewModel, game, notes, onRoute)
            } else {
                GameShell(viewModel = viewModel, state = state, onExit = { onRoute("home") })
            }
        }
        "library" -> LibraryScreen(viewModel = viewModel, onBack = { onRoute("home") })
        "notes_setup" -> NotesSetupScreen(
            viewModel = viewModel,
            onStarted = { onRoute("notes") },
            onBack = { onRoute("home") },
        )
        "notes" -> {
            val state = notes
            if (state == null) {
                Home(viewModel, game, notes, onRoute)
            } else {
                NotesScreen(viewModel = viewModel, state = state, onExit = { onRoute("home") })
            }
        }
        else -> Home(viewModel, game, notes, onRoute)
    }
}

@Composable
private fun Home(
    viewModel: GameViewModel,
    game: com.clocktower.engine.GameState?,
    notes: com.clocktower.engine.NotesState?,
    onRoute: (String) -> Unit,
) {
    val archived by viewModel.archivedGames.collectAsState()
    HomeScreen(
        game = game,
        notes = notes,
        onResume = { onRoute("game") },
        onNewGame = { onRoute("setup") },
        onResumeNotes = { onRoute("notes") },
        onNewNotes = { onRoute("notes_setup") },
        onLibrary = { onRoute("library") },
        onEndGame = { viewModel.endGame() },
        onEndNotes = { viewModel.endNotes() },
        buildLabel = remember { runCatching { jsBuildLabel().take(7) }.getOrDefault("") },
        archivedGames = archived,
        onOpenArchived = {
            viewModel.resumeArchived(it)
            onRoute("game")
        },
        onDiscardArchived = { viewModel.discardArchived(it) },
        lookup = viewModel::characterById,
    )
}
