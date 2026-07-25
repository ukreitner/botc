package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.Script
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.color

/**
 * Script reference: the character sheet by team, full night order, and
 * active jinxes — everything on the printed sheets, searchable in play.
 */
@Composable
fun ReferenceScreen(
    viewModel: GameViewModel,
    script: Script,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val characters = remember(script) { viewModel.gameData.resolve(script) }
    val jinxes = remember(script) {
        viewModel.gameData.activeJinxes(characters.map { it.id })
    }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Characters") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Night order") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Jinxes (${jinxes.size})") })
        }
        when (tab) {
            0 -> CharacterSheet(characters)
            1 -> NightOrderSheet(viewModel, characters)
            else -> JinxSheet(viewModel, jinxes.map { it })
        }
    }
}

@Composable
private fun CharacterSheet(characters: List<Character>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val order = listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON, Team.TRAVELLER, Team.FABLED)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CharacterToken(character = c, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
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
private fun NightOrderSheet(viewModel: GameViewModel, characters: List<Character>) {
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
            NightOrderRow(viewModel, first[i], i + 1)
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
            NightOrderRow(viewModel, other[i], i + 1)
        }
    }
}

@Composable
private fun NightOrderRow(viewModel: GameViewModel, id: String, position: Int) {
    val (label, color) = when (id) {
        NightMarkers.DUSK -> "Dusk" to AgedGold
        NightMarkers.DAWN -> "Dawn" to AgedGold
        NightMarkers.MINION_INFO -> "Minion info" to AgedGold
        NightMarkers.DEMON_INFO -> "Demon info" to AgedGold
        else -> {
            val c = viewModel.gameData.character(id)
            (c?.name ?: id) to (c?.team?.color ?: MaterialTheme.colorScheme.onSurface)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$position.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun JinxSheet(viewModel: GameViewModel, jinxes: List<com.clocktower.engine.Jinx>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (jinxes.isEmpty()) {
            item {
                Text(
                    "No jinxes between characters on this script.",
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
                    "${c1?.name ?: j.id1} ✕ ${c2?.name ?: j.id2}",
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
