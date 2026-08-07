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

@Serializable
data class NightGuideEntry(
    val first: GuideNight? = null,
    val other: GuideNight? = null,
)

object NightGuide {

    val VALID_KINDS = setOf("message", "token", "good", "evil")
    val VALID_TOKENS = setOf("", "self", "pick")

    private val json = Json { ignoreUnknownKeys = true }

    /** Character id -> guide, loaded from the bundled resource. */
    val entries: Map<String, NightGuideEntry> by lazy {
        val stream = NightGuide::class.java.getResourceAsStream("/botc/data/night_guide.json")
            ?: return@lazy emptyMap()
        json.decodeFromString<Map<String, NightGuideEntry>>(
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
    }

    fun forStep(characterId: String, isFirstNight: Boolean): GuideNight? {
        val entry = entries[characterId] ?: return null
        return if (isFirstNight) entry.first else entry.other
    }
}
