package com.clocktower.engine.rules

import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSeverity
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Candidate
import com.clocktower.engine.Character
import com.clocktower.engine.CharacterRule
import com.clocktower.engine.DayAbility
import com.clocktower.engine.DayRule
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeathTrigger
import com.clocktower.engine.EffectKind
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.FabledEntry
import com.clocktower.engine.GameState
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightRule
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.PromptKind
import com.clocktower.engine.Ref
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.Status
import com.clocktower.engine.StepGate
import com.clocktower.engine.Team
import com.clocktower.engine.TokenRule
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.TriggerOption
import com.clocktower.engine.TriggerResult
import com.clocktower.engine.Tokens
import com.clocktower.engine.Until
import com.clocktower.engine.WakeCount
import com.clocktower.engine.WakePredicate

/**
 * Fabled and Loric behaviour (WP7-FAB): 14 `team = "fabled"` + 11 `team = "loric"`.
 *
 * ## What is different about this file
 *
 * Every other WP7 package writes rows for SEATED characters. A Fabled has no
 * seat: it lives in [GameState.fabled] as a [FabledEntry], its tokens live on
 * seats or in `GameState.storytellerReminders`, and its per-game configuration
 * lives in `FabledEntry.config` under the typed keys of lead D45.
 *
 * That has three consequences for the rows below, all of them filed to WP2 in
 * this package's final report:
 *
 * 1. **`standing` never fires for a Fabled.** `Standing.emitSelf` /
 *    `emitPositional` iterate `state.seats` and look the registry up by the
 *    seat's character id. The Storm Catcher's `ONLY_EXECUTION_KILLS` is
 *    therefore emitted by WP1's own `fabledConfig` block in `Effects.kt` — this
 *    file AGREES with it and never duplicates it.
 * 2. **`onDeath` never fires for a Fabled.** `Deaths.fireDeathTriggers` iterates
 *    seats too. The Angel's and the Hindu's triggers are declared here so they
 *    start working the moment that loop also walks `state.fabled`.
 * 3. **`day.onNomination` / `day.onExecution` never fire for a Fabled**, for the
 *    same reason (`DayRules.triggersFor`, `Execution.consequences`).
 *
 * What IS live today for a Fabled row:
 *
 * - [CharacterRule.tokens] — `Tokens.all` layers `CharacterRules.tokenRules`
 *   over WP1's `Tokens.BASE` and later rows win, so a Fabled owns its own token
 *   lifecycle (the Duchess's dawn sweep, the Toymaker's marker) without editing
 *   `Tokens.kt`.
 * - [NightRule.pending] — `NightPlan.resolve` looks the registry up by
 *   `step.abilityId`, which for a Fabled's own night-order slot IS the Fabled
 *   id. The Toymaker's obligation is placed and enforced through it.
 *
 * ## Conventions
 *
 * Official Title Case labels only, with `copies` matching `characters.json`
 * exactly (`TokensTest` fails the build otherwise). Characters whose official
 * reminder set is empty declare no tokens at all, even where the audit digest
 * wants one — those are listed as WP5 data follow-ups, never invented here.
 */
internal val FABLED_RULES: List<CharacterRule> = listOf(
    // ---- Fabled (14) ----
    angel(),
    buddhist(),
    deusExFiasco(),
    djinn(),
    doomsayer(),
    duchess(),
    ferryman(),
    fibbin(),
    fiddler(),
    hellsLibrarian(),
    revolutionary(),
    sentinel(),
    spiritOfIvory(),
    toymaker(),

    // ---- Loric (11) ----
    bigWig(),
    bootlegger(),
    gardener(),
    godOfUg(),
    hindu(),
    knaves(),
    pope(),
    stormCatcher(),
    tor(),
    ventriloquist(),
    zenomancer(),
)

// ===========================================================================
// Shared helpers
// ===========================================================================

/** The in-play entry for [id], or null when this Fabled is not in play. */
internal fun fabledEntry(state: GameState, id: String): FabledEntry? =
    state.fabled.firstOrNull { Character.normalizeId(it.id) == Character.normalizeId(id) }

/** True when [id] is one of the Fabled the storyteller declared for this game. */
internal fun fabledActive(state: GameState, id: String): Boolean = fabledEntry(state, id) != null

/** One typed `FabledEntry.config` value (lead D45), or null. */
internal fun fabledConfig(state: GameState, id: String, key: String): String? =
    fabledEntry(state, id)?.config?.get(key)

/** Seats currently holding the token `(sourceId, label)`, as an effect or a reminder. */
internal fun seatsHolding(state: GameState, sourceId: String, label: String): List<Player> {
    val key = Tokens.key(sourceId, label)
    return state.seats.filter { seat ->
        seat.reminders.any { Tokens.key(it) == key } ||
            state.effects.any { it.targetId == seat.id && Tokens.key(it.sourceCharacterId, it.label) == key }
    }
}

/**
 * A standing fact the storyteller must be reminded of but that the engine
 * cannot act on — the `reference` channel of `night_guide.json` (lead D23),
 * rendered as one STANDING_FACT briefing line.
 *
 * Every Fabled and Loric with no mechanical hook the engine can reach still
 * gets an explicit [CharacterRule] carrying one of these: never silently absent.
 */
private fun referenceNote(id: String, text: String): DayRule = DayRule(
    briefing = { ctx ->
        if (!fabledActive(ctx.state, id)) {
            emptyList()
        } else {
            listOf(
                BriefingItem(
                    key = "fabled:$id",
                    kind = BriefingKind.STANDING_FACT,
                    severity = BriefingSeverity.INFO,
                    sourceId = id,
                    text = text,
                ),
            )
        }
    },
)

/** A [DayRule] that is a reference note AND a day-tab action. */
private fun referenceNote(id: String, text: String, ability: DayAbility): DayRule =
    referenceNote(id, text).copy(ability = ability)

