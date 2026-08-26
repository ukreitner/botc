package com.clocktower.engine

/**
 * One note the kill sheet shows before a death is recorded: a protection that
 * might stop it, or an ability that fires because of it.
 *
 * [appliesTo] is what lets the sheet split "Applies to this death" from
 * "Not relevant to this cause" — a Soldier's Demon protection is not an answer
 * to an execution (grimoire-and-seats §6).
 */
data class DeathNote(
    val kind: Kind,
    val appliesTo: Set<DeathCause>,
    val text: String,
    val sourceId: String = "",
) {
    enum class Kind { PROTECTION, TRIGGER }

    /** True when this note bears on a death of [cause]. */
    fun relevantTo(cause: DeathCause): Boolean = appliesTo.isEmpty() || cause in appliesTo
}

/**
 * Rule consequences the grimoire can derive on its own.
 *
 * WP1 rebuilt this on top of [Status] / [Effects]: `isImpaired` and
 * `derivedPoison` are now views over the effect model instead of substring
 * greps, so a hand-placed token and an engine-placed one behave identically.
 * The prose helpers survive as the kill sheet's and nomination banner's source
 * until WP9/WP10 render [DeathNote] and `NominationCheck` directly.
 */
object StatusEffects {

    /**
     * Players poisoned by a standing positional ability, with the reason.
     *
     * Derived from the [Standing] rules, so the No Dashii's Soldier neighbour is
     * correctly NOT poisoned and a drunk No Dashii poisons nobody.
     */
    fun derivedPoison(state: GameState, lookup: (String) -> Character?): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        for (p in state.seats) {
            for (e in Status.effectsOn(state, lookup, p.id)) {
                if (e.kind != EffectKind.POISONED || !e.derived) continue
                out[p.id] = e.note.ifEmpty { "Poisoned by the ${lookup(e.sourceCharacterId)?.name ?: "?"}" }
            }
        }
        return out
    }

    /** True when the player's ability is not working — drunk, poisoned or suppressed. */
    fun isImpaired(state: GameState, lookup: (String) -> Character?, player: Player): Boolean =
        Status.isImpaired(state, lookup, player.id)

    /**
     * Everything to weigh before recording this player's death, as prose.
     * Prefer [notes], which says which causes each line applies to.
     */
    fun deathNotes(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): List<String> = notes(state, lookup, playerId).map { it.text }

    /**
     * Structured death notes: protections that might stop this death and
     * abilities that fire because of it, each tagged with the causes it bears on.
     */
    fun notes(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
    ): List<DeathNote> {
        val player = state.player(playerId) ?: return emptyList()
        val out = mutableListOf<DeathNote>()
        val id = player.characterId?.let(Character::normalizeId)
        val character = id?.let(lookup)
        val seats = state.players

        // ---- protections, straight from the effect model ----
        for (e in Status.protections(state, lookup, playerId)) {
            val source = lookup(e.sourceCharacterId)?.name ?: e.sourceCharacterId
            val text = when (e.kind) {
                EffectKind.SAFE_FROM_DEMON -> "$source: safe from the Demon."
                EffectKind.CANT_DIE_TONIGHT -> "$source: can't die tonight."
                EffectKind.CANT_DIE -> "$source: can't die."
                EffectKind.SURVIVES_EXECUTION -> "$source: survives execution today."
                EffectKind.ONLY_EXECUTION_KILLS -> "$source: only an execution can kill them."
                EffectKind.DAY_IMMUNE -> "$source: cannot die during the day."
                EffectKind.DEATH_TIED_TO -> "$source: dies only if its poisoned host is dead."
                EffectKind.DEMON_CANNOT_KILL -> "$source: the Demon cannot kill tonight."
                else -> continue
            }
            out += DeathNote(
                kind = DeathNote.Kind.PROTECTION,
                appliesTo = Deaths.PROTECTS[e.kind].orEmpty(),
                text = if (e.label.isEmpty()) text else "Marked '${e.label}' — $text",
                sourceId = e.sourceCharacterId,
            )
        }
        if (id == "fool" && Status.live(state, lookup, playerId, EffectKind.SPENT).isEmpty()) {
            out += DeathNote(
                DeathNote.Kind.PROTECTION,
                DeathCause.entries.toSet(),
                "Fool: the first time they die, they don't.",
                "fool",
            )
        }

        // ---- abilities that trigger on this death ----
        fun trigger(text: String, causes: Set<DeathCause>, source: String) {
            out += DeathNote(DeathNote.Kind.TRIGGER, causes, text, source)
        }
        val nightCauses = setOf(
            DeathCause.DEMON_KILL, DeathCause.EVIL_ABILITY, DeathCause.GOOD_ABILITY,
            @Suppress("DEPRECATION") DeathCause.DEMON,
            @Suppress("DEPRECATION") DeathCause.OTHER_NIGHT_DEATH,
        )
        val demonCauses = setOf(
            DeathCause.DEMON_KILL,
            @Suppress("DEPRECATION") DeathCause.DEMON,
        )
        when (id) {
            "ravenkeeper" ->
                trigger("Ravenkeeper: if dying at night, they wake to learn a character.", nightCauses, id)
            "sage" -> trigger("Sage: if the Demon killed them, show 2 players, one the Demon.", demonCauses, id)
            "farmer" -> trigger("Farmer: a living good player becomes a Farmer tonight.", nightCauses, id)
            "moonchild" -> trigger("Moonchild: they publicly choose a player who may die tonight.", emptySet(), id)
            "sweetheart" -> trigger("Sweetheart: choose 1 player to be drunk from now on.", emptySet(), id)
            "barber" -> trigger("Barber: the Demon may swap two players' characters tonight.", emptySet(), id)
            "poppygrower" -> trigger("Poppy Grower: minions & demon learn each other tonight.", emptySet(), id)
            "king" -> trigger("Choirboy (if in play) learns the Demon when the King dies to it.", demonCauses, id)
            else -> Unit
        }
        if (character?.team == Team.DEMON) {
            // Travellers never count towards the Scarlet Woman's threshold.
            if (seats.any { it.characterId == "scarletwoman" && it.alive } &&
                state.aliveCountResidents >= 5
            ) {
                trigger("Scarlet Woman becomes the Demon (5+ alive).", emptySet(), "scarletwoman")
            }
            if (id == "imp") trigger("Imp self-kill: a Minion becomes the Imp.", emptySet(), "imp")
        }
        if (character?.team == Team.MINION && seats.any { it.characterId == "minstrel" && it.alive }) {
            trigger(
                "Minstrel: if executed, everyone (but Travellers) is drunk until dusk tomorrow.",
                setOf(DeathCause.EXECUTION),
                "minstrel",
            )
        }
        if (character?.team == Team.MINION && seats.any { it.characterId == "vigormortis" && it.alive }) {
            trigger(
                "Vigormortis kill: the Minion keeps their ability and one Townsfolk neighbour is poisoned.",
                demonCauses,
                "vigormortis",
            )
        }
        if (character?.team == Team.OUTSIDER && seats.any { it.characterId == "godfather" && it.alive }) {
            trigger(
                "Godfather kills tonight because an Outsider died today.",
                setOf(DeathCause.EXECUTION, DeathCause.DAY_ABILITY),
                "godfather",
            )
        }
        if (id == "zombuul" && state.deaths.none { it.playerId == playerId }) {
            trigger(
                "Zombuul: the first time it dies, it lives but registers as dead.",
                emptySet(),
                "zombuul",
            )
        }
        if (seats.any { it.characterId == "grandmother" && it.alive } &&
            player.reminders.any { Tokens.key(it) == Tokens.key("grandmother", "Grandchild") }
        ) {
            trigger("Grandmother dies too if the Demon killed her grandchild.", demonCauses, "grandmother")
        }
        return out
    }

    /** Rule triggers to surface the moment a nomination is declared. */
    fun nominationWarnings(
        state: GameState,
        lookup: (String) -> Character?,
        nominatorId: Long?,
        nomineeId: Long?,
    ): List<String> {
        val notes = mutableListOf<String>()
        val nominator = nominatorId?.let { state.player(it) }
        val nominee = nomineeId?.let { state.player(it) }

        fun holds(p: Player, sourceId: String, label: String): Boolean =
            p.reminders.any { Tokens.key(it) == Tokens.key(sourceId, label) }

        if (nominator != null) {
            if (state.alivePlayers.size >= 4 && holds(nominator, "witch", "Cursed")) {
                notes += "${nominator.name} is Witch-cursed — they die immediately for nominating (if 4+ alive)."
            }
            if (nominator.characterId == "golem") {
                notes += "Golem nominates: if the nominee is not the Demon, the nominee dies; " +
                    "the Golem may only nominate once per game."
            }
        }
        if (nominee != null) {
            val virginSpent = holds(nominee, "virgin", "No Ability") ||
                Status.live(state, lookup, nominee.id, EffectKind.SPENT).isNotEmpty()
            if (nominee.characterId == "virgin" && !virginSpent) {
                notes += "Virgin's first nomination: if ${nominator?.name ?: "the nominator"} " +
                    "is a Townsfolk, they are executed immediately."
            }
            if (holds(nominee, "fearmonger", "Fear")) {
                notes += "Fearmonger chose ${nominee.name}: if executed from this nomination, their team loses."
            }
        }
        if (nominator != null &&
            Status.live(state, lookup, nominator.id, EffectKind.MAD).isNotEmpty()
        ) {
            notes += "${nominator.name} is Cerenovus-mad — check their claim before this goes further."
        }
        return notes
    }
}
