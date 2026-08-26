package com.clocktower.engine.rules

import com.clocktower.engine.BriefingContext
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.CardOffer
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.Identity
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.NightContext
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.NominationContext
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.Registration
import com.clocktower.engine.ShowCardSpec
import com.clocktower.engine.Status
import com.clocktower.engine.StepGate
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.Team
import com.clocktower.engine.TokenRule
import com.clocktower.engine.Tokens
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.TriggerOption
import com.clocktower.engine.TriggerResult
import com.clocktower.engine.Until
import com.clocktower.engine.WakeContext
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate

// ---------------------------------------------------------------------------
// Character ids, spelled once
// ---------------------------------------------------------------------------

private const val ARTIST = "artist"
private const val BARBER = "barber"
private const val CERENOVUS = "cerenovus"
private const val CLOCKMAKER = "clockmaker"
private const val DREAMER = "dreamer"
private const val EVIL_TWIN = "eviltwin"
private const val FANG_GU = "fanggu"
private const val FLOWERGIRL = "flowergirl"
private const val JUGGLER = "juggler"
private const val KLUTZ = "klutz"
private const val MATHEMATICIAN = "mathematician"
private const val MUTANT = "mutant"
private const val NO_DASHII = "nodashii"
private const val ORACLE = "oracle"
private const val PHILOSOPHER = "philosopher"
private const val PIT_HAG = "pithag"
private const val SAGE = "sage"
private const val SAVANT = "savant"
private const val SEAMSTRESS = "seamstress"
private const val SNAKE_CHARMER = "snakecharmer"
private const val SWEETHEART = "sweetheart"
private const val TOWN_CRIER = "towncrier"
private const val VIGORMORTIS = "vigormortis"
private const val VORTOX = "vortox"
private const val WITCH = "witch"

// ---- official Title Case labels (lead D5); never compared by substring ----

private const val ABNORMAL = "Abnormal"
private const val CORRECT = "Correct"
private const val CURSED = "Cursed"
private const val DEAD = "Dead"
private const val DEMON_NOT_VOTED = "Demon Not Voted"
private const val DEMON_VOTED = "Demon Voted"
private const val DRUNK = "Drunk"
private const val HAIRCUTS_TONIGHT = "Haircuts Tonight"
private const val HAS_ABILITY = "Has Ability"
private const val IS_THE_PHILOSOPHER = "Is The Philosopher"
private const val MAD = "Mad"
private const val MINION_NOMINATED = "Minion Nominated"
private const val MINIONS_NOT_NOMINATED = "Minions Not Nominated"
private const val NO_ABILITY = "No Ability"
private const val ONCE = "Once"
private const val POISONED = "Poisoned"
private const val TWIN = "Twin"

/** The Demon's own attack, in both the current and the legacy spelling. */
@Suppress("DEPRECATION")
private val DEMON_DEATHS: Set<DeathCause> = setOf(DeathCause.DEMON_KILL, DeathCause.DEMON)

// ---------------------------------------------------------------------------
// Shared shapes
// ---------------------------------------------------------------------------

/** "Choose a player: they die." Every SV Demon's other-night step. */
private fun demonAttack(
    sourceId: String,
    noneLabel: String = "No kill (impaired, protected, or the storyteller's choice)",
    onResolve: List<NightEffect> = emptyList(),
    onNone: List<NightEffect> = emptyList(),
) = ChoosePlayers(
    sourceId = sourceId,
    prompt = "WHO DID THEY CHOOSE?",
    min = 1,
    max = 1,
    constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.SELF_ALLOWED),
    sort = TargetSort.ALIVE_FIRST,
    allowNone = true,
    noneLabel = noneLabel,
    perTarget = listOf(NightEffect.Attack(Ref.Target, DeathCause.DEMON_KILL)),
    onResolve = onResolve,
    onNone = onNone,
)

/** The gate every SV Demon shares: alive, and REDUCED rather than skipped when silenced. */
private val demonGate: WakePredicate = Gates.all(Gates.aliveHolder, Gates.notExorcised)

/** A pure information step: `InfoCalc` builds the picker and the answer. */
private fun infoStep(id: String, prompt: String, gate: WakePredicate = Gates.aliveHolder) =
    NightRule(gate = gate, prompt = prompt, infoId = id)

private fun seatsHolding(state: GameState, sourceId: String, label: String): List<Player> =
    state.seats.filter { DayRules.hasToken(state, it.id, sourceId, label) }

private fun aliveDemons(state: GameState, lookup: (String) -> Character?): List<Player> =
    state.alivePlayers.filter { it.characterId?.let(lookup)?.team == Team.DEMON }

private fun aliveMinions(state: GameState, lookup: (String) -> Character?): List<Player> =
    state.alivePlayers.filter { it.characterId?.let(lookup)?.team == Team.MINION }

private fun names(players: List<Player>): String = players.joinToString { it.name }

/** Choices this seat has recorded for [sourceId] that actually named somebody. */
private fun choicesMade(state: GameState, sourceId: String, holderId: Long): Int =
    state.ledger.count {
        it.kind == LedgerKind.CHOICE &&
            Character.normalizeId(it.sourceId) == sourceId &&
            it.actorId == holderId &&
            it.targetIds.isNotEmpty()
    }

/** At least [n] living residents, or a readable skip. */
private fun atLeastAlive(n: Int, reason: String): WakePredicate = WakePredicate { ctx ->
    val alive = ctx.state.aliveCountResidents
    if (alive >= n) StepGate.Fire else StepGate.Skip("only $alive players live — $reason")
}

// ---------------------------------------------------------------------------
// Death-trigger plumbing
// ---------------------------------------------------------------------------

/** True when [event] is this seat's own death, as this character, with the ability working. */
private fun diedAsSelf(event: DeathEvent, holder: Player, characterId: String): Boolean =
    event.playerId == holder.id &&
        !event.registeredOnly &&
        Character.normalizeId(event.characterIdAtDeath.orEmpty()) == characterId

/** Lead D35: a death-triggered ability needs the holder sober at the trigger moment. */
private fun workedAtDeath(event: DeathEvent): Boolean = event.abilityImpairedAtDeath != true

private fun prompt(
    at: BriefingSlot,
    kind: PromptKind,
    sourceId: String,
    subject: Long,
    title: String,
    detail: String = "",
) = Prompt(
    id = 0,
    at = at,
    kind = kind,
    sourceId = sourceId,
    subjectPlayerId = subject,
    title = title,
    detail = detail,
)

