package com.clocktower.engine.rules

import com.clocktower.engine.ActionOption
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.CardOffer
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Decisions
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Memory
import com.clocktower.engine.NightContext
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Options
import com.clocktower.engine.Phase
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.ShowCardSpec
import com.clocktower.engine.StandingRule
import com.clocktower.engine.StepGate
import com.clocktower.engine.Status
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.TokenRule
import com.clocktower.engine.Tokens
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.TriggerOption
import com.clocktower.engine.TriggerResult
import com.clocktower.engine.Until
import com.clocktower.engine.WakeContext
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate
import com.clocktower.engine.YesNo

/**
 * Experimental Minion behaviour. Owned by WP7-EXP-M.
 *
 * Fifteen characters: boffin, boomdandy, fearmonger, goblin, harpy, marionette,
 * mezepheles, organgrinder, psychopath, summoner, vizier, widow, wizard, wraith,
 * xaan.
 *
 * Some rows are deliberately thin, because the behaviour already lives where the
 * architecture put it and a registry row would only duplicate — or displace — it:
 *  - **marionette** — the night-1 step is `NightInfo`'s (it owns the `marionette`
 *    slot outright), the NO_ABILITY standing rule is WP1's `Standing.emitSelf`,
 *    the believed-character grant is WP4's `Identity.derivedGrants`, and the bag
 *    and seating rules are WP4's `Setup`/`SetupRequirements`.
 *  - **xaan** — the night-X poison is WP1's positional `Standing.xaan`, which
 *    needs `StatusQuery.abilityWorksBase`. Declaring `standing` here would move
 *    it to `Standing.emitSelf`, which has no query to ask, so this row leaves it
 *    alone and owns only the wake gate and the `X` record token.
 *  - **goblin / psychopath / boomdandy** day rows: WP3 hand-wrote most of them
 *    (lead D61). Only where the card says something WP3's row does not is a
 *    registry row written, and then it wins outright for that id.
 */
internal val EXP_MINION_RULES: List<CharacterRule> = listOf(
    boffin(),
    boomdandy(),
    fearmonger(),
    goblin(),
    harpy(),
    marionette(),
    mezepheles(),
    organGrinder(),
    psychopath(),
    summoner(),
    vizier(),
    widow(),
    wizard(),
    wraith(),
    xaan(),
)

// ---------------------------------------------------------------------------
// Boffin
// ---------------------------------------------------------------------------

/**
 * "The Demon (even if drunk or poisoned) has a not-in-play good character's
 * ability. You both know which."
 *
 * W7E closes this row's P0. `Identity.derivedGrants` now DERIVES
 * `FloatingGrant(holder = ALIVE_DEMON, worksWhileImpaired = true)` straight from
 * `decisions["boffin.grant"]` while a Boffin seat exists, so the Demon really
 * wakes at the granted character's own night position. Nothing is stored twice:
 * the setup decision is the whole record. This row wakes both seats and
 * pre-fills both cards.
 */
private fun boffin() = CharacterRule(
    id = "boffin",
    // Night 1 happens before anyone can be dead, and the grant is a setup fact:
    // the row exists while a Boffin seat exists.
    actsWhileDead = true,
    // The wiki runs the Boffin by laying a SECOND CHARACTER TOKEN next to the
    // Demon's. The grimoire has one token per seat, so WP6C gave the grant a
    // global reminder instead: it names a character that is not in play, which
    // is exactly what `remindersGlobal` is for. The granted character's id goes
    // in `PlacedReminder.characterId`; the label is the same either way.
    tokens = listOf(
        TokenRule(
            "boffin", "Demon Has This Ability", effect = null, until = Until.FOREVER,
        ),
    ),
    firstNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Wake the Boffin and the Demon. Show 'This character selected you' and the " +
            "Boffin token, then the not-in-play good character the Demon also has.",
        cards = { ctx ->
            val grant = grantOf(ctx.state)
            buildList {
                add(
                    CardOffer(
                        label = "SHOW: THIS CHARACTER SELECTED YOU",
                        card = ShowCardSpec.CharacterCard("THIS CHARACTER SELECTED YOU", "boffin"),
                        truthful = true,
                    ),
                )
                if (grant != null) {
                    add(
                        CardOffer(
                            label = "SHOW: THE DEMON HAS THIS ABILITY",
                            card = ShowCardSpec.CharacterCard("THE DEMON HAS THIS ABILITY", grant),
                            truthful = true,
                        ),
                    )
                }
            }
        },
        wakeCounts = WakeCount.ACT,
    ),
    // The Boffin dies: the Demon loses the granted ability from now on.
    onDeath = listOf(
        DeathTrigger(
            gate = { _, _, event, holder -> event.playerId == holder.id },
            produce = { state, _, _, holder ->
                val grant = grantOf(state)
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = BriefingSlot.DAWN,
                            kind = PromptKind.INFO,
                            sourceId = "boffin",
                            subjectPlayerId = holder.id,
                            title = "The Boffin died — the Demon loses the " +
                                "${grant ?: "granted"} ability from now on.",
                            detail = "Do not announce it. Remove the granted step from the sheet.",
                        ),
                    ),
                )
            },
        ),
    ),
)

private fun grantOf(state: GameState): String? =
    state.decisions[Decisions.BOFFIN_GRANT]?.takeIf { it.isNotBlank() }?.let(Character::normalizeId)

// ---------------------------------------------------------------------------
// Boomdandy
// ---------------------------------------------------------------------------

/**
 * "If you are executed, all but 3 players die. After a 10 to 1 countdown, the
 * player with the most players pointing at them, dies."
 *
 * The headline rule the card insists on: the explosion fires **whatever the
 * execution's outcome** — a Devil's-Advocate-protected Boomdandy still explodes.
 * WP3's built-in requires `outcome == DIED`, so this row wins outright for
 * `boomdandy` (lead D61).
 */
