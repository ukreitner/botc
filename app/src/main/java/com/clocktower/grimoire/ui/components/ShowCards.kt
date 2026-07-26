package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.TownsfolkBlue
import com.clocktower.grimoire.ui.theme.color

/**
 * A card the storyteller holds up to a player across the table: huge text
 * on a black screen, optionally with character tokens. Mirrors the paper
 * info tokens of the physical game.
 */
sealed interface ShowCard {
    data class Message(val title: String, val subtitle: String = "") : ShowCard
    data class CharacterCard(val prefix: String, val characterId: String) : ShowCard
    data class NumberCard(val number: Int) : ShowCard
    data class AlignmentCard(val evil: Boolean) : ShowCard
    data class BluffsCard(val characterIds: List<String>) : ShowCard

    /**
     * A neutral, full-script character sheet the player can silently point
     * at (Pit-Hag, Philosopher, Cerenovus…). Shows every script character
     * with zero game-state hints, so it reveals nothing.
     */
    data class SheetCard(val characterIds: List<String>) : ShowCard
}

/** Full-screen presentation of a [ShowCard]; tap anywhere to close. */
@Composable
fun FullScreenShow(
    card: ShowCard,
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when (card) {
                is ShowCard.Message -> BigText(card.title, card.subtitle)
                is ShowCard.NumberCard -> Text(
                    text = "${card.number}",
                    fontSize = 220.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = AgedGold,
                )
                is ShowCard.AlignmentCard -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (card.evil) "👎" else "👍",
                        fontSize = 140.sp,
                    )
                    Text(
                        text = if (card.evil) "YOU ARE EVIL" else "YOU ARE GOOD",
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (card.evil) EmberRed else TownsfolkBlue,
                        textAlign = TextAlign.Center,
                    )
                }
                is ShowCard.CharacterCard -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BigText(card.prefix)
                    val character = viewModel.characterById(card.characterId)
                    CharacterToken(character = character, size = 180.dp)
                    Text(
                        text = character?.name ?: "?",
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Parchment,
                        textAlign = TextAlign.Center,
                    )
                }
                is ShowCard.BluffsCard -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BigText("THESE CHARACTERS\nARE NOT IN PLAY")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (id in card.characterIds) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CharacterToken(character = viewModel.characterById(id), size = 100.dp)
                            }
                        }
                    }
                }
                is ShowCard.SheetCard -> CharacterSheetGrid(card.characterIds, viewModel)
            }
        }
    }
}

/** The pointable character sheet: every script character, grouped by team. */
@Composable
private fun CharacterSheetGrid(characterIds: List<String>, viewModel: GameViewModel) {
    val byTeam = characterIds
        .mapNotNull { viewModel.characterById(it) }
        .sortedBy { it.name }
        .groupBy { it.team }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 40.dp, bottom = 40.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "POINT TO A CHARACTER",
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON, Team.TRAVELLER)) {
            val characters = byTeam[team] ?: continue
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = team.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = team.color,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(characters, key = { it.id }) { character ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CharacterToken(character = character, size = 62.dp)
                    Text(
                        text = character.name,
                        fontSize = 11.sp,
                        color = Parchment,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "tap anywhere to close",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun BigText(title: String, subtitle: String = "") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = title,
            fontSize = 52.sp,
            lineHeight = 60.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = AgedGold,
            textAlign = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                color = Parchment,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Picker for what to show: the classic info-token phrases, number and
 * alignment signals, character cards, current bluffs, and free text.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShowToolSheet(
    viewModel: GameViewModel,
    state: GameState,
    onShow: (ShowCard) -> Unit,
    onDismiss: () -> Unit,
) {
    var customText by rememberSaveable { mutableStateOf("") }
    var characterPrefix by rememberSaveable { mutableStateOf("THIS PLAYER IS") }
    var characterSearch by rememberSaveable { mutableStateOf("") }
    val scriptCharacters = viewModel.gameData.resolve(state.script)
    val inPlayIds = state.players.mapNotNull { it.characterId }.toSet()
    val visibleCharacters = remember(scriptCharacters, inPlayIds, characterSearch) {
        val needle = characterSearch.trim()
        scriptCharacters
            .filter { it.team != Team.FABLED }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedWith(
                compareByDescending<Character> { it.id in inPlayIds }
                    .thenBy { it.team.ordinal }
                    .thenBy { it.name },
            )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Show a card", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Hold the phone up to a player — tap the screen to close it again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {
                        onShow(
                            ShowCard.SheetCard(
                                scriptCharacters.filter { it.team != Team.FABLED }.map { it.id },
                            ),
                        )
                    },
                    label = { Text("Character sheet — player points silently") },
                )
            }
            item {
                Text("Character tokens", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Characters in play are first. Pick a phrase, then tap a symbol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (prefix in listOf("THIS PLAYER IS", "YOU ARE", "THIS CHARACTER SELECTED YOU")) {
                        FilterChip(
                            selected = characterPrefix == prefix,
                            onClick = { characterPrefix = prefix },
                            label = { Text(prefix) },
                        )
                    }
                }
                OutlinedTextField(
                    value = characterSearch,
                    onValueChange = { characterSearch = it },
                    placeholder = { Text("Find a character…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (character in visibleCharacters) {
                        ShowCharacterTile(character) {
                            onShow(ShowCard.CharacterCard(characterPrefix, character.id))
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Phrases", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (phrase in listOf(
                        "THIS IS THE DEMON",
                        "THESE ARE YOUR MINIONS",
                        "THIS PLAYER IS…",
                        "THIS CHARACTER SELECTED YOU",
                        "YOU ARE…",
                        "USE YOUR ABILITY?",
                        "DID YOU NOMINATE TODAY?",
                    )) {
                        AssistChip(onClick = { onShow(ShowCard.Message(phrase)) }, label = { Text(phrase) })
                    }
                }
            }
            item {
                Text("Signals", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..9) {
                        AssistChip(onClick = { onShow(ShowCard.NumberCard(n)) }, label = { Text("$n") })
                    }
                    AssistChip(onClick = { onShow(ShowCard.AlignmentCard(evil = false)) }, label = { Text("👍 Good") })
                    AssistChip(onClick = { onShow(ShowCard.AlignmentCard(evil = true)) }, label = { Text("👎 Evil") })
                    if (state.demonBluffIds.isNotEmpty()) {
                        AssistChip(
                            onClick = { onShow(ShowCard.BluffsCard(state.demonBluffIds)) },
                            label = { Text("Bluffs (${state.demonBluffIds.size})") },
                        )
                    }
                }
            }
            item {
                Text("Custom text", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Anything you need to say silently…") },
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        enabled = customText.isNotBlank(),
                        onClick = { onShow(ShowCard.Message(customText)) },
                    ) { Text("Show") }
                }
            }
        }
    }
}

@Composable
private fun ShowCharacterTile(character: Character, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        CharacterToken(character = character, size = 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            character.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
