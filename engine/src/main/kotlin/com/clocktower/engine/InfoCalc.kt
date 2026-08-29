package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What the engine owes this player tonight. MUST_LIE outranks MAY_LIE (lead D50). */
@Serializable
enum class InfoObligation { TRUTH, MAY_LIE, MUST_LIE }

/**
 * Who a computed answer is FOR.
 *
 * Most of `InfoCalc` computes what a character learns, and the night sheet turns
 * it into a card to hold up. Some rows compute what the STORYTELLER needs in
 * order to run the step and nothing else: the Courtier names a character and is
 * told nothing, the Exorcist is told nothing either way, the Cult Leader's
 * neighbours are the storyteller's own crib. Those answers may never become a
 * show card — the Courtier's put the whole grimoire in front of the Courtier
 * and the Exorcist's told them who the Demon was (playtest B2-2, D2-2, D2-3).
 */
@Serializable
enum class InfoAudience {
    /** The holder is shown this. It becomes a card. */
    PLAYER,

    /** For the storyteller's eyes only. Never a card, never a `SHOW … TO …` button. */
    STORYTELLER,
}

/** The shape of one piece of information, so the UI never parses prose. */
@Serializable
sealed interface Answer {
    /** A number, with the range the storyteller may plausibly claim instead. */
    @Serializable
    data class Count(val n: Int, val min: Int = 0, val max: Int = 0) : Answer

    @Serializable
    data class YesNoAnswer(val yes: Boolean) : Answer

    @Serializable
    data class Characters(val ids: List<String>) : Answer

    @Serializable
    data class Players(val ids: List<Long>, val characterId: String? = null) : Answer

    @Serializable
    data class Message(val text: String) : Answer
}

/**
 * One computed piece of night information: the true answer, the lies that are
 * plausible instead, and the three DISTINCT reasons a lie may be owed
 * (impairment, misregistration, a Vortox) kept apart (lead D10/D50).
 */
@Serializable
data class InfoResult(
    /** The TRUE answer, always computed. */
    val answer: Answer,
    /** Storyteller-voice headline: "1 of Ana's alive neighbours is evil". */
    val headline: String,
    val detail: String = "",
    /**
     * Plausible alternatives to show instead, generated for EVERY answer shape.
     * Empty ONLY when the engine genuinely cannot lie — in which case the UI must
     * not render a "false info" heading at all.
     */
    val alternatives: List<Answer> = emptyList(),
    /**
     * Other answers that are equally TRUE — the storyteller's choice, not a lie.
     *
     * "1 of 2 players is a particular Townsfolk" is a choice of WHICH Townsfolk
     * to reveal, and that choice is one of the storyteller's main levers. The
     * card offered exactly one true chip and hid the rest of the decision behind
     * "show a card…" (playtest B2-15).
     */
    val alsoTrue: List<Answer> = emptyList(),
    val obligation: InfoObligation = InfoObligation.TRUTH,
    /** Impairment, misregistration and Vortox are THREE obligations — keep them apart. */
    val caveats: List<String> = emptyList(),
    /** True when the holder's ability is not working at all. */
    val abilityMalfunctions: Boolean = false,
    /**
     * The words above the token on a character card, when the physical info
     * token says something more specific than "THIS CHARACTER" — the
     * Undertaker's is "THIS CHARACTER DIED TODAY".
     */
    val cardPrefix: String = "",
    /**
     * Who this answer is for. [InfoAudience.STORYTELLER] answers are never
     * offered as a card and never wear the `SHOW … TO …` primary (B2-2).
     */
    val audience: InfoAudience = InfoAudience.PLAYER,
)

/**
 * Computes the TRUE information a night ability would give, straight from the
 * grimoire state, plus every caveat the storyteller must weigh: drunkenness or
 * poisoning of the holder, misregistration (Spy, Recluse) and the Vortox.
 *
 * Every answer is typed ([Answer]) and carries the lies the engine can generate,
 * so no screen ever string-matches a headline again (lead D10/D50).
 */
object InfoCalc {

    /** How many player selections a character's calc needs (0 for none). */
    fun targetsNeeded(characterId: String): Int = when (Character.normalizeId(characterId)) {
        "fortuneteller", "seamstress", "chambermaid" -> 2
        "dreamer", "villageidiot", "ravenkeeper", "grandmother", "exorcist" -> 1
        // W7H additions.
        "harlot", "beggar" -> 1
        else -> 0
    }

    /** Whether we can compute anything useful for this character. */
    fun supports(characterId: String): Boolean = Character.normalizeId(characterId) in supportedIds

    /** Every id this calculator knows. WP7 files new ones to WP2, in one batch per wave. */
    val supportedIds: Set<String> = setOf(
        "chef", "empath", "clockmaker", "shugenja", "oracle", "undertaker",
        "towncrier", "flowergirl", "fortuneteller", "dreamer", "seamstress",
        "villageidiot", "cultleader", "king", "washerwoman", "librarian",
        "investigator", "knight", "sage", "steward", "noble", "bountyhunter",
        "chambermaid", "mathematician", "balloonist", "ravenkeeper",
        "grandmother",
        // WP2 additions (ARCHITECTURE §2.12, friction §10).
        "godfather", "juggler", "exorcist", "courtier", "savant", "tealady",
        // W7A: the one Fabled that computes a number (it has no seat).
        "duchess",
        // W7H: the half of `MISSING_INFO_IDS` whose answer the grimoire really
        // does know. The rest of that list is free text or a storyteller
        // judgement, and those rows say `infoId = ""` instead.
        // Ids are compared through `Character.normalizeId`, which strips the
        // dot: the registry row spells it `king.demon`, this set spells the
        // normalised form.
        "choirboy", "kingdemon", "nightwatchman", "preacher", "farmer",
        "harlot", "beggar",
    )

