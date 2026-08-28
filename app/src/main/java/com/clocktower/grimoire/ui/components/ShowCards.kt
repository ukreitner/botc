package com.clocktower.grimoire.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.ShowCardSpec
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.TownsfolkBlue
import com.clocktower.grimoire.ui.theme.color
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A card the storyteller holds up to a player across the table: huge text on a
 * black screen, optionally with character tokens. Mirrors the paper info tokens
 * of the physical game.
 *
 * This is the RENDERER's type. The engine offers cards as `ShowCardSpec`
 * (serialisable, no Compose); [asCard] maps one onto the other, and the three
 * variants ux/night-screen §D asks for — [PointCard], [MultiTokenCard] and an
 * [AlignmentCard] that carries its own text — live here, where the drawing is.
 */
sealed interface ShowCard {
    data class Message(val title: String, val subtitle: String = "") : ShowCard

    data class CharacterCard(val prefix: String, val characterId: String) : ShowCard

    data class NumberCard(val number: Int) : ShowCard

    /**
     * GOOD / EVIL, or neither. [text] is the caption the guide entry asked for —
     * a `kind:"good"` show whose text reads "GOOD IS WINNING" must not be
     * repainted as "YOU ARE GOOD" (defect #6, characters/general.md #1).
     */
    data class AlignmentCard(val evil: Boolean?, val text: String = "") : ShowCard

    data class BluffsCard(val characterIds: List<String>) : ShowCard

    /**
     * A neutral, full-script character sheet the player can silently point at
     * (Pit-Hag, Philosopher, Cerenovus…). Shows every script character with zero
     * game-state hints, so it reveals nothing.
     */
    data class SheetCard(val characterIds: List<String>) : ShowCard

    /**
     * The card the app never had (defect #18): **one to three player names at
     * 48 sp with their seat numbers**, so the storyteller points with the phone
     * instead of putting it down and pointing with a hand they do not have free.
     *
     * Washerwoman, Librarian, Investigator, Noble, Steward, Knight, Sage,
     * Grandmother, "this player stopped you", "these are your Minions".
     */
    data class PointCard(
        val prefix: String,
        val playerNames: List<String>,
        val seatNumbers: List<Int>,
        /** Optional token between prefix and names. */
        val characterId: String? = null,
    ) : ShowCard

    /** Two or more tokens at once — the Dreamer's pair, the Godfather's Outsiders. */
    data class MultiTokenCard(val prefix: String, val characterIds: List<String>) : ShowCard
}

/** The renderer's view of an engine-offered card. */
fun ShowCardSpec.asCard(): ShowCard = when (this) {
    is ShowCardSpec.Message -> ShowCard.Message(title, subtitle)
    is ShowCardSpec.CharacterCard -> ShowCard.CharacterCard(prefix, characterId)
    is ShowCardSpec.NumberCard -> ShowCard.NumberCard(number)
    is ShowCardSpec.AlignmentCard -> ShowCard.AlignmentCard(evil)
    is ShowCardSpec.BluffsCard -> ShowCard.BluffsCard(characterIds)
    is ShowCardSpec.SheetCard -> ShowCard.SheetCard(characterIds)
    is ShowCardSpec.PointCard ->
        ShowCard.PointCard(prefix, playerNames, seatNumbers, characterId)
    is ShowCardSpec.MultiTokenCard -> ShowCard.MultiTokenCard(prefix, characterIds)
}

/** What this card said, in one line, for the ledger ("Ben was shown: 1"). */
fun ShowCard.describe(nameOf: (String) -> String): String = when (this) {
    is ShowCard.Message -> listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" — ")
    is ShowCard.CharacterCard -> "$prefix ${nameOf(characterId)}"
    is ShowCard.NumberCard -> number.toString()
    is ShowCard.AlignmentCard -> text.ifBlank { alignmentWord(evil) }
    is ShowCard.BluffsCard -> "not in play: " + characterIds.joinToString { nameOf(it) }
    is ShowCard.SheetCard -> "the character sheet"
    // The character is half the meaning: "ONE OF THESE PLAYERS IS THE Chef —
    // Ana, Dan", never "…IS THE Ana, Dan" (playtest B P2 #16).
    is ShowCard.PointCard ->
        prefix + (characterId?.let { " " + nameOf(it) }.orEmpty()) + " — " + playerNames.joinToString()
    is ShowCard.MultiTokenCard -> prefix + " " + characterIds.joinToString { nameOf(it) }
}

