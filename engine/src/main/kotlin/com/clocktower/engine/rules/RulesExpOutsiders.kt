package com.clocktower.engine.rules

import com.clocktower.engine.Bluffs
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.ChooseCharacter
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Decisions
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.GameState
import com.clocktower.engine.LedgerKind
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
import com.clocktower.engine.SetupRequirements
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
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate

// ---------------------------------------------------------------------------
// Character ids and official Title Case token labels (lead D5).
// Never compared by hand — everything goes through `Tokens.key`.
// ---------------------------------------------------------------------------

private const val DAMSEL = "damsel"
private const val GOLEM = "golem"
private const val HATTER = "hatter"
private const val HERETIC = "heretic"
private const val HERMIT = "hermit"
private const val OGRE = "ogre"
private const val PLAGUE_DOCTOR = "plaguedoctor"
private const val POLITICIAN = "politician"
private const val PUZZLEMASTER = "puzzlemaster"
private const val SNITCH = "snitch"
private const val ZEALOT = "zealot"

private const val HUNTSMAN = "huntsman"
private const val MARIONETTE = "marionette"
private const val LEGION = "legion"

private const val GUESS_USED = "Guess Used"
private const val MAY_NOT_NOMINATE = "May Not Nominate"
private const val TEA_PARTY_TONIGHT = "Tea Party Tonight"
private const val STORYTELLER_ABILITY = "Storyteller Ability"
private const val FRIEND = "Friend"
private const val DRUNK = "Drunk"

/** The Zealot's threshold, straight off the card: "5 or more players alive". */
private const val ZEALOT_ALIVE_THRESHOLD = 5

/**
 * Experimental Outsider behaviour. Owned by WP7-EXP-O.
 *
 * Five of the eleven never wake (Golem, Heretic, Politician, Puzzlemaster,
 * Zealot) and act at nomination / end of game / any time; two fire *because*
 * their holder died (Hatter, Plague Doctor); the Hermit owns no row of its own
 * at all — `Identity.derivedGrants` already gives that seat every Outsider on
 * the script, so each borrowed ability is planned from ITS registry row here.
 */
internal val EXP_OUTSIDER_RULES: List<CharacterRule> = listOf(
    damsel(),
    golem(),
    hatter(),
    heretic(),
    hermit(),
    ogre(),
    plagueDoctor(),
    politician(),
    puzzlemaster(),
    snitch(),
    zealot(),
)

// ===========================================================================
// Damsel — "All Minions know a Damsel is in play. If a Minion publicly guesses
//           you (once), your team loses."
// ===========================================================================

private fun damsel() = CharacterRule(
    id = DAMSEL,
    // "A dead Damsel is safe": the guess resolver must find no ability.
    keepsAbilityWhenDead = false,
    tokens = listOf(TokenRule(DAMSEL, GUESS_USED, EffectKind.SPENT, Until.FOREVER)),

    // Night 1 is not an action — it is the Minion-info hand-out, which the
    // storyteller may still owe on a later night if MINION_INFO was skipped
    // (Poppy Grower, fewer than 7 residents).
    firstNight = NightRule(
        gate = damselShownGate(),
        prompt = "During Minion info, show every Minion (never the Marionette) the DAMSEL token. " +
            "Say nothing about who the Damsel is.",
        wakeCounts = WakeCount.INFORMED,
    ),

    // The only other-night business is the Huntsman's transformation.
    otherNight = NightRule(
        gate = damselTransformGate(),
        prompt = "The Huntsman chose the Damsel. Choose a not-in-play Townsfolk, " +
            "show the 'You are' token and their new character token.",
        action = {
            ChooseCharacter(
                sourceId = DAMSEL,
                prompt = "WHICH NOT-IN-PLAY TOWNSFOLK DO THEY BECOME?",
                pool = CharacterPool.TOWNSFOLK,
                allowNone = false,
                onResolve = listOf(
                    NightEffect.BecomeCharacter(
                        on = Ref.Source,
                        characterId = "",
                        evil = false,
                        reason = ChangeReason.HUNTSMAN_DAMSEL,
                    ),
                    NightEffect.ShowCardTo(Ref.Source, "YOU ARE"),
                ),
            )
        },
    ),

    // "If a Minion publicly guesses you (once), your team loses." One guess for
    // the whole evil team; the Day tab owns the input, WinCheck the advisory.
    day = DayRule(
        ability = DayAbility(
            label = "Damsel guess",
            oncePerGame = true,
            recordsAs = DAMSEL,
            available = { state, lookup, holder ->
                holder.alive &&
                    Status.hasAbility(state, lookup, holder.id) &&
                    !isSpent(state, lookup, holder, DAMSEL, GUESS_USED)
            },
        ),
    ),
)

