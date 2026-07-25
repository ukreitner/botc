package com.clocktower.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameActionsTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }

    private fun newGame(players: Int = 8): GameState =
        GameActions.newGame(tb, (1..players).map { "P$it" })

    @Test
    fun `kill records death and grants ghost vote`() {
        var state = GameActions.advancePhase(newGame()) // night 1
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        val dead = assertNotNull(state.player(2))
        assertFalse(dead.alive)
        assertFalse(dead.ghostVoteUsed)
        assertEquals(1, state.deaths.size)
        assertTrue(state.deaths.first().atNight)
        // killing again is a no-op
        assertEquals(state, GameActions.kill(state, 2, DeathCause.EXECUTION))
    }

    @Test
    fun `revive clears same-cycle death record`() {
        var state = GameActions.advancePhase(newGame())
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        state = GameActions.revive(state, 2)
        assertTrue(assertNotNull(state.player(2)).alive)
        assertTrue(state.deaths.isEmpty())
    }

    @Test
    fun `phase cycle advances night day night`() {
        var state = newGame()
        assertEquals(Phase.SETUP, state.phase)
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(1, state.cycle)
        state = GameActions.advancePhase(state)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(1, state.cycle)
        state = GameActions.toggleNightStep(state, "poisoner")
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(2, state.cycle)
        assertTrue(state.nightStepsDone.isEmpty(), "night checklist resets each night")
    }

    @Test
    fun `execution threshold is half alive rounded up`() {
        assertEquals(4, Voting.executionThreshold(8))
        assertEquals(4, Voting.executionThreshold(7))
        assertEquals(3, Voting.executionThreshold(5))
        assertEquals(2, Voting.executionThreshold(3))
    }

    @Test
    fun `vote outcome logic`() {
        // Below threshold: safe.
        assertEquals(NominationResult.SAFE, Voting.outcome(votes = 3, threshold = 4, currentHighest = 0))
        // Meets threshold, beats nothing: about to die.
        assertEquals(NominationResult.ABOUT_TO_DIE, Voting.outcome(4, 4, 0))
        // Ties the current highest: nobody dies.
        assertEquals(NominationResult.TIED, Voting.outcome(5, 4, 5))
        // Below the current highest: safe.
        assertEquals(NominationResult.SAFE, Voting.outcome(4, 4, 5))
        // Beats the current highest: new about-to-die.
        assertEquals(NominationResult.ABOUT_TO_DIE, Voting.outcome(6, 4, 5))
    }

    @Test
    fun `random bag matches distribution for trouble brewing`() {
        val characters = data.resolve(tb)
        for (count in 5..15) {
            val bag = assertNotNull(
                GameActions.randomBag(characters, count, Random(count)),
                "no bag for $count players",
            )
            assertEquals(count, bag.size)
            assertTrue(GameActions.validateBag(bag, count).isEmpty(), "invalid bag for $count players")
        }
    }

    @Test
    fun `bag with baron balances outsiders`() {
        val characters = data.resolve(tb)
        // Force many attempts; whenever the Baron lands in a bag it must carry +2 outsiders.
        var seenBaron = false
        for (seed in 0..60) {
            val bag = GameActions.randomBag(characters, 10, Random(seed)) ?: continue
            if (bag.any { it.id == "baron" }) {
                seenBaron = true
                assertEquals(2, bag.count { it.team == Team.OUTSIDER })
                assertEquals(5, bag.count { it.team == Team.TOWNSFOLK })
            }
        }
        assertTrue(seenBaron, "baron never appeared across 60 seeds")
    }

    @Test
    fun `deal skips travellers and assigns everyone else`() {
        var state = newGame(6)
        state = GameActions.assignCharacter(state, 5, "beggar", isTraveller = true)
        val bag = listOf("imp", "poisoner", "washerwoman", "chef", "butler")
        state = GameActions.deal(state, bag, Random(1))
        assertEquals("beggar", state.player(5)?.characterId)
        val assigned = state.players.filter { !it.isTraveller }.mapNotNull { it.characterId }
        assertEquals(bag.toSet(), assigned.toSet())
    }

    @Test
    fun `nomination bookkeeping`() {
        var state = GameActions.advancePhase(GameActions.advancePhase(newGame())) // day 1
        state = GameActions.recordNomination(
            state,
            Nomination(day = 1, nominatorId = 0, nomineeId = 3, votes = 5, result = NominationResult.ABOUT_TO_DIE),
        )
        assertTrue(GameActions.hasNominatedToday(state, 0))
        assertFalse(GameActions.hasNominatedToday(state, 3))
        assertTrue(GameActions.hasBeenNominatedToday(state, 3))
        assertEquals(5, GameActions.highestVotesToday(state))
    }

    @Test
    fun `night order for an in-play tb game`() {
        var state = newGame(8)
        val assignments = listOf("imp", "poisoner", "washerwoman", "empath", "fortuneteller", "butler", "drunk", "mayor")
        assignments.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state)

        val first = data.nightOrder.firstNight(state, data::character)
        val ids = first.map { it.id }
        // Dusk, minion info, demon info present; poisoner before washerwoman; imp does not act night one.
        assertTrue(ids.contains(NightMarkers.DUSK))
        assertTrue(ids.contains(NightMarkers.MINION_INFO))
        assertTrue(ids.contains(NightMarkers.DEMON_INFO))
        assertTrue(ids.indexOf("poisoner") < ids.indexOf("washerwoman"))
        assertFalse(ids.contains("imp"))
        assertFalse(ids.contains("mayor"))

        val other = data.nightOrder.otherNight(state, data::character)
        val otherIds = other.map { it.id }
        assertTrue(otherIds.contains("imp"))
        assertTrue(otherIds.indexOf("poisoner") < otherIds.indexOf("imp"))
        assertTrue(otherIds.indexOf("imp") < otherIds.indexOf("empath"))
    }

    @Test
    fun `minion and demon info omitted in teensyville games`() {
        var state = newGame(5)
        listOf("imp", "poisoner", "washerwoman", "empath", "mayor").forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        state = GameActions.advancePhase(state)
        val ids = data.nightOrder.firstNight(state, data::character).map { it.id }
        assertFalse(ids.contains(NightMarkers.MINION_INFO))
        assertFalse(ids.contains(NightMarkers.DEMON_INFO))
    }

    @Test
    fun `game state survives serialization round trip`() {
        var state = newGame(7)
        state = GameActions.assignCharacter(state, 0, "imp")
        state = GameActions.addReminder(state, 0, PlacedReminder("imp", "Dead"))
        state = GameActions.advancePhase(state)
        state = GameActions.kill(state, 1, DeathCause.DEMON)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(GameState.serializer(), state)
        val back = json.decodeFromString(GameState.serializer(), text)
        assertEquals(state, back)
    }
}
