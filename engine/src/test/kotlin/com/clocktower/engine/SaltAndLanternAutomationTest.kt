package com.clocktower.engine

import com.clocktower.engine.rules.SaltAndLantern
import com.clocktower.engine.rules.SaltAndLanternAutomation
import kotlinx.serialization.json.Json
import kotlin.test.*

class SaltAndLanternAutomationTest {
    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().single { it.id == SaltAndLantern.ID }
    private val lookup: (String) -> Character? = data::character
    private val ferry = SaltAndLantern.FERRYMAN_ID
    private fun game(vararg ids: String, night: Int = 2): GameState {
        var s = Seats.newGame(script, ids.indices.map { "P${it + 1}" })
        ids.forEachIndexed { index, id -> s = Seats.assignCharacter(s, index.toLong(), id) }
        return s.copy(phase = Phase.NIGHT, cycle = night)
    }
    private fun step(s: GameState, id: String) = NightPlan.build(s, lookup).steps.first { it.abilityId == id }
    private fun act(s: GameState, id: String, vararg targets: Long): GameState =
        NightPlan.resolve(s, lookup, step(s, id).key, NightInput(playerIds = targets.toList()))
    private fun kill(s: GameState, id: Long) = Deaths.attempt(s, lookup, id, KillCause(DeathCause.STORYTELLER)).state
    private fun poison(s: GameState, id: Long) = Effects.place(s, id, EffectKind.POISONED, "st", null, Until.FOREVER).state
    private fun count(s: GameState, id: String, holder: Long, vararg targets: Long) =
        (InfoCalc.compute(s, lookup, id, holder, targets.toList())!!.answer as Answer.Count).n
    private fun mark(s: GameState, id: Long, source: String, label: String) =
        Status.live(s, lookup, id).any { Tokens.key(it.sourceCharacterId, it.label) == Tokens.key(source, label) }
    private fun nextNight(s: GameState) = Phases.advancePhase(Phases.advancePhase(s, lookup), lookup)

    @Test fun `new and previously saved scripts both get real actions and information`() {
        assertTrue(script.manualNightInstructions.isEmpty())
        val base = game("chef", "empath", "fortuneteller", "wrecker", "siren", "stowaway", night = 1)
        for (s in listOf(base, base.copy(script = script.copy(manualNightInstructions = mapOf("chef" to "old manual", "wrecker" to "old manual"))))) {
            for (id in listOf("chef", "empath", "fortuneteller", "wrecker")) {
                assertNotNull(step(s, id).action, id)
                assertFalse("manual resolution" in step(s, id).badges, id)
            }
            assertEquals(1, step(s, "chef").cards.count { it.truthful })
            assertTrue(NightPlan.givesInfoTonight(s, lookup, "chef", 0L))
        }
    }

    @Test fun `Stowaway is omitted alive or dead but included when poisoned`() {
        val s = game("chef", "siren", "stowaway", "wrecker", "empath", night = 1)
        assertEquals(1, count(s, "chef", 0))
        assertEquals(1, count(kill(s, 2), "chef", 0))
        assertEquals(0, count(poison(s, 2), "chef", 0))
        val e = game("empath", "stowaway", "siren", "chef", "wrecker")
        assertEquals(2, count(e, "empath", 0))
        assertEquals(1, count(poison(e, 1), "empath", 0))
        val c = game("cartographer", "stowaway", "siren", "chef", "empath", night = 1)
        assertEquals(1, count(c, "cartographer", 0))
        assertEquals(2, count(poison(c, 1), "cartographer", 0))
    }

