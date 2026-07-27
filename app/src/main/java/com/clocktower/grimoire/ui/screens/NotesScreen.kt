package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clocktower.engine.Character
import com.clocktower.engine.LinkKind
import com.clocktower.engine.NoteSeat
import com.clocktower.engine.NotesActions
import com.clocktower.engine.NotesState
import com.clocktower.engine.Setup
import com.clocktower.engine.Team
import com.clocktower.engine.Trust
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.NightSky
import com.clocktower.grimoire.ui.theme.TownsfolkBlue
import com.clocktower.grimoire.ui.theme.Twilight
import com.clocktower.grimoire.ui.theme.color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Player-notes mode: the "empty grimoire". Seats show what each player
 * CLAIMS (not truth), ringed by how much you trust them, with relationship
 * lines — who accuses whom, who defends whom, who's on a team together.
 */

private val TrustColors = mapOf(
    Trust.UNKNOWN to Color(0x66FFFFFF),
    Trust.TRUSTED to TownsfolkBlue,
    Trust.SUSPICIOUS to Color(0xFFE0A33C),
    Trust.EVIL to Color(0xFFC93B3B),
)

private val LinkColors = mapOf(
    LinkKind.ACCUSES to EmberRed,
    LinkKind.DEFENDS to TownsfolkBlue,
    LinkKind.INFO to AgedGold,
    LinkKind.SAME_TEAM to Color(0xFFA46FD1),
    LinkKind.OPPOSITE_TEAM to Color(0xFFE0862C),
)

