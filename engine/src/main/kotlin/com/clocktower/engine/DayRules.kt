package com.clocktower.engine

import kotlinx.serialization.Serializable

/** A frozen snapshot of how ONE nomination was voted. Persisted on the Nomination (lead D27). */
@Serializable
data class VoteRules(
    val eligibleVoterIds: List<Long>,
    val threshold: Int,
    /** False under a sober Voudon and for every exile. */
    val spendsGhostVotes: Boolean,
    /** Per-voter weight. Absent = 1. Bureaucrat 3, Thief -1, Banshee 2. */
    val weights: Map<Long, Int> = emptyMap(),
    /** One line per modifier applied, for the log and the tally explanation. */
    val reasons: List<String> = emptyList(),
) {
    fun weightOf(playerId: Long): Int = weights[playerId] ?: 1

    fun tally(voterIds: Collection<Long>): Int = voterIds.sumOf { weightOf(it) }
}

@Serializable
enum class NominationResult { ABOUT_TO_DIE, SAFE, TIED, WITHDRAWN }

/** The Judge forces one nomination to pass or fail. */
@Serializable
enum class JudgeForce { PASS, FAIL }

/** A nomination and its vote tally. */
@Serializable
data class Nomination(
    val day: Int,
    val nominatorId: Long,
    /** May be [GameState.STORYTELLER_SEAT_ID] in an Atheist game (lead D44). */
    val nomineeId: Long,
    /** The WEIGHTED tally — what the rules use. */
    val votes: Int = 0,
    /** Raw hands raised, clock order from the nominee's left. Never weighted. */
    val voterIds: List<Long> = emptyList(),
    val result: NominationResult = NominationResult.SAFE,
    val isExile: Boolean = false,

    // ---- added ----
    /** The FULL rules snapshot at the moment of the tally. Never recompute from live state. */
    val voteRules: VoteRules? = null,
    /** Extra hands one voter raised (the awoken Banshee's second). */
    val extraVotes: Map<Long, Int> = emptyMap(),
    /** Registration snapshot (lead D51) — Town Crier and Flowergirl read THIS. */
    val nominatorCharacterId: String? = null,
    val nominatorTeams: Set<Team> = emptySet(),
    val demonIdsAtRecord: List<Long> = emptyList(),
    val registersRuling: String = "",
    /** The nominee publicly claimed Goblin before votes were called. */
    val goblinClaim: Boolean = false,
    val judgeForced: JudgeForce? = null,
    /** Ability triggers that fired on this nomination, for the log. */
    val triggersFired: List<String> = emptyList(),
)

/** What a nomination trigger does to the day. */
@Serializable
enum class TriggerKind {
    /** The engine kills someone the moment the nomination is declared. */
    AUTO_DEATH,

    /** The engine executes someone immediately (consuming the day's execution). */
    AUTO_EXECUTION,

    /** No more nominations today. */
    END_DAY,

    /** Changes how this vote is tallied or who may vote. */
    VOTE_MODIFIER,

    /** The storyteller must decide something before votes are called. */
    CHOICE,

    /** Information only. */
    WARN,
}

@Serializable
data class TriggerOption(val id: String, val label: String, val isDefault: Boolean = false)

@Serializable
data class NominationTrigger(
    val kind: TriggerKind,
    val sourceId: String,
    val actorId: Long? = null,
    val targetId: Long? = null,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work — surfaced as a caution, never as suppression. */
    val impaired: Boolean = false,
)

@Serializable
data class NominationCheck(
    val legal: Boolean,
    /** Hard rule violations: "Dana has already nominated today". */
    val blockers: List<String> = emptyList(),
    /** Legal but unusual: "Nominating a dead player — allowed, no ghost vote at stake". */
    val cautions: List<String> = emptyList(),
    val triggers: List<NominationTrigger> = emptyList(),
)

/** Vote thresholds. Kept from the pre-split engine; [DayRules.voteRules] supersedes it. */
object Voting {
    /** Threshold for an execution among [aliveCount] living players. */
    fun executionThreshold(aliveCount: Int): Int = (aliveCount + 1) / 2

    /** Threshold for a traveller exile among [totalCount] players. */
    fun exileThreshold(totalCount: Int): Int = (totalCount + 1) / 2

    /**
     * Whether [votes] makes the nominee about-to-die, given the current
     * highest tally [currentHighest] today (0 if none) and the threshold.
     * Equal to the highest is a tie (no one dies); beating it marks the new
     * about-to-die player.
     */
    fun outcome(votes: Int, threshold: Int, currentHighest: Int): NominationResult = when {
        votes < threshold -> NominationResult.SAFE
        votes > currentHighest -> NominationResult.ABOUT_TO_DIE
        votes == currentHighest -> NominationResult.TIED
        else -> NominationResult.SAFE // below today's highest tally
    }
}

/**
 * Nomination, voting and day predicates (WP3, ARCHITECTURE §2.8).
 *
 * Nothing here is stored: "the day is closed", "the execution is spent" and
 * "who is on the block" are all derived from `state.executions` and
 * `state.nominations` (lead D30). The per-character rows this object knows are
 * exactly the ones ARCHITECTURE §2.8 names; a `CharacterRule.day.onNomination`
 * from `engine/rules/` **wins outright** over the built-in of the same id, so
 * WP7 can refine any of them without editing this file.
 */
object DayRules {

    /** May this player do this, and why not. */
    data class Right(val allowed: Boolean, val reason: String = "")

    // ---- option ids answering a NominationTrigger ----

    /** Take the trigger's default action. */
    const val OPTION_APPLY: String = "apply"

    /** Decline the trigger — the storyteller ruled it does not fire. */
    const val OPTION_SKIP: String = "skip"

    /** Virgin: the nominator registered as a Townsfolk after all / did not. */
    const val OPTION_EXECUTE: String = "execute"
    const val OPTION_REGISTERS_GOOD: String = "spy-registers-good"

    // ---- official token labels this object reads (never by substring) ----
    private const val TOKEN_THREE_VOTES = "3 Votes"
    private const val TOKEN_NEGATIVE_VOTE = "Negative Vote"
    private const val TOKEN_HAS_ABILITY = "Has Ability"
    private const val TOKEN_MAY_NOT_NOMINATE = "May Not Nominate"
    private const val TOKEN_CURSED = "Cursed"
    private const val TOKEN_FEAR = "Fear"
    private const val TOKEN_AMIGO = "Amigo"
    private const val TOKEN_CLAIMED = "Claimed"
    private const val TOKEN_MASTER = "Master"
    private const val TOKEN_NO_ABILITY = "No Ability"
    private const val TOKEN_BEGGAR = "Token"

