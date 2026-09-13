package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomInformationTest {
    private val data = GameData.loadDefault()
    private val json = Json { encodeDefaults = true }

    @Test
    fun `custom information keeps its exact content and provenance after saving and reopening`() {
        val state = GameActions.newGame(data.builtInScripts().first { it.id == "tb" }, listOf("Ana", "Ben"))
        val shown = "Neither the Lantern Keeper nor the Salt Merchant is next to you.\nChoose another player."
        val recorded = Ledger.shown(state, state.seats.first().id, "chef", shown, truthful = null)
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), recorded))
        val entry = restored.ledger.single()
        assertEquals(shown, entry.shown)
        assertEquals(state.seats.first().id, entry.actorId)
        assertEquals("chef", entry.sourceId)
        assertEquals(Verdict.ST_CHOICE, entry.verdict)
        assertTrue(entry.byStoryteller)
        assertFalse(entry.impaired, "A custom answer does not establish that an ability malfunctioned")
        assertEquals(state.players, restored.players)
        val log = GameLog.rows(restored, data::character).single().text
        assertTrue(log.contains(shown))
        assertTrue(log.endsWith("(custom choice)"))
    }

    @Test
    fun `normal suggestions retain their existing record while group custom cards are labelled`() {
        val state = GameActions.newGame(data.builtInScripts().first { it.id == "tb" }, listOf("Ana", "Ben"))
        val player = state.seats.first().id
        assertEquals(Ledger.told(state, player, "chef", "2"), Ledger.shown(state, player, "chef", "2", true))
        assertEquals(Ledger.told(state, player, "chef", "3", impaired = true),
            Ledger.shown(state, player, "chef", "3", false))
        val group = Ledger.shown(state, null, "minioninfo", "Ben and Cleo", null).ledger.single()
        assertEquals(LedgerKind.NOTE, group.kind)
        assertEquals("Shown (custom choice): Ben and Cleo", group.text)
    }
}