private fun LinkKind.label(): String = when (this) {
    LinkKind.ACCUSES -> "Accuses →"
    LinkKind.DEFENDS -> "Defends →"
    LinkKind.INFO -> "Info about →"
    LinkKind.SAME_TEAM -> "Same team"
    LinkKind.OPPOSITE_TEAM -> "Opposite team"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    viewModel: GameViewModel,
    state: NotesState,
    onExit: () -> Unit,
) {
    var openSeatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var linkKind by rememberSaveable { mutableStateOf<LinkKind?>(null) }
    var linkFirst by rememberSaveable { mutableStateOf<Long?>(null) }
    var linkLabel by rememberSaveable { mutableStateOf("") }
    var showMatrix by rememberSaveable { mutableStateOf(false) }
    var showTimeline by rememberSaveable { mutableStateOf(false) }
    var showMe by rememberSaveable { mutableStateOf(false) }
    val canUndo by viewModel.canUndoNotes.collectAsState()
    val canRedo by viewModel.canRedoNotes.collectAsState()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // ---- Top bar ----------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            TextButton(onClick = onExit) { Text("Home") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.script.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AgedGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { viewModel.updateNotes { NotesActions.setDay(it, it.day - 1) } },
                        enabled = state.day > 1,
                    ) { Text("−") }
                    Text("Day ${state.day}", style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = { viewModel.updateNotes { NotesActions.setDay(it, it.day + 1) } },
                    ) { Text("＋") }
                }
            }
            TextButton(onClick = { viewModel.undoNotes() }, enabled = canUndo) { Text("↶") }
            TextButton(onClick = { viewModel.redoNotes() }, enabled = canRedo) { Text("↷") }
        }

        // ---- The circle -------------------------------------------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    val w = this.size.width
                    val h = this.size.height
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Twilight, NightSky),
                            center = center,
                            radius = kotlin.math.max(w, h) / 1.4f,
                        ),
                    )
                    val count = state.seats.size
                    if (count == 0) return@drawBehind
                    val childMax = SeatGeometry.childMax(count, w.toInt(), h.toInt())
                    val inset = childMax / 2f + 8.dp.toPx()
                    val rx = w / 2f - inset
                    val ry = h / 2f - inset
                    if (rx <= 0 || ry <= 0) return@drawBehind
                    val angles = SeatGeometry.equalArcAngles(count, rx, ry)
                    val centers = angles.map { angle ->
                        Offset(
                            center.x + rx * cos(angle).toFloat(),
                            center.y + ry * sin(angle).toFloat(),
                        )
                    }
                    val indexById = state.seats.mapIndexed { i, s -> s.id to i }.toMap()
                    val gap = childMax * 0.42f
                    for (link in state.links) {
                        val fromIndex = indexById[link.fromSeatId] ?: continue
                        val toIndex = indexById[link.toSeatId] ?: continue
                        val from = centers[fromIndex]
                        val to = centers[toIndex]
                        val dir = to - from
                        val length = kotlin.math.hypot(dir.x, dir.y)
                        if (length < gap * 2.2f) continue
                        val unit = Offset(dir.x / length, dir.y / length)
                        val start = from + Offset(unit.x * gap, unit.y * gap)
                        val end = to - Offset(unit.x * gap, unit.y * gap)
                        val color = LinkColors[link.kind] ?: AgedGold
                        drawLine(
                            color = color.copy(alpha = 0.8f),
                            start = start,
                            end = end,
                            strokeWidth = 2.5.dp.toPx(),
                        )
                        if (link.kind.directed) {
                            val angle = atan2(unit.y, unit.x)
                            val head = 9.dp.toPx()
                            for (side in listOf(1f, -1f)) {
                                val a = angle + side * 2.7f
                                drawLine(
                                    color = color,
                                    start = end,
                                    end = end + Offset(cos(a) * head, sin(a) * head),
                                    strokeWidth = 2.5.dp.toPx(),
                                )
                            }
                        }
                    }
                },
        ) {
            CircleLayout(Modifier.fillMaxSize()) {
                for (seat in state.seats) {
                    NoteSeatView(
                        viewModel = viewModel,
                        seat = seat,
                        isMe = state.mySeatId == seat.id,
                        linkPickActive = linkKind != null,
                        linkFirstPick = linkFirst == seat.id,
                        onClick = {
                            val kind = linkKind
                            if (kind == null) {
                                openSeatId = seat.id
                            } else {
                                val first = linkFirst
                                if (first == null) {
                                    linkFirst = seat.id
                                } else if (first != seat.id) {
                                    viewModel.updateNotes {
                                        NotesActions.addLink(it, first, seat.id, kind, linkLabel.trim())
                                    }
                                    linkFirst = null
                                    linkLabel = ""
                                } else {
                                    linkFirst = null
                                }
                            }
                        },
                    )
                }
            }
            if (state.seats.isEmpty()) {
                Text(
                    "No seats yet — add some below.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (linkKind != null) {
                Text(
                    text = if (linkFirst == null) {
                        when (linkKind) {
                            LinkKind.SAME_TEAM, LinkKind.OPPOSITE_TEAM -> "Tap the first of the pair"
                            else -> "Tap who it comes FROM"
                        }
                    } else {
                        when (linkKind) {
                            LinkKind.SAME_TEAM, LinkKind.OPPOSITE_TEAM -> "Tap the second of the pair"
                            else -> "Now tap who it points AT"
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = LinkColors[linkKind] ?: AgedGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp),
                )
            }
        }

        // ---- Bottom tray ------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (kind in LinkKind.entries) {
                        FilterChip(
                            selected = linkKind == kind,
                            onClick = {
                                linkKind = if (linkKind == kind) null else kind
                                linkFirst = null
                            },
                            label = {
                                Text(kind.label(), color = LinkColors[kind] ?: Color.Unspecified)
                            },
                        )
                    }
                }
                if (linkKind != null) {
                    OutlinedTextField(
                        value = linkLabel,
                        onValueChange = { linkLabel = it },
                        placeholder = { Text("Optional note for the next line (\"says I'm the Imp\")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AssistChip(onClick = { showMatrix = true }, label = { Text("Claim matrix") })
                    AssistChip(onClick = { showTimeline = true }, label = { Text("Timeline") })
                    AssistChip(onClick = { showMe = true }, label = { Text("Me") })
                    AssistChip(
                        onClick = { viewModel.updateNotes { NotesActions.addSeat(it, "") } },
                        label = { Text("＋ Seat") },
                    )
                }
            }
        }
    }

    openSeatId?.let { seatId ->
        state.seat(seatId)?.let { seat ->
            NoteSeatSheet(
                viewModel = viewModel,
                state = state,
                seat = seat,
                onDismiss = { openSeatId = null },
            )
        } ?: run { openSeatId = null }
    }
    if (showMatrix) {
        ClaimMatrixSheet(viewModel, state, onDismiss = { showMatrix = false })
    }
    if (showTimeline) {
        TimelineSheet(viewModel, state, onDismiss = { showTimeline = false })
    }
    if (showMe) {
        MeSheet(viewModel, state, onDismiss = { showMe = false })
    }
}

