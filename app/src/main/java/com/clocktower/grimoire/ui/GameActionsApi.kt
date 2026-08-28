package com.clocktower.grimoire.ui

import com.clocktower.engine.Alignment
import com.clocktower.engine.Bluffs
import com.clocktower.engine.Briefing
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Briefings
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Deaths
import com.clocktower.engine.Decisions
import com.clocktower.engine.Effects
import com.clocktower.engine.Execution
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.ExecutionRecord
import com.clocktower.engine.ExecutionVia
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Identity
import com.clocktower.engine.KillCause
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.Ledger
import com.clocktower.engine.LedgerEntry
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.InfoCalc
import com.clocktower.engine.InfoResult
import com.clocktower.engine.NightInput
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationCheck
import com.clocktower.engine.NominationResult
import com.clocktower.engine.NominationTrigger
import com.clocktower.engine.Phase
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Player
import com.clocktower.engine.Prompt
import com.clocktower.engine.Prompts
import com.clocktower.engine.RenderedToken
import com.clocktower.engine.Script
import com.clocktower.engine.SeatNote
import com.clocktower.engine.Seats
import com.clocktower.engine.Selection
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.Tokens
import com.clocktower.engine.Verdict
import com.clocktower.engine.VoteRules
import com.clocktower.engine.StepKey
import kotlin.random.Random

/**
 * Every engine verb the UI can call, wired ONCE for both platforms.
 *
 * `GameViewModel` (Android) and its web twin implement this and provide only
 * [update], [characterById] and [gameData]; every wrapper below is a default
 * method. A new engine verb is added HERE and nowhere else — never again in
 * two files (lead D26, ARCHITECTURE §3.3).
 *
 * **Ownership:** WP0 created this file with the existing verbs. It is
 * append-only inside the marked per-WP blocks; never reorder or reformat
 * another package's block.
 */
interface GameActionsApi {

    val gameData: GameData

    fun update(transform: (GameState) -> GameState)

    fun characterById(id: String?): Character?

    /** Character lookup in the shape every engine function takes. */
    val lookup: (String) -> Character? get() = { id -> characterById(id) }

    // ---- WP0: existing verbs, moved verbatim ----

    fun addSeat(name: String) = update { Seats.addSeat(it, name) }

    fun removeSeat(playerId: Long) = update { Seats.removeSeat(it, playerId) }

    fun moveSeat(playerId: Long, delta: Int) = update { Seats.moveSeat(it, playerId, delta) }

    fun rename(playerId: Long, name: String) = update { Seats.rename(it, playerId, name) }

    fun assign(playerId: Long, characterId: String?, isTraveller: Boolean = false) =
        update { Seats.assignCharacter(it, playerId, characterId, isTraveller) }

    fun setShownCharacter(playerId: Long, characterId: String?) =
        update { Seats.setShownCharacter(it, playerId, characterId) }

    fun flipAlignment(playerId: Long) = update { Seats.flipAlignment(it, playerId, lookup) }

    fun setAlignment(playerId: Long, alignment: Alignment?) =
        update { Seats.setAlignment(it, playerId, alignment) }

    fun setNote(playerId: Long, note: String) = update { Seats.setNote(it, playerId, note) }

    /** Legacy direct kill. WP1 replaces every call site with [attemptDeath]. */
    fun kill(playerId: Long, cause: DeathCause) =
        update { Deaths.kill(it, playerId, cause, lookup) }

    fun revive(playerId: Long) = update { Deaths.revive(it, playerId) }

    fun resurrect(playerId: Long) = update { Deaths.resurrect(it, lookup, playerId) }

    fun toggleGhostVote(playerId: Long) = update { Deaths.toggleGhostVote(it, playerId) }

    fun addReminder(playerId: Long, reminder: PlacedReminder) =
        update { Effects.addReminder(it, playerId, reminder) }

    fun removeReminder(playerId: Long, index: Int) =
        update { Effects.removeReminder(it, playerId, index) }

