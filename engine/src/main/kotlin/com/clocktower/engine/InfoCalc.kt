package com.clocktower.engine

/**
 * Computes the TRUE information a night ability would give, straight from
 * the grimoire state, together with every caveat the storyteller must weigh:
 * drunkenness/poisoning of the info holder, misregistration (Spy, Recluse),
 * and the Vortox inverting townsfolk info.
 */
object InfoCalc {

    /** What the calculator produced for one step. */
    data class InfoResult(
        /** The true answer, e.g. "1 of Ana's alive neighbours is evil". */
        val headline: String,
        /** Supporting detail, e.g. which players/pairs were counted. */
        val detail: String = "",
        /** Warnings: impairment, misregistration, Vortox, approximations. */
        val caveats: List<String> = emptyList(),
    )

    /** How many player selections a character's calc needs (0 for none). */
    fun targetsNeeded(characterId: String): Int = when (characterId) {
        "fortuneteller", "seamstress", "chambermaid" -> 2
        "dreamer", "villageidiot", "ravenkeeper", "grandmother" -> 1
        else -> 0
    }

    /** Whether we can compute anything useful for this character. */
    fun supports(characterId: String): Boolean = characterId in setOf(
        "chef", "empath", "clockmaker", "shugenja", "oracle", "undertaker",
        "towncrier", "flowergirl", "fortuneteller", "dreamer", "seamstress",
        "villageidiot", "cultleader", "king", "washerwoman", "librarian",
        "investigator", "knight", "steward", "noble", "bountyhunter",
        "chambermaid", "mathematician", "balloonist", "ravenkeeper",
        "grandmother",
    )

    fun compute(
        data: GameData,
        state: GameState,
        characterId: String,
        holderId: Long?,
        targets: List<Long> = emptyList(),
    ): InfoResult? {
        if (!supports(characterId)) return null
        val holder = holderId?.let { state.player(it) }
        val lookup: (String) -> Character? = { id ->
            state.script.customCharacters.find { it.id == id } ?: data.character(id)
        }
        val ctx = Ctx(data, state, lookup, holder)
        val result = when (characterId) {
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
            "steward" -> steward(ctx)
            "noble" -> noble(ctx)
            "bountyhunter" -> bountyHunter(ctx)
            "chambermaid" -> chambermaid(ctx, targets)
            "mathematician" -> InfoResult(
                headline = "Count abilities that malfunctioned since dawn",
                caveats = listOf("Track malfunctions manually — drunk/poisoned players whose ability 'worked' abnormally count."),
            )
            "balloonist" -> balloonist(ctx)
            else -> null
        } ?: return null
        return result.copy(caveats = commonCaveats(ctx, characterId) + result.caveats)
    }

    // ---- shared helpers -------------------------------------------------

