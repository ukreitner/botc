package com.clocktower.grimoire.ui.screens.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.engine.DayAbilities
import com.clocktower.engine.GameState
import com.clocktower.grimoire.ui.GameViewModel

/** Day choices declared by the engine, with the same confirmation and undo boundary on both platforms. */
@Composable
fun DayAbilitiesPanel(viewModel: GameViewModel, state: GameState) {
    val offers = DayAbilities.forState(state, viewModel.lookup).filter { it.ability.resolve != null }
    var selected by remember(state.cycle) { mutableStateOf<Pair<String, Long>?>(null) }
    var target by remember(selected) { mutableStateOf<Long?>(null) }
    if (offers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Day abilities")
        for (offer in offers) {
            OutlinedButton(
                enabled = offer.available && offer.holderId != null,
                onClick = { selected = offer.sourceId to offer.holderId!! },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${offer.holderName} · ${offer.ability.label}" + if (offer.available) "" else " · ${offer.reason}")
            }
        }
    }
    val current = offers.firstOrNull { it.sourceId == selected?.first && it.holderId == selected?.second && it.available }
        ?: return
    val holder = state.player(current.holderId!!) ?: return
    val targets = current.ability.targets?.invoke(state, viewModel.lookup, holder)
    AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text("${holder.name} · ${current.ability.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current.ability.confirmation)
                for (id in targets.orEmpty()) {
                    OutlinedButton(onClick = { target = id }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (target == id) "✓ " else "") + (state.player(id)?.name ?: "?"))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = targets == null || target in targets, onClick = {
                viewModel.resolveDayAbility(current.sourceId, holder.id, target)
                selected = null
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
    )
}
