package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameState
import com.clocktower.engine.Phase
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
 * The engine-side phase decision. WP6 implements it on top of `Briefings.at`
 * and `WinCheck.duskCheck`; until then [requestPhaseAdvance] carries the
 * shipped behaviour, moved verbatim out of `GameShell`.
 */
object PhaseFlow {
    fun request(state: GameState, lookup: (String) -> Character?): PhaseRequest = TODO("WP6")
}

/**
 * The three guards the phase button can raise, as UI state. WP0 moved these
 * out of `GameShell` unchanged so WP8 owns only tabs, scaffold, top bar and
 * scrim, and WP6 can replace the bodies with [PhaseFlow.request].
 */
class PhaseGuards {
    /** Setup problems that must be fixed (or deliberately ignored) first. */
    var setupIssues by mutableStateOf(listOf<String>())

    /** Someone is on the block and has not been executed. */
    var duskGuard by mutableStateOf(false)

    /** Night steps still unticked, by title. */
    var unfinishedNightSteps by mutableStateOf(listOf<String>())

    /** Debounce: an accidental double tap must not skip a whole phase. */
    var lastAdvanceAt by mutableLongStateOf(0L)
}

@Composable
internal fun rememberPhaseGuards(): PhaseGuards = remember { PhaseGuards() }

/**
 * Runs the phase button. Returns the tab to switch to, or null when a guard
 * dialog opened instead. Moved verbatim from `GameShell.requestPhaseAdvance`.
 */
internal fun requestPhaseAdvance(
    viewModel: GameViewModel,
    state: GameState,
    guards: PhaseGuards,
): GameTab? {
    // Debounce: an accidental double tap must not skip a whole phase.
    val nowMs = com.clocktower.engine.Time.epochMillis()
    if (nowMs - guards.lastAdvanceAt < 800) return null
    guards.lastAdvanceAt = nowMs
    // Setup guard: empty/manual games must meet the same adjusted team
    // distribution as the bag builder before first night can begin.
    if (state.phase == Phase.SETUP) {
        val issues = GameActions.validateSetupState(state, viewModel::characterById)
        if (issues.isNotEmpty()) {
            guards.setupIssues = issues
            return GameTab.GRIMOIRE
        }
    }
    // Dusk guard: someone is on the block and hasn't died.
    val onBlock = GameActions.aboutToDie(state)?.let { state.player(it) }
    if (state.phase == Phase.DAY && onBlock?.alive == true) {
        guards.duskGuard = true
        return null
    }
    if (state.phase == Phase.NIGHT) {
        val nightSteps = if (state.cycle == 1) {
            viewModel.gameData.nightOrder.firstNight(state, viewModel::characterById)
        } else {
            viewModel.gameData.nightOrder.otherNight(state, viewModel::characterById)
        }
        val unfinished = nightSteps
            .filterNot { it.id in state.nightStepsDone }
            .map { it.title }
        if (unfinished.isNotEmpty()) {
            guards.unfinishedNightSteps = unfinished
            return GameTab.NIGHT
        }
    }
    viewModel.advancePhase()
    // Jump to the tab that matters for the new phase.
    return when (state.phase) {
        Phase.SETUP, Phase.DAY -> GameTab.NIGHT
        Phase.NIGHT -> GameTab.DAY
    }
}

/** The three guard dialogs, moved verbatim out of `GameShell`. */
@Composable
internal fun PhaseGuardDialogs(
    viewModel: GameViewModel,
    state: GameState,
    guards: PhaseGuards,
    onTab: (GameTab) -> Unit,
) {
    if (guards.setupIssues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { guards.setupIssues = emptyList() },
            title = { Text("Setup isn't legal yet") },
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
                }) { Text("Start the night anyway") }
            },
        )
    }
    if (guards.duskGuard) {
        val onBlock = GameActions.aboutToDie(state)?.let { state.player(it) }
        AlertDialog(
            onDismissRequest = { guards.duskGuard = false },
            title = { Text("Dusk falls") },
            text = {
                Text(
                    "${onBlock?.name ?: "Someone"} is on the block and hasn't been executed. " +
                        "Execute before night?",
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    guards.duskGuard = false
                    onBlock?.let { viewModel.kill(it.id, DeathCause.EXECUTION) }
                    viewModel.advancePhase()
                    onTab(GameTab.NIGHT)
                }) { Text("Execute & begin night") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        guards.duskGuard = false
                        viewModel.advancePhase()
                        onTab(GameTab.NIGHT)
                    }) { Text("No execution") }
                    TextButton(onClick = { guards.duskGuard = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (guards.unfinishedNightSteps.isNotEmpty()) {
        val count = guards.unfinishedNightSteps.size
        AlertDialog(
            onDismissRequest = { guards.unfinishedNightSteps = emptyList() },
            title = { Text("Night checklist incomplete") },
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
                    val expectedCycle = state.cycle
                    viewModel.update { current ->
                        if (current.phase == Phase.NIGHT && current.cycle == expectedCycle) {
                            GameActions.advancePhase(current)
                        } else {
                            current
                        }
                    }
                    onTab(GameTab.DAY)
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
}
