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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.clocktower.grimoire.ui.theme.NightSky
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.Twilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    onOpenBluffs: () -> Unit = {},
    onOpenFabled: () -> Unit = {},
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
            // Candlelit table: a warm vignette pooling at the centre of the
            // circle, and a faint gold ring the seats appear to rest on.
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Twilight, NightSky),
                        center = center,
                        radius = kotlin.math.max(w, h) / 1.4f,
                    ),
                )
                // The decorative ring follows the SAME ellipse the seats
                // sit on, so the two never disagree.
                val childMax = SeatGeometry.childMax(state.players.size.coerceAtLeast(1), w.toInt(), h.toInt())
                val inset = childMax / 2f + 8.dp.toPx()
                val rx = w / 2f - inset
                val ry = h / 2f - inset
                if (rx > 0 && ry > 0) {
                    drawOval(
                        color = AgedGold.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - rx, center.y - ry),
                        size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawOval(
                        color = AgedGold.copy(alpha = 0.05f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - rx * 0.55f, center.y - ry * 0.55f),
                        size = androidx.compose.ui.geometry.Size(rx * 1.1f, ry * 1.1f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
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
                    compactLevel = when {
                        state.players.size > 16 -> 2
                        state.players.size > 12 -> 1
                        else -> 0
                    },
                    wakeNumber = player.nightRoleId?.let { wakeOrder[it] },
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
        // Quick edit access: bluffs on the left, fabled on the right — both
        // tappable, both always one gesture away.
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp, start = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenBluffs)
                .padding(2.dp),
        ) {
            if (state.demonBluffIds.isEmpty()) {
                Text(
                    "+ bluffs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (id in state.demonBluffIds) {
                    CharacterToken(character = viewModel.characterById(id), size = 30.dp)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenFabled)
                .padding(2.dp),
        ) {
            if (state.fabledIds.isEmpty()) {
                Text(
                    "fabled +",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (id in state.fabledIds) {
                    CharacterToken(character = viewModel.characterById(id), size = 30.dp)
                }
            }
        }

        if (scale != 1f || offsetX != 0f || offsetY != 0f) {
            FilledTonalIconButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(48.dp),
            ) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = "Reset zoom and recenter grimoire",
                )
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

        val childMax = SeatGeometry.childMax(count, width, height)
        val childConstraints = Constraints(maxWidth = childMax, maxHeight = childMax * 2)
        val placeables = measurables.map { it.measure(childConstraints) }

        val inset = childMax / 2f + 8.dp.toPx()
        val radiusX = width / 2f - inset
        val radiusY = height / 2f - inset
        val angles = SeatGeometry.equalArcAngles(count, radiusX, radiusY)

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val angle = angles[index]
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

/**
 * Shared seat-ring geometry: the layout and the decorative background use
 * the SAME ellipse, and seats are spread by equal ARC LENGTH so they don't
 * bunch at the flat top and bottom of a tall screen.
 */
object SeatGeometry {

    fun childMax(count: Int, width: Int, height: Int): Int = when {
        count <= 8 -> (min(width, height) / 3.5f).toInt()
        count <= 12 -> (min(width, height) / 4.4f).toInt()
        else -> (min(width, height) / 5.4f).toInt()
    }

    /**
     * [count] angles starting at 12 o'clock, clockwise, spaced so the
     * distance travelled ALONG the ellipse between neighbours is equal.
     */
    fun equalArcAngles(count: Int, radiusX: Float, radiusY: Float): List<Double> {
        if (count <= 0) return emptyList()
        val samples = 1440
        val step = 2 * kotlin.math.PI / samples
        // Cumulative arc length from the top of the ellipse.
        val cumulative = DoubleArray(samples + 1)
        for (i in 1..samples) {
            val t = -kotlin.math.PI / 2 + step * (i - 0.5)
            val dx = -radiusX * kotlin.math.sin(t)
            val dy = radiusY * kotlin.math.cos(t)
            cumulative[i] = cumulative[i - 1] + kotlin.math.sqrt(dx * dx + dy * dy) * step
        }
        val total = cumulative[samples]
        val angles = ArrayList<Double>(count)
        var cursor = 0
        for (k in 0 until count) {
            val target = total * k / count
            while (cursor < samples && cumulative[cursor + 1] < target) cursor++
            angles.add(-kotlin.math.PI / 2 + step * cursor)
        }
        return angles
    }
}

/** One seat: name, token (with shroud when dead), ghost vote, reminders. */
@Composable
private fun SeatView(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    compactLevel: Int,
    wakeNumber: Int?,
    onClick: () -> Unit,
) {
    val character = viewModel.characterById(player.characterId)
    val isEvil = player.isEvil(viewModel::characterById)
    val impaired = com.clocktower.engine.StatusEffects.isImpaired(state, viewModel::characterById, player)
    val seatDescription = buildString {
        append(player.name)
        append(", ")
        append(character?.name ?: "no character assigned")
        player.shownCharacterId?.let { shownId ->
            append(", shown as ")
            append(viewModel.characterById(shownId)?.name ?: shownId)
        }
        append(if (player.alive) ", alive" else ", dead")
        if (character != null) append(if (isEvil) ", evil" else ", good")
        if (player.isTraveller) append(", traveller")
        if (!player.alive) {
            append(if (player.ghostVoteUsed) ", ghost vote spent" else ", ghost vote available")
        }
        if (impaired) append(", drunk or poisoned")
        if (player.reminders.isNotEmpty()) {
            append(", reminders: ")
            append(player.reminders.joinToString { it.label })
        }
    }
    // Larger faces: the art should be readable across the table.
    val tokenSize = when (compactLevel) {
        2 -> 56.dp
        1 -> 62.dp
        else -> 74.dp
    }
    val visibleReminders = if (compactLevel == 0) 4 else 2
    val reminderSize = if (compactLevel == 0) 26.dp else 22.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = seatDescription
            }
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
                size = tokenSize,
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
            if (impaired) {
                // Drawn badge, not an emoji: a small poison-green dot.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4C7A3D))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", style = MaterialTheme.typography.labelSmall, color = Color.White)
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
        // The character's full name, never truncated: two lines allowed.
        if (character != null) {
            Text(
                text = character.name,
                fontSize = (tokenSize.value / 6f).coerceIn(9f, 13f).sp,
                lineHeight = (tokenSize.value / 5.4f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = Parchment.copy(alpha = if (player.alive) 0.95f else 0.5f),
            )
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
                // Newly placed nightly reminders are the ones the
                // storyteller most urgently needs to see at a glance.
                for (reminder in player.reminders.takeLast(visibleReminders)) {
                    val source = viewModel.characterById(reminder.sourceId)
                    ReminderToken(
                        label = reminder.label,
                        color = source?.team?.color ?: BloodRed,
                        size = reminderSize,
                    )
                }
                if (player.reminders.size > visibleReminders) {
                    Text(
                        "+${player.reminders.size - visibleReminders}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
