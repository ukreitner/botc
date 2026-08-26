package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.Decisions
import com.clocktower.engine.Distribution
import com.clocktower.engine.Script
import com.clocktower.engine.Setup
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.Team
import com.clocktower.engine.Time
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.platform.KeepScreenOn
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Twilight
import com.clocktower.grimoire.ui.theme.color
import kotlin.random.Random

/** Which card of the one-page setup screen is expanded. */
private const val CARD_SCRIPT = 0
private const val CARD_TABLE = 1
private const val CARD_BAG = 2
private const val CARD_FABLED = 3

/**
 * ONE scrolling "Game setup" screen (setup-and-home §S1) — four collapsible
 * cards over a sticky bag tray and a persistent action bar, replacing the
 * three-stage wizard. Every card shows a one-line summary when collapsed, so
 * the whole state of the game-to-be is legible at a glance and any card can be
 * revisited without losing the others.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: GameViewModel,
    onGameStarted: () -> Unit,
    onBack: () -> Unit,
    /** A script handed in by the share target / `?script=` link. */
    preselectedScriptId: String? = null,
) {
    // The phone is passed around the table during setup too.
    KeepScreenOn()

    var scriptId by rememberSaveable { mutableStateOf(preselectedScriptId) }
    var names by rememberSaveable { mutableStateOf(List(8) { "" } as List<String>) }
    // A list, not a set: Village Idiot / Legion bags legally repeat ids.
    var bagIds by rememberSaveable { mutableStateOf(ArrayList<String>() as List<String>) }
    var pinnedIds by rememberSaveable { mutableStateOf(ArrayList<String>() as List<String>) }
    var bannedIds by rememberSaveable { mutableStateOf(ArrayList<String>() as List<String>) }
    var fabledIds by rememberSaveable { mutableStateOf(ArrayList<String>() as List<String>) }
    var expanded by rememberSaveable { mutableStateOf(CARD_SCRIPT) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var allowDuplicates by rememberSaveable { mutableStateOf(false) }
    var seatlessAck by rememberSaveable { mutableStateOf(false) }
    var outsiderBranch by rememberSaveable { mutableStateOf<Int?>(null) }
    var bagMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    // Seat indices marked as Travellers: they fill no distribution slot and are
    // dealt no token (setup-and-home #8). Not saveable-critical.
    var travellerSeats by remember { mutableStateOf(setOf<Int>()) }
    // Set once the bag has been dealt: hand-out mode owns the screen until the
    // storyteller finishes passing the phone round the table.
    var handingOut by rememberSaveable { mutableStateOf(false) }

    val imported by viewModel.importedScripts.collectAsState()
    val rosters by viewModel.recentRosters.collectAsState()
    val builtIn = remember { viewModel.gameData.builtInScripts() }
    val allScripts = builtIn + imported
    val script = allScripts.find { it.id == scriptId }
    val liveGame by viewModel.game.collectAsState()

    if (handingOut) {
        val state = liveGame
        if (state == null) {
            handingOut = false
        } else {
            HandOutMode(
                viewModel = viewModel,
                state = state,
                onDone = { handingOut = false; onGameStarted() },
            )
            return
        }
    }

    val residentCount = names.indices.count { it !in travellerSeats }
    val characters = remember(script) {
        script?.let { viewModel.gameData.resolve(it).filter { c -> c.team.isTownResident } }
            .orEmpty()
    }
    val byId = remember(characters) { characters.associateBy { it.id } }
    val selected = bagIds.mapNotNull { byId[it] }
    val validCount = residentCount in Setup.MIN_PLAYERS..Setup.MAX_PLAYERS
    // A character the rules put in play WITHOUT a bag token — today only Lil'
    // Monsta's centre token. Derived from the engine's own BagShape
    // (`forbidInBag`), so a future one needs no change here.
    val seatlessCandidates = remember(characters, residentCount, validCount) {
        if (!validCount) {
            emptyList()
        } else {
            val base = Setup.distributionFor(residentCount)
            characters.mapNotNull { c ->
                Setup.bagShapeFor(c.id, base, residentCount)
                    ?.takeIf { it.forbidInBag.isNotEmpty() }
                    ?.let { c to it }
            }
        }
    }
    val seatlessIds = if (seatlessAck) seatlessCandidates.map { it.first.id } else emptyList()
    val issues = if (script == null || !validCount) {
        emptyList()
    } else {
        Setup.validateBag(
            bag = selected,
            playerCount = residentCount,
            fabledIds = fabledIds,
            allowAnyDuplicates = allowDuplicates,
            // WP4's merger note: the wizard must pass the same seatless /
            // virtual context the in-game checklist does. There is no
            // GameState yet, so the Lil' Monsta acknowledgement is the only
            // seatless character the wizard can know about, and no Boffin or
            // Alchemist grant has been chosen.
            inPlayIds = seatlessIds,
            state = null,
            virtual = emptyList(),
        )
    }
    val warnings = if (script == null || !validCount) {
        emptyList()
    } else {
        Setup.bagWarnings(selected, residentCount, seatlessIds, null)
    }
    val seatFilling = selected.count { it.id !in seatlessIds }
    val ready = script != null && validCount && seatFilling == residentCount && issues.isEmpty()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            TextButton(onClick = {
                if (names.any { it.isNotBlank() } || bagIds.isNotEmpty()) confirmCancel = true else onBack()
            }) { Text("Cancel") }
            Text(
                "New game",
                style = MaterialTheme.typography.titleLarge,
                color = AgedGold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = {
                bagIds = ArrayList()
                pinnedIds = ArrayList()
                bannedIds = ArrayList()
                fabledIds = ArrayList()
                travellerSeats = emptySet()
                outsiderBranch = null
                bagMessage = null
            }) { Text("Reset") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- 1 SCRIPT ---------------------------------------------------
            item("card-script") {
                SetupCard(
                    index = 1,
                    title = "SCRIPT",
                    summary = script?.let {
                        "${it.name} · ${it.characterIds.size} characters"
                    } ?: "choose one",
                    done = script != null,
                    expanded = expanded == CARD_SCRIPT,
                    onToggle = { expanded = if (expanded == CARD_SCRIPT) -1 else CARD_SCRIPT },
                ) {
                    ScriptPicker(
                        scripts = allScripts,
                        viewModel = viewModel,
                        selectedId = scriptId,
                        onImport = { showImport = true },
                        onDelete = { viewModel.deleteScript(it) },
                        onPick = { picked ->
                            if (scriptId != picked.id) {
                                bagIds = ArrayList()
                                pinnedIds = ArrayList()
                                bannedIds = ArrayList()
                            }
                            scriptId = picked.id
                            expanded = CARD_TABLE
                        },
                    )
                }
            }

            // ---- 2 TABLE ----------------------------------------------------
            item("card-table") {
                SetupCard(
                    index = 2,
                    title = "TABLE",
                    summary = buildString {
                        append("$residentCount seats")
                        if (travellerSeats.isNotEmpty()) append(" + ${travellerSeats.size} travellers")
                        if (validCount) append(" · ${distributionLabel(residentCount)}")
                    },
                    done = validCount,
                    expanded = expanded == CARD_TABLE,
                    onToggle = { expanded = if (expanded == CARD_TABLE) -1 else CARD_TABLE },
                ) {
                    TableCard(
                        names = names,
                        travellerSeats = travellerSeats,
                        rosters = rosters.map { it.names },
                        onNames = { names = it },
                        onTravellers = { travellerSeats = it },
                        onPaste = { showPaste = true },
                    )
                }
            }

            // ---- 3 BAG ------------------------------------------------------
            item("card-bag") {
                SetupCard(
                    index = 3,
                    title = "BAG",
                    summary = if (script == null) "pick a script first" else "$seatFilling / $residentCount",
                    done = ready,
                    expanded = expanded == CARD_BAG,
                    onToggle = { expanded = if (expanded == CARD_BAG) -1 else CARD_BAG },
                ) {
                    if (script == null || !validCount) {
                        Text(
                            if (script == null) {
                                "Choose a script first."
                            } else {
                                "Seat between ${Setup.MIN_PLAYERS} and ${Setup.MAX_PLAYERS} " +
                                    "non-Traveller players."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BagHeader(
                            playerCount = residentCount,
                            selected = selected,
                            outsiderBranch = outsiderBranch,
                            onBranch = { outsiderBranch = it },
                            issues = issues,
                            warnings = warnings,
                            allowDuplicates = allowDuplicates,
                            onAllowDuplicates = { allowDuplicates = it },
                            seatlessNote = seatlessCandidates.firstOrNull()?.second?.note.orEmpty(),
                            seatlessAck = seatlessAck,
                            onSeatlessAck = { seatlessAck = it },
                            message = bagMessage,
                            onRandomize = { keep ->
                                val required = if (keep) bagIds else pinnedIds
                                val bag = rollBag(characters, residentCount, required, bannedIds)
                                if (bag == null) {
                                    bagMessage = "Couldn't make a legal bag for $residentCount " +
                                        "players with those pins and bans. Loosen one and try again."
                                } else {
                                    bagMessage = null
                                    bagIds = ArrayList(bag.map { it.id })
                                }
                            },
                            onClear = { bagIds = ArrayList(); bagMessage = null },
                        )
                    }
                }
            }
            if (expanded == CARD_BAG && script != null && validCount) {
                bagCharacterRows(
                    characters = characters,
                    bagIds = bagIds,
                    pinnedIds = pinnedIds,
                    bannedIds = bannedIds,
                    allowDuplicates = allowDuplicates,
                    onBagIds = { bagIds = it; bagMessage = null },
                    onPinned = { pinnedIds = it },
                    onBanned = { bannedIds = it },
                )
            }

            // ---- 4 FABLED & HOUSE RULES --------------------------------------
            item("card-fabled") {
                SetupCard(
                    index = 4,
                    title = "FABLED & HOUSE RULES",
                    summary = if (fabledIds.isEmpty()) {
                        "none"
                    } else {
                        fabledIds.mapNotNull { viewModel.gameData.character(it)?.name }
                            .joinToString(", ")
                    },
                    done = true,
                    expanded = expanded == CARD_FABLED,
                    onToggle = { expanded = if (expanded == CARD_FABLED) -1 else CARD_FABLED },
                ) {
                    FabledPicker(
                        viewModel = viewModel,
                        activeIds = fabledIds,
                        onToggle = { id ->
                            fabledIds = ArrayList(
                                if (id in fabledIds) fabledIds - id else fabledIds + id,
                            )
                        },
                    )
                }
            }
            item("tail") { Spacer(Modifier.height(8.dp)) }
        }

        // ---- the sticky bag tray --------------------------------------------
        // The only place the storyteller needs to look to know the bag (§S1).
        BagTray(
            selected = selected,
            pinnedIds = pinnedIds,
            seatlessIds = seatlessIds,
            playerCount = residentCount,
            onRemove = { index ->
                bagIds = ArrayList(bagIds.filterIndexed { i, _ -> i != index })
                bagMessage = null
            },
        )

        // ---- the action bar --------------------------------------------------
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val start: (deal: Boolean) -> Unit = { deal ->
                val finalNames = names.mapIndexed { i, n -> n.ifBlank { "Player ${i + 1}" } }
                viewModel.startGame(script!!, finalNames)
                if (fabledIds.isNotEmpty()) viewModel.setFabled(fabledIds)
                // Traveller seats first: dealing counts non-Travellers.
                viewModel.game.value?.players?.forEachIndexed { index, seat ->
                    if (index in travellerSeats) viewModel.setTraveller(seat.id, true)
                }
                if (seatlessAck) {
                    // `Setup.seatlessInPlayIds` reads exactly this decision.
                    viewModel.applySetupRequirementAck(SetupRequirements.LILMONSTA_NO_DEMON_SEAT)
                }
                outsiderBranch?.let { viewModel.setDecisionNumber(Decisions.OUTSIDER_BRANCH, it) }
                if (deal) {
                    // Deal exactly the resolved, seat-filling tokens: `Seats.deal`
                    // REQUIRES one per non-Traveller seat and throws otherwise, so
                    // never hand it a raw id list that might not resolve.
                    val dealt = selected.map { it.id }.filter { it !in seatlessIds }
                    viewModel.deal(dealt, Time.epochMillis())
                    handingOut = true
                } else {
                    onGameStarted()
                }
            }
            FilledTonalButton(
                enabled = ready,
                onClick = { start(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ready) {
                        "Deal & hand out tokens  ($residentCount ready)"
                    } else {
                        "Deal & hand out tokens"
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (script != null && validCount && seatFilling == residentCount && issues.isNotEmpty()) {
                // Fabled and house rules legitimately bend the distribution —
                // never let the checker block a real game (D54: visible and
                // overridable, never silent).
                OutlinedButton(
                    onClick = { start(true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text("Deal anyway — I know what I'm doing")
                }
            }
            TextButton(
                enabled = script != null && validCount,
                onClick = { start(false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start empty · assign by hand")
            }
        }
    }

    if (showImport) {
        ImportScriptDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val error = viewModel.importScript(text)
                if (error == null) showImport = false
                error
            },
        )
    }
    if (showPaste) {
        PasteListDialog(
            onDismiss = { showPaste = false },
            onNames = { pasted ->
                names = ArrayList(pasted)
                travellerSeats = travellerSeats.filter { it < pasted.size }.toSet()
                showPaste = false
            },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Leave setup?") },
            text = { Text("The names and the bag you have built will be lost.") },
            confirmButton = {
                FilledTonalButton(onClick = { confirmCancel = false; onBack() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep building") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun SetupCard(
    index: Int,
    title: String,
    summary: String,
    done: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp),
        ) {
            Text(
                "$index",
                style = MaterialTheme.typography.titleMedium,
                color = AgedGold,
                modifier = Modifier.width(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (done) {
                Text("OK", style = MaterialTheme.typography.labelSmall, color = AgedGold)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
        }
    }
}

@Composable
private fun ScriptPicker(
    scripts: List<Script>,
    viewModel: GameViewModel,
    selectedId: String?,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onPick: (Script) -> Unit,
) {
    var fileError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val openImportFile = com.clocktower.grimoire.ui.platform.rememberImportFileOpener { text ->
        fileError = if (text == null) "Couldn't read that file." else viewModel.importScript(text)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (script in scripts) {
            val unknown = remember(script) { viewModel.gameData.unknownIds(script) }
            val chosen = script.id == selectedId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (chosen) Twilight else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(script) }
                    .padding(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        script.name + if (chosen) "  •" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (chosen) AgedGold else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        buildString {
                            if (script.author.isNotBlank()) append("by ${script.author} · ")
                            append("${script.characterIds.size} characters")
                            if (script.customCharacters.isNotEmpty()) {
                                append(" · ${script.customCharacters.size} homebrew")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (unknown.isNotEmpty()) {
                        Text(
                            "Unknown ids skipped: ${unknown.joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (!script.isBuiltIn) {
                    IconButton(onClick = { pendingDeleteId = script.id }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete script")
                    }
                }
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import script (paste link or JSON)")
        }
        OutlinedButton(onClick = openImportFile, modifier = Modifier.fillMaxWidth()) {
            Text("Import script from file (.json)")
        }
        fileError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    pendingDeleteId?.let { scriptId ->
        val scriptName = scripts.find { it.id == scriptId }?.name ?: "this script"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete imported script?") },
            text = { Text("$scriptName will be removed from this device. This cannot be undone.") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    pendingDeleteId = null
                    onDelete(scriptId)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Keep script") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableCard(
    names: List<String>,
    travellerSeats: Set<Int>,
    rosters: List<List<String>>,
    onNames: (List<String>) -> Unit,
    onTravellers: (Set<Int>) -> Unit,
    onPaste: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rosters.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (roster in rosters) {
                    AssistChip(
                        onClick = {
                            onNames(ArrayList(roster))
                            onTravellers(travellerSeats.filter { it < roster.size }.toSet())
                        },
                        label = { Text("Last table (${roster.size})") },
                    )
                }
            }
        }
        for (index in names.indices) {
            val isTraveller = index in travellerSeats
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}.",
                    modifier = Modifier.width(26.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTraveller) AgedGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = names[index],
                    onValueChange = { new ->
                        onNames(ArrayList(names).also { it[index] = new })
                    },
                    placeholder = { Text("Player ${index + 1}") },
                    singleLine = true,
                    // Type-Next-type-Next instead of type-dismiss-tap-type.
                    // Compose's default action for Next moves focus to the
                    // following field, so 12 names become type-Next-type-Next.
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (index == names.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onTravellers(
                            if (isTraveller) travellerSeats - index else travellerSeats + index,
                        )
                    },
                ) {
                    Text(
                        if (isTraveller) "TRAV" else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTraveller) AgedGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = names.size > Setup.MIN_PLAYERS,
                    onClick = {
                        onNames(ArrayList(names).also { it.removeAt(index) })
                        onTravellers(
                            travellerSeats.mapNotNull {
                                when {
                                    it == index -> null
                                    it > index -> it - 1
                                    else -> it
                                }
                            }.toSet(),
                        )
                    },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove seat")
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = names.size < Setup.MAX_PLAYERS,
                onClick = { onNames(ArrayList(names).also { it.add("") }) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add seat")
            }
            OutlinedButton(onClick = onPaste) { Text("Paste list") }
        }
        Text(
            "Seats marked TRAV are Travellers: they fill no distribution slot and are dealt no token.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BagHeader(
    playerCount: Int,
    selected: List<Character>,
    outsiderBranch: Int?,
    onBranch: (Int?) -> Unit,
    issues: List<String>,
    warnings: List<String>,
    allowDuplicates: Boolean,
    onAllowDuplicates: (Boolean) -> Unit,
    /** The BagShape note for a character in play with no bag token; "" for none. */
    seatlessNote: String,
    seatlessAck: Boolean,
    onSeatlessAck: (Boolean) -> Unit,
    message: String?,
    onRandomize: (keepCurrent: Boolean) -> Unit,
    onClear: () -> Unit,
) {
    // EVERY legal branch, not just the last one the parser saw
    // (setup-and-home #9): the header and the validator must agree.
    val allowed = remember(playerCount, selected) {
        runCatching { Setup.allowedDistributions(playerCount, selected) }.getOrDefault(emptySet())
    }
    val counts = selected.groupingBy { it.team }.eachCount()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Need: " + listOf(
                Team.TOWNSFOLK to "townsfolk",
                Team.OUTSIDER to "outsiders",
                Team.MINION to "minions",
                Team.DEMON to "demon",
            ).joinToString(" · ") { (team, label) ->
                val options = allowed.map { it.count(team) }.distinct().sorted()
                "${options.joinToString(" or ")} $label"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for ((team, label) in listOf(
            Team.TOWNSFOLK to "TF",
            Team.OUTSIDER to "OUT",
            Team.MINION to "MIN",
            Team.DEMON to "DEM",
        )) {
            val have = counts[team] ?: 0
            val want = allowed.map { it.count(team) }.maxOrNull() ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = team.color,
                    modifier = Modifier.width(34.dp),
                )
                LinearProgressIndicator(
                    progress = { if (want == 0) 0f else (have.toFloat() / want).coerceIn(0f, 1f) },
                    color = team.color,
                    modifier = Modifier.weight(1f).height(6.dp),
                )
                Text(
                    "  $have/$want",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // A choice bracket is a QUESTION, not a silent default (D54).
        val branches = remember(selected) {
            selected.mapNotNull(Setup::modifierFor).filter { it.choice }
        }
        if (branches.isNotEmpty()) {
            val options = allowed.map { it.outsiders }.distinct().sorted()
            Text(
                branches.joinToString(", ") { "${it.characterId} [${it.text}]" } +
                    " — which are you running?",
                style = MaterialTheme.typography.bodySmall,
                color = AgedGold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in options) {
                    FilterChip(
                        selected = outsiderBranch == option,
                        onClick = { onBranch(if (outsiderBranch == option) null else option) },
                        label = { Text("$option outsiders") },
                    )
                }
            }
        }

        if (seatlessNote.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = seatlessAck, onCheckedChange = onSeatlessAck)
                Text(seatlessNote, style = MaterialTheme.typography.bodySmall)
            }
        }

        for (issue in issues) {
            Text(issue, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        for (warning in warnings) {
            Text(warning, color = AgedGold, style = MaterialTheme.typography.bodySmall)
        }
        message?.let {
            Text(it, color = EmberRed, style = MaterialTheme.typography.bodySmall)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onRandomize(false) }) { Text("Randomize") }
            OutlinedButton(onClick = { onRandomize(true) }) { Text("Fill the rest") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allowDuplicates, onCheckedChange = onAllowDuplicates)
            Text(
                "House rule: allow duplicates of any character",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FabledPicker(
    viewModel: GameViewModel,
    activeIds: List<String>,
    onToggle: (String) -> Unit,
) {
    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Fabled are chosen BEFORE the bag: the Sentinel changes the legal " +
                "Outsider count, and the Deus ex Fiasco may not be added later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (fabled in viewModel.gameData.allFabled) {
            val active = fabled.id in activeIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(fabled.id) }
                    .padding(vertical = 4.dp),
            ) {
                CharacterToken(character = fabled, size = 38.dp, dimmed = !active)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        fabled.name + if (active) "  •" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) AgedGold else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        fabled.ability,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The sticky tray: the whole bag, always on screen, tap a token to remove it. */
@Composable
private fun BagTray(
    selected: List<Character>,
    pinnedIds: List<String>,
    seatlessIds: List<String>,
    playerCount: Int,
    onRemove: (index: Int) -> Unit,
) {
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().background(Twilight).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            "IN THE BAG · ${selected.count { it.id !in seatlessIds }} / $playerCount",
            style = MaterialTheme.typography.labelSmall,
            color = AgedGold,
        )
        if (selected.isEmpty()) {
            Text(
                "empty — randomize, or tick characters below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selected.forEachIndexed { index, character ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onRemove(index) }.width(52.dp),
                    ) {
                        CharacterToken(character = character, size = 36.dp)
                        Text(
                            (if (character.id in pinnedIds) "PIN " else "") + character.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (character.id in seatlessIds) {
                                AgedGold
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bag rows and helpers
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.bagCharacterRows(
    characters: List<Character>,
    bagIds: List<String>,
    pinnedIds: List<String>,
    bannedIds: List<String>,
    allowDuplicates: Boolean,
    onBagIds: (List<String>) -> Unit,
    onPinned: (List<String>) -> Unit,
    onBanned: (List<String>) -> Unit,
) {
    for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)) {
        val members = characters.filter { it.team == team }
        if (members.isEmpty()) continue
        item("head-${team.name}") {
            Text(
                team.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = team.color,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
        items(members, key = { "bag-" + it.id }) { c ->
            BagRow(
                character = c,
                count = bagIds.count { it == c.id },
                pinned = c.id in pinnedIds,
                banned = c.id in bannedIds,
                duplicable = allowDuplicates || c.id in Setup.DUPLICABLE,
                onAdd = { onBagIds(ArrayList(bagIds + c.id)) },
                onRemove = {
                    val index = bagIds.lastIndexOf(c.id)
                    if (index >= 0) onBagIds(ArrayList(bagIds.filterIndexed { i, _ -> i != index }))
                },
                onPin = {
                    onPinned(ArrayList(if (c.id in pinnedIds) pinnedIds - c.id else pinnedIds + c.id))
                    if (c.id in bannedIds) onBanned(ArrayList(bannedIds - c.id))
                },
                onBan = {
                    onBanned(ArrayList(if (c.id in bannedIds) bannedIds - c.id else bannedIds + c.id))
                    if (c.id in pinnedIds) onPinned(ArrayList(pinnedIds - c.id))
                },
            )
        }
    }
}

@Composable
private fun BagRow(
    character: Character,
    count: Int,
    pinned: Boolean,
    banned: Boolean,
    duplicable: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onPin: () -> Unit,
    onBan: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (count == 0) onAdd() else if (!duplicable) onRemove() }),
    ) {
        if (duplicable) {
            // Legal duplicates get a stepper instead of a checkbox.
            IconButton(enabled = count > 0, onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove copy")
            }
            Text("$count", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(18.dp))
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add copy")
            }
        } else {
            Checkbox(checked = count > 0, onCheckedChange = { if (count > 0) onRemove() else onAdd() })
        }
        CharacterToken(character = character, size = 36.dp, dimmed = banned)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(character.name, style = MaterialTheme.typography.titleSmall)
                if (character.setup) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "modifies setup",
                        style = MaterialTheme.typography.labelSmall,
                        color = AgedGold,
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
        // Pin = always randomize me in; ban = never.
        TextButton(onClick = onPin) {
            Text(
                "PIN",
                style = MaterialTheme.typography.labelSmall,
                color = if (pinned) AgedGold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onBan) {
            Text(
                "BAN",
                style = MaterialTheme.typography.labelSmall,
                color = if (banned) EmberRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A random legal bag that honours the storyteller's pins and bans
 * (setup-and-home #11). `Setup.randomBag` has no such parameters, so bans are
 * applied by narrowing the pool and pins by re-rolling until they appear —
 * cheap for the one or two pins a real storyteller sets.
 */
private fun rollBag(
    characters: List<Character>,
    playerCount: Int,
    required: List<String>,
    banned: List<String>,
): List<Character>? {
    val pool = characters.filterNot { it.id in banned && it.id !in required }
    val random = Random(Time.epochMillis())
    repeat(40) {
        val bag = Setup.randomBag(pool, playerCount, random) ?: return@repeat
        val remaining = bag.map { it.id }.toMutableList()
        val honoured = required.all { remaining.remove(it) }
        if (honoured) return bag
    }
    // Last resort: no pins at all is still better than nothing.
    return if (required.isEmpty()) null else Setup.randomBag(pool, playerCount, random)
}

private fun distributionLabel(count: Int): String {
    val d = Setup.distributionFor(count.coerceIn(Setup.MIN_PLAYERS, Setup.MAX_PLAYERS))
    return "${d.townsfolk}/${d.outsiders}/${d.minions}/${d.demons}"
}

private fun Distribution.count(team: Team): Int = when (team) {
    Team.TOWNSFOLK -> townsfolk
    Team.OUTSIDER -> outsiders
    Team.MINION -> minions
    Team.DEMON -> demons
    else -> 0
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/** One multi-line field that splits on newline, comma or semicolon (§S2). */
@Composable
fun PasteListDialog(
    onDismiss: () -> Unit,
    onNames: (List<String>) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { splitRoster(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste the table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "One name per line, or separated by commas or semicolons. " +
                        "The seat count grows to match.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Uri\nDana\nAri…") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Text(
                    if (parsed.isEmpty()) {
                        "No names yet."
                    } else {
                        "${parsed.size} seats: ${parsed.joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = parsed.size >= Setup.MIN_PLAYERS,
                onClick = { onNames(parsed) },
            ) { Text("Use these ${parsed.size} seats") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Splits a pasted roster on newline, comma or semicolon; blanks dropped. */
fun splitRoster(text: String): List<String> =
    text.split('\n', ',', ';', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(Setup.MAX_PLAYERS)

@Composable
fun ImportScriptDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> String?,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a share LINK from the official script tool " +
                        "(script.bloodontheclocktower.com/?script=…) or the raw script JSON.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("https://script.bloodontheclocktower.com/?script=… or [\"washerwoman\", …]") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { error = onImport(text) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
