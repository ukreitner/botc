package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
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
import com.clocktower.grimoire.ui.components.OverlayInsets
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.components.StatusPip
import com.clocktower.grimoire.ui.components.TokenCopies
import com.clocktower.grimoire.ui.components.bottomActionPadding
import com.clocktower.grimoire.ui.components.labelCopies
import com.clocktower.grimoire.ui.components.rememberOverlayInsets
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
    // Measured OUTSIDE the sheet, where the numbers still exist: a
    // `ModalBottomSheet` reports its insets as already consumed, so a nested
    // `windowInsetsPadding` inside it is a no-op and `overlayBottomPadding`
    // has only its own 24 dp margin to work with — 63 px against the 84 px
    // the home indicator swallows (components/SafeArea.kt).
    val overlayInsets = rememberOverlayInsets()
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
                insets = overlayInsets,
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
                insets = overlayInsets,
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
                insets = overlayInsets,
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
                    // Same measured-outside insets as the two pickers: a long
                    // table's swap list runs to the bottom edge too.
                    .padding(bottom = bottomActionPadding(overlayInsets.bottom)),
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

    // fillMaxHeight is load-bearing: it bounds the Column so the body can take
    // `weight(1f)` and the action bar can stay pinned to the bottom edge.
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
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
                .weight(1f)
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
                    onSuspend = {
                        viewModel.suspendRenderedToken(player.id, token, !token.suspended)
                    },
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
            // Four controls have to fit 1080 px. With Material's default 24 dp
            // of horizontal button padding "Change…" was left ~114 px of room
            // and wrapped to "Chang / e…", and "+ Token" to "+ / Token"
            // (playtest D, P2-13). The touch targets are unchanged: only the
            // padding INSIDE each button shrinks.
            val tight = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = onKill,
                    enabled = player.alive,
                    contentPadding = tight,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Kill…", maxLines = 1, softWrap = false) }
                FilledTonalButton(
                    onClick = onAddReminder,
                    contentPadding = tight,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("+ Token", maxLines = 1, softWrap = false) }
                FilledTonalButton(
                    onClick = onPickCharacter,
                    contentPadding = tight,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Change…", maxLines = 1, softWrap = false) }
                // Undo, reachable WHILE the sheet is open (P1-10).
                TextButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    contentPadding = tight,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("⤺ Undo", maxLines = 1, softWrap = false) }
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
            inert = token.inert,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                token.label + when {
                    token.suspended -> " (turned over)"
                    token.inert -> " (not in force)"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                buildString {
                    append(source?.name ?: "Storyteller")
                    if (token.expiryText.isNotEmpty()) append(" · ${token.expiryText}")
                    if (token.derived) append(" · no physical token")
                    // Playtest D P2-12: an effect whose source has stopped
                    // working is not applying, and the pip must not claim it is.
                    if (token.inert) {
                        append(" · doing nothing — ${source?.name ?: "its source"}'s ")
                        append("ability is not working")
                    }
                    if (token.note.isNotEmpty()) append(" — ${token.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A derived token has no physical counterpart to turn over — everything
        // else does, hand-placed reminders included (playtest D, P1-5).
        if (!token.derived) {
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
            text = deathLine(d, lookup),
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

/**
 * The bottom safe area the enclosing `ModalBottomSheet` did NOT take for itself.
 *
 * D82 gave the pickers their insets as CONTENT padding, which fixed the last
 * ROW — and left the CONTAINER wrong: the sheet pads itself by `navigationBars`
 * (63 px on the reference phone), so its child was still laid out to y=2337,
 * 21 px past the `mandatorySystemGestures` edge at 2316 that `ui.py audit`
 * measures and that a finger actually cannot reach (playtest D2-6, B2's
 * "bottom 59px" on the character picker).
 *
 * This is exactly that difference, and it is the only part that may be spent as
 * LAYOUT padding: 21 px off the viewport is invisible, where the full inset was
 * what made the list too short to hold a search result (D82's `imePadding`
 * lesson). The content padding gives back the same amount so the total scroll
 * clearance is unchanged.
 *
 * `asPaddingValues` reads the RAW window inset, which is what makes this
 * readable from inside the sheet at all; zero on the web, where Compose knows
 * nothing about either.
 */
@Composable
private fun sheetGestureOverrun(): Dp {
    val safe = WindowInsets.safeContent.asPaddingValues().calculateBottomPadding()
    val consumedBySheet = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return (safe - consumedBySheet).coerceAtLeast(0.dp)
}

/**
 * Grid of script characters (plus travellers) to assign to a seat.
 *
 * [insets] must be measured at the SHEET's call site
 * (`rememberOverlayInsets()`), not in here: inside a `ModalBottomSheet` the
 * insets read as consumed and every number comes back zero.
 */
@Composable
fun CharacterPicker(
    viewModel: GameViewModel,
    state: GameState,
    insets: OverlayInsets,
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

    val overrun = sheetGestureOverrun()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            // The CONTAINER stops at the safe edge (D2-6): the sheet took the
            // navigation bar, this takes what the home indicator swallows on
            // top of it. See [sheetGestureOverrun] — 21 px, given straight back
            // below so nothing scrolls any further than it used to.
            .padding(bottom = overrun),
        // CONTENT padding, not layout padding, and the difference matters:
        //
        // * top 8 dp — a full-height `ModalBottomSheet` starts 8 px ABOVE the
        //   status-bar inset on the reference phone, which put [Back]'s top
        //   edge under the status bar ("top 8px under the status bar/cutout").
        // * bottom — `overlayBottomPadding()` is 24 dp of margin plus an inset
        //   the sheet reports as consumed, so it could only offer 63 px
        //   against the home indicator's 84 px ("bottom 21px under the gesture
        //   inset"). [insets] carries the real number in from outside.
        //
        // As `Modifier.padding` both of these shrink the list's VIEWPORT, and
        // with `imePadding()` also taking the keyboard's height that leaves a
        // few hundred pixels: the first search result fell outside the viewport
        // and off the semantics tree entirely, so `D_bmr_assign.sh` could not
        // find any row to tap. As `contentPadding` the viewport keeps its full
        // height and the padding is space at the ends of the SCROLL — which is
        // what "the last row must clear the home indicator" actually means.
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = (bottomActionPadding(insets.bottom) - overrun).coerceAtLeast(0.dp),
        ),
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
 * Every character whose reminder tokens the token picker offers: the script,
 * plus any character actually SITTING at the table that the script does not
 * list (playtest C-20).
 *
 * `GameData.resolve(script)` walks `script.characterIds`, and travellers are
 * not in there — they come from `GameData.travellersFor(script)`. So a game
 * with a seated Voudon, Bureaucrat, Thief and Beggar had no `3 Votes`, no
 * `Negative Vote` and no traveller tokens at all in the picker: they could
 * only ever be placed by the traveller's own night step, and a mid-day
 * correction ("that token is on the wrong seat") was impossible. Anything
 * seated and off-script is folded in — which also covers a character a Pit-Hag
 * created from outside the script — and it sorts into the "In play" group,
 * because that is exactly what it is.
 */
internal fun tokenPickerCharacters(
    onScript: List<Character>,
    seatedCharacterIds: Collection<String>,
    lookup: (String) -> Character?,
): List<Character> {
    val known = onScript.map { it.id }.toSet()
    val extra = seatedCharacterIds.filterNot { it in known }.distinct().sorted()
    return onScript + extra.mapNotNull(lookup)
}

/**
 * Token picker: in-play characters first, search, and one chip per DISTINCT
 * label with its copy count.
 *
 * `characters.json` lists an N-copy token N times (FOLLOWUPS, WP10/WP8), so a
 * raw render showed the Pukka's "Poisoned Poisoned". [labelCopies] collapses
 * them to `Poisoned ×2`, and [GameActionsApi.placeToken] enforces the same N
 * when placing.
 *
 * Which characters it draws from is [tokenPickerCharacters].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPicker(
    viewModel: GameViewModel,
    state: GameState,
    insets: OverlayInsets,
    targetName: String = "this seat",
    onKill: () -> Unit = {},
    onPick: (PlacedReminder) -> Unit,
    onBack: () -> Unit,
) {
    val inPlayIds = state.seats.mapNotNull { it.characterId }.toSet()
    val scriptCharacters = remember(state.script, state.seats) {
        tokenPickerCharacters(
            onScript = viewModel.gameData.resolve(state.script),
            seatedCharacterIds = inPlayIds,
            lookup = viewModel::characterById,
        )
    }
    var search by rememberSaveable { mutableStateOf("") }
    val (inPlay, offScript) = scriptCharacters.partition { it.id in inPlayIds }

    val generic = remember {
        labelCopies(listOf("Drunk", "Poisoned", "Protected", "Mad", "Used", "No Ability", "Good", "Evil", "?"))
    }

    val overrun = sheetGestureOverrun()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            // …and the container itself stops at the safe edge, so the Tea
            // Lady's last `Cannot Die ×2` is not drawn into the gesture strip
            // where `ui.py tap` refuses it (D2-6).
            .padding(bottom = overrun),
        // The character picker's reasoning, verbatim (see there): 8 dp of top
        // so [Back] clears the status bar a full-height sheet starts 8 px
        // above, the measured inset at the bottom so the last chip clears the
        // home indicator — and both as CONTENT padding, so the keyboard and
        // the safe area do not eat the viewport between them.
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = (bottomActionPadding(insets.bottom) - overrun).coerceAtLeast(0.dp),
        ),
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
