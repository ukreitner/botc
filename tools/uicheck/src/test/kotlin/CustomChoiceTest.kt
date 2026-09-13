package com.clocktower.grimoire.uicheck

import com.clocktower.engine.GameActions
import com.clocktower.engine.Ledger
import com.clocktower.engine.Script
import com.clocktower.engine.Verdict
import com.clocktower.grimoire.ui.components.CustomCardDraft
import com.clocktower.grimoire.ui.components.CustomCardKind
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.describe
import com.clocktower.grimoire.ui.screens.night.customOffer
import com.clocktower.grimoire.ui.screens.night.primaryLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomChoiceTest {
    private val state = GameActions.newGame(Script("custom", "Custom", characterIds = emptyList()), listOf("Ana", "Ben", "Cleo", "Dan"))

    @Test
    fun `custom text survives the displayed card primary label and ledger without suggestion truncation`() {
        val text = "SHOW Ben the lantern, then tell Cleo: neither of these players is the keeper of the old lighthouse."
        val card = CustomCardDraft(text = text).card(state)!!
        val offer = customOffer(card) { it }
        assertEquals(text, offer.answerText)
        assertEquals(ShowCard.Message(text), offer.card)
        assertTrue(primaryLabel(answer = offer.answerText, holder = "Ana").contains(text.uppercase()))
        assertNull(offer.truthful)
        val saved = Ledger.shown(state, state.seats.first().id, "chef", card.describe { it }, offer.truthful)
        val entry = saved.ledger.last()
        assertEquals(text, entry.shown)
        assertEquals(Verdict.ST_CHOICE, entry.verdict)
        assertFalse(entry.impaired)
    }

    @Test
    fun `any player combination and character token can replace a suggestion without changing the grimoire`() {
        val original = ShowCard.PointCard("ONE OF THESE PLAYERS IS THE", listOf("Ana", "Ben"), listOf(1, 2), "chef")
        val draft = CustomCardDraft.from(original, state).copy(
            playerIds = listOf(state.seats[3].id, state.seats[2].id),
            characterIds = listOf("lighthousekeeper"),
        )
        val replacement = draft.card(state) as ShowCard.PointCard
        assertEquals(listOf("Dan", "Cleo"), replacement.playerNames)
        assertEquals(listOf(4, 3), replacement.seatNumbers)
        assertEquals("lighthousekeeper", replacement.characterId)
        val saved = Ledger.shown(state, state.seats.first().id, "washerwoman", replacement.describe { it }, null)
        assertEquals(state.players, saved.players)
        assertEquals(state.effects, saved.effects)
        assertTrue(saved.ledger.last().shown.contains("lighthousekeeper — Dan, Cleo"))
    }

    @Test
    fun `numbers and character sets are not limited to the offered alternatives`() {
        assertEquals(ShowCard.NumberCard(42), CustomCardDraft(CustomCardKind.NUMBER, number = "42").card(state))
        val draft = CustomCardDraft.from(ShowCard.CharacterCard("THIS CHARACTER", "chef"), state)
            .copy(characterIds = listOf("saltkeeper", "lamplighter"))
        assertEquals(ShowCard.MultiTokenCard("THIS CHARACTER", listOf("saltkeeper", "lamplighter")), draft.card(state))
        assertEquals(ShowCard.AlignmentCard(true, "YOU ARE EVIL"),
            CustomCardDraft(CustomCardKind.ALIGNMENT, text = "YOU ARE EVIL", evil = true).card(state))
    }

    @Test
    fun `custom alignment records both the displayed alignment and arbitrary caption`() {
        for ((evil, alignment) in listOf(true to "EVIL", false to "GOOD", null to "NEITHER")) {
            val caption = "You now serve the sea"
            val card = CustomCardDraft(CustomCardKind.ALIGNMENT, text = caption, evil = evil).card(state)!!
            val offer = customOffer(card) { it }
            assertEquals("$alignment — $caption", offer.answerText)
            val saved = Ledger.shown(state, state.seats.first().id, "smuggler", card.describe { it }, offer.truthful)
            assertEquals("$alignment — $caption", saved.ledger.last().shown)
            assertEquals(alignment, ShowCard.AlignmentCard(evil).describe { it })
            assertEquals(alignment, ShowCard.AlignmentCard(evil, alignment).describe { it })
            assertEquals("YOU ARE $alignment", ShowCard.AlignmentCard(evil, "YOU ARE $alignment").describe { it })
        }
    }

    @Test
    fun `empty or invalid custom answers cannot be shown`() {
        assertNull(CustomCardDraft(text = " \n ").card(state))
        assertNull(CustomCardDraft(CustomCardKind.NUMBER, number = "many").card(state))
        assertNull(CustomCardDraft(CustomCardKind.PLAYERS).card(state))
        assertNull(CustomCardDraft(CustomCardKind.CHARACTERS).card(state))
        assertNull(CustomCardDraft(CustomCardKind.PLAYERS, playerIds = listOf(-1L)).card(state))
    }
}
