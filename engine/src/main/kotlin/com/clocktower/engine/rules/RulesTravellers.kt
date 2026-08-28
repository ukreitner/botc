package com.clocktower.engine.rules

import com.clocktower.engine.BriefingContext
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionContext
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.GrantMode
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Memory
import com.clocktower.engine.NightContext
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationContext
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.Registration
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.Sequence
import com.clocktower.engine.StandingRule
import com.clocktower.engine.StepGate
import com.clocktower.engine.Status
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.TokenRule
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.TriggerOption
import com.clocktower.engine.Until
import com.clocktower.engine.Voting
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate
import com.clocktower.engine.YesNo

/**
 * Traveller behaviour (WP7-TRAV). Every `"team": "traveler"` character in
 * `characters.json`: 5 Trouble Brewing, 5 Bad Moon Rising, 5 Sects & Violets
 * and 3 experimental.
 *
 * Three things travellers do NOT own here, because the engine owns them:
 *  - the exile funnel, exile vote weights and the Voudon / Bureaucrat / Thief
 *    vote regime live in `DayRules.voteRules` (WP3);
 *  - the Scapegoat substitution and the Deviant's "cannot die by exile" are
 *    steps 11 and 13 of `Deaths.killOutcome` (WP1);
 *  - `traveller.alignment:<seat>` is a `SetupRequirements` row (WP4).
 * Rows here refine those, they never restate them (lead D61).
 */
internal val TRAVELLER_RULES: List<CharacterRule> = listOf(
    // ---------------------------------------------------------------- TB
    beggar(),
    bureaucrat(),
    gunslinger(),
    scapegoat(),
    thief(),
    // --------------------------------------------------------------- BMR
    apprentice(),
    bishop(),
    judge(),
    matron(),
    voudon(),
    // ---------------------------------------------------------------- SV
    barista(),
    boneCollector(),
    butcher(),
    deviant(),
    harlot(),
    // --------------------------------------------------------------- EXP
    cacklejack(),
    gangster(),
    gnome(),
)

// =====================================================================
// Trouble Brewing
// =====================================================================

/**
 * "You must use a vote token to vote. If a dead player gives you theirs, you
 * learn their alignment. **You are sober & healthy.**"
 *
 * The last clause is the only part the engine can enforce today: an innate
 * [EffectKind.SOBER_HEALTHY] that beats every impairment (`Status.impairment`
 * returns empty for a live SOBER_HEALTHY). A Poisoner's token stays placeable
 * and visible — it simply has no effect.
 *
 * W7b closed the rest: `Player.voteTokens` (lead D72) is the hoard,
 * `DayRules.record` spends one per execution vote, and the day ability below
 * moves a dead player's token over — placing the `beggar/Token` reminder that
 * names the donor and writing the alignment the Beggar learns to the ledger.
 */
private fun beggar(): CharacterRule = CharacterRule(
    id = "beggar",
    // "If a dead player gives you their vote token, you learn their alignment."
    // The donation has to be visible in the grimoire for the rest of the game
    // and the official set carries no label for it (WP6C data change). The
    // donor goes in `PlacedReminder.targetPlayerId`, so one token is enough for
    // each gift and the note names who gave it.
    tokens = listOf(
        TokenRule("beggar", "Token", effect = null, until = Until.FOREVER, endsWithSource = false),
    ),
    standing = StandingRule("beggar") { state, holder, _ ->
        if (!holder.alive || !holder.seated) {
            emptyList()
        } else {
            listOf(
                Effect(
                    id = holder.standingSince,
                    kind = EffectKind.SOBER_HEALTHY,
                    targetId = holder.id,
                    sourceCharacterId = "beggar",
                    // No source seat to check, exactly as WP1 emits the Drunk's
                    // NO_ABILITY: a self-sourced SOBER_HEALTHY would recurse
                    // through `abilityWorks`, trip the in-flight guard and put
                    // the Beggar in `paradoxSeats` for a board with no paradox.
                    // The rule already ends with its holder — a dead Beggar
                    // emits nothing at all.
                    sourcePlayerId = null,
                    until = Until.FOREVER,
                    endsWithSource = false,
                    label = "",
                    note = "The Beggar is sober & healthy — drunk and poison tokens have no effect.",
                    createdCycle = state.cycle,
                    createdAtNight = state.phase != Phase.DAY,
                    derived = true,
                ),
            )
        }
    },
    day = DayRule(
        // W7b: the hoard is REAL. `DayRules.giveVoteToken` moves one token from
        // a dead seat to this one, places the `beggar/Token` reminder naming the
        // donor and writes the alignment the Beggar learns; `DayRules.record`
        // spends one on every execution vote the Beggar's hand is up for.
        ability = DayAbility(
            label = "Take a dead player's vote token",
            recordsAs = "beggar",
            available = { state, _, holder ->
                holder.alive &&
                    state.seats.any { !it.alive && it.seated && it.voteTokens > 0 }
            },
        ),
        briefing = { ctx ->
            val tokens = ctx.holder.voteTokens
            dayStart(
                ctx,
                "beggar",
                BriefingSeverity.ACTION,
                "${ctx.holder.name} is the Beggar and holds " +
                    (if (tokens == 1) "1 vote token" else "$tokens vote tokens") +
                    ": each execution vote spends one, and with none left their hand does not " +
                    "count. A dead player may hand theirs over at any time — take it from the " +
                    "Beggar's day ability and show them that player's alignment privately. They " +
                    "support exiles freely, spending nothing, and cannot be drunk or poisoned.",
            )
        },
    ),
)