    private class Ctx(
        val data: GameData,
        val state: GameState,
        val lookup: (String) -> Character?,
        val holder: Player?,
    ) {
        val players: List<Player> get() = state.players
        fun character(p: Player): Character? = p.characterId?.let(lookup)
        fun isEvil(p: Player): Boolean = p.isEvil(lookup)
        fun name(p: Player): String = p.name
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
            when (p.characterId) {
                "spy" -> notes += "${ctx.name(p)} is the Spy — may register as good / a Townsfolk or Outsider."
                "recluse" -> notes += "${ctx.name(p)} is the Recluse — may register as evil / a Minion or Demon."
            }
        }
        return notes
    }

    /** Impairment: is the info holder drunk, poisoned, or ability-less? */
    fun impairments(state: GameState, lookup: (String) -> Character?, player: Player?): List<String> {
        player ?: return emptyList()
        val notes = mutableListOf<String>()
        if (player.characterId == "drunk") {
            notes += "${player.name} IS the Drunk — their ability malfunctions."
        }
        for (r in player.reminders) {
            val label = r.label.lowercase()
            when {
                "poison" in label -> notes += "${player.name} is POISONED (${sourceName(lookup, r)}) — give false info."
                "drunk" in label -> notes += "${player.name} is DRUNK (${sourceName(lookup, r)}) — give false info."
                label == "no ability" -> notes += "${player.name} has no ability (${sourceName(lookup, r)})."
            }
        }
        if (!player.alive) notes += "${player.name} is dead — they normally don't act."
        StatusEffects.derivedPoison(state, lookup)[player.id]?.let { notes += "$it — give false info." }
        return notes
    }

    private fun sourceName(lookup: (String) -> Character?, r: PlacedReminder): String =
        lookup(r.sourceId)?.name ?: "marker"

    private fun commonCaveats(ctx: Ctx, characterId: String): List<String> {
        val notes = impairments(ctx.state, ctx.lookup, ctx.holder).toMutableList()
        val holderTeam = ctx.holder?.let { ctx.character(it)?.team }
        val vortoxInPlay = ctx.players.any { it.characterId == "vortox" && it.alive }
        if (vortoxInPlay && (holderTeam == Team.TOWNSFOLK || holderTeam == null)) {
            notes += "VORTOX in play — Townsfolk info must be FALSE."
        }
        return notes
    }

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
        if (seats.size < 2) return InfoResult("0 pairs")
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
            headline = "$pairs pair${if (pairs == 1) "" else "s"} of neighbouring evil players",
            detail = if (pairNames.isEmpty()) "" else "Pairs: ${pairNames.joinToString()}",
            caveats = misregistrations(ctx, seats),
        )
    }

    private fun empath(ctx: Ctx): InfoResult {
        val holder = ctx.holder ?: return InfoResult("Select the Empath's seat first")
        val neighbours = aliveNeighbours(ctx, holder)
        val evilCount = neighbours.count { ctx.isEvil(it) }
        return InfoResult(
            headline = "$evilCount of ${holder.name}'s alive neighbours ${if (evilCount == 1) "is" else "are"} evil",
            detail = neighbours.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, neighbours),
        )
    }

    private fun clockmaker(ctx: Ctx): InfoResult {
        val seats = ctx.players
        val demonIdx = seats.indexOfFirst { ctx.character(it)?.team == Team.DEMON }
        if (demonIdx < 0) return InfoResult("No Demon in the grimoire")
        var best: Int? = null
        var bestName = ""
        for ((i, p) in seats.withIndex()) {
            if (ctx.character(p)?.team != Team.MINION) continue
            val d = kotlin.math.abs(i - demonIdx)
            val steps = minOf(d, seats.size - d)
            if (best == null || steps < best!!) {
                best = steps; bestName = ctx.name(p)
            }
        }
        return if (best == null) {
            InfoResult("No Minion in the grimoire", caveats = misregistrations(ctx, seats))
        } else {
            InfoResult(
                headline = "$best step${if (best == 1) "" else "s"} from Demon to nearest Minion",
                detail = "Nearest Minion: $bestName",
                caveats = misregistrations(ctx, seats),
            )
        }
    }

    private fun shugenja(ctx: Ctx): InfoResult {
        val holder = ctx.holder ?: return InfoResult("Select the Shugenja's seat first")
        val seats = ctx.players
        val index = seats.indexOfFirst { it.id == holder.id }
        var cw = -1
        var ccw = -1
        for (step in 1 until seats.size) {
            if (cw < 0 && ctx.isEvil(seats[(index + step) % seats.size])) cw = step
            if (ccw < 0 && ctx.isEvil(seats[(index - step + seats.size) % seats.size])) ccw = step
        }
        val notes = misregistrations(ctx, seats)
        return when {
            cw < 0 && ccw < 0 -> InfoResult("No evil players found")
            cw == ccw -> InfoResult(
                headline = "Equidistant ($cw steps each way) — point either direction",
                caveats = notes,
            )
            ccw < 0 || (cw in 1 until ccw) -> InfoResult(
                headline = "Closest evil is CLOCKWISE ($cw steps)",
                caveats = notes,
            )
            else -> InfoResult(
                headline = "Closest evil is ANTI-CLOCKWISE ($ccw steps)",
                caveats = notes,
            )
        }
    }

    private fun oracle(ctx: Ctx): InfoResult {
        val dead = ctx.players.filter { !it.alive }
        val evilDead = dead.filter { ctx.isEvil(it) }
        return InfoResult(
            headline = "${evilDead.size} dead player${if (evilDead.size == 1) " is" else "s are"} evil",
            detail = if (dead.isEmpty()) "No one is dead" else dead.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, dead),
        )
    }

    private fun undertaker(ctx: Ctx): InfoResult {
        val day = relevantDay(ctx.state)
        val executed = ctx.state.deaths.lastOrNull {
            it.cause == DeathCause.EXECUTION && it.day == day
        } ?: return InfoResult("No one was executed today — the Undertaker doesn't wake")
        val player = ctx.state.player(executed.playerId)
        val character = player?.let { ctx.character(it) }
        return InfoResult(
            headline = "Show: ${character?.name ?: "?"}",
            detail = "${player?.name ?: "?"} was executed today",
            caveats = player?.let { misregistrations(ctx, listOf(it)) } ?: emptyList(),
        )
    }

    private fun townCrier(ctx: Ctx): InfoResult {
        val today = ctx.state.nominations.filter { it.day == relevantDay(ctx.state) && !it.isExile }
        val minionNominators = today.mapNotNull { n ->
            ctx.state.player(n.nominatorId)?.takeIf { ctx.character(it)?.team == Team.MINION }
        }
        return InfoResult(
            headline = if (minionNominators.isNotEmpty()) "YES — a Minion nominated today" else "NO — no Minion nominated today",
            detail = minionNominators.joinToString { ctx.name(it) },
            caveats = misregistrations(ctx, today.mapNotNull { ctx.state.player(it.nominatorId) }),
        )
    }

    private fun flowergirl(ctx: Ctx): InfoResult {
        val today = ctx.state.nominations.filter { it.day == relevantDay(ctx.state) }
        val demonIds = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }.map { it.id }.toSet()
        val voted = today.any { n -> n.voterIds.any { it in demonIds } }
        return InfoResult(
            headline = if (voted) "YES — the Demon voted today" else "NO — the Demon did not vote today",
            caveats = misregistrations(ctx, ctx.players.filter { it.id in demonIds }) +
                if (today.any { it.voterIds.isEmpty() && it.votes > 0 }) {
                    listOf("Some votes were tallied without recording who voted — verify manually.")
                } else emptyList(),
        )
    }

    private fun fortuneTeller(ctx: Ctx, targets: List<Long>): InfoResult {
        if (targets.size < 2) return InfoResult("Pick the 2 players the Fortune Teller chose")
        val chosen = targets.mapNotNull { ctx.state.player(it) }
        val demonHit = chosen.filter { ctx.character(it)?.team == Team.DEMON }
        val herringHit = chosen.filter { p -> p.reminders.any { it.label.equals("Red herring", true) } }
        val yes = demonHit.isNotEmpty() || herringHit.isNotEmpty()
        val reasons = buildList {
            demonHit.forEach { add("${ctx.name(it)} is the Demon") }
            herringHit.forEach { add("${ctx.name(it)} is the red herring") }
        }
        val noHerring = ctx.players.none { p -> p.reminders.any { it.label.equals("Red herring", true) } }
        return InfoResult(
            headline = if (yes) "YES" else "NO",
            detail = reasons.joinToString(),
            caveats = misregistrations(ctx, chosen) +
                if (noHerring) listOf("No 'Red herring' reminder placed yet — assign one good player as the red herring.") else emptyList(),
        )
    }

    private fun dreamer(ctx: Ctx, targets: List<Long>): InfoResult {
        val target = targets.firstOrNull()?.let { ctx.state.player(it) }
            ?: return InfoResult("Pick the player the Dreamer chose")
        val character = ctx.character(target)
        val good = character?.team?.isEvil == false
        return InfoResult(
            headline = "${ctx.name(target)} is the ${character?.name ?: "?"}",
            detail = "Show that token plus 1 ${if (good) "evil" else "good"} character token of your choice",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    private fun seamstress(ctx: Ctx, targets: List<Long>): InfoResult {
        if (targets.size < 2) return InfoResult("Pick the 2 players the Seamstress chose")
        val chosen = targets.mapNotNull { ctx.state.player(it) }
        val same = ctx.isEvil(chosen[0]) == ctx.isEvil(chosen[1])
        return InfoResult(
            headline = if (same) "YES — same alignment" else "NO — different alignments",
            detail = chosen.joinToString { "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})" },
            caveats = misregistrations(ctx, chosen),
        )
    }

    private fun villageIdiot(ctx: Ctx, targets: List<Long>): InfoResult {
        val target = targets.firstOrNull()?.let { ctx.state.player(it) }
            ?: return InfoResult("Pick the player the Village Idiot chose")
        return InfoResult(
            headline = "${ctx.name(target)} is ${if (ctx.isEvil(target)) "EVIL" else "GOOD"}",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    private fun revealCharacter(ctx: Ctx, targets: List<Long>, who: String): InfoResult {
        val target = targets.firstOrNull()?.let { ctx.state.player(it) }
            ?: return InfoResult("Pick the player the $who chose/knows")
        val character = ctx.character(target)
        return InfoResult(
            headline = "${ctx.name(target)} is the ${character?.name ?: "?"}",
            caveats = misregistrations(ctx, listOf(target)),
        )
    }

    private fun cultLeader(ctx: Ctx): InfoResult {
        val holder = ctx.holder ?: return InfoResult("Select the Cult Leader's seat first")
        val neighbours = aliveNeighbours(ctx, holder)
        return InfoResult(
            headline = "Alive neighbours: " + neighbours.joinToString {
                "${ctx.name(it)} (${if (ctx.isEvil(it)) "evil" else "good"})"
            },
            detail = "The Cult Leader becomes the alignment of one of them (your choice).",
        )
    }

    private fun king(ctx: Ctx): InfoResult {
        val alive = ctx.players.count { it.alive }
        val dead = ctx.players.size - alive
        if (dead < alive) return InfoResult("Dead ($dead) don't outnumber living ($alive) — the King doesn't wake")
        val options = ctx.players.filter { it.alive }.mapNotNull { p -> ctx.character(p)?.name }
        return InfoResult(
            headline = "Show 1 alive character",
            detail = "In play & alive: ${options.joinToString()}",
        )
    }

    private fun startKnowing(ctx: Ctx, team: Team, label: String): InfoResult {
        val inPlay = ctx.players.filter { ctx.character(it)?.team == team }
        if (inPlay.isEmpty()) {
            return InfoResult(
                headline = "No $label in play" + if (team == Team.OUTSIDER) " — show the 0 signal" else "",
                caveats = misregistrations(ctx, ctx.players),
            )
        }
        return InfoResult(
            headline = "$label in play: " + inPlay.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" },
            detail = "Show one of those character tokens, point to that player plus 1 wrong player.",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun knight(ctx: Ctx): InfoResult {
        val demons = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }
        return InfoResult(
            headline = "Point to 2 players that are NOT the Demon",
            detail = "Demon: ${demons.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun steward(ctx: Ctx): InfoResult {
        val good = ctx.players.filter { !ctx.isEvil(it) }
        return InfoResult(
            headline = "Point to 1 good player",
            detail = "Good players: ${good.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun noble(ctx: Ctx): InfoResult {
        val evil = ctx.players.filter { ctx.isEvil(it) }
        return InfoResult(
            headline = "Point to 3 players: exactly 1 evil, 2 good",
            detail = "Evil players: ${evil.joinToString { ctx.name(it) }}",
            caveats = misregistrations(ctx, ctx.players),
        )
    }

    private fun bountyHunter(ctx: Ctx): InfoResult {
        val evil = ctx.players.filter { ctx.isEvil(it) }
        return InfoResult(
            headline = "Point to 1 evil player (mark them 'Known')",
            detail = "Evil players: ${evil.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" }}",
            caveats = listOf("Remember: 1 Townsfolk is evil in a Bounty Hunter game."),
        )
    }

    private fun chambermaid(ctx: Ctx, targets: List<Long>): InfoResult {
        if (targets.size < 2) return InfoResult("Pick the 2 players the Chambermaid chose")
        val order = if (ctx.state.cycle == 1) ctx.data.firstNightOrder else ctx.data.otherNightOrder
        val chosen = targets.mapNotNull { ctx.state.player(it) }
        val wakers = chosen.filter { p ->
            p.alive && p.characterId != null && p.characterId in order
        }
        return InfoResult(
            headline = "${wakers.size} of the 2 wake tonight (approximate)",
            detail = chosen.joinToString { "${ctx.name(it)}: ${if (it in wakers) "wakes" else "doesn't wake"}" },
            caveats = listOf(
                "Approximation from the night order — characters that only sometimes wake " +
                    "(Ravenkeeper, once-per-game abilities already spent...) need your judgement.",
            ),
        )
    }

    private fun balloonist(ctx: Ctx): InfoResult {
        val byType = ctx.players
            .mapNotNull { p -> ctx.character(p)?.let { c -> c.team to p } }
            .groupBy({ it.first }, { it.second })
        return InfoResult(
            headline = "Show a player of a DIFFERENT character type than last night",
            detail = byType.entries.joinToString("\n") { (team, ps) ->
                "${team.displayName}: ${ps.joinToString { ctx.name(it) }}"
            },
        )
    }
}
