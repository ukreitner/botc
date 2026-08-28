package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Briefing
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Briefings
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.ExecutionVia
import com.clocktower.engine.GameState
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Phase
import com.clocktower.engine.Prompts
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.GameViewModel

/** The in-game tabs. Moved out of GameShell by WP0 so the phase flow can steer them. */
internal enum class GameTab { GRIMOIRE, NIGHT, DAY, REFERENCE }

/** What the phase button should do right now (ARCHITECTURE §3.2). */
sealed interface PhaseRequest {
    /** Setup requirements or unfinished required night steps block the advance. */
    data class Blocked(val title: String, val items: List<BriefingItem>) : PhaseRequest

    data class ConfirmDawn(val briefing: Briefing) : PhaseRequest

    data class ConfirmDusk(
        val briefing: Briefing,
        val advisories: List<WinCheck.Advisory>,
    ) : PhaseRequest

    data object Advance : PhaseRequest
}

/**
 * The engine-side phase decision (WP6).
 *
 * Pure: the caller decides what to render. Tapping "Dawn" stops being a silent
 * state change (friction §5) — it returns the briefing computed on the state as
 * it stands NOW, with every token the night placed still on the grimoire, which
 * is the same instant `Phases.advancePhase` freezes into `GameState.lastDawn`.
 */
object PhaseFlow {

    /** Blocked title for a setup that does not yet meet the bag rules. */
    const val TITLE_SETUP: String = "Setup isn't legal yet"

    /** Blocked title for a night with required steps still unticked. */
    const val TITLE_NIGHT: String = "Night checklist incomplete"

    fun request(state: GameState, lookup: (String) -> Character?): PhaseRequest = when (state.phase) {
        Phase.SETUP -> setupBlockers(state, lookup)?.let {
            PhaseRequest.Blocked(TITLE_SETUP, it)
        } ?: PhaseRequest.Advance

        // Only REQUIRED steps block the dawn: a gated-off row is auto-ticked by
        // the planner and renders grey with a [Run anyway] (lead D37/D60), so it
        // must never hold the storyteller up here.
        Phase.NIGHT -> unfinishedSteps(state, lookup)?.let {
            PhaseRequest.Blocked(TITLE_NIGHT, it)
        } ?: PhaseRequest.ConfirmDawn(Briefings.at(state, lookup, BriefingSlot.DAWN))

        Phase.DAY -> PhaseRequest.ConfirmDusk(
            briefing = Briefings.at(state, lookup, BriefingSlot.DUSK),
            advisories = WinCheck.duskCheck(state, lookup),
        )
    }

