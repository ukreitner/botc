package com.clocktower.engine.rules

import com.clocktower.engine.BriefingSlot
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
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.EffectKind
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.LedgerEntry
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Memory
import com.clocktower.engine.NightAction
import com.clocktower.engine.NightContext
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.Sequence
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.StepGate
import com.clocktower.engine.Status
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.Team
import com.clocktower.engine.TokenRule
import com.clocktower.engine.Tokens
import com.clocktower.engine.TriggerResult
import com.clocktower.engine.Until
import com.clocktower.engine.Verdict
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate
import com.clocktower.engine.YesNo

/**
 * Bad Moon Rising behaviour (WP7-BMR).
 *
 * Twenty-two rows — 13 Townsfolk, 4 Outsiders, 4 Minions (the Mastermind included)
 * and 4 Demons. Travellers belong to WP7-TRAV.
 *
 * Four of these are the user's own reported bugs and are the reason this package
 * exists (`docs/audit/ux/friction-log.md`, pinned by `BmrSessionPlaytestTest`):
 *
 *  * **Pukka** — poisons on night N, kills that victim at its night N+1 step. The
 *    new `Poisoned` token goes down FIRST, so the victim dies still poisoned and
 *    only then becomes healthy (lead D4). An Exorcised Pukka is `Reduced`, never
 *    `Skip`ped, and its standing victim still dies (lead D24/D36/D63).
 *  * **Devil's Advocate** — "different to last night" is
 *    [TargetConstraint.DIFFERENT_FROM_LAST_NIGHT] over the ledger, never a token:
 *    the token is swept at dusk, the CHOICE is not (lead D3).
 *  * **Gossip** — the day's statement is a `LedgerEntry(kind = STATEMENT)` the
 *    night step reads back and judges (lead D1).
 *  * **Professor** — `NightEffect.Resurrect`, which queues the dawn announcement
 *    and the RUN_FIRST_NIGHT re-run through the one kill funnel (lead D7).
 *
 * Nothing here calls a kill directly, writes to `Player.reminders`, or branches on
 * another character's id outside a `jinxRules` entry.
 */
internal val BMR_RULES: List<CharacterRule> = listOf(
    // ================================================================
    // Townsfolk
    // ================================================================
    grandmother(),
    sailor(),
    chambermaid(),
    exorcist(),
    innkeeper(),
    gambler(),
    gossip(),
    courtier(),
    professor(),
    minstrel(),
    teaLady(),
    pacifist(),
    fool(),

    // ================================================================
    // Outsiders
    // ================================================================
    goon(),
    lunatic(),
    tinker(),
    moonchild(),

    // ================================================================
    // Minions
    // ================================================================
    godfather(),
    devilsAdvocate(),
    assassin(),
    mastermind(),

    // ================================================================
    // Demons
    // ================================================================
    zombuul(),
    pukka(),
    shabaloth(),
    po(),
)

// ====================================================================
// Townsfolk
// ====================================================================

/**
 * "You start knowing a good player & their character. If the Demon kills them,
 * you die too."
 *
 * The first night is pure information — the `Grandchild` token was placed at
 * setup (`SetupRequirements` "grandmother.grandchild"), so the step only shows it.
 * The other-night row is storyteller bookkeeping at order 72, AFTER every kill
 * source and after the Professor's 64: a grandchild the Professor brought back is
 * no longer dead, so the Grandmother lives.
 */
private fun grandmother() = CharacterRule(
    id = "grandmother",
    killCause = DeathCause.GOOD_ABILITY,
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the marked Grandchild's character token, then point to that player.",
        action = { ShowInfo("grandmother", "WHICH PLAYER IS THE GRANDCHILD?", targetsNeeded = 1) },
        infoId = "grandmother",
    ),
    otherNight = NightRule(
        // Lead D35: the default is that the holder must be sober at the trigger.
        // `Gates.hasAbility` skips an impaired Grandmother rather than killing her,
        // and the row keeps its [Run anyway] override for the storyteller.
        gate = Gates.all(
            WakePredicate { ctx ->
                val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Grandmother in play")
                when {
                    !holder.alive -> StepGate.Skip("the Grandmother is already dead")
                    grandchildDemonKilledTonight(ctx.state, holder.id, ctx.night) ->
                        StepGate.Fire

                    else -> StepGate.Skip(
                        "the grandchild was not killed by the Demon tonight — the Grandmother lives",
                    )
                }
            },
            Gates.hasAbility,
        ),
        // The Grandmother is never woken: this row is the storyteller's own.
        wakeCounts = WakeCount.NONE,
        prompt = "The Demon killed the grandchild. The Grandmother dies too — " +
            "announce both deaths at dawn, in seat order.",
        pending = { ctx ->
            val holder = ctx.holder
            if (holder == null || !grandchildDemonKilledTonight(ctx.state, holder.id, ctx.night)) {
                emptyList()
            } else {
                listOf(
                    NightEffect.Attack(
                        on = Ref.Seat(holder.id),
                        cause = DeathCause.GOOD_ABILITY,
                    ),
                    NightEffect.PlaceToken(
                        sourceId = "grandmother",
                        label = "Dead",
                        on = Ref.Seat(holder.id),
                    ),
                )
            }
        },
    ),
)

/**
 * "Each night, choose an alive player: either you or they are drunk until dusk.
 * You can't die."
 *
 * The innate `CANT_DIE` is WP1's `Standing.emitSelf` row — declaring a
 * `StandingRule` here would REPLACE it, and the positional half of that engine
 * (`abilityWorksBase`, `registersEvilBase`) is not reachable from `engine/rules/`.
 * Sobriety needs no special case either: the standing effect carries
 * `sourcePlayerId = the Sailor`, so a Sailor who drunked themselves stops
 * protecting through `abilityWorks` alone.
 */
