package com.clocktower.engine.rules

import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.Gates
import com.clocktower.engine.GameState
import com.clocktower.engine.Memory
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
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.StandingRule
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
import com.clocktower.engine.WakePredicate

/**
 * Trouble Brewing behaviour (WP7-TB): the 22 resident characters of the base
 * edition. Travellers live in `RulesTravellers.kt`.
 *
 * Everything here is declarative. The night engine reads [CharacterRule.firstNight]
 * / [CharacterRule.otherNight], the kill funnel reads `onDeath`, `DayRules`
 * and `Execution` read `day`, the status engine reads `standing` and
 * `keepsAbilityWhenDead`, and `Tokens` layers `tokens` over its base table.
 *
 * Rows that look empty are not oversights: the Baron is a bag modifier the
 * setup engine derives from `[+2 Outsiders]`, the Mayor's bounce and dusk win
 * live in the one kill funnel and `WinCheck`, and the Recluse only ever
 * *reacts* — its row exists to say "this ability keeps working while dead".
 */
internal val TB_RULES: List<CharacterRule> = listOf(

    // =====================================================================
    // Townsfolk
    // =====================================================================

    /** "You start knowing that 1 of 2 players is a particular Townsfolk." */
    CharacterRule(
        id = "washerwoman",
        firstNight = info(
            "washerwoman",
            "Show the character token of a Townsfolk in play, then point at two players — " +
                "one of them is that character.",
        ),
        tokens = listOf(
            // Night-1 "start knowing" marks are never swept: they are the
            // storyteller's only record of what was shown (lead D9).
            TokenRule("washerwoman", "Townsfolk", until = Until.MANUAL),
            TokenRule("washerwoman", "Wrong", until = Until.MANUAL),
        ),
    ),

    /** "…a particular Outsider. (Or that zero are in play.)" */
    CharacterRule(
        id = "librarian",
        firstNight = info(
            "librarian",
            "Show the character token of an Outsider in play, then point at two players — " +
                "one of them is that character. With no Outsider in play, show a 0.",
        ),
        tokens = listOf(
            TokenRule("librarian", "Outsider", until = Until.MANUAL),
            TokenRule("librarian", "Wrong", until = Until.MANUAL),
        ),
    ),

    /** "…a particular Minion." No zero clause — this one always names a character. */
    CharacterRule(
        id = "investigator",
        firstNight = info(
            "investigator",
            "Show the character token of a Minion in play, then point at two players — " +
                "one of them is that character.",
        ),
        tokens = listOf(
            TokenRule("investigator", "Minion", until = Until.MANUAL),
            TokenRule("investigator", "Wrong", until = Until.MANUAL),
        ),
    ),

    /** "You start knowing how many pairs of evil players there are." */
    CharacterRule(
        id = "chef",
        firstNight = info(
            "chef",
            "Show the finger signal for the number of pairs of neighbouring evil players.",
        ),
    ),

    /** "Each night, you learn how many of your 2 alive neighbours are evil." */
    CharacterRule(
        id = "empath",
        firstNight = info("empath", "Show the finger signal (0, 1, 2) for their evil alive neighbours."),
        otherNight = info("empath", "Show the finger signal (0, 1, 2) for their evil alive neighbours."),
    ),

    /**
     * "Each night, choose 2 players: you learn if either is a Demon."
     *
     * Dead players and the Fortune Teller themselves are legal picks, so the
     * constraint list is deliberately permissive. The red herring is a setup
     * token, placed once and never moved.
     */
    CharacterRule(
        id = "fortuneteller",
        firstNight = fortuneTeller(),
        otherNight = fortuneTeller(),
        tokens = listOf(TokenRule("fortuneteller", "Red Herring", until = Until.FOREVER)),
    ),

    /**
     * "Each night*, you learn which character died by execution today."
     *
     * The gate is the whole character: no execution, or an execution nobody
     * died from, and they do not wake at all. `Execution.execute` places the
     * "Died Today" mark on whoever actually died.
     */
    CharacterRule(
        id = "undertaker",
        otherNight = NightRule(
            gate = Gates.all(Gates.aliveHolder, Gates.executedToday()),
            prompt = "Show the character token of the player who died by execution today.",
            action = { ShowInfo("undertaker", "SHOW THEM", targetsNeeded = 0) },
            infoId = "undertaker",
        ),
        tokens = listOf(TokenRule("undertaker", "Died Today", until = Until.DAWN)),
    ),

    /**
     * "Each night*, choose a player (not yourself): they are safe from the Demon
     * tonight."
     *
     * The token is placed even when the Monk is drunk or poisoned — the grimoire
     * must look normal to a Spy. The effect is inert automatically, because the
     * status recursion asks whether the Monk's ability works before honouring it.
     */
    CharacterRule(
        id = "monk",
        otherNight = NightRule(
            gate = Gates.aliveHolder,
            prompt = "The Monk points to a player other than themselves. Mark that player 'Safe'.",
            action = {
                ChoosePlayers(
                    sourceId = "monk",
                    prompt = "WHO DID THEY CHOOSE?",
                    min = 1,
                    max = 1,
                    constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.NOT_SELF),
                    sort = TargetSort.ALIVE_FIRST,
                    perTarget = listOf(
                        NightEffect.PlaceToken(
                            sourceId = "monk",
                            label = "Safe",
                            on = Ref.Target,
                            kind = EffectKind.SAFE_FROM_DEMON,
                            until = Until.DAWN,
                        ),
                    ),
                )
            },
        ),
        tokens = listOf(
            TokenRule("monk", "Safe", EffectKind.SAFE_FROM_DEMON, Until.DAWN, protects = true),
        ),
    ),

    /**
     * "If you die at night, you are woken to choose a player: you learn their
     * character."
     *
     * The one ability whose gate is the inverse of every other: alive is the
     * reason to skip. Cause, impairment and a same-night resurrection are all
     * irrelevant — they died tonight, so they wake.
     */
    CharacterRule(
        id = "ravenkeeper",
        actsWhileDead = true,
        keepsAbilityWhenDead = true,
        firstNight = ravenkeeper(),
        otherNight = ravenkeeper(),
    ),

    /**
     * "The 1st time you are nominated, if the nominator is a Townsfolk, they are
     * executed immediately."
     *
     * First time in the GAME, not the day. The ability is spent whichever way the
     * storyteller rules — including when the Virgin is drunk or poisoned, which
     * is the one case the built-in row got wrong (it offered the execution as the
     * default). A nominator who does not register as a Townsfolk is a decision,
     * not a verdict: misregistration is the storyteller's to rule on (lead D10).
     */
    CharacterRule(
        id = "virgin",
        day = DayRule(onNomination = ::virginNomination),
        tokens = listOf(TokenRule("virgin", "No Ability", EffectKind.SPENT, Until.FOREVER)),
    ),

    /**
     * "Once per game, during the day, publicly choose a player: if they are the
     * Demon, they die."
     *
     * The shot itself is a day-tab button; its kill goes through the one funnel
     * with [DeathCause.DAY_ABILITY] — a slay is not an execution, so the
     * Undertaker must not see it.
     */
    CharacterRule(
        id = "slayer",
        killCause = DeathCause.DAY_ABILITY,
        day = DayRule(
            ability = DayAbility(
                label = "Slayer shot",
                oncePerGame = true,
                recordsAs = "slayer",
                available = { state, lookup, holder ->
                    holder.alive &&
                        Status.hasAbility(state, lookup, holder.id) &&
                        !isSpent(state, holder, "slayer", lookup("slayer")?.spentLabel.orEmpty())
                },
            ),
        ),
        tokens = listOf(TokenRule("slayer", "No Ability", EffectKind.SPENT, Until.FOREVER)),
    ),

    /**
     * "You are safe from the Demon." Innate, so it tracks seating, character
     * changes and life status: it is derived on every query, never stored, and
     * renders no token (lead D3). `SAFE_FROM_DEMON` also blocks non-kill Demon
     * harm, which is why a Soldier neighbouring a No Dashii is not poisoned.
     */
    CharacterRule(
        id = "soldier",
        standing = StandingRule("soldier") { state, holder, _ ->
            listOf(innate(state, holder, EffectKind.SAFE_FROM_DEMON, "soldier", holder.id))
        },
    ),

    /**
     * "If only 3 players live & no execution occurs, your team wins. If you die
     * at night, another player might die instead."
     *
     * Both halves are cross-cutting and live where they belong: the bounce is
     * step 12 of the kill funnel (after the protections, so a Monk-protected
     * Mayor yields "nobody dies" rather than a redirect) and the dusk win is a
     * `WinCheck` rule. Neither is a night step, and the Mayor never wakes.
     */
    CharacterRule(id = "mayor"),

    // =====================================================================
    // Outsiders
    // =====================================================================

    /**
     * "Each night, choose a player (not yourself): tomorrow, you may only vote if
     * they are voting too."
     *
     * `NOT_SELF` is load-bearing — the rules forbid the Butler's own seat. The
     * vote restriction itself is a day rule (`DayRules.canVote`), not an
     * impairment, so the Master mark carries no rule of its own.
     */
    CharacterRule(
        id = "butler",
        firstNight = butler(),
        otherNight = butler(),
        tokens = listOf(TokenRule("butler", "Master", until = Until.DUSK)),
    ),

    /**
     * "You do not know you are the Drunk. You think you are a Townsfolk
     * character, but you are not."
     *
     * The Drunk never has a row of their own: they wake at the believed
     * character's slot, run that character's step, and everything they do is
     * inert. The source of the effect is their own character, so it must NOT end
     * with its source — that would make the Drunk sober.
     */
    CharacterRule(
        id = "drunk",
        standing = StandingRule("drunk") { state, holder, _ ->
            listOf(
                innate(state, holder, EffectKind.NO_ABILITY, "drunk", null)
                    .copy(endsWithSource = false),
            )
        },
        tokens = listOf(TokenRule("drunk", "Is The Drunk", until = Until.FOREVER)),
    ),

    /**
     * "You might register as evil & as a Minion or Demon, even if dead."
     *
     * Chosen fresh for every query and never automatic: a Recluse registers as
     * its true team until the storyteller rules otherwise, and that ruling is an
     * `Effect(REGISTERS_AS)` plus a ledger RULING (lead D10). The row's whole job
     * is [keepsAbilityWhenDead] — the misregistration outlives the Recluse.
     */
    CharacterRule(id = "recluse", keepsAbilityWhenDead = true),

    /**
     * "If you die by execution, your team loses."
     *
     * The loss itself is `WinCheck`'s, from the execution snapshot. What was
     * missing is the warning BEFORE the vote: this row puts it on the nomination,
     * where the storyteller can still steer the day.
     */
    CharacterRule(
        id = "saint",
        day = DayRule(onNomination = ::saintNomination),
    ),

    // =====================================================================
    // Minions
    // =====================================================================

    /**
     * "Each night, choose a player: they are poisoned tonight and tomorrow day."
     *
     * Until DUSK, so the poison survives the night into the whole of the
     * following day — the Slayer shot, the Virgin trigger and the Mayor bounce
     * all fail while it stands. Self and dead players are legal picks.
     */
    CharacterRule(
        id = "poisoner",
        firstNight = poisoner(),
        otherNight = poisoner(),
        tokens = listOf(
            TokenRule("poisoner", "Poisoned", EffectKind.POISONED, Until.DUSK, impairs = true),
        ),
    ),

    /**
     * "Each night, you see the Grimoire. You might register as good & as a
     * Townsfolk or Outsider, even if dead."
     *
     * A wake with no question: the storyteller shows the grimoire. The
     * registration half keeps working after death, so the row declares it.
     */
    CharacterRule(
        id = "spy",
        keepsAbilityWhenDead = true,
        firstNight = spy(),
        otherNight = spy(),
    ),

    /**
     * "If there are 5 or more players alive & the Demon dies, you become the
     * Demon. (Travellers don't count.)"
     *
     * The catch is a death trigger, counted on the board as it was JUST BEFORE
     * the Demon died and only while the Scarlet Woman's own ability works. It
     * marks her and queues the obligation; the storyteller confirms the change of
     * character, because on a multi-Demon script she becomes the Demon that died.
     *
     * Her night row exists only to show her the token, so it fires only once she
     * carries the mark.
     */
    CharacterRule(
        id = "scarletwoman",
        otherNight = NightRule(
            gate = Gates.all(Gates.aliveHolder, becameTheDemon()),
            prompt = "Show the 'You are' card, then the Demon token they have become.",
        ),
        onDeath = listOf(
            DeathTrigger(gate = ::scarletWomanCatches, produce = ::scarletWomanPromotion),
        ),
        tokens = listOf(TokenRule("scarletwoman", "Is The Demon", until = Until.FOREVER)),
    ),

    /**
     * "There are extra Outsiders in play. [+2 Outsiders]"
     *
     * Setup only, and permanent: the bag change does not revert when the Baron
     * dies, and poison cannot undo it. The bracket in the ability text is what
     * the setup engine reads, so this row declares no bag shape of its own.
     */
    CharacterRule(id = "baron"),

    // =====================================================================
    // Demon
    // =====================================================================

    /**
     * "Each night*, choose a player: they die. If you kill yourself this way, a
     * Minion becomes the Imp."
     *
     * Dead players and the Imp's own seat are legal picks, and "no kill" is a
     * real answer. A silenced Imp is REDUCED rather than skipped, so the wake
     * still happens and nothing they choose kills (lead D36/D63).
     */
    CharacterRule(
        id = "imp",
        killCause = DeathCause.DEMON_KILL,
        otherNight = NightRule(
            gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
            prompt = "The Imp points to a player. That player dies.",
            action = {
                ChoosePlayers(
                    sourceId = "imp",
                    prompt = "WHO DID THEY CHOOSE?",
                    min = 1,
                    max = 1,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.ALIVE_FIRST,
                    allowNone = true,
                    noneLabel = "No kill (impaired, protected, or storyteller's choice)",
                    perTarget = listOf(
                        NightEffect.Attack(on = Ref.Target, cause = DeathCause.DEMON_KILL),
                    ),
                )
            },
        ),
        onDeath = listOf(DeathTrigger(gate = ::impKilledItself, produce = ::impStarPass)),
        // The kill funnel shrouds the seat itself, so the "Dead" mark is the
        // storyteller's own bookkeeping. It is declared, not placed: an attack
        // that a Monk or a Soldier stopped must not leave a "Dead" token on a
        // living player. The rule gives a hand-placed one its dawn sweep.
        tokens = listOf(TokenRule("imp", "Dead", until = Until.DAWN)),
    ),
)

