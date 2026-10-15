package com.clocktower.grimoire.ui.screens.night

import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.ChoosePlayersAndCharacters
import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.NightAction
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightInput
import com.clocktower.engine.Options
import com.clocktower.engine.Ref
import com.clocktower.engine.Sequence
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.YesNo
import com.clocktower.engine.assignmentConstraintError

/** Passes have no residual choices: a stale yes must never fire a later stage. */
fun PickState.toNightInput(overrideConstraints: Boolean = false): NightInput =
    if (none) NightInput(none = true, overrideConstraints = overrideConstraints || this.overrideConstraints) else NightInput(
        playerIds = playerIds,
        characterIds = characterIds,
        assignments = assignments,
        yes = yes,
        optionId = optionId,
        optionIds = optionIds,
        overrideConstraints = overrideConstraints || this.overrideConstraints,
    )

fun assignmentProblem(action: NightAction?, pick: PickState, state: GameState, lookup: (String) -> Character?): String? =
    if (action is ChoosePlayersAndCharacters) assignmentConstraintError(state, lookup, action, pick.toNightInput()) else null

fun allowsNoChoice(action: NightAction?): Boolean = when (action) {
    is ChoosePlayers -> action.allowNone
    is ChooseCharacter -> action.allowNone
    is ChoosePlayersAndCharacters -> action.allowNone
    is Sequence -> action.stages.any(::allowsNoChoice)
    else -> false
}

/** The engine has one shared target/character/yes field, and independent option stages. */
fun supportedNightAction(action: NightAction?): Boolean = when (action) {
    is Sequence -> action.stages.isNotEmpty() &&
        action.stages.none { it is Sequence } &&
        action.stages.count { picksAllowed(it) > 0 } <= 1 &&
        action.stages.count { it is ChooseCharacter || it is ChoosePlayerAndCharacter || it is ChoosePlayersAndCharacters } <= 1 &&
        action.stages.count { it is YesNo } <= 1 &&
        action.stages.filterIsInstance<Options>().all { options ->
            options.options.all { it.targetIds.isEmpty() && it.characterIds.isEmpty() }
        }
    else -> true
}

fun nightActionInputComplete(action: NightAction?, pick: PickState): Boolean {
    if (!supportedNightAction(action)) return false
    if (pick.none) return allowsNoChoice(action)
    return when (action) {
        null -> true
        is YesNo -> pick.yes != null
        is ChooseCharacter -> pick.characterIds.size == 1 && pick.characterIds.single().isNotBlank()
        is ChoosePlayers -> pick.playerIds.distinct().size == pick.playerIds.size &&
            pick.playerIds.size in action.min.coerceAtLeast(1)..action.max
        is ChoosePlayerAndCharacter -> pick.playerIds.size == 1 &&
            pick.characterIds.size == 1 && pick.characterIds.single().isNotBlank()
        is ChoosePlayersAndCharacters -> pick.assignments.size in action.min.coerceAtLeast(1)..action.max &&
            pick.assignments.all { it.second.isNotBlank() } &&
            pick.assignments.map { it.first }.distinct().size == pick.assignments.size
        is Options -> action.options.any { it.id == pick.optionId }
        is Sequence -> action.stages.indices.all { index ->
            nightActionInputComplete(action.stages[index], sequenceStagePick(action, pick, index))
        }
        is ShowInfo -> pick.playerIds.size == action.targetsNeeded.coerceAtLeast(0)
    }
}

fun sequenceStagePick(action: Sequence, pick: PickState, index: Int): PickState =
    if (action.stages.getOrNull(index) is Options) {
        val optionIndex = action.stages.take(index).count { it is Options }
        pick.copy(optionId = pick.optionIds.getOrNull(optionIndex).orEmpty())
    } else pick

fun updateSequenceStagePick(action: Sequence, pick: PickState, index: Int, stagePick: PickState): PickState {
    if (stagePick.none) return PickState(none = true)
    return when (action.stages.getOrNull(index)) {
        is Options -> {
            val optionIndex = action.stages.take(index).count { it is Options }
            val answers = List(action.stages.count { it is Options }) { pick.optionIds.getOrNull(it).orEmpty() }
                .toMutableList()
            answers[optionIndex] = stagePick.optionId
            pick.copy(optionIds = answers, none = false)
        }
        is YesNo -> pick.copy(yes = stagePick.yes, none = false)
        else -> {
            // A new target/guess needs new follow-up answers, rather than the
            // previous player's verdict or live/die answers.
            val changed = pick.playerIds != stagePick.playerIds || pick.characterIds != stagePick.characterIds ||
                pick.assignments != stagePick.assignments
            stagePick.copy(yes = if (changed) null else pick.yes,
                optionIds = if (changed) emptyList() else pick.optionIds, none = false)
        }
    }
}

fun setAssignmentPlayers(pick: PickState, playerIds: List<Long>): PickState = pick.copy(
    playerIds = playerIds,
    assignments = playerIds.distinct().map { id -> id to pick.assignments.firstOrNull { it.first == id }?.second.orEmpty() },
    none = false,
)

fun setAssignedCharacter(pick: PickState, playerId: Long, characterId: String): PickState = pick.copy(
    assignments = pick.assignments.map { if (it.first == playerId) playerId to characterId else it },
    none = false,
)

/** Preview only the branch the storyteller selected, never its opposite. */
fun pickedActionEffects(action: NightAction?, pick: PickState): List<NightEffect> = when (action) {
    null, is ShowInfo -> emptyList()
    is ChoosePlayers -> if (pick.none) action.onNone else action.perTarget + action.onResolve
    is ChooseCharacter -> if (pick.none) action.onNone else action.onResolve
    is ChoosePlayerAndCharacter -> if (pick.none) action.onNone else action.onResolve
    is ChoosePlayersAndCharacters -> if (pick.none) action.onNone else action.perPair + action.onResolve
    is YesNo -> when (pick.yes) { true -> action.onYes; false -> action.onNo; null -> emptyList() }
    is Options -> action.options.firstOrNull { it.id == pick.optionId }?.effects.orEmpty()
    is Sequence -> action.stages.flatMapIndexed { index, stage ->
        pickedActionEffects(stage, sequenceStagePick(action, if (pick.none) PickState(none = true) else pick, index))
    }
}

/** Concrete attack recipients; self-directed effects must name their holder. */
fun attackTargetIds(ref: Ref, holderId: Long?, pickedIds: List<Long>): List<Long> = when (ref) {
    Ref.Source -> listOfNotNull(holderId)
    Ref.Target, Ref.AllTargets -> pickedIds
    is Ref.TargetN -> listOfNotNull(pickedIds.getOrNull(ref.index))
    is Ref.Seat -> listOf(ref.playerId)
    else -> emptyList()
}

/** A concise consequence label for answers that do not name a player directly. */
fun pickedOptionLabels(action: NightAction?, pick: PickState): List<String> = when (action) {
    is Options -> action.options.firstOrNull { it.id == pick.optionId }?.let { listOf(it.label) }.orEmpty()
    is YesNo -> when (pick.yes) { true -> listOf(action.yesLabel); false -> listOf(action.noLabel); null -> emptyList() }
    is Sequence -> action.stages.flatMapIndexed { index, stage -> pickedOptionLabels(stage, sequenceStagePick(action, pick, index)) }
    else -> emptyList()
}