private fun sailor(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Sailor points at a living player. Either the Sailor or that player is " +
            "drunk until dusk — move the Drunk token onto the Sailor if you choose them.",
        action = {
            ChoosePlayers(
                sourceId = "sailor",
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.SELF_ALLOWED),
                sort = TargetSort.ALIVE_FIRST,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "sailor",
                        label = "Drunk",
                        on = Ref.Target,
                        kind = EffectKind.DRUNK,
                        until = Until.DUSK,
                    ),
                ),
            )
        },
    )
    return CharacterRule(id = "sailor", firstNight = rule, otherNight = rule)
}

/**
 * "Each night, choose 2 alive players (not yourself): you learn how many woke
 * tonight due to their ability."
 *
 * The count is `InfoCalc.chambermaid`, which projects over the un-run tail of
 * tonight's plan (lead D13) — that is the Mathematician jinx.
 */
private fun chambermaid(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.minAlive(2)),
        prompt = "The Chambermaid points at two players. Show how many of them woke " +
            "tonight for their OWN ability.",
        action = {
            ShowInfo(
                sourceId = "chambermaid",
                prompt = "WHICH TWO DID THEY CHOOSE?",
                targetsNeeded = 2,
                constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.NOT_SELF),
            )
        },
        infoId = "chambermaid",
    )
    return CharacterRule(id = "chambermaid", firstNight = rule, otherNight = rule)
}

/**
 * "Each night*, choose a player (different to last night): the Demon, if chosen,
 * learns who you are then doesn't wake tonight."
 *
 * No first-night step, ever. The suppression itself is not this row's job: the
 * `Chosen` token is a `DEMON_CANNOT_KILL` effect, and `Gates.notExorcised` turns
 * it into a `StepGate.Reduced` on the Demon's own row (lead D24) — so the Demon's
 * pending half, the Pukka's standing victim above all, still resolves (D63).
 */
private fun exorcist() = CharacterRule(
    id = "exorcist",
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Exorcist points at a player, different from last night. If it is the " +
            "Demon: wake them, show the Exorcist token and point at the Exorcist. Deaths " +
            "already scheduled from earlier nights still happen.",
        action = {
            ChoosePlayers(
                sourceId = "exorcist",
                prompt = "WHO DID THEY CHOOSE?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.DIFFERENT_FROM_LAST_NIGHT,
                ),
                sort = TargetSort.ALIVE_FIRST,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "exorcist",
                        label = "Chosen",
                        on = Ref.Target,
                        kind = EffectKind.DEMON_CANNOT_KILL,
                        until = Until.DAWN,
                    ),
                ),
            )
        },
        infoId = "exorcist",
    ),
)

/**
 * "Each night*, choose 2 players: they can't die tonight, but 1 is drunk until
 * dusk."
 *
 * Both picks get `Safe` (two official copies — the old app's exclusive placement
 * is what stole the first one). The SECOND pick is the drunk one; the prompt says
 * so, so the storyteller controls it by pick order. The self-protection trap needs
 * no code: both effects carry `sourcePlayerId = the Innkeeper`, so an Innkeeper who
 * made themselves drunk loses both (lead D3).
 */
private fun innkeeper() = CharacterRule(
    id = "innkeeper",
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Innkeeper points at two players. Both are safe tonight; the SECOND " +
            "one you tap is the drunk one, until dusk tomorrow.",
        action = {
            ChoosePlayers(
                sourceId = "innkeeper",
                prompt = "WHICH TWO DID THEY CHOOSE? (the second is the drunk one)",
                min = 2,
                max = 2,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "innkeeper",
                        label = "Safe",
                        on = Ref.Target,
                        kind = EffectKind.CANT_DIE_TONIGHT,
                        until = Until.DAWN,
                    ),
                ),
                // `scope.current` is the LAST pick when onResolve runs (§2.11).
                onResolve = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "innkeeper",
                        label = "Drunk",
                        on = Ref.Target,
                        kind = EffectKind.DRUNK,
                        until = Until.DUSK,
                    ),
                ),
            )
        },
    ),
)

/**
 * "Each night*, choose a player & guess their character: if you guess wrong, you
 * die."
 *
 * Two stages resolved from ONE input: the pick, then the storyteller's verdict.
 * The default answer is "correct" — a resolve with no verdict must never kill.
 * The comparison is against the true `characterId`: a Drunk shown the Chef is the
 * Drunk, so "Chef" is wrong and "Drunk" is right.
 */
private fun gambler() = CharacterRule(
    id = "gambler",
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Gambler points at a player and a character. Tell them nothing. " +
            "Judge the guess against the player's TRUE character.",
        action = {
            Sequence(
                sourceId = "gambler",
                prompt = "WHO, AND WHICH CHARACTER?",
                stages = listOf(
                    ChoosePlayerAndCharacter(
                        sourceId = "gambler",
                        prompt = "WHO DID THEY CHOOSE, AND WHAT DID THEY GUESS?",
                        playerConstraints = listOf(
                            TargetConstraint.ANY_LIVING_STATE,
                            TargetConstraint.SELF_ALLOWED,
                        ),
                        pool = CharacterPool.SCRIPT,
                    ),
                    YesNo(
                        sourceId = "gambler",
                        prompt = "WAS THE GUESS WRONG?",
                        yesLabel = "Wrong — the Gambler dies",
                        noLabel = "Correct — nothing happens",
                        onYes = listOf(
                            NightEffect.Attack(
                                on = Ref.Source,
                                cause = DeathCause.GOOD_ABILITY,
                            ),
                            NightEffect.PlaceToken("gambler", "Dead", Ref.Source),
                        ),
                    ),
                ),
            )
        },
    ),
)

