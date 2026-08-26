package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.clocktower.engine.Candidate
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.Selection
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.SetupRequirements
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameState
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Team
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.TownsfolkBlue

/** Chronological record of everything that happened, derived from state. */
@Composable
fun GameLogDialog(state: GameState, onDismiss: () -> Unit) {
    data class Entry(val day: Int, val atNight: Boolean, val text: String)

    val entries = remember(state) {
        val list = mutableListOf<Entry>()
        for (d in state.deaths) {
            val name = state.player(d.playerId)?.name ?: "?"
            val cause = when (d.cause) {
                DeathCause.EXECUTION -> "executed"
                DeathCause.DEMON -> "died in the night"
                DeathCause.OTHER_NIGHT_DEATH -> "died in the night (other)"
                DeathCause.EXILE -> "exiled"
                DeathCause.DEMON_KILL -> "died in the night"
                DeathCause.EVIL_ABILITY, DeathCause.GOOD_ABILITY,
                DeathCause.DAY_ABILITY, DeathCause.TRAVELLER_ABILITY,
                -> "died to an ability"
                DeathCause.STORYTELLER -> "died (storyteller)"
            }
            list += Entry(
                d.day, d.atNight,
                "$name $cause" + if (d.resurrected) " (later resurrected)" else "",
            )
        }
        for (n in state.nominations) {
            val nominator = state.player(n.nominatorId)?.name ?: "?"
            val nominee = state.player(n.nomineeId)?.name ?: "?"
            val outcome = when (n.result) {
                NominationResult.ABOUT_TO_DIE -> "reached the block"
                NominationResult.TIED -> "tied"
                NominationResult.SAFE -> "safe"
                NominationResult.WITHDRAWN -> "withdrawn"
            }
            list += Entry(
                n.day, false,
                "$nominator nominated $nominee${if (n.isExile) " (exile)" else ""} — ${n.votes} votes, $outcome",
            )
        }
        list.sortedWith(compareBy({ it.day }, { !it.atNight }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game log") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entries.isEmpty()) {
                    item { Text("Nothing has happened yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(entries.size) { i ->
                    val e = entries[i]
                    Row {
                        Text(
                            text = (if (e.atNight) "N" else "D") + "${e.day}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgedGold,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Up/down seat reordering, wrapping around the circle. */
@Composable
fun ReorderSeatsDialog(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seat order (clockwise)") },
        text = {
            LazyColumn {
                items(state.players, key = { it.id }) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            p.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { viewModel.update { s -> com.clocktower.engine.GameActions.moveSeat(s, p.id, -1) } }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.update { s -> com.clocktower.engine.GameActions.moveSeat(s, p.id, +1) } }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Toggleable list of Fabled to bring into the game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabledSheet(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Fabled", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Tap to add or remove. Active fabled appear on the grimoire and in the night order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(viewModel.gameData.allFabled, key = { "fab-" + it.id }) { c ->
                val active = c.id in state.fabledIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setFabled(
                                if (active) state.fabledIds - c.id else state.fabledIds + c.id,
                            )
                        }
                        .padding(vertical = 4.dp),
                ) {
                    CharacterToken(character = c, size = 44.dp, dimmed = !active)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name + if (active) "  •" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) AgedGold else MaterialTheme.colorScheme.onSurface,
                        )
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

/** Jinxes among the characters actually assigned right now. */
@Composable
fun ActiveJinxesDialog(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    val inPlay = state.players.mapNotNull { it.characterId } + state.fabledIds
    val jinxes = remember(inPlay) { viewModel.gameData.activeJinxes(inPlay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jinxes in play (${jinxes.size})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (jinxes.isEmpty()) {
                    item { Text("No jinxed pairs among assigned characters.") }
                }
                items(jinxes.size) { i ->
                    val j = jinxes[i]
                    Column {
                        Text(
                            "${viewModel.gameData.character(j.id1)?.name} × ${viewModel.gameData.character(j.id2)?.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = AgedGold,
                        )
                        Text(j.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Win-condition advisory with a path to the reveal screen. */
@Composable
fun WinAdvisoryDialog(
    advisory: WinCheck.Advisory,
    onDeclare: (goodWins: Boolean) -> Unit,
    onMastermindDay: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Is the game over?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(advisory.reason, style = MaterialTheme.typography.bodyLarge)
                for (c in advisory.cautions) {
                    Text("! $c", color = EmberRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column {
                FilledTonalButton(onClick = { onDeclare(advisory.goodWins ?: true) }) {
                    Text(if (advisory.goodWins == false) "Declare evil victory" else "Declare good victory")
                }
                if (onMastermindDay != null && advisory.cautions.any { "Mastermind" in it }) {
                    TextButton(onClick = onMastermindDay) { Text("Play the Mastermind day") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep playing") } },
    )
}

/** End-of-game reveal: the full cast, alignments, and how everyone died. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevealSheet(
    viewModel: GameViewModel,
    state: GameState,
    goodWins: Boolean,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    if (goodWins) "GOOD WINS" else "EVIL WINS",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (goodWins) TownsfolkBlue else EmberRed,
                )
                Text(
                    "${state.script.name} · ${state.players.size} players · ${state.cycle} ${if (state.cycle == 1) "day" else "days"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.players, key = { "reveal-" + it.id }) { p ->
                val character = viewModel.characterById(p.characterId)
                val evil = p.isEvil(viewModel::characterById)
                val death = state.deaths.lastOrNull { it.playerId == p.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterToken(character = character, size = 44.dp, dimmed = !p.alive)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (evil) EmberRed else TownsfolkBlue,
                        )
                        Text(
                            buildString {
                                append(character?.name ?: "no character")
                                p.shownCharacterId?.let { shownId ->
                                    append(" · shown as ")
                                    append(viewModel.characterById(shownId)?.name ?: shownId)
                                }
                                character?.team?.let { if (it == Team.TRAVELLER) append(" (traveller)") }
                                if (death != null) {
                                    append(" · ")
                                    append(
                                        when (death.cause) {
                                            DeathCause.EXECUTION -> "executed day ${death.day}"
                                            DeathCause.DEMON -> "killed night ${death.day}"
                                            DeathCause.OTHER_NIGHT_DEATH -> "died night ${death.day}"
                                            DeathCause.EXILE -> "exiled day ${death.day}"
                                            DeathCause.DEMON_KILL -> "killed night ${death.day}"
                                            DeathCause.EVIL_ABILITY, DeathCause.GOOD_ABILITY,
                                            DeathCause.DAY_ABILITY, DeathCause.TRAVELLER_ABILITY,
                                            -> "died day ${death.day}"
                                            DeathCause.STORYTELLER -> "died day ${death.day}"
                                        },
                                    )
                                } else if (p.alive) {
                                    append(" · survived")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onNewGame) { Text("End game & return home") }
                    TextButton(onClick = onDismiss) { Text("Back to grimoire") }
                }
            }
        }
    }
}

/**
 * The "Before the first night" checklist (setup-and-home §S4).
 *
 * WP11 replaced the four hand-written dialogs (Fortune Teller red herring,
 * Drunk, Lunatic, Marionette) with ONE sheet rendering `SetupRequirements.all`,
 * so all 26-odd setup decisions are prompted and validated, and adding the next
 * character is a table row rather than 35 more lines of UI.
 *
 * It is deliberately NOT gated on `phase == SETUP` (defect #5): a Pit-Hag
 * creating a Drunk on night 3 raises `drunk.token` and the sheet re-opens.
 */
@Composable
fun SetupIdentityPrompts(
    viewModel: GameViewModel,
    state: GameState,
) {
    val lookup = viewModel::characterById
    val blockingKey = remember(state) {
        SetupRequirements.unmet(state, lookup).filter { it.blocking }.joinToString("|") { it.id }
    }
    // "Seen" is the set of blocking rows the storyteller has already dismissed.
    // A NEW blocking row (a mid-game identity change) re-raises the sheet.
    var dismissedKey by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(blockingKey) {
        if (blockingKey.isNotEmpty() && blockingKey != dismissedKey) open = true
    }
    if (open) {
        SetupChecklistSheet(
            viewModel = viewModel,
            state = state,
            onDismiss = {
                open = false
                dismissedKey = blockingKey
            },
        )
    }
}

/**
 * The checklist itself: one row per `SetupRequirement`, ticked when satisfied,
 * every row openable, and every row's answer applied through the requirement's
 * own `apply` — no character ids and no per-character UI anywhere.
 *
 * Advisory rows (`blocking = false`) are shown too, greyed and labelled, so a
 * default is never silent (lead D54).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupChecklistSheet(
    viewModel: GameViewModel,
    state: GameState,
    onDismiss: () -> Unit,
) {
    val lookup = viewModel::characterById
    val rows = remember(state) { SetupRequirements.all(state, lookup) }
    // By INDEX, not by id: two Lunatics (or two Village Idiots) legally raise
    // two rows with the same id, and each carries its own seat in its `apply`.
    val satisfied = remember(state, rows) { rows.map { it.satisfied(state, lookup) } }
    val doneCount = satisfied.count { it }
    var openRow by rememberSaveable { mutableStateOf(-1) }
    var showBluffs by rememberSaveable { mutableStateOf(false) }

    // The bluff picker is itself a bottom sheet, so it REPLACES the checklist
    // rather than stacking on it; closing it comes back here.
    if (showBluffs) {
        BluffsSheet(viewModel, state, onDismiss = { showBluffs = false })
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    "Before the first night",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AgedGold,
                )
                Text(
                    "$doneCount of ${rows.size} done" +
                        if (rows.isEmpty()) " — this game needs no setup decisions." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
            items(rows.size, key = { "req-$it" }) { index ->
                val row = rows[index]
                val ok = satisfied.getOrElse(index) { false }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (row.kind == RequirementKind.BLUFFS) {
                                showBluffs = true
                            } else {
                                openRow = index
                            }
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        if (ok) "✓" else "○",
                        color = if (ok) AgedGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ok) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            row.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!row.blocking) {
                        Text(
                            "optional",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "\"Begin night\" still works with rows outstanding — the guard " +
                        "tells you what is missing and lets you start anyway.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }

    rows.getOrNull(openRow)?.let { row ->
        SetupRequirementDialog(
            viewModel = viewModel,
            state = state,
            requirement = row,
            rowIndex = openRow,
            onDismiss = { openRow = -1 },
        )
    }
}

/**
 * One requirement, answered. The picker shape is chosen from
 * [RequirementKind] and the candidate list the requirement itself supplies —
 * the screen never knows which character raised the row.
 */
@Composable
private fun SetupRequirementDialog(
    viewModel: GameViewModel,
    state: GameState,
    requirement: SetupRequirement,
    /** Position in the checklist — the row's identity, since ids can repeat. */
    rowIndex: Int,
    onDismiss: () -> Unit,
) {
    val lookup = viewModel::characterById
    val candidates = remember(state, rowIndex) { requirement.candidates(state, lookup) }
    // Rows that place a token on SEVERAL seats at once. Advisory rows that name
    // no single holder are the only ones this applies to today (the Lunatic's
    // fake Minions); everything else takes exactly one answer.
    val multi = requirement.kind == RequirementKind.REMINDER &&
        !requirement.blocking && candidates.size > 1
    var chosen by rememberSaveable(rowIndex) { mutableStateOf(ArrayList<String>() as List<String>) }
    var freeText by rememberSaveable(rowIndex) { mutableStateOf("") }

    val apply: (Selection) -> Unit = { selection ->
        viewModel.applySetupRequirement(requirement, selection)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(requirement.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(requirement.prompt, style = MaterialTheme.typography.bodyMedium)
                if (requirement.problem.isNotBlank()) {
                    Text(
                        requirement.problem,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    candidates.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        // Index keys: a script may legally list one id twice.
                        items(candidates.size, key = { "cand-$it" }) { i ->
                            val candidate = candidates[i]
                            val picked = candidate.id in chosen
                            TextButton(
                                enabled = candidate.enabled,
                                onClick = {
                                    if (multi) {
                                        chosen = ArrayList(
                                            if (picked) chosen - candidate.id else chosen + candidate.id,
                                        )
                                    } else {
                                        apply(selectionFor(requirement, candidate))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val character = viewModel.characterById(candidate.id)
                                if (character != null && candidate.playerId == null) {
                                    CharacterToken(character = character, size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    (if (picked) "• " else "") + candidate.label,
                                    modifier = Modifier.weight(1f),
                                )
                                if (candidate.badge.isNotBlank()) {
                                    Text(
                                        candidate.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AgedGold,
                                    )
                                }
                            }
                        }
                    }

                    // A free-text secret (the Mezepheles' word) or an
                    // acknowledgement with nothing to pick.
                    requirement.kind == RequirementKind.GRANT -> OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    else -> Text(
                        "Nothing to pick — confirm when you have done it at the table.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when {
                multi -> FilledTonalButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = { apply(Selection(playerIds = chosen.mapNotNull { it.toLongOrNull() })) },
                ) { Text("Place ${chosen.size}") }

                candidates.isEmpty() && requirement.kind == RequirementKind.GRANT -> FilledTonalButton(
                    enabled = freeText.isNotBlank(),
                    onClick = { apply(Selection(text = freeText)) },
                ) { Text("Save") }

                candidates.isEmpty() -> FilledTonalButton(onClick = { apply(Selection()) }) {
                    Text("Confirm")
                }

                else -> TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Maps one candidate onto the `Selection` shape its requirement's `apply`
 * reads. Seat candidates carry a `playerId`; number rows carry an integer id;
 * everything else is a character id, which the decision rows read as text.
 */
private fun selectionFor(requirement: SetupRequirement, candidate: Candidate): Selection = when {
    candidate.playerId != null -> Selection(playerIds = listOf(candidate.playerId!!))
    requirement.kind == RequirementKind.NUMBER -> Selection(
        number = candidate.id.toIntOrNull(),
        characterIds = listOf(candidate.id),
        text = candidate.id,
    )
    else -> Selection(characterIds = listOf(candidate.id), text = candidate.id)
}
