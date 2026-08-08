package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Character
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
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
    var showLog by rememberSaveable { mutableStateOf(false) }
    var showFabled by rememberSaveable { mutableStateOf(false) }
    var showJinxes by rememberSaveable { mutableStateOf(false) }
    var showReorder by rememberSaveable { mutableStateOf(false) }
    var revealGoodWins by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var setupGuardIssues by rememberSaveable { mutableStateOf(listOf<String>()) }
    var duskGuard by rememberSaveable { mutableStateOf(false) }
    var unfinishedNightSteps by rememberSaveable { mutableStateOf(listOf<String>()) }
    var dismissedAdvisory by rememberSaveable { mutableStateOf("") }
    var showRevealFlow by rememberSaveable { mutableStateOf(false) }
    var grimoireLocked by rememberSaveable { mutableStateOf(false) }
    var nightScrim by rememberSaveable { mutableStateOf(false) }
    var drunkPromptDone by rememberSaveable { mutableStateOf(false) }
    var lunaticPromptDone by rememberSaveable { mutableStateOf(false) }
    var marionettePromptDone by rememberSaveable { mutableStateOf(false) }

    // The table's phone must not sleep mid-game.
    com.clocktower.grimoire.ui.platform.KeepScreenOn()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val stateHolder = rememberSaveableStateHolder()
    var lastAdvanceAt by remember { mutableLongStateOf(0L) }

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
    val requestPhaseAdvance: () -> Unit = advance@{
        // Debounce: an accidental double tap must not skip a whole phase.
        val nowMs = com.clocktower.engine.Time.epochMillis()
        if (nowMs - lastAdvanceAt < 800) return@advance
        lastAdvanceAt = nowMs
        // Setup guard: empty/manual games must meet the same adjusted team
        // distribution as the bag builder before first night can begin.
        if (state.phase == Phase.SETUP) {
            val issues = GameActions.validateSetupState(state, viewModel::characterById)
            if (issues.isNotEmpty()) {
                setupGuardIssues = issues
                tab = GameTab.GRIMOIRE
                return@advance
            }
        }
        // Dusk guard: someone is on the block and hasn't died.
        val onBlock = GameActions.aboutToDie(state)?.let { state.player(it) }
        if (state.phase == Phase.DAY && onBlock?.alive == true) {
            duskGuard = true
            return@advance
        }
        if (state.phase == Phase.NIGHT) {
            val nightSteps = if (state.cycle == 1) {
                viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
            } else {
                viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
            }
            val unfinished = nightSteps
                .filterNot { it.id in state.nightStepsDone }
                .map { it.title }
            if (unfinished.isNotEmpty()) {
                unfinishedNightSteps = unfinished
                tab = GameTab.NIGHT
                return@advance
            }
        }
        viewModel.advancePhase()
        // Jump to the tab that matters for the new phase.
        tab = when (state.phase) {
            Phase.SETUP, Phase.DAY -> GameTab.NIGHT
            Phase.NIGHT -> GameTab.DAY
        }
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
                        FilledTonalIconButton(onClick = requestPhaseAdvance) {
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
                            onClick = requestPhaseAdvance,
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
    // The Fortune Teller needs a red herring before night one.
    var herringPromptDone by rememberSaveable { mutableStateOf(false) }
    val ftSeat = state.players.find { it.characterId == "fortuneteller" }
    val waitingForHerring = !herringPromptDone && state.phase == Phase.SETUP && ftSeat != null &&
        state.players.none { p -> p.reminders.any { it.label.equals("Red herring", true) } }
    if (waitingForHerring) {
        AlertDialog(
            onDismissRequest = { herringPromptDone = true },
            title = { Text("Fortune Teller red herring") },
            text = {
                Column {
                    Text("Pick the good player who registers as the Demon to the Fortune Teller:")
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        val candidates = state.players.filter { !it.isEvil(viewModel::characterById) }
                        items(candidates.size) { i ->
                            val p = candidates[i]
                            TextButton(onClick = {
                                viewModel.addReminder(
                                    p.id,
                                    com.clocktower.engine.PlacedReminder("fortuneteller", "Red herring"),
                                )
                                herringPromptDone = true
                            }) { Text(p.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { herringPromptDone = true }) { Text("Later") } },
        )
    }
    // The Drunk needs a believed-character before night one.
    val drunkSeat = state.players.find { it.characterId == "drunk" }
    val waitingForDrunk = !drunkPromptDone && !waitingForHerring &&
        state.phase == Phase.SETUP && drunkSeat != null && drunkSeat.shownCharacterId == null
    if (waitingForDrunk) {
        checkNotNull(drunkSeat)
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        val options = viewModel.gameData.resolve(state.script)
            .filter { it.team == com.clocktower.engine.Team.TOWNSFOLK && it.id !in inPlay }
        HiddenIdentityDialog(
            title = "The Drunk is in play",
            explanation = "${drunkSeat.name} is the Drunk. Which Townsfolk token do they see?",
            options = options,
            onPick = { character ->
                viewModel.update { current ->
                    var next = GameActions.setShownCharacter(current, drunkSeat.id, character.id)
                    if (next.player(drunkSeat.id)?.reminders?.none {
                            it.sourceId == "drunk" && it.label == "Is the Drunk"
                        } == true
                    ) {
                        next = GameActions.addReminder(
                            next,
                            drunkSeat.id,
                            com.clocktower.engine.PlacedReminder("drunk", "Is the Drunk"),
                        )
                    }
                    GameActions.setNote(
                        next,
                        drunkSeat.id,
                        "Believes they are the ${character.name}",
                    )
                }
                drunkPromptDone = true
            },
            onLater = { drunkPromptDone = true },
        )
    }

    // The Lunatic sees a Demon token but keeps the Lunatic's real rules.
    val lunaticSeat = state.players.find { it.characterId == "lunatic" }
    val waitingForLunatic = !lunaticPromptDone && !waitingForHerring && !waitingForDrunk &&
        state.phase == Phase.SETUP && lunaticSeat != null && lunaticSeat.shownCharacterId == null
    if (waitingForLunatic) {
        checkNotNull(lunaticSeat)
        val options = viewModel.gameData.resolve(state.script)
            .filter { it.team == com.clocktower.engine.Team.DEMON }
        HiddenIdentityDialog(
            title = "The Lunatic is in play",
            explanation = "${lunaticSeat.name} is the Lunatic. Which Demon token do they see?",
            options = options,
            onPick = { character ->
                viewModel.update { current ->
                    val next = GameActions.setShownCharacter(current, lunaticSeat.id, character.id)
                    GameActions.setNote(
                        next,
                        lunaticSeat.id,
                        "Believes they are the ${character.name}",
                    )
                }
                lunaticPromptDone = true
            },
            onLater = { lunaticPromptDone = true },
        )
    }

    // The Marionette sees a good token and wakes as that apparent role.
    val marionetteSeat = state.players.find { it.characterId == "marionette" }
    val waitingForMarionette = !marionettePromptDone && !waitingForHerring &&
        !waitingForDrunk && !waitingForLunatic && state.phase == Phase.SETUP &&
        marionetteSeat != null && marionetteSeat.shownCharacterId == null
    if (waitingForMarionette) {
        checkNotNull(marionetteSeat)
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        val options = viewModel.gameData.resolve(state.script)
            .filter { !it.team.isEvil && it.team.isTownResident && it.id !in inPlay }
        HiddenIdentityDialog(
            title = "The Marionette is in play",
            explanation = "${marionetteSeat.name} is the Marionette. Which good token do they think they are?",
            options = options,
            onPick = { character ->
                viewModel.update { current ->
                    var next = GameActions.setShownCharacter(current, marionetteSeat.id, character.id)
                    if (next.player(marionetteSeat.id)?.reminders?.none {
                            it.sourceId == "marionette" && it.label == "Is the Marionette"
                        } == true
                    ) {
                        next = GameActions.addReminder(
                            next,
                            marionetteSeat.id,
                            com.clocktower.engine.PlacedReminder("marionette", "Is the Marionette"),
                        )
                    }
                    GameActions.setNote(
                        next,
                        marionetteSeat.id,
                        "Believes they are the ${character.name}",
                    )
                }
                marionettePromptDone = true
            },
            onLater = { marionettePromptDone = true },
        )
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
                "☠ MASTERMIND DAY — whoever is executed, their team loses",
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
    if (setupGuardIssues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { setupGuardIssues = emptyList() },
            title = { Text("Setup isn't legal yet") },
            text = {
                Column(
                    verticalArrangement =
                        androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Text("Fix these issues before beginning the first night:")
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(setupGuardIssues) { issue ->
                            Text(
                                "• $issue",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    setupGuardIssues = emptyList()
                    tab = GameTab.GRIMOIRE
                }) { Text("Fix setup") }
            },
        )
    }
    if (duskGuard) {
        val onBlock = GameActions.aboutToDie(state)?.let { state.player(it) }
        AlertDialog(
            onDismissRequest = { duskGuard = false },
            title = { Text("Dusk falls") },
            text = { Text("${onBlock?.name ?: "Someone"} is on the block and hasn't been executed. Execute before night?") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    duskGuard = false
                    onBlock?.let { viewModel.kill(it.id, DeathCause.EXECUTION) }
                    viewModel.advancePhase()
                    tab = GameTab.NIGHT
                }) { Text("Execute & begin night") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        duskGuard = false
                        viewModel.advancePhase()
                        tab = GameTab.NIGHT
                    }) { Text("No execution") }
                    TextButton(onClick = { duskGuard = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (unfinishedNightSteps.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { unfinishedNightSteps = emptyList() },
            title = { Text("Night checklist incomplete") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${unfinishedNightSteps.size} step${if (unfinishedNightSteps.size == 1) " is" else "s are"} still unchecked:",
                    )
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(unfinishedNightSteps) { title ->
                            Text(
                                "• $title",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    unfinishedNightSteps = emptyList()
                    val expectedCycle = state.cycle
                    viewModel.update { current ->
                        if (current.phase == Phase.NIGHT && current.cycle == expectedCycle) {
                            GameActions.advancePhase(current)
                        } else {
                            current
                        }
                    }
                    tab = GameTab.DAY
                }) { Text("Dawn anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    unfinishedNightSteps = emptyList()
                    tab = GameTab.NIGHT
                }) { Text("Keep checking") }
            },
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
private fun HiddenIdentityDialog(
    title: String,
    explanation: String,
    options: List<Character>,
    onPick: (Character) -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(title) },
        text = {
            Column {
                Text(explanation)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (options.isEmpty()) {
                        item {
                            Text(
                                "No eligible characters are available on this script. " +
                                    "You can set the shown identity from the player's seat.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(options, key = { it.id }) { character ->
                        TextButton(
                            onClick = { onPick(character) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CharacterToken(character = character, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(character.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
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
