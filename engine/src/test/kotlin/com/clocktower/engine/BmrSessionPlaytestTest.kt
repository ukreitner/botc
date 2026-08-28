package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The user's own Bad Moon Rising session, as a fixture (WP12).
 *
 * `docs/audit/ux/friction-log.md` walks one real 12-player game — Setup through
 * Day 3 — and every one of the five complaints the rebuild exists to answer
 * happens in it:
 *
 *  1. the **Pukka** was offered a kill on the night it should only poison;
 *  2. the **Devil's Advocate** token gave no way to honour "different to last
 *     night" once it had been swept;
 *  3. a **Gossip** statement had to be written on paper;
 *  4. the **Professor**'s resurrection announced nothing at dawn and re-ran no
 *     first night;
 *  5. the **Lunatic** had no bluffs, no fake Minions and no Demon behaviour.
 *
 * The seating, the bag and the whole sequence of events are pinned here now so
 * the packages that fix those five things have a fixture to be measured against.
 *
 * Tests are in two groups.
 *  * **Live** — what today's engine already gets right about this game. These
 *    must never regress; they are the reason the fixture was committed early.
 *  * **The five complaints** — one test each. Every one of them was `@Ignore`d
 *    in WP12 pass 1, naming the package that would deliver it; all five ran
 *    green in WP12 pass 2 once WP1/WP2/WP3/WP4/WP6/WP7-BMR and the Wave 7
 *    schema pass had landed, and the `@Ignore` lines are gone. Nothing in this
 *    file may be skipped again.
 */
class BmrSessionPlaytestTest {

    private val data = GameData.loadDefault()
    private val bmr = data.builtInScripts().first { it.id == "bmr" }

    private data class Seat(
        val name: String,
        val characterId: String,
        /** What the player was shown, when that differs from the truth. */
        val shownAs: String = characterId,
    )

    /**
     * The reported table, in seat order. 8 Townsfolk / 1 Outsider / 2 Minions /
     * 1 Demon — legal for 12 because the Godfather took its `[-1 Outsider]`.
     */
    private val seats = listOf(
        Seat("Ana", "devilsadvocate"),
        Seat("Ben", "sailor"),
        Seat("Cleo", "chambermaid"),
        Seat("Dev", "gossip"),
        Seat("Erin", "grandmother"),
        Seat("Gita", "professor"),
        Seat("Iris", "tealady"),
        Seat("Hal", "exorcist"),
        Seat("Finn", "fool"),
        Seat("Jonas", "lunatic", shownAs = "po"),
        Seat("Kai", "pukka"),
        Seat("Lena", "godfather"),
    )

    private fun bag(): List<Character> =
        seats.map { assertNotNull(data.character(it.characterId), it.characterId) }

    /** The grimoire exactly as the storyteller left it before night 1. */
    private fun setUpSession(): GameState {
        var state = GameActions.newGame(bmr, seats.map { it.name })
        seats.forEachIndexed { index, seat ->
            state = GameActions.assignCharacter(state, index.toLong(), seat.characterId)
            if (seat.shownAs != seat.characterId) {
                state = GameActions.setShownCharacter(state, index.toLong(), seat.shownAs)
            }
        }
        // The Grandmother's grandchild is the Fool — the collision that makes
        // night 3 of this game worth pinning.
        state = GameActions.addReminder(state, id(state, "Finn"), PlacedReminder("grandmother", "Grandchild"))
        state = GameActions.setBluffs(state, listOf("courtier", "minstrel", "moonchild"))
        return state
    }

    private fun id(state: GameState, name: String): Long =
        assertNotNull(state.players.find { it.name == name }, name).id

    private fun deadNames(state: GameState): Set<String> =
        state.players.filterNot { it.alive }.map { it.name }.toSet()

    /** Records a nomination the way the day screen does, and returns the outcome. */
    private fun nominate(
        state: GameState,
        nominator: String,
        nominee: String,
        votes: Int,
    ): GameState {
        val outcome = Voting.outcome(votes, state.executionThreshold, GameActions.highestVotesToday(state))
        return GameActions.recordNomination(
            state,
            Nomination(
                day = state.cycle,
                nominatorId = id(state, nominator),
                nomineeId = id(state, nominee),
                votes = votes,
                result = outcome,
            ),
        )
    }

