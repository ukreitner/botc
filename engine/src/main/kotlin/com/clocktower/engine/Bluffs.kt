package com.clocktower.engine

import kotlin.random.Random

/** One set of bluffs this game owes someone. A LIST, not a map (lead D38). */
data class BluffRequirement(
    /** Key into [GameState.bluffSets]. Source-qualified so one seat can hold two sets. */
    val key: String,
    /** Seat that receives them; null for the Demon set in a multi-Demon game. */
    val recipientId: Long?,
    /** "Demon bluffs", "Snitch bluffs — Ana (Poisoner)", "Lunatic bluffs — Bo". */
    val label: String,
    val size: Int = 3,
    /** Only the Lunatic (and an impaired Snitch) may be shown in-play characters. */
    val allowInPlay: Boolean = false,
    /** Night-order step where the card is shown. */
    val stepSlotId: String,
    val sourceId: String,
    /** The rules sentence surfaced under the picker. */
    val reason: String = "",
    /** false = offer it, never block on it (Legion: "bluffs are optional"). */
    val required: Boolean = true,
) {
    companion object {
        const val DEMON_KEY = "demon"
    }
}

data class BluffCandidate(
    val character: Character,
    val inPlay: Boolean,
    /** "the Drunk believes this", "the Boffin gave the Demon this", "the Alchemist has this". */
    val inUseBy: String? = null,
)

/**
 * How one character contributes bluff requirements. Referenced by
 * `CharacterRule.bluffs`; WP4 fills in the registry side.
 */
data class BluffRule(
    val produce: (state: GameState, lookup: (String) -> Character?, holder: Player) ->
    List<BluffRequirement>,
)

/**
 * Bluff sets, per requirement key (WP4). WP0 moved `setBluffs` / `suggestBluffs` /
 * `setFabled` here verbatim.
 */
object Bluffs {

    /** The Marionette is never woken for anything that would confirm they are a Minion. */
    private const val MARIONETTE = "marionette"

    /**
     * Every bluff set this game owes someone, in hand-out order.
     *
     * No bluffs AT ALL under a Lil' Monsta (How to Run skips both info steps) or
     * an Atheist. No Demon set below 7 residents unless a Poppy Grower or a
     * Summoner is in play. The Summoner's set replaces the Demon's while no
     * Demon seat exists. The Snitch adds ONE INDEPENDENT SET PER MINION —
     * excluding the Marionette, including Legion seats, which is the official
     * "possibly 6 bluffs" case as two separate sets on one seat. The Lunatic
     * gets their own set, and theirs alone may contain in-play characters.
     *
     * The retired Snitch × Marionette "+3" jinx is deliberately NOT here (lead D38).
     */
    fun requirements(state: GameState, lookup: (String) -> Character?): List<BluffRequirement> {
        val seats = state.seats
        val inPlay = seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
        if ("lilmonsta" in inPlay || "atheist" in inPlay) return emptyList()

        val residents = seats.count { !it.isTraveller }
        val demonSeats = seats.filter { it.characterId?.let(lookup)?.team == Team.DEMON }
        // Legion "registers as a Minion too", so a Legion seat gets a Snitch set
        // of its own on top of the Demon set — the official "possibly 6 bluffs"
        // case, as two independent sets on one seat. (Lead D32; this becomes
        // `Registration.registersAs` once WP1 lands.)
        val minionSeats = seats.filter {
            val id = Character.normalizeId(it.characterId.orEmpty())
            id != MARIONETTE &&
                (it.characterId?.let(lookup)?.team == Team.MINION || id == "legion")
        }
        val poppyGrower = "poppygrower" in inPlay
        val requirements = mutableListOf<BluffRequirement>()

        // The Demon's three. Teensyville games below 7 get none unless a Poppy
        // Grower or a Summoner puts the step back on the sheet.
        if (demonSeats.isNotEmpty() && (residents >= 7 || poppyGrower || "summoner" in inPlay)) {
            requirements += BluffRequirement(
                key = BluffRequirement.DEMON_KEY,
                recipientId = demonSeats.singleOrNull()?.id,
                label = "Demon bluffs",
                size = 3,
                stepSlotId = if (poppyGrower) {
                    NightMarkers.DEMON_BLUFFS_ONLY
                } else {
                    NightMarkers.DEMON_INFO
                },
                sourceId = "demoninfo",
                reason = "Three good characters that are not in play.",
                // "Bluffs are optional" in a Legion game.
                required = "legion" !in inPlay,
            )
        }

        // "You get 3 bluffs" — the Summoner's set stands in until a Demon exists.
        if (demonSeats.isEmpty()) {
            for (summoner in seats.filter { it.characterId == "summoner" }) {
                requirements += BluffRequirement(
                    key = "summoner:${summoner.id}",
                    recipientId = summoner.id,
                    label = "Summoner bluffs — ${summoner.name}",
                    size = 3,
                    stepSlotId = "summoner",
                    sourceId = "summoner",
                    reason = "Three good characters that are not in play.",
                    // Jinx: the Alchemist-Summoner does not get bluffs.
                    required = "alchemist" !in inPlay,
                )
            }
        }

        // "Each Minion gets 3 bluffs" — one independent set each.
        val snitch = seats.firstOrNull { it.characterId == "snitch" }
        if (snitch != null) {
            val snitchImpaired = StatusEffects.isImpaired(state, lookup, snitch)
            for (minion in minionSeats) {
                requirements += BluffRequirement(
                    key = "snitch:${minion.id}",
                    recipientId = minion.id,
                    label = "Snitch bluffs — ${minion.name} (${characterName(minion, lookup)})",
                    size = 3,
                    // An impaired Snitch may hand out in-play characters.
                    allowInPlay = snitchImpaired,
                    stepSlotId = "snitch",
                    sourceId = "snitch",
                    reason = "Three good characters, drawn independently of the Demon's.",
                )
            }
        }

        // The Lunatic's own set — the only one that may name in-play characters.
        for (lunatic in seats.filter { it.characterId == "lunatic" }) {
            requirements += BluffRequirement(
                key = "lunatic:${lunatic.id}",
                recipientId = lunatic.id,
                label = "Lunatic bluffs — ${lunatic.name}",
                size = 3,
                allowInPlay = true,
                stepSlotId = "lunatic",
                sourceId = "lunatic",
                reason = "The Lunatic's bluffs MAY include in-play characters.",
            )
        }
        return requirements
    }

