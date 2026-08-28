package com.clocktower.engine.rules

import com.clocktower.engine.ActionOption
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.CardOffer
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.Effects
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.KillSuppression
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Memory
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Options
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.ShowCardSpec
import com.clocktower.engine.StandingRule
import com.clocktower.engine.Status
import com.clocktower.engine.StepGate
import com.clocktower.engine.Team
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.TokenRule
import com.clocktower.engine.Tokens
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.TriggerResult
import com.clocktower.engine.Until
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate
import com.clocktower.engine.YesNo

/**
 * Experimental Townsfolk behaviour (WP7-EXP-T).
 *
 * Thirty characters: acrobat, alchemist, alsaahir, amnesiac, atheist, balloonist,
 * banshee, bountyhunter, cannibal, choirboy, cultleader, engineer, farmer,
 * fisherman, general, highpriestess, huntsman, king, knight, lycanthrope,
 * magician, nightwatchman, noble, pixie, poppygrower, preacher, princess,
 * shugenja, steward, villageidiot.
 *
 * Conventions used throughout (ARCHITECTURE §7):
 *  - Official Title Case labels only; every [TokenRule] here matches the copy
 *    count in `characters.json` (`TokensTest` fails the build otherwise).
 *  - Once per game = `Character.spentLabel` + [Gates.notSpent] + `MarkSpent`.
 *  - Kills go through `NightEffect.Attack`, never a direct call.
 *  - An id in [NightRule.infoId] that `InfoCalc` does not support is deliberate:
 *    it suppresses the planner's fallback `ShowInfo` and self-documents the
 *    calculator WP2 still owes (see [MISSING_INFO_IDS]).
 */
internal val EXP_TOWNSFOLK_RULES: List<CharacterRule> = listOf(
    acrobat(),
    alchemist(),
    alsaahir(),
    amnesiac(),
    atheist(),
    balloonist(),
    banshee(),
    bountyHunter(),
    cannibal(),
    choirboy(),
    cultLeader(),
    engineer(),
    farmer(),
    fisherman(),
    general(),
    highPriestess(),
    huntsman(),
    king(),
    knight(),
    lycanthrope(),
    magician(),
    nightwatchman(),
    noble(),
    pixie(),
    poppyGrower(),
    preacher(),
    princess(),
    shugenja(),
    steward(),
    villageIdiot(),
)

/**
 * `InfoCalc` keys these rows name but the calculator does not implement yet.
 * Filed to WP2 as one batch; until then the step is a marker row with a prompt
 * and (where the answer is a token) a pre-filled card.
 */
internal val MISSING_INFO_IDS: List<String> = listOf(
    "acrobat", "amnesiac", "banshee", "choirboy", "engineer", "farmer",
    "general", "highpriestess", "huntsman", "king.demon", "lycanthrope",
    "magician", "nightwatchman", "pixie", "poppygrower", "preacher", "princess",
)

// ---------------------------------------------------------------------------
// acrobat
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a player: if they are or become drunk or poisoned
 * tonight, you die."
 *
 * The death is a HIGH-WATER MARK over the whole night, not a point-in-time
 * query, and the engine has no `GameState.nightImpaired` watermark yet
 * (digest §group 9). The nearest expressible thing is a dawn obligation the
 * storyteller discharges, so the ruling is never lost between here and dawn.
 */
private fun acrobat() = CharacterRule(
    id = "acrobat",
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Acrobat points at a player. They learn nothing — show them nothing.",
        infoId = "acrobat",
        action = {
            ChoosePlayers(
                sourceId = "acrobat",
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.SEAT_ORDER,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "acrobat",
                        label = "Chosen",
                        on = Ref.Target,
                        kind = EffectKind.MARKER,
                        until = Until.DAWN,
                    ),
                ),
                onResolve = listOf(
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.DAWN,
                        kind = PromptKind.RESOLVE_KILL,
                        sourceId = "acrobat",
                        on = Ref.Source,
                        title = "Acrobat: if the player they chose IS or BECAME drunk or " +
                            "poisoned at any point tonight, the Acrobat dies now.",
                    ),
                ),
            )
        },
    ),
)

// ---------------------------------------------------------------------------
// alchemist
// ---------------------------------------------------------------------------

/**
 * "You have a Minion ability."
 *
 * The granted ability is an `AbilityGrant(sourceId = "alchemist")` placed at
 * setup (WP4, `alchemist.grant`), so `Identity.actingRoles` already gives the
 * seat a second row at the Minion's own night-order index (lead D43). All this
 * row owes is the night-1 reveal.
 */
private fun alchemist() = CharacterRule(
    id = "alchemist",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Wake the Alchemist. Show the 'You are' token and the Minion character " +
            "token they hold. They do NOT wake with the Minions and do not learn the Demon.",
        cards = { ctx ->
            val granted = ctx.holder
                ?.grants
                ?.firstOrNull { Character.normalizeId(it.sourceId) == "alchemist" }
                ?.abilityId
                ?.let(Character::normalizeId)
            if (granted.isNullOrEmpty()) {
                emptyList()
            } else {
                listOf(
                    CardOffer(
                        label = "SHOW: YOU ARE",
                        card = ShowCardSpec.CharacterCard("YOU ARE", granted),
                        truthful = true,
                    ),
                )
            }
        },
    ),
)

// ---------------------------------------------------------------------------
// alsaahir
// ---------------------------------------------------------------------------

/** "Each day, if you publicly guess which players are Minion(s) and Demon(s), good wins." */
private fun alsaahir() = CharacterRule(
    id = "alsaahir",
    day = DayRule(
        ability = DayAbility(
            label = "Alsaahir guess",
            oncePerDay = true,
            recordsAs = "alsaahir",
            available = { _, _, holder -> holder.alive },
        ),
    ),
)

// ---------------------------------------------------------------------------
// amnesiac
// ---------------------------------------------------------------------------

/**
 * "You do not know what your ability is."
 *
 * The invented ability lives in `decisions["amnesiac.ability"]` (WP4). A
 * passive ability must emit NO step, so the gate asks once rather than
 * blocking dawn on a row that has nothing behind it.
 */
