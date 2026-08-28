package com.clocktower.grimoire.ui.screens.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.FadedInk
import com.clocktower.grimoire.ui.theme.PaleGold

/**
 * The timeline's one card shape (ux/day-screen §A): a tappable header that
 * always shows the stage's summary, and a body that only exists while the
 * stage is open. Collapsed, every stage is one line — which is what lets the
 * whole day fit on a phone.
 */
@Composable
fun StageCard(
    row: StageRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    val accent = when (row.tone) {
        StageTone.ALERT -> EmberRed
        StageTone.ACTION -> AgedGold
        StageTone.QUIET -> FadedInk
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (expanded) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        if (row.badge > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge(row.badge, accent)
                        }
                    }
                    Text(
                        row.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (row.complete) {
                    Text("✓", style = MaterialTheme.typography.titleMedium, color = FadedInk)
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    body()
                }
            }
        }
    }
}

@Composable
private fun Badge(count: Int, accent: Color) {
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.20f)) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

/**
 * One briefing line. `ANNOUNCE` rows are read aloud and tick off; everything
 * else is information the storyteller acts on. The action is a stable string
 * the engine wrote (`Briefings.ACTION_*`) — the screen never invents one.
 */
@Composable
fun BriefingRow(
    item: BriefingItem,
    ticked: Boolean,
    onTick: (() -> Unit)?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = when (item.severity) {
        BriefingSeverity.ALERT -> EmberRed
        BriefingSeverity.ACTION -> PaleGold
        BriefingSeverity.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val glyph = when {
            onTick == null -> "•"
            ticked -> "☑"
            else -> "☐"
        }
        Text(
            glyph,
            style = MaterialTheme.typography.bodyLarge,
            color = if (ticked) FadedInk else color,
            modifier = if (onTick != null) {
                Modifier.clickable(onClick = onTick).padding(end = 8.dp)
            } else {
                Modifier.padding(end = 8.dp)
            },
        )
        Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ticked) FadedInk else color,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** The heading above each briefing section, in the order they are used. */
fun sectionHeading(kind: BriefingKind): String = when (kind) {
    BriefingKind.ANNOUNCE -> "ANNOUNCE, IN THIS ORDER"
    BriefingKind.PRIVATE -> "FOR YOU ONLY — NEVER SAY IT"
    BriefingKind.STANDING_FACT -> "TRUE TODAY"
    BriefingKind.TODO_ASK -> "STILL TO DO"
    BriefingKind.SWEPT -> "SWEPT OFF THE GRIMOIRE"
}

/** Section order, top to bottom, for both the dawn and the day-start cards. */
val BRIEFING_SECTIONS: List<BriefingKind> = listOf(
    BriefingKind.ANNOUNCE,
    BriefingKind.PRIVATE,
    BriefingKind.STANDING_FACT,
    BriefingKind.TODO_ASK,
    BriefingKind.SWEPT,
)
