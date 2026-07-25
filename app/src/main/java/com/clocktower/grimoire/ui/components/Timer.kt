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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A compact discussion timer anchored to a wall-clock deadline, so it keeps
 * counting while other tabs are open. When it expires it shows "Time!"
 * until the storyteller taps it away.
 */
@Composable
fun DiscussionTimer(modifier: Modifier = Modifier) {
    // 0 = idle; otherwise the epoch-millis deadline.
    var endAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endAt) {
        while (endAt != 0L) {
            now = System.currentTimeMillis()
            delay(250)
        }
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
            if (endAt != 0L) {
                val remaining = ((endAt - now) / 1000).coerceAtLeast(0)
                val expired = now >= endAt
                FilledTonalButton(onClick = { endAt = 0L }) {
                    Text(
                        text = if (expired) "Time!  ✕" else "%d:%02d  ✕".format(remaining / 60, remaining % 60),
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Text("Timer", style = MaterialTheme.typography.labelLarge)
                for ((label, secs) in listOf("1m" to 60, "2m" to 120, "5m" to 300)) {
                    AssistChip(
                        onClick = { endAt = System.currentTimeMillis() + secs * 1000L },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}
