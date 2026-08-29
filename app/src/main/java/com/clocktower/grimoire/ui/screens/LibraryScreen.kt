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
import com.clocktower.grimoire.ui.theme.AgedGold

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
    // A2-7: imports come FIRST, newest first — the same order the setup screen
    // uses (A-19). Appended after the three built-ins, the script you had just
    // imported sat off the right-hand edge of the tab row with its tab clipped
    // by the screen, which is the opposite of where you want to land.
    val scripts = imported.asReversed() + builtIn
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    // A2-7: the Library's Import was the only import path with no confirmation
    // of any kind — the selected tab changed and that was the whole feedback.
    var awaitingImport by rememberSaveable { mutableStateOf(false) }
    var importNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val safeIndex = index.coerceIn(0, (scripts.size - 1).coerceAtLeast(0))

    androidx.compose.runtime.LaunchedEffect(imported) {
        // The store appends what it just wrote, so while an import is in flight
        // the last entry IS it — and imports sort first, so it is tab 0.
        val added = imported.lastOrNull()
        if (awaitingImport && added != null) {
            importNotice = "Imported \"${added.name}\" — ${added.characterIds.size} characters."
            index = 0
            awaitingImport = false
        }
    }

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
        importNotice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = AgedGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
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
                    awaitingImport = true
                }
                error
            },
        )
    }
}