    @Test fun `Wrecker redirects the information and records both original and resolved choices`() {
        var s = game("fortuneteller", "wrecker", "chef", "empath", "lighthousekeeper", "siren")
        s = Effects.addReminder(s, 2, PlacedReminder("fortuneteller", "Red herring"))
        s = act(s, "wrecker", 2, 3)
        assertTrue(mark(s, 2, "wrecker", "Wrecked"))
        assertTrue(mark(s, 3, "wrecker", "Wrecked"))
        assertEquals(Answer.YesNoAnswer(false), InfoCalc.compute(s, lookup, "fortuneteller", 0, listOf(2, 4))!!.answer)
        val after = act(s, "fortuneteller", 2, 4)
        assertEquals(listOf(3L, 4L), after.ledger.last { it.kind == LedgerKind.CHOICE }.targetIds)
        val audit = after.ledger.last { it.kind == LedgerKind.RULING }
        assertEquals(listOf(2L, 4L), audit.targetIds)
        assertEquals(listOf(3L, 4L), audit.targetIdsB)
        assertEquals(Answer.YesNoAnswer(true), InfoCalc.compute(poison(s, 1), lookup, "fortuneteller", 0, listOf(2, 4))!!.answer)
        val evilChooser = Seats.setAlignment(s, 0, Alignment.EVIL)
        assertEquals(Answer.YesNoAnswer(true), InfoCalc.compute(evilChooser, lookup, "fortuneteller", 0, listOf(2, 4))!!.answer)
        val dusk = nextNight(s)
        assertFalse(mark(dusk, 2, "wrecker", "Wrecked"))
    }

    @Test fun `Keeper protection uses the redirected target and announces only that target`() {
        var s = game("lighthousekeeper", "wrecker", "chef", "empath", "siren")
        s = act(s, "wrecker", 2, 3)
        val preview = NightPlan.previewChoice(s, lookup, step(s, "lighthousekeeper"), NightInput(playerIds = listOf(2)))
        assertEquals(listOf(3L), preview.targetIds)
        s = act(s, "lighthousekeeper", 2)
        assertFalse(Status.demonHarmBlocked(s, lookup, 2))
        assertTrue(Status.demonHarmBlocked(s, lookup, 3))
        val dawn = Briefings.at(s, lookup, BriefingSlot.DAWN).announce
        assertTrue(dawn.any { it.text == "Announce: Last night, the lantern fell on P4." })
        assertFalse(dawn.any { it.text == "Announce: Last night, the lantern fell on P3." })
        assertFalse(Status.demonHarmBlocked(Phases.advancePhase(s, lookup), lookup, 3))
    }

    @Test fun `v1_3 Wrecker validates actual Demon characters including dead and newly changed seats`() {
        val base = game("wrecker", "recluse", "siren", "chef", "imp")
        assertTrue(TargetConstraint.NOT_DEMON in (step(base, "wrecker").action as ChoosePlayers).constraints)
        for (s in listOf(base, kill(base, 2), Seats.setAlignment(base, 2, Alignment.GOOD))) {
            val invalid = act(s, "wrecker", 1, 2)
            assertFalse(invalid.seats.any { mark(invalid, it.id, "wrecker", "Wrecked") })
        }
        val legal = act(kill(base, 1), "wrecker", 0, 1)
        assertTrue(mark(legal, 0, "wrecker", "Wrecked"), "Wrecker may choose themselves")
        assertTrue(mark(legal, 1, "wrecker", "Wrecked"), "Dead Recluse is legal regardless of possible registration")
        val changed = Seats.assignCharacter(base, 1, "imp")
        val rechecked = act(changed, "wrecker", 1, 3)
        assertFalse(mark(rechecked, 1, "wrecker", "Wrecked"), "Recheck after a character change or starpass")
    }

    @Test fun `Wrecker does not force an illegal Keeper self choice`() {
        var s = game("lighthousekeeper", "wrecker", "chef", "empath", "siren")
        s = act(s, "wrecker", 0, 2)
        s = act(s, "lighthousekeeper", 2)
        assertTrue(Status.demonHarmBlocked(s, lookup, 2))
        assertFalse(Status.demonHarmBlocked(s, lookup, 0))
    }

    @Test fun `Navigator endpoints are ordered and optional use is spent only when used`() {
        val base = game("navigator", "wrecker", "stowaway", "siren", "chef", "empath")
        assertEquals(2, count(base, "navigator", 0, 0, 4))
        assertEquals(1, count(base, "navigator", 0, 2, 4), "Stowaway endpoint uses its physical position")
        val passed = NightPlan.resolve(base, lookup, step(base, "navigator").key, NightInput(none = true))
        assertFalse(Memory.isSpent(passed, "navigator", 0))
        val swapped = act(base, "wrecker", 0, 4)
        assertEquals(0, count(swapped, "navigator", 0, 0, 4))
        val used = act(swapped, "navigator", 0, 4)
        assertTrue(Memory.isSpent(used, "navigator", 0))
        assertTrue(step(nextNight(used), "navigator").gate is StepGate.Skip)
        assertTrue(Memory.isSpent(act(poison(base, 0), "navigator", 0, 4), "navigator", 0))
    }

