package com.clocktower.engine

import kotlinx.serialization.Serializable

/**
 * Advisory win-condition detection (WP3, ARCHITECTURE §2.8).
 *
 * Blood on the Clocktower endings are storyteller calls (Scarlet Woman,
 * Mastermind, Evil Twin, Atheist…), so these are prompts with reasons — never
 * automatic. Every advisory carries a stable [Advisory.ruleId] so the UI can
 * dedupe and dismiss on identity rather than on prose.
 */
object WinCheck {

    @Serializable
    data class Advisory(
        /** Suggested winner, or null when it's purely "check this". */
        val goodWins: Boolean?,
        val reason: String,
        /** Rules that could overturn the suggestion. */
        val cautions: List<String> = emptyList(),
        /** Stable id for dedupe and dismissal: "demon-dead", "mayor-dusk", "vortox-dusk". */
        val ruleId: String = "",
        /** True when the ST must answer before the phase can advance. */
        val blocking: Boolean = false,
    )

    /** A question the end-game dialog MUST answer before "Declare victory" (lead D40). */
    @Serializable
    data class EndGameQuestion(
        val id: String,
        val sourceId: String,
        val question: String,
        val options: List<TriggerOption> = emptyList(),
    )

    // ---- stable rule ids ----
    const val RULE_DEMON_DEAD: String = "demon-dead"
    const val RULE_TWO_ALIVE: String = "two-alive"
    const val RULE_SAINT: String = "saint"
    const val RULE_MASTERMIND: String = "mastermind"
    const val RULE_VORTOX_DUSK: String = "vortox-dusk"
    const val RULE_MAYOR_DUSK: String = "mayor-dusk"
    const val RULE_LEVIATHAN_DAY5: String = "leviathan-day5"
    const val RULE_RIOT_DAY3: String = "riot-day3"
    const val RULE_ZOMBUUL_NIGHT: String = "zombuul-night"
    const val RULE_GOBLIN_CLAIM: String = "goblin-claim"
    const val RULE_FEARMONGER: String = "fearmonger"
    const val RULE_EVIL_TWIN: String = "eviltwin-good-executed"
    const val RULE_ATHEIST: String = "atheist-storyteller-executed"
    const val RULE_LEVIATHAN_TWO_GOOD: String = "leviathan-two-good"

    /**
     * Advisory dedupe and dismissal key on [Advisory.ruleId], never on the prose —
     * the same rule may word itself differently on two evaluations, and two
     * different rules may read alike.
     */
    fun dedupe(advisories: List<Advisory>): List<Advisory> = advisories.distinctBy { it.ruleId }

    /** Continuous and cheap; called on any state change. */
    fun check(state: GameState, lookup: (String) -> Character?): Advisory? =
        finish(state, lookup, listOfNotNull(rawCheck(state, lookup))).firstOrNull()

    /**
     * DAY -> NIGHT, called BEFORE `advancePhase`. Ordered, and **all** matches are
     * returned so a Vortox/Mayor collision is visible rather than silently resolved.
     */
    fun duskCheck(state: GameState, lookup: (String) -> Character?): List<Advisory> {
        val out = mutableListOf<Advisory>()
        val executedToday = state.executions.any {
            it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION
        }

        // 1. Vortox: "If no execution occurs, evil wins." Sober and alive only —
        //    an impaired Vortox loses the whole ability, the clause included (D11).
        val vortox = DayRules.holderOfWithAbility(state, lookup, "vortox")
        if (vortox != null && !executedToday) {
            out += Advisory(
                goodWins = false,
                reason = "No execution today and the Vortox is alive and sober — evil wins.",
                cautions = listOf("Record an execution you forgot before declaring this."),
                ruleId = RULE_VORTOX_DUSK,
                blocking = true,
            )
        }

        // 2. Mayor: "If only 3 players live and no execution occurs, good wins."
        val mayor = DayRules.holderOfWithAbility(state, lookup, "mayor")
        if (mayor != null && !executedToday && state.aliveCountWithTravellers == 3) {
            val cautions = mutableListOf(
                "Travellers count towards the 3 — exile them first if that is the intent.",
                "A tied vote is a no-execution: good still wins.",
            )
            if (vortox != null) {
                cautions += "COLLISION: the Vortox also wins on a no-execution day. " +
                    "There is no jinx — you decide."
            }
            out += Advisory(
                goodWins = true,
                reason = "Three players live and nobody was executed — the Mayor wins for good.",
                cautions = cautions,
                ruleId = RULE_MAYOR_DUSK,
                blocking = true,
            )
        }

        // 3. Leviathan: alive at the end of day 5.
        val leviathan = DayRules.holderOf(state, "leviathan")
        if (leviathan != null && DayRules.leviathanDay(state) >= 5) {
            out += Advisory(
                goodWins = false,
                reason = "Day 5 ends with the Leviathan in play — evil wins.",
                ruleId = RULE_LEVIATHAN_DAY5,
                blocking = true,
            )
        }

        // 4. Riot: day 3 ends the game.
        if (DayRules.holderOf(state, "riot") != null && DayRules.riotDay(state) >= 3) {
            out += Advisory(
                goodWins = state.alivePlayers.none {
                    Team.DEMON in Registration.registersAs(state, lookup, it)
                },
                reason = "Riot: day 3 ends the game — good wins only if every Riot is dead.",
                ruleId = RULE_RIOT_DAY3,
                blocking = true,
            )
        }

        // 5. Zombuul: a briefing, not a win.
        val zombuul = DayRules.holderOf(state, "zombuul")
        if (zombuul != null && state.deaths.none { it.day == state.cycle && !it.atNight }) {
            out += Advisory(
                goodWins = null,
                reason = "Nobody died today — the Zombuul kills tonight.",
                ruleId = RULE_ZOMBUUL_NIGHT,
            )
        }

        rawCheck(state, lookup)?.let { out += it }
        return finish(state, lookup, out)
    }

