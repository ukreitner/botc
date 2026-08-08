package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.grimoire.ui.theme.AgedGold
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen shield over the grimoire. A stray tap from a player shows
 * nothing; only a deliberate long HOLD (1.2s) reopens the app. Engaged
 * automatically after the character reveal flow and on demand from the
 * top bar.
 */
@Composable
fun PrivacyCover(onUnlock: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        // Unlock only if the finger stays down long enough;
                        // quick curious taps release before the timeout.
                        val released = withTimeoutOrNull(1200L) { tryAwaitRelease() }
                        holding = false
                        if (released == null) onUnlock()
                    },
                )
            },
    ) {
        Text("", fontSize = 64.sp)
        Text(
            text = "The grimoire is closed",
            fontSize = 26.sp,
            fontFamily = FontFamily.Serif,
            color = AgedGold.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (holding) "Keep holding…" else "Storyteller: press and hold to open",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