    fun setBluffs(ids: List<String>) = update { Bluffs.setDemonBluffs(it, ids) }

    fun setFabled(ids: List<String>) = update { Bluffs.setFabled(it, ids) }

    fun advancePhase() = update { Phases.advancePhase(it, lookup) }

    fun toggleNightStep(stepId: String) = update { NightPlan.toggleDone(it, stepId) }

    fun recordNomination(nomination: Nomination) =
        update { DayRules.recordNomination(it, nomination) }

    fun setStorytellerNotes(notes: String) = update { it.copy(storytellerNotes = notes) }

    fun starPass(demonPlayerId: Long, heirPlayerId: Long) = update {
        Identity.starPass(
            state = it,
            lookup = lookup,
            demonPlayerId = demonPlayerId,
            heirPlayerId = heirPlayerId,
        )
    }

    fun snakeCharmerSwap(charmerId: Long, demonPlayerId: Long) =
        update { Identity.snakeCharmerSwap(it, charmerId, demonPlayerId) }

    // ---- WP1: effects, status, deaths ----

    // ---- WP2: night ----

    /**
     * Tonight's sheet. Pure and cheap: rebuild it on every recomposition rather
     * than caching, so an insertion appears the moment state changes (I6).
     */
    fun nightPlan(state: GameState): NightPlan = NightPlan.build(state, lookup)

    /** Applies what the storyteller entered on one night step, and ticks it. */
    fun resolveNightStep(key: StepKey, input: NightInput) =
        update { NightPlan.resolve(it, lookup, key, input) }

    /** Ticks or un-ticks one row by its [StepKey.token]. */
    fun toggleNightStep(key: StepKey) = update { NightPlan.toggleDone(it, key.token) }

    /** The information one step computes, typed, with the lies it may be told with. */
    fun nightInfo(
        state: GameState,
        characterId: String,
        holderId: Long?,
        targets: List<Long> = emptyList(),
    ): InfoResult? = InfoCalc.compute(state, lookup, characterId, holderId, targets)

    // ---- WP3: day, ledger, execution ----

    /**
     * Records anything said in public. Works with NOTHING in play: the default
     * [sourceId] is a plain claim, and [text] alone is a complete entry.
     */
    fun recordStatement(
        speakerId: Long?,
        text: String,
        sourceId: String = Ledger.Sources.CLAIM,
        targetIds: List<Long> = emptyList(),
        characterIds: List<String> = emptyList(),
        genuine: Boolean = true,
    ) = update {
        Ledger.statement(
            state = it,
            speakerId = speakerId,
            sourceId = sourceId,
            text = text,
            targetIds = targetIds,
            characterIds = characterIds,
            genuine = genuine,
        )
    }

    /** A private day-time conversation (Savant, Artist, Fisherman, Amnesiac). */
    fun recordPrivate(playerId: Long, sourceId: String, text: String, shown: String) =
        update { Ledger.private(it, playerId, sourceId, text, shown) }

    /** A night choice — this is what survives the token sweep (lead D3). */
    fun recordChoice(
        sourceId: String,
        actorId: Long?,
        targetIds: List<Long>,
        characterIds: List<String> = emptyList(),
        impaired: Boolean = false,
        byStoryteller: Boolean = false,
    ) = update {
        Ledger.choice(it, sourceId, actorId, targetIds, characterIds, impaired, byStoryteller)
    }

    /** Information actually delivered to a player — true or false. */
    fun recordTold(playerId: Long, sourceId: String, shown: String, impaired: Boolean = false) =
        update { Ledger.told(it, playerId, sourceId, shown, impaired) }

    /** A storyteller decision that must stay consistent (misregistration, madness). */
    fun recordRuling(sourceId: String, playerId: Long?, text: String) =
        update { Ledger.ruling(it, sourceId, playerId, text) }

    /** Free text with no other structure. */
    fun recordNote(text: String, playerId: Long? = null) = update { Ledger.note(it, text, playerId) }