    /** The one character that spends a physical vote token to vote (lead D72). */
    private const val BEGGAR = "beggar"

    // ---- who may nominate / be nominated ----

    /**
     * Bishop: only the ST nominates. Butcher: one extra after the day's first
     * execution. Banshee (awoken): twice per day, and may nominate while dead.
     * Golem: once per game.
     */
    fun canNominate(state: GameState, lookup: (String) -> Character?, playerId: Long): Right {
        val player = state.player(playerId)
            ?: return Right(false, "That seat is not in the game.")
        if (!player.seated) return Right(false, "${player.name} has left the game.")
        if (state.phase != Phase.DAY) return Right(false, "Nominations only happen during the day.")
        if (nominationsClosed(state, lookup)) {
            return Right(false, "Nominations are closed today — the day's execution is settled.")
        }
        bishop(state, lookup)?.let {
            if (it.id != playerId) {
                return Right(
                    false,
                    "Only the storyteller nominates while the Bishop (${it.name}) has their ability.",
                )
            }
        }
        if (Status.live(state, lookup, playerId, EffectKind.NO_NOMINATE).isNotEmpty()) {
            return Right(false, "${player.name} may not nominate.")
        }
        val id = player.characterId?.let(Character::normalizeId)
        val awokenBanshee = id == "banshee" && hasToken(state, playerId, "banshee", TOKEN_HAS_ABILITY)
        if (!player.alive && !awokenBanshee) {
            return Right(false, "${player.name} is dead — the dead may vote, but never nominate.")
        }
        if (id == "golem" && hasToken(state, playerId, "golem", TOKEN_MAY_NOT_NOMINATE)) {
            return Right(false, "The Golem has already used their nomination.")
        }
        val used = state.nominations.count {
            it.day == state.cycle && it.nominatorId == playerId && !it.isExile
        }
        val allowance = nominationAllowance(state, lookup, player, awokenBanshee)
        if (used >= allowance) {
            return Right(false, "${player.name} has already nominated today.")
        }
        return Right(true)
    }

    /** How many nominations this seat gets today: 1, or 2 for a Butcher / awoken Banshee. */
    private fun nominationAllowance(
        state: GameState,
        lookup: (String) -> Character?,
        player: Player,
        awokenBanshee: Boolean,
    ): Int {
        val id = player.characterId?.let(Character::normalizeId)
        if (awokenBanshee) return 2
        // "Each day, after the 1st execution, you may nominate again."
        if (id == "butcher" && Status.hasAbility(state, lookup, player.id) && executionSpent(state)) {
            return 2
        }
        return 1
    }

    /** Anyone not nominated today, DEAD INCLUDED (rules: dead players may be executed). */
    fun canBeNominated(state: GameState, lookup: (String) -> Character?, playerId: Long): Right {
        if (playerId == GameState.STORYTELLER_SEAT_ID) {
            return if (state.seats.any { it.characterId?.let(Character::normalizeId) == "atheist" }) {
                Right(true)
            } else {
                Right(false, "The storyteller can only be nominated in an Atheist game.")
            }
        }
        val player = state.player(playerId)
            ?: return Right(false, "That seat is not in the game.")
        if (!player.seated) return Right(false, "${player.name} has left the game.")
        if (nominationsClosed(state, lookup)) {
            return Right(false, "Nominations are closed today — the day's execution is settled.")
        }
        if (hasBeenNominatedToday(state, playerId)) {
            return Right(false, "${player.name} has already been nominated today.")
        }
        return Right(true)
    }

    /** Pure pre-flight, called on every chip tap so the UI renders live. */
    fun checkNomination(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): NominationCheck {
        val blockers = mutableListOf<String>()
        val cautions = mutableListOf<String>()

        nominatorId?.let { id ->
            canNominate(state, lookup, id).takeIf { !it.allowed }?.let { blockers += it.reason }
        }
        nomineeId?.let { id ->
            canBeNominated(state, lookup, id).takeIf { !it.allowed }?.let { blockers += it.reason }
        }
        if (nominatorId != null && nomineeId != null && nominatorId == nomineeId) {
            cautions += "Self-nomination — legal, but check that is what was said."
        }
        nomineeId?.let { id ->
            val nominee = state.player(id)
            if (nominee != null && !nominee.alive) {
                cautions += "Nominating a dead player — allowed, but no ghost vote is at stake."
            }
            if (nominee?.isTraveller == true) {
                cautions += "${nominee.name} is a Traveller — this is an exile call, not an execution."
            }
        }
        val triggers = triggersFor(state, lookup, nominatorId, nomineeId)
        return NominationCheck(
            legal = blockers.isEmpty(),
            // One reason, one row, one override. A closed day is a fact about
            // the DAY, so `canNominate` and `canBeNominated` both refuse with
            // the byte-identical sentence — and the panel drew it twice, each
            // with its own [Allow anyway] (playtest C2-6, the residue of C-8).
            // Deduping here rather than in the panel keeps every caller honest:
            // the log and the web build read the same list.
            blockers = blockers.distinct(),
            cautions = cautions.distinct(),
            triggers = triggers,
        )
    }

