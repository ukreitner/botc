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
    /**
     * `DEMON_CANNOT_KILL` tokens only: how far the suppression reaches (lead
     * D68). The Exorcist SILENCES and a deferred kill still lands; the Princess
     * and the Lycanthrope stop it.
     */
    val suppression: KillSuppression = KillSuppression.SILENCED,
) {
    /** The canonical case-insensitive identity of this rule. */
    val key: String get() = Tokens.key(sourceId, label)
}

/**
 * Token identity and lifetime (WP1).
 *
 * [BASE] is the lifecycle table of ARCHITECTURE §2.4. Per-character registry
 * rows (`CharacterRule.tokens`, WP7) are layered on top and win on a key clash,
 * so a character package can refine its own token without editing this file.
 */
object Tokens {

    /** Canonical case-insensitive key. The ONLY legal way to compare tokens. */
    fun key(sourceId: String, label: String): String =
        Character.normalizeId(sourceId) + "/" + label.trim().lowercase()

    /** Canonical key for a placed token. */
    fun key(reminder: PlacedReminder): String = key(reminder.sourceId, reminder.label)

    /**
     * The sourceId every storyteller-placed generic token carries. Never "".
     * Aliases WP0's [STORYTELLER_SOURCE_ID] so the load migration and the
     * registry can never drift apart.
     */
    const val STORYTELLER_SOURCE: String = STORYTELLER_SOURCE_ID

    /** A token with no physical limit — the generic storyteller markers. */
    const val UNLIMITED: Int = Int.MAX_VALUE

    // ---------------------------------------------------------------- registry

    /** `(sourceId, label)` of every once-per-game spend mark in the official data. */
    private val SPENT_MARKS: List<Pair<String, String>> = listOf(
        "slayer" to "No Ability",
        "virgin" to "No Ability",
        "courtier" to "No Ability",
        "fool" to "No Ability",
        "professor" to "No Ability",
        "assassin" to "No Ability",
        "judge" to "No Ability",
        "artist" to "No Ability",
        "seamstress" to "No Ability",
        "bonecollector" to "No Ability",
        "engineer" to "No Ability",
        "fisherman" to "No Ability",
        "huntsman" to "No Ability",
        "nightwatchman" to "No Ability",
        "mezepheles" to "No Ability",
        "fibbin" to "No Ability",
        "damsel" to "Guess Used",
        "puzzlemaster" to "Guess Used",
        "philosopher" to "Is The Philosopher",
    )

    /** `(sourceId, label, copies)` markers swept at dawn — no rule, pure memory. */
    private val DAWN_MARKERS: List<Triple<String, String, Int>> = listOf(
        Triple("undertaker", "Died Today", 1),
        Triple("godfather", "Died Today", 1),
        Triple("godfather", "Dead", 1),
        Triple("zombuul", "Died Today", 1),
        Triple("zombuul", "Dead", 1),
        Triple("juggler", "Correct", 5),
        Triple("mathematician", "Abnormal", 5),
        Triple("barber", "Haircuts Tonight", 1),
        Triple("hatter", "Tea Party Tonight", 1),
        Triple("poppygrower", "Evil Wakes", 1),
        Triple("cacklejack", "Not Me", 1),
        Triple("acrobat", "Chosen", 1),
        Triple("acrobat", "Dead", 1),
        Triple("organgrinder", "About To Die", 1),
        Triple("legion", "About To Die", 1),
        Triple("legion", "Dead", 1),
        Triple("lunatic", "Chosen", 3),
        Triple("po", "Dead", 3),
        Triple("pukka", "Dead", 1),
        Triple("shabaloth", "Dead", 2),
        Triple("shabaloth", "Alive", 1),
        Triple("nodashii", "Dead", 1),
        Triple("vigormortis", "Dead", 1),
        Triple("lleech", "Dead", 1),
        Triple("lycanthrope", "Dead", 1),
        Triple("assassin", "Dead", 1),
        Triple("gossip", "Dead", 1),
        Triple("yaggababble", "Dead", 3),
        Triple("gambler", "Dead", 1),
        Triple("moonchild", "Dead", 1),
        Triple("kazali", "Dead", 1),
        Triple("grandmother", "Dead", 1),
        Triple("fanggu", "Dead", 1),
        Triple("lilmonsta", "Dead", 1),
        Triple("professor", "Alive", 1),
    )

