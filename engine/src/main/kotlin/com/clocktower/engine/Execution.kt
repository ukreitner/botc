package com.clocktower.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }

/** How the execution was decided — for the log and for rules that bypass the tally. */
@Serializable
enum class ExecutionVia { VOTE, VIRGIN, VIZIER, JUDGE, PSYCHOPATH, RIOT, STORYTELLER }

/**
 * Every execution, INCLUDING days on which nobody was executed. This list is the
 * single "day is closed" signal (lead D30) — there is no boolean anywhere.
 *
 * An execution that kills nobody is still an execution: Vortox, Mayor, Leviathan,
 * Goblin, Boomdandy and the Undertaker all hinge on the distinction.
 */
@Serializable
data class ExecutionRecord(
    val day: Int,
    val outcome: ExecutionOutcome,
    /** Null only when outcome == NO_EXECUTION. May be [GameState.STORYTELLER_SEAT_ID]. */
    val playerId: Long? = null,
    /** Who nominated them — Fearmonger, Psychopath roshambo, Town Crier, the log. */
    val nominatorId: Long? = null,
    /** Index into `state.nominations` for the nomination this resolved. */
    val nominationIndex: Int? = null,
    /** The DeathEvent this execution produced, when it killed someone. */
    val deathEventId: Long? = null,
    /**
     * Character credited with the save, for SURVIVED: "devilsadvocate", "pacifist",
     * "fool", "sailor", "tealady", "vizier", "zombuul", "psychopath", "mayor",
     * "scapegoat", "alreadyDead". "" for a bare storyteller decision.
     */
    val preventedBy: String = "",
    /** Seat that died instead (Scapegoat). The execution still belongs to [playerId]. */
    val diedInsteadId: Long? = null,
    val via: ExecutionVia = ExecutionVia.VOTE,
    /** Snapshots so later character/alignment changes cannot rewrite history. */
    val characterIdAtExecution: String? = null,
    val wasEvilAtExecution: Boolean? = null,
    val abilityImpairedAtExecution: Boolean? = null,
    /** Weighted tally and threshold at the moment of the decision. */
    val tally: Int = 0,
    val threshold: Int = 0,
)

/** A consequence the storyteller must confirm after an execution resolves. */
@Serializable
data class ExecutionConsequence(
    val sourceId: String,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work (drunk/poisoned/dead/spent) — the ST decides anyway. */
    val impaired: Boolean = false,
)

/**
 * The one execution funnel (WP3, ARCHITECTURE §2.7).
 *
 * Every "Execute" button in the app lands here, and every death it causes goes
 * on through `Deaths.attempt` — so a Devil's Advocate, a Pacifist, a Scapegoat
 * and a Zombuul are all resolved by the same fifteen-step table, once.
 */
object Execution {

    /** "Executed — but they don't die" credited to nothing in particular. */
    const val PREVENTED_BY_STORYTELLER: String = ""

    /** The execution hit a seat that was already dead. */
    const val PREVENTED_BY_ALREADY_DEAD: String = "alreadyDead"

