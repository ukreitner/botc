package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The token registry: parity with `characters.json`, the derived expiry lists,
 * countdown chains, mutex pairs and the grimoire centre (ARCHITECTURE §2.4).
 */
class TokensTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    private fun night(vararg characterIds: String): GameState {
        var state = GameActions.newGame(tb, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        return Phases.advancePhase(state, lookup)
    }

    // ---- parity with the official data -------------------------------------

    @Test
    fun `every token rule names a real reminder in characters json`() {
        val missing = mutableListOf<String>()
        for (rule in Tokens.all) {
            // Generic storyteller tokens are not a character's reminders.
            if (Character.normalizeId(rule.sourceId) == Tokens.STORYTELLER_SOURCE) continue
            val character = lookup(rule.sourceId)
            if (character == null) {
                missing += "${rule.sourceId} is not a character"
                continue
            }
            val labels = character.allReminders.map { it.trim().lowercase() }
            if (rule.label.trim().lowercase() !in labels) {
                missing += "${rule.sourceId}/${rule.label} — has ${character.allReminders}"
            }
        }
        assertTrue(missing.isEmpty(), "token rules with no official reminder:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `every token rule declares the official copy count`() {
        val wrong = mutableListOf<String>()
        for (rule in Tokens.all) {
            if (Character.normalizeId(rule.sourceId) == Tokens.STORYTELLER_SOURCE) continue
            val character = lookup(rule.sourceId) ?: continue
            val actual = character.allReminders.count { it.trim().equals(rule.label.trim(), true) }
            if (actual != rule.copies) {
                wrong += "${rule.sourceId}/${rule.label}: rule says ${rule.copies}, data has $actual"
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `the official label changes from WP5 are the ones in force`() {
        // FOLLOWUPS: the labels the data regeneration moved.
        assertNotNull(Tokens.rule("innkeeper", "Safe"))
        assertNull(Tokens.rule("innkeeper", "Protected"), "\"Protected\" is the old spelling")
        assertEquals(2, Tokens.rule("innkeeper", "Safe")!!.copies)
        assertEquals(2, Tokens.rule("tealady", "Cannot Die")!!.copies)
        assertNotNull(Tokens.rule("devilsadvocate", "Survives Execution"))
        assertEquals(3, Tokens.rule("lunatic", "Chosen")!!.copies)
        assertEquals(2, Tokens.rule("pukka", "Poisoned")!!.copies)
        assertEquals(3, Tokens.rule("po", "Dead")!!.copies)
        assertEquals(2, Tokens.rule("shabaloth", "Dead")!!.copies)
        assertEquals(3, Tokens.rule("vigormortis", "Has Ability")!!.copies)
        assertEquals(3, Tokens.rule("vigormortis", "Poisoned")!!.copies)
        assertEquals(2, Tokens.rule("nodashii", "Poisoned")!!.copies)
        assertEquals(1, Tokens.rule("leviathan", "Good Player Executed")!!.copies)
        assertNotNull(Tokens.rule("minstrel", "Everyone Is Drunk"))
    }

    @Test
    fun `keys are case insensitive and id normalising`() {
        assertEquals(Tokens.key("monk", "Safe"), Tokens.key("Monk", "safe "))
        assertEquals(Tokens.key("devilsadvocate", "Survives Execution"), Tokens.key("devils_advocate", "SURVIVES EXECUTION"))
        assertNotNull(Tokens.rule("MONK", "safe"))
        // Monk and Innkeeper share a label and are told apart by sourceId alone.
        assertEquals(EffectKind.SAFE_FROM_DEMON, Tokens.rule("monk", "Safe")!!.effect)
        assertEquals(EffectKind.CANT_DIE_TONIGHT, Tokens.rule("innkeeper", "Safe")!!.effect)
    }

    // ---- the derived expiry lists ------------------------------------------

    @Test
    fun `the dawn and dusk lists are derived, not hand maintained`() {
        val dawn = Tokens.expiringAtDawn.map { Tokens.key(it.sourceId, it.label) }
        val dusk = Tokens.expiringAtDusk.map { Tokens.key(it.sourceId, it.label) }

        for (k in listOf("monk" to "Safe", "innkeeper" to "Safe", "exorcist" to "Chosen",
                "undertaker" to "Died Today", "godfather" to "Died Today", "zombuul" to "Died Today",
                "juggler" to "Correct", "princess" to "Doesn't Kill", "barber" to "Haircuts Tonight",
                "poppygrower" to "Evil Wakes", "lunatic" to "Chosen", "mathematician" to "Abnormal")) {
            assertTrue(Tokens.key(k.first, k.second) in dawn, "${k.first}/${k.second} should expire at dawn")
        }
        for (k in listOf("poisoner" to "Poisoned", "sailor" to "Drunk", "innkeeper" to "Drunk",
                "butler" to "Master", "devilsadvocate" to "Survives Execution", "witch" to "Cursed",
                "cerenovus" to "Mad", "harpy" to "Mad", "goblin" to "Claimed", "goon" to "Drunk",
                "organgrinder" to "Drunk", "bonecollector" to "Has Ability", "thief" to "Negative Vote",
                "bureaucrat" to "3 Votes", "barista" to "Sober & Healthy")) {
            assertTrue(Tokens.key(k.first, k.second) in dusk, "${k.first}/${k.second} should expire at dusk")
        }
        // Never swept.
        for (k in listOf("golem" to "May Not Nominate", "banshee" to "Has Ability",
                "damsel" to "Guess Used", "leviathan" to "Good Player Executed",
                "sweetheart" to "Drunk", "snakecharmer" to "Poisoned", "widow" to "Poisoned",
                "nodashii" to "Poisoned", "puzzlemaster" to "Drunk", "fanggu" to "Once",
                "po" to "3 Attacks", "mezepheles" to "Turns Evil")) {
            val key = Tokens.key(k.first, k.second)
            assertFalse(key in dawn, "${k.first}/${k.second} must never be swept at dawn")
            assertFalse(key in dusk, "${k.first}/${k.second} must never be swept at dusk")
        }
    }

    @Test
    fun `the night one start knowing tokens are never swept`() {
        var state = night("washerwoman", "librarian", "investigator", "imp", "chef")
        state = Effects.addReminder(state, 0L, PlacedReminder("washerwoman", "Townsfolk"))
        state = Effects.addReminder(state, 1L, PlacedReminder("librarian", "Wrong"))
        state = Effects.addReminder(state, 2L, PlacedReminder("investigator", "Minion"))

        repeat(4) { state = Phases.advancePhase(state, lookup) }
        assertEquals(1, state.players.first { it.id == 0L }.reminders.size)
        assertEquals(1, state.players.first { it.id == 1L }.reminders.size)
        assertEquals(1, state.players.first { it.id == 2L }.reminders.size)
        assertEquals(Until.MANUAL, Tokens.rule("washerwoman", "Townsfolk")!!.until)
    }

    @Test
    fun `a dawn token survives the night it was placed on and is gone by day`() {
        var state = night("monk", "imp", "chef", "empath", "mayor")
        state = Effects.addReminder(state, 2L, PlacedReminder("monk", "Safe"))
        assertTrue(Status.protections(state, lookup, 2L).any { it.kind == EffectKind.SAFE_FROM_DEMON })

        state = Phases.advancePhase(state, lookup) // day 1
        assertTrue(state.players.first { it.id == 2L }.reminders.isEmpty(), "swept at dawn")
    }

    @Test
    fun `a day placed marker survives the following night, for the undertaker`() {
        var state = Phases.advancePhase(night("undertaker", "imp", "chef", "empath", "mayor"), lookup)
        state = Effects.addReminder(state, 2L, PlacedReminder("undertaker", "Died Today"))

        state = Phases.advancePhase(state, lookup) // night 2 — the Undertaker learns
        assertEquals(1, state.players.first { it.id == 2L }.reminders.size, "still there tonight")
        state = Phases.advancePhase(state, lookup) // day 2
        assertTrue(state.players.first { it.id == 2L }.reminders.isEmpty(), "gone at the next dawn")
    }

    // ---- countdowns --------------------------------------------------------

    @Test
    fun `the courtier chain counts up over three dusks then disappears`() {
        var state = night("courtier", "imp", "chef", "empath", "mayor")
        state = Effects.addReminder(state, 1L, PlacedReminder("courtier", "Drunk 1"))

        fun label() = state.players.first { it.id == 1L }.reminders.singleOrNull()?.label

        assertEquals("Drunk 1", label())
        state = Phases.advancePhase(state, lookup) // day 1 — dawn does not advance it
        assertEquals("Drunk 1", label())
        state = Phases.advancePhase(state, lookup) // night 2 — first dusk
        assertEquals("Drunk 2", label())
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup) // night 3 — second dusk
        assertEquals("Drunk 3", label())
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup) // night 4 — third dusk
        assertNull(label(), "the chain ends")
    }

    @Test
    fun `every countdown chain is declared, not hand written`() {
        for (source in listOf("summoner" to "Night", "xaan" to "Night", "leviathan" to "Day", "riot" to "Day")) {
            val first = assertNotNull(
                Tokens.rule(source.first, "${source.second} 1"),
                "${source.first} needs a countdown chain",
            )
            assertTrue(Tokens.isCountdown(first))
            assertEquals("${source.second} 2", first.countdownNext)
        }
        assertEquals("Drunk 2", Tokens.rule("courtier", "Drunk 1")!!.countdownNext)
        assertEquals("Drunk 3", Tokens.rule("courtier", "Drunk 2")!!.countdownNext)
        assertNull(Tokens.rule("courtier", "Drunk 3")!!.countdownNext, "the chain ends")
        assertTrue(Tokens.isCountdown(Tokens.rule("courtier", "Drunk 3")!!), "still a chain member")
    }

    @Test
    fun `leviathan day tokens replace each other`() {
        var state = night("imp", "chef", "empath", "mayor", "soldier")
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Day 1"))
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Day 2"))
        assertEquals(1, state.storytellerReminders.size, "one group, one token")
        assertEquals("Day 2", state.storytellerReminders.single().label)
    }

    // ---- mutex pairs reset rather than delete ------------------------------

    @Test
    fun `the flowergirl pair resets to demon not voted at dawn`() {
        var state = Phases.advancePhase(night("flowergirl", "imp", "chef", "empath", "mayor"), lookup)
        state = Effects.addReminder(state, 1L, PlacedReminder("flowergirl", "Demon Voted"))

        state = Phases.advancePhase(state, lookup) // night 2 — the Flowergirl reads it
        assertEquals("Demon Voted", state.players.first { it.id == 1L }.reminders.single().label)

        state = Phases.advancePhase(state, lookup) // day 2 — dawn resets it
        assertEquals(
            "Demon Not Voted",
            state.players.first { it.id == 1L }.reminders.single().label,
            "reset, not deleted",
        )
    }

    @Test
    fun `the town crier pair is a mutex too`() {
        assertEquals(
            Tokens.rule("towncrier", "Minion Nominated")!!.mutexGroup,
            Tokens.rule("towncrier", "Minions Not Nominated")!!.mutexGroup,
        )
        assertTrue(Tokens.rule("towncrier", "Minion Nominated")!!.mutexGroup.isNotEmpty())
    }

    @Test
    fun `a mutex pair can never coexist on one seat`() {
        var state = night("flowergirl", "imp", "chef", "empath", "mayor")
        state = Effects.place(
            state, 1L, EffectKind.MARKER, "flowergirl", 0L, Until.DAWN, "Demon Not Voted",
        ).state
        state = Effects.place(
            state, 1L, EffectKind.MARKER, "flowergirl", 0L, Until.DAWN, "Demon Voted",
        ).state
        assertEquals(1, state.effects.count { it.sourceCharacterId == "flowergirl" })
        assertEquals("Demon Voted", state.effects.single { it.sourceCharacterId == "flowergirl" }.label)
    }

    // ---- the grimoire centre -----------------------------------------------

    @Test
    fun `the minstrel token lives in the centre and never impairs the seat under it`() {
        val rule = assertNotNull(Tokens.rule("minstrel", "Everyone Is Drunk"))
        assertTrue(rule.grimoireCentre)
        assertFalse(rule.impairs, "the centre token is a fact, not an impairment")
        assertNull(rule.effect)

        var state = night("minstrel", "imp", "chef", "empath", "mayor")
        state = Effects.addCentreReminder(state, PlacedReminder("minstrel", "Everyone Is Drunk"))
        assertEquals(1, state.storytellerReminders.size)
        assertTrue(state.players.all { it.reminders.isEmpty() })
        assertTrue(
            state.seats.none { Status.isImpaired(state, lookup, it.id) },
            "the centre token impairs nobody",
        )
    }

    @Test
    fun `the grimoire centre tokens are the ones D9 names`() {
        for (k in listOf("leviathan" to "Day 1", "riot" to "Day 1", "fanggu" to "Once",
                "minstrel" to "Everyone Is Drunk", "leviathan" to "Good Player Executed")) {
            assertTrue(
                Tokens.rule(k.first, k.second)!!.grimoireCentre,
                "${k.first}/${k.second} belongs in the grimoire centre",
            )
        }
    }

    // ---- generic storyteller tokens ----------------------------------------

    @Test
    fun `the four generic storyteller tokens have the declared lifetimes`() {
        assertEquals(Until.DUSK, Tokens.rule("st", "Poisoned")!!.until)
        assertEquals(Until.DUSK, Tokens.rule("st", "Drunk")!!.until)
        assertEquals(Until.DAWN, Tokens.rule("st", "Protected")!!.until)
        assertEquals(Until.DUSK, Tokens.rule("st", "Mad")!!.until)
        assertTrue(Tokens.rule("st", "Poisoned")!!.impairs)
    }

    @Test
    fun `spent marks use the official spentLabel from the data`() {
        for (character in data.characters.filter { it.spentLabel.isNotEmpty() }) {
            val rule = Tokens.rule(character.id, character.spentLabel)
            assertNotNull(rule, "${character.id} declares spentLabel '${character.spentLabel}'")
            // Usually a plain SPENT mark. The Golem's is also a standing rule —
            // "May Not Nominate" keeps suppressing nominations after it is spent.
            assertTrue(
                rule.effect == EffectKind.SPENT || rule.effect == EffectKind.NO_NOMINATE,
                "${character.id}/${character.spentLabel} carries ${rule.effect}",
            )
            assertEquals(Until.FOREVER, rule.until, "a spend mark never expires")
        }
    }

    @Test
    fun `advanceCountdowns only touches chains due at that boundary`() {
        var state = night("courtier", "summoner", "chef", "empath", "imp")
        state = Effects.addReminder(state, 0L, PlacedReminder("courtier", "Drunk 1"))
        state = Effects.addReminder(state, 1L, PlacedReminder("summoner", "Night 1"))
        state = Effects.addReminder(state, 2L, PlacedReminder("monk", "Safe"))

        val atDawn = Tokens.advanceCountdowns(state, Until.DAWN)
        assertEquals("Drunk 1", atDawn.players.first { it.id == 0L }.reminders.single().label)
        assertEquals("Safe", atDawn.players.first { it.id == 2L }.reminders.single().label)

        val atDusk = Tokens.advanceCountdowns(state, Until.DUSK)
        assertEquals("Drunk 2", atDusk.players.first { it.id == 0L }.reminders.single().label)
        assertEquals("Night 2", atDusk.players.first { it.id == 1L }.reminders.single().label)
        assertEquals("Safe", atDusk.players.first { it.id == 2L }.reminders.single().label)
    }

    // ---- the dawn briefing is computed before the sweep --------------------

    @Test
    fun `the dawn briefing sees the grimoire before anything is swept`() {
        var state = night("monk", "imp", "chef", "empath", "mayor")
        state = Effects.place(
            state, 2L, EffectKind.SAFE_FROM_DEMON, "monk", 0L, Until.DAWN, "Safe",
        ).state

        var sawMonkToken: Boolean? = null
        var sawPhase: Phase? = null
        val probe = Phases.BriefingSource { s, _, slot ->
            if (slot == BriefingSlot.DAWN) {
                sawMonkToken = s.effects.any { it.label == "Safe" && it.sourceCharacterId == "monk" }
                sawPhase = s.phase
            }
            Briefing(slot, s.cycle)
        }

        val after = Phases.advancePhase(state, lookup, probe)
        assertEquals(true, sawMonkToken, "the Monk token must still be there when DAWN is computed")
        assertEquals(Phase.NIGHT, sawPhase, "and the briefing runs before the phase flips")
        assertTrue(after.effects.none { it.label == "Safe" }, "and only then is it swept")
        assertEquals(BriefingSlot.DAWN, after.lastDawn?.slot, "the briefing is frozen on the state")
    }

    @Test
    fun `the dusk briefing is frozen the same way`() {
        val state = Phases.advancePhase(night("monk", "imp", "chef", "empath", "mayor"), lookup)
        val after = Phases.advancePhase(state, lookup) { s, _, slot -> Briefing(slot, s.cycle) }
        assertEquals(BriefingSlot.DUSK, after.lastDusk?.slot)
        assertEquals(Phase.NIGHT, after.phase)
        assertEquals(2, after.cycle)
    }
}
