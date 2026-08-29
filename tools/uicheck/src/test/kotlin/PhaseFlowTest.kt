package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Bluffs
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.Deaths
import com.clocktower.engine.Effects
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.KillCause
import com.clocktower.engine.NightPlan
import com.clocktower.engine.Phase
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Script
import com.clocktower.engine.Seats
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.StepGate
import com.clocktower.engine.WinCheck
import com.clocktower.grimoire.ui.screens.DuskActions
import com.clocktower.grimoire.ui.screens.declareLabel
import com.clocktower.grimoire.ui.screens.declaredWinner
import com.clocktower.grimoire.ui.screens.endsTheGame
import com.clocktower.grimoire.ui.screens.PhaseFlow
import com.clocktower.grimoire.ui.screens.PhaseRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6's phase-flow acceptance: **`PhaseFlow.request` returns `Blocked` only for
 * REQUIRED steps.** A gated-off row is auto-ticked by the planner and renders
 * grey with a `[Run anyway]` (lead D37/D60) — it must never hold dawn up.
 *
 * Lives in `tools/uicheck` rather than `engine/src/test` because `PhaseFlow` is
 * UI code under `app/`, which the `:engine` test source set cannot see; uicheck
 * already compiles every app source. Run with `./gradlew -p tools/uicheck test`.
 */
class PhaseFlowTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val bmr: Script = data.builtInScripts().first { it.id == "bmr" }

    /**
     * The friction-log's twelve-seat Bad Moon Rising table: legal for 12
     * because the Godfather takes its `[-1 Outsider]`.
     */
    private val table = listOf(
        "Ana" to "professor",
        "Bo" to "grandmother",
        "Cai" to "devilsadvocate",
        "Dee" to "gossip",
        "Eve" to "sailor",
        "Fay" to "chambermaid",
        "Gus" to "exorcist",
        "Hal" to "fool",
        "Ivy" to "tealady",
        "Jo" to "goon",
        "Kit" to "godfather",
        "Lex" to "zombuul",
    )

    /** Seated, with the two setup rows this table owes already answered. */
    private fun seated(): GameState {
        var state = GameActions.newGame(bmr, table.map { it.first })
        for ((name, characterId) in table) {
            val id = state.players.first { it.name == name }.id
            state = Seats.assignCharacter(state, id, characterId)
        }
        state = Effects.addReminder(
            state,
            seat(state, "Hal"),
            PlacedReminder("grandmother", "Grandchild"),
        )
        return Bluffs.setDemonBluffs(state, listOf("courtier", "minstrel", "moonchild"))
    }

    private fun seat(state: GameState, name: String): Long =
        state.players.first { it.name == name }.id

    // ------------------------------------------------------------------
    // SETUP
    // ------------------------------------------------------------------

    @Test
    fun `an empty game is blocked by its setup requirements`() {
        val state = GameActions.newGame(bmr, (1..8).map { "P$it" })
        val request = PhaseFlow.request(state, lookup)

        val blocked = request as? PhaseRequest.Blocked
        assertNotNull("an unassigned bag must block: $request", blocked)
        assertEquals(PhaseFlow.TITLE_SETUP, blocked!!.title)
        assertTrue("the blockers are named", blocked.items.all { it.text.isNotBlank() })
        assertEquals(
            SetupRequirements.unmet(state, lookup).count { it.blocking },
            blocked.items.size,
        )
    }

    @Test
    fun `a legal table advances straight into the first night`() {
        val state = seated()
        assertTrue(
            "the fixture must be a legal setup: " +
                SetupRequirements.unmet(state, lookup).filter { it.blocking }.map { it.problem },
            SetupRequirements.unmet(state, lookup).none { it.blocking },
        )
        assertEquals(PhaseRequest.Advance, PhaseFlow.request(state, lookup))
    }

    // ------------------------------------------------------------------
    // NIGHT — the acceptance criterion
    // ------------------------------------------------------------------

    /** Night 2, with someone executed on day 1 so the Zombuul's step is gated off. */
    private fun nightTwoWithASkippedStep(): GameState {
        var state = Phases.advancePhase(seated(), lookup) // night 1
        state = Phases.advancePhase(state, lookup) // day 1
        state = Deaths.attempt(
            state,
            lookup,
            seat(state, "Dee"),
            KillCause(DeathCause.EXECUTION),
        ).state
        return Phases.advancePhase(state, lookup) // night 2
    }

    @Test
    fun `the night blocks on unticked required steps and names only those`() {
        val state = nightTwoWithASkippedStep()
        assertEquals(Phase.NIGHT, state.phase)

        val plan = NightPlan.build(state, lookup)
        val skipped = plan.steps.filter { it.gate is StepGate.Skip }
        assertTrue(
            "the fixture needs a gated-off row: ${plan.steps.map { it.key.token to it.gate }}",
            skipped.isNotEmpty(),
        )

        val blocked = PhaseFlow.request(state, lookup) as? PhaseRequest.Blocked
        assertNotNull("unticked required steps must block", blocked)
        assertEquals(PhaseFlow.TITLE_NIGHT, blocked!!.title)
        assertEquals(plan.unfinished(state.nightStepsDone).size, blocked.items.size)

        // Not one Skip row is on the list, by title or by key.
        for (step in skipped) {
            assertTrue(
                "a skipped step must never block dawn: ${step.title}",
                blocked.items.none { it.key == "night-step:${step.key.token}" },
            )
        }
    }

    @Test
    fun `ticking every required step opens the dawn card while skips stay unticked`() {
        val state = nightTwoWithASkippedStep()
        val plan = NightPlan.build(state, lookup)
        val required = plan.steps.filter { it.required }.map { it.key.token }.toSet()
        val skipped = plan.steps.filter { it.gate is StepGate.Skip }

        val done = state.copy(nightStepsDone = required)
        // The gated-off rows are deliberately NOT ticked.
        assertTrue(
            "the fixture must still hold an unticked skip",
            skipped.any { it.key.token !in done.nightStepsDone },
        )

        val request = PhaseFlow.request(done, lookup)
        val dawn = request as? PhaseRequest.ConfirmDawn
        assertNotNull("dawn opens with only skips outstanding: $request", dawn)
        assertEquals(BriefingSlot.DAWN, dawn!!.briefing.slot)
        assertEquals(done.cycle, dawn.briefing.cycle)
    }

    // ------------------------------------------------------------------
    // DAY
    // ------------------------------------------------------------------

    @Test
    fun `the day always confirms dusk and carries the briefing and the advisories`() {
        var state = Phases.advancePhase(seated(), lookup) // night 1
        state = Phases.advancePhase(state, lookup) // day 1

        val request = PhaseFlow.request(state, lookup)
        val dusk = request as? PhaseRequest.ConfirmDusk
        assertNotNull("the day never blocks — it confirms: $request", dusk)
        assertEquals(BriefingSlot.DUSK, dusk!!.briefing.slot)
        // Nobody died on day 1, and this table holds a Zombuul.
        assertTrue(
            "the conditional wake reaches the sheet: ${dusk.briefing.items.map { it.text }}",
            dusk.briefing.items.any { "the Zombuul kills tonight" in it.text },
        )
        assertEquals(
            WinCheck.duskCheck(state, lookup),
            dusk.advisories,
        )
    }

    // ------------------------------------------------------------------
    // DUSK SHEET BUTTONS — playtest C-16
    // ------------------------------------------------------------------

    @Test
    fun `with nobody on the block the primary records the no-execution and says so`() {
        val label = DuskActions.confirmLabel(nextCycle = 4, onBlockName = null, executionSpent = false)
        assertEquals("NO EXECUTION — BEGIN NIGHT 4 →", label)
        assertTrue(
            "the primary is the record (lead D30)",
            DuskActions.confirmRecordsNoExecution(onBlockName = null, executionSpent = false),
        )
    }

    @Test
    fun `beginning the night without a record is offered, and is the secondary`() {
        assertEquals(
            "Begin night without recording",
            DuskActions.secondaryLabel(onBlockName = null, executionSpent = false),
        )
        assertFalse(
            "the secondary must NOT imply a no-execution",
            DuskActions.secondaryRecordsNoExecution(onBlockName = null, executionSpent = false),
        )
    }

    @Test
    fun `with someone on the block the primary executes and the secondary records no execution`() {
        assertEquals(
            "EXECUTE BEA & BEGIN NIGHT",
            DuskActions.confirmLabel(nextCycle = 3, onBlockName = "Bea", executionSpent = false),
        )
        assertFalse(
            "an execution is not a no-execution",
            DuskActions.confirmRecordsNoExecution(onBlockName = "Bea", executionSpent = false),
        )
        assertEquals("No execution", DuskActions.secondaryLabel("Bea", executionSpent = false))
        assertTrue(DuskActions.secondaryRecordsNoExecution("Bea", executionSpent = false))
    }

    // ------------------------------------------------------------------
    // ENDINGS — one decision, wherever an advisory surfaces (C2-1, C2-2)
    // ------------------------------------------------------------------

    @Test
    fun `an ending advisory is offered, a briefing advisory is not`() {
        val vortox = WinCheck.Advisory(
            goodWins = false,
            reason = "No execution today and the Vortox is alive and sober — evil wins.",
            ruleId = WinCheck.RULE_VORTOX_DUSK,
            blocking = true,
        )
        assertTrue("the dusk sheet must offer it, not print it", endsTheGame(vortox))
        assertFalse(declaredWinner(vortox))
        assertEquals("Declare evil victory", declareLabel(vortox))

        val mayor = WinCheck.Advisory(
            goodWins = true,
            reason = "Three players live and nobody was executed — the Mayor wins for good.",
            ruleId = WinCheck.RULE_MAYOR_DUSK,
            blocking = true,
        )
        assertTrue(endsTheGame(mayor))
        assertTrue(declaredWinner(mayor))
        assertEquals("Declare good victory", declareLabel(mayor))

        // The Zombuul's row is a briefing: no winner, so no declare button.
        val zombuul = WinCheck.Advisory(
            goodWins = null,
            reason = "Nobody died today — the Zombuul kills tonight.",
            ruleId = WinCheck.RULE_ZOMBUUL_NIGHT,
        )
        assertFalse("a conditional wake does not end a game", endsTheGame(zombuul))
    }

    @Test
    fun `the dusk briefing carries the same advisory the sheet renders, keyed for dedupe`() {
        // C2-2: the sheet rendered `advisories` AND the briefing line the same
        // advisory produced. Both are keyed on `ruleId` via BriefingItem.sourceId,
        // which is what the sheet filters on.
        var state = Phases.advancePhase(seated(), lookup) // night 1
        state = Phases.advancePhase(state, lookup) // day 1
        val dusk = PhaseFlow.request(state, lookup) as PhaseRequest.ConfirmDusk
        val ruleIds = WinCheck.duskCheck(state, lookup).map { it.ruleId }.toSet()
        assertTrue(ruleIds.isNotEmpty())
        assertTrue(
            "every advisory reaches the briefing under its own ruleId: " +
                dusk.briefing.items.map { it.sourceId to it.text },
            ruleIds.all { id -> dusk.briefing.items.any { it.sourceId == id } },
        )
    }

    @Test
    fun `an owed exile is named on the primary without disturbing the three D77 labels`() {
        // With nothing owed the labels are byte-identical to D77's.
        assertEquals(
            "NO EXECUTION — BEGIN NIGHT 4 →",
            DuskActions.confirmLabel(4, onBlockName = null, executionSpent = false),
        )
        // C2-3: the exile the table voted for is named, and the record it is
        // about to write is still readable behind it.
        assertEquals(
            "EXILE BEGGED · NO EXECUTION — BEGIN NIGHT 4 →",
            DuskActions.confirmLabel(4, null, executionSpent = false, exileName = "Begged"),
        )
        assertEquals(
            "EXILE BEGGED · EXECUTE BEA & BEGIN NIGHT",
            DuskActions.confirmLabel(4, "Bea", executionSpent = false, exileName = "Begged"),
        )
        assertEquals(
            "EXILE BEGGED · BEGIN NIGHT 4 →",
            DuskActions.confirmLabel(4, null, executionSpent = true, exileName = "Begged"),
        )
        // The exile is not the day's execution: it never changes what is recorded.
        assertTrue(DuskActions.confirmRecordsNoExecution(onBlockName = null, executionSpent = false))
    }

    @Test
    fun `a day that already has its record only begins the night`() {
        assertEquals(
            "BEGIN NIGHT 5 →",
            DuskActions.confirmLabel(nextCycle = 5, onBlockName = null, executionSpent = true),
        )
        assertFalse(DuskActions.confirmRecordsNoExecution(onBlockName = null, executionSpent = true))
        assertNull(
            "nothing left to record, so no second button",
            DuskActions.secondaryLabel(onBlockName = null, executionSpent = true),
        )
    }
}
