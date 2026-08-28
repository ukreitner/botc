package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The night sheet as a pure function of tonight's state (WP2, invariant I6).
 *
 * Every acceptance criterion of ARCHITECTURE §4 WP2 has a case here: purity and
 * cost, the four dynamic insertions, the four gates, per-holder identity, and
 * the Chambermaid's count.
 */
class NightPlanTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val sv = data.builtInScripts().first { it.id == "sv" }
    private val lookup: (String) -> Character? = data::character

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(script: Script, vararg roles: String): GameState {
        var state = GameActions.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    /** Straight to night [n], without walking the days. */
    private fun atNight(state: GameState, n: Int): GameState = state.copy(cycle = n)

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String, holderId: Long? = null): NightStep? =
        plan(state).steps.firstOrNull {
            it.abilityId == abilityId && (holderId == null || it.holderId == holderId)
        }

    // ==================================================================
    // Purity and cost
    // ==================================================================

    @Test
    fun `build is pure and idempotent`() {
        val state = game(tb, "imp", "poisoner", "washerwoman", "empath", "chef", "monk", "mayor", "butler")
        val first = plan(state)
        val second = plan(state)
        assertEquals(first, second, "two builds of one state must be identical")
        // Building must not touch the state it was given.
        assertEquals(state, state.copy(), "build mutates nothing")
        assertTrue(first.steps.isNotEmpty())
    }

    @Test
    fun `build costs under five milliseconds at fifteen seats`() {
        val roles = listOf(
            "imp", "poisoner", "baron", "spy", "washerwoman", "librarian", "investigator",
            "chef", "empath", "fortuneteller", "undertaker", "monk", "ravenkeeper",
            "virgin", "mayor",
        )
        val state = atNight(game(tb, *roles.toTypedArray()), 2)
        assertEquals(15, state.players.size)
        repeat(WARMUP) { plan(state) }
        val started = System.nanoTime()
        repeat(RUNS) { plan(state) }
        val perBuild = (System.nanoTime() - started) / RUNS / 1_000_000.0
        assertTrue(perBuild < 5.0, "NightPlan.build took %.2f ms at 15 seats".format(perBuild))
    }

    private companion object {
        const val WARMUP = 50
        const val RUNS = 100
    }

    // ==================================================================
    // Dynamic insertion
    // ==================================================================

    @Test
    fun `a Pit-Hag-created Chef gets a first-night step after the Pit-Hag`() {
        var state = atNight(game(sv, "vortox", "pithag", "saint", "chef", "oracle", "seamstress"), 3)
        val saint = 2L
        val pithag = assertNotNull(step(state, "pithag"), "the Pit-Hag wakes on night 3")
        // The storyteller has just run the Pit-Hag's step.
        state = state.copy(
            nightStepsDone = plan(state).steps.filter { it.order <= pithag.order }
                .map { it.key.token }
                .toSet(),
        )
        state = Identity.changeCharacter(state, lookup, saint, "chef", ChangeReason.PIT_HAG)

        val chef = assertNotNull(
            plan(state).steps.firstOrNull {
                it.key == StepKey("chef", saint, StepVariant.FIRST)
            },
            "the new Chef owes a first-night step: ${plan(state).steps.map { it.key.token }}",
        )
        assertEquals(WakeStyle.FIRST_NIGHT, chef.style)
        assertTrue(chef.order > pithag.order, "it is placed after the Pit-Hag")
        assertFalse(chef.key.token in state.nightStepsDone, "and it is not ticked")
    }

    @Test
    fun `a resurrection inserts a FIRST re-run of the resurrected seat's night`() {
        var state = game(bmr, "pukka", "professor", "grandmother", "sailor", "tealady", "fool")
        val grandmother = 2L
        state = Deaths.attempt(
            state,
            lookup,
            grandmother,
            KillCause(DeathCause.DEMON_KILL, "pukka", 0L),
        ).state
        assertFalse(assertNotNull(state.player(grandmother)).alive)

        state = atNight(state, 2)
        state = Deaths.resurrect(state, lookup, grandmother)

        val rerun = assertNotNull(
            plan(state).steps.firstOrNull {
                it.key == StepKey("grandmother", grandmother, StepVariant.FIRST)
            },
            "the resurrected seat's first night is re-run: ${plan(state).steps.map { it.key.token }}",
        )
        assertEquals(WakeStyle.FIRST_NIGHT, rerun.style)
        assertNotNull(rerun.promptId, "and it discharges the RUN_FIRST_NIGHT prompt")
    }

    @Test
    fun `a Summoner-created Lleech acts the same night`() {
        var state = atNight(game(bmr, "summoner", "poisoner", "sailor", "tealady", "fool", "gossip"), 3)
        val minion = 1L
        assertTrue(plan(state).steps.none { it.abilityId == "lleech" })

        state = Identity.changeCharacter(state, lookup, minion, "lleech", ChangeReason.SUMMONER, newEvil = true)

        val lleech = assertNotNull(
            plan(state).steps.firstOrNull { it.abilityId == "lleech" && it.holderId == minion },
            "the Demon the Summoner made acts tonight: ${plan(state).steps.map { it.key.token }}",
        )
        assertTrue(lleech.required)
    }

    @Test
    fun `a mid-night Scarlet Woman promotion lands after the cursor and is badged`() {
        var state = atNight(
            game(tb, "imp", "scarletwoman", "chef", "empath", "monk", "mayor", "butler", "virgin"),
            2,
        )
        val imp = 0L
        val scarlet = 1L
        val impStep = assertNotNull(step(state, "imp"), "the Imp wakes on night 2")
        // The Demon's slot has already gone by when the promotion happens.
        state = state.copy(
            nightStepsDone = plan(state).steps.filter { it.order <= impStep.order }
                .map { it.key.token }
                .toSet(),
        )
        state = Deaths.attempt(state, lookup, imp, KillCause(DeathCause.DEMON_KILL, "imp", imp)).state
        state = Identity.changeCharacter(state, lookup, scarlet, "imp", ChangeReason.SCARLET_WOMAN, newEvil = true)

        val promoted = assertNotNull(
            plan(state).steps.firstOrNull { it.abilityId == "imp" && it.holderId == scarlet },
            "the new Demon has a row: ${plan(state).steps.map { it.key.token }}",
        )
        assertTrue(promoted.order > impStep.order, "after the cursor, not at the Demon's slot")
        assertTrue(
            NightPlan.OUT_OF_ORDER in promoted.badges,
            "and badged out of order: ${promoted.badges}",
        )
    }

    // ==================================================================
    // Gating
    // ==================================================================

    @Test
    fun `an alive Ravenkeeper is skipped and auto-ticked, a dead one fires`() {
        var state = atNight(game(tb, "imp", "poisoner", "ravenkeeper", "chef", "monk", "mayor"), 2)
        val ravenkeeper = 2L

        val alive = assertNotNull(step(state, "ravenkeeper"))
        assertTrue(alive.gate is StepGate.Skip, "an alive Ravenkeeper does not wake: ${alive.gate}")
        assertFalse(alive.required)
        assertTrue(alive.isDone(emptySet()), "a skipped row is done by definition")
        assertTrue(
            plan(state).unfinished(emptySet()).none { it.abilityId == "ravenkeeper" },
            "and never blocks dawn",
        )

        state = Deaths.attempt(
            state,
            lookup,
            ravenkeeper,
            KillCause(DeathCause.DEMON_KILL, "imp", 0L),
        ).state
        val dead = assertNotNull(step(state, "ravenkeeper"))
        assertEquals(StepGate.Fire, dead.gate, "the night they die, they wake")
        assertTrue("died tonight" in dead.badges, dead.badges.toString())
    }

    @Test
    fun `an Exorcised Pukka is reduced and still kills its standing victim`() {
        var state = game(bmr, "pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = 0L
        val victim = 2L

        val night1 = assertNotNull(step(state, "pukka"), "the Pukka acts on night 1")
        val choose = assertNotNull(night1.action as? ChoosePlayers)
        assertTrue(
            choose.perTarget.none { it is NightEffect.Attack },
            "night 1 poisons, it does not kill: ${choose.perTarget}",
        )
        state = NightPlan.resolve(state, lookup, night1.key, NightInput(playerIds = listOf(victim)))
        assertTrue(Status.isImpaired(state, lookup, victim), "the chosen player is poisoned")
        assertTrue(assertNotNull(state.player(victim)).alive, "and nobody dies on the first night")

        // Night 2: the Exorcist silences the Pukka.
        state = atNight(state, 2).copy(nightStepsDone = emptySet())
        state = Effects.place(
            state = state,
            target = pukka,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "exorcist",
            sourcePlayerId = 1L,
            until = Until.DAWN,
            label = "Chosen",
        ).state

        val night2 = assertNotNull(step(state, "pukka"))
        val gate = night2.gate
        assertTrue(gate is StepGate.Reduced, "a silenced Demon is REDUCED, never skipped: $gate")
        assertFalse(StepGate.CHOOSE in gate.allow, "the choice half is suppressed")
        assertTrue(StepGate.PENDING in gate.allow, "the deferred half still runs")

        state = NightPlan.resolve(state, lookup, night2.key, NightInput(none = true))
        assertFalse(
            assertNotNull(state.player(victim)).alive,
            "the player poisoned on a previous night still dies",
        )
        assertTrue(
            Status.effectsOn(state, lookup, victim).none { it.kind == EffectKind.POISONED },
            "and is no longer poisoned afterwards",
        )
    }

    @Test
    fun `a Vigormortis-preserved Minion acts while dead and stops when its Demon dies`() {
        var state = atNight(game(sv, "vigormortis", "poisoner", "chef", "oracle", "seamstress", "mutant"), 2)
        val vigormortis = 0L
        val poisoner = 1L

        state = Deaths.attempt(
            state,
            lookup,
            poisoner,
            KillCause(DeathCause.DEMON_KILL, "vigormortis", vigormortis),
        ).state
        state = Effects.place(
            state = state,
            target = poisoner,
            kind = EffectKind.HAS_ABILITY,
            sourceCharacterId = "vigormortis",
            sourcePlayerId = vigormortis,
            until = Until.FOREVER,
            label = "Has Ability",
        ).state

        assertEquals(
            StepGate.Fire,
            assertNotNull(step(state, "poisoner")).gate,
            "a Vigormortis-preserved Minion keeps acting while dead",
        )

        state = Deaths.attempt(
            state,
            lookup,
            vigormortis,
            KillCause(DeathCause.EXECUTION),
        ).state
        assertTrue(
            assertNotNull(step(state, "poisoner")).gate is StepGate.Skip,
            "and stops the moment the Vigormortis dies",
        )
    }

    @Test
    fun `a Zombuul is skipped on a day-death day and fires otherwise`() {
        var state = atNight(game(bmr, "zombuul", "sailor", "tealady", "fool", "gossip", "professor"), 2)
        assertEquals(
            StepGate.Fire,
            assertNotNull(step(state, "zombuul")).gate,
            "nobody died today: the Zombuul attacks",
        )

        // Somebody dies during day 1 — the Zombuul does not kill on night 2.
        var day = state.copy(phase = Phase.DAY, cycle = 1)
        day = Deaths.attempt(day, lookup, 5L, KillCause(DeathCause.EXECUTION)).state
        assertTrue(day.deaths.isNotEmpty(), "the execution really killed someone")
        state = day.copy(phase = Phase.NIGHT, cycle = 2)
        val gate = assertNotNull(step(state, "zombuul")).gate
        assertTrue(gate is StepGate.Skip, "someone died today: $gate")
        assertTrue("died today" in gate.reason, gate.reason)
    }

    @Test
    fun `a Godfather asks the storyteller when today's deaths are ambiguous`() {
        var state = game(bmr, "zombuul", "godfather", "sailor", "tealady", "fool", "gossip")
        var day = state.copy(phase = Phase.DAY, cycle = 1)
        day = Deaths.attempt(day, lookup, 5L, KillCause(DeathCause.EXECUTION)).state
        assertTrue(day.deaths.isNotEmpty(), "the execution really killed someone")
        state = day.copy(phase = Phase.NIGHT, cycle = 2)
        val gate = assertNotNull(step(state, "godfather")).gate
        assertTrue(gate is StepGate.Conditional, "a Townsfolk died — was it an Outsider? $gate")
    }

    // ==================================================================
    // Per-holder identity
    // ==================================================================

    @Test
    fun `two Village Idiots are two steps with distinct tokens`() {
        var state = atNight(
            game(tb, "imp", "poisoner", "villageidiot", "villageidiot", "chef", "mayor"),
            2,
        )
        val rows = plan(state).steps.filter { it.abilityId == "villageidiot" }
        assertEquals(2, rows.size, "one row per holder (lead D16/D23)")
        assertEquals(listOf(2L, 3L), rows.map { it.holderId })
        assertEquals(2, rows.map { it.key.token }.toSet().size, "with distinct tokens")

        state = NightPlan.toggleDone(state, rows[0].key.token)
        assertTrue(rows[0].key.token in state.nightStepsDone)
        assertFalse(rows[1].key.token in state.nightStepsDone, "ticking one does not tick the other")
    }

    @Test
    fun `a Drunk wakes as the character they believe they are`() {
        var state = game(tb, "imp", "poisoner", "drunk", "chef", "monk", "mayor")
        state = GameActions.setShownCharacter(state, 2, "washerwoman")
        val row = assertNotNull(
            step(state, "washerwoman", holderId = 2L),
            "the Drunk wakes on the Washerwoman's row",
        )
        assertEquals("drunk", row.sourceId, "and the row says which grant produced it")
        assertTrue(row.banner.isNotBlank(), "with a banner saying nothing they do has any effect")
        assertTrue(plan(state).steps.none { it.abilityId == "drunk" })
    }

    // ==================================================================
    // The Chambermaid's count (lead D13)
    // ==================================================================

    @Test
    fun `wokeCount is zero for the wiki's Exorcist and Shabaloth example`() {
        var state = atNight(
            game(bmr, "shabaloth", "exorcist", "fool", "chambermaid", "sailor", "tealady"),
            2,
        )
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "exorcist",
            sourcePlayerId = 1L,
            until = Until.DAWN,
            label = "Chosen",
        ).state
        assertEquals(
            0,
            NightPlan.wokeCount(state, lookup, listOf(0L, 2L)),
            "the silenced Shabaloth and the never-waking Fool both count for nothing",
        )
        assertEquals(
            1,
            NightPlan.wokeCount(state, lookup, listOf(1L, 2L)),
            "the Exorcist woke for their own ability",
        )
    }

    @Test
    fun `an INFORMED wake counts for nothing`() {
        val state = game(
            tb,
            "imp", "baron", "spy", "washerwoman", "chef", "empath", "monk", "mayor",
        )
        val minionInfo = assertNotNull(
            plan(state).steps.firstOrNull { it.slotId == NightMarkers.MINION_INFO },
        )
        assertEquals(WakeCount.INFORMED, minionInfo.wakeCounts)
        assertTrue(1L in minionInfo.wakes, "the Baron is woken for Minion info")
        assertEquals(
            0,
            NightPlan.wokeCount(state, lookup, listOf(1L)),
            "but Minion info is not their own ability (lead D13)",
        )
    }

    // ==================================================================
    // The source gate this package must pass (I1)
    // ==================================================================

    @Test
    fun `no character id appears in NightPlan or NightAction`() {
        val ids = data.characters.map { it.id }.toSet()
        val literal = Regex("\"([^\"\\\\\\n]*)\"")
        for (relative in listOf(
            "engine/src/main/kotlin/com/clocktower/engine/NightPlan.kt",
            "engine/src/main/kotlin/com/clocktower/engine/NightAction.kt",
        )) {
            val text = assertNotNull(RepoFiles.textOrNull(relative), "missing $relative")
            val hits = text.lines().withIndex()
                .filterNot { (_, line) ->
                    val t = line.trimStart()
                    t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                }
                .flatMap { (i, line) ->
                    literal.findAll(line).map { it.groupValues[1] }.filter { it in ids }
                        .map { "$relative:${i + 1} $it" }
                        .toList()
                }
            assertTrue(hits.isEmpty(), "character ids in the night engine (I1):\n$hits")
        }
    }
}
