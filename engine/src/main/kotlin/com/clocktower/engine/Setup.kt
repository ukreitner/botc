package com.clocktower.engine

import kotlin.random.Random
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

        // Characters that rewrite the whole team structure (Atheist, Legion,
        // Lil' Monsta, Kazali, Lord of Typhon, Summoner) declare a BagShape
        // instead — see [bagShapeFor] (lead D28). `TEAM_WARPING_IDS` is gone.

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

    // ---- the bag: moved verbatim out of GameActions by WP0 ----

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
            var dist = distributionFor(playerCount)
            val picked = linkedMapOf<Team, MutableList<Character>>()

            // Draw team by team, folding each drawn character's modifier
            // before the next team (and next member) is drawn.
            for (team in teamsInOrder) {
                val pool = byTeam[team].orEmpty().shuffled(random)
                val chosen = mutableListOf<Character>()
                for (candidate in pool) {
                    if (chosen.size >= dist.count(team)) break
                    chosen += candidate
                    modifierFor(candidate)?.let { dist += it }
                }
                picked[team] = chosen
            }

            // Reconcile: force required companions in, then trim/fill drift
            // caused by modifiers of later-drawn characters and by BagShapes.
            var bag = picked.values.flatten().toMutableList()
            repeat(4) {
                for (c in bag.toList()) {
                    val companionId = modifierFor(c)?.requiredCompanionId ?: continue
                    if (bag.none { it.id == companionId }) {
                        available.find { it.id == companionId }?.let { bag.add(it) }
                    }
                }
                val shapes = shapesFor(bag, playerCount)
                val forbidden = forbiddenIds(shapes)
                val target = shapeTarget(
                    shapes.values,
                    adjustedDistribution(playerCount, bag.filterNot { it.id in forbidden }),
                    playerCount,
                )
                for (team in teamsInOrder) {
                    // A shape-forbidden character (Lil' Monsta) fills no seat.
                    val members = bag.filter { it.team == team && it.id !in forbidden }
                    var excess = members.size - target.count(team)
                    if (excess > 0) {
                        // Never trim characters that modify setup or are
                        // required companions — trim plain members instead.
                        val required = bag.mapNotNull { modifierFor(it)?.requiredCompanionId }.toSet()
                        for (m in members.shuffled(random)) {
                            if (excess == 0) break
                            if (m.setup || m.id in required) continue
                            bag.remove(m); excess--
                        }
                        // A shape may forbid a whole team the bag still holds
                        // (Summoner's Demon, Kazali's Minions): trim those too.
                        for (m in bag.filter { it.team == team && it.id !in forbidden }.shuffled(random)) {
                            if (excess == 0) break
                            if (m.id in required || bagShapeFor(m.id, distributionFor(playerCount), playerCount) != null) continue
                            bag.remove(m); excess--
                        }
                    } else if (excess < 0) {
                        val unused = byTeam[team].orEmpty().filter { c ->
                            c.id !in forbidden && bag.none { it.id == c.id }
                        }
                        for (m in unused.shuffled(random)) {
                            if (excess == 0) break
                            bag.add(m); excess++
                        }
                    }
                }
            }

            val seatFilling = bag.count { it.id !in forbiddenIds(shapesFor(bag, playerCount)) }
            if (seatFilling == playerCount && validateBag(bag, playerCount).isEmpty()) {
                return bag
            }
        }
        return null
    }

    /**
     * The distribution [randomBag] aims at: [base] clamped into every shape's
     * ranges, then rebalanced so the seats still add up to [playerCount] — the
     * slack goes to the Townsfolk first, then the Outsiders, exactly as the
     * physical game trades a Demon slot for a Minion.
     */
    private fun shapeTarget(
        shapes: Collection<BagShape>,
        base: Distribution,
        playerCount: Int,
    ): Distribution {
        fun rangeOf(team: Team): IntRange? {
            val ranges = shapes.mapNotNull { it.range(team) }
            if (ranges.isEmpty()) return null
            return ranges.maxOf { it.first }..ranges.minOf { it.last }
        }

        val counts = mutableMapOf(
            Team.TOWNSFOLK to base.townsfolk,
            Team.OUTSIDER to base.outsiders,
            Team.MINION to base.minions,
            Team.DEMON to base.demons,
        )
        for (team in counts.keys.toList()) {
            rangeOf(team)?.let { counts[team] = counts.getValue(team).coerceIn(it) }
        }
        var drift = playerCount - counts.values.sum()
        for (team in listOf(Team.TOWNSFOLK, Team.OUTSIDER)) {
            if (drift == 0) break
            val range = rangeOf(team) ?: 0..playerCount
            val want = (counts.getValue(team) + drift).coerceIn(range)
            drift -= want - counts.getValue(team)
            counts[team] = want
        }
        return Distribution(
            townsfolk = counts.getValue(Team.TOWNSFOLK),
            outsiders = counts.getValue(Team.OUTSIDER),
            minions = counts.getValue(Team.MINION),
            demons = counts.getValue(Team.DEMON),
        )
    }

    private fun Distribution.count(team: Team): Int = when (team) {
        Team.TOWNSFOLK -> townsfolk
        Team.OUTSIDER -> outsiders
        Team.MINION -> minions
        Team.DEMON -> demons
        else -> 0
    }

    /**
     * Ids that may legally appear multiple times in a bag.
     *
     * `riot` is NOT one of them (lead D28): a Riot game deals ordinary Minions,
     * which become Riot on day 3.
     */
    val DUPLICABLE = setOf("villageidiot", "legion")

    /**
     * Human-readable problems with a proposed bag, empty when legal.
     *
     * Active Fabled ([fabledIds]) that legally bend the distribution are
     * honoured — the Sentinel allows one extra or one fewer Outsider.
     * [inPlayIds] names characters that are in play WITHOUT a bag token
     * (Lil' Monsta's centre token, a Boffin or Alchemist grant): their
     * [BagShape] applies even though they never fill a seat.
     */
    fun validateBag(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: Collection<String> = emptyList(),
        /** House rule: any character may appear multiple times. */
        allowAnyDuplicates: Boolean = false,
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
        /**
         * Characters whose setup bracket applies although they hold no bag
         * token: a Boffin or Alchemist grant, the Marionette's believed
         * character. See [virtualSetupCharacters].
         */
        virtual: List<Character> = emptyList(),
    ): List<String> {
        val issues = mutableListOf<String>()
        val model = bagModel(bag, playerCount, fabledIds, inPlayIds, state, virtual)
        val shapes = model.shapes
        val seatless = model.seatless
        val seatFilling = model.seatFilling
        if (seatFilling.size != playerCount) {
            if (seatless.isNotEmpty()) {
                for (token in seatless.distinctBy { it.id }) {
                    issues += "${token.name} is a token, not a seat — it fills no seat in the bag"
                }
            } else {
                issues += "Bag has ${bag.size} characters for $playerCount players"
            }
        }
        val modifiers = model.modifiers
        val relaxedTeams = model.relaxedTeams
        val allowed = model.allowed
        val counts = seatFilling.groupingBy { it.team }.eachCount()
        val allTeams = BAG_TEAMS

        // A BagShape REPLACES the distribution check for the teams it pins.
        val pinned = mutableSetOf<Team>()
        for (team in allTeams) {
            val range = model.pinnedRange(team) ?: continue
            pinned += team
            val actual = counts[team] ?: 0
            if (actual !in range) {
                val expectedText =
                    if (range.first == range.last) "${range.first}" else "${range.first} to ${range.last}"
                issues += "${team.displayName}: $actual in bag, expected $expectedText"
            }
        }

        val checkedTeams = allTeams.filterNot { it in relaxedTeams || it in pinned }
        val matchesAllowedDistribution = allowed.any { distribution ->
            checkedTeams.all { team ->
                (counts[team] ?: 0) == distribution.count(team)
            }
        }
        if (checkedTeams.isNotEmpty() && !matchesAllowedDistribution) {
            var explained = false
            for (team in checkedTeams) {
                val actual = counts[team] ?: 0
                val expected = allowed.map { it.count(team) }.distinct().sorted()
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
        for ((id, shape) in shapes) {
            for (required in shape.requireInBag) {
                if (bag.none { Character.normalizeId(it.id) == Character.normalizeId(required) }) {
                    issues += "$id requires the $required in the bag"
                }
            }
        }

        val dupes = if (allowAnyDuplicates) {
            emptyMap()
        } else {
            bag.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        }
        for ((id, n) in dupes) {
            val copies = shapes.values.firstNotNullOfOrNull { shape ->
                shape.copies[id]?.takeUnless { shape.advisory }
            }
            when {
                copies != null -> if (n !in copies) {
                    issues += "$id appears $n times, maximum ${copies.last}"
                }
                id in DUPLICABLE -> Unit
                else -> issues += "$id appears $n times"
            }
        }
        return issues.distinct()
    }

    /** The teams a bag is built out of, in the order every screen lists them. */
    val BAG_TEAMS: List<Team> =
        listOf(Team.TOWNSFOLK, Team.OUTSIDER, Team.MINION, Team.DEMON)

    /**
     * Everything [validateBag] derives from a proposed bag BEFORE it starts
     * writing messages — the one place the shapes, the seat-filling split, the
     * relaxed teams and the legal distributions are worked out.
     *
     * It exists so [bagTargets] cannot drift from the validator: playtest A-5
     * had the setup screen call `allowedDistributions(playerCount, selected)`
     * with neither the Fabled ids nor the acknowledgements, so the header
     * demanded "4 outsiders · 1 demon" for a bag the validator was perfectly
     * happy with — and, on a Lil' Monsta script, all four bars read "at target"
     * while the issue list underneath said the bag was short a Townsfolk.
     */
    private class BagModel(
        val shapes: Map<String, BagShape>,
        val seatless: List<Character>,
        val seatFilling: List<Character>,
        val modifiers: List<SetupModifier>,
        val relaxedTeams: Set<Team>,
        val allowed: Set<Distribution>,
    ) {
        /** The range a [BagShape] pins this team to, or null when none does. */
        fun pinnedRange(team: Team): IntRange? {
            val ranges = shapes.values.mapNotNull { shape ->
                when (team) {
                    Team.TOWNSFOLK -> shape.townsfolk
                    Team.OUTSIDER -> shape.outsiders
                    Team.MINION -> shape.minions
                    Team.DEMON -> shape.demons
                    else -> null
                }
            }
            if (ranges.isEmpty()) return null
            return ranges.maxOf { it.first }..ranges.minOf { it.last }
        }
    }

    private fun bagModel(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: Collection<String>,
        inPlayIds: Collection<String>,
        state: GameState?,
        virtual: List<Character>,
    ): BagModel {
        val shapes = shapesFor(bag, playerCount, inPlayIds + virtual.map { it.id }, state)
        val forbidden = forbiddenIds(shapes)
        val (seatless, seatFilling) = bag.partition { Character.normalizeId(it.id) in forbidden }
        val modifiers = (seatFilling + virtual).mapNotNull { modifierFor(it) }
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
        var allowed = allowedDistributions(playerCount, seatFilling + virtual)
        if ("sentinel" in fabledIds.map { Character.normalizeId(it) }) {
            allowed = allowed
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
        return BagModel(shapes, seatless, seatFilling, modifiers, relaxedTeams, allowed)
    }

    /**
     * The counts [validateBag] would accept for each team, given exactly the
     * same inputs — what the bag builder's "Need:" line and progress bars must
     * render, so a screen can never contradict its own issue list (A-5).
     *
     * A team is [TeamTarget.free] when the storyteller chooses its count
     * outright (an open-ended bracket such as the Kazali's "-? to +? Outsiders"):
     * the validator does not check it, so nothing should be shown as incomplete.
     */
    fun bagTargets(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: Collection<String> = emptyList(),
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
        virtual: List<Character> = emptyList(),
    ): Map<Team, TeamTarget> {
        val model = bagModel(bag, playerCount, fabledIds, inPlayIds, state, virtual)
        return BAG_TEAMS.associateWith { team ->
            val pinned = model.pinnedRange(team)
            when {
                // A BagShape REPLACES the distribution check for this team.
                pinned != null -> TeamTarget(pinned.toList())
                team in model.relaxedTeams -> TeamTarget(emptyList(), free = true)
                else -> TeamTarget(model.allowed.map { it.count(team) }.distinct().sorted())
            }
        }
    }

    /**
     * The tokens in [bag] that actually fill a seat — everything a [BagShape]
     * forbids from the bag (Lil' Monsta's centre token) removed, exactly as
     * [validateBag] partitions it.
     *
     * The deal must use this and nothing else: `Seats.deal` requires one token
     * per non-Traveller seat and throws otherwise, and "IN THE BAG · 9 / 8"
     * (playtest A-8) was the screen counting a token the validator had already
     * set aside.
     */
    fun seatFillingBag(
        bag: List<Character>,
        playerCount: Int,
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
        virtual: List<Character> = emptyList(),
    ): List<Character> =
        bagModel(bag, playerCount, emptyList(), inPlayIds, state, virtual).seatFilling

    /** How many of one team a bag actually holds, seatless tokens excluded. */
    fun bagCounts(
        bag: List<Character>,
        playerCount: Int,
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
        virtual: List<Character> = emptyList(),
    ): Map<Team, Int> =
        seatFillingBag(bag, playerCount, inPlayIds, state, virtual)
            .groupingBy { it.team }
            .eachCount()

    /**
     * Bag notes that WARN but never block: the Legion ratio, and every
     * [BagShape.note]. Rendered under the bag stage (lead D28 / D18).
     */
    fun bagWarnings(
        bag: List<Character>,
        playerCount: Int,
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
    ): List<String> {
        val shapes = shapesFor(bag, playerCount, inPlayIds, state)
        val warnings = mutableListOf<String>()
        for ((id, shape) in shapes) {
            if (shape.note.isNotBlank()) warnings += shape.note
            if (!shape.advisory) continue
            for ((copyId, range) in shape.copies) {
                val n = bag.count { Character.normalizeId(it.id) == Character.normalizeId(copyId) }
                if (n !in range) {
                    warnings += "$id: $copyId appears $n times — most players should be $copyId " +
                        "(about ${range.first} at $playerCount players)"
                }
            }
        }
        return warnings.distinct()
    }

    /**
     * Validates both the bag and mandatory first-night setup choices. This is
     * used again at the phase boundary so manually assigned games cannot
     * bypass setup requirements.
     *
     * WP4: now one line over the declarative table (lead D30/D48). Every check
     * the old `when` block did survives as a [SetupRequirement] row.
     */
    fun validateSetupState(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<String> = SetupRequirements.blockingProblems(state, lookup)

    /**
     * Characters in play that hold no seat, so their [BagShape] applies while
     * they never fill a seat. Today: Lil' Monsta's centre token, once the
     * storyteller has acknowledged the `lilmonsta.noDemonSeat` requirement.
     */
    fun seatlessInPlayIds(state: GameState): List<String> =
        if (Decisions.bool(state, SetupRequirements.LILMONSTA_NO_DEMON_SEAT)) {
            listOf("lilmonsta")
        } else {
            emptyList()
        }

    /**
     * Characters whose setup bracket applies even though they are not tokens in
     * the bag — "these changes are made during setup, as normal": the Boffin's
     * gift and the Alchemist's Minion ability, plus the character a Marionette
     * believes they are (mandated by the Huntsman and Balloonist jinxes).
     *
     * The Drunk's believed Townsfolk is deliberately NOT here: no jinx rules
     * either way, so it stays an advisory question.
     */
    fun virtualSetupCharacters(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<Character> = listOfNotNull(
        state.decisions[Decisions.BOFFIN_GRANT]?.takeIf { it.isNotBlank() }?.let(lookup),
        state.decisions[Decisions.ALCHEMIST_GRANT]?.takeIf { it.isNotBlank() }?.let(lookup),
        state.seats.firstOrNull { it.characterId == "marionette" }
            ?.shownCharacterId?.let(lookup),
    ).filter { it.setup }

    /**
     * Bag override for one character, replacing the old `TEAM_WARPING_IDS`
     * relax-everything hack (lead D28). One table row per character instead of
     * a `when` (lead D34); `null` means "an ordinary character in an ordinary bag".
     *
     * [base] is `distributionFor(playerCount)`, unadjusted.
     */
    fun bagShapeFor(
        characterId: String,
        base: Distribution,
        playerCount: Int,
        state: GameState? = null,
    ): BagShape? = CharacterRules.all[Character.normalizeId(characterId)]
        ?.bagShape
        // W7G: the registry row wins, so a WP7 file can own its own bag rule
        // without editing this table (lead D61's "registry wins outright").
        ?.invoke(base, playerCount)
        ?: builtInBagShape(characterId, base, playerCount, state)

    private fun builtInBagShape(
        characterId: String,
        base: Distribution,
        playerCount: Int,
        state: GameState?,
    ): BagShape? = when (Character.normalizeId(characterId)) {
        // "You choose which players are which Minions" — created on the first night.
        "kazali" -> BagShape(
            minions = 0..0,
            demons = 1..1,
            outsiders = 0..(base.outsiders + base.minions),
            townsfolk = (playerCount - 1 - base.outsiders - base.minions)..(playerCount - 1),
            note = "Minions are created on the first night.",
        )

        // "The 3 Minions and the evil line are created on the first night."
        "lordoftyphon" -> BagShape(
            minions = 0..0,
            demons = 1..1,
            outsiders = 0..(base.outsiders + base.minions),
            townsfolk = (playerCount - 1 - base.outsiders - base.minions)..(playerCount - 1),
            note = "The Minions and the evil line are created on the first night.",
        )

        // A token in the centre, not a seat: swap the Demon slot for a Minion
        // (10 players -> 7 / 0 / 3 / 0, lead D18).
        "lilmonsta" -> BagShape(
            townsfolk = base.townsfolk..base.townsfolk,
            outsiders = base.outsiders..base.outsiders,
            minions = (base.minions + 1)..(base.minions + 1),
            demons = 0..0,
            forbidInBag = setOf("lilmonsta"),
            note = "Lil' Monsta is a token, not a seat. " +
                "Put ${base.minions + 1} Minions and no Demon in the bag.",
        )

        // "[No Demon]" — the Summoner makes one on the third night.
        "summoner" -> BagShape(
            townsfolk = (base.townsfolk + 1)..(base.townsfolk + 1),
            minions = base.minions..base.minions,
            demons = 0..0,
            note = "No Demon in the bag: the Summoner creates one on the 3rd night.",
        )

        // "[No evil characters]" — every seat is a Townsfolk or Outsider.
        "atheist" -> BagShape(
            townsfolk = 0..playerCount,
            outsiders = 0..playerCount,
            minions = 0..0,
            demons = 0..0,
            note = "No evil characters. The Storyteller may break the rules.",
        )

        // "[Most players are Legion]" — the ratio is advisory, `minions = 0` is firm.
        "legion" -> BagShape(
            townsfolk = 0..playerCount,
            outsiders = 0..playerCount,
            minions = 0..0,
            demons = 1..(playerCount - 1),
            copies = mapOf("legion" to (playerCount / 2 + 1)..(playerCount - 1)),
            advisory = true,
            note = "About ${playerCount / 2 + 2} Legion to " +
                "${playerCount - playerCount / 2 - 2} good at $playerCount players.",
        )

        // "This ensures that only one Minion token is in the bag."
        "marionette" -> if (base.minions >= 3) {
            BagShape(
                minions = (base.minions - 1)..(base.minions - 1),
                note = "One Minion token fewer: the Marionette is the missing Minion.",
            )
        } else {
            null
        }

        "villageidiot" -> BagShape(copies = mapOf("villageidiot" to 1..3))

        // "[X Outsiders]" — X is frozen at setup and never recomputed.
        "xaan" -> state?.let { Decisions.int(it, Decisions.XAAN_X) }
            ?.takeIf { it >= 0 }
            ?.let { x -> BagShape(outsiders = x..x, note = "Xaan: X = $x Outsiders.") }

        // Riot is an ORDINARY Demon in an ORDINARY bag (lead D28). No shape.
        else -> null
    }

    /** The range this shape pins for one team, or null when it leaves the team free. */
    private fun BagShape.range(team: Team): IntRange? = when (team) {
        Team.TOWNSFOLK -> townsfolk
        Team.OUTSIDER -> outsiders
        Team.MINION -> minions
        Team.DEMON -> demons
        else -> null
    }

    /**
     * Every bag shape this game raises: one per character in the bag, plus one
     * per character that is in play without a bag token (Lil' Monsta's centre
     * token, a Boffin or Alchemist grant).
     */
    fun shapesFor(
        bag: List<Character>,
        playerCount: Int,
        inPlayIds: Collection<String> = emptyList(),
        state: GameState? = null,
    ): Map<String, BagShape> {
        val base = distributionFor(playerCount)
        val ids = (bag.map { it.id } + inPlayIds).map(Character::normalizeId).distinct()
        return ids.mapNotNull { id ->
            bagShapeFor(id, base, playerCount, state)?.let { id to it }
        }.toMap()
    }

    /**
     * Ids the shapes say must NOT sit in the bag even though they are in play
     * (Lil' Monsta). They occupy no seat, so they never count toward
     * [playerCount].
     */
    private fun forbiddenIds(shapes: Map<String, BagShape>): Set<String> =
        shapes.values.flatMap { it.forbidInBag }.map(Character::normalizeId).toSet()
}

/**
 * The counts one team may legally hold in a bag, as `Setup.validateBag` judges
 * it. [free] means the storyteller chooses outright and nothing is checked.
 */
data class TeamTarget(val counts: List<Int>, val free: Boolean = false) {
    /** Whether a bag holding [have] of this team satisfies the validator. */
    fun accepts(have: Int): Boolean = free || have in counts

    /** The count to aim at from [have] — the nearest legal one, never a lie. */
    fun target(have: Int): Int? = counts.minByOrNull { kotlin.math.abs(it - have) }
}

/** Bag override for one character, replacing `Setup.TEAM_WARPING_IDS` (lead D28). */
data class BagShape(
    val townsfolk: IntRange? = null,
    val outsiders: IntRange? = null,
    val minions: IntRange? = null,
    val demons: IntRange? = null,
    val requireInBag: Set<String> = emptySet(),
    /** Ids that must NOT be in the bag even though they are "in play" (lilmonsta). */
    val forbidInBag: Set<String> = emptySet(),
    val copies: Map<String, IntRange> = emptyMap(),
    /** Advisory only — warn, never block (Legion). */
    val advisory: Boolean = false,
    val note: String = "",
)
