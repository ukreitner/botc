package com.clocktower.grimoire.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameState
import com.clocktower.engine.InfoCalc
import com.clocktower.engine.StatusEffects
import com.clocktower.engine.GuideShow
import com.clocktower.engine.NightGuide
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.NightStep
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Player
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.FullScreenShow
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Twilight
import com.clocktower.grimoire.ui.theme.color

/**
 * The night sheet: in-play characters in official wake order with prompts,
 * check-off tracking, and — where the grimoire knows the answer — the TRUE
 * information to give, with drunk/poisoned/misregistration warnings and
 * one-tap full-screen signals.
 */
@Composable
fun NightScreen(
    viewModel: GameViewModel,
    state: GameState,
    onOpenShowTool: () -> Unit = {},
) {
    val isFirstNight = state.cycle == 1
    val steps = remember(state.players, state.fabledIds, state.cycle, state.demonBluffIds) {
        if (isFirstNight) {
            viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
        } else {
            viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
        }
    }
    var expandedId by rememberSaveable(state.cycle) {
        mutableStateOf(steps.firstOrNull { it.id !in state.nightStepsDone }?.id)
    }
    var previousDone by remember(state.cycle) { mutableStateOf(state.nightStepsDone) }
    var showCard by remember { mutableStateOf<ShowCard?>(null) }
    var pendingReminderLabel by rememberSaveable(state.cycle) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val activeCharacter = expandedId
        ?.takeUnless { it in NightMarkers.all }
        ?.let(viewModel::characterById)

    LaunchedEffect(state.nightStepsDone) {
        val newlyUnchecked = previousDone - state.nightStepsDone
        if (newlyUnchecked.isNotEmpty()) {
            expandedId = steps.firstOrNull { it.id in newlyUnchecked }?.id
        }
        previousDone = state.nightStepsDone
    }

    LaunchedEffect(expandedId) {
        pendingReminderLabel = null
        val stepIndex = steps.indexOfFirst { it.id == expandedId }
        if (stepIndex >= 0) {
            // +1 for the progress header before the first checklist row.
            listState.animateScrollToItem(stepIndex + 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = if (isFirstNight) "First Night" else "Night ${state.cycle}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AgedGold,
                )
                Text(
                    text = "${steps.count { it.id in state.nightStepsDone }} of ${steps.size} steps done · tap a step for details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                val done = step.id in state.nightStepsDone
                NightStepRow(
                    viewModel = viewModel,
                    state = state,
                    step = step,
                    done = done,
                    expanded = expandedId == step.id,
                    onExpand = { expandedId = if (expandedId == step.id) null else step.id },
                    onToggleDone = {
                        viewModel.toggleNightStep(step.id)
                        expandedId = if (done) {
                            step.id
                        } else {
                            steps.drop(index + 1)
                                .firstOrNull { it.id !in state.nightStepsDone }
                                ?.id
                        }
                    },
                    onShow = { showCard = it },
                )
            }
            item {
                Text(
                    text = "Only characters currently in the grimoire appear here. " +
                        "Dead players usually don't act — skip them unless their ability says otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        NightToolTray(
            viewModel = viewModel,
            state = state,
            character = activeCharacter,
            pendingReminderLabel = pendingReminderLabel,
            onPendingReminder = { pendingReminderLabel = it },
            onShow = { showCard = it },
            onOpenShowTool = onOpenShowTool,
        )
    }

    showCard?.let { card ->
        FullScreenShow(card = card, viewModel = viewModel, onDismiss = { showCard = null })
    }
}

/**
 * A persistent physical-style tray for the currently expanded night
 * character. Pick a reminder token, then a seat; no sheet/menu round-trip.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NightToolTray(
    viewModel: GameViewModel,
    state: GameState,
    character: Character?,
    pendingReminderLabel: String?,
    onPendingReminder: (String?) -> Unit,
    onShow: (ShowCard) -> Unit,
    onOpenShowTool: () -> Unit,
) {
    val reminders = character?.allReminders.orEmpty()
    // Once-per-game abilities get a one-tap "spent" mark on their holder.
    val oncePerGame = character?.ability?.startsWith("Once per game", ignoreCase = true) == true
    val holders = state.players.filter { it.nightRoleId == character?.id }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Text gets the full width; actions wrap below — never a
            // squeezed one-character-per-line column on narrow phones.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (character != null) {
                    CharacterToken(character = character, size = 36.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        character?.let { "${it.name} night tools" } ?: "Night tools",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (character == null) {
                            "Open a character step for its tools. “Sheet” lets a player point at a character silently."
                        } else if (reminders.isEmpty()) {
                            "No reminder tokens for this character."
                        } else {
                            "Tap a reminder, then tap the player who gets it."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                character?.let {
                    AssistChip(
                        onClick = {
                            onShow(ShowCard.CharacterCard("THIS PLAYER IS", it.id))
                        },
                        label = { Text("Show token") },
                    )
                }
                AssistChip(
                    onClick = {
                        val ids = viewModel.gameData.resolve(state.script)
                            .filter { it.team != Team.FABLED }
                            .map { it.id }
                        onShow(ShowCard.SheetCard(ids))
                    },
                    label = { Text("Sheet") },
                )
                if (oncePerGame && character != null && holders.isNotEmpty() &&
                    holders.any { h -> h.reminders.none { it.label.equals("No ability", true) } }
                ) {
                    AssistChip(
                        onClick = {
                            for (h in holders) {
                                viewModel.update { s ->
                                    GameActions.placeExclusiveReminder(
                                        s, h.id,
                                        com.clocktower.engine.PlacedReminder(character.id, "No ability"),
                                    )
                                }
                            }
                        },
                        label = { Text("Mark spent") },
                    )
                }
                AssistChip(onClick = onOpenShowTool, label = { Text("All tokens") })
            }

            if (character != null && reminders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reminders) { label ->
                        FilterChip(
                            selected = pendingReminderLabel == label,
                            onClick = {
                                onPendingReminder(
                                    if (pendingReminderLabel == label) null else label,
                                )
                            },
                            leadingIcon = {
                                // A tiny full token is unreadable; a plain
                                // team-colored dot marks these as tokens.
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(character.team.color, CircleShape),
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            if (character != null && pendingReminderLabel != null) {
                Text(
                    "Place “$pendingReminderLabel” on:",
                    style = MaterialTheme.typography.labelMedium,
                    color = AgedGold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.players, key = { it.id }) { player ->
                        AssistChip(
                            onClick = {
                                val reminder = PlacedReminder(character.id, pendingReminderLabel)
                                val availableCopies = character.allReminders.count {
                                    it == pendingReminderLabel
                                }
                                viewModel.update { current ->
                                    if (availableCopies <= 1) {
                                        GameActions.placeExclusiveReminder(current, player.id, reminder)
                                    } else {
                                        val placed = current.players.flatMap { seat ->
                                            seat.reminders.mapIndexedNotNull { index, token ->
                                                if (token == reminder) seat.id to index else null
                                            }
                                        }
                                        val roomForAnother = placed.size < availableCopies
                                        val cleared = if (roomForAnother) {
                                            current
                                        } else {
                                            val (seatId, index) = placed.first()
                                            GameActions.removeReminder(current, seatId, index)
                                        }
                                        GameActions.addReminder(cleared, player.id, reminder)
                                    }
                                }
                                onPendingReminder(null)
                            },
                            leadingIcon = {
                                CharacterToken(
                                    character = viewModel.characterById(player.characterId),
                                    size = 26.dp,
                                    dimmed = !player.alive,
                                )
                            },
                            label = { Text(player.name + if (!player.alive) " †" else "") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prepares a guide show card: the text is freely editable and, for
 * "pick" token cards, any script character can be chosen — so Pixie
 * madness, Cerenovus commands etc. are two taps, never typing from scratch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideShowDialog(
    viewModel: GameViewModel,
    state: GameState,
    stepCharacterId: String,
    show: GuideShow,
    onShow: (ShowCard) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(show.label) { mutableStateOf(show.text) }
    var tokenId by rememberSaveable(show.label) {
        mutableStateOf(if (show.token == "self") stepCharacterId else null)
    }
    var search by rememberSaveable(show.label) { mutableStateOf("") }
    val needsToken = show.kind == "token"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(show.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text shown full-screen — edit freely") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsToken && show.token == "pick") {
                    Text(
                        "Token to show under the text:",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Find a character…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The token being shown is almost always in play (or a
                    // bluff of one) — surface those before the rest.
                    val inPlayIds = state.players.mapNotNull { it.characterId }.toSet()
                    val candidates = viewModel.gameData.resolve(state.script)
                        .filter { it.team != Team.FABLED }
                        .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
                        .sortedBy { it.name }
                        .sortedByDescending { it.id in inPlayIds }
                        .take(24)
                    val (inPlay, others) = candidates.partition { it.id in inPlayIds }
                    for ((section, chars) in listOf("In play" to inPlay, "Not in play" to others)) {
                        if (chars.isEmpty()) continue
                        Text(
                            section,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (c in chars) {
                                FilterChip(
                                    selected = tokenId == c.id,
                                    onClick = { tokenId = if (tokenId == c.id) null else c.id },
                                    leadingIcon = { CharacterToken(character = c, size = 24.dp) },
                                    label = { Text(c.name) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = text.isNotBlank() && (!needsToken || tokenId != null),
                onClick = {
                    onShow(
                        if (needsToken) {
                            ShowCard.CharacterCard(text, tokenId!!)
                        } else {
                            ShowCard.Message(text)
                        },
                    )
                },
            ) { Text("Show full-screen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One-tap resolutions for the fiddly multi-step interactions: Snake Charmer
 * swap, Imp star-pass, Fang Gu jump, Professor resurrection. Each applies
 * the full official consequence in a single confirmed action (undoable).
 */
@Composable
private fun QuickResolutions(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
) {
    val holder = step.playerIds.firstOrNull()?.let { state.player(it) } ?: return
    fun teamOf(p: Player): Team? = viewModel.characterById(p.characterId)?.team

    when (step.id) {
        "snakecharmer" -> if (holder.alive) {
            ResolutionPicker(
                key = "snakecharmer-${state.cycle}",
                title = "Charm hit the Demon? Pick the Demon — they and ${holder.name} swap characters " +
                    "AND alignments, and the new Snake Charmer is poisoned forever.",
                candidates = state.players
                    .filter { it.alive && it.id != holder.id }
                    .sortedByDescending { teamOf(it) == Team.DEMON },
                viewModel = viewModel,
                confirmLabel = { "Swap with ${it.name} & poison" },
            ) { s, target -> GameActions.snakeCharmerSwap(s, holder.id, target.id) }
        }
        "fanggu" -> if (holder.alive) {
            DemonKillPanel(viewModel, state, step.id, holder)
            ResolutionPicker(
                key = "fanggu-${state.cycle}",
                title = "Fang Gu jump (once per game) — chose an Outsider? The Fang Gu dies and " +
                    "the Outsider becomes an evil Fang Gu.",
                candidates = state.players
                    .filter { it.alive && it.id != holder.id }
                    .sortedByDescending { teamOf(it) == Team.OUTSIDER },
                viewModel = viewModel,
                confirmLabel = { "${holder.name} dies » ${it.name} is the Fang Gu" },
            ) { s, target ->
                val jumped = GameActions.starPass(s, holder.id, target.id, viewModel::characterById)
                GameActions.placeExclusiveReminder(jumped, target.id, PlacedReminder("fanggu", "Once"))
            }
        }
        "professor" -> {
            val deadCandidates = state.players
                .filter { !it.alive }
                .sortedByDescending { teamOf(it) == Team.TOWNSFOLK }
            if (deadCandidates.isNotEmpty() &&
                holder.reminders.none { it.label.equals("No ability", true) }
            ) {
                ResolutionPicker(
                    key = "professor-${state.cycle}",
                    title = "Professor used their once-per-game — pick a dead Townsfolk to resurrect.",
                    candidates = deadCandidates,
                    viewModel = viewModel,
                    confirmLabel = { "Resurrect ${it.name}" },
                ) { s, target ->
                    val revived = GameActions.resurrect(s, target.id)
                    GameActions.placeExclusiveReminder(revived, holder.id, PlacedReminder("professor", "No ability"))
                }
            }
        }
        else -> {
            val character = viewModel.characterById(step.id)
            if (character?.team == Team.DEMON && holder.alive) {
                DemonKillPanel(viewModel, state, step.id, holder)
            }
        }
    }
}

/**
 * The nightly demon kill, resolved in place: pick the chosen player, see
 * every protection/trigger that applies, then confirm the death (or not).
 * The Imp choosing themself flows straight into the star pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemonKillPanel(
    viewModel: GameViewModel,
    state: GameState,
    demonId: String,
    holder: Player,
) {
    var targetId by rememberSaveable("$demonId-${state.cycle}") { mutableStateOf<Long?>(null) }
    fun teamOf(p: Player): Team? = viewModel.characterById(p.characterId)?.team

    Text(
        text = "Demon kill — who did ${holder.name} choose?",
        style = MaterialTheme.typography.labelLarge,
        color = AgedGold,
    )
    if (StatusEffects.isImpaired(state, viewModel::characterById, holder)) {
        Text(
            "! The Demon is drunk/poisoned — the attack fails (choose 'No kill').",
            style = MaterialTheme.typography.bodySmall,
            color = EmberRed,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val candidates = state.players.sortedWith(
            compareByDescending<Player> { it.alive }.thenBy { it.id == holder.id },
        )
        for (p in candidates) {
            FilterChip(
                selected = targetId == p.id,
                onClick = { targetId = if (targetId == p.id) null else p.id },
                leadingIcon = {
                    CharacterToken(
                        character = viewModel.characterById(p.characterId),
                        size = 26.dp,
                        dimmed = !p.alive,
                    )
                },
                label = {
                    Text(
                        when {
                            p.id == holder.id -> "${p.name} (self)"
                            !p.alive -> "${p.name} †"
                            else -> p.name
                        },
                    )
                },
            )
        }
    }

    val target = targetId?.let { state.player(it) }
    if (target != null) {
        for (note in StatusEffects.deathNotes(state, viewModel::characterById, target.id)) {
            Text("! $note", style = MaterialTheme.typography.bodySmall, color = EmberRed)
        }
        if (target.id == holder.id && demonId == "imp") {
            Text(
                "Star pass — the Imp dies. Tap who becomes the new Imp " +
                    "(a Minion; the Scarlet Woman catches it first if able):",
                style = MaterialTheme.typography.labelLarge,
                color = AgedGold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val heirs = state.players
                    .filter { it.alive && it.id != holder.id }
                    .sortedWith(
                        compareByDescending<Player> { it.characterId == "scarletwoman" }
                            .thenByDescending { teamOf(it) == Team.MINION },
                    )
                for (heir in heirs) {
                    AssistChip(
                        onClick = {
                            viewModel.update { s ->
                                GameActions.starPass(s, holder.id, heir.id, viewModel::characterById)
                            }
                            targetId = null
                        },
                        leadingIcon = {
                            CharacterToken(character = viewModel.characterById(heir.characterId), size = 26.dp)
                        },
                        label = { Text("${heir.name} becomes the Imp") },
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = target.alive,
                    onClick = {
                        viewModel.update { s ->
                            GameActions.kill(s, target.id, DeathCause.DEMON, viewModel::characterById)
                        }
                        targetId = null
                    },
                ) { Text("${target.name} dies") }
                TextButton(onClick = { targetId = null }) { Text("No kill") }
            }
        }
    }
}

/** Pick-a-player-then-confirm widget used by [QuickResolutions]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolutionPicker(
    key: String,
    title: String,
    candidates: List<Player>,
    viewModel: GameViewModel,
    confirmLabel: (Player) -> String,
    action: (GameState, Player) -> GameState,
) {
    var selectedId by rememberSaveable(key) { mutableStateOf<Long?>(null) }
    Text(
        text = "$title",
        style = MaterialTheme.typography.labelLarge,
        color = AgedGold,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (p in candidates) {
                FilterChip(
                    selected = selectedId == p.id,
                    onClick = { selectedId = if (selectedId == p.id) null else p.id },
                    leadingIcon = {
                        CharacterToken(
                            character = viewModel.characterById(p.characterId),
                            size = 26.dp,
                            dimmed = !p.alive,
                        )
                    },
                    label = { Text(p.name + if (!p.alive) " †" else "") },
                )
            }
        }
        val selected = candidates.find { it.id == selectedId }
        if (selected != null) {
            FilledTonalButton(
                onClick = {
                    viewModel.update { s -> action(s, selected) }
                    selectedId = null
                },
            ) { Text(confirmLabel(selected)) }
        }
    }
}

@Composable
private fun NightStepRow(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    done: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onShow: (ShowCard) -> Unit,
) {
    val isMarker = step.id in NightMarkers.all
    val holders = step.playerIds.mapNotNull { state.player(it) }
    val allDead = holders.isNotEmpty() && holders.none { it.alive }

    Surface(
        color = if (isMarker) Twilight else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (done) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onExpand),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { onToggleDone() })
                if (!isMarker && step.id != NightMarkers.MINION_INFO && step.id != NightMarkers.DEMON_INFO) {
                    CharacterToken(
                        character = viewModel.characterById(step.id),
                        size = 44.dp,
                        dimmed = done || allDead,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                done -> MaterialTheme.colorScheme.onSurfaceVariant
                                isMarker -> AgedGold
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (holders.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = holders.joinToString { it.name + if (!it.alive) " †" else "" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (step.detail.isNotBlank() && (expanded || !done)) {
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (allDead) {
                        Text(
                            text = "All holders are dead — usually skip.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                StepDetailPanel(viewModel, state, step, onShow)
            }
        }
    }
}

/** Expanded contents: computed true info, target picking, show-card shortcuts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDetailPanel(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    onShow: (ShowCard) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Demon info gets a one-tap bluff display.
        if (step.id == NightMarkers.DEMON_INFO && state.demonBluffIds.isNotEmpty()) {
            AssistChip(
                onClick = { onShow(ShowCard.BluffsCard(state.demonBluffIds)) },
                label = { Text("Show bluffs full-screen") },
            )
        }

        // The full how-to-run book for this character tonight, with
        // prepared (and editable) full-screen cards.
        val guide = NightGuide.forStep(step.id, state.cycle == 1)
        if (guide != null) {
            if (guide.instructions.isNotBlank() && guide.instructions != step.detail) {
                Text(
                    text = guide.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (guide.shows.isNotEmpty()) {
                var activeShow by remember { mutableStateOf<GuideShow?>(null) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (show in guide.shows) {
                        AssistChip(
                            onClick = {
                                when (show.kind) {
                                    "good" -> onShow(ShowCard.AlignmentCard(evil = false))
                                    "evil" -> onShow(ShowCard.AlignmentCard(evil = true))
                                    else -> activeShow = show
                                }
                            },
                            label = { Text("» ${show.label}") },
                        )
                    }
                }
                activeShow?.let { show ->
                    GuideShowDialog(
                        viewModel = viewModel,
                        state = state,
                        stepCharacterId = step.id,
                        show = show,
                        onShow = {
                            activeShow = null
                            onShow(it)
                        },
                        onDismiss = { activeShow = null },
                    )
                }
            }
        }

        QuickResolutions(viewModel, state, step)

        if (InfoCalc.supports(step.id)) {
            val holderId = step.playerIds.firstOrNull()
            val targetsNeeded = InfoCalc.targetsNeeded(step.id)
            var targets by rememberSaveable(step.id) { mutableStateOf(listOf<Long>()) }

            if (targetsNeeded > 0) {
                Text(
                    "Chosen player${if (targetsNeeded > 1) "s" else ""} ($targetsNeeded):",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in state.players) {
                        FilterChip(
                            selected = p.id in targets,
                            onClick = {
                                targets = when {
                                    p.id in targets -> targets - p.id
                                    targets.size < targetsNeeded -> targets + p.id
                                    else -> targets.drop(1) + p.id
                                }
                            },
                            label = { Text(p.name) },
                        )
                    }
                }
            }

            val result = InfoCalc.compute(viewModel.gameData, state, step.id, holderId, targets)
            if (result != null) {
                Text(
                    text = "${result.headline}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                )
                if (result.detail.isNotBlank()) {
                    Text(
                        result.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (caveat in result.caveats) {
                    Text(
                        "! $caveat",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberRed,
                    )
                }
                // Numeric or yes/no answers can be flashed full-screen.
                val leadingNumber = result.headline.takeWhile { it.isDigit() }.toIntOrNull()
                val isYes = result.headline.startsWith("YES")
                val isNo = result.headline.startsWith("NO")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (leadingNumber != null && leadingNumber <= 9) {
                        AssistChip(
                            onClick = { onShow(ShowCard.NumberCard(leadingNumber)) },
                            label = { Text("Show $leadingNumber full-screen") },
                        )
                    }
                    if (isYes || isNo) {
                        AssistChip(
                            onClick = { onShow(ShowCard.Message(if (isYes) "YES" else "NO")) },
                            label = { Text("Show answer full-screen") },
                        )
                    }
                }
                // Impaired players get false info — offer the lies one tap away.
                val impaired = result.caveats.any {
                    "POISONED" in it || "DRUNK" in it || "IS the Drunk" in it || "VORTOX" in it || "No Dashii" in it
                }
                if (impaired) {
                    Text(
                        "False info to show instead:",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmberRed,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (leadingNumber != null) {
                            for (n in 0..4) {
                                if (n == leadingNumber) continue
                                AssistChip(
                                    onClick = { onShow(ShowCard.NumberCard(n)) },
                                    label = { Text("$n") },
                                )
                            }
                        }
                        if (isYes || isNo) {
                            AssistChip(
                                onClick = { onShow(ShowCard.Message(if (isYes) "NO" else "YES")) },
                                label = { Text(if (isYes) "Show NO" else "Show YES") },
                            )
                        }
                    }
                }
            }
        }
    }
}
