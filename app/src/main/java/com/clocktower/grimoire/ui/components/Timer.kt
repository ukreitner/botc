package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A compact discussion timer: preset chips start a countdown; the running
 * chip shows remaining time and taps to cancel. Ticks once per second.
 */
@Composable
fun DiscussionTimer(modifier: Modifier = Modifier) {
    var secondsLeft by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        if (secondsLeft <= 0) running = false
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (running) {
                val expired = secondsLeft == 0
                FilledTonalButton(onClick = { running = false; secondsLeft = 0 }) {
                    Text(
                        text = if (expired) "Time!" else "%d:%02d  ✕".format(secondsLeft / 60, secondsLeft % 60),
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Text("Timer", style = MaterialTheme.typography.labelLarge)
                for ((label, secs) in listOf("1m" to 60, "2m" to 120, "5m" to 300)) {
                    AssistChip(
                        onClick = { secondsLeft = secs; running = true },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}