/** Owed until the storyteller acknowledges the Minions have seen the token. */
private fun damselShownGate(): WakePredicate = WakePredicate { ctx ->
    when {
        Decisions.bool(ctx.state, SetupRequirements.DAMSEL_MINIONS) ->
            StepGate.Skip("every Minion has already been shown the Damsel token")

        minionSeats(ctx.state, ctx.lookup).isEmpty() ->
            StepGate.Skip("there is no Minion to show the Damsel token to")

        else -> StepGate.Fire
    }
}

/**
 * Fires only when tonight's Huntsman step actually picked THIS Damsel. An
 * impaired Huntsman spends the use and changes nobody, which the CHOICE row's
 * own `impaired` flag records — no live re-evaluation (the Minstrel trap).
 */
private fun damselTransformGate(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Damsel seat")
    val picked = ctx.state.ledger.lastOrNull {
        it.kind == LedgerKind.CHOICE &&
            Character.normalizeId(it.sourceId) == HUNTSMAN &&
            it.cycle == ctx.night &&
            it.atNight &&
            holder.id in it.targetIds
    }
    when {
        picked == null -> StepGate.Skip("the Huntsman has not chosen the Damsel")
        picked.impaired -> StepGate.Skip(
            "the Huntsman was drunk or poisoned — the use is spent but the Damsel does not change",
        )

        else -> StepGate.Fire
    }
}

// ===========================================================================
// Golem — "You may only nominate once per game. When you do, if the nominee is
//          not the Demon, they die."
// ===========================================================================

private fun golem() = CharacterRule(
    id = GOLEM,
    // Not a Demon kill: Monk and Soldier never apply, Sailor/Tea Lady/Fool do.
    killCause = DeathCause.DAY_ABILITY,
    tokens = listOf(TokenRule(GOLEM, MAY_NOT_NOMINATE, EffectKind.NO_NOMINATE, Until.FOREVER)),
    // No night rule at all — the Golem is on neither night list and never wakes.
    // The nomination row itself is WP3's (`DayRules.builtInTriggers` + `applyGolem`),
    // which already reads `Registration.registersAs` and places this token in
    // every branch; a registry override would only duplicate it (lead D61).
)

// ===========================================================================
// Hatter — "If you died today or tonight, the Minion & Demon players may choose
//           new Minion & Demon characters to be."
// ===========================================================================