/** A token placed by an on-death trigger. The funnel stamps `id` (lead D64). */
private fun deathToken(
    holder: Player,
    state: GameState,
    sourceId: String,
    label: String,
    kind: EffectKind = EffectKind.MARKER,
    until: Until = Until.DAWN,
    note: String = "",
) = Effect(
    id = 0,
    kind = kind,
    targetId = holder.id,
    sourceCharacterId = sourceId,
    sourcePlayerId = holder.id,
    until = until,
    label = label,
    note = note,
    createdCycle = state.cycle,
    createdAtNight = state.phase != Phase.DAY,
)

// ---------------------------------------------------------------------------
// Per-character gates that read more than the shared predicates can
// ---------------------------------------------------------------------------

/**
 * Sage: only on the night the DEMON killed them. A Pit-Hag, Assassin or Gossip
 * death is not a Demon kill and the Sage does not wake (wiki example 3).
 */
private val sageKilledByTheDemon: WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Sage seat")
    val death = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.day == ctx.night && it.atNight && it.resurrectedAtCycle == null
    }
    when {
        death == null ->
            StepGate.Skip("they are alive — the Sage only wakes on the night the Demon kills them")

        death.cause !in DEMON_DEATHS -> StepGate.Skip(
            "they died to " + (death.killerCharacterId.ifEmpty { "another ability" }) +
                ", not to the Demon — the Sage does not wake",
        )

        else -> StepGate.Fire
    }
}

/**
 * Juggler: the reveal exists only when a genuine, unresolved guess was recorded
 * on the day that has just ended — never "every night from night 2".
 */
private val juggledYesterday: WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    val guessed = ctx.state.ledger.any {
        it.kind == LedgerKind.STATEMENT &&
            Character.normalizeId(it.sourceId) == JUGGLER &&
            it.actorId == holder.id &&
            it.genuine &&
            it.cycle == ctx.night - 1 &&
            it.resolvedCycle == null
    }
    if (guessed) {
        StepGate.Fire
    } else {
        StepGate.Skip("no guesses were recorded yesterday — the Juggler learns nothing")
    }
}

/**
 * Sweetheart: armed by a death this seat has not yet paid for. Counting deaths
 * against recorded choices makes the resurrected-and-killed-again case fall out
 * (both drunks persist) without a marker the official data does not have.
 */
private fun sweetheartOwes(state: GameState, holder: Player): Int {
    val deaths = state.deaths.count {
        diedAsSelf(it, holder, SWEETHEART) && it.resurrectedAtCycle == null && workedAtDeath(it)
    }
    return deaths - choicesMade(state, SWEETHEART, holder.id)
}

private val sweetheartOwesADrunk: WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Sweetheart seat")
    when {
        holder.alive -> StepGate.Skip("the Sweetheart is alive — nobody becomes drunk yet")
        sweetheartOwes(ctx.state, holder) > 0 -> StepGate.Fire
        else -> StepGate.Skip("their drunk has already been chosen")
    }
}

/**
 * Barber: the Demon's haircut, armed by the official token. The acting player is
 * the Demon, but the row belongs to the (dead) Barber's seat, which is where the
 * token sits.
 */
private val barberArmed: WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Barber seat")
    val demons = aliveDemons(ctx.state, ctx.lookup)
    when {
        !DayRules.hasToken(ctx.state, holder.id, BARBER, HAIRCUTS_TONIGHT) ->
            StepGate.Skip("the Barber has not died today or tonight")

        demons.isEmpty() -> StepGate.Skip("no living Demon to offer the swap to")
        else -> silencedDemonGate(ctx, demons)
    }
}

/** An Exorcised Demon does not choose tonight — and the haircut is the choosing half. */
private fun silencedDemonGate(ctx: WakeContext, demons: List<Player>): StepGate {
    val silenced = demons.all {
        Status.live(ctx.state, ctx.lookup, it.id, EffectKind.DEMON_CANNOT_KILL).isNotEmpty()
    }
    return if (silenced) {
        StepGate.Skip("the Demon has been silenced tonight — no haircut")
    } else {
        StepGate.Fire
    }
}

/**
 * Philosopher: they wake every night until they use it. The gate reads the SPENT
 * mark (`Is The Philosopher`, lead D49) and, as a belt, the recorded gain — a
 * head-shake records a choice with no character and does NOT spend the ability.
 */
private val philosopherUnspent: WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    val gained = ctx.state.ledger.any {
        it.kind == LedgerKind.CHOICE &&
            Character.normalizeId(it.sourceId) == PHILOSOPHER &&
            it.actorId == holder.id &&
            it.characterIds.any { id -> id.isNotBlank() }
    }
    if (gained) {
        StepGate.Skip("they have already gained an ability — once per game")
    } else {
        StepGate.Fire
    }
}

/** Fang Gu: the jump is once per game and its record is a grimoire-centre token. */
private fun fangGuJumpSpent(state: GameState): Boolean =
    state.storytellerReminders.any { Tokens.key(it) == Tokens.key(FANG_GU, ONCE) } ||
        state.seats.any { DayRules.hasToken(state, it.id, FANG_GU, ONCE) } ||
        state.identityLog.any { it.reason == ChangeReason.FANG_GU_JUMP }

// ---------------------------------------------------------------------------
// The registry
// ---------------------------------------------------------------------------

/**
 * Sects & Violets behaviour (WP7-SV): 13 Townsfolk, 4 Outsiders, 4 Minions,
 * 4 Demons. Travellers are WP7-TRAV's.
 *
 * Conventions, all from ARCHITECTURE §7 and lead D5/D31/D49/D52:
 *  - official Title Case labels, `copies` matching `characters.json` exactly;
 *  - kills only through `NightEffect.Attack`;
 *  - once-per-game through `Character.spentLabel` + `Gates.notSpent()`;
 *  - anything the engine may not decide is a `Prompt`, never a silent guess.
 */
internal val SV_RULES: List<CharacterRule> = listOf(
    clockmaker(),
    dreamer(),
    snakeCharmer(),
    mathematician(),
    flowergirl(),
    townCrier(),
    oracle(),
    savant(),
    seamstress(),
    philosopher(),
    artist(),
    juggler(),
    sage(),
    mutant(),
    sweetheart(),
    barber(),
    klutz(),
    evilTwin(),
    witch(),
    cerenovus(),
    pitHag(),
    fangGu(),
    vigormortis(),
    noDashii(),
    vortox(),
)