/**
 * "Each night, choose a player (not yourself): their vote counts as 3 votes
 * tomorrow." Dead targets are legal (their one ghost vote is then worth 3) and
 * repeat targets are legal — no `DIFFERENT_FROM_LAST_NIGHT`.
 *
 * The weight itself is `DayRules.voteRules` (WP3); this row only places the
 * token that the weight keys off.
 */
private fun bureaucrat(): CharacterRule = markVoter(
    id = "bureaucrat",
    label = "3 Votes",
    prompt = "Wake the Bureaucrat. They point at a player other than themselves — " +
        "that player's vote counts as 3 votes tomorrow.",
    briefing = "counts as 3 votes today. Count it out loud. Exiles are unaffected.",
)

/**
 * "Each day, after the 1st vote has been tallied, you may choose a player that
 * voted: they die."
 *
 * Not an execution: `DeathCause.DAY_ABILITY`, no `ExecutionRecord`, the day
 * continues and the Undertaker learns nothing. The window opens on the day's
 * FIRST non-exile nomination and closes whether or not they shoot.
 */
private fun gunslinger(): CharacterRule = CharacterRule(
    id = "gunslinger",
    killCause = DeathCause.DAY_ABILITY,
    // Once per DAY, not per game, so no `spentLabel` (lead D49) — the ledger
    // still owns availability and this token is what the storyteller sees.
    // Swept at dawn with the rest of the day's markers.
    tokens = listOf(
        TokenRule("gunslinger", "No Ability", effect = null, until = Until.DAWN),
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Gunslinger shot",
            oncePerDay = true,
            recordsAs = "gunslinger",
            available = { state, lookup, holder ->
                Status.hasAbility(state, lookup, holder.id) &&
                    holder.alive &&
                    firstExecutionVoteToday(state) != null &&
                    Memory.by(state, LedgerKind.CHOICE, "gunslinger", holder.id)
                        .none { it.cycle == state.cycle && !it.atNight }
            },
        ),
        briefing = { ctx ->
            val nomination = firstExecutionVoteToday(ctx.state)
            val voters = nomination?.voterIds.orEmpty().mapNotNull { ctx.state.player(it)?.name }
            dayStart(
                ctx,
                "gunslinger",
                BriefingSeverity.ACTION,
                if (nomination == null) {
                    "${ctx.holder.name} is the Gunslinger. After today's FIRST execution vote is " +
                        "tallied, ask whether they want to shoot. Exiles never open the window."
                } else {
                    "Gunslinger: ask ${ctx.holder.name} whether they shoot. Legal targets are only " +
                        "the players who voted — ${voters.joinToString().ifEmpty { "nobody voted" }}. " +
                        "This is NOT an execution: the day continues, the block does not move, and " +
                        "the Undertaker learns nothing. The chance is gone either way."
                },
            )
        },
    ),
)

/**
 * "If a player of your alignment is executed, you might be executed instead."
 *
 * The substitution itself is step 13 of `Deaths.killOutcome` and the matching
 * `Execution.consequences` row — both WP3/WP1 built-ins that already compare
 * REGISTERED alignment and require an alive, unimpaired Scapegoat, which is
 * exactly what the card asks for. Nothing to override (lead D61).
 */
private fun scapegoat(): CharacterRule = CharacterRule(
    id = "scapegoat",
    day = DayRule(
        briefing = { ctx ->
            val evil = Registration.registersEvil(ctx.state, ctx.lookup, ctx.holder)
            dayStart(
                ctx,
                "scapegoat",
                BriefingSeverity.ACTION,
                "${ctx.holder.name} is the Scapegoat (${if (evil) "evil" else "good"}). If a " +
                    "${if (evil) "evil" else "good"} player is executed today you may execute them " +
                    "instead: the nominee survives, the Scapegoat dies as an EXECUTION (the " +
                    "Undertaker sees Scapegoat) and the day ends. A Spy or Recluse may count as " +
                    "either alignment — your call.",
            )
        },
    ),
)

/**
 * "Each night, choose a player (not yourself): their vote counts negatively
 * tomorrow." Same shape as the Bureaucrat, opposite sign; dead targets are
 * explicitly useful on the final day.
 */
private fun thief(): CharacterRule = markVoter(
    id = "thief",
    label = "Negative Vote",
    prompt = "Wake the Thief. They point at a player other than themselves — " +
        "that player's vote counts as −1 tomorrow.",
    briefing = "counts as −1 today. The tally dips when they raise their hand. " +
        "Exiles are unaffected: their support counts +1.",
)

// =====================================================================
// Bad Moon Rising
// =====================================================================

/**
 * "On your 1st night, you gain a Townsfolk ability (if good), or a Minion
 * ability (if evil)."
 *
 * The Apprentice does NOT become that character: the token stays, `Is The
 * Apprentice` is placed beside the granted one, and every ability that detects
 * characters still detects them as the Apprentice. The grant is an
 * `AbilityGrant(REPLACE)` on `Player.grants` — there is no `NightEffect` that
 * writes one, so the step raises the obligation and the storyteller applies it
 * (see the report: WP2 `NightEffect.Grant`).
 */