/**
 * A Fabled step is never dead, never impaired, never spent, never Exorcised.
 *
 * A function, not a `val`: top-level properties initialise in declaration order,
 * and [FABLED_RULES] is built above.
 */
private fun fabledAlwaysFires(): WakePredicate = WakePredicate { StepGate.Fire }

/** Seats that are seated, alive and not Travellers — the "players" a rule counts. */
internal fun aliveResidents(state: GameState): List<Player> =
    state.seats.filter { it.alive && !it.isTraveller }

// ===========================================================================
// Fabled
// ===========================================================================

/**
 * Angel — "Something bad might happen to whoever is most responsible for the
 * death of a new player."
 *
 * P0: `angel/Protected` is a MARKER and must NEVER read as a protection. The
 * old app matched protections on the bare label "Protected", so an Angel's
 * protectee could not die — the exact inverse of the rule. WP1's `Tokens.BASE`
 * scopes the protective "Protected" to [Tokens.STORYTELLER_SOURCE]; this row
 * pins the Angel's own copies as pure markers so the two can never merge.
 *
 * The penalty itself is a storyteller ruling: the death trigger asks, it never
 * decides. The official token set is `Protected ×2` + `Something Bad`, so the
 * "no ability today" / "can't vote today" penalties have no data label yet —
 * filed to WP5.
 */
private fun angel() = CharacterRule(
    id = "angel",
    killCause = DeathCause.STORYTELLER,
    tokens = listOf(
        // NOT SAFE_FROM_DEMON. The protectee dies normally; the Angel punishes
        // whoever caused it. Removed by hand, or in one tap on the final day.
        TokenRule("angel", "Protected", effect = null, until = Until.FOREVER, copies = 2),
        TokenRule("angel", "Something Bad", effect = null, until = Until.FOREVER),
    ),
    firstNight = NightRule(
        gate = fabledAlwaysFires(),
        prompt = "Announce which players are protected by the Angel.",
        wakeCounts = WakeCount.NONE,
    ),
    onDeath = listOf(
        DeathTrigger(
            // "Even if dead" by construction: the Angel has no seat and no ability
            // to lose, so no `Status.hasAbility` gate (lead D35 exception).
            gate = { state, event, _ ->
                fabledActive(state, "angel") &&
                    seatsHolding(state, "angel", "Protected").any { it.id == event.playerId }
            },
            produce = { state, event, _ ->
                val victim = state.player(event.playerId)?.name ?: "The protected player"
                val suggested = if (event.cause == DeathCause.EXECUTION) {
                    state.executions.lastOrNull { it.day == event.day }?.nominatorId
                } else {
                    event.killerPlayerId
                }
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = BriefingSlot.NOW,
                            kind = PromptKind.DECIDE,
                            sourceId = "angel",
                            subjectPlayerId = event.playerId,
                            targetIds = listOfNotNull(suggested),
                            title = "Angel: $victim was protected — who was most responsible?",
                            detail = "Something bad MIGHT happen to them: they die, they lose their " +
                                "ability today, they cannot vote today, or you place the SOMETHING " +
                                "BAD token and decide later. Nothing is forced.",
                        ),
                    ),
                )
            },
        ),
    ),
    day = referenceNote(
        "angel",
        "Angel — the protected players die normally. On the final day, remove the Angel so " +
            "the town is free to execute a protected player.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "angel.protectees",
            characterId = "angel",
            kind = RequirementKind.REMINDER,
            title = "Angel: who is protected?",
            prompt = "Who does the Angel protect? Ask each player's consent first. They still " +
                "die normally; whoever is most responsible for their death gets a penalty.",
            problem = "Angel is in play but protects nobody",
            blocking = false,
            candidates = { state, _ -> state.seats.map { Candidate(it.id.toString(), it.name, it.id) } },
            satisfied = { state, _ -> fabledEntry(state, "angel")?.playerIds?.isNotEmpty() == true },
        ),
    ),
)

/**
 * Buddhist — "For the first 2 minutes of each day, veteran players may not talk."
 *
 * A day-timer card with no rules effect: the silence is social, never enforced
 * by `checkNomination`. The official reminder set is EMPTY, so the digest's
 * `Silent` token cannot be declared (WP5 follow-up).
 */
private fun buddhist() = CharacterRule(
    id = "buddhist",
    firstNight = NightRule(
        gate = fabledAlwaysFires(),
        prompt = "Announce which players are affected by the Buddhist.",
        wakeCounts = WakeCount.NONE,
    ),
    day = referenceNote(
        "buddhist",
        "Buddhist — the veteran players may not talk for the first 2 minutes of the day. " +
            "Start the 2-minute timer at dawn; a silent player may still nominate.",
        DayAbility(
            label = "Buddhist — start the 2 minutes",
            oncePerDay = true,
            recordsAs = "buddhist",
            available = { state, _, _ ->
                fabledEntry(state, "buddhist")?.playerIds?.isNotEmpty() == true
            },
        ),
    ),
    setup = listOf(
        SetupRequirement(
            id = "buddhist.veterans",
            characterId = "buddhist",
            kind = RequirementKind.REMINDER,
            title = "Buddhist: who is a veteran?",
            prompt = "Who is a Buddhist? They must stay silent for the first 2 minutes of each " +
                "day. Ask their consent first.",
            blocking = false,
            candidates = { state, _ -> state.seats.map { Candidate(it.id.toString(), it.name, it.id) } },
            satisfied = { state, _ -> fabledEntry(state, "buddhist")?.playerIds?.isNotEmpty() == true },
        ),
    ),
)

/**
 * Deus ex Fiasco — "At least once per game, the Storyteller will make a mistake,
 * correct it, and publicly admit to it."
 *
 * `setup = true` in the data and it may not be added mid-game, so the
 * requirement is an acknowledgement taken during SETUP. WHOOPSIE is a permanent
 * record of the game, in the grimoire centre, never on a seat.
 */
