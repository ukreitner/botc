package com.clocktower.grimoire.ui.screens.night

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.Answer
import com.clocktower.engine.Deaths
import com.clocktower.engine.GameState
import com.clocktower.engine.InfoCalc
import com.clocktower.engine.InfoResult
import com.clocktower.engine.KillCause
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightGuide
import com.clocktower.engine.NightInput
import com.clocktower.engine.NightMarkers
import com.clocktower.engine.NightPlan
import com.clocktower.engine.NightStep
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.StepGate
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.TokenCopies
import com.clocktower.grimoire.ui.components.labelCopies
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.asCard
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed

/**
 * One night step, as one card.
 *
 * The organising principle of ux/night-screen: **a night step is a card, a card
 * has exactly one primary button, and pressing that button advances the night.**
 * Everything the storyteller must read is above the button; everything they must
 * decide is a tap on the same card; nothing is in a tray, a bottom sheet or
 * another tab.
 *
 * The card body carries no `clickable` (defect #22): a tap on the prose, on the
 * gap between chips or slightly off a chip edge used to collapse the step the
 * storyteller was working on and throw away what they had entered.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun NightCard(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    forced: Boolean,
    onRunAnyway: () -> Unit,
    onShow: (ShowCard, Long?, Boolean) -> Unit,
    onOpenShowTool: () -> Unit,
    onKillSheet: (Long, Long?) -> Unit,
    onDawn: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = step.key
    // Keyed on the CYCLE as well as the step: last night's two lit chips must
    // never be presented as tonight's answer (defect #3).
    var pick by remember(state.cycle, key.token) { mutableStateOf(PickState()) }
    var gateAnswer by rememberSaveable(state.cycle, key.token) { mutableStateOf<Boolean?>(null) }
    var drawerOpen by rememberSaveable(state.cycle, key.token) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShowCard?>(null) }

    val skipped = isSkipped(step, forced)
    val gate = step.gate
    val awaitingGate = needsGateAnswer(gate, gateAnswer != null)
    val choiceAllowed = gate !is StepGate.Reduced || StepGate.CHOOSE in gate.allow
    val action = step.action?.takeIf { choiceAllowed && !skipped && !awaitingGate }

    val holders = step.wakes.mapNotNull { state.player(it) }
    val holderName = holders.firstOrNull()?.name.orEmpty()
    val character = viewModel.characterById(step.abilityId)

    // ---- the information this step gives, and the cards that carry it ----
    val infoId = (step.action as? ShowInfo)?.sourceId?.ifBlank { step.abilityId } ?: step.abilityId
    val info = remember(state, key.token, pick.playerIds) {
        if (InfoCalc.supports(infoId) && pick.playerIds.size >= picksNeeded(step.action)) {
            viewModel.nightInfo(state, infoId, step.holderId, pick.playerIds)
        } else {
            null
        }
    }
    val offers = remember(step.cards, info, state) {
        val fromEngine = (step.cards + info?.let { NightPlan.cardsFor(it) }.orEmpty())
            .map { UiOffer(it.label, it.card.asCard(), it.truthful, it.editable) }
        (fromEngine + pointOffers(state, viewModel, info)).distinctBy { it.label }
    }
    val answer = info?.let {
        answerLabel(
            it.answer,
            characterName = { id -> viewModel.characterById(id)?.name ?: id },
            playerName = { id -> state.player(id)?.name ?: "?" },
        )
    }.orEmpty()

    // ---- what the action will do, previewed through the ONE kill funnel ----
    val effects = actionEffects(step.action)
    val attack = effects.filterIsInstance<NightEffect.Attack>().firstOrNull()
    val outcomes: List<Pair<Long, KillOutcome>> = remember(state, key.token, pick.playerIds, attack) {
        if (attack == null || skipped) {
            emptyList()
        } else {
            pick.playerIds.map { id ->
                id to Deaths.killOutcome(
                    state,
                    viewModel::characterById,
                    id,
                    KillCause(
                        cause = attack.cause,
                        sourceCharacterId = step.abilityId,
                        sourcePlayerId = step.holderId,
                        ignoresProtection = !attack.respectProtection,
                    ),
                )
            }
        }
    }
    val needsKillSheet = outcomes.any { it.second is KillOutcome.Choice }
    val deathLine = outcomes.joinToString(" · ") { (id, outcome) ->
        deathHeadline(outcome, state.player(id)?.name.orEmpty())
    }.trim(' ', '·')

    val isDawn = step.slotId == NightMarkers.DAWN
    val label = primaryLabel(
        picked = pick.playerIds.mapNotNull { state.player(it)?.name },
        places = placedLabels(effects),
        deathLine = deathLine,
        answer = answer,
        holder = holderName,
        none = pick.none,
        skipped = skipped,
        dawn = isDawn,
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 1. who wakes ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (character != null) {
                    CharacterToken(character = character, size = 52.dp, dimmed = skipped)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = character?.name ?: step.title,
                        fontSize = nightSp(22f).sp,
                        lineHeight = nightSp(26f).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else AgedGold,
                    )
                    val seats = holders.joinToString(" · ") { holder ->
                        val seat = state.seats.indexOfFirst { it.id == holder.id } + 1
                        "${holder.name}${if (seat > 0) " · seat $seat" else ""}"
                    }
                    if (seats.isNotBlank()) {
                        Text(
                            text = seats,
                            fontSize = nightSp(16f).sp,
                            lineHeight = nightSp(20f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ---- 2. badges: the gate, then whatever the planner noticed ----
            val badge = gateBadge(gate)
            if (badge != null || step.badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    badge?.let { NightChip(label = it.text, tone = it.tone) }
                    for (text in step.badges) NightChip(label = text, tone = Tone.MUTED)
                }
            }

            // ---- 3. the one derived fact worth ember ----
            if (step.banner.isNotBlank()) {
                Text(
                    text = step.banner,
                    fontSize = nightSp(16f).sp,
                    lineHeight = nightSp(21f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmberRed,
                )
            }

            // ---- 4. the instruction, in the storyteller's voice ----
            val prompt = step.prompt.ifBlank { step.detail }
            if (prompt.isNotBlank()) {
                Text(
                    text = prompt,
                    fontSize = nightSp(18f).sp,
                    lineHeight = nightSp(24f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ---- 5. a Conditional gate is answered before anything is offered ----
            if (awaitingGate && gate is StepGate.Conditional) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        NightChip(
                            label = gate.yesLabel,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { gateAnswer = true },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        NightChip(
                            label = gate.noLabel,
                            tone = Tone.MUTED,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                gateAnswer = false
                                viewModel.resolveNightStep(key, NightInput(none = true))
                            },
                        )
                    }
                }
            }

            // ---- 6. the ask ----
            action?.let { NightAsk(viewModel, state, step, it, pick) { next -> pick = next } }

            // ---- 7. what the grimoire already knows ----
            if (info != null) {
                Text(
                    text = info.headline,
                    fontSize = nightSp(17f).sp,
                    lineHeight = nightSp(22f).sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                )
                if (info.detail.isNotBlank()) {
                    Text(
                        text = info.detail,
                        fontSize = NIGHT_MIN_SP.sp,
                        lineHeight = nightSp(19f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (caveat in info.caveats) {
                    Text(
                        text = "! $caveat",
                        fontSize = NIGHT_MIN_SP.sp,
                        lineHeight = nightSp(19f).sp,
                        color = EmberRed,
                    )
                }
            }

            // ---- 8. pre-filled cards: tap shows, long-press edits ----
            if (offers.isNotEmpty()) {
                CardOffers(
                    offers = offers,
                    recipientId = step.holderId,
                    onShow = onShow,
                    onEdit = { editing = it },
                )
            }

            // ---- 9. the consequence, before the button that applies it ----
            for ((id, outcome) in outcomes) {
                Text(
                    text = "${state.player(id)?.name.orEmpty()}: ${outcomeDetail(outcome)}",
                    fontSize = nightSp(16f).sp,
                    lineHeight = nightSp(21f).sp,
                    color = EmberRed,
                )
            }

            // ---- 10. ONE primary button, stating the outcome ----
            if (skipped) {
                PrimaryButton(label = "RUN IT ANYWAY", onConfirm = onRunAnyway)
            } else if (!awaitingGate) {
                PrimaryButton(
                    label = if (needsKillSheet) "RESOLVE THE DEATH…" else label,
                    enabled = primaryEnabled(action, pick),
                    holdMillis = if (isDestructive(effects) && !needsKillSheet) HOLD_CONFIRM_MILLIS else 0,
                    onConfirm = {
                        when {
                            needsKillSheet ->
                                outcomes.firstOrNull { it.second is KillOutcome.Choice }
                                    ?.let { onKillSheet(it.first, step.holderId) }

                            else -> {
                                viewModel.resolveNightStep(
                                    key,
                                    NightInput(
                                        playerIds = pick.playerIds,
                                        characterIds = pick.characterIds,
                                        yes = pick.yes,
                                        none = pick.none,
                                    ),
                                )
                                if (isDawn) onDawn()
                            }
                        }
                    },
                )
            }

            // ---- 11. prev / next at thumb level ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    NightChip(
                        label = "‹ back",
                        tone = Tone.MUTED,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBack,
                    )
                }
                Box(Modifier.weight(1f)) {
                    NightChip(
                        label = "skip ›",
                        tone = Tone.MUTED,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSkip,
                    )
                }
            }

            // ---- 12. the collapsed secondary drawer ----
            NightChip(
                label = if (drawerOpen) "⌃ fewer options" else "⌄ other outcomes · show a card · how to run this",
                tone = Tone.MUTED,
                modifier = Modifier.fillMaxWidth(),
                onClick = { drawerOpen = !drawerOpen },
            )
            if (drawerOpen) {
                SecondaryDrawer(
                    viewModel = viewModel,
                    state = state,
                    step = step,
                    attacked = pick.playerIds,
                    hasAttack = attack != null,
                    onOpenShowTool = onOpenShowTool,
                    onKillSheet = onKillSheet,
                )
            }
        }
    }

    editing?.let { card ->
        CardEditor(
            card = card,
            onDismiss = { editing = null },
            onShow = {
                editing = null
                onShow(it, step.holderId, true)
            },
        )
    }
}

/**
 * One card the storyteller can hold up, as the SCREEN sees it.
 *
 * The engine offers `CardOffer(label, ShowCardSpec, truthful)`. Two answer
 * shapes have no `ShowCardSpec` to carry them yet — "point at these players"
 * and "here are two tokens at once" — so the screen builds those itself from
 * the same typed `InfoResult` (see [pointOffers]). When `ShowCardSpec` grows
 * `PointCard` and `MultiTokenCard`, [pointOffers] deletes and the registry
 * offers them directly.
 */
