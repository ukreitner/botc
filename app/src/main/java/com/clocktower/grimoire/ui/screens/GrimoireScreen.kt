package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.EffectGroup
import com.clocktower.engine.Effects
import com.clocktower.engine.GameState
import com.clocktower.engine.Identity
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.RenderedToken
import com.clocktower.engine.Status
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.PipRow
import com.clocktower.grimoire.ui.components.ReminderToken
import com.clocktower.grimoire.ui.components.StatusPip
import com.clocktower.grimoire.ui.components.ZoomControls
import com.clocktower.grimoire.ui.components.rememberZoomState
import com.clocktower.grimoire.ui.components.visiblePips
import com.clocktower.grimoire.ui.components.zoomGestures
import com.clocktower.grimoire.ui.components.zoomTransform
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.MIN_TEXT_SP
import com.clocktower.grimoire.ui.theme.NightSky
import com.clocktower.grimoire.ui.theme.OnBlockGold
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.ShroudBlack
import com.clocktower.grimoire.ui.theme.Twilight
import com.clocktower.grimoire.ui.theme.color
import com.clocktower.grimoire.ui.theme.displayName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The grimoire.
 *
 * Two views over one selection and one filter (grimoire-and-seats §4):
 * **Circle** is for RECOGNITION — status pips, no micro-text — and **Board**
 * is for READING, listing every token in full text with nothing truncated.
 * The seat sheet is for acting.
 */