private fun apprentice(): CharacterRule = CharacterRule(
    id = "apprentice",
    firstNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, notGranted("apprentice")),
        prompt = "Wake the Apprentice. Show the YOU ARE card and one not-in-play " +
            "Townsfolk token (if good) or Minion token (if evil). They keep the Apprentice " +
            "token — place Is The Apprentice beside the granted one.",
        action = { ctx ->
            // The seat's explicit alignment, which `traveller.alignment:<seat>`
            // (WP4) makes a blocking arrival requirement precisely for this.
            val evil = ctx.holder?.isEvil(ctx.lookup) ?: false
            ChooseCharacter(
                sourceId = "apprentice",
                prompt = if (evil) "WHICH MINION ABILITY?" else "WHICH TOWNSFOLK ABILITY?",
                pool = if (evil) CharacterPool.MINION else CharacterPool.TOWNSFOLK,
                allowNone = false,
                onResolve = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "apprentice",
                        label = "Is The Apprentice",
                        on = Ref.Source,
                        until = Until.FOREVER,
                    ),
                    NightEffect.ShowCardTo(on = Ref.Source, card = "YOU ARE"),
                    // W7E: the grant is REAL now. ADD, not REPLACE: the Apprentice
                    // has no ability of its own to displace, and `characterId`
                    // stays "apprentice" so every ability still detects them as
                    // the Apprentice, and they are still a Traveller.
                    NightEffect.GrantAbility(
                        abilityId = "",
                        sourceId = "apprentice",
                        on = Ref.Source,
                        mode = GrantMode.ADD,
                    ),
                ),
            )
        },
    ),
    tokens = listOf(TokenRule("apprentice", "Is The Apprentice", null, Until.FOREVER)),
)

/**
 * "Only the Storyteller can nominate. At least 1 opposing player must be
 * nominated each day."
 *
 * The nomination block is `DayRules.canNominate`'s built-in (WP3) and is
 * correct. What is missing there is the daily obligation, which this row
 * surfaces as a `WARN` on every nomination and as a standing day-start fact.
 */
private fun bishop(): CharacterRule = CharacterRule(
    id = "bishop",
    day = DayRule(
        onNomination = { ctx ->
            val bishop = ctx.holder
            if (!bishop.alive || !Status.hasAbility(ctx.state, ctx.lookup, bishop.id)) {
                emptyList()
            } else {
                val bishopEvil = Registration.registersEvil(ctx.state, ctx.lookup, bishop)
                val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
                val opposes = nominee != null &&
                    Registration.registersEvil(ctx.state, ctx.lookup, nominee) != bishopEvil
                val owed = !opposingNominatedToday(ctx.state, ctx.lookup, bishopEvil)
                listOf(
                    NominationTrigger(
                        kind = TriggerKind.WARN,
                        sourceId = "bishop",
                        actorId = bishop.id,
                        targetId = ctx.nomineeId,
                        headline = when {
                            opposes && owed -> "This nomination discharges the Bishop's obligation."
                            owed -> "The Bishop still owes an ${if (bishopEvil) "good" else "evil"} " +
                                "nominee before dusk."
                            else -> "The Bishop's obligation is already discharged today."
                        },
                        detail = "Only the storyteller nominates while ${bishop.name} has their " +
                            "ability. Exiles are unaffected — any player, alive or dead, may call one.",
                    ),
                )
            }
        },
        briefing = { ctx ->
            val bishopEvil = Registration.registersEvil(ctx.state, ctx.lookup, ctx.holder)
            dayStart(
                ctx,
                "bishop",
                BriefingSeverity.ACTION,
                "Bishop — you nominate today; players may not (they may still call exiles). " +
                    "You must nominate at least one ${if (bishopEvil) "good" else "evil"} player " +
                    "before dusk. On the final day, nominate every living player.",
            )
        },
    ),
    tokens = listOf(
        TokenRule("bishop", "Nominate Good", null, Until.DUSK),
        TokenRule("bishop", "Nominate Evil", null, Until.DUSK),
    ),
)

/**
 * "Once per game, if another player nominated, you may choose to force the
 * current execution to pass or fail."
 *
 * A forced pass is a real EXECUTION (`ExecutionVia.JUDGE`), not a good-ability
 * death — so the Undertaker, the Saint and the Scapegoat all see it. The
 * `Nomination.judgeForced` field and the `aboutToDie` replay are WP3's.
 */
private fun judge(): CharacterRule = CharacterRule(
    id = "judge",
    day = DayRule(
        ability = DayAbility(
            label = "Judge's ruling",
            oncePerGame = true,
            recordsAs = "judge",
            available = { state, lookup, holder ->
                holder.alive &&
                    Status.hasAbility(state, lookup, holder.id) &&
                    !Memory.isSpent(state, "judge", holder.id) &&
                    !DayRules.hasToken(state, holder.id, "judge", "No Ability") &&
                    state.nominations.any {
                        it.day == state.cycle && !it.isExile && it.nominatorId != holder.id
                    }
            },
        ),
        briefing = { ctx ->
            val spent = Memory.isSpent(ctx.state, "judge", ctx.holder.id) ||
                DayRules.hasToken(ctx.state, ctx.holder.id, "judge", "No Ability")
            dayStart(
                ctx,
                "judge",
                if (spent) BriefingSeverity.INFO else BriefingSeverity.ACTION,
                if (spent) {
                    "Judge — ability spent."
                } else {
                    "Judge in play, ability unspent. Once per game, on someone else's nomination, " +
                        "${ctx.holder.name} may force the execution to pass (the nominee dies and " +
                        "the day ends) or fail (votes reset to 0, the day continues). Never on " +
                        "their own nomination, and never on an exile."
                },
            )
        },
    ),
    tokens = listOf(TokenRule("judge", "No Ability", EffectKind.SPENT, Until.FOREVER)),
)

