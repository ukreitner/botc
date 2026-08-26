package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.platform.rememberAlertAtTable
import kotlinx.coroutines.delay

/**
 * The discussion timer's deadline, held OUTSIDE composition.
 *
 * ux/day-screen finding 40: the old timer kept `endAt` in a `rememberSaveable`
 * inside the composable, and `GameShell` composes it only for the Grimoire and
 * Day tabs — so a trip to the Night tab dropped it out of composition and reset
 * a running five-minute timer to idle. Hoisting the deadline into a plain
 * holder makes "survives a tab switch" a property of the *state*, not of where
 * the composable happens to be mounted (spec §J, test 17).
 *
 * The instance in [DayTimer] is shared, so the Day tab's bottom bar and the
 * Grimoire tab's floating pill are two views of ONE timer rather than two
 * timers that disagree.
 */
class TimerState {
    /** Epoch millis the timer runs out at; 0 while idle or paused. */
    var endAt by mutableLongStateOf(0L)

    /** Millis left when the storyteller paused; 0 while running or idle. */
    var pausedRemainingMs by mutableLongStateOf(0L)

    /** The last preset used, so the bottom bar's one-tap default is the right one. */
    var lastPresetSec by mutableIntStateOf(DEFAULT_PRESET_SEC)

    /** True once [rememberAlertAtTable] has fired for this deadline. */
    var alerted by mutableStateOf(false)

    /** The controls panel is open. */
    var expanded by mutableStateOf(false)

    /**
     * How many bottom bars are currently rendering this timer. `GameShell`'s
     * floating pill stands down while the Day tab hosts it, so the storyteller
     * never sees two timers (and no `GameShell` edit is needed to do it).
     */
    var barHosts by mutableIntStateOf(0)

    val running: Boolean get() = endAt != 0L
    val paused: Boolean get() = pausedRemainingMs > 0L
    val idle: Boolean get() = !running && !paused

    fun start(seconds: Int) {
        lastPresetSec = seconds
        pausedRemainingMs = 0L
        alerted = false
        endAt = com.clocktower.engine.Time.epochMillis() + seconds * 1000L
    }

    fun pause() {
        if (!running) return
        pausedRemainingMs = (endAt - com.clocktower.engine.Time.epochMillis()).coerceAtLeast(0L)
        endAt = 0L
    }

    fun resume() {
        if (!paused) return
        endAt = com.clocktower.engine.Time.epochMillis() + pausedRemainingMs
        pausedRemainingMs = 0L
    }

    fun addSeconds(seconds: Int) {
        when {
            running -> endAt += seconds * 1000L
            paused -> pausedRemainingMs += seconds * 1000L
            else -> start(seconds)
        }
        alerted = false
    }

    fun reset() {
        start(lastPresetSec)
    }

    fun stop() {
        endAt = 0L
        pausedRemainingMs = 0L
        alerted = false
    }

    /** Millis left at [now]; negative once it has run out. */
    fun remainingMs(now: Long): Long = when {
        paused -> pausedRemainingMs
        running -> endAt - now
        else -> 0L
    }

    companion object {
        const val DEFAULT_PRESET_SEC: Int = 300
    }
}

/** The one shared discussion timer for the whole game shell. */
object DayTimer {
    val shared: TimerState = TimerState()
}

/**
 * Pure formatting and cadence, so the timer's arithmetic is testable without a
 * composition (`tools/uicheck/src/test`).
 */
object TimerFormat {

    /** Preset durations offered in the panel, in seconds (spec §J). */
    val PRESETS_SEC: List<Int> = listOf(60, 120, 180, 300, 480)

    fun presetLabel(seconds: Int): String =
        if (seconds % 60 == 0) "${seconds / 60}m" else "${seconds}s"