// =========================================================================
// Night-rule shapes
// =========================================================================

/**
 * A zero-target information step: the answer is computed, never picked. The
 * three "you start knowing" steps are this shape too — the pair of players and
 * the character shown are prepared before the first night and replayed here.
 */
private fun info(id: String, prompt: String): NightRule = NightRule(
    gate = Gates.aliveHolder,
    prompt = prompt,
    action = { ShowInfo(id, "SHOW THEM", targetsNeeded = 0) },
    infoId = id,
)

private fun fortuneTeller(): NightRule = NightRule(
    gate = Gates.aliveHolder,
    prompt = "They point at two players. Nod or shake for whether one of them is the Demon. " +
        "Dead players and the Fortune Teller themselves are legal choices.",
    action = {
        ShowInfo(
            sourceId = "fortuneteller",
            prompt = "WHICH TWO DID THEY CHOOSE?",
            targetsNeeded = 2,
            constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
        )
    },
    infoId = "fortuneteller",
)

private fun ravenkeeper(): NightRule = NightRule(
    gate = Gates.diedTonight(),
    prompt = "They died tonight — wake them. They point at a player; show that player's character token.",
    action = {
        ShowInfo(
            sourceId = "ravenkeeper",
            prompt = "WHO DID THEY CHOOSE?",
            targetsNeeded = 1,
            constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
        )
    },
    infoId = "ravenkeeper",
)