    private fun setupBlockers(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<BriefingItem>? {
        val problems = SetupRequirements.unmet(state, lookup).filter { it.blocking }
        if (problems.isEmpty()) return null
        return problems.mapIndexed { index, requirement ->
            BriefingItem(
                key = "setup:${requirement.id}:$index",
                kind = BriefingKind.TODO_ASK,
                severity = BriefingSeverity.ALERT,
                sourceId = requirement.characterId,
                text = requirement.problem.ifBlank { requirement.title },
            )
        }
    }

    private fun unfinishedSteps(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<BriefingItem>? {
        val unfinished = NightPlan.build(state, lookup).unfinished(state.nightStepsDone)
        // A question the engine raised tonight and is still owed — the Imp's
        // star pass — blocks the dawn exactly as an unticked row does: the
        // night cannot end with no Demon on the board (playtest B P0 #3).
        val owed = Prompts.due(state, BriefingSlot.NOW)
        if (unfinished.isEmpty() && owed.isEmpty()) return null
        return unfinished.map { step ->
            BriefingItem(
                key = "night-step:${step.key.token}",
                kind = BriefingKind.TODO_ASK,
                severity = BriefingSeverity.ACTION,
                sourceId = step.abilityId,
                text = step.title,
                playerId = step.holderId,
            )
        } + owed.map { prompt ->
            BriefingItem(
                key = "prompt:${prompt.id}",
                kind = BriefingKind.TODO_ASK,
                severity = BriefingSeverity.ALERT,
                sourceId = prompt.sourceId,
                text = prompt.title,
                playerId = prompt.subjectPlayerId,
                promptId = prompt.id,
            )
        }
    }
}

/**
 * The guards and sheets the phase button can raise, as UI state. WP0 moved these
 * out of `GameShell`; WP6 drives them from [PhaseFlow.request] so `GameShell`
 * owns only tabs, scaffold, top bar and scrim.
 */
class PhaseGuards {
    /** Setup problems that must be fixed (or deliberately ignored) first. */
    var setupIssues by mutableStateOf(listOf<String>())

    /** Night steps still unticked, by title. */
    var unfinishedNightSteps by mutableStateOf(listOf<String>())

    /** The read-aloud dawn card, shown before the day opens. */
    var dawn by mutableStateOf<Briefing?>(null)

    /** The dusk sheet, shown before the night begins. */
    var dusk by mutableStateOf<Briefing?>(null)

    /** Blocking endings the dusk sheet must show alongside the briefing. */
    var duskAdvisories by mutableStateOf(listOf<WinCheck.Advisory>())

    /** Debounce: an accidental double tap must not skip a whole phase. */
    var lastAdvanceAt by mutableLongStateOf(0L)

    /** Someone is on the block and has not been executed. */
    var onBlockId by mutableStateOf<Long?>(null)

    /**
     * Keys of the briefing items already acted on from the open sheet. The
     * briefing itself is a frozen snapshot, so a line whose obligation the tap
     * just retired would otherwise look untouched: these are ticked and go
     * quiet instead.
     */
    var actedKeys by mutableStateOf(setOf<String>())

    internal fun clear() {
        setupIssues = emptyList()
        unfinishedNightSteps = emptyList()
        dawn = null
        dusk = null
        duskAdvisories = emptyList()
        onBlockId = null
        actedKeys = emptySet()
    }
}

@Composable
internal fun rememberPhaseGuards(): PhaseGuards = remember { PhaseGuards() }

/**
 * Runs the phase button. Returns the tab to switch to, or null when a guard or
 * a briefing sheet opened instead.
 */
internal fun requestPhaseAdvance(
    viewModel: GameViewModel,
    state: GameState,
    guards: PhaseGuards,
): GameTab? {
    // Debounce: an accidental double tap must not skip a whole phase.
    val nowMs = com.clocktower.engine.Time.epochMillis()
    if (nowMs - guards.lastAdvanceAt < DEBOUNCE_MS) return null
    guards.lastAdvanceAt = nowMs

    return when (val request = PhaseFlow.request(state, viewModel::characterById)) {
        is PhaseRequest.Blocked -> {
            val lines = request.items.map { it.text }
            if (state.phase == Phase.SETUP) {
                guards.setupIssues = lines
                GameTab.GRIMOIRE
            } else {
                guards.unfinishedNightSteps = lines
                GameTab.NIGHT
            }
        }

        is PhaseRequest.ConfirmDawn -> {
            guards.dawn = request.briefing
            null
        }

        is PhaseRequest.ConfirmDusk -> {
            guards.dusk = request.briefing
            guards.duskAdvisories = request.advisories.filter { it.blocking }
            guards.onBlockId = DayRules.aboutToDie(state)
                ?.takeIf { state.player(it)?.alive == true && !DayRules.executionSpent(state) }
            null
        }

        PhaseRequest.Advance -> {
            viewModel.advancePhase()
            GameTab.NIGHT
        }
    }
}

/** An accidental double tap must not skip a whole phase. */
private const val DEBOUNCE_MS = 800L

/**
 * The guard dialogs and the two read-aloud briefing sheets.
 *
 * [onItem] is the one-tap follow-through on a briefing line's `actionId`
 * (`open-seat:7`, `record:gossip`, …). The shell owns it because half the
 * prefixes are navigation — a seat sheet, a tab — which only the shell can
 * perform; it returns true when the item was consumed, so the line can be
 * ticked off here. Defaulted so a caller that wants read-only cards needs no
 * change.
 */
@Composable
internal fun PhaseGuardDialogs(
    viewModel: GameViewModel,
    state: GameState,
    guards: PhaseGuards,
    onItem: (BriefingItem) -> Boolean = { false },
    onTab: (GameTab) -> Unit,
) {
    val handleItem: (BriefingItem) -> Unit = { item ->
        if (onItem(item)) guards.actedKeys = guards.actedKeys + item.key
    }
    if (guards.setupIssues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { guards.setupIssues = emptyList() },
            title = { Text(PhaseFlow.TITLE_SETUP) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fix these issues before beginning the first night:")
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(guards.setupIssues) { issue ->
                            Text(
                                "• $issue",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        "Running a Fabled or house rule the checker doesn't know? " +
                            "You can start anyway — the guard only advises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    guards.setupIssues = emptyList()
                    onTab(GameTab.GRIMOIRE)
                }) { Text("Fix setup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    guards.setupIssues = emptyList()
                    viewModel.advancePhase()
                    onTab(GameTab.NIGHT)
                }) { Text("Start the night anyway") }
            },
        )
    }

    if (guards.unfinishedNightSteps.isNotEmpty()) {
        val count = guards.unfinishedNightSteps.size
        AlertDialog(
            onDismissRequest = { guards.unfinishedNightSteps = emptyList() },
            title = { Text(PhaseFlow.TITLE_NIGHT) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count step${if (count == 1) " is" else "s are"} still unchecked:")
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(guards.unfinishedNightSteps) { title ->
                            Text(
                                "• $title",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    guards.unfinishedNightSteps = emptyList()
                    // The dawn card still runs: skipping the checklist must not
                    // skip the report (friction §5).
                    guards.dawn = Briefings.at(
                        state,
                        viewModel::characterById,
                        BriefingSlot.DAWN,
                    )
                }) { Text("Dawn anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    guards.unfinishedNightSteps = emptyList()
                    onTab(GameTab.NIGHT)
                }) { Text("Keep checking") }
            },
        )
    }

    guards.dawn?.let { briefing ->
        BriefingSheet(
            briefing = briefing,
            title = "Dawn · night ${briefing.cycle}",
            confirmLabel = "OPEN DAY ${briefing.cycle} →",
            onConfirm = {
                val expectedCycle = state.cycle
                guards.clear()
                viewModel.update { current ->
                    if (current.phase == Phase.NIGHT && current.cycle == expectedCycle) {
                        com.clocktower.engine.Phases.advancePhase(current, viewModel::characterById)
                    } else {
                        current
                    }
                }
                onTab(GameTab.DAY)
            },
            onDismiss = { guards.dawn = null },
            acted = guards.actedKeys,
            onItem = handleItem,
        )
    }

    guards.dusk?.let { briefing ->
        val onBlock = guards.onBlockId?.let { state.player(it) }
        BriefingSheet(
            briefing = briefing,
            title = "Dusk · day ${briefing.cycle}",
            confirmLabel = if (onBlock == null) {
                "BEGIN NIGHT ${briefing.cycle + 1} →"
            } else {
                "EXECUTE ${onBlock.name.uppercase()} & BEGIN NIGHT"
            },
            advisories = guards.duskAdvisories,
            onConfirm = {
                val target = onBlock?.id
                val index = target?.let { blockingNominationIndex(state, it) }
                guards.clear()
                // One execution funnel, always (lead D24): never a bare kill.
                target?.let {
                    viewModel.execute(it, via = ExecutionVia.VOTE, nominationIndex = index)
                }
                viewModel.advancePhase()
                onTab(GameTab.NIGHT)
            },
            onDismiss = { guards.clear() },
            // A day that closes with nobody executed is a RECORD, not an absence
            // (lead D30): the Mayor, the Vortox and the Zombuul all read it.
            secondaryLabel = "No execution".takeIf { !DayRules.executionSpent(state) },
            onSecondary = {
                guards.clear()
                viewModel.noExecution()
                viewModel.advancePhase()
                onTab(GameTab.NIGHT)
            },
            acted = guards.actedKeys,
            onItem = handleItem,
        )
    }
}

/**
 * The nomination that put [playerId] on the block today, so the execution keeps
 * its nominator — the Fearmonger's win depends on it.
 */
private fun blockingNominationIndex(state: GameState, playerId: Long): Int? =
    state.nominations.indexOfLast {
        it.day == state.cycle && !it.isExile && it.nomineeId == playerId
    }.takeIf { it >= 0 }

/**
 * The read-aloud card of ARCHITECTURE §3.2: ANNOUNCE lines first, PRIVATE
 * below, SWEPT and TODO_ASK under that, and one primary button.
 *
 * A line that carries an `actionId` is tappable: [onItem] performs it. Lines
 * whose key is in [acted] are already done and read as ticked.
 */
@Composable
@Suppress("LongParameterList")
private fun BriefingSheet(
    briefing: Briefing,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    advisories: List<WinCheck.Advisory> = emptyList(),
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    acted: Set<String> = emptySet(),
    onItem: (BriefingItem) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (advisory in advisories) {
                    item(key = "advisory:${advisory.ruleId}") {
                        Text(
                            advisory.reason,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                for (section in SECTIONS) {
                    val lines = briefing.of(section.kind)
                    if (lines.isEmpty()) continue
                    item(key = "heading:${section.kind}") {
                        Text(
                            section.heading,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(lines, key = { it.key }) { line ->
                        val done = line.key in acted
                        val actionable = line.actionId.isNotBlank() && !done
                        Text(
                            text = if (done) "✓ ${line.text}" else "• ${line.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                done -> MaterialTheme.colorScheme.onSurfaceVariant
                                line.severity == BriefingSeverity.ALERT ->
                                    MaterialTheme.colorScheme.error
                                actionable -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            // 44 dp of height: a briefing line is tapped in a
                            // dark room, standing up, holding a grimoire.
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (actionable) {
                                        Modifier
                                            .heightIn(min = 44.dp)
                                            .clickable { onItem(line) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            Row {
                secondaryLabel?.let { TextButton(onClick = onSecondary) { Text(it) } }
                TextButton(onClick = onDismiss) { Text("Not yet") }
            }
        },
    )
}

/** The card's sections, in reading order. */
private data class BriefingSection(val kind: BriefingKind, val heading: String)

private val SECTIONS: List<BriefingSection> = listOf(
    BriefingSection(BriefingKind.ANNOUNCE, "SAY OUT LOUD, IN THIS ORDER"),
    BriefingSection(BriefingKind.PRIVATE, "DO NOT SAY — your notes"),
    BriefingSection(BriefingKind.TODO_ASK, "BEFORE YOU MOVE ON"),
    BriefingSection(BriefingKind.STANDING_FACT, "TRUE NOW"),
    BriefingSection(BriefingKind.SWEPT, "TAKEN OFF THE GRIMOIRE"),
)
