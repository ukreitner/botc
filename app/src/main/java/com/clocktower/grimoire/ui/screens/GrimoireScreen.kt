package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.clocktower.engine.GameState
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.BloodRed
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The grimoire: every seat arranged in a circle exactly like tokens laid
 * out inside the physical grimoire, with shrouds on the dead, reminder
 * tokens fanned around each seat, pinch-zoom and pan.
 */
@Composable
fun GrimoireScreen(
    viewModel: GameViewModel,
    state: GameState,
    onOpenSeat: (Long) -> Unit,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    // During the night, badge each seat with its wake-order position.
    val wakeOrder: Map<String, Int> = remember(state.players, state.phase, state.cycle, state.fabledIds) {
        if (state.phase != Phase.NIGHT) {
            emptyMap()
        } else {
            val steps = if (state.cycle == 1) {
                viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
            } else {
                viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
            }
            steps.filter { it.id !in NightMarkers.all && it.playerIds.isNotEmpty() }
                .mapIndexed { index, step -> step.id to index + 1 }
                .toMap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 2.5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
    ) {
        CircleLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        ) {
            for (player in state.players) {
                SeatView(
                    viewModel = viewModel,
                    state = state,
                    player = player,
                    wakeNumber = player.characterId?.let { wakeOrder[it] },
                    onClick = { onOpenSeat(player.id) },
                )
            }
        }

        // Standing facts every storyteller keeps re-deriving.
        val ghostVotes = state.players.count { !it.alive && !it.ghostVoteUsed }
        Text(
            text = "${state.alivePlayers.size} alive · ${state.executionThreshold} to execute · $ghostVotes ghost vote${if (ghostVotes == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
        )
        if (state.fabledIds.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 8.dp),
            ) {
                for (id in state.fabledIds) {
                    CharacterToken(character = viewModel.characterById(id), size = 34.dp)
                }
            }
        }

        if (state.players.isEmpty()) {
            Text(
                text = "No seats yet — add players from setup.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lays children out evenly around an ellipse inscribed in the available
 * space, first child at 12 o'clock, proceeding clockwise (matching how a
 * storyteller reads the room).
 */
@Composable
fun CircleLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val count = measurables.size
        if (count == 0) {
            return@Layout layout(width, height) {}
        }

        // Child size scales down as the circle fills up.
        val childMax = when {
            count <= 8 -> min(width, height) / 4
            count <= 12 -> min(width, height) / 5
            else -> min(width, height) / 6
        }
        val childConstraints = Constraints(maxWidth = childMax, maxHeight = childMax * 2)
        val placeables = measurables.map { it.measure(childConstraints) }

        val radiusX = width / 2f - childMax / 2f - 8.dp.toPx()
        val radiusY = height / 2f - childMax / 2f - 8.dp.toPx()

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val angle = -Math.PI / 2 + 2 * Math.PI * index / count
                val cx = width / 2f + radiusX * cos(angle).toFloat()
                val cy = height / 2f + radiusY * sin(angle).toFloat()
                placeable.place(
                    x = (cx - placeable.width / 2f).toInt(),
                    y = (cy - placeable.height / 2f).toInt(),
                )
            }
        }
    }
}

/** One seat: name, token (with shroud when dead), ghost vote, reminders. */
@Composable
private fun SeatView(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    wakeNumber: Int?,
    onClick: () -> Unit,
) {
    val character = viewModel.characterById(player.characterId)
    val isEvil = player.isEvil(viewModel::characterById)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Text(
            // The storyteller sees true alignment at a glance: evil names in
            // ember red, good in parchment (dimmed when dead).
            text = player.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                !player.alive -> MaterialTheme.colorScheme.onSurfaceVariant
                isEvil && character != null -> EmberRed
                else -> MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Box(contentAlignment = Alignment.Center) {
            CharacterToken(
                character = character,
                size = 64.dp,
                dimmed = !player.alive,
            )
            if (!player.alive) {
                // The shroud: a dark drape over the top of the token.
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 46.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp))
                        .background(Color(0xE6151020))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp),
                        ),
                )
            }
            if (player.isTraveller) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AgedGold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("T", style = MaterialTheme.typography.labelSmall, color = Color.Black)
                }
            }
            if (wakeNumber != null && player.alive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2040)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$wakeNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = AgedGold,
                    )
                }
            }
        }
        if (!player.alive) {
            Text(
                text = if (player.ghostVoteUsed) "no vote" else "ghost vote",
                style = MaterialTheme.typography.labelSmall,
                color = if (player.ghostVoteUsed) MaterialTheme.colorScheme.onSurfaceVariant else AgedGold,
            )
        }
        if (player.reminders.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (reminder in player.reminders.take(4)) {
                    val source = viewModel.characterById(reminder.sourceId)
                    ReminderToken(
                        label = reminder.label,
                        color = source?.team?.color ?: BloodRed,
                        size = 26.dp,
                    )
                }
                if (player.reminders.size > 4) {
                    Text(
                        "+${player.reminders.size - 4}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
