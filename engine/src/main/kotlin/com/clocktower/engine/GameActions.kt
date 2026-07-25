package com.clocktower.engine

import kotlin.random.Random

/**
 * Pure state-transition helpers. Every function returns a new [GameState];
 * nothing here mutates. The UI layer persists whatever comes back.
 */
object GameActions {

    fun newGame(script: Script, playerNames: List<String>): GameState = GameState(
        script = script,
        players = playerNames.mapIndexed { index, name ->
            Player(id = index.toLong(), name = name.ifBlank { "Player ${index + 1}" })
        },
    )

    fun rename(state: GameState, playerId: Long, name: String): GameState =
        state.updatePlayer(playerId) { it.copy(name = name) }

    fun assignCharacter(state: GameState, playerId: Long, characterId: String?, isTraveller: Boolean = false): GameState =
        state.updatePlayer(playerId) { it.copy(characterId = characterId, isTraveller = isTraveller) }

    fun flipAlignment(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(alignmentFlipped = !it.alignmentFlipped) }

    fun setNote(state: GameState, playerId: Long, note: String): GameState =
        state.updatePlayer(playerId) { it.copy(note = note) }

    /** Kills a player, recording the cause. Dead players gain a ghost vote. */
    fun kill(state: GameState, playerId: Long, cause: DeathCause): GameState {
        val player = state.player(playerId) ?: return state
        if (!player.alive) return state
        return state
            .updatePlayer(playerId) { it.copy(alive = false, ghostVoteUsed = false) }
            .copy(
                deaths = state.deaths + DeathRecord(
                    playerId = playerId,
                    day = state.cycle,
                    atNight = state.phase == Phase.NIGHT,
                    cause = cause,
                ),
            )
    }

    /** Brings a player back to life (Professor, storyteller correction...). */
    fun revive(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(deaths = state.deaths.filterNot { it.playerId == playerId && it.day == state.cycle })

    fun toggleGhostVote(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(ghostVoteUsed = !it.ghostVoteUsed) }

    fun addReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState =
        state.updatePlayer(playerId) { it.copy(reminders = it.reminders + reminder) }

    fun removeReminder(state: GameState, playerId: Long, index: Int): GameState =
        state.updatePlayer(playerId) {
            it.copy(reminders = it.reminders.filterIndexed { i, _ -> i != index })
        }

    fun setBluffs(state: GameState, bluffIds: List<String>): GameState =
        state.copy(demonBluffIds = bluffIds.take(3))

    fun setFabled(state: GameState, fabledIds: List<String>): GameState =
        state.copy(fabledIds = fabledIds)

    /** SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> DAY 2 -> ... */
    fun advancePhase(state: GameState): GameState = when (state.phase) {
        Phase.SETUP -> state.copy(phase = Phase.NIGHT, cycle = 1, nightStepsDone = emptySet())
        Phase.NIGHT -> state.copy(phase = Phase.DAY)
        Phase.DAY -> state.copy(phase = Phase.NIGHT, cycle = state.cycle + 1, nightStepsDone = emptySet())
    }

    fun toggleNightStep(state: GameState, stepId: String): GameState =
        state.copy(
            nightStepsDone = if (stepId in state.nightStepsDone) {
                state.nightStepsDone - stepId
            } else {
                state.nightStepsDone + stepId
            },
        )

    fun recordNomination(state: GameState, nomination: Nomination): GameState =
        state.copy(nominations = state.nominations + nomination)

    /** Highest passing vote tally so far today (for tie/beat logic). */
    fun highestVotesToday(state: GameState): Int =
        state.nominations
            .filter { it.day == state.cycle && !it.isExile }
            .filter { it.result == NominationResult.ABOUT_TO_DIE || it.result == NominationResult.TIED }
            .maxOfOrNull { it.votes } ?: 0

    /** Players a nominator hasn't yet nominated today, per one-nomination rules. */
    fun hasNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nominatorId == playerId && !it.isExile }

    fun hasBeenNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nomineeId == playerId && !it.isExile }

    /**
     * Randomly deals characters to seats from [bag]. The bag must already
     * match the player count; extra players stay unassigned.
     */
    fun deal(state: GameState, bag: List<String>, random: Random = Random): GameState {
        val shuffled = bag.shuffled(random)
        var i = 0
        return state.copy(
            players = state.players.map { p ->
                if (p.isTraveller || i >= shuffled.size) p else p.copy(characterId = shuffled[i++])
            },
        )
    }

    /**
     * Picks a random legal bag for [playerCount] from [script] characters:
     * correct base counts, adjusted by setup modifiers of the drawn set.
     * Retries until the adjusted distribution is satisfied or attempts run out;
     * returns null when the script can't fill the distribution.
     */
    fun randomBag(
        available: List<Character>,
        playerCount: Int,
        random: Random = Random,
        attempts: Int = 200,
    ): List<Character>? {
        val townsfolk = available.filter { it.team == Team.TOWNSFOLK }
        val outsiders = available.filter { it.team == Team.OUTSIDER }
        val minions = available.filter { it.team == Team.MINION }
        val demons = available.filter { it.team == Team.DEMON }

        repeat(attempts) {
            val base = Setup.distributionFor(playerCount)
            // Draw evil + outsiders first, then re-check against modifiers.
            val demon = demons.shuffled(random).take(base.demons)
            val minion = minions.shuffled(random).take(base.minions)
            var dist = base
            (demon + minion).forEach { c -> Setup.modifierFor(c)?.let { dist += it } }

            val outsider = outsiders.shuffled(random).take(dist.outsiders.coerceAtLeast(0))
            outsider.forEach { c -> Setup.modifierFor(c)?.let { dist += it } }

            val town = townsfolk.shuffled(random).take(dist.townsfolk.coerceAtLeast(0))
            town.forEach { c -> Setup.modifierFor(c)?.let { dist += it } }

            val bag = town + outsider + minion + demon
            if (bag.size == playerCount && validateBag(bag, playerCount).isEmpty()) {
                return bag
            }
        }
        return null
    }

    /** Human-readable problems with a proposed bag, empty when legal. */
    fun validateBag(bag: List<Character>, playerCount: Int): List<String> {
        val issues = mutableListOf<String>()
        if (bag.size != playerCount) {
            issues += "Bag has ${bag.size} characters for $playerCount players"
        }
        val dist = Setup.adjustedDistribution(playerCount, bag)
        val counts = bag.groupingBy { it.team }.eachCount()
        fun check(team: Team, expected: Int) {
            val actual = counts[team] ?: 0
            if (actual != expected) {
                val hasChoice = bag.any { Setup.modifierFor(it)?.choice == true }
                if (!hasChoice) {
                    issues += "${team.displayName}: $actual in bag, expected $expected"
                }
            }
        }
        check(Team.TOWNSFOLK, dist.townsfolk)
        check(Team.OUTSIDER, dist.outsiders)
        check(Team.MINION, dist.minions)
        check(Team.DEMON, dist.demons)
        val dupes = bag.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        for ((id, n) in dupes) {
            // Village Idiot legally duplicates; anything else is a mistake.
            if (id != "villageidiot" && id != "legion" && id != "riot") {
                issues += "$id appears $n times"
            }
        }
        return issues
    }
}