    /**
     * `(sourceId, label, copies)` markers that never expire on their own.
     *
     * NOTE FOR THE LEAD: ARCHITECTURE §2.4 files `po/3 Attacks` and
     * `mezepheles/Turns Evil` under `Until.DAWN`. Both are placed on one night
     * and read on a LATER one — the Po's token arms its next three-kill step,
     * and "Turns Evil" is a permanent alignment change — so a dawn sweep would
     * destroy them before they are used. They are FOREVER here, matching
     * night-engine §4 defect 28 and the BMR digest.
     */
    private val PERMANENT_MARKERS: List<Triple<String, String, Int>> = listOf(
        Triple("po", "3 Attacks", 1),
        Triple("mezepheles", "Turns Evil", 1),
        Triple("xaan", "X", 1),
        Triple("widow", "Know", 1),
        Triple("fearmonger", "Fear", 1),
        Triple("eviltwin", "Twin", 1),
        Triple("grandmother", "Grandchild", 1),
        Triple("scarletwoman", "Is The Demon", 1),
        Triple("drunk", "Is The Drunk", 1),
        Triple("marionette", "Is The Marionette", 1),
        Triple("lilmonsta", "Is The Demon", 1),
        Triple("alchemist", "Is The Alchemist", 1),
        Triple("apprentice", "Is The Apprentice", 1),
        Triple("plaguedoctor", "Storyteller Ability", 1),
        Triple("cannibal", "Lunch", 1),
        Triple("alhadikhia", "1", 1),
        Triple("alhadikhia", "2", 1),
        Triple("alhadikhia", "3", 1),
        Triple("hermit", "1", 1),
        Triple("hermit", "2", 1),
        Triple("hermit", "3", 1),
    )

    private fun MutableList<TokenRule>.addCountdown(
        sourceId: String,
        labels: List<String>,
        group: String,
        grimoireCentre: Boolean = false,
    ) {
        labels.forEachIndexed { i, label ->
            add(
                TokenRule(
                    sourceId = sourceId,
                    label = label,
                    until = Until.DUSK,
                    countdownNext = labels.getOrNull(i + 1),
                    exclusiveGroup = group,
                    grimoireCentre = grimoireCentre,
                ),
            )
        }
    }

