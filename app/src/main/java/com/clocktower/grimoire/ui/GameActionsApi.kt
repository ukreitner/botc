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
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Nomination
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Seats
import com.clocktower.engine.Selection
import com.clocktower.engine.SetupRequirement
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
}
