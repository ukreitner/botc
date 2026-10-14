package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Phase confirmations exercised against real nominations, deaths, and saved games. */
class PhaseTransitionPlaytestTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private fun day(vararg characters: String): GameState {
        var state = GameActions.newGame(
            data.builtInScripts().first { it.id == "tb" },
            characters.map { it.replaceFirstChar(Char::uppercase) },
        )
        characters.forEachIndexed { i, id ->
            state = Seats.assignCharacter(state, state.players[i].id, id, lookup(id)?.team == Team.TRAVELLER)
        }
        state = Phases.advanceFrom(state, Phase.SETUP, state.cycle, lookup)
        return Phases.advanceFrom(state, Phase.NIGHT, 1, lookup)
    }

    private fun block(state: GameState, role: String): GameState {
        val target = state.players.first { it.characterId == role }.id
        val voters = state.alivePlayers.take(4).map { it.id }
        val tally = DayRules.tally(state, lookup, voters, isExile = false)
        val result = Voting.outcome(
            tally, DayRules.voteRules(state, lookup, isExile = false).threshold,
            DayRules.highestVotesToday(state),
        )
        val next = DayRules.record(
            state, lookup,
            Nomination(state.cycle, state.players.first().id, target,
                votes = tally, voterIds = voters, result = result),
        )
        assertEquals(target, DayRules.aboutToDie(next), "the actual vote puts $role on the block")
        return next
    }

    @Test
    fun `a dusk execution waits for the Pacifist choice and then records survival before night`() {
        val state = block(day("pacifist", "chef", "empath", "monk", "soldier", "poisoner", "imp"), "chef")
        val target = assertNotNull(DayRules.aboutToDie(state))

        // This was the broken path: the execution returned unchanged and the
        // dusk handler nevertheless advanced to night with no execution.
        assertEquals(state, Execution.execute(state, lookup, target))
        assertEquals(state, Phases.closeDay(state, 1, lookup, executePlayerId = target))

        val resolved = Execution.execute(state, lookup, target, optionId = Deaths.OPTION_LIVES)
        assertEquals(ExecutionOutcome.SURVIVED, DayRules.executionToday(resolved)?.outcome)
        assertTrue(assertNotNull(resolved.player(target)).alive)
        val night = Phases.closeDay(resolved, 1, lookup)
        assertEquals(Phase.NIGHT, night.phase)
        assertEquals(2, night.cycle)
        assertEquals(ExecutionOutcome.SURVIVED, night.executions.single().outcome)
    }

    @Test
    fun `executing at dusk and entering the night produces one complete next state`() {
        val state = block(day("washerwoman", "chef", "empath", "monk", "soldier", "poisoner", "imp"), "chef")
        val target = assertNotNull(DayRules.aboutToDie(state))
        val next = Phases.closeDay(state, 1, lookup, executePlayerId = target)
        assertEquals(Phase.NIGHT, next.phase)
        assertEquals(2, next.cycle)
        assertFalse(assertNotNull(next.player(target)).alive)
        assertEquals(ExecutionOutcome.DIED, next.executions.single().outcome)
        assertEquals(state.players.first().id, next.executions.single().nominatorId)
        assertEquals(0, next.executions.single().nominationIndex)
        assertEquals(1, next.deaths.single().day)
        assertFalse(next.deaths.single().atNight)
        assertEquals(next, Phases.closeDay(next, 1, lookup, executePlayerId = target), "double confirmation cannot skip night")
    }

    @Test
    fun `a dead nominee still receives an execution record when dusk closes`() {
        var state = day("washerwoman", "chef", "empath", "monk", "soldier", "poisoner", "imp")
        val target = state.players.first { it.characterId == "chef" }.id
        state = Deaths.attempt(state, lookup, target, KillCause(DeathCause.STORYTELLER)).state
        state = block(state, "chef")
        val next = Phases.closeDay(state, 1, lookup, executePlayerId = target)
        assertEquals(Phase.NIGHT, next.phase)
        assertEquals(ExecutionOutcome.SURVIVED, next.executions.single().outcome)
        assertEquals(Execution.PREVENTED_BY_ALREADY_DEAD, next.executions.single().preventedBy)
    }

    @Test
    fun `dusk keeps the whole day unchanged until an owed Deviant exile is resolved`() {
        var state = block(day("washerwoman", "chef", "empath", "monk", "soldier", "poisoner", "imp", "deviant"), "chef")
        val executee = assertNotNull(DayRules.aboutToDie(state))
        val traveller = state.players.last().id
        val voters = state.players.take(5).map { it.id }
        val tally = DayRules.tally(state, lookup, voters, isExile = true)
        val threshold = DayRules.voteRules(state, lookup, isExile = true).threshold
        assertTrue(tally >= threshold)
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, state.players.first().id, traveller,
                voterIds = voters, isExile = true, result = NominationResult.ABOUT_TO_DIE),
        )
        assertEquals(traveller, DayRules.exileOwed(state))
        assertEquals(state, Phases.closeDay(state, 1, lookup, executePlayerId = executee, exilePlayerId = traveller))
        assertTrue(assertNotNull(state.player(executee)).alive, "an unresolved exile cannot leave half a dusk transaction applied")

        val resolved = Execution.exile(state, lookup, traveller, optionId = Deaths.OPTION_LIVES)
        assertEquals(null, DayRules.exileOwed(resolved))
        assertTrue(assertNotNull(resolved.player(traveller)).alive)
        val night = Phases.closeDay(resolved, 1, lookup, executePlayerId = executee)
        assertEquals(Phase.NIGHT, night.phase)
        assertFalse(assertNotNull(night.player(executee)).alive)
        assertTrue(assertNotNull(night.player(traveller)).alive)
    }

    @Test
    fun `repeated dawn and dusk taps across saved nights cannot skip phases or duplicate records`() {
        var state = day("washerwoman", "chef", "empath", "monk", "soldier", "poisoner", "imp")
        val json = Json { encodeDefaults = true }
        for (day in 1..5) {
            assertEquals(Phase.DAY, state.phase)
            assertEquals(day, state.cycle)
            val night = Phases.closeDay(state, day, lookup, recordNoExecution = true)
            assertEquals(Phase.NIGHT, night.phase)
            assertEquals(day + 1, night.cycle)
            assertEquals(day, night.executions.size)
            assertEquals(night, Phases.closeDay(night, day, lookup, recordNoExecution = true))
            val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), night))
            val plan = NightPlan.build(restored, lookup)
            val completed = restored.copy(nightStepsDone = plan.steps.filter { it.required }.map { it.key.token }.toSet())
            state = Phases.advanceFrom(completed, Phase.NIGHT, day + 1, lookup)
            assertEquals(state, Phases.advanceFrom(state, Phase.NIGHT, day + 1, lookup), "double dawn does not close the new day")
            assertEquals(state, Phases.closeDay(state, day, lookup, recordNoExecution = true), "a stale previous-day sheet cannot close today")
            assertTrue(state.executions.all { it.outcome == ExecutionOutcome.NO_EXECUTION })
        }
    }
}