// ---------------------------------------------------------------------------
// Townsfolk
// ---------------------------------------------------------------------------

/** "You start knowing how many steps from the Demon to its nearest Minion." */
private fun clockmaker() = CharacterRule(
    id = CLOCKMAKER,
    firstNight = infoStep(
        CLOCKMAKER,
        "Show the hand signal for the number of places from the Demon to the closest Minion. " +
            "Never show 0.",
    ),
)

/** "Each night, choose a player: you learn 1 good & 1 evil character, 1 of which is correct." */
private fun dreamer(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "They point at a player. Show 1 Townsfolk/Outsider token and 1 Minion/Demon " +
            "token; one of them is that player's character.",
        action = {
            ChoosePlayers(
                sourceId = DREAMER,
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.NOT_SELF,
                    TargetConstraint.NOT_TRAVELLER,
                ),
                sort = TargetSort.ALIVE_FIRST,
                onResolve = listOf(NightEffect.RecordChoice()),
            )
        },
        infoId = DREAMER,
    )
    return CharacterRule(id = DREAMER, firstNight = rule, otherNight = rule)
}

/**
 * "Each night, choose an alive player: a chosen Demon swaps characters &
 * alignments with you & is then poisoned."
 *
 * The swap is a `Prompt`: `NightEffect` cannot branch on the target's team, and
 * `SwapCharacters` keeps each seat's own alignment (Barber semantics), which is
 * the opposite of what this card asks for. See the WP2 notes in the final report.
 */
private fun snakeCharmer(): CharacterRule {
    fun rule() = NightRule(
        gate = Gates.aliveHolder,
        prompt = "They point at a player. Only a Demon does anything: swap their characters " +
            "AND alignments, then poison the new Snake Charmer. Seat order — do not hint.",
        action = { ctx ->
            val holder = ctx.holder
            val works = holder != null && Status.hasAbility(ctx.state, ctx.lookup, holder.id)
            val demons = aliveDemons(ctx.state, ctx.lookup)
            ChoosePlayers(
                sourceId = SNAKE_CHARMER,
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                // "choose an ALIVE player" — the ability text wins over the run-book.
                constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.SEAT_ORDER,
                allowNone = true,
                noneLabel = "They chose nobody",
                onResolve = if (!works || demons.isEmpty()) {
                    listOf(NightEffect.RecordChoice())
                } else {
                    listOf(
                        NightEffect.RecordChoice(),
                        NightEffect.QueuePrompt(
                            at = BriefingSlot.NOW,
                            kind = PromptKind.DECIDE,
                            sourceId = SNAKE_CHARMER,
                            title = "Snake Charmer: if they pointed at the Demon " +
                                "(${names(demons)}), swap their characters AND alignments, " +
                                "then poison the new Snake Charmer.",
                            on = Ref.Target,
                        ),
                    )
                },
            )
        },
    )
    return CharacterRule(
        id = SNAKE_CHARMER,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(
            // Per victim and permanent: it outlives nothing, and a second charm
            // must never cure the first victim.
            TokenRule(SNAKE_CHARMER, POISONED, EffectKind.POISONED, Until.FOREVER, impairs = true),
        ),
    )
}

/** "Each night, you learn how many players' abilities worked abnormally (since dawn)." */
private fun mathematician(): CharacterRule {
    fun rule() = infoStep(
        MATHEMATICIAN,
        "Show the hand signal for the number of PLAYERS whose ability malfunctioned since dawn " +
            "because of another character's ability. Never count the Mathematician themself.",
    )
    return CharacterRule(
        id = MATHEMATICIAN,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(TokenRule(MATHEMATICIAN, ABNORMAL, null, Until.DAWN, copies = 5)),
    )
}

/** "Each night*, you learn if a Demon voted today." */
private fun flowergirl() = CharacterRule(
    id = FLOWERGIRL,
    otherNight = infoStep(
        FLOWERGIRL,
        "Nod yes or shake your head no for whether a Demon voted today. The answer comes from " +
            "the recorded votes, snapshotted at the moment of the vote.",
    ),
    // Two-state pair, lead D52: at dawn "Demon Voted" resets to "Demon Not Voted",
    // and the two may never sit on one seat.
    tokens = listOf(
        TokenRule(
            FLOWERGIRL, DEMON_VOTED, null, Until.DAWN,
            countdownNext = DEMON_NOT_VOTED, mutexGroup = "flowergirl.vote",
        ),
        TokenRule(FLOWERGIRL, DEMON_NOT_VOTED, null, Until.FOREVER, mutexGroup = "flowergirl.vote"),
    ),
    day = DayRule(
        briefing = { ctx ->
            if (ctx.slot != BriefingSlot.DAY_START || !ctx.holder.alive) {
                emptyList()
            } else {
                listOf(
                    BriefingItem(
                        key = "flowergirl.watch",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.INFO,
                        sourceId = FLOWERGIRL,
                        playerId = ctx.holder.id,
                        text = "Flowergirl (${ctx.holder.name}) — note whether a Demon votes today.",
                    ),
                )
            }
        },
    ),
)

/** "Each night*, you learn if a Minion nominated today." */
private fun townCrier() = CharacterRule(
    id = TOWN_CRIER,
    otherNight = infoStep(
        TOWN_CRIER,
        "Nod yes or shake your head no for whether a Minion nominated today. Exiles never count.",
    ),
    tokens = listOf(
        TokenRule(
            TOWN_CRIER, MINION_NOMINATED, null, Until.DAWN,
            countdownNext = MINIONS_NOT_NOMINATED, mutexGroup = "towncrier.nomination",
        ),
        TokenRule(
            TOWN_CRIER, MINIONS_NOT_NOMINATED, null, Until.FOREVER,
            mutexGroup = "towncrier.nomination",
        ),
    ),
    day = DayRule(
        // The answer is derived from the nomination records; this row only makes
        // sure the storyteller notices, during the day, that tonight's answer
        // just changed — and asks for the misregistration ruling while it is cheap.
        onNomination = { ctx ->
            val nominator = ctx.nominatorId?.let { ctx.state.player(it) }
            val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
            when {
                !ctx.holder.alive || nominator == null -> emptyList()
                nominee?.isTraveller == true -> emptyList() // an exile is not a nomination
                else -> {
                    val teams = Registration.registersAs(ctx.state, ctx.lookup, nominator)
                    val certainly = nominator.characterId?.let(ctx.lookup)?.team == Team.MINION
                    if (Team.MINION !in teams) {
                        emptyList()
                    } else {
                        listOf(
                            NominationTrigger(
                                kind = TriggerKind.WARN,
                                sourceId = TOWN_CRIER,
                                actorId = ctx.holder.id,
                                headline = if (certainly) {
                                    "Town Crier: ${nominator.name} is a Minion — " +
                                        "tonight's answer is YES."
                                } else {
                                    "Town Crier: decide now whether ${nominator.name} " +
                                        "registers as a Minion."
                                },
                                detail = "Place the Minion Nominated marker on " +
                                    "${ctx.holder.name}'s seat.",
                                impaired = !Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id),
                            ),
                        )
                    }
                }
            }
        },
    ),
)

