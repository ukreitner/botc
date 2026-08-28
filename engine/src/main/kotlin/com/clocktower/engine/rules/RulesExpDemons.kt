package com.clocktower.engine.rules

import com.clocktower.engine.ActionOption
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.CardOffer
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterPool
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.ChoosePlayerAndCharacter
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.Counters
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathEvent
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.Effect
import com.clocktower.engine.EffectKind
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.ExecutionRecord
import com.clocktower.engine.GameState
import com.clocktower.engine.Gates
import com.clocktower.engine.JinxRule
import com.clocktower.engine.NightAction
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
import com.clocktower.engine.Registration
import com.clocktower.engine.SeatPredicate
import com.clocktower.engine.Sequence
import com.clocktower.engine.Setup
import com.clocktower.engine.ShowCardSpec
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
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate
import com.clocktower.engine.YesNo

/**
 * Experimental Demon behaviour (WP7-EXP-D).
 *
 * The ten `edition = "exp"`, `team = "demon"` characters of `characters.json`:
 * alhadikhia, kazali, legion, leviathan, lilmonsta, lleech, lordoftyphon, ojo,
 * riot, yaggababble.
 *
 * The dominant defect this file exists to fix is the group's shared one: nine of
 * the ten were being handed a generic one-target Demon kill on a night they do
 * not have one (Al-Hadikhia's ritual, Kazali's Minion creation, Lleech's host,
 * Lord of Typhon's neighbours, Ojo's *character* pick, Riot and Leviathan's "no
 * night kill at all", Yaggababble's zero charges on night 1). Each row below
 * declares the action it really has, so no panel has to guess.
 */
internal val EXP_DEMON_RULES: List<CharacterRule> = listOf(
    alHadikhia(),
    kazali(),
    legion(),
    leviathan(),
    lilMonsta(),
    lleech(),
    lordOfTyphon(),
    ojo(),
    riot(),
    yaggababble(),
)

// ---------------------------------------------------------------------------
// Al-Hadikhia
// ---------------------------------------------------------------------------

/**
 * "Each night*, you may choose 3 players (all players learn who): each silently
 * chooses to live or die, but if all live, all die."
 *
 * The dilemma IS the sequencing, so the row is a [Sequence]: pick the three in
 * order (they take the 1 / 2 / 3 tokens), then answer live-or-die for each in
 * turn. W7b made the three answers INDEPENDENT — `NightInput.optionIds` carries
 * one per [Options] stage — so the row no longer has to collapse them into a
 * single "did all three choose to live?" yes/no and hand the rest to a prompt.
 *
 * Each answer resolves as it is given, in order, which is how the table plays
 * it: a first victim's death is on the board before the second is asked. Every
 * death goes through `Deaths.attempt`, so each Monk / Soldier / Innkeeper /
 * Lleech outcome is surfaced per victim rather than auto-cancelled.
 *
 * Dead players are legal picks and "live" REVIVES one — guarded by
 * `When(IS_DEAD)` so choosing to live is a no-op for someone already alive.
 * "If all live, all die" is the third stage's own third answer: by then the
 * storyteller knows the other two, and it kills all three at once.
 */
private fun alHadikhia() = CharacterRule(
    id = "alhadikhia",
    killCause = DeathCause.DEMON_KILL,
    // The wiki does not rule whether Sage / Grandmother / Choirboy fire on these.
    demonKillUncertain = true,
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "Say SILENCE BEGINS. The Al-Hadikhia may choose 3 players. Wake each in " +
            "order, ask 'do you choose to live?', announce the answer, then wake the next. " +
            "Say THE SILENCE HAS ENDED. A dead player who chooses to live comes back. If the " +
            "third also chose to live and so did the other two, tap ALL THREE DIE.",
        action = { alHadikhiaRitual() },
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW: SILENCE BEGINS",
                    card = ShowCardSpec.Message("SILENCE", "NOBODY MAY SPEAK"),
                    truthful = true,
                ),
                CardOffer(
                    label = "SHOW: DO YOU CHOOSE TO LIVE?",
                    card = ShowCardSpec.Message(
                        "THE AL-HADIKHIA HAS CHOSEN YOU",
                        "DO YOU CHOOSE TO LIVE?",
                    ),
                    truthful = true,
                ),
                CardOffer(
                    label = "SHOW: SILENCE ENDS",
                    card = ShowCardSpec.Message("THE SILENCE HAS ENDED"),
                    truthful = true,
                ),
            )
        },
    ),
    // The 1|2|3 tokens must SURVIVE dawn so the status report can read them, and
    // be gone before the next selection — dusk, not dawn (digest, expiry).
    tokens = listOf(
        TokenRule("alhadikhia", "1", until = Until.DUSK),
        TokenRule("alhadikhia", "2", until = Until.DUSK),
        TokenRule("alhadikhia", "3", until = Until.DUSK),
    ),
)

/** Option ids of one Al-Hadikhia live/die answer. */
private const val AH_LIVE = "live"
private const val AH_DIE = "die"
private const val AH_ALL_LIVE = "alllive"

/**
 * One chosen player's answer. [index] is their place in the pick order, so the
 * effects address `Ref.TargetN(index)` and nothing depends on `scope.current`.
 *
 * The LAST stage carries the extra "…and so did the other two" branch: that is
 * the first moment the storyteller knows the whole answer.
 */
private fun alHadikhiaAnswer(index: Int, ordinal: String, last: Boolean = false): NightAction =
    Options(
        sourceId = "alhadikhia",
        prompt = "DID THE $ordinal CHOSEN PLAYER CHOOSE TO LIVE?",
        options = buildList {
            add(
                ActionOption(
                    id = AH_DIE,
                    label = "They chose to DIE",
                    detail = "One kill attempt: protections and the Innkeeper still apply.",
                    effects = listOf(
                        NightEffect.Attack(Ref.TargetN(index), DeathCause.DEMON_KILL),
                    ),
                ),
            )
            add(
                ActionOption(
                    id = AH_LIVE,
                    label = "They chose to LIVE",
                    detail = "A DEAD player who chooses to live comes back.",
                    effects = listOf(
                        NightEffect.When(
                            predicate = SeatPredicate.IS_DEAD,
                            on = Ref.TargetN(index),
                            then = listOf(NightEffect.Resurrect(Ref.TargetN(index))),
                        ),
                    ),
                ),
            )
            if (last) {
                add(
                    ActionOption(
                        id = AH_ALL_LIVE,
                        label = "They chose to LIVE — and so did the other two: ALL THREE DIE",
                        detail = "\"…but if all live, all die.\" One attempt each.",
                        effects = listOf(
                            NightEffect.Attack(Ref.AllTargets, DeathCause.DEMON_KILL),
                        ),
                    ),
                )
            }
        },
    )

