package com.clocktower.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Explicit alignment, used where it is a choice rather than a consequence of the character. */
@Serializable
enum class Alignment { GOOD, EVIL }

/** A dated storyteller note on one seat. Append-only: setup prompts must never overwrite. */
@Serializable
data class SeatNote(
    val cycle: Int,
    val phase: Phase,
    val text: String,
)

/**
 * A storyteller-placed grimoire token with no rule attached.
 *
 * Rule-bearing tokens are NOT stored here — they are rendered from [Effect]
 * (see `Effects.rendered`). This list is the storyteller's own scratch layer:
 * free markers, improvised rulings, and any legacy token the load migration
 * could not match to a [TokenRule].
 *
 * NOTE FOR IMPLEMENTERS: never compare two `PlacedReminder`s with `==` to decide
 * whether "the same token" is already placed — the payload fields make that
 * comparison fail. Compare `Tokens.key(sourceId, label)`.
 */
@Serializable
data class PlacedReminder(
    /** Character id the token belongs to, or "st" for a storyteller token. Never "". */
    val sourceId: String,
    /** Official Title Case label from `characters.json`. Compared case-insensitively. */
    val label: String,
    /** Character this token points at: Cerenovus's mad character, Courtier's target. */
    val characterId: String? = null,
    /** Seat this token points back at: Harpy's 2nd, Grandmother's grandchild. */
    val targetPlayerId: Long? = null,
    /** Free text for an improvised ruling. Rendered under the token; searchable. */
    val note: String = "",
    /** `state.cycle` when placed — powers "placed N3" and homebrew countdowns. */
    val placedCycle: Int = 0,
    /**
     * Turned over: the physical token stays on the seat, the rule it projects
     * stops applying (wiki, Abilities). The effect-backed half of this lives on
     * [Effect.suspended]; a hand-placed token had nowhere to record it, so the
     * seat sheet's `Suspend` was a no-op for one (playtest D, P1-5).
     */
    val suspended: Boolean = false,
)