private fun butler(): NightRule = NightRule(
    gate = Gates.aliveHolder,
    prompt = "The Butler points to a player other than themselves. Mark that player as 'Master'.",
    action = {
        ChoosePlayers(
            sourceId = "butler",
            prompt = "WHO DID THEY CHOOSE?",
            min = 1,
            max = 1,
            constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.NOT_SELF),
            sort = TargetSort.ALIVE_FIRST,
            perTarget = listOf(
                NightEffect.PlaceToken(
                    sourceId = "butler",
                    label = "Master",
                    on = Ref.Target,
                    kind = EffectKind.MARKER,
                    until = Until.DUSK,
                ),
            ),
        )
    },
)

private fun poisoner(): NightRule = NightRule(
    gate = Gates.aliveHolder,
    prompt = "The Poisoner points to a player. That player is poisoned tonight and tomorrow day.",
    action = {
        ChoosePlayers(
            sourceId = "poisoner",
            prompt = "WHO DID THEY CHOOSE?",
            min = 1,
            max = 1,
            constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
            sort = TargetSort.ALIVE_FIRST,
            perTarget = listOf(
                NightEffect.PlaceToken(
                    sourceId = "poisoner",
                    label = "Poisoned",
                    on = Ref.Target,
                    kind = EffectKind.POISONED,
                    until = Until.DUSK,
                ),
            ),
        )
    },
)

