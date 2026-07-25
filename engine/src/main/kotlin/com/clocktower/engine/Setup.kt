package com.clocktower.engine

import kotlinx.serialization.Serializable

/** Base role distribution for a given number of (non-traveller) players. */
@Serializable
data class Distribution(
    val townsfolk: Int,
    val outsiders: Int,
    val minions: Int,
    val demons: Int,
) {
    val total: Int get() = townsfolk + outsiders + minions + demons

    /**
     * Applies a setup modifier the way the physical game does: deltas trade
     * against the Townsfolk count so the total stays equal to the player
     * count, and no team can go below zero (a "-1 Outsider" on a
     * 0-outsider count simply doesn't apply).
     */
    operator fun plus(mod: SetupModifier): Distribution {
        val newOutsiders = (outsiders + mod.outsiderDelta).coerceAtLeast(0)
        val newMinions = (minions + mod.minionDelta).coerceAtLeast(0)
        val newDemons = (demons + mod.demonDelta).coerceAtLeast(0)
        val applied = (newOutsiders - outsiders) + (newMinions - minions) + (newDemons - demons)
        return Distribution(
            townsfolk = (townsfolk - applied - mod.townsfolkRemoved).coerceAtLeast(0),
            outsiders = newOutsiders,
            minions = newMinions,
            demons = newDemons,
        )
    }
}

/**
 * How a setup-modifying character (square-bracket ability text) changes the
 * bag.
 */
@Serializable
data class SetupModifier(
    val characterId: String,
    /** Human readable bracket text, e.g. "+2 Outsiders". */
    val text: String,
    val outsiderDelta: Int = 0,
    val minionDelta: Int = 0,
    val demonDelta: Int = 0,
    /** Extra townsfolk removed without a matching add (rare). */
    val townsfolkRemoved: Int = 0,
    /**
     * Teams whose final count is a storyteller choice rather than a fixed
     * delta (e.g. Godfather "-1 or +1 Outsider", Xaan "[X Outsiders]",
     * Kazali "-? to +? Outsiders"). Bag validation relaxes only these teams.
     */
    val choiceTeams: Set<Team> = emptySet(),
    /**
     * Exact deltas for bounded choices such as Godfather's -1 or +1
     * Outsider. Choice teams absent from this map are genuinely open-ended.
     */
    val choiceDeltas: Map<Team, Set<Int>> = emptyMap(),
    /** A specific character that must join the bag (Huntsman -> Damsel). */
    val requiredCompanionId: String? = null,
) {
    val choice: Boolean get() = choiceTeams.isNotEmpty()
}

object Setup {

    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 20

    /** Characters whose bracket rewrites the whole team structure. */
    private val TEAM_WARPING_IDS = setOf("atheist", "legion", "riot")

    /** Bracket-mandated companions: this character forces another into play. */
    val COMPANIONS: Map<String, String> = mapOf(
        "huntsman" to "damsel",
        "choirboy" to "king",
    )

    /**
     * Official Trouble Brewing rulebook distribution through 15 players.
     * Above 15, extra players would normally be Travellers; for the app's
     * larger casual-game option, continue the table's repeating three-player
     * pattern so the distribution still accounts for every requested seat.
     */
    fun distributionFor(playerCount: Int): Distribution {
        require(playerCount >= MIN_PLAYERS) { "Need at least $MIN_PLAYERS players" }
        require(playerCount <= MAX_PLAYERS) { "At most $MAX_PLAYERS players are supported" }
        if (playerCount <= 6) {
            return Distribution(
                townsfolk = 3,
                outsiders = playerCount - 5,
                minions = 1,
                demons = 1,
            )
        }

        val threePlayerBlock = (playerCount - 7) / 3
        return Distribution(
            townsfolk = 5 + 2 * threePlayerBlock,
            outsiders = (playerCount - 7) % 3,
            minions = 1 + threePlayerBlock,
            demons = 1,
        )
    }

