package com.clocktower.grimoire.ui

import com.clocktower.engine.Alignment
import com.clocktower.engine.Bluffs
import com.clocktower.engine.ChangeReason
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Deaths
import com.clocktower.engine.Decisions
import com.clocktower.engine.Effects
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Identity
import com.clocktower.engine.KillCause
import com.clocktower.engine.LedgerEntry
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Nomination
import com.clocktower.engine.Phase
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Player
import com.clocktower.engine.RenderedToken
import com.clocktower.engine.SeatNote
import com.clocktower.engine.Seats
import com.clocktower.engine.Selection
import com.clocktower.engine.SetupRequirement
import com.clocktower.engine.Tokens
import com.clocktower.engine.DayRules
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

    // ---- WP3: day, ledger, execution ----

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

    // ---- WP8: night screen ----

    // ---- WP9: day screen ----

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
}