/**
 * "Each day, you may make a public statement. Tonight, if it was true, a player
 * dies."
 *
 * The Gossip is never woken — the row is storyteller bookkeeping at order 57,
 * after every Demon, the Assassin and the Godfather. Its whole memory is the day's
 * `LedgerEntry(kind = STATEMENT, sourceId = "gossip")`, which the Day tab records
 * whether or not a Gossip is in play (invariant I3, the user's own request).
 * Drunkenness and death are judged NOW, at this step, not when the statement was
 * made.
 */
private fun gossip() = CharacterRule(
    id = "gossip",
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = Gates.all(
            Gates.aliveHolder,
            // "Judge drunkenness and death NOW, at this step — not when the
            // statement was made." An impaired Gossip kills nobody by default.
            Gates.hasAbility,
            WakePredicate { ctx ->
                when (gossipStatement(ctx.state)?.verdict) {
                    null -> StepGate.Skip(
                        "no Gossip statement was recorded yesterday — record one on the Day tab",
                    )

                    Verdict.FALSE -> StepGate.Skip("the statement was false — nobody dies")
                    Verdict.TRUE -> StepGate.Fire
                    else -> StepGate.Conditional(
                        question = "Was it true? “" + gossipStatement(ctx.state)?.text.orEmpty() + "”",
                        yesLabel = "True — a player dies",
                        noLabel = "False — nobody dies",
                    )
                }
            },
        ),
        wakeCounts = WakeCount.NONE,
        prompt = "The Gossip's statement was true. Choose a player who is not protected " +
            "from dying tonight; if everyone is protected, nobody dies.",
        // W7C: the step QUOTES the statement, so the Gossip is never asked
        // "what did you say?" again (friction F52, invariant I3).
        banner = { ctx ->
            gossipStatement(ctx.state)?.text?.takeIf { it.isNotBlank() }
                ?.let { "Yesterday's statement: \u201C" + it + "\u201D" }
                .orEmpty()
        },
        detail = { ctx ->
            val entry = gossipStatement(ctx.state)
            if (entry == null || entry.text.isBlank()) {
                ""
            } else {
                val speaker = entry.actorId?.let { ctx.state.player(it)?.name }
                "Said on day " + entry.cycle +
                    (speaker?.let { " by " + it } ?: "") +
                    ": \u201C" + entry.text + "\u201D"
            }
        },
        action = { ctx ->
            ChoosePlayers(
                sourceId = "gossip",
                prompt = "WHO DIES? “" + gossipStatement(ctx.state)?.text.orEmpty() + "”",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "Nobody can die — everyone is protected",
                perTarget = listOf(
                    NightEffect.Attack(on = Ref.Target, cause = DeathCause.GOOD_ABILITY),
                    NightEffect.PlaceToken("gossip", "Dead", Ref.Target),
                ),
            )
        },
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Statement",
            oncePerDay = true,
            recordsAs = "gossip",
            available = { _, _, holder -> holder.alive },
        ),
    ),
)

/**
 * "Once per game, at night, choose a character: they are drunk for 3 nights &
 * 3 days."
 *
 * The countdown is a `TokenRule` chain advanced at dusk — `Drunk 1` → `Drunk 2` →
 * `Drunk 3` → gone (lead D14). The Courtier is never woken again once spent, so
 * nothing here reduces the counter itself.
 *
 * Pointing at ANY character spends the ability, in play or not, sober or not:
 * `MarkSpent` is unconditional and `PlaceToken` simply lands on nobody when the
 * chosen character holds no seat.
 */
private fun courtier(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Courtier either shakes their head, or points at a character on the " +
            "sheet. Pointing at anyone spends the ability — even a character not in play.",
        action = {
            ChooseCharacter(
                sourceId = "courtier",
                prompt = "WHICH CHARACTER DID THEY NAME?",
                pool = CharacterPool.SCRIPT,
                allowNone = true,
                onResolve = listOf(
                    NightEffect.MarkSpent("courtier"),
                    NightEffect.PlaceToken(
                        sourceId = "courtier",
                        label = "Drunk 1",
                        on = Ref.Target,
                        kind = EffectKind.DRUNK,
                        until = Until.DUSK,
                    ),
                ),
            )
        },
        infoId = "courtier",
    )
    return CharacterRule(id = "courtier", firstNight = rule, otherNight = rule)
}

/**
 * "Once per game, at night*, choose a dead player: if they are a Townsfolk, they
 * are resurrected."
 *
 * `NightEffect.Resurrect` is the whole fix for the user's fourth complaint: the
 * kill funnel's `Deaths.resurrect` clears the seat's spent marks (Glossary),
 * queues a `RUN_FIRST_NIGHT` prompt that `NightPlan` turns into an inserted
 * FIRST-night step tonight, and writes the pending dawn announcement — "X is
 * alive again", never why (lead D7).
 *
 * Team is judged on the TRUE character, so a dead Lunatic or Drunk is an Outsider
 * and is not offered.
 */