    /** NIGHT -> DAY, for endings that resolve at dawn. */
    fun dawnCheck(state: GameState, lookup: (String) -> Character?): List<Advisory> =
        finish(state, lookup, listOfNotNull(rawCheck(state, lookup)))

    /**
     * Questions the end-game dialog must answer before a victory is declared
     * (lead D40). Each is a rule the grimoire cannot decide on its own.
     */
    fun endGameQuestions(state: GameState, lookup: (String) -> Character?): List<EndGameQuestion> =
        buildList {
            for (seat in state.seats) {
                when (seat.characterId?.let(Character::normalizeId)) {
                    "politician" -> add(
                        EndGameQuestion(
                            id = "politician:${seat.id}",
                            sourceId = "politician",
                            question = "Was ${seat.name} (Politician) the player most responsible " +
                                "for their team losing?",
                            options = listOf(
                                TriggerOption("yes", "Yes — they change alignment and win alone"),
                                TriggerOption("no", "No", isDefault = true),
                            ),
                        ),
                    )

                    "fiddler" -> add(
                        EndGameQuestion(
                            id = "fiddler:${seat.id}",
                            sourceId = "fiddler",
                            question = "Did the Fiddler's duel happen? Whose side won it?",
                            options = listOf(
                                TriggerOption("good", "Good"),
                                TriggerOption("evil", "Evil"),
                                TriggerOption("none", "No duel", isDefault = true),
                            ),
                        ),
                    )

                    "cultleader" -> add(
                        EndGameQuestion(
                            id = "cultleader:${seat.id}",
                            sourceId = "cultleader",
                            question = "Did the town vote to join ${seat.name}'s cult?",
                            options = listOf(
                                TriggerOption("yes", "Yes — the cult wins"),
                                TriggerOption("no", "No", isDefault = true),
                            ),
                        ),
                    )

                    else -> Unit
                }
            }
        }

    /**
     * Per-player win/lose for the reveal sheet, after the questions are answered.
     * Travellers win with the alignment they were given, never with their team.
     */
    fun results(
        state: GameState,
        lookup: (String) -> Character?,
        goodWins: Boolean,
    ): Map<Long, Boolean> = state.seats.associate { seat ->
        val evil = Registration.alignment(state, lookup, seat) == Alignment.EVIL
        seat.id to (evil != goodWins)
    }