private fun amnesiac(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, amnesiacWakes()),
        prompt = "Run whatever the Amnesiac's invented ability needs. Mark each seat " +
            "involved with a '?' token and write what it means on the token.",
        infoId = "amnesiac",
        action = {
            ChoosePlayers(
                sourceId = "amnesiac",
                prompt = "WHO IS INVOLVED TONIGHT?",
                min = 0,
                max = 3,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.SEAT_ORDER,
                allowNone = true,
                noneLabel = "Nobody — the ability needs no target tonight",
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "amnesiac",
                        label = "?",
                        on = Ref.Target,
                        kind = EffectKind.MARKER,
                        until = Until.MANUAL,
                    ),
                ),
            )
        },
    )
    return CharacterRule(
        id = "amnesiac",
        firstNight = rule,
        otherNight = rule,
        // "?" is deliberately in NO expiry table: nobody knows what the ability is.
        tokens = listOf(TokenRule("amnesiac", "?", null, Until.MANUAL, copies = 3)),
    )
}

/** The storyteller wrote an ability that wakes them; otherwise ask, never assume. */
private fun amnesiacWakes(): WakePredicate = WakePredicate { ctx ->
    val written = ctx.state.decisions["amnesiac.ability"].orEmpty().isNotBlank()
    if (written) {
        StepGate.Fire
    } else {
        StepGate.Conditional(
            question = "Does the Amnesiac's ability wake them tonight?",
            yesLabel = "Yes — run it",
            noLabel = "No — it is passive",
        )
    }
}

// ---------------------------------------------------------------------------
// atheist
// ---------------------------------------------------------------------------

/**
 * "The Storyteller can break the game rules, and if executed, good wins, even
 * if you are dead."
 *
 * The bag shape (`Setup.bagShapeFor`), the storyteller nominee seat and the
 * win advisories are WP3/WP4's; this row owns the "even if you are dead" clause
 * and the confirmation the day tab must show.
 */
private fun atheist() = CharacterRule(
    id = "atheist",
    keepsAbilityWhenDead = true,
    day = DayRule(
        onExecution = { ctx ->
            if (ctx.record.playerId != GameState.STORYTELLER_SEAT_ID) {
                emptyList()
            } else {
                listOf(
                    ExecutionConsequence(
                        sourceId = "atheist",
                        headline = "The Storyteller was executed — GOOD WINS.",
                        detail = "${ctx.holder.name} is the Atheist; this holds even though " +
                            "they are dead.",
                        impaired = false,
                    ),
                )
            }
        },
    ),
)

// ---------------------------------------------------------------------------
// balloonist
// ---------------------------------------------------------------------------

/**
 * "Each night, you learn a player of a different character type than last night."
 *
 * W7E: `TargetConstraint.DIFFERENT_TYPE_FROM_LAST_NIGHT` reads the ledger, so it
 * survives the token sweep exactly as `DIFFERENT_FROM_LAST_NIGHT` does — the old
 * row could only state the constraint in the prompt and hope.
 */
private fun balloonist(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at a player of a DIFFERENT character type to the one shown last " +
            "night. Move the Know token to them. They do not learn the type.",
        action = { ctx ->
            ChoosePlayers(
                sourceId = "balloonist",
                prompt = "WHO DID THEY LEARN?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.DIFFERENT_TYPE_FROM_LAST_NIGHT,
                ),
                sort = TargetSort.SEAT_ORDER,
                // The token MOVES: the setup one is a hand-placed reminder, which
                // `Effects.place` cannot displace on its own.
                perTarget = clearToken(ctx.state, "balloonist", "Know") +
                    NightEffect.PlaceToken(
                        sourceId = "balloonist",
                        label = "Know",
                        on = Ref.Target,
                        kind = EffectKind.MARKER,
                        until = Until.FOREVER,
                    ),
            )
        },
    )
    return CharacterRule(
        id = "balloonist",
        firstNight = rule,
        otherNight = rule,
        // The Know token MOVES; it is in neither expiry table.
        tokens = listOf(TokenRule("balloonist", "Know", null, Until.FOREVER, copies = 1)),
    )
}

// ---------------------------------------------------------------------------
// banshee
// ---------------------------------------------------------------------------

/**
 * "If the Demon kills you, all players learn this. From now on, you may
 * nominate twice per day and vote twice per nomination."
 *
 * The awakening is a [DeathTrigger] so it lands the instant the kill resolves
 * (the night row is only the announcement checkpoint). `DayRules` already reads
 * the `banshee/Has Ability` token for the double nomination and vote.
 */
private fun banshee() = CharacterRule(
    id = "banshee",
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    otherNight = NightRule(
        gate = bansheeAwokeTonight(),
        prompt = "Announce publicly that the Banshee has died. Do not say who.",
        infoId = "banshee",
        wakeCounts = WakeCount.NONE,
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW: THE BANSHEE HAS AWOKEN",
                    card = ShowCardSpec.Message("THE BANSHEE HAS AWOKEN"),
                    truthful = true,
                ),
            )
        },
    ),
    onDeath = listOf(
        DeathTrigger(
            gate = { _, _, event, holder ->
                event.playerId == holder.id &&
                    isDemonKill(event) &&
                    event.abilityImpairedAtDeath != true
            },
            produce = { state, _, _, holder ->
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = BriefingSlot.DAWN,
                            kind = PromptKind.ANNOUNCE,
                            sourceId = "banshee",
                            subjectPlayerId = holder.id,
                            title = "Announce publicly: the Banshee has died.",
                            detail = "From now on ${holder.name} may nominate twice per day " +
                                "and vote twice per nomination, and never spends a ghost vote.",
                        ),
                    ),
                    effects = listOf(
                        Effect(
                            id = 0,
                            kind = EffectKind.HAS_ABILITY,
                            targetId = holder.id,
                            sourceCharacterId = "banshee",
                            sourcePlayerId = holder.id,
                            until = Until.FOREVER,
                            endsWithSource = false,
                            label = "Has Ability",
                            note = "The Banshee was killed by the Demon while sober and healthy.",
                            createdCycle = state.cycle,
                            createdAtNight = state.phase != Phase.DAY,
                        ),
                    ),
                )
            },
        ),
    ),
)

/** Fires only on the night the Demon actually killed this Banshee. */
private fun bansheeAwokeTonight(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Banshee seat")
    val killed = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.day == ctx.night && it.atNight &&
            it.resurrectedAtCycle == null
    }
    when {
        killed == null -> StepGate.Skip("the Banshee did not die tonight")
        !isDemonKill(killed) ->
            StepGate.Skip("the Banshee did not die to the Demon — say nothing")
        killed.abilityImpairedAtDeath == true ->
            StepGate.Skip("the Banshee was drunk or poisoned when they died — say nothing")
        else -> StepGate.Fire
    }
}

