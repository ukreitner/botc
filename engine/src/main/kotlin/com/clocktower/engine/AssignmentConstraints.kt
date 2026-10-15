package com.clocktower.engine

/** Shared by the picker and resolver; an explicit storyteller ruling may override it. */
fun assignmentConstraintError(
    state: GameState,
    lookup: (String) -> Character?,
    action: ChoosePlayersAndCharacters,
    input: NightInput,
): String? {
    if (input.none || input.overrideConstraints) return null
    if (!action.matchTargetType && !action.sameTargetType &&
        !action.completeTargetGroup && !action.preserveInPlayOwners) return null
    val pairs = input.assignments
    if (pairs.isEmpty()) return "Choose the players and their new characters."
    val targets = pairs.map { (id, _) -> state.player(id) }
    val choices = pairs.map { (_, id) -> lookup(id) }
    if (targets.any { it == null } || choices.any { it == null }) {
        return "Choose a valid character for every selected player."
    }
    val targetTypes = targets.map { it?.characterId?.let(lookup)?.team }
    if (action.matchTargetType && pairs.indices.any { targetTypes[it] != choices[it]?.team }) {
        return "Minions must stay Minions and Demons must stay Demons."
    }
    if (action.sameTargetType && targetTypes.distinct().size != 1) {
        return "Choose the Minions or the Demon, never both."
    }
    if (action.completeTargetGroup) {
        when (targetTypes.distinct().singleOrNull()) {
            Team.MINION -> {
                val allMinions = state.seats.filter { it.characterId?.let(lookup)?.team == Team.MINION }.map { it.id }.toSet()
                if (pairs.map { it.first }.toSet() != allMinions) {
                    return "Assign every Minion, including any whose character stays the same."
                }
            }
            Team.DEMON -> if (pairs.size != 1) return "Choose exactly one Demon."
            else -> return "Choose Minion players or one Demon player."
        }
        if (pairs.map { Character.normalizeId(it.second) }.distinct().size != pairs.size) {
            return "Choose a different character for each player."
        }
    }
    if (action.preserveInPlayOwners && pairs.any { (targetId, chosen) ->
            state.seats.any { it.id != targetId &&
                Character.normalizeId(it.characterId.orEmpty()) == Character.normalizeId(chosen) }
        }) {
        return "An already in-play character must stay with its current player."
    }
    return null
}