    // ==================================================================
    // Live — what today's engine already gets right about this game
    // ==================================================================

    @Test
    fun `the reported bag is legal for twelve with the Godfather's minus one Outsider`() {
        val issues = GameActions.validateBag(bag(), 12)
        assertTrue(issues.isEmpty(), "the friction-log bag must be legal: $issues")
        assertEquals(8, bag().count { it.team == Team.TOWNSFOLK })
        assertEquals(1, bag().count { it.team == Team.OUTSIDER })
        assertEquals(2, bag().count { it.team == Team.MINION })
        assertEquals(1, bag().count { it.team == Team.DEMON })
        // Base 12 is 7/2/2/1; the Godfather's choice makes 1 or 3 Outsiders legal,
        // never the unmodified 2.
        val unmodified = bag().filterNot { it.id == "fool" } +
            assertNotNull(data.character("barber"))
        assertTrue(
            GameActions.validateBag(unmodified, 12).any { "Outsider" in it },
            "2 Outsiders must still be rejected under the Godfather",
        )
    }

    @Test
    fun `the Lunatic is seated as the Po and keeps a wake row of their own`() {
        val state = setUpSession()
        val jonas = assertNotNull(state.player(id(state, "Jonas")))
        assertEquals("lunatic", jonas.characterId)
        assertEquals("po", jonas.characterShownToPlayerId, "the Lunatic believes they are the Po")
        assertFalse(jonas.isEvil(data::character), "the Lunatic is a good Outsider")

        val night1 = NightPlan.build(state, data::character).steps.map { it.slotId }
        assertTrue("lunatic" in night1, "the Lunatic has a wake row of their own: $night1")
        // The real Demon still wakes; the two rows are independent.
        assertTrue(NightMarkers.DEMON_INFO in night1)
    }

    @Test
    fun `night one wakes every actor of the reported game in official order`() {
        val state = GameActions.advancePhase(setUpSession())
        val ids = NightPlan.build(state, data::character).steps.map { it.slotId }

        assertEquals(
            listOf(
                NightMarkers.DUSK,
                NightMarkers.MINION_INFO,
                "lunatic",
                NightMarkers.DEMON_INFO,
                "sailor",
                "godfather",
                "devilsadvocate",
                "pukka",
                "grandmother",
                "chambermaid",
                NightMarkers.DAWN,
            ),
            ids,
            "night 1 of the reported game, in official nightsheet order",
        )
        // The Lunatic is woken between the Minion and Demon info steps: they are
        // handed the illusion before the real Demon is told who they are.
        assertTrue(ids.indexOf(NightMarkers.MINION_INFO) < ids.indexOf("lunatic"))
        assertTrue(ids.indexOf("lunatic") < ids.indexOf(NightMarkers.DEMON_INFO))
        // The Tea Lady, the Fool, the Gossip, the Professor and the Exorcist have
        // no first-night action at all.
        for (silent in listOf("tealady", "fool", "gossip", "professor", "exorcist")) {
            assertFalse(silent in ids, "$silent must not wake on night 1: $ids")
        }
    }

    @Test
    fun `other nights wake the whole cast in the official nightsheet order`() {
        var state = GameActions.advancePhase(setUpSession())
        state = GameActions.advancePhase(state) // day 1
        state = GameActions.advancePhase(state) // night 2
        val ids = NightPlan.build(state, data::character).steps.map { it.slotId }

        for (expected in listOf(
            "sailor", "devilsadvocate", "lunatic", "exorcist",
            "pukka", "godfather", "gossip", "professor", "grandmother", "chambermaid",
        )) {
            assertTrue(expected in ids, "night 2 is missing $expected: $ids")
        }
        // The order the official nightsheet fixes, and the part of it this game
        // depends on: the Exorcist resolves before the Demon, and the Professor's
        // resurrection happens after the Demon has killed.
        assertTrue(ids.indexOf("devilsadvocate") < ids.indexOf("exorcist"), ids.toString())
        assertTrue(ids.indexOf("exorcist") < ids.indexOf("pukka"), ids.toString())
        assertTrue(ids.indexOf("pukka") < ids.indexOf("godfather"), ids.toString())
        assertTrue(ids.indexOf("godfather") < ids.indexOf("gossip"), ids.toString())
        assertTrue(ids.indexOf("gossip") < ids.indexOf("professor"), ids.toString())
        assertTrue(ids.indexOf("professor") < ids.indexOf("grandmother"), ids.toString())
        assertTrue(ids.indexOf("grandmother") < ids.indexOf("chambermaid"), ids.toString())
    }