    /** `4:58`, floor-rounded, never negative. */
    fun clock(remainingMs: Long): String {
        val total = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    /**
     * Tick every second normally and four times a second only inside the last
     * ten (finding 44: the old timer wrote state 4× a second all day).
     */
    fun tickMs(remainingMs: Long): Long = if (remainingMs <= LAST_SECONDS_MS) FAST_TICK_MS else SLOW_TICK_MS

    /** The bottom bar's label: idle shows the preset, running shows the clock. */
    fun barLabel(timer: TimerState, now: Long): String = when {
        timer.idle -> presetLabel(timer.lastPresetSec)
        timer.remainingMs(now) <= 0L -> "TIME"
        timer.paused -> clock(timer.remainingMs(now)) + " ‖"
        else -> clock(timer.remainingMs(now))
    }

    private const val LAST_SECONDS_MS = 10_000L
    private const val FAST_TICK_MS = 250L
    private const val SLOW_TICK_MS = 1_000L
}

/**
 * Drives [timer] forward and returns "now" in epoch millis, recomposing at the
 * cadence [TimerFormat.tickMs] asks for. Fires the table alert once, on expiry.
 */
@Composable
fun rememberTimerNow(timer: TimerState): Long {
    var now by remember { mutableLongStateOf(com.clocktower.engine.Time.epochMillis()) }
    val alert = rememberAlertAtTable()
    LaunchedEffect(timer.endAt) {
        while (timer.endAt != 0L) {
            now = com.clocktower.engine.Time.epochMillis()
            val remaining = timer.endAt - now
            if (remaining <= 0L && !timer.alerted) {
                timer.alerted = true
                alert()
            }
            delay(TimerFormat.tickMs(remaining))
        }
        now = com.clocktower.engine.Time.epochMillis()
    }
    return now
}

/**
 * The floating pill `GameShell` anchors bottom-right on the Grimoire tab.
 *
 * The deadline is a PARAMETER, never `rememberSaveable` inside — that is the
 * compile-level guarantee that finding 40 cannot come back.
 */
@Composable
fun DiscussionTimer(modifier: Modifier = Modifier, timer: TimerState = DayTimer.shared) {
    // The Day tab's bottom bar owns the timer while it is on screen.
    if (timer.barHosts > 0) return

    val now = rememberTimerNow(timer)

    if (timer.idle && !timer.expanded) {
        FilledTonalIconButton(
            onClick = { timer.expanded = true },
            modifier = modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = "Discussion timer")
        }
        return
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (!timer.idle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val expired = timer.remainingMs(now) <= 0L
                    FilledTonalButton(onClick = { timer.expanded = !timer.expanded }) {
                        Text(
                            text = TimerFormat.barLabel(timer, now),
                            fontWeight = FontWeight.Bold,
                            color = if (expired) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = { timer.stop() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Stop the timer",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (timer.expanded || timer.idle) {
                TimerControls(timer, now)
            }
        }
    }
}

/**
 * Pause / +1m / reset / stop plus the presets. Four discrete targets, so
 * glancing at the remaining time can never cancel the timer (finding 42).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerControls(timer: TimerState, now: Long, onDone: () -> Unit = {}) {
    val expired = !timer.idle && timer.remainingMs(now) <= 0L
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (expired) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { timer.stop(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "TIME — tap to dismiss",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (!timer.idle) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { if (timer.paused) timer.resume() else timer.pause() },
                    label = { Text(if (timer.paused) "Resume" else "Pause") },
                )
                AssistChip(onClick = { timer.addSeconds(60) }, label = { Text("+1m") })
                AssistChip(onClick = { timer.reset() }, label = { Text("Reset") })
                AssistChip(
                    onClick = { timer.stop(); timer.expanded = false; onDone() },
                    label = { Text("Stop") },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (seconds in TimerFormat.PRESETS_SEC) {
                AssistChip(
                    onClick = {
                        timer.start(seconds)
                        timer.expanded = false
                        onDone()
                    },
                    label = { Text(TimerFormat.presetLabel(seconds)) },
                )
            }
        }
    }
}

/**
 * Registers this composition as the timer's host, so the shell's floating pill
 * stands down while the Day tab's bottom bar is showing it.
 */
@Composable
fun HostTimerInBar(timer: TimerState) {
    DisposableEffect(timer) {
        timer.barHosts++
        onDispose { timer.barHosts-- }
    }
}
