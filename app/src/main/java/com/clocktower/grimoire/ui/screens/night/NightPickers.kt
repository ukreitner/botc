package com.clocktower.grimoire.ui.screens.night

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.Effects
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.GameState
import com.clocktower.engine.Memory
import com.clocktower.engine.NightAction
import com.clocktower.engine.NightStep
import com.clocktower.engine.Sequence
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.Team
import com.clocktower.engine.YesNo
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.components.StatusPip
import com.clocktower.grimoire.ui.components.visiblePips
import com.clocktower.grimoire.ui.theme.AgedGold

/** How many status pips one seat cell has room for beside its token. */
private const val PIP_BUDGET = 3

/**
 * Every seat, annotated with what this step's constraints make of it.
 *
 * "Different from last night" and "never the same twice" read the LEDGER
 * (`Memory`), not the grimoire, so they survive the dawn token sweep — which is
 * the Devil's Advocate and Exorcist bug the friction log opens with.
 */
fun seatOptions(
    state: GameState,
    lookup: (String) -> Character?,
    step: NightStep,
): List<SeatOption> {
    val holderId = step.holderId
    val lastNight = Memory.forbiddenTargets(state, step.abilityId, holderId)
    val everChosen = Memory.everChosen(state, step.abilityId, holderId)
    val neighbours = holderId?.let { state.seatNeighbours(it).map { p -> p.id }.toSet() }.orEmpty()
    return state.seats.mapIndexed { index, player ->
        SeatOption(
            id = player.id,
            seat = index + 1,
            name = player.name,
            alive = player.alive,
            self = player.id == holderId,
            team = player.characterId?.let(lookup)?.team,
            evil = player.isEvil(lookup),
            traveller = player.isTraveller,
            chosenLastNight = player.id in lastNight,
            chosenBefore = player.id in everChosen,
            neighbour = player.id in neighbours,
        )
    }
}

/** The constraints one action puts on its player picks. */
fun constraintsOf(action: NightAction?): List<TargetConstraint> = when (action) {
    is ChoosePlayers -> action.constraints
    is ChoosePlayerAndCharacter -> action.playerConstraints
    is ShowInfo -> action.constraints
    is Sequence -> action.stages.flatMap { constraintsOf(it) }
    else -> emptyList()
}

/** The seat order this action asks for. */
fun sortOf(action: NightAction?): TargetSort =
    (action as? ChoosePlayers)?.sort ?: TargetSort.SEAT_ORDER

/**
 * THE picker. One component for every ask on the night sheet, replacing the
 * four inconsistent ones the screen used to carry (defect #13) and the
 * horizontally scrolling seat row that had to be dragged to reach seat 11 of 12
 * in the dark (defect #12).
 */
@Composable
fun NightAsk(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    action: NightAction,
    pick: PickState,
    onPick: (PickState) -> Unit,
) {
    val label = action.prompt.ifBlank { "WHAT DID THEY CHOOSE?" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = nightSp(16f).sp,
            lineHeight = nightSp(21f).sp,
            fontWeight = FontWeight.Bold,
            color = AgedGold,
        )
        if (action is YesNo) {
            YesNoAsk(action, pick, onPick)
        }
        if (picksAllowed(action) > 0) {
            SeatGrid(viewModel, state, step, action, pick, onPick)
        }
        if (action is ChooseCharacter || action is ChoosePlayerAndCharacter) {
            CharacterAsk(viewModel, state, action, pick, onPick)
        }
        val allowsNone = (action as? ChoosePlayers)?.allowNone == true ||
            (action as? ChooseCharacter)?.allowNone == true
        if (allowsNone) {
            val noneLabel = (action as? ChoosePlayers)?.noneLabel ?: "They chose nobody"
            NightChip(
                label = if (pick.none) "✓ $noneLabel" else noneLabel,
                tone = if (pick.none) Tone.ACTIVE else Tone.MUTED,
                onClick = {
                    onPick(if (pick.none) pick.copy(none = false) else PickState(none = true))
                },
            )
        }
    }
}

@Composable
private fun YesNoAsk(action: YesNo, pick: PickState, onPick: (PickState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for ((yes, text) in listOf(true to action.yesLabel, false to action.noLabel)) {
            Box(Modifier.weight(1f)) {
                NightChip(
                    label = if (pick.yes == yes) "✓ $text" else text,
                    tone = if (pick.yes == yes) Tone.ACTIVE else Tone.NORMAL,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPick(pick.copy(yes = yes, none = false)) },
                )
            }
        }
    }
}

/**
 * Seats as a two-column grid of 64 dp rows in seat order, with the seat number
 * always visible. Seats this ability may not choose sit under a disclosure with
 * the reason — never selectable-then-disabled (defect #14).
 */