    @Test
    fun `the session replays through the engine to the reported day three state`() {
        var state = setUpSession()

        // ---- Night 1 -------------------------------------------------
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(1, state.cycle)
        // The Pukka poisons the Sailor. TODAY the storyteller places the token by
        // hand; the ignored Pukka test below is what makes the engine do it.
        state = GameActions.addReminder(state, id(state, "Ben"), PlacedReminder("pukka", "Poisoned"))
        // The Devil's Advocate protects the Godfather.
        state = GameActions.placeExclusiveReminder(
            state,
            id(state, "Lena"),
            PlacedReminder("devilsadvocate", "Survives Execution"),
        )
        assertTrue(
            StatusEffects.isImpaired(state, data::character, assertNotNull(state.player(id(state, "Ben")))),
            "a Pukka-poisoned Sailor is impaired",
        )

        // ---- Day 1: the Grandmother is executed ----------------------
        state = GameActions.advancePhase(state)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(6, state.executionThreshold, "12 alive")
        state = nominate(state, nominator = "Lena", nominee = "Erin", votes = 7)
        assertEquals(id(state, "Erin"), GameActions.aboutToDie(state))
        state = GameActions.kill(state, id(state, "Erin"), DeathCause.EXECUTION)
        assertEquals(setOf("Erin"), deadNames(state))

        // ---- Night 2 -------------------------------------------------
        state = GameActions.advancePhase(state)
        assertEquals(2, state.cycle)
        // The Devil's Advocate protects Lena a SECOND night running — the move
        // the rules forbid, and which today's grimoire cannot even notice.
        state = GameActions.placeExclusiveReminder(
            state,
            id(state, "Lena"),
            PlacedReminder("devilsadvocate", "Survives Execution"),
        )
        // The Lunatic, believing they are the Po, "kills" three players.
        for (victim in listOf("Cleo", "Iris", "Hal")) {
            state = GameActions.addReminder(state, id(state, victim), PlacedReminder("lunatic", "Chosen"))
        }
        // The Pukka poisons the Gossip; the Sailor, poisoned last night, dies.
        state = GameActions.addReminder(state, id(state, "Dev"), PlacedReminder("pukka", "Poisoned"))
        state = GameActions.kill(state, id(state, "Ben"), DeathCause.DEMON_KILL)
        state = state.updatePlayer(id(state, "Ben")) { player ->
            player.copy(reminders = player.reminders.filterNot { it.sourceId == "pukka" })
        }
        // The Professor resurrects the Grandmother and spends the ability.
        state = GameActions.resurrect(state, id(state, "Erin"))
        state = GameActions.addReminder(state, id(state, "Gita"), PlacedReminder("professor", "No Ability"))
        assertTrue(assertNotNull(state.player(id(state, "Erin"))).alive, "Erin is alive again")
        assertTrue(
            state.deaths.any { it.playerId == id(state, "Erin") && it.resurrected },
            "the execution stays in the log, marked resurrected",
        )
        assertEquals(setOf("Ben"), deadNames(state))

        // ---- Day 2: the Gossip speaks, the Lunatic is executed --------
        state = GameActions.advancePhase(state)
        assertEquals(11, state.alivePlayers.size)
        assertEquals(6, state.executionThreshold)
        state = nominate(state, nominator = "Ana", nominee = "Jonas", votes = 6)
        assertEquals(id(state, "Jonas"), GameActions.aboutToDie(state))
        // Executing the only Outsider is what arms the Godfather, and the engine
        // says so before the button is pressed.
        assertTrue(
            StatusEffects.deathNotes(state, data::character, id(state, "Jonas"))
                .any { "Godfather" in it },
            "the Godfather trigger is surfaced on the Outsider's death: " +
                StatusEffects.deathNotes(state, data::character, id(state, "Jonas")),
        )
        state = GameActions.kill(state, id(state, "Jonas"), DeathCause.EXECUTION)
        assertEquals(setOf("Ben", "Jonas"), deadNames(state))
        val jonasDeath = assertNotNull(state.deaths.lastOrNull { it.playerId == id(state, "Jonas") })
        assertEquals(DeathCause.EXECUTION, jonasDeath.cause)
        assertFalse(jonasDeath.atNight, "an Outsider that dies BY DAY is the one that arms the Godfather")

        // ---- Night 3 -------------------------------------------------
        state = GameActions.advancePhase(state)
        assertEquals(3, state.cycle)
        // The Exorcist chooses the Pukka.
        state = GameActions.placeExclusiveReminder(
            state,
            id(state, "Kai"),
            PlacedReminder("exorcist", "Chosen"),
        )
        // The Godfather shoots the Fool, who is also the Grandmother's grandchild.
        val foolNotes = StatusEffects.deathNotes(state, data::character, id(state, "Finn"))
        assertTrue(foolNotes.any { "Fool" in it }, "the Fool's first-death save is surfaced: $foolNotes")
        assertTrue(
            foolNotes.any { "Grandmother" in it },
            "the grandchild link is surfaced at the same moment: $foolNotes",
        )
        state = GameActions.addReminder(state, id(state, "Finn"), PlacedReminder("fool", "No Ability"))
        assertTrue(assertNotNull(state.player(id(state, "Finn"))).alive, "the Fool's first death does not happen")
        assertTrue(assertNotNull(state.player(id(state, "Erin"))).alive, "no Demon killed the grandchild")

        // ---- Day 3 ---------------------------------------------------
        state = GameActions.advancePhase(state)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(3, state.cycle)
        assertEquals(setOf("Ben", "Jonas"), deadNames(state))
        assertEquals(null, WinCheck.check(state, data::character), "the game is still running on day 3")
    }

