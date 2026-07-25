package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Player
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.theme.BloodRed
import com.clocktower.grimoire.ui.theme.color

/** Bottom sheet with every storyteller action for one seat. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeatSheet(
    viewModel: GameViewModel,
    state: GameState,
    playerId: Long,
    onDismiss: () -> Unit,
) {
    val player = state.player(playerId)
    // Dismiss as an effect, not during composition, if the seat vanished
    // (undo of an added seat, removal from another control).
    LaunchedEffect(player == null) {
        if (player == null) onDismiss()
    }
    if (player == null) return
    val character = viewModel.characterById(player.characterId)
    var mode by rememberSaveable { mutableStateOf(SeatSheetMode.ACTIONS) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (mode) {
            SeatSheetMode.ACTIONS -> SeatActions(
                viewModel = viewModel,
                state = state,
                player = player,
                character = character,
                onPickCharacter = { mode = SeatSheetMode.PICK_CHARACTER },
                onAddReminder = { mode = SeatSheetMode.ADD_REMINDER },
                onDismiss = onDismiss,
            )
            SeatSheetMode.PICK_CHARACTER -> CharacterPicker(
                viewModel = viewModel,
                state = state,
                onPick = { picked, traveller ->
                    viewModel.assign(player.id, picked?.id, traveller)
                    mode = SeatSheetMode.ACTIONS
                },
                onBack = { mode = SeatSheetMode.ACTIONS },
            )
            SeatSheetMode.ADD_REMINDER -> ReminderPicker(
                viewModel = viewModel,
                state = state,
                onPick = { reminder ->
                    viewModel.addReminder(player.id, reminder)
                    mode = SeatSheetMode.ACTIONS
                },
                onBack = { mode = SeatSheetMode.ACTIONS },
            )
        }
    }
}

private enum class SeatSheetMode { ACTIONS, PICK_CHARACTER, ADD_REMINDER }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatActions(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    character: Character?,
    onPickCharacter: () -> Unit,
    onAddReminder: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(player.id) { mutableStateOf(player.name) }
    var note by rememberSaveable(player.id) { mutableStateOf(player.note) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CharacterToken(character = character, size = 72.dp, dimmed = !player.alive)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = buildString {
                        append(character?.name ?: "No character")
                        character?.team?.let { append(" · ${it.displayName}") }
                        if (player.alignmentFlipped) append(" · turned ${if (player.isEvil(viewModel::characterById)) "evil" else "good"}")
                        if (!player.alive) append(" · dead")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        character?.ability?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        // Life & death
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (player.alive) {
                FilledTonalButton(onClick = { viewModel.kill(player.id, DeathCause.DEMON) }) {
                    Text("Died at night")
                }
                FilledTonalButton(onClick = { viewModel.kill(player.id, DeathCause.EXECUTION) }) {
                    Text("Executed")
                }
                OutlinedButton(onClick = { viewModel.kill(player.id, DeathCause.STORYTELLER) }) {
                    Text("Other death")
                }
            } else {
                FilledTonalButton(onClick = { viewModel.revive(player.id) }) { Text("Revive") }
                OutlinedButton(onClick = { viewModel.toggleGhostVote(player.id) }) {
                    Text(if (player.ghostVoteUsed) "Restore ghost vote" else "Use ghost vote")
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickCharacter) { Text("Change character") }
            OutlinedButton(onClick = onAddReminder) { Text("Add reminder") }
            OutlinedButton(onClick = { viewModel.flipAlignment(player.id) }) { Text("Flip alignment") }
            if (state.phase == Phase.SETUP || player.isTraveller) {
                OutlinedButton(onClick = { viewModel.removeSeat(player.id); onDismiss() }) {
                    Text("Remove seat", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (player.reminders.isNotEmpty()) {
            Text("Reminders (tap to remove)", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                player.reminders.forEachIndexed { index, reminder ->
                    val source = viewModel.characterById(reminder.sourceId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.removeReminder(player.id, index) },
                    ) {
                        ReminderToken(
                            label = reminder.label,
                            color = source?.team?.color ?: BloodRed,
                            size = 44.dp,
                        )
                        source?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Player name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Seat notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    if (name != player.name) viewModel.rename(player.id, name)
                    if (note != player.note) viewModel.setNote(player.id, note)
                    onDismiss()
                },
            ) { Text("Save & close") }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

/** Grid of script characters (plus travellers) to assign to a seat. */
@Composable
fun CharacterPicker(
    viewModel: GameViewModel,
    state: GameState,
    onPick: (Character?, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val script = state.script
    val scriptCharacters = remember(script) { viewModel.gameData.resolve(script) }
    val travellers = remember(script) { viewModel.gameData.travellersFor(script) }
    val inPlay = state.players.mapNotNull { it.characterId }.toSet()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose character", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        item {
            AssistChip(onClick = { onPick(null, false) }, label = { Text("Clear seat (no character)") })
        }
        val groups = scriptCharacters
            .filter { it.team != Team.TRAVELLER && it.team != Team.FABLED }
            .groupBy { it.team }
        for ((team, members) in groups) {
            item {
                Text(
                    team.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = team.color,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(members, key = { "pick-" + it.id }) { c ->
                CharacterPickRow(c, inPlay.contains(c.id)) { onPick(c, false) }
            }
        }
        item {
            Text(
                "Travellers",
                style = MaterialTheme.typography.titleMedium,
                color = Team.TRAVELLER.color,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(travellers, key = { "pick-t-" + it.id }) { c ->
            CharacterPickRow(c, inPlay.contains(c.id)) { onPick(c, true) }
        }
    }
}

@Composable
private fun CharacterPickRow(character: Character, inPlay: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        CharacterToken(character = character, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(character.name, style = MaterialTheme.typography.titleSmall)
                if (inPlay) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "in play",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                character.ability,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Reminder tokens from characters in play, then the whole script, then generic. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPicker(
    viewModel: GameViewModel,
    state: GameState,
    onPick: (PlacedReminder) -> Unit,
    onBack: () -> Unit,
) {
    val scriptCharacters = remember(state.script) { viewModel.gameData.resolve(state.script) }
    val inPlayIds = state.players.mapNotNull { it.characterId }.toSet()
    val (inPlay, offScript) = scriptCharacters.partition { it.id in inPlayIds }

    val generic = listOf("Drunk", "Poisoned", "Dead", "Protected", "Mad", "Good", "Evil", "Used", "?")

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add reminder", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        item {
            Text("Generic", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (label in generic) {
                    Box(Modifier.clickable { onPick(PlacedReminder("", label)) }) {
                        ReminderToken(label = label, color = BloodRed, size = 48.dp)
                    }
                }
            }
        }
        for ((title, group) in listOf("In play" to inPlay, "Rest of script" to offScript)) {
            val withReminders = group.filter { it.allReminders.isNotEmpty() }
            if (withReminders.isEmpty()) continue
            item {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(withReminders, key = { "rem-" + it.id }) { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.width(110.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (label in c.allReminders) {
                            Box(Modifier.clickable { onPick(PlacedReminder(c.id, label)) }) {
                                ReminderToken(label = label, color = c.team.color, size = 44.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