private fun boomdandy() = CharacterRule(
    id = "boomdandy",
    killCause = DeathCause.EVIL_ABILITY,
    // "Declare that the Boomdandy has exploded." The explosion runs across a
    // ring of kills, a 10-to-1 countdown and a finger vote, so the state has to
    // survive between storyteller taps; the official set had no label for it
    // (WP6C data change). It ends the game, so it never expires.
    tokens = listOf(
        TokenRule(
            "boomdandy", "Exploded", effect = null, until = Until.FOREVER,
            endsWithSource = false, grimoireCentre = true,
        ),
    ),
    day = DayRule(
        onExecution = { ctx ->
            val record = ctx.record
            if (record.playerId != ctx.holder.id ||
                record.outcome == ExecutionOutcome.NO_EXECUTION
            ) {
                emptyList()
            } else {
                val impaired = record.abilityImpairedAtExecution
                    ?: !Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id)
                val survived = record.outcome == ExecutionOutcome.SURVIVED
                listOf(
                    ExecutionConsequence(
                        sourceId = "boomdandy",
                        headline = "${ctx.holder.name} was the BOOMDANDY — declare that they " +
                            "have exploded. All but 3 players die.",
                        detail = buildString {
                            append("Kill down to 3 alive in seat order from the Boomdandy, ")
                            append("keeping the Demon alive; then count 10 to 1 and execute the ")
                            append("player the most fingers point at. A tie kills nobody. ")
                            append("Either way the day ends now.")
                            if (survived) {
                                append(
                                    " They were executed but did not die — the explosion " +
                                        "still happens.",
                                )
                            }
                            if (impaired) {
                                append(
                                    " The Boomdandy's ability is not working: the wiki does not " +
                                        "rule on this — your call.",
                                )
                            }
                        },
                        options = listOf(
                            TriggerOption(
                                id = "boomdandy-explode",
                                label = "They explode",
                                isDefault = !impaired,
                            ),
                            TriggerOption(
                                id = "boomdandy-no-explosion",
                                label = "No explosion",
                                isDefault = impaired,
                            ),
                        ),
                        impaired = impaired,
                    ),
                )
            }
        },
    ),
)

// ---------------------------------------------------------------------------
// Fearmonger
// ---------------------------------------------------------------------------

/**
 * "Each night, choose a player: if you nominate & execute them, their team
 * loses. All players know if you choose a new player."
 *
 * The day half (the nominator-specific warning and the win) is WP3's, and it is
 * already nominator-aware — this row owns the night only.
 */
private fun fearmonger(): CharacterRule {
    val rule = { first: Boolean ->
        NightRule(
            gate = aliveOr(
                "dead — the Fearmonger has no ability; leave the Fear token where it is",
            ),
            prompt = "The Fearmonger points at a player — alive or dead, good or evil, " +
                "themselves included. Move the Fear token there.",
            action = { ctx ->
                ChoosePlayers(
                    sourceId = "fearmonger",
                    prompt = "WHO DOES THE FEARMONGER THREATEN?",
                    min = 1,
                    max = 1,
                    constraints = listOf(
                        TargetConstraint.ANY_LIVING_STATE,
                        TargetConstraint.SELF_ALLOWED,
                    ),
                    sort = TargetSort.SEAT_ORDER,
                    allowNone = false,
                    perTarget = listOf(
                        NightEffect.PlaceToken(
                            sourceId = "fearmonger",
                            label = "Fear",
                            on = Ref.Target,
                            until = Until.FOREVER,
                        ),
                    ),
                    // "All players know if you choose a NEW player." The engine cannot
                    // make one effect conditional on the pick, so the line the
                    // storyteller reads carries the condition and last night's answer.
                    onResolve = listOf(
                        NightEffect.Announce(BriefingSlot.DAWN, announceLine(ctx, first)),
                    ),
                )
            },
        )
    }
    return CharacterRule(
        id = "fearmonger",
        firstNight = rule(true),
        otherNight = rule(false),
        tokens = listOf(TokenRule("fearmonger", "Fear", null, Until.FOREVER)),
    )
}

private fun announceLine(ctx: NightContext, firstNight: Boolean): String {
    val say = "'The Fearmonger has chosen a player.'"
    val previous = if (firstNight) {
        null
    } else {
        Memory.lastChoice(ctx.state, "fearmonger", ctx.holder?.id)
            ?.targetIds
            ?.firstOrNull()
            ?.let { ctx.state.player(it)?.name }
    }
    // Night 1 is always a new player, and so is the first night after a night the
    // step never ran — there is nothing to compare against.
    return previous?.let {
        "If the Fearmonger chose someone NEW tonight, announce $say Last night they chose $it " +
            "— the same player again means say nothing."
    } ?: "Announce $say"
}

// ---------------------------------------------------------------------------
// Goblin
// ---------------------------------------------------------------------------

/**
 * "If you publicly claim to be the Goblin when nominated & are executed that
 * day, your team wins."
 *
 * The Goblin never wakes. WP3 already offers the claim checkbox for every
 * nominee and resolves the win; the card asks for two warnings it does not
 * carry, so this row re-offers the identical CHOICE (so `applyTrigger`'s
 * `goblin` branch still fires) and adds them.
 */