/**
 * "Each day, you may choose up to 3 sets of 2 players to swap seats. Players
 * may not leave their seats to talk in private."
 *
 * `Seats` has no `swapSeats` verb and `DayAbility` cannot express "3 per day"
 * (see the report), so this row is the day-tab entry point plus the daily
 * announcement the storyteller must actually make.
 */
private fun matron(): CharacterRule = CharacterRule(
    id = "matron",
    day = DayRule(
        ability = DayAbility(
            label = "Matron seat swap",
            recordsAs = "matron",
            available = { state, lookup, holder ->
                holder.alive && Status.hasAbility(state, lookup, holder.id)
            },
        ),
        briefing = { ctx ->
            dayStart(
                ctx,
                "matron",
                BriefingSeverity.ACTION,
                "Matron in play. Announce: players may not leave their seats to talk in private — " +
                    "you may only whisper with your neighbours. ${ctx.holder.name} may call up to 3 " +
                    "seat swaps today; unused swaps do not carry over. A swap moves the whole seat " +
                    "(tokens, life, ghost vote) and silently reorders every adjacency.",
            )
        },
    ),
)

/**
 * "Only you & the dead can vote. They don't need a vote token to do so. A 50%
 * majority isn't required."
 *
 * The whole rule is `DayRules.voteRules` (WP3), which already returns
 * `eligible = the Voudon + the dead`, `threshold = 1` and
 * `spendsGhostVotes = false`, and lets the exile branch win first. This row is
 * the announcement that has to be made before nominations.
 */
private fun voudon(): CharacterRule = CharacterRule(
    id = "voudon",
    day = DayRule(
        briefing = { ctx ->
            val alive = Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id) && ctx.holder.alive
            dayStart(
                ctx,
                "voudon",
                if (alive) BriefingSeverity.ALERT else BriefingSeverity.INFO,
                if (alive) {
                    "Voudon in play. Announce before nominations: 'Living players, your hands stay " +
                        "down. Only the dead — and ${ctx.holder.name} — vote today. Dead players do " +
                        "not need a vote token and may vote on every nomination.' One vote is enough. " +
                        "Alive players still nominate; the dead may not (they may still call exiles)."
                } else {
                    "Voudon — no ability: voting is normal again. Anyone already about to die stays " +
                        "about to die; earlier tallies used the Voudon rules."
                },
            )
        },
    ),
)

// =====================================================================
// Sects & Violets
// =====================================================================

/**
 * "Each night, until dusk, 1) a player becomes sober, healthy & gets true info,
 * or 2) their ability works twice. They learn which."
 *
 * The Barista is never woken — the TARGET is — so the row does not count for
 * the Chambermaid ([WakeCount.NONE]). Both tokens are one-of-a-kind: choosing
 * one clears the other from every seat first.
 */
private fun barista(): CharacterRule = CharacterRule(
    id = "barista",
    firstNight = baristaNight(),
    otherNight = baristaNight(),
    tokens = listOf(
        TokenRule("barista", "Sober & Healthy", EffectKind.SOBER_HEALTHY, Until.DUSK),
        // W7I: a typed effect, so `NightPlan` emits the second
        // `StepVariant.AGAIN` row without ever naming the Barista.
        TokenRule("barista", "Acts Twice", EffectKind.ACTS_TWICE, Until.DUSK),
        // The official stand-ins for a doubled character's own one-of-a-kind reminders.
        TokenRule("barista", "?", null, Until.DUSK, copies = 2),
    ),
)

private fun baristaNight(): NightRule = NightRule(
    gate = Gates.aliveHolder,
    wakeCounts = WakeCount.NONE,
    prompt = "Do NOT wake the Barista — they learn nothing. Choose a player, wake THEM, and tell " +
        "them which effect applies until dusk: sober & healthy with true info, or their ability " +
        "works twice.",
    action = { ctx ->
        Sequence(
            sourceId = "barista",
            prompt = "WHO DID THE STORYTELLER CHOOSE, AND WHICH EFFECT?",
            stages = listOf(
                ChoosePlayers(
                    sourceId = "barista",
                    prompt = "WHO IS AFFECTED?",
                    min = 1,
                    max = 1,
                    constraints = listOf(TargetConstraint.ANY_LIVING_STATE),
                    sort = TargetSort.ALIVE_FIRST,
                    // Only one of each token exists: clear both from every seat first.
                    onResolve = clearBaristaTokens(ctx),
                ),
                YesNo(
                    sourceId = "barista",
                    prompt = "WHICH EFFECT?",
                    yesLabel = "Sober & Healthy — true info until dusk",
                    noLabel = "Acts Twice — their ability works twice until dusk",
                    onYes = listOf(
                        NightEffect.PlaceToken(
                            sourceId = "barista",
                            label = "Sober & Healthy",
                            on = Ref.Target,
                            kind = EffectKind.SOBER_HEALTHY,
                            until = Until.DUSK,
                        ),
                    ),
                    onNo = listOf(
                        NightEffect.PlaceToken(
                            sourceId = "barista",
                            label = "Acts Twice",
                            on = Ref.Target,
                            until = Until.DUSK,
                        ),
                    ),
                ),
            ),
        )
    },
)