    /**
     * The engine-facing entry point: everything comes from [state] and [lookup].
     *
     * Never call this for an id whose [targetsNeeded] is greater than zero from
     * inside `NightPlan.build` — the Chambermaid's answer is a function of the
     * plan, and that would recurse.
     */
    fun compute(
        state: GameState,
        lookup: (String) -> Character?,
        characterId: String,
        holderId: Long?,
        targets: List<Long> = emptyList(),
    ): InfoResult? {
        val id = Character.normalizeId(characterId)
        if (!supports(id)) return null
        val ctx = Ctx(state, lookup, holderId?.let { state.player(it) })
        val result = when (id) {
            "chef" -> chef(ctx)
            "empath" -> empath(ctx)
            "clockmaker" -> clockmaker(ctx)
            "shugenja" -> shugenja(ctx)
            "oracle" -> oracle(ctx)
            "undertaker" -> undertaker(ctx)
            "towncrier" -> townCrier(ctx)
            "flowergirl" -> flowergirl(ctx)
            "fortuneteller" -> fortuneTeller(ctx, targets)
            "dreamer" -> dreamer(ctx, targets)
            "seamstress" -> seamstress(ctx, targets)
            "villageidiot" -> villageIdiot(ctx, targets)
            "ravenkeeper" -> revealCharacter(ctx, targets, "Ravenkeeper")
            "grandmother" -> revealCharacter(ctx, targets, "Grandmother")
            "cultleader" -> cultLeader(ctx)
            "king" -> king(ctx)
            "washerwoman" -> startKnowing(ctx, Team.TOWNSFOLK, "Townsfolk")
            "librarian" -> startKnowing(ctx, Team.OUTSIDER, "Outsider")
            "investigator" -> startKnowing(ctx, Team.MINION, "Minion")
            "knight" -> knight(ctx)
            "sage" -> sage(ctx)
            "steward" -> steward(ctx)
            "noble" -> noble(ctx)
            "bountyhunter" -> bountyHunter(ctx)
            "chambermaid" -> chambermaid(ctx, targets)
            "mathematician" -> mathematician(ctx)
            "balloonist" -> balloonist(ctx)
            "godfather" -> godfather(ctx)
            "juggler" -> juggler(ctx)
            "exorcist" -> exorcist(ctx, targets)
            "courtier" -> courtier(ctx)
            "savant" -> savant(ctx)
            "tealady" -> teaLady(ctx)
            "duchess" -> duchess(ctx)
            "choirboy" -> choirboy(ctx)
            "kingdemon" -> kingToDemon(ctx)
            "nightwatchman" -> selfReveal(ctx, "nightwatchman", "THIS PLAYER IS")
            "preacher" -> selfReveal(ctx, "preacher", "THIS CHARACTER SELECTED YOU")
            "farmer" -> becomes(ctx, "farmer")
            "harlot" -> revealCharacter(ctx, targets, "Harlot")
            "beggar" -> beggar(ctx, targets)
            else -> null
        } ?: return null
        return finish(ctx, id, result)
    }

    /** Compatibility entry point for callers that hold the whole dataset. */
    fun compute(
        data: GameData,
        state: GameState,
        characterId: String,
        holderId: Long?,
        targets: List<Long> = emptyList(),
    ): InfoResult? {
        val lookup: (String) -> Character? = { id ->
            state.script.customCharacters.find { it.id == id } ?: data.character(id)
        }
        return compute(state, lookup, characterId, holderId, targets)
    }

    // ---- shared helpers -------------------------------------------------

    private class Ctx(
        val state: GameState,
        val lookup: (String) -> Character?,
        val holder: Player?,
    ) {
        val players: List<Player> get() = state.players
        fun character(p: Player): Character? = p.characterId?.let(lookup)
        fun isEvil(p: Player): Boolean = p.isEvil(lookup)
        fun name(p: Player): String = p.name

        /** Every character this game could name, custom ones included. */
        val script: List<Character> by lazy {
            val custom = state.script.customCharacters.associateBy { it.id }
            state.script.characterIds.mapNotNull { custom[it] ?: lookup(it) }
        }

        val inPlayIds: Set<String> by lazy {
            state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
        }
    }

    /**
     * Resolves an exact number of distinct target ids. Selection state can
     * outlive a removed seat in the UI, so stale ids are treated as an
     * incomplete choice rather than silently omitted.
     */
    private fun validTargets(ctx: Ctx, targets: List<Long>, expected: Int): List<Player>? {
        if (targets.size != expected || targets.toSet().size != expected) return null
        val players = targets.map { ctx.state.player(it) }
        if (players.any { it == null }) return null
        return players.filterNotNull()
    }

    /**
     * The day a night-time "today" question refers to: during NIGHT of
     * cycle N the preceding day was N-1 (night 1 has no preceding day).
     */
    private fun relevantDay(state: GameState): Int =
        if (state.phase == Phase.NIGHT) state.cycle - 1 else state.cycle

    /** Players that might register differently than their true alignment. */
    private fun misregistrations(ctx: Ctx, relevant: Collection<Player>): List<String> {
        val notes = mutableListOf<String>()
        for (p in relevant) {
            val ruled = Status.live(ctx.state, ctx.lookup, p.id, EffectKind.REGISTERS_AS)
            if (ruled.isNotEmpty()) {
                notes += "${ctx.name(p)} has a standing registration ruling — " +
                    ruled.joinToString { it.note.ifEmpty { it.label } }
                continue
            }
            when (Character.normalizeId(p.characterId.orEmpty())) {
                "spy" -> notes += "${ctx.name(p)} is the Spy — may register as good / a Townsfolk or Outsider."
                "recluse" -> notes += "${ctx.name(p)} is the Recluse — may register as evil / a Minion or Demon."
            }
        }
        return notes
    }

    /**
     * Impairment: is the info holder drunk, poisoned, or ability-less? Read from
     * the typed effect model, never from a token label (lead D5/D11).
     */
    fun impairments(state: GameState, lookup: (String) -> Character?, player: Player?): List<String> {
        player ?: return emptyList()
        val own = Character.normalizeId(player.characterId.orEmpty())
        val notes = mutableListOf<String>()
        for (reason in Status.impairment(state, lookup, player.id)) {
            val source = Character.normalizeId(reason.effect.sourceCharacterId)
            val sourceName = lookup(reason.effect.sourceCharacterId)?.name ?: "storyteller"
            notes += when {
                // "It is just as if this player is the Drunk / Marionette / Lunatic."
                source == own && own.isNotEmpty() ->
                    "${player.name} IS the $sourceName — their ability malfunctions."
                reason.effect.kind == EffectKind.POISONED ->
                    "${player.name} is POISONED ($sourceName) — give false info."
                reason.effect.kind == EffectKind.DRUNK ->
                    "${player.name} is DRUNK ($sourceName) — give false info."
                else -> "${player.name} has no ability ($sourceName)."
            }
        }
        // …but not for a character whose ability is MEANT to fire from the
        // grave. The Ravenkeeper's own card already says "dead — acts anyway",
        // and the two lines contradicted each other (playtest B P2 #15).
        val actsWhenDead =
            CharacterRules.all[Character.normalizeId(player.characterId.orEmpty())]?.keepsAbilityWhenDead
        if (!player.alive && actsWhenDead != true) {
            notes += "${player.name} is dead — they normally don't act."
        }
        return notes
    }