private fun deusExFiasco() = CharacterRule(
    id = "deusexfiasco",
    tokens = listOf(
        TokenRule(
            "deusexfiasco", "Whoopsie", effect = null, until = Until.FOREVER,
            grimoireCentre = true,
        ),
    ),
    day = referenceNote(
        "deusexfiasco",
        "Deus ex Fiasco — you owe the table at least one mistake, corrected and publicly " +
            "admitted. Place a WHOOPSIE token each time you make one.",
        DayAbility(
            label = "Whoopsie — a mistake was made",
            recordsAs = "deusexfiasco",
            available = { state, _, _ -> fabledActive(state, "deusexfiasco") },
        ),
    ),
    setup = listOf(
        SetupRequirement(
            id = "deusexfiasco.availability",
            characterId = "deusexfiasco",
            kind = RequirementKind.ACK,
            title = "Deus ex Fiasco: announce it now",
            prompt = "Announce the Deus ex Fiasco before the bag goes round. It cannot be added " +
                "once the game has started.",
            blocking = false,
            satisfied = { state, _ -> fabledActive(state, "deusexfiasco") },
        ),
    ),
)

/**
 * Djinn — "Use the Djinn's special rule. All players know what it is."
 *
 * P0: the rules the Djinn announces are the jinxes of the SCRIPT, not of the
 * characters that happened to be dealt — telling the table only about dealt
 * pairs leaks the bag. The scope split lives in `GameData.activeJinxes`, which
 * is asked for a set of ids: the caller passes the script's ids for the read-out
 * and the assigned ids for per-seat hints. No jinx pair names a Fabled, so the
 * Djinn itself contributes no `jinxRules`.
 */
private fun djinn() = CharacterRule(
    id = "djinn",
    day = referenceNote(
        "djinn",
        "Djinn — read every jinx on this script out loud before the bag goes round. They apply " +
            "whether or not the jinxed characters are in play.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "djinn.announce",
            characterId = "djinn",
            kind = RequirementKind.ACK,
            title = "Djinn: read the script's jinxes out",
            prompt = "Read these special rules to the group before the bag goes round. They apply " +
                "this game whether or not the characters are in play.",
            blocking = false,
            satisfied = { state, _ -> fabledActive(state, "djinn") },
        ),
    ),
)

/**
 * Doomsayer — "If 4 or more players live, each living player may publicly choose
 * (once per game) that a player of their own alignment dies."
 *
 * Once per PLAYER, not once per game: `FabledEntry.spentBy` holds the invokers,
 * so a resurrected invoker stays spent. The count is of *players*, so Travellers
 * count (`aliveCountWithTravellers`). The official reminder set is empty, so the
 * digest's `Used` token cannot be declared (WP5 follow-up).
 */
private fun doomsayer() = CharacterRule(
    id = "doomsayer",
    killCause = DeathCause.STORYTELLER,
    day = referenceNote(
        "doomsayer",
        "Doomsayer — while 4 or more players live, any living player may publicly spend it once " +
            "to kill a player of their own alignment. Not an execution, not a night death.",
        DayAbility(
            label = "Doomsayer",
            oncePerGame = false,
            recordsAs = "doomsayer",
            available = { state, _, holder ->
                val entry = fabledEntry(state, "doomsayer")
                entry != null &&
                    state.aliveCountWithTravellers >= 4 &&
                    holder.alive &&
                    holder.id !in entry.spentBy
            },
        ),
    ),
)

/**
 * Duchess — "Each day, 3 players may choose to visit you. At night*, each visitor
 * learns how many visitors are evil, but 1 gets false info."
 *
 * P0: the visitor tokens must be swept at DAWN. WP1's table does not name them,
 * so yesterday's visitors were silently reused every other night; this row owns
 * their lifecycle. `Visitor ×2 + False Info` is the official three-token set, so
 * marking a second visitor no longer un-marks the first.
 *
 * The number counts the False Info player and the woken player themselves and is
 * legally 0..3. `InfoCalc` has no `duchess` case yet (filed to WP2), so the step
 * is declared with `infoId = "duchess"` and degrades to a prompt until it does.
 */
private fun duchess() = CharacterRule(
    id = "duchess",
    tokens = listOf(
        TokenRule("duchess", "Visitor", effect = null, until = Until.DAWN, copies = 2),
        TokenRule("duchess", "False Info", effect = null, until = Until.DAWN),
    ),
    otherNight = NightRule(
        gate = WakePredicate { ctx ->
            val marked = duchessVisitors(ctx.state).size
            if (marked == 3) {
                StepGate.Fire
            } else {
                StepGate.Skip("no visitors tonight — $marked players are marked, not 3")
            }
        },
        prompt = "Wake each player marked \"Visitor\" or \"False Info\" one at a time. Show the " +
            "Duchess token, then fingers (0, 1, 2 or 3) equal to the number of EVIL players " +
            "marked either \"Visitor\" or \"False Info\". The \"False Info\" player is shown any " +
            "other number.",
        infoId = "duchess",
        wakeCounts = WakeCount.NONE,
    ),
    day = referenceNote(
        "duchess",
        "Duchess — pick tonight's 3 visitors during the day. One of them gets false info; who " +
            "that is never appears on a show card.",
        DayAbility(
            label = "Duchess — tonight's visitors",
            oncePerDay = true,
            recordsAs = "duchess",
            available = { state, _, _ -> fabledActive(state, "duchess") },
        ),
    ),
)

/** Seats marked with either Duchess token — the visitors, in seat order. */
internal fun duchessVisitors(state: GameState): List<Player> =
    (seatsHolding(state, "duchess", "Visitor") + seatsHolding(state, "duchess", "False Info"))
        .distinctBy { it.id }
        .sortedBy { state.seats.indexOfFirst { seat -> seat.id == it.id } }

/**
 * Ferryman — "On the final day, all dead players regain their vote token."
 *
 * Keyed off `GameState.finalDayCycle` (lead D47), which is DECLARED by the
 * storyteller and never inferred. The restore is idempotent — it clears
 * `ghostVoteUsed`, it never toggles — and `FabledEntry.used` guards it so a vote
 * spent after the restore stays spent. Adding the Ferryman AFTER the final day is
 * declared fires it immediately.
 */