private fun alHadikhiaRitual(): NightAction = Sequence(
    sourceId = "alhadikhia",
    prompt = "THREE PLAYERS IN ORDER — THEN ONE LIVE/DIE ANSWER EACH",
    stages = listOf(
        ChoosePlayers(
            sourceId = "alhadikhia",
            prompt = "WHICH 3 PLAYERS DID THEY CHOOSE? (in order)",
            min = 0,
            max = 3,
            // Dead players are legal, powerful picks and must not be demoted.
            constraints = listOf(
                TargetConstraint.ANY_LIVING_STATE,
                TargetConstraint.SELF_ALLOWED,
            ),
            sort = TargetSort.SEAT_ORDER,
            allowNone = true,
            noneLabel = "Chooses no one tonight",
            onResolve = listOf(
                NightEffect.PlaceToken("alhadikhia", "1", Ref.TargetN(0), until = Until.DUSK),
                NightEffect.PlaceToken("alhadikhia", "2", Ref.TargetN(1), until = Until.DUSK),
                NightEffect.PlaceToken("alhadikhia", "3", Ref.TargetN(2), until = Until.DUSK),
                // Announced even when nothing changed — and, because `onNone` is
                // empty, never announced when nobody was chosen.
                NightEffect.Announce(
                    at = BriefingSlot.DAWN,
                    text = "Al-Hadikhia: say whether each of the three chosen players is " +
                        "alive or dead — even if nothing changed.",
                ),
            ),
        ),
        alHadikhiaAnswer(0, "1ST"),
        alHadikhiaAnswer(1, "2ND"),
        alHadikhiaAnswer(2, "3RD", last = true),
    ),
)

// ---------------------------------------------------------------------------
// Kazali
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a player: they die. [You choose which players are which
 * Minions. -? to +? Outsiders]"
 *
 * Night 1 is the Minion creation and NOTHING else — no kill panel. A Kazali
 * created mid-game (Pit-Hag) skips the creation and keeps only the kill.
 * The bag itself is WP4's `Setup.bagShapeFor("kazali")`.
 */
private fun kazali() = CharacterRule(
    id = "kazali",
    killCause = DeathCause.DEMON_KILL,
    firstNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, enteredAtSetup()),
        prompt = "The Kazali chooses which players are which Minions. Wake each target, show " +
            "the 'You are' and Minion tokens and give a thumbs-down. Repeat until the normal " +
            "number of Minions exist. Do NOT offer a kill tonight.",
        action = { ctx -> kazaliConversion(ctx) },
    ),
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "The Kazali chooses a player: they die.",
        action = { demonAttack("kazali") },
    ),
)

private fun kazaliConversion(ctx: NightContext): NightAction {
    val needed = minionsWanted(ctx.state)
    val made = ctx.state.seats.count { it.characterId?.let(ctx.lookup)?.team == Team.MINION }
    val left = (needed - made).coerceAtLeast(0)
    return ChoosePlayerAndCharacter(
        sourceId = "kazali",
        prompt = "MINIONS STILL TO CREATE: $left OF $needed — POINT AT A PLAYER, THEN A MINION",
        playerConstraints = listOf(
            TargetConstraint.ALIVE,
            TargetConstraint.NOT_SELF,
            TargetConstraint.NOT_TRAVELLER,
        ),
        pool = CharacterPool.MINION,
        // "a unique Minion token" — one each.
        requireNotInPlay = true,
        onResolve = listOf(
            // Alignment becomes evil; `changeCharacter` keeps the original
            // character on the IdentityRecord, which is the wiki's own bluff advice.
            NightEffect.BecomeCharacter(
                on = Ref.Target,
                characterId = "",
                evil = true,
                reason = ChangeReason.KAZALI,
            ),
            NightEffect.ShowCardTo(Ref.Target, "YOU ARE — then a thumbs-down: YOU ARE EVIL"),
        ),
    )
}

// ---------------------------------------------------------------------------
// Legion
// ---------------------------------------------------------------------------

/**
 * "Each night*, a player might die. Executions fail if only evil voted. You
 * register as a Minion too. [Most players are Legion]"
 *
 * One row for ALL the Legion ([CharacterRule.groupStep]): the kill tool must not
 * vanish when the lowest-seated Legion dies, so the gate asks "is ANY Legion
 * alive", not "is this holder alive". Registration as {DEMON, MINION} is WP1's
 * `Registration.registersAs`; the only-evil-voted execution is WP3's
 * `DayRules.executionFailsOnlyEvilVoted`; the bag is WP4's `bagShapeFor`.
 */
private fun legion() = CharacterRule(
    id = "legion",
    groupStep = true,
    killCause = DeathCause.DEMON_KILL,
    demonKillUncertain = true,
    // No first-night row at all: Legion lives at DEMON_INFO on night 1.
    otherNight = NightRule(
        gate = anyLivingHolder("legion", "every Legion is dead"),
        prompt = "YOU decide whether anyone dies tonight — no Legion is woken. Kill a Legion " +
            "most nights; aim for 2 good and 1 Legion on the final day.",
        action = { ctx -> legionKill(ctx) },
    ),
)

private fun legionKill(ctx: NightContext): NightAction {
    val good = ctx.state.alivePlayers.count { !Registration.registersEvil(ctx.state, ctx.lookup, it) }
    val tail = if (good <= 2) " ONE MORE GOOD DEATH AND EVIL WINS." else ""
    return ChoosePlayers(
        sourceId = "legion",
        prompt = "DOES ANYONE DIE TONIGHT?$tail",
        min = 0,
        max = 1,
        constraints = listOf(TargetConstraint.ALIVE),
        sort = TargetSort.DEMON_FIRST,
        // "Nobody dies" is a first-class, recorded answer, not a bare text button.
        allowNone = true,
        noneLabel = "Nobody dies tonight",
        perTarget = listOf(NightEffect.Attack(Ref.Target, DeathCause.DEMON_KILL)),
    )
}