    /** Queues a sentence the storyteller owes the table. */
    fun announce(text: String, sourceId: String = Ledger.Sources.STORYTELLER) =
        update { Ledger.announce(it, text, sourceId) }

    fun markAnnounced(entryId: Long) = update { Ledger.markAnnounced(it, entryId) }

    fun setLedgerVerdict(entryId: Long, verdict: Verdict) =
        update { Ledger.setVerdict(it, entryId, verdict) }

    /** Marks a ledger entry consumed by a later step (a resolved Gossip statement). */
    fun resolveLedgerEntry(entryId: Long) = update { Ledger.resolve(it, entryId) }

    fun editLedgerEntry(entryId: Long, transform: (LedgerEntry) -> LedgerEntry) =
        update { Ledger.edit(it, entryId, transform) }

    fun deleteLedgerEntry(entryId: Long) = update { Ledger.delete(it, entryId) }

    /** Records a nomination, freezing its vote rules and registration snapshot. */
    fun nominate(nomination: Nomination, force: Boolean = false) =
        update { DayRules.record(it, lookup, nomination, force) }

    /** Applies a nomination trigger the storyteller accepted. */
    fun applyNominationTrigger(trigger: NominationTrigger, optionId: String) =
        update { DayRules.applyTrigger(it, lookup, trigger, optionId) }

    /** THE execution funnel. Every Execute button in the app calls this. */
    fun execute(
        playerId: Long,
        nominatorId: Long? = null,
        via: ExecutionVia = ExecutionVia.VOTE,
        nominationIndex: Int? = null,
        force: Boolean = false,
        outcome: ExecutionOutcome? = null,
        preventedBy: String = "",
        optionId: String = "",
    ) = update {
        Execution.execute(
            state = it,
            lookup = lookup,
            playerId = playerId,
            nominatorId = nominatorId,
            via = via,
            nominationIndex = nominationIndex,
            force = force,
            outcome = outcome,
            preventedBy = preventedBy,
            optionId = optionId,
        )
    }

    /** "Executed — but they don't die." Still the day's execution. */
    fun executeButSurvives(playerId: Long, preventedBy: String = "", nominatorId: Long? = null) =
        execute(
            playerId = playerId,
            nominatorId = nominatorId,
            outcome = ExecutionOutcome.SURVIVED,
            preventedBy = preventedBy,
        )

    /** Closes the day with no execution — what the Mayor, Vortox and Zombuul need. */
    fun noExecution() = update { Execution.noExecution(it) }

    /** Exile a Traveller. Never an execution; never modified by an ability. */
    fun exile(playerId: Long) = update { Execution.exile(it, lookup, playerId) }

    // ---- WP4: setup, identity, bluffs ----

    /** THE single funnel for every character change (lead D17). */
    fun changeCharacter(
        playerId: Long,
        newCharacterId: String?,
        reason: ChangeReason,
        newEvil: Boolean? = null,
        shownCharacterId: String? = null,
        suppressReveal: Boolean = false,
    ) = update {
        Identity.changeCharacter(
            state = it,
            lookup = lookup,
            playerId = playerId,
            newCharacterId = newCharacterId,
            reason = reason,
            newEvil = newEvil,
            shownCharacterId = shownCharacterId,
            suppressReveal = suppressReveal,
        )
    }

    fun swapCharacters(a: Long, b: Long) = update { Identity.swapCharacters(it, lookup, a, b) }

    /** The Lunatic draws the Demon's token and the Demon draws the Lunatic's. */
    fun applyLunaticTokenSwap() = update { Identity.applyLunaticTokenSwap(it, lookup) }

    /** Restores a seat's true token — the Demon's, at DEMON_INFO. */
    fun revealTrueIdentity(playerId: Long) = update { Identity.revealTrueIdentity(it, playerId) }

    fun markRevealed(playerId: Long) = update { Identity.markRevealed(it, playerId) }

    fun markRerunDone(playerId: Long) = update { Identity.markRerunDone(it, playerId) }