private fun hatter() = CharacterRule(
    id = HATTER,
    // The row exists BECAUSE the holder is dead: never the red "usually skip".
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    tokens = listOf(TokenRule(HATTER, TEA_PARTY_TONIGHT, null, Until.DAWN)),
    onDeath = listOf(
        DeathTrigger(
            gate = { _, event, holder -> event.playerId == holder.id },
            produce = { state, event, holder ->
                if (event.abilityImpairedAtDeath == true) {
                    // Read the SNAPSHOT, never live state — a Hatter cured after
                    // death still holds no tea party.
                    TriggerResult(
                        prompts = listOf(
                            Prompt(
                                id = 0,
                                at = BriefingSlot.NOW,
                                kind = PromptKind.ANNOUNCE,
                                sourceId = HATTER,
                                subjectPlayerId = holder.id,
                                title = "${holder.name} (Hatter) died drunk or poisoned — " +
                                    "the evil team does NOT change characters tonight.",
                            ),
                        ),
                    )
                } else {
                    TriggerResult(
                        effects = listOf(
                            Effect(
                                id = 0,
                                kind = EffectKind.MARKER,
                                targetId = holder.id,
                                sourceCharacterId = HATTER,
                                sourcePlayerId = holder.id,
                                until = Until.DAWN,
                                // The Hatter is dead; the token must outlive them.
                                endsWithSource = false,
                                label = TEA_PARTY_TONIGHT,
                                note = "The Minions and the Demon may each choose a new character tonight.",
                                createdCycle = state.cycle,
                                createdAtNight = state.phase != Phase.DAY,
                            ),
                        ),
                        prompts = listOf(
                            Prompt(
                                id = 0,
                                at = BriefingSlot.DUSK,
                                kind = PromptKind.ANNOUNCE,
                                sourceId = HATTER,
                                subjectPlayerId = holder.id,
                                title = "Tea party tonight — the Minions and the Demon " +
                                    "may each choose a new character.",
                            ),
                        ),
                    )
                }
            },
        ),
    ),
    // Never on the first night: nobody has died yet.
    otherNight = NightRule(
        gate = teaPartyGate(),
        prompt = "Tea party: wake the Demon, then each Minion in turn (never the Marionette). " +
            "Each may take a new character of their own team that is not in play. " +
            "Run this row once per player who changes.",
        action = {
            ChoosePlayerAndCharacter(
                sourceId = HATTER,
                prompt = "WHO CHANGES, AND INTO WHAT?",
                playerConstraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.EVIL),
                pool = CharacterPool.EVIL,
                requireNotInPlay = true,
                onResolve = listOf(
                    NightEffect.BecomeCharacter(
                        on = Ref.Target,
                        characterId = "",
                        evil = true,
                        reason = ChangeReason.HATTER,
                    ),
                    NightEffect.ShowCardTo(Ref.Target, "YOU ARE"),
                ),
            )
        },
    ),
)

private fun teaPartyGate(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Hatter seat")
    val inPlay = ctx.state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
    val death = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.resurrectedAtCycle == null
    }
    when {
        LEGION in inPlay -> StepGate.Skip("Legion is in play — the Hatter has no ability")
        !hasToken(ctx.state, ctx.lookup, holder, HATTER, TEA_PARTY_TONIGHT) ->
            StepGate.Skip("the Hatter has not died — no tea party tonight")

        death?.abilityImpairedAtDeath == true -> StepGate.Skip(
            "the Hatter was drunk or poisoned when they died — nobody changes character",
        )

        teaPartyGuests(ctx.state, ctx.lookup).isEmpty() ->
            StepGate.Skip("no Minion or Demon is left to change character")

        else -> StepGate.Fire
    }
}

/**
 * The Demon first, then the Minions in seat order. The Marionette is never
 * woken by an ability that would confirm they are a Minion, so they never
 * change and never appear here.
 */
private fun teaPartyGuests(state: GameState, lookup: (String) -> Character?): List<Player> {
    val demons = state.seats.filter { it.characterId?.let(lookup)?.team == Team.DEMON }
    return demons + minionSeats(state, lookup)
}

// ===========================================================================
// Heretic — "Whoever wins, loses & whoever loses, wins, even if you are dead."
// ===========================================================================

private fun heretic() = CharacterRule(
    id = HERETIC,
    // "even if you are dead" — the whole ability is a game-end rule, and a dead
    // Heretic still reverses the result. A drunk or poisoned one does not, which
    // `WinCheck.hereticPass` checks at the moment the game ends (lead D40).
    keepsAbilityWhenDead = true,
    // No night, no day, no tokens, no targets: nothing to declare.
)

// ===========================================================================
// Hermit — "You have all Outsider abilities. [-0 or -1 Outsider]"
// ===========================================================================