private fun professor() = CharacterRule(
    id = "professor",
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Professor shakes their head, or points at a dead player. Only a " +
            "Townsfolk comes back. Announce it at dawn, after the deaths — never say why.",
        action = {
            ChoosePlayers(
                sourceId = "professor",
                prompt = "WHO DID THEY CHOOSE?",
                min = 0,
                max = 1,
                constraints = listOf(TargetConstraint.DEAD, TargetConstraint.TOWNSFOLK),
                sort = TargetSort.DEAD_FIRST,
                allowNone = true,
                noneLabel = "Shook their head — no choice",
                perTarget = listOf(
                    NightEffect.MarkSpent("professor"),
                    NightEffect.Resurrect(on = Ref.Target),
                    NightEffect.PlaceToken("professor", "Alive", Ref.Target),
                ),
            )
        },
    ),
    tokens = listOf(
        // The public record that this seat came back: never swept (digest
        // bmr-townsfolk, professor). WP1's table has it at DAWN.
        TokenRule("professor", "Alive", null, Until.FOREVER),
    ),
)

/**
 * "When a Minion dies by execution, all other players (except Travellers) are
 * drunk until dusk tomorrow."
 *
 * No night step, ever. The trigger lives in the execution funnel; the drunkenness
 * is N `Effect(DRUNK, until = DUSK_AFTER_N_DAYS)` rows plus the grimoire-centre
 * token (lead D15), which is why the token must never impair the seat it is drawn
 * on.
 */
private fun minstrel() = CharacterRule(id = "minstrel")

/** "If both your alive neighbors are good, they can't die." A `Standing` rule (WP1). */
private fun teaLady() = CharacterRule(id = "tealady")

/**
 * "Executed good players might not die."
 *
 * No night step and no token — the whole character is `killOutcome` step 10,
 * which asks the storyteller EVERY time a good player is executed.
 */
private fun pacifist() = CharacterRule(id = "pacifist")

/**
 * "The 1st time you die, you don't."
 *
 * No night step. `killOutcome` step 15 — deliberately last, so any other
 * protection resolves first and the once-per-game is not consumed.
 */
private fun fool() = CharacterRule(id = "fool")

// ====================================================================
// Outsiders
// ====================================================================

/**
 * "Each night, the 1st player to choose you with their ability is drunk until
 * dusk. You become their alignment."
 *
 * No night step — correctly absent from both order lists. The trigger is reactive:
 * it fires inside another character's resolution, the moment the Goon's seat lands
 * in a resolved target list, and only for a PLAYER's choice
 * (`LedgerEntry.byStoryteller == false`). The engine has no hook for that yet;
 * see this package's report (WP2 follow-up). The token itself is correct today.
 */
private fun goon() = CharacterRule(id = "goon")

/**
 * "You think you are a Demon, but you are not. The Demon knows who you are & who
 * you choose at night."
 *
 * `Identity.derivedGrants` REPLACES the Lunatic's ability with the believed
 * Demon's, at the Lunatic's own slot and with `alwaysFalse = true`, so most games
 * run the BELIEVED Demon's registry row instead of this one — which is why every
 * BMR Demon row below routes an `alwaysFalse` holder through [placeboAction]:
 * the choices are recorded and marked, and nothing whatsoever happens.
 *
 * This row is the fallback for a Lunatic with no believed Demon chosen yet. The
 * bluffs and the fake Minions are WP4's `Bluffs.requirements` /
 * `SetupRequirements`, and the real Demon is shown the Lunatic on `DEMON_INFO`.
 */
private fun lunatic(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Let the Lunatic act as the Demon they believe they are. Place their " +
            "Chosen markers. Nobody dies from this — nothing they do has any effect.",
        action = { ctx -> placeboAction(ctx, "lunatic", max = 3) },
    )
    return CharacterRule(id = "lunatic", firstNight = rule, otherNight = rule)
}

/**
 * "You might die at any time."
 *
 * A storyteller lever, never a wake. The cause is `GOOD_ABILITY`, which is the one
 * that gives the right protection answer: the Tea Lady, Sailor, Innkeeper and Fool
 * stop it; the Monk and Soldier do not, and neither the Sage nor the Grandmother
 * fires. A Tinker killed BY DAY arms the Godfather; killed at night, it does not.
 */
private fun tinker() = CharacterRule(
    id = "tinker",
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.hasAbility),
        wakeCounts = WakeCount.NONE,
        prompt = "The Tinker might die tonight — your call. Never kill them when it would " +
            "end the game, and never say how they died.",
        action = {
            YesNo(
                sourceId = "tinker",
                prompt = "DOES THE TINKER DIE TONIGHT?",
                yesLabel = "Yes — they die",
                noLabel = "No — leave them alive",
                onYes = listOf(
                    NightEffect.Attack(on = Ref.Source, cause = DeathCause.GOOD_ABILITY),
                    NightEffect.PlaceToken("tinker", "Dead", Ref.Source),
                ),
            )
        },
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Tinker dies now",
            recordsAs = "tinker",
            available = { _, _, holder -> holder.alive },
        ),
    ),
    tokens = listOf(
        // Missing from WP1's table entirely; the official reminder exists.
        TokenRule("tinker", "Dead", null, Until.DAWN),
    ),
)

/**
 * "When you learn that you died, publicly choose 1 alive player. Tonight, if it
 * was a good player, they die."
 *
 * The curse is armed by the Moonchild's own `DeathEvent` — a SURVIVED execution
 * (Devil's Advocate, Pacifist, Fool, Sailor, Tea Lady) and a Zombuul-style
 * `registeredOnly` shroud must not arm it. The choice is made in daylight and
 * recorded as a STATEMENT; this night row only resolves it. The ability works
 * while dead, by definition.
 */