// ---------------------------------------------------------------------------
// bountyhunter
// ---------------------------------------------------------------------------

/**
 * "You start knowing 1 evil player. If the player you know dies, you learn
 * another evil player tonight."
 *
 * The other-night wake condition is COMPUTED from the Know token and the death
 * list, never remembered: exactly the shape the Pukka's standing victim uses.
 */
private fun bountyHunter() = CharacterRule(
    id = "bountyhunter",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at the player marked Know. The Bounty Hunter learns the PLAYER, " +
            "not the character.",
    ),
    otherNight = NightRule(
        gate = knownPlayerDied(),
        prompt = "Point at a new evil player and move the Know token to them.",
        action = { ctx ->
            ChoosePlayers(
                sourceId = "bountyhunter",
                prompt = "WHICH EVIL PLAYER DID THEY LEARN?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.NOT_CHOSEN_BEFORE,
                ),
                sort = TargetSort.SEAT_ORDER,
                allowNone = true,
                noneLabel = "Every evil player has already been learned",
                perTarget = clearToken(ctx.state, "bountyhunter", "Know") +
                    NightEffect.PlaceToken(
                        sourceId = "bountyhunter",
                        label = "Know",
                        on = Ref.Target,
                        kind = EffectKind.MARKER,
                        until = Until.FOREVER,
                    ),
            )
        },
    ),
    tokens = listOf(TokenRule("bountyhunter", "Know", null, Until.FOREVER, copies = 1)),
)

/** "If the player you know dies" — died tonight, or died earlier today. */
private fun knownPlayerDied(): WakePredicate = WakePredicate { ctx ->
    val key = Tokens.key("bountyhunter", "Know")
    val marked = ctx.state.seats.firstOrNull { seat ->
        seat.reminders.any { Tokens.key(it) == key } ||
            ctx.state.effects.any {
                it.targetId == seat.id && Tokens.key(it.sourceCharacterId, it.label) == key
            }
    } ?: return@WakePredicate StepGate.Conditional(
        question = "No player is marked Know. Did the player the Bounty Hunter knows die?",
        yesLabel = "Yes — they learn a new evil player",
        noLabel = "No — skip",
    )
    if (marked.alive) {
        return@WakePredicate StepGate.Skip("${marked.name} (marked Know) is still alive")
    }
    val died = ctx.state.deaths.any {
        it.playerId == marked.id && it.resurrectedAtCycle == null &&
            ((it.day == ctx.night && it.atNight) || (it.day == ctx.night - 1 && !it.atNight))
    }
    if (died) StepGate.Fire else StepGate.Skip("${marked.name} did not die today or tonight")
}

// ---------------------------------------------------------------------------
// cannibal
// ---------------------------------------------------------------------------

/**
 * "You have the ability of the recently killed executee."
 *
 * The Cannibal has no night-order slot of its own by design: `Identity`
 * derives a `REPLACE` grant from the `cannibal/Lunch` token and wakes the seat
 * at the EATEN character's index (lead D43), and `Execution.consequences`
 * already owns the day-side Lunch / poison reminder. This row therefore
 * deliberately declares no night rule — a `firstNight`/`otherNight` here would
 * create a second, wrong row at the Cannibal's own (non-existent) slot.
 */
private fun cannibal() = CharacterRule(id = "cannibal")

// ---------------------------------------------------------------------------
// choirboy
// ---------------------------------------------------------------------------

/**
 * "If the Demon kills the King, you learn which player is the Demon."
 *
 * The row exists ONLY on the nights it fires — the sheet gets shorter, which is
 * the point. A Minion kill, an execution, or an attack the King survived all
 * leave no row.
 */
private fun choirboy() = CharacterRule(
    id = "choirboy",
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, kingKilledByDemon()),
        prompt = "Wake the Choirboy and point at the Demon. They learn the PLAYER, " +
            "not the character.",
        infoId = "choirboy",
    ),
    // NO `onDeath` row: the trigger is the KING's death, and the gate above already
    // derives it from the death list the moment the kill lands. A DeathTrigger
    // queuing a `Prompt(at = TONIGHT)` would build a SECOND choirboy row
    // (`StepVariant.AGAIN`) beside the one the night order already places.
)

/** A King died to a Demon's own ability tonight. */
private fun kingKilledByDemon(): WakePredicate = WakePredicate { ctx ->
    val fired = ctx.state.deaths.any {
        it.day == ctx.night && it.atNight && !it.registeredOnly &&
            it.resurrectedAtCycle == null &&
            Character.normalizeId(it.characterIdAtDeath.orEmpty()) == "king" &&
            isDemonKill(it)
    }
    if (fired) {
        StepGate.Fire
    } else {
        StepGate.Skip("the Demon has not killed the King")
    }
}

// ---------------------------------------------------------------------------
// cultleader
// ---------------------------------------------------------------------------

/**
 * "Each night, you become the alignment of an alive neighbor."
 *
 * W7E: `NightEffect.SetAlignment` writes the side and nothing else. Before it
 * existed the only identity effect was `BecomeCharacter`, which strips the
 * seat's tokens, clears `shownCharacterId` and writes an `IdentityRecord` — so
 * the planner would have inserted a bogus "new character" row every night, and
 * the row had to ask the storyteller to do it by hand instead.
 *
 * Which side is still the storyteller's call whenever the neighbours disagree,
 * so this is a three-way [Options] rather than a computed flip:
 * `InfoCalc.cultleader` supplies the neighbours and their alignments, and "no
 * change" is the answer that does not wake anybody.
 */
