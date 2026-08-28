package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import com.clocktower.grimoire.ui.components.DiscussionTimer
import com.clocktower.grimoire.ui.components.PrivacyCover
import com.clocktower.grimoire.ui.components.FullScreenShow
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.ShowToolSheet
import com.clocktower.grimoire.ui.components.dialogWindowBottomFix
import com.clocktower.grimoire.ui.components.overlayBottomPadding
import com.clocktower.grimoire.ui.components.overlaySafeAreaPadding
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.Briefings
import com.clocktower.engine.Effects
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.Team
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.screens.night.dimLabel
import com.clocktower.grimoire.ui.screens.night.nextDimLevel
import com.clocktower.grimoire.ui.screens.night.screenBrightness
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
    var showTravellerJoin by rememberSaveable { mutableStateOf(false) }
    var spyMode by rememberSaveable { mutableStateOf(false) }
    // How much of the "Before the first night" checklist is still owed, for the
    // menu entry that re-opens it (playtest D, P1-10).
    val setupOutstanding = remember(state) {
        SetupRequirements.unmet(state, viewModel::characterById).count { it.blocking }
    }

    // The table's phone must not sleep mid-game — and the browser drops its
    // wake lock on every tab switch, so it is re-requested on resume too.
    com.clocktower.grimoire.ui.platform.KeepScreenOn()
    com.clocktower.grimoire.ui.platform.RequestWakeLockOnResume()

    // The night dim level lives in GameState (ux/night-screen §H): the scrim is
    // the only lever the PWA has, and on Android the window brightness follows.
    val setBrightness = com.clocktower.grimoire.ui.platform.rememberScreenBrightness()
    val dimming = state.phase == Phase.NIGHT && state.dimLevel > 0
    LaunchedEffect(state.dimLevel, state.phase) {
        setBrightness(if (dimming) screenBrightness(state.dimLevel) else null)
    }
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

    // One-tap follow-through on a briefing line (WP6's `actionId` prefixes).
    // The navigation half can only happen here — the shell owns the tabs and
    // the seat sheet — and it closes the card first, because a seat sheet
    // underneath a briefing dialog would be invisible. Re-tapping the phase
    // button brings the card straight back: nothing is spent by leaving it.
    // The engine half stays with the engine, and the line is ticked in place.
    val onBriefingItem: (BriefingItem) -> Boolean = { item ->
        val action = item.actionId
        when {
            action.startsWith(Briefings.ACTION_OPEN_SEAT) -> {
                val seatId = item.playerId
                    ?: action.removePrefix(Briefings.ACTION_OPEN_SEAT).toLongOrNull()
                if (seatId != null) {
                    phaseGuards.clear()
                    openSeat = seatId
                }
                false
            }

            // The plan already carries the inserted FIRST re-run step; the
            // night sheet is where it is ticked off.
            action.startsWith(Briefings.ACTION_RERUN_FIRST_NIGHT) -> {
                phaseGuards.clear()
                tab = GameTab.NIGHT
                false
            }

            // The Day tab renders the same item with a working composer beside
            // it (its DAWN card's record action); the shell only has to land
            // the storyteller there.
            action.startsWith(Briefings.ACTION_RECORD) -> {
                phaseGuards.clear()
                tab = GameTab.DAY
                false
            }

            // mark-announced:, resolve-prompt: — and execute:, which the dusk
            // sheet's own primary button performs.
            action.isNotBlank() -> viewModel.resolveBriefingItem(item)

            else -> false
        }
    }

    // The scrim lives OUTSIDE the Scaffold so it covers the top bar and the
    // navigation bar — the two most saturated surfaces on the screen, and the
    // ones the old in-content scrim left at full brightness (defect #21).
    Box(Modifier.fillMaxSize()) {
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
                            // 14 sp floor: the top bar is read at arm's length
                            // in a dark room like everything else.
                            style = MaterialTheme.typography.bodyMedium,
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
                    // C-18 / ux/day-screen §I: the phase button moves OFF the
                    // top bar for the day. Dusk is the most destructive control
                    // in the app and it had two paths — this unlabelled moon
                    // icon and the Dusk card's [Everyone, eyes closed ▸] — so
                    // the icon went. Setup ("Begin night") and the night
                    // ("Dawn") keep theirs; the night sheet's own last card is
                    // a duplicate the storyteller has to scroll to, and setup
                    // has no other entry point at all.
                    if (state.phase != Phase.DAY) {
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
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // A-4 / D P1-10: once the "Before the first night" sheet
                        // had been dismissed with the same rows still
                        // outstanding, `SetupIdentityPrompts` would not raise it
                        // again — and no menu entry reached it, so the Lunatic's
                        // Demon token, their bluffs and the Grandchild were
                        // unreachable. The checklist is always one tap away now,
                        // under the name it wears on its own header, and it
                        // opens through the SAME opener as the begin-night
                        // guard's "Fix setup" so there is one sheet, not two.
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (setupOutstanding == 0) {
                                        "Before the first night…"
                                    } else {
                                        "Before the first night… · $setupOutstanding to do"
                                    },
                                )
                            },
                            onClick = { showMenu = false; SetupChecklist.open() },
                        )
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
                            text = { Text("Screen dimming: ${dimLabel(state.dimLevel)}") },
                            onClick = { viewModel.setDimLevel(nextDimLevel(state.dimLevel)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Show the grimoire to a player…") },
                            onClick = { showMenu = false; spyMode = true },
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
                            text = { Text("A traveller joins…") },
                            onClick = { showMenu = false; showTravellerJoin = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add an empty seat") },
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
                        // The last card of the sheet IS the dawn button
                        // (ux/night-screen §F): "OPEN THE DAY →".
                        onDawn = onPhaseButton,
                    )
                    GameTab.DAY -> DayScreen(
                        viewModel = viewModel,
                        state = state,
                        // The Day tab hands the close of day back to the
                        // shell's dusk sheet rather than duplicating it.
                        onDusk = onPhaseButton,
                        onOpenSeat = { openSeat = it },
                    )
                    // WP11: passing the live state marks what is in play and
                    // in which seat on the Script tab.
                    GameTab.REFERENCE -> ReferenceScreen(viewModel, state.script, state)
                }
            }
            // Mounted on EVERY tab so its deadline survives a tab switch
            // (grimoire-and-seats §13, P1-17). The Day and Night tabs dock it
            // in a bar of their own (`HostTimerInBar`) and this pill stands
            // down there: a floating control cannot be kept clear of the night
            // card's FULL-WIDTH primary button, and BottomStart landed squarely
            // on it, stealing its taps (playtest B P1 #4).
            DiscussionTimer(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        }
    }
        if (dimming) {
            // Red-shifted dimming preserves the room's darkness; a plain Box
            // with no pointer handling lets every touch through.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A0A0A).copy(alpha = nightScrimAlpha(state.dimLevel))),
            )
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
        // The caption lets the storyteller orient without opening anything —
        // "Night 3 · press and hold to open" (grimoire-and-seats §8).
        PrivacyCover(caption = phaseLabel, onUnlock = { grimoireLocked = false })
    }
    // Setup identity prompts live in GameExtras.kt (WP0 extraction; WP11 owns
    // them next). It also renders the checklist sheet — including for every
    // `SetupChecklist.open()` request, from the overflow menu and from the
    // begin-night guard's "Fix setup".
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
    if (showLog) GameLogDialog(state, viewModel.lookup, onDismiss = { showLog = false })
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
    PhaseGuardDialogs(viewModel, state, phaseGuards, onItem = onBriefingItem) { tab = it }
    activeCard?.let { card ->
        FullScreenShow(
            card = card,
            viewModel = viewModel,
            coverCaption = phaseLabel,
            onDismiss = { activeCard = null },
        )
    }
    if (showTravellerJoin) {
        TravellerJoinDialog(
            viewModel = viewModel,
            state = state,
            onDismiss = { showTravellerJoin = false },
        )
    }
    if (spyMode) {
        ReadOnlyGrimoire(
            viewModel = viewModel,
            state = state,
            onDone = {
                spyMode = false
                // The phone was just in a player's hands: re-arm the cover
                // rather than dropping back into the open grimoire.
                grimoireLocked = true
            },
        )
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

/**
 * A Traveller joins mid-game: one dialog, one confirm (grimoire-and-seats §10).
 *
 * The old path was "Add seat" and then roughly sixteen taps across the grimoire
 * to give the seat a character and an alignment — and the alignment, which is
 * always the storyteller's choice for a Traveller, was never actually asked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravellerJoinDialog(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var afterId by rememberSaveable { mutableStateOf(state.seats.lastOrNull()?.id) }
    var characterId by rememberSaveable { mutableStateOf<String?>(null) }
    var evil by rememberSaveable { mutableStateOf(false) }

    val travellers = remember(state.script) {
        val onScript = viewModel.gameData.resolve(state.script).filter { it.team == Team.TRAVELLER }
        onScript.ifEmpty { viewModel.gameData.characters.filter { it.team == Team.TRAVELLER } }
            .sortedBy { it.name }
    }
    val character = characterId?.let { viewModel.characterById(it) }
    val announce = buildString {
        append(name.ifBlank { "A traveller" })
        append(" joins the game")
        character?.let { append(" as the ").append(it.name) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A traveller joins") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("Sits after", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((index, seat) in state.seats.withIndex()) {
                            FilterChip(
                                selected = afterId == seat.id,
                                onClick = { afterId = seat.id },
                                label = { Text("${index + 1} ${seat.name}") },
                            )
                        }
                    }
                }
                item {
                    Text("Character", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (traveller in travellers) {
                            FilterChip(
                                selected = characterId == traveller.id,
                                onClick = { characterId = traveller.id },
                                leadingIcon = { CharacterToken(character = traveller, size = 24.dp) },
                                label = { Text(traveller.name) },
                            )
                        }
                    }
                }
                item {
                    Text("Alignment", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !evil,
                            onClick = { evil = false },
                            label = { Text("Good") },
                        )
                        FilterChip(
                            selected = evil,
                            onClick = { evil = true },
                            label = { Text("Evil") },
                        )
                    }
                    Text(
                        "A Traveller's alignment is always your choice — never the character's.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text("Announce", style = MaterialTheme.typography.titleSmall)
                    Text(announce, style = MaterialTheme.typography.bodyMedium, color = AgedGold)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = name.isNotBlank(),
                onClick = {
                    viewModel.joinTraveller(
                        name = name.trim(),
                        afterPlayerId = afterId,
                        characterId = characterId,
                        evil = evil,
                        announce = announce,
                    )
                    onDismiss()
                },
            ) { Text("Seat them") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The grimoire as a player may hold it (grimoire-and-seats §8): the Spy's and
 * the Widow's look.
 *
 * Read-only means read-only — no taps, no long-press, no seat sheet, and none
 * of the storyteller-private material (notes, bluffs, the log, the top bar, the
 * tabs) is composed at all. Every token is listed in full text rather than
 * truncated to `+N`, because this is the one view whose whole job is reading.
 * Seats can be redacted before it is handed over, which is what the Magician
 * jinx needs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadOnlyGrimoire(
    viewModel: GameViewModel,
    state: GameState,
    onDone: () -> Unit,
) {
    var handedOver by rememberSaveable { mutableStateOf(false) }
    var redacted by remember { mutableStateOf(emptySet<Long>()) }
    // Measured HERE, in the shell's composition: inside the dialog window every
    // inset reads zero (playtest D, P1-7).
    val dialogBottomFix = dialogWindowBottomFix()
    Dialog(
        // A player is holding the phone in stage 2, and its only button used to
        // be under the home indicator: with back-press refused as well there was
        // no way out at all (playtest D, P1-7). Back drops to the storyteller's
        // view, which `onDone` immediately covers, so it is never a leak.
        onDismissRequest = onDone,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            // A dialog window sits ABOVE the shell's own inset padding and does
            // not always dispatch the system insets into its content, so the
            // action row is lifted by `overlayBottomPadding()` on top of
            // whatever `overlaySafeAreaPadding()` managed to apply. `fillMaxSize`
            // is what stops the column growing past the bottom of the screen and
            // clipping that row to a 4 px sliver.
            Column(
                Modifier
                    .fillMaxSize()
                    .overlaySafeAreaPadding()
                    .padding(bottom = dialogBottomFix)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!handedOver) {
                    Text("Show the grimoire", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                    Text(
                        "Tap any seat you want hidden, then hand the phone over. " +
                            "Nothing on the next screen can be tapped.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        for ((index, seat) in state.seats.withIndex()) {
                            FilterChip(
                                selected = seat.id in redacted,
                                onClick = {
                                    redacted = if (seat.id in redacted) redacted - seat.id else redacted + seat.id
                                },
                                label = { Text("${index + 1} ${seat.name}") },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = overlayBottomPadding()),
                    ) {
                        FilledTonalButton(
                            onClick = { handedOver = true },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("HAND IT OVER") }
                        TextButton(
                            onClick = onDone,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) { Text("Cancel") }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.seats) { index, seat ->
                            ReadOnlySeatRow(viewModel, state, index + 1, seat.id, seat.id in redacted)
                        }
                    }
                    FilledTonalButton(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = overlayBottomPadding())
                            .heightIn(min = 56.dp),
                    ) { Text("DONE — BACK TO THE SHEET") }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlySeatRow(
    viewModel: GameViewModel,
    state: GameState,
    seatNumber: Int,
    playerId: Long,
    redacted: Boolean,
) {
    val player = state.player(playerId) ?: return
    val tokens = remember(state, playerId) {
        if (redacted) emptyList() else Effects.rendered(state, viewModel::characterById, playerId)
    }
    Row(verticalAlignment = Alignment.Top) {
        CharacterToken(
            character = if (redacted) null else viewModel.characterById(player.characterId),
            size = 44.dp,
            dimmed = !player.alive,
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = "$seatNumber  ${player.name}${if (player.alive) "" else " †"}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (redacted) {
                Text(
                    "hidden by the storyteller",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = viewModel.characterById(player.characterId)?.name ?: "no character",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (token in tokens) {
                    TokenLine(viewModel = viewModel, token = token)
                }
            }
        }
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