    /**
     * Registry rows first (WP7), then the built-in table of ARCHITECTURE §2.8 for
     * every character the registry does not yet cover.
     *
     * Fabled hold no seat, so their rows are walked separately with
     * [CharacterRules.GRIMOIRE_HOLDER] — the Big Wig's per-nominee madness would
     * otherwise never fire.
     */
    private fun triggersFor(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): List<NominationTrigger> {
        val fromRegistry = mutableListOf<NominationTrigger>()
        for (holder in state.seats) {
            val id = holder.characterId?.let(Character::normalizeId) ?: continue
            val hook = CharacterRules.all[id]?.day?.onNomination ?: continue
            fromRegistry += hook(NominationContext(state, lookup, nominatorId, nomineeId, holder))
        }
        for (rule in CharacterRules.fabledRows(state)) {
            val hook = rule.day?.onNomination ?: continue
            fromRegistry += hook(
                NominationContext(
                    state, lookup, nominatorId, nomineeId, CharacterRules.GRIMOIRE_HOLDER,
                ),
            )
        }
        val covered = fromRegistry.map { Character.normalizeId(it.sourceId) }.toSet()
        val builtIn = builtInTriggers(state, lookup, nominatorId, nomineeId)
            .filterNot { Character.normalizeId(it.sourceId) in covered }
        return fromRegistry + builtIn
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun builtInTriggers(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): List<NominationTrigger> = buildList {
        val nominator = nominatorId?.let { state.player(it) }
        val nominee = nomineeId?.let { state.player(it) }

        // Virgin: "The 1st time you are nominated, if the nominator is a
        // Townsfolk, they are executed immediately." First time EVER, not today.
        if (nominee != null && nominee.characterId?.let(Character::normalizeId) == "virgin") {
            val everNominated = state.nominations.any { it.nomineeId == nominee.id && !it.isExile }
            val spent = hasToken(state, nominee.id, "virgin", TOKEN_NO_ABILITY) ||
                Memory.isSpent(state, "virgin", nominee.id)
            if (!everNominated && !spent && nominee.alive) {
                val townsfolk = nominator != null &&
                    Team.TOWNSFOLK in Registration.registersAs(state, lookup, nominator)
                val impaired = Status.isImpaired(state, lookup, nominee.id)
                add(
                    NominationTrigger(
                        kind = if (townsfolk) TriggerKind.AUTO_EXECUTION else TriggerKind.WARN,
                        sourceId = "virgin",
                        actorId = nominee.id,
                        targetId = nominator?.id,
                        headline = if (townsfolk) {
                            "${nominator?.name ?: "The nominator"} is executed immediately — " +
                                "the Virgin's first nomination."
                        } else {
                            "${nominee.name} is the Virgin, nominated for the first time — " +
                                "${nominator?.name ?: "the nominator"} does not register as a Townsfolk."
                        },
                        detail = "The Virgin's ability is spent either way.",
                        options = listOf(
                            TriggerOption(OPTION_EXECUTE, "Execute the nominator", isDefault = townsfolk),
                            TriggerOption(OPTION_REGISTERS_GOOD, "They do not register as a Townsfolk"),
                            TriggerOption(OPTION_SKIP, "Nothing happens"),
                        ),
                        impaired = impaired,
                    ),
                )
            }
        }

        // Witch: "If the player you cursed nominates, they die."
        val witch = holderOf(state, "witch")
        if (witch != null && nominator != null &&
            hasToken(state, nominator.id, "witch", TOKEN_CURSED) &&
            state.aliveCountResidents >= 4
        ) {
            add(
                NominationTrigger(
                    kind = TriggerKind.AUTO_DEATH,
                    sourceId = "witch",
                    actorId = witch.id,
                    targetId = nominator.id,
                    headline = "${nominator.name} was cursed by the Witch — they die now.",
                    detail = "The nomination stands and the vote continues.",
                    options = listOf(
                        TriggerOption(OPTION_APPLY, "They die", isDefault = true),
                        TriggerOption(OPTION_SKIP, "Nothing happens"),
                    ),
                    impaired = !Status.hasAbility(state, lookup, witch.id),
                ),
            )
        }

        // Golem: "You may only nominate once per game. When you do, if the
        // nominee is not the Demon, they die."
        if (nominator != null && nominator.characterId?.let(Character::normalizeId) == "golem" &&
            !hasToken(state, nominator.id, "golem", TOKEN_MAY_NOT_NOMINATE)
        ) {
            val nomineeIsDemon = nominee != null &&
                Team.DEMON in Registration.registersAs(state, lookup, nominee)
            add(
                NominationTrigger(
                    kind = if (nomineeIsDemon) TriggerKind.WARN else TriggerKind.AUTO_DEATH,
                    sourceId = "golem",
                    actorId = nominator.id,
                    targetId = nominee?.id,
                    headline = if (nomineeIsDemon) {
                        "${nominee?.name} is the Demon — the Golem's nomination kills nobody."
                    } else {
                        "${nominee?.name ?: "The nominee"} dies — the Golem nominated them."
                    },
                    detail = "The Golem may not nominate again. The vote continues as normal.",
                    options = listOf(
                        TriggerOption(OPTION_APPLY, "Apply", isDefault = true),
                        TriggerOption(OPTION_SKIP, "Nothing happens"),
                    ),
                    impaired = !Status.hasAbility(state, lookup, nominator.id),
                ),
            )
        }

        // Gnome: "If a player nominates an Amigo, the Gnome may kill them."
        val gnome = holderOf(state, "gnome")
        if (gnome != null && nominee != null && nominator != null &&
            hasToken(state, nominee.id, "gnome", TOKEN_AMIGO)
        ) {
            add(
                NominationTrigger(
                    kind = TriggerKind.CHOICE,
                    sourceId = "gnome",
                    actorId = gnome.id,
                    targetId = nominator.id,
                    headline = "${nominee.name} is the Gnome's Amigo — " +
                        "${gnome.name} may kill ${nominator.name}.",
                    detail = "The vote continues either way.",
                    options = listOf(
                        TriggerOption(OPTION_APPLY, "${nominator.name} dies"),
                        TriggerOption(OPTION_SKIP, "Nobody dies", isDefault = true),
                    ),
                    impaired = !Status.hasAbility(state, lookup, gnome.id),
                ),
            )
        }

        // Fearmonger: the win fires at EXECUTION, and only when the Fearmonger
        // is the nominator.
        val fearmonger = holderOf(state, "fearmonger")
        if (nominee != null && hasToken(state, nominee.id, "fearmonger", TOKEN_FEAR)) {
            val byFearmonger = fearmonger != null && nominator?.id == fearmonger.id
            add(
                NominationTrigger(
                    kind = TriggerKind.WARN,
                    sourceId = "fearmonger",
                    actorId = fearmonger?.id,
                    targetId = nominee.id,
                    headline = if (byFearmonger) {
                        "The Fearmonger nominated ${nominee.name} — if they are executed, EVIL WINS."
                    } else {
                        "${nominee.name} carries Fear, but the Fearmonger did not nominate — " +
                            "this is an ordinary nomination."
                    },
                    detail = "Announce that the Fearmonger's ability is in play, not who holds it.",
                    impaired = fearmonger != null && !Status.hasAbility(state, lookup, fearmonger.id),
                ),
            )
        }

        // Goblin: "If you publicly claim to be the Goblin when nominated, and are
        // executed that day, your team wins."
        //
        // Gated like every sibling above it on the character being able to win
        // it at all: a Goblin must be IN PLAY, and an exile is not an execution
        // (day-engine §D test 38 — "On an exile it does not fire"). Ungated it
        // fired on every nomination of every Trouble Brewing game, where nobody
        // can be the Goblin, and the claim it invited then carried an
        // "…if they are the Goblin, EVIL WINS" advisory into the execution
        // sheet of a game with no Goblin (playtest C-2).
        val goblin = holderOf(state, "goblin")
        if (goblin != null && nominee != null && !nominee.isTraveller) {
            add(
                NominationTrigger(
                    kind = TriggerKind.CHOICE,
                    sourceId = "goblin",
                    actorId = nominee.id,
                    headline = "Did ${nominee.name} claim to be the Goblin?",
                    detail = "A claim only counts when it is made out loud before the votes.",
                    options = listOf(
                        TriggerOption(OPTION_APPLY, "They claimed the Goblin"),
                        TriggerOption(OPTION_SKIP, "No claim", isDefault = true),
                    ),
                ),
            )
        }

        // Vizier: "If you are alive, all players may vote and votes cannot be
        // hidden; you may choose to execute immediately."
        val vizier = vizier(state, lookup)
        if (vizier != null) {
            add(
                NominationTrigger(
                    kind = TriggerKind.VOTE_MODIFIER,
                    sourceId = "vizier",
                    actorId = vizier.id,
                    targetId = nomineeId,
                    headline = "${vizier.name} (Vizier) may execute immediately once a good " +
                        "player has voted.",
                    detail = "Offer the Vizier's execution after the tally.",
                ),
            )
        }

        // Riot: on day 3 the nominee dies and must nominate again.
        if (riotDay(state) == 3 && nominee != null) {
            add(
                NominationTrigger(
                    kind = TriggerKind.AUTO_DEATH,
                    sourceId = "riot",
                    targetId = nominee.id,
                    headline = "${nominee.name} dies immediately and must nominate — Riot.",
                    detail = "There is no vote. The day ends when nobody is left to nominate.",
                    options = listOf(
                        TriggerOption(OPTION_APPLY, "They die", isDefault = true),
                        TriggerOption(OPTION_SKIP, "Nothing happens"),
                    ),
                ),
            )
        }

        // Psychopath: the roshambo happens at execution; the day ends either way.
        if (nominee != null && nominee.characterId?.let(Character::normalizeId) == "psychopath" &&
            nominee.alive
        ) {
            add(
                NominationTrigger(
                    kind = TriggerKind.WARN,
                    sourceId = "psychopath",
                    actorId = nominee.id,
                    headline = "${nominee.name} is the Psychopath — " +
                        "play rock-paper-scissors before the execution resolves.",
                    impaired = !Status.hasAbility(state, lookup, nominee.id),
                ),
            )
        }

        // Madness: check the claim before this goes any further.
        for (seat in listOfNotNull(nominator, nominee)) {
            val mad = Status.live(state, lookup, seat.id, EffectKind.MAD).firstOrNull() ?: continue
            add(
                NominationTrigger(
                    kind = TriggerKind.WARN,
                    sourceId = mad.sourceCharacterId.ifEmpty { "cerenovus" },
                    actorId = seat.id,
                    headline = "${seat.name} is mad that they are " +
                        "${lookup(mad.characterId.orEmpty())?.name ?: "a character"} — " +
                        "check the claim before this goes further.",
                    detail = "Execute them today if they are not trying to convince the town.",
                ),
            )
        }
    }

    /** Applies a trigger the ST accepted (or declined with `optionId = "skip"`). */
    fun applyTrigger(
        state: GameState,
        lookup: (String) -> Character?,
        trigger: NominationTrigger,
        optionId: String,
    ): GameState {
        if (optionId == OPTION_SKIP) return state
        return when (Character.normalizeId(trigger.sourceId)) {
            "virgin" -> applyVirgin(state, lookup, trigger, optionId)

            "witch" -> trigger.targetId?.let {
                Deaths.attempt(
                    state, lookup, it,
                    KillCause(DeathCause.EVIL_ABILITY, "witch", trigger.actorId),
                ).state
            } ?: state

            "golem" -> applyGolem(state, lookup, trigger)

            "gnome" -> trigger.targetId?.let {
                Deaths.attempt(
                    state, lookup, it,
                    KillCause(DeathCause.TRAVELLER_ABILITY, "gnome", trigger.actorId),
                ).state
            } ?: state

            "riot" -> trigger.targetId?.let {
                Deaths.attempt(
                    state, lookup, it,
                    KillCause(DeathCause.DEMON_KILL, "riot", trigger.actorId),
                ).state
            } ?: state

            "goblin" -> applyGoblinClaim(state, trigger)

            // WARN and VOTE_MODIFIER rows change nothing by themselves.
            else -> state
        }
    }

    private fun applyVirgin(
        state: GameState,
        lookup: (String) -> Character?,
        trigger: NominationTrigger,
        optionId: String,
    ): GameState {
        val virginId = trigger.actorId ?: return state
        // The ability is spent whichever way the storyteller rules.
        var next = Effects.place(
            state = state,
            target = virginId,
            kind = EffectKind.SPENT,
            sourceCharacterId = "virgin",
            sourcePlayerId = virginId,
            until = Until.FOREVER,
            label = TOKEN_NO_ABILITY,
            note = "The Virgin's first nomination has happened.",
        ).state
        next = Ledger.spent(next, "virgin", virginId)
        if (optionId == OPTION_REGISTERS_GOOD) {
            return Ledger.ruling(
                next,
                sourceId = "virgin",
                playerId = trigger.targetId,
                text = "The nominator did not register as a Townsfolk — nobody is executed.",
            )
        }
        val nominatorId = trigger.targetId ?: return next
        return Execution.execute(
            state = next,
            lookup = lookup,
            playerId = nominatorId,
            nominatorId = virginId,
            via = ExecutionVia.VIRGIN,
            force = true,
        )
    }

    private fun applyGolem(
        state: GameState,
        lookup: (String) -> Character?,
        trigger: NominationTrigger,
    ): GameState {
        val golemId = trigger.actorId ?: return state
        var next = Effects.place(
            state = state,
            target = golemId,
            kind = EffectKind.NO_NOMINATE,
            sourceCharacterId = "golem",
            sourcePlayerId = golemId,
            until = Until.FOREVER,
            label = TOKEN_MAY_NOT_NOMINATE,
            note = "The Golem's one nomination is used.",
        ).state
        if (trigger.kind == TriggerKind.AUTO_DEATH && trigger.targetId != null) {
            next = Deaths.attempt(
                next, lookup, trigger.targetId,
                KillCause(DeathCause.DAY_ABILITY, "golem", golemId),
            ).state
        }
        return next
    }

    private fun applyGoblinClaim(state: GameState, trigger: NominationTrigger): GameState {
        val claimant = trigger.actorId ?: return state
        val name = state.player(claimant)?.name ?: return state
        // Several players may claim Goblin on one day, so this token is never exclusive.
        var next = Effects.addReminder(state, claimant, PlacedReminder("goblin", TOKEN_CLAIMED, placedCycle = state.cycle))
        next = Ledger.statement(
            next,
            speakerId = claimant,
            sourceId = "goblin",
            text = "$name publicly claims to be the Goblin.",
        )
        return Ledger.announce(next, "$name claims to be the Goblin.", sourceId = "goblin", actorId = claimant)
    }

    /**
     * Records the nomination, freezing the vote rules and the registration
     * snapshot on it. Refuses an illegal one unless [force] — the ST always wins.
     */
    fun record(
        state: GameState,
        lookup: (String) -> Character?,
        nomination: Nomination,
        force: Boolean = false,
    ): GameState {
        val check = checkNomination(state, lookup, nomination.nominatorId, nomination.nomineeId)
        if (!check.legal && !force) return state

        val rules = nomination.voteRules ?: voteRules(state, lookup, nomination.isExile)
        val nominator = state.player(nomination.nominatorId)
        val demonIds = state.seats
            .filter { Team.DEMON in Registration.registersAs(state, lookup, it) }
            .map { it.id }
        // The weighted tally is only recomputed when raw hands were supplied:
        // a caller that passed a headcount straight through keeps it. It goes
        // through the same [countedVoters] filter as [tally], so a hand the vote
        // panel marked ineligible cannot reach the record either (C-1).
        val counted = countedVoters(state, lookup, nomination.voterIds, nomination.isExile, rules)
        val votes = if (nomination.voterIds.isNotEmpty()) {
            rules.tally(counted) + nomination.extraVotes.values.sum()
        } else {
            nomination.votes
        }
        val frozen = nomination.copy(
            day = if (nomination.day == 0) state.cycle else nomination.day,
            votes = votes,
            voteRules = rules,
            nominatorCharacterId = nomination.nominatorCharacterId ?: nominator?.characterId,
            nominatorTeams = nomination.nominatorTeams.ifEmpty {
                nominator?.let { Registration.registersAs(state, lookup, it) }.orEmpty()
            },
            demonIdsAtRecord = nomination.demonIdsAtRecord.ifEmpty { demonIds },
        )
        var next = recordNomination(state, frozen)
        // Only a hand that COUNTED spends anything: an ineligible hand costs the
        // seat no ghost vote and no Beggar token (C-1).
        if (!frozen.isExile && rules.spendsGhostVotes) {
            for (voter in counted) {
                val p = next.player(voter) ?: continue
                if (!p.alive && !p.ghostVoteUsed) {
                    next = next.updatePlayer(voter) { it.copy(ghostVoteUsed = true) }
                }
            }
        }
        // "You must use a vote token to vote." Only on an execution: the Beggar
        // supports exiles freely and spends nothing (lead D72).
        if (!frozen.isExile) {
            for (voter in counted) {
                val p = next.player(voter) ?: continue
                if (isBeggar(p) && p.voteTokens > 0) {
                    next = next.updatePlayer(voter) { it.copy(voteTokens = it.voteTokens - 1) }
                }
            }
        }
        return next
    }

    /** True for a seat whose TRUE character is the Beggar. */
    private fun isBeggar(player: Player): Boolean =
        player.characterId?.let(Character::normalizeId) == BEGGAR

    /**
     * Is [characterId] on THIS game's script? A caveat about a character the
     * script has never heard of is noise the storyteller has to read past
     * (playtest D, P2-15). Script membership, not in-play: a Goblin claim is a
     * claim, and the claimant need not hold the token.
     */
    private fun onScript(state: GameState, characterId: String): Boolean {
        val id = Character.normalizeId(characterId)
        return state.script.characterIds.any { Character.normalizeId(it) == id }
    }

    /**
     * "If a dead player gives you their vote token, you learn their alignment."
     *
     * One irreversible transfer: the donor's token moves to the Beggar's hoard
     * and the donor's ghost vote goes with it (a vote token given away is a vote
     * not cast). The `beggar/Token` reminder records WHO gave it —
     * `PlacedReminder.targetPlayerId` — so one token per gift is enough, and the
     * alignment the Beggar learns is written to the ledger as a private TOLD.
     *
     * Returns the state unchanged when the gift is not legal: only a DEAD, seated
     * donor with a token left may give, and only to a seated Beggar.
     */
    fun giveVoteToken(
        state: GameState,
        lookup: (String) -> Character?,
        donorId: Long,
        beggarId: Long,
    ): GameState {
        val donor = state.player(donorId) ?: return state
        val beggar = state.player(beggarId) ?: return state
        if (!donor.seated || !beggar.seated) return state
        if (donor.id == beggar.id) return state
        if (donor.alive || donor.voteTokens <= 0) return state
        if (!isBeggar(beggar)) return state

        val evil = Registration.registersEvil(state, lookup, donor)
        val alignment = if (evil) "evil" else "good"
        var next = state
            .updatePlayer(donorId) { it.copy(voteTokens = it.voteTokens - 1, ghostVoteUsed = true) }
            .updatePlayer(beggarId) { it.copy(voteTokens = it.voteTokens + 1) }
        next = Effects.addReminder(
            next,
            beggarId,
            PlacedReminder(
                sourceId = BEGGAR,
                label = TOKEN_BEGGAR,
                targetPlayerId = donorId,
                note = "${donor.name} gave their vote token — they are $alignment.",
                placedCycle = next.cycle,
            ),
        )
        return Ledger.record(
            next,
            LedgerEntry(
                kind = LedgerKind.TOLD,
                sourceId = BEGGAR,
                actorId = beggarId,
                targetIds = listOf(donorId),
                text = "${donor.name} gave ${beggar.name} (Beggar) their vote token.",
                shown = alignment,
            ),
        )
    }

    /** WP0 move of `GameActions.recordNomination` — appends with no checks. */
    fun recordNomination(state: GameState, nomination: Nomination): GameState =
        state.copy(nominations = state.nominations + nomination)

    // ---- voting ----

    /** Computes the snapshot to freeze on the Nomination. */
    @Suppress("CyclomaticComplexMethod")
    fun voteRules(state: GameState, lookup: (String) -> Character?, isExile: Boolean): VoteRules {
        val reasons = mutableListOf<String>()

        if (isExile) {
            // "Vote weights never apply to exiles, and no ghost vote is spent."
            return VoteRules(
                eligibleVoterIds = state.seats.map { it.id },
                threshold = Voting.exileThreshold(state.seats.size),
                spendsGhostVotes = false,
                weights = emptyMap(),
                reasons = listOf("Exile — abilities do not apply; every vote counts once."),
            )
        }

        val voudon = holderOfWithAbility(state, lookup, "voudon")
        val eligible: List<Long>
        val threshold: Int
        var spendsGhostVotes = true
        if (voudon != null) {
            eligible = (listOf(voudon.id) + state.seats.filterNot { it.alive }.map { it.id }).distinct()
            threshold = 1
            spendsGhostVotes = false
            reasons += "Voudon: only ${voudon.name} and the dead may vote, one vote is enough, " +
                "and no vote token is spent."
        } else {
            eligible = state.seats
                .filter { it.alive || !it.ghostVoteUsed }
                .map { it.id }
            threshold = Voting.executionThreshold(state.aliveCountWithTravellers)
        }

        val weights = mutableMapOf<Long, Int>()
        for (seat in state.seats) {
            var weight = 1
            if (liveToken(state, lookup, seat.id, "bureaucrat", TOKEN_THREE_VOTES)) {
                weight = 3
                reasons += "${seat.name}'s vote counts 3 times (Bureaucrat)."
            }
            if (liveToken(state, lookup, seat.id, "thief", TOKEN_NEGATIVE_VOTE)) {
                weight = -1
                reasons += "${seat.name}'s vote counts negatively (Thief)."
            }
            if (seat.characterId?.let(Character::normalizeId) == "banshee" &&
                hasToken(state, seat.id, "banshee", TOKEN_HAS_ABILITY)
            ) {
                weight = 2
                reasons += "${seat.name} (Banshee) may raise two hands."
            }
            // "You must use a vote token to vote." The Bureaucrat's ×3 and the
            // Thief's −1 still apply to the vote they do spend a token on — this
            // only zeroes the weight of a Beggar with an empty hoard (lead D72).
            if (isBeggar(seat) && seat.voteTokens <= 0) {
                weight = 0
                reasons += "${seat.name} (Beggar) has no vote token left to spend."
            }
            if (Status.live(state, lookup, seat.id, EffectKind.NO_VOTE).isNotEmpty()) {
                weight = 0
                reasons += "${seat.name} may not vote."
            }
            if (weight != 1) weights[seat.id] = weight
        }
        if (secretVoting(state, lookup)) {
            reasons += if (organGrinder(state, lookup) != null) {
                "Secret voting — the Organ Grinder is sober; close eyes for the tally."
            } else {
                "Secret voting — a house rule for this game; close eyes for the tally."
            }
        }
        return VoteRules(
            eligibleVoterIds = eligible,
            threshold = threshold,
            spendsGhostVotes = spendsGhostVotes,
            weights = weights,
            reasons = reasons.distinct(),
        )
    }

    /**
     * The weighted tally for [voterIds] — over [countedVoters], never over the
     * raw hands.
     */
    fun tally(
        state: GameState,
        lookup: (String) -> Character?,
        voterIds: List<Long>,
        isExile: Boolean,
    ): Int {
        val rules = voteRules(state, lookup, isExile)
        return rules.tally(countedVoters(state, lookup, voterIds, isExile, rules))
    }

    /**
     * The hands that actually COUNT, out of the raw hands that went up.
     *
     * **A hand the app itself labels "may not vote" can never move the tally**
     * (playtest C-1). Every raw hand is filtered against the same
     * [VoteRules.eligibleVoterIds] the vote panel renders its ⊘ from — a spent
     * ghost vote, a living player under a sober Voudon, an `EffectKind.NO_VOTE`
     * seat — so the tally, the outcome line, the Lock-in label and the recorded
     * `Nomination.votes` can never disagree with the reason printed next to the
     * chip.
     *
     * A Butler whose Master's hand is down is dropped on **every** day, not only
     * under secret voting (playtest C-3; wiki: *"you may only vote if they are
     * voting too"*). The Butler test runs against the already-eligible hands, so
     * a Master whose own hand does not count cannot license the Butler's.
     *
     * An exile counts every hand once: no ability applies to it at all.
     */
    fun countedVoters(
        state: GameState,
        lookup: (String) -> Character?,
        voterIds: List<Long>,
        isExile: Boolean,
        rules: VoteRules = voteRules(state, lookup, isExile),
    ): List<Long> {
        if (isExile) return voterIds
        val eligible = voterIds.filter { it in rules.eligibleVoterIds }
        return eligible.filterNot { butlerVotingIllegally(state, lookup, it, eligible) }
    }

    /** True when this seat is a Butler whose Master's hand is not up. */
    fun butlerVotingIllegally(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        voterIds: List<Long>,
    ): Boolean {
        val player = state.player(playerId) ?: return false
        if (player.characterId?.let(Character::normalizeId) != "butler") return false
        if (!Status.hasAbility(state, lookup, playerId)) return false
        val master = masterOf(state, playerId) ?: return false
        return master !in voterIds
    }

    /** The seat carrying this Butler's `Master` token, if one is placed. */
    fun masterOf(state: GameState, butlerId: Long): Long? {
        val key = Tokens.key("butler", TOKEN_MASTER)
        state.effects.firstOrNull {
            Tokens.key(it.sourceCharacterId, it.label) == key && it.sourcePlayerId == butlerId
        }?.let { return it.targetId }
        return state.seats.firstOrNull { seat ->
            seat.id != butlerId && seat.reminders.any { Tokens.key(it) == key }
        }?.id
    }

    /** Zealot seats that must have a hand up (5+ alive). */
    fun mustVote(state: GameState, lookup: (String) -> Character?): List<Long> {
        if (state.aliveCountWithTravellers < 5) return emptyList()
        // W7G: an IMPAIRED Zealot is still obliged. "If 5 or more players are
        // alive, you must vote" is not an ability that can be turned off — the
        // player does not know they are drunk, and a drunk Zealot who abstains
        // is a leak. Death ends it; poison does not.
        return state.alivePlayers
            .filter { seat ->
                Identity.actingRoles(state, lookup, seat).any { it.abilityId == "zealot" }
            }
            .map { it.id }
    }

    /**
     * Eyes-closed voting: the tally, the verdict and the block are hidden.
     *
     * Armed by a sober living Organ Grinder, and — because the Organ Grinder is
     * on no base script and some tables play this way anyway (ux/day-screen §F)
     * — by the `secretVotes` house rule. The two are ORed: turning the house
     * rule off never opens the eyes of a table that has an Organ Grinder in it.
     */
    fun secretVoting(state: GameState, lookup: (String) -> Character?): Boolean =
        state.houseRules.secretVotes || organGrinder(state, lookup) != null

    /** The seat whose working Organ Grinder ability is closing every eye, if any. */
    fun organGrinder(state: GameState, lookup: (String) -> Character?): Player? =
        holderOfWithAbility(state, lookup, "organgrinder")

    /** Legion: an execution fails if only evil players voted. */
    fun executionFailsOnlyEvilVoted(
        state: GameState,
        lookup: (String) -> Character?,
        voterIds: List<Long>,
    ): Boolean {
        if (voterIds.isEmpty()) return false
        val legion = state.alivePlayers.any {
            it.characterId?.let(Character::normalizeId) == "legion"
        }
        if (!legion) return false
        return voterIds.mapNotNull { state.player(it) }
            .all { Registration.registersEvil(state, lookup, it) }
    }

    // ---- derived day state (no stored flags) ----

    fun executionToday(state: GameState): ExecutionRecord? =
        state.executions.lastOrNull { it.day == state.cycle }

    /** True when the day's one execution has been spent. SURVIVED counts. */
    fun executionSpent(state: GameState): Boolean =
        state.executions.any { it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION }

    /**
     * Derived from the executions list — there is no stored day-closed boolean
     * (lead D30). A declared `NO_EXECUTION` closes the day just as an execution does.
     */
    fun nominationsClosed(state: GameState, lookup: (String) -> Character?): Boolean {
        val today = state.executions.filter { it.day == state.cycle }
        if (today.any { it.outcome == ExecutionOutcome.NO_EXECUTION }) return true
        if (today.isEmpty()) return false
        return !secondExecutionAllowed(state, lookup)
    }

    /** Why nominations are closed, in storyteller voice. Empty when they are open. */
    fun nominationsClosedReason(state: GameState, lookup: (String) -> Character?): String {
        if (!nominationsClosed(state, lookup)) return ""
        val today = executionToday(state) ?: return ""
        return when (today.outcome) {
            ExecutionOutcome.NO_EXECUTION -> "No execution today — the day is over."
            ExecutionOutcome.DIED -> {
                val who = today.diedInsteadId ?: today.playerId
                "${state.player(who ?: -2L)?.name ?: "Someone"} was executed — the day is over."
            }

            ExecutionOutcome.SURVIVED ->
                "${state.player(today.playerId ?: -2L)?.name ?: "Someone"} was executed and " +
                    "survived — the day is over."
        }
    }

    /** The Butcher exception: a second execution is legal today. */
    fun secondExecutionAllowed(state: GameState, lookup: (String) -> Character?): Boolean {
        val executions = state.executions.count {
            it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION
        }
        if (executions != 1) return false
        return state.alivePlayers.any {
            it.characterId?.let(Character::normalizeId) == "butcher" &&
                Status.hasAbility(state, lookup, it.id)
        }
    }

    /** The Vizier cannot die during the day, by any means. */
    fun immuneToDayDeath(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): Boolean = Status.live(state, lookup, playerId, EffectKind.DAY_IMMUNE).isNotEmpty()

    /** Living unimpaired Vizier holder, or null. */
    fun vizier(state: GameState, lookup: (String) -> Character?): Player? =
        holderOfWithAbility(state, lookup, "vizier")

    /** Living unimpaired Bishop holder, or null. */
    fun bishop(state: GameState, lookup: (String) -> Character?): Player? =
        holderOfWithAbility(state, lookup, "bishop")

    /** The Riot / Leviathan day counter from the grimoire-centre token, or 0. */
    fun riotDay(state: GameState): Int = countdownDay(state, "riot")

    /** The Leviathan's day counter from the grimoire-centre token, or 0. */
    fun leviathanDay(state: GameState): Int = countdownDay(state, "leviathan")

    private fun countdownDay(state: GameState, sourceId: String): Int {
        val id = Character.normalizeId(sourceId)
        val token = (state.storytellerReminders + state.players.flatMap { it.reminders })
            .lastOrNull { Character.normalizeId(it.sourceId) == id && it.label.startsWith("Day ", true) }
        return token?.label?.removePrefix("Day ")?.removePrefix("day ")?.trim()?.toIntOrNull() ?: 0
    }

    // ---- existing helpers, moved here from GameActions in WP0 ----

    /**
     * A Traveller the table voted out today who is still in the game.
     *
     * The exile itself is a separate, explicit tap ([Exile] on the nomination
     * row), and until playtest C2-3 nothing anywhere noticed when it was
     * missed: the strip said "No one is about to die.", the DUSK card said
     * there was no execution today, and the dusk sheet's BEFORE YOU MOVE ON —
     * the same section that warns about an un-executed block — was empty. The
     * night then ran with the traveller seated, holding their vote and counted
     * in the execution threshold.
     *
     * NOT scoped to today, unlike [aboutToDie]: a passing exile vote *is* the
     * exile by the rules, so the obligation outlives the day it was taken on
     * and the warning keeps standing until the seat leaves. Withdrawing the
     * nomination is the way out for a vote the storyteller ruled did not count.
     */
    fun exileOwed(state: GameState): Long? = state.nominations
        .lastOrNull {
            it.isExile &&
                it.result == NominationResult.ABOUT_TO_DIE &&
                state.player(it.nomineeId)?.alive == true
        }
        ?.nomineeId

    /**
     * How many votes the NEXT nomination must reach to beat today's standing
     * high — the one meaning of "to beat" anywhere in the app.
     *
     * The day screen had two: the stat strip appended "· 5 to beat" (the
     * standing high-water) while the tie line one row below said "6 to beat it"
     * (the number a vote must reach). Same words, different numbers, one line
     * apart on screen (playtest C2-10). Zero when nothing is standing.
     */
    fun votesToBeat(state: GameState): Int =
        highestVotesToday(state).takeIf { it > 0 }?.plus(1) ?: 0

    /** Highest passing vote tally so far today (for tie/beat logic). */
    fun highestVotesToday(state: GameState): Int =
        state.nominations
            .filter { it.day == state.cycle && !it.isExile }
            .filter { it.result == NominationResult.ABOUT_TO_DIE || it.result == NominationResult.TIED }
            .maxOfOrNull { it.votes } ?: 0

    /** Players a nominator hasn't yet nominated today, per one-nomination rules. */
    fun hasNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nominatorId == playerId && !it.isExile }

