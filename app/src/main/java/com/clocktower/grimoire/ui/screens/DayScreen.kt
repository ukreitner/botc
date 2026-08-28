package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Briefing
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Briefings
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameState
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Verdict
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.DayTimer
import com.clocktower.grimoire.ui.components.HostTimerInBar
import com.clocktower.grimoire.ui.components.TimerControls
import com.clocktower.grimoire.ui.components.TimerFormat
import com.clocktower.grimoire.ui.components.rememberTimerNow
import com.clocktower.grimoire.ui.screens.day.BRIEFING_SECTIONS
import com.clocktower.grimoire.ui.screens.day.BriefingRow
import com.clocktower.grimoire.ui.screens.day.DayModel
import com.clocktower.grimoire.ui.screens.day.DayStage
import com.clocktower.grimoire.ui.screens.day.ExecutionSheet
import com.clocktower.grimoire.ui.screens.day.DayStats
import com.clocktower.grimoire.ui.screens.day.ExileSheet
import com.clocktower.grimoire.ui.screens.day.NominationDetail
import com.clocktower.grimoire.ui.screens.day.SeatRingPanel
import com.clocktower.grimoire.ui.screens.day.SaidModel
import com.clocktower.grimoire.ui.screens.day.SaidRow
import com.clocktower.grimoire.ui.screens.day.SaidSheet
import com.clocktower.grimoire.ui.screens.day.StageCard
import com.clocktower.grimoire.ui.screens.day.sectionHeading
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.FadedInk
import com.clocktower.grimoire.ui.theme.PaleGold

/**
 * The **day timeline**: one screen, one stage, one primary action
 * (ux/day-screen §0–§K, ARCHITECTURE §3.2).
 *
 * Stages appear in the order the storyteller lives them — Dawn · Morning
 * briefing · What was said · Nominations · Dusk — each collapsing to a single
 * summary line once done, over a **fixed bottom bar** (`⏱ timer`, `+ Say`,
 * `Nominate`) that is the only thing the thumb ever has to find.
 *
 * What this screen consumes and nothing else: `Briefings.at(..., DAY_START)`,
 * `DayRules`, `Execution` and the ledger — every one of them through
 * [GameViewModel]'s `GameActionsApi` (§3.3). There is no character id anywhere
 * in this file: the Virgin's interceptor, the Witch's death and the Goblin's
 * claim all arrive as `NominationTrigger`s the engine built (I1).
 */