private fun alignmentWord(evil: Boolean?): String = when (evil) {
    true -> "EVIL"
    false -> "GOOD"
    null -> "NEITHER"
}

/**
 * Full-screen presentation of a [ShowCard].
 *
 * **The card body is not tappable** (defect #2 — a tap anywhere used to dismiss
 * it, including a tap by the player it was being shown to, dropping straight
 * back to the night sheet while the phone still pointed at their face). Exit is
 * a press-and-hold on a bottom-edge control, and **releasing it lands on the
 * privacy cover**, never on the grimoire: the storyteller turns the phone back
 * around and holds again (ux/night-screen §E).
 *
 * ⟳ FLIP rotates the content 180° for a card held out to a player sitting
 * opposite — every card used to be upside down to its intended reader.
 */
@Composable
fun FullScreenShow(
    card: ShowCard,
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    /** Shown on the cover the card exits onto: "Night 3". */
    coverCaption: String = "",
) {
    var covered by remember { mutableStateOf(false) }
    if (covered) {
        PrivacyCover(caption = coverCaption, onUnlock = onDismiss)
        return
    }
    var flipped by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { /* the card is not dismissible by a tap; hold to close */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Black edge to edge — under the notch and under the home indicator —
        // but NOTHING readable or pressable goes there. The card used to draw
        // its FLIP / HOLD TO CLOSE row at the physical bottom of the screen,
        // where the phone's gesture strip covered all but a sliver of it and
        // the storyteller could not close the card at all.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // The insets are the ones measured at the app ROOT: inside a
            // Dialog's own window Compose reports zero, which is how FLIP and
            // HOLD TO CLOSE came to be drawn inside the gesture strip, sliced
            // in half and untappable (playtest B P1 #5).
            val safeBottom = dialogSafeBottom()
            // …plus the height the window itself pushed the content down by,
            // which is exactly how far it now hangs off the bottom edge.
            val overflow = dialogTopOverflow()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(
                        top = overlaySafeTop(),
                        bottom = bottomActionClearance(safeBottom) + overflow,
                    )
                    .graphicsLayer { rotationZ = if (flipped) 180f else 0f },
                contentAlignment = Alignment.Center,
            ) {
                CardBody(card, viewModel)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Bottom only: a side navigation bar in landscape must not
                    // shift a row that is meant to stay centred.
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = bottomActionPadding(safeBottom) + overflow),
            ) {
                FilledTonalButton(onClick = { flipped = !flipped }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("⟳ FLIP", fontSize = 16.sp)
                }
                HoldToClose(onClose = { covered = true })
            }
        }
    }
}

/** The 1.2 s hold that closes a card, with the ring the privacy cover uses. */
@Composable
private fun HoldToClose(onClose: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = if (holding) HOLD_MILLIS.toInt() else 150),
        label = "card-hold",
    )
    Box(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AgedGold.copy(alpha = 0.18f))
            .drawBehind {
                if (progress > 0f) {
                    drawRect(color = AgedGold.copy(alpha = 0.5f), size = size.copy(width = size.width * progress))
                }
                drawRoundRect(color = AgedGold.copy(alpha = 0.4f), style = Stroke(width = 2f))
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        val released = withTimeoutOrNull(HOLD_MILLIS) { tryAwaitRelease() }
                        holding = false
                        if (released == null) onClose()
                    },
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (holding) "keep holding…" else "HOLD TO CLOSE",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Parchment,
        )
    }
}