    fun hasBeenNominatedToday(state: GameState, playerId: Long): Boolean =
        state.nominations.any { it.day == state.cycle && it.nomineeId == playerId && !it.isExile }

    /**
     * Who is currently on the block today, derived from the nomination
     * sequence: a passing tally that beats the previous highest puts its
     * nominee on the block; a later equal tally clears the block (tie).
     */
    fun aboutToDie(state: GameState): Long? {
        var onBlock: Long? = null
        for (n in state.nominations.filter { it.day == state.cycle && !it.isExile }) {
            when (n.result) {
                NominationResult.ABOUT_TO_DIE -> onBlock = n.nomineeId
                NominationResult.TIED -> onBlock = null
                else -> Unit
            }
        }
        return onBlock
    }

    // ---- shared helpers ----

    /**
     * True when this seat carries `(sourceId, label)` — as a storyteller-placed
     * free token or as an effect-backed one. Always compared through
     * [Tokens.key]; never by `==` and never by substring (lead D5).
     */
    /**
     * True when the token `(sourceId, label)` is PHYSICALLY on this seat —
     * a storyteller reminder or a stored effect, suspended ones excluded.
     *
     * Deliberately NOT a rules question. A Vigormortis's `Has Ability` marker
     * has to stay findable at the exact moment the Vigormortis dies, so the
     * teardown can name the seats it is coming off. Use [liveToken] when the
     * question is "does this token still DO anything".
     */
    internal fun hasToken(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val player = state.player(playerId) ?: return false
        if (player.reminders.any { Tokens.key(it) == key }) return true
        return state.effects.any {
            it.targetId == playerId &&
                !it.suspended &&
                Tokens.key(it.sourceCharacterId, it.label) == key
        }
    }

