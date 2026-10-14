package com.clocktower.engine

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Arrival, ordinary votes, exile, departure, save/reload, then the following night. */
class TravellerSessionPlaytestTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val script = data.builtInScripts().first { it.id == "tb" }

    private fun day(vararg roles: String): GameState {
        var state = Seats.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, role ->
            state = Seats.assignCharacter(state, i.toLong(), role, lookup(role)?.team == Team.TRAVELLER)
        }
        return Phases.advancePhase(Phases.advancePhase(state, lookup), lookup)
    }

    @Test
    fun `dead player with spent vote calls exile despite Bishop and completed execution`() {
        var state = day("imp", "poisoner", "chef", "empath", "monk", "bishop")
        state = Deaths.kill(state, 2, DeathCause.STORYTELLER, lookup)
        state = state.updatePlayer(2) { it.copy(ghostVoteUsed = true) }
        state = Execution.execute(state, lookup, 3)
        assertTrue(DayRules.nominationsClosed(state, lookup))
        assertFalse(DayRules.canNominate(state, lookup, 2).allowed)
        val check = DayRules.checkNomination(state, lookup, 2, 5)
        assertTrue(check.legal, check.blockers.toString())
        val before = state.nominations.size
        state = DayRules.record(state, lookup, Nomination(
            day = state.cycle, nominatorId = 2, nomineeId = 5,
            voterIds = listOf(0, 2, 3), isExile = true,
            result = NominationResult.ABOUT_TO_DIE,
        ))
        assertEquals(before + 1, state.nominations.size)
        assertEquals(3, state.nominations.last().votes)
        assertEquals(5L, DayRules.exileOwed(state))
        assertTrue(assertNotNull(state.player(2)).ghostVoteUsed)
        state = Execution.exile(state, lookup, 5)
        assertFalse(assertNotNull(state.player(5)).alive)
        assertTrue(assertNotNull(state.player(5)).seated, "Exile is a death; departure is separate")
        assertEquals(1, state.executions.size, "Exile must not add another execution")
    }

    @Test
    fun `exile does not trigger Golem or Witch and does not consume caller nomination`() {
        var state = day("imp", "witch", "golem", "chef", "monk", "beggar")
        state = Effects.reconcile(Effects.addReminder(state, 2, PlacedReminder("witch", "Cursed")), lookup)
        val check = DayRules.checkNomination(state, lookup, 2, 5)
        assertTrue(check.legal)
        assertTrue(check.triggers.none { it.kind != TriggerKind.WARN }, check.triggers.toString())
        state = DayRules.record(state, lookup, Nomination(
            day = 1, nominatorId = 2, nomineeId = 5, voterIds = listOf(2), isExile = true,
        ))
        assertFalse(DayRules.hasNominatedToday(state, 2))
        assertTrue(DayRules.canNominate(state, lookup, 2).allowed)
        assertTrue(assertNotNull(state.player(2)).alive)
        assertFalse(DayRules.canBeExiled(state, 5).allowed, "One call per Traveller per day")
        val tomorrow = Phases.advancePhase(Phases.advancePhase(state, lookup), lookup)
        assertTrue(DayRules.canBeExiled(tomorrow, 5).allowed)
    }

    @Test
    fun `previous nominator can call another exile without overriding normal rules`() {
        var state = day("imp", "poisoner", "chef", "monk", "beggar", "thief")
        state = DayRules.record(state, lookup, Nomination(day = 1, nominatorId = 2, nomineeId = 3))
        assertFalse(DayRules.canNominate(state, lookup, 2).allowed)
        for (traveller in listOf(4L, 5L)) {
            assertTrue(DayRules.checkNomination(state, lookup, 2, traveller).legal)
            state = DayRules.record(state, lookup, Nomination(
                day = 1, nominatorId = 2, nomineeId = traveller, isExile = true,
            ))
        }
        assertEquals(3, state.nominations.size)
        assertFalse(DayRules.checkNomination(state, lookup, 2, 4).legal)
    }

    @Test
    fun `departing Traveller preserves history stops effects and never reuses their identity`() {
        var state = day("imp", "poisoner", "chef", "monk", "bureaucrat").copy(phase = Phase.NIGHT)
        val mark = NightPlan.build(state, lookup).steps.single { it.abilityId == "bureaucrat" }
        state = NightPlan.resolve(state, lookup, mark.key, NightInput(playerIds = listOf(2)))
        state = Phases.advancePhase(state, lookup)
        assertEquals(3, DayRules.voteRules(state, lookup, false).weightOf(2))
        state = DayRules.record(state, lookup, Nomination(
            day = 1, nominatorId = 4, nomineeId = 3, voterIds = listOf(4),
        ))
        state = Seats.leaveTraveller(state, 4)
        assertEquals("P5", state.player(state.nominations.single().nominatorId)?.name)
        assertFalse(assertNotNull(state.player(4)).seated)
        assertTrue(assertNotNull(state.player(4)).alive, "Leaving must not invent a death")
        assertTrue(state.deaths.isEmpty())
        assertFalse(Status.hasAbility(state, lookup, 4))
        assertEquals(1, DayRules.voteRules(state, lookup, false).weightOf(2))
        assertEquals(4, state.seats.size)
        assertEquals(2, DayRules.voteRules(state, lookup, true).threshold)
        assertFalse(4L in DayRules.voteRules(state, lookup, true).eligibleVoterIds)
        state = Json.decodeFromString<GameState>(Json.encodeToString(state))
        state = Seats.addSeat(state, "New arrival", afterId = 1)
        val arrival = state.seats.single { it.name == "New arrival" }
        assertEquals(5L, arrival.id)
        assertEquals(listOf(0L, 1L, 5L, 2L, 3L), state.seats.map { it.id })
        state = Seats.assignCharacter(state, arrival.id, "beggar", isTraveller = true)
        state = Seats.setAlignment(state, arrival.id, Alignment.EVIL)
        state = Phases.advancePhase(state, lookup)
        val plan = NightPlan.build(state, lookup)
        assertTrue(plan.steps.none { it.key.holderId == 4L })
        assertTrue(plan.steps.any { it.title == "New traveller — New arrival" })
    }

    @Test
    fun `exile support ignores duplicate or departed hands and departure clears pending exile`() {
        var state = day("imp", "poisoner", "chef", "monk", "beggar", "thief")
        state = DayRules.record(state, lookup, Nomination(
            day = 1, nominatorId = 2, nomineeId = 5, voterIds = listOf(0, 1, 2),
            result = NominationResult.ABOUT_TO_DIE, isExile = true,
        ))
        assertEquals(5L, DayRules.exileOwed(state))
        state = Seats.leaveTraveller(state, 5)
        assertNull(DayRules.exileOwed(state))
        assertEquals(2, DayRules.tally(state, lookup, listOf(0, 0, 2, 5, 999), isExile = true))
        assertFalse(DayRules.canCallExile(state, 5).allowed)
        assertFalse(DayRules.canBeExiled(state, 5).allowed)
    }

    @Test
    fun `funny Deviant resolves a passed exile without being killed or blocking dusk`() {
        var state = day("imp", "poisoner", "chef", "monk", "deviant")
        state = DayRules.record(state, lookup, Nomination(
            day = 1, nominatorId = 2, nomineeId = 4, voterIds = listOf(0, 1, 2),
            result = NominationResult.ABOUT_TO_DIE, isExile = true,
        ))
        assertEquals(4L, DayRules.exileOwed(state))
        val cause = KillCause(DeathCause.EXILE)
        val unanswered = Deaths.attempt(state, lookup, 4, cause)
        assertTrue(unanswered.outcome is KillOutcome.Choice)
        assertEquals(state, unanswered.state)
        state = Execution.exile(state, lookup, 4, Deaths.OPTION_LIVES)
        assertTrue(assertNotNull(state.player(4)).alive)
        assertTrue(state.nominations.single().exileResolved)
        assertTrue(state.executions.isEmpty())
        assertNull(DayRules.exileOwed(state))
        state = Json.decodeFromString<GameState>(Json.encodeToString(state))
        assertNull(DayRules.exileOwed(state), "Resolution persists across reloads")
        assertFalse(DayRules.canBeExiled(state, 4).allowed, "The call still happened today")
    }

    @Test
    fun `departed evil Traveller no longer counts as an Empath neighbour`() {
        var state = day("imp", "chef", "empath", "beggar", "monk")
        state = Seats.setAlignment(state, 3, Alignment.EVIL)
        val before = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 2))
        assertTrue(before.headline.startsWith("1"), before.headline)
        state = Seats.leaveTraveller(state, 3)
        val after = assertNotNull(InfoCalc.compute(data, state, "empath", holderId = 2))
        assertTrue(after.headline.startsWith("0"), after.headline)
        assertEquals(listOf(1L, 4L), state.seatNeighbours(2).map { it.id })
        // Reordering skips the historical seat rather than wasting a move on it.
        state = Seats.moveSeat(state, 2, 1)
        assertEquals(listOf(0L, 1L, 4L, 2L), state.seats.map { it.id })
    }
}
