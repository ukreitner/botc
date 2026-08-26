package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.NightGuide
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.Script
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.color

/**
 * Script reference: the character sheet by team, the full night order, and the
 * jinxes — everything on the printed sheets, searchable in play.
 *
 * With a live [state] it also marks what is IN PLAY and in which seat
 * (setup-and-home §S7, defect #26), and offers Travellers and Fabled as their
 * own sections, which `builtIn()` filters out of the script itself (#28).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(
    viewModel: GameViewModel,
    script: Script,
    /** The running game, when there is one: enables the in-play column. */
    state: GameState? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable(script.id) { mutableStateOf("") }
    var inPlayOnly by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    val scriptCharacters = remember(script) { viewModel.gameData.resolve(script) }
    val travellers = remember(script) { viewModel.gameData.travellersFor(script) }
    val fabled = remember(script, state) {
        val active = state?.fabledIds.orEmpty().toSet()
        viewModel.gameData.allFabled.filter { it.id in active }
    }
    val characters = remember(scriptCharacters, travellers, fabled) {
        (scriptCharacters + travellers + fabled).distinctBy { it.id }
    }
    // Which seats hold which character, so a row can say "seat 3 (Ari)".
    val seatsById = remember(state) {
        state?.seats.orEmpty()
            .mapIndexedNotNull { index, seat ->
                seat.characterId?.let { Character.normalizeId(it) to "seat ${index + 1} (${seat.name})" }
            }
            .groupBy({ it.first }, { it.second })
    }
    val jinxes = remember(characters, inPlayOnly, seatsById) {
        val ids = if (inPlayOnly && state != null) seatsById.keys else characters.map { it.id }.toSet()
        viewModel.gameData.activeJinxes(ids + state?.fabledIds.orEmpty())
    }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Characters") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Night order") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Jinxes (${jinxes.size})") })
        }
        when (tab) {
            0 -> Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search names and abilities") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (state != null) {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        FilterChip(
                            selected = inPlayOnly,
                            onClick = { inPlayOnly = !inPlayOnly },
                            label = { Text("In play only") },
                        )
                    }
                }
                val filtered = remember(characters, query, inPlayOnly, seatsById) {
                    val needle = query.trim()
                    characters
                        .filter { !inPlayOnly || Character.normalizeId(it.id) in seatsById }
                        .filter {
                            needle.isEmpty() ||
                                it.name.contains(needle, ignoreCase = true) ||
                                it.ability.contains(needle, ignoreCase = true)
                        }
                }
                CharacterSheet(filtered, seatsById, { openId = it }, Modifier.weight(1f))
            }
            1 -> NightOrderSheet(viewModel, characters, seatsById) { openId = it }
            else -> JinxSheet(viewModel, jinxes, state != null, inPlayOnly) { inPlayOnly = it }
        }
    }

    openId?.let { id ->
        characters.firstOrNull { it.id == id }?.let { character ->
            CharacterDetailDialog(
                viewModel = viewModel,
                character = character,
                seats = seatsById[Character.normalizeId(character.id)].orEmpty(),
                jinxes = viewModel.gameData.activeJinxes(characters.map { it.id })
                    .filter { it.id1 == character.id || it.id2 == character.id },
                onDismiss = { openId = null },
            )
        } ?: run { openId = null }
    }
}