    /** Deals the bag and places the identity tokens the characters declare. */
    fun deal(bagIds: List<String>, seed: Long) =
        update { Seats.deal(it, bagIds, Random(seed), lookup) }

    /** Stores one bluff set under its [BluffRequirement.key]. */
    fun setBluffSet(key: String, ids: List<String>) = update { Bluffs.set(it, key, ids) }

    fun clearBluffSet(key: String) = update { Bluffs.clear(it, key) }

    /** Answers one setup-checklist row through its own `apply`. */
    fun applySetupRequirement(requirement: SetupRequirement, selection: Selection) =
        update { requirement.apply(it, selection) }

    fun setDecision(key: String, value: String) = update { Decisions.set(it, key, value) }

    fun clearDecision(key: String) = update { Decisions.clear(it, key) }

    // ---- WP6: prompts and briefings ----

    /**
     * The briefing for one slot, derived fresh from prompts + effects + ledger
     * + deaths. Pure and cheap — never cache it; `state.lastDawn` and
     * `state.lastDusk` are the frozen snapshots, taken before the token sweep.
     */
    fun briefing(state: GameState, slot: BriefingSlot): Briefing =
        Briefings.at(state, lookup, slot)

    /** Everything that constrains today — the Day tab's morning card. */
    fun dayBriefing(state: GameState): Briefing =
        Briefings.at(state, lookup, BriefingSlot.DAY_START)

    /**
     * Acts on one [BriefingItem]'s `actionId` where the engine owns the
     * follow-through: ticking off a sentence that has been said, and retiring
     * an obligation. Navigation actions ("open-seat:7") stay with the screen.
     *
     * Returns true when the item was consumed here.
     */
    fun resolveBriefingItem(item: BriefingItem): Boolean = when {
        item.actionId.startsWith(Briefings.ACTION_MARK_ANNOUNCED) -> {
            item.ledgerId?.let { markAnnounced(it) }
            item.ledgerId != null
        }

        item.actionId.startsWith(Briefings.ACTION_RESOLVE_PROMPT) -> {
            item.promptId?.let { resolvePrompt(it) }
            item.promptId != null
        }

        else -> false
    }

    /** Unresolved obligations that come due at [slot], oldest first. */
    fun promptsDue(state: GameState, slot: BriefingSlot): List<Prompt> = Prompts.due(state, slot)

    /** Retires one obligation as done, keeping it for the log and undo. */
    fun resolvePrompt(promptId: Long) = update { Prompts.resolve(it, promptId) }

    /**
     * Answers a CHOOSE_PLAYER obligation with the seat the storyteller picked —
     * the Imp's star-pass heir. One update: the character change the prompt
     * names, the ledger row, and the obligation retired.
     */
    fun answerPromptWithPlayer(promptId: Long, playerId: Long) =
        update { Prompts.answerWithPlayer(it, lookup, promptId, playerId) }

    /** Drops an obligation entirely — the storyteller ruled it never applied. */
    fun dismissPrompt(promptId: Long) = update { Prompts.dismiss(it, promptId) }

    /** Queues an obligation the storyteller owes. */
    fun queuePrompt(prompt: Prompt) = update { Prompts.queue(it, prompt) }

    // ---- WP8: night screen ----

    /**
     * The night screen's dim level: 0 = full, 1 = 55 %, 2 = 25 %.
     *
     * It lives in `GameState`, not in `rememberSaveable`, so the room stays dark
     * across a tab switch, a process death and a reload of the PWA
     * (ux/night-screen §H, defect #21).
     */
    fun setDimLevel(level: Int) = update { it.copy(dimLevel = level.coerceIn(0, 2)) }

    /**
     * Ticks one night row WITHOUT toggling it back off.
     *
     * [toggleNightStep] is the storyteller correcting themselves; this is the
     * screen recording that a step is finished — after the shared `KillSheet`
     * applied a death the row was about to apply, or after a deliberate skip.
     */
    fun markNightStepDone(key: StepKey) = update {
        if (key.token in it.nightStepsDone) it else NightPlan.toggleDone(it, key.token)
    }

