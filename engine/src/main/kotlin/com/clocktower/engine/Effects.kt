package com.clocktower.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

/** The three kinds that stop an ability working. */
internal val IMPAIRING: Set<EffectKind> =
    setOf(EffectKind.DRUNK, EffectKind.POISONED, EffectKind.NO_ABILITY)

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

/** The `untilEvent` strings the engine recognises. Never spell one inline. */
object UntilEvents {
    /** Cannibal: poisoned until a good player dies by execution. */
    const val GOOD_DIES_BY_EXECUTION = "goodDiesByExecution"

    /** Lleech: the effect ends when the poisoned host dies. */
    const val HOST_DIES = "hostDies"

    /** Poppy Grower: evil learn each other when the Poppy Grower dies. */
    const val POPPY_GROWER_DIES = "poppyGrowerDies"
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
    /**
     * True for a standing-rule effect: no physical token exists for it, and it is
     * re-derived from the board on every query rather than stored. The grimoire
     * draws these with a dotted ring.
     *
     * Never serialised — nothing derived is ever in `GameState.effects`.
     */
    @Transient val derived: Boolean = false,
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

// ---------------------------------------------------------------------------
// The status query engine
// ---------------------------------------------------------------------------

/**
 * One memoised evaluation over an immutable [GameState].
 *
 * Every public [Status] entry point builds one of these, so the `abilityWorks`
 * recursion of ARCHITECTURE §2.3 is memoised per query and the standing rules
 * are emitted exactly once.
 */
internal class StatusQuery(
    val state: GameState,
    val lookup: (String) -> Character?,
) {
    /** True once a mutual-impairment cycle has been resolved as "both active". */
    var paradox: Boolean = false
        private set

    /** Seats involved in a paradox, for the storyteller's DECIDE prompt. */
    val paradoxSeats: MutableSet<Long> = linkedSetOf()

    /** Innate effects a seat creates for itself — never depend on another standing rule. */
    private val selfStanding: List<Effect> by lazy { Standing.emitSelf(state, lookup) }

    /** Tokens the storyteller placed by hand, projected through the [TokenRule] registry. */
    private val projected: List<Effect> by lazy { Standing.projectTokens(state) }

    /** Everything but the positional standing rules — what those rules are allowed to read. */
    private val baseEffects: List<Effect> by lazy {
        (state.effects + selfStanding + projected).filterNot { expired(it) }
    }

    private val baseByTarget: Map<Long, List<Effect>> by lazy { baseEffects.groupBy { it.targetId } }

    /** Positional rules (Tea Lady, No Dashii, Xaan, Storm Catcher) read [baseEffects]. */
    private val positionalStanding: List<Effect> by lazy { Standing.emitPositional(this) }

    /** Every live effect in the game, oldest first. */
    val allEffects: List<Effect> by lazy {
        (baseEffects + positionalStanding.filterNot { expired(it) }).sortedBy { it.id }
    }

    private val byTarget: Map<Long, List<Effect>> by lazy { allEffects.groupBy { it.targetId } }

    private val memo = HashMap<Long, Boolean>()

    /**
     * Effects whose `active` is being evaluated right now — the cycle guard.
     * Keyed by (id, targetId): the paradox case is two effects with the SAME id,
     * so the id alone does not identify one.
     */
    private val inFlight = HashSet<Pair<Long, Long>>()

    private val baseMemo = HashMap<Long, Boolean>()
    private val baseInFlight = HashSet<Pair<Long, Long>>()

    // ---- time ----------------------------------------------------------

    /** Night N is `2N`, day N is `2N + 1`, so phases are totally ordered. */
    private fun slot(cycle: Int, atNight: Boolean): Long =
        cycle.toLong() * 2 + if (atNight) 0 else 1

    private val now: Long get() = slot(state.cycle, state.phase != Phase.DAY)

    /** True when [e] has passed the boundary its [Until] names. */
    fun expired(e: Effect): Boolean {
        // A countdown step's lifetime is its chain, not the clock: "Drunk 1"
        // becomes "Drunk 2" at dusk rather than expiring there.
        if (e.label.isNotEmpty() &&
            Tokens.rule(e.sourceCharacterId, e.label)?.let(Tokens::isCountdown) == true
        ) {
            return false
        }
        val born = slot(e.createdCycle, e.createdAtNight)
        val bornAtNight = born % 2 == 0L
        return when (e.until) {
            // Swept at the first night -> day boundary at or after creation.
            Until.DAWN -> now >= if (bornAtNight) born + 1 else born + 2
            // Active tonight and tomorrow day; gone at the next day -> night boundary.
            Until.DUSK -> now >= if (bornAtNight) born + 2 else born + 1
            Until.DUSK_AFTER_N_DAYS -> {
                val firstDusk = if (bornAtNight) born + 2 else born + 1
                val days = ((e.untilCycle ?: 0) - e.createdCycle).coerceAtLeast(0)
                now >= firstDusk + 2L * days
            }
            // Consumed explicitly, never by the clock.
            Until.ON_SOURCE_STEP, Until.EVENT, Until.FOREVER, Until.MANUAL -> false
        }
    }

    // ---- the recursion, verbatim from ARCHITECTURE §2.3 -----------------

    fun effectsOn(playerId: Long): List<Effect> = byTarget[playerId].orEmpty()

    fun keepsAbilityWhenDead(characterId: String?): Boolean {
        val id = characterId?.let(Character::normalizeId) ?: return false
        CharacterRules.all[id]?.let { return it.keepsAbilityWhenDead }
        return id in KEEPS_ABILITY_WHEN_DEAD
    }

    /**
     * `active(e, cap)` of ARCHITECTURE §2.3: an effect applies while its source
     * still has their ability.
     *
     * DEVIATION FROM THE §2.3 PSEUDOCODE, deliberate. The published recursion caps
     * the search on `Effect.id` (`it.id < cap`, `min(cap, e.id)`), which makes an
     * effect immune to anything that impaired its source *afterwards*. That
     * contradicts two of WP1's own acceptance cases — the Widow/Innkeeper chain and
     * "Julian poisons Amy, then Evin poisons Julian" — and the wiki rule they come
     * from ("if the sober Innkeeper protects the Chambermaid, but then the Innkeeper
     * becomes drunk, the Chambermaid stops being protected").
     *
     * Termination is kept by guarding on the EFFECT being evaluated rather than on
     * its id: re-entering an effect means two effects sustain each other, which is
     * the paradox — resolved as "both active", exactly as §2.3 requires.
     */
    fun active(e: Effect, cap: Long = Long.MAX_VALUE): Boolean {
        if (expired(e) || e.suspended) return false
        if (!e.endsWithSource) return true
        val source = e.sourcePlayerId ?: return true
        val key = e.id to e.targetId
        if (!inFlight.add(key)) {
            // A sustains B and B sustains A. Neither can be resolved first, so both
            // apply and the storyteller is asked to settle it.
            paradox = true
            paradoxSeats += e.targetId
            return true
        }
        return try {
            abilityWorks(source, cap)
        } finally {
            inFlight.remove(key)
        }
    }

    /** `abilityWorks(pid)` of ARCHITECTURE §2.3, memoised outside a paradox. */
    fun abilityWorks(playerId: Long, cap: Long = Long.MAX_VALUE): Boolean {
        if (inFlight.isEmpty()) memo[playerId]?.let { return it }
        val result = compute(playerId, cap, effectsOn(playerId), ::active)
        if (inFlight.isEmpty()) memo[playerId] = result
        return result
    }

    /**
     * "Does this seat's ability work?", answered from [baseEffects] only.
     *
     * The positional standing rules must ask it: they are themselves part of
     * [allEffects], so consulting the full query while emitting them would recurse
     * through their own lazy. Everything a positional rule legitimately depends on
     * — a Poisoner's token, an Innkeeper's drunk, the seat's own innate state — is
     * already in the base set.
     */
    fun abilityWorksBase(playerId: Long): Boolean {
        if (baseInFlight.isEmpty()) baseMemo[playerId]?.let { return it }
        val result = compute(playerId, Long.MAX_VALUE, baseByTarget[playerId].orEmpty(), ::activeBase)
        if (baseInFlight.isEmpty()) baseMemo[playerId] = result
        return result
    }

    private fun activeBase(e: Effect, cap: Long): Boolean {
        if (expired(e) || e.suspended) return false
        if (!e.endsWithSource) return true
        val source = e.sourcePlayerId ?: return true
        val key = e.id to e.targetId
        if (!baseInFlight.add(key)) return true
        return try {
            abilityWorksBase(source)
        } finally {
            baseInFlight.remove(key)
        }
    }

    private fun compute(
        playerId: Long,
        cap: Long,
        on: List<Effect>,
        isActive: (Effect, Long) -> Boolean,
    ): Boolean {
        val p = state.player(playerId) ?: return false
        val actives = on.filter { !it.suspended && !expired(it) }
        val hasAbilityToken = actives.any { it.kind == EffectKind.HAS_ABILITY && isActive(it, cap) }
        if (!p.alive && !keepsAbilityWhenDead(p.characterId) && !hasAbilityToken) return false
        if (actives.any { it.kind == EffectKind.SOBER_HEALTHY && isActive(it, cap) }) return true
        return actives.none { it.kind in IMPAIRING && isActive(it, cap) }
    }

    /** Live impairing effects on this seat, in creation order. Empty when the ability works. */
    fun impairment(playerId: Long): List<Reason> {
        val actives = effectsOn(playerId)
        if (actives.any { it.kind == EffectKind.SOBER_HEALTHY && active(it, Long.MAX_VALUE) }) {
            return emptyList()
        }
        return actives
            .filter { it.kind in IMPAIRING && active(it, Long.MAX_VALUE) }
            .sortedBy { it.id }
            .map { Reason(it, reasonText(it)) }
    }

    private fun reasonText(e: Effect): String {
        val verb = when (e.kind) {
            EffectKind.DRUNK -> "Drunk"
            EffectKind.POISONED -> "Poisoned"
            else -> "No ability"
        }
        val sourceName = lookup(e.sourceCharacterId)?.name
        val holder = e.sourcePlayerId?.let { state.player(it) }?.name
        return when {
            sourceName == null && e.note.isNotEmpty() -> "$verb — ${e.note}"
            sourceName == null -> "$verb (storyteller)"
            holder == null -> "$verb by the $sourceName"
            else -> "$verb by the $sourceName ($holder)"
        }
    }

    /**
     * Seats caught in a mutual-impairment paradox: A's poison is sustained by B
     * and B's by A, with the same effect id, so neither can be resolved first.
     *
     * Two mechanisms find these, deliberately. [active]'s in-flight guard catches
     * any sustaining cycle as it is walked; this scan additionally catches the
     * equal-id case, where two DERIVED effects (both stamped `standingSince`)
     * impair each other. Two STORED effects never share an id, so their mutual
     * poison resolves deterministically by id and is not a paradox at all.
     */
    fun detectParadox(): Set<Long> {
        val impairing = allEffects.filter {
            it.kind in IMPAIRING && it.endsWithSource && it.sourcePlayerId != null && !it.suspended
        }
        val out = linkedSetOf<Long>()
        for (a in impairing) {
            for (b in impairing) {
                if (a.id != b.id) continue
                if (a.targetId == b.targetId) continue
                if (a.sourcePlayerId == b.targetId && b.sourcePlayerId == a.targetId) {
                    out += a.targetId
                    out += b.targetId
                }
            }
        }
        if (out.isNotEmpty()) paradox = true
        return out + paradoxSeats
    }

    /**
     * SAFE_FROM_DEMON blocks non-kill Demon harm too, read from [baseEffects] only.
     *
     * It MUST use [activeBase]: the positional standing rules call this while
     * [positionalStanding] is still initialising, and the full [active] would
     * recurse back through that lazy — terminating only by tripping the paradox
     * guard, which then raises a DECIDE prompt on a board with no paradox on it.
     */
    fun demonHarmBlockedBase(playerId: Long): Boolean =
        baseByTarget[playerId].orEmpty().any {
            it.kind == EffectKind.SAFE_FROM_DEMON && activeBase(it, Long.MAX_VALUE)
        }

    /**
     * "Does this seat register evil?", answered from [baseEffects] only.
     *
     * The positional standing rules (Tea Lady) need registration, and
     * [Registration] needs the effect list — asking the full query here would
     * recurse forever. Storyteller REGISTERS_AS rulings are stored effects, so
     * the base set already carries everything registration depends on.
     */
    fun registersEvilBase(player: Player): Boolean {
        val ruled = baseByTarget[player.id].orEmpty()
            .filter { it.kind == EffectKind.REGISTERS_AS && !it.suspended && activeBase(it, Long.MAX_VALUE) }
            .mapNotNull { it.characterId?.trim()?.lowercase() }
        // A ruling REPLACES the seat's real side, in both directions: a Recluse
        // ruled evil registers evil, and a Spy ruled good registers good.
        if (ruled.isNotEmpty()) {
            return ruled.any {
                it == "evil" || it == "minion" || it == "demon" || lookup(it)?.team?.isEvil == true
            }
        }
        if (player.alignment == Alignment.EVIL) return true
        return player.isEvil(lookup)
    }

    internal companion object {
        /**
         * Stopgap for `CharacterRule.keepsAbilityWhenDead` until the WP7 registry
         * rows land. The registry always wins when it has a row.
         */
        val KEEPS_ABILITY_WHEN_DEAD: Set<String> = setOf(
            "recluse", "spy", "ravenkeeper", "sweetheart", "moonchild", "klutz", "barber",
            "hatter", "poppygrower", "plaguedoctor", "heretic", "atheist", "politician",
            "banshee", "zealot", "puzzlemaster",
        )
    }
}

/** Status queries over stored effects plus the standing rules (WP1). */
object Status {

