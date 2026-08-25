package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What a rule-bearing effect does. The rules classifier — see [EffectGroup] for display. */
@Serializable
enum class EffectKind {
    // impairing
    DRUNK, POISONED, NO_ABILITY,

    // anti-impairing / ability-granting
    /** Barista — beats every impairment. */
    SOBER_HEALTHY,

    /** Bone Collector, Vigormortis-preserved Minion, Pixie. */
    HAS_ABILITY,

    // protective (see Deaths.PROTECTS — every kind declares which causes it blocks)
    /** Monk SAFE, Soldier (innate). */
    SAFE_FROM_DEMON,

    /** Innkeeper SAFE. */
    CANT_DIE_TONIGHT,

    /** Sailor (innate, self), Tea Lady CANNOT DIE. */
    CANT_DIE,

    /** Storm Catcher STORMCAUGHT. */
    ONLY_EXECUTION_KILLS,

    /** Devil's Advocate. */
    SURVIVES_EXECUTION,

    /** Vizier (innate). */
    DAY_IMMUNE,

    /** Lleech -> host (linkedPlayerId = the host). */
    DEATH_TIED_TO,

    // suppression
    /** Lycanthrope, Princess, Exorcist-silenced Demon, Toymaker. */
    DEMON_CANNOT_KILL,

    /** Beggar without a token, Golem spent, Butler under secret voting. */
    NO_VOTE, NO_NOMINATE,

    // state
    /** Cerenovus / Mutant / Harpy / Pixie — payload = characterId. */
    MAD,

    /** Spy, Recluse, Faux Paw, Revolutionary — payload = characterId/team. */
    REGISTERS_AS,

    /** A once-per-game ability has been used. */
    SPENT,

    /** Anything with no rule: Know, Correct, Grandchild, Visitor… */
    MARKER,
}

/** Display bucket, glyph and pip priority. Derived from [EffectKind] — do not store. */
@Serializable
enum class EffectGroup(val glyph: String, val priority: Int) {
    PENDING_DEATH("†", 0),
    IMPAIRED("!", 1),
    PROTECTED("+", 2),
    MADNESS("M", 3),
    IDENTITY("=", 4),
    ABILITY("O", 5),
    INFO("i", 6),
    MARKER("·", 7),
}

/** The display bucket for one effect kind. */
val EffectKind.group: EffectGroup
    get() = when (this) {
        EffectKind.DRUNK, EffectKind.POISONED, EffectKind.NO_ABILITY -> EffectGroup.IMPAIRED
        EffectKind.SAFE_FROM_DEMON, EffectKind.CANT_DIE_TONIGHT, EffectKind.CANT_DIE,
        EffectKind.ONLY_EXECUTION_KILLS, EffectKind.SURVIVES_EXECUTION, EffectKind.DAY_IMMUNE,
        EffectKind.DEATH_TIED_TO, EffectKind.DEMON_CANNOT_KILL,
        -> EffectGroup.PROTECTED
        EffectKind.MAD -> EffectGroup.MADNESS
        EffectKind.REGISTERS_AS -> EffectGroup.IDENTITY
        EffectKind.SOBER_HEALTHY, EffectKind.HAS_ABILITY, EffectKind.SPENT,
        EffectKind.NO_VOTE, EffectKind.NO_NOMINATE,
        -> EffectGroup.ABILITY
        EffectKind.MARKER -> EffectGroup.MARKER
    }

/**
 * When an effect stops applying. `SOURCE_LOSES_ABILITY` is additionally applied
 * on top of every value, through the `Status.impairment` recursion.
 */
@Serializable
enum class Until {
    /** "tonight" */
    DAWN,

    /** "until dusk" / "tonight and tomorrow day" */
    DUSK,

    /** Minstrel (n = 1), Courtier (n = 2 => 3 nights and 3 days). */
    DUSK_AFTER_N_DAYS,

    /** Consumed at the source's next night step (the Pukka's poison). */
    ON_SOURCE_STEP,

    /** untilEvent: "goodDiesByExecution", "hostDies", "poppyGrowerDies". */
    EVENT,
    FOREVER,

    /** Only the storyteller removes it (night-1 "start knowing" tokens). */
    MANUAL,
}

/**
 * A typed, dated, sourced rule applying to one seat. Effects — not tokens — decide
 * who is drunk, poisoned, protected, mad or spent.
 */
@Serializable
data class Effect(
    /** Monotonic from [GameState.nextEffectId]. Doubles as the resolution-order key. */
    val id: Long,
    val kind: EffectKind,
    val targetId: Long,
    /** Lleech host, Grandmother grandchild, Harpy's 2nd. */
    val linkedPlayerId: Long? = null,
    /** Character payload: the Cerenovus's mad character, the REGISTERS_AS character. */
    val characterId: String? = null,
    /** Who created it. "" = storyteller / house rule. */
    val sourceCharacterId: String,
    /** The seat whose ability sustains it. Null = no living source to check. */
    val sourcePlayerId: Long? = null,
    val until: Until,
    /** Absolute cycle at which a DAWN/DUSK/DUSK_AFTER_N_DAYS expiry fires. */
    val untilCycle: Int? = null,
    val untilEvent: String? = null,
    /** False only for effects that explicitly outlive their source (Sweetheart, Puzzlemaster). */
    val endsWithSource: Boolean = true,
    /** Grimoire token text. "" renders no token (Soldier, Sailor, Vizier are innate). */
    val label: String = "",
    /** Storyteller-visible explanation, shown on tap. */
    val note: String = "",
    val createdCycle: Int,
    val createdAtNight: Boolean,
    /** The DeathEvent / night action that created it, for exact rollback by `revive`. */
    val causeEventId: Long? = null,
    /** Storyteller override: keep the token, suppress the rule (the physical "turn it over"). */
    val suspended: Boolean = false,
)

