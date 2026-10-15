package com.clocktower.grimoire.uicheck

import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.screens.night.customOffer
import com.clocktower.grimoire.ui.screens.night.nightCardAlreadyPresented
import com.clocktower.grimoire.ui.screens.night.primaryLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCardPresentationTest {
    @Test
    fun `showing an offered card allows completion without showing it again`() {
        val demon = ShowCard.Message("THIS IS THE DEMON", "Player 6")
        assertFalse(nightCardAlreadyPresented(emptyList(), demon, presented = null))
        val shown = nightCardAlreadyPresented(emptyList(), demon, presented = demon)
        assertTrue(shown)
        assertEquals(
            "DONE — NEXT STEP",
            primaryLabel(answer = "THIS IS THE DEMON — Player 6".takeUnless { shown }.orEmpty()),
        )
    }

    @Test
    fun `custom and false information do not replace the pending choice effects`() {
        val custom = customOffer(ShowCard.Message("Your neighbour is evil")) { it }
        val shown = nightCardAlreadyPresented(emptyList(), custom.card, custom.card)
        assertTrue(shown)
        assertEquals(
            "PLAYER 4 — POISONED",
            primaryLabel(
                picked = listOf("Player 4"),
                places = listOf("Poisoned"),
                answer = custom.answerText.takeUnless { shown }.orEmpty(),
            ),
        )
        val falseNumber = ShowCard.NumberCard(0)
        assertTrue(nightCardAlreadyPresented(emptyList(), falseNumber, falseNumber))
        assertFalse(nightCardAlreadyPresented(emptyList(), ShowCard.NumberCard(1), falseNumber))
    }

    @Test
    fun `changing a pick clears presentation and a different card still needs showing`() {
        val previous = ShowCard.Message("THIS IS THE DEMON", "Player 6")
        val changed = ShowCard.Message("THIS IS THE DEMON", "Player 3")
        assertFalse(nightCardAlreadyPresented(emptyList(), changed, previous))
        assertFalse(nightCardAlreadyPresented(emptyList(), previous, presented = null))
    }

    @Test
    fun `showing one card cannot discharge a multi-card replay`() {
        val first = ShowCard.NumberCard(1)
        val second = ShowCard.Message("A second piece of information")
        assertTrue(nightCardAlreadyPresented(listOf(first), first, first))
        assertFalse(nightCardAlreadyPresented(listOf(first, second), first, first))
        assertFalse(nightCardAlreadyPresented(listOf(first, second), second, second))
        assertFalse(nightCardAlreadyPresented(listOf(second), first, first))
    }
}