private fun moonchild() = CharacterRule(
    id = "moonchild",
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    killCause = DeathCause.GOOD_ABILITY,
    otherNight = NightRule(
        gate = WakePredicate { ctx ->
            val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Moonchild in play")
            val curse = moonchildChoice(ctx.state, holder.id)
            when {
                curse == null && holder.alive ->
                    StepGate.Skip("the Moonchild is alive — nothing to do")

                curse == null ->
                    StepGate.Skip("the Moonchild has not publicly chosen anybody yet")

                // The snapshot the ledger took WHEN THEY CHOSE, not tonight's
                // state: "good when chosen", "sober when chosen".
                curse.impaired -> StepGate.Skip(
                    "the Moonchild was drunk or poisoned when they chose — nobody dies",
                )

                else -> StepGate.Fire
            }
        },
        wakeCounts = WakeCount.NONE,
        prompt = "The Moonchild publicly chose a player today. If that player was GOOD when " +
            "chosen, and the Moonchild was sober and healthy then, that player dies now.",
        action = { ctx ->
            val holder = ctx.holder
            val named = holder?.let { moonchildChoice(ctx.state, it.id) }
                ?.targetIds
                ?.firstOrNull()
                ?.let { ctx.state.player(it)?.name }
            ChoosePlayers(
                sourceId = "moonchild",
                prompt = if (named == null) "WHO DID THEY CHOOSE?" else "CONFIRM: $named",
                min = 1,
                max = 1,
                constraints = listOf(TargetConstraint.ANY_LIVING_STATE),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They were evil, or the Moonchild was impaired — nobody dies",
                perTarget = listOf(
                    NightEffect.Attack(on = Ref.Target, cause = DeathCause.GOOD_ABILITY),
                    NightEffect.PlaceToken("moonchild", "Dead", Ref.Target),
                ),
            )
        },
    ),
    // Arming the curse: the Moonchild learns of their own death at dawn (a night
    // death) or immediately (a day death), and chooses publicly, right then.
    onDeath = listOf(
        DeathTrigger(
            gate = { state, event, holder ->
                event.playerId == holder.id &&
                    !event.registeredOnly &&
                    moonchildChoice(state, holder.id) == null
            },
            produce = { _, event, holder ->
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = if (event.atNight) BriefingSlot.DAWN else BriefingSlot.NOW,
                            kind = PromptKind.CHOOSE_PLAYER,
                            sourceId = "moonchild",
                            subjectPlayerId = holder.id,
                            title = "${holder.name} died — they publicly choose 1 alive player now",
                            detail = "Record the choice, the target's alignment and the " +
                                "Moonchild's impairment AT THIS MOMENT. Tonight, if that " +
                                "player was good, they die.",
                        ),
                    ),
                )
            },
        ),
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Moonchild's public choice",
            recordsAs = "moonchild",
            available = { state, _, holder ->
                !holder.alive && moonchildChoice(state, holder.id) == null
            },
        ),
    ),
)

// ====================================================================
// Minions
// ====================================================================

/**
 * "You start knowing which Outsiders are in play. If 1 died today, choose a player
 * tonight: they die. [-1 or +1 Outsider]"
 *
 * The arming condition is computed from `DeathEvent`s, never remembered: an
 * Outsider who died BY DAY. A night death does not count, and neither does a
 * SURVIVED execution — which is why the Devil's Advocate cannot arm this. When
 * somebody died today but nobody who registered as an Outsider, the storyteller
 * is ASKED rather than overruled: misregistration is theirs to rule on.
 * Exactly ONE kill, even if two Outsiders died.
 */
private fun godfather() = CharacterRule(
    id = "godfather",
    killCause = DeathCause.EVIL_ABILITY,
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the Godfather each Outsider character token that is in play.",
        infoId = "godfather",
    ),
    otherNight = NightRule(
        gate = Gates.all(
            Gates.aliveHolder,
            Gates.someoneDiedToday(expected = true, team = Team.OUTSIDER),
        ),
        prompt = "An Outsider died today. The Godfather points at any player: that player " +
            "dies. Only ONE kill, even if two Outsiders died.",
        action = {
            ChoosePlayers(
                sourceId = "godfather",
                prompt = "WHO DID THEY CHOOSE?",
                min = 0,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "No kill (impaired, protected, or storyteller's choice)",
                perTarget = listOf(
                    NightEffect.Attack(on = Ref.Target, cause = DeathCause.EVIL_ABILITY),
                    NightEffect.PlaceToken("godfather", "Dead", Ref.Target),
                ),
            )
        },
        infoId = "godfather",
    ),
)

/**
 * "Each night, choose a living player (different to last night): if executed
 * tomorrow, they don't die."
 *
 * The user's second complaint, and the reason "different to last night" is a
 * `TargetConstraint` and never a token: the `Survives Execution` token is swept at
 * DUSK, *before* the Devil's Advocate's other-night step at index 25, so the token
 * cannot be the memory. `Memory.lastChoice` reads the ledger, which survives
 * everything (lead D1/D3).
 */
private fun devilsAdvocate(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Devil's Advocate points at a living player, DIFFERENT from last " +
            "night. If that player is executed tomorrow, they do not die.",
        // W7C: the picker excludes last night's choice; the banner says who.
        banner = { ctx ->
            val last = devilsAdvocateLastPick(ctx)
            if (last.isEmpty()) "" else "Chosen last night: " + last.joinToString() + " — not again tonight."
        },
        action = { ctx ->
            val last = devilsAdvocateLastPick(ctx)
            ChoosePlayers(
                sourceId = "devilsadvocate",
                prompt = if (last.isEmpty()) {
                    "WHO DID THEY CHOOSE?"
                } else {
                    "WHO DID THEY CHOOSE? (not ${last.joinToString()} — chosen last night)"
                },
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ALIVE,
                    TargetConstraint.SELF_ALLOWED,
                    TargetConstraint.DIFFERENT_FROM_LAST_NIGHT,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They chose nobody / were not woken",
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "devilsadvocate",
                        label = "Survives Execution",
                        on = Ref.Target,
                        kind = EffectKind.SURVIVES_EXECUTION,
                        until = Until.DUSK,
                    ),
                ),
            )
        },
    )
    return CharacterRule(id = "devilsadvocate", firstNight = rule, otherNight = rule)
}