private fun cultLeader(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Both alive neighbours the same alignment? The change is FORCED. " +
            "Wake them ONLY if the alignment actually changed, then show the thumb.",
        infoId = "cultleader",
        action = {
            Options(
                sourceId = "cultleader",
                prompt = "WHICH ALIGNMENT DOES THE CULT LEADER TAKE TONIGHT?",
                options = listOf(
                    ActionOption(
                        id = "none",
                        label = "No change — do not wake them",
                    ),
                    ActionOption(
                        id = "evil",
                        label = "They join the evil neighbour",
                        detail = "Wake them and show a thumbs-down.",
                        effects = listOf(
                            NightEffect.SetAlignment(
                                on = Ref.Source,
                                evil = true,
                                note = "Cult Leader: joined an evil neighbour.",
                            ),
                            NightEffect.ShowCardTo(Ref.Source, "YOU ARE — evil (thumbs down)"),
                        ),
                    ),
                    ActionOption(
                        id = "good",
                        label = "They join the good neighbour",
                        detail = "Wake them and show a thumbs-up.",
                        effects = listOf(
                            NightEffect.SetAlignment(
                                on = Ref.Source,
                                evil = false,
                                note = "Cult Leader: joined a good neighbour.",
                            ),
                            NightEffect.ShowCardTo(Ref.Source, "YOU ARE — good (thumbs up)"),
                        ),
                    ),
                ),
            )
        },
    )
    return CharacterRule(
        id = "cultleader",
        firstNight = rule,
        otherNight = rule,
        day = DayRule(
            ability = DayAbility(
                label = "Form a cult",
                oncePerDay = true,
                recordsAs = "cultleader",
                available = { _, _, holder -> holder.alive },
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// engineer
// ---------------------------------------------------------------------------

/**
 * "Once per game, at night, choose which Minions or which Demon is in play."
 *
 * The rebuild is SEVERAL seats and several characters at once, which no single
 * `NightAction` carries: `ChoosePlayerAndCharacter` is one pair. The row
 * therefore records WHICH seats are being rebuilt and spends the ability; the
 * storyteller assigns each new character from the seat sheet, which already
 * clears the old character's tokens (`Identity.changeCharacter`) and — since
 * lead D67 — keeps the seat's alignment by default.
 *
 * W7D closed the dangerous half of this gap: `BecomeCharacter` with an empty
 * character id and nothing picked used to WIPE the seat. It is now inert. An
 * N-pair action for the multi-seat rebuild is still owed (wave 7b).
 */
private fun engineer(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Engineer may choose which Minions OR which Demon is in play. " +
            "Change each seat from the seat sheet, then wake the changed players one " +
            "at a time and show the 'You are' token and their new character.",
        infoId = "engineer",
        action = {
            ChoosePlayers(
                sourceId = "engineer",
                prompt = "WHICH EVIL SEATS ARE THEY REBUILDING?",
                min = 1,
                max = 3,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.EVIL),
                sort = TargetSort.DEMON_FIRST,
                allowNone = true,
                noneLabel = "Declined — nothing changes and the ability is NOT used",
                onResolve = listOf(NightEffect.MarkSpent("engineer")),
            )
        },
    )
    return CharacterRule(id = "engineer", firstNight = rule, otherNight = rule)
}

// ---------------------------------------------------------------------------
// farmer
// ---------------------------------------------------------------------------

/** "When you die at night, an alive good player becomes a Farmer." */
private fun farmer() = CharacterRule(
    id = "farmer",
    actsWhileDead = true,
    otherNight = NightRule(
        gate = farmerDiedTonight(),
        prompt = "Wake a living good player. Show the 'You are' token and a Farmer token. " +
            "They do NOT get first-night information.",
        infoId = "farmer",
        action = {
            ChoosePlayers(
                sourceId = "farmer",
                prompt = "WHO BECOMES THE FARMER?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ALIVE,
                    TargetConstraint.GOOD,
                    TargetConstraint.NOT_SELF,
                ),
                sort = TargetSort.TOWNSFOLK_FIRST,
                allowNone = true,
                noneLabel = "No alive good player — nobody becomes a Farmer",
                perTarget = listOf(
                    NightEffect.BecomeCharacter(
                        on = Ref.Target,
                        characterId = "farmer",
                        evil = false,
                        reason = ChangeReason.FARMER,
                    ),
                ),
            )
        },
    ),
)

/** The seat holding this row died TONIGHT, other than by execution, while sober. */
private fun farmerDiedTonight(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Farmer seat")
    val death = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.day == ctx.night && it.atNight &&
            it.resurrectedAtCycle == null
    }
    when {
        death == null -> StepGate.Skip("the Farmer did not die tonight")
        death.cause == DeathCause.EXECUTION -> StepGate.Skip("executed — no new Farmer")
        death.abilityImpairedAtDeath == true ->
            StepGate.Skip("the Farmer was drunk or poisoned when they died — no new Farmer")
        else -> StepGate.Fire
    }
}

// ---------------------------------------------------------------------------
// fisherman
// ---------------------------------------------------------------------------

/** "Once per game, during the day, visit the Storyteller for some advice." */
private fun fisherman() = CharacterRule(
    id = "fisherman",
    day = DayRule(
        ability = DayAbility(
            label = "Give advice",
            oncePerGame = true,
            recordsAs = "fisherman",
            available = { state, _, holder ->
                holder.alive && !Memory.isSpent(state, "fisherman", holder.id) &&
                    holder.reminders.none {
                        Tokens.key(it) == Tokens.key("fisherman", "No Ability")
                    }
            },
        ),
    ),
)

// ---------------------------------------------------------------------------
// general
// ---------------------------------------------------------------------------

/**
 * "Each night, you learn which alignment the Storyteller believes is winning."
 *
 * A judgement, not a computation: `InfoCalc` must NOT invent an answer, and the
 * Vortox's "Townsfolk info must be false" does not apply because there is no
 * true answer to invert. All three answers are one tap.
 */
private fun general(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the General a thumb signal: up for good winning, down for evil " +
            "winning, to the side for neither.",
        infoId = "general",
        cards = {
            listOf(
                CardOffer("SHOW: GOOD IS WINNING", ShowCardSpec.Message("GOOD", "GOOD IS WINNING"), true),
                CardOffer("SHOW: EVIL IS WINNING", ShowCardSpec.Message("EVIL", "EVIL IS WINNING"), true),
                CardOffer("SHOW: NEITHER", ShowCardSpec.Message("—", "NEITHER TEAM IS WINNING"), true),
            )
        },
    )
    return CharacterRule(id = "general", firstNight = rule, otherNight = rule)
}

// ---------------------------------------------------------------------------
// highpriestess
// ---------------------------------------------------------------------------

/** "Each night, learn which player the Storyteller believes you should talk to most." */
private fun highPriestess(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at the player they should talk to most. Alive or dead, good or " +
            "evil, Travellers included — do not filter. A repeat is a deliberate signal.",
        infoId = "highpriestess",
        action = {
            ChoosePlayers(
                sourceId = "highpriestess",
                prompt = "WHO SHOULD THEY TALK TO MOST?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.SEAT_ORDER,
            )
        },
    )
    return CharacterRule(id = "highpriestess", firstNight = rule, otherNight = rule)
}

// ---------------------------------------------------------------------------
// huntsman
// ---------------------------------------------------------------------------