    /**
     * True when the token `(sourceId, label)` is on this seat AND in force (W7G).
     *
     * The vote weights are the case that needs it: a Bureaucrat's "3 Votes" and
     * a Thief's "Negative Vote" end with their source, so killing or poisoning
     * the Bureaucrat takes the weight with it — which reading `state.effects`
     * raw could never see. A hand-placed reminder has no source to lose and
     * always counts.
     */
    internal fun liveToken(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        sourceId: String,
        label: String,
    ): Boolean {
        val key = Tokens.key(sourceId, label)
        val player = state.player(playerId) ?: return false
        if (player.reminders.any { Tokens.key(it) == key }) return true
        return Status.live(state, lookup, playerId)
            .any { Tokens.key(it.sourceCharacterId, it.label) == key }
    }

    /** The seat holding [characterId], alive or dead. Null when nobody does. */
    internal fun holderOf(state: GameState, characterId: String): Player? {
        val id = Character.normalizeId(characterId)
        return state.seats.firstOrNull { it.characterId?.let(Character::normalizeId) == id }
    }

    /** The living holder of [characterId] whose ability is working, if any. */
    internal fun holderOfWithAbility(
        state: GameState,
        lookup: (String) -> Character?,
        characterId: String,
    ): Player? {
        val id = Character.normalizeId(characterId)
        return state.alivePlayers.firstOrNull {
            it.characterId?.let(Character::normalizeId) == id &&
                Status.hasAbility(state, lookup, it.id)
        }
    }
}

