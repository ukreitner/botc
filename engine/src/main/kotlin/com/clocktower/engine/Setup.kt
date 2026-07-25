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
     * Official Trouble Brewing rulebook distribution. Above 15 players the
     * extras are travellers in real games, but we allow the pattern to
     * continue for very large casual games.
     */
    fun distributionFor(playerCount: Int): Distribution {
        require(playerCount >= MIN_PLAYERS) { "Need at least $MIN_PLAYERS players" }
        val capped = playerCount.coerceAtMost(15)
        val townsfolk = when (capped) {
            5, 6 -> 3
            7, 8, 9 -> 5
            10, 11, 12 -> 7
            else -> 9
        }
        val outsiders = when (capped) {
            5, 7, 10, 13 -> 0
            6, 8, 11, 14 -> 1
            else -> 2
        }
        val minions = when (capped) {
            in 5..9 -> 1
            in 10..12 -> 2
            else -> 3
        }
        return Distribution(townsfolk, outsiders, minions, demons = 1)
    }

    private val bracketRegex = Regex("""\[(.*?)]""")
    private val deltaRegex =
        Regex("""([+-]\d+)\s+(Townsfolk|Outsider|Minion|Demon)s?""", RegexOption.IGNORE_CASE)
    private val teamWordRegex =
        Regex("""(Townsfolk|Outsider|Minion|Demon)s?""", RegexOption.IGNORE_CASE)

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
        if (isChoice) {
            for (m in teamWordRegex.findAll(bracket)) {
                choiceTeams += when (m.groupValues[1].lowercase()) {
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

        val matches = deltaRegex.findAll(bracket).toList()
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

    /** Applies every selected character's modifier to the base distribution. */
    fun adjustedDistribution(playerCount: Int, selected: List<Character>): Distribution {
        var dist = distributionFor(playerCount)
        for (c in selected) {
            modifierFor(c)?.let { dist += it }
        }
        return dist
    }
}
