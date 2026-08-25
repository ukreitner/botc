package com.clocktower.engine

import kotlinx.serialization.Serializable

/**
 * A card the storyteller holds up to a player across the table, as DATA.
 * The engine decides *what* card to offer; the Compose renderer in
 * `app/.../components/ShowCards.kt` decides how it looks.
 *
 * WP2 moves the app's `ShowCard` renderer onto this type; WP8 extends it with
 * `PointCard`, `MultiTokenCard` and a captioned alignment card.
 */
@Serializable
sealed interface ShowCardSpec {

    /** Huge text on black, optionally with a second line. */
    @Serializable
    data class Message(val title: String, val subtitle: String = "") : ShowCardSpec

    /** One character token under a prefix line ("YOU ARE", "THIS PLAYER IS"). */
    @Serializable
    data class CharacterCard(val prefix: String, val characterId: String) : ShowCardSpec

    /** A single number, as big as the screen allows. */
    @Serializable
    data class NumberCard(val number: Int) : ShowCardSpec

    /** GOOD / EVIL. */
    @Serializable
    data class AlignmentCard(val evil: Boolean) : ShowCardSpec

    /** The Demon's three not-in-play bluffs. */
    @Serializable
    data class BluffsCard(val characterIds: List<String>) : ShowCardSpec

    /**
     * A neutral, full-script character sheet the player can silently point
     * at (Pit-Hag, Philosopher, Cerenovus…). Shows every script character
     * with zero game-state hints, so it reveals nothing.
     */
    @Serializable
    data class SheetCard(val characterIds: List<String>) : ShowCardSpec
}
