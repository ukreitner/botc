package com.clocktower.grimoire.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.GameActions
import com.clocktower.engine.Script
import com.clocktower.engine.Setup
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.color
import kotlin.random.Random

private enum class SetupStage { SCRIPT, PLAYERS, BAG }

/** Three-stage new game flow: pick script, seat players, build the bag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: GameViewModel,
    onGameStarted: () -> Unit,
    onBack: () -> Unit,
) {
    var stage by rememberSaveable { mutableStateOf(SetupStage.SCRIPT) }
    var scriptId by rememberSaveable { mutableStateOf<String?>(null) }
    var names by rememberSaveable { mutableStateOf(List(8) { "" }) }
    // A list, not a set: Village Idiot / Legion / Riot bags legally repeat ids.
    var bagIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showImport by rememberSaveable { mutableStateOf(false) }

    val imported by viewModel.importedScripts.collectAsState()
    val builtIn = remember { viewModel.gameData.builtInScripts() }
    val allScripts = builtIn + imported
    val script = allScripts.find { it.id == scriptId }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (stage) {
        SetupStage.SCRIPT -> ScriptStage(
            scripts = allScripts,
            viewModel = viewModel,
            onImport = { showImport = true },
            onDelete = { viewModel.deleteScript(it) },
            onPick = { picked ->
                if (scriptId != picked.id) {
                    bagIds = emptyList()
                }
                scriptId = picked.id
                stage = SetupStage.PLAYERS
            },
            onBack = onBack,
        )
        SetupStage.PLAYERS -> PlayersStage(
            names = names,
            onNames = { names = it },
            onNext = { stage = SetupStage.BAG },
            onBack = { stage = SetupStage.SCRIPT },
        )
        SetupStage.BAG -> script?.let { s ->
            BagStage(
                viewModel = viewModel,
                script = s,
                playerCount = names.size,
                bagIds = bagIds,
                onBagIds = { bagIds = it },
                onBack = { stage = SetupStage.PLAYERS },
                onStart = { deal, validatedBagIds ->
                    viewModel.startGame(s, names.mapIndexed { i, n -> n.ifBlank { "Player ${i + 1}" } })
                    if (deal && validatedBagIds.isNotEmpty()) {
                        viewModel.update { state ->
                            GameActions.deal(state, validatedBagIds, Random(System.nanoTime()))
                        }
                    }
                    onGameStarted()
                },
            )
        } ?: run { stage = SetupStage.SCRIPT }
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
}

@Composable
private fun ScriptStage(
    scripts: List<Script>,
    viewModel: GameViewModel,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onPick: (Script) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var fileError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileError = try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (text == null) "Couldn't read that file." else viewModel.importScript(text)
            } catch (e: Exception) {
                "Couldn't read that file: ${e.message}"
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        "STEP 1 OF 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Choose a script", style = MaterialTheme.typography.headlineMedium, color = AgedGold)
                }
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
        items(scripts, key = { it.id }) { script ->
            val unknown = remember(script) { viewModel.gameData.unknownIds(script) }
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(script) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(script.name, style = MaterialTheme.typography.titleLarge)
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
                        // A taste of the script: one token per team.
                        val preview = remember(script) {
                            val resolved = viewModel.gameData.resolve(script)
                            listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)
                                .mapNotNull { team -> resolved.firstOrNull { it.team == team } }
                        }
                        if (preview.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                for (c in preview) {
                                    CharacterToken(character = c, size = 34.dp)
                                }
                            }
                        }
                    }
                    if (!script.isBuiltIn) {
                        IconButton(onClick = { pendingDeleteId = script.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete script")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Import script (paste link or JSON)")
            }
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Import script from file (.json)")
            }
            fileError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
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

@Composable
private fun PlayersStage(
    names: List<String>,
    onNames: (List<String>) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "STEP 2 OF 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Players", style = MaterialTheme.typography.headlineMedium, color = AgedGold)
            Text(
                "${names.size} seats, clockwise from the top of the circle. " +
                    "Base distribution: ${distributionLabel(names.size)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(names.size, key = { it }) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}.",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = names[index],
                    onValueChange = { new -> onNames(names.toMutableList().also { it[index] = new }) },
                    placeholder = { Text("Player ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = names.size > Setup.MIN_PLAYERS,
                    onClick = { onNames(names.toMutableList().also { it.removeAt(index) }) },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove seat")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = names.size < Setup.MAX_PLAYERS,
                    onClick = { onNames(names + "") },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add seat")
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                FilledTonalButton(onClick = onNext) { Text("Next: build the bag") }
            }
        }
    }
}

private fun distributionLabel(count: Int): String {
    val d = Setup.distributionFor(count.coerceAtLeast(Setup.MIN_PLAYERS))
    return "${d.townsfolk} townsfolk / ${d.outsiders} outsiders / ${d.minions} minions / ${d.demons} demon"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BagStage(
    viewModel: GameViewModel,
    script: Script,
    playerCount: Int,
    bagIds: List<String>,
    onBagIds: (List<String>) -> Unit,
    onBack: () -> Unit,
    onStart: (deal: Boolean, validatedBagIds: List<String>) -> Unit,
) {
    val characters = remember(script) {
        viewModel.gameData.resolve(script).filter { it.team.isTownResident }
    }
    val byId = remember(characters) { characters.associateBy { it.id } }
    var search by rememberSaveable { mutableStateOf("") }
    var randomizeError by rememberSaveable(script.id, playerCount) { mutableStateOf<String?>(null) }
    val selected = bagIds.mapNotNull { byId[it] }
    val issues = GameActions.validateBag(selected, playerCount)
    val adjusted = Setup.adjustedDistribution(playerCount, selected)
    val modifiers = selected.mapNotNull { Setup.modifierFor(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "STEP 3 OF 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Build the bag", style = MaterialTheme.typography.headlineMedium, color = AgedGold)
            Text(
                "Need: ${adjusted.townsfolk} townsfolk · ${adjusted.outsiders} outsiders · " +
                    "${adjusted.minions} minions · ${adjusted.demons} demon" +
                    (if (modifiers.isNotEmpty()) "  (after ${modifiers.joinToString { "[${it.text}]" }})" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${selected.size} of $playerCount chosen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val bag = GameActions.randomBag(characters, playerCount)
                    if (bag != null) {
                        randomizeError = null
                        onBagIds(bag.map { it.id })
                    } else {
                        randomizeError =
                            "This script couldn't make a legal bag for $playerCount players. " +
                                "Check that it has enough characters in every team."
                    }
                }) { Text("Randomize") }
                TextButton(onClick = {
                    randomizeError = null
                    onBagIds(emptyList())
                }) { Text("Clear") }
            }
        }
        randomizeError?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (issues.isNotEmpty() && selected.isNotEmpty()) {
            item {
                Column {
                    for (issue in issues) {
                        Text(issue, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Running a Fabled or house rule that changes the counts? " +
                            "You can still deal this bag with the override button below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search characters…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val groups = characters
            .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
            .groupBy { it.team }
        for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)) {
            val members = groups[team] ?: continue
            item {
                Text(
                    team.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = team.color,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(members, key = { it.id }) { c ->
                BagRow(
                    character = c,
                    count = bagIds.count { it == c.id },
                    duplicable = c.id in GameActions.DUPLICABLE,
                    onAdd = {
                        randomizeError = null
                        onBagIds(bagIds + c.id)
                    },
                    onRemove = {
                        val index = bagIds.lastIndexOf(c.id)
                        if (index >= 0) {
                            randomizeError = null
                            onBagIds(bagIds.filterIndexed { i, _ -> i != index })
                        }
                    },
                )
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                FilledTonalButton(
                    enabled = selected.size == playerCount && issues.isEmpty(),
                    onClick = { onStart(true, selected.map { it.id }) },
                ) { Text("Deal randomly & start") }
                if (selected.size == playerCount && issues.isNotEmpty()) {
                    // Fabled (Sentinel...) and house rules legitimately bend the
                    // distribution — never let the checker block a real game.
                    OutlinedButton(onClick = { onStart(true, selected.map { it.id }) }) {
                        Text("Deal anyway (I know what I'm doing)")
                    }
                }
                OutlinedButton(onClick = { onStart(false, emptyList()) }) {
                    Text("Start empty (assign in grimoire)")
                }
            }
        }
    }
}

@Composable
private fun BagRow(
    character: Character,
    count: Int,
    duplicable: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
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
            Text("$count", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(20.dp))
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add copy")
            }
        } else {
            Checkbox(checked = count > 0, onCheckedChange = { if (count > 0) onRemove() else onAdd() })
        }
        CharacterToken(character = character, size = 40.dp)
        Spacer(Modifier.width(10.dp))
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
    }
}

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
