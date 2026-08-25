package com.clocktower.engine

/**
 * The declarative replacement for `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`.
 * Every rule in this registry MUST name a (sourceId, label) that exists in
 * `characters.json` — a GameDataTest asserts it, case-insensitively.
 */
data class TokenRule(
    val sourceId: String,
    /** Official Title Case label. Matched case-insensitively everywhere. */
    val label: String,
    /** The effect this token renders. Null = a pure marker with no rule. */
    val effect: EffectKind? = null,
    val until: Until = Until.FOREVER,
    /** For DUSK_AFTER_N_DAYS. */
    val untilDays: Int = 0,
    val untilEvent: String = "",
    /** How many physical copies the character owns. From `characters.json`, N-listed. */
    val copies: Int = 1,
    val endsWithSource: Boolean = true,
    /** True when the label alone means "this seat is drunk/poisoned" (lead D46). */
    val impairs: Boolean = false,
    val protects: Boolean = false,
    /** Countdown chain: "Drunk 1" -> "Drunk 2" -> "Drunk 3" -> gone. Advanced at dusk. */
    val countdownNext: String? = null,
    /** Two-state pair that can never coexist: Flowergirl, Town Crier (lead D52). */
    val mutexGroup: String = "",
    /** Tokens in one group replace each other: Leviathan "Day 1".."Day 5" (lead D33). */
    val exclusiveGroup: String = "",
    /** Lives in the centre of the grimoire, not on a seat. */
    val grimoireCentre: Boolean = false,
)

/** Token identity and lifetime (WP1). */
object Tokens {

    /** Canonical case-insensitive key. The ONLY legal way to compare tokens. */
    fun key(sourceId: String, label: String): String =
        Character.normalizeId(sourceId) + "/" + label.trim().lowercase()

    /** Canonical key for a placed token. */
    fun key(reminder: PlacedReminder): String = key(reminder.sourceId, reminder.label)

    fun rule(sourceId: String, label: String): TokenRule? = TODO("WP1")

    fun rule(r: PlacedReminder): TokenRule? = rule(r.sourceId, r.label)

    /** Derived from the registry — never hand-maintained. */
    val expiringAtDawn: List<TokenRule> get() = TODO("WP1")

    /** Derived from the registry — never hand-maintained. */
    val expiringAtDusk: List<TokenRule> get() = TODO("WP1")

    /** Advances every countdown chain. Called from `Phases.advancePhase` at dusk. */
    fun advanceCountdowns(state: GameState, at: Until): GameState = TODO("WP1")
}