/**
 * "Once per game, at night*, choose a player: they die, even if for some reason
 * they could not."
 *
 * `respectProtection = false` becomes `KillCause.ignoresProtection`, which is step
 * 1 of the kill funnel: nothing else is even evaluated. A head-shake spends
 * nothing.
 */
private fun assassin() = CharacterRule(
    id = "assassin",
    killCause = DeathCause.EVIL_ABILITY,
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "The Assassin either shakes their head, or points at a player. That player " +
            "dies — no protection of any kind stops it.",
        action = {
            ChoosePlayers(
                sourceId = "assassin",
                prompt = "WHO DID THEY CHOOSE?",
                min = 0,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.ALIVE_FIRST,
                allowNone = true,
                noneLabel = "They shook their head 'no'",
                perTarget = listOf(
                    NightEffect.Attack(
                        on = Ref.Target,
                        cause = DeathCause.EVIL_ABILITY,
                        respectProtection = false,
                    ),
                    NightEffect.PlaceToken("assassin", "Dead", Ref.Target),
                    NightEffect.MarkSpent("assassin"),
                ),
            )
        },
    ),
)

/**
 * "If the Demon dies by execution (ending the game), play for 1 more day. If a
 * player is then executed, their team loses."
 *
 * No wake, no token. The entry condition and the extra day belong to `WinCheck`
 * and `Execution.consequences`, which already hold them.
 */
private fun mastermind() = CharacterRule(id = "mastermind")

// ====================================================================
// Demons
// ====================================================================

/**
 * "Each night*, if no-one died today, choose a player: they die. The 1st time you
 * die, you live but register as dead."
 *
 * Only DAY deaths block it — a player killed at NIGHT does not count, and that is
 * the rule the app is most likely to get wrong. The first "death" is stored as
 * `DeathEvent(registeredOnly = true)` by `killOutcome` step 14, so the seat is
 * shrouded but `isTrulyAlive`: it keeps acting, which is why this row cannot use
 * `Gates.aliveHolder`.
 */
private fun zombuul() = CharacterRule(
    id = "zombuul",
    actsWhileDead = true,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = Gates.all(
            aliveOrRegistersDead(),
            Gates.someoneDiedToday(expected = false),
            Gates.notExorcised,
        ),
        prompt = "Nobody died today. The Zombuul points at any player — killing themselves " +
            "and hiding in plain sight is legal.",
        action = { ctx ->
            demonAttack(
                ctx = ctx,
                sourceId = "zombuul",
                deadLabel = "Dead",
                min = 0,
                max = 1,
                allowNone = true,
            )
        },
    ),
)

/**
 * "Each night, choose a player: they are poisoned. The previously poisoned player
 * dies then becomes healthy."
 *
 * **The user's first report.** The order is load-bearing and is exactly lead D4:
 *
 *  1. the ACTION places the new `Poisoned` token — the official data has TWO
 *     copies, so it does not displace the standing one;
 *  2. the PENDING half, computed from the grimoire as it was BEFORE the choice,
 *     then kills the standing victim — who is therefore still poisoned at the
 *     moment of death (`abilityImpairedAtDeath == true`, so every on-death
 *     trigger they own fires malfunctioning);
 *  3. and removes their poison whether they died or were protected — "the
 *     Innkeeper prevents the Pukka from killing a poisoned player, then that
 *     player is no longer poisoned".
 *
 * `Attack(deferred = true)` is lead D63: an Exorcised Pukka does not poison
 * tonight, but the player it poisoned last night still dies.
 */
private fun pukka(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "The Pukka points at a player: that player is POISONED. The player " +
            "poisoned on the previous night dies now, still poisoned, then becomes healthy.",
        action = { ctx ->
            if (isPlacebo(ctx)) {
                placeboAction(ctx, "pukka", max = 1)
            } else {
                ChoosePlayers(
                    sourceId = "pukka",
                    prompt = "WHO DID THEY CHOOSE?",
                    min = 1,
                    max = 1,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.ALIVE_FIRST,
                    allowNone = true,
                    noneLabel = "They chose nobody",
                    perTarget = listOf(
                        NightEffect.PlaceToken(
                            sourceId = "pukka",
                            label = "Poisoned",
                            on = Ref.Target,
                            kind = EffectKind.POISONED,
                            until = Until.ON_SOURCE_STEP,
                        ),
                    ),
                )
            }
        },
        pending = { ctx ->
            if (isPlacebo(ctx)) {
                emptyList()
            } else {
                standingVictims(ctx).flatMap { victim ->
                    listOf(
                        NightEffect.Attack(
                            on = Ref.Seat(victim),
                            cause = DeathCause.DEMON_KILL,
                            deferred = true,
                        ),
                        NightEffect.PlaceToken("pukka", "Dead", Ref.Seat(victim)),
                        NightEffect.RemoveToken("pukka", "Poisoned", Ref.Seat(victim)),
                    )
                }
            }
        },
    )
    return CharacterRule(
        id = "pukka",
        killCause = DeathCause.DEMON_KILL,
        firstNight = rule,
        otherNight = rule,
    )
}

