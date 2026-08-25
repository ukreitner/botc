package com.clocktower.engine

import kotlinx.serialization.Serializable

/** Whether a granted ability replaces the seat's own or is exercised alongside it. */
@Serializable
enum class GrantMode {
    /** The seat no longer wakes for its own character (Philosopher, Alchemist, Cannibal, Drunk). */
    REPLACE,

    /** The seat wakes for BOTH (Boffin's Demon, Pixie, Hermit, Bone Collector's target). */
    ADD,
}

@Serializable
data class AbilityGrant(
    /** The ability actually exercised: "chambermaid", "poisoner", "pukka". */
    val abilityId: String,
    /**
     * Who granted it: "philosopher", "alchemist", "boffin", "cannibal", "pixie",
     * "bonecollector", "hermit", "apprentice", "drunk", "marionette", "lunatic".
     */
    val sourceId: String,
    val mode: GrantMode = GrantMode.ADD,
    /** Night-order slot to wake at; null = the ability's own slot. */
    val slotId: String? = null,
    /** Boffin, Bone Collector, Ogre: works even while the holder is drunk or poisoned. */
    val worksWhileImpaired: Boolean = false,
    /** Drunk, Marionette, Lunatic: the ability NEVER works; every result is fabricated. */
    val alwaysFalse: Boolean = false,
    val cycle: Int = 0,
    /** Independent once-per-game state for a granted once-per-game ability. */
    val spent: Boolean = false,
)

/** A grant whose holder is derived, not fixed to a seat. */
@Serializable
data class FloatingGrant(
    val abilityId: String,
    /** "boffin", "plaguedoctor". */
    val sourceId: String,
    val holder: GrantHolder,
    val worksWhileImpaired: Boolean = false,
)

@Serializable
enum class GrantHolder { ALIVE_DEMON, STORYTELLER }

/** One thing a seat is woken for. */
data class ActingRole(
    val playerId: Long,
    /** Whose rules to run: night guide entry, InfoCalc key, target count, tokens. */
    val abilityId: String,
    /** Which night-order slot it fires in. */
    val slotId: String,
    /** Null when this is the seat's own character. */
    val sourceId: String?,
    val alwaysFalse: Boolean,
    val worksWhileImpaired: Boolean,
)

/** Why a seat's character changed. */
@Serializable
enum class ChangeReason {
    DEAL, STAR_PASS, STAR_PASS_TOKEN_SWAP, FANG_GU_JUMP, SCARLET_WOMAN, PIT_HAG, BARBER,
    ENGINEER, HATTER, SNAKE_CHARMER, KAZALI, SUMMONER, LORD_OF_TYPHON, HUNTSMAN_DAMSEL,
    AMNESIAC, DEUS_EX_FIASCO, FARMER, STORYTELLER,
}

@Serializable
data class IdentityRecord(
    val playerId: Long,
    val cycle: Int,
    val atNight: Boolean,
    val fromCharacterId: String?,
    val toCharacterId: String?,
    val fromEvil: Boolean,
    val toEvil: Boolean,
    val reason: ChangeReason,
    /** The player still has to be shown their new token. */
    val pendingReveal: Boolean = true,
    val pendingFirstNightRerun: Boolean = false,
    val notes: List<String> = emptyList(),
)

/**
 * Who a seat is, who they believe they are, and what they act as (WP4).
 * WP0 moved `starPass` / `swapCharacters` / `snakeCharmerSwap` here verbatim.
 */
object Identity {

    /** The token this player has SEEN — what a "YOU ARE" card must show. */
    fun believedCharacterId(p: Player): String? = p.shownCharacterId ?: p.characterId

    /** What this seat IS. Never `shownCharacterId`. */
    fun registersAs(p: Player): String? = p.characterId

    /** Everything this seat is woken for, own ability first. */
    fun actingRoles(state: GameState, lookup: (String) -> Character?, p: Player): List<ActingRole> =
        TODO("WP4")

    fun allActingRoles(state: GameState, lookup: (String) -> Character?): List<ActingRole> =
        TODO("WP4")

    /**
     * Grants implied by the grimoire rather than stored — nothing is stored twice:
     *   characterId == "drunk"       -> REPLACE(shownCharacterId, "drunk", alwaysFalse)
     *   characterId == "marionette"  -> REPLACE(shownCharacterId, "marionette", alwaysFalse)
     *   characterId == "lunatic"     -> REPLACE(shownCharacterId, "lunatic", slotId = "lunatic", alwaysFalse)
     *   characterId == "hermit"      -> ADD(every Outsider on the script, "hermit")
     *   cannibal + a "Lunch" token   -> REPLACE(last executee's characterIdAtDeath, "cannibal", …)
     *   floatingGrant(ALIVE_DEMON)   -> ADD on the single alive Demon seat
     */
    fun derivedGrants(
        state: GameState,
        lookup: (String) -> Character?,
        p: Player,
    ): List<AbilityGrant> = TODO("WP4")