    // ---- the continuous rules ----

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
    private fun rawCheck(state: GameState, lookup: (String) -> Character?): Advisory? {
        val players = state.seats.filterNot { it.isTraveller }
        if (players.none { it.characterId != null }) return null
        val demons = players.filter { Team.DEMON in Registration.registersAs(state, lookup, it) }
        // A Zombuul that only registers dead is still an alive Demon (lead D6).
        val aliveDemons = demons.filter { it.alive || state.isTrulyAlive(it.id) }
        val inPlayIds = players.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()

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
                    ruleId = RULE_MASTERMIND,
                )
            }
            // Suppress the demons-dead advisory while the extra day plays out.
            return null
        }

        // An executed storyteller ends an Atheist game outright, before anything
        // the board would otherwise say.
        atheistExecution(state)?.let { return it }
        saint(state, lookup, players)?.let { return it }

        if (demons.isNotEmpty() && aliveDemons.isEmpty()) {
            // A Summoner who has not created the Demon yet is not a dead Demon.
            val summonerPending = "summoner" in inPlayIds &&
                players.none { it.characterId?.let(lookup)?.team == Team.DEMON }
            if (!summonerPending) {
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
                    ruleId = RULE_DEMON_DEAD,
                )
            }
        }

        // "When only 2 players live, evil wins" — unconditional in the Glossary,
        // so a seatless Demon (Lil' Monsta) does not suppress it.
        if (state.aliveCountResidents <= 2 && players.any { it.alive }) {
            val cautions = mutableListOf<String>()
            if ("mayor" in inPlayIds) {
                cautions += "Mayor: at 3 alive with no execution, good wins instead — " +
                    "check before it drops to 2."
            }
            return Advisory(
                goodWins = false,
                reason = "Only ${state.aliveCountResidents} players live — evil wins.",
                cautions = cautions,
                ruleId = RULE_TWO_ALIVE,
            )
        }

        goblinClaim(state, lookup)?.let { return it }
        fearmonger(state, lookup)?.let { return it }
        evilTwin(state, lookup)?.let { return it }
        leviathanCounter(state)?.let { return it }

        return null
    }

    /** "If 2 good players are executed, evil wins." Counted from the tokens. */
    private fun leviathanCounter(state: GameState): Advisory? {
        if (DayRules.holderOf(state, "leviathan") == null) return null
        val key = Tokens.key("leviathan", "Good Player Executed")
        val marks = (state.storytellerReminders + state.players.flatMap { it.reminders })
            .count { Tokens.key(it) == key }
        if (marks < 2) return null
        return Advisory(
            goodWins = false,
            reason = "Two good players have been executed — the Leviathan wins.",
            ruleId = RULE_LEVIATHAN_TWO_GOOD,
        )
    }

    /**
     * "If you die by execution, your team loses." Reads the execution record when
     * one exists (an execution that killed nobody must not lose the game) and
     * falls back to the death record for games driven straight through `Deaths`.
     */
    private fun saint(
        state: GameState,
        lookup: (String) -> Character?,
        players: List<Player>,
    ): Advisory? {
        val executedSaint = state.deaths.lastOrNull { death ->
            if (death.cause != DeathCause.EXECUTION) return@lastOrNull false
            if (death.resurrected) return@lastOrNull false
            val currentPlayer = players.find { it.id == death.playerId }
            val wasSaint = death.characterIdAtDeath?.let { Character.normalizeId(it) == "saint" }
                ?: (currentPlayer?.characterId?.let(Character::normalizeId) == "saint")
            if (!wasSaint) return@lastOrNull false
            // The execution row, where there is one, is the authority on impairment.
            val row = state.executions.lastOrNull {
                it.day == death.day && (it.diedInsteadId ?: it.playerId) == death.playerId
            }
            val wasImpaired = row?.abilityImpairedAtExecution
                ?: death.abilityImpairedAtDeath
                ?: currentPlayer?.let { Status.isImpaired(state, lookup, it.id) }
                ?: false
            !wasImpaired
        } ?: return null
        val who = state.player(executedSaint.playerId)?.name ?: "The Saint"
        return Advisory(
            goodWins = false,
            reason = "The Saint died by execution - the good team loses.",
            cautions = listOf("$who was executed on day ${executedSaint.day}."),
            ruleId = RULE_SAINT,
        )
    }

    private fun goblinClaim(state: GameState, lookup: (String) -> Character?): Advisory? {
        val goblin = DayRules.holderOf(state, "goblin") ?: return null
        val claimed = state.nominations.lastOrNull {
            it.goblinClaim && !it.isExile && it.nomineeId == goblin.id
        } ?: return null
        val executed = state.executions.lastOrNull {
            it.day == claimed.day && it.playerId == goblin.id
        } ?: return null
        val impaired = executed.abilityImpairedAtExecution
            ?: Status.isImpaired(state, lookup, goblin.id)
        val cautions = mutableListOf<String>()
        if (executed.outcome == ExecutionOutcome.SURVIVED) {
            cautions += "The execution killed nobody — the Goblin's ability still counts."
        }
        if (impaired) {
            cautions += "The Goblin was drunk or poisoned when they claimed — this is not a win."
        }
        return Advisory(
            goodWins = if (impaired) null else false,
            reason = "${goblin.name} claimed the Goblin and was executed — evil wins.",
            cautions = cautions,
            ruleId = RULE_GOBLIN_CLAIM,
        )
    }

    private fun fearmonger(state: GameState, lookup: (String) -> Character?): Advisory? {
        val fearmonger = DayRules.holderOf(state, "fearmonger") ?: return null
        val executed = state.executions.lastOrNull {
            it.outcome == ExecutionOutcome.DIED && it.nominatorId == fearmonger.id
        } ?: return null
        val victim = executed.playerId ?: return null
        if (!DayRules.hasToken(state, victim, "fearmonger", "Fear")) return null
        return Advisory(
            goodWins = false,
            reason = "The Fearmonger nominated ${state.player(victim)?.name ?: "them"} and they " +
                "were executed — evil wins.",
            cautions = listOfNotNull(
                "The Fearmonger was drunk or poisoned — this is not a win."
                    .takeIf { Status.isImpaired(state, lookup, fearmonger.id) },
            ),
            ruleId = RULE_FEARMONGER,
        )
    }

    private fun evilTwin(state: GameState, lookup: (String) -> Character?): Advisory? {
        DayRules.holderOf(state, "eviltwin") ?: return null
        val executed = state.executions.lastOrNull { it.outcome == ExecutionOutcome.DIED } ?: return null
        val victim = executed.playerId?.let { state.player(it) } ?: return null
        if (!DayRules.hasToken(state, victim.id, "eviltwin", "Twin")) return null
        if (Registration.registersEvil(state, lookup, victim)) return null
        return Advisory(
            goodWins = false,
            reason = "${victim.name} is the Evil Twin's good twin and was executed — evil wins.",
            ruleId = RULE_EVIL_TWIN,
        )
    }

    private fun atheistExecution(state: GameState): Advisory? {
        if (state.seats.none { it.characterId?.let(Character::normalizeId) == "atheist" }) return null
        val executed = state.executions.lastOrNull {
            it.playerId == GameState.STORYTELLER_SEAT_ID
        } ?: return null
        return Advisory(
            goodWins = true,
            reason = "The storyteller was executed on day ${executed.day} — the Atheist's " +
                "good team wins.",
            ruleId = RULE_ATHEIST,
        )
    }

    // ---- the two passes every result goes through ----

    /**
     * Atheist suppression, the Heretic inversion and dedupe by [Advisory.ruleId],
     * in that order. Applied to every entry point so no individual rule has to
     * know about them.
     */
    private fun finish(
        state: GameState,
        lookup: (String) -> Character?,
        advisories: List<Advisory>,
    ): List<Advisory> = dedupe(hereticPass(state, lookup, atheistPass(state, advisories)))

    /**
     * "If the storyteller is executed, good wins" — and while the Atheist has
     * their ability nothing else can hand evil the game.
     */
    private fun atheistPass(state: GameState, advisories: List<Advisory>): List<Advisory> {
        val atheist = state.seats.firstOrNull {
            it.characterId?.let(Character::normalizeId) == "atheist"
        } ?: return advisories
        return advisories.map { advisory ->
            if (advisory.goodWins == false && advisory.ruleId != RULE_ATHEIST) {
                advisory.copy(
                    goodWins = null,
                    cautions = advisory.cautions +
                        "${atheist.name} is the Atheist — evil cannot win while their ability works.",
                )
            } else {
                advisory
            }
        }
    }

    /**
     * "If good wins, evil wins instead" — works while dead, suppressed while the
     * Heretic is drunk or poisoned (lead D40). A final map, so no rule knows.
     */
    private fun hereticPass(
        state: GameState,
        lookup: (String) -> Character?,
        advisories: List<Advisory>,
    ): List<Advisory> {
        val heretic = state.seats.firstOrNull {
            it.characterId?.let(Character::normalizeId) == "heretic"
        } ?: return advisories
        if (Status.isImpaired(state, lookup, heretic.id)) return advisories
        return advisories.map { advisory ->
            if (advisory.goodWins == null) {
                advisory
            } else {
                advisory.copy(
                    goodWins = !advisory.goodWins,
                    reason = "The Heretic (${heretic.name}) inverts this: " +
                        "${if (advisory.goodWins) "evil" else "good"} wins. " + advisory.reason,
                    cautions = advisory.cautions +
                        "Whoever wins with the Heretic, wins — unless the Heretic is drunk or poisoned.",
                )
            }
        }
    }
}
