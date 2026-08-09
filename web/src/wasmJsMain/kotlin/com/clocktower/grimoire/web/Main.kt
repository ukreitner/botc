package com.clocktower.grimoire.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import com.clocktower.engine.BotcResources
import com.clocktower.engine.GameData
import com.clocktower.grimoire.WebApp
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.IconStore
import com.clocktower.grimoire.ui.screens.GameShell
import com.clocktower.grimoire.ui.screens.HomeScreen
import com.clocktower.grimoire.ui.screens.LibraryScreen
import com.clocktower.grimoire.ui.screens.NotesScreen
import com.clocktower.grimoire.ui.screens.NotesSetupScreen
import com.clocktower.grimoire.ui.screens.SetupScreen
import com.clocktower.grimoire.ui.theme.GrimoireTheme
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
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
        startIconPrefetch()
        kotlinx.browser.document.getElementById("boot")?.remove()
        CanvasBasedWindow(canvasElementId = "compose", title = "Clocktower Grimoire") {
            WebRoot()
        }
    }
}

private suspend fun fetchText(url: String): String {
    val response = window.fetch(url).await<Response>()
    if (!response.ok) error("fetch $url -> ${response.status}")
    return response.text().await<JsString>().toString()
}

/** Fetches every character's art in the background; tokens fill in live. */
private fun startIconPrefetch() {
    for (character in WebApp.gameData.characters) {
        appScope.launch {
            for (ext in listOf("png", "webp")) {
                try {
                    val response = window.fetch("icons/${character.id}.$ext").await<Response>()
                    if (!response.ok) continue
                    val buffer = response.arrayBuffer().await<ArrayBuffer>()
                    val i8 = Int8Array(buffer)
                    val bytes = ByteArray(i8.length) { i8[it] }
                    IconStore.ready[character.id] =
                        Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    return@launch
                } catch (e: Exception) {
                    // Missing art falls back to the monogram token.
                }
            }
        }
    }
}

/**
 * Route state stands in for Android's NavHost; same screens, same flow.
 */
@Composable
private fun WebRoot() {
    val viewModel = remember { GameViewModel() }
    val game by viewModel.game.collectAsState()
    val notes by viewModel.notes.collectAsState()
    var route by rememberSaveable { mutableStateOf("home") }

    GrimoireTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The background paints edge-to-edge (under notch and home
            // indicator); the content stays out of both.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = jsSafeTop().dp,
                        bottom = jsSafeBottom().dp,
                    ),
            ) {
                WebRoutes(route, viewModel, game, notes) { route = it }
            }
        }
    }
}

@Composable
private fun WebRoutes(
    route: String,
    viewModel: GameViewModel,
    game: com.clocktower.engine.GameState?,
    notes: com.clocktower.engine.NotesState?,
    onRoute: (String) -> Unit,
) {
    when (route) {
        "setup" -> SetupScreen(
            viewModel = viewModel,
            onGameStarted = { onRoute("game") },
            onBack = { onRoute("home") },
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
    )
}
