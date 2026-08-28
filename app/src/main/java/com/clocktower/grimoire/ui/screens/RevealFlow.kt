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
import com.clocktower.engine.SetupRequirements
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.overlaySafeAreaPadding
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
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        // A dialog is hosted ABOVE the shell's own inset padding, so the safe
        // area is re-applied here rather than inside [HandOutMode] — which is
        // also a first-class destination (SetupScreen), where the shell has
        // already padded it and doing it again would double up.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().overlaySafeAreaPadding()) {
                HandOutMode(viewModel = viewModel, state = state, onDone = onDone, seats = seats)
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
) {
    KeepScreenOn()
    var seatOrder by rememberSaveable { mutableStateOf(false) }
    var openSeatId by rememberSaveable { mutableStateOf<Long?>(null) }

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
    val done = queue.count { it.tokenShownAt != null }
    val next = queue.firstOrNull { it.tokenShownAt == null }

    openSeatId?.let { id ->
        val seat = state.player(id)
        if (seat == null) {
            openSeatId = null
        } else {
            HandOutCard(
                viewModel = viewModel,
                player = seat,
                position = queue.indexOfFirst { it.id == id } + 1,
                total = queue.size,
                onFinished = {
                    viewModel.markTokenHandedOut(seat.id)
                    openSeatId = null
                },
                onLater = { openSeatId = null },
            )
            return
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
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
            TextButton(onClick = { seatOrder = !seatOrder }) {
                Text(if (seatOrder) "Shuffled" else "Seat order", color = AgedGold)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (seat in queue) {
                    val shown = seat.tokenShownAt != null
                    val isNext = seat.id == next?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(if (isNext) Twilight else Color.Transparent)
                            .clickable { openSeatId = seat.id }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            when {
                                shown -> "✓"
                                isNext -> "▶"
                                else -> "○"
                            },
                            color = if (shown) AgedGold else Parchment,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            seat.name,
                            color = if (shown) AgedGold.copy(alpha = 0.7f) else Parchment,
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
                onClick = { openSeatId = next.id },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next: ${next.name}", modifier = Modifier.padding(vertical = 4.dp))
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