/** "Each night*, you learn how many dead players are evil." */
private fun oracle() = CharacterRule(
    id = ORACLE,
    otherNight = infoStep(
        ORACLE,
        "Show the hand signal for the number of dead EVIL players. Never a number larger than " +
            "the number of corpses.",
    ),
)

/** "Each day, you may visit the Storyteller to learn 2 things: 1 is true & 1 is false." */
private fun savant() = CharacterRule(
    id = SAVANT,
    // Correctly on neither night order list: the Savant initiates, during the day.
    day = DayRule(
        ability = DayAbility(
            label = "Savant visit",
            oncePerDay = true,
            recordsAs = SAVANT,
            available = { state, lookup, holder ->
                holder.alive &&
                    Identity.actingRoles(state, lookup, holder).any { it.abilityId == SAVANT } &&
                    state.ledger.none {
                        it.kind == LedgerKind.PRIVATE &&
                            Character.normalizeId(it.sourceId) == SAVANT &&
                            it.actorId == holder.id &&
                            it.cycle == state.cycle
                    }
            },
        ),
        briefing = { ctx -> savantBriefing(ctx) },
    ),
)

private fun savantBriefing(ctx: BriefingContext): List<BriefingItem> {
    if (ctx.slot != BriefingSlot.DAY_START || !ctx.holder.alive) return emptyList()
    val impaired = Status.isImpaired(ctx.state, ctx.lookup, ctx.holder.id)
    return listOf(
        BriefingItem(
            key = "savant.visit.${ctx.holder.id}",
            kind = BriefingKind.TODO_ASK,
            severity = BriefingSeverity.ACTION,
            sourceId = SAVANT,
            playerId = ctx.holder.id,
            text = if (impaired) {
                "Savant (${ctx.holder.name}) may visit — they are drunk or poisoned, so give " +
                    "TWO true or TWO false statements."
            } else {
                "Savant (${ctx.holder.name}) may visit you today — one true statement and " +
                    "one false one."
            },
            actionId = "savant.visit",
        ),
    )
}

/** "Once per game, at night, choose 2 players: you learn if they are the same alignment." */
private fun seamstress(): CharacterRule {
    fun rule() = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "They shake their head, or point at two players other than themselves. " +
            "If they chose, the ability is spent — even when the answer is false.",
        action = {
            ChoosePlayers(
                sourceId = SEAMSTRESS,
                prompt = "WHICH TWO DID THEY CHOOSE?",
                min = 2,
                max = 2,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.NOT_SELF),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They passed — keep the ability",
                onResolve = listOf(
                    NightEffect.RecordChoice(),
                    NightEffect.MarkSpent(SEAMSTRESS),
                ),
                onNone = listOf(NightEffect.RecordChoice()),
            )
        },
        infoId = SEAMSTRESS,
    )
    return CharacterRule(
        id = SEAMSTRESS,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(TokenRule(SEAMSTRESS, NO_ABILITY, EffectKind.SPENT, Until.FOREVER)),
    )
}

/**
 * "Once per game, at night, choose a good character: gain that ability. If this
 * character is in play, they are drunk."
 *
 * The gain itself is an `AbilityGrant` (WP4) and no `NightEffect` creates one, so
 * the step records the choice and raises the obligation. The duplicate's
 * drunkenness is `philosopher/Drunk`, which outlives nothing and ends the moment
 * the Philosopher dies or is impaired.
 */
private fun philosopher(): CharacterRule {
    fun rule() = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent(), philosopherUnspent),
        prompt = "They shake their head, or point at a good character on their sheet. " +
            "Swap the token in ONLY if that character is not in play; if it IS in play, " +
            "make that player drunk instead.",
        action = { ctx ->
            ChooseCharacter(
                sourceId = PHILOSOPHER,
                prompt = "WHICH CHARACTER DID THEY POINT AT?",
                pool = CharacterPool.GOOD,
                allowNone = true,
                onResolve = listOf(
                    NightEffect.RecordChoice(),
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.NOW,
                        kind = PromptKind.CHOOSE_CHARACTER,
                        sourceId = PHILOSOPHER,
                        title = "Philosopher: grant ${ctx.holder?.name ?: "them"} the chosen " +
                            "ability, add the Is The Philosopher reminder, and make any " +
                            "in-play copy of that character drunk.",
                        on = Ref.Source,
                    ),
                ),
            )
        },
    )
    return CharacterRule(
        id = PHILOSOPHER,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(
            TokenRule(PHILOSOPHER, DRUNK, EffectKind.DRUNK, Until.FOREVER, impairs = true),
            TokenRule(PHILOSOPHER, IS_THE_PHILOSOPHER, EffectKind.SPENT, Until.FOREVER),
        ),
    )
}

/** "Once per game, during the day, privately ask the Storyteller any yes/no question." */
private fun artist() = CharacterRule(
    id = ARTIST,
    // No step, ever — correctly absent from both order lists.
    tokens = listOf(TokenRule(ARTIST, NO_ABILITY, EffectKind.SPENT, Until.FOREVER)),
    day = DayRule(
        ability = DayAbility(
            label = "Artist question",
            oncePerGame = true,
            recordsAs = ARTIST,
            available = { state, lookup, holder ->
                holder.alive &&
                    Identity.actingRoles(state, lookup, holder).any { it.abilityId == ARTIST } &&
                    Status.live(state, lookup, holder.id, EffectKind.SPENT).none {
                        Character.normalizeId(it.sourceCharacterId) == ARTIST
                    }
            },
        ),
    ),
)

