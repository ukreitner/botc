package com.clocktower.engine

/**
 * Advisory win-condition detection. Blood on the Clocktower endings are
 * storyteller calls (Scarlet Woman, Mastermind, Evil Twin, Atheist...),
 * so these are prompts with reasons — never automatic.
 */
object WinCheck {

    data class Advisory(
        /** Suggested winner, or null when it's purely "check this". */
        val goodWins: Boolean?,
        val reason: String,
        /** Rules that could overturn the suggestion. */
        val cautions: List<String> = emptyList(),
    )

    fun check(state: GameState, lookup: (String) -> Character?): Advisory? {
        val players = state.players.filter { !it.isTraveller }
        if (players.none { it.characterId != null }) return null
        val demons = players.filter { lookup(it.characterId ?: "")?.team == Team.DEMON }
        val aliveDemons = demons.filter { it.alive }
        val alive = players.filter { it.alive }
        val inPlayIds = players.mapNotNull { it.characterId }.toSet()

        // The Mastermind's extra day has its own resolution: the first
        // execution ends the game against the executed player's team.
        if (state.mastermindDayActive) {
            // Only executions AFTER the Demon's own fall resolve the extra day.
            val demonExecIndex = state.deaths.indexOfLast { d ->
                d.cause == DeathCause.EXECUTION &&
                    (d.characterIdAtDeath ?: state.player(d.playerId)?.characterId)
                        ?.let(lookup)?.team == Team.DEMON
            }
            val executed = state.deaths.withIndex().lastOrNull { (i, d) ->
                i > demonExecIndex && d.cause == DeathCause.EXECUTION && !d.resurrected
            }?.value
            if (executed != null) {
                val executedPlayer = state.player(executed.playerId)
                val executedEvil = executedPlayer?.isEvil(lookup) ?: false
                return Advisory(
                    goodWins = executedEvil,
                    reason = "Mastermind day: ${executedPlayer?.name ?: "a player"} was executed — " +
                        "their team (${if (executedEvil) "evil" else "good"}) loses.",
                )
            }
            // Suppress the demons-dead advisory while the extra day plays out.
            return null
        }

        val executedSaint = state.deaths.lastOrNull {
            if (it.cause != DeathCause.EXECUTION) return@lastOrNull false
            val currentPlayer = players.find { player -> player.id == it.playerId }
            val wasSaint = it.characterIdAtDeath?.let { id -> id == "saint" }
                ?: (currentPlayer?.characterId == "saint")
            val wasImpaired = it.abilityImpairedAtDeath
                ?: currentPlayer?.let { player ->
                    StatusEffects.isImpaired(state, lookup, player)
                }
                ?: false
            wasSaint && !wasImpaired
        }
        if (executedSaint != null) {
            return Advisory(
                goodWins = false,
                reason = "The Saint died by execution - the good team loses.",
            )
        }

        if (demons.isNotEmpty() && aliveDemons.isEmpty()) {
            val cautions = mutableListOf<String>()
            if ("scarletwoman" in inPlayIds) {
                cautions += "Scarlet Woman: with 5+ players alive she becomes the Demon instead."
            }
            if ("mastermind" in inPlayIds) {
                cautions += "Mastermind: if the Demon died by execution, play one more day first."
            }
            if ("imp" in inPlayIds) {
                cautions += "Imp star-pass: if the Imp killed itself, a Minion becomes the Imp."
            }
            return Advisory(
                goodWins = true,
                reason = "Every Demon is dead — good wins, unless an ability says otherwise.",
                cautions = cautions,
            )
        }

        if (alive.size <= 2 && aliveDemons.isNotEmpty()) {
            val cautions = mutableListOf<String>()
            if ("mayor" in inPlayIds) {
                cautions += "Mayor: at 3 alive with no execution, good wins instead — check before it drops to 2."
            }
            return Advisory(
                goodWins = false,
                reason = "Only ${alive.size} players live and the Demon is among them — evil wins.",
                cautions = cautions,
            )
        }

        return null
    }
}