    /**
     * Records a card that was actually held up to a player — true or false.
     *
     * This is the fact the log, the next night's step and the morning briefing
     * all need and that the app used to keep only in the storyteller's head
     * (ux/night-screen defect #10). A card with no single recipient (a sheet
     * held out to the table) is recorded as a plain note.
     */
    fun recordShown(playerId: Long?, sourceId: String, shown: String, truthful: Boolean = true) =
        update {
            if (playerId == null) {
                Ledger.note(it, "Shown: $shown")
            } else {
                Ledger.told(it, playerId, sourceId, shown, impaired = !truthful)
            }
        }

    /**
     * A Traveller joins mid-game: seat, character, alignment and the
     * announcement in ONE update, so undo puts the table back in one step
     * (grimoire-and-seats §10, lead D25/D62).
     *
     * Alignment is always explicit — a Traveller's team is a storyteller
     * decision, never a consequence of the character.
     */
    fun joinTraveller(
        name: String,
        afterPlayerId: Long?,
        characterId: String?,
        evil: Boolean,
        announce: String = "",
    ) = update { state ->
        val seated = Seats.addSeat(state, name, afterPlayerId)
        // `Seats.addSeat` stamps the new seat with max(id) + 1, so the highest
        // id in the new state IS the seat that was just added.
        val id = seated.players.maxOfOrNull { it.id } ?: return@update state
        val assigned = Seats.assignCharacter(seated, id, characterId, isTraveller = true)
        val aligned = Seats.setAlignment(
            assigned,
            id,
            if (evil) Alignment.EVIL else Alignment.GOOD,
        )
        if (announce.isBlank()) aligned else Ledger.announce(aligned, announce)
    }

    // ---- WP9: day screen ----

    /**
     * The nomination pre-flight, recomputed on every chip tap so the ring's
     * `NominationCheck` card is live (§3.2). Pure — nothing is written until
     * the storyteller locks the vote in.
     */
    fun nominationCheck(state: GameState, nominatorId: Long?, nomineeId: Long?): NominationCheck =
        DayRules.checkNomination(state, lookup, nominatorId, nomineeId)

    /**
     * The NOMINATION briefing for the pair the storyteller has TAPPED.
     *
     * `Briefings.at(..., NOMINATION)` defaults the pair to the last nomination
     * already recorded today, which cannot be right for a "the 1st time you are
     * nominated" rule — recording the nomination is what spends it. The day
     * screen must call this per tap instead (WP6 merger note).
     */
    fun nominationBriefing(state: GameState, nominatorId: Long?, nomineeId: Long?): Briefing =
        Briefings.forNomination(state, lookup, nominatorId, nomineeId)

    /** The frozen-at-record vote rules: who may vote, the threshold, the weights. */
    fun voteRules(state: GameState, isExile: Boolean): VoteRules =
        DayRules.voteRules(state, lookup, isExile)

    /** The weighted tally for these raw hands, with the Butler exception applied. */
    fun voteTally(state: GameState, voterIds: List<Long>, isExile: Boolean): Int =
        DayRules.tally(state, lookup, voterIds, isExile)

    /** A sober living Organ Grinder: the whole Day tab goes secret. */
    fun secretVoting(state: GameState): Boolean = DayRules.secretVoting(state, lookup)

    /**
     * What the execution funnel WOULD decide, from the same fifteen-step table
     * it applies — so the dusk card can show the Devil's Advocate before the
     * button is pressed, and can ask a `KillOutcome.Choice` first (D24).
     */
    fun executionPreview(state: GameState, playerId: Long): KillOutcome =
        Deaths.killOutcome(state, lookup, playerId, KillCause(DeathCause.EXECUTION))

