package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.FadedInk

/** Landing screen: resume, new game, or browse the library. */
@Composable
fun HomeScreen(
    game: GameState?,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onLibrary: () -> Unit,
    onEndGame: () -> Unit,
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("☾", style = MaterialTheme.typography.displayMedium, color = FadedInk)
        Text(
            "Clocktower",
            style = MaterialTheme.typography.displayLarge,
            color = AgedGold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Grimoire",
            style = MaterialTheme.typography.displayMedium,
            color = FadedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A storyteller's companion for Blood on the Clocktower",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "❦",
            style = MaterialTheme.typography.titleLarge,
            color = AgedGold.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(40.dp))

        if (game != null) {
            FilledTonalButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text("Resume game", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${game.script.name} · ${game.players.size} players · " +
                            when (game.phase) {
                                Phase.SETUP -> "setting up"
                                Phase.NIGHT -> if (game.cycle == 1) "first night" else "night ${game.cycle}"
                                Phase.DAY -> "day ${game.cycle}"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        ElevatedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
            Text("New game", modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("Character library & night order", modifier = Modifier.padding(vertical = 6.dp))
        }
        if (game != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { confirmEnd = true }) {
                Text("End current game", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End game?") },
            text = { Text("This clears the saved grimoire. There is no undo across games.") },
            confirmButton = {
                FilledTonalButton(onClick = { confirmEnd = false; onEndGame() }) { Text("End game") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep playing") } },
        )
    }
}
