package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Endings (WP3, ARCHITECTURE §2.8; day-engine §G; lead D40).
 *
 * Every advisory carries a stable `ruleId`; the dusk rules are ordered with the
 * Vortox before the Mayor and every match is returned, so a collision is visible
 * rather than silently resolved; and a final Heretic pass inverts the lot.
 */
class WinCheckTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val sv = data.builtInScripts().first { it.id == "sv" }
    private val lookup: (String) -> Character? = data::character

    private fun newGame(script: Script = tb, players: Int = 8): GameState =
        GameActions.newGame(script, (1..players).map { "P$it" })

    private fun day1(state: GameState): GameState =
        GameActions.advancePhase(GameActions.advancePhase(state))

    private fun assign(state: GameState, seat: Long, id: String, traveller: Boolean = false) =
        GameActions.assignCharacter(state, seat, id, traveller)

    private fun killAll(state: GameState, seats: List<Long>): GameState {
        var next = state
        for (seat in seats) {
            next = Deaths.attempt(next, lookup, seat, KillCause(DeathCause.STORYTELLER)).state
        }
        return next
    }

    // ==================================================================
    // Dusk rules
    // ==================================================================

    @Test
    fun `the Mayor wins at dusk with three alive and no execution — travellers included`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 1L, "mayor")
        state = assign(state, 2L, "chef")
        state = assign(state, 7L, "scapegoat", traveller = true)
        state = day1(state)
        state = killAll(state, listOf(2L, 3L, 4L, 5L, 6L))
        assertEquals(3, state.aliveCountWithTravellers, "two residents plus a Traveller")

        state = Execution.noExecution(state)
        val advisories = WinCheck.duskCheck(state, lookup)
        val mayor = assertNotNull(advisories.find { it.ruleId == WinCheck.RULE_MAYOR_DUSK })
        assertEquals(true, mayor.goodWins)
        assertTrue(mayor.blocking)
        assertTrue(mayor.cautions.any { "Travellers count" in it })
    }

    @Test
    fun `the Mayor wins on a tie, because a tie is a no-execution`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 1L, "mayor")
        state = day1(state)
        state = killAll(state, listOf(4L, 5L, 6L, 7L))
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 1L, 2L, votes = 3, result = NominationResult.ABOUT_TO_DIE),
        )
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 2L, 3L, votes = 3, result = NominationResult.TIED),
        )
        assertNull(DayRules.aboutToDie(state), "a tie clears the block")
        assertEquals(4, state.aliveCountWithTravellers)

        state = killAll(state, listOf(3L))
        state = Execution.noExecution(state)
        assertEquals(3, state.aliveCountWithTravellers)
        assertTrue(WinCheck.duskCheck(state, lookup).any { it.ruleId == WinCheck.RULE_MAYOR_DUSK })
    }

    @Test
    fun `the Vortox wins at dusk when no execution was recorded`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 1L, "chef")
        state = day1(state)

        val advisories = WinCheck.duskCheck(state, lookup)
        val vortox = assertNotNull(advisories.firstOrNull())
        assertEquals(WinCheck.RULE_VORTOX_DUSK, vortox.ruleId, "the Vortox is checked first")
        assertEquals(false, vortox.goodWins)
        assertTrue(vortox.blocking)
    }

    @Test
    fun `an execution that killed nobody still satisfies the Vortox`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 3L, "chef")
        state = day1(state)
        state = Execution.execute(
            state, lookup, playerId = 3L,
            outcome = ExecutionOutcome.SURVIVED, preventedBy = "devilsadvocate",
        )
        assertTrue(state.deaths.isEmpty(), "nobody died")
        assertTrue(
            WinCheck.duskCheck(state, lookup).none { it.ruleId == WinCheck.RULE_VORTOX_DUSK },
            "an execution happened, which is all the Vortox asks",
        )

        // A declared no-execution does NOT satisfy it.
        var none = newGame(sv, 8)
        none = assign(none, 0L, "vortox")
        none = day1(none)
        none = Execution.noExecution(none)
        assertTrue(WinCheck.duskCheck(none, lookup).any { it.ruleId == WinCheck.RULE_VORTOX_DUSK })
    }

    @Test
    fun `an impaired Vortox loses the no-execution clause with the rest of the ability`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 1L, "pithag")
        state = day1(state)
        state = GameActions.addReminder(state, 0L, PlacedReminder("poisoner", "Poisoned"))
        assertTrue(
            WinCheck.duskCheck(state, lookup).none { it.ruleId == WinCheck.RULE_VORTOX_DUSK },
            "lead D11: an impaired Vortox loses the whole ability",
        )
    }

    @Test
    fun `the Vortox is ordered before the Mayor and the Mayor carries the collision caution`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 1L, "mayor")
        state = day1(state)
        state = killAll(state, listOf(3L, 4L, 5L, 6L, 7L))
        assertEquals(3, state.aliveCountWithTravellers)
        state = Execution.noExecution(state)

        val advisories = WinCheck.duskCheck(state, lookup)
        assertEquals(WinCheck.RULE_VORTOX_DUSK, advisories.first().ruleId)
        val mayor = assertNotNull(advisories.find { it.ruleId == WinCheck.RULE_MAYOR_DUSK })
        assertTrue(
            mayor.cautions.any { "COLLISION" in it },
            "both matches are returned so the collision is visible: ${mayor.cautions}",
        )
        assertTrue(advisories.all { it.ruleId.isNotEmpty() })
    }

    @Test
    fun `the Leviathan wins at the end of day five`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "leviathan")
        state = day1(state)
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Day 5"))
        assertEquals(5, DayRules.leviathanDay(state))
        val advisory = assertNotNull(
            WinCheck.duskCheck(state, lookup).find { it.ruleId == WinCheck.RULE_LEVIATHAN_DAY5 },
        )
        assertEquals(false, advisory.goodWins)
        assertTrue(advisory.blocking)
    }

    @Test
    fun `two executed good players hand the Leviathan the game`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "leviathan")
        state = assign(state, 1L, "chef")
        state = day1(state)
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Good Player Executed"))
        assertNull(WinCheck.check(state, lookup), "one is not enough")
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Good Player Executed"))
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_LEVIATHAN_TWO_GOOD, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    @Test
    fun `the Zombuul dusk briefing is not a win`() {
        var state = newGame(bmr, 8)
        state = assign(state, 0L, "zombuul")
        state = day1(state)
        val advisory = assertNotNull(
            WinCheck.duskCheck(state, lookup).find { it.ruleId == WinCheck.RULE_ZOMBUUL_NIGHT },
        )
        assertNull(advisory.goodWins, "a briefing, not an ending")
        assertFalse(advisory.blocking)
    }

    // ==================================================================
    // Continuous rules
    // ==================================================================

    @Test
    fun `a Zombuul that only registers dead has not ended the game`() {
        var state = newGame(bmr, 8)
        state = assign(state, 0L, "zombuul")
        state = assign(state, 1L, "chef")
        state = day1(state)

        val attempt = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION))
        state = attempt.state
        assertTrue(attempt.outcome is KillOutcome.RegistersDead)
        assertFalse(assertNotNull(state.player(0)).alive, "stored dead")
        assertTrue(state.isTrulyAlive(0L), "but alive by the rules")
        assertNull(
            WinCheck.check(state, lookup),
            "a Zombuul's first death is not 'every Demon is dead'",
        )
    }

    @Test
    fun `a Summoner that has not created a Demon does not read as every Demon dead`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "summoner")
        state = assign(state, 1L, "chef")
        state = assign(state, 2L, "empath")
        state = GameActions.advancePhase(state) // night 1
        assertNull(WinCheck.check(state, lookup), "there is no Demon yet, not a dead one")
    }

    @Test
    fun `two alive residents end the game even with no Demon seat`() {
        var state = newGame(tb, 8)
        // A Lil' Monsta game: the Demon is a token, not a seat.
        state = assign(state, 0L, "poisoner")
        state = assign(state, 1L, "baron")
        for (seat in 2L..7L) state = assign(state, seat, "chef")
        state = day1(state)
        state = killAll(state, listOf(2L, 3L, 4L, 5L, 6L, 7L))
        assertEquals(2, state.aliveCountResidents)

        val advisory = assertNotNull(
            WinCheck.check(state, lookup),
            "the Glossary states the 2-alive rule unconditionally",
        )
        assertEquals(WinCheck.RULE_TWO_ALIVE, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    @Test
    fun `every Demon dead is still the ordinary good win`() {
        var state = newGame(tb, 8)
        val cast = listOf("imp", "scarletwoman", "washerwoman", "empath", "chef", "recluse", "soldier", "mayor")
        cast.forEachIndexed { i, id -> state = assign(state, i.toLong(), id) }
        state = day1(state)
        assertNull(WinCheck.check(state, lookup))

        val demonDead = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state
        val advisory = assertNotNull(WinCheck.check(demonDead, lookup))
        assertEquals(WinCheck.RULE_DEMON_DEAD, advisory.ruleId)
        assertEquals(true, advisory.goodWins)
        assertTrue(advisory.cautions.any { "Scarlet Woman" in it })
    }

    @Test
    fun `a Saint executed but surviving does not lose the game`() {
        var state = newGame(tb, 5)
        listOf("imp", "saint", "mayor", "chef", "spy")
            .forEachIndexed { i, id -> state = assign(state, i.toLong(), id) }
        state = day1(state)

        val survived = Execution.execute(
            state, lookup, playerId = 1L,
            outcome = ExecutionOutcome.SURVIVED, preventedBy = "pacifist",
        )
        assertTrue(survived.deaths.isEmpty())
        assertNull(
            WinCheck.check(survived, lookup)?.takeIf { it.ruleId == WinCheck.RULE_SAINT },
            "an execution that killed nobody did not kill the Saint",
        )

        val died = Execution.execute(state, lookup, playerId = 1L)
        val advisory = assertNotNull(WinCheck.check(died, lookup))
        assertEquals(WinCheck.RULE_SAINT, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    @Test
    fun `a poisoned Saint does not lose the game, and the record decides it`() {
        var state = newGame(tb, 5)
        listOf("imp", "saint", "mayor", "chef", "poisoner")
            .forEachIndexed { i, id -> state = assign(state, i.toLong(), id) }
        state = GameActions.addReminder(state, 1L, PlacedReminder("poisoner", "Poisoned"))
        state = day1(state)
        state = Execution.execute(state, lookup, playerId = 1L)
        assertEquals(true, state.executions.single().abilityImpairedAtExecution)
        assertNull(
            WinCheck.check(state, lookup)?.takeIf { it.ruleId == WinCheck.RULE_SAINT },
            "a poisoned Saint has no ability",
        )

        // Removing the poison afterwards must not rewrite the execution.
        val cleaned = GameActions.removeReminder(state, 1L, 0)
        assertNull(
            WinCheck.check(cleaned, lookup)?.takeIf { it.ruleId == WinCheck.RULE_SAINT },
            "the snapshot on the ExecutionRecord is the authority",
        )
    }

    @Test
    fun `a claimed and executed Goblin wins for evil`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 4L, "goblin")
        state = day1(state)
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 1L, 4L, votes = 5, goblinClaim = true),
            force = true,
        )
        state = Execution.execute(state, lookup, playerId = 4L, nominationIndex = 0)

        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_GOBLIN_CLAIM, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    @Test
    fun `a claimed Goblin who survived the execution still wins, with a caution`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 4L, "goblin")
        state = day1(state)
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 1L, 4L, votes = 5, goblinClaim = true),
            force = true,
        )
        state = Execution.execute(
            state, lookup, playerId = 4L, nominationIndex = 0,
            outcome = ExecutionOutcome.SURVIVED, preventedBy = "pacifist",
        )
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_GOBLIN_CLAIM, advisory.ruleId)
        assertTrue(advisory.cautions.any { "killed nobody" in it })
    }

    @Test
    fun `a poisoned Goblin's claim is a caution, not a win`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 4L, "goblin")
        state = GameActions.addReminder(state, 4L, PlacedReminder("poisoner", "Poisoned"))
        state = day1(state)
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 1L, 4L, votes = 5, goblinClaim = true),
            force = true,
        )
        state = Execution.execute(state, lookup, playerId = 4L, nominationIndex = 0)
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_GOBLIN_CLAIM, advisory.ruleId)
        assertNull(advisory.goodWins, "the storyteller must rule, and the answer is not a win")
        assertTrue(advisory.cautions.any { "drunk or poisoned" in it })
    }

    @Test
    fun `a Goblin exiled rather than executed does not win`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 4L, "goblin", traveller = true)
        state = day1(state)
        state = DayRules.record(
            state, lookup,
            Nomination(state.cycle, 1L, 4L, votes = 5, goblinClaim = true, isExile = true),
            force = true,
        )
        state = Execution.exile(state, lookup, 4L)
        assertTrue(state.executions.isEmpty(), "an exile writes no ExecutionRecord")
        assertNull(WinCheck.check(state, lookup)?.takeIf { it.ruleId == WinCheck.RULE_GOBLIN_CLAIM })
    }

    @Test
    fun `the Fearmonger's win needs the Fearmonger to have nominated`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 2L, "fearmonger")
        state = assign(state, 5L, "chef")
        state = GameActions.addReminder(state, 5L, PlacedReminder("fearmonger", "Fear"))
        state = day1(state)

        val byOther = Execution.execute(state, lookup, playerId = 5L, nominatorId = 1L)
        assertNull(
            WinCheck.check(byOther, lookup)?.takeIf { it.ruleId == WinCheck.RULE_FEARMONGER },
            "someone else nominated: an ordinary execution",
        )

        val byFearmonger = Execution.execute(state, lookup, playerId = 5L, nominatorId = 2L)
        val advisory = assertNotNull(WinCheck.check(byFearmonger, lookup))
        assertEquals(WinCheck.RULE_FEARMONGER, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    // ==================================================================
    // The two final passes
    // ==================================================================

    @Test
    fun `the Heretic inverts every advisory and names itself`() {
        var state = newGame(tb, 8)
        val cast = listOf("imp", "scarletwoman", "washerwoman", "empath", "chef", "heretic", "soldier", "mayor")
        cast.forEachIndexed { i, id -> state = assign(state, i.toLong(), id) }
        state = day1(state)
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state

        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_DEMON_DEAD, advisory.ruleId, "the ruleId is unchanged")
        assertEquals(false, advisory.goodWins, "good winning means evil wins")
        assertTrue("Heretic" in advisory.reason, advisory.reason)

        // It works while dead…
        val deadHeretic = Deaths.attempt(state, lookup, 5L, KillCause(DeathCause.STORYTELLER)).state
        assertEquals(false, assertNotNull(WinCheck.check(deadHeretic, lookup)).goodWins)

        // …and is suppressed while impaired.
        val poisoned = GameActions.addReminder(state, 5L, PlacedReminder("poisoner", "Poisoned"))
        assertEquals(true, assertNotNull(WinCheck.check(poisoned, lookup)).goodWins)
    }

    @Test
    fun `the Heretic also inverts a dusk advisory`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 1L, "heretic")
        state = day1(state)
        val vortox = assertNotNull(
            WinCheck.duskCheck(state, lookup).find { it.ruleId == WinCheck.RULE_VORTOX_DUSK },
        )
        assertEquals(true, vortox.goodWins, "the Vortox's evil win becomes a good one")
    }

    @Test
    fun `an Atheist suppresses every evil win and adds its own`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 1L, "atheist")
        state = day1(state)
        state = killAll(state, listOf(2L, 3L, 4L, 5L, 6L, 7L))
        assertEquals(2, state.aliveCountResidents)

        val suppressed = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_TWO_ALIVE, suppressed.ruleId)
        assertNull(suppressed.goodWins, "evil cannot win while the Atheist's ability works")
        assertTrue(suppressed.cautions.any { "Atheist" in it })

        val executed = Execution.execute(
            state, lookup, playerId = GameState.STORYTELLER_SEAT_ID, force = true,
        )
        val advisory = assertNotNull(
            WinCheck.check(executed, lookup)?.takeIf { it.ruleId == WinCheck.RULE_ATHEIST }
                ?: WinCheck.dawnCheck(executed, lookup).find { it.ruleId == WinCheck.RULE_ATHEIST },
        )
        assertEquals(true, advisory.goodWins)
    }

    @Test
    fun `advisory dedupe keys on ruleId, never on the prose`() {
        val a = WinCheck.Advisory(true, "Every Demon is dead.", ruleId = WinCheck.RULE_DEMON_DEAD)
        val b = WinCheck.Advisory(true, "The last Demon has died.", ruleId = WinCheck.RULE_DEMON_DEAD)
        val c = WinCheck.Advisory(true, "Every Demon is dead.", ruleId = WinCheck.RULE_TWO_ALIVE)

        assertEquals(listOf(a), WinCheck.dedupe(listOf(a, b)), "same rule, different words")
        assertEquals(listOf(a, c), WinCheck.dedupe(listOf(a, c)), "same words, different rules")
    }

    @Test
    fun `duskCheck never returns two advisories with one ruleId`() {
        var state = newGame(sv, 8)
        state = assign(state, 0L, "vortox")
        state = assign(state, 1L, "mayor")
        state = day1(state)
        state = killAll(state, listOf(3L, 4L, 5L, 6L, 7L))
        state = Execution.noExecution(state)
        val ids = WinCheck.duskCheck(state, lookup).map { it.ruleId }
        assertEquals(ids.distinct(), ids, ids.toString())
        assertTrue(ids.size >= 2, "both the Vortox and the Mayor matched: $ids")
    }

    // ==================================================================
    // End-game questions and per-player results
    // ==================================================================

    @Test
    fun `the end-game dialog is blocked by the questions the grimoire cannot answer`() {
        var state = newGame(tb, 8)
        state = assign(state, 0L, "imp")
        state = assign(state, 1L, "politician")
        state = assign(state, 2L, "cultleader")
        state = day1(state)

        val questions = WinCheck.endGameQuestions(state, lookup)
        assertEquals(setOf("politician", "cultleader"), questions.map { it.sourceId }.toSet())
        assertTrue(questions.all { it.options.isNotEmpty() })
        assertTrue(questions.all { it.id.isNotEmpty() })

        assertTrue(WinCheck.endGameQuestions(newGame(), lookup).isEmpty())
    }

    @Test
    fun `results give every seat a win or a loss, travellers by their alignment`() {
        var state = newGame(tb, 6)
        state = assign(state, 0L, "imp")
        state = assign(state, 1L, "poisoner")
        state = assign(state, 2L, "chef")
        state = assign(state, 3L, "saint")
        state = assign(state, 4L, "beggar", traveller = true)
        state = Seats.setAlignment(state, 4L, Alignment.EVIL)
        state = day1(state)

        val good = WinCheck.results(state, lookup, goodWins = true)
        assertEquals(6, good.size)
        assertEquals(false, good[0L], "the Imp loses")
        assertEquals(false, good[1L], "the Poisoner loses")
        assertEquals(true, good[2L], "the Chef wins")
        assertEquals(true, good[3L], "the Saint wins")
        assertEquals(false, good[4L], "an evil Traveller loses with evil")

        val evil = WinCheck.results(state, lookup, goodWins = false)
        assertEquals(true, evil[0L])
        assertEquals(false, evil[2L])
        assertEquals(true, evil[4L])
    }
}
