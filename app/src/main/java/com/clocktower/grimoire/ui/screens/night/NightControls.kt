package com.clocktower.grimoire.ui.screens.night

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Twilight
import kotlinx.coroutines.withTimeoutOrNull

/** The theme colour for one [Tone]. WP10 owns the palette; this only maps it. */
@Composable
fun Tone.color(): Color = when (this) {
    Tone.NORMAL -> MaterialTheme.colorScheme.onSurface
    Tone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.ACTIVE -> AgedGold
    Tone.ALERT -> EmberRed
}

/**
 * The one primary button of a night card.
 *
 * Non-destructive steps are a single tap. Anything that kills, resurrects,
 * rewrites a character or spends a once-per-game ability is a
 * **press-and-hold** with a filling bar (ux/night-screen §H, defect #23) — the
 * app used to kill a player on one unconfirmed tap of the only prominent
 * control on the card.
 */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 0,
    onConfirm: () -> Unit,
) {
    var holding by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = if (holding) holdMillis else 140),
        label = "night-primary-hold",
    )
    val container =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val press = if (holdMillis <= 0) {
        Modifier.clickable(enabled = enabled, onClick = onConfirm)
    } else {
        Modifier.pointerInput(enabled, holdMillis) {
            detectTapGestures(
                onPress = {
                    if (!enabled) return@detectTapGestures
                    holding = true
                    // Fires only if the finger stays down: a fumble in the dark
                    // releases early and nothing happens.
                    val released = withTimeoutOrNull(holdMillis.toLong()) { tryAwaitRelease() }
                    holding = false
                    if (released == null) onConfirm()
                },
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PRIMARY_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .drawBehind {
                if (fill > 0f) {
                    drawRect(color = Color.White.copy(alpha = 0.28f), size = size.copy(width = size.width * fill))
                }
            }
            .then(press)
            .semantics {
                contentDescription =
                    if (holdMillis > 0) "$label. Press and hold to confirm." else label
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = nightSp(18f).sp,
                lineHeight = nightSp(22f).sp,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (holdMillis > 0) {
                Text(
                    text = if (holding) "keep holding…" else "press and hold",
                    fontSize = NIGHT_MIN_SP.sp,
                    color = content.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Height of every primary-path target on the night screen, in dp. */
const val PRIMARY_HEIGHT_DP: Float = 56f

/**
 * The fixed strip at the top of the night screen: which night, which step, a
 * segment bar of the whole sheet, and the ⏻ dim control (ux/night-screen §B1).
 *
 * It never scrolls, so "which step am I on" is never one flick away.
 */
@Composable
fun ProgressStrip(
    cycleLabel: String,
    progress: Progress,
    segments: List<Tone>,
    dimLevel: Int,
    listOpen: Boolean,
    onDim: () -> Unit,
    onToggleList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Twilight, tonalElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cycleLabel.uppercase(),
                    fontSize = nightSp(16f).sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                )
                Text(
                    text = "  ·  ${progress.label}",
                    fontSize = NIGHT_MIN_SP.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (listOpen) "hide sheet" else "whole sheet",
                    fontSize = NIGHT_MIN_SP.sp,
                    color = AgedGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleList)
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
                Text(
                    text = "⏻ ${dimLabel(dimLevel)}",
                    fontSize = NIGHT_MIN_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dimLevel == 0) MaterialTheme.colorScheme.onSurfaceVariant else AgedGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDim)
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .semantics { contentDescription = "Screen dimming, now ${dimLabel(dimLevel)}" },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                for ((index, tone) in segments.withIndex()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (tone == Tone.ACTIVE) 8.dp else 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (tone) {
                                    Tone.ACTIVE -> AgedGold
                                    Tone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    Tone.ALERT -> EmberRed
                                    Tone.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            )
                            .semantics { contentDescription = "step ${index + 1}" },
                    )
                }
            }
        }
    }
}

/** A small gold-on-twilight chip used for badges and secondary actions. */
@Composable
fun NightChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.ACTIVE,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(tone.color().copy(alpha = 0.14f))
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .heightIn(min = if (onClick == null) 0.dp else 44.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = NIGHT_MIN_SP.sp,
            lineHeight = nightSp(18f).sp,
            fontWeight = FontWeight.SemiBold,
            color = tone.color(),
        )
    }
}

/** The gutter dot of a collapsed row, sized so it is never mistaken for a control. */
@Composable
fun RowGutter(mark: RowMark, tone: Tone) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = mark.glyph,
            fontSize = NIGHT_MIN_SP.sp,
            fontWeight = FontWeight.Bold,
            color = tone.color(),
        )
    }
}