@Composable
fun DayScreen(
    viewModel: GameViewModel,
    state: GameState,
    /**
     * Closes the day through the shell's phase flow, so the storyteller lands
     * on WP6's dusk sheet — which is where "No execution today", the blocking
     * win advisories and the execution-on-the-block hand-off already live. The
     * Day tab must hand off to it, never duplicate it.
     */
    onDusk: (() -> Unit)? = null,
    /** Opens a seat's sheet on the Grimoire tab, when the shell wires it. */
    onOpenSeat: ((Long) -> Unit)? = null,
) {
    val lookup: (String) -> Character? = viewModel::characterById

    // ---- draft nomination, kept across tab switches by the shell's holder ----
    var nominatorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nomineeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var voters by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var forceNomination by rememberSaveable { mutableStateOf(false) }

    var openStage by rememberSaveable { mutableStateOf<DayStage?>(null) }
    var ticked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sayFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var saySource by rememberSaveable { mutableStateOf<String?>(null) }
    var sayOpen by rememberSaveable { mutableStateOf(false) }
    var executeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exileId by rememberSaveable { mutableStateOf<Long?>(null) }
    var timerOpen by rememberSaveable { mutableStateOf(false) }

    val currentPlayerIds = state.players.map { it.id }.toSet()
    LaunchedEffect(currentPlayerIds) {
        if (nominatorId !in currentPlayerIds) nominatorId = null
        if (nomineeId !in currentPlayerIds) nomineeId = null
        voters = voters.intersect(currentPlayerIds)
    }

    val dayStart = remember(state) { viewModel.dayBriefing(state) }
    val dawn = state.lastDawn?.takeIf { it.slot == BriefingSlot.DAWN && it.cycle == state.cycle }
    val rows = remember(state, dayStart, dawn, ticked) {
        DayModel.stages(state, lookup, dawn, dayStart, ticked)
    }
    val stats = remember(state) { DayModel.stats(state, lookup) }
    val expanded = openStage ?: DayModel.autoExpanded(rows)
    val sources = remember(dayStart) { DayModel.statementSources(dayStart) }

    fun resetDraft() {
        nominatorId = null
        nomineeId = null
        voters = emptySet()
        forceNomination = false
    }

    fun openSay(speakerId: Long?, sourceId: String?) {
        sayFor = speakerId
        saySource = sourceId
        sayOpen = true
        openStage = DayStage.SAID
    }

    Column(Modifier.fillMaxSize()) {
        DayStatStrip(stats)

        // The ring is PINNED, not scrolled to: two taps must never cost a
        // scroll while the table is waiting (§E).
        if (expanded == DayStage.NOMINATIONS) {
            SeatRingPanel(
                viewModel = viewModel,
                state = state,
                nominatorId = nominatorId,
                nomineeId = nomineeId,
                onPickSeat = { id ->
                    when {
                        nominatorId == id -> nominatorId = null
                        nomineeId == id -> nomineeId = null
                        nominatorId == null -> nominatorId = id
                        else -> {
                            nomineeId = id
                            voters = emptySet()
                        }
                    }
                    forceNomination = false
                },
                onSay = { openSay(it, null) },
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in rows) {
                item(key = row.stage.name) {
                    StageCard(
                        row = row,
                        expanded = expanded == row.stage,
                        onToggle = { openStage = if (expanded == row.stage) null else row.stage },
                    ) {
                        when (row.stage) {
                            DayStage.DAWN -> DawnBody(
                                viewModel = viewModel,
                                briefing = dawn,
                                ticked = ticked,
                                onTick = { ticked = ticked + it },
                                onOpenSeat = onOpenSeat,
                                onRecord = { openSay(null, it) },
                            )

                            DayStage.BRIEFING -> BriefingBody(
                                viewModel = viewModel,
                                briefing = dayStart,
                                ticked = ticked,
                                onTick = { ticked = ticked + it },
                                onOpenSeat = onOpenSeat,
                                onRecord = { openSay(null, it) },
                            )

                            DayStage.SAID -> SaidBody(
                                viewModel = viewModel,
                                state = state,
                                onCompose = { openSay(null, null) },
                            )

                            DayStage.NOMINATIONS -> NominationsBody(
                                viewModel = viewModel,
                                state = state,
                                nominatorId = nominatorId,
                                nomineeId = nomineeId,
                                voters = voters,
                                force = forceNomination,
                                secret = stats.secret,
                                onToggleVoter = { id ->
                                    voters = if (id in voters) voters - id else voters + id
                                },
                                onForce = { forceNomination = true },
                                onReset = { resetDraft() },
                                onExecute = { executeId = it },
                                onExile = { exileId = it },
                            )

                            DayStage.DUSK -> DuskBody(
                                viewModel = viewModel,
                                state = state,
                                onDusk = onDusk,
                                onExecute = { executeId = it },
                            )
                        }
                    }
                }
            }
        }

        DayBottomBar(
            timerOpen = timerOpen,
            onTimer = { timerOpen = !timerOpen },
            onSay = { openSay(null, null) },
            onNominate = { openStage = DayStage.NOMINATIONS },
        )
    }

    if (sayOpen) {
        SaidSheet(
            viewModel = viewModel,
            state = state,
            initialSpeakerId = sayFor,
            initialSourceId = saySource,
            sources = sources,
            onDismiss = { sayOpen = false },
        )
    }

    exileId?.let { id ->
        ExileSheet(
            viewModel = viewModel,
            state = state,
            targetId = id,
            onDismiss = { exileId = null },
        )
    }

    executeId?.let { id ->
        ExecutionSheet(
            viewModel = viewModel,
            state = state,
            targetId = id,
            nominatorId = blockingNomination(state, id)?.nominatorId,
            nominationIndex = blockingNominationIndexFor(state, id),
            onDismiss = { executeId = null },
        )
    }
}

