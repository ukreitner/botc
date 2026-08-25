package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The backwards-compatibility guarantees of ARCHITECTURE §5.5. A save written
 * by the SHIPPED app must decode, migrate and re-encode with every
 * storyteller-visible fact intact.
 */
class PersistenceTest {

    private val data = GameData.loadDefault()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lookup: (String) -> Character? = data::character

    /** Exactly the shape the shipped app writes: legacy field names, no new fields. */
    private val shippedSave = """
        {
          "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": ["imp", "poisoner"] },
          "players": [
            {
              "id": 0, "name": "Ana", "characterId": "imp", "shownCharacterId": null,
              "alignmentFlipped": false, "alive": true, "ghostVoteUsed": false,
              "isTraveller": false,
              "reminders": [ { "sourceId": "poisoner", "label": "Poisoned" } ],
              "note": "claimed Chef"
            },
            {
              "id": 1, "name": "Bo", "characterId": "mayor",
              "alignmentFlipped": true, "alive": false, "ghostVoteUsed": true,
              "isTraveller": false,
              "reminders": [ { "sourceId": "", "label": "Something homebrew" } ],
              "note": ""
            }
          ],
          "fabledIds": ["sentinel"],
          "phase": "DAY",
          "cycle": 2,
          "demonBluffIds": ["chef", "empath", "butler"],
          "nominations": [
            { "day": 2, "nominatorId": 0, "nomineeId": 1, "votes": 3, "voterIds": [0, 1],
              "result": "ABOUT_TO_DIE", "isExile": false }
          ],
          "deaths": [
            { "playerId": 1, "day": 2, "atNight": false, "cause": "EXECUTION",
              "characterIdAtDeath": "mayor", "abilityImpairedAtDeath": false,
              "resurrected": false }
          ],
          "nightStepsDone": ["poisoner", "imp"],
          "mastermindDayActive": false,
          "storytellerNotes": "watch the Butler",
          "updatedAt": 1730000000000
        }
    """.trimIndent()

    private fun decode(text: String): GameState =
        json.decodeFromString(GameState.serializer(), text)

    @Test
    fun `a shipped save decodes migrates and re-encodes with nothing lost`() {
        val migrated = decode(shippedSave).migrated(lookup)

        // Seats, phase, cycle, notes, history: all intact.
        assertEquals(2, migrated.players.size)
        assertEquals(Phase.DAY, migrated.phase)
        assertEquals(2, migrated.cycle)
        assertEquals("watch the Butler", migrated.storytellerNotes)
        assertEquals(setOf("poisoner", "imp"), migrated.nightStepsDone)
        assertEquals(1, migrated.nominations.size)
        assertEquals(NominationResult.ABOUT_TO_DIE, migrated.nominations.single().result)
        assertEquals(1, migrated.deaths.size)
        assertEquals(1L, migrated.deaths.single().playerId)
        assertEquals(DeathCause.EXECUTION, migrated.deaths.single().cause)
        assertEquals("mayor", migrated.deaths.single().characterIdAtDeath)
        assertTrue(assertNotNull(migrated.player(1)).ghostVoteUsed)

        // Legacy fields folded into their modern homes.
        assertEquals(listOf("chef", "empath", "butler"), migrated.demonBluffIds)
        assertEquals(listOf("sentinel"), migrated.fabledIds)
        assertEquals(listOf("sentinel"), migrated.fabled.map { it.id })
        assertEquals("claimed Chef", assertNotNull(migrated.player(0)).notes.single().text)
        assertEquals(Alignment.EVIL, assertNotNull(migrated.player(1)).alignment)

        // A token with no source is rewritten to the storyteller source, never "".
        val homebrew = assertNotNull(migrated.player(1)).reminders.single()
        assertEquals(STORYTELLER_SOURCE_ID, homebrew.sourceId)
        assertEquals("Something homebrew", homebrew.label)
        // A recognised token is still on the seat until WP1 projects it into an Effect.
        assertEquals("poisoner", assertNotNull(migrated.player(0)).reminders.single().sourceId)

        // Re-encoding and decoding again changes nothing.
        val round = decode(json.encodeToString(GameState.serializer(), migrated))
        assertEquals(migrated, round)
    }

    @Test
    fun `migration is idempotent and clears every legacy field`() {
        val once = decode(shippedSave).migrated(lookup)
        val twice = once.migrated(lookup)
        assertEquals(once, twice)

        // The legacy fields are gone from the re-encoded JSON's payload.
        val text = json.encodeToString(GameState.serializer(), once)
        val reread = decode(text)
        assertEquals(emptyList(), reread.legacyFabledIds)
        assertEquals(emptyList(), reread.legacyDemonBluffIds)
        assertEquals("", assertNotNull(reread.player(0)).legacyNote)
        assertEquals(false, assertNotNull(reread.player(1)).legacyAlignmentFlipped)
    }