@Composable
private fun SeatGrid(
    viewModel: GameViewModel,
    state: GameState,
    step: NightStep,
    action: NightAction,
    pick: PickState,
    onPick: (PickState) -> Unit,
) {
    val constraints = constraintsOf(action)
    val options = remember(state, step.key.token, action) {
        sortOptions(seatOptions(state, viewModel::characterById, step), sortOf(action))
    }
    val max = picksAllowed(action)
    val (open, blocked) = options.partition { blockedBecause(it, constraints) == null }
    var showBlocked by remember(step.key.token) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (pair in open.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (option in pair) {
                    SeatCell(
                        viewModel = viewModel,
                        state = state,
                        option = option,
                        selected = option.id in pick.playerIds,
                        blockedReason = null,
                        modifier = Modifier.weight(1f),
                    ) {
                        onPick(
                            pick.copy(
                                playerIds = togglePick(pick.playerIds, option.id, max),
                                none = false,
                            ),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (blocked.isNotEmpty()) {
            NightChip(
                label = if (showBlocked) "⌃ hide ${blocked.size} they cannot choose" else "⌄ ${blocked.size} they cannot choose",
                tone = Tone.MUTED,
                onClick = { showBlocked = !showBlocked },
            )
            if (showBlocked) {
                for (pair in blocked.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (option in pair) {
                            SeatCell(
                                viewModel = viewModel,
                                state = state,
                                option = option,
                                selected = false,
                                blockedReason = blockedBecause(option, constraints),
                                modifier = Modifier.weight(1f),
                                onClick = null,
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatCell(
    viewModel: GameViewModel,
    state: GameState,
    option: SeatOption,
    selected: Boolean,
    blockedReason: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val player = state.player(option.id)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(
                if (selected) AgedGold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) AgedGold else androidx.compose.ui.graphics.Color.Transparent,
                shape = shape,
            )
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        CharacterToken(
            character = viewModel.characterById(player?.characterId),
            size = 34.dp,
            dimmed = !option.alive || blockedReason != null,
        )
        Spacer(Modifier.width(6.dp))
        // WP10's status pips, so "she is marked SAFE" is visible while the
        // storyteller is choosing rather than after they have chosen.
        val pips = remember(state, option.id) {
            visiblePips(
                Effects.rendered(state, viewModel::characterById, option.id).map { it.group },
                budget = PIP_BUDGET,
            )
        }
        if (pips.shown.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (group in pips.shown) StatusPip(group = group, size = 14.dp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "${option.seat}  ${option.name}",
                fontSize = nightSp(16f).sp,
                lineHeight = nightSp(20f).sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (blockedReason == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textDecoration = if (option.chosenLastNight) TextDecoration.LineThrough else null,
                maxLines = 2,
            )
            val caption = buildList {
                if (option.self) add("◆ themselves")
                if (!option.alive) add("dead")
                if (option.chosenLastNight) add("last night")
                blockedReason?.let { add(it) }
            }.distinct().joinToString(" · ")
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = NIGHT_MIN_SP.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** The character half of a Pit-Hag / Cerenovus / Engineer style ask. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterAsk(
    viewModel: GameViewModel,
    state: GameState,
    action: NightAction,
    pick: PickState,
    onPick: (PickState) -> Unit,
) {
    val pool = when (action) {
        is ChooseCharacter -> action.pool
        is ChoosePlayerAndCharacter -> action.pool
        else -> CharacterPool.SCRIPT
    }
    val inPlay = state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
    val candidates = remember(state.script, pool, inPlay) {
        viewModel.gameData.resolve(state.script)
            .filter { it.team != Team.FABLED }
            .filter { character ->
                when (pool) {
                    CharacterPool.SCRIPT -> true
                    CharacterPool.GOOD -> character.team == Team.TOWNSFOLK || character.team == Team.OUTSIDER
                    CharacterPool.EVIL -> character.team == Team.MINION || character.team == Team.DEMON
                    CharacterPool.TOWNSFOLK -> character.team == Team.TOWNSFOLK
                    CharacterPool.OUTSIDER -> character.team == Team.OUTSIDER
                    CharacterPool.MINION -> character.team == Team.MINION
                    CharacterPool.DEMON -> character.team == Team.DEMON
                    CharacterPool.NOT_IN_PLAY -> Character.normalizeId(character.id) !in inPlay
                }
            }
            .sortedWith(compareBy({ it.team.ordinal }, { it.name }))
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (character in candidates) {
            val selected = character.id in pick.characterIds
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) AgedGold.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .clickable {
                        onPick(
                            pick.copy(
                                characterIds = if (selected) emptyList() else listOf(character.id),
                                none = false,
                            ),
                        )
                    }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            ) {
                CharacterToken(character = character, size = 46.dp)
                Text(
                    text = character.name,
                    fontSize = NIGHT_MIN_SP.sp,
                    lineHeight = nightSp(16f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}