/** One innate rule a character's mere presence creates. Evaluated on every query, never stored. */
class StandingRule(
    val characterId: String,
    val emit: (state: GameState, holder: Player, lookup: (String) -> Character?) -> List<Effect>,
)

/** Why a seat's ability is not working, in storyteller prose. */
data class Reason(val effect: Effect, val text: String)

/** One token as the grimoire draws it. */
data class RenderedToken(
    val sourceId: String,
    val label: String,
    val group: EffectGroup,
    val effectId: Long? = null,
    /** Null for a storyteller free token. */
    val effect: Effect? = null,
    /** "expires at dusk", "placed N3". */
    val expiryText: String = "",
    /** No Dashii-style: no physical token, dotted ring. */
    val derived: Boolean = false,
    val suspended: Boolean = false,
    val note: String = "",
)

/** Status queries over stored effects plus the standing rules (WP1). */
object Status {

    /** Stored + derived effects on this seat, with expiry and suspension applied. */
    fun effectsOn(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect> =
        TODO("WP1")

    /**
     * Every reason this seat's ability is not working, in creation order.
     * Empty when the ability works. A live `SOBER_HEALTHY` (Barista) always wins
     * and returns an empty list.
     */
    fun impairment(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Reason> =
        TODO("WP1")

    fun isImpaired(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        impairment(state, lookup, playerId).isNotEmpty()

    /**
     * True when this seat's ability functions right now: alive (or on the
     * keeps-ability-when-dead list, or holding a live HAS_ABILITY) and unimpaired.
     */
    fun hasAbility(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        TODO("WP1")

    /** True when one specific granted/own role works — honours `alwaysFalse` and `worksWhileImpaired`. */
    fun roleWorks(state: GameState, lookup: (String) -> Character?, role: ActingRole): Boolean =
        TODO("WP1")

    fun protections(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect> =
        TODO("WP1")

    /** SAFE_FROM_DEMON blocks non-kill Demon harm too (No Dashii poison on a Soldier). */
    fun demonHarmBlocked(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        TODO("WP1")
}

/**
 * Effect lifecycle. WP0 moved the reminder primitives here verbatim; WP1
 * implements the typed effect layer that renders them.
 */
object Effects {

    /** The state plus the effect that was placed, and any copy it displaced. */
    data class Placement(val state: GameState, val effect: Effect, val displaced: Effect? = null)

    /**
     * Places an effect and (when [label] is non-empty) makes it render as a
     * token. Honours `copies`, `mutexGroup` and `exclusiveGroup`: the oldest copy is
     * displaced, never silently lost — the displaced placement is returned for the
     * snackbar and the undo label.
     */
    fun place(
        state: GameState,
        target: Long,
        kind: EffectKind,
        sourceCharacterId: String,
        sourcePlayerId: Long?,
        until: Until,
        label: String = "",
        note: String = "",
        characterId: String? = null,
        linkedPlayerId: Long? = null,
        endsWithSource: Boolean = true,
        causeEventId: Long? = null,
    ): Placement = TODO("WP1")

    fun remove(state: GameState, effectId: Long): GameState = TODO("WP1")

    fun suspend(state: GameState, effectId: Long, suspended: Boolean): GameState = TODO("WP1")

    /** Drops every effect whose `sourceCharacterId` is [characterId] on any seat. */
    fun removeBySource(state: GameState, characterId: String): GameState = TODO("WP1")

    /** Drops every effect and prompt stamped with [causeEventId] — used by `revive`. */
    fun rollback(state: GameState, causeEventId: Long): GameState = TODO("WP1")

    /**
     * Re-evaluates `endsWithSource` teardown and standing rules. Call after every
     * kill, resurrect, character change, and at each phase boundary.
     */
    fun reconcile(state: GameState, lookup: (String) -> Character?): GameState = TODO("WP1")

    /** The tokens to draw for one seat: effect-backed first, then storyteller free tokens. */
    fun rendered(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): List<RenderedToken> = TODO("WP1")

    // ---- storyteller free tokens (moved verbatim from GameActions in WP0) ----

    /** Adds a storyteller-placed token to a seat. */
    fun addReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState =
        state.updatePlayer(playerId) { it.copy(reminders = it.reminders + reminder) }

    /**
     * Places a reminder that only exists once in the grimoire (Poisoner's
     * poison, Monk's Safe...): removes the same token from every other seat
     * first, so nightly choices move instead of accumulating.
     */
    fun placeExclusiveReminder(
        state: GameState,
        playerId: Long,
        reminder: PlacedReminder,
    ): GameState {
        val cleared = state.copy(
            players = state.players.map { p ->
                p.copy(
                    reminders = p.reminders.filterNot {
                        it.sourceId == reminder.sourceId && it.label == reminder.label
                    },
                )
            },
        )
        return addReminder(cleared, playerId, reminder)
    }

    fun removeReminder(state: GameState, playerId: Long, index: Int): GameState =
        state.updatePlayer(playerId) {
            it.copy(reminders = it.reminders.filterIndexed { i, _ -> i != index })
        }
}