    private val bracketRegex = Regex("""\[(.*?)]""")
    private val deltaRegex =
        Regex("""([+-]\d+)\s+(Townsfolk|Outsider|Minion|Demon)s?""", RegexOption.IGNORE_CASE)
    private val boundedChoiceRegex = Regex(
        """([+-]\d+)\s+(?:or|to)\s+([+-]\d+)\s+(Townsfolk|Outsider|Minion|Demon)s?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extracts a [SetupModifier] from a character's bracketed setup text.
     * Returns null for characters without setup effects. Non-numeric setup
     * effects (Marionette, Lunatic...) yield a modifier with zero deltas but
     * carry the text so the UI can surface it.
     */
    fun modifierFor(character: Character): SetupModifier? {
        if (!character.setup) return null
        val bracket = bracketRegex.find(character.ability)?.groupValues?.get(1)
            ?: return SetupModifier(character.id, "Modifies setup")

        // Characters that rewrite the whole structure: relax every count.
        if (character.id in TEAM_WARPING_IDS) {
            return SetupModifier(character.id, bracket, choiceTeams = Team.entries.toSet())
        }
        // Summoner: the game starts with no Demon in the bag.
        if (bracket.contains("No Demon", ignoreCase = true)) {
            return SetupModifier(character.id, bracket, demonDelta = -1)
        }

        var outsiders = 0
        var minions = 0
        var demons = 0
        var townsfolkRemoved = 0
        val choiceTeams = mutableSetOf<Team>()

        // Storyteller-choice ranges: "+0 or +1 Outsider", "-1 or +1 Outsider",
        // "+0 to +2 Village Idiots", "-? to +? Outsiders", "[X Outsiders]".
        val isChoice = bracket.contains(" or ", ignoreCase = true) ||
            bracket.contains(" to ", ignoreCase = true) ||
            bracket.contains('?') ||
            bracket.contains(Regex("""\bX\b"""))
        val matches = deltaRegex.findAll(bracket).toList()
        if (isChoice) {
            val variableTeamNames = when {
                bracket.contains('?') -> Regex(
                    """\?(?:\s+to\s+[+-]\?)?\s+(Townsfolk|Outsider|Minion|Demon)s?""",
                    RegexOption.IGNORE_CASE,
                ).findAll(bracket).map { it.groupValues[1] }.toList()
                bracket.contains(Regex("""\bX\b""")) -> Regex(
                    """\bX\s+(Townsfolk|Outsider|Minion|Demon)s?""",
                    RegexOption.IGNORE_CASE,
                ).findAll(bracket).map { it.groupValues[1] }.toList()
                else -> matches.map { it.groupValues[2] }
            }
            for (teamName in variableTeamNames) {
                choiceTeams += when (teamName.lowercase()) {
                    "townsfolk" -> Team.TOWNSFOLK
                    "outsider" -> Team.OUTSIDER
                    "minion" -> Team.MINION
                    else -> Team.DEMON
                }
            }
            // Village Idiot's "+0 to +2 Village Idiots" mentions no team word;
            // extra copies replace townsfolk.
            if (choiceTeams.isEmpty()) choiceTeams += Team.TOWNSFOLK
        }

        val boundedChoice = boundedChoiceRegex.find(bracket)
        val choiceDeltas = if (isChoice && !bracket.contains('?') && boundedChoice != null) {
            val team = when (boundedChoice.groupValues[3].lowercase()) {
                "townsfolk" -> Team.TOWNSFOLK
                "outsider" -> Team.OUTSIDER
                "minion" -> Team.MINION
                else -> Team.DEMON
            }
            mapOf(
                team to setOf(
                    boundedChoice.groupValues[1].toInt(),
                    boundedChoice.groupValues[2].toInt(),
                ),
            )
        } else if (isChoice && !bracket.contains('?')) {
            matches.groupBy { match ->
                when (match.groupValues[2].lowercase()) {
                    "townsfolk" -> Team.TOWNSFOLK
                    "outsider" -> Team.OUTSIDER
                    "minion" -> Team.MINION
                    else -> Team.DEMON
                }
            }
                .mapValues { (_, teamMatches) ->
                    teamMatches.map { it.groupValues[1].toInt() }.toSet()
                }
                .filterValues { it.isNotEmpty() }
        } else {
            emptyMap()
        }
        if (isChoice && matches.isNotEmpty()) {
            // Ranged choice ("-1 or +1"): apply the last listed option as the
            // suggested default; validation stays relaxed for those teams.
            applyDelta(matches.last()) { o, mi, d, t ->
                outsiders += o; minions += mi; demons += d; townsfolkRemoved += t
            }
        } else {
            for (m in matches) {
                applyDelta(m) { o, mi, d, t ->
                    outsiders += o; minions += mi; demons += d; townsfolkRemoved += t
                }
            }
        }

        val companion = COMPANIONS[character.id]
        // "+the Damsel" adds an Outsider slot filled by the companion.
        if (character.id == "huntsman") outsiders += 1

        return SetupModifier(
            characterId = character.id,
            text = bracket,
            outsiderDelta = outsiders,
            minionDelta = minions,
            demonDelta = demons,
            townsfolkRemoved = townsfolkRemoved,
            choiceTeams = choiceTeams,
            choiceDeltas = choiceDeltas,
            requiredCompanionId = companion,
        )
    }

    private inline fun applyDelta(
        match: MatchResult,
        apply: (outsiders: Int, minions: Int, demons: Int, townsfolkRemoved: Int) -> Unit,
    ) {
        val amount = match.groupValues[1].toInt()
        when (match.groupValues[2].lowercase()) {
            "townsfolk" -> apply(0, 0, 0, -amount)
            "outsider" -> apply(amount, 0, 0, 0)
            "minion" -> apply(0, amount, 0, 0)
            "demon" -> apply(0, 0, amount, 0)
        }
    }

    /**
     * Applies every selected character's modifier to the base distribution.
     * Deltas are aggregated before clamping so the result cannot depend on
     * the order in which characters were selected.
     */
    fun adjustedDistribution(playerCount: Int, selected: List<Character>): Distribution {
        val modifiers = selected.mapNotNull(::modifierFor)
        return distributionFor(playerCount) + combine(modifiers)
    }

    /**
     * Every legal distribution produced by bounded setup choices. Open-ended
     * choices remain marked in [SetupModifier.choiceTeams] for the validator.
     */
    fun allowedDistributions(playerCount: Int, selected: List<Character>): Set<Distribution> {
        val modifiers = selected.mapNotNull(::modifierFor)
        var combinations = listOf(SetupModifier(characterId = "", text = ""))
        for (modifier in modifiers) {
            combinations = combinations.flatMap { accumulated ->
                variants(modifier).map { variant ->
                    combine(listOf(accumulated, variant))
                }
            }
        }
        return combinations.map { distributionFor(playerCount) + it }.toSet()
    }

    private fun variants(modifier: SetupModifier): List<SetupModifier> {
        var variants = listOf(modifier)
        for ((team, deltas) in modifier.choiceDeltas) {
            variants = variants.flatMap { current ->
                deltas.map { delta ->
                    when (team) {
                        Team.TOWNSFOLK -> current.copy(townsfolkRemoved = -delta)
                        Team.OUTSIDER -> current.copy(outsiderDelta = delta)
                        Team.MINION -> current.copy(minionDelta = delta)
                        Team.DEMON -> current.copy(demonDelta = delta)
                        else -> current
                    }
                }
            }
        }
        return variants
    }

    private fun combine(modifiers: List<SetupModifier>): SetupModifier =
        SetupModifier(
            characterId = "",
            text = "",
            outsiderDelta = modifiers.sumOf { it.outsiderDelta },
            minionDelta = modifiers.sumOf { it.minionDelta },
            demonDelta = modifiers.sumOf { it.demonDelta },
            townsfolkRemoved = modifiers.sumOf { it.townsfolkRemoved },
        )
}