/**
 * "Once per game, at night, choose a living player: the Damsel, if chosen,
 * becomes a not-in-play Townsfolk."
 *
 * SCHEMA GAP (WP2): a conditional second stage is unexpressible — `Sequence`
 * feeds every stage the SAME `NightInput`, and a `BecomeCharacter` whose
 * character id resolves to nothing wipes the target's character. The guess is
 * recorded and the ability spent here; the transform is a seat-sheet change,
 * which `Identity.changeCharacter` already makes safe (it drops
 * `damsel/Guess Used` with the old character).
 */
private fun huntsman(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Huntsman may guess a living player. If they chose the Damsel, " +
            "change that seat to a not-in-play Townsfolk and show them their new token. " +
            "The Huntsman learns nothing either way.",
        infoId = "huntsman",
        action = {
            ChoosePlayers(
                sourceId = "huntsman",
                prompt = "WHO DID THEY GUESS?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.SEAT_ORDER,
                allowNone = true,
                noneLabel = "Declined — no guess, the ability is NOT used",
                onResolve = listOf(NightEffect.MarkSpent("huntsman")),
            )
        },
    )
    return CharacterRule(id = "huntsman", firstNight = rule, otherNight = rule)
}

// ---------------------------------------------------------------------------
// king
// ---------------------------------------------------------------------------

/**
 * "Each night, if the dead equal or outnumber the living, you learn 1 alive
 * character. The Demon knows you are the King."
 *
 * Night 1 is not the King's step at all — it is the DEMON's, so it fires even
 * for an impaired King and counts as an INFORMED wake (lead D13). The
 * `king.demon` info id deliberately has no calculator, which suppresses the
 * planner's alive-character fallback on the wrong night.
 */
private fun king() = CharacterRule(
    id = "king",
    firstNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Wake the Demon. Show the 'This player is' and King tokens, then point " +
            "at the King.",
        infoId = "king.demon",
        wakeCounts = WakeCount.INFORMED,
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW: THIS PLAYER IS THE KING",
                    card = ShowCardSpec.CharacterCard("THIS PLAYER IS", "king"),
                    truthful = true,
                ),
            )
        },
    ),
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, deadAtLeastAlive()),
        prompt = "Show the King the character token of a living player. They may be shown " +
            "the same character on different nights.",
    ),
    // Jinx-gated variant (lead D19): with a Leviathan or a Riot in play the
    // threshold drops to "at least 1 player is dead". `jinxRules` is not read by
    // the planner yet — filed to WP2 with the Riot/Leviathan batch.
    jinxRules = mapOf(
        "leviathan" to NightRule(gate = Gates.all(Gates.aliveHolder, deadAtLeast(1))),
        "riot" to NightRule(gate = Gates.all(Gates.aliveHolder, deadAtLeast(1))),
    ),
)

/** "If the dead equal or outnumber the living" — every seat, Travellers included. */
private fun deadAtLeastAlive(): WakePredicate = WakePredicate { ctx ->
    val alive = ctx.state.seats.count { it.alive }
    val dead = ctx.state.seats.size - alive
    if (dead >= alive) {
        StepGate.Fire
    } else {
        StepGate.Skip("$dead dead vs $alive alive — the King needs dead >= alive")
    }
}

private fun deadAtLeast(n: Int): WakePredicate = WakePredicate { ctx ->
    val dead = ctx.state.seats.count { !it.alive }
    if (dead >= n) StepGate.Fire else StepGate.Skip("$dead dead — this needs $n")
}

// ---------------------------------------------------------------------------
// knight
// ---------------------------------------------------------------------------

/** "You start knowing 2 players that are not the Demon." */
private fun knight() = CharacterRule(
    id = "knight",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at the 2 players marked Know. Neither is the Demon — they may be " +
            "any other character, Minions included.",
    ),
    tokens = listOf(TokenRule("knight", "Know", null, Until.FOREVER, copies = 2)),
)

// ---------------------------------------------------------------------------
// lycanthrope
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose an alive player. If good, they die & the Demon doesn't
 * kill tonight. One good player registers as evil."
 *
 * The Demon's suppression is a real `DEMON_CANNOT_KILL` effect on every Demon
 * seat, so `Deaths.killOutcome` blocks the kill wherever it comes from rather
 * than by hiding a button (lead D36) — the Demon still wakes, still chooses, and
 * must never learn it failed.
 *
 * SETTLED (lead D68, the scope this row asked for): the suppression carries a
 * [KillSuppression], so `NightPlan.applyEffect` can tell the two apart. An
 * Exorcised Demon is SILENCED and its standing Pukka victim still dies (D63);
 * the Lycanthrope's "the Demon doesn't kill tonight" is NO_KILL_TONIGHT and
 * stops that deferred kill too, which is the wiki's own worked example.
 *
 * The Faux Paw misregistration is a [StandingRule] (lead D58) so it lapses the
 * moment the Lycanthrope dies or is impaired, with no extra code.
 */