private fun goblin() = CharacterRule(
    id = "goblin",
    tokens = listOf(TokenRule("goblin", "Claimed", null, Until.DUSK)),
    day = DayRule(
        onNomination = { ctx ->
            val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
            // One set of rows per nomination, not one per Goblin seat.
            val firstGoblinSeat =
                ctx.state.seats.firstOrNull { isCharacter(it, "goblin") }?.id == ctx.holder.id
            // An exile is not an execution, so no claim can win on one
            // (day-engine §D test 38, playtest C-2).
            if (nominee == null || nominee.characterId == null || nominee.isTraveller ||
                !firstGoblinSeat
            ) {
                emptyList()
            } else {
                buildList {
                    add(
                        NominationTrigger(
                            kind = TriggerKind.CHOICE,
                            sourceId = "goblin",
                            actorId = nominee.id,
                            headline = "Did ${nominee.name} claim to be the Goblin?",
                            detail = "A claim only counts when it is made out loud BEFORE the " +
                                "votes. Several players may claim on the same day.",
                            options = listOf(
                                TriggerOption(DayRules.OPTION_APPLY, "They claimed the Goblin"),
                                TriggerOption(DayRules.OPTION_SKIP, "No claim", isDefault = true),
                            ),
                        ),
                    )
                    if (nominee.id == ctx.holder.id && nominee.alive) {
                        val impaired = !Status.hasAbility(ctx.state, ctx.lookup, nominee.id)
                        add(
                            NominationTrigger(
                                kind = TriggerKind.WARN,
                                sourceId = "goblin",
                                actorId = nominee.id,
                                headline = "${nominee.name} IS the Goblin — if they claim now, " +
                                    "before the votes, and are executed today, EVIL WINS.",
                                detail = if (impaired) {
                                    "Their ability is not working: treat the claim as a caution, " +
                                        "not a win."
                                } else {
                                    ""
                                },
                                impaired = impaired,
                            ),
                        )
                    }
                    if (hasToken(ctx.state, ctx.lookup, nominee.id, "goblin", "Claimed")) {
                        add(
                            NominationTrigger(
                                kind = TriggerKind.WARN,
                                sourceId = "goblin",
                                actorId = nominee.id,
                                headline = "${nominee.name} already claimed the Goblin today — " +
                                    "executing them ends the game if they really are the Goblin.",
                            ),
                        )
                    }
                }
            }
        },
    ),
)

// ---------------------------------------------------------------------------
// Harpy
// ---------------------------------------------------------------------------

/**
 * "Each night, choose 2 players: tomorrow, the 1st player is mad that the 2nd is
 * evil, or one or both might die."
 *
 * The two tokens keep WP1's `Until.DUSK`, which in this engine means "tonight
 * and tomorrow day, gone at the next dusk" — exactly the wiki's *tomorrow*, and
 * exactly when the Harpy re-chooses. The digest's "still there on the next
 * night" reading is the OLD app's dawn sweep; it does not apply here.
 */
private fun harpy(): CharacterRule {
    val rule = NightRule(
        gate = aliveOr("dead — no Harpy ability; leave the Mad and 2nd tokens where they are"),
        prompt = "The Harpy points at two players, in order: the 1st goes mad, the 2nd is who " +
            "they must be mad about. Wake the 1st, show the Harpy token, then point at the 2nd.",
        action = { ctx ->
            ChoosePlayers(
                sourceId = "harpy",
                prompt = "1ST (GOES MAD), THEN 2ND (THE ACCUSED)",
                min = 2,
                max = 2,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.SEAT_ORDER,
                onResolve = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "harpy",
                        label = "Mad",
                        on = Ref.TargetN(0),
                        kind = EffectKind.MAD,
                        until = Until.DUSK,
                        // W7E: the Mad token points back at the ACCUSED, so the
                        // pair survives on the board and not only in the prompt
                        // text — which is what tomorrow's madness ruling reads.
                        linkedPlayerId = Ref.TargetN(1),
                    ),
                    NightEffect.PlaceToken(
                        sourceId = "harpy",
                        label = "2nd",
                        on = Ref.TargetN(1),
                        until = Until.DUSK,
                        linkedPlayerId = Ref.TargetN(0),
                    ),
                    // The madness binds TOMORROW, so the decision is a day obligation.
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.DAY_START,
                        kind = PromptKind.DECIDE,
                        sourceId = "harpy",
                        on = Ref.TargetN(0),
                        title = madnessTitle(ctx),
                    ),
                ),
            )
        },
        cards = { ctx ->
            listOf(
                CardOffer(
                    label = "SHOW: THIS CHARACTER SELECTED YOU",
                    card = ShowCardSpec.CharacterCard("THIS CHARACTER SELECTED YOU", "harpy"),
                    truthful = true,
                ),
            ).plus(lastPairCard(ctx))
        },
    )
    return CharacterRule(
        id = "harpy",
        killCause = DeathCause.EVIL_ABILITY,
        firstNight = rule,
        otherNight = rule,
        tokens = listOf(
            TokenRule("harpy", "Mad", EffectKind.MAD, Until.DUSK),
            TokenRule("harpy", "2nd", null, Until.DUSK),
        ),
        day = DayRule(
            // WP3's generic madness row says "mad that they are <character>", which is
            // the Cerenovus's shape. The Harpy's madness is about a SEAT.
            onNomination = { ctx ->
                val second = seatWithToken(ctx.state, ctx.lookup, "harpy", "2nd")
                val seats = listOfNotNull(
                    ctx.nominatorId?.let { ctx.state.player(it) },
                    ctx.nomineeId?.let { ctx.state.player(it) },
                ).distinctBy { it.id }
                seats.filter { hasToken(ctx.state, ctx.lookup, it.id, "harpy", "Mad") }
                    .map { mad ->
                        NominationTrigger(
                            kind = TriggerKind.WARN,
                            sourceId = "harpy",
                            actorId = mad.id,
                            targetId = second?.id,
                            headline = "${mad.name} is mad that " +
                                "${second?.name ?: "the Harpy's 2nd player"} is evil (Harpy) — " +
                                "check the claim before this goes further.",
                            detail = "If they are not acting mad, you may kill them, the 2nd " +
                                "player, both, or neither.",
                            impaired = !Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id),
                        )
                    }
            },
        ),
    )
}

private fun madnessTitle(ctx: NightContext): String =
    "Harpy madness tomorrow: the 1st player must act mad that the 2nd is evil, or you may kill " +
        "one, both, or neither." +
        if (ctx.holder != null && Status.isImpaired(ctx.state, ctx.lookup, ctx.holder.id)) {
            " The Harpy's ability is not working: the madness is not enforced and nobody may be " +
                "killed for breaking it."
        } else {
            ""
        }

