package com.clocktower.grimoire.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.clocktower.engine.GameState
import com.clocktower.engine.InfoCalc
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.NightStep
import com.clocktower.engine.PlacedReminder
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.FullScreenShow
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Twilight

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
) {
    val isFirstNight = state.cycle == 1
    val steps = remember(state.players, state.fabledIds, state.cycle, state.demonBluffIds) {
        if (isFirstNight) {
            viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
        } else {
            viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
        }
    }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCard by remember { mutableStateOf<ShowCard?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
        items(steps, key = { it.id }) { step ->
            NightStepRow(
                viewModel = viewModel,
                state = state,
                step = step,
                done = step.id in state.nightStepsDone,
                expanded = expandedId == step.id,
                onExpand = { expandedId = if (expandedId == step.id) null else step.id },
                onToggleDone = { viewModel.toggleNightStep(step.id) },
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

    showCard?.let { card ->
        FullScreenShow(card = card, viewModel = viewModel, onDismiss = { showCard = null })
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

        // Place this character's reminder tokens without leaving the sheet:
        // pick the token, then the seat it goes on.
        val stepCharacter = viewModel.characterById(step.id)
        val tokens = stepCharacter?.allReminders.orEmpty()
        if (tokens.isNotEmpty()) {
            var pendingLabel by rememberSaveable(step.id) { mutableStateOf<String?>(null) }
            Text("Place reminder:", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (label in tokens) {
                    FilterChip(
                        selected = pendingLabel == label,
                        onClick = { pendingLabel = if (pendingLabel == label) null else label },
                        label = { Text(label) },
                    )
                }
            }
            pendingLabel?.let { label ->
                Text(
                    "…on whom?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in state.players) {
                        AssistChip(
                            onClick = {
                                viewModel.addReminder(p.id, PlacedReminder(step.id, label))
                                pendingLabel = null
                            },
                            label = { Text(p.name) },
                        )
                    }
                }
            }
        }

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
                    text = "✦ ${result.headline}",
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
                        "⚠ $caveat",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberRed,
                    )
                }
                // Numeric or yes/no answers can be flashed full-screen.
                val leadingNumber = result.headline.takeWhile { it.isDigit() }.toIntOrNull()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (leadingNumber != null && leadingNumber <= 9) {
                        AssistChip(
                            onClick = { onShow(ShowCard.NumberCard(leadingNumber)) },
                            label = { Text("Show $leadingNumber full-screen") },
                        )
                    }
                    if (result.headline.startsWith("YES") || result.headline.startsWith("NO")) {
                        AssistChip(
                            onClick = {
                                onShow(ShowCard.Message(if (result.headline.startsWith("YES")) "YES" else "NO"))
                            },
                            label = { Text("Show answer full-screen") },
                        )
                    }
                }
            }
        }
    }
}