// ---------------------------------------------------------------------------
// Leviathan
// ---------------------------------------------------------------------------

/**
 * "If more than 1 good player is executed, evil wins. All players know you are
 * in play. After day 5, evil wins."
 *
 * `demonKills("leviathan") == false`: there is no kill control on either night,
 * ever. The win rules do not require the Leviathan to be alive, so the ability
 * survives death. The endings live in WinCheck (WP3); this row supplies the
 * announcement, the per-execution counter line and the four jinxed night
 * actions.
 */
private fun leviathan() = CharacterRule(
    id = "leviathan",
    keepsAbilityWhenDead = true,
    firstNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Announce publicly: THE LEVIATHAN IS IN PLAY. It is day 1. Nobody is woken " +
            "and nobody dies.",
        wakeCounts = WakeCount.NONE,
        cards = {
            listOf(
                CardOffer(
                    label = "SHOW: LEVIATHAN IN PLAY",
                    card = ShowCardSpec.Message("THE LEVIATHAN", "IS IN PLAY"),
                    truthful = true,
                ),
            )
        },
    ),
    otherNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Nobody dies tonight — the Leviathan does not kill. Move the day reminder on.",
        wakeCounts = WakeCount.NONE,
    ),
    day = DayRule(
        onNomination = { ctx ->
            leviathanNomination(ctx.state, ctx.lookup, ctx.nominatorId, ctx.nomineeId, ctx.holder)
        },
        onExecution = { ctx -> leviathanExecution(ctx.state, ctx.lookup, ctx.record) },
    ),
    // Lead D19: only active when the jinxed character is on the script. Each
    // gives the Leviathan a nightly choice its own ability text never mentions.
    jinxRules = mapOf(
        "banshee" to JinxRule(leviathanJinxChoice("The Banshee dies AND gains their ability.")),
        "farmer" to JinxRule(
            leviathanJinxChoice("The Farmer uses their ability and does NOT die."),
        ),
        "ravenkeeper" to JinxRule(
            leviathanJinxChoice("The Ravenkeeper uses their ability and does NOT die."),
        ),
        "sage" to JinxRule(leviathanJinxChoice("The Sage uses their ability and does NOT die.")),
    ),
    // "If MORE THAN 1 good player is executed, evil wins" — the count has to be
    // able to reach two. The official data lists one copy, so the second mark
    // displaced the first and `goodExecutedMarks` could never return 2 (WP6C
    // data change).
    tokens = listOf(
        TokenRule(
            "leviathan", "Good Player Executed", effect = null, until = Until.FOREVER,
            copies = 2, grimoireCentre = true,
        ),
    ),
)

private fun leviathanJinxChoice(effect: String) = NightRule(
    gate = Gates.actsWhileDead,
    prompt = "Jinx: choose an alive good player, different to every previously chosen " +
        "player. $effect",
    action = {
        ChoosePlayers(
            sourceId = "leviathan",
            prompt = "WHICH ALIVE GOOD PLAYER? (never one chosen before)",
            min = 1,
            max = 1,
            constraints = listOf(
                TargetConstraint.ALIVE,
                TargetConstraint.GOOD,
                // Every previous night, not just last night.
                TargetConstraint.NOT_CHOSEN_BEFORE,
            ),
            sort = TargetSort.ALIVE_FIRST,
        )
    },
    wakeCounts = WakeCount.NONE,
)

/**
 * The counter row. It always emits while a Leviathan is in play, evil executions
 * included: a registry row only wins over the built-in of the same id when it
 * actually produces one (lead D61), and the built-in's advice is wrong about
 * SURVIVED executions.
 */
private fun leviathanExecution(
    state: GameState,
    lookup: (String) -> Character?,
    record: ExecutionRecord,
): List<ExecutionConsequence> {
    if (record.outcome == ExecutionOutcome.NO_EXECUTION) return emptyList()
    val executed = record.playerId?.let { state.player(it) } ?: return emptyList()
    val wasGood = record.wasEvilAtExecution?.let { !it }
        ?: !Registration.registersEvil(state, lookup, executed)
    if (!wasGood) {
        return listOf(
            ExecutionConsequence(
                sourceId = "leviathan",
                headline = "${executed.name} was evil — no 'Good Player Executed' token. " +
                    "The count stays at ${goodExecutedMarks(state)} of 2.",
            ),
        )
    }
    return listOf(
        ExecutionConsequence(
            sourceId = "leviathan",
            headline = "${executed.name} was good — place a 'Good Player Executed' token. " +
                "That is ${goodExecutedMarks(state) + 1} of 2.",
            detail = "EVERY execution counts, including one nobody died from (a Virgin's " +
                "nominator, a Pacifist save, a storyteller execution). Alignment is judged at " +
                "the moment of execution. At 2, EVIL WINS.",
        ),
    )
}

private fun leviathanNomination(
    state: GameState,
    lookup: (String) -> Character?,
    nominatorId: Long?,
    nomineeId: Long?,
    holder: Player,
): List<NominationTrigger> {
    val nominee = nomineeId?.let { state.player(it) } ?: return emptyList()
    if (nominatorId != holder.id) return emptyList()
    if (!safeFromTheDemon(state, lookup, nominee)) return emptyList()
    return listOf(
        NominationTrigger(
            kind = TriggerKind.WARN,
            sourceId = "leviathan",
            actorId = holder.id,
            targetId = nominee.id,
            headline = "JINX: the Leviathan nominated ${nominee.name}, who is protected — " +
                "if this execution goes ahead, GOOD WINS.",
            detail = "Monk 'Safe', Innkeeper 'Safe', Exorcist 'Chosen' and the Soldier all " +
                "trigger the jinx. Show this again on the execute button.",
        ),
    )
}

// ---------------------------------------------------------------------------
// Lil' Monsta
// ---------------------------------------------------------------------------

/**
 * "Each night, Minions choose who babysits Lil' Monsta & 'is the Demon'. Each
 * night*, a player might die. [+1 Minion]"
 *
 * Lil' Monsta is a token, not a seat (lead D18 / D59): the Minions wake as one
 * group ([CharacterRule.groupStep]) and no Demon is woken at all. Night 1 has
 * the babysitter choice and NO kill; other nights take the babysitter first and
 * the (optional) victim second, in one undoable action, so the token moves
 * rather than accumulating.
 *
 * Registration of the babysitter as the Demon is WP1's `Registration`; the win
 * conditions therefore fall out of the ordinary demons-dead rule.
 */