private fun hermit() = CharacterRule(
    id = HERMIT,
    // One row per borrowed Outsider, each at that Outsider's own night position
    // and keyed by `StepKey.abilityId` — `Identity.derivedGrants` emits
    // ADD(every Outsider on the script, "hermit"), so nothing is needed here to
    // make them appear. This flag only documents the shape (lead D39/D16).
    perHolder = true,
    // On death the Hermit keeps ONLY the borrowed abilities that say they work
    // while dead (Recluse misregistration, the Puzzlemaster's standing drunk,
    // the Heretic's reversal). Those carry their own flags; the Hermit seat
    // itself must not keep the rest, so this stays false.
    keepsAbilityWhenDead = false,
    // "1" / "2" / "3" stand in for a borrowed Outsider's own token when that
    // Outsider is ALSO in play; `PlacedReminder.note` carries the meaning the
    // bare numeral loses.
    tokens = listOf(
        TokenRule(HERMIT, "1", null, Until.FOREVER),
        TokenRule(HERMIT, "2", null, Until.FOREVER),
        TokenRule(HERMIT, "3", null, Until.FOREVER),
    ),
)

// ===========================================================================
// Ogre — "On your 1st night, choose a player (not yourself): you become their
//         alignment (you don't know which) even if drunk or poisoned."
// ===========================================================================

private fun ogre() = CharacterRule(
    id = OGRE,
    tokens = listOf(TokenRule(OGRE, FRIEND, null, Until.FOREVER)),
    firstNight = ogreRule(),
    // The same rule on other nights, for an Ogre created mid-game: "on your 1st
    // night" is the night they ENTER PLAY. WP6C put `ogre` into the otherNight
    // order, in the same place it sits on the first night (after the Spy).
    otherNight = ogreRule(),
)

private fun ogreRule() = NightRule(
    // Deliberately NOT gated on impairment: "even if drunk or poisoned" is
    // explicit, and the Ogre is one of exactly three abilities that work while
    // impaired. Alive only, and only on their FIRST night as the Ogre.
    gate = ogreFriendGate(),
    prompt = "The Ogre points at a player who is not themself. Place the Friend token. " +
        "If that player registers as evil, the Ogre becomes evil — they are not told. " +
        "Give no signal either way.",
    action = {
        ChoosePlayers(
            sourceId = OGRE,
            prompt = "WHO DID THEY CHOOSE?",
            min = 1,
            max = 1,
            constraints = listOf(TargetConstraint.ALIVE, TargetConstraint.NOT_SELF),
            sort = TargetSort.SEAT_ORDER,
            allowNone = false,
            perTarget = listOf(
                NightEffect.PlaceToken(
                    sourceId = OGRE,
                    label = FRIEND,
                    on = Ref.Target,
                    kind = EffectKind.MARKER,
                    until = Until.FOREVER,
                ),
                // SCHEMA GAP (filed to WP2): there is no `NightEffect.SetAlignment`,
                // and the answer depends on the seat that was just picked, which a
                // static effect list cannot branch on. The storyteller is asked
                // instead — never guessed, never silently skipped.
                NightEffect.QueuePrompt(
                    at = BriefingSlot.NOW,
                    kind = PromptKind.DECIDE,
                    sourceId = OGRE,
                    title = "Set the Ogre's alignment now: their friend registers EVIL " +
                        "-> the Ogre is evil; registers GOOD -> the Ogre stays good. " +
                        "The Ogre is never told, and their team stays OUTSIDER.",
                    on = Ref.Source,
                ),
            ),
        )
    },
)

/**
 * "On your 1st night" — the first night this seat holds the Ogre, not night 1
 * of the game. The CHOICE row survives every token sweep, so it, not the
 * Friend token, is the memory this reads.
 */
private fun ogreFriendGate(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Ogre seat")
    when {
        !holder.alive -> StepGate.Skip("dead — the Ogre chooses only while alive")
        Memory.by(ctx.state, LedgerKind.CHOICE, OGRE, holder.id).isNotEmpty() ->
            StepGate.Skip("they have already chosen their friend")

        ctx.state.alivePlayers.none { it.id != holder.id } ->
            StepGate.Skip("there is nobody else alive to choose")

        else -> StepGate.Fire
    }
}

