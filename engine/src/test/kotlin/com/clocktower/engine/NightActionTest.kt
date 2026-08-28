package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The resolution contract of ARCHITECTURE §2.11 (WP2): constraints are checked
 * at RESOLVE time, `perTarget` runs one target at a time, every `Attack` goes
 * through the kill funnel, and CHOICE / WOKE / MALFUNCTION are recorded — with
 * "they chose nobody" a real answer rather than an absence.
 */
class NightActionTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val lookup: (String) -> Character? = data::character

    private fun game(script: Script, vararg roles: String): GameState {
        var state = GameActions.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    private fun step(state: GameState, abilityId: String): NightStep =
        assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull { it.abilityId == abilityId },
            "no $abilityId step: ${NightPlan.build(state, lookup).steps.map { it.key.token }}",
        )

    private fun entries(state: GameState, kind: LedgerKind) =
        state.ledger.filter { it.kind == kind }

    // ==================================================================
    // What the storyteller entered
    // ==================================================================

    @Test
    fun `a choice is recorded and the step is ticked`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = step(state, "pukka")
        state = NightPlan.resolve(state, lookup, pukka.key, NightInput(playerIds = listOf(2L)))

        val choice = assertNotNull(entries(state, LedgerKind.CHOICE).lastOrNull())
        assertEquals("pukka", choice.sourceId)
        assertEquals(0L, choice.actorId)
        assertEquals(listOf(2L), choice.targetIds)
        assertTrue(pukka.key.token in state.nightStepsDone, "and the row is ticked")
    }

    @Test
    fun `they chose nobody is a real answer, not an absence`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = step(state, "pukka")
        state = NightPlan.resolve(state, lookup, pukka.key, NightInput(none = true))

        val choice = assertNotNull(entries(state, LedgerKind.CHOICE).lastOrNull())
        assertEquals(emptyList(), choice.targetIds)
        assertEquals(NightPlan.NO_CHOICE, choice.text)
        assertTrue(state.players.all { it.alive }, "and nothing happened")
    }

    @Test
    fun `a storyteller-made choice is marked as one`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = step(state, "pukka")
        state = NightPlan.resolve(
            state,
            lookup,
            pukka.key,
            NightInput(playerIds = listOf(2L), byStoryteller = true),
        )
        assertTrue(assertNotNull(entries(state, LedgerKind.CHOICE).lastOrNull()).byStoryteller)
    }

    @Test
    fun `an unknown step key changes nothing but its tick`() {
        val state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val next = NightPlan.resolve(
            state,
            lookup,
            StepKey("nosuchcharacter", 3L),
            NightInput(playerIds = listOf(2L)),
        )
        assertEquals(state.ledger, next.ledger, "no engine function throws on UI input")
        assertEquals(state.players, next.players)
        assertTrue("nosuchcharacter#3" in next.nightStepsDone)
    }

    @Test
    fun `toggleDone round-trips`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val token = step(state, "pukka").key.token
        state = NightPlan.toggleDone(state, token)
        assertTrue(token in state.nightStepsDone)
        state = NightPlan.toggleDone(state, token)
        assertFalse(token in state.nightStepsDone)
    }

    // ==================================================================
    // Constraints, at resolve time
    // ==================================================================

    @Test
    fun `a target that has become illegal is dropped at resolve time`() {
        var state = game(bmr, "zombuul", "sailor", "tealady", "fool", "gossip", "professor")
            .copy(cycle = 2)
        // The Zombuul's picker is ALIVE-only; seat 5 died before the step ran.
        state = Deaths.attempt(state, lookup, 5L, KillCause(DeathCause.STORYTELLER)).state
        val deathsBefore = state.deaths.size

        val zombuul = step(state, "zombuul")
        assertIs<ChoosePlayers>(zombuul.action)
        assertTrue(TargetConstraint.ALIVE in (zombuul.action as ChoosePlayers).constraints)
        state = NightPlan.resolve(state, lookup, zombuul.key, NightInput(playerIds = listOf(5L)))

        assertEquals(deathsBefore, state.deaths.size, "a dead seat cannot be attacked again")
        assertEquals(
            emptyList(),
            assertNotNull(entries(state, LedgerKind.CHOICE).lastOrNull()).targetIds,
            "and the illegal pick is not recorded as a choice",
        )
    }

    @Test
    fun `an attack goes through the kill funnel, so protection still applies`() {
        var state = game(bmr, "zombuul", "sailor", "tealady", "fool", "gossip", "professor")
            .copy(cycle = 2)
        state = Effects.place(
            state = state,
            target = 5L,
            kind = EffectKind.SAFE_FROM_DEMON,
            sourceCharacterId = "monk",
            sourcePlayerId = 4L,
            until = Until.DAWN,
            label = "Safe",
        ).state

        val zombuul = step(state, "zombuul")
        state = NightPlan.resolve(state, lookup, zombuul.key, NightInput(playerIds = listOf(5L)))

        assertTrue(assertNotNull(state.player(5L)).alive, "the Monk's protection held")
        assertTrue(
            entries(state, LedgerKind.RULING).any { it.actorId == 5L },
            "and the prevented death is recorded, not silently dropped",
        )
    }

    // ==================================================================
    // Effects, in the order the rules ask for
    // ==================================================================

    @Test
    fun `the new poison is placed before the previous victim dies`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val first = 2L
        val second = 4L

        state = NightPlan.resolve(
            state,
            lookup,
            step(state, "pukka").key,
            NightInput(playerIds = listOf(first)),
        )
        assertTrue(Status.isImpaired(state, lookup, first))

        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = NightPlan.resolve(
            state,
            lookup,
            step(state, "pukka").key,
            NightInput(playerIds = listOf(second)),
        )

        assertFalse(assertNotNull(state.player(first)).alive, "the old victim dies")
        val death = assertNotNull(state.deaths.lastOrNull { it.playerId == first })
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("pukka", death.killerCharacterId)
        assertEquals(0L, death.killerPlayerId)
        assertEquals(true, death.abilityImpairedAtDeath, "and dies still poisoned")
        assertTrue(
            Status.effectsOn(state, lookup, first).none { it.kind == EffectKind.POISONED },
            "then becomes healthy",
        )
        assertTrue(Status.isImpaired(state, lookup, second), "while the new victim is poisoned")
    }

    @Test
    fun `Ref Seat addresses exactly the seat the registry named`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        state = NightPlan.resolve(
            state,
            lookup,
            step(state, "pukka").key,
            NightInput(playerIds = listOf(2L)),
        )
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = NightPlan.resolve(state, lookup, step(state, "pukka").key, NightInput(none = true))

        assertFalse(assertNotNull(state.player(2L)).alive, "the seat carrying the token")
        assertTrue(
            state.seats.filter { it.id != 2L }.all { it.alive },
            "and nobody else",
        )
    }

    // ==================================================================
    // Wake and malfunction records (lead D13, §2.11 steps 5-6)
    // ==================================================================

    @Test
    fun `resolving records an own-ability wake`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        state = NightPlan.resolve(
            state,
            lookup,
            step(state, "pukka").key,
            NightInput(playerIds = listOf(2L)),
        )
        val woke = assertNotNull(entries(state, LedgerKind.WOKE).lastOrNull())
        assertEquals(0L, woke.actorId)
        assertTrue(woke.genuine, "the Pukka woke for their own ability")
    }

    @Test
    fun `a silenced Demon's wake does not count for the Chambermaid`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
            .copy(cycle = 2)
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "exorcist",
            sourcePlayerId = 1L,
            until = Until.DAWN,
            label = "Chosen",
        ).state
        state = NightPlan.resolve(state, lookup, step(state, "pukka").key, NightInput(none = true))

        val woke = assertNotNull(entries(state, LedgerKind.WOKE).lastOrNull { it.actorId == 0L })
        assertFalse(woke.genuine, "they were woken, but not for their own ability")
        assertEquals(0, NightPlan.wokeCount(state, lookup, listOf(0L)))
    }

    @Test
    fun `an impaired holder's step records a malfunction the Mathematician can count`() {
        var state = game(bmr, "pukka", "poisoner", "gossip", "chambermaid", "professor", "fool")
            .copy(cycle = 2)
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        state = NightPlan.resolve(
            state,
            lookup,
            step(state, "pukka").key,
            NightInput(playerIds = listOf(2L)),
        )
        assertTrue(
            entries(state, LedgerKind.MALFUNCTION).any { it.actorId == 0L },
            "the engine proved this one: ${state.ledger.map { it.kind to it.actorId }}",
        )
        assertEquals(1, NightPlan.malfunctionCount(state, night = 2))
        assertEquals(0, NightPlan.malfunctionCount(state, night = 2, excluding = 0L))
    }

    // ==================================================================
    // The declared action model
    // ==================================================================

    @Test
    fun `an information step is offered as a typed picker, not a free search`() {
        val state = game(tb, "imp", "poisoner", "fortuneteller", "chef", "monk", "mayor")
        val fortuneTeller = step(state, "fortuneteller")
        val action = assertIs<ShowInfo>(fortuneTeller.action)
        assertEquals("fortuneteller", action.sourceId)
        assertEquals(2, action.targetsNeeded, "the Fortune Teller points at two players")

        val chef = step(state, "chef")
        assertEquals(0, assertIs<ShowInfo>(chef.action).targetsNeeded)
        assertTrue(chef.cards.isNotEmpty(), "and a zero-target answer arrives pre-filled")
        assertTrue(chef.cards.any { it.truthful }, "with the truth first")
        assertTrue(chef.cards.any { !it.truthful }, "and the lies beside it")
    }

    @Test
    fun `a card offer carries a populated card, never a picker`() {
        val state = game(tb, "imp", "poisoner", "chef", "empath", "monk", "mayor")
        val chef = step(state, "chef")
        val truth = assertNotNull(chef.cards.firstOrNull { it.truthful })
        assertIs<ShowCardSpec.NumberCard>(truth.card)
        assertTrue(truth.label.startsWith("SHOW: "))
        val lie = assertNotNull(chef.cards.firstOrNull { !it.truthful })
        assertTrue(lie.label.startsWith("LIE"), lie.label)
    }

}