/** "The Tips & Tricks recommend repeating the same pair" — show last night's. */
private fun lastPairCard(ctx: NightContext): List<CardOffer> {
    val previous = Memory.lastChoice(ctx.state, "harpy", ctx.holder?.id)?.targetIds.orEmpty()
    if (previous.size < 2) return emptyList()
    val names = previous.mapNotNull { ctx.state.player(it)?.name }
    if (names.size < 2) return emptyList()
    return listOf(
        CardOffer(
            label = "LAST NIGHT: ${names[0]} → ${names[1]}",
            card = ShowCardSpec.Message("LAST NIGHT", "${names[0]} was mad that ${names[1]} is evil"),
            truthful = true,
            editable = false,
        ),
    )
}

// ---------------------------------------------------------------------------
// Marionette
// ---------------------------------------------------------------------------

/**
 * "You think you are a good character, but you are not. The Demon knows who you
 * are. [You neighbor the Demon]"
 *
 * The Marionette never wakes for itself: `NightInfo` owns the `marionette`
 * night-order slot (it wakes the DEMON), `Identity.derivedGrants` REPLACEs the
 * seat's own role with the believed character's, and `Standing.emitSelf` gives
 * the seat NO_ABILITY that does NOT end with its source. Declaring `standing`
 * here would move that rule into `emitSelf` unchanged, so this row leaves it.
 * `firstNight`/`otherNight` are null on purpose: no Marionette-owned step exists.
 */
private fun marionette() = CharacterRule(
    id = "marionette",
    firstNight = null,
    otherNight = null,
    tokens = listOf(TokenRule("marionette", "Is The Marionette", null, Until.FOREVER)),
)

// ---------------------------------------------------------------------------
// Mezepheles
// ---------------------------------------------------------------------------

/**
 * "You start knowing a secret word. The 1st good player to say this word becomes
 * evil that night."
 *
 * What matters is whether the Mezepheles is sober **at night**, not when the word
 * was spoken — and the ability is spent either way.
 */
private fun mezepheles() = CharacterRule(
    id = "mezepheles",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the Mezepheles their secret word. Do not say it out loud.",
        cards = { ctx ->
            val word = ctx.state.decisions[Decisions.MEZEPHELES_WORD].orEmpty()
            if (word.isBlank()) {
                emptyList()
            } else {
                listOf(
                    CardOffer(
                        label = "SHOW: ${word.uppercase()}",
                        card = ShowCardSpec.Message("YOUR SECRET WORD", word),
                        truthful = true,
                        editable = false,
                    ),
                )
            }
        },
    ),
    otherNight = NightRule(
        // "Remove their night token from the night sheet" once it is spent, and
        // there is nothing to do until a good player is marked.
        gate = Gates.all(Gates.notSpent(), someoneSaidTheWord()),
        prompt = "The marked player turns evil. Wake them, show the 'You are' token and give a " +
            "thumbs-down. Show them NOTHING else — not who the other evil players are.",
        action = { ctx ->
            val victim = seatWithToken(ctx.state, ctx.lookup, "mezepheles", "Turns Evil")
            val impaired = ctx.holder != null &&
                !Status.hasAbility(ctx.state, ctx.lookup, ctx.holder.id)
            val name = victim?.name ?: "the marked player"
            YesNo(
                sourceId = "mezepheles",
                prompt = "DOES $name TURN EVIL?",
                yesLabel = if (impaired) "$name turns evil anyway" else "$name turns evil",
                noLabel = "The Mezepheles' ability is not working — they stay good",
                onYes = listOfNotNull(
                    // W7E: the flip is REAL now. `SetAlignment` writes the side
                    // and nothing else — the seat keeps its character and its
                    // ability, which is exactly what the card says and what a
                    // `BecomeCharacter` could never express.
                    victim?.let {
                        NightEffect.SetAlignment(
                            on = Ref.Seat(it.id),
                            evil = true,
                            note = "Mezepheles: ${it.name} said the secret word and now plays " +
                                "for evil. They keep their character and their ability.",
                        )
                    },
                    victim?.let {
                        NightEffect.ShowCardTo(Ref.Seat(it.id), "YOU ARE — evil (thumbs down)")
                    },
                    victim?.let { NightEffect.RemoveToken("mezepheles", "Turns Evil", Ref.Seat(it.id)) },
                    NightEffect.MarkSpent("mezepheles"),
                ),
                // The ability is spent whether or not anybody turned.
                onNo = listOfNotNull(
                    victim?.let { NightEffect.RemoveToken("mezepheles", "Turns Evil", Ref.Seat(it.id)) },
                    NightEffect.MarkSpent("mezepheles"),
                ),
            )
        },
    ),
    tokens = listOf(
        TokenRule("mezepheles", "Turns Evil", null, Until.FOREVER),
        TokenRule("mezepheles", "No Ability", EffectKind.SPENT, Until.FOREVER),
    ),
    day = DayRule(
        // The word may be said publicly or privately, on ANY day — which is why
        // this cannot live on the night step alone.
        ability = DayAbility(
            label = "Someone said the secret word",
            oncePerGame = true,
            recordsAs = "mezepheles",
            available = { state, lookup, holder ->
                !Memory.isSpent(state, "mezepheles", holder.id) &&
                    seatWithToken(state, lookup, "mezepheles", "Turns Evil") == null
            },
        ),
    ),
)

/** Fires only while some seat is marked `Turns Evil`. */
private fun someoneSaidTheWord(): WakePredicate = WakePredicate { ctx ->
    if (seatWithToken(ctx.state, ctx.lookup, "mezepheles", "Turns Evil") != null) {
        StepGate.Fire
    } else {
        StepGate.Skip("nobody has said the secret word — the Mezepheles does not wake")
    }
}