// ===========================================================================
// Plague Doctor — "When you die, the Storyteller gains a Minion ability."
// ===========================================================================

private fun plagueDoctor() = CharacterRule(
    id = PLAGUE_DOCTOR,
    actsWhileDead = true,
    keepsAbilityWhenDead = true,
    tokens = listOf(
        // Lives in the centre of the grimoire: the ability is held by the
        // storyteller, not by a seat (records-and-memory §C).
        TokenRule(
            PLAGUE_DOCTOR,
            STORYTELLER_ABILITY,
            null,
            Until.FOREVER,
            grimoireCentre = true,
        ),
    ),
    onDeath = listOf(
        DeathTrigger(
            gate = { _, event, holder -> event.playerId == holder.id },
            produce = { _, event, holder ->
                val impaired = event.abilityImpairedAtDeath == true
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = BriefingSlot.NOW,
                            kind = if (impaired) PromptKind.ANNOUNCE else PromptKind.CHOOSE_CHARACTER,
                            sourceId = PLAGUE_DOCTOR,
                            subjectPlayerId = holder.id,
                            title = if (impaired) {
                                "${holder.name} (Plague Doctor) died drunk or poisoned — " +
                                    "you gain NO Minion ability, not even if they are cured later."
                            } else {
                                "You gain a Minion ability NOW — choose which. " +
                                    "It is yours from this instant, not from tonight."
                            },
                        ),
                    ),
                )
            },
        ),
    ),
    // The safety net for a death the storyteller resolved without the picker.
    // Emitted on the first night too, for a night-1 death. WP6C put
    // `plaguedoctor` into the firstNight order for exactly that case; the gate
    // ("dead, and no ability taken yet") keeps it silent in every other game.
    firstNight = plagueDoctorRule(),
    otherNight = plagueDoctorRule(),
)

private fun plagueDoctorRule() = NightRule(
    gate = plagueDoctorGate(),
    prompt = "The Plague Doctor is dead and you have not taken an ability yet. " +
        "Choose a Minion ability to hold yourself. Tell nobody, ever.",
    action = {
        ChooseCharacter(
            sourceId = PLAGUE_DOCTOR,
            prompt = "WHICH MINION ABILITY DOES THE STORYTELLER GAIN?",
            pool = CharacterPool.MINION,
            allowNone = false,
            onResolve = listOf(
                NightEffect.PlaceToken(
                    sourceId = PLAGUE_DOCTOR,
                    label = STORYTELLER_ABILITY,
                    on = Ref.Source,
                    kind = EffectKind.MARKER,
                    until = Until.FOREVER,
                ),
                // SCHEMA GAP (filed to WP2): no `NightEffect.GrantAbility`, so the
                // `FloatingGrant(holder = STORYTELLER)` that makes the gained ability
                // wake at its own night position cannot be placed from here.
                NightEffect.QueuePrompt(
                    at = BriefingSlot.NOW,
                    kind = PromptKind.DECIDE,
                    sourceId = PLAGUE_DOCTOR,
                    title = "Record the Minion ability you now hold as a storyteller grant, " +
                        "so it wakes at that character's own night position.",
                ),
            ),
        )
    },
)

private fun plagueDoctorGate(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Skip("no Plague Doctor seat")
    val death = ctx.state.deaths.lastOrNull {
        it.playerId == holder.id && it.resurrectedAtCycle == null
    }
    val alreadyTaken = ctx.state.floatingGrants.any {
        Character.normalizeId(it.sourceId) == PLAGUE_DOCTOR
    } || hasToken(ctx.state, ctx.lookup, holder, PLAGUE_DOCTOR, STORYTELLER_ABILITY)
    when {
        holder.alive -> StepGate.Skip("the Plague Doctor is alive — nothing has been gained")
        death?.abilityImpairedAtDeath == true -> StepGate.Skip(
            "the Plague Doctor died drunk or poisoned — no ability is gained",
        )

        alreadyTaken -> StepGate.Skip("the storyteller already holds a Minion ability")
        else -> StepGate.Fire
    }
}

