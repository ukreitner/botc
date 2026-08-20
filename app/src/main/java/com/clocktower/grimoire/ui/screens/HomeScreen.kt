package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.clocktower.engine.GameState
import com.clocktower.engine.NotesState
import com.clocktower.engine.Phase
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.FadedInk
import com.clocktower.grimoire.ui.theme.Midnight
import com.clocktower.grimoire.ui.theme.NightSky
import com.clocktower.grimoire.ui.theme.PaleGold
import com.clocktower.grimoire.ui.theme.Twilight
import kotlin.random.Random

/** Landing screen: resume, new game, or browse the library. */
@Composable
fun HomeScreen(
    game: GameState?,
    notes: NotesState? = null,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onResumeNotes: () -> Unit = {},
    onNewNotes: () -> Unit = {},
    onLibrary: () -> Unit,
    onEndGame: () -> Unit,
    onEndNotes: () -> Unit = {},
    // Short build id shown as a footer, so it's obvious which build is
    // installed (handy right after an in-app update). Hidden when blank.
    buildLabel: String = "",
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmEndNotes by rememberSaveable { mutableStateOf(false) }

    // A quiet night sky behind everything: fixed stars, a crescent moon,
    // and a candle-glow pooling behind the title. Drawn, not image assets.
    val stars = remember {
        val rnd = Random(1913)
        List(70) {
            Triple(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat() * 1.1f)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(NightSky)
                // Candlelight rising from behind the title block.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Twilight, Midnight.copy(alpha = 0f)),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.width * 0.9f,
                    ),
                )
                for ((fx, fy, r) in stars) {
                    // Keep the lower third mostly clear — that's button land.
                    val y = fy * size.height * 0.62f
                    val alpha = 0.10f + (r - 0.4f) * 0.22f
                    drawCircle(
                        color = PaleGold.copy(alpha = alpha),
                        radius = r * density,
                        center = Offset(fx * size.width, y),
                    )
                }
                // Crescent moon, top right — a real path difference, so it
                // renders correctly over any backdrop and any aspect ratio.
                val moonR = minOf(size.width, size.height) * 0.055f
                val moonC = Offset(size.width * 0.82f, size.height * 0.10f)
                val disc = Path().apply {
                    addOval(Rect(moonC - Offset(moonR, moonR), Size(moonR * 2, moonR * 2)))
                }
                val bite = Path().apply {
                    val c = moonC + Offset(moonR * 0.45f, -moonR * 0.28f)
                    val r = moonR * 0.86f
                    addOval(Rect(c - Offset(r, r), Size(r * 2, r * 2)))
                }
                drawPath(
                    Path.combine(PathOperation.Difference, disc, bite),
                    color = AgedGold.copy(alpha = 0.8f),
                )
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Clocktower",
            style = MaterialTheme.typography.displayLarge.copy(
                shadow = Shadow(
                    color = AgedGold.copy(alpha = 0.45f),
                    blurRadius = 26f,
                ),
            ),
            color = AgedGold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Grimoire",
            style = MaterialTheme.typography.displayMedium,
            color = FadedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A storyteller's companion for Blood on the Clocktower",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        // A hairline gold rule, fading out at both ends.
        Spacer(
            Modifier
                .height(1.dp)
                .fillMaxWidth(0.55f)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AgedGold.copy(alpha = 0.65f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
        )
        Spacer(Modifier.height(28.dp))

        if (game != null) {
            FilledTonalButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text("Resume game", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${game.script.name} · ${game.players.size} players · " +
                            when (game.phase) {
                                Phase.SETUP -> "setting up"
                                Phase.NIGHT -> if (game.cycle == 1) "first night" else "night ${game.cycle}"
                                Phase.DAY -> "day ${game.cycle}"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        ElevatedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
            Text("New game (storyteller)", modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))

        if (notes != null) {
            FilledTonalButton(onClick = onResumeNotes, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text("Resume player notes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${notes.script.name} · ${notes.seats.size} seats · day ${notes.day}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            ElevatedButton(onClick = onNewNotes, modifier = Modifier.fillMaxWidth()) {
                Text("Take notes (player)", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("Character library & night order", modifier = Modifier.padding(vertical = 6.dp))
        }
        if (game != null || notes != null) {
            Spacer(Modifier.height(24.dp))
        }
        if (game != null) {
            TextButton(onClick = { confirmEnd = true }) {
                Text("End current game", color = MaterialTheme.colorScheme.error)
            }
        }
        if (notes != null) {
            TextButton(onClick = { confirmEndNotes = true }) {
                Text("End notes session", color = MaterialTheme.colorScheme.error)
            }
        }
        if (buildLabel.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "build $buildLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End game?") },
            text = { Text("This clears the saved grimoire. There is no undo across games.") },
            confirmButton = {
                FilledTonalButton(onClick = { confirmEnd = false; onEndGame() }) { Text("End game") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep playing") } },
        )
    }
    if (confirmEndNotes) {
        AlertDialog(
            onDismissRequest = { confirmEndNotes = false },
            title = { Text("End notes session?") },
            text = { Text("This clears your player notes. There is no undo across sessions.") },
            confirmButton = {
                FilledTonalButton(onClick = { confirmEndNotes = false; onEndNotes() }) { Text("End notes") }
            },
            dismissButton = { TextButton(onClick = { confirmEndNotes = false }) { Text("Keep them") } },
        )
    }
}
