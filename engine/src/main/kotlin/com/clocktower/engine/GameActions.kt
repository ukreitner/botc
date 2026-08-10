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

    /** Adds a seat (e.g. a traveller arriving mid-game) after [afterId], or at the end. */
    fun addSeat(state: GameState, name: String, afterId: Long? = null): GameState {
        val id = (state.players.maxOfOrNull { it.id } ?: -1L) + 1
        val player = Player(id = id, name = name.ifBlank { "Player ${state.players.size + 1}" })
        val index = afterId?.let { anchor -> state.players.indexOfFirst { it.id == anchor } } ?: -1
        val players = state.players.toMutableList()
        if (index >= 0) players.add(index + 1, player) else players.add(player)
        return state.copy(players = players)
    }

    /** Removes a seat entirely (only sensible during setup or for departed travellers). */
    fun removeSeat(state: GameState, playerId: Long): GameState =
        state.copy(players = state.players.filterNot { it.id == playerId })

    /** Moves a seat one position around the circle (for seat swaps). */
    fun moveSeat(state: GameState, playerId: Long, delta: Int): GameState {
        val players = state.players.toMutableList()
        val from = players.indexOfFirst { it.id == playerId }
        if (from < 0 || players.size < 2) return state
        val to = ((from + delta) % players.size + players.size) % players.size
        val p = players.removeAt(from)
        players.add(to, p)
        return state.copy(players = players)
    }

    fun rename(state: GameState, playerId: Long, name: String): GameState =
        state.updatePlayer(playerId) { it.copy(name = name) }

    fun assignCharacter(state: GameState, playerId: Long, characterId: String?, isTraveller: Boolean = false): GameState =
        state.updatePlayer(playerId) {
            it.copy(
                characterId = characterId,
                shownCharacterId = null,
                isTraveller = isTraveller,
            )
        }

    fun setShownCharacter(state: GameState, playerId: Long, shownCharacterId: String?): GameState =
        state.updatePlayer(playerId) { it.copy(shownCharacterId = shownCharacterId) }

    /**
     * Resolves a successful Snake Charmer hit: the charmer and the chosen
     * Demon swap characters AND alignments (both revert to their new
     * character's natural alignment), and the new Snake Charmer — the former
     * Demon player — is poisoned.
     */
    fun snakeCharmerSwap(state: GameState, charmerId: Long, demonPlayerId: Long): GameState {
        var next = swapCharacters(state, charmerId, demonPlayerId)
        next = next.updatePlayer(charmerId) { it.copy(alignmentFlipped = false, shownCharacterId = null) }
        next = next.updatePlayer(demonPlayerId) { it.copy(alignmentFlipped = false, shownCharacterId = null) }
        return placeExclusiveReminder(
            next, demonPlayerId,
            PlacedReminder("snakecharmer", "Poisoned"),
        )
    }

    /**
     * Resolves a demon self-kill that passes the mantle: the demon dies and
     * the chosen heir becomes that same demon (evil). Covers the Imp
     * star-pass and the Fang Gu jump alike.
     */
    fun starPass(
        state: GameState,
        demonPlayerId: Long,
        heirPlayerId: Long,
        lookup: (String) -> Character? = { null },
    ): GameState {
        val demonCharacter = state.player(demonPlayerId)?.characterId ?: return state
        if (state.player(heirPlayerId) == null) return state
        var next = kill(state, demonPlayerId, DeathCause.OTHER_NIGHT_DEATH, lookup)
        next = next.updatePlayer(heirPlayerId) {
            it.copy(
                characterId = demonCharacter,
                shownCharacterId = null,
                alignmentFlipped = false,
            )
        }
        return next
    }

    /** Swaps two seats' characters (Barber, Snake Charmer...). */
    fun swapCharacters(state: GameState, id1: Long, id2: Long): GameState {
        val p1 = state.player(id1) ?: return state
        val p2 = state.player(id2) ?: return state
        return state
            .updatePlayer(id1) {
                it.copy(
                    characterId = p2.characterId,
                    shownCharacterId = p2.shownCharacterId,
                )
            }
            .updatePlayer(id2) {
                it.copy(
                    characterId = p1.characterId,
                    shownCharacterId = p1.shownCharacterId,
                )
            }
    }

    /**
     * Suggests 3 demon bluffs: not-in-play good characters from the script,
     * preferring two townsfolk and one outsider like most storytellers.
     */
    fun suggestBluffs(available: List<Character>, state: GameState, random: Random = Random): List<String> {
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        val townsfolk = available.filter { it.team == Team.TOWNSFOLK && it.id !in inPlay }.shuffled(random)
        val outsiders = available.filter { it.team == Team.OUTSIDER && it.id !in inPlay }.shuffled(random)
        val picks = (townsfolk.take(2) + outsiders.take(1) + townsfolk.drop(2) + outsiders.drop(1))
        return picks.take(3).map { it.id }
    }

    fun flipAlignment(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(alignmentFlipped = !it.alignmentFlipped) }

    fun setNote(state: GameState, playerId: Long, note: String): GameState =
        state.updatePlayer(playerId) { it.copy(note = note) }

    /** Kills a player, recording the cause. Dead players gain a ghost vote. */
    fun kill(
        state: GameState,
        playerId: Long,
        cause: DeathCause,
        lookup: (String) -> Character? = { null },
    ): GameState {
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
                    characterIdAtDeath = player.characterId,
                    abilityImpairedAtDeath = StatusEffects.isImpaired(state, lookup, player),
                ),
            )
    }

    /**
     * Undo a mistaken death: the most recent death record is DROPPED, as if
     * it never happened. For in-game resurrection use [resurrect].
     */
    fun revive(state: GameState, playerId: Long): GameState {
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId }
        return state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(deaths = state.deaths.filterIndexed { i, _ -> i != lastDeath })
    }

    /**
     * In-game resurrection (Professor, Shabaloth regurgitation, Bone
     * Collector...): the player lives again but the death record STAYS in
     * the log, marked resurrected — Undertaker/Cannibal history survives.
     */
    fun resurrect(state: GameState, playerId: Long): GameState {
        val lastDeath = state.deaths.indexOfLast { it.playerId == playerId && !it.resurrected }
        return state.updatePlayer(playerId) { it.copy(alive = true, ghostVoteUsed = false) }
            .copy(
                deaths = state.deaths.mapIndexed { i, d ->
                    if (i == lastDeath) d.copy(resurrected = true) else d
                },
            )
    }

    fun toggleGhostVote(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(ghostVoteUsed = !it.ghostVoteUsed) }

    fun addReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState =
        state.updatePlayer(playerId) { it.copy(reminders = it.reminders + reminder) }

    /**
     * Places a reminder that only exists once in the grimoire (Poisoner's
     * poison, Monk's Safe...): removes the same token from every other seat
     * first, so nightly choices move instead of accumulating.
     */
    fun placeExclusiveReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState {
        val cleared = state.copy(
            players = state.players.map { p ->
                p.copy(reminders = p.reminders.filterNot { it.sourceId == reminder.sourceId && it.label == reminder.label })
            },
        )
        return addReminder(cleared, playerId, reminder)
    }

    fun removeReminder(state: GameState, playerId: Long, index: Int): GameState =
        state.updatePlayer(playerId) {
            it.copy(reminders = it.reminders.filterIndexed { i, _ -> i != index })
        }

    fun setBluffs(state: GameState, bluffIds: List<String>): GameState =
        state.copy(demonBluffIds = bluffIds.take(3))

    fun setFabled(state: GameState, fabledIds: List<String>): GameState =
        state.copy(fabledIds = fabledIds)

    /**
     * Reminder tokens whose effect only lasts the night: removed at dawn,
     * exactly like sweeping them off the physical grimoire.
     */
    private val EXPIRES_AT_DAWN: Set<Pair<String, String>> = setOf(
        "monk" to "Safe",
        "innkeeper" to "Protected",
        "exorcist" to "Chosen",
        "lunatic" to "Attack 1",
        "lunatic" to "Attack 2",
        "lunatic" to "Attack 3",
    )

    /**
     * Reminder tokens that last "tonight and tomorrow day": removed at
     * dusk, right before their source picks a new target.
     */
    private val EXPIRES_AT_DUSK: Set<Pair<String, String>> = setOf(
        "poisoner" to "Poisoned",
        "sailor" to "Drunk",
        "innkeeper" to "Drunk",
        "butler" to "Master",
        "devilsadvocate" to "Survives execution",
        "witch" to "Cursed",
        "cerenovus" to "Mad",
        "harpy" to "Mad",
        "harpy" to "2nd",
        "goblin" to "Claimed",
    )

    private fun clearEphemeral(state: GameState, table: Set<Pair<String, String>>): GameState =
        state.copy(
            players = state.players.map { player ->
                player.copy(
                    reminders = player.reminders.filterNot { (it.sourceId to it.label) in table },
                )
            },
        )

    /**
     * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> DAY 2 -> ...
     * Day- and night-scoped reminder tokens expire automatically at the
     * transition (undoable, and re-placeable by hand like everything else).
     */
    fun advancePhase(state: GameState): GameState = when (state.phase) {
        Phase.SETUP -> state.copy(phase = Phase.NIGHT, cycle = 1, nightStepsDone = emptySet())
        Phase.NIGHT -> clearEphemeral(state, EXPIRES_AT_DAWN).copy(phase = Phase.DAY)
        Phase.DAY -> clearEphemeral(state, EXPIRES_AT_DUSK)
            .copy(phase = Phase.NIGHT, cycle = state.cycle + 1, nightStepsDone = emptySet())
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
     * Who is currently on the block today, derived from the nomination
     * sequence: a passing tally that beats the previous highest puts its
     * nominee on the block; a later equal tally clears the block (tie).
     */
    fun aboutToDie(state: GameState): Long? {
        var onBlock: Long? = null
        for (n in state.nominations.filter { it.day == state.cycle && !it.isExile }) {
            when (n.result) {
                NominationResult.ABOUT_TO_DIE -> onBlock = n.nomineeId
                NominationResult.TIED -> onBlock = null
                else -> Unit
            }
        }
        return onBlock
    }

    /**
     * Randomly deals characters to non-Traveller seats from [bag]. A size
     * mismatch is rejected before any assignments are produced, preventing a
     * partial re-deal from retaining stale characters on leftover seats.
     */
    fun deal(state: GameState, bag: List<String>, random: Random = Random): GameState {
        val recipientCount = state.players.count { !it.isTraveller }
        require(bag.size == recipientCount) {
            "Bag has ${bag.size} characters for $recipientCount non-Traveller players"
        }
        val shuffled = bag.shuffled(random)
        var i = 0
        return state.copy(
            players = state.players.map { p ->
                if (p.isTraveller) {
                    p
                } else {
                    p.copy(characterId = shuffled[i++], shownCharacterId = null)
                }
            },
        )
    }

    /**
     * Picks a random legal bag for [playerCount] from the script's
     * characters, folding in setup modifiers as they are drawn (demons
     * first, then minions, outsiders, townsfolk) and reconciling companions
     * ([+the Damsel], [+the King]) and count drift afterwards. Returns null
     * when the script can't fill the distribution.
     */
    fun randomBag(
        available: List<Character>,
        playerCount: Int,
        random: Random = Random,
        attempts: Int = 200,
    ): List<Character>? {
        val byTeam = available.groupBy { it.team }
        val teamsInOrder = listOf(Team.DEMON, Team.MINION, Team.OUTSIDER, Team.TOWNSFOLK)

        repeat(attempts) {
            var dist = Setup.distributionFor(playerCount)
            val picked = linkedMapOf<Team, MutableList<Character>>()

            // Draw team by team, folding each drawn character's modifier
            // before the next team (and next member) is drawn.
            for (team in teamsInOrder) {
                val pool = byTeam[team].orEmpty().shuffled(random)
                val chosen = mutableListOf<Character>()
                for (candidate in pool) {
                    if (chosen.size >= dist.count(team)) break
                    chosen += candidate
                    Setup.modifierFor(candidate)?.let { dist += it }
                }
                picked[team] = chosen
            }

            // Reconcile: force required companions in, then trim/fill drift
            // caused by modifiers of later-drawn characters.
            var bag = picked.values.flatten().toMutableList()
            repeat(4) {
                for (c in bag.toList()) {
                    val companionId = Setup.modifierFor(c)?.requiredCompanionId ?: continue
                    if (bag.none { it.id == companionId }) {
                        available.find { it.id == companionId }?.let { bag.add(it) }
                    }
                }
                val target = Setup.adjustedDistribution(playerCount, bag)
                for (team in teamsInOrder) {
                    val members = bag.filter { it.team == team }
                    var excess = members.size - target.count(team)
                    if (excess > 0) {
                        // Never trim characters that modify setup or are
                        // required companions — trim plain members instead.
                        val required = bag.mapNotNull { Setup.modifierFor(it)?.requiredCompanionId }.toSet()
                        for (m in members.shuffled(random)) {
                            if (excess == 0) break
                            if (m.setup || m.id in required) continue
                            bag.remove(m); excess--
                        }
                    } else if (excess < 0) {
                        val unused = byTeam[team].orEmpty().filter { c -> bag.none { it.id == c.id } }
                        for (m in unused.shuffled(random)) {
                            if (excess == 0) break
                            bag.add(m); excess++
                        }
                    }
                }
            }

            if (bag.size == playerCount && validateBag(bag, playerCount).isEmpty()) {
                return bag
            }
        }
        return null
    }

    private fun Distribution.count(team: Team): Int = when (team) {
        Team.TOWNSFOLK -> townsfolk
        Team.OUTSIDER -> outsiders
        Team.MINION -> minions
        Team.DEMON -> demons
        else -> 0
    }

    /** Ids that may legally appear multiple times in a bag. */
    val DUPLICABLE = setOf("villageidiot", "legion", "riot")

    /**
     * Human-readable problems with a proposed bag, empty when legal.
     * Active Fabled ([fabledIds]) that legally bend the distribution are
     * honoured — the Sentinel allows one extra or one fewer Outsider.
     */
    fun validateBag(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: Collection<String> = emptyList(),
        /** House rule: any character may appear multiple times. */
        allowAnyDuplicates: Boolean = false,
    ): List<String> {
        val issues = mutableListOf<String>()
        if (bag.size != playerCount) {
            issues += "Bag has ${bag.size} characters for $playerCount players"
        }
        val modifiers = bag.mapNotNull { Setup.modifierFor(it) }
        val unboundedChoiceTeams = modifiers
            .flatMap { it.choiceTeams - it.choiceDeltas.keys }
            .toSet()
        val relaxedTeams = buildSet {
            addAll(unboundedChoiceTeams)
            // A variable Outsider/Minion/Demon count is paid for by the
            // Townsfolk count, so both sides of that trade must be flexible.
            if (any { it == Team.OUTSIDER || it == Team.MINION || it == Team.DEMON }) {
                add(Team.TOWNSFOLK)
            }
        }
        var allowedDistributions = Setup.allowedDistributions(playerCount, bag)
        if ("sentinel" in fabledIds.map { Character.normalizeId(it) }) {
            allowedDistributions = allowedDistributions
                .flatMap { d ->
                    listOf(
                        d,
                        d.copy(outsiders = d.outsiders + 1, townsfolk = d.townsfolk - 1),
                        d.copy(outsiders = d.outsiders - 1, townsfolk = d.townsfolk + 1),
                    )
                }
                .filter { it.outsiders >= 0 && it.townsfolk >= 0 }
                .toSet()
        }
        val counts = bag.groupingBy { it.team }.eachCount()
        val checkedTeams = listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)
            .filterNot { it in relaxedTeams }
        val matchesAllowedDistribution = allowedDistributions.any { distribution ->
            checkedTeams.all { team ->
                (counts[team] ?: 0) == distribution.count(team)
            }
        }
        if (!matchesAllowedDistribution) {
            var explained = false
            for (team in checkedTeams) {
                val actual = counts[team] ?: 0
                val expected = allowedDistributions.map { it.count(team) }.distinct().sorted()
                if (actual !in expected) {
                    val expectedText = expected.joinToString(" or ")
                    issues += "${team.displayName}: $actual in bag, expected $expectedText"
                    explained = true
                }
            }
            if (!explained) {
                issues += "Team counts do not form one legal setup-modifier combination"
            }
        }

        for (mod in modifiers) {
            val companion = mod.requiredCompanionId ?: continue
            if (bag.none { it.id == companion }) {
                issues += "${mod.characterId} requires the $companion in the bag [${mod.text}]"
            }
        }

        val dupes = if (allowAnyDuplicates) emptyMap() else bag.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        for ((id, n) in dupes) {
            if (id !in DUPLICABLE) {
                issues += "$id appears $n times"
            } else if (id == "villageidiot" && n > 3) {
                issues += "villageidiot appears $n times, maximum 3"
            }
        }
        return issues
    }

    /**
     * Validates both the bag and mandatory first-night setup choices. This is
     * used again at the phase boundary so manually assigned games cannot
     * bypass setup requirements.
     */
    fun validateSetupState(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<String> {
        val residents = state.players.filterNot { it.isTraveller }
        val characters = residents.mapNotNull { player ->
            player.characterId?.let(lookup)
        }
        val issues = validateBag(characters, residents.size, state.fabledIds).toMutableList()
        val inPlayIds = residents.mapNotNull { it.characterId }.toSet()

        for (player in residents) {
            val shown = player.shownCharacterId?.let(lookup)
            when (player.characterId) {
                "drunk" -> if (shown == null || shown.team != Team.TOWNSFOLK ||
                    shown.id in inPlayIds
                ) {
                    issues += "${player.name}: choose a not-in-play Townsfolk token to show the Drunk"
                }
                "lunatic" -> if (shown?.team != Team.DEMON) {
                    issues += "${player.name}: choose the Demon token shown to the Lunatic"
                }
                "marionette" -> {
                    if (shown == null || shown.team.isEvil || !shown.team.isTownResident ||
                        shown.id in inPlayIds
                    ) {
                        issues += "${player.name}: choose a not-in-play good token to show the Marionette"
                    }
                    val index = state.players.indexOfFirst { it.id == player.id }
                    val neighbours = if (index >= 0 && state.players.size > 1) {
                        listOf(
                            state.players[(index - 1 + state.players.size) % state.players.size],
                            state.players[(index + 1) % state.players.size],
                        )
                    } else {
                        emptyList()
                    }
                    if (neighbours.none { it.characterId?.let(lookup)?.team == Team.DEMON }) {
                        issues += "${player.name}: the Marionette must neighbor the Demon"
                    }
                }
            }
        }

        if (residents.any { it.characterId == "fortuneteller" }) {
            val herringSeats = state.players.filter { player ->
                player.reminders.any {
                    it.sourceId == "fortuneteller" && it.label.equals("Red herring", true)
                }
            }
            when {
                herringSeats.size != 1 ->
                    issues += "Fortune Teller: choose exactly one good red herring"
                herringSeats.single().isEvil(lookup) ->
                    issues += "Fortune Teller: the red herring must be a good player"
            }
        }
        return issues.distinct()
    }
}