// ===========================================================================
// Politician — "If you were the player most responsible for your team losing,
//               you change alignment & win, even if dead."
// ===========================================================================

private fun politician() = CharacterRule(
    id = POLITICIAN,
    // "even if dead". A drunk or poisoned Politician cannot change alignment,
    // which the end-game question surfaces rather than decides.
    keepsAbilityWhenDead = true,
    // The alignment change happens at game end ONLY — nothing during the game
    // registers the Politician as the other alignment, so there is no standing
    // rule, no token and no night row. `WinCheck.endGameQuestions` owns the
    // blocking question (lead D40).
)

// ===========================================================================
// Puzzlemaster — "1 player is drunk, even if you die. If you guess (once) who
//                 it is, learn the Demon player, but guess wrong & get false info."
// ===========================================================================

private fun puzzlemaster() = CharacterRule(
    id = PUZZLEMASTER,
    // The DRUNK survives the Puzzlemaster's death ("even if you die"); the
    // *guess* does not, and `day.ability.available` enforces that separately.
    keepsAbilityWhenDead = true,
    tokens = listOf(
        // "even if you die" is `endsWithSource = false`, NOT a keeps-ability flag:
        // the token holds whether or not the Puzzlemaster's own ability works.
        TokenRule(
            PUZZLEMASTER,
            DRUNK,
            EffectKind.DRUNK,
            Until.FOREVER,
            endsWithSource = false,
            impairs = true,
        ),
        TokenRule(PUZZLEMASTER, GUESS_USED, EffectKind.SPENT, Until.FOREVER),
    ),
    // No night action of any kind: the guess is an any-time day action. A
    // correct guess is one that names the seat carrying THIS character's Drunk
    // token — never merely an impaired seat (a Sailor-drunk or Poisoner-poisoned
    // seat is a WRONG guess).
    day = DayRule(
        ability = DayAbility(
            label = "Puzzlemaster guess",
            oncePerGame = true,
            recordsAs = PUZZLEMASTER,
            available = { state, lookup, holder ->
                holder.alive && !isSpent(state, lookup, holder, PUZZLEMASTER, GUESS_USED)
            },
        ),
    ),
)

/**
 * True when [targetId] is the seat the Puzzlemaster made drunk — the one test
 * the guess resolver may use. `Status.isImpaired` is the trap: it is true for a
 * Sailor, an Innkeeper, a Poisoner, a No Dashii and a Minstrel too.
 */
internal fun puzzlemasterGuessIsCorrect(state: GameState, targetId: Long): Boolean {
    val key = Tokens.key(PUZZLEMASTER, DRUNK)
    val seat = state.player(targetId) ?: return false
    return seat.reminders.any { Tokens.key(it) == key } ||
        state.effects.any { it.targetId == targetId && Tokens.key(it.sourceCharacterId, it.label) == key }
}

// ===========================================================================
// Snitch — "Each Minion gets 3 bluffs."
// ===========================================================================

private fun snitch() = CharacterRule(
    id = SNITCH,
    // The Snitch's own life and sobriety are irrelevant to whether the hand-out
    // happens: an impaired Snitch still hands out bluffs, they are just allowed
    // to be in play (`BluffRequirement.allowInPlay`, WP4).
    firstNight = NightRule(
        gate = snitchGate(midGame = false),
        prompt = "A Snitch is in play. Before you put each Minion back to sleep, show them " +
            "'These characters are not in play' and their OWN three tokens. " +
            "The Marionette is never woken and gets nothing.",
        wakeCounts = WakeCount.INFORMED,
    ),
    // A Snitch or a Minion created mid-game still owes a set. WP6C put `snitch`
    // into the otherNight order, before summoner/lunatic, mirroring its
    // first-night place straight after MINION INFO.
    otherNight = NightRule(
        gate = snitchGate(midGame = true),
        prompt = "A Minion has not been given bluffs yet and a Snitch is in play. " +
            "Wake them and show three not-in-play characters.",
        wakeCounts = WakeCount.INFORMED,
    ),
)

