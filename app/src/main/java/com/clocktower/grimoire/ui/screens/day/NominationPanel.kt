package com.clocktower.grimoire.ui.screens.day

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.Briefing
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.GameState
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationCheck
import com.clocktower.engine.NominationResult
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.TriggerKind
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.FadedInk
import com.clocktower.grimoire.ui.theme.PaleGold
import com.clocktower.grimoire.ui.theme.PoisonGreen

/**
 * The nomination stage: a **pinned seat ring** (tap 1 = nominator, tap 2 =
 * nominee), the `NominationCheck` card **between the ring and the vote panel**,
 * and the vote panel itself (ux/day-screen §E/§F, ARCHITECTURE §3.2).
 *
 * The ring replaces two `FlowRow`s of every seat in the game — roughly 230 dp
 * of small labels the storyteller had to read twice while the table waited.
 * A ring matches the actual table, so the tap is spatial rather than a name
 * hunt, and ineligible seats stay tappable behind a reason and an
 * [Allow anyway] (finding 14: a dead Banshee, a Butcher's second nomination
 * and a Riot re-nomination are all legal somewhere).
 */
@Suppress("LongParameterList")
@Composable
fun SeatRingPanel(
    viewModel: GameViewModel,
    state: GameState,
    nominatorId: Long?,
    nomineeId: Long?,
    /** The storyteller has explicitly reopened a closed day. */
    reopened: Boolean,
    /** Both taps have landed: the ring stands down so the tally fits (C2-9). */
    collapsed: Boolean,
    onPickSeat: (Long) -> Unit,
    onSay: (Long) -> Unit,
    onReopen: () -> Unit,
    /** Brings the ring back to change either half of the pair. */
    onChangePair: () -> Unit,
) {
    val lookup: (String) -> Character? = viewModel::characterById
    val ring = remember(state, nominatorId, nomineeId) {
        NominationModel.ring(state, lookup, nominatorId, nomineeId)
    }
    val closedReason = remember(state) { DayRules.nominationsClosedReason(state, lookup) }
    // A closed day disables the ring instead of pretending to take a
    // nomination it will drop on the floor (C-4). The reason is stated where
    // the taps would have gone, and reopening is one deliberate tap.
    val locked = NominationModel.ringLocked(closedReason, reopened)
    Column(Modifier.fillMaxWidth()) {
        if (closedReason.isNotBlank()) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    closedReason,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmberRed,
                    modifier = Modifier.weight(1f),
                )
                if (locked) {
                    TextButton(onClick = onReopen) { Text("Nominate anyway") }
                }
            }
        }
        if (collapsed) {
            // The ring's whole job was the two taps; done, it hands the screen
            // to the tally (C2-9). What it was showing in its centre — the pair
            // — is all that is left, plus the way back.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${ring.firstOrNull { it.pick == SeatPick.NOMINATOR }?.name ?: "?"} » " +
                        (ring.firstOrNull { it.pick == SeatPick.NOMINEE }?.name ?: "?"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmberRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChangePair) { Text("Change") }
            }
            return@Column
        }
        Text(
            if (locked) {
                "Nominations are closed. Tap Nominate anyway to take one regardless."
            } else {
                NominationModel.ringPrompt(
                    nominatorId,
                    nomineeId,
                    nominatorId?.let { state.player(it)?.name },
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (locked) FadedInk else PaleGold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        SeatRing(
            ring = ring,
            enabled = !locked,
            onTap = onPickSeat,
            onLongPress = onSay,
        )
    }
}

/**
 * The half that sits below the ring: the `NominationCheck` card and, once both
 * taps have landed, the vote panel. The order is the spec's — check card
 * BETWEEN the ring and the vote panel — so a Virgin's interceptor is answered
 * before a single hand goes up.
 */
@Suppress("LongParameterList")
@Composable
fun NominationDetail(
    viewModel: GameViewModel,
    state: GameState,
    nominatorId: Long?,
    nomineeId: Long?,
    voters: Set<Long>,
    force: Boolean,
    onToggleVoter: (Long) -> Unit,
    onForce: () -> Unit,
    onReset: () -> Unit,
) {
    val check = remember(state, nominatorId, nomineeId) {
        viewModel.nominationCheck(state, nominatorId, nomineeId)
    }

    if (nominatorId != null || nomineeId != null) {
        // Per chip tap — the NOMINATION slot's default pair is the last
        // RECORDED nomination, which is wrong for "the 1st time you are
        // nominated" (the Virgin). WP6 merger note.
        val briefing = remember(state, nominatorId, nomineeId) {
            viewModel.nominationBriefing(state, nominatorId, nomineeId)
        }
        NominationCheckCard(
            viewModel = viewModel,
            state = state,
            check = check,
            briefing = briefing,
            force = force,
            onForce = onForce,
        )
    }

    if (nominatorId != null && nomineeId != null) {
        HorizontalDivider()
        VotePanel(
            viewModel = viewModel,
            state = state,
            nominatorId = nominatorId,
            nomineeId = nomineeId,
            voters = voters,
            blockers = check.blockers,
            force = force,
            onToggleVoter = onToggleVoter,
            onForce = onForce,
            onReset = onReset,
        )
    }
}

/**
 * The ring itself. Seats sit where they sit at the table, so the storyteller
 * points the phone the same way they point their arm.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatRing(
    ring: List<RingSeat>,
    enabled: Boolean,
    onTap: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The ring sizes ITSELF: the height it needs is the height that keeps
        // two adjacent 48 dp hit targets apart at this table size, plus a clear
        // strip under the lowest seat. A fixed 240 dp box put twelve seats
        // 38 dp apart and let the list scroll up against the bottom row.
        val widthDp = maxWidth.value
        val maxRy = NominationModel.maxRadiusYFor(maxHeight.value)
        val seatWidth = NominationModel.seatWidthDp(ring.size, widthDp, maxRy).dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(NominationModel.ringHeightDp(ring.size, widthDp, maxRy).dp),
        ) {
        for ((index, seat) in ring.withIndex()) {
            val tint = when (seat.pick) {
                SeatPick.NOMINATOR -> AgedGold
                SeatPick.NOMINEE -> EmberRed
                SeatPick.NONE -> if (seat.allowed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    FadedInk
                }
            }
            val (cx, cy) = NominationModel.seatCentreDp(index, ring.size, widthDp, maxRy)
            Box(
                Modifier
                    .offset(
                        x = cx.dp - seatWidth / 2,
                        y = (cy - NominationModel.SEAT_HEIGHT_DP / 2f).dp,
                    )
                    .size(width = seatWidth, height = NominationModel.SEAT_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (seat.pick == SeatPick.NONE) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        tint.copy(alpha = 0.22f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = enabled,
                            onClick = { onTap(seat.id) },
                            // Long-press a seat to record what they said (§C).
                            onLongClick = { onLongPress(seat.id) },
                        ),
                ) {
                    Column(
                        Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            seat.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (seat.pick == SeatPick.NONE) {
                                FontWeight.Normal
                            } else {
                                FontWeight.Bold
                            },
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            buildString {
                                append(seat.seatNumber)
                                if (!seat.alive) append(" †")
                                if (seat.isTraveller) append(" ✈")
                                when (seat.pick) {
                                    SeatPick.NOMINATOR -> append(" ◆")
                                    SeatPick.NOMINEE -> append(" ✕")
                                    SeatPick.NONE -> Unit
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (seat.allowed) FadedInk else FadedInk.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        // The centre carries the pair under construction, so the storyteller
        // can read back what they are about to call without looking away.
        val nominator = ring.firstOrNull { it.pick == SeatPick.NOMINATOR }
        val nominee = ring.firstOrNull { it.pick == SeatPick.NOMINEE }
        Text(
            when {
                nominator != null && nominee != null -> "${nominator.name} » ${nominee.name}"
                nominator != null -> "${nominator.name} nominates…"
                else -> "who nominates?"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (nominee != null) EmberRed else FadedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        }
    }
}

/**
 * The `NominationCheck` card: blockers, cautions, and one card per
 * `NominationTrigger` with **buttons, not red text** (finding 15). Every
 * button applies its full consequence in one undoable update through
 * `DayRules.applyTrigger` — the Virgin's execution, the Witch's death, the
 * Goblin's claim. Nothing here knows a character id.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NominationCheckCard(
    viewModel: GameViewModel,
    state: GameState,
    check: NominationCheck,
    briefing: Briefing,
    force: Boolean,
    onForce: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (blocker in check.blockers) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        blocker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmberRed,
                        modifier = Modifier.weight(1f),
                    )
                    if (!force) {
                        TextButton(onClick = onForce) { Text("Allow anyway") }
                    }
                }
            }
            if (force && check.blockers.isNotEmpty()) {
                Text(
                    "Allowed anyway — the storyteller always wins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PoisonGreen,
                )
            }
            for (caution in check.cautions) {
                Text(
                    caution,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaleGold,
                )
            }
            for (trigger in check.triggers) {
                TriggerCard(viewModel, trigger)
            }
            // Anything the NOMINATION briefing adds beyond what is already on
            // this card. Every reason used to be rendered twice — once as a
            // card, once as a `·` bullet under it (finding 8) — because only
            // the triggers were de-duplicated, never the blockers or cautions.
            val said = (check.blockers + check.cautions + check.triggers.map { it.headline })
                .map { it.trim() }
                .toSet()
            val extra = briefing.items.filterNot { it.text.trim() in said }
            for (item in extra) {
                Text(
                    "• ${item.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FadedInk,
                )
            }
            if (check.blockers.isEmpty() && check.cautions.isEmpty() && check.triggers.isEmpty()) {
                Text(
                    "Nothing fires on this nomination.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FadedInk,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerCard(viewModel: GameViewModel, trigger: NominationTrigger) {
    val tone = when (trigger.kind) {
        TriggerKind.AUTO_DEATH, TriggerKind.AUTO_EXECUTION -> EmberRed
        TriggerKind.END_DAY, TriggerKind.VOTE_MODIFIER -> PaleGold
        TriggerKind.CHOICE -> AgedGold
        TriggerKind.WARN -> FadedInk
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "! ${trigger.headline}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = tone,
        )
        if (trigger.detail.isNotBlank()) {
            Text(trigger.detail, style = MaterialTheme.typography.bodyMedium, color = FadedInk)
        }
        if (trigger.impaired) {
            Text(
                "The ability may not work — you decide anyway.",
                style = MaterialTheme.typography.bodyMedium,
                color = PoisonGreen,
            )
        }
        val options = trigger.options.ifEmpty {
            listOf(
                com.clocktower.engine.TriggerOption(DayRules.OPTION_APPLY, "Apply it"),
                com.clocktower.engine.TriggerOption(DayRules.OPTION_SKIP, "Skip", isDefault = true),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (option in options) {
                AssistChip(
                    onClick = { viewModel.applyNominationTrigger(trigger, option.id) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/**
 * Vote counting (§F): a glanceable running tally, the clockwise order the
 * rules prescribe, weights and uncounted hands shown inline, and a **Lock in**
 * button whose label states the *result*.
 *
 * Under secret voting (a sober living Organ Grinder) the tally, the verdict
 * and the "to beat" hint are `•••` — the phone must not be readable from
 * across the table.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun VotePanel(
    viewModel: GameViewModel,
    state: GameState,
    nominatorId: Long,
    nomineeId: Long,
    voters: Set<Long>,
    /** Why the engine would refuse this nomination — empty when it would not. */
    blockers: List<String>,
    force: Boolean,
    onToggleVoter: (Long) -> Unit,
    onForce: () -> Unit,
    onReset: () -> Unit,
) {
    val lookup: (String) -> Character? = viewModel::characterById
    val view = remember(state, nominatorId, nomineeId, voters) {
        NominationModel.voteView(state, lookup, nominatorId, nomineeId, voters)
    }
    val orderedVoters = view.order.filter { it in voters }
    // Secret voting: the number exists, but only for the storyteller and only
    // while they are holding the phone (§F). A long press peeks, a tap hides.
    var peek by remember(nomineeId) { mutableStateOf(false) }
    val hidden = view.secret && !peek

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${view.nominatorName} » ${view.nomineeName}" + if (view.isExile) "  (exile)" else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (view.secret) "SECRET" else "needs ${view.threshold}",
            style = MaterialTheme.typography.labelLarge,
            color = if (view.secret) EmberRed else FadedInk,
        )
    }

    if (view.secret) {
        Text(
            view.secretLine,
            style = MaterialTheme.typography.bodyMedium,
            color = EmberRed,
        )
    }

    // The big glanceable tally — the storyteller is sweeping a hand round a
    // circle of twelve, not reading a parenthetical (finding 21).
    Row(
        Modifier
            .fillMaxWidth()
            // Hold-to-peek belongs to the Organ Grinder and nobody else: a
            // full-width click target that did nothing on an ordinary day was
            // the node `audit` caught overlapping the ring by 34 % once the
            // list scrolled up against it (D-6).
            .then(
                if (view.secret) {
                    Modifier.combinedClickable(
                        onClick = { peek = false },
                        onLongClick = { peek = true },
                    )
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            if (hidden) "•••" else view.tally.toString(),
            fontSize = TALLY_SP.sp,
            fontWeight = FontWeight.Bold,
            color = if (view.result == NominationResult.ABOUT_TO_DIE && !hidden) {
                EmberRed
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (view.secret) "hold to peek" else "of ${view.threshold}",
            style = MaterialTheme.typography.titleMedium,
            color = FadedInk,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    Text(
        "Votes for ${view.nomineeName}, starting now — clockwise from their left.",
        style = MaterialTheme.typography.bodyMedium,
        color = FadedInk,
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (id in view.order) {
            val seat = state.player(id) ?: continue
            val blocked = view.ineligible[id]
            val weight = view.weights[id]
            FilterChip(
                selected = id in voters,
                // A seat the rules say may not vote does not toggle (C-1). The
                // chip stays on the row with its ⊘ and its reason, because the
                // hand DID go up and the storyteller has to see that it was
                // noticed — but it can never reach the tally that decides who
                // is on the block.
                enabled = blocked == null,
                onClick = { onToggleVoter(id) },
                label = {
                    Text(
                        buildString {
                            append(seat.name)
                            // A spent ghost vote only stops a hand when this
                            // vote spends ghost votes at all (never under a
                            // sober Voudon, never on an exile).
                            if (!seat.alive) {
                                val spent = seat.ghostVoteUsed && view.rules.spendsGhostVotes
                                append(if (spent) " ⊘" else " †")
                            }
                            if (weight != null) append(if (weight < 0) " −${-weight}" else " ×$weight")
                        },
                    )
                },
                leadingIcon = if (blocked != null) {
                    { Text("⊘", style = MaterialTheme.typography.labelMedium, color = FadedInk) }
                } else {
                    null
                },
            )
        }
    }

    for (line in NominationModel.ineligibleLines(view)) {
        Text("⊘ $line", style = MaterialTheme.typography.bodyMedium, color = FadedInk)
    }
    for ((_, reason) in view.uncounted) {
        Text("! $reason", style = MaterialTheme.typography.bodyMedium, color = PaleGold)
    }
    for (id in view.mustVote) {
        if (id !in voters) {
            val name = state.player(id)?.name ?: "That seat"
            Text(
                "! $name must vote for every nomination.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaleGold,
            )
        }
    }
    for (reason in view.reasons) {
        Text("· $reason", style = MaterialTheme.typography.bodyMedium, color = FadedInk)
    }

    if (!hidden) {
        Text(
            view.outcomeLine,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (view.result == NominationResult.ABOUT_TO_DIE) {
                EmberRed
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    // `DayRules.record` refuses an illegal nomination and returns the state
    // untouched, so a live Lock in on a closed day cleared the draft and wrote
    // nothing at all — the storyteller believed the vote was on the record
    // (C-4). The button now says it will not be recorded, and says why.
    val refused = NominationModel.lockInRefused(blockers, force)
    if (refused) {
        Text(
            "This will NOT be recorded: ${blockers.first()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EmberRed,
        )
        TextButton(onClick = onForce) { Text("Record it anyway") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            enabled = !refused,
            modifier = Modifier.weight(1f),
            onClick = {
                viewModel.nominate(
                    Nomination(
                        day = state.cycle,
                        nominatorId = nominatorId,
                        nomineeId = nomineeId,
                        voterIds = orderedVoters,
                        result = view.result,
                        isExile = view.isExile,
                        voteRules = view.rules,
                    ),
                    force = force,
                )
                onReset()
            },
        ) { Text(NominationModel.lockInLabel(view), fontWeight = FontWeight.Bold) }
        TextButton(onClick = onReset) { Text("Cancel") }
    }
}

/** 48 sp: readable at arm's length while counting hands (§F). */
private const val TALLY_SP = 48f
