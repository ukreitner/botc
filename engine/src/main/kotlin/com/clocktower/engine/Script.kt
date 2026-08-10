package com.clocktower.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A playable script: an ordered set of characters, possibly including
 * custom (homebrew) character definitions carried inside the script file.
 */
@Serializable
data class Script(
    val id: String,
    val name: String,
    val author: String = "",
    /** Normalized character ids, in sheet order. */
    val characterIds: List<String>,
    /** Custom characters defined inline in the imported JSON. */
    val customCharacters: List<Character> = emptyList(),
    val isBuiltIn: Boolean = false,
)

/**
 * Parses script JSON in the official script tool format:
 * an array whose entries are character id strings, `{"id": "..."}` refs,
 * an optional `{"id": "_meta", "name": ..., "author": ...}` header, or
 * full custom character objects.
 */
object ScriptParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, fallbackName: String = "Imported script"): Script {
        val root = json.parseToJsonElement(text)
        val entries: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["characters"]?.jsonArray
                ?: throw IllegalArgumentException("Unrecognized script JSON: expected an array")
            else -> throw IllegalArgumentException("Unrecognized script JSON: expected an array")
        }

        var name = fallbackName
        var author = ""
        val ids = mutableListOf<String>()
        val custom = mutableListOf<Character>()

        for (entry in entries) {
            when (entry) {
                is JsonPrimitive -> ids += Character.normalizeId(entry.content)
                is JsonObject -> {
                    val rawId = entry["id"]?.jsonPrimitive?.content ?: continue
                    if (rawId == "_meta") {
                        entry["name"]?.jsonPrimitive?.content?.let { name = it }
                        entry["author"]?.jsonPrimitive?.content?.let { author = it }
                        continue
                    }
                    val id = Character.normalizeId(rawId)
                    ids += id
                    // A full inline character definition has at least a team + ability/name.
                    if (entry.containsKey("team")) {
                        parseCustomCharacter(id, entry)?.let { custom += it }
                    }
                }
                else -> Unit
            }
        }

        return Script(
            id = "imported-" + Character.normalizeId(name).ifEmpty { "script" },
            name = name,
            author = author,
            // Hand-edited scripts sometimes repeat an id; duplicates would
            // crash keyed lists downstream, so keep first occurrence only.
            characterIds = ids.distinct(),
            customCharacters = custom.distinctBy { it.id },
        )
    }

    private fun parseCustomCharacter(id: String, obj: JsonObject): Character? {
        val teamRaw = obj["team"]?.jsonPrimitive?.content ?: return null
        val team = when (teamRaw.lowercase()) {
            "townsfolk" -> Team.TOWNSFOLK
            "outsider" -> Team.OUTSIDER
            "minion" -> Team.MINION
            "demon" -> Team.DEMON
            "traveler", "traveller" -> Team.TRAVELLER
            "fabled" -> Team.FABLED
            else -> return null
        }
        fun str(key: String): String = (obj[key] as? JsonPrimitive)?.content ?: ""
        fun strList(key: String): List<String> =
            (obj[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

        fun int(key: String): Int =
            (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0

        return Character(
            id = id,
            name = str("name").ifEmpty { id.replaceFirstChar { c -> c.uppercase() } },
            edition = "custom",
            team = team,
            ability = str("ability"),
            setup = obj["setup"]?.jsonPrimitive?.booleanOrNull
                ?: str("ability").contains('['),
            firstNightReminder = str("firstNightReminder"),
            otherNightReminder = str("otherNightReminder"),
            reminders = strList("reminders"),
            remindersGlobal = strList("remindersGlobal"),
            firstNight = int("firstNight"),
            otherNight = int("otherNight"),
            // The script tool allows "image" as a single URL or a list
            // (good/evil/traveller variants) — take the first.
            image = when (val img = obj["image"]) {
                is JsonPrimitive -> img.content
                is JsonArray -> (img.firstOrNull() as? JsonPrimitive)?.content ?: ""
                else -> ""
            },
        )
    }
}
