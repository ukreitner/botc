package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val scripts = builtIn + imported
    var index by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, (scripts.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack) { Text("← Home") }
            Text("Library", style = MaterialTheme.typography.headlineMedium)
        }
        PrimaryTabRow(selectedTabIndex = safeIndex) {
            scripts.forEachIndexed { i, script ->
                Tab(
                    selected = safeIndex == i,
                    onClick = { index = i },
                    text = { Text(script.name, maxLines = 1) },
                )
            }
        }
        if (scripts.isNotEmpty()) {
            ReferenceScreen(viewModel, scripts[safeIndex])
        }
    }
}
