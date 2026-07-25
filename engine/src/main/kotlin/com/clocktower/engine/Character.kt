package com.clocktower.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which side of the grimoire a character belongs to. */
@Serializable
enum class Team {
    @SerialName("townsfolk") TOWNSFOLK,
    @SerialName("outsider") OUTSIDER,
    @SerialName("minion") MINION,
    @SerialName("demon") DEMON,
    @SerialName("traveler") TRAVELLER,
    @SerialName("fabled") FABLED;

    val isEvil: Boolean get() = this == MINION || this == DEMON
    val isTownResident: Boolean get() = this != TRAVELLER && this != FABLED

    val displayName: String
        get() = when (this) {
            TOWNSFOLK -> "Townsfolk"
            OUTSIDER -> "Outsider"
            MINION -> "Minion"
            DEMON -> "Demon"
            TRAVELLER -> "Traveller"
            FABLED -> "Fabled"
        }
}

/**
 * A Blood on the Clocktower character. Bundled characters come from the
 * embedded dataset; custom characters can be defined inside imported
 * script JSON files.
 */
@Serializable
data class Character(
    val id: String,
    val name: String,
    val edition: String = "custom",
    val team: Team,
    val ability: String = "",
    val setup: Boolean = false,
    val firstNightReminder: String = "",
    val otherNightReminder: String = "",
    val reminders: List<String> = emptyList(),
    val remindersGlobal: List<String> = emptyList(),
) {
    /** All reminder-token labels this character can put into the grimoire. */
    val allReminders: List<String> get() = reminders + remindersGlobal

    companion object {
        /**
         * Canonical id form: lowercase with everything except letters and
         * digits removed. Handles the official script tool's snake_case ids
         * ("fortune_teller") as well as compact ids ("fortuneteller").
         */
        fun normalizeId(raw: String): String =
            raw.lowercase().filter { it.isLetterOrDigit() }
    }
}

/** A jinx between two characters (the Djinn's rules). */
@Serializable
data class Jinx(
    val id1: String,
    val id2: String,
    val reason: String,
)