    /**
     * THE execution funnel. Every "Execute" button in the app calls this:
     * DayScreen block banner, DayScreen nomination row, GameShell dusk guard,
     * SeatSheet, and any registry-driven auto-execution (Virgin, Vizier, Judge).
     *
     * Order of operations:
     *  1. Refuse for a Traveller (travellers are exiled, never executed).
     *  2. Refuse when `executionSpent` and not `secondExecutionAllowed`, unless [force].
     *  3. Snapshot character/alignment/impairment/tally/threshold.
     *  4. Append the ExecutionRecord ALWAYS — before any kill, so an aborted kill
     *     still leaves the execution recorded.
     *  5. Route the death through `Deaths.attempt(cause = EXECUTION)`; the funnel's
     *     outcome decides DIED vs SURVIVED and fills `preventedBy`/`diedInsteadId`.
     *  6. Place ("undertaker", "Died Today") on the seat that actually died, if any.
     *
     * [outcome] and [preventedBy] are the storyteller's override — the
     * "Executed, but they don't die" button of day-engine §F. [optionId] answers
     * a `KillOutcome.Choice` (Pacifist, Scapegoat); with an unanswered choice the
     * state is returned untouched so the UI can ask first.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount")
    fun execute(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        nominatorId: Long? = null,
        via: ExecutionVia = ExecutionVia.VOTE,
        nominationIndex: Int? = null,
        force: Boolean = false,
        outcome: ExecutionOutcome? = null,
        preventedBy: String = PREVENTED_BY_STORYTELLER,
        optionId: String = "",
    ): GameState {
        val isStoryteller = playerId == GameState.STORYTELLER_SEAT_ID
        val target = state.player(playerId)
        if (!isStoryteller) {
            // 1. Travellers are exiled, never executed.
            if (target == null || !target.seated || target.isTraveller) return state
        }
        // 2. One execution per day, unless the Butcher says otherwise.
        if (DayRules.executionSpent(state) &&
            !DayRules.secondExecutionAllowed(state, lookup) &&
            !force
        ) {
            return state
        }

        // 3. Snapshots, taken before anything changes.
        val nomination = nominationIndex?.let { state.nominations.getOrNull(it) }
        val record = ExecutionRecord(
            day = state.cycle,
            outcome = ExecutionOutcome.SURVIVED,
            playerId = playerId,
            nominatorId = nominatorId ?: nomination?.nominatorId,
            nominationIndex = nominationIndex,
            via = via,
            characterIdAtExecution = target?.characterId,
            wasEvilAtExecution = target?.let { Registration.registersEvil(state, lookup, it) },
            abilityImpairedAtExecution = target?.let { Status.isImpaired(state, lookup, it.id) },
            tally = nomination?.votes ?: 0,
            threshold = nomination?.voteRules?.threshold ?: state.executionThreshold,
        )

        // The storyteller forced the result: no kill funnel, no death.
        if (outcome == ExecutionOutcome.SURVIVED) {
            return append(state, record.copy(preventedBy = preventedBy))
        }
        if (outcome == ExecutionOutcome.NO_EXECUTION) return noExecution(state)
        if (isStoryteller) {
            // An Atheist game: the storyteller is "executed" and nobody dies.
            return append(state, record.copy(outcome = ExecutionOutcome.DIED, preventedBy = preventedBy))
        }

        // 4. The record lands first, so an aborted kill still leaves it behind.
        var next = append(state, record)

        // 5. One kill funnel for every execution.
        val cause = KillCause(
            cause = DeathCause.EXECUTION,
            sourceCharacterId = viaSource(via),
            sourcePlayerId = record.nominatorId,
        )
        val decided = Deaths.killOutcome(next, lookup, playerId, cause)
        if (decided is KillOutcome.Choice && optionId.isEmpty()) {
            // The storyteller has to answer before anything is written.
            return state
        }
        val attempt = Deaths.attempt(next, lookup, playerId, cause, optionId)
        next = attempt.state

        val resolved = resolve(record, attempt)
        next = replaceLast(next, resolved)

        // 6. The Undertaker learns whoever actually died.
        val diedId = resolved.diedInsteadId ?: resolved.playerId
        if (resolved.outcome == ExecutionOutcome.DIED && diedId != null) {
            next = Effects.place(
                state = next,
                target = diedId,
                kind = EffectKind.MARKER,
                sourceCharacterId = "undertaker",
                sourcePlayerId = null,
                until = Tokens.rule("undertaker", DIED_TODAY)?.until ?: Until.DAWN,
                label = DIED_TODAY,
                note = "Executed on day ${next.cycle}.",
            ).state
        }
        return Effects.reconcile(next, lookup)
    }

    /** Records that today had no execution. Idempotent; replaced if an execution follows. */
    fun noExecution(state: GameState): GameState {
        if (DayRules.executionSpent(state)) return state
        val cleared = state.executions.filterNot {
            it.day == state.cycle && it.outcome == ExecutionOutcome.NO_EXECUTION
        }
        return state.copy(
            executions = cleared + ExecutionRecord(
                day = state.cycle,
                outcome = ExecutionOutcome.NO_EXECUTION,
            ),
        )
    }