// ---------------------------------------------------------------------------
// Organ Grinder
// ---------------------------------------------------------------------------

/**
 * "All players keep their eyes closed when voting and the vote tally is secret.
 * Each night, choose if you are drunk until dusk."
 *
 * Note the inversion: choosing to be drunk switches the eyes-closed rule OFF, so
 * a nodded-yes Organ Grinder gives the town an ordinary public day.
 * `DayRules.secretVoting` already reads it.
 */
private fun organGrinder(): CharacterRule {
    val rule = NightRule(
        gate = aliveOr("dead — the Organ Grinder no longer chooses, and voting is public"),
        prompt = "The Organ Grinder nods to be drunk until dusk, or shakes their head to stay " +
            "sober. If anyone asks why the vote was secret, say only that an Organ Grinder is " +
            "in play.",
        action = {
            YesNo(
                sourceId = "organgrinder",
                prompt = "DRUNK TODAY? NOD YES / SHAKE NO",
                yesLabel = "Nods — drunk until dusk (voting is PUBLIC today)",
                noLabel = "Shakes — sober (EYES CLOSED voting today)",
                onYes = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "organgrinder",
                        label = "Drunk",
                        on = Ref.Source,
                        kind = EffectKind.DRUNK,
                        until = Until.DUSK,
                    ),
                    NightEffect.RecordChoice(),
                ),
                onNo = listOf(
                    NightEffect.RemoveToken("organgrinder", "Drunk", Ref.Source),
                    NightEffect.RecordChoice(),
                ),
            )
        },
    )
    return CharacterRule(
        id = "organgrinder",
        firstNight = rule,
        otherNight = rule,
        tokens = listOf(
            // `endsWithSource = false` is load-bearing, and this row exists for it.
            // The Organ Grinder drunkens THEMSELVES, so a source-ending effect is
            // circular: the drunkenness breaks the ability that sustains it, which
            // un-drunkens them, which re-sustains it. `Status.abilityWorks` resolves
            // that circle as "the ability works", so a nodded-yes Organ Grinder
            // would still have closed every eye. The drunkenness is the ability's
            // own product and outlives it, exactly like `sweetheart/Drunk`.
            TokenRule(
                "organgrinder", "Drunk", EffectKind.DRUNK, Until.DUSK,
                endsWithSource = false, impairs = true,
            ),
            TokenRule("organgrinder", "About To Die", null, Until.DAWN),
        ),
    )
}

// ---------------------------------------------------------------------------
// Psychopath
// ---------------------------------------------------------------------------

/**
 * "Each day, before nominations, you may publicly choose a player: they die. If
 * executed, you only die if you lose roshambo."
 *
 * The roshambo half is WP3's execution consequence and is left alone; this row
 * adds the day tool WP3 has no home for. Once per DAY, not per game, so no
 * `spentLabel` (lead D49): the `STATEMENT` ledger row stays the authority and
 * the `Used Today` token WP6C added to `characters.json` is the grimoire's copy
 * of it, closing the window from either side.
 */
private fun psychopath() = CharacterRule(
    id = "psychopath",
    killCause = DeathCause.DAY_ABILITY,
    tokens = listOf(
        TokenRule("psychopath", "Used Today", effect = null, until = Until.DAWN),
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Public kill",
            oncePerDay = true,
            recordsAs = "psychopath",
            available = { state, lookup, holder ->
                state.phase == Phase.DAY &&
                    holder.alive &&
                    Status.hasAbility(state, lookup, holder.id) &&
                    // "before nominations": the window closes with the first real one.
                    state.nominations.none { it.day == state.cycle && !it.isExile } &&
                    Memory.statementsOn(state, state.cycle, sourceId = "psychopath").isEmpty() &&
                    seatsHolding(state, "psychopath", "Used Today").none { it.id == holder.id }
            },
        ),
    ),
)

// ---------------------------------------------------------------------------
// Summoner
// ---------------------------------------------------------------------------

/**
 * "You get 3 bluffs. On the 3rd night, choose a player: they become an evil
 * Demon of your choice. [No Demon]"
 *
 * The bluffs are `Bluffs.requirements` (WP4) and land on this step through
 * `NightPlan.withBluffs`; the no-Demon bag is `Setup.bagShapeFor`; the
 * "no Demon and the Summoner is gone" win is `WinCheck`. This row owns the
 * counter and the summoning.
 */
private fun summoner() = CharacterRule(
    id = "summoner",
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "Show the 'These characters are not in play' token and 3 not-in-play good " +
            "characters. Mark the Summoner 'Night 1'. There is no Demon yet.",
        pending = { ctx ->
            val holder = ctx.holder
            if (holder == null || nightTokenOf(ctx.state, ctx.lookup, "summoner") != null) {
                emptyList()
            } else {
                listOf(
                    NightEffect.PlaceToken(
                        sourceId = "summoner",
                        label = "Night 1",
                        on = Ref.Seat(holder.id),
                        until = Until.DUSK,
                    ),
                )
            }
        },
    ),
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.nightIs(3), Gates.notSpent()),
        prompt = "The Summoner points at a player and at a Demon on the script. That player " +
            "becomes that evil Demon. Do NOT show them who the Minions are, and do NOT give " +
            "them bluffs. They act tonight, at their own place in the order.",
        action = {
            ChoosePlayerAndCharacter(
                sourceId = "summoner",
                prompt = "WHO BECOMES WHAT?",
                playerConstraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                pool = CharacterPool.DEMON,
                onResolve = listOf(
                    NightEffect.BecomeCharacter(
                        on = Ref.Target,
                        characterId = "",
                        evil = true,
                        reason = ChangeReason.SUMMONER,
                    ),
                    NightEffect.ShowCardTo(Ref.Target, "YOU ARE — the Demon (thumbs down)"),
                    NightEffect.MarkSpent("summoner"),
                    NightEffect.RemoveToken("summoner", "Night 3", Ref.Source),
                ),
            )
        },
    ),
    tokens = listOf(
        TokenRule(
            "summoner", "Night 1", null, Until.DUSK,
            countdownNext = "Night 2", exclusiveGroup = "summoner.night",
        ),
        TokenRule(
            "summoner", "Night 2", null, Until.DUSK,
            countdownNext = "Night 3", exclusiveGroup = "summoner.night",
        ),
        TokenRule("summoner", "Night 3", null, Until.DUSK, exclusiveGroup = "summoner.night"),
        // `MarkSpent` removes the Night 3 token, so without this the grimoire
        // had nothing left saying the Summoner is finished. `spentLabel` in
        // `characters.json` names it, so `Gates.notSpent` reads it too.
        TokenRule("summoner", "No Ability", EffectKind.SPENT, Until.FOREVER),
    ),
)

