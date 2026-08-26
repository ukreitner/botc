package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * WP11's half of the save-compatibility contract (ARCHITECTURE §5.5).
 *
 * `SavedData` lives in `app/` and is compiled into both the Android app and the
 * wasm PWA, so it is not on the engine's test classpath. What IS testable here
 * is everything that makes an old save decode into the new wrapper:
 *
 *  1. every `SavedData` / `Roster` property is defaulted, so a save written
 *     before `schemaVersion`, `archivedGames` and `recentRosters` existed
 *     decodes without them;
 *  2. the payload the wrapper carries — a `GameState` per archived game —
 *     still decodes and migrates (WP0's `PersistenceTest` pins the live game;
 *     the archive list holds the same type);
 *  3. both persistence entry points keep `ignoreUnknownKeys` + `encodeDefaults`
 *     and run the migration, so neither a newer nor an older file breaks.
 */
class SavedDataCompatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private val savedDataSource = "app/src/main/java/com/clocktower/grimoire/data/SavedData.kt"
    private val androidPersistence = "app/src/main/java/com/clocktower/grimoire/data/Persistence.kt"
    private val webPersistence = "web/src/wasmJsMain/kotlin/com/clocktower/grimoire/WebApp.kt"

    /** A whole save file exactly as the SHIPPED app writes it: no WP11 keys. */
    private val shippedWrapper = """
        {
          "game": {
            "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": ["imp", "poisoner"] },
            "players": [
              { "id": 0, "name": "Ana", "characterId": "imp", "note": "claimed Chef" },
              { "id": 1, "name": "Bo", "characterId": "mayor", "alignmentFlipped": true }
            ],
            "fabledIds": ["sentinel"],
            "demonBluffIds": ["chef", "empath", "butler"],
            "phase": "DAY",
            "cycle": 2,
            "updatedAt": 1730000000000
          },
          "importedScripts": [],
          "notes": null
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // 1. Every property is defaulted, so an older file decodes.
    // ------------------------------------------------------------------

    /** Constructor properties of one `data class` in a Kotlin source file. */
    private fun constructorProperties(source: String, className: String): List<String> {
        val start = source.indexOf("data class $className(")
        assertTrue(start >= 0, "no `data class $className(` in the source")
        var depth = 0
        var end = -1
        for (i in start until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        assertTrue(end > start, "unbalanced parentheses around $className")
        // Comments go first — a KDoc sentence contains commas, and the split
        // below is on commas.
        val body = source.substring(start + "data class $className(".length, end)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
        val entries = mutableListOf<String>()
        var level = 0
        var current = StringBuilder()
        for (ch in body) {
            when {
                ch in "(<[" -> { level++; current.append(ch) }
                ch in ")>]" -> { level--; current.append(ch) }
                ch == ',' && level == 0 -> { entries += current.toString(); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        entries += current.toString()
        return entries.map { it.lines().joinToString(" ").trim() }.filter { it.isNotBlank() }
    }

    @Test
    fun `every SavedData property has a default so an old save decodes`() {
        val source = RepoFiles.text(savedDataSource)
        for (className in listOf("SavedData", "Roster")) {
            for (property in constructorProperties(source, className)) {
                assertTrue(
                    "=" in property,
                    "$className.$property has no default — a save written before it existed " +
                        "would fail to decode (ARCHITECTURE §5.5.2)",
                )
            }
        }
    }

    @Test
    fun `SavedData carries the three fields WP11 added`() {
        val properties = constructorProperties(RepoFiles.text(savedDataSource), "SavedData")
            .map { it.substringBefore(':').substringAfterLast(' ').trim() }
        for (field in listOf("schemaVersion", "archivedGames", "recentRosters")) {
            assertTrue(field in properties, "SavedData is missing `$field` (WP11 acceptance)")
        }
        for (field in listOf("game", "importedScripts", "notes")) {
            assertTrue(field in properties, "SavedData lost the shipped field `$field`")
        }
    }

    // ------------------------------------------------------------------
    // 2. The payload still decodes and migrates.
    // ------------------------------------------------------------------

    @Test
    fun `a shipped save file has none of the new keys and its game still migrates`() {
        val wrapper = json.parseToJsonElement(shippedWrapper).jsonObject
        for (added in listOf("schemaVersion", "archivedGames", "recentRosters")) {
            assertTrue(
                added !in wrapper.keys,
                "the shipped-save fixture must NOT carry $added — that is the point of the test",
            )
        }
        // The wrapper's payload is what actually has to survive.
        val game = assertNotNull(wrapper["game"])
        val migrated = json.decodeFromString(GameState.serializer(), game.toString()).migrated(lookup)
        assertEquals(2, migrated.players.size)
        assertEquals(Phase.DAY, migrated.phase)
        assertEquals(listOf("chef", "empath", "butler"), migrated.demonBluffIds)
        assertEquals(listOf("sentinel"), migrated.fabledIds)
        assertEquals("claimed Chef", migrated.players.first().notes.single().text)
    }

    @Test
    fun `an archived game is the same type as the live one and migrates the same way`() {
        val game = json.parseToJsonElement(shippedWrapper).jsonObject.getValue("game")
        // `archivedGames` is a List<GameState>; every element goes through the
        // same engine migration the live game does.
        val archive = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(GameState.serializer()),
            "[${game}, ${game}]",
        ).map { it.migrated(lookup) }
        assertEquals(2, archive.size)
        assertTrue(archive.all { it.demonBluffIds.size == 3 })
        // Idempotent: migrating an already-migrated archive changes nothing.
        assertEquals(archive, archive.map { it.migrated(lookup) })
    }

    // ------------------------------------------------------------------
    // 3. Both entry points still migrate, with both Json flags.
    // ------------------------------------------------------------------

    @Test
    fun `both persistence entry points migrate and keep the serializer flags`() {
        for (relative in listOf(androidPersistence, webPersistence)) {
            val source = assertNotNull(RepoFiles.textOrNull(relative), "missing $relative")
            assertTrue(
                "ignoreUnknownKeys = true" in source,
                "$relative must keep ignoreUnknownKeys so a NEWER save loads in an older build",
            )
            assertTrue(
                "encodeDefaults = true" in source,
                "$relative must keep encodeDefaults so legacy @SerialName fields round-trip",
            )
            assertTrue(
                "migratedSavedData" in source,
                "$relative must run the load migration exactly once (ARCHITECTURE §5.1)",
            )
        }
    }

    @Test
    fun `the save schema version the app stamps is the engine's`() {
        val source = RepoFiles.text(savedDataSource)
        assertTrue(
            "schemaVersion = SCHEMA_VERSION" in source,
            "SavedData.migratedSavedData must stamp the engine's SCHEMA_VERSION, not a literal",
        )
        assertEquals(2, SCHEMA_VERSION, "SCHEMA_VERSION moved; check the migration steps")
    }

    // ------------------------------------------------------------------
    // 4. The archive and roster caps the acceptance criteria name.
    // ------------------------------------------------------------------

    @Test
    fun `the archive keeps ten games and the roster memory five tables`() {
        val source = RepoFiles.text(savedDataSource)
        assertTrue("ARCHIVE_LIMIT = 10" in source, "archived games must be capped at the last 10")
        assertTrue("ROSTER_LIMIT = 5" in source, "recent rosters must be capped at 5")
    }
}