    // ==================================================================
    // The five complaints. All five are LIVE (WP12 pass 2) — never skip them.
    // ==================================================================

    @Test
    fun `Pukka poisons on the night it chooses and kills on the next`() {
        // Complaint 1: "Pukka ... offered to kill even though it's supposed to
        // poison then kill the turn after" (friction-log F17, lead D4).
        var state = GameActions.advancePhase(setUpSession())
        val kai = id(state, "Kai")
        val ben = id(state, "Ben")
        val dev = id(state, "Dev")

        // Night 1: the step exists, and it is a POISON step, not a kill step.
        val night1 = NightPlan.build(state, data::character)
        val pukka1 = assertNotNull(
            night1.steps.find { it.abilityId == "pukka" },
            "the Pukka must have a night-1 step",
        )
        val choose1 = assertNotNull(pukka1.action as? ChoosePlayers, "the Pukka chooses a player")
        assertEquals(1, choose1.max)
        assertTrue(
            choose1.perTarget.none { it is NightEffect.Attack },
            "night 1 must not offer a kill: ${choose1.perTarget}",
        )
        state = NightPlan.resolve(state, data::character, pukka1.key, NightInput(playerIds = listOf(ben)))
        assertTrue(
            Status.isImpaired(state, data::character, ben),
            "the Sailor is poisoned from night 1",
        )
        assertTrue(
            assertNotNull(state.player(ben)).alive,
            "nobody dies on the Pukka's first night",
        )
        // A poisoned Sailor is not immortal (friction-log test 2).
        assertTrue(
            Status.protections(state, data::character, ben).isEmpty(),
            "poison voids the Sailor's own protection",
        )

        // Night 2: one choice, two consequences, in the rules' order.
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // day 1, night 2
        val night2 = NightPlan.build(state, data::character)
        val pukka2 = assertNotNull(night2.steps.find { it.abilityId == "pukka" })
        state = NightPlan.resolve(state, data::character, pukka2.key, NightInput(playerIds = listOf(dev)))
        assertFalse(assertNotNull(state.player(ben)).alive, "the previously poisoned player dies")
        val death = assertNotNull(state.deaths.lastOrNull { it.playerId == ben })
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("pukka", death.killerCharacterId)
        assertEquals(kai, death.killerPlayerId)
        assertEquals(true, death.abilityImpairedAtDeath, "they die still poisoned, then become healthy")
        assertTrue(
            Status.effectsOn(state, data::character, ben).none { it.kind == EffectKind.POISONED },
            "and then becomes healthy",
        )
        assertTrue(Status.isImpaired(state, data::character, dev), "the new target is poisoned")

        // Night 3: the Exorcist silences the Pukka. No new poison, but the
        // player poisoned on night 2 still dies (data-accuracy test 16).
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // day 2, night 3
        state = GameActions.placeExclusiveReminder(
            state,
            id(state, "Kai"),
            PlacedReminder("exorcist", "Chosen"),
        )
        val night3 = NightPlan.build(state, data::character)
        val pukka3 = assertNotNull(night3.steps.find { it.abilityId == "pukka" })
        assertTrue(
            pukka3.gate is StepGate.Reduced,
            "an Exorcised Demon is REDUCED, never skipped (lead D24): ${pukka3.gate}",
        )
        state = NightPlan.resolve(state, data::character, pukka3.key, NightInput(none = true))
        assertFalse(assertNotNull(state.player(dev)).alive, "the deferred death still happens")
    }