data class UiOffer(
    val label: String,
    val card: ShowCard,
    val truthful: Boolean,
    val editable: Boolean = true,
)

/**
 * The card the app never had (ux/night-screen defect #18, its "largest single
 * gap between the paper procedure and the app"): the phone does the pointing,
 * so the storyteller's other hand — the one that taps knees — stays free.
 *
 * Washerwoman, Librarian, Investigator, Noble, Steward, Knight, Sage,
 * Grandmother and every "point out the Minions" step answer with
 * `Answer.Players`; the Dreamer answers with two characters at once. Lies get
 * the same treatment, from `InfoResult.alternatives`, so a poisoned holder is
 * never offered a red heading with nothing under it (defect #16).
 */
private fun pointOffers(
    state: GameState,
    viewModel: GameViewModel,
    info: InfoResult?,
): List<UiOffer> {
    info ?: return emptyList()
    fun cardFor(answer: Answer): ShowCard? = when {
        answer is Answer.Players && answer.ids.isNotEmpty() -> {
            val seats = answer.ids.mapNotNull { id ->
                state.seats.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { it + 1 to state.player(id) }
            }
            ShowCard.PointCard(
                prefix = pointPrefix(answer.characterId != null, seats.size),
                playerNames = seats.mapNotNull { it.second?.name },
                seatNumbers = seats.map { it.first },
                characterId = answer.characterId,
            )
        }
        answer is Answer.Characters && answer.ids.size > 1 ->
            ShowCard.MultiTokenCard("THESE CHARACTERS", answer.ids)
        else -> null
    }
    fun label(answer: Answer, truthful: Boolean): String {
        val what = when (answer) {
            is Answer.Players -> answer.ids.mapNotNull { state.player(it)?.name }.joinToString(", ")
            is Answer.Characters -> answer.ids.joinToString(", ") { viewModel.characterById(it)?.name ?: it }
            else -> ""
        }
        return if (truthful) "SHOW: $what" else "LIE · SHOW $what"
    }
    return buildList {
        cardFor(info.answer)?.let { add(UiOffer(label(info.answer, true), it, true)) }
        for (alternative in info.alternatives) {
            cardFor(alternative)?.let { add(UiOffer(label(alternative, false), it, false)) }
        }
    }
}

