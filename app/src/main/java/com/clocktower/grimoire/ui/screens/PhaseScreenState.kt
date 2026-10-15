package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.screens.day.DayStage

/** A phase tab never offers mutations for a phase that is not running. */
@Composable
internal fun InactivePhaseNotice(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Null is an explicit collapse after a user choice; only the initial state follows the timeline. */
internal fun expandedDayStage(autoStage: DayStage, selectedStage: DayStage?, stageChosen: Boolean): DayStage? =
    if (stageChosen) selectedStage else autoStage

/** The second tap may be on the same player: self-nominations are legal. */
internal fun nominationPairAfterTap(
    nominatorId: Long?,
    nomineeId: Long?,
    tappedId: Long,
): Pair<Long?, Long?> = when {
    nominatorId == null -> tappedId to nomineeId
    nomineeId == null -> nominatorId to tappedId
    nominatorId == tappedId -> null to nomineeId
    nomineeId == tappedId -> nominatorId to null
    else -> nominatorId to tappedId
}

/** Indices from the full plan that are actually emitted into the lazy list. */
internal fun nightRowWindow(rowCount: Int, activeIndex: Int, expanded: Boolean, radius: Int): IntRange =
    if (expanded || activeIndex !in 0 until rowCount) {
        0 until rowCount
    } else {
        (activeIndex - radius).coerceAtLeast(0)..(activeIndex + radius).coerceAtMost(rowCount - 1)
    }