private fun spy(): NightRule = NightRule(
    gate = Gates.aliveHolder,
    prompt = "Show the Grimoire to the Spy for as long as they need.",
)

/** The Scarlet Woman wakes only once she has been marked as the new Demon. */
private fun becameTheDemon(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    if (carries(ctx.state, holder.id, "scarletwoman", "Is The Demon")) {
        StepGate.Fire
    } else {
        StepGate.Skip("they have not become the Demon")
    }
}

// =========================================================================
// Death triggers
// =========================================================================

/**
 * "If there are 5 or more players alive & the Demon dies, you become the Demon."
 *
 * The count is the board as it was *just before* the Demon died, so the seat
 * that has this instant been shrouded is added back. Travellers never count.
 *
 * NOTE FOR WP2: `DeathTrigger` is handed no character lookup, so "is this
 * Scarlet Woman's ability working?" is answered from the stored effects alone.
 * That covers poison and drunkenness; it cannot see a standing rule.
 */
private fun scarletWomanCatches(state: GameState, event: DeathEvent, holder: Player): Boolean {
    if (event.teamAtDeath != Team.DEMON) return false
    if (event.playerId == holder.id) return false
    if (event.registeredOnly) return false
    if (!holder.alive) return false
    if (impairedByToken(state, holder.id)) return false
    if (carries(state, holder.id, "scarletwoman", "Is The Demon")) return false
    val deadWasResident = state.player(event.playerId)?.isTraveller == false
    val aliveBefore = state.aliveCountResidents + if (deadWasResident) 1 else 0
    return aliveBefore >= SCARLET_WOMAN_MINIMUM
}

