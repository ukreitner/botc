package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.clocktower.grimoire.ui.components.DiscussionTimer
import com.clocktower.grimoire.ui.components.FullScreenShow
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.ShowToolSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold

private enum class GameTab { GRIMOIRE, NIGHT, DAY, REFERENCE }

/**
 * The in-game scaffold: grimoire / night / day / reference tabs, phase
 * control, undo, bluffs and storyteller notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameShell(
    viewModel: GameViewModel,
    state: GameState,
    onExit: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(GameTab.GRIMOIRE) }
    var openSeat by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBluffs by rememberSaveable { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showAddSeat by rememberSaveable { mutableStateOf(false) }
    var showCardTool by rememberSaveable { mutableStateOf(false) }
    var activeCard by remember { mutableStateOf<ShowCard?>(null) }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val stateHolder = rememberSaveableStateHolder()
    var lastAdvanceAt by remember { mutableLongStateOf(0L) }

    val phaseLabel = when (state.phase) {
        Phase.SETUP -> "Setup"
        Phase.NIGHT -> if (state.cycle == 1) "First Night" else "Night ${state.cycle}"
        Phase.DAY -> "Day ${state.cycle}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.script.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$phaseLabel · ${state.alivePlayers.size}/${state.players.size} alive",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgedGold,
                        )
                    }
                },
                actions = {
                    IconButton(enabled = canUndo, onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(enabled = canRedo, onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    FilledTonalButton(
                        onClick = {
                            // Debounce: an accidental double tap must not skip
                            // a whole phase.
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastAdvanceAt < 800) return@FilledTonalButton
                            lastAdvanceAt = nowMs
                            viewModel.advancePhase()
                            // Jump to the tab that matters for the new phase.
                            tab = when (state.phase) {
                                Phase.SETUP, Phase.DAY -> GameTab.NIGHT
                                Phase.NIGHT -> GameTab.DAY
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Text(
                            when (state.phase) {
                                Phase.SETUP -> "Begin night"
                                Phase.NIGHT -> "Dawn"
                                Phase.DAY -> "Dusk"
                            },
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Demon bluffs") },
                            onClick = { showMenu = false; showBluffs = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Storyteller notes") },
                            onClick = { showMenu = false; showNotes = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Show a card…") },
                            onClick = { showMenu = false; showCardTool = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add seat (traveller joins)") },
                            onClick = { showMenu = false; showAddSeat = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Back to home") },
                            onClick = { showMenu = false; onExit() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                GameTabItem(tab == GameTab.GRIMOIRE, { tab = GameTab.GRIMOIRE }, "Grimoire") {
                    Icon(Icons.Filled.Groups, contentDescription = null)
                }
                GameTabItem(tab == GameTab.NIGHT, { tab = GameTab.NIGHT }, "Night") {
                    Icon(Icons.Filled.DarkMode, contentDescription = null)
                }
                GameTabItem(tab == GameTab.DAY, { tab = GameTab.DAY }, "Day") {
                    Icon(Icons.Filled.WbSunny, contentDescription = null)
                }
                GameTabItem(tab == GameTab.REFERENCE, { tab = GameTab.REFERENCE }, "Script") {
                    Icon(Icons.Filled.AutoStories, contentDescription = null)
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // SaveableStateProvider keeps each tab's in-progress state (an
            // unrecorded vote tally, scroll positions) across tab switches.
            stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    GameTab.GRIMOIRE -> GrimoireScreen(viewModel, state) { openSeat = it }
                    GameTab.NIGHT -> NightScreen(viewModel, state)
                    GameTab.DAY -> DayScreen(viewModel, state)
                    GameTab.REFERENCE -> ReferenceScreen(viewModel, state.script)
                }
            }
            if (tab == GameTab.GRIMOIRE || tab == GameTab.DAY) {
                DiscussionTimer(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
        }
    }

    openSeat?.let { seatId ->
        SeatSheet(
            viewModel = viewModel,
            state = state,
            playerId = seatId,
            onDismiss = { openSeat = null },
        )
    }
    if (showBluffs) {
        BluffsSheet(viewModel, state, onDismiss = { showBluffs = false })
    }
    if (showCardTool) {
        ShowToolSheet(
            viewModel = viewModel,
            state = state,
            onShow = { activeCard = it },
            onDismiss = { showCardTool = false },
        )
    }
    activeCard?.let { card ->
        FullScreenShow(card = card, viewModel = viewModel, onDismiss = { activeCard = null })
    }
    if (showAddSeat) {
        var seatName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSeat = false },
            title = { Text("Add seat") },
            text = {
                OutlinedTextField(
                    value = seatName,
                    onValueChange = { seatName = it },
                    label = { Text("Player name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    viewModel.addSeat(seatName)
                    showAddSeat = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddSeat = false }) { Text("Cancel") } },
        )
    }
    if (showNotes) {
        var notes by rememberSaveable { mutableStateOf(state.storytellerNotes) }
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text("Storyteller notes") },
            text = {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    viewModel.setStorytellerNotes(notes)
                    showNotes = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNotes = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RowScope.GameTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label) },
    )
}