    /**
     * The WP1 lifecycle table. Every row's (sourceId, label) is present in
     * `characters.json`, with the copy count the official data lists.
     */
    internal val BASE: List<TokenRule> = buildList {
        // ---- generic storyteller tokens (ARCHITECTURE §2.4, first bullet) ----
        // UNLIMITED copies: these are not physical character tokens, so the
        // storyteller can mark as many seats as a ruling needs. With copies = 1
        // a second hand-placed Poisoned would displace the first.
        add(
            TokenRule(
                STORYTELLER_SOURCE, "Poisoned", EffectKind.POISONED, Until.DUSK,
                copies = UNLIMITED, impairs = true,
            ),
        )
        add(
            TokenRule(
                STORYTELLER_SOURCE, "Drunk", EffectKind.DRUNK, Until.DUSK,
                copies = UNLIMITED, impairs = true,
            ),
        )
        add(
            TokenRule(
                STORYTELLER_SOURCE, "Protected", EffectKind.SAFE_FROM_DEMON, Until.DAWN,
                copies = UNLIMITED, protects = true,
            ),
        )
        add(TokenRule(STORYTELLER_SOURCE, "Mad", EffectKind.MAD, Until.DUSK, copies = UNLIMITED))

        // ---- protection ----
        add(TokenRule("monk", "Safe", EffectKind.SAFE_FROM_DEMON, Until.DAWN, protects = true))
        add(TokenRule("innkeeper", "Safe", EffectKind.CANT_DIE_TONIGHT, Until.DAWN, copies = 2, protects = true))
        add(TokenRule("tealady", "Cannot Die", EffectKind.CANT_DIE, Until.FOREVER, copies = 2, protects = true))
        add(
            TokenRule(
                "devilsadvocate", "Survives Execution", EffectKind.SURVIVES_EXECUTION,
                Until.DUSK, protects = true,
            ),
        )
        add(
            TokenRule(
                "stormcatcher", "Stormcaught", EffectKind.ONLY_EXECUTION_KILLS,
                Until.FOREVER, protects = true,
            ),
        )

        // ---- the Demon is silenced (lead D36 / D49) ----
        add(TokenRule("exorcist", "Chosen", EffectKind.DEMON_CANNOT_KILL, Until.DAWN, protects = true))
        add(
            TokenRule(
                "princess", "Doesn't Kill", EffectKind.DEMON_CANNOT_KILL, Until.DAWN,
                protects = true,
                // "The Demon does not kill tonight" reaches a kill set up on an
                // earlier night too — unlike an Exorcist's silencing (lead D68).
                suppression = KillSuppression.NO_KILL_TONIGHT,
            ),
        )
        add(
            TokenRule(
                "toymaker", "Final Night: No Attack", EffectKind.DEMON_CANNOT_KILL,
                Until.FOREVER, protects = true,
            ),
        )

        // ---- drunk / poisoned ----
        add(TokenRule("poisoner", "Poisoned", EffectKind.POISONED, Until.DUSK, impairs = true))
        add(TokenRule("sailor", "Drunk", EffectKind.DRUNK, Until.DUSK, impairs = true))
        add(TokenRule("innkeeper", "Drunk", EffectKind.DRUNK, Until.DUSK, impairs = true))
        add(TokenRule("goon", "Drunk", EffectKind.DRUNK, Until.DUSK, impairs = true))
        add(TokenRule("organgrinder", "Drunk", EffectKind.DRUNK, Until.DUSK, impairs = true))
        add(TokenRule("snakecharmer", "Poisoned", EffectKind.POISONED, Until.FOREVER, impairs = true))
        add(TokenRule("cannibal", "Poisoned", EffectKind.POISONED, Until.FOREVER, impairs = true))
        add(TokenRule("philosopher", "Drunk", EffectKind.DRUNK, Until.FOREVER, impairs = true))
        add(TokenRule("widow", "Poisoned", EffectKind.POISONED, Until.FOREVER, impairs = true))
        add(TokenRule("lleech", "Poisoned", EffectKind.POISONED, Until.FOREVER, impairs = true))
        add(TokenRule("nodashii", "Poisoned", EffectKind.POISONED, Until.FOREVER, copies = 2, impairs = true))
        add(TokenRule("vigormortis", "Poisoned", EffectKind.POISONED, Until.FOREVER, copies = 3, impairs = true))
        // "1 player is drunk, even if you die" — outlives its source (lead D3).
        add(
            TokenRule(
                "puzzlemaster", "Drunk", EffectKind.DRUNK, Until.FOREVER,
                endsWithSource = false, impairs = true,
            ),
        )
        add(
            TokenRule(
                "sweetheart", "Drunk", EffectKind.DRUNK, Until.FOREVER,
                endsWithSource = false, impairs = true,
            ),
        )
        // The Pukka's poison is consumed at its own next step (lead D4).
        add(
            TokenRule(
                "pukka", "Poisoned", EffectKind.POISONED, Until.ON_SOURCE_STEP,
                copies = 2, impairs = true,
            ),
        )

        // ---- ability granted / removed ----
        add(TokenRule("barista", "Sober & Healthy", EffectKind.SOBER_HEALTHY, Until.DUSK))
        add(TokenRule("barista", "Acts Twice", null, Until.DUSK))
        add(TokenRule("bonecollector", "Has Ability", EffectKind.HAS_ABILITY, Until.DUSK))
        add(TokenRule("banshee", "Has Ability", EffectKind.HAS_ABILITY, Until.FOREVER))
        add(TokenRule("pixie", "Has Ability", EffectKind.HAS_ABILITY, Until.FOREVER))
        add(TokenRule("vigormortis", "Has Ability", EffectKind.HAS_ABILITY, Until.FOREVER, copies = 3))
        add(TokenRule("golem", "May Not Nominate", EffectKind.NO_NOMINATE, Until.FOREVER))

        // ---- madness ----
        add(TokenRule("cerenovus", "Mad", EffectKind.MAD, Until.DUSK))
        add(TokenRule("harpy", "Mad", EffectKind.MAD, Until.DUSK))
        add(TokenRule("harpy", "2nd", null, Until.DUSK))
        add(TokenRule("ventriloquist", "Mad", EffectKind.MAD, Until.DUSK))
        add(TokenRule("pixie", "Mad", EffectKind.MAD, Until.FOREVER))

        // ---- misregistration (lead D10) ----
        add(TokenRule("lycanthrope", "Faux Paw", EffectKind.REGISTERS_AS, Until.FOREVER))
        add(TokenRule("revolutionary", "Register Falsely?", EffectKind.REGISTERS_AS, Until.FOREVER))
        add(TokenRule("revolutionary", "Aligned", null, Until.FOREVER, copies = 2))

        // ---- once-per-game spend marks (Character.spentLabel, lead D49) ----
        for (spent in SPENT_MARKS) add(TokenRule(spent.first, spent.second, EffectKind.SPENT, Until.FOREVER))
        add(TokenRule("fanggu", "Once", EffectKind.SPENT, Until.FOREVER, grimoireCentre = true))

        // ---- night-1 "start knowing" tokens: never swept (lead D9) ----
        add(TokenRule("washerwoman", "Townsfolk", null, Until.MANUAL))
        add(TokenRule("washerwoman", "Wrong", null, Until.MANUAL))
        add(TokenRule("librarian", "Outsider", null, Until.MANUAL))
        add(TokenRule("librarian", "Wrong", null, Until.MANUAL))
        add(TokenRule("investigator", "Minion", null, Until.MANUAL))
        add(TokenRule("investigator", "Wrong", null, Until.MANUAL))

        // ---- two-state pairs that can never coexist (lead D52) ----
        // At dawn the positive token RESETS to the negative one rather than being
        // deleted (night-engine §4 defect 28): the negative state is the resting
        // state, so it is the chain terminal and never expires on its own.
        add(
            TokenRule(
                "flowergirl", "Demon Voted", null, Until.DAWN,
                countdownNext = "Demon Not Voted", mutexGroup = "flowergirl.vote",
            ),
        )
        add(
            TokenRule(
                "flowergirl", "Demon Not Voted", null, Until.FOREVER,
                mutexGroup = "flowergirl.vote",
            ),
        )
        add(
            TokenRule(
                "towncrier", "Minion Nominated", null, Until.DAWN,
                countdownNext = "Minions Not Nominated", mutexGroup = "towncrier.nomination",
            ),
        )
        add(
            TokenRule(
                "towncrier", "Minions Not Nominated", null, Until.FOREVER,
                mutexGroup = "towncrier.nomination",
            ),
        )

        // ---- countdown chains, advanced at dusk (lead D14) ----
        add(
            TokenRule(
                "courtier", "Drunk 1", EffectKind.DRUNK, Until.DUSK, impairs = true,
                countdownNext = "Drunk 2", exclusiveGroup = "courtier.drunk",
            ),
        )
        add(
            TokenRule(
                "courtier", "Drunk 2", EffectKind.DRUNK, Until.DUSK, impairs = true,
                countdownNext = "Drunk 3", exclusiveGroup = "courtier.drunk",
            ),
        )
        add(
            TokenRule(
                "courtier", "Drunk 3", EffectKind.DRUNK, Until.DUSK, impairs = true,
                exclusiveGroup = "courtier.drunk",
            ),
        )
        addCountdown("summoner", listOf("Night 1", "Night 2", "Night 3"), "summoner.night")
        addCountdown("xaan", listOf("Night 1", "Night 2", "Night 3"), "xaan.night")
        addCountdown(
            "leviathan", listOf("Day 1", "Day 2", "Day 3", "Day 4", "Day 5"),
            "leviathan.day", grimoireCentre = true,
        )
        addCountdown("riot", listOf("Day 1", "Day 2", "Day 3"), "riot.day", grimoireCentre = true)

        // ---- grimoire-centre standing facts (lead D9) ----
        add(TokenRule("leviathan", "Good Player Executed", null, Until.FOREVER, grimoireCentre = true))
        // D15: the seat effects are separate DRUNK effects. The centre token itself
        // must NEVER impair the seat it is drawn on.
        add(
            TokenRule(
                "minstrel", "Everyone Is Drunk", null, Until.DUSK_AFTER_N_DAYS, untilDays = 1,
                impairs = false, grimoireCentre = true,
            ),
        )

        // ---- day-scoped markers ----
        add(TokenRule("butler", "Master", null, Until.DUSK))
        add(TokenRule("witch", "Cursed", null, Until.DUSK))
        add(TokenRule("goblin", "Claimed", null, Until.DUSK))
        add(TokenRule("thief", "Negative Vote", null, Until.DUSK))
        add(TokenRule("bureaucrat", "3 Votes", null, Until.DUSK))

        // ---- night-scoped markers, swept at dawn ----
        for (m in DAWN_MARKERS) add(TokenRule(m.first, m.second, null, Until.DAWN, copies = m.third))

        // ---- permanent markers ----
        for (m in PERMANENT_MARKERS) add(TokenRule(m.first, m.second, null, Until.FOREVER, copies = m.third))
    }

