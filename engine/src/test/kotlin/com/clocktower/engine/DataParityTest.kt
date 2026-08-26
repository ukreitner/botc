package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `characters.json` must equal The Pandemonium Institute's official
 * `roles.json` wherever the app does not deliberately add to it.
 *
 * Lead D28 asked for a `characters.json` ↔ `raw_*.json` parity test. D31 made
 * the official machine-readable data the source of truth and WP5 deleted the
 * `raw_*.json` shards, so the parity that matters — and the one pinned here —
 * is `engine/.../characters.json` ↔ `tools/data/roles.json`, the vendored
 * official file that `tools/regen-data.py` generates from (see `tools/DATA.md`).
 *
 * The generator's only licensed deviations are documented in `tools/DATA.md`
 * and reproduced below: the `traveller`/`traveler` team spelling, the app's
 * edition ids, the app's verbose night prose, and the added `spentLabel`.
 * Everything this test compares — id, name, team, ability, `setup`, and the
 * reminder lists including multiplicity — must be verbatim.
 */
class DataParityTest {

    private val data = GameData.loadDefault()

    /** Official team strings to the app's [Team]; only the spelling differs. */
    private fun officialTeam(raw: String): Team = when (raw) {
        "traveller", "traveler" -> Team.TRAVELLER
        "townsfolk" -> Team.TOWNSFOLK
        "outsider" -> Team.OUTSIDER
        "minion" -> Team.MINION
        "demon" -> Team.DEMON
        "fabled" -> Team.FABLED
        "loric" -> Team.LORIC
        else -> Team.UNKNOWN
    }

    private data class OfficialRole(
        val id: String,
        val name: String,
        val team: Team,
        val ability: String,
        val setup: Boolean,
        val reminders: List<String>,
        val remindersGlobal: List<String>,
    )

