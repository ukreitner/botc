package com.clocktower.engine

import com.clocktower.engine.rules.SaltAndLantern
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaltAndLanternTest {
    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().single { it.id == SaltAndLantern.ID }
    private val lookup: (String) -> Character? = data::character

    private fun game(vararg ids: String, night: Int = 1): GameState {
        var state = Seats.newGame(script, ids.indices.map { "P${it + 1}" })
        ids.forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }
        return state.copy(phase = Phase.NIGHT, cycle = night)
    }

    @Test
    fun `bundled selectable script preserves the whole supplied roster and original text`() {
        val source = ScriptParser.parse(BotcResources.read("/botc/data/salt-and-lantern.json"))
        assertEquals("Salt & Lantern", script.name)
        assertEquals("Claude", script.author)
        assertTrue(script.isBuiltIn)
        assertEquals(25, script.characterIds.size)
        assertEquals(12, script.customCharacters.size)
        assertEquals(source.characterIds.map { if (it == "ferryman") SaltAndLantern.FERRYMAN_ID else it }, script.characterIds)
        assertTrue(data.unknownIds(script).isEmpty())
        assertEquals(mapOf(Team.TOWNSFOLK to 13, Team.OUTSIDER to 4, Team.MINION to 4, Team.DEMON to 4),
            data.resolve(script).groupingBy { it.team }.eachCount())
        source.customCharacters.forEach { original ->
            val id = if (original.id == "ferryman") SaltAndLantern.FERRYMAN_ID else original.id
            val actual = assertNotNull(lookup(id))
            assertEquals(original.copy(id = id, spentLabel = actual.spentLabel), actual, original.id)
            assertTrue(actual.flavor.isNotBlank())
        }
        assertEquals(37.5, lookup("cartographer")!!.firstNight)
        assertEquals(24.3, lookup("siren")!!.otherNight)
        assertEquals(24.6, lookup("kraken")!!.otherNight)
        assertEquals(listOf("Wrecked", "Wrecked"), lookup("wrecker")!!.reminders)
        assertEquals(listOf("Dead", "Dead", "Thrash"), lookup("kraken")!!.reminders)
        assertEquals(Team.FABLED, lookup("ferryman")!!.team)
        assertEquals(Team.TOWNSFOLK, lookup(SaltAndLantern.FERRYMAN_ID)!!.team)
        assertEquals(8, source.jinxes.size)
        assertEquals(11, data.activeJinxes(script.characterIds, script).count {
            it in script.jinxes
        })
    }

    @Test
    fun `script and game save round trips preserve manual guidance fractional order and jinxes`() {
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString(Script.serializer(), json.encodeToString(Script.serializer(), script))
        assertEquals(script, restored)
        val state = game("siren", "wrecker", "chef", "stowaway", "cartographer")
        val restoredGame = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
        assertEquals(script, restoredGame.script)
        assertEquals(NightPlan.build(state, lookup), NightPlan.build(restoredGame, lookup))
    }

    @Test
    fun `actual night rows follow the written night sheet on both nights`() {
        val ids = script.characterIds.toTypedArray()
        for (night in 1..2) {
            val state = game(*ids, night = night)
            val expected = data.nightOrder(script, night == 1).filter { it !in NightMarkers.all }
            val actual = NightPlan.build(state, lookup).steps
                .filter { it.slotId !in NightMarkers.all }.map { it.slotId }
            assertEquals(expected, actual)
            assertEquals("smuggler", actual.last())
        }
    }

    @Test
    fun `Stowaway and Wrecker cannot get unadjusted official information suggestions`() {
        val state = game("chef", "empath", "fortuneteller", "stowaway", "wrecker", "siren")
        for ((holder, id) in listOf("chef", "empath", "fortuneteller").withIndex()) {
            assertNull(InfoCalc.compute(state, lookup, id, holder.toLong(), listOf(3L, 5L)))
            val row = NightPlan.build(state, lookup).steps.first { it.abilityId == id }
            assertEquals("", row.infoId)
            assertTrue(row.cards.isEmpty())
            assertNull(row.action)
            assertTrue("Resolve manually" in row.banner)
            assertFalse(NightPlan.givesInfoTonight(state, lookup, id, holder.toLong()))
        }
        val official = state.copy(script = data.builtInScripts().first { it.id == "tb" })
        assertNotNull(InfoCalc.compute(official, lookup, "chef", 0L))
    }

    @Test
    fun `manual Pukka resolution does not poison or kill a target or a previous victim`() {
        var state = game("pukka", "chef", "lighthousekeeper", "harbourmaster", "wrecker", night = 2)
        state = Effects.place(state, 1L, EffectKind.POISONED, "pukka", 0L, Until.MANUAL, "Poisoned").state
        val row = NightPlan.build(state, lookup).steps.first { it.abilityId == "pukka" }
        assertTrue(row.deferredDeaths.isEmpty())
        val after = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(2L)))
        assertEquals(state.players, after.players)
        assertEquals(state.deaths, after.deaths)
        assertEquals(state.effects, after.effects)
        assertTrue(row.key.token in after.nightStepsDone)
    }

    @Test
    fun `Ferryman remains available after death and Navigator stops after its spend marker`() {
        var state = game(SaltAndLantern.FERRYMAN_ID, "navigator", "siren", "wrecker", "chef", night = 2)
        state = state.copy(players = state.players.map { if (it.id == 0L) it.copy(alive = false) else it })
        val ferryman = NightPlan.build(state, lookup).steps.first { it.abilityId == SaltAndLantern.FERRYMAN_ID }
        assertTrue(ferryman.gate is StepGate.Conditional)
        assertTrue(ferryman.required)
        state = Effects.addReminder(state, 1L, PlacedReminder("navigator", "No ability"))
        val navigator = NightPlan.build(state, lookup).steps.first { it.abilityId == "navigator" }
        assertTrue(navigator.gate is StepGate.Skip)
    }

    @Test
    fun `Ferryman resurrection retains spent Navigator Slayer and Harbourmaster abilities`() {
        assertFalse(script.resurrectionRestoresAbilities)
        for ((id, label) in listOf("navigator" to "No ability", "slayer" to "No Ability", "harbourmaster" to "Closed")) {
            for (handPlaced in listOf(true, false)) {
                var state = game(id, SaltAndLantern.FERRYMAN_ID, "imp", "chef", "smuggler", night = 2)
                // Seat controls use addReminder; automatic abilities use stored effects.
                // Hand-placed reminders are projected on query, never stored in state.effects.
                state = if (handPlaced) Effects.addReminder(state, 0L, PlacedReminder(id, label))
                    else Effects.place(state, 0L, EffectKind.SPENT, id, 0L, Until.FOREVER, label).state
                assertTrue(Status.live(state, lookup, 0L, EffectKind.SPENT).isNotEmpty(), "$id before death")
                state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.DEMON_KILL, "imp")).state
                assertFalse(state.player(0L)!!.alive)
                val after = Deaths.resurrect(state, lookup, 0L)
                assertTrue(after.player(0L)!!.alive)
                assertTrue(Status.live(after, lookup, 0L, EffectKind.SPENT).isNotEmpty(), "$id after resurrection")
                if (handPlaced) {
                    assertTrue(after.player(0L)!!.reminders.any { Tokens.key(it) == Tokens.key(id, label) }, id)
                } else {
                    assertTrue(after.effects.any { it.targetId == 0L && it.kind == EffectKind.SPENT }, id)
                }
                if (id == "navigator") {
                    assertTrue(NightPlan.build(after, lookup).steps.first { it.abilityId == id }.gate is StepGate.Skip)
                }
            }
        }
    }

    @Test
    fun `resurrected Cartographer first-night rerun occurs before Smuggler`() {
        var state = game("cartographer", SaltAndLantern.FERRYMAN_ID, "imp", "empath", "smuggler", night = 2)
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.DEMON_KILL, "imp")).state
        state = Deaths.resurrect(state, lookup, 0L)
        val rows = NightPlan.build(state, lookup).steps
        val cartographer = rows.indexOfFirst { it.abilityId == "cartographer" && it.key.variant == StepVariant.FIRST }
        val smuggler = rows.indexOfFirst { it.abilityId == "smuggler" }
        assertTrue(cartographer >= 0)
        assertTrue(cartographer < smuggler, "the Smuggler must be able to replay the returned Cartographer's information")
    }

    @Test
    fun `homebrew duties appear in night markers and phase briefings`() {
        val state = game("siren", "lighthousekeeper", "albatross", SaltAndLantern.FERRYMAN_ID, "harbourmaster", night = 2)
        val plan = NightPlan.build(state, lookup)
        assertTrue("cursed nominator" in plan.steps.first { it.slotId == NightMarkers.DUSK }.detail)
        assertTrue("lantern fell" in plan.steps.first { it.slotId == NightMarkers.DAWN }.detail)
        assertTrue(Briefings.at(state, lookup, BriefingSlot.DAY_START).items.any { "public closure" in it.text })
        assertTrue(Briefings.at(state, lookup, BriefingSlot.DAWN).items.any { "lantern fell" in it.text })
    }
}