/** "On your 1st day, publicly guess up to 5 players' characters. That night, you learn how many." */
private fun juggler() = CharacterRule(
    id = JUGGLER,
    otherNight = infoStep(
        JUGGLER,
        "Show the hand signal for the number of Correct markers, then remove them. If they were " +
            "drunk or poisoned when they guessed but are healthy now, give the TRUE number.",
        gate = Gates.all(Gates.aliveHolder, juggledYesterday),
    ),
    tokens = listOf(TokenRule(JUGGLER, CORRECT, null, Until.DAWN, copies = 5)),
    day = DayRule(
        ability = DayAbility(
            label = "Juggler guesses",
            oncePerGame = true,
            recordsAs = JUGGLER,
            available = { state, lookup, holder ->
                holder.alive &&
                    Identity.actingRoles(state, lookup, holder).any { it.abilityId == JUGGLER } &&
                    state.ledger.none {
                        it.kind == LedgerKind.STATEMENT &&
                            Character.normalizeId(it.sourceId) == JUGGLER &&
                            it.actorId == holder.id &&
                            it.genuine
                    }
            },
        ),
    ),
)

/** "If the Demon kills you, you learn that it is 1 of 2 players." */
private fun sage() = CharacterRule(
    id = SAGE,
    // Dead is the PRECONDITION here, never a reason to skip.
    actsWhileDead = true,
    otherNight = NightRule(
        gate = Gates.all(sageKilledByTheDemon, Gates.notSpent()),
        prompt = "The Demon killed them — WAKE THEM. Point at two players; one must be the " +
            "Demon that killed them.",
        action = {
            ChoosePlayers(
                sourceId = SAGE,
                prompt = "POINT AT TWO PLAYERS",
                min = 2,
                max = 2,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.NOT_SELF),
                sort = TargetSort.ALIVE_FIRST,
                onResolve = listOf(NightEffect.RecordChoice(), NightEffect.MarkSpent(SAGE)),
            )
        },
        infoId = SAGE,
    ),
)

// ---------------------------------------------------------------------------
// Outsiders
// ---------------------------------------------------------------------------

/** "If you are 'mad' about being an Outsider, you might be executed." */
private fun mutant() = CharacterRule(
    id = MUTANT,
    day = DayRule(
        briefing = { ctx ->
            if (ctx.slot != BriefingSlot.DAY_START || !ctx.holder.alive) {
                emptyList()
            } else {
                val impaired = Status.isImpaired(ctx.state, ctx.lookup, ctx.holder.id)
                listOf(
                    BriefingItem(
                        key = "mutant.madness.${ctx.holder.id}",
                        kind = BriefingKind.STANDING_FACT,
                        severity = if (impaired) BriefingSeverity.INFO else BriefingSeverity.ACTION,
                        sourceId = MUTANT,
                        playerId = ctx.holder.id,
                        text = if (impaired) {
                            "Mutant (${ctx.holder.name}) is drunk or poisoned — their ability " +
                                "does not work. Do NOT execute them for madness."
                        } else {
                            "Mutant (${ctx.holder.name}) — you may execute them at any time for " +
                                "pushing an Outsider claim. It uses today's execution."
                        },
                        actionId = "execute-for-madness:${ctx.holder.id}",
                    ),
                )
            }
        },
    ),
)

/** "When you die, 1 player is drunk from now on." */
private fun sweetheart() = CharacterRule(
    id = SWEETHEART,
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    otherNight = NightRule(
        gate = sweetheartOwesADrunk,
        prompt = "The Sweetheart is dead — choose one player who is drunk from now on. " +
            "Do NOT wake anybody: nobody is ever told.",
        action = {
            ChoosePlayers(
                sourceId = SWEETHEART,
                prompt = "WHO IS DRUNK FROM NOW ON?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = SWEETHEART,
                        label = DRUNK,
                        on = Ref.Target,
                        kind = EffectKind.DRUNK,
                        until = Until.FOREVER,
                    ),
                ),
                onResolve = listOf(NightEffect.RecordChoice()),
            )
        },
        // Nobody is woken, so the Chambermaid never sees this step.
        wakeCounts = WakeCount.NONE,
    ),
    tokens = listOf(
        // "from now on" — it outlives the Sweetheart deliberately (lead D3).
        TokenRule(
            SWEETHEART, DRUNK, EffectKind.DRUNK, Until.FOREVER,
            endsWithSource = false, impairs = true,
        ),
    ),
    onDeath = listOf(
        DeathTrigger(
            gate = { _, event, holder ->
                diedAsSelf(event, holder, SWEETHEART) && workedAtDeath(event) && !event.atNight
            },
            produce = { state, _, holder ->
                TriggerResult(
                    prompts = listOf(
                        prompt(
                            at = BriefingSlot.NOW,
                            kind = PromptKind.CHOOSE_PLAYER,
                            sourceId = SWEETHEART,
                            subject = holder.id,
                            title = "${holder.name} the Sweetheart died — choose one player " +
                                "who is drunk from now on.",
                            detail = "The drunkenness is retroactive to this moment: anything " +
                                "resolved after it depends on the choice. " +
                                "Nobody is told. (Day ${state.cycle}.)",
                        ),
                    ),
                )
            },
        ),
    ),
)

/**
 * "If you died today or tonight, the Demon may choose 2 players (not another
 * Demon) to swap characters."
 */