/**
 * "Each night*, choose 2 players: they die. A dead player you chose last night
 * might be regurgitated."
 *
 * The two kills resolve ONE AT A TIME, with protection re-derived between them —
 * that is the wiki's own Tea Lady example, and `ChoosePlayers.perTarget` gives it
 * for free. The regurgitation is a storyteller "might", so it is a DECIDE prompt
 * naming last night's picks rather than a silent decision; it sits in the PENDING
 * half, so an Exorcised Shabaloth still gets asked (its kills do not happen).
 */
private fun shabaloth() = CharacterRule(
    id = "shabaloth",
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "First settle the regurgitation, then the Shabaloth points at two players, " +
            "one at a time. Dead players are legal targets — that is how tomorrow's " +
            "regurgitation is set up.",
        // W7C: the candidates are on the row, not only inside the pending prompt.
        banner = { ctx ->
            val candidates = regurgitationCandidates(ctx)
            if (candidates.isEmpty()) {
                ""
            } else {
                "May regurgitate: " + candidates.joinToString { it.name } +
                    " — decide BEFORE tonight's two picks."
            }
        },
        action = { ctx ->
            if (isPlacebo(ctx)) {
                placeboAction(ctx, "shabaloth", max = 2)
            } else {
                ChoosePlayers(
                    sourceId = "shabaloth",
                    prompt = "WHICH TWO DID THEY CHOOSE? (in order)",
                    min = 2,
                    max = 2,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.ALIVE_FIRST,
                    perTarget = listOf(
                        NightEffect.Attack(on = Ref.Target, cause = DeathCause.DEMON_KILL),
                        NightEffect.PlaceToken("shabaloth", "Dead", Ref.Target),
                    ),
                )
            }
        },
        pending = { ctx ->
            val candidates = regurgitationCandidates(ctx)
            if (isPlacebo(ctx) || candidates.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.NOW,
                        kind = PromptKind.DECIDE,
                        sourceId = "shabaloth",
                        title = "Regurgitate one of " +
                            candidates.joinToString { it.name } +
                            "? They come back with their ability, once-per-game included — " +
                            "announce it at dawn, after the deaths.",
                    ),
                )
            }
        },
    ),
    tokens = listOf(
        // The permanent public record of a regurgitation (digest
        // bmr-evil-and-outsiders, shabaloth). WP1's table has it at DAWN.
        TokenRule("shabaloth", "Alive", null, Until.FOREVER),
    ),
)

/**
 * "Each night*, you may choose a player: they die. If your last choice was
 * no-one, choose 3 players tonight."
 *
 * The charge is the official `3 Attacks` token, not a ledger read, because it must
 * survive an Exorcised night: the charge carries to the Po's next WAKE, not to the
 * next night. Choosing no-one charges even while drunk or poisoned (explicit wiki
 * rule); a kill that merely FAILS is still a choice and charges nothing. A charged
 * night always spends the charge, even when every attack failed.
 */
private fun po() = CharacterRule(
    id = "po",
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "If the Po is charged they must point at THREE players, one at a time. " +
            "Otherwise they point at one player, or shake their head and charge.",
        action = { ctx ->
            val holder = ctx.holder
            val charged = holder != null &&
                DayRules.hasToken(ctx.state, holder.id, "po", "3 Attacks")
            when {
                isPlacebo(ctx) -> placeboAction(ctx, "po", max = if (charged) 3 else 1)

                charged -> ChoosePlayers(
                    sourceId = "po",
                    prompt = "WHICH THREE DID THEY CHOOSE? (in order)",
                    min = 3,
                    max = 3,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.ALIVE_FIRST,
                    perTarget = listOf(
                        NightEffect.Attack(on = Ref.Target, cause = DeathCause.DEMON_KILL),
                        NightEffect.PlaceToken("po", "Dead", Ref.Target),
                    ),
                    // The charge is spent even when every attack failed.
                    onResolve = listOf(NightEffect.RemoveToken("po", "3 Attacks", Ref.Source)),
                    onNone = listOf(NightEffect.RemoveToken("po", "3 Attacks", Ref.Source)),
                )

                else -> ChoosePlayers(
                    sourceId = "po",
                    prompt = "WHO DID THEY CHOOSE?",
                    min = 0,
                    max = 1,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.ALIVE_FIRST,
                    allowNone = true,
                    noneLabel = "Shook their head — no-one (the Po charges)",
                    perTarget = listOf(
                        NightEffect.Attack(on = Ref.Target, cause = DeathCause.DEMON_KILL),
                        NightEffect.PlaceToken("po", "Dead", Ref.Target),
                    ),
                    onNone = listOf(NightEffect.PlaceToken("po", "3 Attacks", Ref.Source)),
                )
            }
        },
    ),
)

// ====================================================================
// Helpers — private to this file (ARCHITECTURE §4 WP7a–i)
// ====================================================================

/**
 * A Zombuul whose first death has happened is stored dead but is alive by the
 * rules, and keeps acting. Everything else that is dead has no ability.
 *
 * A function, not a top-level `val`: [BMR_RULES] is initialised first, and a
 * property declared below it would still be null when the rows are built.
 */
private fun aliveOrRegistersDead(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    if (holder.alive || ctx.state.isTrulyAlive(holder.id)) {
        StepGate.Fire
    } else {
        StepGate.Skip("dead — no ability")
    }
}

/**
 * True when this row is being run by somebody who only BELIEVES they hold the
 * character: a Lunatic, a Drunk or a Marionette shown a Demon token
 * (`ActingRole.alwaysFalse`). Nothing they choose may have any effect.
 */
