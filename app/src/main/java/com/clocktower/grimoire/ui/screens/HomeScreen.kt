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
import com.clocktower.engine.NotesState
import com.clocktower.engine.Phase
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.FadedInk

/** Landing screen: resume, new game, or browse the library. */
@Composable
fun HomeScreen(
    game: GameState?,
    notes: NotesState? = null,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onResumeNotes: () -> Unit = {},
    onNewNotes: () -> Unit = {},
    onLibrary: () -> Unit,
    onEndGame: () -> Unit,
    onEndNotes: () -> Unit = {},
    // Short build id shown as a footer, so it's obvious which build is
    // installed (handy right after an in-app update). Hidden when blank.
    buildLabel: String = "",
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmEndNotes by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
            Text("New game (storyteller)", modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))

        if (notes != null) {
            FilledTonalButton(onClick = onResumeNotes, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text("Resume player notes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${notes.script.name} · ${notes.seats.size} seats · day ${notes.day}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            ElevatedButton(onClick = onNewNotes, modifier = Modifier.fillMaxWidth()) {
                Text("Take notes (player)", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("Character library & night order", modifier = Modifier.padding(vertical = 6.dp))
        }
        if (game != null || notes != null) {
            Spacer(Modifier.height(24.dp))
        }
        if (game != null) {
            TextButton(onClick = { confirmEnd = true }) {
                Text("End current game", color = MaterialTheme.colorScheme.error)
            }
        }
        if (notes != null) {
            TextButton(onClick = { confirmEndNotes = true }) {
                Text("End notes session", color = MaterialTheme.colorScheme.error)
            }
        }
        if (buildLabel.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "build $buildLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
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
    if (confirmEndNotes) {
        AlertDialog(
            onDismissRequest = { confirmEndNotes = false },
            title = { Text("End notes session?") },
            text = { Text("This clears your player notes. There is no undo across sessions.") },
            confirmButton = {
                FilledTonalButton(onClick = { confirmEndNotes = false; onEndNotes() }) { Text("End notes") }
            },
            dismissButton = { TextButton(onClick = { confirmEndNotes = false }) { Text("Keep them") } },
        )
    }
}