private fun lilMonsta() = CharacterRule(
    id = "lilmonsta",
    groupStep = true,
    killCause = DeathCause.DEMON_KILL,
    demonKillUncertain = true,
    firstNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Wake every Minion except the Marionette — dead Minions too. They choose who " +
            "babysits. Show that player the 'You are' and Lil' Monsta tokens and hand the " +
            "physical token over. NOBODY DIES TONIGHT.",
        action = { lilMonstaBabysitter() },
        // The Minions are woken, but not for their own ability (lead D13).
        wakeCounts = WakeCount.INFORMED,
    ),
    otherNight = NightRule(
        gate = Gates.actsWhileDead,
        prompt = "Wake every Minion except the Marionette. FIRST pick the babysitter, THEN " +
            "(optionally) tonight's victim — the kill is YOUR choice, not the Demon's.",
        action = { ctx -> lilMonstaNight(ctx) },
        wakeCounts = WakeCount.INFORMED,
    ),
    day = DayRule(
        onExecution = { ctx -> lilMonstaExecution(ctx.state, ctx.lookup, ctx.record.playerId) },
    ),
    // Never swept: registration and the Psychopath/Vizier jinxes are day rules.
    tokens = listOf(
        TokenRule("lilmonsta", "Is The Demon", until = Until.FOREVER, copies = 1),
    ),
)

private fun lilMonstaExecution(
    state: GameState,
    lookup: (String) -> Character?,
    executedId: Long?,
): List<ExecutionConsequence> {
    val executed = executedId?.let { state.player(it) } ?: return emptyList()
    if (!holdsLilMonsta(state, executed)) return emptyList()
    return buildList {
        add(
            ExecutionConsequence(
                sourceId = "lilmonsta",
                headline = "${executed.name} was holding Lil' Monsta — if they died, the " +
                    "Demon is dead and GOOD WINS.",
                detail = "Scarlet Woman jinx: with a living Scarlet Woman and 5+ alive, she " +
                    "takes Lil' Monsta instead — move the token rather than ending the game. " +
                    "The Undertaker still sees this seat's OWN character.",
            ),
        )
        val jinxed = executed.characterId?.let(Character::normalizeId)
        if (jinxed == "psychopath" || jinxed == "vizier") {
            add(
                ExecutionConsequence(
                    sourceId = "lilmonsta",
                    headline = "JINX: a babysitting ${lookup(jinxed)?.name ?: jinxed} DIES when " +
                        "executed, overriding their usual immunity.",
                ),
            )
        }
    }
}

private fun lilMonstaBabysitter(): NightAction = ChoosePlayers(
    sourceId = "lilmonsta",
    prompt = "WHO BABYSITS LIL' MONSTA?",
    min = 1,
    max = 1,
    // Any player is legal: a good player or a Traveller included, and the same
    // player two nights running.
    constraints = listOf(TargetConstraint.ANY_LIVING_STATE),
    sort = TargetSort.MINION_FIRST,
    perTarget = listOf(
        NightEffect.PlaceToken(
            sourceId = "lilmonsta",
            label = "Is The Demon",
            on = Ref.Target,
            until = Until.FOREVER,
        ),
        NightEffect.ShowCardTo(Ref.Target, "YOU HOLD LIL' MONSTA — YOU ARE THE DEMON"),
    ),
)

private fun lilMonstaNight(ctx: NightContext): NightAction {
    val minions = ctx.state.alivePlayers.count {
        it.characterId?.let(ctx.lookup)?.team == Team.MINION
    }
    val advice = if (minions >= 2) {
        " MINIONS ALIVE: $minions — kill Minions so only 1 remains on the final day."
    } else {
        ""
    }
    return ChoosePlayers(
        sourceId = "lilmonsta",
        prompt = "1st PICK: WHO BABYSITS. 2nd PICK (optional): WHO DIES.$advice",
        min = 1,
        max = 2,
        constraints = listOf(TargetConstraint.ANY_LIVING_STATE),
        sort = TargetSort.MINION_FIRST,
        onResolve = listOf(
            NightEffect.PlaceToken(
                sourceId = "lilmonsta",
                label = "Is The Demon",
                on = Ref.TargetN(0),
                until = Until.FOREVER,
            ),
            NightEffect.ShowCardTo(Ref.TargetN(0), "YOU HOLD LIL' MONSTA — YOU ARE THE DEMON"),
            // Absent second pick => `Ref.TargetN(1)` addresses no seat and nobody dies.
            NightEffect.Attack(Ref.TargetN(1), DeathCause.DEMON_KILL),
        ),
    )
}

// ---------------------------------------------------------------------------
// Lleech
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a player: they die. You start by choosing a player: they
 * are poisoned. You die if & only if they are dead."
 *
 * Night 1 is the host choice and NO kill. The life-link is a [StandingRule]
 * emitting `DEATH_TIED_TO` on the Lleech, which WP1's kill funnel already reads
 * (step 3) — so the block is absolute across execution, exile, Slayer, Gossip,
 * Assassin and the Lleech's own choice. Because it is an *ability*, an impaired
 * Lleech loses it, which `abilityWorks` gives for free.
 *
 * The other half is the chain: when the HOST dies, the Lleech dies too.
 */