    /**
     * Exile a Traveller. Never an execution — no `ExecutionRecord` is written,
     * the day's execution stays available, and no ability modifies the vote.
     */
    fun exile(state: GameState, lookup: (String) -> Character?, playerId: Long): GameState {
        val player = state.player(playerId) ?: return state
        val attempt = Deaths.attempt(
            state = state,
            lookup = lookup,
            targetId = playerId,
            cause = KillCause(DeathCause.EXILE, sourceCharacterId = "st"),
        )
        if (attempt.outcome is KillOutcome.Choice) return state
        return Ledger.announce(
            attempt.state,
            text = "${player.name} is exiled.",
            sourceId = "st",
            actorId = playerId,
        )
    }

    /**
     * What the storyteller must confirm now. Registry rows (`CharacterRule.day.
     * onExecution`, WP7) come first and win outright over the built-in of the
     * same character id.
     */
    fun consequences(
        state: GameState,
        lookup: (String) -> Character?,
        record: ExecutionRecord,
    ): List<ExecutionConsequence> {
        val fromRegistry = mutableListOf<ExecutionConsequence>()
        for (holder in state.seats) {
            val id = holder.characterId?.let(Character::normalizeId) ?: continue
            val hook = CharacterRules.all[id]?.day?.onExecution ?: continue
            fromRegistry += hook(ExecutionContext(state, lookup, record, holder))
        }
        val covered = fromRegistry.map { Character.normalizeId(it.sourceId) }.toSet()
        return fromRegistry +
            builtInConsequences(state, lookup, record)
                .filterNot { Character.normalizeId(it.sourceId) in covered }
    }

    // ---- internals ----

    /** The official Undertaker mark. */
    private const val DIED_TODAY = "Died Today"

    private fun append(state: GameState, record: ExecutionRecord): GameState = state.copy(
        // A declared "no execution" is replaced the moment a real one happens.
        executions = state.executions.filterNot {
            it.day == record.day && it.outcome == ExecutionOutcome.NO_EXECUTION
        } + record,
    )

    private fun replaceLast(state: GameState, record: ExecutionRecord): GameState {
        val index = state.executions.indexOfLast { it.day == record.day }
        if (index < 0) return state.copy(executions = state.executions + record)
        return state.copy(
            executions = state.executions.mapIndexed { i, r -> if (i == index) record else r },
        )
    }

    /** Turns the kill funnel's verdict into the execution's own. */
    private fun resolve(record: ExecutionRecord, attempt: DeathAttempt): ExecutionRecord =
        when (val o = attempt.outcome) {
            is KillOutcome.Dies -> record.copy(
                outcome = ExecutionOutcome.DIED,
                deathEventId = attempt.event?.id,
            )

            is KillOutcome.Prevented -> record.copy(
                outcome = ExecutionOutcome.SURVIVED,
                preventedBy = o.by?.sourceCharacterId.orEmpty(),
            )

            is KillOutcome.Spends -> record.copy(
                outcome = ExecutionOutcome.SURVIVED,
                preventedBy = o.sourceId,
            )

            // The Zombuul is shrouded and registers dead, but they are not dead.
            is KillOutcome.RegistersDead -> record.copy(
                outcome = ExecutionOutcome.SURVIVED,
                preventedBy = "zombuul",
                deathEventId = attempt.event?.id,
            )

            is KillOutcome.Redirect -> record.copy(
                outcome = if (attempt.event != null) ExecutionOutcome.DIED else ExecutionOutcome.SURVIVED,
                deathEventId = attempt.event?.id,
                diedInsteadId = attempt.event?.playerId?.takeIf { it != record.playerId },
                preventedBy = "scapegoat".takeIf { attempt.event != null } ?: "mayor",
            )

            KillOutcome.AlreadyDead -> record.copy(
                outcome = ExecutionOutcome.SURVIVED,
                preventedBy = PREVENTED_BY_ALREADY_DEAD,
            )

            // Unreachable: an unanswered Choice returns before this point.
            is KillOutcome.Choice -> record
        }

