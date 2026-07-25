package com.clocktower.grimoire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.screens.GameShell
import com.clocktower.grimoire.ui.screens.HomeScreen
import com.clocktower.grimoire.ui.screens.LibraryScreen
import com.clocktower.grimoire.ui.screens.SetupScreen
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.GrimoireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrimoireTheme {
                Surface(Modifier.fillMaxSize()) {
                    GrimoireAppRoot()
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val GAME = "game"
    const val LIBRARY = "library"
}

@Composable
private fun GrimoireAppRoot(viewModel: GameViewModel = viewModel()) {
    val nav = rememberNavController()
    val game by viewModel.game.collectAsState()
    val ready by viewModel.ready.collectAsState()

    // Hold rendering until the saved game has been read, so a process-death
    // restore doesn't flash the "no game" fallback before the grimoire loads.
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Clocktower Grimoire", style = MaterialTheme.typography.headlineMedium, color = AgedGold)
        }
        return
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                game = game,
                onResume = { nav.navigate(Routes.GAME) },
                onNewGame = { nav.navigate(Routes.SETUP) },
                onLibrary = { nav.navigate(Routes.LIBRARY) },
                onEndGame = { viewModel.endGame() },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                viewModel = viewModel,
                onGameStarted = {
                    nav.navigate(Routes.GAME) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GAME) {
            val state = game
            if (state == null) {
                // Game ended elsewhere; fall back home.
                HomeScreen(
                    game = null,
                    onResume = {},
                    onNewGame = { nav.navigate(Routes.SETUP) },
                    onLibrary = { nav.navigate(Routes.LIBRARY) },
                    onEndGame = {},
                )
            } else {
                GameShell(
                    viewModel = viewModel,
                    state = state,
                    onExit = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