/** Both Barista tokens are unique; a new choice removes them from wherever they sit. */
private fun clearBaristaTokens(ctx: NightContext): List<NightEffect> =
    ctx.state.seats.flatMap { seat ->
        listOf("Sober & Healthy", "Acts Twice").map { label ->
            NightEffect.RemoveToken("barista", label, Ref.Seat(seat.id))
        }
    }

/**
 * "Once per game, at night*, choose a dead player: they regain their ability
 * until dusk."
 *
 * No kill, no resurrection, no character change — the player stays dead and
 * simply carries a live `HAS_ABILITY` effect, which `Status.roleWorks` and
 * `Gates.aliveHolder` already honour. A first-night-only restored ability
 * (Clockmaker, Washerwoman, Chef) needs its FIRST-variant step re-run tonight;
 * `NightEffect.QueuePrompt` cannot name the target's own character as the slot,
 * so the obligation is raised at NOW (see the report: WP2).
 */
private fun boneCollector(): CharacterRule = CharacterRule(
    id = "bonecollector",
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent(), someoneIsDead()),
        prompt = "Wake the Bone Collector. They shake their head no, or point at any dead player: " +
            "that player regains their ability until dusk and may need waking later tonight. " +
            "They are not told who chose them.",
        action = {
            ChoosePlayers(
                sourceId = "bonecollector",
                prompt = "WHICH DEAD PLAYER?",
                min = 0,
                max = 1,
                constraints = listOf(TargetConstraint.DEAD),
                sort = TargetSort.DEAD_FIRST,
                allowNone = true,
                noneLabel = "They shook their head no",
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "bonecollector",
                        label = "Has Ability",
                        on = Ref.Target,
                        kind = EffectKind.HAS_ABILITY,
                        until = Until.DUSK,
                    ),
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.NOW,
                        kind = PromptKind.RUN_STEP,
                        sourceId = "bonecollector",
                        on = Ref.Target,
                        title = "Bone Collector: run this seat's own ability tonight. If it is " +
                            "first-night-only, run its FIRST-night version. A spent once-per-game " +
                            "ability may be used again while Has Ability stands.",
                    ),
                ),
                // Shaking their head no does not spend the ability.
                onResolve = listOf(NightEffect.MarkSpent("bonecollector")),
            )
        },
    ),
    tokens = listOf(
        TokenRule("bonecollector", "Has Ability", EffectKind.HAS_ABILITY, Until.DUSK),
        TokenRule("bonecollector", "No Ability", EffectKind.SPENT, Until.FOREVER),
    ),
)

/**
 * "Each day, after the 1st execution, you may nominate again."
 *
 * `DayRules.nominationAllowance` / `secondExecutionAllowed` already give the
 * Butcher their extra nomination (WP3). What is missing is the prompt at the
 * moment it becomes true, which is an execution consequence.
 */
private fun butcher(): CharacterRule = CharacterRule(
    id = "butcher",
    day = DayRule(
        onExecution = { ctx ->
            val butcher = ctx.holder
            val real = ctx.record.outcome != ExecutionOutcome.NO_EXECUTION
            if (!real || !butcher.alive || !Status.hasAbility(ctx.state, ctx.lookup, butcher.id)) {
                emptyList()
            } else {
                val threshold = Voting.executionThreshold(ctx.state.aliveCountWithTravellers)
                listOf(
                    ExecutionConsequence(
                        sourceId = "butcher",
                        headline = "${butcher.name} (Butcher) may nominate again — " +
                            "even someone already nominated today.",
                        detail = "The extra nomination only has to reach $threshold votes; it does " +
                            "NOT have to beat today's highest tally. An execution counts even when " +
                            "the executed player did not die. Exiles are never executions.",
                        options = listOf(
                            TriggerOption(DayRules.OPTION_APPLY, "Start the Butcher's nomination"),
                            TriggerOption(DayRules.OPTION_SKIP, "Not now", isDefault = true),
                        ),
                    ),
                )
            }
        },
    ),
)

/**
 * "If you were funny today, you cannot die by exile."
 *
 * Step 11 of `Deaths.killOutcome` (WP1) already offers the choice on an EXILE
 * and only on an EXILE — never execution, never the Demon, never a Gangster,
 * Harlot or Gnome kill. This row carries the daily judgement the storyteller
 * has to make hours before the exile is called.
 */
private fun deviant(): CharacterRule = CharacterRule(
    id = "deviant",
    day = DayRule(
        briefing = { ctx ->
            dayStart(
                ctx,
                "deviant",
                BriefingSeverity.ACTION,
                "Deviant — ${ctx.holder.name}. Were they funny today? Be forgiving: even slightly " +
                    "funny counts, and you may have agreed a different criterion with them. If yes, " +
                    "they cannot die by exile today — but they can still be executed or killed by " +
                    "any other ability. Judge fresh every day.",
            )
        },
    ),
    // W7G: the criterion is a table agreement, so the row is advisory (it never
    // blocks "Begin night") but it must be ON the checklist — the Deviant needs
    // to know what counts before the game starts.
    setup = listOf(
        SetupRequirement(
            id = "deviant.criterion",
            characterId = "deviant",
            kind = RequirementKind.ACK,
            title = "Deviant: agree what counts as funny",
            prompt = "Agree with the Deviant what you will accept as funny — a joke, a pun, a " +
                "voice, anything you two settle on. Be forgiving, and judge fresh every day.",
            blocking = false,
            satisfied = { state, _ ->
                state.seats.none { it.characterId?.let(Character::normalizeId) == "deviant" } ||
                    state.seats.any { seat ->
                        seat.characterId?.let(Character::normalizeId) == "deviant" &&
                            seat.notes.any { it.text.isNotBlank() }
                    }
            },
        ),
    ),
)

