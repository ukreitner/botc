package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

    // ==================================================================
    // §5.5(1) — a save from the PRE-REBUILD app, in full
    // ==================================================================

    /**
     * A complete game save in the shape the app wrote at commit 078fbd5, the
     * last commit before WP0 — every field of that `GameState`, and nothing
     * that did not exist yet.
     *
     * The `shippedSave` above is a two-seat smoke test; this is the real thing:
     * eight seats covering alive/dead, a spent ghost vote, a Traveller, a
     * believed identity, a flipped alignment, a per-seat note, a recognised
     * token and a source-less one, plus every one of the five 078fbd5
     * `DeathCause` values, an exile, and the whole storyteller header.
     */
    private val preRebuildSave = """
        {
          "script": {
            "id": "homebrew-1",
            "name": "Harbour Lights",
            "author": "The Storyteller",
            "characterIds": ["imp", "poisoner", "chef", "empath", "monk", "butler", "saint", "beggar"],
            "customCharacters": [],
            "isBuiltIn": false
          },
          "players": [
            { "id": 0, "name": "Ana", "characterId": "imp", "shownCharacterId": null,
              "alignmentFlipped": false, "alive": true, "ghostVoteUsed": false,
              "isTraveller": false,
              "reminders": [ { "sourceId": "imp", "label": "Dead" } ],
              "note": "claimed Chef on day 1" },
            { "id": 1, "name": "Bo", "characterId": "poisoner",
              "alignmentFlipped": false, "alive": true, "ghostVoteUsed": false,
              "isTraveller": false,
              "reminders": [], "note": "" },
            { "id": 2, "name": "Cy", "characterId": "drunk", "shownCharacterId": "chef",
              "alignmentFlipped": false, "alive": true, "ghostVoteUsed": false,
              "isTraveller": false,
              "reminders": [ { "sourceId": "poisoner", "label": "Poisoned" } ],
              "note": "thinks they are the Chef" },
            { "id": 3, "name": "Di", "characterId": "empath",
              "alignmentFlipped": false, "alive": false, "ghostVoteUsed": true,
              "isTraveller": false,
              "reminders": [], "note": "" },
            { "id": 4, "name": "Ed", "characterId": "monk",
              "alignmentFlipped": true, "alive": false, "ghostVoteUsed": false,
              "isTraveller": false,
              "reminders": [ { "sourceId": "", "label": "Bootlegger: owes a favour" } ],
              "note": "turned evil by a house rule" },
            { "id": 5, "name": "Fi", "characterId": "butler",
              "alignmentFlipped": false, "alive": false, "ghostVoteUsed": false,
              "isTraveller": false, "reminders": [], "note": "" },
            { "id": 6, "name": "Gus", "characterId": "saint",
              "alignmentFlipped": false, "alive": false, "ghostVoteUsed": false,
              "isTraveller": false, "reminders": [], "note": "" },
            { "id": 7, "name": "Hal", "characterId": "beggar",
              "alignmentFlipped": false, "alive": false, "ghostVoteUsed": false,
              "isTraveller": true, "reminders": [], "note": "joined on day 2" }
          ],
          "fabledIds": ["sentinel", "djinn"],
          "phase": "DAY",
          "cycle": 4,
          "demonBluffIds": ["slayer", "undertaker", "virgin"],
          "nominations": [
            { "day": 2, "nominatorId": 3, "nomineeId": 6, "votes": 4,
              "voterIds": [3, 4, 5, 0], "result": "ABOUT_TO_DIE", "isExile": false },
            { "day": 3, "nominatorId": 0, "nomineeId": 7, "votes": 5,
              "voterIds": [0, 1, 2, 4, 5], "result": "ABOUT_TO_DIE", "isExile": true },
            { "day": 4, "nominatorId": 1, "nomineeId": 2, "votes": 2,
              "voterIds": [1, 0], "result": "SAFE", "isExile": false }
          ],
          "deaths": [
            { "playerId": 3, "day": 1, "atNight": true, "cause": "DEMON",
              "characterIdAtDeath": "empath", "abilityImpairedAtDeath": false,
              "resurrected": false },
            { "playerId": 6, "day": 2, "atNight": false, "cause": "EXECUTION",
              "characterIdAtDeath": "saint", "abilityImpairedAtDeath": true,
              "resurrected": false },
            { "playerId": 7, "day": 3, "atNight": false, "cause": "EXILE",
              "characterIdAtDeath": "beggar", "abilityImpairedAtDeath": false,
              "resurrected": false },
            { "playerId": 5, "day": 3, "atNight": true, "cause": "OTHER_NIGHT_DEATH",
              "characterIdAtDeath": "butler", "abilityImpairedAtDeath": null,
              "resurrected": false },
            { "playerId": 4, "day": 3, "atNight": true, "cause": "STORYTELLER",
              "characterIdAtDeath": "monk", "abilityImpairedAtDeath": false,
              "resurrected": true }
          ],
          "nightStepsDone": ["poisoner", "monk", "imp"],
          "mastermindDayActive": true,
          "storytellerNotes": "Ed is evil by Bootlegger rule. Watch the Beggar.",
          "updatedAt": 1730000000000
        }
    """.trimIndent()

    /**
     * Every top-level key the 078fbd5 `GameState` could write. Pinned as a set
     * so the fixture above cannot quietly stop covering one of them, and so a
     * reader can check it against `git show 078fbd5:…/GameState.kt` in one pass.
     */
    private val preRebuildKeys = setOf(
        "script", "players", "fabledIds", "phase", "cycle", "demonBluffIds",
        "nominations", "deaths", "nightStepsDone", "mastermindDayActive",
        "storytellerNotes", "updatedAt",
    )

    @Test
    fun `a complete pre-rebuild save migrates with every storyteller-visible fact intact`() {
        // The fixture really is the old schema, whole: no post-WP0 key, every
        // pre-WP0 key.
        assertEquals(
            preRebuildKeys,
            json.parseToJsonElement(preRebuildSave).jsonObject.keys,
            "the fixture must be exactly the 078fbd5 GameState shape",
        )

        val migrated = decode(preRebuildSave).migrated(lookup)

        // script — carried whole, custom characters and all.
        assertEquals("homebrew-1", migrated.script.id)
        assertEquals("Harbour Lights", migrated.script.name)
        assertEquals("The Storyteller", migrated.script.author)
        assertEquals(8, migrated.script.characterIds.size)
        assertFalse(migrated.script.isBuiltIn)

        // players — name, truth, belief, life, ghost vote, Traveller flag.
        assertEquals(
            listOf("Ana", "Bo", "Cy", "Di", "Ed", "Fi", "Gus", "Hal"),
            migrated.players.map { it.name },
        )
        assertEquals(
            listOf("imp", "poisoner", "drunk", "empath", "monk", "butler", "saint", "beggar"),
            migrated.players.map { it.characterId },
        )
        assertEquals("chef", assertNotNull(migrated.player(2)).characterShownToPlayerId)
        assertEquals(
            listOf(true, true, true, false, false, false, false, false),
            migrated.players.map { it.alive },
        )
        assertTrue(assertNotNull(migrated.player(3)).ghostVoteUsed)
        assertTrue(assertNotNull(migrated.player(7)).isTraveller)

        // The flipped alignment becomes the explicit override, and still reads evil.
        assertEquals(Alignment.EVIL, assertNotNull(migrated.player(4)).alignment)
        assertTrue(assertNotNull(migrated.player(4)).isEvil(lookup))
        assertEquals(null, assertNotNull(migrated.player(0)).alignment, "an unflipped seat gains none")

        // Per-seat notes become the append-only note list, text intact.
        assertEquals("claimed Chef on day 1", assertNotNull(migrated.player(0)).notes.single().text)
        assertEquals("thinks they are the Chef", assertNotNull(migrated.player(2)).notes.single().text)
        assertEquals(emptyList(), assertNotNull(migrated.player(1)).notes)

        // Tokens: a recognised one keeps its source, a source-less one becomes
        // the storyteller's rather than "".
        assertEquals("poisoner", assertNotNull(migrated.player(2)).reminders.single().sourceId)
        val houseRule = assertNotNull(migrated.player(4)).reminders.single()
        assertEquals(STORYTELLER_SOURCE_ID, houseRule.sourceId)
        assertEquals("Bootlegger: owes a favour", houseRule.label)

        // fabledIds / demonBluffIds fold into their modern homes and read back.
        assertEquals(listOf("sentinel", "djinn"), migrated.fabled.map { it.id })
        assertEquals(listOf("sentinel", "djinn"), migrated.fabledIds)
        assertEquals(listOf("slayer", "undertaker", "virgin"), migrated.demonBluffIds)
        assertEquals(
            listOf("slayer", "undertaker", "virgin"),
            migrated.bluffSets[BluffRequirement.DEMON_KEY],
        )

        // The whole day history, including the exile and the seats that voted.
        assertEquals(3, migrated.nominations.size)
        assertEquals(listOf(3L, 4L, 5L, 0L), migrated.nominations[0].voterIds)
        assertEquals(NominationResult.ABOUT_TO_DIE, migrated.nominations[0].result)
        assertTrue(migrated.nominations[1].isExile)
        assertEquals(NominationResult.SAFE, migrated.nominations[2].result)
        assertEquals(listOf(2, 3, 4), migrated.nominations.map { it.day })

        // Every death, with the two retired causes mapped onto the taxonomy.
        // `DEMON` is unconditionally `DEMON_KILL`; `OTHER_NIGHT_DEATH` splits on
        // when it happened, because that is the only evidence the old schema
        // left — at night it was the Demon, by day it was the storyteller. The
        // day half of that branch is pinned separately, further down this file.
        assertEquals(5, migrated.deaths.size)
        assertEquals(
            listOf(
                DeathCause.DEMON_KILL, DeathCause.EXECUTION, DeathCause.EXILE,
                DeathCause.DEMON_KILL, DeathCause.STORYTELLER,
            ),
            migrated.deaths.map { it.cause },
        )
        assertEquals(
            listOf("empath", "saint", "beggar", "butler", "monk"),
            migrated.deaths.map { it.characterIdAtDeath },
        )
        assertEquals(
            listOf(false, true, false, null, false),
            migrated.deaths.map { it.abilityImpairedAtDeath },
        )
        assertEquals(listOf(true, false, false, true, true), migrated.deaths.map { it.atNight })
        assertTrue(migrated.deaths.last().resurrected, "the resurrection flag survives")
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), migrated.deaths.map { it.id }, "and each gains an id")
        assertEquals(6L, migrated.nextDeathId)

        // The storyteller's own header.
        assertEquals(Phase.DAY, migrated.phase)
        assertEquals(4, migrated.cycle)
        assertEquals(setOf("poisoner", "monk", "imp"), migrated.nightStepsDone)
        assertTrue(migrated.mastermindDayActive)
        assertEquals("Ed is evil by Bootlegger rule. Watch the Beggar.", migrated.storytellerNotes)
        assertEquals(1730000000000L, migrated.updatedAt)
        assertTrue(migrated.id.isNotBlank(), "and the save is stamped with a game id")

        // Re-encode, re-decode: nothing moved, and migrating again is a no-op.
        val round = decode(json.encodeToString(GameState.serializer(), migrated))
        assertEquals(migrated, round)
        assertEquals(migrated, round.migrated(lookup))
    }

    // ==================================================================
    // §5.2 — today's state, with every field carrying a value
    // ==================================================================

    /**
     * A `GameState` in which every serialised field of the CURRENT schema holds
     * a non-default value.
     *
     * The histories are played rather than hand-built — a real night, a real
     * nomination, a real execution — so `deaths`, `nominations`, `executions`,
     * `ledger`, `effects`, `nightStepsDone`, `lastDawn` and `lastDusk` hold
     * whatever the engine actually writes, not what a test author imagines. The
     * remainder is filled in explicitly.
     *
     * MAINTENANCE: a field added to `GameState` or `Player` belongs here and in
     * the assertions below. A round trip that silently ignores it proves
     * nothing about it. Every serialised element of both types is covered as of
     * the D72 additions (`counters`, `nightImpaired`, `Player.voteTokens`) and
     * `houseRules`.
     */
    private fun everyFieldPopulated(): GameState {
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = Seats.newGame(tb, listOf("Ana", "Bo", "Cy", "Di", "Ed", "Fi"))
        listOf("chef", "empath", "monk", "butler", "poisoner", "imp")
            .forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }

        // Night 1 — a real poisoning, real info rows, a real dawn.
        state = Phases.advancePhase(state, lookup)
        for ((slot, input) in listOf(
            "poisoner" to NightInput(playerIds = listOf(0L)),
            "chef" to NightInput(),
            "empath" to NightInput(),
            "butler" to NightInput(playerIds = listOf(1L)),
        )) {
            val step = assertNotNull(
                NightPlan.build(state, lookup).steps.find { it.slotId == slot },
                "no $slot row",
            )
            state = NightPlan.resolve(state, lookup, step.key, input)
        }

        // Day 1 — a nomination and the execution it carries.
        state = Phases.advancePhase(state, lookup)
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = 1L,
                nomineeId = 5L,
                voterIds = listOf(0L, 1L, 2L),
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
        state = Execution.execute(
            state,
            lookup,
            playerId = 5L,
            nominatorId = 1L,
            nominationIndex = state.nominations.size - 1,
        )
        // Night 2, so `lastDusk` is frozen too, and one row of it resolved so
        // `nightStepsDone` holds the night in progress rather than the one the
        // phase change just cleared.
        state = Phases.advancePhase(state, lookup)
        val nightTwoPoisoner = assertNotNull(
            NightPlan.build(state, lookup).steps.find { it.slotId == "poisoner" },
        )
        state = NightPlan.resolve(
            state,
            lookup,
            nightTwoPoisoner.key,
            NightInput(playerIds = listOf(2L)),
        )

        return state.copy(
            id = "round-trip-fixture",
            updatedAt = 1_730_000_000_000L,
            identityLog = listOf(
                IdentityRecord(
                    playerId = 4L,
                    cycle = 1,
                    atNight = true,
                    fromCharacterId = "poisoner",
                    toCharacterId = "imp",
                    fromEvil = true,
                    toEvil = true,
                    reason = ChangeReason.SCARLET_WOMAN,
                    pendingReveal = false,
                    pendingFirstNightRerun = true,
                    notes = listOf("hand them the Imp token at dawn"),
                ),
            ),
            prompts = state.prompts + Prompt(
                id = state.nextPromptId,
                at = BriefingSlot.DAWN,
                kind = PromptKind.ANNOUNCE,
                sourceId = "professor",
                subjectPlayerId = 3L,
                targetIds = listOf(3L),
                characterIds = listOf("empath"),
                title = "Announce that Di is alive again.",
                detail = "Never say why.",
                dueCycle = 2,
                stepSlotId = "empath",
                causeEventId = 1L,
                optional = true,
            ),
            nextPromptId = state.nextPromptId + 1,
            floatingGrants = listOf(
                FloatingGrant(
                    abilityId = "poisoner",
                    sourceId = "boffin",
                    holder = GrantHolder.ALIVE_DEMON,
                    worksWhileImpaired = true,
                ),
            ),
            storytellerReminders = listOf(
                PlacedReminder(
                    sourceId = STORYTELLER_SOURCE_ID,
                    label = "Vortox rules tonight",
                    characterId = "vortox",
                    targetPlayerId = 2L,
                    note = "house rule agreed before the game",
                    placedCycle = 1,
                ),
            ),
            fabled = listOf(
                FabledEntry(
                    id = "djinn",
                    playerIds = listOf(0L, 2L),
                    spentBy = listOf(1L),
                    used = true,
                    note = "Chef and Monk jinx: only one may be woken.",
                    addedOnCycle = 1,
                    config = mapOf("djinn.rule" to "one wake"),
                ),
            ),
            bluffSets = mapOf(
                BluffRequirement.DEMON_KEY to listOf("slayer", "undertaker", "virgin"),
                "lunatic:2" to listOf("chef", "monk", "saint"),
            ),
            decisions = mapOf(
                Decisions.XAAN_X to "2",
                Decisions.COUNT_TRAVELLERS_FOR_INFO to "true",
            ),
            counters = mapOf(
                Counters.YAGGABABBLE_SAID to 3,
                "homebrew.tally" to 7,
            ),
            // Whatever the played night already recorded, plus two seats the
            // fixture impairs by hand, so the field is non-default either way.
            nightImpaired = state.nightImpaired + setOf(0L, 2L),
            finalDayCycle = 5,
            houseRules = HouseRules(secretVotes = true),
            mastermindDayActive = true,
            storytellerNotes = "Bo is running the table. Watch the Butler.",
            dimLevel = 2,
            legacyDemonBluffIds = listOf("slayer"),
            legacyFabledIds = listOf("djinn"),
            players = state.players.map { player ->
                player.copy(
                    shownCharacterId = player.shownCharacterId ?: "saint",
                    alignment = Alignment.GOOD,
                    ghostVoteUsed = true,
                    // Distinct per seat, none of them the default 1, so a
                    // round trip that shuffled the seats would be caught too.
                    voteTokens = player.id.toInt() + 2,
                    isTraveller = player.id == 3L,
                    leftGame = player.id == 3L,
                    reminders = player.reminders + PlacedReminder(
                        sourceId = "monk",
                        label = "Safe",
                        characterId = "monk",
                        targetPlayerId = 2L,
                        note = "placed by hand",
                        placedCycle = 1,
                    ),
                    grants = listOf(
                        AbilityGrant(
                            abilityId = "chef",
                            sourceId = "philosopher",
                            mode = GrantMode.ADD,
                            slotId = "chef",
                            worksWhileImpaired = true,
                            alwaysFalse = true,
                            cycle = 1,
                            spent = true,
                        ),
                    ),
                    notes = listOf(SeatNote(cycle = 1, phase = Phase.DAY, text = "claimed Chef")),
                    tokenShownAt = 1_729_000_000_000L,
                    standingSince = 3L,
                    legacyNote = "pre-WP0 note",
                    legacyAlignmentFlipped = true,
                )
            },
        )
    }

    @Test
    @kotlin.OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun `today's state with every field populated round-trips through Json`() {
        val state = everyFieldPopulated()

        // Nothing in the fixture is left at its default — a round trip that
        // carries only defaults would pass while proving nothing.
        assertTrue(state.id.isNotBlank())
        assertEquals("tb", state.script.id)
        assertTrue(state.script.characterIds.isNotEmpty())
        assertEquals(6, state.players.size)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(2, state.cycle)
        assertTrue(state.updatedAt > 0L)
        assertTrue(state.deaths.isNotEmpty())
        assertTrue(state.nextDeathId > 1L)
        assertTrue(state.nominations.isNotEmpty())
        assertTrue(state.executions.isNotEmpty())
        assertTrue(state.ledger.isNotEmpty())
        assertTrue(state.nextLedgerId > 1L)
        assertTrue(state.identityLog.isNotEmpty())
        assertTrue(state.effects.isNotEmpty())
        assertTrue(state.nextEffectId > 1L)
        assertTrue(state.prompts.isNotEmpty())
        assertTrue(state.nextPromptId > 1L)
        assertTrue(state.floatingGrants.isNotEmpty())
        assertTrue(state.storytellerReminders.isNotEmpty())
        assertTrue(state.fabled.isNotEmpty())
        assertTrue(state.bluffSets.isNotEmpty())
        assertTrue(state.decisions.isNotEmpty())
        assertEquals(3, Counters.get(state, Counters.YAGGABABBLE_SAID))
        assertEquals(7, Counters.get(state, "homebrew.tally"))
        assertNotNull(state.finalDayCycle)
        assertTrue(state.houseRules.secretVotes)
        assertFalse(state.houseRules.none)
        assertTrue(state.nightStepsDone.isNotEmpty())
        assertTrue(state.nightImpaired.containsAll(setOf(0L, 2L)))
        assertNotNull(state.lastDawn)
        assertNotNull(state.lastDusk)
        assertTrue(state.mastermindDayActive)
        assertTrue(state.storytellerNotes.isNotBlank())
        assertEquals(2, state.dimLevel)
        assertTrue(state.legacyDemonBluffIds.isNotEmpty())
        assertTrue(state.legacyFabledIds.isNotEmpty())
        val seat = assertNotNull(state.player(0))
        assertTrue(state.players.all { it.name.isNotBlank() })
        assertTrue(state.players.any { !it.alive }, "the executed seat must be stored dead")
        assertNotNull(seat.characterId)
        assertNotNull(seat.shownCharacterId)
        assertNotNull(seat.alignment)
        assertTrue(seat.ghostVoteUsed)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), state.players.map { it.voteTokens })
        assertTrue(assertNotNull(state.player(3)).isTraveller)
        assertTrue(assertNotNull(state.player(3)).leftGame)
        assertTrue(seat.reminders.isNotEmpty())
        assertTrue(seat.grants.isNotEmpty())
        assertTrue(seat.notes.isNotEmpty())
        assertNotNull(seat.tokenShownAt)
        assertTrue(seat.standingSince > 0L)
        assertTrue(seat.legacyNote.isNotBlank())
        assertTrue(seat.legacyAlignmentFlipped)

        // The round trip itself: equal by value, in one hop and in two.
        val text = json.encodeToString(GameState.serializer(), state)
        val round = decode(text)
        assertEquals(state, round)
        assertEquals(text, json.encodeToString(GameState.serializer(), round))

        // `encodeDefaults` is what makes an older build able to read this file,
        // so every serialised element must actually be written out.
        val written = json.parseToJsonElement(text).jsonObject.keys
        val declared = GameState.serializer().descriptor.elementNames.toSet()
        assertEquals(
            emptySet(),
            declared - written,
            "encodeDefaults must write every element of GameState",
        )
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