private fun scarletWomanPromotion(
    state: GameState,
    event: DeathEvent,
    holder: Player,
): TriggerResult {
    val demon = state.player(event.playerId)?.name ?: "the Demon"
    return TriggerResult(
        prompts = listOf(
            Prompt(
                id = 0,
                at = BriefingSlot.NOW,
                kind = PromptKind.CHOOSE_CHARACTER,
                sourceId = "scarletwoman",
                subjectPlayerId = holder.id,
                targetIds = listOf(event.playerId),
                characterIds = listOfNotNull(event.characterIdAtDeath),
                title = "${holder.name} becomes the Demon — $demon has died.",
                detail = "Change their character to the Demon that died, then wake them tonight " +
                    "to show the 'You are' card and the token.",
            ),
        ),
        effects = listOf(
            // Placed by the funnel, which stamps the id (`Effect(id = 0, …)`).
            Effect(
                id = 0,
                kind = EffectKind.MARKER,
                targetId = holder.id,
                sourceCharacterId = "scarletwoman",
                sourcePlayerId = holder.id,
                until = Until.FOREVER,
                // A permanent change of character: it must not evaporate when
                // she is later poisoned.
                endsWithSource = false,
                label = "Is The Demon",
                note = "Caught the Demon when $demon died.",
                createdCycle = state.cycle,
                createdAtNight = state.phase != Phase.DAY,
            ),
        ),
    )
}

