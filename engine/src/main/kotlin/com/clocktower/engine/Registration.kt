package com.clocktower.engine

/**
 * What a seat registers as, right now, to an asking ability. ALWAYS a set:
 * Legion registers as both DEMON and MINION; a Lil' Monsta babysitter adds DEMON.
 * Every "is this player a Demon / Minion / evil" question goes through here (lead D32).
 */
object Registration {

    fun registersAs(state: GameState, lookup: (String) -> Character?, player: Player): Set<Team> =
        TODO("WP1")

    fun registersEvil(state: GameState, lookup: (String) -> Character?, player: Player): Boolean =
        TODO("WP1")

    /** The character this seat registers as to [askedBy], honouring REGISTERS_AS rulings. */
    fun registersAsCharacter(
        state: GameState,
        lookup: (String) -> Character?,
        player: Player,
        askedBy: String,
    ): String? = TODO("WP1")

    /** True alignment: explicit override, else the character's natural team. */
    fun alignment(state: GameState, lookup: (String) -> Character?, player: Player): Alignment =
        TODO("WP1")
}