    /**
     * THE single funnel for every character change (lead D17). In order:
     *  1. Alignment: `evil = newEvil ?: currentAlignment` (the Pit-Hag rule).
     *  2. Effects & tokens: remove every Effect and PlacedReminder in the WHOLE
     *     grimoire whose source is the abandoned character; keep foreign ones.
     *  3. Shown identity, grants sourced by the old character, SPENT marks: cleared.
     *  4. Append an IdentityRecord; queue a pending-reveal Prompt and, when the new
     *     ability is first-night or "start knowing", a RUN_FIRST_NIGHT Prompt.
     *  5. Notes: square-bracket setup text has NO effect mid-game; a second Demon
     *     warning; `Bluffs.conflicts(...)`; broken Marionette adjacency / Typhon line.
     */
    fun changeCharacter(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        newCharacterId: String?,
        reason: ChangeReason,
        newEvil: Boolean? = null,
        shownCharacterId: String? = null,
        suppressReveal: Boolean = false,
    ): GameState = TODO("WP4")

    /**
     * Resolves a demon self-kill that passes the mantle: the demon dies and
     * the chosen heir becomes that same demon (evil). Covers the Imp
     * star-pass and the Fang Gu jump alike.
     *
     * WP0: moved verbatim from `GameActions.starPass`. WP4 rewrites it as the
     * official token swap (§2.10b) on top of [changeCharacter].
     */
    fun starPass(
        state: GameState,
        lookup: (String) -> Character? = { null },
        demonPlayerId: Long,
        heirPlayerId: Long,
        @Suppress("UNUSED_PARAMETER") cause: DeathCause = DeathCause.OTHER_NIGHT_DEATH,
    ): GameState {
        val demonCharacter = state.player(demonPlayerId)?.characterId ?: return state
        if (state.player(heirPlayerId) == null) return state
        var next = Deaths.kill(state, demonPlayerId, DeathCause.OTHER_NIGHT_DEATH, lookup)
        next = next.updatePlayer(heirPlayerId) {
            it.copy(
                characterId = demonCharacter,
                shownCharacterId = null,
                alignment = null,
                legacyAlignmentFlipped = false,
            )
        }
        return next
    }

    /**
     * Swaps two seats' characters (Barber, Snake Charmer...).
     *
     * WP0: moved verbatim from `GameActions.swapCharacters`. WP4 replaces it with
     * two [changeCharacter] calls that stop swapping `shownCharacterId`.
     */
    fun swapCharacters(
        state: GameState,
        lookup: (String) -> Character? = { null },
        a: Long,
        b: Long,
    ): GameState {
        val p1 = state.player(a) ?: return state
        val p2 = state.player(b) ?: return state
        return state
            .updatePlayer(a) {
                it.copy(
                    characterId = p2.characterId,
                    shownCharacterId = p2.shownCharacterId,
                )
            }
            .updatePlayer(b) {
                it.copy(
                    characterId = p1.characterId,
                    shownCharacterId = p1.shownCharacterId,
                )
            }
    }

    /**
     * Resolves a successful Snake Charmer hit: the charmer and the chosen
     * Demon swap characters AND alignments (both revert to their new
     * character's natural alignment), and the new Snake Charmer — the former
     * Demon player — is poisoned.
     *
     * WP0: moved verbatim from `GameActions.snakeCharmerSwap`.
     */
    fun snakeCharmerSwap(
        state: GameState,
        charmerId: Long,
        demonPlayerId: Long,
    ): GameState {
        var next = swapCharacters(state, { null }, charmerId, demonPlayerId)
        next = next.updatePlayer(charmerId) {
            it.copy(alignment = null, legacyAlignmentFlipped = false, shownCharacterId = null)
        }
        next = next.updatePlayer(demonPlayerId) {
            it.copy(alignment = null, legacyAlignmentFlipped = false, shownCharacterId = null)
        }
        return Effects.placeExclusiveReminder(
            next,
            demonPlayerId,
            PlacedReminder("snakecharmer", "Poisoned"),
        )
    }

    fun pendingReveals(state: GameState): List<IdentityRecord> = TODO("WP4")

    fun markRevealed(state: GameState, playerId: Long): GameState = TODO("WP4")

    /** Ids that must never be held by two live seats: every Demon character, plus lilmonsta. */
    fun duplicateLiveCharacterIds(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<String> = TODO("WP4")
}
