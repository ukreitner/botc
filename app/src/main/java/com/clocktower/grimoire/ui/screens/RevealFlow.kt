package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.engine.Alignment as SeatAlignment
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.Identity
import com.clocktower.engine.Player
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.SetupRequirements
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.overlaySafeAreaPadding
import com.clocktower.grimoire.ui.components.rememberDialogInsets
import com.clocktower.grimoire.ui.platform.KeepScreenOn
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.TownsfolkBlue
import com.clocktower.grimoire.ui.theme.Twilight
import com.clocktower.grimoire.ui.theme.color
import kotlin.random.Random
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Characters who are never told their own alignment, so the hand-out must not
 * offer an alignment page for them at all — the Ogre "becomes their alignment
 * (you don't know which)".
 *
 * **Filed to WP7:** this belongs in the per-character registry as a
 * `learnsOwnAlignment = false` row. Until that lands it is one named constant
 * here, never a `when` scattered through the screen.
 */
private val NEVER_TOLD_ALIGNMENT = setOf("ogre")

/** How long a finger must stay down before a token is shown. */
private const val HOLD_MILLIS = 700L

/**
 * The requirement kinds that decide what a seat's own card SAYS.
 *
 * [RequirementKind.SHOWN_TOKEN] is the character printed on it — the Drunk's
 * Chambermaid, the Lunatic's Demon, the Marionette's good token — and
 * [RequirementKind.ALIGNMENT] is the second page's GOOD/EVIL. Every other kind
 * describes something the storyteller does at the table (place a token, point
 * at players, choose bluffs) and changes nothing the card prints, so it never
 * holds a hand-over up.
 */
private val CARD_DECIDING_KINDS = setOf(
    RequirementKind.SHOWN_TOKEN,
    RequirementKind.ALIGNMENT,
)

/**
 * Seats whose card would LIE if it were handed over now, and the rows that must
 * be answered first.
 *
 * Playtest A-1: the Drunk was handed a card reading "YOU ARE / Drunk" because
 * the hand-out queued every seat with a `characterId` and never asked whether
 * the seat's *believed* character had been chosen yet. The very same screen was
 * already printing "The Drunk believes — … Which Townsfolk token do they see?"
 * as outstanding. This is the connection that was missing: one seat-scoped row
 * ([SetupRequirement.seatId]), one gate.
 *
 * Pure, so `tools/uicheck` can assert exactly which seats are unrevealable.
 */
internal fun handOutBlockers(
    state: GameState,
    lookup: (String) -> Character?,
): Map<Long, List<SetupRequirement>> =
    SetupRequirements.unmet(state, lookup)
        .filter { it.blocking && it.kind in CARD_DECIDING_KINDS && it.seatId != null }
        .groupBy { it.seatId!! }

/**
 * "Pass the phone" hand-out mode (setup-and-home §S6), replacing the old
 * tap-through reveal.
 *
 * Kept as a `Dialog` wrapper so `GameShell`'s overflow entry is unchanged;
 * [HandOutMode] is the same screen as a first-class destination, which the
 * setup screen lands on straight after dealing.
 */
@Composable
fun RevealFlow(
    viewModel: GameViewModel,
    state: GameState,
    onDone: () -> Unit,
    /** Restrict the pass to these seats (a paired hand-over, one re-show). */
    seats: List<Long>? = null,
) {
    // MEASURED HERE, outside the dialog. Inside the dialog's own window Compose
    // reports no insets at all, so both `overlaySafeAreaPadding()` and
    // `safeDrawingPadding()` resolved to zero and the whole column was laid out
    // against the full 2400 px — "Start over" and "Finish later" were drawn
    // past the bottom of the screen and the storyteller had no way out of
    // hand-out mode but the hardware Back key (playtest A-2).
    val insets = rememberDialogInsets()
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().overlaySafeAreaPadding(insets)) {
                HandOutMode(
                    viewModel = viewModel,
                    state = state,
                    onDone = onDone,
                    seats = seats,
                    // The box above already applied them; asking again inside
                    // the dialog adds nothing today and would double up the day
                    // a platform starts reporting insets there.
                    applyOwnInsets = false,
                )
            }
        }
    }
}