    /** [BASE] with the per-character registry (WP7) layered on top; later rows win. */
    val all: List<TokenRule> by lazy {
        val merged = LinkedHashMap<String, TokenRule>()
        for (r in BASE) merged[r.key] = r
        for (r in CharacterRules.tokenRules) merged[r.key] = r
        merged.values.toList()
    }

    private val byKey: Map<String, TokenRule> by lazy { all.associateBy { it.key } }

    fun rule(sourceId: String, label: String): TokenRule? = byKey[key(sourceId, label)]

    fun rule(r: PlacedReminder): TokenRule? = rule(r.sourceId, r.label)

    // ------------------------------------------------------------- lifecycles

    /**
     * True when this rule is a step in a countdown chain — either it names a
     * successor, or another rule of the same source names it as one. Countdown
     * steps are advanced by [advanceCountdowns], never swept by a plain expiry.
     */
    fun isCountdown(rule: TokenRule): Boolean =
        rule.countdownNext != null ||
            all.any {
                it.sourceId == rule.sourceId &&
                    it.countdownNext != null &&
                    key(it.sourceId, it.countdownNext) == rule.key
            }

    /** Derived from the registry — never hand-maintained. */
    val expiringAtDawn: List<TokenRule>
        get() = all.filter { it.until == Until.DAWN && !isCountdown(it) }