    @Test
    fun `Devil's Advocate cannot choose the same player two nights running`() {
        // Complaint 2: the DA token gave the storyteller no way to honour
        // "different to last night" (friction-log F21/F30, lead D3).
        var state = GameActions.advancePhase(setUpSession())
        val ana = id(state, "Ana")
        val lena = id(state, "Lena")

        val night1 = NightPlan.build(state, data::character)
        val da1 = assertNotNull(night1.steps.find { it.abilityId == "devilsadvocate" })
        state = NightPlan.resolve(state, data::character, da1.key, NightInput(playerIds = listOf(lena)))

        // Through dawn, the whole day, and dusk: the TOKEN goes, the CHOICE stays.
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // day 1, night 2
        val recorded = assertNotNull(
            Memory.lastChoice(state, sourceId = "devilsadvocate", holderId = ana),
            "the ledger remembers the choice after the token expired",
        )
        assertEquals(listOf(lena), recorded.targetIds)
        assertTrue(lena in Memory.forbiddenTargets(state, "devilsadvocate", ana))

        val night2 = NightPlan.build(state, data::character)
        val da2 = assertNotNull(night2.steps.find { it.abilityId == "devilsadvocate" })
        val choose = assertNotNull(da2.action as? ChoosePlayers)
        assertTrue(
            TargetConstraint.DIFFERENT_FROM_LAST_NIGHT in choose.constraints,
            "night 2's picker must exclude last night's choice: ${choose.constraints}",
        )
        assertTrue(
            da2.banner.isNotBlank() || da2.detail.isNotBlank(),
            "and the step must say who was chosen last night",
        )
    }

    @Test
    fun `a Gossip statement is recorded on the day it is made`() {
        // Complaint 3: "make it easy to write down all the gossips even if
        // Gossip isn't in play" (friction-log F52, invariant I3).
        var state = setUpSession()
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // night 1, day 1
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // night 2, day 2
        val dev = id(state, "Dev")

        state = Ledger.statement(
            state,
            speakerId = dev,
            sourceId = "gossip",
            text = "There is no Minion sitting next to me.",
        )
        val recorded = Memory.statementsOn(state, day = 2, sourceId = "gossip")
        assertEquals(1, recorded.size, "the statement is on the day it was made")
        val entry = recorded.single()
        assertEquals(dev, entry.actorId)
        assertEquals(Verdict.UNJUDGED, entry.verdict, "the storyteller judges it later, not now")

        state = Ledger.setVerdict(state, entry.id, Verdict.TRUE)
        assertEquals(
            Verdict.TRUE,
            Memory.statementsOn(state, day = 2, sourceId = "gossip").single().verdict,
        )

        // Night 3 reads the day-2 statement back; the Gossip never has to be
        // asked "what did you say?" again.
        state = GameActions.advancePhase(state)
        assertEquals(3, state.cycle)
        val gossipStep = assertNotNull(
            NightPlan.build(state, data::character).steps.find { it.abilityId == "gossip" },
        )
        assertTrue(
            "no Minion" in gossipStep.banner || "no Minion" in gossipStep.detail,
            "the night step quotes the statement: ${gossipStep.banner} / ${gossipStep.detail}",
        )
    }