private fun lleech() = CharacterRule(
    id = "lleech",
    killCause = DeathCause.DEMON_KILL,
    // The life-link, kept apart from the poison so the official Soldier ruling
    // ("not poisoned, but becomes the host") is expressible. It outlives the
    // Lleech: the link is what decides whether the Lleech may die at all.
    tokens = listOf(
        TokenRule("lleech", "Host", effect = null, until = Until.FOREVER, endsWithSource = false),
    ),
    standing = StandingRule("lleech") { state, holder, _ ->
        val host = hostOf(state) ?: return@StandingRule emptyList()
        listOf(
            Effect(
                id = holder.standingSince,
                kind = EffectKind.DEATH_TIED_TO,
                targetId = holder.id,
                linkedPlayerId = host,
                sourceCharacterId = "lleech",
                sourcePlayerId = holder.id,
                until = Until.FOREVER,
                label = "",
                note = "The Lleech dies if and only if its host is dead.",
                createdCycle = state.cycle,
                createdAtNight = state.phase != Phase.DAY,
                derived = true,
            ),
        )
    },
    firstNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, noHostYet()),
        prompt = "The Lleech chooses a host: that player is poisoned, and the Lleech can only " +
            "die once they are dead. NO KILL TONIGHT.",
        action = { lleechHostChoice() },
    ),
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised, notSelfHosted()),
        prompt = "The Lleech chooses a player: they die. Choosing its own host kills the " +
            "Lleech too, and ends the game for evil.",
        action = { demonAttack("lleech", selfAllowed = true) },
    ),
    onDeath = listOf(
        // The host died while a Mastermind lives and it was an execution: the
        // Lleech lives, but loses its ability (jinx).
        DeathTrigger(
            gate = { state, _, event, holder -> hostChainFires(state, event, holder) && mastermindJinx(state, event) },
            produce = { state, _, _, holder ->
                TriggerResult(
                    prompts = listOf(
                        obligation(
                            state = state,
                            kind = PromptKind.ANNOUNCE,
                            subject = holder.id,
                            title = "MASTERMIND JINX: the Lleech's host was executed — the " +
                                "Lleech LIVES but loses their ability.",
                        ),
                    ),
                    effects = listOf(
                        Effect(
                            // The funnel stamps the real id (lead D64).
                            id = 0,
                            kind = EffectKind.NO_ABILITY,
                            targetId = holder.id,
                            sourceCharacterId = "lleech",
                            sourcePlayerId = holder.id,
                            until = Until.FOREVER,
                            endsWithSource = false,
                            label = "",
                            note = "Mastermind jinx: the host was executed.",
                            createdCycle = state.cycle,
                            createdAtNight = state.phase != Phase.DAY,
                        ),
                    ),
                )
            },
        ),
        // The ordinary chain: the host is dead, so the Lleech dies and good wins.
        DeathTrigger(
            gate = { state, _, event, holder -> hostChainFires(state, event, holder) && !mastermindJinx(state, event) },
            produce = { state, _, event, holder ->
                val host = state.player(event.playerId)?.name ?: "the host"
                TriggerResult(
                    prompts = listOf(
                        obligation(
                            state = state,
                            kind = PromptKind.RESOLVE_KILL,
                            subject = holder.id,
                            title = "$host was the Lleech's host and is dead — ${holder.name} " +
                                "(Lleech) dies now. GOOD WINS.",
                        ),
                    ),
                )
            },
        ),
        // An impaired Lleech has no life-link at all: it simply survives.
        DeathTrigger(
            gate = { state, _, event, holder ->
                isThisLleech(holder) && holder.alive && event.playerId == hostOf(state) &&
                    impairedNow(state, holder.id)
            },
            produce = { state, _, _, holder ->
                TriggerResult(
                    prompts = listOf(
                        obligation(
                            state = state,
                            kind = PromptKind.ANNOUNCE,
                            subject = holder.id,
                            title = "${holder.name} (Lleech) is drunk or poisoned — it survives " +
                                "its host's death. Say nothing.",
                        ),
                    ),
                )
            },
        ),
    ),
)

private fun lleechHostChoice(): NightAction = ChoosePlayers(
    sourceId = "lleech",
    prompt = "WHO IS THE LLEECH'S HOST?",
    min = 1,
    max = 1,
    constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.SELF_ALLOWED),
    sort = TargetSort.ALIVE_FIRST,
    perTarget = listOf(
        // Host FIRST: it is the life-link, and it is the one the Soldier ruling
        // keeps when the poison is waived (WP6C added the label).
        NightEffect.PlaceToken(
            sourceId = "lleech",
            label = "Host",
            on = Ref.Target,
            kind = EffectKind.MARKER,
            until = Until.FOREVER,
        ),
        NightEffect.PlaceToken(
            sourceId = "lleech",
            label = "Poisoned",
            on = Ref.Target,
            kind = EffectKind.POISONED,
            until = Until.FOREVER,
        ),
    ),
)

// ---------------------------------------------------------------------------
// Lord of Typhon
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a player: they die. [Evil characters are in a line. You
 * are in the middle. +1 Minion. -? to +? Outsiders]"
 *
 * The Lord of Typhon itself never wakes on night 1 — its neighbours do, one at a
 * time, and each becomes a different Minion. No kill panel on night 1. The bag,
 * the split and the seating validation are WP4's.
 */
private fun lordOfTyphon() = CharacterRule(
    id = "lordoftyphon",
    killCause = DeathCause.DEMON_KILL,
    firstNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, enteredAtSetup()),
        prompt = "The Lord of Typhon does NOT wake. Its neighbours become Minions: show each a " +
            "different Minion token and a thumbs-down, then replace their token. NO KILL TONIGHT.",
        action = { ctx -> typhonConversion(ctx) },
        wakeCounts = WakeCount.INFORMED,
    ),
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "The Lord of Typhon chooses a player: they die.",
        action = { demonAttack("lordoftyphon") },
    ),
)

private fun typhonConversion(ctx: NightContext): NightAction {
    val holder = ctx.holder
    val line = if (holder == null) {
        ""
    } else {
        val names = ctx.state.seatNeighbours(holder.id).joinToString(" and ") { it.name }
        if (names.isEmpty()) "" else " THE LINE GROWS OUT FROM $names."
    }
    return ChoosePlayerAndCharacter(
        sourceId = "lordoftyphon",
        prompt = "WHICH SEAT BECOMES WHICH MINION?$line",
        playerConstraints = listOf(
            TargetConstraint.ALIVE,
            TargetConstraint.NOT_SELF,
            TargetConstraint.NOT_TRAVELLER,
        ),
        pool = CharacterPool.MINION,
        // "a unique Minion token" — already-assigned Minions are not offered.
        requireNotInPlay = true,
        onResolve = listOf(
            NightEffect.BecomeCharacter(
                on = Ref.Target,
                characterId = "",
                evil = true,
                reason = ChangeReason.LORD_OF_TYPHON,
            ),
            NightEffect.ShowCardTo(Ref.Target, "YOU ARE — then a thumbs-down: YOU ARE EVIL"),
        ),
    )
}

// ---------------------------------------------------------------------------
// Ojo
// ---------------------------------------------------------------------------

/**
 * "Each night*, choose a character: they die. If they are not in play, the
 * Storyteller chooses who dies."
 *
 * The modality is the defect: this is a CHARACTER pick, not a player pick, and
 * the second half depends on the first. One [ChoosePlayerAndCharacter] takes
 * both — the named character (recorded, because the name is evidence even when
 * the kill fails) and the seat that dies. The step's own prompt does the
 * in-play arithmetic the storyteller otherwise has to do from memory.
 */