/** One seat in the grimoire circle. */
@Serializable
data class Player(
    val id: Long,
    val name: String,
    /** THE TRUTH. What this player actually is. Never what they believe. */
    val characterId: String? = null,
    /**
     * The token this player has SEEN — Drunk, Lunatic, Marionette, a mid-change
     * seat whose new token has not been handed over yet, and the real Demon in a
     * Lunatic game (set to "lunatic" at deal, cleared at DEMON_INFO).
     */
    val shownCharacterId: String? = null,
    /**
     * Explicit alignment override. Wins over the character's natural team.
     * Set for Travellers (always asked), Bounty Hunter's evil Townsfolk, an
     * evil-turned good player. Null = derive from the character.
     */
    val alignment: Alignment? = null,
    val alive: Boolean = true,
    /** Dead players hold one ghost vote until they spend it. */
    val ghostVoteUsed: Boolean = false,
    /**
     * Physical vote tokens this seat holds (lead D72).
     *
     * Everyone starts with exactly one and nothing but the Beggar ever looks:
     * a Beggar spends one to vote on an execution, and a dead player may hand
     * theirs over (`DayRules.giveVoteToken`), which is the Beggar's hoard and
     * the donor's last vote at the same time.
     */
    val voteTokens: Int = 1,
    val isTraveller: Boolean = false,
    /** True once a Traveller has left the game: no seat, no vote, no threshold. */
    val leftGame: Boolean = false,
    /** Storyteller free tokens only — see [PlacedReminder]. */
    val reminders: List<PlacedReminder> = emptyList(),
    /** Abilities this seat exercises in addition to / instead of its own. */
    val grants: List<AbilityGrant> = emptyList(),
    val notes: List<SeatNote> = emptyList(),
    /** Wall-clock millis when this seat's token was last handed to the player. */
    val tokenShownAt: Long? = null,
    /**
     * Effect-id watermark stamped whenever this seat's `characterId` changes.
     * Standing (innate) effects are ordered by it, so "the Poisoner poisoned the
     * No Dashii on night 1" resolves correctly while a Snake-Charmer-created
     * No Dashii on night 4 starts fresh.
     */
    val standingSince: Long = 0L,

    // ---- migration-only, never read by new code ----
    @SerialName("note") internal val legacyNote: String = "",
    @SerialName("alignmentFlipped") internal val legacyAlignmentFlipped: Boolean = false,
) {
    /** The token a "YOU ARE" card must show. Alias of `Identity.believedCharacterId`. */
    val characterShownToPlayerId: String? get() = shownCharacterId ?: characterId

    /** Seats that have left the game are not seats. */
    val seated: Boolean get() = !leftGame

    /**
     * Legacy single-note view of [notes], kept so the seat sheet compiles until
     * WP10 renders the note list. Reads every note, newest last.
     */
    @Deprecated("Use Player.notes (List<SeatNote>). Removed with WP10.")
    val note: String
        get() = if (notes.isEmpty()) legacyNote else notes.joinToString("\n") { it.text }

    /**
     * Legacy "this seat's alignment is not its character's" flag, kept so the
     * seat sheet compiles until WP10 reads [alignment] directly.
     */
    @Deprecated("Use Player.alignment. Removed with WP10.")
    val alignmentFlipped: Boolean get() = alignment != null || legacyAlignmentFlipped

    /**
     * Drunk and Marionette wake as the good character they believe they are.
     * A Lunatic keeps its own dedicated wake row, despite seeing a Demon token.
     */
    @Deprecated(
        "Deleted with WP2. Use Identity.actingRoles(state, lookup, player).",
        ReplaceWith("Identity.actingRoles(state, lookup, this).firstOrNull()?.abilityId"),
    )
    val nightRoleId: String?
        get() = if (characterId == "drunk" || characterId == "marionette") {
            shownCharacterId ?: characterId
        } else {
            characterId
        }

    fun team(lookup: (String) -> Character?): Team? =
        characterId?.let { lookup(it)?.team }

    /**
     * True when this seat plays for evil: the explicit [alignment] override
     * when set, otherwise the character's natural team.
     */
    fun isEvil(lookup: (String) -> Character?): Boolean {
        alignment?.let { return it == Alignment.EVIL }
        val base = team(lookup)?.isEvil ?: false
        return base != legacyAlignmentFlipped
    }
}

/** A Fabled in play, with the per-Fabled state the rules need. */
@Serializable
data class FabledEntry(
    val id: String,
    /** Seats this Fabled points at (Revolutionary's pair, Angel's protectee, Djinn's none). */
    val playerIds: List<Long> = emptyList(),
    /** Seats that have used the Fabled's once-per-player affordance (Doomsayer). */
    val spentBy: List<Long> = emptyList(),
    /** Once-per-game Fabled effects (Fibbin, Toymaker's skipped night). */
    val used: Boolean = false,
    /** The storyteller's own wording (Djinn's special rule, Bootlegger's house rules). */
    val note: String = "",
    val addedOnCycle: Int = 0,
    /**
     * Typed keys: "sentinel.outsiderDelta", "stormcatcher.favouredCharacterId",
     * "revolutionary.pair", "spiritofivory.baselineEvil", "toymaker.skipUsed".
     */
    val config: Map<String, String> = emptyMap(),
)

/**
 * Rules the table agreed on that no character puts in force (ux/day-screen §F).
 *
 * A house rule is never inferred and never silent: every field defaults to
 * "off", the storyteller ticks it by hand, and the rule that reads it ORs it
 * with the in-play condition rather than replacing it — a game with a sober
 * Organ Grinder is still a secret-vote game whatever this says.
 *
 * New rules are added as defaulted fields here, so one field on [GameState]
 * carries all of them and old saves keep loading.
 */
@Serializable
data class HouseRules(
    /**
     * Eyes closed for every vote, without an Organ Grinder: the tally, the
     * verdict and the block are hidden behind hold-to-peek all game.
     */
    val secretVotes: Boolean = false,
) {
    /** True while the table is playing entirely by the book. */
    val none: Boolean get() = this == HouseRules()
}

@Serializable
enum class Phase { SETUP, NIGHT, DAY }