    @Test fun `Pearl Diver uses night death history and Wrecker respects it`() {
        var s = game("pearldiver", "wrecker", "drunk", "chef", "siren")
        s = Seats.setShownCharacter(s, 2, "investigator")
        s = kill(s, 2)
        s = s.copy(phase = Phase.DAY)
        s = Execution.execute(s, lookup, 3)
        s = s.copy(phase = Phase.NIGHT)
        s = act(s, "wrecker", 2, 3)
        assertEquals(Answer.Characters(listOf("drunk")), InfoCalc.compute(s, lookup, "pearldiver", 0, listOf(2))!!.answer)
        assertEquals(listOf(2L), NightPlan.previewChoice(s, lookup, step(s, "pearldiver"), NightInput(playerIds = listOf(2))).targetIds)
        assertEquals(Answer.Characters(listOf("drunk")), InfoCalc.compute(Deaths.resurrect(s, lookup, 2), lookup, "pearldiver", 0, listOf(2))!!.answer)
        assertTrue(InfoCalc.compute(s, lookup, "pearldiver", 0, listOf(3))!!.answer is Answer.Message)
        val empty = game("pearldiver", "wrecker", "drunk", "chef", "siren")
        assertEquals(0, (step(empty, "pearldiver").action as ShowInfo).targetsNeeded)
        assertEquals(Answer.YesNoAnswer(false), InfoCalc.compute(empty, lookup, "pearldiver", 0)!!.answer)
    }

    @Test fun `Drunk believed once per game abilities remain usable but cannot affect the game`() {
        var navigator = Seats.setShownCharacter(game("drunk", "wrecker", "siren", "chef", "empath"), 0, "navigator")
        navigator = act(navigator, "navigator", 1, 3)
        assertTrue(Memory.isSpent(navigator, "navigator", 0))
        assertTrue(step(nextNight(navigator), "navigator").gate is StepGate.Skip)
        var harbour = Seats.setShownCharacter(game("drunk", "wrecker", "siren", "chef", "empath", night = 1), 0, "harbourmaster")
            .copy(phase = Phase.DAY)
        assertTrue(DayAbilities.forState(harbour, lookup).any { it.sourceId == "harbourmaster" && it.available })
        harbour = DayAbilities.resolve(harbour, lookup, "harbourmaster", 0)
        assertTrue(Memory.isSpent(harbour, "harbourmaster", 0))
        assertFalse(SaltAndLanternAutomation.closed(Phases.advancePhase(harbour, lookup)))
        assertEquals(harbour, DayAbilities.resolve(harbour, lookup, "harbourmaster", 0))
    }

    @Test fun `Smuggler replays typed false and custom cards exactly after save and resume`() {
        var s = game("smuggler", "drunk", "chef", "siren", "empath")
        val cards = listOf(ShowCardSpec.NumberCard(7), ShowCardSpec.Message("CUSTOM", "exact subtitle"),
            ShowCardSpec.PointCard("ONE OF THESE PLAYERS", listOf("P3", "P5"), listOf(3, 5)))
        cards.forEachIndexed { i, card -> s = Ledger.shown(s, 1, "chef", "shown $i", if (i == 0) false else null, card) }
        s = Ledger.shown(s, 3, NightMarkers.DEMON_INFO, "private bluffs", true, ShowCardSpec.BluffsCard(listOf("chef")))
        val json = Json { encodeDefaults = true }
        s = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        val info = InfoCalc.compute(s, lookup, "smuggler", 0, listOf(1))!!
        assertEquals(cards, info.exactCards)
        assertEquals(cards, NightPlan.cardsFor(s, info).map { it.card })
        assertEquals(Answer.YesNoAnswer(false), InfoCalc.compute(s, lookup, "smuggler", 0, listOf(3))!!.answer)
        assertEquals(Answer.YesNoAnswer(false), InfoCalc.compute(nextNight(s), lookup, "smuggler", 0, listOf(1))!!.answer)
    }