/**
 * "If you kill yourself this way, a Minion becomes the Imp."
 *
 * Whether an heir exists is asked as "is anybody else left", not "is a Minion
 * left": the trigger has no character lookup to read a team with (filed for
 * WP2), and the storyteller picks the heir from the prompt either way.
 */
private fun impKilledItself(state: GameState, event: DeathEvent, holder: Player): Boolean {
    if (event.playerId != holder.id) return false
    if (event.killerPlayerId != holder.id) return false
    return state.seats.any { it.id != holder.id && it.alive && !it.isTraveller }
}

private fun impStarPass(state: GameState, event: DeathEvent, holder: Player): TriggerResult =
    TriggerResult(
        prompts = listOf(
            Prompt(
                id = 0,
                at = BriefingSlot.NOW,
                kind = PromptKind.CHOOSE_PLAYER,
                sourceId = "imp",
                subjectPlayerId = holder.id,
                title = "${holder.name} killed themselves — a Minion becomes the Imp.",
                detail = "Choose an alive Minion, change their character, then show them the " +
                    "'You are' card and the Imp token. They do not act again tonight.",
                dueCycle = state.cycle,
            ),
        ),
    )

// =========================================================================
// Day rows
// =========================================================================

/**
 * The Virgin's nomination row. It supersedes the built-in of the same id
 * (lead D61), and differs from it in one place that matters at 1am: an impaired
 * Virgin does not execute anybody, and the storyteller is not offered the
 * execution as the default.
 */
private fun virginNomination(ctx: NominationContext): List<NominationTrigger> {
    val virgin = ctx.holder
    if (ctx.nomineeId != virgin.id || !virgin.alive) return emptyList()
    // "The 1st time you are nominated" — in the game, not today.
    if (ctx.state.nominations.any { it.nomineeId == virgin.id && !it.isExile }) return emptyList()
    val spentLabel = ctx.lookup("virgin")?.spentLabel.orEmpty()
    if (isSpent(ctx.state, virgin, "virgin", spentLabel)) return emptyList()

    val nominator = ctx.nominatorId?.let { ctx.state.player(it) }
    val nominatorName = nominator?.name ?: "the nominator"
    // The TRUE character decides this, through the registration set: a Drunk who
    // believes they are the Chef is an Outsider and does not fire the Virgin.
    val townsfolk = nominator != null &&
        Team.TOWNSFOLK in Registration.registersAs(ctx.state, ctx.lookup, nominator)
    val works = Status.hasAbility(ctx.state, ctx.lookup, virgin.id)

    val kind = when {
        !works -> TriggerKind.WARN
        townsfolk -> TriggerKind.AUTO_EXECUTION
        // Not a Townsfolk *right now* — but who a seat registers as is the
        // storyteller's ruling, so this is a question, not a verdict.
        else -> TriggerKind.CHOICE
    }
    val headline = when {
        !works -> "${virgin.name} is the Virgin, but their ability is not working — " +
            "nothing happens. Do not say why."
        townsfolk -> "$nominatorName is executed immediately — the Virgin's first nomination."
        else -> "$nominatorName does not register as a Townsfolk — you decide whether the " +
            "Virgin's ability fires."
    }
    return listOf(
        NominationTrigger(
            kind = kind,
            sourceId = "virgin",
            actorId = virgin.id,
            targetId = nominator?.id,
            headline = headline,
            detail = "The Virgin's ability is spent either way, and the day ends if the " +
                "nominator is executed.",
            options = listOf(
                TriggerOption(
                    DayRules.OPTION_EXECUTE,
                    "Execute $nominatorName",
                    isDefault = works && townsfolk,
                ),
                TriggerOption(
                    DayRules.OPTION_REGISTERS_GOOD,
                    "Nothing happens — the ability is still spent",
                    isDefault = !works || !townsfolk,
                ),
                TriggerOption(DayRules.OPTION_SKIP, "Not a real nomination — spend nothing"),
            ),
            impaired = !works,
        ),
    )
}