    @Test
    fun `Professor resurrection announces at dawn and re-runs the first night`() {
        // Complaint 4: "When Professor brings someone back it should remind in
        // the morning and rerun the 1st night for that" (invariant I4, lead D7).
        var state = setUpSession()
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // night 1, day 1
        val erin = id(state, "Erin")
        val gita = id(state, "Gita")
        state = nominate(state, nominator = "Lena", nominee = "Erin", votes = 7)
        state = Deaths.attempt(
            state,
            data::character,
            erin,
            KillCause(DeathCause.EXECUTION),
        ).state
        assertFalse(assertNotNull(state.player(erin)).alive)

        state = GameActions.advancePhase(state) // night 2
        val professorStep = assertNotNull(
            NightPlan.build(state, data::character).steps.find { it.abilityId == "professor" },
        )
        state = NightPlan.resolve(
            state,
            data::character,
            professorStep.key,
            NightInput(playerIds = listOf(erin)),
        )
        assertTrue(assertNotNull(state.player(erin)).alive, "a dead Townsfolk comes back")
        assertTrue(
            Memory.isSpent(state, "professor", gita),
            "and the once-per-game ability is spent",
        )

        // (a) an obligation to re-run her first night, tonight;
        val rerun = assertNotNull(
            Prompts.forTonight(state).find {
                it.kind == PromptKind.RUN_FIRST_NIGHT && it.subjectPlayerId == erin
            },
            "a RUN_FIRST_NIGHT prompt for the resurrected seat: ${state.prompts}",
        )
        assertEquals("grandmother", rerun.stepSlotId)

        // (b) tonight's sheet grows the inserted first-night step, in place;
        val replanned = NightPlan.build(state, data::character)
        val inserted = assertNotNull(
            replanned.steps.find { it.key.abilityId == "grandmother" && it.key.variant == StepVariant.FIRST },
            "the Grandmother's FIRST-night step is inserted: ${replanned.steps.map { it.key.token }}",
        )
        assertEquals(WakeStyle.FIRST_NIGHT, inserted.style)

        // (c) and dawn tells the storyteller to announce it, without saying why.
        val dawn = Briefings.at(state, data::character, BriefingSlot.DAWN)
        assertTrue(
            dawn.announce.any { "alive again" in it.text },
            "the dawn report announces the resurrection: ${dawn.items.map { it.text }}",
        )
    }

