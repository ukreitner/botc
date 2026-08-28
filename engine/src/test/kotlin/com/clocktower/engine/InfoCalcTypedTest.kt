package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The typed half of `InfoCalc` (WP2, lead D10/D50): every answer is an [Answer],
 * every answer carries the lies the engine can generate, and impairment,
 * misregistration and the Vortox stay three separate obligations.
 */
class InfoCalcTypedTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    /** 8 seats: 0 = [first], then imp, poisoner, recluse, spy, chef, empath, mayor. */
    private fun game(first: String, vararg rest: String): GameState {
        val roles = listOf(first) + rest.toList()
        var state = GameActions.newGame(tb, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    private fun compute(state: GameState, id: String, holder: Long?, targets: List<Long> = emptyList()) =
        InfoCalc.compute(state, lookup, id, holder, targets)

    // ==================================================================
    // Typed answers
    // ==================================================================

    @Test
    fun `answers are typed, not prose`() {
        val state = game("chef", "imp", "poisoner", "recluse", "spy", "empath", "mayor", "monk")
        val chef = assertNotNull(compute(state, "chef", 0L))
        assertIs<Answer.Count>(chef.answer)
        assertTrue(chef.headline.startsWith("${(chef.answer as Answer.Count).n} pair"))

        val fortune = assertNotNull(compute(state, "fortuneteller", 5L, listOf(1L, 6L)))
        assertIs<Answer.YesNoAnswer>(fortune.answer)
        assertTrue((fortune.answer as Answer.YesNoAnswer).yes, "seat 1 is the Imp")

        val librarian = assertNotNull(compute(state, "librarian", 5L))
        assertIs<Answer.Players>(librarian.answer)
        assertEquals(listOf(3L), (librarian.answer as Answer.Players).ids, "the Recluse is the Outsider")
    }

    @Test
    fun `every supported id can generate a lie`() {
        val missing = mutableListOf<String>()
        for (id in InfoCalc.supportedIds) {
            // A step-scoped alias ("kingdemon") is not a character the bag can
            // hold; seat the character it belongs to.
            val state = wellStockedGame(if (id == "kingdemon") "king" else id)
            val targets = when (InfoCalc.targetsNeeded(id)) {
                0 -> emptyList()
                1 -> listOf(1L)
                else -> listOf(1L, 2L)
            }
            val result = compute(state, id, 0L, targets)
            if (result == null || result.alternatives.isEmpty()) missing += id
        }
        assertTrue(missing.isEmpty(), "no false info could be generated for: $missing")
    }

    @Test
    fun `an answer the engine cannot lie about renders no lie at all`() {
        // No Demon in the grimoire: the Clockmaker has nothing to measure, so the
        // UI contract "no lie generated => no heading" has to be expressible.
        val state = game("clockmaker", "chef", "empath", "mayor", "monk", "butler")
        val result = assertNotNull(compute(state, "clockmaker", 0L))
        assertIs<Answer.Message>(result.answer)
        assertTrue(result.alternatives.isEmpty(), "nothing plausible to show instead")
    }

    @Test
    fun `a numeric lie stays inside the plausible range`() {
        val state = game("empath", "imp", "poisoner", "recluse", "spy", "chef", "mayor", "monk")
        val result = assertNotNull(compute(state, "empath", 0L))
        val counts = result.alternatives.filterIsInstance<Answer.Count>()
        assertTrue(counts.isNotEmpty())
        assertTrue(counts.all { it.n in 0..2 }, "an Empath can only ever show 0, 1 or 2: $counts")
        assertTrue(counts.none { it.n == (result.answer as Answer.Count).n }, "and never the truth")
    }

    @Test
    fun `a character lie prefers a character of the same team that is not in play`() {
        var state = game("undertaker", "imp", "poisoner", "recluse", "spy", "chef", "empath", "mayor")
        state = state.copy(phase = Phase.DAY, cycle = 1)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.EXECUTION)).state
        state = state.copy(phase = Phase.NIGHT, cycle = 2)

        val result = assertNotNull(compute(state, "undertaker", 0L))
        assertEquals(Answer.Characters(listOf("poisoner")), result.answer)
        val lies = result.alternatives.filterIsInstance<Answer.Characters>()
        assertTrue(lies.isNotEmpty())
        assertTrue(lies.none { it.ids == listOf("poisoner") })
        assertTrue(
            lies.first().ids.all { data.character(it)?.team == Team.MINION },
            "the first lie is another Minion: ${lies.first().ids}",
        )
    }

    // ==================================================================
    // Obligation: three reasons, kept apart (lead D50 / D11)
    // ==================================================================

    @Test
    fun `clean information is owed truthfully`() {
        val state = game("empath", "chef", "mayor", "monk", "butler", "virgin")
        val result = assertNotNull(compute(state, "empath", 0L))
        assertEquals(InfoObligation.TRUTH, result.obligation)
        assertTrue(result.caveats.isEmpty(), result.caveats.toString())
        assertFalse(result.abilityMalfunctions)
    }

    @Test
    fun `an impaired holder MAY lie and the caveat says why`() {
        var state = game("empath", "imp", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 2L,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        val result = assertNotNull(compute(state, "empath", 0L))
        assertEquals(InfoObligation.MAY_LIE, result.obligation)
        assertTrue(result.abilityMalfunctions)
        assertTrue(result.caveats.any { "POISONED" in it }, result.caveats.toString())
    }

    @Test
    fun `an alive sober Vortox forces Townsfolk information to be false`() {
        val state = game("empath", "vortox", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
        val result = assertNotNull(compute(state, "empath", 0L))
        assertEquals(InfoObligation.MUST_LIE, result.obligation, "MUST_LIE outranks everything")
        assertTrue(result.caveats.any { "VORTOX" in it })
        assertTrue(result.alternatives.isNotEmpty(), "and the lie must be supplied")
    }

    @Test
    fun `an impaired Vortox loses the whole ability`() {
        var state = game("empath", "vortox", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
        state = Effects.place(
            state = state,
            target = 1L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 2L,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        val result = assertNotNull(compute(state, "empath", 0L))
        assertEquals(
            InfoObligation.TRUTH,
            result.obligation,
            "a drunk or poisoned Vortox does not force false Townsfolk info (lead D11)",
        )
    }

    @Test
    fun `misregistration is its own caveat, not an impairment`() {
        val state = game("chef", "imp", "recluse", "spy", "mayor", "monk", "butler", "virgin")
        val result = assertNotNull(compute(state, "chef", 0L))
        assertEquals(InfoObligation.MAY_LIE, result.obligation)
        assertTrue(result.caveats.any { "Recluse" in it })
        assertTrue(result.caveats.any { "Spy" in it })
        assertFalse(result.abilityMalfunctions, "nothing is wrong with the Chef's ability")
    }

    // ==================================================================
    // The rewritten and the new calculators
    // ==================================================================

    @Test
    fun `the Chambermaid counts what tonight's sheet actually does`() {
        val state = game(
            "chambermaid", "imp", "poisoner", "monk", "ravenkeeper", "mayor", "butler", "virgin",
        ).copy(cycle = 2)
        // Seat 3 is the Monk (wakes for their own ability); seat 4 is an alive
        // Ravenkeeper (does not wake); seat 5 is the Mayor (never wakes).
        val monkAndRavenkeeper = assertNotNull(compute(state, "chambermaid", 0L, listOf(3L, 4L)))
        assertEquals(Answer.Count(1, 0, 2), monkAndRavenkeeper.answer)
        assertEquals(
            NightPlan.wokeCount(state, lookup, listOf(3L, 4L)),
            (monkAndRavenkeeper.answer as Answer.Count).n,
        )
        val neither = assertNotNull(compute(state, "chambermaid", 0L, listOf(4L, 5L)))
        assertEquals(Answer.Count(0, 0, 2), neither.answer)
    }

    @Test
    fun `the Mathematician counts recorded malfunctions and never their own`() {
        var state = game("mathematician", "imp", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
            .copy(cycle = 2)
        state = malfunction(state, seat = 3L, sourceId = "chef")
        state = malfunction(state, seat = 0L, sourceId = "mathematician")

        assertEquals(2, NightPlan.malfunctionCount(state, night = 2))
        val result = assertNotNull(compute(state, "mathematician", 0L))
        assertEquals(
            Answer.Count(1, 0, state.alivePlayers.size),
            result.answer,
            "the Mathematician does not detect their own ability failing",
        )
    }

    @Test
    fun `the Godfather learns which Outsiders are in play`() {
        val state = game("godfather", "imp", "recluse", "butler", "chef", "mayor", "monk", "virgin")
        val result = assertNotNull(compute(state, "godfather", 0L))
        assertEquals(Answer.Characters(listOf("recluse", "butler")), result.answer)
        assertTrue(result.alternatives.isNotEmpty(), "and a not-in-play Outsider to lie with")
    }

    @Test
    fun `the Tea Lady is told whether their protection is on`() {
        var state = game("chef", "tealady", "mayor", "imp", "poisoner", "monk", "butler", "virgin")
        val on = assertNotNull(compute(state, "tealady", 1L))
        assertEquals(Answer.YesNoAnswer(true), on.answer, "both neighbours are good")

        state = GameActions.assignCharacter(state, 2, "spy")
        val off = assertNotNull(compute(state, "tealady", 1L))
        assertEquals(Answer.YesNoAnswer(false), off.answer, "an evil neighbour turns it off")
    }

    @Test
    fun `the Exorcist is told whether they hit the Demon`() {
        val state = game("exorcist", "imp", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
        assertEquals(
            Answer.YesNoAnswer(true),
            assertNotNull(compute(state, "exorcist", 0L, listOf(1L))).answer,
        )
        assertEquals(
            Answer.YesNoAnswer(false),
            assertNotNull(compute(state, "exorcist", 0L, listOf(2L))).answer,
        )
    }

    @Test
    fun `the Juggler is scored from the day's recorded guesses`() {
        var state = game("juggler", "imp", "poisoner", "chef", "mayor", "monk", "butler", "virgin")
            .copy(phase = Phase.DAY, cycle = 1)
        state = state.copy(
            ledger = state.ledger + LedgerEntry(
                id = 1,
                cycle = 1,
                atNight = false,
                kind = LedgerKind.STATEMENT,
                sourceId = "juggler",
                actorId = 0L,
                targetIds = listOf(1L, 3L),
                characterIds = listOf("imp", "mayor"),
            ),
            nextLedgerId = 2,
        )
        state = state.copy(phase = Phase.NIGHT, cycle = 2)
        val result = assertNotNull(compute(state, "juggler", 0L))
        assertEquals(Answer.Count(1, 0, 2), result.answer, "one of the two guesses was right")
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** Night 2 of a 12-seat game with an execution behind it and half the table dead. */
    private fun wellStockedGame(first: String): GameState {
        val roles = listOf(
            first, "imp", "poisoner", "recluse", "spy", "chef",
            "empath", "mayor", "monk", "butler", "virgin", "saint",
        )
        var state = GameActions.newGame(tb, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state).copy(phase = Phase.DAY, cycle = 1)
        state = Deaths.attempt(state, lookup, 6L, KillCause(DeathCause.EXECUTION)).state
        for (seat in listOf(7L, 8L, 9L, 10L, 11L)) {
            state = Deaths.attempt(state, lookup, seat, KillCause(DeathCause.STORYTELLER)).state
        }
        // Six dead, six alive: even the King wakes.
        return state.copy(phase = Phase.NIGHT, cycle = 2)
    }

    private fun malfunction(state: GameState, seat: Long, sourceId: String): GameState {
        val id = state.nextLedgerId
        return state.copy(
            ledger = state.ledger + LedgerEntry(
                id = id,
                cycle = state.cycle,
                atNight = true,
                kind = LedgerKind.MALFUNCTION,
                sourceId = sourceId,
                actorId = seat,
                impaired = true,
            ),
            nextLedgerId = id + 1,
        )
    }
}