/** "If you die by execution, your team loses" — said BEFORE the vote, not after. */
private fun saintNomination(ctx: NominationContext): List<NominationTrigger> {
    val saint = ctx.holder
    if (ctx.nomineeId != saint.id || !saint.alive) return emptyList()
    val works = Status.hasAbility(ctx.state, ctx.lookup, saint.id)
    return listOf(
        NominationTrigger(
            kind = TriggerKind.WARN,
            sourceId = "saint",
            actorId = saint.id,
            targetId = ctx.nominatorId,
            headline = if (works) {
                "${saint.name} is the SAINT — if this execution kills them, their team loses " +
                    "and the game ends."
            } else {
                "${saint.name} is the Saint, but their ability is not working — executing them " +
                    "is safe. Do not tell the town."
            },
            detail = "Only an execution triggers it: a Demon kill, a Slayer shot or an exile " +
                "does nothing.",
            impaired = !works,
        ),
    )
}

// =========================================================================
// Shared helpers
// =========================================================================

/** "Travellers don't count" — the Scarlet Woman's own threshold. */
private const val SCARLET_WOMAN_MINIMUM = 5

/**
 * An innate effect, derived on every query and stamped with the holder's
 * `standingSince` so any placed effect outranks it. `label = ""` renders no
 * token: the ability is printed on the character, not on the grimoire.
 */
private fun innate(
    state: GameState,
    holder: Player,
    kind: EffectKind,
    sourceCharacterId: String,
    sourcePlayerId: Long?,
): Effect = Effect(
    id = holder.standingSince,
    kind = kind,
    targetId = holder.id,
    sourceCharacterId = sourceCharacterId,
    sourcePlayerId = sourcePlayerId,
    until = Until.FOREVER,
    label = "",
    createdCycle = state.cycle,
    createdAtNight = state.phase != Phase.DAY,
    derived = true,
)

/** True when this seat carries `(sourceId, label)` as a token or as an effect. */
private fun carries(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
    val key = Tokens.key(sourceId, label)
    val seat = state.player(playerId) ?: return false
    return seat.reminders.any { Tokens.key(it) == key } ||
        state.effects.any {
            it.targetId == playerId &&
                !it.suspended &&
                Tokens.key(it.sourceCharacterId, it.label) == key
        }
}

/** A once-per-game ability already used: the SPENT effect or the official mark. */
private fun isSpent(
    state: GameState,
    holder: Player,
    sourceId: String,
    spentLabel: String,
): Boolean {
    val id = Character.normalizeId(sourceId)
    if (Memory.isSpent(state, sourceId, holder.id)) return true
    if (state.effects.any {
            it.targetId == holder.id &&
                it.kind == EffectKind.SPENT &&
                Character.normalizeId(it.sourceCharacterId) == id
        }
    ) {
        return true
    }
    return spentLabel.isNotEmpty() && carries(state, holder.id, sourceId, spentLabel)
}

/**
 * Impairment from stored effects only. `Status.isImpaired` needs a character
 * lookup, which `DeathTrigger` is not given (filed for WP2).
 */
private fun impairedByToken(state: GameState, playerId: Long): Boolean =
    state.effects.any {
        it.targetId == playerId &&
            !it.suspended &&
            (
                it.kind == EffectKind.DRUNK ||
                    it.kind == EffectKind.POISONED ||
                    it.kind == EffectKind.NO_ABILITY
                )
    }