/**
 * "Each night*, choose a living player: if they agree, you learn their
 * character, but you both might die."
 *
 * Three beats, one step: pick, ask for consent, then both-or-neither. The
 * deaths are `TRAVELLER_ABILITY`, never a Demon kill, so a Monk's Safe and a
 * Soldier do not stop them while Sailor / Tea Lady / Innkeeper / Fool do.
 * Consent is recorded by the choice; only "both die" has effects (see the
 * report: one `NightInput` cannot carry two independent yes/no answers).
 */
private fun harlot(): CharacterRule = CharacterRule(
    id = "harlot",
    killCause = DeathCause.TRAVELLER_ABILITY,
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Wake the Harlot; they point at any living player. Wake that player, show THIS " +
            "CHARACTER SELECTED YOU — they nod or shake their head. Only if they agreed, show the " +
            "Harlot their TRUE character token, then decide: both die, or neither.",
        action = {
            Sequence(
                sourceId = "harlot",
                prompt = "WHO DID THEY CHOOSE, AND DID BOTH DIE?",
                stages = listOf(
                    ChoosePlayers(
                        sourceId = "harlot",
                        prompt = "WHO DID THE HARLOT CHOOSE?",
                        min = 1,
                        max = 1,
                        constraints = listOf(TargetConstraint.ALIVE),
                        sort = TargetSort.ALIVE_FIRST,
                        allowNone = true,
                        noneLabel = "They refused to reveal — nothing happens",
                    ),
                    YesNo(
                        sourceId = "harlot",
                        prompt = "THEY AGREED AND SAW THE CHARACTER — DO BOTH DIE?",
                        yesLabel = "Both die",
                        noLabel = "Neither dies",
                        onYes = listOf(
                            NightEffect.Attack(
                                on = Ref.Target,
                                cause = DeathCause.TRAVELLER_ABILITY,
                            ),
                            NightEffect.Attack(
                                on = Ref.Source,
                                cause = DeathCause.TRAVELLER_ABILITY,
                            ),
                        ),
                    ),
                ),
            )
        },
    ),
    // Two official DEAD reminders: one per possible death.
    tokens = listOf(TokenRule("harlot", "Dead", null, Until.DAWN, copies = 2)),
)

// =====================================================================
// Experimental
// =====================================================================

/**
 * "Each day, choose a player: a different player changes character tonight."
 *
 * The day half marks one seat NOT ME; the night half hands a new character to
 * somebody else. W7D / lead D67: `BecomeCharacter.evil` is nullable, so the
 * change is REAL and the seat KEEPS its alignment — the row no longer has to
 * guess (a forced GOOD on a new Minion would have corrupted every evil count).
 * The storyteller may still overrule the side from the DECIDE prompt.
 */
private fun cacklejack(): CharacterRule = CharacterRule(
    id = "cacklejack",
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Before dawn, choose a player who is NOT marked Not Me. Wake them, show the YOU ARE " +
            "card and their new character token, and swap the token in the grimoire.",
        action = { ctx ->
            ChoosePlayerAndCharacter(
                sourceId = "cacklejack",
                prompt = notMeHint(ctx),
                playerConstraints = listOf(TargetConstraint.ANY_LIVING_STATE),
                pool = CharacterPool.SCRIPT,
                requireNotInPlay = true,
                onResolve = listOf(
                    NightEffect.BecomeCharacter(
                        on = Ref.Target,
                        characterId = "",
                        reason = ChangeReason.STORYTELLER,
                    ),
                    NightEffect.ShowCardTo(on = Ref.Target, card = "YOU ARE"),
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.NOW,
                        kind = PromptKind.DECIDE,
                        sourceId = "cacklejack",
                        on = Ref.Target,
                        title = "Cacklejack: this seat has changed and KEPT its alignment " +
                            "(lead D67). Overrule the side here if the table played it the " +
                            "other way, and check they were not the seat marked Not Me.",
                    ),
                ),
                onNone = listOf(NightEffect.RecordChoice()),
            )
        },
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Not Me",
            oncePerDay = true,
            recordsAs = "cacklejack",
            available = { state, lookup, holder ->
                holder.alive && Status.hasAbility(state, lookup, holder.id)
            },
        ),
        briefing = { ctx ->
            dayStart(
                ctx,
                "cacklejack",
                BriefingSeverity.ACTION,
                "Cacklejack — ${ctx.holder.name} chooses a player today; mark them Not Me. That " +
                    "player is the one who will NOT change character tonight: a different player does.",
            )
        },
    ),
    tokens = listOf(TokenRule("cacklejack", "Not Me", null, Until.DAWN)),
)

