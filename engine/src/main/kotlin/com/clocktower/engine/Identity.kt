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
    AMNESIAC, DEUS_EX_FIASCO, FARMER, RIOT, STORYTELLER,
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
 *
 * Three layers, never guessed (invariant I7):
 *  - `characterId` is the truth,
 *  - `shownCharacterId` is what the player believes,
 *  - `grants` + [actingRoles] is what they are woken for.
 */
object Identity {

    /** Seats a Drunk / Marionette / Lunatic believes-token is projected from. */
    private val BELIEVED_ROLE_SOURCES = setOf("drunk", "marionette", "lunatic")

    /** The Cannibal's "recently killed executee" marker; `Execution` places it. */
    internal const val CANNIBAL_LUNCH = "Lunch"

    /** Seat-note prefix the setup prompts write; cleared on every character change. */
    private const val BELIEF_NOTE_PREFIX = "Believes they are"

    /** The token this player has SEEN — what a "YOU ARE" card must show. */
    fun believedCharacterId(p: Player): String? = p.shownCharacterId ?: p.characterId

    /** What this seat IS. Never `shownCharacterId`. */
    fun registersAs(p: Player): String? = p.characterId

    /**
     * Everything this seat is woken for, own ability first.
     *
     * A single [GrantMode.REPLACE] grant (Drunk, Marionette, Lunatic, Philosopher,
     * Alchemist, Cannibal) removes the seat's own row; [GrantMode.ADD] grants
     * (Boffin, Hermit, Pixie, Bone Collector) sit alongside it.
     */
    fun actingRoles(state: GameState, lookup: (String) -> Character?, p: Player): List<ActingRole> {
        val grants = p.grants + derivedGrants(state, lookup, p)
        val replaced = grants.any { it.mode == GrantMode.REPLACE }
        val roles = mutableListOf<ActingRole>()
        val own = p.characterId?.let(Character::normalizeId)
        if (own != null && !replaced) {
            roles += ActingRole(
                playerId = p.id,
                abilityId = own,
                slotId = own,
                sourceId = null,
                alwaysFalse = false,
                worksWhileImpaired = false,
            )
        }
        for (grant in grants) {
            val abilityId = Character.normalizeId(grant.abilityId)
            if (abilityId.isEmpty()) continue
            roles += ActingRole(
                playerId = p.id,
                abilityId = abilityId,
                slotId = grant.slotId?.let(Character::normalizeId) ?: abilityId,
                sourceId = Character.normalizeId(grant.sourceId),
                alwaysFalse = grant.alwaysFalse,
                worksWhileImpaired = grant.worksWhileImpaired,
            )
        }
        return roles.distinctBy { Triple(it.abilityId, it.slotId, it.sourceId) }
    }

