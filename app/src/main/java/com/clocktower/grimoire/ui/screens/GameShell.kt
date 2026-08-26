package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.clocktower.grimoire.ui.components.DiscussionTimer
import com.clocktower.grimoire.ui.components.PrivacyCover
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold

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
    var showLog by rememberSaveable { mutableStateOf(false) }
    var showFabled by rememberSaveable { mutableStateOf(false) }
    var showJinxes by rememberSaveable { mutableStateOf(false) }
    var showReorder by rememberSaveable { mutableStateOf(false) }
    var revealGoodWins by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val phaseGuards = rememberPhaseGuards()
    var dismissedAdvisory by rememberSaveable { mutableStateOf("") }
    var showRevealFlow by rememberSaveable { mutableStateOf(false) }
    var grimoireLocked by rememberSaveable { mutableStateOf(false) }
    var nightScrim by rememberSaveable { mutableStateOf(false) }

    // The table's phone must not sleep mid-game.
    com.clocktower.grimoire.ui.platform.KeepScreenOn()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val stateHolder = rememberSaveableStateHolder()

    val phaseLabel = when (state.phase) {
        Phase.SETUP -> "Setup"
        Phase.NIGHT -> if (state.cycle == 1) "First Night" else "Night ${state.cycle}"
        Phase.DAY -> "Day ${state.cycle}"
    }
    val phaseActionLabel = when (state.phase) {
        Phase.SETUP -> "Begin night"
        Phase.NIGHT -> "Dawn"
        Phase.DAY -> "Dusk"
    }
    // Phase logic lives in PhaseFlow.kt (WP0 extraction; WP6 owns it next).
    val onPhaseButton: () -> Unit = {
        requestPhaseAdvance(viewModel, state, phaseGuards)?.let { tab = it }
    }

    Scaffold(
        topBar = {
            BoxWithConstraints {
                val compactTopBar = maxWidth < 520.dp
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
                    IconButton(onClick = { grimoireLocked = true }) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide the grimoire")
                    }
                    IconButton(enabled = canUndo, onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(enabled = canRedo, onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    if (compactTopBar) {
                        FilledTonalIconButton(onClick = onPhaseButton) {
                            Icon(
                                imageVector = if (state.phase == Phase.NIGHT) {
                                    Icons.Filled.WbSunny
                                } else {
                                    Icons.Filled.DarkMode
                                },
                                contentDescription = phaseActionLabel,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onPhaseButton,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(phaseActionLabel)
                        }
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
                            text = { Text("Reveal characters to players…") },
                            onClick = { showMenu = false; showRevealFlow = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (nightScrim) "Night dimming off" else "Night dimming on") },
                            onClick = { showMenu = false; nightScrim = !nightScrim },
                        )
                        DropdownMenuItem(
                            text = { Text("Fabled…") },
                            onClick = { showMenu = false; showFabled = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Jinxes in play") },
                            onClick = { showMenu = false; showJinxes = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Game log") },
                            onClick = { showMenu = false; showLog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Reorder seats") },
                            onClick = { showMenu = false; showReorder = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add seat (traveller joins)") },
                            onClick = { showMenu = false; showAddSeat = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Declare good victory") },
                            onClick = { showMenu = false; revealGoodWins = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Declare evil victory") },
                            onClick = { showMenu = false; revealGoodWins = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Back to home") },
                            onClick = { showMenu = false; onExit() },
                        )
                    }
                },
                )
            }
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
                    GameTab.GRIMOIRE -> GrimoireScreen(
                        viewModel, state,
                        onOpenBluffs = { showBluffs = true },
                        onOpenFabled = { showFabled = true },
                    ) { openSeat = it }
                    GameTab.NIGHT -> NightScreen(
                        viewModel = viewModel,
                        state = state,
                        onOpenShowTool = { showCardTool = true },
                    )
                    GameTab.DAY -> DayScreen(viewModel, state)
                    // WP11: passing the live state marks what is in play and
                    // in which seat on the Script tab.
                    GameTab.REFERENCE -> ReferenceScreen(viewModel, state.script, state)
                }
            }
            if (tab == GameTab.GRIMOIRE || tab == GameTab.DAY) {
                DiscussionTimer(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
            if (nightScrim && state.phase == Phase.NIGHT) {
                // Red-shifted dimming preserves the room's darkness; plain
                // Box without pointer handling lets touches pass through.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66300000)),
                )
            }
        }
    }
    if (showRevealFlow) {
        RevealFlow(
            viewModel, state,
            onDone = {
                showRevealFlow = false
                // The phone just went around the circle — shield the
                // grimoire until the storyteller deliberately reopens it.
                grimoireLocked = true
            },
        )
    }
    if (grimoireLocked) {
        PrivacyCover(onUnlock = { grimoireLocked = false })
    }
    // Setup identity prompts live in GameExtras.kt (WP0 extraction; WP11 owns them next).
    SetupIdentityPrompts(viewModel, state)

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
    if (showLog) GameLogDialog(state, onDismiss = { showLog = false })
    if (showFabled) FabledSheet(viewModel, state, onDismiss = { showFabled = false })
    if (showJinxes) ActiveJinxesDialog(viewModel, state, onDismiss = { showJinxes = false })
    if (showReorder) ReorderSeatsDialog(viewModel, state, onDismiss = { showReorder = false })

    // Advisory when the grimoire looks like a finished game.
    val advisory = remember(state.players, state.phase) {
        WinCheck.check(state, viewModel::characterById)
    }
    if (advisory != null && advisory.reason != dismissedAdvisory && revealGoodWins == null) {
        WinAdvisoryDialog(
            advisory = advisory,
            onDeclare = { revealGoodWins = it },
            onMastermindDay = {
                dismissedAdvisory = advisory.reason
                viewModel.update { it.copy(mastermindDayActive = true) }
            },
            onDismiss = { dismissedAdvisory = advisory.reason },
        )
    }
    if (state.mastermindDayActive && revealGoodWins == null) {
        // Persistent banner while the extra day plays out.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "MASTERMIND DAY — whoever is executed, their team loses",
                color = AgedGold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(Color(0xE61E1630))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    revealGoodWins?.let { goodWins ->
        RevealSheet(
            viewModel = viewModel,
            state = state,
            goodWins = goodWins,
            onNewGame = {
                revealGoodWins = null
                viewModel.endGame()
                onExit()
            },
            onDismiss = { revealGoodWins = null },
        )
    }
    PhaseGuardDialogs(viewModel, state, phaseGuards) { tab = it }
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
