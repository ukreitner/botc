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
    fun `custom characters keep their external image url`() {
        val single = ScriptParser.parse(
            """[{"id":"gob","name":"Gob","team":"minion","ability":"x","image":"https://example.com/gob.png"}]""",
        )
        assertEquals("https://example.com/gob.png", single.customCharacters.first().image)
        val listForm = ScriptParser.parse(
            """[{"id":"gob2","name":"Gob2","team":"townsfolk","ability":"x","image":["https://a.png","https://b.png"]}]""",
        )
        assertEquals("https://a.png", listForm.customCharacters.first().image)
    }

    @Test
    fun `duplicates house rule silences the copy check`() {
        val data = GameData.loadDefault()
        val chef = data.character("chef")!!
        val bag = listOf(chef, chef, chef, data.character("saint")!!, data.character("poisoner")!!)
        val strict = GameActions.validateBag(bag, 5)
        assertTrue(strict.any { "appears" in it }, "strict mode flags copies: $strict")
        val relaxed = GameActions.validateBag(bag, 5, allowAnyDuplicates = true)
        assertTrue(relaxed.none { "appears" in it }, "house rule allows copies: $relaxed")
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

class ScriptLinkTest {
    @kotlin.test.Test
    fun `decodes an official script tool share link`() {
        val url = "https://script.bloodontheclocktower.com/?script=H4sIAAAAAAAAAy2QQU5DMQxEr1LNOifoDrHgDiCE%2FBM3MT%2BJKzufUiHujpKyG41Gb%2Bx5%2B4EknPHReBAC6BhFDWe88nAEdGqMM55OLyatnZ6L2uH4Dcjc2agi4EtqpcySRAcCht56NGFDgPS%2BM1%2BXztS2uhR%2Fq0XxGabW2YXibJJcxo1GLI06ArzSfcUv4oXt39SaHugm3YfxPCBS77KtWxI1X16yo%2B%2BzVSs3BFy1ypAoi5I1XWiUxfGjNe1Lkju5y%2BqJZJXHTR%2B1d8qZNtq2ypN17PvcSj91kefTRfiCgEJW1wqfR8ozu3HOZA%2FilbPSwPsfu1ZU%2FnQBAAA%3D"
        kotlin.test.assertTrue(ScriptLink.isLink(url))
        val json = kotlin.test.assertNotNull(ScriptLink.decode(url))
        val script = ScriptParser.parse(json)
        kotlin.test.assertTrue(script.characterIds.size >= 15, "decoded ${script.characterIds.size} ids")
        val data = GameData.loadDefault()
        kotlin.test.assertTrue(data.unknownIds(script).isEmpty(), "unknown: ${data.unknownIds(script)}")
    }

    @kotlin.test.Test
    fun `pure kotlin inflate round trips real gzip output`() {
        val random = kotlin.random.Random(42)
        for (size in listOf(0, 1, 100, 5_000, 60_000)) {
            // Compressible-ish data: runs + random, to hit all block types.
            val original = ByteArray(size) { i ->
                if (i % 7 < 4) 'a'.code.toByte() else random.nextInt(256).toByte()
            }
            val gzipped = java.io.ByteArrayOutputStream().use { bos ->
                java.util.zip.GZIPOutputStream(bos).use { it.write(original) }
                bos.toByteArray()
            }
            val inflated = Inflate.gunzip(gzipped)
            kotlin.test.assertTrue(original.contentEquals(inflated), "mismatch at size $size")
        }
    }

    @kotlin.test.Test
    fun `raw json is not treated as a link`() {
        kotlin.test.assertTrue(!ScriptLink.isLink("""["imp","poisoner"]"""))
        kotlin.test.assertEquals(null, ScriptLink.decode("""["imp","poisoner"]"""))
    }
}

class SnakeCharmerTest {
    @kotlin.test.Test
    fun `successful charm swaps characters and alignments and poisons`() {
        val data = GameData.loadDefault()
        val sv = data.builtInScripts().first { it.id == "sv" }
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        state = GameActions.assignCharacter(state, 0, "snakecharmer")
        state = GameActions.assignCharacter(state, 1, "vortox")
        state = GameActions.snakeCharmerSwap(state, 0, 1)
        kotlin.test.assertEquals("vortox", state.player(0)?.characterId)
        kotlin.test.assertEquals("snakecharmer", state.player(1)?.characterId)
        kotlin.test.assertTrue(state.player(0)!!.isEvil(data::character), "new demon is evil")
        kotlin.test.assertTrue(!state.player(1)!!.isEvil(data::character), "new charmer is good")
        kotlin.test.assertTrue(state.player(1)!!.reminders.any { it.label == "Poisoned" && it.sourceId == "snakecharmer" })
    }
}

class NightGuideTest {
    @kotlin.test.Test
    fun `guide covers every night actor with valid shows`() {
        val data = GameData.loadDefault()
        kotlin.test.assertTrue(NightGuide.entries.size >= 110, "loaded ${NightGuide.entries.size} entries")
        // Lead D23: the guide also carries marker entries (DUSK, MINION_INFO,
        // DEMON_INFO, DAWN) that describe a step of the night with no character
        // behind it. Every OTHER key must still resolve to a character.
        for (marker in listOf(
            NightMarkers.DUSK,
            NightMarkers.MINION_INFO,
            NightMarkers.DEMON_INFO,
            NightMarkers.DAWN,
        )) {
            kotlin.test.assertTrue(marker in NightGuide.entries, "no guide entry for marker $marker")
        }
        for ((id, entry) in NightGuide.entries) {
            if (id !in NightMarkers.all) {
                kotlin.test.assertTrue(data.character(id) != null, "unknown character $id")
            }
            for (night in listOfNotNull(entry.first, entry.other)) {
                kotlin.test.assertTrue(night.instructions.isNotBlank(), "$id: blank instructions")
                for (show in night.shows) {
                    kotlin.test.assertTrue(show.kind in NightGuide.VALID_KINDS, "$id: kind ${show.kind}")
                    kotlin.test.assertTrue(show.token in NightGuide.VALID_TOKENS, "$id: token ${show.token}")
                }
            }
        }
        // The canonical example: the Pixie's madness card is a pickable token.
        val pixie = NightGuide.forStep("pixie", isFirstNight = true)
        kotlin.test.assertTrue(pixie != null && pixie.shows.any { it.kind == "token" && it.token == "pick" })
        // Every character on the canonical wake orders has a guide entry.
        val orders = data.firstNightOrder + data.otherNightOrder
        val missing = orders
            .filter { it !in NightMarkers.all }
            .mapNotNull { data.character(it)?.id }
            .filter { it !in NightGuide.entries }
        kotlin.test.assertTrue(missing.isEmpty(), "no guide for: $missing")
    }
}

class EphemeralReminderTest {
    @kotlin.test.Test
    fun `night-scoped tokens clear at dawn and day-scoped tokens at dusk`() {
        val data = GameData.loadDefault()
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E"))
        state = GameActions.advancePhase(state) // night 1
        state = GameActions.addReminder(state, 0, PlacedReminder("monk", "Safe"))
        state = GameActions.addReminder(state, 0, PlacedReminder("poisoner", "Poisoned"))
        state = GameActions.addReminder(state, 0, PlacedReminder("washerwoman", "Townsfolk"))

        state = GameActions.advancePhase(state) // dawn -> day 1
        val labelsAtDay = state.player(0)!!.reminders.map { it.label }
        kotlin.test.assertTrue("Safe" !in labelsAtDay, "Monk Safe swept at dawn")
        kotlin.test.assertTrue("Poisoned" in labelsAtDay, "Poison lasts through the day")
        kotlin.test.assertTrue("Townsfolk" in labelsAtDay, "info tokens untouched")

        state = GameActions.advancePhase(state) // dusk -> night 2
        val labelsAtNight = state.player(0)!!.reminders.map { it.label }
        kotlin.test.assertTrue("Poisoned" !in labelsAtNight, "Poisoner token swept at dusk")
        kotlin.test.assertTrue("Townsfolk" in labelsAtNight)
    }

    @kotlin.test.Test
    fun `permanent poison sources are not swept`() {
        val data = GameData.loadDefault()
        val sv = data.builtInScripts().first { it.id == "sv" }
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        state = GameActions.advancePhase(state)
        state = GameActions.addReminder(state, 0, PlacedReminder("snakecharmer", "Poisoned"))
        state = GameActions.advancePhase(state)
        state = GameActions.advancePhase(state)
        kotlin.test.assertTrue(state.player(0)!!.reminders.any { it.label == "Poisoned" })
    }
}

class SentinelTest {
    @kotlin.test.Test
    fun `sentinel fabled relaxes the outsider count by one either way`() {
        val data = GameData.loadDefault()
        val tb = data.builtInScripts().first { it.id == "tb" }
        // 8 players, base 5/1/1/1 — build a 6/0/1/1 bag (one outsider short).
        var state = GameActions.newGame(tb, (1..8).map { "P$it" })
        val playerIds = state.players.map { it.id }
        val townsfolk = listOf("washerwoman", "librarian", "investigator", "chef", "empath", "monk")
        townsfolk.forEachIndexed { index, id -> state = GameActions.assignCharacter(state, playerIds[index], id) }
        state = GameActions.assignCharacter(state, playerIds[6], "poisoner")
        state = GameActions.assignCharacter(state, playerIds[7], "imp")
        val without = GameActions.validateSetupState(state, data::character)
        kotlin.test.assertTrue(without.any { "Outsider" in it }, "flagged without sentinel: $without")
        state = GameActions.setFabled(state, listOf("sentinel"))
        val with = GameActions.validateSetupState(state, data::character)
        kotlin.test.assertTrue(with.none { "Outsider" in it || "Townsfolk" in it }, "sentinel should relax counts: $with")
    }
}

class StarPassTest {
    @kotlin.test.Test
    fun `imp star pass kills the imp and crowns the heir`() {
        val data = GameData.loadDefault()
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E"))
        state = GameActions.assignCharacter(state, 0, "imp")
        state = GameActions.assignCharacter(state, 1, "scarletwoman")
        state = GameActions.starPass(state, 0, 1, data::character)
        kotlin.test.assertTrue(!state.player(0)!!.alive, "old Imp is dead")
        kotlin.test.assertEquals("imp", state.player(1)?.characterId)
        kotlin.test.assertTrue(state.player(1)!!.isEvil(data::character), "new Imp is evil")
        kotlin.test.assertEquals("imp", state.deaths.last().characterIdAtDeath)
    }
}