    private val official: List<OfficialRole> by lazy {
        val root = Json.parseToJsonElement(RepoFiles.text("tools/data/roles.json"))
        (root as JsonArray).map { element ->
            val obj = element.jsonObject
            fun strings(key: String): List<String> =
                obj[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            OfficialRole(
                id = obj.getValue("id").jsonPrimitive.content,
                name = obj.getValue("name").jsonPrimitive.content,
                team = officialTeam(obj.getValue("team").jsonPrimitive.content),
                ability = obj["ability"]?.jsonPrimitive?.content.orEmpty(),
                setup = obj["setup"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                reminders = strings("reminders"),
                remindersGlobal = strings("remindersGlobal"),
            )
        }
    }

    @Test
    fun `bundled characters are exactly the official roster`() {
        assertEquals(181, official.size, "vendored roles.json is not the pinned 181-character set")
        val officialIds = official.map { it.id }
        assertEquals(officialIds.size, officialIds.toSet().size, "duplicate id in roles.json")

        val bundledIds = data.characters.map { it.id }.toSet()
        val missing = officialIds.filterNot { it in bundledIds }
        val extra = bundledIds.filterNot { id -> official.any { it.id == id } }
        assertTrue(missing.isEmpty(), "characters.json is missing official ids: $missing")
        assertTrue(extra.isEmpty(), "characters.json invents ids the official data has no: $extra")
        assertEquals(181, data.characters.size)

        // Official ids are already in the app's normalised form; if upstream
        // ever ships a snake_case id the generator must normalise it, and the
        // app would silently stop resolving night-order entries.
        for (id in officialIds) {
            assertEquals(Character.normalizeId(id), id, "official id is not app-normalised: $id")
        }
    }

    @Test
    fun `every character matches the official name team ability and setup flag`() {
        for (role in official) {
            val bundled = assertNotNull(data.character(role.id), "no bundled character ${role.id}")
            assertEquals(role.name, bundled.name, "${role.id}: name")
            assertEquals(role.team, bundled.team, "${role.id}: team")
            assertEquals(role.ability, bundled.ability, "${role.id}: ability text")
            assertEquals(role.setup, bundled.setup, "${role.id}: setup flag")
        }
    }

    @Test
    fun `the eleven Loric characters are filed under Team LORIC`() {
        // WP5's regeneration writes "loric" only once WP0's `@SerialName("loric")`
        // exists; before that the 11 entries are parked in "fabled". This is the
        // pin that says the flip has happened (lead D31/D56).
        val officialLoric = official.filter { it.team == Team.LORIC }.map { it.id }.sorted()
        assertEquals(11, officialLoric.size, "official Loric count")
        val bundledLoric = data.characters.filter { it.team == Team.LORIC }.map { it.id }.sorted()
        assertEquals(officialLoric, bundledLoric, "Loric characters are not filed under Team.LORIC")
        assertTrue(
            data.characters.none { it.team == Team.UNKNOWN },
            "a bundled team failed to deserialise: " +
                data.characters.filter { it.team == Team.UNKNOWN }.map { it.id },
        )
    }

    @Test
    fun `reminder labels match the official data including copy counts`() {
        for (role in official) {
            val bundled = assertNotNull(data.character(role.id), role.id)
            assertEquals(
                role.reminders,
                bundled.reminders,
                "${role.id}: reminders (order and copy count are both official)",
            )
            assertEquals(
                role.remindersGlobal,
                bundled.remindersGlobal,
                "${role.id}: remindersGlobal",
            )
        }
        // The multiplicity is the whole point of the copy-count check: a Pukka
        // holds two Poisoned tokens at once, which is what makes poison-then-kill
        // expressible at all (lead D4).
        assertEquals(2, assertNotNull(data.character("pukka")).reminders.count { it == "Poisoned" })
        assertEquals(3, assertNotNull(data.character("po")).reminders.count { it == "Dead" })
        assertEquals(2, assertNotNull(data.character("innkeeper")).reminders.count { it == "Safe" })
        assertEquals(2, assertNotNull(data.character("tealady")).reminders.count { it == "Cannot Die" })
        assertEquals(3, assertNotNull(data.character("lunatic")).reminders.count { it == "Chosen" })
    }

    @Test
    fun `no two reminder labels differ only by case and every spent label is real`() {
        // Lead D5: comparisons are case-insensitive, so two labels that differ
        // only by case are indistinguishable to the engine and must not exist.
        val byLowercase = mutableMapOf<String, MutableSet<String>>()
        for (character in data.characters) {
            for (label in character.allReminders) {
                byLowercase.getOrPut(label.lowercase()) { mutableSetOf() } += label
            }
        }
        val collisions = byLowercase.filterValues { it.size > 1 }
        assertTrue(collisions.isEmpty(), "labels differing only by case: $collisions")

        // Lead D49: `spentLabel` drives Gates.notSpent, so it has to name a
        // token the character actually owns.
        for (character in data.characters) {
            if (character.spentLabel.isBlank()) continue
            assertTrue(
                character.allReminders.any { it.equals(character.spentLabel, ignoreCase = true) },
                "${character.id}: spentLabel '${character.spentLabel}' is not one of ${character.allReminders}",
            )
        }
    }

    @Test
    fun `team totals match the official roster`() {
        val expected = official.groupingBy { it.team }.eachCount()
        val actual = data.characters.groupingBy { it.team }.eachCount()
        assertEquals(expected, actual, "team totals drifted from roles.json")
        // The counts data-accuracy §2 pins, spelled out so a regression names itself.
        assertEquals(69, actual[Team.TOWNSFOLK])
        assertEquals(23, actual[Team.OUTSIDER])
        assertEquals(27, actual[Team.MINION])
        assertEquals(19, actual[Team.DEMON])
        assertEquals(18, actual[Team.TRAVELLER])
        assertEquals(14, actual[Team.FABLED])
        assertEquals(11, actual[Team.LORIC])
    }
}