/**
 * The hand-out roster: who still needs their token, who has had it, and one
 * press-and-hold card per seat.
 *
 * Progress is STATE (`Player.tokenShownAt`), not local composition, so closing
 * and reopening resumes, and a character change (Pit-Hag, star pass, Huntsman)
 * automatically puts that seat back in the queue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandOutMode(
    viewModel: GameViewModel,
    state: GameState,
    onDone: () -> Unit,
    seats: List<Long>? = null,
    /**
     * False when a caller has already applied the safe area for us — the
     * `Dialog` in [RevealFlow], whose window reports none of its own.
     */
    applyOwnInsets: Boolean = true,
) {
    KeepScreenOn()
    var seatOrder by rememberSaveable { mutableStateOf(false) }
    var openSeatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showChecklist by rememberSaveable { mutableStateOf(false) }

    val queue = remember(state.players, seats, seatOrder) {
        val pool = seats?.mapNotNull { state.player(it) }
            ?: state.players.filter { it.characterId != null }
        if (seatOrder) {
            pool
        } else {
            // A fixed shuffle per game: everyone can see who is handed the
            // phone, so seat order is a tell (setup-and-home #25).
            pool.shuffled(Random(state.handOutSeed))
        }
    }
    // A-1: a seat whose believed character (or a Traveller's side) is still an
    // open question is NOT dealt — not by "Next:", not by tapping its name.
    val blockers = remember(state) { handOutBlockers(state, viewModel::characterById) }
    val blocked = queue.filter { blockers.containsKey(it.id) }
    val done = queue.count { it.tokenShownAt != null }
    val next = queue.firstOrNull { it.tokenShownAt == null && !blockers.containsKey(it.id) }

    val openSeat = openSeatId?.let { state.player(it) }
    if (openSeatId != null && openSeat == null) openSeatId = null

    Box(Modifier.fillMaxSize()) {
        when {
            openSeat != null && blockers.containsKey(openSeat.id) -> BlockedSeatCard(
                player = openSeat,
                rows = blockers.getValue(openSeat.id),
                onAnswer = { showChecklist = true },
                onBack = { openSeatId = null },
            )

            openSeat != null -> HandOutCard(
                viewModel = viewModel,
                player = openSeat,
                position = queue.indexOfFirst { it.id == openSeat.id } + 1,
                total = queue.size,
                onFinished = {
                    viewModel.markTokenHandedOut(openSeat.id)
                    openSeatId = null
                },
                onLater = { openSeatId = null },
            )

            else -> HandOutRoster(
                viewModel = viewModel,
                state = state,
                queue = queue,
                blockers = blockers,
                blocked = blocked,
                done = done,
                next = next,
                seatOrder = seatOrder,
                onSeatOrder = { seatOrder = it },
                onOpenSeat = { openSeatId = it },
                onChecklist = { showChecklist = true },
                onDone = onDone,
                applyOwnInsets = applyOwnInsets,
            )
        }
        if (showChecklist) {
            // The one-tap jump A-1 asks for: the outstanding row is answered
            // here and the seat is immediately dealable again.
            SetupChecklistSheet(
                viewModel = viewModel,
                state = state,
                onDismiss = { showChecklist = false },
            )
        }
    }
}