/** How long the exit control must be held, in ms. Matches the privacy cover. */
private const val HOLD_MILLIS: Long = 1200L

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBody(card: ShowCard, viewModel: GameViewModel) {
    when (card) {
        is ShowCard.Message -> BigText(card.title, card.subtitle)

        is ShowCard.NumberCard -> Text(
            text = "${card.number}",
            fontSize = 220.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = AgedGold,
        )

        is ShowCard.AlignmentCard -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = alignmentWord(card.evil),
                fontSize = 110.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = when (card.evil) {
                    true -> EmberRed
                    false -> TownsfolkBlue
                    null -> Parchment
                },
                textAlign = TextAlign.Center,
            )
            Text(
                // The guide entry's own words win; "YOU ARE GOOD" is only the
                // fallback (defect #6).
                text = card.text.ifBlank { "YOU ARE " + alignmentWord(card.evil) },
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                color = Parchment,
                textAlign = TextAlign.Center,
            )
        }

        is ShowCard.CharacterCard -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BigText(card.prefix)
            val character = viewModel.characterById(card.characterId)
            CharacterToken(character = character, size = 180.dp)
            Text(
                text = character?.name ?: "?",
                fontSize = 44.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Parchment,
                textAlign = TextAlign.Center,
            )
        }

        is ShowCard.BluffsCard -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BigText("THESE CHARACTERS\nARE NOT IN PLAY")
            // FlowRow, not Row: a fourth bluff used to run off the screen edge
            // (defect #33).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                for (id in card.characterIds) {
                    TokenWithName(character = viewModel.characterById(id), size = 100.dp)
                }
            }
        }

        is ShowCard.MultiTokenCard -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BigText(card.prefix)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                for (id in card.characterIds) {
                    TokenWithName(character = viewModel.characterById(id), size = 110.dp)
                }
            }
        }

        is ShowCard.PointCard -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = card.prefix,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                textAlign = TextAlign.Center,
            )
            card.characterId?.let {
                CharacterToken(character = viewModel.characterById(it), size = 160.dp)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((index, name) in card.playerNames.withIndex()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = name,
                            fontSize = 48.sp,
                            lineHeight = 54.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Parchment,
                            textAlign = TextAlign.Center,
                        )
                        card.seatNumbers.getOrNull(index)?.let { seat ->
                            Text(
                                text = "seat $seat",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        is ShowCard.SheetCard -> CharacterSheetGrid(card.characterIds, viewModel)
    }
}

/** The pointable character sheet: every script character, grouped by team. */
@Composable
private fun CharacterSheetGrid(characterIds: List<String>, viewModel: GameViewModel) {
    val byTeam = characterIds
        .mapNotNull { viewModel.characterById(it) }
        .sortedBy { it.name }
        .groupBy { it.team }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 40.dp, bottom = 40.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "POINT TO A CHARACTER",
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON, Team.TRAVELLER)) {
            val characters = byTeam[team] ?: continue
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = team.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = team.color,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(characters, key = { it.id }) { character ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CharacterToken(character = character, size = 62.dp)
                    Text(
                        text = character.name,
                        fontSize = 14.sp,
                        color = Parchment,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigText(title: String, subtitle: String = "") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = title,
            fontSize = 52.sp,
            lineHeight = 60.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = AgedGold,
            textAlign = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                color = Parchment,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Picker for what to show: the classic info-token phrases, number and alignment
 * signals, character cards, current bluffs, and free text.
 *
 * It no longer announces what is in play (defect #35): this is the sheet the
 * storyteller opens while walking towards a player, and it used to sort the
 * in-play characters first and say so out loud. The in-play shortcut survives
 * as a collapsed group that is hidden until asked for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShowToolSheet(
    viewModel: GameViewModel,
    state: GameState,
    onShow: (ShowCard) -> Unit,
    onDismiss: () -> Unit,
) {
    var customText by rememberSaveable { mutableStateOf("") }
    var characterPrefix by rememberSaveable { mutableStateOf("THIS PLAYER IS") }
    var characterSearch by rememberSaveable { mutableStateOf("") }
    var showSuggested by rememberSaveable { mutableStateOf(false) }
    val scriptCharacters = viewModel.gameData.resolve(state.script)
    val inPlayIds = state.players.mapNotNull { it.characterId }.toSet()
    val visibleCharacters = remember(scriptCharacters, characterSearch) {
        val needle = characterSearch.trim()
        scriptCharacters
            .filter { it.team != Team.FABLED }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            // Alphabetical within team, and NOTHING about what is in play.
            .sortedWith(compareBy<Character> { it.team.ordinal }.thenBy { it.name })
    }

    // Fully expanded from the first frame: half-open, the sheet's own search
    // field landed under the home indicator and `audit` called its centre
    // untappable (playtest B P2 #20).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = overlayBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Show a card", style = MaterialTheme.typography.headlineSmall, color = AgedGold)
                Text(
                    "Hold the phone up to a player. The card cannot be tapped away — " +
                        "hold the button at the bottom to close it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {
                        onShow(
                            ShowCard.SheetCard(
                                scriptCharacters.filter { it.team != Team.FABLED }.map { it.id },
                            ),
                        )
                    },
                    label = { Text("Character sheet — player points silently") },
                )
            }
            item {
                Text("Character tokens", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (prefix in listOf(
                        "THIS PLAYER IS",
                        "YOU ARE",
                        "THIS CHARACTER SELECTED YOU",
                        "YOU ARE MAD THAT YOU ARE",
                    )) {
                        FilterChip(
                            selected = characterPrefix == prefix,
                            onClick = { characterPrefix = prefix },
                            label = { Text(prefix) },
                        )
                    }
                }
                OutlinedTextField(
                    value = characterPrefix,
                    onValueChange = { characterPrefix = it },
                    label = { Text("Text above the token — edit freely") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = characterSearch,
                    onValueChange = { characterSearch = it },
                    placeholder = { Text("Find a character…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                AssistChip(
                    onClick = { showSuggested = !showSuggested },
                    label = { Text(if (showSuggested) "Hide suggestions" else "Suggest from this game") },
                )
                if (showSuggested) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (character in visibleCharacters.filter { it.id in inPlayIds }) {
                            ShowCharacterTile(character) {
                                onShow(ShowCard.CharacterCard(characterPrefix, character.id))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (character in visibleCharacters) {
                        ShowCharacterTile(character) {
                            onShow(ShowCard.CharacterCard(characterPrefix, character.id))
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Phrases", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (phrase in listOf(
                        "THIS IS THE DEMON",
                        "THESE ARE YOUR MINIONS",
                        "THIS PLAYER IS…",
                        "THIS CHARACTER SELECTED YOU",
                        "YOU ARE…",
                        "USE YOUR ABILITY?",
                        "DID YOU NOMINATE TODAY?",
                    )) {
                        AssistChip(onClick = { onShow(ShowCard.Message(phrase)) }, label = { Text(phrase) })
                    }
                }
            }
            item {
                Text("Point at a seat", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The phone does the pointing — hold it up and the player reads the name.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((index, seat) in state.seats.withIndex()) {
                        AssistChip(
                            onClick = {
                                onShow(
                                    ShowCard.PointCard(
                                        prefix = "THIS PLAYER",
                                        playerNames = listOf(seat.name),
                                        seatNumbers = listOf(index + 1),
                                    ),
                                )
                            },
                            label = { Text("${index + 1} ${seat.name}") },
                        )
                    }
                }
            }
            item {
                Text("Signals", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..9) {
                        AssistChip(onClick = { onShow(ShowCard.NumberCard(n)) }, label = { Text("$n") })
                    }
                    AssistChip(onClick = { onShow(ShowCard.AlignmentCard(evil = false)) }, label = { Text("Good") })
                    AssistChip(onClick = { onShow(ShowCard.AlignmentCard(evil = true)) }, label = { Text("Evil") })
                    if (state.demonBluffIds.isNotEmpty()) {
                        AssistChip(
                            onClick = { onShow(ShowCard.BluffsCard(state.demonBluffIds)) },
                            label = { Text("Bluffs (${state.demonBluffIds.size})") },
                        )
                    }
                }
            }
            item {
                Text("Custom text", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Anything you need to say silently…") },
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        enabled = customText.isNotBlank(),
                        onClick = { onShow(ShowCard.Message(customText)) },
                    ) { Text("Show") }
                }
            }
        }
    }
}

@Composable
private fun ShowCharacterTile(character: Character, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        CharacterToken(character = character, size = 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            character.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