// ---------------------------------------------------------------------------
// Vizier
// ---------------------------------------------------------------------------

/**
 * "All players know you are the Vizier. You cannot die during the day. If good
 * voted, you may choose to execute immediately."
 *
 * The one thing this row exists for: the Courtier/Preacher jinx says *"If the
 * Vizier loses their ability, they learn this, and cannot die during the day"* —
 * so DAY_IMMUNE must NOT end with the Vizier's own ability. WP1's built-in
 * `Standing.emitSelf` row has `endsWithSource = true`; this one wins over it.
 */
private fun vizier() = CharacterRule(
    id = "vizier",
    actsWhileDead = true,
    standing = StandingRule("vizier") { state, holder, _ ->
        listOf(
            Effect(
                id = holder.standingSince,
                kind = EffectKind.DAY_IMMUNE,
                targetId = holder.id,
                sourceCharacterId = "vizier",
                sourcePlayerId = holder.id,
                until = Until.FOREVER,
                // The jinx: losing the ability does not restore day mortality.
                endsWithSource = false,
                label = "",
                note = "The Vizier cannot die during the day — even drunk, poisoned or " +
                    "stripped of their ability.",
                createdCycle = state.cycle,
                createdAtNight = state.phase != Phase.DAY,
                derived = true,
            ),
        )
    },
    // "When the first night has ended" — the Vizier sits after DAWN in the order.
    firstNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Announce to everybody, good and evil, which player is the Vizier.",
        cards = { ctx ->
            val holder = ctx.holder ?: ctx.state.seats.firstOrNull { isCharacter(it, "vizier") }
            if (holder == null) {
                emptyList()
            } else {
                listOf(
                    CardOffer(
                        label = "SHOW: THE VIZIER IS ${holder.name.uppercase()}",
                        card = ShowCardSpec.Message("THE VIZIER IS", holder.name),
                        truthful = true,
                        editable = false,
                    ),
                )
            }
        },
        wakeCounts = WakeCount.NONE,
    ),
    // The Courtier/Preacher jinx: "if the Vizier loses their ability, they LEARN
    // this, and cannot die during the day". The lost ability is a state the
    // table can see, so it needs a token; the official set is empty (WP6C data
    // change). It does not carry NO_ABILITY — whatever stripped the Vizier owns
    // that effect, and the standing DAY_IMMUNE row above deliberately survives.
    tokens = listOf(
        TokenRule("vizier", "No Ability", effect = null, until = Until.FOREVER),
    ),
)

// ---------------------------------------------------------------------------
// Widow
// ---------------------------------------------------------------------------

/**
 * "On your 1st night, look at the Grimoire & choose a player: they are poisoned.
 * 1 good player knows a Widow is in play."
 *
 * *Your* 1st night, not night 1: the gate is "has never chosen", so a mid-game
 * Widow (Pit-Hag, Alchemist, Summoner) gets the step whenever they arrive. The
 * poison itself is WP1's `widow/Poisoned` token rule — FOREVER, `endsWithSource`,
 * so it ends when the Widow dies and pauses while the Widow is impaired.
 */
private fun widow(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, hasNotActed("widow")),
        prompt = "Show the Widow the Grimoire for as long as they need — redact nothing they " +
            "are entitled to see, and never their own or the 'Know' token. They point at a " +
            "character token: that player is poisoned. Then wake the player marked 'Know'.",
        action = {
            ChoosePlayers(
                sourceId = "widow",
                prompt = "WHO DO THEY POINT AT?",
                min = 1,
                max = 1,
                constraints = listOf(
                    TargetConstraint.ANY_LIVING_STATE,
                    TargetConstraint.SELF_ALLOWED,
                ),
                sort = TargetSort.TOWNSFOLK_FIRST,
                allowNone = false,
                perTarget = listOf(
                    NightEffect.PlaceToken(
                        sourceId = "widow",
                        label = "Poisoned",
                        on = Ref.Target,
                        kind = EffectKind.POISONED,
                        until = Until.FOREVER,
                    ),
                ),
            )
        },
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW THE 'KNOW' PLAYER: A WIDOW IS IN PLAY",
                    card = ShowCardSpec.CharacterCard("THIS CHARACTER IS IN PLAY", "widow"),
                    truthful = true,
                ),
            )
        },
    )
    return CharacterRule(
        id = "widow",
        firstNight = rule,
        // A mid-game Widow needs the same step on a later night: the wiki's own
        // example is a Pit-Hag who becomes the Widow on night 3 and runs the
        // whole step then. WP6C put `widow` into the otherNight order (straight
        // after the Poisoner, as on the first night) so this row renders.
        otherNight = rule,
        tokens = listOf(
            // Two copies: the poison lasts until the Widow dies, so a second
            // Widow's victim must not un-poison the first one.
            TokenRule(
                "widow", "Poisoned", EffectKind.POISONED, Until.FOREVER,
                copies = 2, impairs = true,
            ),
            TokenRule("widow", "Know", null, Until.FOREVER),
        ),
        onDeath = listOf(
            DeathTrigger(
                gate = { state, _, event, holder ->
                    event.playerId == holder.id &&
                        state.players.any { seat ->
                            seat.id != holder.id &&
                                hasTokenRaw(state, seat.id, "widow", "Poisoned")
                        }
                },
                produce = { state, _, _, holder ->
                    val victim = state.players.firstOrNull {
                        it.id != holder.id && hasTokenRaw(state, it.id, "widow", "Poisoned")
                    }
                    TriggerResult(
                        prompts = listOf(
                            Prompt(
                                id = 0,
                                at = BriefingSlot.DAWN,
                                kind = PromptKind.INFO,
                                sourceId = "widow",
                                subjectPlayerId = victim?.id ?: holder.id,
                                title = "${victim?.name ?: "The Widow's victim"} is no longer " +
                                    "poisoned — the Widow died.",
                                detail = "Do not announce it. Their information is true again " +
                                    "from now on.",
                            ),
                        ),
                    )
                },
            ),
        ),
    )
}

