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

    operator fun plus(mod: SetupModifier): Distribution = Distribution(
        townsfolk = townsfolk - mod.outsiderDelta - mod.minionDelta - mod.demonDelta - mod.townsfolkRemoved,
        outsiders = outsiders + mod.outsiderDelta,
        minions = minions + mod.minionDelta,
        demons = demons + mod.demonDelta,
    )
}

/**
 * How a setup-modifying character (square-bracket ability text) changes the
 * bag. Deltas trade against the Townsfolk count so the total stays equal to
 * the player count, matching how the physical game is set up.
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
     * True when the delta is a storyteller choice (e.g. "+0 or +1 Outsider",
     * Godfather's "-1 or +1 Outsider", Xaan's "X Outsiders") and the default
     * applied here is only a suggestion.
     */
    val choice: Boolean = false,
)

object Setup {

    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 20

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
    private val deltaRegex = Regex("""([+-]\d+)\s+(Townsfolk|Outsider|Minion|Demon)s?""", RegexOption.IGNORE_CASE)

    /**
     * Extracts a [SetupModifier] from a character's bracketed setup text.
     * Returns null for characters without setup effects. Non-numeric setup
     * effects (Marionette, Atheist, Legion, Summoner...) yield a modifier
     * with zero deltas but carry the text so the UI can surface it.
     */
    fun modifierFor(character: Character): SetupModifier? {
        if (!character.setup) return null
        val bracket = bracketRegex.find(character.ability)?.groupValues?.get(1)
            ?: return SetupModifier(character.id, "Modifies setup")
        var outsiders = 0
        var minions = 0
        var demons = 0
        var townsfolkRemoved = 0
        var choice = false

        // "+0 or +1 Outsider" / "-1 or +1 Outsider": a storyteller choice.
        if (bracket.contains(" or ", ignoreCase = true) || bracket.contains(" to ", ignoreCase = true)) {
            choice = true
        }

        val matches = deltaRegex.findAll(bracket).toList()
        if (choice && matches.isNotEmpty()) {
            // Default to the last listed option (e.g. "+1" from "-1 or +1").
            val m = matches.last()
            applyDelta(m) { o, mi, d, t -> outsiders += o; minions += mi; demons += d; townsfolkRemoved += t }
        } else {
            for (m in matches) {
                applyDelta(m) { o, mi, d, t -> outsiders += o; minions += mi; demons += d; townsfolkRemoved += t }
            }
        }
        if (bracket.contains("X Outsiders", ignoreCase = true)) choice = true

        return SetupModifier(
            characterId = character.id,
            text = bracket,
            outsiderDelta = outsiders,
            minionDelta = minions,
            demonDelta = demons,
            townsfolkRemoved = townsfolkRemoved,
            choice = choice,
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
