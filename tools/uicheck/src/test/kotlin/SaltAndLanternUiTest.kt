package com.clocktower.grimoire.uicheck

import com.clocktower.engine.*
import com.clocktower.engine.rules.SaltAndLantern
import com.clocktower.grimoire.ui.components.ShowCard
import com.clocktower.grimoire.ui.components.asCard
import com.clocktower.grimoire.ui.components.asSpec
import com.clocktower.grimoire.ui.components.describe
import com.clocktower.grimoire.ui.screens.night.*
import org.junit.Assert.*
import org.junit.Test

class SaltAndLanternUiTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> com.clocktower.engine.Character? = data::character
    private fun game(vararg ids: String): GameState {
        val script = data.builtInScripts().single { it.id == SaltAndLantern.ID }
        var state = Seats.newGame(script, ids.indices.map { "P${it + 1}" })
        ids.forEachIndexed { i, id -> state = Seats.assignCharacter(state, i.toLong(), id) }
        return state.copy(phase = Phase.NIGHT, cycle = 2)
    }
    private fun step(state: GameState, id: String) = NightPlan.build(state, lookup).steps.first { it.abilityId == id }

    @Test fun `Smuggler replays every rendered card without losing custom captions or seat order`() {
        val cards = listOf(
            ShowCard.Message("A custom truth", "or a chosen lie"), ShowCard.NumberCard(42),
            ShowCard.AlignmentCard(null, "Neither team"), ShowCard.AlignmentCard(true, "Serve the sea"),
            ShowCard.CharacterCard("THIS CHARACTER", "drunk"),
            ShowCard.PointCard("ONE OF THESE", listOf("P4", "P2"), listOf(4, 2), "chef"),
            ShowCard.MultiTokenCard("THESE CHARACTERS", listOf("empath", "chef")),
            ShowCard.BluffsCard(listOf("chef", "empath")), ShowCard.SheetCard(listOf("drunk", "chef")),
        )
        var state = game("smuggler", "drunk", "siren", "chef", "empath")
        for (card in cards) state = Ledger.shown(state, 1, "chef", card.describe { it }, false, card.asSpec())
        val info = InfoCalc.compute(state, lookup, "smuggler", 0, listOf(1))!!
        assertEquals(cards, NightPlan.cardsFor(state, info).map { it.card.asCard() })
    }

    @Test fun `Wrecker picker blocks both living and dead actual Demons and permits other dead seats`() {
        var state = game("wrecker", "siren", "imp", "recluse", "chef")
        state = Deaths.attempt(state, lookup, 2, KillCause(DeathCause.STORYTELLER)).state
        state = Deaths.attempt(state, lookup, 3, KillCause(DeathCause.STORYTELLER)).state
        val row = step(state, "wrecker")
        val options = seatOptions(state, lookup, row).associateBy { it.id }
        val constraints = constraintsOf(row.action)
        assertNotNull(blockedBecause(options.getValue(1), constraints))
        assertNotNull(blockedBecause(options.getValue(2), constraints))
        for (id in listOf(0L, 3L, 4L)) assertNull(blockedBecause(options.getValue(id), constraints))
    }

    @Test fun `Pearl Diver picker remembers a night death after resurrection but excludes an execution`() {
        var state = game("pearldiver", "chef", "empath", "siren", "wrecker")
        state = Deaths.attempt(state, lookup, 1, KillCause(DeathCause.STORYTELLER)).state
        state = Deaths.resurrect(state, lookup, 1)
        state = Execution.execute(state.copy(phase = Phase.DAY), lookup, 2).copy(phase = Phase.NIGHT)
        val row = step(state, "pearldiver")
        val options = seatOptions(state, lookup, row).associateBy { it.id }
        assertNull(blockedBecause(options.getValue(1), constraintsOf(row.action)))
        assertNotNull(blockedBecause(options.getValue(2), constraintsOf(row.action)))
    }

    @Test fun `Siren conversion and Navigator spending use the same safety control as attacks`() {
        val state = game("siren", "drunk", "navigator", "chef", "wrecker")
        val conversion = NightPlan.previewChoice(state, lookup, step(state, "siren"), NightInput(playerIds = listOf(1)))
        assertTrue(conversion.effects.any { it is NightEffect.SetAlignment })
        assertFalse(conversion.effects.any { it is NightEffect.Attack })
        assertTrue(isDestructive(conversion.effects))
        val spending = NightPlan.previewChoice(state, lookup, step(state, "navigator"), NightInput(playerIds = listOf(0, 3)))
        assertTrue(isDestructive(spending.effects))
        assertFalse(isDestructive(NightPlan.previewChoice(state, lookup, step(state, "navigator"), NightInput(none = true)).effects))
    }
}
