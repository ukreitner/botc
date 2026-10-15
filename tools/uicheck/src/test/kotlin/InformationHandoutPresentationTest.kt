package com.clocktower.grimoire.uicheck

import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.InfoCalc
import com.clocktower.engine.NightPlan
import com.clocktower.engine.ShowCardSpec
import com.clocktower.grimoire.ui.components.CustomCardDraft
import com.clocktower.grimoire.ui.components.CustomCardKind
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.asCard
import com.clocktower.grimoire.ui.components.asSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InformationHandoutPresentationTest {
    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().first().copy(characterIds = listOf("dreamer", "grandmother", "gambler", "chef", "imp", "poisoner"))
    private fun game(role: String) = GameActions.newGame(script, listOf("Marco", "Nova", "Marius")).let {
        GameActions.assignCharacter(GameActions.assignCharacter(GameActions.assignCharacter(it, 0, role), 1, "imp"), 2, "gambler")
    }

    @Test fun `Dreamer engine offers render and edit as a pair without losing the other character`() {
        val state = game("dreamer")
        val info = requireNotNull(InfoCalc.compute(state, data::character, "dreamer", 0, listOf(1)))
        for (offer in NightPlan.cardsFor(state, info)) {
            val shown = offer.card.asCard()
            assertTrue(shown is ShowCard.MultiTokenCard)
            assertEquals(offer.card, shown.asSpec())
            val draft = CustomCardDraft.from(shown, state)
            assertEquals(CustomCardKind.CHARACTERS, draft.kind)
            assertEquals(2, draft.characterIds.size)
            assertEquals(shown, draft.card(state))
            val changed = draft.copy(characterIds = listOf("chef", "poisoner")).card(state)
            assertEquals(ShowCard.MultiTokenCard("Nova IS ONE OF THESE CHARACTERS", listOf("chef", "poisoner")), changed)
        }
    }

    @Test fun `Grandmother renderer and custom editor preserve named grandchild and token`() {
        val state = game("grandmother")
        val info = requireNotNull(InfoCalc.compute(state, data::character, "grandmother", 0, listOf(2)))
        val spec = NightPlan.cardsFor(state, info).first { it.truthful }.card
        assertEquals(ShowCardSpec.PointCard("THIS IS YOUR GRANDCHILD", listOf("Marius"), listOf(3), "gambler"), spec)
        val shown = spec.asCard()
        assertEquals(spec, shown.asSpec())
        assertEquals(shown, CustomCardDraft.from(shown, state).card(state))
    }
}