    private fun characterName(seat: Player, lookup: (String) -> Character?): String =
        seat.characterId?.let(lookup)?.name ?: seat.characterId.orEmpty()

    /**
     * Legal picks for [req], annotated — never filtered — when a character is
     * already spoken for. A Drunk's believed Townsfolk, a Boffin grant and an
     * Alchemist grant are all LEGAL bluffs; the storyteller just wants to know.
     */
    fun candidates(
        state: GameState,
        script: List<Character>,
        req: BluffRequirement,
    ): List<BluffCandidate> {
        val inPlay = state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
        val inUse = inUseAnnotations(state)
        // Never offer a Lunatic the Demon they believe they are.
        val believedDemon = req.recipientId
            ?.let { state.player(it) }
            ?.takeIf { it.characterId == "lunatic" }
            ?.shownCharacterId
            ?.let(Character::normalizeId)
        return script
            .filter { it.team == Team.TOWNSFOLK || it.team == Team.OUTSIDER }
            .filterNot { it.id == believedDemon }
            .filter { req.allowInPlay || it.id !in inPlay }
            .distinctBy { it.id }
            .map { BluffCandidate(it, inPlay = it.id in inPlay, inUseBy = inUse[it.id]) }
    }

    /** "the Drunk believes this", "the Boffin gave the Demon this", … */
    private fun inUseAnnotations(state: GameState): Map<String, String> {
        val notes = mutableMapOf<String, String>()
        for (seat in state.seats) {
            val believed = seat.shownCharacterId?.let(Character::normalizeId) ?: continue
            when (Character.normalizeId(seat.characterId.orEmpty())) {
                "drunk" -> notes[believed] = "the Drunk believes this"
                MARIONETTE -> notes[believed] = "the Marionette believes this"
                else -> Unit
            }
        }
        state.decisions[Decisions.BOFFIN_GRANT]?.takeIf { it.isNotBlank() }?.let {
            notes[Character.normalizeId(it)] = "the Boffin gave the Demon this"
        }
        state.decisions[Decisions.ALCHEMIST_GRANT]?.takeIf { it.isNotBlank() }?.let {
            notes[Character.normalizeId(it)] = "the Alchemist has this"
        }
        val madKey = Tokens.key("pixie", "Mad")
        for (seat in state.seats) {
            for (reminder in seat.reminders) {
                if (Tokens.key(reminder) != madKey) continue
                reminder.characterId?.let {
                    notes[Character.normalizeId(it)] = "the Pixie is mad about this"
                }
            }
        }
        return notes
    }