    @Test fun `healthy harbour closure is locked at declaration and lasts exactly one night`() {
        var s = game("harbourmaster", "pukka", "chef", "empath", "wrecker", night = 1).copy(phase = Phase.DAY)
        s = DayAbilities.resolve(s, lookup, "harbourmaster", 0)
        assertTrue(Memory.isSpent(s, "harbourmaster", 0))
        s = poison(s, 0)
        s = Phases.advancePhase(s, lookup)
        assertTrue(SaltAndLanternAutomation.closed(s))
        for (target in s.seats) assertTrue(kill(s, target.id).player(target.id)!!.alive)
        s = act(s, "pukka", 2)
        assertTrue(Status.isImpaired(s, lookup, 2), "Closing the harbour does not block new Pukka poison")
        s = nextNight(s)
        assertFalse(SaltAndLanternAutomation.closed(s))
        assertFalse(kill(s, 3).player(3)!!.alive)
    }

    @Test fun `impaired or previously spent Harbourmaster cannot close a later night`() {
        val base = game("harbourmaster", "siren", "chef", "empath", "wrecker", night = 1).copy(phase = Phase.DAY)
        var s = DayAbilities.resolve(poison(base, 0), lookup, "harbourmaster", 0)
        assertTrue(Memory.isSpent(s, "harbourmaster", 0))
        s = Phases.advancePhase(s, lookup)
        assertFalse(SaltAndLanternAutomation.closed(s))
        assertFalse(kill(s, 2).player(2)!!.alive)
        val marked = Effects.addReminder(base, 0, PlacedReminder("harbourmaster", "Closed"))
        assertFalse(DayAbilities.forState(marked, lookup).single { it.sourceId == "harbourmaster" }.available)
        assertEquals(marked, DayAbilities.resolve(marked, lookup, "harbourmaster", 0))
    }

    @Test fun `Albatross curses its nominator on execution even when it survives`() {
        for (forced in listOf(false, true)) {
            var s = game("albatross", "devilsadvocate", "chef", "kraken", "harbourmaster", night = 1)
            s = act(s, "devilsadvocate", 0)
            s = Phases.advancePhase(s, lookup)
            s = Execution.execute(s, lookup, 0, nominatorId = 1, outcome = if (forced) ExecutionOutcome.SURVIVED else null)
            assertTrue(s.player(0)!!.alive)
            assertTrue(mark(s, 1, "albatross", "Cursed"))
            s = Phases.advancePhase(s, lookup)
            assertFalse(s.player(1)!!.alive)
            assertEquals("albatross", s.deaths.last().killerCharacterId)
            assertTrue(s.deaths.last().atNight)
            assertEquals(1, (step(s, "kraken").action as ChoosePlayers).max, "A Minion dying at dusk is a night death")
        }
    }

    @Test fun `harbour closure prevents and consumes the Albatross curse`() {
        var s = game("albatross", "harbourmaster", "chef", "siren", "wrecker", night = 1).copy(phase = Phase.DAY)
        s = DayAbilities.resolve(s, lookup, "harbourmaster", 1)
        s = Execution.execute(s, lookup, 0, nominatorId = 2)
        s = Phases.advancePhase(s, lookup)
        assertTrue(s.player(2)!!.alive)
        assertFalse(mark(s, 2, "albatross", "Cursed"))
        assertTrue(nextNight(s).player(2)!!.alive)
        val impaired = poison(game("albatross", "siren", "chef", "empath", "wrecker", night = 1), 0).copy(phase = Phase.DAY)
        assertFalse(mark(Execution.execute(impaired, lookup, 0, nominatorId = 2), 2, "albatross", "Cursed"))
    }

