package com.clocktower.grimoire.ui.screens.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.Ledger
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.platform.rememberDictation
import com.clocktower.grimoire.ui.components.overlayBottomPadding
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.FadedInk

/**
 * The statement composer — the answer to the friction log's *"record Gossip
 * statements with nothing in play"* complaint (§C).
 *
 * **Two taps and a sentence:** tap the speaker, the field is already focused,
 * type or dictate, tap **Add**. Everything else has a default.
 *
 * **Zero typing:** with a speaker picked and the field empty, the primary
 * button reads **Claims…** and opens a character grid, so *"Ana claims to be
 * the Empath"* — the most common statement in every game — costs three taps and
 * no keyboard. That path matters twice over on the PWA, where Compose renders
 * the field on a canvas and iOS Safari's dictation key may never reach it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaidSheet(
    viewModel: GameViewModel,
    state: GameState,
    /** Speaker to pre-select, from a seat long-press or a briefing row. */
    initialSpeakerId: Long?,
    /** Ledger source to pre-select, from the collect list. */
    initialSourceId: String?,
    /** Source chips offered: the day's collect list, then a plain claim. */
    sources: List<String>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SaidSheetBody(viewModel, state, initialSpeakerId, initialSourceId, sources, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod")
private fun SaidSheetBody(
    viewModel: GameViewModel,
    state: GameState,
    initialSpeakerId: Long?,
    initialSourceId: String?,
    sources: List<String>,
    onDismiss: () -> Unit,
) {
    val lookup: (String) -> Character? = viewModel::characterById
    var speakerId by rememberSaveable { mutableStateOf(initialSpeakerId) }
    var text by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable {
        mutableStateOf(
            initialSourceId
                ?: SaidModel.defaultSource(state, initialSpeakerId, sources),
        )
    }
    var showClaims by rememberSaveable { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    val startDictation = rememberDictation { spoken ->
        text = if (text.isBlank()) spoken else "$text $spoken"
    }

    // Tapping a seat is tap one; the field being focused already is what makes
    // "type or dictate" tap two. Never make the storyteller aim at the field.
    LaunchedEffect(speakerId) {
        if (speakerId != null && !showClaims) {
            runCatching { focus.requestFocus() }
        }
    }

    fun add(keepOpen: Boolean) {
        val speaker = speakerId
        if (text.isBlank() || speaker == null) return
        viewModel.recordStatement(
            speakerId = speaker,
            text = text.trim(),
            sourceId = sourceId,
        )
        text = ""
        if (keepOpen) {
            speakerId = SaidModel.nextSpeaker(state, speaker)
            sourceId = SaidModel.defaultSource(state, speakerId, sources)
        } else {
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = overlayBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "What was said · Day ${state.cycle}",
            style = MaterialTheme.typography.headlineSmall,
            color = AgedGold,
        )

        if (showClaims) {
            ClaimsGrid(
                state = state,
                lookup = lookup,
                onPick = { character ->
                    speakerId?.let { speaker ->
                        viewModel.recordClaim(
                            speakerId = speaker,
                            characterId = character.id,
                            text = SaidModel.claimText(character.name),
                        )
                    }
                    showClaims = false
                    onDismiss()
                },
                onCancel = { showClaims = false },
            )
            return@Column
        }

        Text("Who said it?", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Alive seats first: the dead rarely say anything that matters and
            // must never push a living seat off the first row.
            for (seat in state.seats.sortedByDescending { it.alive }) {
                FilterChip(
                    selected = speakerId == seat.id,
                    onClick = {
                        speakerId = if (speakerId == seat.id) null else seat.id
                        sourceId = SaidModel.defaultSource(state, speakerId, sources)
                    },
                    label = { Text(seat.name + if (!seat.alive) " †" else "") },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                placeholder = { Text("Fay is the Imp") },
                label = { Text("One line, in their words") },
            )
            if (startDictation != null) {
                IconButton(onClick = startDictation) {
                    Icon(Icons.Filled.Mic, contentDescription = "Dictate")
                }
            }
        }

        if (sources.size > 1) {
            Text("Recorded as", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (id in sources) {
                    FilterChip(
                        selected = sourceId == id,
                        onClick = { sourceId = id },
                        label = { Text(SaidModel.sourceLabel(lookup, id)) },
                    )
                }
            }
        }

        val canAdd = speakerId != null && text.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                enabled = speakerId != null,
                onClick = { if (canAdd) add(keepOpen = false) else showClaims = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (canAdd) "Add" else "Claims…",
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(enabled = canAdd, onClick = { add(keepOpen = true) }) {
                Text("Add & another")
            }
        }
        Text(
            if (speakerId == null) {
                "Tap a seat, then type or dictate one line. Nothing has to be in play."
            } else {
                "Add & another moves to the next living seat clockwise."
            },
            style = MaterialTheme.typography.bodySmall,
            color = FadedInk,
        )
    }
}

/** The zero-typing grid: every character on the script, in play first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimsGrid(
    state: GameState,
    lookup: (String) -> Character?,
    onPick: (Character) -> Unit,
    onCancel: () -> Unit,
) {
    val candidates = remember(state.script.id, state.players) {
        SaidModel.claimCandidates(state, lookup)
    }
    Text("Claims to be…", style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (character in candidates) {
            AssistChip(
                onClick = { onPick(character) },
                label = { Text(character.name) },
            )
        }
    }
    TextButton(onClick = onCancel) { Text("Back") }
}

/** Source ids the composer always offers, even with an empty collect list. */
val DEFAULT_STATEMENT_SOURCES: List<String> = listOf(Ledger.Sources.CLAIM)