private fun lycanthrope() = CharacterRule(
    id = "lycanthrope",
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Lycanthrope points at an alive player. If that player is good they " +
            "die, and NO ONE dies to the Demon tonight — the Demon still wakes and still " +
            "chooses. Monk and Soldier do not protect against this.",
        infoId = "lycanthrope",
        action = { ctx ->
            val demonSeats = ctx.state.seats
                .filter { it.characterId?.let(ctx.lookup)?.team == Team.DEMON }
            ChoosePlayers(
                sourceId = "lycanthrope",
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                // GOOD is true alignment; a seat the storyteller has ruled registers
                // evil (a Faux Paw target) is their call to decline instead.
                constraints = listOf(
                    TargetConstraint.ALIVE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.GOOD,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They chose nobody, or the player registers evil — nothing happens",
                perTarget = listOf(
                    NightEffect.Attack(Ref.Target, DeathCause.GOOD_ABILITY),
                    NightEffect.PlaceToken(
                        sourceId = "lycanthrope",
                        label = "Dead",
                        on = Ref.Target,
                        kind = EffectKind.MARKER,
                        until = Until.DAWN,
                    ),
                ),
                onResolve = demonSeats.map { demon ->
                    // No official token exists for this: the effect is unlabelled, so
                    // the grimoire shows the reason on the kill sheet, not a fake token.
                    NightEffect.PlaceToken(
                        sourceId = "lycanthrope",
                        label = "",
                        on = Ref.Seat(demon.id),
                        kind = EffectKind.DEMON_CANNOT_KILL,
                        until = Until.DAWN,
                        // Lead D68 settles the question this row filed: "the Demon
                        // doesn't kill tonight" reaches a DEFERRED kill too, which
                        // is exactly the wiki's worked Pukka example. The Exorcist's
                        // SILENCED scope does not.
                        suppression = KillSuppression.NO_KILL_TONIGHT,
                    )
                },
            )
        },
    ),
    standing = StandingRule("lycanthrope") { state, holder, _ ->
        val key = Tokens.key("lycanthrope", "Faux Paw")
        state.seats
            .filter { seat -> seat.reminders.any { Tokens.key(it) == key } }
            .map { seat ->
                Effect(
                    id = seat.standingSince,
                    kind = EffectKind.REGISTERS_AS,
                    targetId = seat.id,
                    characterId = "evil",
                    sourceCharacterId = "lycanthrope",
                    sourcePlayerId = holder.id,
                    until = Until.FOREVER,
                    label = "Faux Paw",
                    note = "Lycanthrope (${holder.name}): this good player registers as evil.",
                    createdCycle = state.cycle,
                    createdAtNight = state.phase != Phase.DAY,
                    derived = true,
                )
            }
    },
    // W7G: "One good player registers as evil" is a SETUP fact — the token goes
    // down before the first night, and the standing rule above reads it. The
    // slot had no consumer until wave 7, so this row was owed and never written.
    setup = listOf(
        SetupRequirement(
            id = "lycanthrope.fauxpaw",
            characterId = "lycanthrope",
            kind = RequirementKind.REMINDER,
            title = "Lycanthrope: mark one good player Faux Paw",
            prompt = "Choose ONE good player and mark them FAUX PAW. They register as evil to " +
                "every ability that asks, but they still win with good and are never told.",
            problem = "Mark a good player Faux Paw before the first night",
            satisfied = { state, _ ->
                val key = Tokens.key("lycanthrope", "Faux Paw")
                state.seats.none { it.characterId?.let(Character::normalizeId) == "lycanthrope" } ||
                    state.seats.any { seat -> seat.reminders.any { Tokens.key(it) == key } }
            },
        ),
    ),
)

// ---------------------------------------------------------------------------
// magician
// ---------------------------------------------------------------------------

/**
 * "The Demon thinks you are a Minion. Minions think you are a Demon."
 *
 * SCHEMA GAP (WP2): the whole ability is a CONTENT TRANSFORM of the shared
 * `MINION_INFO` / `DEMON_INFO` builder, which lives in `NightInfo` inside
 * `CharacterRules.kt` — not reachable from a `CharacterRule`. `NightInfo` today
 * names only the real Demon to the Minions and only the real Minions to the
 * Demon, i.e. the next two rows still tell the storyteller to do the opposite
 * of this row. Filed to WP2 (owner of `NightInfo`): interleave the Magician
 * into both lists, suppress the "point out the Marionette" clause, split a
 * Legion `DEMON_INFO`, and honour the Vizier exception.
 *
 * What IS expressible: the Magician's own informational row, which says what
 * the following two rows must do.
 */
private fun magician() = CharacterRule(
    id = "magician",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Magician does not wake. During Minion info, point at the Magician " +
            "AND the Demon without saying which is which. During Demon info, point at " +
            "the Magician among the Minions.",
        infoId = "magician",
        wakeCounts = WakeCount.NONE,
    ),
)

// ---------------------------------------------------------------------------
// nightwatchman
// ---------------------------------------------------------------------------

/** "Once per game, at night, choose a player: they learn you are the Nightwatchman." */
private fun nightwatchman(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Nightwatchman may choose a player — alive or dead. Put the " +
            "Nightwatchman to sleep, then wake the target separately and show them the " +
            "'This player is' and Nightwatchman tokens.",
        infoId = "nightwatchman",
        action = {
            ChoosePlayers(
                sourceId = "nightwatchman",
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "Declined — no choice, the ability is NOT used",
                perTarget = listOf(NightEffect.ShowCardTo(Ref.Target, "nightwatchman")),
                onResolve = listOf(NightEffect.MarkSpent("nightwatchman")),
            )
        },
    )
    return CharacterRule(id = "nightwatchman", firstNight = rule, otherNight = rule)
}

// ---------------------------------------------------------------------------
// noble
// ---------------------------------------------------------------------------

/** "You start knowing 3 players, 1 and only 1 of which is evil." */
private fun noble() = CharacterRule(
    id = "noble",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at the 3 players marked Know. Exactly 1 of them is evil — a " +
            "Recluse may be your 1 evil, a Spy may be one of your 2 good.",
    ),
    tokens = listOf(TokenRule("noble", "Know", null, Until.FOREVER, copies = 3)),
)

// ---------------------------------------------------------------------------
// pixie
// ---------------------------------------------------------------------------

/**
 * "You start knowing 1 in-play Townsfolk. If you were mad that you were this
 * character, you gain their ability when they die."
 *
 * SCHEMA GAP (WP2): `NightEffect.PlaceToken` carries no character payload, so
 * the mad character is read back from the CHOICE ledger entry (and from
 * `PlacedReminder.characterId` when the storyteller placed it by hand).
 * `pixie/Has Ability` is granted through a prompt, never silently, because
 * "were they mad enough?" is a storyteller judgement.
 */
private fun pixie() = CharacterRule(
    id = "pixie",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the Pixie an in-play Townsfolk token. They must be mad that they " +
            "are it. Mark the Pixie Mad and note which character.",
        infoId = "pixie",
        action = {
            ChooseCharacter(
                sourceId = "pixie",
                prompt = "WHICH IN-PLAY TOWNSFOLK ARE THEY MAD ABOUT?",
                pool = CharacterPool.TOWNSFOLK,
                allowNone = false,
                onResolve = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "pixie",
                        label = "Mad",
                        on = Ref.Source,
                        kind = EffectKind.MAD,
                        until = Until.FOREVER,
                        // W7E: the token NAMES the character they are mad about,
                        // which is the whole ability — the death trigger below
                        // reads it back. It used to live only in the ledger.
                        characterId = "",
                    ),
                ),
            )
        },
    ),
    onDeath = listOf(
        DeathTrigger(
            gate = { state, _, event, holder ->
                val mad = pixieMadCharacter(state, holder)
                mad != null &&
                    Character.normalizeId(event.characterIdAtDeath.orEmpty()) == mad &&
                    event.playerId != holder.id &&
                    !holdsToken(holder, "pixie", "Has Ability")
            },
            produce = { state, _, event, holder ->
                val mad = pixieMadCharacter(state, holder).orEmpty()
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = if (event.atNight) BriefingSlot.DAWN else BriefingSlot.NOW,
                            kind = PromptKind.PLACE_EFFECT,
                            sourceId = "pixie",
                            subjectPlayerId = holder.id,
                            characterIds = listOf(mad),
                            title = "The $mad just died. Was ${holder.name} (Pixie) mad " +
                                "enough about being the $mad?",
                            detail = "If yes, remove Mad and place Has Ability on the Pixie. " +
                                "They gain that character's ability from now on.",
                            optional = true,
                        ),
                    ),
                )
            },
        ),
    ),
)