private fun ferryman() = CharacterRule(
    id = "ferryman",
    day = referenceNote(
        "ferryman",
        "Ferryman — when you declare the final day, every dead player gets their vote token back. " +
            "It affects execution votes only; exiles were already unrestricted.",
        DayAbility(
            label = "Ferryman — return the dead players' votes",
            oncePerGame = true,
            recordsAs = "ferryman",
            available = { state, _, _ -> ferrymanOwes(state) },
        ),
    ),
)

/** True when the Ferryman is in play, the final day is declared, and it has not fired. */
internal fun ferrymanOwes(state: GameState): Boolean {
    val entry = fabledEntry(state, "ferryman") ?: return false
    return !entry.used && state.finalDayCycle != null
}

/**
 * Fibbin — "Once per game, 1 good player might get incorrect information."
 *
 * Information only: the Fibbin never makes an ability fail, places nothing on a
 * seat and creates NO `Effect`, so `Status.impairment` must stay empty and a
 * Fibbin-lied player is not a Mathematician malfunction. Its NO ABILITY mark is
 * the spend record and belongs on the FABLED token, not on a player — hence
 * `grimoireCentre`, which is this row's one refinement of WP1's spend-mark table.
 *
 * `Character.spentLabel` is "No Ability" in the data (lead D49), so
 * `Gates.notSpent()` reads it without any text heuristic.
 */
private fun fibbin() = CharacterRule(
    id = "fibbin",
    tokens = listOf(
        TokenRule(
            "fibbin", "No Ability", EffectKind.SPENT, Until.FOREVER,
            endsWithSource = false, grimoireCentre = true,
        ),
    ),
    day = referenceNote(
        "fibbin",
        "Fibbin — once per game you may give ONE good player incorrect information, or make " +
            "information that would have been false true. Information only: it never makes an " +
            "ability fail, and it is not a malfunction for the Mathematician.",
    ),
)

/**
 * Fiddler — "Once per game, the Demon secretly chooses an opposing player: all
 * players choose which of these 2 players win."
 *
 * The contest is not a nomination and not an exile: dead players vote without
 * spending a ghost vote, Travellers vote, both contestants vote, and no vote
 * weight, jinx or `DayRules` guard applies. A tie is won by the EVIL contestant.
 * Activating it ends the game, so it is offered as a once-per-game day action
 * rather than fired by any rule.
 */
private fun fiddler() = CharacterRule(
    id = "fiddler",
    day = referenceNote(
        "fiddler",
        "Fiddler — using it ENDS the game. Abilities stop working, the Demon secretly picks an " +
            "opposing player, everyone votes for one of the two, and the winner's whole team " +
            "wins. A tie is won by the evil contestant.",
        DayAbility(
            label = "Fiddler — hold the contest",
            oncePerGame = true,
            recordsAs = "fiddler",
            available = { state, _, _ -> fabledEntry(state, "fiddler")?.used == false },
        ),
    ),
    setup = listOf(
        SetupRequirement(
            id = "fiddler.endTime",
            characterId = "fiddler",
            kind = RequirementKind.ACK,
            title = "Fiddler: announce when the game ends",
            prompt = "Fiddler added — when will the game end? Announce it to the group now.",
            blocking = false,
            satisfied = { state, _ -> fabledActive(state, "fiddler") },
        ),
    ),
)

/**
 * Hell's Librarian — "Something bad might happen to whoever talks when the
 * Storyteller has asked for silence."
 *
 * The official token is SOMETHING BAD only; the "no ability today" and "no vote
 * today" penalties have no data label (WP5 follow-up), so they are applied as
 * storyteller rulings — `NO_ABILITY` / `NO_VOTE` effects until DUSK — rather
 * than invented tokens.
 */
private fun hellsLibrarian() = CharacterRule(
    id = "hellslibrarian",
    killCause = DeathCause.STORYTELLER,
    tokens = listOf(
        TokenRule("hellslibrarian", "Something Bad", effect = null, until = Until.FOREVER),
    ),
    day = referenceNote(
        "hellslibrarian",
        "Hell's Librarian — a light penalty works much better than a severe one. Whoever talks " +
            "through your silence may die, lose their ability today, lose their vote today, or " +
            "just take the SOMETHING BAD token while you decide.",
        DayAbility(
            label = "Ask for silence",
            recordsAs = "hellslibrarian",
            available = { state, _, _ -> fabledActive(state, "hellslibrarian") },
        ),
    ),
)

/**
 * Revolutionary — "2 neighboring players are known to be the same alignment.
 * Once per game, 1 of them registers falsely."
 *
 * The pair must be NEIGHBOURS and must be dealt the same alignment — a
 * constrained deal, not a hope. The spend is a `REGISTERS_AS` effect (lead D10),
 * never an invented "Registered: X" token pair; the wiki does not say whether it
 * lasts for one piece of information or one night, so the choice is offered and
 * recorded, never guessed.
 */
private fun revolutionary() = CharacterRule(
    id = "revolutionary",
    tokens = listOf(
        TokenRule("revolutionary", "Register Falsely?", EffectKind.REGISTERS_AS, Until.FOREVER),
        TokenRule("revolutionary", "Aligned", effect = null, until = Until.FOREVER, copies = 2),
    ),
    day = referenceNote(
        "revolutionary",
        "Revolutionary — the two marked players are publicly known to be the same alignment. " +
            "Once per game you may make one of them register falsely.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "revolutionary.pair",
            characterId = "revolutionary",
            kind = RequirementKind.PAIR,
            title = "Revolutionary: pick the pair",
            prompt = "Pick two NEIGHBOURING seats who play as a pair. They draw the same " +
                "alignment. Get both players' consent first, and tell the table.",
            problem = "The Revolutionary's two players must be neighbours",
            candidates = { state, _ -> state.seats.map { Candidate(it.id.toString(), it.name, it.id) } },
            satisfied = { state, _ -> revolutionaryPairIsLegal(state) },
        ),
    ),
)

