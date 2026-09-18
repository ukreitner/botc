package com.clocktower.engine

import com.clocktower.engine.rules.SaltAndLantern
import kotlinx.serialization.json.Json
import kotlin.test.*

class WreckerFirstNightTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private fun firstNight(): GameState {
        val script = data.builtInScripts().single { it.id == SaltAndLantern.ID }
        val roles = listOf("fortuneteller", "wrecker", "chef", "empath", "cartographer", "investigator", "imp")
        var state = Seats.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { index, role -> state = Seats.assignCharacter(state, index.toLong(), role) }
        state = Effects.addReminder(state, 2, PlacedReminder("fortuneteller", "Red herring"))
        return Phases.advancePhase(state, lookup)
    }

    private fun step(state: GameState, id: String) = NightPlan.build(state, lookup).steps.single { it.abilityId == id }

    private fun wreck(state: GameState, targets: List<Long>): GameState =
        NightPlan.resolve(state, lookup, step(state, "wrecker").key, NightInput(playerIds = targets))

    private fun pairs(ids: List<Long>) = ids.flatMapIndexed { index, a -> ids.drop(index + 1).map { b -> listOf(a, b) } }

    @Test fun `Wrecker acts before first night information without changing assigned characters`() {
        val state = firstNight()
        assertEquals(1, state.cycle)
        assertEquals(Phase.NIGHT, state.phase)
        val order = NightPlan.build(state, lookup).steps.map { it.abilityId }
        assertTrue(order.indexOf(NightMarkers.MINION_INFO) < order.indexOf("wrecker"))
        assertTrue(order.indexOf(NightMarkers.DEMON_INFO) < order.indexOf("wrecker"))
        for (role in listOf("investigator", "chef", "empath", "cartographer", "fortuneteller")) {
            assertTrue(order.indexOf("wrecker") < order.indexOf(role), role)
        }
        val after = wreck(state, listOf(1, 2))
        assertEquals(state.players.map { it.characterId }, after.players.map { it.characterId })
        assertEquals(state.players.map { it.shownCharacterId }, after.players.map { it.shownCharacterId })
    }

    @Test fun `every legal first night Wrecker pair redirects Fortune Teller information exactly once`() {
        val state = firstNight()
        val allPairs = pairs(state.seats.map { it.id })
        val wreckerPairs = pairs(state.seats.filter { it.characterId != "imp" }.map { it.id })
        for (marks in wreckerPairs) {
            val wrecked = wreck(state, marks)
            for (chosen in allPairs) {
                val actual = chosen.map { if (it in marks) marks.single { other -> other != it } else it }
                // The actual Demon and the setup red herring are the only YES seats.
                val expected = Answer.YesNoAnswer(actual.any { it == 2L || it == 6L })
                val info = InfoCalc.compute(wrecked, lookup, "fortuneteller", 0, chosen)!!
                assertEquals(expected, info.answer, "Wrecked $marks, picked $chosen")
                assertEquals(InfoObligation.TRUTH, info.obligation)
                val row = step(wrecked, "fortuneteller")
                val input = NightInput(playerIds = chosen)
                assertEquals(actual, NightPlan.previewChoice(wrecked, lookup, row, input).targetIds)
                val after = NightPlan.resolve(wrecked, lookup, row.key, input)
                assertEquals(actual, after.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "fortuneteller" }.targetIds)
                assertEquals(after, NightPlan.resolve(after, lookup, row.key, input))
            }
        }
    }

    @Test fun `Wrecker leaves passive starting information and Investigator cards unchanged`() {
        val state = firstNight()
        for (marks in pairs(state.seats.filter { it.characterId != "imp" }.map { it.id })) {
            val wrecked = wreck(state, marks)
            for ((holder, role) in listOf(2L to "chef", 3L to "empath", 4L to "cartographer", 5L to "investigator")) {
                assertEquals(0, (step(wrecked, role).action as ShowInfo).targetsNeeded)
                val before = InfoCalc.compute(state, lookup, role, holder)!!
                val after = InfoCalc.compute(wrecked, lookup, role, holder)!!
                assertEquals(before, after, "$role with Wrecked $marks")
                assertEquals(NightPlan.cardsFor(state, before), NightPlan.cardsFor(wrecked, after))
            }
        }
    }

    @Test fun `first night redirect survives resume but stops with poison death evil chooser or dawn`() {
        val state = wreck(firstNight(), listOf(2, 3))
        val picked = listOf(2L, 4L)
        fun answer(s: GameState) = InfoCalc.compute(s, lookup, "fortuneteller", 0, picked)!!.answer
        val json = Json { encodeDefaults = true }
        val resumed = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
        assertEquals(Answer.YesNoAnswer(false), answer(resumed))
        val poisoned = Effects.place(state, 1, EffectKind.POISONED, "poisoner", null, Until.DUSK).state
        assertEquals(Answer.YesNoAnswer(true), answer(poisoned))
        val dead = Deaths.attempt(state, lookup, 1, KillCause(DeathCause.STORYTELLER)).state
        assertEquals(Answer.YesNoAnswer(true), answer(dead))
        assertEquals(Answer.YesNoAnswer(true), answer(Seats.setAlignment(state, 0, Alignment.EVIL)))
        assertEquals(Answer.YesNoAnswer(true), answer(Phases.advancePhase(state, lookup)))
    }
}