    /**
     * The same preview for an exile, which is a different cause entirely: no
     * ability modifies it, it is never the day's execution, and no
     * `ExecutionRecord` is written (lead D25/D58).
     */
    fun exilePreview(state: GameState, playerId: Long): KillOutcome = Deaths.killOutcome(
        state,
        lookup,
        playerId,
        KillCause(DeathCause.EXILE, sourceCharacterId = Ledger.Sources.STORYTELLER),
    )

    /** Today's execution record, including a declared "no execution" (lead D30). */
    fun executionToday(state: GameState): ExecutionRecord? = DayRules.executionToday(state)

    /** What the storyteller must confirm now that the execution has resolved. */
    fun executionConsequences(
        state: GameState,
        record: ExecutionRecord,
    ): List<ExecutionConsequence> = Execution.consequences(state, lookup, record)

    /** True when the day is closed: an execution happened, or none will. */
    fun nominationsClosed(state: GameState): Boolean = DayRules.nominationsClosed(state, lookup)

    /**
     * The zero-typing claim: "Ana claims to be the Empath", recorded as a
     * STATEMENT with the character attached so a later ruling can read it.
     */
    fun recordClaim(speakerId: Long, characterId: String, text: String) = recordStatement(
        speakerId = speakerId,
        text = text,
        sourceId = Ledger.Sources.CLAIM,
        characterIds = listOf(characterId),
    )

    /**
     * Withdraws a recorded nomination (ux/day-screen findings 16/17): the row
     * becomes `WITHDRAWN`, so it stops counting towards the block and the
     * highest tally, and any ghost vote it spent is handed back. One update, so
     * one undo reverses the whole thing.
     */
    fun withdrawNomination(index: Int) = update { state ->
        val nomination = state.nominations.getOrNull(index) ?: return@update state
        if (nomination.result == NominationResult.WITHDRAWN) return@update state
        var next = state.copy(
            nominations = state.nominations.mapIndexed { i, n ->
                if (i == index) n.copy(result = NominationResult.WITHDRAWN, votes = 0) else n
            },
        )
        if (!nomination.isExile && nomination.voteRules?.spendsGhostVotes != false) {
            for (voter in nomination.voterIds) {
                val seat = next.player(voter) ?: continue
                if (!seat.alive && seat.ghostVoteUsed) {
                    next = next.updatePlayer(voter) { it.copy(ghostVoteUsed = false) }
                }
            }
        }
        next
    }

    // ---- WP10: grimoire, seat sheet, kill sheet ----

    /**
     * THE kill funnel, for every kill site the UI owns (lead D24, friction §1).
     * `KillSheet` renders `Deaths.killOutcome` first and then calls this with
     * the same [cause], so preview and application can never disagree.
     *
     * [optionId] answers a `KillOutcome.Choice` — pass "" the first time, then
     * one of `Deaths.OPTION_DIES` / `OPTION_LIVES` / `OPTION_REDIRECT`.
     */
    fun attemptDeath(
        targetId: Long,
        cause: KillCause,
        optionId: String = "",
        /** A storyteller's own reason, recorded with the death as ONE undo step. */
        ruling: String = "",
    ) = update {
        val after = Deaths.attempt(it, lookup, targetId, cause, optionId).state
        if (ruling.isBlank()) after else withRuling(after, targetId, "st", ruling.trim())
    }

    /** Removes one effect by id — the rule behind a rendered token. */
    fun removeEffect(effectId: Long) = update { Effects.remove(it, effectId) }

    /**
     * Turns a token upside-down instead of removing it (wiki, Abilities): the
     * effect stops applying but survives until the storyteller restores it.
     */
    fun suspendEffect(effectId: Long, suspended: Boolean) =
        update { Effects.suspend(it, effectId, suspended) }

    /**
     * Removes whatever backs a rendered token: the effect if it has one,
     * otherwise the matching storyteller free token on that seat.
     */
    fun removeRenderedToken(playerId: Long, token: RenderedToken) = update { state ->
        val effectId = token.effectId
        if (effectId != null) {
            Effects.remove(state, effectId)
        } else {
            val key = Tokens.key(token.sourceId, token.label)
            val index = state.player(playerId)?.reminders?.indexOfFirst { Tokens.key(it) == key } ?: -1
            if (index < 0) state else Effects.removeReminder(state, playerId, index)
        }
    }

