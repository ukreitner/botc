package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.GameViewModel

/**
 * Out-of-game reference: browse any edition's characters, night order and
 * jinxes without starting a game.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val builtIn = remember { viewModel.gameData.builtInScripts() }
    val imported by viewModel.importedScripts.collectAsState()
    // Newly imported scripts sort to the top of the imported group, so the
    // one you just added is the one you land on (setup-and-home #36).
    val scripts = builtIn + imported.reversed()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    val safeIndex = index.coerceIn(0, (scripts.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextButton(onClick = onBack) { Text("< Home") }
            Text(
                "Library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = { showImport = true }) { Text("Import") }
        }
        if (scripts.isNotEmpty()) {
            // Scrollable: a dozen imported scripts no longer squeeze the
            // built-ins into unreadable slivers (setup-and-home #29).
            PrimaryScrollableTabRow(selectedTabIndex = safeIndex, edgePadding = 12.dp) {
                scripts.forEachIndexed { i, script ->
                    Tab(
                        selected = safeIndex == i,
                        onClick = { index = i },
                        text = { Text(script.name, maxLines = 1) },
                    )
                }
            }
            ReferenceScreen(viewModel, scripts[safeIndex])
        }
    }

    if (showImport) {
        ImportScriptDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val error = viewModel.importScript(text)
                if (error == null) {
                    showImport = false
                    index = builtIn.size
                }
                error
            },
        )
    }
}