    /**
     * Two Townsfolk and one Outsider, preferring characters nobody else is
     * using, drawn INDEPENDENTLY per recipient. A Lunatic's set deliberately
     * includes at least one in-play character — that is the tell.
     */
    fun suggest(
        state: GameState,
        script: List<Character>,
        req: BluffRequirement,
        random: Random,
    ): List<String> {
        val pool = candidates(state, script, req)
        if (pool.isEmpty()) return emptyList()
        val order = compareBy<BluffCandidate>(
            { if (it.inUseBy == null) 0 else 1 },
            { if (it.inPlay) 1 else 0 },
        )
        fun draw(team: Team) =
            pool.filter { it.character.team == team }.shuffled(random).sortedWith(order)

        val picks = mutableListOf<BluffCandidate>()
        picks += draw(Team.TOWNSFOLK).take(2)
        picks += draw(Team.OUTSIDER).take(1)
        if (req.allowInPlay && picks.none { it.inPlay }) {
            pool.firstOrNull { it.inPlay && it !in picks }?.let { inPlayPick ->
                if (picks.size >= req.size) picks.removeAt(picks.lastIndex)
                picks += inPlayPick
            }
        }
        for (extra in pool.shuffled(random).sortedWith(order)) {
            if (picks.size >= req.size) break
            if (extra !in picks) picks += extra
        }
        return picks.take(req.size).map { it.character.id }
    }

    /**
     * Suggests 3 demon bluffs: not-in-play good characters from the script,
     * preferring two townsfolk and one outsider like most storytellers.
     *
     * WP0: moved verbatim from `GameActions.suggestBluffs`; WP4 folds it into
     * the per-requirement [suggest].
     */
    fun suggestBluffs(
        available: List<Character>,
        state: GameState,
        random: Random = Random,
    ): List<String> {
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        val townsfolk = available.filter { it.team == Team.TOWNSFOLK && it.id !in inPlay }.shuffled(random)
        val outsiders = available.filter { it.team == Team.OUTSIDER && it.id !in inPlay }.shuffled(random)
        val picks = (townsfolk.take(2) + outsiders.take(1) + townsfolk.drop(2) + outsiders.drop(1))
        return picks.take(3).map { it.id }
    }

    /** Stores one bluff set under its requirement key. */
    fun set(state: GameState, key: String, ids: List<String>): GameState =
        state.copy(bluffSets = state.bluffSets + (key to ids))

    fun clear(state: GameState, key: String): GameState =
        state.copy(bluffSets = state.bluffSets - key)

    /** WP0 move of `GameActions.setBluffs`: the Demon's three. */
    fun setDemonBluffs(state: GameState, bluffIds: List<String>): GameState =
        set(state, BluffRequirement.DEMON_KEY, bluffIds.take(3))

    /**
     * WP0 move of `GameActions.setFabled`: replaces the active Fabled list,
     * keeping the per-Fabled state of entries that stay in play.
     */
    fun setFabled(state: GameState, fabledIds: List<String>): GameState {
        val existing = state.fabled.associateBy { it.id }
        return state.copy(
            fabled = fabledIds.map { id ->
                existing[id] ?: FabledEntry(id = id, addedOnCycle = state.cycle)
            },
            legacyFabledIds = emptyList(),
        )
    }

    /** "Fisherman is one of the Demon's bluffs and is now in play." */
    fun conflicts(state: GameState, lookup: (String) -> Character?): List<String> {
        val inPlay = state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()
        val labels = requirements(state, lookup).associate { it.key to it.label }
        val conflicts = mutableListOf<String>()
        for ((key, ids) in state.bluffSets) {
            // The Lunatic's set is ALLOWED to hold in-play characters.
            if (key.startsWith("lunatic:")) continue
            val label = labels[key] ?: key
            for (id in ids) {
                val normalized = Character.normalizeId(id)
                if (normalized in inPlay) {
                    conflicts += "${lookup(normalized)?.name ?: id} is one of the $label " +
                        "and is now in play."
                }
            }
        }
        return conflicts.distinct()
    }
}