    /** Derived from the registry — never hand-maintained. */
    val expiringAtDusk: List<TokenRule>
        get() = all.filter { it.until == Until.DUSK && !isCountdown(it) }

    /** The rule one step further along [rule]'s chain, or null when the chain ends. */
    fun next(rule: TokenRule): TokenRule? =
        rule.countdownNext?.let { rule(rule.sourceId, it) }

    /**
     * Advances every countdown chain whose rules fire at [at]: "Drunk 1" becomes
     * "Drunk 2", and the last step of a chain is removed. Applies to rule-bearing
     * effects, to seat tokens and to the grimoire centre, so a hand-placed
     * countdown behaves exactly like an engine-placed one.
     *
     * Called from [Phases.advancePhase]; countdowns are declared at [Until.DUSK]
     * (lead D14), so the dawn call is a no-op unless a registry row says otherwise.
     */
    fun advanceCountdowns(state: GameState, at: Until): GameState {
        fun step(sourceId: String, label: String): String? {
            val rule = rule(sourceId, label) ?: return label
            if (!isCountdown(rule) || rule.until != at) return label
            return rule.countdownNext
        }

        val effects = state.effects.mapNotNull { e ->
            if (e.label.isEmpty()) {
                e
            } else {
                step(e.sourceCharacterId, e.label)?.let { e.copy(label = it) }
            }
        }
        fun advance(list: List<PlacedReminder>): List<PlacedReminder> =
            list.mapNotNull { r -> step(r.sourceId, r.label)?.let { r.copy(label = it) } }

        return state.copy(
            effects = effects,
            players = state.players.map { it.copy(reminders = advance(it.reminders)) },
            storytellerReminders = advance(state.storytellerReminders),
        )
    }
}