/** Everything the storyteller tracks for one game. */
@Serializable
data class GameState(
    val script: Script,
    /** Stable id, stamped at newGame — the key for archived games. */
    val id: String = "",
    val players: List<Player> = emptyList(),
    val phase: Phase = Phase.SETUP,
    /** Night N is followed by day N. */
    val cycle: Int = 1,
    /** Millis timestamp of last modification, for save management. */
    val updatedAt: Long = 0L,

    // ---- history (append-only) ----
    val deaths: List<DeathEvent> = emptyList(),
    val nextDeathId: Long = 1L,
    val nominations: List<Nomination> = emptyList(),
    val executions: List<ExecutionRecord> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val nextLedgerId: Long = 1L,
    val identityLog: List<IdentityRecord> = emptyList(),

    // ---- live rules state ----
    val effects: List<Effect> = emptyList(),
    val nextEffectId: Long = 1L,
    val prompts: List<Prompt> = emptyList(),
    val nextPromptId: Long = 1L,
    /** Abilities held by no fixed seat: the Boffin's grant, the Plague Doctor's. */
    val floatingGrants: List<FloatingGrant> = emptyList(),
    /** Tokens that live in the centre of the grimoire, on no seat. */
    val storytellerReminders: List<PlacedReminder> = emptyList(),
    val fabled: List<FabledEntry> = emptyList(),

    // ---- storyteller decisions ----
    /** Bluff sets, keyed by BluffRequirement.key ("demon", "lunatic:7", "snitch:7"). */
    val bluffSets: Map<String, List<String>> = emptyMap(),
    /** Setup choices and secrets that must survive the whole game. See [Decisions]. */
    val decisions: Map<String, String> = emptyMap(),
    /**
     * Per-game integer tallies, keyed like [decisions] (lead D72). The
     * Yaggababble's utterance count is the first; anything that has to be
     * counted rather than decided belongs here. See [Counters].
     */
    val counters: Map<String, Int> = emptyMap(),
    /** Day the storyteller has declared final (Ferryman, Angel, Fiddler). */
    val finalDayCycle: Int? = null,
    /** Rules the table agreed on that no character puts in force. See [HouseRules]. */
    val houseRules: HouseRules = HouseRules(),

    // ---- night progress ----
    /** Holds [StepKey.token] values. Degrades to bare ability ids for simple steps. */
    val nightStepsDone: Set<String> = emptySet(),
    /**
     * Every seat that has been drunk, poisoned or ability-less at ANY moment
     * tonight — the Acrobat's high-water mark (lead D72).
     *
     * Seeded at dusk from the seats already impaired, added to by
     * `Effects.reconcile` every time the night applies a new impairment, and
     * cleared at dawn once the briefing has been computed. A point-in-time
     * `Status.isImpaired` cannot answer "or BECOME drunk or poisoned tonight",
     * which is why this is stored rather than derived.
     */
    val nightImpaired: Set<Long> = emptySet(),

    // ---- computed-and-frozen briefings ----
    /** The dawn briefing, computed BEFORE tokens were swept, so saves are re-openable. */
    val lastDawn: Briefing? = null,
    val lastDusk: Briefing? = null,

    /**
     * True while the Mastermind's extra day is being played out after the
     * Demon died by execution: if anyone is executed, their team loses.
     */
    val mastermindDayActive: Boolean = false,
    val storytellerNotes: String = "",
    /** Night-screen dim level, 0 = off, 1 = 55%, 2 = 25%. Persisted, not remembered. */
    val dimLevel: Int = 0,

    // ---- migration-only, never read by new code ----
    @SerialName("demonBluffIds") internal val legacyDemonBluffIds: List<String> = emptyList(),
    @SerialName("fabledIds") internal val legacyFabledIds: List<String> = emptyList(),
) {
    // ---- seats ----
    fun player(id: Long): Player? = players.find { it.id == id }

    val seats: List<Player> get() = players.filter { it.seated }
    val alivePlayers: List<Player> get() = seats.filter { it.alive }
    val aliveNonTravellers: List<Player> get() = seats.filter { it.alive && !it.isTraveller }

    /** Alive seats INCLUDING travellers — the Mayor's count (wiki: "Travellers count"). */
    val aliveCountWithTravellers: Int get() = alivePlayers.size

    /** Alive seats EXCLUDING travellers — the evil-wins-at-2 count and Scarlet Woman's 5+. */
    val aliveCountResidents: Int get() = aliveNonTravellers.size

    /**
     * True when the player is alive by the RULES, which a Zombuul's first death is
     * (they are stored dead and register as dead, but the game is not over).
     */
    fun isTrulyAlive(playerId: Long): Boolean {
        val p = player(playerId) ?: return false
        if (p.alive) return true
        return deaths.lastOrNull { it.playerId == playerId && it.resurrectedAtCycle == null }
            ?.registeredOnly == true
    }

    fun updatePlayer(id: Long, transform: (Player) -> Player): GameState =
        copy(players = players.map { if (it.id == id) transform(it) else it })

    /** The two physical neighbours of a seat, over ALL seats including Travellers. */
    fun seatNeighbours(playerId: Long): List<Player> {
        val i = players.indexOfFirst { it.id == playerId }
        if (i < 0 || players.size < 2) return emptyList()
        return listOf(players[(i - 1 + players.size) % players.size], players[(i + 1) % players.size])
    }

    // ---- derived compatibility accessors (read-only) ----
    val fabledIds: List<String> get() = fabled.map { it.id }
    val demonBluffIds: List<String> get() = bluffSets[BluffRequirement.DEMON_KEY].orEmpty()

    /** Votes needed for an execution. Prefer DayRules.voteRules(...) — this ignores abilities. */
    val executionThreshold: Int get() = (aliveCountWithTravellers + 1) / 2

    /** Votes needed for an exile. Never modified by any ability. */
    val exileThreshold: Int get() = (seats.size + 1) / 2

    companion object {
        /** Nominee id used when the STORYTELLER is nominated (Atheist games). */
        const val STORYTELLER_SEAT_ID: Long = -1L
    }
}