private fun ojo() = CharacterRule(
    id = "ojo",
    killCause = DeathCause.DEMON_KILL,
    // No first-night row at all — the Ojo is absent from the first-night order.
    otherNight = NightRule(
        gate = Gates.all(Gates.aliveHolder, Gates.notExorcised),
        prompt = "The Ojo chooses a CHARACTER. In play: that player dies. Not in play: YOU " +
            "choose who dies — almost always a good player. Record the name either way.",
        action = { ctx -> ojoChoice(ctx) },
        cards = { ctx ->
            listOf(
                CardOffer(
                    label = "SHOW: THE CHARACTER SHEET",
                    card = ShowCardSpec.SheetCard(ctx.state.script.characterIds),
                    truthful = true,
                ),
            )
        },
    ),
)

private fun ojoChoice(ctx: NightContext): NightAction = ChoosePlayerAndCharacter(
    sourceId = "ojo",
    prompt = "WHICH CHARACTER DID THEY NAME — AND WHO DIES? " +
        "(the holder if in play, otherwise your choice)",
    playerConstraints = listOf(TargetConstraint.ANY_LIVING_STATE),
    // The printed sheet, unsorted by in-play: a filtered picker leaks.
    pool = CharacterPool.SCRIPT,
    requireNotInPlay = false,
    onResolve = listOf(
        // Exactly one death. A protected in-play holder means NOBODY dies and no
        // substitute is offered — the choice was spent.
        NightEffect.Attack(Ref.Target, DeathCause.DEMON_KILL),
    ),
)

// ---------------------------------------------------------------------------
// Riot
// ---------------------------------------------------------------------------

/**
 * "On day 3, Minions become Riot & nominees die but nominate an alive player
 * immediately. This must happen."
 *
 * Riot never kills at night. Nights 1–2 are a skipped marker; night 3 is the
 * storyteller's conversion of every Minion — alive AND dead — into a Riot. Days
 * 1 and 2 are completely ordinary. WP3 owns the day-3 chain itself; this row
 * keeps WP3's AUTO_DEATH row and adds the four instant-win jinxes and the
 * Grandmother one, which must fire at NOMINATION time, before the kill lands.
 */
private fun riot() = CharacterRule(
    id = "riot",
    groupStep = true,
    // A dead Riot still nominates on day 3 and still counts for "until all Riot
    // are dead".
    keepsAbilityWhenDead = true,
    killCause = DeathCause.DEMON_KILL,
    otherNight = NightRule(
        gate = riotNightThree(),
        prompt = "Tomorrow is the riot. Wake each Minion in turn, show YOU ARE and the Riot " +
            "token, then replace their character token. The Riot is never woken to kill.",
        action = { ctx -> riotConversion(ctx) },
        wakeCounts = WakeCount.INFORMED,
    ),
    day = DayRule(onNomination = { ctx -> riotNomination(ctx.state, ctx.lookup, ctx.nomineeId) }),
)

private fun riotConversion(ctx: NightContext): NightAction {
    val minions = ctx.state.seats.filter { it.characterId?.let(ctx.lookup)?.team == Team.MINION }
    val names = minions.joinToString { it.name }
    return ChoosePlayers(
        sourceId = "riot",
        prompt = if (names.isEmpty()) {
            "NO MINIONS LEFT TO CONVERT"
        } else {
            "WHICH MINIONS BECOME RIOT? ALL OF THEM: $names (dead ones too)"
        },
        min = 0,
        max = minions.size.coerceAtLeast(1),
        // Dead Minions convert as well.
        constraints = listOf(TargetConstraint.ANY_LIVING_STATE, TargetConstraint.MINION),
        sort = TargetSort.SEAT_ORDER,
        allowNone = true,
        noneLabel = "No Minions to convert",
        perTarget = listOf(
            NightEffect.ShowCardTo(Ref.Target, "YOU ARE — the Riot"),
            NightEffect.BecomeCharacter(
                on = Ref.Target,
                characterId = "riot",
                // The Riot's own text makes them evil: this is one of the few
                // rows where the side is named rather than preserved (lead D67).
                evil = true,
                reason = ChangeReason.RIOT,
            ),
        ),
    )
}