    /**
     * ONE placement semantic for the seat sheet, the token peek and the night
     * tray (grimoire-and-seats §7, P0-6): a token is placed respecting the
     * number of physical copies the character owns. With one copy it MOVES;
     * with N it accumulates to N and then displaces the oldest.
     *
     * [copies] defaults to the count in `characters.json`, where an N-copy
     * token is listed N times.
     */
    fun placeToken(playerId: Long, reminder: PlacedReminder, copies: Int = -1) = update { state ->
        val fixed = reminder.copy(
            sourceId = reminder.sourceId.ifBlank { Tokens.STORYTELLER_SOURCE },
            placedCycle = if (reminder.placedCycle > 0) reminder.placedCycle else state.cycle,
        )
        val limit = when {
            copies > 0 -> copies
            else -> characterById(fixed.sourceId)
                ?.allReminders
                ?.count { it.trim().equals(fixed.label.trim(), ignoreCase = true) }
                ?.coerceAtLeast(1)
                ?: Int.MAX_VALUE
        }
        if (limit == Int.MAX_VALUE) {
            Effects.addReminder(state, playerId, fixed)
        } else {
            val key = Tokens.key(fixed)
            // Everywhere this token currently sits, OLDEST first: earliest
            // cycle, then seat order, then placement order within the seat.
            val placed = state.players.flatMapIndexed { seat: Int, p: Player ->
                p.reminders.mapIndexedNotNull { i, r ->
                    if (Tokens.key(r) == key) Triple(p.id, i, r.placedCycle * 10_000 + seat * 100 + i) else null
                }
            }.sortedBy { it.third }
            val displace = (placed.size + 1 - limit).coerceAtLeast(0)
            val doomed = placed.take(displace).map { it.first to it.second }.toSet()
            val cleared = state.copy(
                players = state.players.map { p ->
                    p.copy(
                        reminders = p.reminders.filterIndexed { i, _ -> (p.id to i) !in doomed },
                    )
                },
            )
            Effects.addReminder(cleared, playerId, fixed)
        }
    }

    /** How many copies of [label] the character [sourceId] physically owns. */
    fun tokenCopies(sourceId: String, label: String): Int =
        characterById(sourceId)
            ?.allReminders
            ?.count { it.trim().equals(label.trim(), ignoreCase = true) }
            ?.coerceAtLeast(1)
            ?: 1

    /** Moves a token from one seat to another in a single undo step. */
    fun moveToken(fromPlayerId: Long, toPlayerId: Long, token: RenderedToken) = update { state ->
        val key = Tokens.key(token.sourceId, token.label)
        val index = state.player(fromPlayerId)?.reminders?.indexOfFirst { Tokens.key(it) == key } ?: -1
        val effectId = token.effectId
        when {
            effectId != null ->
                state.copy(
                    effects = state.effects.map {
                        if (it.id == effectId) it.copy(targetId = toPlayerId) else it
                    },
                )
            index >= 0 -> {
                val reminder = state.player(fromPlayerId)!!.reminders[index]
                Effects.addReminder(
                    Effects.removeReminder(state, fromPlayerId, index),
                    toPlayerId,
                    reminder,
                )
            }
            else -> state
        }
    }

    /**
     * APPENDS a dated seat note. `Seats.setNote` replaces the whole list, which
     * is how the setup prompts used to destroy whatever the storyteller had
     * typed (grimoire-and-seats P1-12); every WP10 surface uses this instead.
     */
    fun appendNote(playerId: Long, text: String) = update { state ->
        if (text.isBlank()) {
            state
        } else {
            state.updatePlayer(playerId) {
                it.copy(notes = it.notes + SeatNote(state.cycle, state.phase, text.trim()))
            }
        }
    }