// ---------------------------------------------------------------------------
// Wizard
// ---------------------------------------------------------------------------

/**
 * "Once per game, choose to make a wish. If granted, it might have a price &
 * leave a clue as to its nature."
 *
 * A DECLINED wish is not a spent one — the Wizard may wish again on a later
 * night. Only a GRANTED wish marks the ability spent, and the effects of a
 * granted wish survive the Wizard's death.
 */
private fun wizard(): CharacterRule {
    val rule = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notSpent()),
        prompt = "Ask the Wizard whether they wish tonight. A declined wish costs nothing — " +
            "prompt them to wish again another night. A granted wish is the whole ability: " +
            "record what they asked for, its price and the public clue, and mark any ongoing " +
            "effect with a '?' token carrying your own note.",
        // W7E: three genuinely different outcomes, so a three-way [Options]
        // rather than a yes/no that had to fold "they did not wish" and "you
        // declined the wish" into one button. Only a GRANTED wish spends.
        action = {
            Options(
                sourceId = "wizard",
                prompt = "WHAT HAPPENED TO THE WISH?",
                options = listOf(
                    ActionOption(
                        id = "none",
                        label = "They did not wish tonight",
                        detail = "Nothing is spent. Do not show a card.",
                    ),
                    ActionOption(
                        id = "declined",
                        label = "You DECLINED the wish — they may wish again",
                        detail = "Nothing is spent.",
                        effects = listOf(
                            NightEffect.ShowCardTo(Ref.Source, "YOUR WISH IS DECLINED. Wish again?"),
                        ),
                    ),
                    ActionOption(
                        id = "granted",
                        label = "Wish GRANTED — record it",
                        detail = "The whole ability is spent.",
                        effects = listOf(
                            NightEffect.MarkSpent("wizard"),
                            NightEffect.QueuePrompt(
                                at = BriefingSlot.NOW,
                                kind = PromptKind.DECIDE,
                                sourceId = "wizard",
                                title = "Record the Wizard's wish: what they asked for, its " +
                                    "price, and the clue for the good team. Place a '?' token " +
                                    "on anything it changes and write the effect on the token.",
                            ),
                            NightEffect.ShowCardTo(Ref.Source, "YOUR WISH IS GRANTED."),
                        ),
                    ),
                ),
            )
        },
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW: YOUR WISH IS GRANTED",
                    card = ShowCardSpec.Message("YOUR WISH IS GRANTED."),
                    truthful = true,
                ),
                CardOffer(
                    label = "SHOW: YOUR WISH IS DECLINED — WISH AGAIN?",
                    card = ShowCardSpec.Message("YOUR WISH IS DECLINED.", "Wish again?"),
                    truthful = true,
                ),
            )
        },
    )
    return CharacterRule(
        id = "wizard",
        firstNight = rule,
        otherNight = rule,
        // Two physical "?" tokens, both free text: the Wizard is the driving case
        // for `PlacedReminder.note`, and both may sit on different seats at once.
        // They track the wish's ongoing effects, never the spend — hence the
        // separate `Wish Granted` mark WP6C added and `characters.json` names as
        // `spentLabel`, so a DECLINED wish leaves the Wizard free to wish again.
        tokens = listOf(
            TokenRule("wizard", "?", null, Until.FOREVER, copies = 2),
            TokenRule("wizard", "Wish Granted", EffectKind.SPENT, Until.FOREVER),
        ),
    )
}

// ---------------------------------------------------------------------------
// Wraith
// ---------------------------------------------------------------------------

/**
 * "You may choose to open your eyes at night. You wake when other evil players
 * do."
 *
 * No digest card exists for the Wraith (WP5 added it with the ten missing
 * characters), so this row does the one thing the official night reminder asks:
 * a marker at the top of the order telling the storyteller the Wraith may watch
 * every later evil wake. Whether that counts for the Chambermaid is genuinely
 * unruled — `INFORMED` is the conservative reading (they are awake for OTHER
 * players' abilities, not for a wake of their own). Flagged to the lead.
 */
private fun wraith(): CharacterRule {
    val rule = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Wraith may open their eyes whenever another evil player wakes tonight. " +
            "They see whatever is in front of them; they learn nothing else and choose nothing.",
        wakeCounts = WakeCount.INFORMED,
    )
    return CharacterRule(
        id = "wraith",
        firstNight = rule,
        otherNight = rule,
    )
}

// ---------------------------------------------------------------------------
// Xaan
// ---------------------------------------------------------------------------

/**
 * "On night X, all Townsfolk are poisoned until dusk. [X Outsiders]"
 *
 * The poison is WP1's positional `Standing.xaan`, which reads
 * `decisions["xaan.X"]` and the live grimoire — it must stay there, because a
 * `CharacterRule.standing` is emitted from `Standing.emitSelf`, which cannot ask
 * `abilityWorksBase` and so could not honour a poisoned Xaan. This row owns the
 * wake gate, the `X` record token and the two briefings.
 */