    @Test fun `Siren converts once with real cards and double tapping does not kill the convert`() {
        var s = game("siren", "drunk", "chef", "empath", "wrecker")
        s = Seats.setShownCharacter(s, 1, "investigator")
        val key = step(s, "siren").key
        s = act(s, "siren", 1)
        assertTrue(s.player(1)!!.alive)
        assertTrue(s.player(1)!!.isEvil(lookup))
        assertEquals("drunk", s.player(1)!!.characterId)
        assertEquals("drunk", s.player(1)!!.shownCharacterId)
        assertTrue(mark(s, 0, "siren", "Sung"))
        val prompt = s.prompts.first { it.sourceId == "siren" && it.subjectPlayerId == 1L }
        assertTrue(ShowCardSpec.CharacterCard("YOU ARE", "drunk") in prompt.cards)
        assertTrue(ShowCardSpec.AlignmentCard(true) in prompt.cards)
        assertEquals(s, NightPlan.resolve(s, lookup, key, NightInput(playerIds = listOf(1))))
        s = act(nextNight(s), "siren", 1)
        assertFalse(s.player(1)!!.alive)
    }

    @Test fun `Siren protection closure and impairment do not consume its conversion`() {
        val base = game("siren", "stowaway", "lighthousekeeper", "harbourmaster", "wrecker")
        for (protected in listOf(act(base, "lighthousekeeper", 1), poison(base, 0),
            Phases.advancePhase(DayAbilities.resolve(base.copy(phase = Phase.DAY, cycle = 1), lookup, "harbourmaster", 3), lookup))) {
            val after = act(protected, "siren", 1)
            assertTrue(after.player(1)!!.alive)
            assertFalse(after.player(1)!!.isEvil(lookup))
            assertFalse(mark(after, 0, "siren", "Sung"))
        }
    }

    @Test fun `Kraken checks actual daytime Minion deaths and each target protection`() {
        var s = game("kraken", "wrecker", "lighthousekeeper", "chef", "empath", night = 1).copy(phase = Phase.DAY)
        s = kill(s, 1)
        s = Phases.advancePhase(s, lookup)
        assertEquals(2, (step(s, "kraken").action as ChoosePlayers).max)
        s = act(s, "lighthousekeeper", 3)
        s = act(s, "kraken", 3, 4)
        assertTrue(s.player(3)!!.alive)
        assertFalse(s.player(4)!!.alive)
        assertFalse(mark(s, 0, "kraken", "Thrash"))
        assertEquals(1, (step(nextNight(s), "kraken").action as ChoosePlayers).max)
        val survived = Execution.execute(game("kraken", "wrecker", "chef", "empath", "harbourmaster", night = 1).copy(phase = Phase.DAY),
            lookup, 1, outcome = ExecutionOutcome.SURVIVED)
        assertEquals(1, (step(Phases.advancePhase(survived, lookup), "kraken").action as ChoosePlayers).max)
    }

    @Test fun `Ferryman Wrecker exception revives self only from a legal other-dead choice`() {
        val base = game(ferry, "wrecker", "poisoner", "chef", "siren")
        for (impaired in listOf(false, true)) for (evilFerry in listOf(false, true)) {
            var s = if (evilFerry) Seats.setAlignment(base, 0, Alignment.EVIL) else base
            if (impaired) s = poison(s, 0)
            s = kill(kill(s, 2), 0)
            s = act(s, "wrecker", 0, 2)
            s = act(s, ferry, 2)
            assertEquals(!impaired && !evilFerry, s.player(0)!!.alive, "impaired=$impaired evil=$evilFerry")
            assertFalse(s.player(2)!!.alive)
        }
        var s = act(kill(kill(base, 2), 0), "wrecker", 0, 2)
        val self = NightPlan.resolve(s, lookup, step(s, ferry).key, NightInput(playerIds = listOf(0), overrideConstraints = true))
        assertFalse(self.player(0)!!.alive)
        assertTrue(step(kill(base, 0), ferry).gate is StepGate.Skip)
        s = poison(s, 1)
        assertFalse(act(s, ferry, 2).player(0)!!.alive)
    }