@Composable
private fun CharacterSheet(
    characters: List<Character>,
    seatsById: Map<String, List<String>>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (characters.isEmpty()) {
            item {
                Text(
                    "No characters match that search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val order = listOf(
            Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON,
            Team.TRAVELLER, Team.FABLED, Team.LORIC,
        )
        for (team in order) {
            val members = characters.filter { it.team == team }
            if (members.isEmpty()) continue
            item(key = "head-${team.name}") {
                Text(
                    "${team.displayName} (${members.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = team.color,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(members, key = { "ref-" + it.id }) { c ->
                val seats = seatsById[Character.normalizeId(c.id)].orEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) },
                ) {
                    CharacterToken(character = c, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            if (seats.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "IN PLAY · ${seats.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AgedGold,
                                )
                            }
                        }
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

@Composable
private fun NightOrderSheet(
    viewModel: GameViewModel,
    characters: List<Character>,
    seatsById: Map<String, List<String>>,
    onOpen: (String) -> Unit,
) {
    val ids = characters.map { it.id }.toSet()
    val data = viewModel.gameData
    val first = data.firstNightOrder.filter { it in ids || it in NightMarkers.all }
    val other = data.otherNightOrder.filter { it in ids || it in NightMarkers.all }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text("First night", style = MaterialTheme.typography.headlineSmall, color = AgedGold) }
        items(first.size, key = { "fn-$it" }) { i ->
            NightOrderRow(viewModel, first[i], i + 1, true, seatsById, onOpen)
        }
        item {
            Text(
                "Other nights",
                style = MaterialTheme.typography.headlineSmall,
                color = AgedGold,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        items(other.size, key = { "on-$it" }) { i ->
            NightOrderRow(viewModel, other[i], i + 1, false, seatsById, onOpen)
        }
    }
}

@Composable
private fun NightOrderRow(
    viewModel: GameViewModel,
    id: String,
    position: Int,
    firstNight: Boolean,
    seatsById: Map<String, List<String>>,
    onOpen: (String) -> Unit,
) {
    val marker = id in NightMarkers.all
    val character = if (marker) null else viewModel.gameData.character(id)
    val label = when (id) {
        NightMarkers.DUSK -> "Dusk"
        NightMarkers.DAWN -> "Dawn"
        NightMarkers.MINION_INFO -> "Minion info"
        NightMarkers.DEMON_INFO -> "Demon info"
        else -> character?.name ?: id
    }
    val color = if (marker) AgedGold else character?.team?.color ?: MaterialTheme.colorScheme.onSurface
    val seats = seatsById[Character.normalizeId(id)].orEmpty()
    val sheetText = if (firstNight) {
        character?.firstNightReminder.orEmpty()
    } else {
        character?.otherNightReminder.orEmpty()
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = character != null) { character?.let { onOpen(it.id) } },
    ) {
        Text(
            "$position.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
                if (character != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (seats.isEmpty()) "not in play" else seats.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (seats.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            AgedGold
                        },
                    )
                }
            }
            if (sheetText.isNotBlank()) {
                Text(
                    sheetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JinxSheet(
    viewModel: GameViewModel,
    jinxes: List<com.clocktower.engine.Jinx>,
    hasGame: Boolean,
    inPlayOnly: Boolean,
    onInPlayOnly: (Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasGame) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = inPlayOnly,
                        onClick = { onInPlayOnly(true) },
                        label = { Text("In play") },
                    )
                    FilterChip(
                        selected = !inPlayOnly,
                        onClick = { onInPlayOnly(false) },
                        label = { Text("All on this script") },
                    )
                }
            }
        }
        if (jinxes.isEmpty()) {
            item {
                Text(
                    "No jinxes between these characters.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(jinxes.size, key = { it }) { i ->
            val j = jinxes[i]
            val c1 = viewModel.gameData.character(j.id1)
            val c2 = viewModel.gameData.character(j.id2)
            Column {
                Text(
                    "${c1?.name ?: j.id1} × ${c2?.name ?: j.id2}",
                    style = MaterialTheme.typography.titleSmall,
                    color = AgedGold,
                )
                Text(
                    j.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One character, in full: ability, seats, night indices, tokens, how to run. */
@Composable
private fun CharacterDetailDialog(
    viewModel: GameViewModel,
    character: Character,
    seats: List<String>,
    jinxes: List<com.clocktower.engine.Jinx>,
    onDismiss: () -> Unit,
) {
    val guide = remember(character.id) { NightGuide.entries[character.id] }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CharacterToken(character = character, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(character.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${character.team.displayName} · ${character.edition}",
                        style = MaterialTheme.typography.labelSmall,
                        color = character.team.color,
                    )
                }
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(character.ability, style = MaterialTheme.typography.bodyMedium)
                }
                if (seats.isNotEmpty()) {
                    item { Section("In play", seats.joinToString(", ")) }
                }
                if (character.firstNight > 0 || character.otherNight > 0) {
                    item {
                        Section(
                            "Night",
                            buildString {
                                if (character.firstNight > 0) append("1st: #${character.firstNight}  ")
                                if (character.otherNight > 0) append("other: #${character.otherNight}")
                            },
                        )
                    }
                }
                if (character.firstNightReminder.isNotBlank()) {
                    item { Section("First night", character.firstNightReminder) }
                }
                if (character.otherNightReminder.isNotBlank()) {
                    item { Section("Other nights", character.otherNightReminder) }
                }
                if (character.allReminders.isNotEmpty()) {
                    item {
                        // Repeated labels are copy counts, not duplicates.
                        Section(
                            "Reminders",
                            character.allReminders.groupingBy { it }.eachCount()
                                .entries.joinToString(", ") { (label, n) ->
                                    if (n > 1) "$label ×$n" else label
                                },
                        )
                    }
                }
                if (character.spentLabel.isNotBlank()) {
                    item { Section("Once per game", character.spentLabel) }
                }
                if (jinxes.isNotEmpty()) {
                    item {
                        Section(
                            "Jinxes",
                            jinxes.joinToString("\n\n") { j ->
                                val other = if (j.id1 == character.id) j.id2 else j.id1
                                "${viewModel.gameData.character(other)?.name ?: other}: ${j.reason}"
                            },
                        )
                    }
                }
                val howTo = listOfNotNull(
                    guide?.setup?.instructions?.takeIf { it.isNotBlank() }?.let { "Setup: $it" },
                    guide?.first?.instructions?.takeIf { it.isNotBlank() }?.let { "First night: $it" },
                    guide?.other?.instructions?.takeIf { it.isNotBlank() }?.let { "Other nights: $it" },
                    guide?.day?.instructions?.takeIf { it.isNotBlank() }?.let { "Day: $it" },
                    guide?.reference?.instructions?.takeIf { it.isNotBlank() },
                )
                if (howTo.isNotEmpty()) {
                    item { Section("How to run", howTo.joinToString("\n\n")) }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Section(title: String, body: String) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AgedGold,
            fontWeight = FontWeight.Bold,
        )
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}