private fun isPlacebo(ctx: NightContext): Boolean = ctx.role?.alwaysFalse == true

/**
 * The believed Demon's action with every consequence removed: the choice is
 * recorded (the real Demon is shown it, and the Mathematician jinx reads it) and,
 * for a Lunatic, marked with their own official `Chosen` tokens. No kills, no
 * poison, no protection change, ever.
 */
private fun placeboAction(ctx: NightContext, sourceId: String, max: Int): NightAction {
    val isLunatic = Character.normalizeId(ctx.holder?.characterId.orEmpty()) == "lunatic"
    return ChoosePlayers(
        sourceId = sourceId,
        prompt = "WHO DID THEY CHOOSE? (nothing happens)",
        min = 0,
        max = max,
        constraints = listOf(
            TargetConstraint.ANY_LIVING_STATE,
            TargetConstraint.SELF_ALLOWED,
        ),
        sort = TargetSort.ALIVE_FIRST,
        allowNone = true,
        noneLabel = "They chose nobody",
        perTarget = if (isLunatic) {
            listOf(NightEffect.PlaceToken("lunatic", "Chosen", Ref.Target))
        } else {
            emptyList()
        },
    )
}

/**
 * The ordinary "point at one player, they die" Demon action.
 *
 * `ALIVE` covers a Zombuul that only REGISTERS as dead — `allowed()` reads
 * `isTrulyAlive` — so the wiki's "kill yourself and hide in plain sight" still
 * works, while a real corpse cannot be attacked twice (WP2 acceptance).
 */
private fun demonAttack(
    ctx: NightContext,
    sourceId: String,
    deadLabel: String,
    min: Int,
    max: Int,
    allowNone: Boolean,
): NightAction {
    if (isPlacebo(ctx)) return placeboAction(ctx, sourceId, max)
    return ChoosePlayers(
        sourceId = sourceId,
        prompt = "WHO DID THEY CHOOSE?",
        min = min,
        max = max,
        constraints = listOf(
            TargetConstraint.ALIVE,
            TargetConstraint.SELF_ALLOWED,
        ),
        sort = TargetSort.ALIVE_FIRST,
        allowNone = allowNone,
        noneLabel = "No kill (impaired, protected, or storyteller's choice)",
        perTarget = listOf(
            NightEffect.Attack(on = Ref.Target, cause = DeathCause.DEMON_KILL),
            NightEffect.PlaceToken(sourceId, deadLabel, Ref.Target),
        ),
    )
}

/**
 * Seats carrying THIS Pukka's standing `Poisoned` token, read from the grimoire as
 * it was before tonight's choice landed. A hand-placed token counts: `Status`
 * projects it through the same [TokenRule] the engine placed one with.
 */
private fun standingVictims(ctx: NightContext): List<Long> {
    val key = Tokens.key("pukka", "Poisoned")
    val holderId = ctx.holder?.id
    return ctx.state.seats
        .filter { seat ->
            Status.effectsOn(ctx.state, ctx.lookup, seat.id).any {
                Tokens.key(it.sourceCharacterId, it.label) == key &&
                    (it.sourcePlayerId == null || holderId == null || it.sourcePlayerId == holderId)
            }
        }
        .map { it.id }
}

/** Dead seats the Shabaloth chose on its previous wake — tomorrow's menu. */
private fun regurgitationCandidates(ctx: NightContext): List<Player> =
    Memory.lastChoice(ctx.state, "shabaloth", ctx.holder?.id)
        ?.targetIds
        .orEmpty()
        .mapNotNull { ctx.state.player(it) }
        .filterNot { it.alive }

/**
 * Yesterday's Gossip statement, still unjudged or judged but not yet consumed.
 * Recorded by the Day tab whether or not a Gossip is in play (invariant I3), so
 * the speaker is not required to be the Gossip's own seat.
 */
/** The names the Devil's Advocate chose on their previous wake, from the ledger. */
private fun devilsAdvocateLastPick(ctx: NightContext): List<String> =
    Memory.lastChoice(ctx.state, "devilsadvocate", ctx.holder?.id)
        ?.targetIds
        ?.mapNotNull { ctx.state.player(it)?.name }
        .orEmpty()

private fun gossipStatement(state: GameState): LedgerEntry? =
    Memory.statementsOn(state, day = state.cycle - 1, sourceId = "gossip")
        .lastOrNull { it.resolvedCycle == null }

/** The Moonchild's public choice, made on the day that has just ended, or today. */
private fun moonchildChoice(state: GameState, holderId: Long): LedgerEntry? =
    Memory.by(state, LedgerKind.STATEMENT, "moonchild", holderId)
        .lastOrNull { it.resolvedCycle == null && it.targetIds.isNotEmpty() }

/**
 * True when the seat marked `grandmother/Grandchild` was killed by a Demon's own
 * ability tonight and is still dead. A grandchild the Professor resurrected at
 * order 64 is alive again by the Grandmother's 72, so she lives.
 */
@Suppress("DEPRECATION")
private fun grandchildDemonKilledTonight(
    state: GameState,
    grandmotherId: Long,
    night: Int,
): Boolean {
    val grandchild = state.seats.firstOrNull {
        it.id != grandmotherId && DayRules.hasToken(state, it.id, "grandmother", "Grandchild")
    } ?: return false
    if (grandchild.alive) return false
    val death: DeathEvent = state.deaths.lastOrNull {
        it.playerId == grandchild.id && it.day == night && it.atNight
    } ?: return false
    if (death.resurrectedAtCycle != null || death.registeredOnly) return false
    return death.cause == DeathCause.DEMON_KILL || death.cause == DeathCause.DEMON
}
