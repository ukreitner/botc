package com.clocktower.engine

/**
 * Rule consequences the grimoire can derive on its own: positional poison,
 * protection that blocks a death, and abilities that trigger on death.
 */
object StatusEffects {

    /**
     * Players poisoned by a standing positional ability, with the reason.
     * Currently: the alive No Dashii poisons its nearest Townsfolk
     * neighbour in each direction (skipping non-Townsfolk seats).
     */
    fun derivedPoison(state: GameState, lookup: (String) -> Character?): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        val seats = state.players
        val noDashii = seats.filter { it.characterId == "nodashii" && it.alive }
        for (demon in noDashii) {
            val index = seats.indexOfFirst { it.id == demon.id }
            for (dir in listOf(-1, +1)) {
                var i = (index + dir + seats.size) % seats.size
                while (i != index) {
                    val p = seats[i]
                    if (p.characterId?.let(lookup)?.team == Team.TOWNSFOLK) {
                        result[p.id] = "Poisoned by the No Dashii (${demon.name}'s nearest Townsfolk neighbour)"
                        break
                    }
                    i = (i + dir + seats.size) % seats.size
                }
            }
        }
        return result
    }

    /** True when the player is drunk/poisoned by reminder, character, or derived effect. */
    fun isImpaired(state: GameState, lookup: (String) -> Character?, player: Player): Boolean {
        if (player.characterId == "drunk") return true
        if (player.reminders.any { r ->
                val l = r.label.lowercase()
                "poison" in l || "drunk" in l
            }
        ) {
            return true
        }
        return player.id in derivedPoison(state, lookup)
    }

    /**
     * Everything to weigh before recording this player's death: protection
     * that might prevent it, and abilities that fire when it happens.
     */
    fun deathNotes(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): List<String> {
        val player = state.player(playerId) ?: return emptyList()
        val notes = mutableListOf<String>()
        val id = player.characterId
        val character = id?.let(lookup)
        val seats = state.players

        // Protection already marked in the grimoire.
        for (r in player.reminders) {
            when (r.label.lowercase()) {
                "safe" -> notes += "Marked 'Safe' (${lookup(r.sourceId)?.name ?: "?"}) — protected from the Demon."
                "protected" -> notes += "Marked 'Protected' (${lookup(r.sourceId)?.name ?: "?"}) — can't die tonight."
                "survives execution" -> notes += "Devil's Advocate: survives execution today."
                "can not die" -> notes += "Tea Lady: can't die."
            }
        }
        // Standing protections.
        if (id == "sailor" && player.alive) notes += "The Sailor can't die."
        if (id == "soldier") notes += "The Soldier is safe from the Demon."
        if (id == "fool" && player.reminders.none { it.label.equals("No ability", true) }) {
            notes += "Fool: the first time they die, they don't."
        }
        if (id == "lleech") notes += "The Lleech only dies if its poisoned host is dead."
        val index = seats.indexOfFirst { it.id == playerId }
        if (index >= 0) {
            val teaLadies = seats.filter { it.characterId == "tealady" && it.alive }
            for (tea in teaLadies) {
                val ti = seats.indexOfFirst { it.id == tea.id }
                val neighbours = listOf((ti - 1 + seats.size) % seats.size, (ti + 1) % seats.size)
                if (index in neighbours && seats[neighbours[0]].let { !it.isEvil(lookup) } &&
                    seats[neighbours[1]].let { !it.isEvil(lookup) }
                ) {
                    notes += "Tea Lady neighbour with both neighbours good — can't die."
                }
            }
        }

        // Abilities that trigger on this death.
        when (id) {
            "ravenkeeper" -> notes += "Ravenkeeper: if dying at night, they wake to learn a character."
            "sage" -> notes += "Sage: if the Demon killed them, show 2 players, one the Demon."
            "farmer" -> notes += "Farmer: a living good player becomes a Farmer tonight."
            "moonchild" -> notes += "Moonchild: they publicly choose a player who may die tonight."
            "sweetheart" -> notes += "Sweetheart: choose 1 player to be drunk from now on."
            "barber" -> notes += "Barber: the Demon may swap two players' characters tonight."
            "poppygrower" -> notes += "Poppy Grower: minions & demon learn each other tonight."
            "king" -> notes += "Choirboy (if in play) learns the Demon when the King dies to it."
        }
        if (character?.team == Team.DEMON) {
            if (seats.any { it.characterId == "scarletwoman" && it.alive } && seats.count { it.alive } >= 5) {
                notes += "Scarlet Woman becomes the Demon (5+ alive)."
            }
            if (id == "imp") notes += "Imp self-kill: a Minion becomes the Imp."
        }
        if (character?.team == Team.MINION && seats.any { it.characterId == "minstrel" && it.alive }) {
            notes += "Minstrel: if executed, everyone (but Travellers) is drunk until dusk tomorrow."
        }
        if (character?.team == Team.MINION && seats.any { it.characterId == "vigormortis" && it.alive }) {
            notes += "Vigormortis kill: the Minion keeps their ability and one Townsfolk neighbour is poisoned."
        }
        if (character?.team == Team.OUTSIDER && seats.any { it.characterId == "godfather" && it.alive }) {
            notes += "Godfather kills tonight because an Outsider died today."
        }
        if (id == "zombuul" && state.deaths.none { it.playerId == playerId }) {
            notes += "Zombuul: the first time it dies, it lives but registers as dead."
        }
        val grandmothers = seats.filter { it.characterId == "grandmother" && it.alive }
        if (grandmothers.isNotEmpty() &&
            player.reminders.any { it.label.equals("Grandchild", true) }
        ) {
            notes += "Grandmother dies too if the Demon killed her grandchild."
        }
        return notes
    }

    /** Rule triggers to surface the moment a nomination is declared. */
    fun nominationWarnings(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): List<String> {
        val notes = mutableListOf<String>()
        val nominator = nominatorId?.let { state.player(it) }
        val nominee = nomineeId?.let { state.player(it) }

        if (nominator != null) {
            if (state.alivePlayers.size >= 4 &&
                nominator.reminders.any { it.label.equals("Cursed", true) }
            ) {
                notes += "${nominator.name} is Witch-cursed — they die immediately for nominating (if 4+ alive)."
            }
            if (nominator.characterId == "golem") {
                notes += "Golem nominates: if the nominee is not the Demon, the nominee dies; the Golem may only nominate once per game."
            }
        }
        if (nominee != null) {
            if (nominee.characterId == "virgin" &&
                nominee.reminders.none { it.label.equals("No ability", true) }
            ) {
                notes += "Virgin's first nomination: if ${nominator?.name ?: "the nominator"} is a Townsfolk, they are executed immediately."
            }
            if (nominee.reminders.any { it.label.equals("Fear", true) }) {
                notes += "Fearmonger chose ${nominee.name}: if executed from this nomination, their team loses."
            }
        }
        if (nominator?.reminders?.any { it.label.equals("Mad", true) } == true) {
            notes += "${nominator.name} is Cerenovus-mad — check their claim before this goes further."
        }
        return notes
    }
}