    /** Every acting role in the game, in seat order. */
    fun allActingRoles(state: GameState, lookup: (String) -> Character?): List<ActingRole> =
        state.seats.flatMap { actingRoles(state, lookup, it) }

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
    ): List<AbilityGrant> {
        val grants = mutableListOf<AbilityGrant>()
        val own = p.characterId?.let(Character::normalizeId)
        val believed = p.shownCharacterId?.let(Character::normalizeId)

        if (own in BELIEVED_ROLE_SOURCES && believed != null && believed != own) {
            grants += AbilityGrant(
                abilityId = believed,
                sourceId = own!!,
                mode = GrantMode.REPLACE,
                // A Lunatic keeps its own dedicated wake row despite acting as a Demon.
                slotId = if (own == "lunatic") "lunatic" else null,
                alwaysFalse = true,
            )
        }

        if (own == "hermit") {
            for (outsider in scriptCharacters(state, lookup)) {
                if (outsider.team != Team.OUTSIDER || outsider.id == "hermit") continue
                grants += AbilityGrant(
                    abilityId = outsider.id,
                    sourceId = "hermit",
                    mode = GrantMode.ADD,
                )
            }
        }

        if (own == "cannibal") {
            cannibalLunchAbility(state)?.let { lunch ->
                grants += AbilityGrant(
                    abilityId = lunch,
                    sourceId = "cannibal",
                    mode = GrantMode.REPLACE,
                    // The Cannibal wakes at the eaten character's own night slot (lead D43).
                    slotId = lunch,
                )
            }
        }

        val floating = state.floatingGrants + boffinGrant(state)
        if (floating.isNotEmpty()) {
            val demonSeatId = soleAliveDemonSeatId(state, lookup)
            for (floating in floating) {
                val holderId = when (floating.holder) {
                    GrantHolder.ALIVE_DEMON -> demonSeatId
                    // The Storyteller is not a seat — NightPlan renders it seatless.
                    GrantHolder.STORYTELLER -> null
                }
                if (holderId == p.id) {
                    grants += AbilityGrant(
                        abilityId = floating.abilityId,
                        sourceId = floating.sourceId,
                        mode = GrantMode.ADD,
                        worksWhileImpaired = floating.worksWhileImpaired,
                    )
                }
            }
        }
        return grants
    }

    /**
     * The Boffin's gift, DERIVED from `Decisions.BOFFIN_GRANT` (WP7-EXP-M's open
     * P0): "the Demon (even if drunk or poisoned) has a not-in-play good
     * character's ability". The setup decision is the whole record — nothing is
     * stored twice — and the grant lasts while a Boffin seat exists, alive or
     * dead, because the card ties it to the Boffin, not to their ability.
     *
     * A dead Boffin ends it: `Deaths` already raises the "the Demon loses the
     * granted ability" prompt, and this stops emitting the row the moment the
     * storyteller retires the seat. The wiki keeps the gift while the Boffin
     * merely sleeps, which is why the check is "a Boffin is seated", not "alive".
     */
    private fun boffinGrant(state: GameState): List<FloatingGrant> {
        val ability = state.decisions[Decisions.BOFFIN_GRANT]
            ?.takeIf { it.isNotBlank() }
            ?.let(Character::normalizeId)
            ?: return emptyList()
        val seated = state.seats.any {
            it.characterId?.let(Character::normalizeId) == BOFFIN
        }
        if (!seated) return emptyList()
        return listOf(
            FloatingGrant(
                abilityId = ability,
                sourceId = BOFFIN,
                holder = GrantHolder.ALIVE_DEMON,
                // "even if drunk or poisoned" — the card's own words.
                worksWhileImpaired = true,
            ),
        )
    }

    private const val BOFFIN = "boffin"

    /** The character the Cannibal currently has, from the seat carrying the Lunch token. */
    private fun cannibalLunchAbility(state: GameState): String? {
        val lunchKey = Tokens.key("cannibal", CANNIBAL_LUNCH)
        val eaten = state.players.lastOrNull { seat ->
            seat.reminders.any { Tokens.key(it) == lunchKey }
        } ?: return null
        val diedAs = state.deaths
            .lastOrNull { it.playerId == eaten.id && it.resurrectedAtCycle == null }
            ?.characterIdAtDeath
        return (diedAs ?: eaten.characterId)?.let(Character::normalizeId)
    }

    /** The one alive seat holding a Demon character, or null when that is ambiguous. */
    private fun soleAliveDemonSeatId(state: GameState, lookup: (String) -> Character?): Long? =
        state.alivePlayers
            .filter { it.characterId?.let(lookup)?.team == Team.DEMON }
            .singleOrNull()
            ?.id

    /** The characters on this game's script, custom ones included. */
    private fun scriptCharacters(state: GameState, lookup: (String) -> Character?): List<Character> {
        val custom = state.script.customCharacters.associateBy { it.id }
        return state.script.characterIds.mapNotNull { custom[it] ?: lookup(it) }
    }

    /**
     * THE single funnel for every character change (lead D17). In order:
     *  1. Alignment: `evil = newEvil ?: currentAlignment` (the Pit-Hag rule).
     *  2. Effects & tokens: remove every Effect and PlacedReminder in the WHOLE
     *     grimoire whose source is the abandoned character; keep foreign ones.
     *  3. Shown identity, grants sourced by the old character, SPENT marks: cleared.
     *  4. Append an IdentityRecord; queue a pending-reveal Prompt and, when the new
     *     ability is first-night or "start knowing", a RUN_FIRST_NIGHT Prompt.
     *  5. Notes: square-bracket setup text has NO effect mid-game; a second Demon
     *     warning; [Bluffs.conflicts]; broken Marionette adjacency / Typhon line.
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
    ): GameState {
        val player = state.player(playerId) ?: return state
        val oldId = player.characterId?.let(Character::normalizeId)
        val newId = newCharacterId?.let(Character::normalizeId)
        val newCharacter = newId?.let(lookup)

        // 1. Alignment persists despite character changes unless the rule says otherwise.
        val wasEvil = player.isEvil(lookup)
        val evil = newEvil ?: wasEvil
        val naturalEvil = newCharacter?.team?.isEvil ?: false
        val alignment = when {
            newId == null -> null
            evil == naturalEvil -> null
            evil -> Alignment.EVIL
            else -> Alignment.GOOD
        }

        // 2. Every effect and token that belonged to the abandoned ability.
        var next = if (oldId == null) state else stripSource(state, oldId)

        // 3. Shown identity, old-sourced grants (already stripped) and SPENT marks.
        val spentLabel = oldId?.let(lookup)?.spentLabel.orEmpty()
        val spentKey = if (oldId != null && spentLabel.isNotBlank()) {
            Tokens.key(oldId, spentLabel)
        } else {
            null
        }
        val watermark = next.nextEffectId
        next = next.updatePlayer(playerId) { seat ->
            seat.copy(
                characterId = newId,
                shownCharacterId = shownCharacterId,
                alignment = alignment,
                legacyAlignmentFlipped = false,
                grants = seat.grants.map { if (it.spent) it.copy(spent = false) else it },
                reminders = seat.reminders.filterNot { spentKey != null && Tokens.key(it) == spentKey },
                notes = seat.notes.filterNot {
                    it.text.startsWith(BELIEF_NOTE_PREFIX, ignoreCase = true)
                },
                legacyNote = "",
                standingSince = watermark,
                tokenShownAt = null,
            )
        }

        // 4 + 5. The record, its notes, and the obligations it creates.
        val pendingReveal = !suppressReveal && newId != null
        val pendingRerun = newId != null &&
            reason != ChangeReason.DEAL &&
            newCharacter?.firstNightReminder?.isNotBlank() == true
        val notes = changeNotes(next, lookup, playerId, newCharacter, reason)
        next = next.copy(
            identityLog = next.identityLog + IdentityRecord(
                playerId = playerId,
                cycle = next.cycle,
                atNight = next.phase == Phase.NIGHT,
                fromCharacterId = oldId,
                toCharacterId = newId,
                fromEvil = wasEvil,
                toEvil = evil,
                reason = reason,
                pendingReveal = pendingReveal,
                pendingFirstNightRerun = pendingRerun,
                notes = notes,
            ),
        )
        if (pendingReveal) {
            next = queuePrompt(
                next,
                Prompt(
                    id = 0,
                    at = BriefingSlot.NOW,
                    kind = PromptKind.INFO,
                    sourceId = newId.orEmpty(),
                    subjectPlayerId = playerId,
                    characterIds = listOfNotNull(newId),
                    title = "Show ${player.name} their new character " +
                        "(${newCharacter?.name ?: newId})",
                    detail = notes.joinToString(" "),
                ),
            )
        }
        if (pendingRerun) {
            next = queuePrompt(
                next,
                Prompt(
                    id = 0,
                    at = BriefingSlot.TONIGHT,
                    kind = PromptKind.RUN_FIRST_NIGHT,
                    sourceId = newId.orEmpty(),
                    subjectPlayerId = playerId,
                    title = "Re-run ${player.name}'s first-night information " +
                        "(${newCharacter?.name ?: newId})",
                    stepSlotId = newId.orEmpty(),
                ),
            )
        }
        return next
    }

    /** Removes every effect, token and grant in the whole grimoire sourced by [characterId]. */
    private fun stripSource(state: GameState, characterId: String): GameState {
        fun ours(sourceId: String) = Character.normalizeId(sourceId) == characterId
        return state.copy(
            players = state.players.map { seat ->
                seat.copy(
                    reminders = seat.reminders.filterNot { ours(it.sourceId) },
                    grants = seat.grants.filterNot { ours(it.sourceId) },
                )
            },
            storytellerReminders = state.storytellerReminders.filterNot { ours(it.sourceId) },
            effects = state.effects.filterNot { ours(it.sourceCharacterId) },
            floatingGrants = state.floatingGrants.filterNot { ours(it.sourceId) },
        )
    }

    /** Appends a prompt, stamping it with the next id. */
    private fun queuePrompt(state: GameState, prompt: Prompt): GameState = state.copy(
        prompts = state.prompts + prompt.copy(id = state.nextPromptId),
        nextPromptId = state.nextPromptId + 1,
    )

    /**
     * The storyteller-voice consequences of one change. Square-bracket setup text
     * is a RULE, not a judgement call: it never applies mid-game (Abilities page).
     */
    private fun changeNotes(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        newCharacter: Character?,
        reason: ChangeReason,
    ): List<String> {
        val notes = mutableListOf<String>()
        if (newCharacter?.team == Team.DEMON) {
            val otherDemons = state.alivePlayers.count {
                it.id != playerId && it.characterId?.let(lookup)?.team == Team.DEMON
            }
            if (otherDemons > 0) notes += "A second Demon exists — deaths tonight are arbitrary."
        }
        if (newCharacter?.setup == true) {
            notes += "${newCharacter.name}'s [setup text] has no effect — square brackets only " +
                "change the setup, and the setup is over."
        }
        if (reason == ChangeReason.STAR_PASS) {
            notes += "The new Demon does not act tonight."
        }
        if (reason in setOf(ChangeReason.KAZALI, ChangeReason.SUMMONER, ChangeReason.LORD_OF_TYPHON)) {
            notes += "Do NOT show them the other evil players and do NOT give bluffs."
        }
        notes += Bluffs.conflicts(state, lookup)
        for (seat in state.players) {
            if (seat.characterId == "marionette" &&
                !SetupRequirements.marionetteNeighbourOk(state, lookup, seat)
            ) {
                notes += "${seat.name} is the Marionette and no longer neighbours the Demon."
            }
        }
        return notes
    }

    /**
     * Demon self-kill that passes the mantle. Official How to Run is a TOKEN SWAP:
     * the corpse takes the heir's old token so exactly ONE seat holds the Demon
     * character afterwards. `DeathEvent.characterIdAtDeath` preserves "died as the Imp".
     *
     * Also serves the Fang Gu jump.
     */
    fun starPass(
        state: GameState,
        lookup: (String) -> Character? = { null },
        demonPlayerId: Long,
        heirPlayerId: Long,
        cause: DeathCause = DeathCause.DEMON_KILL,
    ): GameState {
        val demon = state.player(demonPlayerId) ?: return state
        val heir = state.player(heirPlayerId) ?: return state
        val demonCharacterId = demon.characterId?.let(Character::normalizeId) ?: return state
        if (heir.id == demon.id || !heir.alive || heir.isTraveller || !heir.seated) return state
        val heirFormerCharacterId = heir.characterId

        // The death snapshot captures the Demon character before any token moves.
        var next = Deaths.kill(state, demonPlayerId, cause, lookup)
        next = changeCharacter(
            state = next,
            lookup = lookup,
            playerId = heirPlayerId,
            newCharacterId = demonCharacterId,
            reason = ChangeReason.STAR_PASS,
            newEvil = true,
        )
        // The corpse takes the heir's old token, so exactly one seat is the Demon.
        next = changeCharacter(
            state = next,
            lookup = lookup,
            playerId = demonPlayerId,
            newCharacterId = heirFormerCharacterId,
            reason = ChangeReason.STAR_PASS_TOKEN_SWAP,
            newEvil = demon.isEvil(lookup),
            suppressReveal = true,
        )
        // The Demon's own "Dead" token, when the character declares one.
        val deadLabel = lookup(demonCharacterId)?.allReminders
            ?.firstOrNull { it.equals("Dead", ignoreCase = true) }
        if (deadLabel != null) {
            next = Effects.addReminder(
                next,
                demonPlayerId,
                PlacedReminder(demonCharacterId, deadLabel, placedCycle = next.cycle),
            )
        }
        return next
    }

    /**
     * Swaps two seats' characters (Barber, Snake Charmer...). Two [changeCharacter]
     * calls — and it deliberately does NOT swap `shownCharacterId`: a Drunk keeps
     * believing what they believed (setup-and-identity defect 15).
     */
    fun swapCharacters(
        state: GameState,
        lookup: (String) -> Character? = { null },
        a: Long,
        b: Long,
    ): GameState {
        val p1 = state.player(a) ?: return state
        val p2 = state.player(b) ?: return state
        if (a == b) return state
        var next = changeCharacter(
            state = state,
            lookup = lookup,
            playerId = a,
            newCharacterId = p2.characterId,
            reason = ChangeReason.BARBER,
            newEvil = p1.isEvil(lookup),
        )
        next = changeCharacter(
            state = next,
            lookup = lookup,
            playerId = b,
            newCharacterId = p1.characterId,
            reason = ChangeReason.BARBER,
            newEvil = p2.isEvil(lookup),
        )
        return next
    }

    /**
     * Resolves a successful Snake Charmer hit: the charmer and the chosen
     * Demon swap characters AND alignments (both revert to their new
     * character's natural alignment), and the new Snake Charmer — the former
     * Demon player — is poisoned.
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

    /**
     * The official Lunatic token swap (How to Run): the Lunatic draws the Demon
     * token and the real Demon draws the Lunatic's. `characterId` never moves.
     */
    fun applyLunaticTokenSwap(state: GameState, lookup: (String) -> Character?): GameState {
        val lunatic = state.players.firstOrNull { it.characterId == "lunatic" } ?: return state
        val demon = state.players.firstOrNull {
            it.characterId?.let(lookup)?.team == Team.DEMON
        } ?: return state
        val demonCharacterId = demon.characterId ?: return state
        return state
            .updatePlayer(lunatic.id) { it.copy(shownCharacterId = demonCharacterId) }
            .updatePlayer(demon.id) { it.copy(shownCharacterId = "lunatic") }
    }

    /** Restores a seat's true token (the Demon at DEMON_INFO after the Lunatic swap). */
    fun revealTrueIdentity(state: GameState, playerId: Long): GameState =
        state.updatePlayer(playerId) { it.copy(shownCharacterId = null) }

    /** Seats whose new token has not been handed over yet, newest first. */
    fun pendingReveals(state: GameState): List<IdentityRecord> =
        state.identityLog.filter { it.pendingReveal }.reversed().distinctBy { it.playerId }

    /** Seats whose new ability still owes a first-night run. */
    fun pendingFirstNightReruns(state: GameState): List<IdentityRecord> =
        state.identityLog.filter { it.pendingFirstNightRerun }.reversed().distinctBy { it.playerId }

    fun markRevealed(state: GameState, playerId: Long): GameState = state.copy(
        identityLog = state.identityLog.map {
            if (it.playerId == playerId) it.copy(pendingReveal = false) else it
        },
    ).updatePlayer(playerId) { it.copy(tokenShownAt = state.updatedAt) }

    fun markRerunDone(state: GameState, playerId: Long): GameState = state.copy(
        identityLog = state.identityLog.map {
            if (it.playerId == playerId) it.copy(pendingFirstNightRerun = false) else it
        },
    )

    /** Ids that must never be held by two live seats: every Demon character, plus lilmonsta. */
    fun duplicateLiveCharacterIds(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<String> = state.alivePlayers
        .mapNotNull { it.characterId?.let(Character::normalizeId) }
        .filter { id -> id == "lilmonsta" || lookup(id)?.team == Team.DEMON }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .sorted()
}