/** The Revolutionary's pair: exactly two seats, neighbouring, same alignment. */
internal fun revolutionaryPairIsLegal(state: GameState): Boolean {
    val pair = fabledEntry(state, "revolutionary")?.playerIds ?: return false
    if (pair.size != 2) return false
    val seats = state.seats
    val a = seats.indexOfFirst { it.id == pair[0] }
    val b = seats.indexOfFirst { it.id == pair[1] }
    if (a < 0 || b < 0 || seats.isEmpty()) return false
    val gap = ((a - b) % seats.size + seats.size) % seats.size
    return gap == 1 || gap == seats.size - 1
}

/**
 * Sentinel — "There might be 1 extra or 1 fewer Outsider in play."
 *
 * The bag rule itself is WP4's: `Setup.validateBag(..., fabledIds)` already
 * widens the legal Outsider counts by ±1 when "sentinel" is active, and clamps
 * at zero so a 5-player base of 0 Outsiders offers {0, 1} only. This row does not
 * duplicate it; it declares the one decision the storyteller must record so
 * "how many Outsiders are in play?" is answerable mid-game.
 */
private fun sentinel() = CharacterRule(
    id = "sentinel",
    day = referenceNote(
        "sentinel",
        "Sentinel — the Outsider count is a secret. One fewer, as printed, or one extra: the " +
            "decision is made once, before the tokens go in the bag, and is never announced.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "sentinel.outsiderDelta",
            characterId = "sentinel",
            kind = RequirementKind.NUMBER,
            title = "Sentinel: how many Outsiders?",
            prompt = "Sentinel — choose: one fewer Outsider, as printed, or one extra. Decide " +
                "before the tokens go in the bag.",
            blocking = false,
            candidates = { _, _ ->
                listOf(
                    Candidate("-1", "One fewer Outsider"),
                    Candidate("0", "As printed"),
                    Candidate("1", "One extra Outsider"),
                )
            },
            satisfied = { state, _ -> fabledConfig(state, "sentinel", SENTINEL_DELTA) != null },
        ),
    ),
)

/** `FabledEntry.config` key for the Sentinel's chosen Outsider delta (lead D45). */
internal const val SENTINEL_DELTA: String = "sentinel.outsiderDelta"

/**
 * Spirit of Ivory — "There can't be more than 1 extra evil player."
 *
 * A rule modifier on BECOMING evil, not a registration. The baseline is snapshot
 * once at SETUP -> NIGHT 1 into `FabledEntry.config["spiritofivory.baselineEvil"]`
 * because deaths, arriving Travellers and character changes all perturb the live
 * distribution. Travellers are excluded from both counts, and the Politician
 * turns evil at scoring rather than in play, so it never counts here.
 *
 * The asymmetry that matters: an Imp star pass does NOT create an extra evil (a
 * Minion becomes the Demon, the count is unchanged) while a Fang Gu jump does.
 * NO MORE EVIL is derived, not hand-managed: it appears the instant the count
 * reaches the cap and lifts again when a Snake Charmer swap drops it.
 */
private fun spiritOfIvory() = CharacterRule(
    id = "spiritofivory",
    tokens = listOf(
        TokenRule(
            "spiritofivory", "No More Evil", effect = null, until = Until.FOREVER,
            grimoireCentre = true,
        ),
    ),
    day = referenceNote(
        "spiritofivory",
        "Spirit of Ivory — at most 1 extra evil player. When the cap is reached the next " +
            "conversion is blocked: the player stays GOOD and the ability still happens.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "spiritofivory.baselineEvil",
            characterId = "spiritofivory",
            kind = RequirementKind.NUMBER,
            title = "Spirit of Ivory: snapshot the evil count",
            prompt = "Record how many evil players this game starts with, before any setup " +
                "conversion. Everything above that number is an \"extra\" evil player.",
            blocking = false,
            satisfied = { state, _ -> fabledConfig(state, "spiritofivory", IVORY_BASELINE) != null },
        ),
    ),
)

/** `FabledEntry.config` key for the Spirit of Ivory's snapshot evil count (lead D45). */
internal const val IVORY_BASELINE: String = "spiritofivory.baselineEvil"

/** Evil, non-Traveller seats right now. */
internal fun currentEvilCount(state: GameState, lookup: (String) -> Character?): Int =
    state.seats.count { !it.isTraveller && it.isEvil(lookup) }

/** The snapshot baseline, or the live count when no snapshot was taken. */
internal fun baselineEvilCount(state: GameState, lookup: (String) -> Character?): Int =
    fabledConfig(state, "spiritofivory", IVORY_BASELINE)?.toIntOrNull()
        ?: currentEvilCount(state, lookup)

/** How many evil players there are above the baseline. */
internal fun extraEvilCount(state: GameState, lookup: (String) -> Character?): Int =
    currentEvilCount(state, lookup) - baselineEvilCount(state, lookup)

/**
 * True when the Spirit of Ivory's cap is reached: the NEXT conversion is blocked
 * and NO MORE EVIL belongs in the grimoire centre.
 */
internal fun noMoreEvil(state: GameState, lookup: (String) -> Character?): Boolean =
    fabledActive(state, "spiritofivory") && extraEvilCount(state, lookup) >= 1