@Composable
fun GrimoireScreen(
    viewModel: GameViewModel,
    state: GameState,
    onOpenBluffs: () -> Unit = {},
    onOpenFabled: () -> Unit = {},
    onOpenSeat: (Long) -> Unit,
) {
    var board by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var peekSeat by rememberSaveable { mutableStateOf<Long?>(null) }
    var hintDismissed by rememberSaveable { mutableStateOf(false) }

    // One pass over the effect model per state, shared by both views: the old
    // screen re-derived impairment per seat on every recomposition.
    val tokens: Map<Long, List<RenderedToken>> = remember(state) {
        state.players.associate { it.id to Effects.rendered(state, viewModel::characterById, it.id) }
    }
    val onBlockId = remember(state.nominations, state.cycle) { DayRules.aboutToDie(state) }
    val matches = remember(state, filter, search, tokens) {
        state.players.filter { seatMatches(it, tokens[it.id].orEmpty(), viewModel.characterById(it.characterId), filter, search) }
            .map { it.id }
            .toSet()
    }
    val filtering = filter != null || search.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        GrimoireHeader(
            viewModel = viewModel,
            state = state,
            tokens = tokens,
            onBlockId = onBlockId,
            board = board,
            onBoard = { board = it },
            filter = filter,
            onFilter = { filter = if (filter == it) null else it },
            search = search,
            onSearch = { search = it },
            onOpenBluffs = onOpenBluffs,
            onOpenFabled = onOpenFabled,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (board) {
                BoardView(
                    viewModel = viewModel,
                    state = state,
                    tokens = tokens,
                    onBlockId = onBlockId,
                    matches = matches,
                    filtering = filtering,
                    onOpenSeat = onOpenSeat,
                    onPeek = { peekSeat = it },
                )
            } else {
                CircleView(
                    viewModel = viewModel,
                    state = state,
                    tokens = tokens,
                    onBlockId = onBlockId,
                    matches = matches,
                    filtering = filtering,
                    onOpenSeat = onOpenSeat,
                    onPeek = { peekSeat = it },
                )
                // The circle stops being the better instrument well before 20
                // seats; say so once instead of letting it degrade silently.
                if (state.players.size >= 18 && !hintDismissed) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 60.dp, end = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "${state.players.size} seats — the Board view reads better on a phone.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { board = true; hintDismissed = true }) { Text("Switch") }
                            TextButton(onClick = { hintDismissed = true }) { Text("No") }
                        }
                    }
                }
            }
            if (state.players.isEmpty()) {
                Text(
                    text = "No seats yet — add players from setup.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    peekSeat?.let { id ->
        TokenPeek(
            viewModel = viewModel,
            state = state,
            playerId = id,
            tokens = tokens[id].orEmpty(),
            onOpenSeat = { peekSeat = null; onOpenSeat(id) },
            onDismiss = { peekSeat = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Header: standing facts, the view switch, filter chips
// ---------------------------------------------------------------------------

/** The two strings the grimoire header prints above the circle. */
data class GrimoireHeaderLine(
    /** "Day 2 · 9 alive · 5 to execute · 2 ghost votes". */
    val facts: String,
    /**
     * One line naming every rule that rewrote the vote — the Voudon, the
     * Bureaucrat's ×3, secret voting. Blank when the vote is the ordinary one;
     * the header is tight, so it renders on a single ellipsised line.
     */
    val voteNote: String = "",
)

/**
 * The header's standing facts — pure Kotlin, so `tools/uicheck` can measure it.
 *
 * Never recompute the threshold or the ghost-vote count here. A sober Voudon
 * (and anything else that rewrites the vote) moves the threshold to 1 and
 * spends no ghost votes at all; [DayRules.voteRules] is the same snapshot the
 * nomination panel and the day stat strip read, so all three agree.
 */
fun grimoireHeaderLine(state: GameState, lookup: (String) -> Character?): GrimoireHeaderLine {
    val cycleLabel = when (state.phase) {
        Phase.SETUP -> "Setup"
        Phase.NIGHT -> "Night ${state.cycle}"
        Phase.DAY -> "Day ${state.cycle}"
    }
    val rules = DayRules.voteRules(state, lookup, isExile = false)
    val ghosts = if (rules.spendsGhostVotes) {
        state.seats.count { !it.alive && !it.ghostVoteUsed }
    } else {
        0
    }
    val facts = buildString {
        append("$cycleLabel · ${state.alivePlayers.size} alive · ${rules.threshold} to execute")
        if (ghosts > 0) append(" · $ghosts ghost ${if (ghosts == 1) "vote" else "votes"}")
    }
    return GrimoireHeaderLine(facts = facts, voteNote = rules.reasons.joinToString(" · "))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrimoireHeader(
    viewModel: GameViewModel,
    state: GameState,
    tokens: Map<Long, List<RenderedToken>>,
    onBlockId: Long?,
    board: Boolean,
    onBoard: (Boolean) -> Unit,
    filter: String?,
    onFilter: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    onOpenBluffs: () -> Unit,
    onOpenFabled: () -> Unit,
) {
    val header = remember(state) { grimoireHeaderLine(state, viewModel::characterById) }
    val expiringAtDusk = tokens.values.sumOf { list -> list.count { it.expiryText.contains("dusk") } }
    val counts = remember(tokens, state.players) { groupCounts(state, tokens) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bluffs left, Fabled right — one tap away, as before. Their
            // height is load-bearing: see [HEADER_CHIP_HEIGHT].
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = HEADER_CHIP_HEIGHT)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenBluffs)
                    .padding(2.dp),
            ) {
                if (state.demonBluffIds.isEmpty()) {
                    Text("+ bluffs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (id in state.demonBluffIds) CharacterToken(character = viewModel.characterById(id), size = 26.dp)
                }
            }
            Text(
                text = header.facts,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = HEADER_CHIP_HEIGHT)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenFabled)
                    .padding(2.dp),
            ) {
                if (state.fabledIds.isEmpty()) {
                    Text("fabled +", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (id in state.fabledIds) CharacterToken(character = viewModel.characterById(id), size = 26.dp)
                }
            }
        }
        // Whatever rewrote the vote, said once, where the numbers are. One
        // line only — this header already carries four rows.
        if (header.voteNote.isNotBlank()) {
            Text(
                header.voteNote,
                style = MaterialTheme.typography.labelMedium,
                color = AgedGold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Second line: the two facts a storyteller re-derives constantly, and
        // the Mastermind day as a LINE, not a banner drawn over everything.
        val second = buildList {
            onBlockId?.let { id ->
                val votes = state.nominations.lastOrNull { it.day == state.cycle && it.nomineeId == id }?.votes
                add("${state.player(id)?.name ?: "someone"} is on the block${votes?.let { " ($it votes)" } ?: ""}")
            }
            if (expiringAtDusk > 0) add("$expiringAtDusk token${if (expiringAtDusk == 1) "" else "s"} expire at dusk")
            if (state.mastermindDayActive) add("MASTERMIND DAY — whoever is executed, their team loses")
        }
        if (second.isNotEmpty()) {
            Text(
                second.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.mastermindDayActive) AgedGold else EmberRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = !board,
                    onClick = { onBoard(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Circle") }
                SegmentedButton(
                    selected = board,
                    onClick = { onBoard(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Board") }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Search", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
        // A chip that shows its count answers "who is poisoned?" before it is
        // even tapped (grimoire-and-seats §4).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((group, n) in counts) {
                FilterChip(
                    selected = filter == group.name,
                    onClick = { onFilter(group.name) },
                    label = { Text("${group.displayName} $n", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { StatusPip(group = group, size = 16.dp) },
                )
            }
            val dead = state.seats.count { !it.alive }
            if (dead > 0) {
                FilterChip(
                    selected = filter == FILTER_DEAD,
                    onClick = { onFilter(FILTER_DEAD) },
                    label = { Text("dead $dead", style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

private const val FILTER_DEAD = "__dead"

/**
 * The minimum touch target, and why the header's two chips must declare it.
 *
 * `+ bluffs` / `fabled +` are a line of 11 sp text: about 18 dp tall laid out.
 * Compose does not leave a target that small unhittable — it expands the
 * clickable's *touch* bounds towards 48 dp around the centre — but that
 * expansion is invisible to the layout, so the grown rect reached down into
 * the row below and overlapped the Search field's top-right corner by 40 %
 * (`ui.py audit`, reported by the harness author and again by every wave-2
 * agent). Two hit targets fighting over the same pixels means a tap near the
 * top of Search opens the Fabled sheet instead.
 *
 * Asking for the 48 dp in LAYOUT gives the chip the same target it was already
 * being granted, in the only place a `Column` can see it — so the search row
 * begins below the chip instead of underneath it. The row grows by ~30 dp,
 * which is the honest price of a control that was always claiming that space.
 */
private val HEADER_CHIP_HEIGHT = 48.dp

/** How many seats carry at least one token of each group. Drives the chips. */
private fun groupCounts(
    state: GameState,
    tokens: Map<Long, List<RenderedToken>>,
): List<Pair<EffectGroup, Int>> {
    val counts = LinkedHashMap<EffectGroup, Int>()
    for (p in state.seats) {
        val live = tokens[p.id].orEmpty().filterNot { it.suspended || it.inert }
        for (group in live.map { it.group }.toSet()) {
            counts[group] = (counts[group] ?: 0) + 1
        }
    }
    return counts.entries.sortedBy { it.key.priority }.map { it.key to it.value }
}

/** Filter + search predicate, shared by both views so highlighting matches. */
private fun seatMatches(
    player: Player,
    tokens: List<RenderedToken>,
    character: Character?,
    filter: String?,
    search: String,
): Boolean {
    if (filter == FILTER_DEAD && player.alive) return false
    if (filter != null && filter != FILTER_DEAD &&
        tokens.none { !it.suspended && !it.inert && it.group.name == filter }
    ) {
        return false
    }
    val q = search.trim()
    if (q.isEmpty()) return true
    return player.name.contains(q, ignoreCase = true) ||
        (character?.name?.contains(q, ignoreCase = true) == true) ||
        tokens.any { it.label.contains(q, ignoreCase = true) }
}

// ---------------------------------------------------------------------------
// Circle view
// ---------------------------------------------------------------------------

@Composable
private fun CircleView(
    viewModel: GameViewModel,
    state: GameState,
    tokens: Map<Long, List<RenderedToken>>,
    onBlockId: Long?,
    matches: Set<Long>,
    filtering: Boolean,
    onOpenSeat: (Long) -> Unit,
    onPeek: (Long) -> Unit,
) {
    val zoom = rememberZoomState()

    // Wake-order badges. `Player.nightRoleId` is deleted (lead D39): the seats
    // that act tonight come from Identity.actingRoles, which is the only thing
    // that knows about a Philosopher's borrowed ability or a Hermit's many.
    val wakeOrder: Map<Long, Int> = remember(state.players, state.phase, state.cycle, state.fabled) {
        if (state.phase != Phase.NIGHT) {
            emptyMap()
        } else {
            // WP2 redirect: NightPlan.build replaces the deleted NightOrder.
            val steps = NightPlan.build(state, viewModel::characterById).steps
            val acting = steps.filter { it.slotId !in NightMarkers.all && it.wakes.isNotEmpty() }
            val slotIndex = acting.mapIndexed { index, step -> step.slotId to index + 1 }.toMap()
            val out = LinkedHashMap<Long, Int>()
            for (p in state.players) {
                val roles = Identity.actingRoles(state, viewModel::characterById, p)
                val n = roles.mapNotNull { slotIndex[it.slotId] ?: slotIndex[it.abilityId] }.minOrNull()
                    ?: acting.firstOrNull { p.id in it.wakes }?.let { slotIndex[it.slotId] }
                if (n != null) out[p.id] = n
            }
            out
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Twilight, NightSky),
                        center = center,
                        radius = kotlin.math.max(size.width, size.height) / 1.4f,
                    ),
                )
            }
            .zoomGestures(zoom),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 12.dp)
                .zoomTransform(zoom),
        ) {
            // The allocation is computed HERE, at composition time, so the
            // seat cards can size themselves to it and the Layout below can
            // measure them against exactly the same budget.
            val alloc = remember(state.players.size, maxWidth, maxHeight) {
                SeatGeometry.allocate(state.players.size, maxWidth.value, maxHeight.value)
            }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val rx = alloc.radiusXDp * density
                    val ry = alloc.radiusYDp * density
                    if (rx > 0 && ry > 0) {
                        drawOval(
                            color = AgedGold.copy(alpha = 0.12f),
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - rx, center.y - ry),
                            size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                },
            )
            CircleLayout(allocation = alloc, modifier = Modifier.fillMaxSize()) {
                for ((index, player) in state.players.withIndex()) {
                    SeatView(
                        viewModel = viewModel,
                        state = state,
                        player = player,
                        seatNumber = index + 1,
                        tokens = tokens[player.id].orEmpty(),
                        alloc = alloc,
                        onBlock = player.id == onBlockId,
                        dimmed = filtering && player.id !in matches,
                        wakeNumber = wakeOrder[player.id],
                        onClick = { onOpenSeat(player.id) },
                        onLongClick = { onPeek(player.id) },
                    )
                }
            }
        }

        ZoomControls(
            state = zoom,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

/**
 * Lays children out evenly around an ellipse inscribed in the available
 * space, first child at 12 o'clock, proceeding clockwise.
 *
 * Passing an [allocation] measures each seat against [SeatGeometry.allocate]'s
 * budget, so a seat card can never be taller than the gap to its neighbour.
 * The player-notes circle passes none and keeps the legacy `childMax * 2`
 * budget.
 */
@Composable
fun CircleLayout(
    modifier: Modifier = Modifier,
    allocation: SeatAllocation? = null,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val count = measurables.size
        if (count == 0) {
            return@Layout layout(width, height) {}
        }

        val childConstraints = if (allocation != null) {
            Constraints(
                maxWidth = (allocation.budgetWDp * density).toInt().coerceAtLeast(1),
                maxHeight = (allocation.budgetHDp * density).toInt().coerceAtLeast(1),
            )
        } else {
            val childMax = SeatGeometry.childMax(count, width, height)
            Constraints(maxWidth = childMax, maxHeight = childMax * 2)
        }
        val placeables = measurables.map { it.measure(childConstraints) }

        val legacyInset = SeatGeometry.childMax(count, width, height) / 2f + 8.dp.toPx()
        val radiusX = allocation?.let { it.radiusXDp * density } ?: (width / 2f - legacyInset)
        val radiusY = allocation?.let { it.radiusYDp * density } ?: (height / 2f - legacyInset)
        val angles = SeatGeometry.equalArcAngles(count, radiusX, radiusY)

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val angle = angles[index]
                val cx = width / 2f + radiusX * cos(angle).toFloat()
                val cy = height / 2f + radiusY * sin(angle).toFloat()
                placeable.place(
                    x = (cx - placeable.width / 2f).toInt(),
                    y = (cy - placeable.height / 2f).toInt(),
                )
            }
        }
    }
}

/**
 * What one seat card is allowed to be, in dp. Every field is derived from the
 * SPACING between neighbours, which is what makes overlap impossible.
 *
 * Pure data, pure arithmetic, no Compose types — `tools/uicheck`'s test source
 * set measures this directly (grimoire-and-seats "Tests to add" 1-3).
 */
data class SeatAllocation(
    val count: Int,
    val radiusXDp: Float,
    val radiusYDp: Float,
    /** Distance along the ellipse between two neighbouring seats. */
    val spacingDp: Float,
    /** The tallest a seat card may be. Always <= [spacingDp] * 0.96. */
    val budgetHDp: Float,
    val budgetWDp: Float,
    val tokenDp: Float,
    val nameDp: Float,
    val pipRowDp: Float,
    val characterNameDp: Float,
    val pipDp: Float,
    val pips: Int,
    val showCharacterName: Boolean,
    /**
     * True when the pips are drawn ON the token instead of in their own row.
     * Above ~17 seats the 40 dp token floor plus a pip row no longer fits the
     * gap; overlaying keeps every pip visible rather than letting the row be
     * measured to zero and vanish (grimoire-and-seats P0-2).
     */
    val pipsOverlayToken: Boolean,
) {
    /** The height the seat card actually occupies. Never exceeds [budgetHDp]. */
    val cardHeightDp: Float
        get() = nameDp + tokenDp + pipRowDp + SeatGeometry.GAP_DP +
            (if (showCharacterName) characterNameDp else 0f)

    /** The width the seat card actually occupies. Never exceeds [budgetWDp]. */
    val cardWidthDp: Float get() = maxOf(tokenDp, pipDp * pips + 2f * (pips - 1).coerceAtLeast(0))
}

/**
 * Shared seat-ring geometry.
 *
 * [allocate] replaces the old fixed-divisor `childMax`, which sized the seat
 * box from the screen and never looked at how much room a seat actually had:
 * from 12 players up a card with a reminder row was TALLER than the gap to its
 * neighbour, and at 13-16 seats a dead player's reminder row was measured to
 * zero and silently disappeared.
 */
object SeatGeometry {

    /** Player name line. */
    const val NAME_DP: Float = 16f

    /** Status pip row. */
    const val PIP_ROW_DP: Float = 18f

    /** Character name line, when it fits. */
    const val CHAR_NAME_DP: Float = 14f

    /** Breathing room inside the card. */
    const val GAP_DP: Float = 4f

    /** Token art floor and cap. Below the floor the art is not recognisable. */
    const val TOKEN_MIN_DP: Float = 40f
    const val TOKEN_MAX_DP: Float = 96f

    /**
     * The absolute floor, used only when [TOKEN_MIN_DP] genuinely will not fit
     * — 19 seats on a 320 x 560 dp screen, say. A small token is bad; a token
     * measured to zero and silently clipped is the bug WP10 exists to kill, so
     * the card always shrinks rather than overflowing.
     */
    const val TOKEN_HARD_MIN_DP: Float = 28f

    /** Legacy fixed-divisor seat box. Still used by the player-notes circle. */
    fun childMax(count: Int, width: Int, height: Int): Int = when {
        count <= 8 -> (min(width, height) / 3.5f).toInt()
        count <= 12 -> (min(width, height) / 4.4f).toInt()
        else -> (min(width, height) / 5.4f).toInt()
    }

    private fun childMaxDp(count: Int, widthDp: Float, heightDp: Float): Float = when {
        count <= 8 -> min(widthDp, heightDp) / 3.5f
        count <= 12 -> min(widthDp, heightDp) / 4.4f
        else -> min(widthDp, heightDp) / 5.4f
    }

    /** Ramanujan's approximation — exact enough to the nearest tenth of a dp. */
    fun ellipsePerimeter(rx: Float, ry: Float): Float {
        if (rx <= 0f || ry <= 0f) return 0f
        val a = maxOf(rx, ry).toDouble()
        val b = minOf(rx, ry).toDouble()
        val h = ((a - b) * (a - b)) / ((a + b) * (a + b))
        return (PI * (a + b) * (1 + 3 * h / (10 + sqrt(4 - 3 * h)))).toFloat()
    }

    /**
     * The spacing-driven seat allocator.
     *
     * ```
     * spacing   = perimeter(rx, ry) / n
     * budgetH   = min(spacing * 0.96, min(w, h) / 2.2)   // never overlap
     * budgetW   = min(childMax(w), spacing * 1.7)        // flanks are vertical
     * token     = (budgetH - name - pipRow - gap).coerceIn(40, 96)
     * showName  = budgetH - (name + token + pipRow + gap) >= 14
     * ```
     *
     * When the token floor plus a pip row no longer fits `budgetH`, the pips
     * move ON TO the token and the card shrinks to fit rather than overflowing.
     */
    fun allocate(count: Int, widthDp: Float, heightDp: Float): SeatAllocation {
        val n = count.coerceAtLeast(1)
        val provisional = childMaxDp(n, widthDp, heightDp)
        val inset = provisional / 2f + 8f
        val rx = (widthDp / 2f - inset).coerceAtLeast(1f)
        val ry = (heightDp / 2f - inset).coerceAtLeast(1f)
        val spacing = ellipsePerimeter(rx, ry) / n
        val budgetH = min(spacing * 0.96f, min(widthDp, heightDp) / 2.2f).coerceAtLeast(NAME_DP + TOKEN_MIN_DP)
        val budgetW = min(provisional, spacing * 1.7f).coerceAtLeast(TOKEN_MIN_DP)

        var pipRow = PIP_ROW_DP
        var token = (budgetH - NAME_DP - pipRow - GAP_DP).coerceIn(TOKEN_MIN_DP, TOKEN_MAX_DP)
        var overlay = false
        if (NAME_DP + token + pipRow + GAP_DP > budgetH) {
            // The pip row is what used to get squeezed to zero. Give it the
            // token's own surface instead of dropping it.
            overlay = true
            pipRow = 0f
            token = (budgetH - NAME_DP - GAP_DP).coerceIn(TOKEN_HARD_MIN_DP, TOKEN_MAX_DP)
        }
        token = min(token, budgetW - 2f).coerceAtLeast(TOKEN_HARD_MIN_DP)
        val slack = budgetH - (NAME_DP + token + pipRow + GAP_DP)
        val showName = slack >= CHAR_NAME_DP
        val pipDp = if (overlay) 16f else PIP_ROW_DP
        // Pips must not run wider than the card: 2 dp of gap between each.
        val pipBudget = ((budgetW + 2f) / (pipDp + 2f)).toInt().coerceIn(1, if (n <= 12) 5 else 4)

        return SeatAllocation(
            count = n,
            radiusXDp = rx,
            radiusYDp = ry,
            spacingDp = spacing,
            budgetHDp = budgetH,
            budgetWDp = budgetW,
            tokenDp = token,
            nameDp = NAME_DP,
            pipRowDp = pipRow,
            characterNameDp = CHAR_NAME_DP,
            pipDp = pipDp,
            pips = pipBudget,
            showCharacterName = showName,
            pipsOverlayToken = overlay,
        )
    }

    /**
     * [count] angles starting at 12 o'clock, clockwise, spaced so the
     * distance travelled ALONG the ellipse between neighbours is equal.
     */
    fun equalArcAngles(count: Int, radiusX: Float, radiusY: Float): List<Double> {
        if (count <= 0) return emptyList()
        val samples = 1440
        val step = 2 * PI / samples
        val cumulative = DoubleArray(samples + 1)
        for (i in 1..samples) {
            val t = -PI / 2 + step * (i - 0.5)
            val dx = -radiusX * kotlin.math.sin(t)
            val dy = radiusY * kotlin.math.cos(t)
            cumulative[i] = cumulative[i - 1] + sqrt(dx * dx + dy * dy) * step
        }
        val total = cumulative[samples]
        val angles = ArrayList<Double>(count)
        var cursor = 0
        for (k in 0 until count) {
            val target = total * k / count
            while (cursor < samples && cumulative[cursor + 1] < target) cursor++
            angles.add(-PI / 2 + step * cursor)
        }
        return angles
    }
}

/** One seat on the circle: name, token, shroud, status pips. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatView(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    seatNumber: Int,
    tokens: List<RenderedToken>,
    alloc: SeatAllocation,
    onBlock: Boolean,
    dimmed: Boolean,
    wakeNumber: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val character = viewModel.characterById(player.characterId)
    val isEvil = player.isEvil(viewModel::characterById)
    val pips = visiblePips(tokens.map { it.group }, alloc.pips)
    val alpha = if (dimmed) 0.28f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .wrapContentSize()
            // NO .clip(): a clipped Column is how tokens used to be cut off
            // without so much as a "+N". The allocator guarantees the fit.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = seatDescription(state, viewModel, player, seatNumber, character, tokens)
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Show every token on this seat",
            ),
    ) {
        Text(
            text = player.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                !player.alive -> MaterialTheme.colorScheme.onSurfaceVariant
                isEvil && character != null -> EmberRed
                else -> MaterialTheme.colorScheme.onBackground
            }.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = alloc.budgetWDp.dp),
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = if (onBlock) {
                    Modifier.border(2.dp, OnBlockGold, CircleShape).padding(2.dp)
                } else {
                    Modifier
                },
            ) {
                CharacterToken(
                    character = character,
                    size = alloc.tokenDp.dp,
                    dimmed = !player.alive || dimmed,
                )
            }
            if (!player.alive) {
                val shroudW = (alloc.tokenDp * 0.46f).dp
                val shroudH = (alloc.tokenDp * 0.62f).dp
                Box(
                    modifier = Modifier
                        .size(width = shroudW, height = shroudH)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(bottomStart = shroudW / 2, bottomEnd = shroudW / 2))
                        .background(ShroudBlack)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(bottomStart = shroudW / 2, bottomEnd = shroudW / 2),
                        ),
                )
            }
            if (player.isTraveller) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AgedGold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("T", fontSize = MIN_TEXT_SP.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            if (wakeNumber != null) {
                // Shown for dead seats too: several characters act while dead.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2040)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$wakeNumber", fontSize = MIN_TEXT_SP.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                }
            }
            if (alloc.pipsOverlayToken) {
                PipStrip(
                    viewModel = viewModel,
                    tokens = tokens,
                    pips = pips,
                    pipDp = alloc.pipDp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (!alloc.pipsOverlayToken) {
            PipStrip(
                viewModel = viewModel,
                tokens = tokens,
                pips = pips,
                pipDp = alloc.pipDp,
                modifier = Modifier.height(alloc.pipRowDp.dp),
            )
        }
        if (alloc.showCharacterName && character != null) {
            Text(
                text = character.name,
                fontSize = MIN_TEXT_SP.sp,
                lineHeight = (MIN_TEXT_SP + 2f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Parchment.copy(alpha = (if (player.alive) 0.95f else 0.5f) * alpha),
                modifier = Modifier.widthIn(max = alloc.budgetWDp.dp),
            )
        }
    }
}

/** Status pips in PRIORITY order, with a tappable overflow count. */
@Composable
private fun PipStrip(
    viewModel: GameViewModel,
    tokens: List<RenderedToken>,
    pips: PipRow,
    pipDp: Float,
    modifier: Modifier = Modifier,
) {
    if (pips.shown.isEmpty() && pips.hidden == 0) return
    val byGroup = tokens.groupBy { it.group }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val used = LinkedHashMap<EffectGroup, Int>()
        for (group in pips.shown) {
            val i = used[group] ?: 0
            used[group] = i + 1
            val token = byGroup[group]?.getOrNull(i)
            StatusPip(
                group = group,
                size = pipDp.dp,
                ringColor = viewModel.characterById(token?.sourceId)?.team?.color ?: Color.Transparent,
                suspended = token?.suspended == true,
                derived = token?.derived == true,
                inert = token?.inert == true,
            )
        }
        if (pips.hidden > 0) {
            Text(
                "+${pips.hidden}",
                fontSize = MIN_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Screen-reader description: still richer than what a sighted user sees. */
private fun seatDescription(
    state: GameState,
    viewModel: GameViewModel,
    player: Player,
    seatNumber: Int,
    character: Character?,
    tokens: List<RenderedToken>,
): String = buildString {
    append("Seat $seatNumber, ")
    append(player.name)
    append(", ")
    append(character?.name ?: "no character assigned")
    player.shownCharacterId?.let { append(", shown as ${viewModel.characterById(it)?.name ?: it}") }
    append(if (player.alive) ", alive" else ", dead")
    if (character != null) append(if (player.isEvil(viewModel::characterById)) ", evil" else ", good")
    if (player.isTraveller) append(", traveller")
    if (!player.alive) append(if (player.ghostVoteUsed) ", ghost vote spent" else ", ghost vote available")
    if (Status.isImpaired(state, viewModel::characterById, player.id)) append(", drunk or poisoned")
    if (tokens.isNotEmpty()) {
        append(", tokens: ")
        append(
            tokens.joinToString {
                it.label + when {
                    it.suspended -> " (suspended)"
                    it.inert -> " (not in force)"
                    else -> ""
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Board view — for READING. Nothing here is ever truncated.
// ---------------------------------------------------------------------------

@Composable
private fun BoardView(
    viewModel: GameViewModel,
    state: GameState,
    tokens: Map<Long, List<RenderedToken>>,
    onBlockId: Long?,
    matches: Set<Long>,
    filtering: Boolean,
    onOpenSeat: (Long) -> Unit,
    onPeek: (Long) -> Unit,
) {
    val rows = if (filtering) state.players.filter { it.id in matches } else state.players
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { "board-${it.id}" }) { player ->
            val index = state.players.indexOfFirst { it.id == player.id } + 1
            BoardRow(
                viewModel = viewModel,
                state = state,
                player = player,
                seatNumber = index,
                tokens = tokens[player.id].orEmpty(),
                onBlock = player.id == onBlockId,
                onClick = { onOpenSeat(player.id) },
                onLongClick = { onPeek(player.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    "No seat matches that filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BoardRow(
    viewModel: GameViewModel,
    state: GameState,
    player: Player,
    seatNumber: Int,
    tokens: List<RenderedToken>,
    onBlock: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val character = viewModel.characterById(player.characterId)
    val isEvil = player.isEvil(viewModel::characterById)
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Show every token on this seat",
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$seatNumber",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            CharacterToken(character = character, size = 32.dp, dimmed = !player.alive)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isEvil && character != null) EmberRed else MaterialTheme.colorScheme.onSurface,
                    )
                    if (onBlock) {
                        Spacer(Modifier.width(6.dp))
                        Text("on the block", style = MaterialTheme.typography.labelMedium, color = OnBlockGold)
                    }
                }
                Text(
                    buildString {
                        append(character?.name ?: "No character")
                        character?.team?.let { append(" · ${it.displayName}") }
                        player.shownCharacterId?.let {
                            append(" · shown as ${viewModel.characterById(it)?.name ?: it}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (player.alive) "alive" else deathSummary(state, viewModel::characterById, player.id),
                style = MaterialTheme.typography.labelMedium,
                color = if (player.alive) MaterialTheme.colorScheme.onSurfaceVariant else EmberRed,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        // Every token, in full text, with its source and expiry — the whole
        // reason the Board exists.
        if (tokens.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 22.dp),
            ) {
                for (token in tokens) TokenLine(viewModel, token)
            }
        }
        player.notes.lastOrNull()?.let {
            Text(
                "note: \"${it.text}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp),
            )
        }
    }
}

/** One token as a full-text line: glyph, label, source, expiry. */
@Composable
fun TokenLine(viewModel: GameViewModel, token: RenderedToken, modifier: Modifier = Modifier) {
    val source = viewModel.characterById(token.sourceId)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        StatusPip(
            group = token.group,
            size = 16.dp,
            ringColor = source?.team?.color ?: Color.Transparent,
            suspended = token.suspended,
            derived = token.derived,
            inert = token.inert,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            buildString {
                append(token.label)
                if (token.suspended) append(" (turned over)")
                // Playtest D P2-12: the engine knew this token was doing
                // nothing; the grimoire drew it at full strength anyway.
                if (token.inert) append(" (not in force)")
                source?.let { append(" · ${it.name}") }
                if (token.expiryText.isNotEmpty()) append(" · ${token.expiryText}")
                if (token.derived) append(" · no physical token")
                if (token.note.isNotEmpty()) append(" — ${token.note}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = token.group.color,
        )
    }
}

// ---------------------------------------------------------------------------
// Token peek — the one-hand "move a token" gesture the physical grimoire has
// ---------------------------------------------------------------------------

@Composable
private fun TokenPeek(
    viewModel: GameViewModel,
    state: GameState,
    playerId: Long,
    tokens: List<RenderedToken>,
    onOpenSeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val player = state.player(playerId) ?: return
    var moving by remember { mutableStateOf<RenderedToken?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tokens on ${player.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                val target = moving
                if (target != null) {
                    Text("Move \"${target.label}\" to…", style = MaterialTheme.typography.titleSmall)
                    FlowRowSeats(state, exclude = playerId) { other ->
                        viewModel.moveToken(playerId, other, target)
                        moving = null
                    }
                } else if (tokens.isEmpty()) {
                    Text("No tokens on this seat.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Suspend turns a token over — the physical convention — so it stops " +
                            "counting without being lost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (token in tokens) {
                        Column {
                            TokenLine(viewModel, token)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // A derived token has no physical counterpart to
                                // turn over; everything else does (P1-5).
                                if (!token.derived) {
                                    TextButton(
                                        onClick = {
                                            viewModel.suspendRenderedToken(
                                                playerId,
                                                token,
                                                !token.suspended,
                                            )
                                        },
                                    ) {
                                        Text(if (token.suspended) "Restore" else "Suspend")
                                    }
                                }
                                TextButton(onClick = { moving = token }) { Text("Move") }
                                TextButton(onClick = { viewModel.removeRenderedToken(playerId, token) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenSeat) { Text("Open seat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSeats(state: GameState, exclude: Long, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in state.seats) {
            if (p.id == exclude) continue
            TextButton(onClick = { onPick(p.id) }) { Text(p.name) }
        }
    }
}