    /** An alive Vortox whose own ability is working forces every Townsfolk lie (D11). */
    private fun vortoxActive(ctx: Ctx): Boolean = ctx.players.any {
        Character.normalizeId(it.characterId.orEmpty()) == "vortox" &&
            it.alive &&
            Status.hasAbility(ctx.state, ctx.lookup, it.id)
    }

    private fun commonCaveats(ctx: Ctx, characterId: String): List<String> {
        val notes = impairments(ctx.state, ctx.lookup, ctx.holder).toMutableList()
        val holderTeam = ctx.holder?.let { ctx.character(it)?.team }
            ?: ctx.lookup(characterId)?.team
        if (vortoxActive(ctx) && (holderTeam == Team.TOWNSFOLK || holderTeam == null)) {
            notes += "VORTOX in play — Townsfolk info must be FALSE."
        }
        return notes
    }

    /** Fills in caveats, obligation and the generated lies. */
    private fun finish(ctx: Ctx, characterId: String, result: InfoResult): InfoResult {
        val caveats = commonCaveats(ctx, characterId) + result.caveats
        val holderTeam = ctx.holder?.let { ctx.character(it)?.team }
            ?: ctx.lookup(characterId)?.team
        val malfunctions = ctx.holder?.let { !Status.hasAbility(ctx.state, ctx.lookup, it.id) } ?: false
        val mustLie = vortoxActive(ctx) && holderTeam == Team.TOWNSFOLK && !malfunctions
        val obligation = when {
            // An impaired Vortox loses the whole ability: Townsfolk info is not
            // forced false any more (lead D11).
            mustLie -> InfoObligation.MUST_LIE
            malfunctions || caveats.isNotEmpty() -> InfoObligation.MAY_LIE
            else -> InfoObligation.TRUTH
        }
        return result.copy(
            caveats = caveats,
            obligation = obligation,
            abilityMalfunctions = malfunctions,
            alternatives = (result.alternatives + generatedLies(ctx, result.answer)).distinct(),
        )
    }

    /**
     * A plausible lie for every answer shape (friction §10): the other numbers in
     * range, the opposite of a yes/no, a wrong character of the same team, a
     * different pair of players.
     */
    private fun generatedLies(ctx: Ctx, answer: Answer): List<Answer> = when (answer) {
        is Answer.Count -> {
            val max = maxOf(answer.max, answer.n)
            (answer.min..max).filter { it != answer.n }.take(MAX_LIES)
                .map { Answer.Count(it, answer.min, max) }
        }

        is Answer.YesNoAnswer -> listOf(Answer.YesNoAnswer(!answer.yes))

        is Answer.Characters -> {
            val real = answer.ids.map(Character::normalizeId).toSet()
            val team = answer.ids.firstOrNull()?.let { ctx.lookup(it)?.team }
            val pool = ctx.script.filterNot { Character.normalizeId(it.id) in real }
            val sameTeam = pool.filter { team == null || it.team == team }
            (sameTeam.ifEmpty { pool })
                .sortedBy { if (Character.normalizeId(it.id) in ctx.inPlayIds) 1 else 0 }
                .take(MAX_LIES)
                .map { Answer.Characters(listOf(it.id)) }
        }

        // A lie must have the SAME SHAPE as the truth: a "1 of 2 players"
        // answer lied about with one name is not a lie the storyteller can
        // show (playtest B P0 #1). Every generated set is the same size as the
        // true one and disjoint from it, so it is false by construction.
        is Answer.Players -> {
            val real = answer.ids.toSet()
            // Never the recipient: a card that points at the player holding it
            // tells them nothing, true or false.
            val others = ctx.state.seats.filterNot { it.id in real || it.id == ctx.holder?.id }
            when {
                answer.ids.size <= 1 ->
                    others.take(MAX_LIES).map { Answer.Players(listOf(it.id), answer.characterId) }

                others.size < answer.ids.size -> emptyList()

                else -> others.windowed(answer.ids.size, step = 1)
                    .take(MAX_LIES)
                    .map { window -> Answer.Players(window.map { it.id }, answer.characterId) }
            }
        }

        // Prose answers can only be lied about by the storyteller; the calculator
        // supplies its own alternatives where a false version is well defined.
        is Answer.Message -> emptyList()
    }

    private const val MAX_LIES = 5

    /** Nearest alive neighbour on each side of [player] (excluding them). */
    private fun aliveNeighbours(ctx: Ctx, player: Player): List<Player> {
        val seats = ctx.players
        val index = seats.indexOfFirst { it.id == player.id }
        if (index < 0 || seats.size < 2) return emptyList()
        fun scan(dir: Int): Player? {
            var i = (index + dir + seats.size) % seats.size
            while (i != index) {
                if (seats[i].alive) return seats[i]
                i = (i + dir + seats.size) % seats.size
            }
            return null
        }
        return listOfNotNull(scan(-1), scan(+1)).distinctBy { it.id }
    }

    // ---- per-character calculators --------------------------------------

    private fun chef(ctx: Ctx): InfoResult {
        val seats = ctx.players
        if (seats.size < 2) return InfoResult(Answer.Count(0, 0, 0), "0 pairs")
        val evil = seats.map { ctx.isEvil(it) }
        var pairs = 0
        val pairNames = mutableListOf<String>()
        for (i in seats.indices) {
            val j = (i + 1) % seats.size
            if (i == j) continue
            if (evil[i] && evil[j]) {
                pairs++
                pairNames += "${ctx.name(seats[i])}+${ctx.name(seats[j])}"
            }
        }
        return InfoResult(
            answer = Answer.Count(pairs, 0, maxOf(1, evil.count { it })),
            headline = "$pairs pair${if (pairs == 1) "" else "s"} of neighbouring evil players",
            detail = if (pairNames.isEmpty()) "" else "Pairs: ${pairNames.joinToString()}",
            caveats = misregistrations(ctx, seats),
        )
    }

