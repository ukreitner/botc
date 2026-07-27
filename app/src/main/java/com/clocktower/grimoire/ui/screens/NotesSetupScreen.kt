package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Script
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold

/**
 * Setup for player-notes mode: pick the script being played, list the
 * seats in table order (starting anywhere), and go. Everything is
 * editable later, so this stays deliberately light.
 */
@Composable
fun NotesSetupScreen(
    viewModel: GameViewModel,
    onStarted: () -> Unit,
    onBack: () -> Unit,
) {
    val imported by viewModel.importedScripts.collectAsState()
    val scripts = remember(imported) { viewModel.gameData.builtInScripts() + imported }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    val names = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { List(8) { "" }.toMutableStateList() }

    val chosenScript: Script? = scripts.find { it.id == chosen }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.width(8.dp))
                Text("Player notes", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
            }
            Text(
                "Your private grimoire for a game someone else is running: " +
                    "track claims, suspicions and who's pointing at whom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("1 · What script are you playing?", style = MaterialTheme.typography.titleMedium)
        }
        items(scripts, key = { it.id }) { script ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { chosen = script.id },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            script.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (chosen == script.id) AgedGold else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${script.characterIds.size + script.customCharacters.size} characters" +
                                (script.author.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (chosen == script.id) {
                        Text("✓", color = AgedGold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Import script (paste link or JSON)")
            }
        }
        item {
            Text(
                "2 · Who's at the table? (clockwise, start anywhere)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        itemsIndexed(names) { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { names[index] = it },
                    placeholder = { Text("Seat ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { names.removeAt(index) }, enabled = names.size > 1) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove seat ${index + 1}")
                }
            }
        }
        item {
            OutlinedButton(onClick = { names.add("") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add seat")
            }
        }
        item {
            FilledTonalButton(
                enabled = chosenScript != null,
                onClick = {
                    chosenScript?.let { script ->
                        viewModel.startNotes(script, names.toList())
                        onStarted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text(
                    if (chosenScript == null) {
                        "Pick a script to start"
                    } else {
                        "Start notes · ${names.size} seats"
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }

    if (showImport) {
        ImportScriptDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val error = viewModel.importScript(text)
                if (error == null) showImport = false
                error
            },
        )
    }
}