/**
 * Toymaker — "The Demon may choose not to attack & must do this at least once
 * per game. Evil players get normal starting info."
 *
 * Two P0s.
 *
 * 1. **The obligation must not silence the Demon every night.** WP1's table
 *    types `toymaker/Final Night: No Attack` as `DEMON_CANNOT_KILL`, which is
 *    read off the SOURCE seat in the kill funnel — so an unspent obligation made
 *    the Demon permanently unable to kill. Lead D36 scopes the suppression to
 *    the "Toymaker final night", so this row refines the token to what it
 *    physically is (a marker meaning "the obligation is unspent") and places a
 *    real, DAWN-scoped `DEMON_CANNOT_KILL` only on a night where the attack
 *    could end the game.
 * 2. **The forced skip must actually be enforced.** `otherNight.pending` runs at
 *    the Toymaker's own night-order slot, which sits before every Demon, so by
 *    the time the Demon's row comes up the funnel already blocks the kill.
 *
 * `demonAttackCouldEndGame` counts alive non-Travellers; Fabled hold no seat, so
 * they are excluded for free. Where a script's win condition differs (Mayor at 3,
 * Leviathan, Riot) this is advisory and the step says so.
 */
private fun toymaker() = CharacterRule(
    id = "toymaker",
    tokens = listOf(
        // A MARKER, not a suppression: it records that the obligation is unspent.
        TokenRule("toymaker", "Final Night: No Attack", effect = null, until = Until.FOREVER),
    ),
    firstNight = NightRule(
        gate = fabledAlwaysFires(),
        prompt = "Mark the Demon \"Final Night: No Attack\". Resolve Minion Info and Demon Info, " +
            "even though there are fewer than 7 players.",
        pending = { ctx ->
            unmarkedDemons(ctx.state, ctx.lookup).map { demon ->
                NightEffect.PlaceToken(
                    sourceId = "toymaker",
                    label = TOYMAKER_MARK,
                    on = Ref.Seat(demon.id),
                    until = Until.FOREVER,
                )
            }
        },
        wakeCounts = WakeCount.NONE,
    ),
    otherNight = NightRule(
        gate = WakePredicate { ctx ->
            if (demonAttackCouldEndGame(ctx.state, ctx.lookup)) {
                StepGate.Fire
            } else {
                StepGate.Skip("a Demon attack cannot end the game tonight")
            }
        },
        prompt = "If a Demon attack could end the game tonight and the Demon is still marked " +
            "\"Final Night: No Attack\", the Demon does not act tonight. Do not wake them.",
        pending = { ctx ->
            if (!demonAttackCouldEndGame(ctx.state, ctx.lookup)) {
                emptyList()
            } else {
                seatsHolding(ctx.state, "toymaker", TOYMAKER_MARK).flatMap { demon ->
                    listOf(
                        // No label: the suppression is tonight's consequence of the
                        // marker, not a second physical token. It expires at dawn.
                        NightEffect.PlaceToken(
                            sourceId = "toymaker",
                            label = "",
                            on = Ref.Seat(demon.id),
                            kind = EffectKind.DEMON_CANNOT_KILL,
                            until = Until.DAWN,
                        ),
                        // The obligation is spent by the forced night.
                        NightEffect.RemoveToken("toymaker", TOYMAKER_MARK, Ref.Seat(demon.id)),
                    )
                }
            }
        },
        wakeCounts = WakeCount.NONE,
    ),
    day = referenceNote(
        "toymaker",
        "Toymaker — the Demon may shake their head for NO ATTACK on any night, and must do it at " +
            "least once. Evil players get normal starting info even below 7 players.",
    ),
)

/** The official Toymaker mark: "the no-attack obligation is unspent". */
internal const val TOYMAKER_MARK: String = "Final Night: No Attack"

/** Demon seats that do not yet carry the Toymaker's mark. */
internal fun unmarkedDemons(
    state: GameState,
    lookup: (String) -> Character?,
): List<Player> {
    val marked = seatsHolding(state, "toymaker", TOYMAKER_MARK).map { it.id }.toSet()
    return state.seats.filter { it.characterId?.let(lookup)?.team == Team.DEMON && it.id !in marked }
}

/**
 * "A night when a Demon attack could end the game": a Demon is alive and killing
 * one more resident would leave two or fewer alive. Travellers and Fabled never
 * count towards the two-alive evil win.
 */
internal fun demonAttackCouldEndGame(
    state: GameState,
    lookup: (String) -> Character?,
): Boolean {
    val alive = aliveResidents(state)
    val demonAlive = alive.any { it.characterId?.let(lookup)?.team == Team.DEMON }
    return demonAlive && alive.size - 1 <= 2
}

// ===========================================================================
// Loric
// ===========================================================================

/**
 * Big Wig — "Each nominee chooses a player: until voting, only they may speak &
 * they are mad the nominee is good or they might die."
 *
 * A madness obligation attached to a nomination. The official reminder set is
 * empty (WP5 follow-up), so the madness is a storyteller ruling recorded as a
 * nomination trigger rather than an invented token.
 */
private fun bigWig() = CharacterRule(
    id = "bigwig",
    day = DayRule(
        onNomination = { ctx ->
            val nominee = ctx.nomineeId?.let { ctx.state.player(it) }
            if (!fabledActive(ctx.state, "bigwig") || nominee == null) {
                emptyList()
            } else {
                listOf(
                    NominationTrigger(
                        kind = TriggerKind.CHOICE,
                        sourceId = "bigwig",
                        actorId = nominee.id,
                        headline = "Big Wig: ${nominee.name} chooses their advocate.",
                        detail = "Until voting, only that player may speak, and they are mad that " +
                            "${nominee.name} is good — or they might die.",
                        options = listOf(
                            TriggerOption("apply", "They spoke as required", isDefault = true),
                            TriggerOption("dies", "They broke madness — they die"),
                        ),
                    ),
                )
            }
        },
        briefing = referenceNote(
            "bigwig",
            "Big Wig — each nominee picks one player to speak for them until voting; that " +
                "player is mad that the nominee is good.",
        ).briefing,
    ),
)

/**
 * Bootlegger — "This script has homebrew characters or rules."
 *
 * Derived, not chosen: it belongs in play whenever the script carries custom
 * characters, and the wiki lets it be removed only by switching to a script that
 * has none. Its two `?` tokens are the official pair for marking whatever the
 * house rule needs.
 */