/** One seat: claim token ringed by trust, shroud when dead, suspects. */
@Composable
private fun NoteSeatView(
    viewModel: GameViewModel,
    seat: NoteSeat,
    isMe: Boolean,
    linkPickActive: Boolean,
    linkFirstPick: Boolean,
    onClick: () -> Unit,
) {
    val claim = viewModel.notesCharacterById(seat.currentClaimId)
    val ring = when {
        linkFirstPick -> AgedGold
        else -> TrustColors[seat.trust] ?: Color.Gray
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Text(
            text = (if (isMe) "★ " else "") + seat.name + (if (!seat.alive) " †" else ""),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
            color = if (seat.alive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .border(
                    width = if (linkFirstPick || seat.trust != Trust.UNKNOWN) 3.dp else 1.5.dp,
                    color = ring,
                    shape = CircleShape,
                )
                .padding(3.dp),
        ) {
            CharacterToken(
                character = claim,
                size = 52.dp,
                dimmed = !seat.alive || (linkPickActive && !linkFirstPick),
            )
        }
        val subtitle = when {
            !seat.alive -> when (seat.executed) {
                true -> "exec d${seat.deathDay ?: "?"}"
                false -> "night d${seat.deathDay ?: "?"}"
                null -> "dead d${seat.deathDay ?: "?"}"
            }
            claim != null -> claim.name
            else -> "no claim"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (claim == null && seat.alive) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                AgedGold.copy(alpha = 0.9f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (seat.suspectIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (id in seat.suspectIds.take(3)) {
                    CharacterToken(character = viewModel.notesCharacterById(id), size = 18.dp)
                }
            }
        }
    }
}

/** Everything about one seat: claim, suspects, trust, life, links, note. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteSeatSheet(
    viewModel: GameViewModel,
    state: NotesState,
    seat: NoteSeat,
    onDismiss: () -> Unit,
) {
    var search by rememberSaveable(seat.id) { mutableStateOf("") }
    var pickMode by rememberSaveable(seat.id) { mutableStateOf("claim") } // claim | suspect
    val characters = remember(state.script, search) {
        val needle = search.trim()
        viewModel.gameData.resolve(state.script)
            .filter { it.team != Team.FABLED }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<Character> { it.team.ordinal }.thenBy { it.name })
    }
    val seatLinks = state.links.filter { it.fromSeatId == seat.id || it.toSeatId == seat.id }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterToken(character = viewModel.notesCharacterById(seat.currentClaimId), size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(seat.name, style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                        Text(
                            seat.currentClaimId?.let { "claims ${viewModel.notesCharacterById(it)?.name}" }
                                ?: "no claim yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.updateNotes { NotesActions.moveSeat(it, seat.id, -1) } }) { Text("◀") }
                    TextButton(onClick = { viewModel.updateNotes { NotesActions.moveSeat(it, seat.id, +1) } }) { Text("▶") }
                }
            }
            item {
                var name by rememberSaveable(seat.id) { mutableStateOf(seat.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.updateNotes { s -> NotesActions.renameSeat(s, seat.id, it) }
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Trust", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (trust in Trust.entries) {
                        FilterChip(
                            selected = seat.trust == trust,
                            onClick = { viewModel.updateNotes { NotesActions.setTrust(it, seat.id, trust) } },
                            label = {
                                Text(
                                    when (trust) {
                                        Trust.UNKNOWN -> "Unsure"
                                        Trust.TRUSTED -> "Trusted"
                                        Trust.SUSPICIOUS -> "Suspicious"
                                        Trust.EVIL -> "Evil"
                                    },
                                    color = TrustColors[trust]?.takeIf { trust != Trust.UNKNOWN } ?: Color.Unspecified,
                                )
                            },
                        )
                    }
                }
            }
            item {
                Text("Life", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (seat.alive) {
                        AssistChip(
                            onClick = { viewModel.updateNotes { NotesActions.markDead(it, seat.id, executed = true) } },
                            label = { Text("Executed") },
                        )
                        AssistChip(
                            onClick = { viewModel.updateNotes { NotesActions.markDead(it, seat.id, executed = false) } },
                            label = { Text("Died at night") },
                        )
                        AssistChip(
                            onClick = { viewModel.updateNotes { NotesActions.markDead(it, seat.id, executed = null) } },
                            label = { Text("Died (other)") },
                        )
                    } else {
                        Text(
                            when (seat.executed) {
                                true -> "Executed day ${seat.deathDay ?: "?"}"
                                false -> "Died in the night of day ${seat.deathDay ?: "?"}"
                                null -> "Died day ${seat.deathDay ?: "?"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberRed,
                        )
                        AssistChip(
                            onClick = { viewModel.updateNotes { NotesActions.revive(it, seat.id) } },
                            label = { Text("Alive again") },
                        )
                        FilterChip(
                            selected = seat.ghostVoteUsed,
                            onClick = { viewModel.updateNotes { NotesActions.toggleGhostVote(it, seat.id) } },
                            label = { Text(if (seat.ghostVoteUsed) "Ghost vote spent" else "Ghost vote available") },
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Characters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = pickMode == "claim",
                        onClick = { pickMode = "claim" },
                        label = { Text("Set claim") },
                    )
                    FilterChip(
                        selected = pickMode == "suspect",
                        onClick = { pickMode = "suspect" },
                        label = { Text("Mark suspect") },
                    )
                }
                Text(
                    if (pickMode == "claim") {
                        "Tap what ${seat.name} claims to be. Claims stack up as history."
                    } else {
                        "Tap the characters YOU think ${seat.name} might really be."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Find a character…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (character in characters) {
                        val isClaim = seat.currentClaimId == character.id
                        val isSuspect = character.id in seat.suspectIds
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.updateNotes { s ->
                                        if (pickMode == "claim") {
                                            NotesActions.setClaim(s, seat.id, character.id)
                                        } else {
                                            val next = if (isSuspect) {
                                                seat.suspectIds - character.id
                                            } else {
                                                seat.suspectIds + character.id
                                            }
                                            NotesActions.setSuspects(s, seat.id, next)
                                        }
                                    }
                                }
                                .border(
                                    width = if (isClaim || isSuspect) 2.dp else 0.dp,
                                    color = when {
                                        isClaim -> AgedGold
                                        isSuspect -> EmberRed
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            CharacterToken(character = character, size = 46.dp)
                            Text(
                                character.name,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (seat.claims.size > 1) {
                item {
                    Text("Claim history", style = MaterialTheme.typography.titleSmall)
                    for ((index, claim) in seat.claims.withIndex()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "d${claim.day} · ${viewModel.notesCharacterById(claim.characterId)?.name ?: claim.characterId}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.updateNotes { NotesActions.removeClaim(it, seat.id, index) }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
            if (seatLinks.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text("Lines", style = MaterialTheme.typography.titleSmall)
                    for (link in seatLinks) {
                        val from = state.seat(link.fromSeatId)?.name ?: "?"
                        val to = state.seat(link.toSeatId)?.name ?: "?"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    append("d${link.day} · $from ${link.kind.label().removeSuffix(" →")} $to")
                                    if (link.label.isNotBlank()) append(" — ${link.label}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = LinkColors[link.kind] ?: Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.updateNotes { NotesActions.removeLink(it, link.id) }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                var note by rememberSaveable(seat.id) { mutableStateOf(seat.note) }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        viewModel.updateNotes { s -> NotesActions.setNote(s, seat.id, it) }
                    },
                    label = { Text("Notes on ${seat.name}") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.updateNotes { NotesActions.removeSeat(it, seat.id) }
                        onDismiss()
                    }) { Text("Remove seat", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/**
 * The claim matrix: every script character with who claims it. Double
 * claims mean someone is lying; unclaimed good characters are likely
 * demon bluffs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimMatrixSheet(
    viewModel: GameViewModel,
    state: NotesState,
    onDismiss: () -> Unit,
) {
    val claimants = NotesActions.claimants(state)
    val characters = viewModel.gameData.resolve(state.script)
        .filter { it.team in setOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON) }
    val distribution = Setup.distributionFor(state.seats.size)
    val claimedOutsiders = state.seats.count { seat ->
        seat.currentClaimId?.let { viewModel.notesCharacterById(it)?.team } == Team.OUTSIDER
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("Claim matrix", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Base setup for ${state.seats.size} players: ${distribution.townsfolk} townsfolk · " +
                        "${distribution.outsiders} outsiders · ${distribution.minions} minions · " +
                        "${distribution.demons} demon. $claimedOutsiders outsider claim" +
                        (if (claimedOutsiders == 1) "" else "s") + " so far" +
                        if (claimedOutsiders > distribution.outsiders) {
                            " — MORE than base setup: a Baron-style modifier or a liar."
                        } else {
                            "."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (claimedOutsiders > distribution.outsiders) EmberRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)) {
                val teamCharacters = characters.filter { it.team == team }
                if (teamCharacters.isEmpty()) continue
                item {
                    Text(
                        team.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = team.color,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                for (character in teamCharacters) {
                    item {
                        val who = claimants[character.id].orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CharacterToken(character = character, size = 34.dp, dimmed = who.isEmpty())
                            Text(
                                character.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(110.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    who.isEmpty() ->
                                        if (team == Team.TOWNSFOLK || team == Team.OUTSIDER) "unclaimed — bluff?" else "—"
                                    who.size == 1 -> who.first().name
                                    else -> who.joinToString { it.name } + "  ⚔ DOUBLE CLAIM"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = if (who.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                                color = when {
                                    who.size > 1 -> EmberRed
                                    who.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Everything recorded, day by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSheet(
    viewModel: GameViewModel,
    state: NotesState,
    onDismiss: () -> Unit,
) {
    val events = NotesActions.timeline(state, viewModel::notesCharacterById)
    var newInfo by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("Timeline", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newInfo,
                        onValueChange = { newInfo = it },
                        placeholder = { Text("My info (\"Empath said 1\")…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        enabled = newInfo.isNotBlank(),
                        onClick = {
                            viewModel.updateNotes { NotesActions.addInfo(it, newInfo.trim()) }
                            newInfo = ""
                        },
                    ) { Text("Add") }
                }
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        "Nothing recorded yet. Claims, deaths, lines and your info all land here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val byDay = events.groupBy { it.first }
            for ((day, dayEvents) in byDay) {
                item {
                    Text(
                        "Day $day",
                        style = MaterialTheme.typography.titleSmall,
                        color = AgedGold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                for (event in dayEvents) {
                    item {
                        Text("· ${event.second}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** My seat, my real character (hidden until held), my private info. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MeSheet(
    viewModel: GameViewModel,
    state: NotesState,
    onDismiss: () -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val characters = remember(state.script, search) {
        val needle = search.trim()
        viewModel.gameData.resolve(state.script)
            .filter { it.team != Team.FABLED }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<Character> { it.team.ordinal }.thenBy { it.name })
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Me", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Marked with ★ on the circle. Your real character stays hidden until you tap it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text("Which seat is you?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (seat in state.seats) {
                        FilterChip(
                            selected = state.mySeatId == seat.id,
                            onClick = {
                                viewModel.updateNotes {
                                    NotesActions.setMySeat(it, if (state.mySeatId == seat.id) null else seat.id)
                                }
                            },
                            label = { Text(seat.name) },
                        )
                    }
                }
            }
            item {
                Text("My real character", style = MaterialTheme.typography.titleSmall)
                if (state.myCharacterId == null) {
                    Text(
                        "Not set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { revealed = !revealed }
                            .padding(6.dp),
                    ) {
                        if (revealed) {
                            val me = viewModel.notesCharacterById(state.myCharacterId)
                            CharacterToken(character = me, size = 44.dp)
                            Text(me?.name ?: "?", style = MaterialTheme.typography.titleMedium, color = AgedGold)
                            TextButton(onClick = {
                                viewModel.updateNotes { NotesActions.setMyCharacter(it, null) }
                            }) { Text("Clear") }
                        } else {
                            Text(
                                "🂠  Hidden — tap to reveal",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Set my character…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (character in characters) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.updateNotes { NotesActions.setMyCharacter(it, character.id) }
                                    revealed = false
                                }
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            CharacterToken(character = character, size = 42.dp)
                            Text(
                                character.name,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