/** The roster page of [HandOutMode]: who is done, who is next, what is blocking. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandOutRoster(
    viewModel: GameViewModel,
    state: GameState,
    queue: List<Player>,
    blockers: Map<Long, List<SetupRequirement>>,
    blocked: List<Player>,
    done: Int,
    next: Player?,
    seatOrder: Boolean,
    onSeatOrder: (Boolean) -> Unit,
    onOpenSeat: (Long) -> Unit,
    onChecklist: () -> Unit,
    onDone: () -> Unit,
    applyOwnInsets: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (applyOwnInsets) Modifier.safeDrawingPadding() else Modifier)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    "HAND OUT TOKENS",
                    style = MaterialTheme.typography.titleMedium,
                    color = AgedGold,
                )
                Text(
                    "$done / ${queue.size} — pass the phone; each player holds to reveal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Parchment,
                )
            }
            TextButton(onClick = { onSeatOrder(!seatOrder) }) {
                Text(if (seatOrder) "Shuffled" else "Seat order", color = AgedGold)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // A-1: the block, stated before the roster and answerable in one tap.
        if (blocked.isNotEmpty()) {
            BlockedBanner(seats = blocked, blockers = blockers, onChecklist = onChecklist)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (seat in queue) {
                    val shown = seat.tokenShownAt != null
                    val isNext = seat.id == next?.id
                    val isBlocked = blockers.containsKey(seat.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(if (isNext) Twilight else Color.Transparent)
                            .clickable { onOpenSeat(seat.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            when {
                                isBlocked -> "!"
                                shown -> "✓"
                                isNext -> "▶"
                                else -> "○"
                            },
                            color = when {
                                isBlocked -> EmberRed
                                shown -> AgedGold
                                else -> Parchment
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            seat.name,
                            color = when {
                                isBlocked -> EmberRed
                                shown -> AgedGold.copy(alpha = 0.7f)
                                else -> Parchment
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Text(
                "Tap any name to (re)show that seat only.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Paired hand-overs and everything else the first night still owes,
            // read straight off the declarative checklist — no character ids.
            val pending = remember(state) {
                SetupRequirements.unmet(state, viewModel::characterById)
                    .filter { it.kind in HANDOVER_KINDS }
            }
            if (pending.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    "STILL TO RUN BEFORE THE FIRST NIGHT",
                    style = MaterialTheme.typography.labelSmall,
                    color = AgedGold,
                )
                for (row in pending) {
                    Text(
                        "• ${row.title} — ${row.prompt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Parchment,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (next != null) {
            FilledTonalButton(
                onClick = { onOpenSeat(next.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next: ${next.name}", modifier = Modifier.padding(vertical = 4.dp))
            }
        } else if (blocked.any { it.tokenShownAt == null }) {
            // Nobody CAN be next: every seat still owed a card is waiting on a
            // setup answer. The primary button becomes that answer (A-1).
            FilledTonalButton(onClick = onChecklist, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Answer ${blockers.values.sumOf { it.size }} setup " +
                        "question${if (blockers.values.sumOf { it.size } == 1) "" else "s"} first",
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        } else {
            Text(
                "Everyone has their token.",
                color = AgedGold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.resetTokenHandout() }) {
                Text("Start over", color = Color.Gray)
            }
            TextButton(onClick = onDone) {
                Text(if (next == null) "Done" else "Finish later", color = AgedGold)
            }
        }
    }
}

/**
 * The A-1 block, at the top of the roster: which seats cannot be dealt, why,
 * and the one tap that fixes it.
 */
@Composable
private fun BlockedBanner(
    seats: List<Player>,
    blockers: Map<Long, List<SetupRequirement>>,
    onChecklist: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Twilight)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "${seats.size} seat${if (seats.size == 1) "" else "s"} cannot be handed out yet",
            style = MaterialTheme.typography.titleSmall,
            color = EmberRed,
        )
        for (seat in seats) {
            for (row in blockers[seat.id].orEmpty()) {
                Text(
                    "• ${seat.name} — ${row.prompt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Parchment,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        OutlinedButton(onClick = onChecklist, modifier = Modifier.padding(top = 6.dp)) {
            Text("Answer these now")
        }
    }
}

/**
 * What a blocked seat gets instead of its card. Never the token: an unanswered
 * "believes" row means the app does not yet know what this player thinks they
 * are, and printing the seat's real character is exactly the leak A-1 filed.
 */
@Composable
private fun BlockedSeatCard(
    player: Player,
    rows: List<SetupRequirement>,
    onAnswer: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("NOT READY", fontSize = 26.sp, color = EmberRed)
            Text(
                player.name,
                fontSize = 42.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                textAlign = TextAlign.Center,
            )
            for (row in rows) {
                Text(
                    row.prompt,
                    fontSize = 17.sp,
                    color = Parchment,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Answer this before the phone reaches them — otherwise the card " +
                    "would show the wrong character.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onAnswer) { Text("Answer now") }
            OutlinedButton(onClick = onBack) { Text("Back to the roster") }
        }
    }
}

private val HANDOVER_KINDS = setOf(
    RequirementKind.PAIR,
    RequirementKind.INFORM,
    RequirementKind.SHOWN_TOKEN,
    RequirementKind.BLUFFS,
)

/**
 * One seat's hand-over: a "pass to NAME" page, then the character card while
 * the finger is held, then — only where the rules say so — an alignment page.
 */
