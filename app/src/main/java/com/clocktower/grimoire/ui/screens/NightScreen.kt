package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameState
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Memory
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightPlan
import com.clocktower.engine.NightStep
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.FullScreenShow
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.describe
import com.clocktower.grimoire.ui.screens.night.NIGHT_MIN_SP
import com.clocktower.grimoire.ui.screens.night.NightCard
import com.clocktower.grimoire.ui.screens.night.RowMark
import com.clocktower.grimoire.ui.screens.night.RowView
import com.clocktower.grimoire.ui.screens.night.RowGutter
import com.clocktower.grimoire.ui.screens.night.ProgressStrip
import com.clocktower.grimoire.ui.screens.night.actionEffects
import com.clocktower.grimoire.ui.screens.night.color
import com.clocktower.grimoire.ui.screens.night.dimAlpha
import com.clocktower.grimoire.ui.screens.night.nextDimLevel
import com.clocktower.grimoire.ui.screens.night.nightSp
import com.clocktower.grimoire.ui.screens.night.openRowKey
import com.clocktower.grimoire.ui.screens.night.openRowToken
import com.clocktower.grimoire.ui.screens.night.openingToken
import com.clocktower.grimoire.ui.screens.night.progress
import com.clocktower.grimoire.ui.screens.night.promptBelongsTo
import com.clocktower.grimoire.ui.screens.night.promptedToken
import com.clocktower.grimoire.ui.screens.night.rowViews
import com.clocktower.grimoire.ui.screens.night.segmentTones
import com.clocktower.grimoire.ui.theme.AgedGold

/**
 * The night sheet.
 *
 * One card at a time, one question on it, and one primary button whose label
 * states the OUTCOME — "EVE SURVIVES — NOBODY DIES", never "Confirm". The rest
 * of tonight is a collapsed list around it that shows the result of every step
 * that is done and the ask of every step that is not.
 *
 * It consumes exactly one thing (ARCHITECTURE §3.2): `NightPlan.build(state)`.
 * Every per-character difference — who wakes, what they are asked, what the
 * cards say, whether the row is gated off and why — arrives as data on the
 * `NightStep` the registry produced, which is why **no character id appears in
 * this file** (invariant I1, enforced by `SourceGatesTest`).
 */
