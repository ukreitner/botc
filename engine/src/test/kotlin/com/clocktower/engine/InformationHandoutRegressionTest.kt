package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** User screenshot regressions: verify the actual handout, not only its instructions. */
class InformationHandoutRegressionTest {
    private val data = GameData.loadDefault()
    private val ids = listOf("dreamer", "grandmother", "lunatic", "philosopher", "flowergirl", "gambler",
        "chef", "empath", "recluse", "poisoner", "spy", "imp", "vigormortis", "vortox", "godfather")
    private fun game(vararg roles: String): GameState {
        val script = data.builtInScripts().first().copy(characterIds = ids)
        var state = GameActions.newGame(script, roles.indices.map { "Player ${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }
    private fun result(state: GameState, id: String, target: Long? = null): InfoResult =
        assertNotNull(InfoCalc.compute(state, data::character, id, 0, listOfNotNull(target)))

    private fun assertPair(answer: Answer): List<String> {
        val ids = assertIs<Answer.Characters>(answer).ids
        assertEquals(2, ids.size)
        assertTrue(data.character(ids[0])?.team in listOf(Team.TOWNSFOLK, Team.OUTSIDER), ids.toString())
        assertTrue(data.character(ids[1])?.team in listOf(Team.MINION, Team.DEMON), ids.toString())
        return ids
    }

    @Test fun `Dreamer truth and false alternatives always show a complete pair together`() {
        for (target in listOf("gambler", "imp", "spy", "recluse", "philosopher")) {
            val state = game("dreamer", target, "poisoner", "chef", "empath")
            val info = result(state, "dreamer", 1)
            (listOf(info.answer) + info.alsoTrue).forEach { assertTrue(target in assertPair(it)) }
            assertTrue(info.alternatives.isNotEmpty())
            info.alternatives.forEach { assertFalse(target in assertPair(it)) }
            NightPlan.cardsFor(state, info).forEach { offer ->
                val card = assertIs<ShowCardSpec.MultiTokenCard>(offer.card)
                assertEquals(2, card.characterIds.size)
                assertFalse(card.prefix.contains("LIE"))
                assertFalse(card.prefix.contains("POISON"))
                assertTrue(card.prefix.contains("Player 2"))
            }
        }
    }

    @Test fun `Dreamer uses character type not alignment and accepts dead targets`() {
        val state = game("dreamer", "gambler", "imp").let { s ->
            s.copy(players = s.players.map { if (it.id == 1L) it.copy(alive = false, alignment = Alignment.EVIL) else it })
        }
        assertEquals("gambler", assertPair(result(state, "dreamer", 1).answer)[0])
    }

    @Test fun `Dreamer poisoned and Vortox answers keep both tokens and false pairs exclude reality`() {
        val sober = game("dreamer", "gambler", "poisoner")
        val poisoned = GameActions.addReminder(sober, 0, PlacedReminder("poisoner", "Poisoned"))
        val impairedInfo = result(poisoned, "dreamer", 1)
        assertEquals(InfoObligation.MAY_LIE, impairedInfo.obligation)
        (listOf(impairedInfo.answer) + impairedInfo.alsoTrue + impairedInfo.alternatives).forEach(::assertPair)
        val vortox = game("dreamer", "gambler", "vortox")
        val forced = result(vortox, "dreamer", 1)
        assertEquals(InfoObligation.MUST_LIE, forced.obligation)
        forced.alternatives.forEach { assertFalse("gambler" in assertPair(it)) }
    }

    @Test fun `working Vortox overrides poisoning or drunkenness of the Dreamer`() {
        for (kind in listOf(EffectKind.POISONED, EffectKind.DRUNK)) {
            val state = Effects.place(game("dreamer", "gambler", "vortox"), 0, kind,
                "poisoner", 1, Until.DUSK, "Impaired").state
            val info = result(state, "dreamer", 1)
            assertTrue(info.abilityMalfunctions)
            assertEquals(InfoObligation.MUST_LIE, info.obligation)
            info.alternatives.forEach { assertFalse("gambler" in assertPair(it)) }
        }
    }

    @Test fun `dead or impaired Vortox no longer forces a poisoned Dreamer to lie`() {
        val base = GameActions.addReminder(game("dreamer", "gambler", "vortox"), 0,
            PlacedReminder("poisoner", "Poisoned"))
        val dead = base.copy(players = base.players.map { if (it.id == 2L) it.copy(alive = false) else it })
        val poisoned = GameActions.addReminder(base, 2, PlacedReminder("poisoner", "Poisoned"))
        val drunk = Effects.place(base, 2, EffectKind.DRUNK, "courtier", 1, Until.DUSK, "Drunk").state
        for (state in listOf(dead, poisoned, drunk)) {
            assertEquals(InfoObligation.MAY_LIE, result(state, "dreamer", 1).obligation)
        }
    }

    @Test fun `Grandmother card identifies the grandchild by name seat and character`() {
        val state = game("grandmother", "gambler", "imp")
        val card = NightPlan.cardsFor(state, result(state, "grandmother", 1)).first { it.truthful }.card
        assertEquals(ShowCardSpec.PointCard("THIS IS YOUR GRANDCHILD", listOf("Player 2"), listOf(2), "gambler"), card)
    }

    @Test fun `Lunatic believed Demon row includes fake minions as player-safe message and names`() {
        for (believed in listOf("vigormortis", "po")) {
            var state = game("lunatic", "gambler", "poisoner", "imp", "chef", "empath", "grandmother")
            state = GameActions.setShownCharacter(state, 0, believed)
            state = GameActions.addReminder(state, 1, PlacedReminder("lunatic", "Fake Minion"))
            val step = NightPlan.build(state, data::character).steps.single { it.slotId == "lunatic" }
            assertTrue(step.cards.any { it.card == ShowCardSpec.Message("THESE ARE YOUR MINIONS") })
            val card = step.cards.map { it.card }.filterIsInstance<ShowCardSpec.PointCard>().single()
            assertEquals(ShowCardSpec.PointCard("THESE ARE YOUR MINIONS", listOf("Player 2"), listOf(2)), card)
            assertFalse(card.toString().contains("FAKE"))
            assertFalse(card.toString().contains("LUNATIC"))
        }
    }

    @Test fun `Godfather communicates zero Outsiders and multiple Outsiders on one card`() {
        val none = game("godfather", "imp", "chef")
        assertEquals(ShowCardSpec.Message("NO OUTSIDERS ARE IN PLAY"),
            NightPlan.cardsFor(none, result(none, "godfather")).first { it.truthful }.card)
        val several = game("godfather", "imp", "lunatic", "recluse")
        val card = assertIs<ShowCardSpec.MultiTokenCard>(
            NightPlan.cardsFor(several, result(several, "godfather")).first { it.truthful }.card)
        assertEquals(listOf("lunatic", "recluse"), card.characterIds)
        assertEquals("THESE OUTSIDERS ARE IN PLAY", card.prefix)
    }
}
