package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameDataTest {

    private val data = GameData.loadDefault()

    @Test
    fun `bundled dataset loads with all editions complete`() {
        fun count(edition: String, team: Team) =
            data.characters.count { it.edition == edition && it.team == team }

        assertEquals(13, count("tb", Team.TOWNSFOLK))
        assertEquals(4, count("tb", Team.OUTSIDER))
        assertEquals(4, count("tb", Team.MINION))
        assertEquals(1, count("tb", Team.DEMON))
        assertEquals(13, count("bmr", Team.TOWNSFOLK))
        assertEquals(4, count("bmr", Team.DEMON))
        assertEquals(13, count("sv", Team.TOWNSFOLK))
        assertEquals(4, count("sv", Team.DEMON))
        assertTrue(data.characters.count { it.team == Team.TRAVELLER } >= 15)
        assertTrue(data.characters.count { it.team == Team.FABLED } >= 10)
        assertTrue(data.characters.size >= 150, "expected a rich experimental set")
    }

    @Test
    fun `no duplicate ids and all ids are normalized`() {
        val ids = data.characters.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate character ids")
        for (id in ids) {
            assertEquals(Character.normalizeId(id), id, "id not normalized: $id")
        }
    }

    @Test
    fun `night order references only known characters`() {
        for (id in data.firstNightOrder + data.otherNightOrder) {
            if (id in NightMarkers.all) continue
            assertNotNull(data.character(id), "unknown id in night order: $id")
        }
    }

    @Test
    fun `every character with a night reminder appears in the matching order`() {
        for (c in data.characters) {
            if (c.firstNightReminder.isNotBlank()) {
                assertTrue(c.id in data.firstNightOrder, "${c.id} missing from first night order")
            }
            if (c.otherNightReminder.isNotBlank()) {
                assertTrue(c.id in data.otherNightOrder, "${c.id} missing from other night order")
            }
        }
    }

    @Test
    fun `jinxes reference known characters`() {
        for (j in data.jinxes) {
            assertNotNull(data.character(j.id1), "unknown jinx id ${j.id1}")
            assertNotNull(data.character(j.id2), "unknown jinx id ${j.id2}")
        }
    }

    @Test
    fun `built in scripts have the right sizes`() {
        val scripts = data.builtInScripts().associateBy { it.id }
        assertEquals(22, scripts.getValue("tb").characterIds.size)
        assertEquals(25, scripts.getValue("bmr").characterIds.size)
        assertEquals(25, scripts.getValue("sv").characterIds.size)
    }

    @Test
    fun `active jinxes found for lleech plus heretic`() {
        val jinxes = data.activeJinxes(listOf("lleech", "heretic", "washerwoman"))
        assertTrue(jinxes.any { setOf(it.id1, it.id2) == setOf("lleech", "heretic") })
    }

    @Test
    fun `key setup characters are flagged`() {
        for (id in listOf("baron", "drunk", "godfather", "fanggu", "vigormortis", "marionette", "lilmonsta", "summoner", "huntsman", "atheist")) {
            val c = assertNotNull(data.character(id), "missing character $id")
            assertTrue(c.setup, "$id should be setup-flagged")
        }
    }

    @Test
    fun `travellers for trouble brewing include its five`() {
        val tb = data.builtInScripts().first { it.id == "tb" }
        val ids = data.travellersFor(tb).map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("scapegoat", "gunslinger", "beggar", "bureaucrat", "thief")))
    }
}
