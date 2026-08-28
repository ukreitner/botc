package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The three GameState fields of lead D72 — `counters`, `nightImpaired` and
 * `Player.voteTokens`.
 *
 * Every one is defaulted, so a save written before they existed still loads
 * (§5.2); `PersistenceTest` owns the shipped-save corpus and is untouched.
 */
class GameStateFieldsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun script(vararg ids: String) =
        Script(id = "test", name = "Test", characterIds = ids.toList())

    private fun seat(id: Long, name: String, characterId: String) =
        Player(id = id, name = name, characterId = characterId)

    // ------------------------------------------------------------------
    // round trip
    // ------------------------------------------------------------------

    @Test
    fun `the three D72 fields round-trip through json`() {
        val state = GameState(
            script = script("imp", "poisoner", "beggar"),
            players = listOf(
                seat(1, "Ana", "imp"),
                seat(2, "Bo", "poisoner").copy(voteTokens = 0),
                seat(3, "Cy", "beggar").copy(voteTokens = 3, isTraveller = true),
            ),
            counters = mapOf("yaggababble.said" to 2, "homebrew.tally" to 7),
            nightImpaired = setOf(2L, 3L),
        )
        val back = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), state))

        assertEquals(mapOf("yaggababble.said" to 2, "homebrew.tally" to 7), back.counters)
        assertEquals(setOf(2L, 3L), back.nightImpaired)
        assertEquals(listOf(1, 0, 3), back.players.map { it.voteTokens })
    }

    @Test
    fun `a save written before D72 loads with the defaults`() {
        val old = """
            {
              "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": ["imp"] },
              "players": [ { "id": 0, "name": "Ana", "characterId": "imp" } ],
              "phase": "DAY",
              "cycle": 2
            }
        """.trimIndent()
        val state = json.decodeFromString<GameState>(old)

        assertEquals(emptyMap(), state.counters)
        assertEquals(emptySet(), state.nightImpaired)
        assertEquals(1, state.players.single().voteTokens)
    }

    // ------------------------------------------------------------------
    // Counters
    // ------------------------------------------------------------------

    @Test
    fun `Counters bumps, sets and never stores a negative total`() {
        var state = GameState(script = script("imp"))
        assertEquals(0, Counters.get(state, Counters.YAGGABABBLE_SAID))

        state = Counters.bump(state, Counters.YAGGABABBLE_SAID)
        state = Counters.bump(state, Counters.YAGGABABBLE_SAID)
        assertEquals(2, Counters.get(state, Counters.YAGGABABBLE_SAID))

        state = Counters.bump(state, Counters.YAGGABABBLE_SAID, -5)
        assertEquals(0, Counters.get(state, Counters.YAGGABABBLE_SAID))

        state = Counters.set(state, Counters.YAGGABABBLE_SAID, 4)
        assertEquals(4, Counters.get(state, Counters.YAGGABABBLE_SAID))

        state = Counters.clear(state, Counters.YAGGABABBLE_SAID)
        assertTrue(state.counters.isEmpty())
    }

    // ------------------------------------------------------------------
    // nightImpaired — the Acrobat's watermark
    // ------------------------------------------------------------------

    private fun poisonedBoard(): GameState = GameState(
        script = script("imp", "poisoner", "chef", "monk"),
        players = listOf(
            seat(1, "Ana", "imp"),
            seat(2, "Bo", "poisoner"),
            seat(3, "Cy", "chef"),
            seat(4, "Di", "monk"),
        ),
        phase = Phase.DAY,
        cycle = 1,
    )

    @Test
    fun `dusk seeds the watermark with everyone already impaired`() {
        var state = poisonedBoard()
        state = Effects.place(
            state = state,
            target = 3L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 2L,
            until = Until.DUSK_AFTER_N_DAYS,
            label = "Poisoned",
        ).state
        state = Effects.reconcile(state, lookup)
        assertTrue(Status.isImpaired(state, lookup, 3L))

        val night = Phases.advancePhase(state, lookup) { _, _, _ -> null }
        assertEquals(Phase.NIGHT, night.phase)
        assertTrue(3L in night.nightImpaired, "the Chef walked into the night poisoned")
    }

    @Test
    fun `an impairment applied during the night raises the watermark and dawn clears it`() {
        var state = poisonedBoard()
        state = Phases.advancePhase(state, lookup) { _, _, _ -> null }
        assertEquals(Phase.NIGHT, state.phase)
        assertTrue(state.nightImpaired.isEmpty(), "nobody was impaired at dusk")

        state = Effects.place(
            state = state,
            target = 4L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 2L,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        state = Effects.reconcile(state, lookup)
        assertTrue(4L in state.nightImpaired, "the Monk was poisoned tonight")

        // The mark SURVIVES the poison being lifted mid-night: it is a
        // high-water mark, not a live query.
        state = state.copy(effects = emptyList())
        state = Effects.reconcile(state, lookup)
        assertFalse(Status.isImpaired(state, lookup, 4L))
        assertTrue(4L in state.nightImpaired)

        val day = Phases.advancePhase(state, lookup) { _, _, _ -> null }
        assertEquals(Phase.DAY, day.phase)
        assertTrue(day.nightImpaired.isEmpty(), "dawn clears the watermark")
    }
}