/**
 * "Once per day, you may choose to kill an alive neighbor, if your other alive
 * neighbor agrees."
 *
 * Targets are derived, never free: the two nearest ALIVE seats each way, dead
 * seats skipped, recomputed on every state change (people die mid-day and the
 * Matron reseats them). The ability is spent by the AGREEMENT, not by the
 * death — a victim who survives still burns it — and "neither agrees" does not
 * spend it. A Gangster kill is not an execution.
 */
private fun gangster(): CharacterRule = CharacterRule(
    id = "gangster",
    killCause = DeathCause.DAY_ABILITY,
    day = DayRule(
        ability = DayAbility(
            label = "Gangster kill",
            oncePerDay = true,
            recordsAs = "gangster",
            available = { state, lookup, holder ->
                holder.alive &&
                    Status.hasAbility(state, lookup, holder.id) &&
                    aliveNeighbours(state, holder).size == 2 &&
                    Memory.by(state, LedgerKind.CHOICE, "gangster", holder.id)
                        .none { it.cycle == state.cycle && !it.atNight }
            },
        ),
        briefing = { ctx ->
            val names = aliveNeighbours(ctx.state, ctx.holder).map { it.name }
            dayStart(
                ctx,
                "gangster",
                BriefingSeverity.ACTION,
                if (names.size < 2) {
                    "Gangster — ${ctx.holder.name} has fewer than two living neighbours, so the " +
                        "ability cannot be used as written today. Your call."
                } else {
                    "Gangster — ${ctx.holder.name}'s living neighbours are ${names[0]} and " +
                        "${names[1]}. You must HEAR one of them agree; the other then dies. If both " +
                        "agree, the Gangster picks. If neither agrees, nothing happens and the " +
                        "ability is NOT spent. Once an agreement is reached it is spent for the day " +
                        "even if the victim survives. This is not an execution: the day continues " +
                        "and it does not let a Butcher nominate again."
                },
            )
        },
    ),
)

/**
 * "All players start knowing a player of your alignment. You may choose to kill
 * anyone who nominates them."
 *
 * WP3's built-in fires on any nomination of an Amigo. Two things the card
 * insists on are missing there, so this row replaces it (lead D61):
 *  - an EXILE call is not a nomination and must never trigger the Gnome;
 *  - the Gnome must be able to act — alive, or dead holding a live
 *    `HAS_ABILITY` (a Bone Collector's restore) — and a dead nominator cannot
 *    be killed again.
 * The storyteller may NOT prompt the Gnome: the row states the fact quietly and
 * defaults to "nobody dies". Whenever the built-in WOULD fire this row emits a
 * row of its own — a `CHOICE` when the kill is legal, a targetless `WARN`
 * saying why not otherwise — because `DayRules.triggersFor` suppresses a
 * built-in only for an id the registry actually emitted.
 */
private fun gnome(): CharacterRule = CharacterRule(
    id = "gnome",
    killCause = DeathCause.TRAVELLER_ABILITY,
    day = DayRule(
        onNomination = { ctx ->
            val trigger = gnomeTrigger(ctx)
            listOfNotNull(trigger)
        },
    ),
    tokens = listOf(TokenRule("gnome", "Amigo", null, Until.FOREVER)),
    // W7G: `CharacterRule.setup` has a consumer now, so the row WP4 still owed
    // lives here instead of being a note in a report.
    setup = listOf(
        SetupRequirement(
            id = "gnome.amigo",
            characterId = "gnome",
            kind = RequirementKind.REMINDER,
            title = "Gnome: mark their amigo",
            prompt = "Choose a player and mark them AMIGO. Tell them privately that they are " +
                "the Gnome's amigo. If anyone nominates them, the nominator dies.",
            problem = "Mark the Gnome's amigo before the first night",
            satisfied = { state, _ ->
                state.seats.none { it.characterId?.let(Character::normalizeId) == "gnome" } ||
                    state.seats.any { DayRules.hasToken(state, it.id, "gnome", "Amigo") }
            },
        ),
    ),
)

private fun gnomeTrigger(ctx: NominationContext): NominationTrigger? {
    val gnome = ctx.holder
    val nominee = ctx.nomineeId?.let { ctx.state.player(it) } ?: return null
    val nominator = ctx.nominatorId?.let { ctx.state.player(it) } ?: return null
    if (!DayRules.hasToken(ctx.state, nominee.id, "gnome", "Amigo")) return null
    // Past this point WP3's built-in would fire, so this row must always speak:
    // returning nothing would let the built-in through, because
    // `DayRules.triggersFor` only suppresses a built-in whose id the registry
    // actually emitted.
    val restored = Status.live(ctx.state, ctx.lookup, gnome.id, EffectKind.HAS_ABILITY).isNotEmpty()
    val blocked = when {
        nominee.isTraveller ->
            "This is an exile call, not a nomination — the Gnome does not trigger."
        !nominator.alive ->
            "${nominator.name} is already dead — there is nobody for the Gnome to kill."
        !gnome.alive && !restored ->
            "${gnome.name} is dead and has no restored ability — the Gnome does not trigger."
        else -> null
    }
    if (blocked != null) {
        return NominationTrigger(
            kind = TriggerKind.WARN,
            sourceId = "gnome",
            actorId = gnome.id,
            targetId = null,
            headline = "${nominee.name} carries the Gnome's Amigo token.",
            detail = blocked,
        )
    }
    return NominationTrigger(
        kind = TriggerKind.CHOICE,
        sourceId = "gnome",
        actorId = gnome.id,
        targetId = nominator.id,
        headline = "${nominee.name} is the Gnome's Amigo — ${gnome.name} may kill " +
            "${nominator.name}.",
        detail = "Do NOT prompt the Gnome; they must speak up before voting starts. If they do, " +
            "${nominator.name} dies at once and the vote for ${nominee.name} still happens with " +
            "one fewer living player. ${nominator.name} keeps their ghost vote.",
        options = listOf(
            TriggerOption(DayRules.OPTION_APPLY, "${nominator.name} dies"),
            TriggerOption(DayRules.OPTION_SKIP, "Nobody dies", isDefault = true),
        ),
        impaired = !Status.hasAbility(ctx.state, ctx.lookup, gnome.id),
    )
}

