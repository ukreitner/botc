package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Teensyville fixture (ARCHITECTURE §4 WP12).
 *
 * The three fixtures in `FullGamePlaytestTest` are 15-player games on the
 * built-in scripts, replayed through the pre-rebuild `GameActions` façade. This
 * one is deliberately the opposite on both axes:
 *
 *  * **A Teensyville table.** Six players on a hand-rolled twelve-character
 *    script, loaded the way a storyteller actually loads one — pasted JSON
 *    through [ScriptParser]. Small games are not big games with fewer seats:
 *    the distribution is its own table, the Minions and the Demon are not
 *    introduced to each other, and the Demon is owed no bluffs. All three of
 *    those are pinned below, against a seven-seat control that shows the same
 *    script behaving normally the moment the table grows.
 *
 *  * **The new engine surface only.** [NightPlan.build] / [NightPlan.resolve],
 *    [DayRules.checkNomination] / [DayRules.record], [Execution.execute],
 *    [Briefings.at], [WinCheck] — never the deprecated `Deaths.kill`, never a
 *    `GameActions` façade call. The last test in the file greps this file's own
 *    source to keep it that way.
 *
 * The game: two nights, one execution, a good win. Night 1 the Poisoner
 * poisons the Chef, whose count is therefore a lie the engine tells the
 * storyteller to tell. Night 2 the Poisoner poisons the MONK, so the Monk's
 * protection is void and the Imp's kill lands on the seat it was aimed at.
 * Day 2 the table executes the Imp.
 */
class TeensyvillePlaytestTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    /**
     * A Teensyville-shaped homebrew script: 6 Townsfolk, 2 Outsiders,
     * 2 Minions, 2 Demons. Written in the official script-tool format, with
     * `_meta`, snake_case ids and a mixed-edition cast, because that is what
     * gets pasted into the app.
     */
    private val scriptJson = """
        [
          {"id": "_meta", "name": "Harbour Lights", "author": "The Storyteller"},
          "chef", "empath", "monk", "undertaker", "fortune_teller", "slayer",
          "butler", "saint",
          "poisoner", "spy",
          "imp", "no_dashii"
        ]
    """.trimIndent()

    private val script by lazy { ScriptParser.parse(scriptJson) }

    /** Seat order round the circle. Ids are seat indices, as `Seats.newGame` mints them. */
    private val ada = 0L // Chef
    private val bea = 1L // Empath
    private val cai = 2L // Monk
    private val dot = 3L // Butler
    private val eli = 4L // Poisoner
    private val fen = 5L // Imp

    private val names = listOf("Ada", "Bea", "Cai", "Dot", "Eli", "Fen")
    private val characterIds = listOf("chef", "empath", "monk", "butler", "poisoner", "imp")

    /** The grimoire as the storyteller leaves it before night 1. */
    private fun setUpTable(): GameState {
        var state = Seats.newGame(script, names)
        characterIds.forEachIndexed { index, id ->
            state = Seats.assignCharacter(state, index.toLong(), id)
        }
        return state
    }

    /** Resolves one night row by its slot, always against a freshly built plan (I6). */
    private fun resolve(state: GameState, slotId: String, input: NightInput = NightInput()): GameState {
        val plan = NightPlan.build(state, lookup)
        val step = assertNotNull(
            plan.steps.find { it.slotId == slotId },
            "no $slotId row tonight: ${plan.steps.map { it.slotId }}",
        )
        val next = NightPlan.resolve(state, lookup, step.key, input)
        assertTrue(
            step.key.token in next.nightStepsDone,
            "resolving a row ticks it off: ${step.key.token}",
        )
        return next
    }

    private fun sheet(state: GameState): List<String> =
        NightPlan.build(state, lookup).steps.map { it.slotId }

    // ==================================================================
    // Setup: the script, the distribution, the bag
    // ==================================================================

    @Test
    fun `the pasted Teensyville script parses to a legal small-game cast`() {
        assertEquals("Harbour Lights", script.name)
        assertEquals("The Storyteller", script.author)
        assertEquals(
            listOf(
                "chef", "empath", "monk", "undertaker", "fortuneteller", "slayer",
                "butler", "saint", "poisoner", "spy", "imp", "nodashii",
            ),
            script.characterIds,
            "snake_case ids are normalised on the way in",
        )
        assertTrue(data.unknownIds(script).isEmpty(), "every id resolves: ${data.unknownIds(script)}")

        // The Teensyville shape: 6/2/2/2, mixed edition, no Travellers.
        val cast = data.resolve(script)
        assertEquals(6, cast.count { it.team == Team.TOWNSFOLK })
        assertEquals(2, cast.count { it.team == Team.OUTSIDER })
        assertEquals(2, cast.count { it.team == Team.MINION })
        assertEquals(2, cast.count { it.team == Team.DEMON })
    }

    @Test
    fun `the five and six player distributions are their own table and the bag is checked against them`() {
        // Small games are NOT the 7+ formula with fewer seats: Teensyville is
        // three Townsfolk, one Minion, one Demon, and the sixth seat is the
        // only Outsider.
        assertEquals(Distribution(3, 0, 1, 1), Setup.distributionFor(5))
        assertEquals(Distribution(3, 1, 1, 1), Setup.distributionFor(6))
        assertEquals(5, Setup.distributionFor(5).total)
        assertEquals(6, Setup.distributionFor(6).total)

        val bag = characterIds.map { assertNotNull(data.character(it), it) }
        assertTrue(Setup.validateBag(bag, 6).isEmpty(), "the six-seat bag: ${Setup.validateBag(bag, 6)}")

        // The same table one seat smaller drops the Outsider, not a Townsfolk.
        val five = bag.filterNot { it.id == "butler" }
        assertTrue(Setup.validateBag(five, 5).isEmpty(), "the five-seat bag: ${Setup.validateBag(five, 5)}")
        assertTrue(
            Setup.validateBag(bag.filterNot { it.id == "chef" }, 5).isNotEmpty(),
            "a five-seat bag with an Outsider and only two Townsfolk is illegal",
        )

        // Two Outsiders needs a Baron, and this script has none.
        val twoOutsiders = bag.filterNot { it.id == "chef" } + assertNotNull(data.character("saint"))
        assertTrue(
            Setup.validateBag(twoOutsiders, 6).any { "Outsider" in it },
            "2 Outsiders at 6 players must be rejected: ${Setup.validateBag(twoOutsiders, 6)}",
        )
    }

    @Test
    fun `at six players nobody is introduced to anybody and the Demon is owed no bluffs`() {
        val state = Phases.advancePhase(setUpTable(), lookup)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(1, state.cycle)

        // THE Teensyville rule: the Minion/Demon info steps need seven players.
        // At six there is no MINION INFO, no DEMON INFO, and no bluff set — the
        // evil team knows itself because there is only one of each.
        val night1 = sheet(state)
        assertEquals(
            listOf(NightMarkers.DUSK, "poisoner", "chef", "empath", "butler", NightMarkers.DAWN),
            night1,
            "night 1 of a six-player game",
        )
        assertFalse(NightMarkers.MINION_INFO in night1)
        assertFalse(NightMarkers.DEMON_INFO in night1)
        assertTrue(
            Bluffs.requirements(state, lookup).none { it.key == BluffRequirement.DEMON_KEY },
            "no bluffs are owed below seven players: " +
                Bluffs.requirements(state, lookup).map { it.key },
        )
        // And nothing on the setup checklist is outstanding: no Drunk, no
        // Fortune Teller, no bluffs — a Teensyville table starts clean.
        assertTrue(
            SetupRequirements.all(state, lookup).all { it.satisfied(state, lookup) },
            "outstanding setup rows: " +
                SetupRequirements.all(state, lookup)
                    .filterNot { it.satisfied(state, lookup) }.map { it.id },
        )
    }

    @Test
    fun `the seventh seat is what turns the info steps and the bluffs back on`() {
        // The control for the test above: same script, same evil team, one more
        // seat. The gate is the player count, never the script.
        var state = setUpTable()
        state = Seats.addSeat(state, "Gus")
        val gus = assertNotNull(state.players.last()).id
        state = Seats.assignCharacter(state, gus, "fortuneteller")
        assertEquals(7, state.seats.size)

        state = Phases.advancePhase(state, lookup)
        val night1 = sheet(state)
        assertTrue(NightMarkers.MINION_INFO in night1, "seven players: $night1")
        assertTrue(NightMarkers.DEMON_INFO in night1, "seven players: $night1")
        assertTrue(
            Bluffs.requirements(state, lookup).any { it.key == BluffRequirement.DEMON_KEY },
            "and the Demon is owed three bluffs again",
        )
    }

    // ==================================================================
    // The game: two nights, one execution, a win
    // ==================================================================

    @Test
    fun `six players play two nights one execution and a good win`() {
        var state = Phases.advancePhase(setUpTable(), lookup)

        // ---- Night 1 -------------------------------------------------
        // The Poisoner poisons the Chef, so the Chef's count is a lie the
        // engine tells the storyteller to tell.
        state = resolve(state, "poisoner", NightInput(playerIds = listOf(ada)))
        assertTrue(Status.isImpaired(state, lookup, ada), "Ada is poisoned")

        val chefInfo = assertNotNull(InfoCalc.compute(data, state, "chef", ada))
        assertEquals(InfoObligation.MAY_LIE, chefInfo.obligation, "a poisoned Chef may be lied to")
        assertTrue(chefInfo.abilityMalfunctions)
        assertTrue(
            chefInfo.caveats.any { "POISONED" in it },
            "and the storyteller is told why: ${chefInfo.caveats}",
        )
        assertTrue(chefInfo.alternatives.isNotEmpty(), "with alternative answers to hand")
        state = resolve(state, "chef")

        // The Empath is healthy: both her neighbours are good, and the engine
        // says so with no caveat at all.
        val empathInfo = assertNotNull(InfoCalc.compute(data, state, "empath", bea))
        assertEquals(InfoObligation.TRUTH, empathInfo.obligation)
        assertEquals(emptyList(), empathInfo.caveats)
        assertTrue(empathInfo.headline.startsWith("0"), empathInfo.headline)
        state = resolve(state, "empath")

        state = resolve(state, "butler", NightInput(playerIds = listOf(bea)))

        val dawn1 = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertTrue(
            dawn1.announce.any { "nobody died" in it.text },
            "night 1 killed nobody: ${dawn1.items.map { it.text }}",
        )

        // ---- Day 1: a nomination that falls short --------------------
        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(1, state.cycle)
        assertEquals(6, state.alivePlayers.size)
        assertEquals(3, state.executionThreshold, "six alive: three votes execute")

        // The day opens by telling the storyteller who is not working.
        assertTrue(
            Briefings.at(state, lookup, BriefingSlot.DAY_START).items
                .any { it.playerId == ada && "does not work" in it.text },
            "the poisoned Chef is on the day-start briefing",
        )

        val check = DayRules.checkNomination(state, lookup, nominatorId = bea, nomineeId = eli)
        assertTrue(check.legal, "a healthy alive player may nominate: ${check.blockers}")
        assertTrue(check.blockers.isEmpty())
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = bea,
                nomineeId = eli,
                voterIds = listOf(ada, bea),
                result = NominationResult.SAFE,
            ),
        )
        assertEquals(2, state.nominations.single().votes, "two hands, weighted by the frozen rules")
        assertEquals(3, assertNotNull(state.nominations.single().voteRules).threshold)
        assertEquals(null, DayRules.aboutToDie(state), "two of three is not enough")
        // Nominating twice in one day is refused without `force`.
        assertFalse(
            DayRules.checkNomination(state, lookup, nominatorId = bea, nomineeId = fen).legal,
            "Bea has already nominated today",
        )

        // Dusk names tonight's sheet before the tokens are swept.
        val dusk = Briefings.at(state, lookup, BriefingSlot.DUSK)
        assertTrue(
            dusk.items.any { "Poisoned" in it.text },
            "dusk lists the token about to come off: ${dusk.items.map { it.text }}",
        )

        // ---- Night 2: the Monk is poisoned, so the kill lands --------
        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(2, state.cycle)
        assertFalse(Status.isImpaired(state, lookup, ada), "night 1's poison ended at dawn")

        val night2 = sheet(state)
        assertEquals(
            listOf(NightMarkers.DUSK, "poisoner", "monk", "imp", "empath", "butler", NightMarkers.DAWN),
            night2,
            "night 2: the Chef is first-night only, the Monk and the Imp have joined",
        )
        assertTrue(night2.indexOf("monk") < night2.indexOf("imp"), "the Monk protects before the Imp kills")

        state = resolve(state, "poisoner", NightInput(playerIds = listOf(cai)))
        assertTrue(Status.isImpaired(state, lookup, cai), "the Monk is poisoned")

        state = resolve(state, "monk", NightInput(playerIds = listOf(bea)))
        assertTrue(
            Status.protections(state, lookup, bea).isEmpty(),
            "a poisoned Monk protects nobody: ${Status.protections(state, lookup, bea)}",
        )

        state = resolve(state, "imp", NightInput(playerIds = listOf(bea)))
        assertFalse(assertNotNull(state.player(bea)).alive, "so the Imp's kill lands")
        val death = assertNotNull(state.deaths.lastOrNull { it.playerId == bea })
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("imp", death.killerCharacterId)
        assertEquals(fen, death.killerPlayerId)
        assertTrue(death.atNight)

        state = resolve(state, "empath")
        state = resolve(state, "butler", NightInput(playerIds = listOf(ada)))

        val dawn2 = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertTrue(
            dawn2.announce.any { it.playerId == bea && "died" in it.text },
            "dawn announces the death: ${dawn2.items.map { it.text }}",
        )
        // The dawn briefing is computed BEFORE the sweep, so it can still name
        // the Monk token that failed.
        assertTrue(
            dawn2.of(BriefingKind.SWEPT).any { "Monk" in it.text },
            "and names the token coming off: ${dawn2.items.map { it.text }}",
        )

        // ---- Day 2: the table executes the Imp -----------------------
        state = Phases.advancePhase(state, lookup)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(2, state.cycle)
        assertEquals(5, state.alivePlayers.size)
        assertEquals(3, state.executionThreshold)
        assertEquals(
            dawn2.items.map { it.text },
            assertNotNull(state.lastDawn).items.map { it.text },
            "the dawn report is frozen on the state, so re-opening the save re-reads it",
        )

        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = ada,
                nomineeId = fen,
                voterIds = listOf(ada, cai, dot),
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
        assertEquals(3, state.nominations.last().votes)
        assertEquals(fen, DayRules.aboutToDie(state))
        assertEquals(null, WinCheck.check(state, lookup), "nothing is decided until the execution")

        state = Execution.execute(
            state,
            lookup,
            playerId = fen,
            nominatorId = ada,
            nominationIndex = state.nominations.size - 1,
        )
        val record = assertNotNull(state.executions.singleOrNull(), "one execution, all game")
        assertEquals(ExecutionOutcome.DIED, record.outcome)
        assertEquals("imp", record.characterIdAtExecution)
        assertEquals(true, record.wasEvilAtExecution)
        assertEquals(3, record.tally)
        assertEquals(3, record.threshold)
        assertEquals(ExecutionVia.VOTE, record.via)
        assertFalse(assertNotNull(state.player(fen)).alive)
        assertEquals(DeathCause.EXECUTION, assertNotNull(state.deaths.lastOrNull()).cause)

        // A second execution the same day is refused without `force`.
        assertEquals(
            state,
            Execution.execute(state, lookup, playerId = eli),
            "one execution per day",
        )

        val win = assertNotNull(WinCheck.check(state, lookup), "the Demon is dead")
        assertEquals(true, win.goodWins)
        assertEquals("demon-dead", win.ruleId)
        assertTrue(
            win.cautions.any { "star-pass" in it },
            "with the Imp's own caveat attached: ${win.cautions}",
        )
    }

    // ==================================================================
    // The fixture's own gate
    // ==================================================================

    /**
     * ARCHITECTURE §4 WP12 asks for a Teensyville fixture written against the
     * NEW engine surface. A test cannot assert what it did not call, so it
     * asserts what it did not WRITE: this file names neither the frozen
     * `GameActions` façade nor the deprecated kill verb, anywhere.
     *
     * The needles are assembled from pieces so that this test is not itself a
     * violation of the rule it enforces.
     */
    @Test
    fun `the fixture calls the new engine surface and never the frozen facade`() {
        val relative = "engine/src/test/kotlin/com/clocktower/engine/TeensyvillePlaytestTest.kt"
        val source = assertNotNull(RepoFiles.textOrNull(relative), "cannot read $relative")

        val facade = "GameActions" + "."
        val deprecatedKill = "Deaths" + ".kill("
        val hits = source.lines().withIndex()
            .filterNot { (_, line) ->
                val t = line.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .filter { (_, line) -> facade in line || deprecatedKill in line }
            .map { (index, line) -> "$relative:${index + 1}  ${line.trim()}" }
        assertTrue(
            hits.isEmpty(),
            "the Teensyville fixture must use NightPlan/DayRules/Execution/Briefings/WinCheck " +
                "directly, never the WP0 façade or the WP1-deprecated kill:\n" + hits.joinToString("\n"),
        )

        // …and it really does call the new surface (a gate that passes because
        // the file is empty is worth nothing).
        for (needle in listOf(
            "NightPlan.build", "NightPlan.resolve", "DayRules.checkNomination", "DayRules.record",
            "Execution.execute", "Briefings.at", "WinCheck.check", "ScriptParser.parse",
        )) {
            assertTrue(needle in source, "the fixture should exercise $needle")
        }
    }
}
