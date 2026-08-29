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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Bluffs
import com.clocktower.engine.GameState
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.Time
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.bottomActionPadding
import com.clocktower.grimoire.ui.components.rememberOverlayInsets
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import kotlin.random.Random

/**
 * Bluff sets, one tab per recipient (setup-and-home §S5).
 *
 * The tabs come straight from `Bluffs.requirements`: the Demon's three, one
 * INDEPENDENT set per Minion under a Snitch, the Summoner's set while no Demon
 * exists, and the Lunatic's own — the only set that may legally name characters
 * that are in play. No character ids anywhere in this file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluffsSheet(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
    /** Open straight onto one requirement's tab (from the checklist). */
    initialKey: String? = null,
) {
    val lookup = viewModel::characterById
    val requirements = remember(state) { Bluffs.requirements(state, lookup) }
    val script = remember(state.script) { SetupRequirements.scriptCharacters(state, lookup) }
    var tab by rememberSaveable {
        mutableStateOf(requirements.indexOfFirst { it.key == initialKey }.coerceAtLeast(0))
    }
    var query by rememberSaveable { mutableStateOf("") }
    val safeTab = tab.coerceIn(0, (requirements.size - 1).coerceAtLeast(0))

    // Measured OUTSIDE the sheet, where the numbers still exist (D82,
    // components/SafeArea.kt).
    val insets = rememberOverlayInsets()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // D2-5: half-expanded, the sheet was measured against the full content
        // height anyway and simply overflowed — the list's container ran to
        // y=2400, the physical bottom of the display, and its last two rows
        // were under the gesture strip with one centre untappable. Fully
        // expanded the content is bounded by the screen, as the checklist's is.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (requirements.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Bluffs", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "This game owes nobody bluffs — a Lil' Monsta or an Atheist game " +
                        "skips both info steps, and a Teensyville under 7 has no Demon set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(1.dp))
            }
            return@ModalBottomSheet
        }

        val requirement = requirements[safeTab]
        val chosen = state.bluffSets[requirement.key].orEmpty()
        val candidates = remember(state, requirement.key) {
            Bluffs.candidates(state, script, requirement)
        }
        val filtered = candidates.filter {
            query.isBlank() ||
                it.character.name.contains(query, ignoreCase = true) ||
                it.character.ability.contains(query, ignoreCase = true)
        }

        if (requirements.size > 1) {
            // A full-height sheet starts 8 px ABOVE the status-bar inset, which
            // is what put the tab row's top edge under the cutout (D2-5).
            ScrollableTabRow(
                selectedTabIndex = safeTab,
                edgePadding = 12.dp,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                requirements.forEachIndexed { index, req ->
                    val full = state.bluffSets[req.key].orEmpty().size >= req.size
                    Tab(
                        selected = index == safeTab,
                        onClick = { tab = index },
                        text = {
                            Text(
                                req.label + if (full) " ✓" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                // LAYOUT padding, so the CONTAINER stops inside the safe area:
                // `overlayBottomPadding` could only offer its own 24 dp margin
                // from in here (a ModalBottomSheet reports its insets as
                // consumed), and [insets] carries the real number in from
                // outside. D82's warning is about compounding this with
                // `imePadding` until the viewport cannot hold a search result —
                // this list takes one or the other, never both.
                .padding(bottom = bottomActionPadding(insets.bottom)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item("bluff-head-${requirement.key}") {
                Text(
                    requirement.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AgedGold,
                )
                Text(
                    "${chosen.size}/${requirement.size} chosen — tap to toggle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (requirement.reason.isNotBlank()) {
                    Text(
                        requirement.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                requirement.recipientId?.let { id ->
                    state.player(id)?.let { seat ->
                        Text(
                            "Shown to ${seat.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!requirement.required) {
                    Text(
                        "Optional in this game.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            viewModel.setBluffSet(
                                requirement.key,
                                // Drawn independently per recipient, so two
                                // sets do not collide by accident.
                                Bluffs.suggest(
                                    state,
                                    script,
                                    requirement,
                                    Random(Time.epochMillis() + requirement.key.hashCode()),
                                ),
                            )
                        },
                        label = { Text("Suggest ${requirement.size}") },
                    )
                    AssistChip(
                        onClick = { viewModel.clearBluffSet(requirement.key) },
                        label = { Text("Clear") },
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search names and abilities") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                // "Fisherman is one of the Demon's bluffs and is now in play."
                for (conflict in Bluffs.conflicts(state, lookup)) {
                    Text(conflict, color = EmberRed, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(filtered, key = { "bluff-" + requirement.key + "-" + it.character.id }) { candidate ->
                val id = candidate.character.id
                val selected = id in chosen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setBluffSet(
                                requirement.key,
                                when {
                                    selected -> chosen - id
                                    chosen.size < requirement.size -> chosen + id
                                    else -> chosen
                                },
                            )
                        }
                        .padding(vertical = 4.dp),
                ) {
                    CharacterToken(
                        character = candidate.character,
                        size = 42.dp,
                        dimmed = !selected && chosen.size >= requirement.size,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                candidate.character.name + if (selected) "  •" else "",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) AgedGold else MaterialTheme.colorScheme.onSurface,
                            )
                            if (candidate.inPlay) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "IN PLAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmberRed,
                                )
                            }
                        }
                        // Legal, but the storyteller wants to know: a Drunk's
                        // believed Townsfolk, a Boffin or Alchemist grant.
                        candidate.inUseBy?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AgedGold)
                        }
                        Text(
                            candidate.character.ability,
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

/** Kept so a caller that only wants one chip can still render it. */
@Composable
internal fun BluffChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