    /** Rewrites one note in place, keeping its cycle/phase stamp. */
    fun editNote(playerId: Long, index: Int, text: String) = update { state ->
        state.updatePlayer(playerId) { p ->
            if (index !in p.notes.indices) {
                p
            } else if (text.isBlank()) {
                p.copy(notes = p.notes.filterIndexed { i, _ -> i != index })
            } else {
                p.copy(notes = p.notes.mapIndexed { i, n -> if (i == index) n.copy(text = text.trim()) else n })
            }
        }
    }

    /**
     * Records a storyteller ruling the grimoire needs to remember — "the Monk
     * saved Dana", "I ruled the Recluse registered as the Imp".
     *
     * HOOK FOR WP3: `Ledger.ruling(...)` is `TODO("WP3")` at this base, so the
     * entry is built here exactly as `Deaths.recordPrevented` builds its own.
     * When WP3 lands, this body becomes
     * `Ledger.ruling(it, sourceId, playerId, text)` and nothing else changes.
     */
    fun recordRuling(playerId: Long?, sourceId: String, text: String) =
        update { withRuling(it, playerId, sourceId, text) }

    private fun withRuling(
        state: GameState,
        playerId: Long?,
        sourceId: String,
        text: String,
    ): GameState {
        val id = state.nextLedgerId
        return state.copy(
            ledger = state.ledger + LedgerEntry(
                id = id,
                cycle = state.cycle,
                atNight = state.phase != Phase.DAY,
                kind = LedgerKind.RULING,
                sourceId = sourceId,
                actorId = playerId,
                text = text,
            ),
            nextLedgerId = id + 1,
        )
    }

    // ---- WP11: setup, hand-out, home, PWA shell ----

    /**
     * Marks this seat's token as handed over. Progress is STATE, not local
     * composition, so re-opening hand-out mode resumes where it stopped
     * (setup-and-home §S6, defect #21).
     */
    fun markTokenHandedOut(playerId: Long) = update { Identity.markRevealed(it, playerId) }

    /** Puts one seat back in the hand-out queue ("show me that again"). */
    fun clearTokenHandedOut(playerId: Long) = update { state ->
        state.updatePlayer(playerId) { it.copy(tokenShownAt = null) }
    }

    /** Puts EVERY seat back in the hand-out queue — a fresh pass round the table. */
    fun resetTokenHandout() = update { state ->
        state.copy(players = state.players.map { it.copy(tokenShownAt = null) })
    }

    /**
     * Marks a seat as a Traveller (or back to a resident) during setup, so it
     * is excluded from the distribution and dealt no token.
     */
    fun setTraveller(playerId: Long, isTraveller: Boolean) = update { state ->
        val seat = state.player(playerId) ?: return@update state
        Seats.assignCharacter(state, playerId, seat.characterId, isTraveller)
    }

    /** Records a storyteller number choice (Xaan's X, the Outsider branch). */
    fun setDecisionNumber(key: String, value: Int) =
        update { Decisions.set(it, key, value.toString()) }

    /**
     * Ticks one ACK-kind checklist row by id — `lilmonsta.noDemonSeat`,
     * `kazali.noMinions`, `damsel.minions`. The setup wizard records the
     * acknowledgement it took before the game existed.
     */
    fun applySetupRequirementAck(id: String) = update { Decisions.set(it, id, "true") }

    // ---- W6: game lifecycle ----

    /**
     * A brand-new game state for [script] and [playerNames].
     *
     * The one engine verb that cannot go through [update]: it does not
     * transform the game in progress, it replaces it, and the view models
     * archive the old one in the same breath. Exposing it here is what lets
     * `GameViewModel`/`WebGameViewModel` hold no `GameActions.` call at all —
     * the last gate of D26 / ARCHITECTURE §3.4.5.
     */
    fun newGame(script: Script, playerNames: List<String>): GameState =
        GameActions.newGame(script, playerNames)
}