/** One row of the Day tab's abilities strip, resolved for a concrete seat. */
data class OfferedDayAbility(
    val ability: DayAbility,
    /** The seat that would use it. Null for a Fabled — the grimoire holds it. */
    val holderId: Long?,
    /** "Sarah", or the Fabled's own name. */
    val holderName: String,
    val sourceId: String,
    /** False = draw it greyed with [reason], never remove it (lead D37). */
    val available: Boolean,
    val reason: String = "",
)

/**
 * The Day tab's abilities strip, from `CharacterRule.day.ability` (W7G).
 *
 * The slot had no consumer at all before wave 7: the Slayer's shot, the Artist's
 * question, the Gossip's statement, the Damsel's guess and the Gangster's kill
 * were all declared and no screen could find them.
 *
 * Every offer is RETURNED, available or not, so the strip can grey a spent
 * ability with its reason rather than silently dropping it (lead D37).
 */
object DayAbilities {

    fun forState(state: GameState, lookup: (String) -> Character?): List<OfferedDayAbility> =
        buildList {
            for (seat in state.seats) {
                val id = seat.characterId?.let(Character::normalizeId) ?: continue
                val ability = CharacterRules.all[id]?.day?.ability ?: continue
                val ok = ability.available(state, lookup, seat)
                add(
                    OfferedDayAbility(
                        ability = ability,
                        holderId = seat.id,
                        holderName = seat.name,
                        sourceId = id,
                        available = ok,
                        reason = if (ok) "" else unavailableReason(state, lookup, seat),
                    ),
                )
            }
            for (rule in CharacterRules.fabledRows(state)) {
                val ability = rule.day?.ability ?: continue
                add(
                    OfferedDayAbility(
                        ability = ability,
                        holderId = null,
                        holderName = lookup(rule.id)?.name ?: rule.id,
                        sourceId = Character.normalizeId(rule.id),
                        available = ability.available(
                            state,
                            lookup,
                            CharacterRules.GRIMOIRE_HOLDER,
                        ),
                    ),
                )
            }
        }

