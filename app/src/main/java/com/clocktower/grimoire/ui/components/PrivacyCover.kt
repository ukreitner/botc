package com.clocktower.grimoire.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.grimoire.ui.theme.AgedGold
import kotlinx.coroutines.withTimeoutOrNull

/** How long the storyteller must hold before the grimoire reopens. */
private const val HOLD_MILLIS = 1200L

/**
 * Full-screen shield over the grimoire.
 *
 * **It is hosted in a `Dialog`, and that is load-bearing** (grimoire-and-seats
 * P0-8). As a plain composable it was declared in `GameShell` BEFORE the
 * mastermind banner, the seat sheet, the win-advisory dialog and every setup
 * prompt — siblings draw in declaration order and dialogs get their own
 * window, so "MASTERMIND DAY — whoever is executed, their team loses" was
 * literally painted on top of the closed grimoire. A dialog window always
 * paints above in-window content, and this one attaches at the moment the
 * cover is engaged, so it is above any sheet that was already open too.
 *
 * Back and outside taps are refused: the ONLY way out is a deliberate
 * [HOLD_MILLIS] hold, now with a progress ring so a storyteller in a hurry can
 * see that it is working rather than assuming it is broken (P2-26).
 *
 * @param caption what to show on the cover so the storyteller can still
 *   orient — "Night 3", "Day 2". Never anything a player must not see.
 */
@Composable
fun PrivacyCover(caption: String = "", onUnlock: () -> Unit) {
    Dialog(
        onDismissRequest = { /* the cover is not dismissible; hold to open */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        var holding by remember { mutableStateOf(false) }
        val progress by animateFloatAsState(
            targetValue = if (holding) 1f else 0f,
            animationSpec = tween(durationMillis = if (holding) HOLD_MILLIS.toInt() else 150),
            label = "privacy-hold",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .semantics { contentDescription = "The grimoire is closed. Press and hold to open." }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            // Unlock only if the finger stays down long enough;
                            // quick curious taps release before the timeout.
                            val released = withTimeoutOrNull(HOLD_MILLIS) { tryAwaitRelease() }
                            holding = false
                            if (released == null) onUnlock()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                // The hold target stays edge to edge — the whole black screen
                // is the control — but the ring and the caption stay clear of
                // the notch and the home indicator like every other overlay.
                modifier = Modifier.overlaySafeAreaPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .drawBehind {
                            val stroke = 4.dp.toPx()
                            drawArc(
                                color = AgedGold.copy(alpha = 0.18f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke),
                            )
                            if (progress > 0f) {
                                drawArc(
                                    color = AgedGold,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(width = stroke),
                                )
                            }
                        },
                ) {
                    Text("", fontSize = 40.sp)
                }
                Text(
                    text = "The grimoire is closed",
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Serif,
                    color = AgedGold.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = when {
                        holding -> "Keep holding…"
                        caption.isNotBlank() -> "$caption · press and hold to open"
                        else -> "Storyteller: press and hold to open"
                    },
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