private fun barber() = CharacterRule(
    id = BARBER,
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    otherNight = NightRule(
        gate = barberArmed,
        prompt = "Wake the DEMON. Show the 'This character selected you' card, then the Barber " +
            "token. They shake their head, or point at 2 players to swap. Alignments never change.",
        action = { ctx ->
            val demons = aliveDemons(ctx.state, ctx.lookup)
            ChoosePlayers(
                sourceId = BARBER,
                prompt = "WHICH TWO DOES THE DEMON SWAP?" +
                    if (demons.isEmpty()) "" else " (${names(demons)} chooses)",
                min = 2,
                max = 2,
                // Dead seats are legal; the acting Demon may swap themself.
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "The Demon declines — no swap",
                onResolve = listOf(
                    NightEffect.SwapCharacters(Ref.TargetN(0), Ref.TargetN(1)),
                    NightEffect.RemoveToken(BARBER, HAIRCUTS_TONIGHT, Ref.Source),
                    NightEffect.RecordChoice(),
                ),
                onNone = listOf(
                    NightEffect.RemoveToken(BARBER, HAIRCUTS_TONIGHT, Ref.Source),
                    NightEffect.RecordChoice(),
                ),
            )
        },
    ),
    tokens = listOf(TokenRule(BARBER, HAIRCUTS_TONIGHT, null, Until.DAWN)),
    onDeath = listOf(
        DeathTrigger(
            // Any death, any cause, any phase — but only a seat that WAS the Barber
            // when it died, and only when the ability was working then.
            gate = { _, event, holder ->
                diedAsSelf(event, holder, BARBER) && workedAtDeath(event)
            },
            produce = { state, _, holder ->
                TriggerResult(
                    effects = listOf(
                        deathToken(
                            holder, state, BARBER, HAIRCUTS_TONIGHT,
                            note = "The Demon may swap two players' characters tonight.",
                        ),
                    ),
                )
            },
        ),
    ),
)

/** "When you learn that you died, publicly choose 1 alive player: if they are evil, your team loses." */
private fun klutz() = CharacterRule(
    id = KLUTZ,
    keepsAbilityWhenDead = true,
    onDeath = listOf(
        DeathTrigger(
            gate = { _, event, holder -> diedAsSelf(event, holder, KLUTZ) },
            produce = { _, event, holder ->
                val worked = workedAtDeath(event)
                TriggerResult(
                    prompts = listOf(
                        prompt(
                            // A day death is learned at once; a night death at dawn.
                            at = if (event.atNight) BriefingSlot.DAY_START else BriefingSlot.NOW,
                            kind = PromptKind.CHOOSE_PLAYER,
                            sourceId = KLUTZ,
                            subject = holder.id,
                            title = "${holder.name} the Klutz died — they publicly point at one " +
                                "ALIVE player. If that player is evil, the Klutz's team loses.",
                            detail = if (worked) {
                                "Give them time to decide. Travellers are legal choices."
                            } else {
                                "They were drunk or poisoned when they died — the choice has no " +
                                    "effect. Say nothing."
                            },
                        ),
                    ),
                )
            },
        ),
    ),
)

// ---------------------------------------------------------------------------
// Minions
// ---------------------------------------------------------------------------

/** "You & an opposing player know each other. If the good player is executed, evil wins." */
private fun evilTwin() = CharacterRule(
    id = EVIL_TWIN,
    firstNight = NightRule(
        gate = WakePredicate { ctx ->
            val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Evil Twin seat")
            when {
                !holder.alive -> StepGate.Skip("the Evil Twin is dead")
                seatsHolding(ctx.state, EVIL_TWIN, TWIN).isEmpty() ->
                    StepGate.Conditional(
                        question = "No good twin has been chosen yet.",
                        yesLabel = "Choose one now",
                        noLabel = "Skip",
                    )

                else -> StepGate.Fire
            }
        },
        prompt = "Wake the Evil Twin and their twin together and let them see each other. " +
            "Show the twin the Evil Twin token; show the Evil Twin the twin's character.",
        cards = { ctx -> evilTwinCards(ctx) },
    ),
    tokens = listOf(TokenRule(EVIL_TWIN, TWIN, null, Until.FOREVER)),
    day = DayRule(
        onNomination = { ctx ->
            val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
            if (nominee == null || !DayRules.hasToken(ctx.state, nominee.id, EVIL_TWIN, TWIN)) {
                emptyList()
            } else {
                listOf(
                    NominationTrigger(
                        kind = TriggerKind.WARN,
                        sourceId = EVIL_TWIN,
                        actorId = ctx.holder.id,
                        headline = "${nominee.name} is the Evil Twin's good twin — " +
                            "executing them hands the game to evil.",
                        detail = "Good also cannot win while ${ctx.holder.name} and " +
                            "${nominee.name} both live.",
                        impaired = !Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id),
                    ),
                )
            }
        },
    ),
)

private fun evilTwinCards(ctx: NightContext): List<CardOffer> {
    val twin = seatsHolding(ctx.state, EVIL_TWIN, TWIN).firstOrNull() ?: return emptyList()
    val believed = Identity.believedCharacterId(twin) ?: return emptyList()
    return listOf(
        CardOffer(
            label = "SHOW THE TWIN: THIS PLAYER IS THE EVIL TWIN",
            card = ShowCardSpec.CharacterCard("THIS PLAYER IS", EVIL_TWIN),
            truthful = true,
            editable = false,
        ),
        CardOffer(
            label = "SHOW THE EVIL TWIN: ${twin.name}'s CHARACTER",
            card = ShowCardSpec.CharacterCard("YOUR TWIN IS", believed),
            truthful = true,
            editable = false,
        ),
    )
}

/** "Each night, choose a player: if they nominate tomorrow, they die. 3 players live: no ability." */
private fun witch(): CharacterRule {
    fun rule() = NightRule(
        gate = Gates.all(
            Gates.aliveHolder,
            atLeastAlive(4, "the Witch has lost their ability"),
        ),
        prompt = "They point at a player — that player is cursed. Place the Cursed reminder. " +
            "Do NOT wake the cursed player: they are never told.",
        action = {
            ChoosePlayers(
                sourceId = WITCH,
                prompt = "WHO DID THEY CURSE?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They chose nobody",
                perTarget = listOf(NightEffect.PlaceToken(WITCH, CURSED, Ref.Target)),
                onResolve = listOf(NightEffect.RecordChoice()),
                onNone = listOf(NightEffect.RecordChoice()),
            )
        },
    )
    return CharacterRule(
        id = WITCH,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(TokenRule(WITCH, CURSED, null, Until.DUSK)),
        day = DayRule(onNomination = ::witchNomination),
    )
}

/**
 * The curse. Refines WP3's built-in row (lead D61 — a registry row of the same id
 * wins outright): exiles never trigger it, a Witch whose ability is not working
 * kills nobody, and a dead nominator cannot nominate at all.
 *
 * Every branch returns a row rather than nothing, because an empty list would let
 * the built-in fire instead (and a silently-removed row is exactly what D37 bans).
 */
