package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.GameState
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold

/**
 * Demon bluff picker: choose up to three not-in-play good characters to
 * show the demon on the first night.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluffsSheet(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    val inPlay = state.players.mapNotNull { it.characterId }.toSet()
    val candidates = remember(state.script, inPlay) {
        viewModel.gameData.resolve(state.script)
            .filter { it.team == Team.TOWNSFOLK || it.team == Team.OUTSIDER }
            .filter { it.id !in inPlay }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Demon bluffs", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "${state.demonBluffIds.size}/3 chosen — tap to toggle. " +
                        "Only not-in-play good characters are listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.AssistChip(
                    onClick = {
                        viewModel.setBluffs(
                            com.clocktower.engine.GameActions.suggestBluffs(
                                viewModel.gameData.resolve(state.script),
                                state,
                            ),
                        )
                    },
                    label = { Text("Suggest 3 for me") },
                )
                Spacer(Modifier.width(1.dp))
            }
            items(candidates, key = { "bluff-" + it.id }) { c ->
                val selected = c.id in state.demonBluffIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val current = state.demonBluffIds
                            viewModel.setBluffs(
                                if (selected) current - c.id
                                else if (current.size < 3) current + c.id
                                else current,
                            )
                        }
                        .padding(vertical = 4.dp),
                ) {
                    CharacterToken(character = c, size = 44.dp, dimmed = !selected && state.demonBluffIds.size >= 3)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name + if (selected) "  ✓" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) AgedGold else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            c.ability,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