    @Test fun `day Ferryman choice is frozen at death and never redirected at night`() {
        var s = game(ferry, "wrecker", "chef", "empath", "siren", night = 1).copy(phase = Phase.DAY)
        s = kill(kill(s, 2), 0)
        s = kill(s, 3)
        assertEquals(listOf(2L), SaltAndLanternAutomation.ferryTargets(s, 0), "A later death is not a choice available at death")
        assertEquals(s, DayAbilities.resolve(s, lookup, ferry, 0, 3))
        s = DayAbilities.resolve(s, lookup, ferry, 0, 2)
        s = Phases.advancePhase(s, lookup)
        s = act(s, "wrecker", 0, 2)
        val row = step(s, ferry)
        assertTrue(row.action is Options)
        s = NightPlan.resolve(s, lookup, row.key, NightInput(optionId = "cross"))
        assertTrue(s.player(2)!!.alive)
        assertFalse(s.player(0)!!.alive)
    }

    @Test fun `Ferryman cannot choose someone who only died after their own night death`() {
        val base = game(ferry, "chef", "empath", "kraken", "wrecker")
        val state = kill(kill(kill(base, 1), 0), 2)
        assertEquals(listOf(1L), SaltAndLanternAutomation.ferryTargets(state, 0))
        assertFalse(act(state, ferry, 2).player(2)!!.alive)
        assertTrue(act(state, ferry, 1).player(1)!!.alive)
    }

    @Test fun `Ferryman health is sampled at death and a crossing still works during closure`() {
        var s = game(ferry, "harbourmaster", "chef", "siren", "wrecker", night = 1).copy(phase = Phase.DAY)
        s = kill(kill(s, 2), 0)
        s = DayAbilities.resolve(s, lookup, ferry, 0, 2)
        s = DayAbilities.resolve(s, lookup, "harbourmaster", 1)
        s = Phases.advancePhase(s, lookup)
        s = NightPlan.resolve(s, lookup, step(s, ferry).key, NightInput(optionId = "cross"))
        assertTrue(s.player(2)!!.alive)
        var bad = poison(game(ferry, "chef", "siren", "wrecker", "empath"), 0)
        bad = kill(kill(bad, 1), 0).copy(effects = emptyList())
        assertFalse(act(bad, ferry, 1).player(1)!!.alive, "Sobering up after death cannot enable the crossing")
    }

    @Test fun `Pukka resumes automatic poison delayed death and Keeper protection`() {
        var s = game("pukka", "chef", "lighthousekeeper", "empath", "wrecker", night = 1)
        s = act(s, "pukka", 1)
        assertTrue(Status.isImpaired(s, lookup, 1))
        val night2 = nextNight(s)
        val killed = act(night2, "pukka", 3)
        assertFalse(killed.player(1)!!.alive)
        assertFalse(Status.isImpaired(killed, lookup, 1))
        assertTrue(Status.isImpaired(killed, lookup, 3))
        val saved = act(act(night2, "lighthousekeeper", 1), "pukka", 3)
        assertTrue(saved.player(1)!!.alive)
        assertFalse(Status.isImpaired(saved, lookup, 1))
        assertTrue(Status.isImpaired(saved, lookup, 3))
    }

    @Test fun `Imp self kill still queues its automatic succession choice`() {
        val s = act(game("imp", "wrecker", "chef", "empath", "harbourmaster"), "imp", 0)
        assertFalse(s.player(0)!!.alive)
        assertTrue(s.prompts.any { it.becomesCharacterId == "imp" && 1L in it.targetIds })
    }

    @Test fun `migration upgrades embedded manual guidance without losing game history`() {
        var s = act(game("siren", "stowaway", "chef", "empath", "wrecker"), "siren", 1)
        s = s.copy(script = script.copy(manualNightInstructions = mapOf("siren" to "Resolve manually"), dayReminder = "old"))
        val migrated = s.migrated(lookup)
        assertEquals(script, migrated.script)
        assertEquals(s.players, migrated.players)
        assertEquals(s.effects, migrated.effects)
        assertEquals(s.ledger, migrated.ledger)
        assertEquals(s.prompts, migrated.prompts)
        assertEquals(s.nightStepsDone, migrated.nightStepsDone)
        assertEquals(migrated, migrated.migrated(lookup))
    }
}
