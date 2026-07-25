package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameState
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Team
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.TownsfolkBlue

/** Chronological record of everything that happened, derived from state. */
@Composable
fun GameLogDialog(state: GameState, onDismiss: () -> Unit) {
    data class Entry(val day: Int, val atNight: Boolean, val text: String)

    val entries = remember(state) {
        val list = mutableListOf<Entry>()
        for (d in state.deaths) {
            val name = state.player(d.playerId)?.name ?: "?"
            val cause = when (d.cause) {
                DeathCause.EXECUTION -> "executed"
                DeathCause.DEMON -> "died in the night"
                DeathCause.OTHER_NIGHT_DEATH -> "died in the night (other)"
                DeathCause.EXILE -> "exiled"
                DeathCause.STORYTELLER -> "died (storyteller)"
            }
            list += Entry(d.day, d.atNight, "$name $cause")
        }
        for (n in state.nominations) {
            val nominator = state.player(n.nominatorId)?.name ?: "?"
            val nominee = state.player(n.nomineeId)?.name ?: "?"
            val outcome = when (n.result) {
                NominationResult.ABOUT_TO_DIE -> "reached the block"
                NominationResult.TIED -> "tied"
                NominationResult.SAFE -> "safe"
                NominationResult.WITHDRAWN -> "withdrawn"
            }
            list += Entry(
                n.day, false,
                "$nominator nominated $nominee${if (n.isExile) " (exile)" else ""} — ${n.votes} votes, $outcome",
            )
        }
        list.sortedWith(compareBy({ it.day }, { !it.atNight }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game log") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entries.isEmpty()) {
                    item { Text("Nothing has happened yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(entries.size) { i ->
                    val e = entries[i]
                    Row {
                        Text(
                            text = (if (e.atNight) "N" else "D") + "${e.day}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgedGold,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Up/down seat reordering, wrapping around the circle. */
@Composable
fun ReorderSeatsDialog(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seat order (clockwise)") },
        text = {
            LazyColumn {
                items(state.players, key = { it.id }) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            p.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { viewModel.update { s -> com.clocktower.engine.GameActions.moveSeat(s, p.id, -1) } }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.update { s -> com.clocktower.engine.GameActions.moveSeat(s, p.id, +1) } }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Toggleable list of Fabled to bring into the game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabledSheet(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Fabled", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Tap to add or remove. Active fabled appear on the grimoire and in the night order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(viewModel.gameData.allFabled, key = { "fab-" + it.id }) { c ->
                val active = c.id in state.fabledIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setFabled(
                                if (active) state.fabledIds - c.id else state.fabledIds + c.id,
                            )
                        }
                        .padding(vertical = 4.dp),
                ) {
                    CharacterToken(character = c, size = 44.dp, dimmed = !active)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name + if (active) "  ✓" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) AgedGold else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            c.ability,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Jinxes among the characters actually assigned right now. */
@Composable
fun ActiveJinxesDialog(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    val inPlay = state.players.mapNotNull { it.characterId } + state.fabledIds
    val jinxes = remember(inPlay) { viewModel.gameData.activeJinxes(inPlay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jinxes in play (${jinxes.size})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (jinxes.isEmpty()) {
                    item { Text("No jinxed pairs among assigned characters.") }
                }
                items(jinxes.size) { i ->
                    val j = jinxes[i]
                    Column {
                        Text(
                            "${viewModel.gameData.character(j.id1)?.name} ✕ ${viewModel.gameData.character(j.id2)?.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = AgedGold,
                        )
                        Text(j.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Win-condition advisory with a path to the reveal screen. */
@Composable
fun WinAdvisoryDialog(
    advisory: WinCheck.Advisory,
    onDeclare: (goodWins: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Is the game over?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(advisory.reason, style = MaterialTheme.typography.bodyLarge)
                for (c in advisory.cautions) {
                    Text("⚠ $c", color = EmberRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onDeclare(advisory.goodWins ?: true) }) {
                Text(if (advisory.goodWins == false) "Declare evil victory" else "Declare good victory")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep playing") } },
    )
}

/** End-of-game reveal: the full cast, alignments, and how everyone died. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevealSheet(
    viewModel: GameViewModel,
    state: GameState,
    goodWins: Boolean,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    if (goodWins) "GOOD WINS" else "EVIL WINS",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (goodWins) TownsfolkBlue else EmberRed,
                )
                Text(
                    "${state.script.name} · ${state.players.size} players · ${state.cycle} ${if (state.cycle == 1) "day" else "days"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.players, key = { "reveal-" + it.id }) { p ->
                val character = viewModel.characterById(p.characterId)
                val evil = p.isEvil(viewModel::characterById)
                val death = state.deaths.lastOrNull { it.playerId == p.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterToken(character = character, size = 44.dp, dimmed = !p.alive)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (evil) EmberRed else TownsfolkBlue,
                        )
                        Text(
                            buildString {
                                append(character?.name ?: "no character")
                                p.shownCharacterId?.let { shownId ->
                                    append(" · shown as ")
                                    append(viewModel.characterById(shownId)?.name ?: shownId)
                                }
                                character?.team?.let { if (it == Team.TRAVELLER) append(" (traveller)") }
                                if (death != null) {
                                    append(" · ")
                                    append(
                                        when (death.cause) {
                                            DeathCause.EXECUTION -> "executed day ${death.day}"
                                            DeathCause.DEMON -> "killed night ${death.day}"
                                            DeathCause.OTHER_NIGHT_DEATH -> "died night ${death.day}"
                                            DeathCause.EXILE -> "exiled day ${death.day}"
                                            DeathCause.STORYTELLER -> "died day ${death.day}"
                                        },
                                    )
                                } else if (p.alive) {
                                    append(" · survived")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onNewGame) { Text("End game & return home") }
                    TextButton(onClick = onDismiss) { Text("Back to grimoire") }
                }
            }
        }
    }
}
