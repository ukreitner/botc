package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptParserTest {

    @Test
    fun `parses official script tool format with meta and snake case ids`() {
        val json = """
            [
              {"id": "_meta", "name": "Catfishing", "author": "The Community"},
              "washerwoman",
              {"id": "fortune_teller"},
              "scarlet_woman",
              "imp"
            ]
        """.trimIndent()
        val script = ScriptParser.parse(json)
        assertEquals("Catfishing", script.name)
        assertEquals("The Community", script.author)
        assertEquals(listOf("washerwoman", "fortuneteller", "scarletwoman", "imp"), script.characterIds)
    }

    @Test
    fun `parses inline custom characters`() {
        val json = """
            [
              {"id": "_meta", "name": "Homebrew Night"},
              "imp",
              {
                "id": "acrobat_2",
                "name": "Tumbler",
                "team": "townsfolk",
                "ability": "Each night, you might fall over. [+1 Outsider]",
                "reminders": ["Fell"]
              }
            ]
        """.trimIndent()
        val script = ScriptParser.parse(json)
        assertEquals(1, script.customCharacters.size)
        val custom = script.customCharacters.first()
        assertEquals("acrobat2", custom.id)
        assertEquals("Tumbler", custom.name)
        assertEquals(Team.TOWNSFOLK, custom.team)
        assertTrue(custom.setup, "bracketed ability implies setup flag")
        assertEquals(listOf("Fell"), custom.reminders)
    }

    @Test
    fun `resolves custom characters through game data`() {
        val data = GameData.loadDefault()
        val json = """["imp", {"id": "weirdo", "name": "Weirdo", "team": "outsider", "ability": "You are weird."}]"""
        val script = ScriptParser.parse(json)
        val resolved = data.resolve(script)
        assertEquals(listOf("imp", "weirdo"), resolved.map { it.id })
        assertTrue(data.unknownIds(script).isEmpty())
    }

    @Test
    fun `unknown ids surface for reporting`() {
        val data = GameData.loadDefault()
        val script = ScriptParser.parse("""["imp", "totallymadeup"]""")
        assertEquals(listOf("totallymadeup"), data.unknownIds(script))
    }
}