/** Typed accessors over [GameState.decisions]. Keys are stable; do not invent new spellings. */
object Decisions {
    const val XAAN_X = "xaan.X"
    const val BOFFIN_GRANT = "boffin.grant"
    const val ALCHEMIST_GRANT = "alchemist.grant"
    const val MEZEPHELES_WORD = "mezepheles.word"
    const val LUNATIC_DEMON = "lunatic.demon"
    const val AMNESIAC_ABILITY = "amnesiac.ability"
    const val OUTSIDER_BRANCH = "setup.outsiderBranch"

    /** "true" = Travellers count towards the 7+ minion/demon-info threshold. */
    const val COUNT_TRAVELLERS_FOR_INFO = "teensyville.countTravellers"

    fun int(state: GameState, key: String): Int? = state.decisions[key]?.toIntOrNull()

    fun bool(state: GameState, key: String, default: Boolean = false): Boolean =
        state.decisions[key]?.toBooleanStrictOrNull() ?: default

    fun set(state: GameState, key: String, value: String): GameState =
        state.copy(decisions = state.decisions + (key to value))

    fun clear(state: GameState, key: String): GameState =
        state.copy(decisions = state.decisions - key)
}

/**
 * Typed accessors over [GameState.counters] (lead D72). Keys are stable and
 * namespaced by character id, exactly like [Decisions].
 */
object Counters {
    /** How many times the Yaggababble said their phrase publicly today. */
    const val YAGGABABBLE_SAID = "yaggababble.said"

    fun get(state: GameState, key: String): Int = state.counters[key] ?: 0

    fun set(state: GameState, key: String, value: Int): GameState =
        state.copy(counters = state.counters + (key to value))

    /** Adds [by] (which may be negative) and never stores a negative total. */
    fun bump(state: GameState, key: String, by: Int = 1): GameState =
        set(state, key, (get(state, key) + by).coerceAtLeast(0))

    fun clear(state: GameState, key: String): GameState =
        state.copy(counters = state.counters - key)
}