/** The character the Pixie was told to be mad about, from the token then the ledger. */
private fun pixieMadCharacter(state: GameState, holder: Player): String? {
    val key = Tokens.key("pixie", "Mad")
    holder.reminders.firstOrNull { Tokens.key(it) == key }?.characterId
        ?.let { return Character.normalizeId(it) }
    return Memory.by(state, LedgerKind.CHOICE, "pixie", holder.id)
        .lastOrNull { it.characterIds.isNotEmpty() }
        ?.characterIds
        ?.firstOrNull()
        ?.let(Character::normalizeId)
}

// ---------------------------------------------------------------------------
// poppygrower
// ---------------------------------------------------------------------------

/**
 * "Minions & Demons do not know each other. If you die, they learn who each
 * other are that night."
 *
 * The night-1 suppression is already derived: `Bluffs.requirements` emits a
 * `DEMON_BLUFFS_ONLY` requirement, which `NightInfo` reads to skip `MINION_INFO`
 * and to retitle `DEMON_INFO` as a bluffs-only row (lead D37). This row owns the
 * Poppy Grower's own two slots.
 */
private fun poppyGrower() = CharacterRule(
    id = "poppygrower",
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Minion info and Demon info do NOT run tonight. Wake the Demon, show " +
            "'These characters are not in play' and 3 not-in-play good tokens. The " +
            "Minions learn nothing — do not wake them.",
        infoId = "poppygrower",
        wakeCounts = WakeCount.NONE,
    ),
    otherNight = NightRule(
        gate = poppyGrowerRevealDue(),
        prompt = "1) Wake all Minions together (never the Marionette). Show 'This is the " +
            "Demon' and point at the Demon. Sleep. 2) Wake the Demon, show 'These are " +
            "your Minions' and point at the Minions (and the Marionette).",
        infoId = "poppygrower",
        wakeCounts = WakeCount.NONE,
        // Nothing is chosen here, so the record is written by the always-run half:
        // `Evil Wakes` marks that the reveal happened on this seat.
        pending = {
            listOf(
                NightEffect.PlaceToken(
                    sourceId = "poppygrower",
                    label = "Evil Wakes",
                    on = Ref.Source,
                    kind = EffectKind.MARKER,
                    until = Until.DAWN,
                ),
            )
        },
    ),
)

/** The reveal is owed on the night of, or the night after, an unimpaired death. */
private fun poppyGrowerRevealDue(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Poppy Grower seat")
    if (holder.alive) {
        return@WakePredicate StepGate.Skip("the Poppy Grower is alive — evil stay apart")
    }
    val death = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.resurrectedAtCycle == null
    } ?: return@WakePredicate StepGate.Skip("no death recorded for the Poppy Grower")
    val due = (death.day == ctx.night && death.atNight) ||
        (death.day == ctx.night - 1 && !death.atNight)
    when {
        !due -> StepGate.Skip("the evil team already met on the night the Poppy Grower died")
        death.abilityImpairedAtDeath == true ->
            StepGate.Skip("the Poppy Grower was drunk or poisoned when they died — evil already knew each other")
        else -> StepGate.Fire
    }
}

// ---------------------------------------------------------------------------
// preacher
// ---------------------------------------------------------------------------

/**
 * "Each night, choose a player: a Minion, if chosen, learns this. All chosen
 * Minions have no ability."
 *
 * `endsWithSource = true` on the token IS the rule: the suppression lapses the
 * moment the Preacher is drunk, poisoned or dead, and `Status.abilityWorks`
 * gives that for free (digest §group 4).
 *
 * The picker is restricted to Minions because `NightAction` cannot make an
 * effect conditional on the target's team: an unconditional `perTarget` would
 * strip a Townsfolk's ability. Non-Minion picks are a decline.
 */