private fun witchNomination(ctx: NominationContext): List<NominationTrigger> {
    val nominator = ctx.nominatorId?.let { ctx.state.player(it) } ?: return emptyList()
    if (!DayRules.hasToken(ctx.state, nominator.id, WITCH, CURSED)) return emptyList()
    val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
    // Alive, or dead holding a live `Has Ability` — `Status.hasAbility` answers both,
    // and answers "no" for a drunk or poisoned Witch.
    val witchWorks = Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id)
    val warn = { headline: String, detail: String ->
        listOf(
            NominationTrigger(
                kind = TriggerKind.WARN,
                sourceId = WITCH,
                actorId = ctx.holder.id,
                targetId = null, // a WARN must never be applied as a kill
                headline = headline,
                detail = detail,
                impaired = !witchWorks,
            ),
        )
    }
    return when {
        nominee?.isTraveller == true -> warn(
            "${nominator.name} is Witch-cursed, but this is an exile — nothing happens.",
            "Ability-triggering restrictions never apply to exiles.",
        )

        // "If just 3 players live, you lose this ability." No row at all: WP3's
        // built-in agrees, and the curse simply is not there to warn about.
        ctx.state.aliveCountResidents < 4 -> emptyList()

        !witchWorks -> warn(
            "${nominator.name} is Witch-cursed, but the Witch's ability is not working.",
            "Nobody dies. Do not tell them.",
        )

        !nominator.alive -> warn(
            "${nominator.name} is dead — the dead never nominate.",
            "The curse does nothing.",
        )

        else -> listOf(
            NominationTrigger(
                kind = TriggerKind.AUTO_DEATH,
                sourceId = WITCH,
                actorId = ctx.holder.id,
                targetId = nominator.id,
                headline = "${nominator.name} was cursed by the Witch — they die now.",
                detail = "The nomination stands and the vote continues. " +
                    "This is not an execution: the day's execution is still available.",
                options = listOf(
                    TriggerOption(DayRules.OPTION_APPLY, "They die", isDefault = true),
                    TriggerOption(DayRules.OPTION_SKIP, "Nothing happens"),
                ),
                impaired = false,
            ),
        )
    }
}

/** "Each night, choose a player & a good character: they are 'mad' they are this character." */
private fun cerenovus(): CharacterRule {
    fun rule() = NightRule(
        // Alive, or dead while carrying a live `Has Ability` — a Vigormortis-killed
        // Cerenovus keeps acting, and `Gates.aliveHolder` already asks exactly that.
        gate = Gates.aliveHolder,
        prompt = "They point at a player, then at a good character. Wake that player: " +
            "'This character selected you', the Cerenovus token, then the chosen character.",
        action = {
            ChoosePlayerAndCharacter(
                sourceId = CERENOVUS,
                prompt = "WHO IS MAD, AND AS WHAT?",
                playerConstraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                pool = CharacterPool.GOOD,
                requireNotInPlay = false,
                onResolve = listOf(
                    NightEffect.PlaceToken(
                        sourceId = CERENOVUS,
                        label = MAD,
                        on = Ref.Target,
                        kind = EffectKind.MAD,
                        until = Until.DUSK,
                    ),
                    NightEffect.RecordChoice(),
                ),
            )
        },
    )
    return CharacterRule(
        id = CERENOVUS,
        firstNight = rule(),
        otherNight = rule(),
        tokens = listOf(TokenRule(CERENOVUS, MAD, EffectKind.MAD, Until.DUSK)),
    )
}

/**
 * "Each night*, choose a player & a character they become (if not in play). If a
 * Demon is made, deaths tonight are arbitrary."
 *
 * The change is a `Prompt`, not a `BecomeCharacter`: the Pit-Hag PRESERVES the
 * target's alignment and `NightEffect.BecomeCharacter.evil` is a non-null Boolean,
 * so the declarative form cannot say "keep whatever they are" — the single most
 * damaging thing to get wrong on this card.
 */
private fun pitHag() = CharacterRule(
    id = PIT_HAG,
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "They point at a player and a character. If that character is already in play, " +
            "nothing happens — record it and say so. A created Minion or Demon learns nothing.",
        action = { ctx -> pitHagAction(ctx) },
    ),
)