    private fun empath(ctx: Ctx): InfoResult {
        val holder = ctx.holder
            ?: return InfoResult(Answer.Message("?"), "Select the Empath's seat first")
        val neighbours = aliveNeighbours(ctx, holder)
        val evilCount = neighbours.count { ctx.isEvil(it) }
        return InfoResult(
            answer = Answer.Count(evilCount, 0, 2),
            headline = "$evilCount of ${holder.name}'s alive neighbours ${if (evilCount == 1) "is" else "are"} evil",
            detail = neighbours.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, neighbours),
        )
    }

    private fun clockmaker(ctx: Ctx): InfoResult {
        val seats = ctx.players
        val demonIdx = seats.indexOfFirst { ctx.character(it)?.team == Team.DEMON }
        if (demonIdx < 0) return InfoResult(Answer.Message("?"), "No Demon in the grimoire")
        var best: Int? = null
        var bestName = ""
        for ((i, p) in seats.withIndex()) {
            if (ctx.character(p)?.team != Team.MINION) continue
            val d = kotlin.math.abs(i - demonIdx)
            val steps = minOf(d, seats.size - d)
            if (best == null || steps < best) {
                best = steps
                bestName = ctx.name(p)
            }
        }
        val answer = best
        return if (answer == null) {
            InfoResult(
                answer = Answer.Message("?"),
                headline = "No Minion in the grimoire",
                caveats = misregistrations(ctx, seats),
            )
        } else {
            InfoResult(
                answer = Answer.Count(answer, 1, maxOf(1, seats.size / 2)),
                headline = "$answer step${if (answer == 1) "" else "s"} from Demon to nearest Minion",
                detail = "Nearest Minion: $bestName",
                caveats = misregistrations(ctx, seats),
            )
        }
    }

    private fun shugenja(ctx: Ctx): InfoResult {
        val holder = ctx.holder
            ?: return InfoResult(Answer.Message("?"), "Select the Shugenja's seat first")
        val seats = ctx.players
        val index = seats.indexOfFirst { it.id == holder.id }
        var cw = -1
        var ccw = -1
        for (step in 1 until seats.size) {
            if (cw < 0 && ctx.isEvil(seats[(index + step) % seats.size])) cw = step
            if (ccw < 0 && ctx.isEvil(seats[(index - step + seats.size) % seats.size])) ccw = step
        }
        val notes = misregistrations(ctx, seats)
        val clockwise = Answer.Message("CLOCKWISE")
        val anti = Answer.Message("ANTI-CLOCKWISE")
        return when {
            cw < 0 && ccw < 0 -> InfoResult(Answer.Message("?"), "No evil players found")
            cw == ccw -> InfoResult(
                answer = clockwise,
                headline = "Equidistant ($cw steps each way) — point either direction",
                alternatives = listOf(anti),
                caveats = notes,
            )
            ccw < 0 || (cw in 1 until ccw) -> InfoResult(
                answer = clockwise,
                headline = "Closest evil is CLOCKWISE ($cw steps)",
                alternatives = listOf(anti),
                caveats = notes,
            )
            else -> InfoResult(
                answer = anti,
                headline = "Closest evil is ANTI-CLOCKWISE ($ccw steps)",
                alternatives = listOf(clockwise),
                caveats = notes,
            )
        }
    }

    private fun oracle(ctx: Ctx): InfoResult {
        val dead = ctx.players.filter { !it.alive }
        val evilDead = dead.filter { ctx.isEvil(it) }
        return InfoResult(
            answer = Answer.Count(evilDead.size, 0, maxOf(1, dead.size)),
            headline = "${evilDead.size} dead player${if (evilDead.size == 1) " is" else "s are"} evil",
            detail = if (dead.isEmpty()) {
                "No one is dead"
            } else {
                dead.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" }
            },
            caveats = misregistrations(ctx, dead),
        )
    }

    private fun undertaker(ctx: Ctx): InfoResult {
        val day = relevantDay(ctx.state)
        // ONE truth for the gate and the answer (playtest B2-4): the day's
        // `ExecutionRecord` (canonical, lead D30) or a death the seat sheet
        // recorded with cause EXECUTION.
        val executedId = NightPlan.executedTodayId(ctx.state, day)
            ?: return InfoResult(
                Answer.Message("—"),
                "No one was executed today — the Undertaker doesn't wake",
            )
        val executed = ctx.state.deaths.lastOrNull { it.playerId == executedId && it.day == day }
        val player = ctx.state.player(executedId)
        val characterId = executed?.characterIdAtDeath ?: player?.characterId
        val character = characterId?.let(ctx.lookup)
        return InfoResult(
            answer = Answer.Characters(listOfNotNull(characterId)),
            headline = "Show: ${character?.name ?: "?"}",
            detail = "${player?.name ?: "?"} was executed today",
            // The physical info token reads "THIS CHARACTER DIED TODAY"; the
            // card printed the generic half of it (playtest B P2 #17).
            cardPrefix = "THIS CHARACTER DIED TODAY",
            caveats = player?.let { misregistrations(ctx, listOf(it)) } ?: emptyList(),
        )
    }

    private fun townCrier(ctx: Ctx): InfoResult {
        val today = ctx.state.nominations.filter { it.day == relevantDay(ctx.state) && !it.isExile }
        val minionNominators = today.mapNotNull { n ->
            ctx.state.player(n.nominatorId)?.takeIf { ctx.character(it)?.team == Team.MINION }
        }
        return InfoResult(
            answer = Answer.YesNoAnswer(minionNominators.isNotEmpty()),
            headline = if (minionNominators.isNotEmpty()) {
                "YES — a Minion nominated today"
            } else {
                "NO — no Minion nominated today"
            },
            detail = minionNominators.joinToString { ctx.name(it) },
            caveats = misregistrations(ctx, today.mapNotNull { ctx.state.player(it.nominatorId) }),
        )
    }

    private fun flowergirl(ctx: Ctx): InfoResult {
        // Supporting a Traveller exile is not a vote and never registers to
        // the Flowergirl, even though the UI records exile supporters in the
        // same Nomination structure for auditability.
        val today = ctx.state.nominations.filter {
            it.day == relevantDay(ctx.state) && !it.isExile
        }
        val demonIds = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }.map { it.id }.toSet()
        val voted = today.any { n -> n.voterIds.any { it in demonIds } }
        return InfoResult(
            answer = Answer.YesNoAnswer(voted),
            headline = if (voted) "YES — the Demon voted today" else "NO — the Demon did not vote today",
            caveats = misregistrations(ctx, ctx.players.filter { it.id in demonIds }) +
                if (today.any { it.voterIds.isEmpty() && it.votes > 0 }) {
                    listOf("Some votes were tallied without recording who voted — verify manually.")
                } else {
                    emptyList()
                },
        )
    }

    private fun fortuneTeller(ctx: Ctx, targets: List<Long>): InfoResult {
        val chosen = validTargets(ctx, targets, 2)
            ?: return InfoResult(
                Answer.Message("?"),
                "Pick 2 different valid players the Fortune Teller chose",
            )
        val herringKey = Tokens.key("fortuneteller", "Red Herring")
        val demonHit = chosen.filter { ctx.character(it)?.team == Team.DEMON }
        val herringHit = chosen.filter { p -> hasToken(ctx, p, herringKey) }
        val yes = demonHit.isNotEmpty() || herringHit.isNotEmpty()
        val reasons = buildList {
            demonHit.forEach { add("${ctx.name(it)} is the Demon") }
            herringHit.forEach { add("${ctx.name(it)} is the red herring") }
        }
        val noHerring = ctx.players.none { p -> hasToken(ctx, p, herringKey) }
        return InfoResult(
            answer = Answer.YesNoAnswer(yes),
            headline = if (yes) "YES" else "NO",
            detail = reasons.joinToString(),
            caveats = misregistrations(ctx, chosen) +
                if (noHerring) {
                    listOf("No 'Red Herring' reminder placed yet — assign one good player as the red herring.")
                } else {
                    emptyList()
                },
        )
    }

    /** Token presence, matched on `(sourceId, label)` case-insensitively (lead D5). */
    private fun hasToken(ctx: Ctx, player: Player, key: String): Boolean =
        player.reminders.any { Tokens.key(it) == key } ||
            Status.effectsOn(ctx.state, ctx.lookup, player.id)
                .any { Tokens.key(it.sourceCharacterId, it.label) == key }

    private fun dreamer(ctx: Ctx, targets: List<Long>): InfoResult {
        val target = validTargets(ctx, targets, 1)?.single()
            ?: return InfoResult(Answer.Message("?"), "Pick the player the Dreamer chose")
        val character = ctx.character(target)
        val good = character?.team?.isEvil == false
        return InfoResult(
            answer = Answer.Characters(listOfNotNull(target.characterId)),
            headline = "${ctx.name(target)} is the ${character?.name ?: "?"}",
            detail = "Show that token plus 1 ${if (good) "evil" else "good"} character token of your choice",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    private fun seamstress(ctx: Ctx, targets: List<Long>): InfoResult {
        val chosen = validTargets(ctx, targets, 2)
            ?: return InfoResult(
                Answer.Message("?"),
                "Pick 2 different valid players the Seamstress chose",
            )
        val same = ctx.isEvil(chosen[0]) == ctx.isEvil(chosen[1])
        return InfoResult(
            answer = Answer.YesNoAnswer(same),
            headline = if (same) "YES — same alignment" else "NO — different alignments",
            detail = chosen.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, chosen),
        )
    }

    private fun villageIdiot(ctx: Ctx, targets: List<Long>): InfoResult {
        val target = validTargets(ctx, targets, 1)?.single()
            ?: return InfoResult(Answer.Message("?"), "Pick the player the Village Idiot chose")
        return InfoResult(
            answer = Answer.YesNoAnswer(ctx.isEvil(target)),
            headline = "${ctx.name(target)} is ${if (ctx.isEvil(target)) "EVIL" else "GOOD"}",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    private fun revealCharacter(ctx: Ctx, targets: List<Long>, who: String): InfoResult {
        val target = validTargets(ctx, targets, 1)?.single()
            ?: return InfoResult(Answer.Message("?"), "Pick the player the $who chose/knows")
        val character = ctx.character(target)
        return InfoResult(
            answer = Answer.Characters(listOfNotNull(target.characterId)),
            headline = "${ctx.name(target)} is the ${character?.name ?: "?"}",
            // The card printed the generic stem "THIS CHARACTER" over the token
            // and the ledger recorded it — the physical info token the
            // Ravenkeeper, the Grandmother and the Harlot are shown reads
            // "THIS PLAYER IS" (playtest B2-10, the residue of B-17).
            cardPrefix = "THIS PLAYER IS",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    /**
     * The Cult Leader's neighbours and their alignments — the storyteller's own
     * crib for the three-way choice on the row. The Cult Leader is woken only
     * when their alignment actually changed, and then sees a thumb, never a card
     * pointing at their neighbours (B2-2's sweep).
     */
    private fun cultLeader(ctx: Ctx): InfoResult {
        val holder = ctx.holder
            ?: return InfoResult(
                Answer.Message("?"),
                "Select the Cult Leader's seat first",
                audience = InfoAudience.STORYTELLER,
            )
        val neighbours = aliveNeighbours(ctx, holder)
        return InfoResult(
            answer = Answer.Players(neighbours.map { it.id }),
            headline = "Alive neighbours: " + neighbours.joinToString {
                "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})"
            },
            detail = "The Cult Leader becomes the alignment of one of them (your choice).",
            audience = InfoAudience.STORYTELLER,
        )
    }

    private fun king(ctx: Ctx): InfoResult {
        val alive = ctx.players.count { it.alive }
        val dead = ctx.players.size - alive
        if (dead < alive) {
            return InfoResult(
                Answer.Message("—"),
                "Dead ($dead) don't outnumber living ($alive) — the King doesn't wake",
            )
        }
        // Never the King's own token: "you learn 1 ALIVE character" is
        // information, and their own is not.
        val aliveSeats = ctx.players.filter { it.alive && it.id != ctx.holder?.id }
        return InfoResult(
            answer = Answer.Characters(aliveSeats.mapNotNull { it.characterId }.take(1)),
            headline = "Show 1 alive character",
            detail = "In play & alive: " + aliveSeats.mapNotNull { ctx.character(it)?.name }.joinToString(),
        )
    }

    /**
     * "You start knowing that **1 of 2 players** is a particular X" — the
     * Washerwoman, the Librarian and the Investigator.
     *
     * The card is a PAIR and one character token: exactly one of the two really
     * is that character, the other is a decoy, and **neither is the recipient**
     * (playtest B P0 #1 — the engine used to hand the whole candidate set plus
     * the first candidate's token to the card, which with one candidate is a
     * flat reveal and with five is nonsense).
     */
    private fun startKnowing(ctx: Ctx, team: Team, label: String): InfoResult {
        val holderId = ctx.holder?.id
        val others = ctx.players.filter { it.id != holderId }
        val inPlay = others.filter { ctx.character(it)?.team == team }
        if (inPlay.isEmpty()) {
            // A lie here keeps the real shape: a pair of players and a
            // character token that is NOT in play.
            val decoyPair = others.firstOrNull()?.let { pointPair(ctx, it, holderId) }
            return InfoResult(
                answer = Answer.Count(0, 0, 0),
                headline = "No $label in play" + if (team == Team.OUTSIDER) " — show the 0 signal" else "",
                detail = "Show the '0' signal — there is no $label to point at.",
                alternatives = if (decoyPair == null) {
                    emptyList()
                } else {
                    ctx.script
                        .filter { it.team == team && Character.normalizeId(it.id) !in ctx.inPlayIds }
                        .take(MAX_LIES)
                        .map { Answer.Players(decoyPair, it.id) }
                },
                caveats = misregistrations(ctx, ctx.players),
            )
        }
        val trueHolder = inPlay.first()
        val characterId = trueHolder.characterId
        val characterName = ctx.character(trueHolder)?.name ?: "?"
        val pair = pointPair(ctx, trueHolder, holderId)
            ?: return InfoResult(
                answer = Answer.Players(listOf(trueHolder.id), characterId),
                headline = "${ctx.name(trueHolder)} is the $characterName — nobody left to pair them with",
                caveats = misregistrations(ctx, ctx.players),
            )
        val decoyName = pair.filter { it != trueHolder.id }
            .mapNotNull { ctx.state.player(it)?.name }
            .joinToString()
        return InfoResult(
            answer = Answer.Players(pair, characterId),
            headline = "1 of 2 players is the $characterName — " +
                "${ctx.name(trueHolder)} really is, $decoyName is the decoy",
            detail = "Show the $characterName token, then point at " +
                pair.mapNotNull { id -> ctx.state.player(id)?.name }.joinToString(" and ") +
                ". Other $label in play: " +
                (
                    inPlay.filter { it.id != trueHolder.id }
                        .joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" }
                        .ifEmpty { "none" }
                    ),
            // The two lies that keep the card's shape: same pair with a wrong
            // token, and the true token over a pair that does not contain them.
            alternatives = startKnowingLies(ctx, team, pair, trueHolder, characterId),
            // Every OTHER real candidate, as a card of its own: which one to
            // show is the storyteller's choice and it belongs on the card
            // (playtest B2-15).
            alsoTrue = inPlay.asSequence()
                .filter { it.id != trueHolder.id }
                .mapNotNull { other ->
                    pointPair(ctx, other, holderId)?.let { Answer.Players(it, other.characterId) }
                }
                .take(MAX_TRUE_CHOICES)
                .toList(),
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    /** How many other true "1 of 2" cards the storyteller is offered outright. */
    private const val MAX_TRUE_CHOICES = 4

    /**
     * [trueHolder] plus one decoy, in seat order — never [excludeId], who is the
     * player the card is being shown to.
     */
    private fun pointPair(ctx: Ctx, trueHolder: Player, excludeId: Long?): List<Long>? {
        val seats = ctx.players
        val start = seats.indexOfFirst { it.id == trueHolder.id }
        if (start < 0) return null
        for (offset in 1 until seats.size) {
            val candidate = seats[(start + offset) % seats.size]
            if (candidate.id == trueHolder.id || candidate.id == excludeId) continue
            val pairIds = seats.filter { it.id == trueHolder.id || it.id == candidate.id }.map { it.id }
            return pairIds
        }
        return null
    }

    /** Same shape, wrong content: a wrong token, or a pair without the true one. */
    private fun startKnowingLies(
        ctx: Ctx,
        team: Team,
        pair: List<Long>,
        trueHolder: Player,
        characterId: String?,
    ): List<Answer> {
        val wrongTokens = ctx.script
            .filter {
                it.team == team && Character.normalizeId(it.id) != Character.normalizeId(characterId.orEmpty())
            }
            .sortedBy { if (Character.normalizeId(it.id) in ctx.inPlayIds) 1 else 0 }
            .take(2)
            .map { Answer.Players(pair, it.id) }
        val wrongPair = ctx.players
            .filter { it.id != trueHolder.id && it.id != ctx.holder?.id }
            .take(2)
            .takeIf { it.size == 2 }
            ?.let { listOf(Answer.Players(it.map { p -> p.id }, characterId)) }
            .orEmpty()
        return wrongTokens + wrongPair
    }

    private fun sage(ctx: Ctx): InfoResult {
        val demons = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }
        if (demons.isEmpty()) return InfoResult(Answer.Message("?"), "No Demon in the grimoire")
        // "You learn 2 players, 1 of which is the Demon" — a bare Demon is a
        // full reveal, so the pair is built here, never by the storyteller.
        val demon = demons.first()
        val pair = pointPair(ctx, demon, ctx.holder?.id) ?: listOf(demon.id)
        return InfoResult(
            answer = Answer.Players(pair),
            headline = "1 of 2 players is the Demon — ${ctx.name(demon)} really is",
            detail = "Point at " + pair.mapNotNull { ctx.state.player(it)?.name }.joinToString(" and "),
            caveats = listOf("Only if the Demon killed the Sage; other deaths don't wake them."),
        )
    }

    private fun knight(ctx: Ctx): InfoResult {
        val demons = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }
        val notDemon = ctx.players.filter { p ->
            p.id != ctx.holder?.id && demons.none { it.id == p.id }
        }
        return InfoResult(
            answer = Answer.Players(notDemon.take(2).map { it.id }),
            headline = "Point to 2 players that are NOT the Demon",
            detail = "Demon: ${demons.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun steward(ctx: Ctx): InfoResult {
        // Never the Steward themselves: they already know they are good.
        val good = ctx.players.filter { !ctx.isEvil(it) && it.id != ctx.holder?.id }
        return InfoResult(
            answer = Answer.Players(good.take(1).map { it.id }),
            headline = "Point to 1 good player" +
                good.firstOrNull()?.let { " — ${ctx.name(it)}" }.orEmpty(),
            detail = "Good players: ${good.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun noble(ctx: Ctx): InfoResult {
        val others = ctx.players.filter { it.id != ctx.holder?.id }
        val evil = others.filter { ctx.isEvil(it) }
        val good = others.filter { !ctx.isEvil(it) }
        val trio = (evil.take(1) + good.take(2)).sortedBy { p ->
            ctx.players.indexOfFirst { it.id == p.id }
        }
        return InfoResult(
            answer = Answer.Players(trio.map { it.id }),
            headline = "Point to 3 players: exactly 1 evil, 2 good",
            detail = "Evil players: ${evil.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun bountyHunter(ctx: Ctx): InfoResult {
        val evil = ctx.players.filter { ctx.isEvil(it) && it.id != ctx.holder?.id }
        return InfoResult(
            answer = Answer.Players(evil.take(1).map { it.id }),
            headline = "Point to 1 evil player (mark them 'Known')",
            detail = "Evil players: ${evil.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" }}",
            caveats = listOf("Remember: 1 Townsfolk is evil in a Bounty Hunter game."),
        )
    }

    /** Rewritten (lead D13): what tonight's sheet ACTUALLY did, not the static order. */
    private fun chambermaid(ctx: Ctx, targets: List<Long>): InfoResult {
        val chosen = validTargets(ctx, targets, 2)
            ?: return InfoResult(
                Answer.Message("?"),
                "Pick 2 different valid players the Chambermaid chose",
            )
        val count = NightPlan.wokeCount(ctx.state, ctx.lookup, chosen.map { it.id })
        val each = chosen.map { it to NightPlan.wokeCount(ctx.state, ctx.lookup, listOf(it.id)) }
        return InfoResult(
            answer = Answer.Count(count, 0, 2),
            headline = "$count of the 2 woke tonight for their own ability",
            detail = each.joinToString { (p, n) -> "${ctx.name(p)}: ${if (n > 0) "woke" else "did not wake"}" },
            caveats = listOf(
                "Counts own-ability wakes only: Minion info, an Exorcist's target and a " +
                    "silenced Demon do not count.",
            ),
        )
    }

    /** Rewritten (lead D13): the malfunctions the engine recorded tonight. */
    private fun mathematician(ctx: Ctx): InfoResult {
        val count = NightPlan.malfunctionCount(ctx.state, ctx.state.cycle, excluding = ctx.holder?.id)
        return InfoResult(
            answer = Answer.Count(count, 0, maxOf(1, ctx.state.alivePlayers.size)),
            headline = "$count abilit${if (count == 1) "y" else "ies"} malfunctioned since dawn",
            detail = "The Mathematician never detects their own ability failing.",
            caveats = listOf(
                "Only malfunctions the engine could prove are counted — add any you ruled by hand.",
            ),
        )
    }

    private fun balloonist(ctx: Ctx): InfoResult {
        val others = ctx.players.filter { it.id != ctx.holder?.id }
        val byType = others
            .mapNotNull { p -> ctx.character(p)?.let { c -> c.team to p } }
            .groupBy({ it.first }, { it.second })
        return InfoResult(
            answer = Answer.Players(others.take(1).map { it.id }),
            headline = "Show a player of a DIFFERENT character type than last night",
            detail = byType.entries.joinToString("\n") { (team, ps) ->
                "${team.displayName}: ${ps.joinToString { ctx.name(it) }}"
            },
        )
    }

    // ---- WP2 additions ---------------------------------------------------

    /** "You start knowing which Outsiders are in play." */
    private fun godfather(ctx: Ctx): InfoResult {
        val outsiders = ctx.players.filter { ctx.character(it)?.team == Team.OUTSIDER }
        val ids = outsiders.mapNotNull { it.characterId }
        return InfoResult(
            answer = Answer.Characters(ids),
            headline = if (ids.isEmpty()) {
                "No Outsiders are in play"
            } else {
                "Outsiders in play: " + outsiders.mapNotNull { ctx.character(it)?.name }.joinToString()
            },
            detail = "Show each of those character tokens.",
            alternatives = ctx.script
                .filter { it.team == Team.OUTSIDER && Character.normalizeId(it.id) !in ctx.inPlayIds }
                .take(MAX_LIES)
                .map { Answer.Characters(listOf(it.id)) },
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    /** "How many of your guesses were correct" — counted from the day's ledger. */
    private fun juggler(ctx: Ctx): InfoResult {
        val day = relevantDay(ctx.state)
        val guesses = ctx.state.ledger.filter {
            Character.normalizeId(it.sourceId) == "juggler" &&
                it.kind == LedgerKind.STATEMENT &&
                it.cycle == day
        }
        var correct = 0
        val lines = mutableListOf<String>()
        for (guess in guesses) {
            for ((i, seat) in guess.targetIds.withIndex()) {
                val guessed = guess.characterIds.getOrNull(i) ?: continue
                val player = ctx.state.player(seat) ?: continue
                val right = Character.normalizeId(player.characterId.orEmpty()) ==
                    Character.normalizeId(guessed)
                if (right) correct++
                lines += "${ctx.name(player)} = ${ctx.lookup(guessed)?.name ?: guessed}" +
                    if (right) " ✓" else " ✗"
            }
        }
        val total = guesses.sumOf { it.targetIds.size }
        return InfoResult(
            answer = Answer.Count(correct, 0, maxOf(1, total)),
            headline = "$correct of $total juggles were correct",
            detail = lines.joinToString(", "),
            caveats = if (guesses.isEmpty()) {
                listOf("No juggle was recorded on day $day — record it on the Day tab first.")
            } else {
                emptyList()
            },
        )
    }

    /**
     * "Is the player you chose the Demon?" — the STORYTELLER's confirmation.
     *
     * The information flows to the Demon (who is woken and shown the Exorcist),
     * never to the Exorcist: *"the Demon, if chosen, learns who you are, then
     * doesn't act tonight"*. The app used to offer `SHOW “YES” TO <Exorcist>`
     * directly under its own line saying they are not told (B2-2 / D2-3).
     */
    private fun exorcist(ctx: Ctx, targets: List<Long>): InfoResult {
        val target = validTargets(ctx, targets, 1)?.single()
            ?: return InfoResult(
                Answer.Message("?"),
                "Pick the player the Exorcist chose",
                audience = InfoAudience.STORYTELLER,
            )
        val isDemon = ctx.character(target)?.team == Team.DEMON
        return InfoResult(
            answer = Answer.YesNoAnswer(isDemon),
            headline = if (isDemon) {
                "YES — ${ctx.name(target)} is the Demon; they do not act tonight"
            } else {
                "NO — ${ctx.name(target)} is not the Demon"
            },
            detail = "The Exorcist is not told either way; this is for you.",
            caveats = misregistrations(ctx, listOf(target)),
            audience = InfoAudience.STORYTELLER,
        )
    }

    /**
     * "Is that character in play, and where?" — for the Courtier's pick.
     *
     * The Courtier names a character and learns NOTHING. This is the
     * storyteller's own crib sheet for finding the seat to make drunk, and it
     * lists every character in play, Demon and Minion included — it was being
     * offered as a card to hold up to the Courtier (B2-2 / D2-2).
     */
    private fun courtier(ctx: Ctx): InfoResult {
        val holder = ctx.holder
        val inPlay = ctx.state.seats.filter { it.characterId != null }
        return InfoResult(
            answer = Answer.Characters(inPlay.mapNotNull { it.characterId }),
            headline = "Whoever they name: these seats hold the characters in play",
            detail = inPlay.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" },
            caveats = buildList {
                if (holder != null && !Status.hasAbility(ctx.state, ctx.lookup, holder.id)) {
                    add("${holder.name}'s ability is not working — nobody actually gets drunk.")
                }
            },
            audience = InfoAudience.STORYTELLER,
        )
    }

    /** "Two statements, one true" — the true halves the grimoire can prove. */
    private fun savant(ctx: Ctx): InfoResult {
        val evil = ctx.players.filter { ctx.isEvil(it) }
        val dead = ctx.players.count { !it.alive }
        val facts = listOf(
            "There are ${evil.size} evil players.",
            "$dead player${if (dead == 1) " is" else "s are"} dead.",
            "The evil players are: ${evil.joinToString { ctx.name(it) }}.",
        )
        return InfoResult(
            answer = Answer.Message(facts.first()),
            headline = "True facts you can pair with a false one",
            detail = facts.joinToString("\n"),
            alternatives = facts.drop(1).map { Answer.Message(it) },
            caveats = listOf("Say one true and one false statement — the Savant must not know which."),
        )
    }

    /**
     * "Is the Tea Lady's protection on?" — both neighbours good and sober.
     *
     * The Tea Lady is never woken and never told; this is the storyteller's own
     * check before a death is applied (B2-2's sweep).
     */
    private fun teaLady(ctx: Ctx): InfoResult {
        val holder = ctx.holder
            ?: return InfoResult(
                Answer.Message("?"),
                "Select the Tea Lady's seat first",
                audience = InfoAudience.STORYTELLER,
            )
        val neighbours = aliveNeighbours(ctx, holder)
        val bothGood = neighbours.isNotEmpty() && neighbours.none { ctx.isEvil(it) }
        val working = Status.hasAbility(ctx.state, ctx.lookup, holder.id)
        val on = bothGood && working
        return InfoResult(
            answer = Answer.YesNoAnswer(on),
            headline = if (on) {
                "YES — both alive neighbours are good; neither can die"
            } else {
                "NO — the Tea Lady's protection is not on"
            },
            detail = neighbours.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, neighbours),
            audience = InfoAudience.STORYTELLER,
        )
    }

    /**
     * Duchess — "each visitor learns how many EVIL players visited today". The
     * number counts the visitors themselves (the "False Info" player included)
     * and is legally 0..3; the "False Info" visitor is shown any OTHER number,
     * which is what [InfoResult.alternatives] already offers.
     *
     * The Duchess holds no seat, so [Ctx.holder] is null here by construction:
     * the answer is a property of the marked seats, not of a holder.
     */
    private fun duchess(ctx: Ctx): InfoResult {
        val visitors = duchessVisitors(ctx)
        val evil = visitors.count { ctx.isEvil(it) }
        return InfoResult(
            answer = Answer.Count(evil, 0, DUCHESS_VISITORS),
            headline = if (visitors.isEmpty()) {
                "No visitors are marked yet — mark 3 players during the day"
            } else {
                "$evil of ${visitors.size} marked visitors ${if (evil == 1) "is" else "are"} evil"
            },
            detail = visitors.joinToString {
                "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})"
            },
            caveats = misregistrations(ctx, visitors),
        )
    }

    /**
     * Choirboy — "you learn which player is the Demon" (the PLAYER, not the
     * character), on the night the Demon kills the King.
     */
    private fun choirboy(ctx: Ctx): InfoResult {
        val demons = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }
        if (demons.isEmpty()) {
            return InfoResult(Answer.Message("?"), "No Demon in the grimoire")
        }
        return InfoResult(
            answer = Answer.Players(demons.map { it.id }),
            headline = "The Demon is " + demons.joinToString { ctx.name(it) },
            detail = "They learn the PLAYER, never the character.",
            caveats = misregistrations(ctx, demons),
        )
    }

    /** The Demon's first night: "you learn which player is the King". */
    private fun kingToDemon(ctx: Ctx): InfoResult {
        val kings = ctx.players.filter {
            Character.normalizeId(it.characterId.orEmpty()) == "king"
        }
        if (kings.isEmpty()) return InfoResult(Answer.Message("—"), "No King in play")
        return InfoResult(
            answer = Answer.Players(kings.map { it.id }, characterId = "king"),
            headline = "The King is " + kings.joinToString { ctx.name(it) },
            detail = "Show the Demon the 'This player is' and King tokens, then point.",
        )
    }

    /**
     * "They learn you are the X" — the Nightwatchman's reveal, the Preacher's
     * selection. The answer is a token, and the only thing the grimoire has to
     * get right is WHICH token.
     */
    private fun selfReveal(ctx: Ctx, characterId: String, prefix: String): InfoResult = InfoResult(
        answer = Answer.Characters(listOf(characterId)),
        headline = "$prefix ${ctx.lookup(characterId)?.name ?: characterId}",
    )

    /** "They become the X" — the Farmer's replacement is shown their new token. */
    private fun becomes(ctx: Ctx, characterId: String): InfoResult = InfoResult(
        answer = Answer.Characters(listOf(characterId)),
        headline = "YOU ARE the ${ctx.lookup(characterId)?.name ?: characterId}",
        detail = "They do NOT get first-night information.",
    )

    /** Beggar — "if a dead player gives you their vote token, you learn their alignment". */
    private fun beggar(ctx: Ctx, targets: List<Long>): InfoResult {
        val donor = validTargets(ctx, targets, 1)?.firstOrNull()
            ?: return InfoResult(Answer.Message("?"), "Select the seat that gave the token")
        val evil = ctx.isEvil(donor)
        return InfoResult(
            answer = Answer.Message(if (evil) "EVIL" else "GOOD"),
            headline = "${ctx.name(donor)} is ${if (evil) "EVIL" else "GOOD"}",
            detail = "Show the Beggar privately. Their TRUE alignment, not what they register as.",
            // A prose answer generates no lie on its own, and this one has
            // exactly one: the other side.
            alternatives = listOf(Answer.Message(if (evil) "GOOD" else "EVIL")),
            caveats = misregistrations(ctx, listOf(donor)),
        )
    }

    /** How many players the Duchess marks each day. */
    private const val DUCHESS_VISITORS = 3

    /**
     * Seats carrying either Duchess mark, in seat order. Matched on
     * `Tokens.key(sourceId, label)`, never on the label alone (lead D5).
     */
    private fun duchessVisitors(ctx: Ctx): List<Player> {
        val keys = setOf(
            Tokens.key("duchess", "Visitor"),
            Tokens.key("duchess", "False Info"),
        )
        return ctx.state.seats.filter { seat ->
            seat.reminders.any { Tokens.key(it) in keys } ||
                ctx.state.effects.any {
                    it.targetId == seat.id && Tokens.key(it.sourceCharacterId, it.label) in keys
                }
        }
    }
}