    @Test
    fun `a save with none of the new fields decodes to sensible defaults`() {
        val bare = decode(
            """{ "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": [] } }""",
        )
        assertEquals(emptyList(), bare.players)
        assertEquals(Phase.SETUP, bare.phase)
        assertEquals(1, bare.cycle)
        assertEquals(emptyList(), bare.ledger)
        assertEquals(emptyList(), bare.effects)
        assertEquals(emptyList(), bare.prompts)
        assertEquals(emptyList(), bare.executions)
        assertEquals(emptyMap(), bare.bluffSets)
        assertEquals(emptyMap(), bare.decisions)
        assertEquals(null, bare.lastDawn)
        assertEquals(null, bare.finalDayCycle)
        assertEquals(0, bare.dimLevel)
        assertEquals(1L, bare.nextEffectId)
        assertEquals(1L, bare.nextLedgerId)
        assertEquals(1L, bare.nextPromptId)
        assertEquals(1L, bare.nextDeathId)
        // And it survives the migration untouched apart from the stamped id.
        val migrated = bare.migrated(lookup)
        assertTrue(migrated.id.isNotBlank())
        assertEquals(bare, migrated.copy(id = ""))
    }

    @Test
    fun `unknown future fields decode without throwing`() {
        val future = decode(
            """
            {
              "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": [] },
              "cycle": 4,
              "somethingFromTheFuture": { "nested": [1, 2, 3] },
              "players": [
                { "id": 0, "name": "Ana", "characterId": "imp", "futureSeatField": true }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(4, future.cycle)
        assertEquals("Ana", assertNotNull(future.player(0)).name)
    }

    @Test
    fun `a PlacedReminder with only sourceId and label decodes`() {
        val reminder = json.decodeFromString(
            PlacedReminder.serializer(),
            """{ "sourceId": "monk", "label": "Safe" }""",
        )
        assertEquals("monk", reminder.sourceId)
        assertEquals("Safe", reminder.label)
        assertEquals(null, reminder.characterId)
        assertEquals(null, reminder.targetPlayerId)
        assertEquals("", reminder.note)
        assertEquals(0, reminder.placedCycle)
    }

    @Test
    fun `bluff sets fabled notes and alignment all round-trip after migration`() {
        var state = decode(shippedSave).migrated(lookup)
        state = Bluffs.set(state, "lunatic:0", listOf("chef", "monk", "virgin"))
        state = Bluffs.setFabled(state, listOf("sentinel", "djinn"))
        state = Seats.setNote(state, 0, "now claims Empath")
        state = Seats.setAlignment(state, 0, Alignment.GOOD)
        state = Decisions.set(state, Decisions.XAAN_X, "2")

        val round = decode(json.encodeToString(GameState.serializer(), state))
        assertEquals(state, round)
        assertEquals(listOf("chef", "monk", "virgin"), round.bluffSets["lunatic:0"])
        assertEquals(listOf("sentinel", "djinn"), round.fabledIds)
        assertEquals("now claims Empath", assertNotNull(round.player(0)).notes.single().text)
        assertEquals(Alignment.GOOD, assertNotNull(round.player(0)).alignment)
        assertEquals(2, Decisions.int(round, Decisions.XAAN_X))
    }

    @Test
    fun `a night-death save maps the deprecated causes onto the new taxonomy`() {
        @Suppress("DEPRECATION")
        val legacyCauses = """
            {
              "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": [] },
              "phase": "NIGHT", "cycle": 3,
              "players": [ { "id": 0, "name": "Ana" }, { "id": 1, "name": "Bo" } ],
              "deaths": [
                { "playerId": 0, "day": 2, "atNight": true, "cause": "DEMON" },
                { "playerId": 1, "day": 2, "atNight": false, "cause": "OTHER_NIGHT_DEATH" }
              ]
            }
        """.trimIndent()
        val migrated = decode(legacyCauses).migrated(lookup)
        assertEquals(DeathCause.DEMON_KILL, migrated.deaths[0].cause)
        assertEquals(DeathCause.STORYTELLER, migrated.deaths[1].cause)
        // Every death gets a stable id, and the counter moves past them.
        assertEquals(listOf(1L, 2L), migrated.deaths.map { it.id })
        assertEquals(3L, migrated.nextDeathId)
    }

    @Test
    fun `an unknown team in the dataset decodes to UNKNOWN instead of throwing`() {
        val character = json.decodeFromString(
            Character.serializer(),
            """{ "id": "wraith", "name": "Wraith", "team": "spectre" }""",
        )
        assertEquals(Team.UNKNOWN, character.team)
        // …and the official spellings still work, including the British one.
        assertEquals(
            Team.TRAVELLER,
            json.decodeFromString(
                Character.serializer(),
                """{ "id": "beggar", "name": "Beggar", "team": "traveller" }""",
            ).team,
        )
        assertEquals("traveler", json.encodeToString(Team.serializer(), Team.TRAVELLER).trim('"'))
    }
}
