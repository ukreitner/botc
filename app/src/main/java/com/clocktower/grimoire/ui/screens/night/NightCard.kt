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
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.ShowCardSpec
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
    /**
     * Questions the engine raised from this row and is still owed — the Imp's
     * star pass. They are asked HERE, on the card, before the row is left
     * (playtest B P0 #3); the sheet keeps the row open until they are answered.
     */
    prompts: List<Prompt> = emptyList(),
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
    val answerCards = remember(info, state) {
        info?.let { result ->
            NightPlan.cardsFor(state, result) { id -> viewModel.characterById(id)?.name ?: id }
        }.orEmpty()
    }
    val offers = remember(step.cards, answerCards) {
        (step.cards + answerCards)
            .map { UiOffer(it.label, it.card.asCard(), it.truthful, it.editable) }
            .distinctBy { it.label }
    }
    // The card the primary button is PROMISING. `SHOW "0" TO CLEO` used to tick
    // the row and advance without ever putting a card on screen or writing a
    // `shown:` row, so the Chef got nothing and the sheet said the step was
    // done (playtest B P1 #6).
    val truthCard = remember(answerCards) { answerCards.firstOrNull { it.truthful }?.card?.asCard() }
    val truthful = remember(offers) { offers.filter { it.truthful } }
    val lies = remember(offers) { offers.filterNot { it.truthful } }
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

    // A card the storyteller has deliberately chosen from the offers. It, and
    // not the computed truth, is then what the primary promises: for a poisoned
    // holder the card itself says "give false info", and the one gold button
    // must not be the true answer (playtest B P1 #9).
    var chosen by remember(state.cycle, key.token, info) { mutableStateOf<UiOffer?>(null) }
    val owesFalseInfo = info != null && mustNotShowTruth(info.obligation, info.abilityMalfunctions)
    val shownAnswer = when {
        chosen != null -> offerAnswerText(chosen!!.label)
        owesFalseInfo -> ""
        else -> answer
    }

    val isDawn = step.slotId == NightMarkers.DAWN
    val label = primaryLabel(
        picked = pick.playerIds.mapNotNull { state.player(it)?.name },
        places = placedLabels(effects),
        deathLine = deathLine,
        answer = shownAnswer,
        holder = holderName,
        none = pick.none,
        skipped = skipped,
        dawn = isDawn,
        impairedHolder = if (owesFalseInfo && chosen == null) holderName else "",
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

            // ---- 4b. the question the engine is still owed, before anything else ----
            val owed = prompts.firstOrNull()
            if (owed != null) {
                NightPromptAsk(
                    viewModel = viewModel,
                    state = state,
                    prompt = owed,
                    onAnswer = { seatId -> viewModel.answerPromptWithPlayer(owed.id, seatId) },
                    onDone = { viewModel.resolvePrompt(owed.id) },
                    onShow = onShow,
                    onDismiss = { viewModel.dismissPrompt(owed.id) },
                )
                return@Column
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
            // A healthy Ravenkeeper was offered ONE truthful card and FIVE
            // ember `LIE · SHOW …` chips filling three rows above the primary,
            // with nothing to say why lying was being suggested (playtest B
            // P2 #18). The lies come out only when the engine says one is
            // owed, at most three of them, under a line that gives the reason;
            // the rest stay one tap away in the drawer.
            val liesOnCard = if (owesFalseInfo) lies.take(MAX_LIE_CHIPS) else emptyList()
            if (liesOnCard.isNotEmpty()) {
                Text(
                    text = "SHOW ONE OF THESE INSTEAD — " +
                        info?.caveats?.firstOrNull().orEmpty().ifBlank { "their ability is not working" },
                    fontSize = NIGHT_MIN_SP.sp,
                    lineHeight = nightSp(19f).sp,
                    fontWeight = FontWeight.Bold,
                    color = EmberRed,
                )
            }
            if (truthful.isNotEmpty() || liesOnCard.isNotEmpty()) {
                CardOffers(
                    offers = truthful + liesOnCard,
                    recipientId = step.holderId,
                    chosen = chosen,
                    onShow = { offer ->
                        chosen = offer
                        onShow(offer.card, step.holderId, offer.truthful)
                    },
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
                    enabled = primaryEnabled(action, pick) && !(owesFalseInfo && chosen == null),
                    holdMillis = if (isDestructive(effects) && !needsKillSheet) HOLD_CONFIRM_MILLIS else 0,
                    onConfirm = {
                        when {
                            needsKillSheet ->
                                outcomes.firstOrNull { it.second is KillOutcome.Choice }
                                    ?.let { onKillSheet(it.first, step.holderId) }

                            else -> {
                                // A primary that says SHOW performs the showing:
                                // the card goes up and the `shown:` row is
                                // written, then the step is ticked (§B.7).
                                val card = chosen?.card ?: truthCard.takeIf { !owesFalseInfo }
                                if (shownAnswer.isNotBlank() && card != null) {
                                    onShow(card, step.holderId, chosen?.truthful ?: true)
                                }
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
                    otherCards = lies - liesOnCard.toSet(),
                    onShow = { offer ->
                        chosen = offer
                        onShow(offer.card, step.holderId, offer.truthful)
                    },
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
 * A question the engine raised and is still owed, asked on the card that raised
 * it — "Fay killed themselves: a Minion becomes the Imp".
 *
 * It knows no rule. [Prompt.targetIds] are the legal answers the engine worked
 * out, [Prompt.becomesCharacterId] is what answering DOES, and
 * `answerPromptWithPlayer` applies both in one state change. A prompt the
 * storyteller rules does not apply is dismissed, and the row carries on.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NightPromptAsk(
    viewModel: GameViewModel,
    state: GameState,
    prompt: Prompt,
    onAnswer: (Long) -> Unit,
    onDone: () -> Unit,
    onShow: (ShowCard, Long?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(prompt.id) { mutableStateOf<Long?>(null) }
    val becomes = viewModel.characterById(prompt.becomesCharacterId)
    // Only a question that asks for a SEAT gets a picker. An obligation that
    // just has to be discharged — "show Ben his new character" — is a card and
    // a confirmation, not a grid of every player.
    val choices = remember(prompt.id, prompt.kind, state.players) {
        if (prompt.kind != PromptKind.CHOOSE_PLAYER) {
            emptyList()
        } else {
            val offered = prompt.targetIds.mapNotNull { state.player(it) }
            offered.ifEmpty { state.seats.filter { it.alive && it.id != prompt.subjectPlayerId } }
        }
    }
    // The token the obligation names — the heir's new "YOU ARE" card.
    val card = remember(prompt.id) {
        prompt.characterIds.firstOrNull()?.let { ShowCard.CharacterCard("YOU ARE", it) }
    }

    Text(
        text = prompt.title,
        fontSize = nightSp(18f).sp,
        lineHeight = nightSp(24f).sp,
        fontWeight = FontWeight.Bold,
        color = EmberRed,
    )
    if (prompt.detail.isNotBlank()) {
        Text(
            text = prompt.detail,
            fontSize = nightSp(16f).sp,
            lineHeight = nightSp(21f).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (choices.isEmpty()) {
        if (card != null) {
            CardOffers(
                offers = listOf(
                    UiOffer(
                        label = "SHOW: YOU ARE " +
                            (viewModel.characterById(prompt.characterIds.first())?.name ?: "?"),
                        card = card,
                        truthful = true,
                    ),
                ),
                recipientId = prompt.subjectPlayerId,
                chosen = null,
                onShow = { offer -> onShow(offer.card, prompt.subjectPlayerId, true) },
                onEdit = {},
            )
        }
        PrimaryButton(label = promptDoneLabel(card != null), onConfirm = onDone)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (seat in choices) {
            val index = state.seats.indexOfFirst { it.id == seat.id } + 1
            val character = viewModel.characterById(seat.characterId)
            NightChip(
                label = "$index ${seat.name}" + character?.let { " · ${it.name}" }.orEmpty(),
                tone = if (picked == seat.id) Tone.ACTIVE else Tone.NORMAL,
                onClick = { picked = if (picked == seat.id) null else seat.id },
            )
        }
    }
    PrimaryButton(
        label = promptPrimaryLabel(picked?.let { id -> state.player(id)?.name }, becomes?.name),
        enabled = picked != null,
        holdMillis = if (becomes == null) 0 else HOLD_CONFIRM_MILLIS,
        onConfirm = { picked?.let(onAnswer) },
    )
    NightChip(
        label = "this does not apply — put the question away",
        tone = Tone.MUTED,
        modifier = Modifier.fillMaxWidth(),
        onClick = onDismiss,
    )
}

/**
 * One card the storyteller can hold up, as the SCREEN sees it.
 *
 * The engine offers `CardOffer(label, ShowCardSpec, truthful)` and nothing else.
 * W7G gave `ShowCardSpec` its `PointCard` and `MultiTokenCard`, so the screen's
 * own `pointOffers` builder is gone: every card the storyteller can hold up is
 * decided by the engine and only DRAWN here.
 */
data class UiOffer(
    val label: String,
    val card: ShowCard,
    val truthful: Boolean,
    val editable: Boolean = true,
)

/**
 * The line above the names on a [ShowCard.PointCard].
 *
 * The wording is the engine's (`ShowCardSpec.pointPrefix`) — it decides what a
 * card says. This stays as the name the measured UI test pins.
 */
fun pointPrefix(withCharacter: Boolean, names: Int): String =
    ShowCardSpec.pointPrefix(withCharacter, names)

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
    chosen: UiOffer?,
    onShow: (UiOffer) -> Unit,
    onEdit: (ShowCard) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (offer in offers) {
            val tone = if (offer.truthful) Tone.ACTIVE else Tone.ALERT
            val card = offer.card
            val picked = chosen?.label == offer.label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.color().copy(alpha = if (picked) 0.38f else 0.16f))
                    .combinedClickable(
                        onClick = { onShow(offer) },
                        onLongClick = { if (offer.editable) onEdit(card) },
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (picked) "✓ ${offer.label}" else offer.label,
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
@Suppress("LongParameterList")
private fun SecondaryDrawer(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    attacked: List<Long>,
    hasAttack: Boolean,
    otherCards: List<UiOffer>,
    onShow: (UiOffer) -> Unit,
    onOpenShowTool: () -> Unit,
    onKillSheet: (Long, Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        if (otherCards.isNotEmpty()) {
            Text(
                text = "FALSE INFO YOU COULD SHOW INSTEAD",
                fontSize = NIGHT_MIN_SP.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CardOffers(
                offers = otherCards,
                recipientId = step.holderId,
                chosen = null,
                onShow = onShow,
                onEdit = {},
            )
        }
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
        // The generic run-book. A marker row's own words are its `prompt` and
        // are already on the card, so this no longer prints them twice.
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

/** How many false cards the card itself offers before the rest go in the drawer. */
private const val MAX_LIE_CHIPS = 3

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