    @Test
    fun `the Lunatic gets their own bluffs fake Minions and Demon behaviour`() {
        // Complaint 5: the Lunatic had no illusion kit at all (friction-log
        // F13/F27, lead D20).
        var state = GameActions.advancePhase(setUpSession())
        val jonas = id(state, "Jonas")

        val requirements = Bluffs.requirements(state, data::character)
        val lunatic = assertNotNull(
            requirements.find { it.sourceId == "lunatic" && it.recipientId == jonas },
            "the Lunatic owes a bluff set of its own: ${requirements.map { it.key }}",
        )
        assertTrue(lunatic.allowInPlay, "a Lunatic's bluffs MAY be characters that are in play")
        assertEquals(3, lunatic.size)
        assertTrue(
            requirements.any { it.key == BluffRequirement.DEMON_KEY },
            "and the real Demon still owes their own, independent set",
        )

        // Two sets, two keys, no interference.
        state = Bluffs.set(state, lunatic.key, listOf("chambermaid", "gossip", "tealady"))
        assertEquals(listOf("chambermaid", "gossip", "tealady"), state.bluffSets[lunatic.key])
        assertEquals(listOf("courtier", "minstrel", "moonchild"), state.demonBluffIds)

        // The fake Minion count equals the real Minion count (Ana and Lena).
        // WP4 filed the fake Minions as a SETUP CHECKLIST row, not as a line of
        // the night step's detail, and keeps it advisory (`blocking = false`)
        // because "Fake Minion" is not an official token. The pass-1 fixture
        // read the count off `NightStep.detail`; the row is the real home, and
        // it is the thing a storyteller can act on.
        val fakeMinions = assertNotNull(
            SetupRequirements.all(state, data::character).find { it.id == "lunatic.minions:$jonas" },
            "the Lunatic owes a fake-Minion row of their own",
        )
        assertFalse(
            fakeMinions.satisfied(state, data::character),
            "nobody has been pointed out as a Minion yet",
        )
        assertFalse(
            fakeMinions.apply(state, Selection(playerIds = listOf(id(state, "Ana"))))
                .let { fakeMinions.satisfied(it, data::character) },
            "one fake Minion is not enough: the game has two real ones",
        )
        state = fakeMinions.apply(state, Selection(playerIds = listOf(id(state, "Ana"), id(state, "Lena"))))
        assertTrue(
            fakeMinions.satisfied(state, data::character),
            "two real Minions, two players pointed out",
        )

        // Lead D70: `Identity.derivedGrants` names the BELIEVED DEMON as the
        // ability and keeps "lunatic" as the slot, so the row sorts at the
        // Lunatic's own place on the nightsheet while running the Po's action.
        val lunaticInfo = assertNotNull(
            NightPlan.build(state, data::character).steps.find { it.slotId == "lunatic" },
            "the Lunatic wakes on a row of their own",
        )
        assertEquals("po", lunaticInfo.abilityId, "the Lunatic runs the believed Demon's ability (D70)")
        assertEquals(listOf(jonas), lunaticInfo.wakes, "one seat wakes on it: the Lunatic")
        assertTrue(
            lunaticInfo.detail.contains(lunatic.label),
            "night 1 hands over the Lunatic's own bluff set: ${lunaticInfo.detail}",
        )
        // The Po has no first night, so neither has a Lunatic who believes they
        // are the Po — the row is the illusion (bluffs, token, fake Minions),
        // and the storyteller is told so on the row itself.
        assertEquals(null, lunaticInfo.action, "no first-night choice: the Po does not act on night 1")
        assertTrue("illusion" in lunaticInfo.banner, "the row says so: ${lunaticInfo.banner}")

        // And from night 2 the Lunatic acts as the Po: a full Demon action, on
        // the Lunatic's own slot, that kills nobody. Charged with the Po's
        // official "3 Attacks" token it takes three targets, exactly as the
        // reported session did.
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // day 1, night 2
        state = GameActions.addReminder(state, jonas, PlacedReminder("po", "3 Attacks"))
        val lunaticAct = assertNotNull(
            NightPlan.build(state, data::character).steps.find { it.slotId == "lunatic" },
        )
        assertEquals("po", lunaticAct.abilityId)
        val action = assertNotNull(lunaticAct.action as? ChoosePlayers, "a Po-shaped choice")
        assertEquals(3, action.max, "the Po's charged night takes three targets")
        assertTrue(
            action.perTarget.none { it is NightEffect.Attack },
            "and not one of them is a kill: ${action.perTarget}",
        )
        val aliveBefore = state.alivePlayers.size
        val chosen = listOf(id(state, "Cleo"), id(state, "Iris"), id(state, "Hal"))
        state = NightPlan.resolve(state, data::character, lunaticAct.key, NightInput(playerIds = chosen))
        assertEquals(aliveBefore, state.alivePlayers.size, "a Lunatic's kills do nothing at all")
        // The illusion is still drawn: the Lunatic's own Chosen markers land.
        for (victim in chosen) {
            val drawn = Effects.rendered(state, data::character, victim)
            assertTrue(
                drawn.any { Tokens.key(it.sourceId, it.label) == Tokens.key("lunatic", "Chosen") },
                "the Lunatic's Chosen marker is on seat $victim: ${drawn.map { it.label }}",
            )
        }
    }
}