private fun pitHagAction(ctx: NightContext): ChoosePlayerAndCharacter {
    val works = ctx.holder != null && Status.hasAbility(ctx.state, ctx.lookup, ctx.holder!!.id)
    return ChoosePlayerAndCharacter(
        sourceId = PIT_HAG,
        prompt = "WHO BECOMES WHAT?",
        playerConstraints = listOf(
            TargetConstraint.ANY_LIVING_STATE,
            TargetConstraint.SELF_ALLOWED,
        ),
        pool = CharacterPool.SCRIPT,
        requireNotInPlay = true,
        onResolve = listOf(
            NightEffect.RecordChoice(),
            NightEffect.QueuePrompt(
                at = BriefingSlot.NOW,
                kind = PromptKind.CHOOSE_CHARACTER,
                sourceId = PIT_HAG,
                title = if (works) {
                    "Pit-Hag: change them into the chosen character, KEEPING their current " +
                        "alignment. If a Demon is made, deaths tonight are arbitrary."
                } else {
                    "The Pit-Hag is drunk or poisoned — let them point, then change nothing."
                },
                on = Ref.Target,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// Demons
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a player: they die. The 1st Outsider this kills becomes an
 * evil Fang Gu & you die instead. [+1 Outsider]"
 *
 * The jump replaces the death rather than following it, so it cannot be an
 * `Attack` plus a follow-up: the ordinary kill is the tap, and the jump is the
 * explicit second button, offered only while the once-per-game is unused.
 */
private fun fangGu() = CharacterRule(
    id = FANG_GU,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = demonGate,
        prompt = "They point at a player. If that player is an OUTSIDER and the jump is unused, " +
            "do not tap the kill — take the jump: the Fang Gu dies instead and that Outsider " +
            "becomes an evil Fang Gu.",
        action = { ctx -> fangGuAction(ctx) },
    ),
    tokens = listOf(
        TokenRule(FANG_GU, DEAD, null, Until.DAWN),
        TokenRule(FANG_GU, ONCE, EffectKind.SPENT, Until.FOREVER, grimoireCentre = true),
    ),
)

private fun fangGuAction(ctx: NightContext): ChoosePlayers {
    val spent = fangGuJumpSpent(ctx.state)
    val outsiders = ctx.state.alivePlayers
        .filter { it.characterId?.let(ctx.lookup)?.team == Team.OUTSIDER }
    return demonAttack(
        sourceId = FANG_GU,
        noneLabel = if (spent || outsiders.isEmpty()) {
            "No kill (impaired, protected, or the storyteller's choice)"
        } else {
            "No kill — or take the jump"
        },
        onNone = if (spent || outsiders.isEmpty()) {
            emptyList()
        } else {
            listOf(
                NightEffect.QueuePrompt(
                    at = BriefingSlot.NOW,
                    kind = PromptKind.DECIDE,
                    sourceId = FANG_GU,
                    title = "Fang Gu jump (once per game): the chosen Outsider — " +
                        "${names(outsiders)} — becomes an evil Fang Gu and the Fang Gu dies " +
                        "instead. Put the Once token in the grimoire centre.",
                ),
            )
        },
    )
}

/**
 * "Each night*, choose a player: they die. Minions you kill keep their ability &
 * poison 1 Townsfolk neighbor. [-1 Outsider]"
 *
 * "Minions YOU kill" — an executed or Slayer-shot Minion keeps nothing, so the
 * two markers are raised as an obligation on this step and never placed blind.
 */
private fun vigormortis() = CharacterRule(
    id = VIGORMORTIS,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = demonGate,
        prompt = "They point at a player. That player dies. If they were a MINION they keep " +
            "their ability and poison one of their Townsfolk neighbours — you choose which.",
        action = { ctx -> vigormortisAction(ctx) },
    ),
    tokens = listOf(
        TokenRule(VIGORMORTIS, DEAD, null, Until.DAWN),
        TokenRule(VIGORMORTIS, HAS_ABILITY, EffectKind.HAS_ABILITY, Until.FOREVER, copies = 3),
        TokenRule(
            VIGORMORTIS, POISONED, EffectKind.POISONED, Until.FOREVER,
            copies = 3, impairs = true,
        ),
    ),
    onDeath = listOf(
        DeathTrigger(
            gate = { state, event, holder ->
                diedAsSelf(event, holder, VIGORMORTIS) &&
                    (
                        seatsHolding(state, VIGORMORTIS, HAS_ABILITY).isNotEmpty() ||
                            seatsHolding(state, VIGORMORTIS, POISONED).isNotEmpty()
                        )
            },
            produce = { state, _, holder ->
                val kept = seatsHolding(state, VIGORMORTIS, HAS_ABILITY)
                val poisoned = seatsHolding(state, VIGORMORTIS, POISONED)
                TriggerResult(
                    prompts = listOf(
                        prompt(
                            at = BriefingSlot.NOW,
                            kind = PromptKind.PLACE_EFFECT,
                            sourceId = VIGORMORTIS,
                            subject = holder.id,
                            title = "The Vigormortis is dead — ${kept.size} dead Minions lose " +
                                "their abilities and ${poisoned.size} players become healthy.",
                            detail = "Remove the markers from ${names(kept)}" +
                                (if (poisoned.isEmpty()) "" else " and ${names(poisoned)}") +
                                ". A Mastermind that has their ability keeps it, by jinx.",
                        ),
                    ),
                )
            },
        ),
    ),
)

private fun vigormortisAction(ctx: NightContext): ChoosePlayers {
    val minions = aliveMinions(ctx.state, ctx.lookup)
    return demonAttack(
        sourceId = VIGORMORTIS,
        onResolve = if (minions.isEmpty()) {
            emptyList()
        } else {
            listOf(
                NightEffect.QueuePrompt(
                    at = BriefingSlot.NOW,
                    kind = PromptKind.PLACE_EFFECT,
                    sourceId = VIGORMORTIS,
                    title = "If the player the Vigormortis just killed was a Minion " +
                        "(${names(minions)}): place Has Ability on them, then poison ONE of " +
                        "their Townsfolk neighbours — clockwise or anticlockwise, your choice.",
                    on = Ref.Target,
                ),
            )
        },
    )
}

/** "Each night*, choose a player: they die. Your 2 Townsfolk neighbors are poisoned." */
private fun noDashii() = CharacterRule(
    id = NO_DASHII,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = demonGate,
        prompt = "They point at a player. That player dies. Deaths never move the poison — " +
            "a dead Townsfolk still counts as the nearest Townsfolk.",
        action = { demonAttack(NO_DASHII) },
    ),
    // The two poisoned neighbours are a positional StandingRule owned by WP1
    // (`Standing.emitPositional`): it needs the recursion-safe `StatusQuery`,
    // which `StandingRule.emit` is not given. Declaring one here would REPLACE
    // the working rule with a weaker one, so this row deliberately declares none.
    tokens = listOf(
        TokenRule(NO_DASHII, DEAD, null, Until.DAWN),
        TokenRule(
            NO_DASHII, POISONED, EffectKind.POISONED, Until.FOREVER,
            copies = 2, impairs = true,
        ),
    ),
)

/**
 * "Each night*, choose a player: they die. Townsfolk abilities yield false info.
 * Each day, if no-one is executed, evil wins."
 *
 * Both standing clauses are already enforced outside the registry — `InfoCalc`
 * computes MUST_LIE and `WinCheck.duskCheck` owns `vortox-dusk` — and both are
 * gated on a living, SOBER Vortox (lead D11).
 */
private fun vortox() = CharacterRule(
    id = VORTOX,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = demonGate,
        prompt = "They point at a player. That player dies.",
        action = { demonAttack(VORTOX) },
    ),
    tokens = listOf(TokenRule(VORTOX, DEAD, null, Until.DAWN)),
    day = DayRule(
        briefing = { ctx ->
            if (ctx.slot != BriefingSlot.DAY_START || !ctx.holder.alive) {
                emptyList()
            } else if (Status.isImpaired(ctx.state, ctx.lookup, ctx.holder.id)) {
                emptyList() // an impaired Vortox loses the whole ability (lead D11)
            } else {
                listOf(
                    BriefingItem(
                        key = "vortox.standing",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.ALERT,
                        sourceId = VORTOX,
                        playerId = ctx.holder.id,
                        text = "VORTOX ALIVE — every Townsfolk answer must be FALSE, and an " +
                            "execution is required today or evil wins. An exile is not an " +
                            "execution; an execution that kills nobody still counts.",
                    ),
                )
            }
        },
    ),
)
