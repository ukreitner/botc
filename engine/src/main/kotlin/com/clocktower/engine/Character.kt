package com.clocktower.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Which side of the grimoire a character belongs to.
 *
 * Deserialisation is tolerant: an unknown team string decodes to [UNKNOWN]
 * instead of throwing, because the official dataset adds teams over time
 * (lead D31). Serialisation always writes the official wire spelling.
 */
@Serializable(with = TeamSerializer::class)
enum class Team(val wireName: String) {
    @SerialName("townsfolk") TOWNSFOLK("townsfolk"),
    @SerialName("outsider") OUTSIDER("outsider"),
    @SerialName("minion") MINION("minion"),
    @SerialName("demon") DEMON("demon"),
    @SerialName("traveler") TRAVELLER("traveler"),
    @SerialName("fabled") FABLED("fabled"),

    /** New official team, 2025 (lead D31). */
    @SerialName("loric") LORIC("loric"),

    /** Deserialisation fallback — the official dataset adds teams over time. */
    UNKNOWN("unknown"),
    ;

    val isEvil: Boolean get() = this == MINION || this == DEMON

    /** Townsfolk, Outsiders, Minions and Demons occupy resident seats; Travellers do not. */
    val isTownResident: Boolean
        get() = this == TOWNSFOLK || this == OUTSIDER || this == MINION || this == DEMON

    val displayName: String
        get() = when (this) {
            TOWNSFOLK -> "Townsfolk"
            OUTSIDER -> "Outsider"
            MINION -> "Minion"
            DEMON -> "Demon"
            TRAVELLER -> "Traveller"
            FABLED -> "Fabled"
            LORIC -> "Loric"
            UNKNOWN -> "Unknown"
        }
}

/**
 * Tolerant [Team] codec: any unrecognised string becomes [Team.UNKNOWN]
 * rather than breaking the whole dataset load.
 */
object TeamSerializer : KSerializer<Team> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.clocktower.engine.Team", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Team) = encoder.encodeString(value.wireName)

    override fun deserialize(decoder: Decoder): Team {
        val raw = decoder.decodeString().trim().lowercase()
        return when (raw) {
            "traveller" -> Team.TRAVELLER
            else -> Team.entries.firstOrNull { it.wireName == raw } ?: Team.UNKNOWN
        }
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
    /**
     * Night-sheet positions for custom characters (from imported script
     * JSON); bundled characters use the canonical global order lists and
     * leave these at 0.
     */
    val firstNight: Int = 0,
    val otherNight: Int = 0,
    /**
     * External art URL for homebrew characters (from the script tool's
     * "image" field). Bundled characters use packaged art and leave this
     * empty; loading is best-effort with the monogram as fallback.
     */
    val image: String = "",
    /**
     * The exact reminder label that marks this character's once-per-game
     * ability as used, in official spelling ("No Ability", "Used",
     * "Guess Used"). Drives `Gates.notSpent`; the "Once per game" text
     * heuristic is deleted (lead D49). Filled in by WP5.
     */
    val spentLabel: String = "",
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
    /**
     * Registry hook id, so a jinx can CHANGE behaviour rather than merely
     * being displayed (lead D19). Empty for a display-only jinx.
     */
    val effect: String = "",
)