/** Day N · alive · threshold · ghosts, and who is on the block. */
@Composable
private fun DayStatStrip(stats: DayStats) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stats.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = AgedGold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stats.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = FadedInk,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                stats.blockLine,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (stats.onBlockId != null) EmberRed else FadedInk,
            )
            // Whatever rewrote today's vote, said once, where the numbers are.
            if (stats.voteNote.isNotBlank()) {
                Text(
                    stats.voteNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = AgedGold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The fixed bottom bar (§A): the timer, the statement composer and the
 * nomination ring. Everything routine lives here so the thumb never hunts.
 */
@Composable
private fun DayBottomBar(
    timerOpen: Boolean,
    onTimer: () -> Unit,
    onSay: () -> Unit,
    onNominate: () -> Unit,
) {
    val timer = DayTimer.shared
    // While this bar is on screen the shell's floating pill stands down, so
    // there is exactly one timer and it is the same one.
    HostTimerInBar(timer)
    val now = rememberTimerNow(timer)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (timerOpen) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TimerControls(timer, now, onDone = onTimer)
                }
                HorizontalDivider()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onTimer) {
                    Text("⏱ " + TimerFormat.barLabel(timer, now))
                }
                FilledTonalButton(onClick = onSay, modifier = Modifier.weight(1f)) {
                    Text("+ Say")
                }
                Button(onClick = onNominate, modifier = Modifier.weight(1f)) {
                    Text("Nominate", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stage bodies
// ---------------------------------------------------------------------------

@Composable
private fun DawnBody(
    viewModel: GameViewModel,
    briefing: Briefing?,
    ticked: Set<String>,
    onTick: (String) -> Unit,
    onOpenSeat: ((Long) -> Unit)?,
    onRecord: (String) -> Unit,
) {
    if (briefing == null || briefing.items.isEmpty()) {
        Text(
            "Nobody died last night. Say so.",
            style = MaterialTheme.typography.bodyMedium,
            color = FadedInk,
        )
        return
    }
    BriefingSections(viewModel, briefing, ticked, onTick, onOpenSeat, onRecord)
}

@Composable
private fun BriefingBody(
    viewModel: GameViewModel,
    briefing: Briefing,
    ticked: Set<String>,
    onTick: (String) -> Unit,
    onOpenSeat: ((Long) -> Unit)?,
    onRecord: (String) -> Unit,
) {
    if (briefing.items.isEmpty()) {
        Text(
            "Nothing constrains today.",
            style = MaterialTheme.typography.bodyMedium,
            color = FadedInk,
        )
        return
    }
    BriefingSections(viewModel, briefing, ticked, onTick, onOpenSeat, onRecord)
}

/** ANNOUNCE first, then PRIVATE, TRUE TODAY, STILL TO DO, SWEPT (WP6's order). */
@Composable
private fun BriefingSections(
    viewModel: GameViewModel,
    briefing: Briefing,
    ticked: Set<String>,
    onTick: (String) -> Unit,
    onOpenSeat: ((Long) -> Unit)?,
    onRecord: (String) -> Unit,
) {
    for (kind in BRIEFING_SECTIONS) {
        val items = briefing.of(kind)
        if (items.isEmpty()) continue
        Text(
            sectionHeading(kind),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PaleGold,
        )
        for (item in items) {
            val tickable = kind == BriefingKind.ANNOUNCE || kind == BriefingKind.TODO_ASK
            val (label, action) = itemAction(viewModel, item, onOpenSeat, onRecord)
            BriefingRow(
                item = item,
                ticked = item.key in ticked,
                onTick = if (tickable) {
                    {
                        // A sentence that has been said stops being owed: the
                        // engine's own verb retires the ledger entry or prompt.
                        viewModel.resolveBriefingItem(item)
                        onTick(item.key)
                    }
                } else {
                    null
                },
                actionLabel = label,
                onAction = action,
            )
        }
    }
}

/**
 * The one-tap follow-through for a briefing item. The engine wrote the
 * `actionId`; the screen only decides which of its own surfaces opens.
 */
private fun itemAction(
    viewModel: GameViewModel,
    item: BriefingItem,
    onOpenSeat: ((Long) -> Unit)?,
    onRecord: (String) -> Unit,
): Pair<String?, (() -> Unit)?> = when {
    item.actionId.startsWith(Briefings.ACTION_RECORD) ->
        "Record it" to { onRecord(item.actionId.removePrefix(Briefings.ACTION_RECORD)) }

    item.actionId.startsWith(Briefings.ACTION_OPEN_SEAT) && onOpenSeat != null ->
        "Open seat" to { item.playerId?.let(onOpenSeat) ?: Unit }

    item.actionId.startsWith(Briefings.ACTION_RERUN_FIRST_NIGHT) ->
        "Done" to { item.playerId?.let { viewModel.markRerunDone(it) } ?: Unit }

    else -> null to null
}

/**
 * "What was said": today's lines, the composer, and the earlier days behind an
 * expander. Verdict chips only where a rule will read the answer.
 */
@Composable
private fun SaidBody(
    viewModel: GameViewModel,
    state: GameState,
    onCompose: () -> Unit,
) {
    val lookup: (String) -> Character? = viewModel::characterById
    val today = remember(state) { SaidModel.rows(state, lookup, state.cycle) }
    val earlier = remember(state) {
        state.ledger.count { it.cycle < state.cycle && it.kind in SaidModel.KINDS }
    }
    var showEarlier by rememberSaveable { mutableStateOf(false) }

    if (today.isEmpty()) {
        Text(
            "Nothing recorded today. Tap a seat, then type or dictate one line — " +
                "nothing has to be in play.",
            style = MaterialTheme.typography.bodyMedium,
            color = FadedInk,
        )
    }
    for (row in today) {
        SaidRowView(viewModel, row)
    }
    FilledTonalButton(onClick = onCompose, modifier = Modifier.fillMaxWidth()) {
        Text("+ Record what was said")
    }
    if (earlier > 0) {
        TextButton(onClick = { showEarlier = !showEarlier }) {
            Text(if (showEarlier) "▾ earlier days ($earlier)" else "▸ earlier days ($earlier)")
        }
        if (showEarlier) {
            Column(
                Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (cycle in (state.cycle - 1) downTo 1) {
                    val rows = SaidModel.rows(state, lookup, cycle)
                    if (rows.isEmpty()) continue
                    Text(
                        "Day $cycle",
                        style = MaterialTheme.typography.labelMedium,
                        color = PaleGold,
                    )
                    for (row in rows) SaidRowView(viewModel, row)
                }
            }
        }
    }
}

@Composable
private fun SaidRowView(viewModel: GameViewModel, row: SaidRow) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(row.line, style = MaterialTheme.typography.bodyMedium)
            if (row.kind == LedgerKind.ANNOUNCE && row.announcePending) {
                Text(
                    "still owed to the table",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberRed,
                )
            }
        }
        if (row.kind == LedgerKind.ANNOUNCE && row.announcePending) {
            TextButton(onClick = { viewModel.markAnnounced(row.entryId) }) { Text("Said it") }
        }
        if (row.wantsVerdict) {
            for (verdict in listOf(Verdict.TRUE, Verdict.FALSE, Verdict.UNJUDGED)) {
                val glyph = when (verdict) {
                    Verdict.TRUE -> "✓"
                    Verdict.FALSE -> "✗"
                    else -> "?"
                }
                TextButton(onClick = { viewModel.setLedgerVerdict(row.entryId, verdict) }) {
                    Text(
                        glyph,
                        fontWeight = if (row.verdict == verdict) FontWeight.Bold else FontWeight.Normal,
                        color = if (row.verdict == verdict) AgedGold else FadedInk,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun NominationsBody(
    viewModel: GameViewModel,
    state: GameState,
    nominatorId: Long?,
    nomineeId: Long?,
    voters: Set<Long>,
    force: Boolean,
    secret: Boolean,
    onToggleVoter: (Long) -> Unit,
    onForce: () -> Unit,
    onReset: () -> Unit,
    onExecute: (Long) -> Unit,
    onExile: (Long) -> Unit,
) {
    NominationDetail(
        viewModel = viewModel,
        state = state,
        nominatorId = nominatorId,
        nomineeId = nomineeId,
        voters = voters,
        force = force,
        onToggleVoter = onToggleVoter,
        onForce = onForce,
        onReset = onReset,
    )

    val todays = state.nominations.withIndex().filter { it.value.day == state.cycle }
    if (todays.isEmpty()) return
    HorizontalDivider()
    Text("Today's nominations", style = MaterialTheme.typography.titleSmall)
    for ((index, nomination) in todays.reversed()) {
        NominationRow(viewModel, state, index, nomination, secret, onExecute, onExile)
    }
}

@Composable
private fun NominationRow(
    viewModel: GameViewModel,
    state: GameState,
    index: Int,
    nomination: Nomination,
    secret: Boolean,
    onExecute: (Long) -> Unit,
    onExile: (Long) -> Unit,
) {
    val nominator = state.player(nomination.nominatorId)
    val nominee = state.player(nomination.nomineeId)
    var showVoters by rememberSaveable(index) { mutableStateOf(false) }
    val onBlockId = DayRules.aboutToDie(state)
    val passed = nomination.result == NominationResult.ABOUT_TO_DIE
    val lookup: (String) -> Character? = viewModel::characterById
    // The Butcher's second execution is legal, so "spent" alone must not hide
    // the button — `nominationsClosed` already accounts for it.
    val executable = nominee?.alive == true && when {
        nomination.isExile -> passed
        else -> passed && nomination.nomineeId == onBlockId &&
            !DayRules.nominationsClosed(state, lookup)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${nominator?.name ?: "?"} » ${nominee?.name ?: "?"}" +
                            if (nomination.isExile) "  (exile)" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (secret && !nomination.isExile) {
                            "••• votes · •••"
                        } else {
                            "${nomination.votes} votes · " + resultLabel(nomination.result)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FadedInk,
                    )
                }
                if (executable) {
                    if (nomination.isExile) {
                        OutlinedButton(onClick = { onExile(nomination.nomineeId) }) {
                            Text("Exile")
                        }
                    } else {
                        OutlinedButton(onClick = { onExecute(nomination.nomineeId) }) {
                            Text("Execute")
                        }
                    }
                }
            }
            Row {
                TextButton(onClick = { showVoters = !showVoters }) {
                    Text(if (showVoters) "▾ voters" else "▸ voters")
                }
                if (nomination.result != NominationResult.WITHDRAWN) {
                    TextButton(onClick = { viewModel.withdrawNomination(index) }) {
                        Text("Withdraw")
                    }
                }
            }
            if (showVoters) {
                Text(
                    if (nomination.voterIds.isEmpty()) {
                        "No hands recorded."
                    } else {
                        nomination.voterIds.joinToString(", ") { state.player(it)?.name ?: "?" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FadedInk,
                )
                for (reason in nomination.voteRules?.reasons.orEmpty()) {
                    Text("· $reason", style = MaterialTheme.typography.labelMedium, color = FadedInk)
                }
            }
        }
    }
}

private fun resultLabel(result: NominationResult): String = when (result) {
    NominationResult.ABOUT_TO_DIE -> "about to die"
    NominationResult.TIED -> "tied"
    NominationResult.WITHDRAWN -> "withdrawn"
    NominationResult.SAFE -> "safe"
}

/**
 * Dusk hands off to the shell's phase flow — WP6's dusk sheet already asks
 * "No execution today?", shows the blocking win advisories and executes
 * whoever is on the block. Duplicating any of that here would give the
 * storyteller two places to close one day.
 */
@Composable
private fun DuskBody(
    viewModel: GameViewModel,
    state: GameState,
    onDusk: (() -> Unit)?,
    onExecute: (Long) -> Unit,
) {
    val lookup: (String) -> Character? = viewModel::characterById
    val record = viewModel.executionToday(state)
    val onBlock = DayRules.aboutToDie(state)?.let { state.player(it) }

    when {
        record?.outcome == ExecutionOutcome.NO_EXECUTION -> Text(
            "No execution today — recorded. The Mayor, the Vortox and the Zombuul all read this.",
            style = MaterialTheme.typography.bodyMedium,
            color = FadedInk,
        )

        record != null -> {
            Text(
                DayRules.nominationsClosedReason(state, lookup),
                style = MaterialTheme.typography.bodyMedium,
                color = FadedInk,
            )
            for (consequence in viewModel.executionConsequences(state, record)) {
                Text(
                    "! ${consequence.headline}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaleGold,
                )
                if (consequence.detail.isNotBlank()) {
                    Text(
                        consequence.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = FadedInk,
                    )
                }
            }
        }

        onBlock != null -> {
            Text(
                "On the block: ${onBlock.name}.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = EmberRed,
            )
            // The preview is the funnel's own verdict, so a Devil's Advocate is
            // visible BEFORE the button rather than after the death (finding 27).
            val preview = viewModel.executionPreview(state, onBlock.id)
            if (preview !is KillOutcome.Dies) {
                Text(
                    previewNote(preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = PaleGold,
                )
            }
            Button(
                onClick = { onExecute(onBlock.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Execute ${onBlock.name}", fontWeight = FontWeight.Bold) }
        }

        else -> Text(
            "No one is about to die. There is no execution today — " +
                "close the day and confirm it.",
            style = MaterialTheme.typography.bodyMedium,
            color = PaleGold,
        )
    }

    if (onDusk != null) {
        Button(onClick = onDusk, modifier = Modifier.fillMaxWidth()) {
            Text("Everyone, eyes closed ▸", fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            "Tap Dusk in the top bar to close the day — that sheet confirms the " +
                "execution or records that there was none.",
            style = MaterialTheme.typography.bodySmall,
            color = FadedInk,
        )
    }
    Spacer(Modifier.height(2.dp))
}

/** One line of warning above the dusk card's Execute button. */
private fun previewNote(preview: KillOutcome): String = when (preview) {
    is KillOutcome.Dies -> preview.reason
    is KillOutcome.Prevented -> preview.reason
    is KillOutcome.Spends -> "They survive — the ability is spent."
    is KillOutcome.RegistersDead -> preview.reason
    is KillOutcome.Redirect -> preview.reason
    is KillOutcome.Choice -> preview.question
    KillOutcome.AlreadyDead -> "They are already dead."
}

/** The nomination that put this seat on the block today. */
private fun blockingNomination(state: GameState, playerId: Long): Nomination? =
    state.nominations.lastOrNull {
        it.day == state.cycle && !it.isExile && it.nomineeId == playerId
    }

private fun blockingNominationIndexFor(state: GameState, playerId: Long): Int? =
    state.nominations.indexOfLast {
        it.day == state.cycle && !it.isExile && it.nomineeId == playerId
    }.takeIf { it >= 0 }