    private fun viaSource(via: ExecutionVia): String = when (via) {
        ExecutionVia.VIRGIN -> "virgin"
        ExecutionVia.VIZIER -> "vizier"
        ExecutionVia.JUDGE -> "judge"
        ExecutionVia.PSYCHOPATH -> "psychopath"
        ExecutionVia.RIOT -> "riot"
        ExecutionVia.VOTE, ExecutionVia.STORYTELLER -> ""
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun builtInConsequences(
        state: GameState,
        lookup: (String) -> Character?,
        record: ExecutionRecord,
    ): List<ExecutionConsequence> = buildList {
        val executed = record.playerId?.let { state.player(it) }
        val died = (record.diedInsteadId ?: record.playerId)?.let { state.player(it) }
        val name = executed?.name ?: "the nominee"

        // Whoever the kill funnel credited with the save gets the first row.
        if (record.outcome == ExecutionOutcome.SURVIVED && record.preventedBy.isNotEmpty()) {
            val saver = lookup(record.preventedBy)?.name ?: record.preventedBy
            add(
                ExecutionConsequence(
                    sourceId = record.preventedBy,
                    headline = "Say: '$name was executed… and remains alive.' Do not say why.",
                    detail = "Credited to the $saver.",
                ),
            )
        }

        // Every protection on the seat gets a row, whether or not it fired: this
        // sheet is where "why is nobody dying?" gets answered. The one already
        // credited above is not repeated.
        if (executed != null) {
            for (effect in Status.protections(state, lookup, executed.id)) {
                val kind = effect.kind
                if (kind !in PROTECTION_ROWS) continue
                if (Character.normalizeId(effect.sourceCharacterId) ==
                    Character.normalizeId(record.preventedBy)
                ) {
                    continue
                }
                add(
                    ExecutionConsequence(
                        sourceId = effect.sourceCharacterId,
                        headline = "$name carries ${effect.label.ifEmpty { kind.name }} — " +
                            "check whether it stops this execution.",
                        impaired = !Status.hasAbility(state, lookup, executed.id),
                    ),
                )
            }
            // The Fool's first death is a character rule, not a standing effect.
            if (executed.characterId?.let(Character::normalizeId) == "fool" &&
                Status.live(state, lookup, executed.id, EffectKind.SPENT).isEmpty() &&
                Character.normalizeId(record.preventedBy) != "fool"
            ) {
                add(
                    ExecutionConsequence(
                        sourceId = "fool",
                        headline = "$name is the Fool — their first death does not happen.",
                        impaired = !Status.hasAbility(state, lookup, executed.id),
                    ),
                )
            }
        }

        // Pacifist: an executed GOOD player might not die.
        DayRules.holderOfWithAbility(state, lookup, "pacifist")?.let { pacifist ->
            if (executed != null && !Registration.registersEvil(state, lookup, executed)) {
                add(
                    ExecutionConsequence(
                        sourceId = "pacifist",
                        headline = "${pacifist.name} is the Pacifist — $name may survive this execution.",
                        options = listOf(
                            TriggerOption(Deaths.OPTION_DIES, "They die", isDefault = true),
                            TriggerOption(Deaths.OPTION_LIVES, "They survive — say nothing"),
                        ),
                    ),
                )
            }
        }

        // Scapegoat: a Traveller of the executed player's alignment may die instead.
        if (executed != null) {
            val evil = Registration.registersEvil(state, lookup, executed)
            state.alivePlayers.firstOrNull {
                it.characterId?.let(Character::normalizeId) == "scapegoat" &&
                    it.id != executed.id &&
                    Registration.registersEvil(state, lookup, it) == evil &&
                    Status.hasAbility(state, lookup, it.id)
            }?.let {
                add(
                    ExecutionConsequence(
                        sourceId = "scapegoat",
                        headline = "${it.name} is a Scapegoat of $name's alignment — " +
                            "they may be executed instead.",
                        options = listOf(
                            TriggerOption(Deaths.OPTION_DIES, "$name dies", isDefault = true),
                            TriggerOption(Deaths.OPTION_REDIRECT, "${it.name} dies instead"),
                        ),
                    ),
                )
            }
        }

        // Vizier: cannot die during the day.
        if (executed != null && DayRules.immuneToDayDeath(state, lookup, executed.id)) {
            add(
                ExecutionConsequence(
                    sourceId = "vizier",
                    headline = "$name cannot die during the day.",
                ),
            )
        }

        // Psychopath: roshambo, and the day ends either way.
        if (executed?.characterId?.let(Character::normalizeId) == "psychopath") {
            add(
                ExecutionConsequence(
                    sourceId = "psychopath",
                    headline = "Play rock-paper-scissors with $name — if they win, they do not die.",
                    options = listOf(
                        TriggerOption(Deaths.OPTION_DIES, "They lost — they die", isDefault = true),
                        TriggerOption(Deaths.OPTION_LIVES, "They won — they live"),
                    ),
                    impaired = !Status.hasAbility(state, lookup, executed.id),
                ),
            )
        }

        // Zombuul: the first death is a shroud that is not a death.
        if (executed?.characterId?.let(Character::normalizeId) == "zombuul") {
            add(
                ExecutionConsequence(
                    sourceId = "zombuul",
                    headline = "Announce the death, but do not shroud $name — they register as dead.",
                    impaired = !Status.hasAbility(state, lookup, executed.id),
                ),
            )
        }

        // Saint: an executed sober Saint loses the game for good.
        if (executed?.characterId?.let(Character::normalizeId) == "saint" &&
            record.outcome == ExecutionOutcome.DIED
        ) {
            add(
                ExecutionConsequence(
                    sourceId = "saint",
                    headline = "$name was the Saint — EVIL WINS.",
                    impaired = record.abilityImpairedAtExecution == true,
                ),
            )
        }

        // Goblin: a claimed and executed Goblin wins outright.
        val claim = state.nominations.lastOrNull {
            it.day == record.day && !it.isExile && it.nomineeId == record.playerId
        }
        if (claim?.goblinClaim == true) {
            add(
                ExecutionConsequence(
                    sourceId = "goblin",
                    headline = "$name claimed the Goblin and was executed — if they are the " +
                        "Goblin, EVIL WINS.",
                    impaired = executed != null && !Status.hasAbility(state, lookup, executed.id),
                ),
            )
        }

        // Fearmonger: the win only fires when the Fearmonger nominated.
        DayRules.holderOf(state, "fearmonger")?.let { fearmonger ->
            if (executed != null &&
                DayRules.hasToken(state, executed.id, "fearmonger", "Fear") &&
                record.nominatorId == fearmonger.id
            ) {
                add(
                    ExecutionConsequence(
                        sourceId = "fearmonger",
                        headline = "The Fearmonger nominated $name and they were executed — EVIL WINS.",
                        impaired = !Status.hasAbility(state, lookup, fearmonger.id),
                    ),
                )
            }
        }

        // Evil Twin: the good twin's execution ends the game.
        if (executed != null && DayRules.hasToken(state, executed.id, "eviltwin", "Twin") &&
            record.outcome == ExecutionOutcome.DIED &&
            !Registration.registersEvil(state, lookup, executed)
        ) {
            add(
                ExecutionConsequence(
                    sourceId = "eviltwin",
                    headline = "$name is the Evil Twin's good twin — EVIL WINS.",
                ),
            )
        }

        // Minstrel: everyone else is drunk until dusk tomorrow.
        if (executed?.characterId?.let(Character::normalizeId) == "minstrel" &&
            record.outcome == ExecutionOutcome.DIED
        ) {
            add(
                ExecutionConsequence(
                    sourceId = "minstrel",
                    headline = "The Minstrel died — every other player is drunk until dusk tomorrow.",
                    impaired = record.abilityImpairedAtExecution == true,
                ),
            )
        }

        // Mastermind: play one more day when the Demon died by execution.
        if (DayRules.holderOf(state, "mastermind") != null && executed != null &&
            record.outcome == ExecutionOutcome.DIED &&
            Team.DEMON in Registration.registersAs(state, lookup, executed)
        ) {
            add(
                ExecutionConsequence(
                    sourceId = "mastermind",
                    headline = "The Demon died by execution and a Mastermind is in play — " +
                        "play one more day.",
                    options = listOf(
                        TriggerOption("mastermind-day", "Play the Mastermind day", isDefault = true),
                        TriggerOption(DayRules.OPTION_SKIP, "The game ends now"),
                    ),
                ),
            )
        }

        // Leviathan: two good players executed and evil wins.
        DayRules.holderOf(state, "leviathan")?.let {
            if (executed != null && !Registration.registersEvil(state, lookup, executed)) {
                add(
                    ExecutionConsequence(
                        sourceId = "leviathan",
                        headline = "A good player was executed — add a " +
                            "'Good Player Executed' token. Two of them and EVIL WINS.",
                    ),
                )
            }
        }

        // Boomdandy: the explosion.
        if (executed?.characterId?.let(Character::normalizeId) == "boomdandy" &&
            record.outcome == ExecutionOutcome.DIED
        ) {
            add(
                ExecutionConsequence(
                    sourceId = "boomdandy",
                    headline = "The Boomdandy was executed — all but three players die. " +
                        "Count to three, then the loudest player is executed.",
                ),
            )
        }

        // Cannibal: the executed player's ability, and the poison if they were evil.
        DayRules.holderOfWithAbility(state, lookup, "cannibal")?.let { cannibal ->
            if (died != null && record.outcome == ExecutionOutcome.DIED) {
                add(
                    ExecutionConsequence(
                        sourceId = "cannibal",
                        headline = "${cannibal.name} (Cannibal) gains ${died.name}'s ability " +
                            "tonight — place Lunch.",
                        detail = if (record.wasEvilAtExecution == true) {
                            "The executed player was evil: the Cannibal is poisoned instead."
                        } else {
                            ""
                        },
                    ),
                )
            }
        }

        // Undertaker: the token is already placed; this is the reminder to use it.
        DayRules.holderOf(state, "undertaker")?.let {
            if (died != null && record.outcome == ExecutionOutcome.DIED) {
                add(
                    ExecutionConsequence(
                        sourceId = "undertaker",
                        headline = "${it.name} (Undertaker) learns ${died.name}'s character tonight.",
                    ),
                )
            }
        }

        // Godfather: "an Outsider died today".
        DayRules.holderOf(state, "godfather")?.let { godfather ->
            val outsider = died?.characterId?.let(lookup)?.team == Team.OUTSIDER
            if (died != null && record.outcome == ExecutionOutcome.DIED && outsider) {
                add(
                    ExecutionConsequence(
                        sourceId = "godfather",
                        headline = "An Outsider died today — ${godfather.name} (Godfather) kills " +
                            "tonight. Place Died Today.",
                        impaired = !Status.hasAbility(state, lookup, godfather.id),
                    ),
                )
            }
        }
    }

    /** Protective kinds worth surfacing on the confirmation sheet. */
    private val PROTECTION_ROWS: List<EffectKind> = listOf(
        EffectKind.SURVIVES_EXECUTION,
        EffectKind.CANT_DIE,
        EffectKind.ONLY_EXECUTION_KILLS,
        EffectKind.DAY_IMMUNE,
    )
}
