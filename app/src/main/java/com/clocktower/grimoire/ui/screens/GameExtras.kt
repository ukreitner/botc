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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.clocktower.engine.Bluffs
import com.clocktower.engine.Character
import com.clocktower.engine.Candidate
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.Selection
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.SetupRequirements
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameLog
import com.clocktower.engine.GameState
import com.clocktower.engine.HouseRules
import com.clocktower.engine.Team
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameActionsApi
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.overlayBottomPadding
import com.clocktower.grimoire.ui.components.rememberOverlayInsets
import com.clocktower.grimoire.ui.components.sheetActionPadding
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.TownsfolkBlue

/**
 * The whole transcript, grouped by phase — the engine's own [GameLog.rows], not
 * a second opinion.
 *
 * It used to rebuild the list from `deaths` + `nominations` alone, so a
 * recorded statement never reached it (C-10) and an execution that killed
 * nobody left no entry at all (C-11) — the one record the Undertaker, the
 * Vortox, the Mayor and the Zombuul all hinge on. `GameLog` already merges the
 * ledger (statements, private answers, announcements, rulings, notes), the
 * executions (`NO_EXECUTION` included), the identity changes, the nominations
 * with their voters and the deaths into one total order; this renders it.
 */
@Composable
fun GameLogDialog(
    state: GameState,
    /**
     * Character lookup, so rows can name the Empath rather than print "empath".
     * Defaulted to the script's own homebrew, because the shell owns the call
     * site; pass `viewModel::characterById` from there for the official names.
     */
    lookup: (String) -> Character? = { id -> state.script.customCharacters.find { it.id == id } },
    onDismiss: () -> Unit,
) {
    val rows = remember(state) { GameLog.rows(state, lookup) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game log") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = LOG_MAX_HEIGHT_DP.dp),
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "Nothing has happened yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(rows.size) { i ->
                    val row = rows[i]
                    // One heading per phase, so a day's business reads as a
                    // block rather than as a column of N2/D2/D2/D2 stamps.
                    val first = i == 0 ||
                        rows[i - 1].cycle != row.cycle ||
                        rows[i - 1].atNight != row.atNight
                    if (first) {
                        Spacer(Modifier.height(if (i == 0) 0.dp else 8.dp))
                        Text(
                            if (row.atNight) "NIGHT ${row.cycle}" else "DAY ${row.cycle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgedGold,
                        )
                    }
                    Text(row.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Cap the log like the shell's other list dialogs (ux/day-screen §K). */
private const val LOG_MAX_HEIGHT_DP = 420

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
                        IconButton(onClick = { viewModel.moveSeat(p.id, -1) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.moveSeat(p.id, +1) }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ---------------------------------------------------------------------------
// House rules (ux/day-screen §F, A-14)
// ---------------------------------------------------------------------------
// The writer itself is `GameActionsApi.setHouseRules` — a member of the
// interface, not an extension out here, so D26 ("every verb the UI calls is
// wired once, in one visible place") is literal.

/** The short names of the rules in force — for a collapsed card's summary. */
fun houseRuleLabels(rules: HouseRules): List<String> = buildList {
    if (rules.secretVotes) add("secret votes")
}

/**
 * The house-rules section: one switch per rule, each saying what it changes in
 * the storyteller's own words. Shared by the setup card and the in-game sheet.
 */
@Composable
fun HouseRulesSection(
    rules: HouseRules,
    onRules: (HouseRules) -> Unit,
) {
    Column {
        Text("HOUSE RULES", style = MaterialTheme.typography.labelLarge, color = AgedGold)
        Text(
            "Rules this table agreed on. Characters that do the same thing still " +
                "do it — turning a rule off never overrides them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HouseRuleRow(
            title = "Secret votes",
            detail = "Eyes closed for every vote. The tally, the verdict and the " +
                "block are hidden — hold the count to peek. (An Organ Grinder " +
                "does this on its own.)",
            checked = rules.secretVotes,
            onCheckedChange = { onRules(rules.copy(secretVotes = it)) },
        )
    }
}

@Composable
private fun HouseRuleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // One hit target for the whole row: the switch is 32 dp wide and
            // the storyteller is holding the phone one-handed.
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (checked) AgedGold else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
                .padding(bottom = overlayBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Fabled & house rules",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AgedGold,
                )
                Spacer(Modifier.height(8.dp))
                // The same section the setup card shows, so a rule agreed on
                // half-way through the first day is one tap away instead of a
                // new game (A-14, ux/day-screen §F).
                HouseRulesSection(
                    rules = state.houseRules,
                    onRules = { viewModel.setHouseRules(it) },
                )
                Spacer(Modifier.height(12.dp))
                Text("FABLED", style = MaterialTheme.typography.labelLarge, color = AgedGold)
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
                .padding(bottom = overlayBottomPadding()),
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
 * creating a Drunk on night 3 raises `drunk.token:<seat>` and the sheet re-opens.
 */
/**
 * "Open the checklist" as something any screen can ask for.
 *
 * Playtest A-4: the checklist had NO permanent entry point — the overflow menu
 * has no row for it and the begin-night guard's "Fix setup" button just closed
 * the dialog — so once dismissed, the storyteller's setup contract could only
 * be reopened by the side effect of assigning a character to a seat. The sheet
 * is rendered by [SetupIdentityPrompts], which the shell composes once; this is
 * how the rest of the app reaches it without every caller having to own the
 * sheet's state. Same shape as `UpdateManager.state`.
 */
object SetupChecklist {
    /** Bumped by [open]; [SetupIdentityPrompts] raises the sheet when it changes. */
    var openRequests by mutableStateOf(0)
        private set

    /** Raise the "Before the first night" sheet, wherever the storyteller is. */
    fun open() {
        openRequests += 1
    }
}

/**
 * The obligations whose ARRIVAL is not, on its own, worth interrupting for
 * (FW3-1).
 *
 * The bag's legality and the bluff lists are what the storyteller is actively
 * composing: every hand-assignment rewrites the bag rows ("Outsider: 0 in bag,
 * expected 0" becomes "Outsider: 1 in bag, expected 0"), and seating the Demon
 * conjures "Demon bluffs" out of nothing. Neither is a hidden obligation — the
 * bag is on the setup screen and in the begin-night guard, the bluffs are on the
 * Demon's own night step — while a Drunk's believed character, a Marionette's
 * seat or a Traveller's alignment exist NOWHERE else and gate the hand-out.
 *
 * So they are collapsed to one id apiece: they still fill the checklist, they
 * still block "Begin night", and they still raise it the first time the list has
 * anything in it — they just stop re-raising it over every second seat.
 */
private val QUIET_REQUIREMENTS = setOf("bag", "bluffs")

/** [QUIET_REQUIREMENTS] folding: `bag.3` -> `bag`, any bluff row -> `bluffs`. */
private fun raiseId(row: SetupRequirement): String = when {
    row.id.startsWith("bag.") -> "bag"
    row.kind == RequirementKind.BLUFFS -> "bluffs"
    else -> row.id
}

@Composable
fun SetupIdentityPrompts(
    viewModel: GameViewModel,
    state: GameState,
) {
    val lookup = viewModel::characterById
    val blockingIds = remember(state) {
        SetupRequirements.unmet(state, lookup).filter { it.blocking }.map(::raiseId).distinct()
    }
    // "Seen" is the set of blocking rows the storyteller has already dismissed.
    // A NEW blocking row (a mid-game identity change) re-raises the sheet.
    var dismissedKey by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(false) }
    // FW3-1: the auto-raise is OWED, not taken. Assigning the Drunk from the
    // seat sheet adds a requirement row, and the checklist used to slide up on
    // top of the sheet the storyteller was working in — `back` then closed the
    // checklist, not the sheet, so it cost a tap on every assignment and blocked
    // `C_setup10` / `C_setup_rest` outright. It is D78's complaint about
    // openers, now about the auto-raise: nothing raises itself over an open
    // sheet; it waits for that sheet to close.
    var owed by rememberSaveable { mutableStateOf(false) }
    // Every sheet and dialog in this app is a window of its own, so while one is
    // up the main window is NOT focused. That is the whole test — it needs no
    // cooperation from the sheets themselves, which is what keeps this local.
    val topmost = LocalWindowInfo.current.isWindowFocused

    val seen = dismissedKey.split('|').filter { it.isNotEmpty() }.toSet()
    // The list raises itself the first time it has anything in it, and after
    // that only for an obligation the storyteller has not been shown — never for
    // a [QUIET_REQUIREMENTS] id on its own.
    val shouldRaise = blockingIds.isNotEmpty() && (
        seen.isEmpty() ||
            blockingIds.any { it !in seen && it !in QUIET_REQUIREMENTS }
        )

    LaunchedEffect(blockingIds) {
        when {
            // Nothing outstanding: forget what was dismissed, so the SAME set
            // of rows raised again later (a Pit-Hag re-creating a Drunk) still
            // re-opens the sheet.
            blockingIds.isEmpty() -> { dismissedKey = ""; owed = false }
            shouldRaise -> owed = true
        }
    }
    LaunchedEffect(owed, topmost, open) {
        if (owed && topmost && !open) {
            open = true
            owed = false
        }
    }
    // Someone asked for it explicitly ("Fix setup", the hand-out screen): open
    // it whatever the checklist currently owes, including nothing at all. A
    // request is the storyteller's own tap, so it is never deferred.
    val requests = SetupChecklist.openRequests
    LaunchedEffect(requests) { if (requests > 0) open = true }
    if (open) {
        SetupChecklistSheet(
            viewModel = viewModel,
            state = state,
            onDismiss = {
                open = false
                dismissedKey = blockingIds.joinToString("|")
                // Answering a row from inside the sheet changes the id list,
                // which would otherwise leave a raise owed and re-open the sheet
                // the instant it closed.
                owed = false
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
    // Row ids carry their seat now ("drunk.token:3"), so they are unique inside
    // one checklist: they key the list and identify the open dialog directly.
    val satisfied = remember(state, rows) {
        rows.associate { it.id to it.satisfied(state, lookup) }
    }
    val doneCount = satisfied.count { it.value }
    var openRowId by rememberSaveable { mutableStateOf<String?>(null) }
    var bluffKey by rememberSaveable { mutableStateOf<String?>(null) }
    // `SetupRequirements` builds its BLUFFS rows straight from this list, in
    // this order, so pairing them off maps "snitch.bluffs:7" onto "snitch:7"
    // without this screen ever parsing a row id or naming a character.
    val bluffKeyOf = remember(state, rows) {
        rows.filter { it.kind == RequirementKind.BLUFFS }
            .map { it.id }
            .zip(Bluffs.requirements(state, lookup).map { it.key })
            .toMap()
    }

    // The bluff picker is itself a bottom sheet, so it REPLACES the checklist
    // rather than stacking on it; closing it comes back here.
    if (bluffKey != null) {
        BluffsSheet(viewModel, state, onDismiss = { bluffKey = null }, initialKey = bluffKey)
        return
    }

    // Measured OUTSIDE the sheet: a ModalBottomSheet reports its insets as
    // already consumed, so nothing inside it can see the home indicator.
    val insets = rememberOverlayInsets()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Without this the sheet opens HALF expanded, is measured against the
        // full content height anyway, and simply overflows the bottom of the
        // screen — which is how a six-row checklist lost its Close button. Fully
        // expanded, the content is bounded by the screen, so `weight` below can
        // give the list what is left after the pinned button.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // A-3: the ROWS scroll, the Close button does not. With six rows the
        // sheet used to grow past the bottom of the screen and take its own
        // only dismissal button with it — `ui.py tap "^Close$"` answered
        // OFFSCREEN, and the row count grows with the script.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            LazyColumn(
                // `fill = false`: a two-row checklist still renders a short
                // sheet rather than a full-height one.
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
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
                items(rows, key = { "req-" + it.id }) { row ->
                    val ok = satisfied[row.id] == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (row.kind == RequirementKind.BLUFFS) {
                                    bluffKey = bluffKeyOf[row.id] ?: bluffKeyOf.values.firstOrNull()
                                } else {
                                    openRowId = row.id
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
                            // A-15: a ticked row shows the ANSWER, not the
                            // question it no longer asks — "✓ The Drunk
                            // believes / Chambermaid".
                            val given = if (ok) row.answer(state, lookup) else ""
                            Text(
                                given.ifBlank { row.prompt },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (given.isNotBlank()) {
                                    AgedGold
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                }
            }
            // Pinned INSIDE the safe area, below the scrolling list.
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .sheetActionPadding(insets),
            ) {
                Text("Close")
            }
        }
    }

    rows.firstOrNull { it.id == openRowId }?.let { row ->
        SetupRequirementDialog(
            viewModel = viewModel,
            state = state,
            requirement = row,
            onDismiss = { openRowId = null },
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
    onDismiss: () -> Unit,
) {
    val lookup = viewModel::characterById
    val rowId = requirement.id
    val candidates = remember(state, rowId) { requirement.candidates(state, lookup) }
    // Rows that place a token on SEVERAL seats at once. Advisory rows that name
    // no single holder are the only ones this applies to today (the Lunatic's
    // fake Minions); everything else takes exactly one answer.
    val multi = requirement.kind == RequirementKind.REMINDER &&
        !requirement.blocking && candidates.size > 1
    // Rows that also (or only) take something typed: any number, and a secret
    // the engine offers no list for (the Mezepheles' word). A GRANT that DOES
    // offer characters must be answered from that list — its stored value is a
    // character id, not prose.
    val typed = requirement.kind == RequirementKind.NUMBER ||
        (requirement.kind == RequirementKind.GRANT && candidates.isEmpty())
    var chosen by rememberSaveable(rowId) { mutableStateOf(ArrayList<String>() as List<String>) }
    var freeText by rememberSaveable(rowId) { mutableStateOf("") }

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
                if (candidates.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
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
                }

                // A free-text secret (the Mezepheles' word), or a number — kept
                // alongside the chips for the count a jinx or a ruling puts
                // outside the bracket's own list.
                if (typed) {
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { entered ->
                            freeText = if (requirement.kind == RequirementKind.NUMBER) {
                                entered.filter { it.isDigit() }.take(2)
                            } else {
                                entered
                            }
                        },
                        label = {
                            Text(
                                when {
                                    requirement.kind != RequirementKind.NUMBER -> "Write it down"
                                    candidates.isEmpty() -> "Number"
                                    else -> "Or type another number"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (candidates.isEmpty() && !typed) {
                    Text(
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

                // A typed answer wins over the chips whenever there is one, so
                // the number field still works now that the chips exist.
                typed && requirement.kind == RequirementKind.NUMBER -> FilledTonalButton(
                    enabled = freeText.toIntOrNull() != null,
                    onClick = {
                        apply(Selection(number = freeText.toIntOrNull(), text = freeText))
                    },
                ) { Text("Save") }

                typed -> FilledTonalButton(
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