private fun bootlegger() = CharacterRule(
    id = "bootlegger",
    tokens = listOf(
        TokenRule("bootlegger", "?", effect = null, until = Until.FOREVER, copies = 2),
    ),
    day = referenceNote(
        "bootlegger",
        "Bootlegger — announce the homebrew characters and house rules before the bag goes " +
            "round. A homebrew character that looks like a night character but has no night " +
            "position must be given one.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "bootlegger.houseRules",
            characterId = "bootlegger",
            kind = RequirementKind.ACK,
            title = "Bootlegger: what are the house rules?",
            prompt = "What homebrew characters or rules are you using? Announce them before the " +
                "bag goes round.",
            blocking = false,
            satisfied = { state, _ -> fabledEntry(state, "bootlegger")?.note?.isNotBlank() == true },
        ),
    ),
)

/**
 * Gardener — "The Storyteller assigns all players' characters."
 *
 * Removes the randomness, nothing else: every setup rule still applies in full,
 * including the bracket modifiers and the Sentinel's ±1 when both are active.
 */
private fun gardener() = CharacterRule(
    id = "gardener",
    day = referenceNote(
        "gardener",
        "Gardener — you assign every player's character by hand. All the normal setup rules " +
            "still apply: team counts, setup brackets, and the Sentinel's range if it is active.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "gardener.mode",
            characterId = "gardener",
            kind = RequirementKind.ACK,
            title = "Gardener: assign every character",
            prompt = "Assign all players' characters by hand. The team targets update as you " +
                "place tokens.",
            blocking = false,
            satisfied = { state, _ ->
                state.seats.isNotEmpty() && state.seats.all { it.characterId != null }
            },
        ),
    ),
)

/**
 * God of Ug — "One Ug hat. When wear Ug hat, must speak one sound at a time but
 * vote twice. If fail, pass Ug hat."
 *
 * The double vote is `Nomination.extraVotes` (lead D44), not an `EffectKind`;
 * the hat itself is the one official token and moves by hand.
 */
private fun godOfUg() = CharacterRule(
    id = "godofug",
    tokens = listOf(
        TokenRule("godofug", "Hat", effect = null, until = Until.FOREVER),
    ),
    day = referenceNote(
        "godofug",
        "God of Ug — whoever wears the Ug hat may speak only one sound at a time, and votes " +
            "twice. If they fail, the hat passes to another player.",
    ),
)

/**
 * Hindu — "The first 4 players to die are immediately reincarnated as Travellers
 * of the same alignment."
 *
 * A death trigger with a running count, not a protection: they DIE, and then
 * come back in a Traveller seat of the same alignment. The character choice is
 * the storyteller's, so the trigger asks rather than deciding.
 */
private fun hindu() = CharacterRule(
    id = "hindu",
    onDeath = listOf(
        DeathTrigger(
            gate = { state, event, _ ->
                fabledActive(state, "hindu") &&
                    state.player(event.playerId)?.isTraveller == false &&
                    reincarnationsSoFar(state) < 4
            },
            produce = { state, event, _ ->
                val victim = state.player(event.playerId)?.name ?: "They"
                TriggerResult(
                    prompts = listOf(
                        Prompt(
                            id = 0,
                            at = BriefingSlot.NOW,
                            kind = PromptKind.CHOOSE_CHARACTER,
                            sourceId = "hindu",
                            subjectPlayerId = event.playerId,
                            title = "Hindu: $victim is reincarnated as a Traveller.",
                            detail = "Pick a Traveller of the same alignment and hand the token " +
                                "over now. This is reincarnation number " +
                                "${reincarnationsSoFar(state) + 1} of 4.",
                        ),
                    ),
                )
            },
        ),
    ),
    day = referenceNote(
        "hindu",
        "Hindu — the first 4 players to die come straight back as Travellers of the same " +
            "alignment.",
    ),
)

/** How many reincarnations the Hindu has already granted this game. */
internal fun reincarnationsSoFar(state: GameState): Int =
    state.prompts.count { Character.normalizeId(it.sourceId) == "hindu" }

/**
 * Knaves — "There are 2 Storytellers: one lies & one tells the truth. Once per
 * game, at dusk, they might switch."
 *
 * Entirely a table procedure: no token, no effect, no engine hook.
 */
private fun knaves() = CharacterRule(
    id = "knaves",
    day = referenceNote(
        "knaves",
        "Knaves — two storytellers, one lying and one truthful. Once per game, at dusk, you may " +
            "swap which of you is which.",
    ),
)

/**
 * Pope — "There are duplicate good characters in play. They might also be bluffs."
 *
 * A bag rule: the "one of each character" invariant is lifted for good
 * characters, and a duplicated character may still be handed out as a bluff.
 * `Setup.DUPLICABLE` is a fixed id set today, so the relaxation is filed to WP4.
 */
private fun pope() = CharacterRule(
    id = "pope",
    day = referenceNote(
        "pope",
        "Pope — good characters may appear more than once in the bag, and a character that IS in " +
            "play may still be given out as a bluff.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "pope.duplicates",
            characterId = "pope",
            kind = RequirementKind.ACK,
            title = "Pope: duplicate good characters",
            prompt = "Put duplicate good characters in the bag. They may also be used as bluffs.",
            blocking = false,
            satisfied = { state, _ -> fabledActive(state, "pope") },
        ),
    ),
)

/**
 * Storm Catcher — "Name a good character. If in play, they can only die by
 * execution, but evil players learn which player it is."
 *
 * The protection is WP1's: `Standing.emitPositional` reads
 * `FabledEntry.config["stormcatcher.favouredCharacterId"]` and emits a derived
 * `ONLY_EXECUTION_KILLS` effect labelled STORMCAUGHT on whoever holds that
 * character. This row AGREES with it and adds nothing to the kill funnel — it
 * owns the token's lifetime (never expires) and the first-night procedure.
 *
 * The block is not just the Demon: `ONLY_EXECUTION_KILLS` blocks every cause
 * except EXECUTION, which includes EXILE — a Traveller can hold a good
 * character, so it is reachable.
 *
 * Naming a character that is NOT in play is a feature, not a mistake: it hands
 * evil a guaranteed-safe bluff. Nothing is marked in that case, and the evil
 * team is shown the "THESE CHARACTERS ARE NOT IN PLAY" token instead.
 */