private fun xaan(): CharacterRule {
    val rule = NightRule(
        gate = onNightX(),
        prompt = "The Xaan does not wake and nothing is shown to anybody. Every Townsfolk is " +
            "poisoned until dusk: all their information tonight is FALSE, and their day " +
            "abilities malfunction tomorrow too.",
        pending = { ctx ->
            val holder = ctx.holder
            val x = Decisions.int(ctx.state, Decisions.XAAN_X)
            if (holder == null || x == null || ctx.night != x) {
                emptyList()
            } else {
                listOf(
                    NightEffect.PlaceToken(
                        sourceId = "xaan",
                        label = "X",
                        on = Ref.Seat(holder.id),
                        until = Until.FOREVER,
                    ),
                    NightEffect.QueuePrompt(
                        at = BriefingSlot.DAWN,
                        kind = PromptKind.INFO,
                        sourceId = "xaan",
                        on = Ref.Seat(holder.id),
                        title = "Night $x — every Townsfolk was poisoned by the Xaan. Everything " +
                            "they were told tonight was false, and the poison runs until DUSK.",
                    ),
                )
            }
        },
        wakeCounts = WakeCount.NONE,
    )
    return CharacterRule(
        id = "xaan",
        firstNight = rule,
        otherNight = rule,
        tokens = listOf(
            TokenRule(
                "xaan", "Night 1", null, Until.DUSK,
                countdownNext = "Night 2", exclusiveGroup = "xaan.night",
            ),
            TokenRule(
                "xaan", "Night 2", null, Until.DUSK,
                countdownNext = "Night 3", exclusiveGroup = "xaan.night",
            ),
            TokenRule("xaan", "Night 3", null, Until.DUSK, exclusiveGroup = "xaan.night"),
            // Placed on night X and read on later ones — never a dawn sweep (lead D56).
            TokenRule("xaan", "X", null, Until.FOREVER),
        ),
    )
}

/**
 * X is frozen at setup and never recomputed from the live Outsider count, so
 * this reads `decisions`, not the bag. The Xaan must be ALIVE to poison; an
 * impaired Xaan is a storyteller call, not a silent no-op.
 */
private fun onNightX(): WakePredicate = WakePredicate { ctx ->
    val x = Decisions.int(ctx.state, Decisions.XAAN_X)
        ?: return@WakePredicate StepGate.Skip("Xaan: X has not been chosen yet")
    val holder = ctx.holder
    when {
        x == 0 -> StepGate.Skip("X = 0 — the Xaan never poisons in this game")
        ctx.night < x -> StepGate.Skip("night ${ctx.night} of X = $x — the Xaan does nothing tonight")
        ctx.night > x -> StepGate.Skip("night X has passed — the Xaan has no further effect")
        holder == null -> StepGate.Fire
        !holder.alive -> StepGate.Skip("the Xaan is dead — no poison tonight")
        Status.isImpaired(ctx.state, ctx.lookup, holder.id) -> StepGate.Conditional(
            question = "The Xaan is drunk or poisoned. By the usual rules their ability does " +
                "not work tonight — but the wiki mentions only 'alive'. Your call.",
            yesLabel = "Poison every Townsfolk anyway",
            noLabel = "No poison tonight",
        )
        else -> StepGate.Fire
    }
}

// ---------------------------------------------------------------------------
// shared helpers
// ---------------------------------------------------------------------------

/** `Gates.aliveHolder` with a card-specific reason on the skip. */
private fun aliveOr(reason: String): WakePredicate = WakePredicate { ctx ->
    when (val gate = Gates.aliveHolder.gate(ctx)) {
        is StepGate.Skip -> StepGate.Skip(reason)
        else -> gate
    }
}

/** "On YOUR 1st night": fires until this holder has recorded a choice. */
private fun hasNotActed(sourceId: String): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    if (Memory.by(ctx.state, LedgerKind.CHOICE, sourceId, holder.id).isEmpty()) {
        StepGate.Fire
    } else {
        StepGate.Skip("they have already had their 1st night")
    }
}

private fun isCharacter(seat: Player, id: String): Boolean =
    Character.normalizeId(seat.characterId.orEmpty()) == Character.normalizeId(id)

/** The seat carrying `(sourceId, label)`, as an effect-backed OR a hand-placed token. */
private fun seatWithToken(
    state: GameState,
    lookup: (String) -> Character?,
    sourceId: String,
    label: String,
): Player? {
    val key = Tokens.key(sourceId, label)
    return state.players.firstOrNull { seat ->
        seat.reminders.any { Tokens.key(it) == key } ||
            Status.effectsOn(state, lookup, seat.id)
                .any { Tokens.key(it.sourceCharacterId, it.label) == key }
    }
}

private fun hasToken(
    state: GameState,
    lookup: (String) -> Character?,
    playerId: Long,
    sourceId: String,
    label: String,
): Boolean {
    val key = Tokens.key(sourceId, label)
    val seat = state.player(playerId) ?: return false
    return seat.reminders.any { Tokens.key(it) == key } ||
        Status.effectsOn(state, lookup, playerId)
            .any { Tokens.key(it.sourceCharacterId, it.label) == key }
}

/** Token test that never builds a `StatusQuery` — safe inside a death trigger. */
private fun hasTokenRaw(
    state: GameState,
    playerId: Long,
    sourceId: String,
    label: String,
): Boolean {
    val key = Tokens.key(sourceId, label)
    val seat = state.player(playerId) ?: return false
    return seat.reminders.any { Tokens.key(it) == key } ||
        state.effects.any {
            it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
        }
}

/** The countdown token this character's holder currently carries, if any. */
private fun nightTokenOf(
    state: GameState,
    lookup: (String) -> Character?,
    sourceId: String,
): String? = listOf("Night 1", "Night 2", "Night 3").firstOrNull { label ->
    state.seats.any { hasToken(state, lookup, it.id, sourceId, label) }
}