@Composable
private fun HandOutCard(
    viewModel: GameViewModel,
    player: Player,
    position: Int,
    total: Int,
    onFinished: () -> Unit,
    onLater: () -> Unit,
) {
    // The token the player has SEEN — the Drunk's Chambermaid, the Lunatic's
    // Demon — never the seat's truth.
    val believedId = Identity.believedCharacterId(player)
    val character = viewModel.characterById(believedId)
    val pages = remember(player, character) { handOutPages(player, character) }
    // Deliberately NOT saveable: re-opening a seat starts at the character
    // card again rather than resuming mid-sequence.
    var page by remember(player.id) { mutableStateOf(0) }
    var pressing by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    val current = pages.getOrNull(page)
    if (current == null) {
        onFinished()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(page, player.id, pages.size) {
                detectTapGestures(
                    onPress = {
                        // Hold, not tap: a stray tap in transit shows nothing,
                        // and releasing hides immediately (§S6, defect #20).
                        // Same gate as PrivacyCover.
                        pressing = true
                        val releasedEarly = withTimeoutOrNull(HOLD_MILLIS) { tryAwaitRelease() }
                        if (releasedEarly != null) {
                            pressing = false
                        } else {
                            revealed = true
                            val released = tryAwaitRelease()
                            revealed = false
                            pressing = false
                            // Only a real finger-lift counts. A CANCELLED
                            // gesture (the sheet closing, a system takeover)
                            // leaves the seat exactly where it was.
                            if (released) {
                                if (page + 1 < pages.size) page += 1 else onFinished()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!revealed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(current.handOverCaption, fontSize = 26.sp, color = Parchment)
                Text(
                    player.name,
                    fontSize = 52.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .background(Twilight)
                        .padding(horizontal = 26.dp, vertical = 16.dp),
                ) {
                    Text(
                        if (pressing) "Keep holding…" else "HOLD to reveal",
                        fontSize = 20.sp,
                        color = AgedGold,
                    )
                }
                Text(
                    "seat $position of $total" +
                        if (pages.size > 1) " · card ${page + 1} of ${pages.size}" else "",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp),
                )
                OutlinedButton(onClick = onLater, modifier = Modifier.padding(top = 12.dp)) {
                    Text("I'll do this later")
                }
            }
        } else {
            when (current) {
                is HandOutPage.CharacterPage -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("YOU ARE", fontSize = 32.sp, fontFamily = FontFamily.Serif, color = Parchment)
                    CharacterToken(character = character, size = 180.dp)
                    Text(
                        character?.name ?: "?",
                        fontSize = 42.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        // The BELIEVED character's own team, always. A flipped
                        // Ogre must not be painted red (defect #18).
                        color = character?.team?.color ?: AgedGold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        character?.ability.orEmpty(),
                        fontSize = 17.sp,
                        color = Parchment,
                        textAlign = TextAlign.Center,
                    )
                    Text("release to hide", fontSize = 13.sp, color = Color.Gray)
                }

                is HandOutPage.AlignmentPage -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        if (current.evil) "YOU ARE EVIL" else "YOU ARE GOOD",
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (current.evil) EmberRed else TownsfolkBlue,
                        textAlign = TextAlign.Center,
                    )
                    Text(current.caption, fontSize = 17.sp, color = Parchment, textAlign = TextAlign.Center)
                    Text("release to hide", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

/** One card in a seat's hand-over sequence. */
private sealed interface HandOutPage {
    val handOverCaption: String

    data object CharacterPage : HandOutPage {
        override val handOverCaption: String get() = "Pass to"
    }

    data class AlignmentPage(val evil: Boolean, val caption: String) : HandOutPage {
        override val handOverCaption: String get() = "Still with"
    }
}

/**
 * The cards this seat is owed.
 *
 * The character card always. An alignment card only where the rules say the
 * player learns it: Travellers (always asked at arrival) and any seat whose
 * explicit [Player.alignment] override contradicts their believed character's
 * natural side — and NEVER for a character who is not told
 * ([NEVER_TOLD_ALIGNMENT]).
 */
private fun handOutPages(player: Player, believed: Character?): List<HandOutPage> {
    val pages = mutableListOf<HandOutPage>(HandOutPage.CharacterPage)
    val id = believed?.id?.let(Character::normalizeId)
        ?: player.characterId?.let(Character::normalizeId)
    if (id in NEVER_TOLD_ALIGNMENT) return pages
    val naturallyEvil = believed?.team?.isEvil == true
    // No override yet means the storyteller has not decided. Showing a
    // default here would TELL the player something untrue; the checklist row
    // (`traveller.alignment:<seat>`) asks for it first.
    val override = player.alignment ?: return pages
    when {
        player.isTraveller -> pages += HandOutPage.AlignmentPage(
            evil = override == SeatAlignment.EVIL,
            caption = "You are a Traveller. This is the side you play for.",
        )
        (override == SeatAlignment.EVIL) != naturallyEvil ->
            pages += HandOutPage.AlignmentPage(
                evil = override == SeatAlignment.EVIL,
                caption = "Your character's usual side does not apply to you.",
            )
    }
    return pages
}

/** A stable shuffle seed for this game: same order every time it is reopened. */
private val GameState.handOutSeed: Int
    get() = if (id.isNotBlank()) id.hashCode() else players.size * 31 + script.id.hashCode()