/**
 * One row's worth of gate. The per-Minion cards themselves are separate
 * `MINION_BLUFFS` rows the planner builds from `Bluffs.requirements` (lead
 * D38) — this row only says whether there is anything to hand out at all.
 */
private fun snitchGate(midGame: Boolean): WakePredicate = WakePredicate { ctx ->
    val recipients = minionSeats(ctx.state, ctx.lookup)
    val owed = Bluffs.requirements(ctx.state, ctx.lookup)
        .filter { Character.normalizeId(it.sourceId) == SNITCH }
    val unfilled = owed.filter { ctx.state.bluffSets[it.key].orEmpty().size < it.size }
    when {
        recipients.isEmpty() -> StepGate.Skip("no Minion to give bluffs to")
        midGame && unfilled.isEmpty() -> StepGate.Skip("every Minion already has their three bluffs")
        else -> StepGate.Fire
    }
}

// ===========================================================================
// Zealot — "If there are 5 or more players alive, you must vote for every
//           nomination."
// ===========================================================================

private fun zealot() = CharacterRule(
    id = ZEALOT,
    keepsAbilityWhenDead = true,
    // No night action, no token, no information. The whole ability is a vote
    // obligation, surfaced the moment a nominee is chosen — before hands go up,
    // so a missing hand is noticed before Record.
    day = DayRule(onNomination = ::zealotWarning),
)

private fun zealotWarning(ctx: NominationContext): List<NominationTrigger> {
    val holder = ctx.holder
    if (!holder.alive) return emptyList()
    // Exiles are exempt: a Traveller nominee means an exile call, not a vote.
    val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
    if (nominee?.isTraveller == true) return emptyList()
    // Travellers count towards the 5 — `alivePlayers` includes them.
    val alive = ctx.state.aliveCountWithTravellers
    if (alive < ZEALOT_ALIVE_THRESHOLD) return emptyList()
    // Deliberately NOT suppressed while drunk or poisoned: the Zealot does not
    // know, so expect the hand and do not correct them.
    val working = Status.hasAbility(ctx.state, ctx.lookup, holder.id)
    return listOf(
        NominationTrigger(
            kind = TriggerKind.WARN,
            sourceId = ZEALOT,
            actorId = holder.id,
            targetId = ctx.nomineeId,
            headline = "${holder.name} is the Zealot — they must vote on this nomination " +
                "($alive alive, travellers count).",
            detail = if (working) {
                "Never add the vote for them. If they forget, remind the table and record " +
                    "what actually happened."
            } else {
                "Their ability is not working, but they do not know that — expect them to " +
                    "vote anyway and do not correct them."
            },
            options = listOf(TriggerOption("noted", "Noted", isDefault = true)),
            impaired = !working,
        ),
    )
}

// ===========================================================================
// Shared helpers
// ===========================================================================

/**
 * Every seat that receives Minion information: Minions, plus Legion (which
 * registers as a Minion too), never the Marionette.
 */
private fun minionSeats(state: GameState, lookup: (String) -> Character?): List<Player> =
    state.seats.filter {
        val id = Character.normalizeId(it.characterId.orEmpty())
        id != MARIONETTE && (it.characterId?.let(lookup)?.team == Team.MINION || id == LEGION)
    }

/** Case-insensitive (sourceId, label) test over both effects and free tokens. */
private fun hasToken(
    state: GameState,
    lookup: (String) -> Character?,
    holder: Player,
    sourceId: String,
    label: String,
): Boolean {
    val key = Tokens.key(sourceId, label)
    if (holder.reminders.any { Tokens.key(it) == key }) return true
    return Status.effectsOn(state, lookup, holder.id)
        .any { Tokens.key(it.sourceCharacterId, it.label) == key }
}

/** A once-per-game ability that has been used, by ledger row or by spend mark. */
private fun isSpent(
    state: GameState,
    lookup: (String) -> Character?,
    holder: Player,
    sourceId: String,
    spentLabel: String,
): Boolean = Memory.isSpent(state, sourceId, holder.id) ||
    hasToken(state, lookup, holder, sourceId, spentLabel)