/** The line above the names on a [ShowCard.PointCard]. */
fun pointPrefix(withCharacter: Boolean, names: Int): String = when {
    withCharacter && names > 1 -> "ONE OF THESE PLAYERS IS THE"
    withCharacter -> "THIS PLAYER IS THE"
    names > 1 -> "THESE PLAYERS"
    else -> "THIS PLAYER"
}

/** The kill funnel's own words, under the picker and above the button. */
fun outcomeDetail(outcome: KillOutcome): String = when (outcome) {
    is KillOutcome.Dies -> outcome.reason.ifBlank { "Nothing stops it — they die." }
    is KillOutcome.Prevented -> listOf(outcome.reason, outcome.announce)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    is KillOutcome.Spends -> "They survive, and the ability is spent."
    is KillOutcome.RegistersDead -> outcome.reason
    is KillOutcome.Redirect -> outcome.reason
    is KillOutcome.Choice -> outcome.question
    KillOutcome.AlreadyDead -> "A dead player cannot die again."
}

/**
 * Pre-filled cards (ux/night-screen §D). Truthful offers are gold; a lie is
 * ember and wears the word LIE, so a fumble at 1 a.m. is visible before the card
 * is up. Tap shows the card as it stands; long-press opens the editor.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CardOffers(
    offers: List<UiOffer>,
    recipientId: Long?,
    onShow: (ShowCard, Long?, Boolean) -> Unit,
    onEdit: (ShowCard) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (offer in offers) {
            val tone = if (offer.truthful) Tone.ACTIVE else Tone.ALERT
            val card = offer.card
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.color().copy(alpha = 0.16f))
                    .combinedClickable(
                        onClick = { onShow(card, recipientId, offer.truthful) },
                        onLongClick = { if (offer.editable) onEdit(card) },
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = offer.label,
                    fontSize = NIGHT_MIN_SP.sp,
                    lineHeight = nightSp(18f).sp,
                    fontWeight = FontWeight.Bold,
                    color = tone.color(),
                )
            }
        }
    }
}

/** Two taps for the uncommon: alternates, the card catalogue, the run-book. */
@Composable
private fun SecondaryDrawer(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    attacked: List<Long>,
    hasAttack: Boolean,
    onOpenShowTool: () -> Unit,
    onKillSheet: (Long, Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        NightChip(
            label = "show a card…",
            tone = Tone.NORMAL,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenShowTool,
        )
        if (hasAttack) {
            for (id in attacked) {
                NightChip(
                    label = "open the kill sheet for ${state.player(id)?.name.orEmpty()}…",
                    tone = Tone.ALERT,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onKillSheet(id, step.holderId) },
                )
            }
        }
        TokenPlacer(viewModel, state, step)
        if (step.key.token in state.nightStepsDone) {
            NightChip(
                label = "undo this step — put it back on the sheet",
                tone = Tone.MUTED,
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.toggleNightStep(step.key) },
            )
        }
        val guide = NightGuide.forStep(step.abilityId, step.style)?.instructions.orEmpty()
        val book = listOf(guide, step.detail).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
        if (book.isNotBlank()) {
            Text(
                text = "HOW TO RUN THIS",
                fontSize = NIGHT_MIN_SP.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = book,
                fontSize = NIGHT_MIN_SP.sp,
                lineHeight = nightSp(19f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A hand-placed reminder token for this character, for the cases the registry
 * cannot know about (a storyteller ruling, a homebrew script).
 *
 * It goes through `GameActionsApi.placeToken`, which honours the number of
 * physical copies `characters.json` lists. The old night tray counted copies
 * itself and, once they were all placed, silently removed whichever copy
 * happened to be first in iteration order (defect #31).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenPlacer(viewModel: GameViewModel, state: GameState, step: NightStep) {
    val character = viewModel.characterById(step.abilityId) ?: return
    val tokens = remember(character.id) { labelCopies(character.allReminders) }
    if (tokens.isEmpty()) return
    var pending by remember(step.key.token) { mutableStateOf<TokenCopies?>(null) }

    Text(
        text = "PLACE A TOKEN BY HAND",
        fontSize = NIGHT_MIN_SP.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (token in tokens) {
            val label = if (token.copies > 1) "${token.label} ×${token.copies}" else token.label
            NightChip(
                label = if (pending?.label == token.label) "✓ $label" else label,
                tone = if (pending?.label == token.label) Tone.ACTIVE else Tone.MUTED,
                onClick = { pending = if (pending?.label == token.label) null else token },
            )
        }
    }
    pending?.let { token ->
        Text(
            text = "Place “${token.label}” on:",
            fontSize = NIGHT_MIN_SP.sp,
            color = AgedGold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((index, seat) in state.seats.withIndex()) {
                NightChip(
                    label = "${index + 1} ${seat.name}",
                    tone = Tone.NORMAL,
                    onClick = {
                        viewModel.placeToken(
                            playerId = seat.id,
                            reminder = PlacedReminder(sourceId = character.id, label = token.label),
                            copies = token.copies,
                        )
                        pending = null
                    },
                )
            }
        }
    }
}

/** Long-press on an offer: the free-text editor, no longer the default path. */
@Composable
private fun CardEditor(card: ShowCard, onDismiss: () -> Unit, onShow: (ShowCard) -> Unit) {
    val initial = when (card) {
        is ShowCard.Message -> card.title
        is ShowCard.CharacterCard -> card.prefix
        is ShowCard.PointCard -> card.prefix
        is ShowCard.MultiTokenCard -> card.prefix
        is ShowCard.AlignmentCard -> card.text
        else -> ""
    }
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit this card") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text shown full-screen") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onShow(
                        when (card) {
                            is ShowCard.CharacterCard -> card.copy(prefix = text)
                            is ShowCard.PointCard -> card.copy(prefix = text)
                            is ShowCard.MultiTokenCard -> card.copy(prefix = text)
                            is ShowCard.AlignmentCard -> card.copy(text = text)
                            else -> ShowCard.Message(text)
                        },
                    )
                },
            ) { Text("Show it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
