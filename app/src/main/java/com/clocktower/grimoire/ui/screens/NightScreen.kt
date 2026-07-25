package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.clocktower.engine.GameState
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.NightStep
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.Twilight

/**
 * The night sheet: only characters in play, in official wake order, with
 * storyteller prompts. Steps check off as the night proceeds.
 */
@Composable
fun NightScreen(
    viewModel: GameViewModel,
    state: GameState,
) {
    val isFirstNight = state.cycle == 1
    val steps = remember(state.players, state.fabledIds, state.cycle) {
        if (isFirstNight) {
            viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
        } else {
            viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = if (isFirstNight) "First Night" else "Night ${state.cycle}",
                style = MaterialTheme.typography.headlineMedium,
                color = AgedGold,
            )
            Text(
                text = "${steps.count { it.id in state.nightStepsDone }} of ${steps.size} steps done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(1.dp))
        }
        items(steps, key = { it.id }) { step ->
            NightStepRow(
                viewModel = viewModel,
                state = state,
                step = step,
                done = step.id in state.nightStepsDone,
                onToggle = { viewModel.toggleNightStep(step.id) },
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
}

@Composable
private fun NightStepRow(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    done: Boolean,
    onToggle: () -> Unit,
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
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            if (!isMarker) {
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
                if (step.detail.isNotBlank()) {
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
    }
}