@Composable
fun NightScreen(
    viewModel: GameViewModel,
    state: GameState,
    onOpenShowTool: () -> Unit = {},
    /** Runs the phase button once the sheet is finished — the last card opens the day. */
    onDawn: () -> Unit = {},
) {
    // Pure and cheap: rebuilt on every state change so an insertion (a
    // resurrection's re-run, a Scarlet Woman promotion) appears at once (I6).
    val plan = remember(state) { viewModel.nightPlan(state) }
    val done = state.nightStepsDone

    // "<night>|<token>", never a bare token: `rememberSaveable(state.cycle)`
    // restores its saved value even when the key changed, so the DAWN row left
    // over from the end of the previous night became night 2's opening card —
    // one tap on "OPEN THE DAY →" and nobody was poisoned, protected or killed
    // (playtest B P0 #2).
    var openRow by rememberSaveable { mutableStateOf("") }
    val activeToken = openRowToken(openRow, state.cycle)
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var forced by remember(state.cycle) { mutableStateOf(emptySet<String>()) }
    var shown by remember { mutableStateOf<ShownCard?>(null) }
    var kill by remember { mutableStateOf<KillRequest?>(null) }
    var pendingDawn by remember(state.cycle) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // A question the engine raised and is still owed (an Imp that killed itself
    // owes a star pass) holds ITS row open until it is answered — ticking the
    // row is not what answers it (playtest B P0 #3).
    val owed = viewModel.promptsDue(state, BriefingSlot.NOW)
    val pinned = promptedToken(plan.steps, owed)
    val active = plan.steps.firstOrNull { it.key.token == pinned }
        ?: plan.steps.firstOrNull { it.key.token == activeToken }
        ?: plan.steps.firstOrNull { it.key.token == openingToken(plan.steps, done) }
    val activeIndex = plan.steps.indexOfFirst { it.key.token == active?.key?.token }

    // The button that finished a step also advances to the next one: ticking is
    // a consequence of doing, never a separate act (defect #7).
    LaunchedEffect(done, plan.steps.size, pendingDawn, state.cycle) {
        val token = activeToken
        if (token == null || token in done || plan.steps.none { it.key.token == token }) {
            openRow = openRowKey(state.cycle, openingToken(plan.steps, done))
        }
        // The dawn card's button ticked its own row a frame ago; the phase
        // button now sees a finished sheet and raises the dawn briefing. If
        // something IS still outstanding, its own guard says so — actionably.
        if (pendingDawn) {
            pendingDawn = false
            onDawn()
        }
    }
    LaunchedEffect(activeToken) {
        val index = plan.steps.indexOfFirst { it.key.token == activeToken }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    val rows = rowViews(
        plan = plan,
        done = done,
        activeToken = active?.key?.token,
        forced = forced,
        holderNames = { step -> step.wakes.mapNotNull { state.player(it)?.name }.joinToString(", ") },
        results = { step -> resultOf(state, step) },
    )
    val window = if (listOpen || activeIndex < 0) {
        rows.indices
    } else {
        (activeIndex - WINDOW..activeIndex + WINDOW)
    }

    Column(Modifier.fillMaxSize()) {
        ProgressStrip(
            cycleLabel = if (plan.isFirstNight) "First night" else "Night ${state.cycle}",
            progress = progress(plan, done, active?.key?.token),
            segments = segmentTones(plan, done, active?.key?.token, forced),
            dimLevel = state.dimLevel,
            listOpen = listOpen,
            onDim = { viewModel.setDimLevel(nextDimLevel(state.dimLevel)) },
            onToggleList = { listOpen = !listOpen },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            // Room at the foot for the docked timer, so the last card's
            // primary button is never underneath it.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexedRows(rows, window) { index, row ->
                val step = plan.steps[index]
                if (step.key.token == active?.key?.token) {
                    NightCard(
                        viewModel = viewModel,
                        state = state,
                        step = step,
                        forced = step.key.token in forced,
                        onRunAnyway = { forced = forced + step.key.token },
                        onShow = { card, who, truthful ->
                            shown = ShownCard(card, who, truthful, step.abilityId)
                            viewModel.recordShown(
                                playerId = who,
                                sourceId = step.abilityId,
                                shown = card.describe { id -> viewModel.characterById(id)?.name ?: id },
                                truthful = truthful,
                            )
                        },
                        onOpenShowTool = onOpenShowTool,
                        onKillSheet = { targetId, killerId ->
                            kill = KillRequest(targetId, killerId, causeOf(state, step), step.key.token)
                        },
                        onDawn = { pendingDawn = true },
                        onBack = {
                            openRow = openRowKey(
                                state.cycle,
                                plan.steps.getOrNull(index - 1)?.key?.token ?: activeToken,
                            )
                        },
                        onSkip = {
                            viewModel.markNightStepDone(step.key)
                            openRow = openRowKey(state.cycle, plan.steps.getOrNull(index + 1)?.key?.token)
                        },
                        prompts = owed.filter { promptBelongsTo(it, step) },
                    )
                } else {
                    NightRowLine(
                        row = row,
                        onOpen = { openRow = openRowKey(state.cycle, row.token) },
                        onRunAnyway = {
                            forced = forced + row.token
                            openRow = openRowKey(state.cycle, row.token)
                        },
                    )
                }
            }
            if (plan.steps.isEmpty()) {
                item {
                    Text(
                        text = "Nobody acts tonight. Open the day when the table is quiet.",
                        fontSize = nightSp(16f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    shown?.let { card ->
        FullScreenShow(
            card = card.card,
            viewModel = viewModel,
            coverCaption = if (plan.isFirstNight) "First night" else "Night ${state.cycle}",
            onDismiss = { shown = null },
        )
    }

    // The ONE kill sheet (lead D24): the night screen no longer owns a kill
    // button of its own. It renders `Deaths.killOutcome` and applies
    // `Deaths.attempt`, exactly as the day screen and the seat sheet do.
    kill?.let { request ->
        KillSheet(
            viewModel = viewModel,
            state = state,
            targetId = request.targetId,
            initialCause = request.cause,
            lockCause = false,
            initialKillerId = request.killerId,
            onDismiss = { kill = null },
            onRecorded = {
                val step = plan.steps.firstOrNull { it.key.token == request.stepToken }
                if (step != null) {
                    viewModel.recordChoice(
                        sourceId = step.abilityId,
                        actorId = step.holderId,
                        targetIds = listOf(request.targetId),
                    )
                    viewModel.markNightStepDone(step.key)
                }
                kill = null
            },
        )
    }
}

/** How many rows either side of the open card the compact sheet shows. */
private const val WINDOW = 3

/** A card being held up to a player, and who it is for. */
private data class ShownCard(
    val card: ShowCard,
    val recipientId: Long?,
    val truthful: Boolean,
    val sourceId: String,
)

/** A death this step wants resolved through the shared kill sheet. */
private data class KillRequest(
    val targetId: Long,
    val killerId: Long?,
    val cause: DeathCause,
    val stepToken: String,
)

/** The cause this step's attack carries, straight from the registry. */
private fun causeOf(state: GameState, step: NightStep): DeathCause =
    actionEffects(step.action).filterIsInstance<NightEffect.Attack>().firstOrNull()?.cause
        ?: defaultCause(state)

/**
 * What this row recorded tonight, for the collapsed list's right-hand column.
 * Read from the LEDGER, which is what survives the dawn token sweep (I3).
 */
private fun resultOf(state: GameState, step: NightStep): String {
    val choice = Memory
        .lastChoice(state, step.abilityId, step.holderId, beforeCycle = state.cycle + 1)
        ?.takeIf { it.cycle == state.cycle }
    if (choice != null) {
        if (choice.text == NightPlan.NO_CHOICE) return "chose nobody"
        val names = choice.targetIds.mapNotNull { state.player(it)?.name }
        if (names.isNotEmpty()) return "→ ${names.joinToString()}"
    }
    val told = Memory.by(state, LedgerKind.TOLD, step.abilityId, step.holderId)
        .lastOrNull { it.cycle == state.cycle }
    return told?.shown?.takeIf { it.isNotBlank() }?.let { "shown: $it" }.orEmpty()
}

/** One line of the collapsed sheet. Tapping it opens that step's card. */
@Composable
private fun NightRowLine(row: RowView, onOpen: () -> Unit, onRunAnyway: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (row.mark == RowMark.CURRENT) {
                    AgedGold.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            RowGutter(row.mark, row.tone)
            Text(
                text = "${row.ordinal}",
                fontSize = NIGHT_MIN_SP.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    fontSize = nightSp(16f).sp,
                    lineHeight = nightSp(20f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = row.tone.color(),
                    maxLines = 2,
                )
                if (row.holders.isNotBlank()) {
                    Text(
                        text = row.holders,
                        fontSize = NIGHT_MIN_SP.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.right,
                fontSize = NIGHT_MIN_SP.sp,
                lineHeight = nightSp(18f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (row.mark == RowMark.SKIPPED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.reason,
                    fontSize = NIGHT_MIN_SP.sp,
                    lineHeight = nightSp(18f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "[Run anyway]",
                    fontSize = NIGHT_MIN_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRunAnyway)
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** `itemsIndexed` restricted to a window of the sheet, keyed by step token. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedRows(
    rows: List<RowView>,
    window: IntRange,
    content: @Composable (Int, RowView) -> Unit,
) {
    for ((index, row) in rows.withIndex()) {
        if (index !in window) continue
        item(key = row.token) { content(index, row) }
    }
}

/** The night scrim's opacity, re-exported so `GameShell` need not import the package. */
fun nightScrimAlpha(level: Int): Float = dimAlpha(level)
