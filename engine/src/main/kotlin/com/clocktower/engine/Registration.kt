package com.clocktower.engine

/**
 * What a seat registers as, right now, to an asking ability. ALWAYS a set:
 * Legion registers as both DEMON and MINION; a Lil' Monsta babysitter adds DEMON.
 * Every "is this player a Demon / Minion / evil" question goes through here (lead D32).
 *
 * Misregistration is never guessed. A Spy, a Recluse or a Faux Paw registers as its
 * true team until the storyteller rules otherwise, and that ruling is an
 * [EffectKind.REGISTERS_AS] effect — never an invented "Registered: X" token (lead D10).
 */
object Registration {

    /** Every team this seat may be counted as by an asking ability. */
    fun registersAs(state: GameState, lookup: (String) -> Character?, player: Player): Set<Team> {
        val out = linkedSetOf<Team>()
        val id = player.characterId?.let(Character::normalizeId)
        val trueTeam = id?.let(lookup)?.team

        // A storyteller ruling REPLACES the true team. A Spy ruled to register as
        // the Chef registers Townsfolk and nothing else — keeping MINION alongside
        // would make the ruling unable to do the one thing it exists for (lead D10).
        val rulings = Status.live(state, lookup, player.id, EffectKind.REGISTERS_AS)
        if (rulings.isNotEmpty()) {
            for (e in rulings) teamOf(e.characterId, lookup)?.let { out += it }
        } else {
            trueTeam?.let { out += it }
        }

        // Innate multi-registration is additive and is NOT a ruling.
        when (id) {
            // "You register as both a Minion and a Demon."
            "legion" -> {
                out += Team.DEMON
                out += Team.MINION
            }
            // Whoever holds the Lil' Monsta token is the Demon for every count.
            else -> if (holdsLilMonsta(player)) out += Team.DEMON
        }

        // A ruling that named nothing recognisable must not empty the set.
        if (out.isEmpty()) trueTeam?.let { out += it }
        return out
    }

    /** True when any team this seat registers as is an evil one. */
    fun registersEvil(state: GameState, lookup: (String) -> Character?, player: Player): Boolean {
        if (registersAs(state, lookup, player).any { it.isEvil }) return true
        // An explicit alignment override (a Traveller, a Bounty Hunter's Townsfolk)
        // is a fact about the seat, not about the character it registers as.
        return player.alignment == Alignment.EVIL
    }

    /** The character this seat registers as to [askedBy], honouring REGISTERS_AS rulings. */
    fun registersAsCharacter(
        state: GameState,
        lookup: (String) -> Character?,
        player: Player,
        askedBy: String,
    ): String? {
        val asked = Character.normalizeId(askedBy)
        val rulings = Status.effectsOn(state, lookup, player.id)
            .filter { it.kind == EffectKind.REGISTERS_AS && !it.suspended && it.characterId != null }
        // A ruling scoped to the asking ability wins over a blanket one.
        rulings.lastOrNull { Character.normalizeId(it.sourceCharacterId) == asked }
            ?.let { return it.characterId }
        rulings.lastOrNull()?.let { return it.characterId }
        return player.characterId
    }

    /** True alignment: explicit override, else the character's natural team. */
    fun alignment(state: GameState, lookup: (String) -> Character?, player: Player): Alignment {
        player.alignment?.let { return it }
        return if (player.isEvil(lookup)) Alignment.EVIL else Alignment.GOOD
    }

    /** A REGISTERS_AS payload may name a character or a bare team/alignment word. */
    private fun teamOf(payload: String?, lookup: (String) -> Character?): Team? {
        val raw = payload?.trim()?.lowercase() ?: return null
        return when (raw) {
            "" -> null
            // "Registers as good" is a real answer, not an absent one.
            "good" -> Team.TOWNSFOLK
            "evil" -> Team.MINION
            "townsfolk" -> Team.TOWNSFOLK
            "outsider" -> Team.OUTSIDER
            "minion" -> Team.MINION
            "demon" -> Team.DEMON
            else -> lookup(raw)?.team
        }
    }

    /** The seat holding the Lil' Monsta babysitting token is the Demon. */
    private fun holdsLilMonsta(player: Player): Boolean =
        player.reminders.any { Tokens.key(it) == Tokens.key("lilmonsta", "Is The Demon") }
}
