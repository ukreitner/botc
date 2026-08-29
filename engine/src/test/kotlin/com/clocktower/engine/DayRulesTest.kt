package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * House rules (`GameState.houseRules`) and the day rules that honour them.
 *
 * The Organ Grinder is on no base script, so the secret-vote path shipped
 * unreachable and unplayed (playtest C, "what I could not reach"). The house
 * rule is the hand switch `ux/day-screen.md` §F asked for, and it is ORed with
 * the character: turning it off must never open the eyes of a table that has a
 * sober Organ Grinder in it.
 */
class DayRulesTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    private fun day1(players: Int = 8): GameState =
        GameActions.advancePhase(
            GameActions.advancePhase(GameActions.newGame(tb, (1..players).map { "P$it" })),
        )

    private fun secret(state: GameState): GameState =
        state.copy(houseRules = state.houseRules.copy(secretVotes = true))

    @Test
    fun `secret voting is off by default and on with the house rule`() {
        val state = day1()
        assertTrue(state.houseRules.none, "a fresh game plays by the book")
        assertFalse(DayRules.secretVoting(state, lookup))

        assertTrue(DayRules.secretVoting(secret(state), lookup))
    }

    @Test
    fun `the house rule never overrides the Organ Grinder`() {
        var state = day1()
        state = GameActions.assignCharacter(state, 6L, "organgrinder")
        assertTrue(DayRules.secretVoting(state, lookup), "a sober Organ Grinder closes every eye")
        // Explicitly OFF — the character still rules the day.
        assertTrue(
            DayRules.secretVoting(state.copy(houseRules = HouseRules(secretVotes = false)), lookup),
            "a house rule set to off is not a licence to open the eyes",
        )
    }

    @Test
    fun `the vote-rules reason names the house rule, not a character that is not there`() {
        val rules = DayRules.voteRules(secret(day1()), lookup, isExile = false)
        val line = rules.reasons.single { it.startsWith("Secret voting") }
        assertTrue("house rule" in line, line)
        assertFalse("Organ Grinder" in line, "there is no Organ Grinder in a Trouble Brewing bag")

        var withGrinder = day1()
        withGrinder = GameActions.assignCharacter(withGrinder, 6L, "organgrinder")
        val grinderLine = DayRules.voteRules(withGrinder, lookup, isExile = false)
            .reasons.single { it.startsWith("Secret voting") }
        assertTrue("Organ Grinder" in grinderLine, grinderLine)
    }

    @Test
    fun `the standing fact says the eyes are closed, and says why`() {
        val briefing = Briefings.at(secret(day1()), lookup, BriefingSlot.DAY_START)
        val fact = briefing.of(BriefingKind.STANDING_FACT)
            .single { it.key.endsWith(":secret-voting") }
        assertTrue("Eyes closed for every vote today" in fact.text, fact.text)
        assertTrue("House rule" in fact.text, fact.text)
        assertEquals("", fact.sourceId, "no Organ Grinder is in play — do not name one")

        var withGrinder = day1()
        withGrinder = GameActions.assignCharacter(withGrinder, 6L, "organgrinder")
        val grinderFact = Briefings.at(withGrinder, lookup, BriefingSlot.DAY_START)
            .of(BriefingKind.STANDING_FACT)
            .single { it.key.endsWith(":secret-voting") }
        assertEquals("organgrinder", grinderFact.sourceId)
        assertFalse("House rule" in grinderFact.text, grinderFact.text)
    }

    @Test
    fun `the house rule changes nothing but the secrecy`() {
        val open = day1()
        val closed = secret(open)
        val a = DayRules.voteRules(open, lookup, isExile = false)
        val b = DayRules.voteRules(closed, lookup, isExile = false)
        assertEquals(a.threshold, b.threshold, "secrecy is not a rule about who dies")
        assertEquals(a.eligibleVoterIds, b.eligibleVoterIds)
        assertEquals(a.spendsGhostVotes, b.spendsGhostVotes)
        assertEquals(a.weights, b.weights)
        assertEquals(
            5,
            DayRules.tally(closed, lookup, listOf(0L, 1L, 2L, 3L, 4L), isExile = false),
            "the storyteller still counts the same hands",
        )
    }

    @Test
    fun `a save written before house rules existed loads by the book`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """
            {
              "script": { "id": "tb", "name": "Trouble Brewing", "characterIds": ["imp"] },
              "players": [ { "id": 0, "name": "Ana", "characterId": "imp" } ],
              "phase": "DAY",
              "cycle": 2
            }
        """.trimIndent()
        val state = json.decodeFromString(GameState.serializer(), old)
        assertEquals(HouseRules(), state.houseRules)
        assertFalse(DayRules.secretVoting(state, lookup))

        val back = json.decodeFromString(
            GameState.serializer(),
            json.encodeToString(GameState.serializer(), secret(state)),
        )
        assertTrue(back.houseRules.secretVotes, "and the flag survives a round trip")
    }

    /**
     * C2-6: a closed day refuses BOTH halves of a nomination with the same
     * sentence, and the panel drew one row (and one [Allow anyway]) per entry.
     */
    @Test
    fun `a closed day states its blocker once, not once per half`() {
        val closed = Execution.noExecution(day1())
        assertTrue(DayRules.nominationsClosed(closed, lookup))
        // Both halves refuse, in the same words.
        val reason = DayRules.canNominate(closed, lookup, 0L).reason
        assertEquals(reason, DayRules.canBeNominated(closed, lookup, 1L).reason)
        assertTrue(reason.isNotBlank())

        val check = DayRules.checkNomination(closed, lookup, nominatorId = 0L, nomineeId = 1L)
        assertFalse(check.legal)
        assertEquals(listOf(reason), check.blockers, "one reason, one row, one override")
    }

    @Test
    fun `an ordinary blocked nomination still lists both distinct reasons`() {
        var state = day1()
        state = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = 0L, nomineeId = 1L, votes = 0),
        )
        // Ana has nominated; Bo has been nominated. Two different sentences.
        val check = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 1L)
        assertEquals(2, check.blockers.size, check.blockers.toString())
        assertEquals(check.blockers, check.blockers.distinct())
    }
}