private fun riotNomination(
    state: GameState,
    lookup: (String) -> Character?,
    nomineeId: Long?,
): List<NominationTrigger> {
    if (DayRules.riotDay(state) < 3) return emptyList()
    val nominee = nomineeId?.let { state.player(it) } ?: return emptyList()
    return buildList {
        // The four instant good wins fire BEFORE the death, at nomination time.
        if (safeFromTheDemon(state, lookup, nominee)) {
            add(
                NominationTrigger(
                    kind = TriggerKind.WARN,
                    sourceId = "riot",
                    targetId = nominee.id,
                    headline = "JINX: the Riot nominated ${nominee.name}, who is protected — " +
                        "GOOD WINS. Do not apply the death.",
                    detail = "Monk, Innkeeper, Exorcist and the Soldier each end the game here.",
                ),
            )
        }
        if (hasToken(state, nominee, "grandmother", "Grandchild")) {
            add(
                NominationTrigger(
                    kind = TriggerKind.WARN,
                    sourceId = "riot",
                    targetId = nominee.id,
                    headline = "JINX: ${nominee.name} is the Grandmother's grandchild — " +
                        "if they die here, EVIL WINS.",
                ),
            )
        }
        // WP3's row, restated: a registry row wins outright over the built-in of
        // the same id, so it must be re-emitted or it disappears (lead D61).
        add(
            NominationTrigger(
                kind = TriggerKind.AUTO_DEATH,
                sourceId = "riot",
                targetId = nominee.id,
                headline = "${nominee.name} dies immediately and must nominate — Riot.",
                detail = "There is no vote and no threshold. The day ends when nobody is left " +
                    "to nominate; a dead player may still be the next nominator.",
                options = listOf(
                    TriggerOption(DayRules.OPTION_APPLY, "They die", isDefault = true),
                    TriggerOption(DayRules.OPTION_SKIP, "Nothing happens"),
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Yaggababble
// ---------------------------------------------------------------------------

/**
 * "You start knowing a secret phrase. For each time you said it publicly today,
 * a player might die."
 *
 * Night 1 shows the phrase and kills NOBODY (there has been no day). Other
 * nights offer up to `charges` victims — and fewer, or none, is legal. The
 * sobriety check is at RESOLUTION time, not speaking time, which is the opposite
 * of what most storytellers assume, so it is on the step.
 *
 * W7b: the utterance tally is `GameState.counters["yaggababble.said"]` (lead
 * D72), added to by the day ability below every time the storyteller hears the
 * phrase, and zeroed by the night step that spends it — "for each time you said
 * it publicly TODAY" is a per-day count, not a running total. The phrase itself
 * stays a `decisions` secret.
 */
private fun yaggababble() = CharacterRule(
    id = "yaggababble",
    killCause = DeathCause.DEMON_KILL,
    demonKillUncertain = true,
    firstNight = NightRule(
        gate = Gates.aliveHolder,
        prompt = "The Yaggababble does NOT kill tonight. Show them their phrase — silently. " +
            "Count every time they say it publicly tomorrow.",
        cards = { ctx ->
            val phrase = phraseOf(ctx.state)
            listOf(
                CardOffer(
                    label = if (phrase.isEmpty()) "SHOW: NO PHRASE SET" else "SHOW: THE PHRASE",
                    card = ShowCardSpec.Message(
                        phrase.ifEmpty { "NO PHRASE CHOSEN YET" },
                        "YOUR SECRET PHRASE",
                    ),
                    truthful = true,
                ),
            )
        },
    ),
    otherNight = NightRule(
        // Impairment RIGHT NOW cancels the whole night, however often the phrase
        // was said; the Exorcist jinx is the same shape.
        gate = Gates.all(Gates.aliveHolder, Gates.hasAbility, Gates.notExorcised, hasCharges()),
        prompt = "The Yaggababble is not woken. Up to <charges> players may die and YOU choose " +
            "who — or fewer, or none. Sobriety is judged NOW, not when the phrase was said.",
        action = { ctx -> yaggababbleKill(ctx) },
        banner = { ctx ->
            val said = chargesOf(ctx.state)
            if (said == 0) "" else "Said the phrase $said time(s) yesterday."
        },
        // `pending` runs on EVERY resolve, including a Reduced (Exorcised) one,
        // so the day's tally is spent whether or not anybody died.
        pending = { listOf(NightEffect.SetCounter(Counters.YAGGABABBLE_SAID, 0)) },
        wakeCounts = WakeCount.NONE,
    ),
    day = DayRule(
        ability = DayAbility(
            label = "Said the phrase in public",
            recordsAs = "yaggababble",
            counterKey = Counters.YAGGABABBLE_SAID,
            // Every utterance counts as it happens; whether it KILLS is judged
            // at the step, on tonight's sobriety, not now.
            available = { _, _, holder -> holder.alive },
        ),
        briefing = { ctx ->
            if (ctx.slot != BriefingSlot.DAY_START) {
                emptyList()
            } else {
                val phrase = phraseOf(ctx.state)
                listOf(
                    BriefingItem(
                        key = "yaggababble:${ctx.holder.id}:${ctx.state.cycle}",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.ACTION,
                        sourceId = "yaggababble",
                        text = "${ctx.holder.name} is the Yaggababble. Tap “Said the phrase in " +
                            "public” every time you hear " +
                            (if (phrase.isEmpty()) "their phrase" else "“$phrase”") +
                            " today — tonight up to that many players may die.",
                        playerId = ctx.holder.id,
                    ),
                )
            }
        },
    ),
)

private fun yaggababbleKill(ctx: NightContext): NightAction {
    val charges = chargesOf(ctx.state)
    return ChoosePlayers(
        sourceId = "yaggababble",
        prompt = "UP TO $charges MAY DIE — YOU MAY KILL FEWER, OR NONE",
        min = 0,
        max = charges.coerceAtLeast(1),
        constraints = listOf(TargetConstraint.ALIVE),
        sort = TargetSort.ALIVE_FIRST,
        allowNone = true,
        noneLabel = "No-one dies tonight",
        // Per victim, so a Monk or Soldier can block one and not another.
        perTarget = listOf(NightEffect.Attack(Ref.Target, DeathCause.DEMON_KILL)),
    )
}

// ===========================================================================
// shared helpers
// ===========================================================================

/** The plain one-target Demon kill, with "nobody dies" as a recorded answer. */
private fun demonAttack(sourceId: String, selfAllowed: Boolean = false) = ChoosePlayers(
    sourceId = sourceId,
    prompt = "WHO DID THEY CHOOSE?",
    min = 1,
    max = 1,
    constraints = buildList {
        add(TargetConstraint.ALIVE)
        if (selfAllowed) add(TargetConstraint.SELF_ALLOWED)
    },
    sort = TargetSort.ALIVE_FIRST,
    allowNone = true,
    noneLabel = "No kill (impaired, protected, or storyteller's choice)",
    perTarget = listOf(NightEffect.Attack(Ref.Target, DeathCause.DEMON_KILL)),
)

/**
 * "Most players are Legion", "any Riot alive": the ability belongs to the GAME
 * while any holder lives, not to the lowest-seated one.
 */
private fun anyLivingHolder(characterId: String, reason: String): WakePredicate =
    WakePredicate { ctx ->
        val id = Character.normalizeId(characterId)
        val alive = ctx.state.alivePlayers.any {
            it.characterId?.let(Character::normalizeId) == id
        }
        if (alive) StepGate.Fire else StepGate.Skip(reason)
    }

/**
 * A Kazali or Lord of Typhon created mid-game (Pit-Hag, Summoner) does not
 * choose new Minions — only its kill runs.
 */
private fun enteredAtSetup(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    val arrived = ctx.state.identityLog.lastOrNull { it.playerId == holder.id }
    if (arrived == null || arrived.reason == ChangeReason.DEAL || arrived.cycle <= 1) {
        StepGate.Fire
    } else {
        StepGate.Skip("created mid-game — it does not choose new Minions")
    }
}

/** "You start by choosing a player": once per game, and only while no host exists. */
private fun noHostYet(): WakePredicate = WakePredicate { ctx ->
    if (hostOf(ctx.state) == null) {
        StepGate.Fire
    } else {
        StepGate.Skip("the Lleech already has a host")
    }
}

/** A self-hosted Lleech is permanently poisoned: no kill, no life-link. */
private fun notSelfHosted(): WakePredicate = WakePredicate { ctx ->
    val holder = ctx.holder ?: return@WakePredicate StepGate.Fire
    if (hostOf(ctx.state) == holder.id) {
        StepGate.Skip("self-hosted — permanently poisoned, no kill")
    } else {
        StepGate.Fire
    }
}

/** Riot converts on night 3 and never kills at night. */
private fun riotNightThree(): WakePredicate = WakePredicate { ctx ->
    if (ctx.night >= 3) {
        StepGate.Fire
    } else {
        StepGate.Skip(
            "Riot does not kill at night. Tomorrow is day ${ctx.night} of 3 — " +
                "days 1 and 2 are completely normal.",
        )
    }
}

/** No phrase said publicly today means no charges, and nobody dies. */
private fun hasCharges(): WakePredicate = WakePredicate { ctx ->
    val charges = chargesOf(ctx.state)
    if (charges > 0) {
        StepGate.Fire
    } else {
        StepGate.Skip("the phrase was not said publicly today — nobody dies")
    }
}

/**
 * The Lleech's host: the seat carrying `lleech/Host`, as an effect or as a
 * hand-placed token. Read from raw state — this runs inside the status query.
 *
 * WP6C added the `Host` label so host-ness and the poison are separate facts:
 * the official Soldier ruling has a host who is NOT poisoned, and until now the
 * only marker was the poison itself. `lleech/Poisoned` stays a fallback so a
 * game saved before the label existed still resolves its host.
 */
private fun hostOf(state: GameState): Long? {
    for (label in listOf("Host", "Poisoned")) {
        val key = Tokens.key("lleech", label)
        state.effects.firstOrNull { Tokens.key(it.sourceCharacterId, it.label) == key }
            ?.let { return it.targetId }
        state.players.firstOrNull { p -> p.reminders.any { Tokens.key(it) == key } }
            ?.let { return it.id }
    }
    return state.effects.firstOrNull {
        Character.normalizeId(it.sourceCharacterId) == "lleech" && it.kind == EffectKind.POISONED
    }?.targetId
}

private fun isThisLleech(holder: Player): Boolean =
    holder.characterId?.let(Character::normalizeId) == "lleech"

/** The host of this Lleech just died, and the Lleech is alive to feel it. */
private fun hostChainFires(state: GameState, event: DeathEvent, holder: Player): Boolean {
    if (!isThisLleech(holder) || !holder.alive) return false
    if (event.playerId != hostOf(state)) return false
    // An impaired Lleech has no life-link — that case has its own trigger.
    return !impairedNow(state, holder.id)
}

/**
 * Drunk / poisoned / no-ability right now, read from raw state.
 *
 * A [DeathTrigger] gate is handed no `lookup`, so the full `Status` recursion is
 * not available here; this is the honest subset it can answer without one.
 * FOLLOWUPS(WP2): give `DeathTrigger` the same `(state, lookup, …)` shape the
 * night and day hooks have.
 */
private fun impairedNow(state: GameState, playerId: Long): Boolean {
    val stored = state.effects.any {
        it.targetId == playerId && !it.suspended &&
            (
                it.kind == EffectKind.DRUNK ||
                    it.kind == EffectKind.POISONED ||
                    it.kind == EffectKind.NO_ABILITY
                )
    }
    if (stored) return true
    val seat = state.player(playerId) ?: return false
    return seat.reminders.any { Tokens.rule(it)?.impairs == true }
}

/** "If the Mastermind is alive and the Lleech host dies by execution…" */
private fun mastermindJinx(state: GameState, event: DeathEvent): Boolean =
    event.cause == DeathCause.EXECUTION &&
        state.alivePlayers.any { it.characterId?.let(Character::normalizeId) == "mastermind" }

private fun obligation(
    state: GameState,
    kind: PromptKind,
    subject: Long,
    title: String,
): Prompt = Prompt(
    // `Prompts.queue` stamps the real id.
    id = 0,
    at = BriefingSlot.NOW,
    kind = kind,
    sourceId = "lleech",
    subjectPlayerId = subject,
    title = title,
    dueCycle = state.cycle,
)

/** Monk / Innkeeper / Exorcist / Soldier — the Riot and Leviathan jinx set. */
private fun safeFromTheDemon(
    state: GameState,
    lookup: (String) -> Character?,
    player: Player,
): Boolean {
    if (player.characterId?.let(Character::normalizeId) == "soldier") return true
    return Status.live(state, lookup, player.id).any {
        it.kind == EffectKind.SAFE_FROM_DEMON ||
            it.kind == EffectKind.CANT_DIE_TONIGHT ||
            it.kind == EffectKind.CANT_DIE ||
            it.kind == EffectKind.DEMON_CANNOT_KILL
    }
}

private fun hasToken(state: GameState, player: Player, sourceId: String, label: String): Boolean {
    val key = Tokens.key(sourceId, label)
    return player.reminders.any { Tokens.key(it) == key } ||
        state.effects.any {
            it.targetId == player.id && Tokens.key(it.sourceCharacterId, it.label) == key
        }
}

/** Both spellings count: a hand-placed token and the effect the pipeline places. */
private fun holdsLilMonsta(state: GameState, player: Player): Boolean =
    hasToken(state, player, "lilmonsta", "Is The Demon")

/** How many "Good Player Executed" marks the Leviathan already has. */
private fun goodExecutedMarks(state: GameState): Int {
    val key = Tokens.key("leviathan", "Good Player Executed")
    return (state.storytellerReminders + state.players.flatMap { it.reminders })
        .count { Tokens.key(it) == key }
}

/** The normal number of Minions for this table — the Kazali's creation target. */
private fun minionsWanted(state: GameState): Int {
    val residents = state.seats.count { !it.isTraveller }
    return runCatching { Setup.distributionFor(residents).minions }.getOrDefault(0)
}

/** The secret phrase itself is a storyteller decision, not a tally. */
private const val YAGG_PHRASE = "yaggababble.phrase"

private fun phraseOf(state: GameState): String = state.decisions[YAGG_PHRASE].orEmpty()

/**
 * How many players may die tonight: the number of times the phrase was said
 * publicly TODAY (lead D72). The night step zeroes it as it spends it, so the
 * count never carries into a second night.
 */
private fun chargesOf(state: GameState): Int = Counters.get(state, Counters.YAGGABABBLE_SAID)
