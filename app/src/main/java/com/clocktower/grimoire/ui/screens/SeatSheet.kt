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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.Effects
import com.clocktower.engine.GameState
import com.clocktower.engine.group
import com.clocktower.engine.LedgerEntry
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Phase
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Player
import com.clocktower.engine.RenderedToken
import com.clocktower.engine.Team
import com.clocktower.engine.Tokens
import com.clocktower.engine.Verdict
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.components.StatusPip
import com.clocktower.grimoire.ui.components.TokenCopies
import com.clocktower.grimoire.ui.components.labelCopies
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.MarkerGrey
import com.clocktower.grimoire.ui.theme.OnBlockGold
import com.clocktower.grimoire.ui.theme.color

/**
 * Seat sheet v2 (grimoire-and-seats §5).
 *
 * Full height, fixed header, scrolling body, **sticky action bar**. The three
 * actions that are ~90% of day usage — Kill…, + Token, Change… — plus an
 * in-sheet Undo, because the app bar's undo is behind the sheet's own scrim
 * exactly when it is needed. Ability text and jinxes are collapsed behind
 * "About this character"; the red death-note wall moved into [KillSheet];
 * name and notes auto-commit on blur AND on dismiss, so a swipe-down no longer
 * silently discards what was typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSheet(
    viewModel: GameViewModel,
    state: GameState,
    playerId: Long,
    onDismiss: () -> Unit,
) {
    val player = state.player(playerId)
    // Dismiss as an effect, not during composition, if the seat vanished.
    LaunchedEffect(player == null) { if (player == null) onDismiss() }
    if (player == null) return

    val character = viewModel.characterById(player.characterId)
    var mode by rememberSaveable(playerId) { mutableStateOf(SeatSheetMode.ACTIONS) }
    var killing by rememberSaveable(playerId) { mutableStateOf(false) }
    // Pending text edits live HERE, above the sheet, so dismissing the sheet
    // by any route can still flush them (P1-11).
    val pending = rememberSaveable(playerId, saver = PendingEdits.Saver) { PendingEdits() }

    fun flush() {
        pending.name?.takeIf { it.isNotBlank() && it != player.name }?.let { viewModel.rename(playerId, it) }
        pending.note?.takeIf { it.isNotBlank() }?.let { viewModel.appendNote(playerId, it) }
        pending.name = null
        pending.note = null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { flush(); onDismiss() },
        sheetState = sheetState,
    ) {
        when (mode) {
            SeatSheetMode.ACTIONS -> SeatActions(
                viewModel = viewModel,
                state = state,
                player = player,
                character = character,
                pending = pending,
                onFlush = ::flush,
                onKill = { killing = true },
                onPickCharacter = { mode = SeatSheetMode.PICK_CHARACTER },
                onPickShownCharacter = { mode = SeatSheetMode.PICK_SHOWN_CHARACTER },
                onAddReminder = { mode = SeatSheetMode.ADD_REMINDER },
                onSwap = { mode = SeatSheetMode.SWAP },
                onDismiss = { flush(); onDismiss() },
            )
            SeatSheetMode.PICK_CHARACTER -> CharacterPicker(
                viewModel = viewModel,
                state = state,
                onPick = { picked, traveller ->
                    if (state.phase == Phase.SETUP) {
                        viewModel.assign(player.id, picked?.id, traveller)
                    } else {
                        viewModel.changeCharacter(player.id, picked?.id, ChangeReason.STORYTELLER)
                        if (traveller != player.isTraveller) viewModel.assign(player.id, picked?.id, traveller)
                    }
                    mode = SeatSheetMode.ACTIONS
                },
                onBack = { mode = SeatSheetMode.ACTIONS },
            )
            SeatSheetMode.PICK_SHOWN_CHARACTER -> CharacterPicker(
                viewModel = viewModel,
                state = state,
                title = "Identity shown to player",
                clearLabel = "Show their actual character",
                includeTravellers = false,
                onPick = { picked, _ ->
                    viewModel.setShownCharacter(player.id, picked?.id)
                    mode = SeatSheetMode.ACTIONS
                },
                onBack = { mode = SeatSheetMode.ACTIONS },
            )
            SeatSheetMode.ADD_REMINDER -> ReminderPicker(
                viewModel = viewModel,
                state = state,
                targetName = player.name,
                onKill = { mode = SeatSheetMode.ACTIONS; killing = true },
                onPick = { reminder ->
                    viewModel.placeToken(player.id, reminder)
                    mode = SeatSheetMode.ACTIONS
                },
                onBack = { mode = SeatSheetMode.ACTIONS },
            )
            SeatSheetMode.SWAP -> Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text("Swap characters with…", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Barber cuts, Snake Charmer swaps — both seats trade tokens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (other in state.seats.filter { it.id != player.id }) {
                    TextButton(onClick = {
                        viewModel.swapCharacters(player.id, other.id)
                        mode = SeatSheetMode.ACTIONS
                    }) {
                        Text("${other.name} (${viewModel.characterById(other.characterId)?.name ?: "no character"})")
                    }
                }
                TextButton(onClick = { mode = SeatSheetMode.ACTIONS }) { Text("Back") }
            }
        }
    }

    if (killing) {
        KillSheet(
            viewModel = viewModel,
            state = state,
            targetId = playerId,
            onDismiss = { killing = false },
        )
    }
}

private enum class SeatSheetMode {
    ACTIONS,
    PICK_CHARACTER,
    PICK_SHOWN_CHARACTER,
    ADD_REMINDER,
    SWAP,
}

/** Uncommitted text-field contents, hoisted so every dismissal route flushes. */
private class PendingEdits(name: String? = null, note: String? = null) {
    var name by mutableStateOf(name)
    var note by mutableStateOf(note)

    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<PendingEdits, String>(
            save = { listOf(it.name.orEmpty(), it.note.orEmpty()) },
            restore = { PendingEdits(it[0].ifEmpty { null }, it[1].ifEmpty { null }) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatActions(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    character: Character?,
    pending: PendingEdits,
    onFlush: () -> Unit,
    onKill: () -> Unit,
    onPickCharacter: () -> Unit,
    onPickShownCharacter: () -> Unit,
    onAddReminder: () -> Unit,
    onSwap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lookup = viewModel::characterById
    val seatNumber = state.players.indexOfFirst { it.id == player.id } + 1
    val neighbours = state.seatNeighbours(player.id)
    val tokens = remember(state, player.id) { Effects.rendered(state, lookup, player.id) }
    val onBlock = remember(state.nominations, state.cycle) {
        com.clocktower.engine.DayRules.aboutToDie(state) == player.id
    }
    var showAbout by rememberSaveable(player.id) { mutableStateOf(false) }
    var showAdvanced by rememberSaveable(player.id) { mutableStateOf(false) }
    val canUndo by viewModel.canUndo.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        // ---- fixed header: the facts the storyteller asks for most ----
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CharacterToken(character = character, size = 64.dp, dimmed = !player.alive)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, style = MaterialTheme.typography.headlineSmall)
                        if (onBlock) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "on the block",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnBlockGold,
                            )
                        }
                    }
                    Text(
                        buildString {
                            append(character?.name ?: "No character")
                            character?.team?.let { append(" · ${it.displayName}") }
                            if (player.alignment != null) {
                                append(" · ${if (player.isEvil(lookup)) "evil" else "good"}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        buildString {
                            append("seat $seatNumber of ${state.seats.size}")
                            if (neighbours.size == 2) {
                                append(" · between ${neighbours[0].name} and ${neighbours[1].name}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (player.alive) {
                            "alive"
                        } else {
                            deathSummary(state, lookup, player.id) +
                                (if (player.ghostVoteUsed) " · ghost vote spent" else " · ghost vote available")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (player.alive) MaterialTheme.colorScheme.onSurfaceVariant else EmberRed,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }

        // ---- scrolling body ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            player.shownCharacterId?.let { shownId ->
                val shown = viewModel.characterById(shownId)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                        CharacterToken(character = shown, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shown to ${player.name}", style = MaterialTheme.typography.labelMedium)
                            Text(shown?.name ?: shownId, style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = { viewModel.setShownCharacter(player.id, null) }) { Text("Clear") }
                    }
                }
            }

            // ---- STATUS ----
            SectionHeader("Status")
            if (tokens.isEmpty()) {
                Text(
                    "No tokens on this seat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (token in tokens) {
                StatusRow(
                    viewModel = viewModel,
                    token = token,
                    onSuspend = { token.effectId?.let { viewModel.suspendEffect(it, !token.suspended) } },
                    onRemove = { viewModel.removeRenderedToken(player.id, token) },
                )
            }

            // ---- HISTORY ----
            SectionHeader("History")
            val history = remember(state, player.id) { seatHistory(state, lookup, player.id) }
            if (history.isEmpty()) {
                Text(
                    "Nothing recorded for this seat yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (line in history) {
                Row {
                    Text(
                        line.stamp,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (line.trailing.isNotEmpty()) {
                        Text(
                            line.trailing,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (line.warn) EmberRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- notes: append-only, so a setup prompt can never destroy one ----
            player.notes.forEachIndexed { index, note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (note.phase == Phase.NIGHT) "N" else "D"}${note.cycle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                    )
                    Text("\"${note.text}\"", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.editNote(player.id, index, "") }) { Text("Delete") }
                }
            }
            OutlinedTextField(
                value = pending.note ?: "",
                onValueChange = { pending.note = it },
                label = { Text("Add a note") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onFlush() },
            )

            HorizontalDivider()

            OutlinedTextField(
                value = pending.name ?: player.name,
                onValueChange = { pending.name = it },
                label = { Text("Player name") },
                singleLine = true,
                supportingText = { Text("saves on blur") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onFlush() },
            )

            // ---- collapsed reference material (was pushing everything down) ----
            TextButton(onClick = { showAbout = !showAbout }) {
                Text(if (showAbout) "▾ About this character" else "▸ About this character")
            }
            if (showAbout && character != null) {
                Text(character.ability, style = MaterialTheme.typography.bodyMedium)
                val inPlay = state.seats.mapNotNull { it.characterId } + state.fabledIds
                for (j in viewModel.gameData.activeJinxes(inPlay).filter { it.id1 == character.id || it.id2 == character.id }) {
                    val partner = if (j.id1 == character.id) j.id2 else j.id1
                    Text(
                        "Jinx with ${viewModel.gameData.character(partner)?.name}: ${j.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "▾ Advanced" else "▸ Advanced")
            }
            if (showAdvanced) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickShownCharacter) {
                        Text(if (player.shownCharacterId == null) "Set shown identity" else "Change shown identity")
                    }
                    OutlinedButton(onClick = { viewModel.flipAlignment(player.id) }) { Text("Flip alignment") }
                    OutlinedButton(onClick = onSwap) { Text("Swap characters") }
                    if (!player.alive) {
                        OutlinedButton(onClick = { viewModel.resurrect(player.id) }) { Text("Resurrect") }
                        OutlinedButton(onClick = { viewModel.revive(player.id) }) { Text("Undo death") }
                        OutlinedButton(onClick = { viewModel.toggleGhostVote(player.id) }) {
                            Text(if (player.ghostVoteUsed) "Restore ghost vote" else "Use ghost vote")
                        }
                    }
                    if (state.phase == Phase.SETUP || player.isTraveller) {
                        OutlinedButton(onClick = { viewModel.removeSeat(player.id); onDismiss() }) {
                            Text("Remove seat", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ---- sticky action bar ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = onKill,
                    enabled = player.alive,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Kill…") }
                FilledTonalButton(
                    onClick = onAddReminder,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("+ Token") }
                FilledTonalButton(
                    onClick = onPickCharacter,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Change…") }
                // Undo, reachable WHILE the sheet is open (P1-10).
                TextButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("⤺ Undo") }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** One standing status: full label, source, expiry, and what to do about it. */
@Composable
private fun StatusRow(
    viewModel: GameViewModel,
    token: RenderedToken,
    onSuspend: () -> Unit,
    onRemove: () -> Unit,
) {
    val source = viewModel.characterById(token.sourceId)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusPip(
            group = token.group,
            size = 20.dp,
            ringColor = source?.team?.color ?: Color.Transparent,
            suspended = token.suspended,
            derived = token.derived,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                token.label + if (token.suspended) " (turned over)" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                buildString {
                    append(source?.name ?: "Storyteller")
                    if (token.expiryText.isNotEmpty()) append(" · ${token.expiryText}")
                    if (token.derived) append(" · no physical token")
                    if (token.note.isNotEmpty()) append(" — ${token.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (token.effectId != null) {
            TextButton(onClick = onSuspend, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (token.suspended) "Restore" else "Suspend")
            }
        }
        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** One line of the seat's History section. */
private data class HistoryLine(
    val order: Int,
    val stamp: String,
    val text: String,
    val trailing: String = "",
    val warn: Boolean = false,
)

/**
 * Everything this seat did, was told, or had happen to it.
 *
 * HOOK FOR WP3: this is `Memory.forPlayer(state, playerId)`, which is
 * `TODO("WP3")` at this base. The ledger entries that exist today ARE read
 * here (WP1's kill funnel already writes RULING rows for prevented deaths),
 * merged with deaths, nominations and executions exactly as lead D5 specifies.
 * When `Memory.forPlayer` lands, replace the `state.ledger.filter { … }` line
 * below with it and delete nothing else — the rendering already handles every
 * `LedgerKind`.
 */
private fun seatHistory(
    state: GameState,
    lookup: (String) -> Character?,
    playerId: Long,
): List<HistoryLine> {
    fun stamp(cycle: Int, atNight: Boolean) = (if (atNight) "N" else "D") + cycle
    val out = mutableListOf<HistoryLine>()

    // --- ledger (Memory.forPlayer's job once WP3 lands) ---
    val entries: List<LedgerEntry> = state.ledger.filter {
        it.actorId == playerId || playerId in it.targetIds || playerId in it.targetIdsB
    }
    for (e in entries) {
        val text = when (e.kind) {
            LedgerKind.TOLD -> "told \"${e.shown}\"" + (if (e.text.isNotEmpty()) " (${e.text})" else "")
            LedgerKind.CHOICE -> {
                val who = e.targetIds.mapNotNull { id -> state.player(id)?.name }
                "chose ${who.joinToString().ifEmpty { "nobody" }}" +
                    (lookup(e.sourceId)?.name?.let { " ($it)" } ?: "")
            }
            LedgerKind.STATEMENT -> "said \"${e.text}\""
            LedgerKind.PRIVATE -> "asked privately: ${e.text}"
            LedgerKind.RULING -> e.text
            LedgerKind.ANNOUNCE -> "announce: ${e.text}"
            LedgerKind.SPENT -> "spent ${lookup(e.sourceId)?.name ?: e.sourceId}"
            LedgerKind.WOKE -> "woke${if (e.byStoryteller) " (storyteller)" else ""}"
            LedgerKind.MALFUNCTION -> "ability malfunctioned: ${e.text}"
            LedgerKind.IMPAIRMENT_SPAN -> e.text.ifEmpty { "impairment window" }
            LedgerKind.NOTE -> e.text
        }
        out += HistoryLine(
            order = e.cycle * 10 + (if (e.atNight) 0 else 1),
            stamp = stamp(e.cycle, e.atNight),
            text = text,
            trailing = when (e.verdict) {
                Verdict.TRUE -> "true"
                Verdict.FALSE -> "FALSE"
                else -> if (e.impaired) "impaired" else ""
            },
            warn = e.verdict == Verdict.FALSE || e.impaired,
        )
    }

    // --- deaths ---
    for (d in state.deaths.filter { it.playerId == playerId }) {
        out += HistoryLine(
            order = d.day * 10 + (if (d.atNight) 0 else 1),
            stamp = stamp(d.day, d.atNight),
            text = deathSummary(state, lookup, playerId).ifEmpty { "died" },
            trailing = if (d.registeredOnly) "registers dead" else "",
            warn = true,
        )
    }

    // --- nominations and votes ---
    for (n in state.nominations) {
        if (n.nominatorId == playerId) {
            out += HistoryLine(
                order = n.day * 10 + 1,
                stamp = "D${n.day}",
                text = "nominated ${state.player(n.nomineeId)?.name ?: "the storyteller"} — ${n.votes} votes",
                trailing = n.result.name.lowercase().replace('_', ' '),
            )
        } else if (n.nomineeId == playerId) {
            out += HistoryLine(
                order = n.day * 10 + 1,
                stamp = "D${n.day}",
                text = "nominated by ${state.player(n.nominatorId)?.name ?: "?"} — ${n.votes} votes",
                trailing = n.result.name.lowercase().replace('_', ' '),
            )
        }
    }

    // --- notes are rendered separately, below, so they stay editable ---
    return out.sortedBy { it.order }
}

/** Grid of script characters (plus travellers) to assign to a seat. */
@Composable
fun CharacterPicker(
    viewModel: GameViewModel,
    state: GameState,
    title: String = "Choose character",
    clearLabel: String = "Clear seat (no character)",
    includeTravellers: Boolean = true,
    onPick: (Character?, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val script = state.script
    val scriptCharacters = remember(script) { viewModel.gameData.resolve(script) }
    val travellers = remember(script) { viewModel.gameData.travellersFor(script) }
    val inPlay = state.seats.mapNotNull { it.characterId }.toSet()
    var search by rememberSaveable { mutableStateOf("") }

    fun matches(c: Character) =
        search.isBlank() || c.name.contains(search, ignoreCase = true) ||
            c.ability.contains(search, ignoreCase = true)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
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
                Text(title, style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search characters") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            AssistChip(onClick = { onPick(null, false) }, label = { Text(clearLabel) })
        }
        val groups = scriptCharacters
            .filter { it.team != Team.TRAVELLER && it.team != Team.FABLED && matches(it) }
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
        if (includeTravellers) {
            val shown = travellers.filter { matches(it) }
            if (shown.isNotEmpty()) {
                item {
                    Text(
                        "Travellers",
                        style = MaterialTheme.typography.titleMedium,
                        color = Team.TRAVELLER.color,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(shown, key = { "pick-t-" + it.id }) { c ->
                    CharacterPickRow(c, inPlay.contains(c.id)) { onPick(c, true) }
                }
            }
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
            .heightIn(min = 48.dp)
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
                        style = MaterialTheme.typography.labelMedium,
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

/**
 * Token picker: in-play characters first, search, and one chip per DISTINCT
 * label with its copy count.
 *
 * `characters.json` lists an N-copy token N times (FOLLOWUPS, WP10/WP8), so a
 * raw render showed the Pukka's "Poisoned Poisoned". [labelCopies] collapses
 * them to `Poisoned ×2`, and [GameActionsApi.placeToken] enforces the same N
 * when placing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPicker(
    viewModel: GameViewModel,
    state: GameState,
    targetName: String = "this seat",
    onKill: () -> Unit = {},
    onPick: (PlacedReminder) -> Unit,
    onBack: () -> Unit,
) {
    val scriptCharacters = remember(state.script) { viewModel.gameData.resolve(state.script) }
    val inPlayIds = state.seats.mapNotNull { it.characterId }.toSet()
    var search by rememberSaveable { mutableStateOf("") }
    val (inPlay, offScript) = scriptCharacters.partition { it.id in inPlayIds }

    val generic = remember {
        labelCopies(listOf("Drunk", "Poisoned", "Protected", "Mad", "Used", "No Ability", "Good", "Evil", "?"))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
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
                Text("Token on $targetName", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search tokens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            // "Dead" is a STATE, not a token — the old generic list offered it
            // and placing it marked nobody dead (grimoire-and-seats §7).
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        "\"Dead\" only marks a pending kill.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onKill) { Text("Kill $targetName…") }
                }
            }
        }
        item {
            Text("Generic", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (t in generic.filter { search.isBlank() || it.label.contains(search, true) }) {
                    TokenPickChip(
                        copies = t,
                        color = MarkerGrey,
                        ring = Color.White.copy(alpha = 0.4f),
                    ) { onPick(PlacedReminder(Tokens.STORYTELLER_SOURCE, t.label)) }
                }
            }
        }
        for ((title, group) in listOf("In play" to inPlay, "Rest of script" to offScript)) {
            val withReminders = group.filter { c ->
                c.allReminders.isNotEmpty() &&
                    (
                        search.isBlank() || c.name.contains(search, true) ||
                            c.allReminders.any { it.contains(search, true) }
                        )
            }
            if (withReminders.isEmpty()) continue
            item {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            items(withReminders, key = { "rem-" + it.id }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.width(104.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (t in labelCopies(c.allReminders)) {
                            val group2 = Tokens.rule(c.id, t.label)?.effect?.group
                            TokenPickChip(
                                copies = t,
                                color = group2?.color ?: MarkerGrey,
                                ring = c.team.color,
                            ) { onPick(PlacedReminder(c.id, t.label)) }
                        }
                    }
                }
            }
        }
    }
}

/** One pickable token: full label at >= 11 sp, plus "x2" when it has copies. */
@Composable
private fun TokenPickChip(
    copies: TokenCopies,
    color: Color,
    ring: Color,
    onPick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onPick)
            .heightIn(min = 48.dp)
            .padding(2.dp),
    ) {
        Box {
            ReminderToken(label = copies.label, color = color, size = 56.dp, ringColor = ring)
            if (copies.copies > 1) {
                Text(
                    "×${copies.copies}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}