    /**
     * Stored + derived effects on this seat, with expiry applied.
     *
     * Suspended effects are RETURNED, not dropped — the grimoire still draws a
     * turned-over token. Callers asking a rules question must skip them; use
     * [live] rather than filtering by hand.
     */
    fun effectsOn(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect> =
        StatusQuery(state, lookup).effectsOn(playerId)

    /**
     * The effects on this seat that are actually in force: unexpired, unsuspended,
     * and with a source whose ability still works. This is the list every rules
     * question wants — "is this Fool spent?", "is this Virgin spent?".
     */
    fun live(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        kind: EffectKind? = null,
    ): List<Effect> {
        val q = StatusQuery(state, lookup)
        return q.effectsOn(playerId).filter {
            (kind == null || it.kind == kind) && !it.suspended && q.active(it)
        }
    }

    /**
     * Every reason this seat's ability is not working, in creation order.
     * Empty when the ability works. A live `SOBER_HEALTHY` (Barista) always wins
     * and returns an empty list.
     */
    fun impairment(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Reason> =
        StatusQuery(state, lookup).impairment(playerId)

    fun isImpaired(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        impairment(state, lookup, playerId).isNotEmpty()

    /**
     * True when this seat's ability functions right now: alive (or on the
     * keeps-ability-when-dead list, or holding a live HAS_ABILITY) and unimpaired.
     */
    fun hasAbility(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        StatusQuery(state, lookup).abilityWorks(playerId, Long.MAX_VALUE)

    /** True when one specific granted/own role works — honours `alwaysFalse` and `worksWhileImpaired`. */
    fun roleWorks(state: GameState, lookup: (String) -> Character?, role: ActingRole): Boolean {
        if (role.alwaysFalse) return false
        val player = state.player(role.playerId) ?: return false
        if (role.worksWhileImpaired) {
            // Impairment is waived, but being dead is not: the seat must still be
            // alive, keep its ability when dead, or hold a LIVE HAS_ABILITY effect.
            if (player.alive) return true
            val q = StatusQuery(state, lookup)
            if (q.keepsAbilityWhenDead(player.characterId)) return true
            return q.effectsOn(role.playerId).any {
                it.kind == EffectKind.HAS_ABILITY && !it.suspended && q.active(it)
            }
        }
        return hasAbility(state, lookup, role.playerId)
    }

    /** Every live protective effect on this seat, oldest first. */
    fun protections(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect> {
        val q = StatusQuery(state, lookup)
        return q.effectsOn(playerId)
            .filter { it.kind.group == EffectGroup.PROTECTED && q.active(it, Long.MAX_VALUE) }
            .sortedBy { it.id }
    }

    /** SAFE_FROM_DEMON blocks non-kill Demon harm too (No Dashii poison on a Soldier). */
    fun demonHarmBlocked(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): Boolean {
        val q = StatusQuery(state, lookup)
        return q.effectsOn(playerId)
            .any { it.kind == EffectKind.SAFE_FROM_DEMON && q.active(it, Long.MAX_VALUE) }
    }

    /** Seats caught in a mutual-impairment paradox the storyteller must settle. */
    fun paradoxSeats(state: GameState, lookup: (String) -> Character?): Set<Long> {
        val q = StatusQuery(state, lookup)
        state.seats.forEach { q.abilityWorks(it.id, Long.MAX_VALUE) }
        return q.detectParadox()
    }
}

// ---------------------------------------------------------------------------
// Standing rules — innate effects, re-derived on every query
// ---------------------------------------------------------------------------

/**
 * The innate rules of ARCHITECTURE §2.3. They are never stored: a seating change,
 * a death or a character change is reflected the instant it happens.
 *
 * Derived effects are stamped `id = holder.standingSince` (0 at setup) so a later
 * placed effect always outranks them in the resolution order.
 */
internal object Standing {

    /** Rules whose effects land on their own holder and read nothing else. */
    fun emitSelf(state: GameState, lookup: (String) -> Character?): List<Effect> = buildList {
        for (p in state.seats) {
            val id = p.characterId?.let(Character::normalizeId) ?: continue
            // WP7 registry rows win outright once they exist.
            if (CharacterRules.all[id]?.standing != null) {
                addAll(CharacterRules.all.getValue(id).standing!!.emit(state, p, lookup))
                continue
            }
            when (id) {
                "soldier" -> add(innate(p, EffectKind.SAFE_FROM_DEMON, id, p.id, state))
                "sailor" -> add(innate(p, EffectKind.CANT_DIE, id, p.id, state))
                "vizier" -> add(innate(p, EffectKind.DAY_IMMUNE, id, p.id, state))
                // "It is just as if this player is the Drunk" — the source is their own
                // character, so the effect must not end with it (ARCHITECTURE §2.3).
                "drunk", "marionette", "lunatic" ->
                    add(innate(p, EffectKind.NO_ABILITY, id, null, state, endsWithSource = false))
                "lleech" -> {
                    val host = hostOf(state) ?: continue
                    add(
                        innate(p, EffectKind.DEATH_TIED_TO, id, p.id, state)
                            .copy(linkedPlayerId = host),
                    )
                }
            }
        }
    }

    /** Rules that read the board: neighbours, teams, and self-protections. */
    fun emitPositional(q: StatusQuery): List<Effect> = buildList {
        val state = q.state
        for (p in state.seats) {
            val id = p.characterId?.let(Character::normalizeId) ?: continue
            if (CharacterRules.all[id]?.standing != null) continue // already emitted
            when (id) {
                "tealady" -> addAll(teaLady(q, p))
                "nodashii" -> addAll(noDashii(q, p))
                "xaan" -> addAll(xaan(q, p))
            }
        }
        for (entry in state.fabled) {
            if (Character.normalizeId(entry.id) != "stormcatcher") continue
            val wanted = entry.config["stormcatcher.favouredCharacterId"] ?: continue
            val seat = state.seats.firstOrNull {
                it.characterId?.let(Character::normalizeId) == Character.normalizeId(wanted)
            } ?: continue
            add(
                Effect(
                    id = seat.standingSince,
                    kind = EffectKind.ONLY_EXECUTION_KILLS,
                    targetId = seat.id,
                    sourceCharacterId = "stormcatcher",
                    sourcePlayerId = null,
                    until = Until.FOREVER,
                    label = "Stormcaught",
                    note = "Storm Catcher: can only die by execution.",
                    createdCycle = state.cycle,
                    createdAtNight = state.phase != Phase.DAY,
                    derived = true,
                ),
            )
        }
    }

    /** "If both your alive neighbors are good, they can't die." */
    private fun teaLady(q: StatusQuery, tea: Player): List<Effect> {
        if (!tea.alive) return emptyList()
        if (!q.abilityWorksBase(tea.id)) return emptyList()
        val neighbours = aliveNeighbours(q.state, tea) ?: return emptyList()
        val (left, right) = neighbours
        val bothGood = listOf(left, right).none { q.registersEvilBase(it) }
        if (!bothGood) return emptyList()
        return listOf(left, right).distinctBy { it.id }.map {
            Effect(
                id = it.standingSince,
                kind = EffectKind.CANT_DIE,
                targetId = it.id,
                sourceCharacterId = "tealady",
                sourcePlayerId = tea.id,
                until = Until.FOREVER,
                label = "Cannot Die",
                note = "Tea Lady (${tea.name}): both alive neighbours are good.",
                createdCycle = q.state.cycle,
                createdAtNight = q.state.phase != Phase.DAY,
                derived = true,
            )
        }
    }

    /** Nearest Townsfolk neighbour each way; a Soldier is never poisoned by a Demon. */
    private fun noDashii(q: StatusQuery, demon: Player): List<Effect> {
        if (!demon.alive) return emptyList()
        if (!q.abilityWorksBase(demon.id)) return emptyList()
        val seats = q.state.players
        val index = seats.indexOfFirst { it.id == demon.id }
        if (index < 0) return emptyList()
        val hit = LinkedHashSet<Long>()
        for (dir in listOf(-1, +1)) {
            var i = (index + dir + seats.size) % seats.size
            while (i != index) {
                val p = seats[i]
                if (p.characterId?.let(q.lookup)?.team == Team.TOWNSFOLK) {
                    if (!q.demonHarmBlockedBase(p.id)) hit += p.id
                    break
                }
                i = (i + dir + seats.size) % seats.size
            }
        }
        return hit.mapNotNull { q.state.player(it) }.map {
            Effect(
                id = it.standingSince,
                kind = EffectKind.POISONED,
                targetId = it.id,
                sourceCharacterId = "nodashii",
                sourcePlayerId = demon.id,
                until = Until.FOREVER,
                label = "Poisoned",
                note = "Poisoned by the No Dashii (${demon.name}'s nearest Townsfolk neighbour)",
                createdCycle = q.state.cycle,
                createdAtNight = q.state.phase != Phase.DAY,
                derived = true,
            )
        }
    }

    /** "On night X, all Townsfolk are poisoned until dusk" — by TRUE team, alive Xaan only. */
    private fun xaan(q: StatusQuery, xaan: Player): List<Effect> {
        if (!xaan.alive) return emptyList()
        val x = Decisions.int(q.state, Decisions.XAAN_X) ?: return emptyList()
        if (q.state.cycle != x) return emptyList()
        if (!q.abilityWorksBase(xaan.id)) return emptyList()
        return q.state.seats
            .filter { it.characterId?.let(q.lookup)?.team == Team.TOWNSFOLK }
            .map {
                Effect(
                    id = it.standingSince,
                    kind = EffectKind.POISONED,
                    targetId = it.id,
                    sourceCharacterId = "xaan",
                    sourcePlayerId = xaan.id,
                    until = Until.DUSK,
                    label = "Poisoned",
                    note = "Xaan: night $x poisons every Townsfolk until dusk.",
                    createdCycle = q.state.cycle,
                    createdAtNight = true,
                    derived = true,
                )
            }
    }

    /**
     * Storyteller tokens projected through the [TokenRule] registry, so a
     * hand-placed `poisoner/Poisoned` obeys exactly the same recursion as an
     * engine-placed one — matched on `(sourceId, label)`, never by substring.
     */
    fun projectTokens(state: GameState): List<Effect> {
        var next = state.nextEffectId
        val out = mutableListOf<Effect>()
        for (p in state.players) {
            for (r in p.reminders) {
                val rule = Tokens.rule(r) ?: continue
                val kind = rule.effect ?: if (rule.impairs) EffectKind.POISONED else continue
                out += Effect(
                    id = next++,
                    kind = kind,
                    targetId = p.id,
                    linkedPlayerId = r.targetPlayerId,
                    characterId = r.characterId,
                    sourceCharacterId = rule.sourceId,
                    sourcePlayerId = sourceSeat(state, rule.sourceId),
                    until = rule.until,
                    untilCycle = if (rule.untilDays > 0) r.placedCycle + rule.untilDays else null,
                    untilEvent = rule.untilEvent.ifEmpty { null },
                    endsWithSource = rule.endsWithSource,
                    label = rule.label,
                    note = r.note,
                    createdCycle = r.placedCycle.takeIf { it > 0 } ?: state.cycle,
                    createdAtNight = state.phase != Phase.DAY,
                )
            }
        }
        return out
    }

    /** The one seat holding [characterId], or null when it is ambiguous or absent. */
    private fun sourceSeat(state: GameState, characterId: String): Long? {
        val id = Character.normalizeId(characterId)
        if (id == Tokens.STORYTELLER_SOURCE) return null
        val seats = state.seats.filter { it.characterId?.let(Character::normalizeId) == id }
        return seats.singleOrNull()?.id
    }

    /** The Lleech's host: the seat carrying its `Poisoned` token or effect. */
    private fun hostOf(state: GameState): Long? {
        state.effects.firstOrNull {
            it.sourceCharacterId == "lleech" && it.kind == EffectKind.POISONED
        }?.let { return it.targetId }
        return state.players.firstOrNull { p ->
            p.reminders.any { Tokens.key(it) == Tokens.key("lleech", "Poisoned") }
        }?.id
    }

    /**
     * The nearest ALIVE seat each way, skipping the dead between them
     * (wiki: "not including any dead players sitting between them").
     * Null when the circle cannot supply two distinct living neighbours.
     */
    private fun aliveNeighbours(state: GameState, of: Player): Pair<Player, Player>? {
        val seats = state.players.filter { it.seated }
        val index = seats.indexOfFirst { it.id == of.id }
        if (index < 0 || seats.size < 3) return null
        fun walk(dir: Int): Player? {
            var i = (index + dir + seats.size) % seats.size
            while (i != index) {
                if (seats[i].alive) return seats[i]
                i = (i + dir + seats.size) % seats.size
            }
            return null
        }
        val left = walk(-1) ?: return null
        val right = walk(+1) ?: return null
        if (left.id == right.id) return null
        return left to right
    }

    private fun innate(
        holder: Player,
        kind: EffectKind,
        sourceCharacterId: String,
        sourcePlayerId: Long?,
        state: GameState,
        endsWithSource: Boolean = true,
    ): Effect = Effect(
        id = holder.standingSince,
        kind = kind,
        targetId = holder.id,
        sourceCharacterId = sourceCharacterId,
        sourcePlayerId = sourcePlayerId,
        until = Until.FOREVER,
        endsWithSource = endsWithSource,
        label = "",
        createdCycle = state.cycle,
        createdAtNight = state.phase != Phase.DAY,
        derived = true,
    )
}

// ---------------------------------------------------------------------------
// Effect lifecycle
// ---------------------------------------------------------------------------

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
    ): Placement {
        val rule = if (label.isEmpty()) null else Tokens.rule(sourceCharacterId, label)
        val id = state.nextEffectId
        val effect = Effect(
            id = id,
            kind = kind,
            targetId = target,
            linkedPlayerId = linkedPlayerId,
            characterId = characterId,
            sourceCharacterId = sourceCharacterId,
            sourcePlayerId = sourcePlayerId,
            until = until,
            untilCycle = if (rule != null && rule.untilDays > 0) {
                state.cycle + rule.untilDays
            } else {
                null
            },
            untilEvent = rule?.untilEvent?.ifEmpty { null },
            endsWithSource = endsWithSource,
            label = label,
            note = note,
            createdCycle = state.cycle,
            createdAtNight = state.phase != Phase.DAY,
            causeEventId = causeEventId,
        )

        var live = state.effects
        var displaced: Effect? = null

        if (rule != null) {
            // Two-state pairs can never coexist on one seat (lead D52).
            if (rule.mutexGroup.isNotEmpty()) {
                live = live.filterNot {
                    it.targetId == target && Tokens.rule(it.sourceCharacterId, it.label)
                        ?.mutexGroup == rule.mutexGroup
                }
            }
            // Tokens in one group replace each other anywhere on the board (lead D33).
            if (rule.exclusiveGroup.isNotEmpty()) {
                live = live.filterNot {
                    Tokens.rule(it.sourceCharacterId, it.label)?.exclusiveGroup == rule.exclusiveGroup
                }
            }
            // Only `copies` physical tokens exist; the oldest is displaced, not lost.
            val sameToken = live.filter {
                Tokens.key(it.sourceCharacterId, it.label) == Tokens.key(sourceCharacterId, label) &&
                    it.sourcePlayerId == sourcePlayerId
            }
            if (sameToken.size >= rule.copies) {
                displaced = sameToken.minByOrNull { it.id }
                live = live.filterNot { it.id == displaced?.id }
            }
        }

        return Placement(
            state = state.copy(effects = live + effect, nextEffectId = id + 1),
            effect = effect,
            displaced = displaced,
        )
    }

    fun remove(state: GameState, effectId: Long): GameState =
        state.copy(effects = state.effects.filterNot { it.id == effectId })

    fun suspend(state: GameState, effectId: Long, suspended: Boolean): GameState =
        state.copy(
            effects = state.effects.map {
                if (it.id == effectId) it.copy(suspended = suspended) else it
            },
        )

    /** Drops every effect whose `sourceCharacterId` is [characterId] on any seat. */
    fun removeBySource(state: GameState, characterId: String): GameState {
        val id = Character.normalizeId(characterId)
        return state.copy(
            effects = state.effects.filterNot {
                Character.normalizeId(it.sourceCharacterId) == id
            },
        )
    }

    /** Drops every effect and prompt stamped with [causeEventId] — used by `revive`. */
    fun rollback(state: GameState, causeEventId: Long): GameState = state.copy(
        effects = state.effects.filterNot { it.causeEventId == causeEventId },
        prompts = state.prompts.filterNot { it.causeEventId == causeEventId },
    )

    /**
     * Re-evaluates `endsWithSource` teardown and standing rules. Call after every
     * kill, resurrect, character change, and at each phase boundary.
     *
     * Drops effects the clock has retired and effects pointing at a seat that no
     * longer exists, records the [LedgerKind.IMPAIRMENT_SPAN] history of lead D41,
     * and raises the paradox prompt of ARCHITECTURE §2.3 when two effects impair
     * each other.
     */
    fun reconcile(state: GameState, lookup: (String) -> Character?): GameState {
        val seats = state.players.map { it.id }.toSet()
        val kept = StatusQuery(state, lookup).let { q ->
            state.effects.filterNot { q.expired(it) || it.targetId !in seats }
        }
        val swept = if (kept.size == state.effects.size) state else state.copy(effects = kept)

        // One query for both passes: the impairment of every seat is what the span
        // history needs and what reveals a paradox, so it is computed exactly once.
        val q = StatusQuery(swept, lookup)
        val impairedNow = swept.seats.associate { it.id to q.impairment(it.id) }
        return raiseParadoxPrompt(recordImpairmentSpans(swept, impairedNow), q.detectParadox())
    }

    /**
     * Opens and closes [LedgerKind.IMPAIRMENT_SPAN] entries so "was seat X impaired
     * during window Y" is answerable without a second store (lead D41).
     */
    private fun recordImpairmentSpans(
        state: GameState,
        impairedNow: Map<Long, List<Reason>>,
    ): GameState {
        val open = state.ledger
            .filter { it.kind == LedgerKind.IMPAIRMENT_SPAN && it.resolvedCycle == null }
            .associateBy { it.actorId }
        var ledger = state.ledger
        var nextId = state.nextLedgerId
        var changed = false
        for (p in state.seats) {
            val reasons = impairedNow[p.id].orEmpty()
            val span = open[p.id]
            if (reasons.isNotEmpty() && span == null) {
                ledger = ledger + LedgerEntry(
                    id = nextId++,
                    cycle = state.cycle,
                    atNight = state.phase != Phase.DAY,
                    kind = LedgerKind.IMPAIRMENT_SPAN,
                    sourceId = "status",
                    actorId = p.id,
                    text = reasons.joinToString("; ") { it.text },
                    impaired = true,
                )
                changed = true
            } else if (reasons.isEmpty() && span != null) {
                ledger = ledger.map {
                    if (it.id == span.id) it.copy(resolvedCycle = state.cycle) else it
                }
                changed = true
            }
        }
        return if (changed) state.copy(ledger = ledger, nextLedgerId = nextId) else state
    }

    /** One DECIDE prompt per live paradox, never duplicated. */
    private fun raiseParadoxPrompt(state: GameState, seats: Set<Long>): GameState {
        if (seats.size < 2) return state
        val names = seats.mapNotNull { state.player(it)?.name }
        if (names.size < 2) return state
        val title = "Paradox: ${names[0]} and ${names[1]} poison each other. " +
            "Tap the one whose ability works."
        if (state.prompts.any { !it.resolved && it.title == title }) return state
        return Prompts.queue(
            state,
            Prompt(
                id = 0,
                at = BriefingSlot.NOW,
                kind = PromptKind.DECIDE,
                sourceId = "status",
                targetIds = seats.toList(),
                title = title,
                detail = "Both effects are treated as active until you decide. " +
                    "Suspending one settles it.",
            ),
        )
    }

    /** The tokens to draw for one seat: effect-backed first, then storyteller free tokens. */
    fun rendered(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): List<RenderedToken> {
        val q = StatusQuery(state, lookup)
        val player = state.player(playerId) ?: return emptyList()
        val fromEffects = q.effectsOn(playerId)
            .filter { it.label.isNotEmpty() }
            .sortedWith(compareBy({ it.kind.group.priority }, { it.id }))
            .map {
                RenderedToken(
                    sourceId = it.sourceCharacterId,
                    label = it.label,
                    group = it.kind.group,
                    effectId = it.id,
                    effect = it,
                    expiryText = expiryText(state, it),
                    derived = it.derived,
                    suspended = it.suspended,
                    note = it.note,
                )
            }
        // A hand-placed token that projected into an effect is already drawn above.
        val drawn = fromEffects.map { Tokens.key(it.sourceId, it.label) }.toMutableList()
        val free = player.reminders.mapNotNull { r ->
            val key = Tokens.key(r)
            if (drawn.remove(key)) {
                null
            } else {
                RenderedToken(
                    sourceId = r.sourceId,
                    label = r.label,
                    group = Tokens.rule(r)?.effect?.group ?: EffectGroup.MARKER,
                    expiryText = if (r.placedCycle > 0) "placed N${r.placedCycle}" else "",
                    note = r.note,
                )
            }
        }
        return fromEffects + free
    }

    /** "expires at dusk", "placed N3" — the line under a token in the grimoire. */
    private fun expiryText(state: GameState, e: Effect): String = when (e.until) {
        Until.DAWN -> "expires at dawn"
        Until.DUSK -> "expires at dusk"
        Until.DUSK_AFTER_N_DAYS -> "expires at dusk of day ${e.untilCycle ?: state.cycle}"
        Until.ON_SOURCE_STEP -> "until ${e.sourceCharacterId} acts again"
        Until.EVENT -> "until ${e.untilEvent.orEmpty()}"
        Until.MANUAL -> "clear by hand"
        Until.FOREVER -> if (e.createdCycle > 0) "placed N${e.createdCycle}" else ""
    }

    // ---- storyteller free tokens (moved verbatim from GameActions in WP0) ----

    /**
     * Adds a storyteller-placed token to a seat.
     *
     * A token with no source is the permanent-poison bug of the audit: an empty
     * `sourceId` is rewritten to [Tokens.STORYTELLER_SOURCE] so no code path can
     * ever store `PlacedReminder(sourceId = "")`.
     */
    fun addReminder(state: GameState, playerId: Long, reminder: PlacedReminder): GameState =
        state.updatePlayer(playerId) { it.copy(reminders = it.reminders + sourced(reminder, state)) }

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
        val fixed = sourced(reminder, state)
        val key = Tokens.key(fixed)
        val cleared = state.copy(
            players = state.players.map { p ->
                p.copy(reminders = p.reminders.filterNot { Tokens.key(it) == key })
            },
        )
        return addReminder(cleared, playerId, fixed)
    }

    fun removeReminder(state: GameState, playerId: Long, index: Int): GameState =
        state.updatePlayer(playerId) {
            it.copy(reminders = it.reminders.filterIndexed { i, _ -> i != index })
        }

    /** Adds a token to the centre of the grimoire (Leviathan Day N, Minstrel, Fang Gu Once). */
    fun addCentreReminder(state: GameState, reminder: PlacedReminder): GameState {
        val fixed = sourced(reminder, state)
        val rule = Tokens.rule(fixed)
        val kept = if (rule != null && rule.exclusiveGroup.isNotEmpty()) {
            state.storytellerReminders.filterNot {
                Tokens.rule(it)?.exclusiveGroup == rule.exclusiveGroup
            }
        } else {
            state.storytellerReminders
        }
        return state.copy(storytellerReminders = kept + fixed)
    }

    /** Never `sourceId = ""` — an unsourced token becomes a storyteller token. */
    private fun sourced(r: PlacedReminder, state: GameState): PlacedReminder {
        val source = r.sourceId.ifBlank { Tokens.STORYTELLER_SOURCE }
        val cycle = if (r.placedCycle > 0) r.placedCycle else state.cycle
        return if (source == r.sourceId && cycle == r.placedCycle) {
            r
        } else {
            r.copy(sourceId = source, placedCycle = cycle)
        }
    }
}
