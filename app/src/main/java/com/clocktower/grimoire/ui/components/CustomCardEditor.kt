package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.ShowCardSpec

enum class CustomCardKind(val label: String) {
    TEXT("Text"), NUMBER("Number"), PLAYERS("Players"), CHARACTERS("Characters"), ALIGNMENT("Alignment"),
}

/** A storyteller's answer is independent of the engine's suggested answers. */
data class CustomCardDraft(
    val kind: CustomCardKind = CustomCardKind.TEXT,
    val text: String = "",
    val number: String = "",
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val evil: Boolean? = false,
) {
    fun card(state: GameState): ShowCard? = when (kind) {
        CustomCardKind.TEXT -> text.trim().takeIf { it.isNotEmpty() }?.let { ShowCard.Message(it) }
        CustomCardKind.NUMBER -> number.trim().toIntOrNull()?.let { ShowCard.NumberCard(it) }
        CustomCardKind.PLAYERS -> {
            val seats = playerIds.distinct().mapNotNull { id ->
                state.seats.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            }
            seats.takeIf { it.isNotEmpty() }?.let {
                ShowCard.PointCard(
                    prefix = text.trim().ifBlank { ShowCardSpec.pointPrefix(characterIds.isNotEmpty(), seats.size) },
                    playerNames = seats.map { state.seats[it].name },
                    seatNumbers = seats.map { it + 1 },
                    characterId = characterIds.firstOrNull(),
                )
            }
        }
        CustomCardKind.CHARACTERS -> when (characterIds.size) {
            0 -> null
            1 -> ShowCard.CharacterCard(text.trim().ifBlank { "THIS CHARACTER" }, characterIds.first())
            else -> ShowCard.MultiTokenCard(text.trim().ifBlank { "THESE CHARACTERS" }, characterIds)
        }
        CustomCardKind.ALIGNMENT -> ShowCard.AlignmentCard(evil, text.trim())
    }

    companion object {
        fun from(card: ShowCard?, state: GameState): CustomCardDraft = when (card) {
            is ShowCard.Message -> CustomCardDraft(text = listOf(card.title, card.subtitle)
                .filter { it.isNotBlank() }.joinToString("\n"))
            is ShowCard.NumberCard -> CustomCardDraft(CustomCardKind.NUMBER, number = card.number.toString())
            is ShowCard.PointCard -> CustomCardDraft(
                CustomCardKind.PLAYERS,
                text = card.prefix,
                playerIds = card.seatNumbers.mapNotNull { state.seats.getOrNull(it - 1)?.id },
                characterIds = listOfNotNull(card.characterId),
            )
            is ShowCard.CharacterCard -> CustomCardDraft(
                CustomCardKind.CHARACTERS, text = card.prefix, characterIds = listOf(card.characterId),
            )
            is ShowCard.MultiTokenCard -> CustomCardDraft(
                CustomCardKind.CHARACTERS, text = card.prefix, characterIds = card.characterIds,
            )
            is ShowCard.BluffsCard -> CustomCardDraft(
                CustomCardKind.CHARACTERS, text = "NOT IN PLAY", characterIds = card.characterIds,
            )
            is ShowCard.AlignmentCard -> CustomCardDraft(
                CustomCardKind.ALIGNMENT, text = card.text, evil = card.evil,
            )
            else -> CustomCardDraft()
        }
    }
}

/** Visible custom choice, also used when editing any suggested card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCardEditor(
    state: GameState,
    characters: List<Character>,
    initial: ShowCard? = null,
    onDismiss: () -> Unit,
    onShow: (ShowCard) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(CustomCardDraft.from(initial, state)) }
    var search by remember { mutableStateOf("") }
    val card = draft.card(state)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom choice") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose exactly what to tell them. Your choice is recorded as shown.",
                    style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (kind in CustomCardKind.entries) {
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = {
                                if (draft.kind != kind) {
                                    draft = draft.copy(kind = kind, text = "", characterIds =
                                        if (kind == CustomCardKind.PLAYERS) draft.characterIds.take(1)
                                        else draft.characterIds)
                                    search = ""
                                }
                            },
                            label = { Text(kind.label) },
                        )
                    }
                }
                if (draft.kind == CustomCardKind.NUMBER) {
                    OutlinedTextField(
                        value = draft.number,
                        onValueChange = { draft = draft.copy(number = it) },
                        label = { Text("Number to show") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = draft.text,
                        onValueChange = { draft = draft.copy(text = it) },
                        label = { Text(if (draft.kind == CustomCardKind.TEXT) "What to tell the player" else "Caption (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (draft.kind == CustomCardKind.PLAYERS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((index, player) in state.seats.withIndex()) {
                            FilterChip(
                                selected = player.id in draft.playerIds,
                                onClick = { draft = draft.copy(playerIds = draft.playerIds.toggle(player.id)) },
                                label = { Text("${index + 1} ${player.name}") },
                            )
                        }
                    }
                }
                if (draft.kind == CustomCardKind.ALIGNMENT) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((evil, label) in listOf(false to "Good", true to "Evil", null to "Neither")) {
                            FilterChip(selected = draft.evil == evil,
                                onClick = { draft = draft.copy(evil = evil) }, label = { Text(label) })
                        }
                    }
                }
                if (draft.kind == CustomCardKind.CHARACTERS || draft.kind == CustomCardKind.PLAYERS) {
                    Text(if (draft.kind == CustomCardKind.PLAYERS) "Character token (optional)" else "Character tokens")
                    OutlinedTextField(value = search, onValueChange = { search = it },
                        label = { Text("Find a character") }, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (character in characters.filter { it.name.contains(search.trim(), ignoreCase = true) }
                            .sortedBy { it.name }) {
                            FilterChip(
                                selected = character.id in draft.characterIds,
                                onClick = {
                                    draft = draft.copy(characterIds = if (draft.kind == CustomCardKind.PLAYERS) {
                                        if (character.id in draft.characterIds) emptyList() else listOf(character.id)
                                    } else draft.characterIds.toggle(character.id))
                                },
                                label = { Text(character.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(enabled = card != null, onClick = { card?.let(onShow) }) { Text("Show custom choice") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item
