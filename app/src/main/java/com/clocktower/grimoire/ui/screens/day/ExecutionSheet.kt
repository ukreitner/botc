package com.clocktower.grimoire.ui.screens.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Deaths
import com.clocktower.engine.ExecutionVia
import com.clocktower.engine.GameState
import com.clocktower.engine.KillOutcome
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.overlayBottomPadding
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.FadedInk
import com.clocktower.grimoire.ui.theme.PaleGold
import com.clocktower.grimoire.ui.theme.PoisonGreen

/**
 * THE execution confirmation sheet — the convergence point the Devil's
 * Advocate, Psychopath, Barber, Boomdandy, Cannibal and Virgin audits all
 * asked for independently (ux/day-screen §G, ARCHITECTURE §3.2, I5).
 *
 * Everything it shows comes from the funnel that will apply it:
 * `executionPreview` is `Deaths.killOutcome` for `DeathCause.EXECUTION` — the
 * same fifteen-step table `Execution.execute` runs — so the storyteller sees
 * *"Fay is marked Survives Execution"* **before** the button, never after the
 * death. An unanswered `KillOutcome.Choice` (Pacifist, Scapegoat, Mayor
 * bounce) is asked here and re-called with its option id; `Execution.execute`
 * returns the state untouched until it is answered (D61).
 *
 * **"Executed — but they don't die" is a first-class button**, not a dialog
 * hidden behind a protection: an execution that kills nobody is still the
 * day's execution, and the Vortox, the Mayor, the Zombuul, the Godfather and
 * the Undertaker all hinge on that record existing (lead D30).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionSheet(
    viewModel: GameViewModel,
    state: GameState,
    targetId: Long,
    /** The nomination that put them on the block, so the record keeps its nominator. */
    nominatorId: Long?,
    nominationIndex: Int?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ExecutionSheetBody(viewModel, state, targetId, nominatorId, nominationIndex, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod")
private fun ExecutionSheetBody(
    viewModel: GameViewModel,
    state: GameState,
    targetId: Long,
    nominatorId: Long?,
    nominationIndex: Int?,
    onDismiss: () -> Unit,
) {
    val target = state.player(targetId)
    if (target == null) {
        onDismiss()
        return
    }
    val preview = remember(state, targetId) { viewModel.executionPreview(state, targetId) }
    val secret = viewModel.secretVoting(state)

    fun execute(optionId: String = "") {
        viewModel.execute(
            playerId = targetId,
            nominatorId = nominatorId,
            via = ExecutionVia.VOTE,
            nominationIndex = nominationIndex,
            optionId = optionId,
        )
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = overlayBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Execute ${target.name}?",
            style = MaterialTheme.typography.headlineSmall,
            color = AgedGold,
        )

        Text("Before you execute:", style = MaterialTheme.typography.titleSmall)
        PreviewLine(preview)

        when (preview) {
            is KillOutcome.Choice -> {
                Text(
                    preview.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaleGold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in preview.options) {
                        AssistChip(
                            onClick = { execute(option.id) },
                            label = { Text(option.label) },
                        )
                    }
                    // The three canonical answers, in case a registry row
                    // offered none of its own.
                    if (preview.options.isEmpty()) {
                        AssistChip(
                            onClick = { execute(Deaths.OPTION_DIES) },
                            label = { Text("They die") },
                        )
                        AssistChip(
                            onClick = { execute(Deaths.OPTION_LIVES) },
                            label = { Text("They live") },
                        )
                    }
                }
            }

            else -> {
                Button(
                    onClick = { execute() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (secret) {
                            "EXECUTE ${target.name.uppercase()}"
                        } else {
                            executeLabel(preview, target.name)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // First-class, always present: an execution that kills nobody is still
        // an execution (lead D30, day-engine §F).
        OutlinedButton(
            onClick = {
                viewModel.executeButSurvives(playerId = targetId, nominatorId = nominatorId)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Executed — but they don't die") }

        Text(
            "Say \"${target.name} is executed\", then \"${target.name} is still alive.\" " +
                "Never say why.",
            style = MaterialTheme.typography.bodySmall,
            color = FadedInk,
        )

        HorizontalDivider()
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Not yet") }
    }
}

/**
 * Exile, with its own visual identity so it can never be mistaken for an
 * execution (§H): an exile is not the day's execution, no ability modifies it,
 * no ghost vote is spent, and it writes no `ExecutionRecord`. It still goes
 * through the kill funnel, so a Choice is answered before anything is written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExileSheet(
    viewModel: GameViewModel,
    state: GameState,
    targetId: Long,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val target = state.player(targetId)
        if (target == null) {
            onDismiss()
            return@ModalBottomSheet
        }
        val preview = remember(state, targetId) { viewModel.exilePreview(state, targetId) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = overlayBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Exile ${target.name}?",
                style = MaterialTheme.typography.headlineSmall,
                color = AgedGold,
            )
            Text(
                "An exile is not an execution: today's execution stays available, " +
                    "no vote token is spent, and no ability changes the result.",
                style = MaterialTheme.typography.bodySmall,
                color = FadedInk,
            )
            PreviewLine(preview)
            Button(
                onClick = {
                    viewModel.exile(targetId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("EXILE ${target.name.uppercase()}", fontWeight = FontWeight.Bold) }
            HorizontalDivider()
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Not yet") }
        }
    }
}

/** The execution's outcome in the button, never the verb (§3.2, night-card rule). */
fun executeLabel(preview: KillOutcome, name: String): String = when (preview) {
    is KillOutcome.Dies -> "${name.uppercase()} IS EXECUTED AND DIES"
    is KillOutcome.Prevented, is KillOutcome.Spends ->
        "${name.uppercase()} IS EXECUTED — AND LIVES"

    is KillOutcome.RegistersDead -> "${name.uppercase()} IS EXECUTED — REGISTERS DEAD"
    is KillOutcome.Redirect -> "${name.uppercase()} IS EXECUTED — THE DEATH MOVES"
    KillOutcome.AlreadyDead -> "EXECUTE ${name.uppercase()} (ALREADY DEAD)"
    is KillOutcome.Choice -> "EXECUTE ${name.uppercase()}"
}

@Composable
private fun PreviewLine(preview: KillOutcome) {
    val (text, color) = when (preview) {
        is KillOutcome.Dies ->
            preview.reason.ifBlank { "Nothing stops it — they die." } to EmberRed

        is KillOutcome.Prevented -> "${preview.reason}\n${preview.announce}" to PoisonGreen
        is KillOutcome.Spends ->
            ((preview.inner as? KillOutcome.Prevented)?.reason
                ?: "They survive, and the ability is spent.") to PoisonGreen

        is KillOutcome.RegistersDead -> preview.reason to PaleGold
        is KillOutcome.Redirect -> preview.reason to PaleGold
        is KillOutcome.Choice -> preview.question to AgedGold
        KillOutcome.AlreadyDead ->
            "A dead player cannot die again — but the execution still counts." to FadedInk
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