    /** Only ones the storyteller can actually tap right now. */
    fun availableIn(state: GameState, lookup: (String) -> Character?): List<OfferedDayAbility> =
        forState(state, lookup).filter { it.available }

    /**
     * Records ONE use of a day ability: bumps its `counterKey` (lead D72) and
     * appends the ledger entry its `recordsAs` names.
     *
     * This is what makes "for each time you said it publicly today" countable —
     * the Yaggababble's night row reads the tally back and zeroes it. Returns
     * the state unchanged when the ability is not on offer right now, so the
     * spent / dead / impaired cases need no caller-side check.
     */
    fun use(
        state: GameState,
        lookup: (String) -> Character?,
        sourceId: String,
        holderId: Long? = null,
        text: String = "",
        kind: LedgerKind = LedgerKind.STATEMENT,
    ): GameState {
        val id = Character.normalizeId(sourceId)
        val offer = forState(state, lookup)
            .firstOrNull { it.sourceId == id && (holderId == null || it.holderId == holderId) }
            ?: return state
        if (!offer.available) return state
        val bumped = if (offer.ability.counterKey.isEmpty()) {
            state
        } else {
            Counters.bump(state, offer.ability.counterKey)
        }
        return Ledger.record(
            bumped,
            LedgerEntry(
                kind = kind,
                sourceId = offer.ability.recordsAs.ifEmpty { id },
                actorId = offer.holderId,
                text = text.ifEmpty { "${offer.holderName}: ${offer.ability.label}" },
            ),
        )
    }

    private fun unavailableReason(
        state: GameState,
        lookup: (String) -> Character?,
        seat: Player,
    ): String = when {
        !seat.alive -> "${seat.name} is dead."
        !Status.hasAbility(state, lookup, seat.id) -> "${seat.name}'s ability is not working."
        else -> "Already used."
    }
}