private fun preacher(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Preacher chooses a player — dead Minions are legal targets. If a " +
            "Minion, wake them and show 'This character selected you' and the Preacher " +
            "token. Never wake the Marionette.",
        infoId = "preacher",
        action = {
            ChoosePlayers(
                sourceId = "preacher",
                prompt = "WHICH MINION DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.MINION,
                ),
                sort = TargetSort.MINION_FIRST,
                allowNone = true,
                noneLabel = "They chose a non-Minion — nothing happens, wake nobody",
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "preacher",
                        label = "No Ability",
                        on = Ref.Target,
                        kind = EffectKind.NO_ABILITY,
                        until = Until.FOREVER,
                    ),
                ),
            )
        },
    )
    return CharacterRule(
        id = "preacher",
        firstNight = rule,
        otherNight = rule,
        tokens = listOf(
            TokenRule(
                sourceId = "preacher",
                label = "No Ability",
                effect = EffectKind.NO_ABILITY,
                until = Until.FOREVER,
                copies = 3,
                endsWithSource = true,
                impairs = true,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// princess
// ---------------------------------------------------------------------------

/**
 * "On your 1st day, if you nominated & executed a player, the Demon doesn't
 * kill tonight."
 *
 * A day character: the nomination warns, the execution raises the consequence,
 * and the night row exists ONLY when the `Doesn't Kill` token is actually on a
 * Demon — a row that appears every night trains the storyteller to tick without
 * reading, which is how the real bug happens.
 *
 * `outcome == SURVIVED` still counts: the executed player does not have to die.
 */
private fun princess() = CharacterRule(
    id = "princess",
    otherNight = NightRule(
        gate = princessBlockActive(),
        prompt = "PRINCESS: wake the Demon and let them choose as normal, but NOBODY dies " +
            "to the Demon's kill tonight. Every other Demon effect still happens.",
        infoId = "princess",
        wakeCounts = WakeCount.NONE,
    ),
    day = DayRule(
        onNomination = { ctx ->
            val nominator = ctx.nominatorId
            if (nominator != ctx.holder.id || ctx.state.cycle != princessFirstDay(ctx.state, ctx.holder)) {
                emptyList()
            } else {
                val nominee = ctx.nomineeId?.let { ctx.state.player(it) }?.name ?: "the nominee"
                listOf(
                    NominationTrigger(
                        kind = TriggerKind.WARN,
                        sourceId = "princess",
                        actorId = ctx.holder.id,
                        targetId = ctx.nomineeId,
                        headline = "${ctx.holder.name} (Princess) nominates on their 1st day — " +
                            "if $nominee is executed today, the Demon does not kill tonight.",
                        detail = "The executed player does not have to die for this to apply.",
                        impaired = Status.isImpaired(ctx.state, ctx.lookup, ctx.holder.id),
                    ),
                )
            }
        },
        onExecution = { ctx ->
            val record = ctx.record
            val nominated = ctx.state.nominations.any {
                it.day == record.day && !it.isExile &&
                    it.nominatorId == ctx.holder.id && it.nomineeId == record.playerId
            }
            if (!nominated ||
                record.outcome == ExecutionOutcome.NO_EXECUTION ||
                record.day != princessFirstDay(ctx.state, ctx.holder)
            ) {
                emptyList()
            } else {
                listOf(
                    ExecutionConsequence(
                        sourceId = "princess",
                        headline = "Princess: the Demon does not kill tonight.",
                        detail = "Impairment is judged AT NIGHT, when the Demon's step is " +
                            "reached — not now. Confirming places \"Doesn't Kill\" on every " +
                            "Demon seat for you.",
                        // W7G: `ExecutionConsequence.apply` — the row DOES the
                        // bookkeeping now instead of telling the storyteller to.
                        apply = { state, _ ->
                            state.seats
                                .filter { it.characterId?.let(ctx.lookup)?.team == Team.DEMON }
                                .fold(state) { acc, demon ->
                                    Effects.place(
                                        state = acc,
                                        target = demon.id,
                                        kind = EffectKind.DEMON_CANNOT_KILL,
                                        sourceCharacterId = "princess",
                                        sourcePlayerId = null,
                                        until = Until.DAWN,
                                        label = "Doesn't Kill",
                                        note = "Princess: the Demon does not kill tonight.",
                                    ).state
                                }
                        },
                    ),
                )
            }
        },
    ),
)

/**
 * "Your 1st day" = the first day this seat held the Princess. A mid-game
 * Princess gets a fresh window; a dealt one starts on day 1.
 */
private fun princessFirstDay(state: GameState, holder: Player): Int =
    state.identityLog
        .lastOrNull { it.playerId == holder.id && Character.normalizeId(it.toCharacterId.orEmpty()) == "princess" }
        ?.cycle
        ?: 1

/** The row exists only while a Demon actually carries the block. */
private fun princessBlockActive(): WakePredicate = WakePredicate { ctx ->
    val key = Tokens.key("princess", "Doesn't Kill")
    val blocked = ctx.state.seats.any { seat ->
        seat.reminders.any { Tokens.key(it) == key } ||
            ctx.state.effects.any {
                it.targetId == seat.id && Tokens.key(it.sourceCharacterId, it.label) == key
            }
    }
    if (blocked) {
        StepGate.Fire
    } else {
        StepGate.Skip("no qualifying nomination and execution — the Demon kills as normal")
    }
}

// ---------------------------------------------------------------------------
// shugenja
// ---------------------------------------------------------------------------

/** "You start knowing if your closest evil player is clockwise or anti-clockwise." */
private fun shugenja() = CharacterRule(
    id = "shugenja",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point clockwise or anti-clockwise. If equidistant the answer is " +
            "arbitrary — the Shugenja is never told that.",
    ),
)

// ---------------------------------------------------------------------------
// steward
// ---------------------------------------------------------------------------

/** "You start knowing 1 good player." */
private fun steward() = CharacterRule(
    id = "steward",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Point at the player marked Know. A Spy is a legal answer — they " +
            "register as good.",
    ),
    tokens = listOf(TokenRule("steward", "Know", null, Until.FOREVER, copies = 1)),
)

// ---------------------------------------------------------------------------
// villageidiot
// ---------------------------------------------------------------------------

/**
 * "Each night, choose a player: you learn their alignment.
 * [+0 to +2 Village Idiots. 1 of the extras is drunk]"
 *
 * One row PER HOLDER (lead D16), which is the whole fix for the P0: three
 * Village Idiots sharing one picker meant the drunk one's caveat was read off
 * the first seat. The `Drunk` mark never moves and never ends, so it outlives
 * its own source.
 */
private fun villageIdiot(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Wake this Village Idiot alone. They point at a player; give a thumb " +
            "signal for that player's alignment.",
    )
    return CharacterRule(
        id = "villageidiot",
        perHolder = true,
        firstNight = rule,
        otherNight = rule,
        tokens = listOf(
            TokenRule(
                sourceId = "villageidiot",
                label = "Drunk",
                effect = EffectKind.DRUNK,
                until = Until.FOREVER,
                copies = 1,
                // The setup decision never moves and survives every other
                // Village Idiot leaving play.
                endsWithSource = false,
                impairs = true,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// shared helpers
// ---------------------------------------------------------------------------

/** A Demon's own ability killed them — never a Minion kill, an execution or the Lycanthrope. */
private fun isDemonKill(event: DeathEvent): Boolean =
    event.cause == DeathCause.DEMON_KILL ||
        @Suppress("DEPRECATION") (event.cause == DeathCause.DEMON)

private fun holdsToken(player: Player, sourceId: String, label: String): Boolean {
    val key = Tokens.key(sourceId, label)
    return player.reminders.any { Tokens.key(it) == key }
}

/**
 * `RemoveToken` for every seat currently carrying `(sourceId, label)`.
 *
 * A "the token moves" rule cannot rely on `TokenRule.copies` alone: `Effects.place`
 * displaces an older EFFECT, but the setup checklist places the first copy as a
 * hand-placed `PlacedReminder`, which nothing displaces. Prepending these keeps
 * exactly one on the board however it got there.
 */
private fun clearToken(state: GameState, sourceId: String, label: String): List<NightEffect> {
    val key = Tokens.key(sourceId, label)
    return state.seats
        .filter { seat ->
            seat.reminders.any { Tokens.key(it) == key } ||
                state.effects.any {
                    it.targetId == seat.id && Tokens.key(it.sourceCharacterId, it.label) == key
                }
        }
        .map { NightEffect.RemoveToken(sourceId, label, Ref.Seat(it.id)) }
}