private fun stormCatcher() = CharacterRule(
    id = "stormcatcher",
    tokens = listOf(
        TokenRule(
            "stormcatcher", "Stormcaught", EffectKind.ONLY_EXECUTION_KILLS, Until.FOREVER,
            protects = true,
        ),
    ),
    firstNight = NightRule(
        gate = fabledAlwaysFires(),
        prompt = "Announce which character is stormcaught. If that character is in play, mark " +
            "that player STORMCAUGHT and wake each evil player in turn: show the character " +
            "token, then the marked player. If not in play, show each evil player the \"THESE " +
            "CHARACTERS ARE NOT IN PLAY\" token and the character token.",
        wakeCounts = WakeCount.NONE,
    ),
    day = referenceNote(
        "stormcatcher",
        "Storm Catcher — the stormcaught player can ONLY die by execution. Every other death " +
            "fails: the Demon, an Assassin, a Gossip, a Godfather, a Witch, a storyteller kill, " +
            "and an exile.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "stormcatcher.favouredCharacterId",
            characterId = "stormcatcher",
            kind = RequirementKind.SHOWN_TOKEN,
            title = "Storm Catcher: name a good character",
            prompt = "Name a good character and announce it publicly. It does not have to be in " +
                "play — naming one that is not hands evil a guaranteed-safe bluff.",
            problem = "Name a good character before the first night",
            satisfied = { state, _ ->
                !fabledConfig(state, "stormcatcher", STORM_CATCHER_CHARACTER).isNullOrBlank()
            },
        ),
    ),
)

/** `FabledEntry.config` key for the Storm Catcher's named good character (lead D45). */
internal const val STORM_CATCHER_CHARACTER: String = "stormcatcher.favouredCharacterId"

/**
 * Tor — "Players don't know their character or alignment. They learn them when
 * they die."
 *
 * Both information steps are skipped on the first night — nobody knows who they
 * are, so nobody can be shown their team — and each night a dead player is shown
 * the YOU ARE token, their character, and a thumb signal for their alignment.
 */
private fun tor() = CharacterRule(
    id = "tor",
    firstNight = NightRule(
        gate = fabledAlwaysFires(),
        prompt = "Skip Minion Info and Demon Info. Nobody is shown their own character token.",
        wakeCounts = WakeCount.NONE,
    ),
    otherNight = NightRule(
        gate = WakePredicate { ctx ->
            if (ctx.diedTonight.isEmpty()) {
                StepGate.Skip("nobody died tonight")
            } else {
                StepGate.Fire
            }
        },
        prompt = "For each player who died tonight: show the \"YOU ARE\" token, their character " +
            "token, and a thumb signal for their alignment.",
        wakeCounts = WakeCount.NONE,
    ),
    day = referenceNote(
        "tor",
        "Tor — players do not know their own character or alignment until they die.",
    ),
    setup = listOf(
        SetupRequirement(
            id = "tor.noReveal",
            characterId = "tor",
            kind = RequirementKind.ACK,
            title = "Tor: do not hand out character tokens",
            prompt = "Deal characters without showing anyone their token, and skip Minion Info " +
                "and Demon Info on the first night.",
            blocking = false,
            satisfied = { state, _ -> fabledActive(state, "tor") },
        ),
    ),
)

/**
 * Ventriloquist — "If a player is mad as a fresh character during their
 * nomination, they might not die if executed today."
 *
 * A "might", so the execution hook asks; the MAD token is WP1's `Until.DUSK`
 * madness token and this row keeps that lifetime.
 */
private fun ventriloquist() = CharacterRule(
    id = "ventriloquist",
    tokens = listOf(
        TokenRule("ventriloquist", "Mad", EffectKind.MAD, Until.DUSK),
    ),
    day = DayRule(
        onExecution = { ctx ->
            val executed = ctx.record.playerId?.let { ctx.state.player(it) }
            val mad = executed != null &&
                Status.live(ctx.state, ctx.lookup, executed.id, EffectKind.MAD)
                    .any { Character.normalizeId(it.sourceCharacterId) == "ventriloquist" }
            if (!fabledActive(ctx.state, "ventriloquist") || executed == null || !mad ||
                ctx.record.outcome != ExecutionOutcome.DIED
            ) {
                emptyList()
            } else {
                listOf(
                    ExecutionConsequence(
                        sourceId = "ventriloquist",
                        headline = "Ventriloquist: ${executed.name} was mad as a fresh character " +
                            "when they nominated.",
                        detail = "They MIGHT not die. Your call.",
                        options = listOf(
                            TriggerOption("dies", "They die", isDefault = true),
                            TriggerOption("lives", "They survive the execution"),
                        ),
                    ),
                )
            }
        },
        briefing = referenceNote(
            "ventriloquist",
            "Ventriloquist — a player who plays a fresh character during their own nomination " +
                "might survive today's execution.",
        ).briefing,
    ),
)

/**
 * Zenomancer — "One or more players each have a goal. When achieved, that player
 * learns a piece of true info."
 *
 * Three GOAL tokens, one per player with a goal; the goals themselves and the
 * information paid out are storyteller text.
 */
private fun zenomancer() = CharacterRule(
    id = "zenomancer",
    tokens = listOf(
        TokenRule("zenomancer", "Goal", effect = null, until = Until.FOREVER, copies = 3),
    ),
    day = referenceNote(
        "zenomancer",
        "Zenomancer — each player marked GOAL has a secret goal. When one achieves it, tell " +
            "them a piece of TRUE information and take the token back.",
    ),
)
