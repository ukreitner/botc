package com.clocktower.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The night guide: for every character that acts at night, the COMPLETE
 * storyteller run-book — full instructions and ready-made show cards
 * (with editable text) so nothing has to be typed or remembered at the
 * table. Pure data; the UI renders it inside each night step.
 */

/**
 * One prepared full-screen card for this step.
 * [kind]: "message" (big text), "token" (text above a character token),
 * "good" / "evil" (alignment cards).
 * [token]: for kind "token" — "self" shows this step's character,
 * "pick" opens a character picker (e.g. Pixie's mad Townsfolk).
 * [text] is a starting point; the storyteller can edit it before showing.
 */
@Serializable
data class GuideShow(
    val label: String,
    val kind: String = "message",
    val text: String = "",
    val token: String = "",
)

@Serializable
data class GuideNight(
    /** Full how-to-run text: who wakes, what they do, what you show/mark. */
    val instructions: String = "",
    val shows: List<GuideShow> = emptyList(),
)

/**
 * One character's complete run-book, in channels (lead D23). Every id in
 * `characters.json` has at least one of `first` / `setup` / `day` / `reference`;
 * `first` exists iff the id is in the first-night order and `other` iff it is in
 * the other-night order. Content is WP5's.
 */
@Serializable
data class NightGuideEntry(
    /** First night. */
    val first: GuideNight? = null,
    /** Other nights. */
    val other: GuideNight? = null,
    /** Before night 1: bag changes, token swaps, storyteller picks. */
    val setup: GuideNight? = null,
    /** Day-phase procedure to run or watch for. */
    val day: GuideNight? = null,
    /** Passive/always-on rules, no storyteller action. */
    val reference: GuideNight? = null,
)

object NightGuide {

    val VALID_KINDS = setOf("message", "token", "good", "evil")
    val VALID_TOKENS = setOf("", "self", "pick")

    private val json = Json { ignoreUnknownKeys = true }

    /** Character id -> guide, loaded from the bundled resource. */
    val entries: Map<String, NightGuideEntry> by lazy {
        val text = BotcResources.readOrNull("/botc/data/night_guide.json")
            ?: return@lazy emptyMap()
        json.decodeFromString<Map<String, NightGuideEntry>>(text)
    }

    fun forStep(characterId: String, isFirstNight: Boolean): GuideNight? {
        val entry = entries[characterId] ?: return null
        return if (isFirstNight) entry.first else entry.other
    }

    /**
     * The run-book for one ACTING ability (lead D23/D39): a `StepVariant.FIRST`
     * re-run shows the first-night book, and a Boffin-granted row shows the
     * granted character's book. WP2 makes this the only entry point.
     */
    fun forStep(abilityId: String, style: WakeStyle): GuideNight? =
        forStep(abilityId, style == WakeStyle.FIRST_NIGHT)

    /** The day / setup / reference channels, for the seat sheet and the script tab. */
    fun channel(characterId: String, channel: String): GuideNight? {
        val entry = entries[characterId] ?: return null
        return when (channel) {
            "first" -> entry.first
            "other" -> entry.other
            "setup" -> entry.setup
            "day" -> entry.day
            "reference" -> entry.reference
            else -> null
        }
    }
}