// =====================================================================
// Shared helpers
// =====================================================================

/**
 * The Bureaucrat and the Thief are the same rule with opposite signs: mark one
 * seat other than yourself, every night, and tomorrow their vote is weighted.
 * Dead targets are legal and repeat targets are legal — deliberately NO
 * `DIFFERENT_FROM_LAST_NIGHT`.
 */
private fun markVoter(
    id: String,
    label: String,
    prompt: String,
    briefing: String,
): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.hasAbility),
        prompt = prompt,
        action = {
            ChoosePlayers(
                sourceId = id,
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.NOT_SELF),
                sort = TargetSort.ALIVE_FIRST,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = id,
                        label = label,
                        on = Ref.Target,
                        until = Until.DUSK,
                    ),
                ),
            )
        },
    )
    return CharacterRule(
        id = id,
        firstNight = rule,
        otherNight = rule,
        day = DayRule(
            briefing = { ctx ->
                val marked = ctx.state.seats
                    .filter { DayRules.hasToken(ctx.state, it.id, id, label) }
                    .joinToString { it.name }
                dayStart(
                    ctx,
                    id,
                    BriefingSeverity.INFO,
                    if (marked.isEmpty()) {
                        "${ctx.holder.name} is the ${ctx.lookup(id)?.name ?: id}: nobody is marked " +
                            "today. The mark comes off at dusk, and immediately if they die or are " +
                            "exiled."
                    } else {
                        "$marked's vote $briefing"
                    },
                )
            },
        ),
        tokens = listOf(TokenRule(id, label, null, Until.DUSK)),
    )
}

/** "You may not act until your ability has been granted" — the Apprentice's own first night. */
private fun notGranted(sourceId: String): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    val granted = holder.grants.any {
        Character.normalizeId(it.sourceId) == Character.normalizeId(sourceId)
    }
    if (granted) {
        StepGate.Skip("they already have their granted ability")
    } else {
        StepGate.Fire
    }
}

/** The Bone Collector has nothing to choose with nobody dead. */
private fun someoneIsDead(): WakePredicate = WakePredicate { ctx ->
    if (ctx.state.seats.any { !it.alive }) {
        StepGate.Fire
    } else {
        StepGate.Skip("nobody is dead — there is nothing to restore")
    }
}

/** The day's FIRST execution nomination; exiles never open the Gunslinger's window. */
private fun firstExecutionVoteToday(state: GameState): Nomination? =
    state.nominations.firstOrNull { it.day == state.cycle && !it.isExile }

/** Nearest ALIVE seat each way, dead seats skipped. Empty when there is only one. */
private fun aliveNeighbours(state: GameState, of: Player): List<Player> {
    val seats = state.seats
    val index = seats.indexOfFirst { it.id == of.id }
    if (index < 0) return emptyList()
    val found = LinkedHashMap<Long, Player>()
    for (direction in listOf(-1, +1)) {
        var i = (index + direction + seats.size) % seats.size
        while (i != index) {
            val seat = seats[i]
            if (seat.alive) {
                found[seat.id] = seat
                break
            }
            i = (i + direction + seats.size) % seats.size
        }
    }
    return found.values.toList()
}

/** Whether the storyteller has already nominated an opposing player today (Bishop). */
private fun opposingNominatedToday(
    state: GameState,
    lookup: (String) -> Character?,
    bishopEvil: Boolean,
): Boolean = state.nominations
    .filter { it.day == state.cycle && !it.isExile }
    .any { nomination ->
        val nominee = state.player(nomination.nomineeId) ?: return@any false
        Registration.registersEvil(state, lookup, nominee) != bishopEvil
    }

/** Names the seat the Cacklejack must NOT pick tonight. */
private fun notMeHint(ctx: NightContext): String {
    val marked = ctx.state.seats
        .filter { DayRules.hasToken(ctx.state, it.id, "cacklejack", "Not Me") }
        .joinToString { it.name }
    return if (marked.isEmpty()) {
        "WHO CHANGES CHARACTER, AND INTO WHAT?"
    } else {
        "WHO CHANGES CHARACTER, AND INTO WHAT? (NOT $marked)"
    }
}

/** One DAY_START standing fact, keyed so the ticked-off set survives undo. */
private fun dayStart(
    ctx: BriefingContext,
    sourceId: String,
    severity: BriefingSeverity,
    text: String,
): List<BriefingItem> {
    if (ctx.slot != BriefingSlot.DAY_START) return emptyList()
    return listOf(
        BriefingItem(
            key = "$sourceId:${ctx.holder.id}:${ctx.state.cycle}",
            kind = BriefingKind.STANDING_FACT,
            severity = severity,
            sourceId = sourceId,
            text = text,
            playerId = ctx.holder.id,
        ),
    )
}
