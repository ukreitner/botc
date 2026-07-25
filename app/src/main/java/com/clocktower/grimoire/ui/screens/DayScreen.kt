package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameState
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Player
import com.clocktower.engine.Voting
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed

/**
 * Day tools: nomination flow with tap-to-vote tally, execution threshold,
 * the day's nomination record, and executions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreen(
    viewModel: GameViewModel,
    state: GameState,
) {
    var nominatorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nomineeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var voters by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val currentPlayerIds = state.players.map { it.id }.toSet()

    // Draft votes survive tab switches. Reconcile them when a Traveller
    // leaves or another seat edit removes one of the selected players.
    LaunchedEffect(currentPlayerIds) {
        if (nominatorId !in currentPlayerIds) nominatorId = null
        if (nomineeId !in currentPlayerIds) nomineeId = null
        voters = voters.intersect(currentPlayerIds)
    }

    val aliveCount = state.alivePlayers.size
    val threshold = Voting.executionThreshold(aliveCount)
    val highest = GameActions.highestVotesToday(state)
    val onBlockId = GameActions.aboutToDie(state)
    val onBlock = onBlockId?.let { state.player(it) }
    val todaysNominations = state.nominations.filter { it.day == state.cycle }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Day ${state.cycle}", style = MaterialTheme.typography.headlineMedium, color = AgedGold)
            Text(
                "$aliveCount alive · $threshold votes to execute" +
                    (if (highest > 0) " · $highest votes is the tally to beat" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    onBlock != null -> "On the block: ${onBlock.name}"
                    highest > 0 -> "Tie — no one is about to die"
                    else -> "No one is about to die"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (onBlock != null) EmberRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ElevatedCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New nomination", style = MaterialTheme.typography.titleMedium)

                    PlayerChipRow(
                        label = "Nominator",
                        players = state.players,
                        selected = nominatorId,
                        enabled = { p ->
                            // Alive players who haven't nominated today (travellers may nominate).
                            p.alive && !GameActions.hasNominatedToday(state, p.id)
                        },
                        onSelect = { nominatorId = if (nominatorId == it) null else it },
                    )
                    PlayerChipRow(
                        label = "Nominee",
                        players = state.players,
                        selected = nomineeId,
                        // Only living players can be nominated (or exiled).
                        enabled = { p -> p.alive && !GameActions.hasBeenNominatedToday(state, p.id) },
                        onSelect = {
                            nomineeId = if (nomineeId == it) null else it
                            // A fresh nominee always starts from an empty tally.
                            voters = emptySet()
                        },
                    )

                    val nominationNotes = com.clocktower.engine.StatusEffects.nominationWarnings(
                        state, viewModel::characterById, nominatorId, nomineeId,
                    )
                    for (note in nominationNotes) {
                        Text("⚠ $note", color = EmberRed, style = MaterialTheme.typography.bodySmall)
                    }

                    if (nomineeId != null) {
                        val nominee = state.player(nomineeId!!)
                        val isExile = nominee?.isTraveller == true
                        val voteThreshold = if (isExile) Voting.exileThreshold(state.players.size) else threshold
                        // Hands are counted clockwise starting left of the
                        // nominee — list voters in that order.
                        val voteOrder = run {
                            val start = state.players.indexOfFirst { it.id == nomineeId }
                            if (start < 0) state.players
                            else (1..state.players.size).map { state.players[(start + it) % state.players.size] }
                        }
                        val orderedVoterIds = voteOrder.map { it.id }.filter { it in voters }
                        HorizontalDivider()
                        Text(
                            text = (if (isExile) "Exile vote — " else "Vote — ") +
                                "tap everyone whose hand is up (${orderedVoterIds.size} so far, needs $voteThreshold)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (p in voteOrder) {
                                val canVote = p.alive || !p.ghostVoteUsed || isExile
                                FilterChip(
                                    selected = p.id in voters,
                                    enabled = canVote,
                                    onClick = {
                                        voters = if (p.id in voters) voters - p.id else voters + p.id
                                    },
                                    label = {
                                        Text(p.name + if (!p.alive) " †" else "")
                                    },
                                )
                            }
                        }
                        val result = if (isExile) {
                            if (orderedVoterIds.size >= voteThreshold) {
                                NominationResult.ABOUT_TO_DIE
                            } else {
                                NominationResult.SAFE
                            }
                        } else {
                            Voting.outcome(orderedVoterIds.size, voteThreshold, highest)
                        }
                        Text(
                            text = when (result) {
                                NominationResult.ABOUT_TO_DIE ->
                                    if (isExile) "Exiled!" else "${nominee?.name} is about to die"
                                NominationResult.TIED -> "Tie — no one is about to die"
                                else -> "${nominee?.name} is safe"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (result == NominationResult.ABOUT_TO_DIE) EmberRed else MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                enabled = nominatorId in currentPlayerIds && nomineeId in currentPlayerIds,
                                onClick = {
                                    val nomination = Nomination(
                                        day = state.cycle,
                                        nominatorId = nominatorId!!,
                                        nomineeId = nomineeId!!,
                                        votes = orderedVoterIds.size,
                                        voterIds = orderedVoterIds,
                                        result = result,
                                        isExile = isExile,
                                    )
                                    viewModel.update { current ->
                                        var next = GameActions.recordNomination(current, nomination)
                                        // Spend ghost votes for dead voters (not on exiles).
                                        if (!isExile) {
                                            for (id in orderedVoterIds) {
                                                val voter = next.player(id)
                                                if (voter != null && !voter.alive && !voter.ghostVoteUsed) {
                                                    next = GameActions.toggleGhostVote(next, id)
                                                }
                                            }
                                        }
                                        next
                                    }
                                    nominatorId = null
                                    nomineeId = null
                                    voters = emptySet()
                                },
                            ) { Text("Record") }
                            TextButton(onClick = {
                                nominatorId = null; nomineeId = null; voters = emptySet()
                            }) { Text("Reset") }
                        }
                    }
                }
            }
        }

        if (todaysNominations.isNotEmpty()) {
            item {
                Text("Today's nominations", style = MaterialTheme.typography.titleMedium)
            }
            for (n in todaysNominations.reversed()) {
                item {
                    NominationRow(viewModel, state, n, onBlockId)
                }
            }
        }

        item {
            HorizontalDivider()
            Text(
                "When dusk falls, execute whoever is on the block (from their seat or here), " +
                    "then advance to night.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChipRow(
    label: String,
    players: List<Player>,
    selected: Long?,
    enabled: (Player) -> Boolean,
    onSelect: (Long) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (p in players) {
                FilterChip(
                    selected = selected == p.id,
                    enabled = enabled(p) || selected == p.id,
                    onClick = { onSelect(p.id) },
                    label = { Text(p.name + if (!p.alive) " †" else "") },
                )
            }
        }
    }
}

@Composable
private fun NominationRow(
    viewModel: GameViewModel,
    state: GameState,
    nomination: Nomination,
    onBlockId: Long?,
) {
    val nominator = state.player(nomination.nominatorId)
    val nominee = state.player(nomination.nomineeId)
    // A record's stored result can be superseded by a later tie; only the
    // currently-blocked player (or a passed exile) gets an execute button.
    val executable = if (nomination.isExile) {
        nomination.result == NominationResult.ABOUT_TO_DIE
    } else {
        nomination.result == NominationResult.ABOUT_TO_DIE && nomination.nomineeId == onBlockId
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${nominator?.name ?: "?"} → ${nominee?.name ?: "?"}" + if (nomination.isExile) " (exile)" else "",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${nomination.votes} votes · " + when (nomination.result) {
                        NominationResult.ABOUT_TO_DIE -> "about to die"
                        NominationResult.TIED -> "tied"
                        NominationResult.WITHDRAWN -> "withdrawn"
                        NominationResult.SAFE -> "safe"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (executable && nominee?.alive == true) {
                OutlinedButton(onClick = {
                    viewModel.kill(
                        nomination.nomineeId,
                        if (nomination.isExile) DeathCause.EXILE else DeathCause.EXECUTION,
                    )
                }) { Text(if (nomination.isExile) "Exile" else "Execute") }
            }
        }
    }
}
