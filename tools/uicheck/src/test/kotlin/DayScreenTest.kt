package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Briefing
import com.clocktower.engine.BriefingItem
import com.clocktower.engine.BriefingKind
import com.clocktower.engine.BriefingSlot
import com.clocktower.engine.Briefings
import com.clocktower.engine.Character
import com.clocktower.engine.DayRules
import com.clocktower.engine.Effects
import com.clocktower.engine.Execution
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Ledger
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Nomination
import com.clocktower.engine.NominationResult
import com.clocktower.engine.Phases
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Script
import com.clocktower.engine.Seats
import com.clocktower.engine.TriggerKind
import com.clocktower.engine.Verdict
import com.clocktower.grimoire.ui.components.TimerFormat
import com.clocktower.grimoire.ui.components.TimerState
import com.clocktower.grimoire.ui.screens.day.DayModel
import com.clocktower.grimoire.ui.screens.day.DayStage
import com.clocktower.grimoire.ui.screens.day.NominationModel
import com.clocktower.grimoire.ui.screens.day.SaidModel
import com.clocktower.grimoire.ui.screens.day.SeatPick
import com.clocktower.grimoire.ui.screens.day.StageTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP9's acceptance, measured on the pure half of the day screen.
 *
 * Every model in `ui/screens/day/` is plain Kotlin precisely so it can be
 * tested without a composition; this lives in `tools/uicheck` because
 * `:engine`'s test source set cannot see `app/`.
 * Run with `./gradlew -p tools/uicheck test`.
 */
class DayScreenTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb: Script = data.builtInScripts().first { it.id == "tb" }

    /** A plain, legal Trouble Brewing table — nothing exotic in play. */
    private val table = listOf(
        "Ana" to "washerwoman",
        "Bo" to "librarian",
        "Cai" to "investigator",
        "Dee" to "chef",
        "Eve" to "empath",
        "Fay" to "fortuneteller",
        "Gus" to "undertaker",
        "Hal" to "monk",
        "Ivy" to "butler",
        "Jo" to "saint",
        "Kit" to "poisoner",
        "Lex" to "imp",
    )

    private fun seated(): GameState {
        var state = GameActions.newGame(tb, table.map { it.first })
        for ((name, characterId) in table) {
            state = Seats.assignCharacter(state, seat(state, name), characterId)
        }
        return state
    }

    /** Day 1, with the night run through so the phase is right. */
    private fun day(): GameState {
        val night = Phases.advancePhase(seated(), lookup)
        return Phases.advancePhase(night, lookup)
    }

    private fun seat(state: GameState, name: String): Long =
        state.players.first { it.name == name }.id

    private fun emptyBriefing(slot: BriefingSlot = BriefingSlot.DAY_START) =
        Briefing(slot = slot, cycle = 1, items = emptyList())

    // ------------------------------------------------------------------
    // "What was said" — works with NOTHING in play (the headline request)
    // ------------------------------------------------------------------

    @Test
    fun `a statement records in a game with nothing in play`() {
        val state = day()
        val bo = seat(state, "Bo")
        // Two taps and a sentence: the speaker, then the text.
        val after = Ledger.statement(state, bo, Ledger.Sources.CLAIM, "Fay is the Imp")

        val rows = SaidModel.rows(after, lookup, after.cycle)
        assertEquals(1, rows.size)
        assertEquals(bo, rows.first().speakerId)
        assertTrue("the line quotes the words: ${rows.first().line}", rows.first().line.contains("Fay is the Imp"))
        assertTrue("the speaker is named: ${rows.first().line}", rows.first().line.startsWith("Bo"))
    }

    @Test
    fun `a plain claim asks for no verdict and a character-sourced statement does`() {
        val state = day()
        val bo = seat(state, "Bo")
        var after = Ledger.statement(state, bo, Ledger.Sources.CLAIM, "Fay is the Imp")
        after = Ledger.statement(after, bo, "gossip", "Two Outsiders have died")

        val rows = SaidModel.rows(after, lookup, after.cycle)
        assertEquals(2, rows.size)
        assertFalse("a plain claim is neither true nor false yet", rows[0].wantsVerdict)
        assertTrue("a Gossip statement is judged", rows[1].wantsVerdict)
        assertEquals(Verdict.UNJUDGED, rows[1].verdict)
    }

    @Test
    fun `the zero-typing claim path needs no keyboard`() {
        val state = day()
        val empath = data.character("empath")!!
        val text = SaidModel.claimText(empath.name)
        assertEquals("Claims to be the Empath", text)

        val candidates = SaidModel.claimCandidates(state, lookup)
        assertTrue("the grid offers the whole script", candidates.size >= tb.characterIds.size / 2)
        // In-play characters come first so the common claim is the nearest tap.
        val inPlayIds = state.seats.mapNotNull { it.characterId }.toSet()
        assertTrue(
            "in-play characters lead the grid: ${candidates.take(3).map { it.id }}",
            candidates.first().id in inPlayIds,
        )
    }

    @Test
    fun `the smart source default is the collect list, never a hard-coded id`() {
        val state = day()
        val gus = seat(state, "Gus")
        // Nothing owed: a plain claim.
        assertEquals(Ledger.Sources.CLAIM, SaidModel.defaultSource(state, gus, emptyList()))
        // The engine's collect list is owed this seat's own ability.
        assertEquals("undertaker", SaidModel.defaultSource(state, gus, listOf("undertaker")))
        // Somebody else's owed row must not steal the default.
        assertEquals(Ledger.Sources.CLAIM, SaidModel.defaultSource(state, gus, listOf("gossip")))
    }

    @Test
    fun `add and another walks clockwise past the dead`() {
        var state = day()
        val bo = seat(state, "Bo")
        val cai = seat(state, "Cai")
        state = state.updatePlayer(cai) { it.copy(alive = false) }

        assertEquals(seat(state, "Dee"), SaidModel.nextSpeaker(state, bo))
    }

    @Test
    fun `the collect list drives the source chips`() {
        val briefing = Briefing(
            slot = BriefingSlot.DAY_START,
            cycle = 1,
            items = listOf(
                BriefingItem(
                    key = "collect:gossip",
                    kind = BriefingKind.TODO_ASK,
                    text = "No Gossip statement recorded today.",
                    actionId = "${Briefings.ACTION_RECORD}gossip",
                ),
            ),
        )
        assertEquals(listOf("gossip"), DayModel.collectSources(briefing))
        assertEquals(listOf("gossip", Ledger.Sources.CLAIM), DayModel.statementSources(briefing))
        // With nothing owed, the composer still offers the plain claim.
        assertEquals(listOf(Ledger.Sources.CLAIM), DayModel.statementSources(emptyBriefing()))
    }

    @Test
    fun `every ledger kind the stage renders is one of the five, never IMPAIRMENT_SPAN`() {
        assertFalse(LedgerKind.IMPAIRMENT_SPAN in SaidModel.KINDS)
        assertEquals(
            listOf(
                LedgerKind.STATEMENT,
                LedgerKind.PRIVATE,
                LedgerKind.ANNOUNCE,
                LedgerKind.RULING,
                LedgerKind.NOTE,
            ),
            SaidModel.KINDS,
        )
    }

    // ------------------------------------------------------------------
    // Nomination — two taps on the ring
    // ------------------------------------------------------------------

    @Test
    fun `the ring is two taps and never hard-blocks a seat`() {
        val state = day()
        val bo = seat(state, "Bo")
        val fay = seat(state, "Fay")

        val firstTap = NominationModel.ring(state, lookup, null, null)
        assertEquals(state.seats.size, firstTap.size)
        assertTrue("tap one picks a nominator", firstTap.all { it.pick == SeatPick.NONE })

        val secondTap = NominationModel.ring(state, lookup, bo, null)
        assertEquals(SeatPick.NOMINATOR, secondTap.first { it.id == bo }.pick)

        val done = NominationModel.ring(state, lookup, bo, fay)
        assertEquals(SeatPick.NOMINATOR, done.first { it.id == bo }.pick)
        assertEquals(SeatPick.NOMINEE, done.first { it.id == fay }.pick)
    }

    @Test
    fun `a dead seat is dimmed with a reason, not removed`() {
        var state = day()
        val cai = seat(state, "Cai")
        state = state.updatePlayer(cai) { it.copy(alive = false) }

        val ring = NominationModel.ring(state, lookup, null, null)
        val dead = ring.first { it.id == cai }
        assertFalse("a dead seat may not nominate", dead.allowed)
        assertTrue("and the storyteller is told why: '${dead.reason}'", dead.reason.isNotBlank())
        // Still present, still tappable — [Allow anyway] is the escape hatch.
        assertEquals(state.seats.size, ring.size)
    }

    @Test
    fun `ring positions run clockwise from the top and stay inside the box`() {
        val positions = (0 until 12).map { NominationModel.position(it, 12) }
        val (topX, topY) = positions.first()
        assertEquals(0.5f, topX, 0.001f)
        assertTrue("seat 1 sits at the top: $topY", topY < 0.5f)
        // Clockwise: seat 4 of 12 is a quarter turn round, on the right.
        assertTrue("seat 4 is on the right: ${positions[3]}", positions[3].first > 0.5f)
        assertTrue(
            "every seat stays inside the box: $positions",
            positions.all { it.first in 0f..1f && it.second in 0f..1f },
        )
        // Degenerate table: never divide by zero.
        assertEquals(0.5f to 0.5f, NominationModel.position(0, 0))
    }

    @Test
    fun `the ring shrinks its seats rather than overlapping them`() {
        val phone = 360f
        val seven = NominationModel.seatWidthDp(7, phone)
        val twelve = NominationModel.seatWidthDp(12, phone)
        val twenty = NominationModel.seatWidthDp(20, phone)

        assertTrue("a bigger table means narrower seats", seven > twelve && twelve > twenty)
        assertTrue("never below the text floor: $twenty", twenty >= NominationModel.MIN_SEAT_DP)
        assertTrue("never absurd at 7: $seven", seven <= NominationModel.MAX_SEAT_DP)

        // No overlap at 12 — the arc each seat owns is at least its width.
        val circumference = 2 * Math.PI * NominationModel.RADIUS * phone
        assertTrue(
            "12 seats fit round the ring: $twelve vs ${circumference / 12}",
            twelve <= circumference / 12 + 0.01,
        )
        // Degenerate tables must not divide by zero.
        assertEquals(NominationModel.MAX_SEAT_DP, NominationModel.seatWidthDp(0, phone), 0.01f)
    }

    @Test
    fun `hands are counted clockwise from the nominee's left`() {
        val state = day()
        val fay = seat(state, "Fay")
        val order = NominationModel.voteOrder(state, fay).map { it.name }

        assertEquals(state.seats.size, order.size)
        assertEquals("Gus", order.first())
        assertEquals("Fay", order.last())
    }

    @Test
    fun `the nomination check reaches the day screen with its triggers`() {
        val state = day()
        val check = DayRules.checkNomination(state, lookup, seat(state, "Bo"), seat(state, "Fay"))

        assertTrue("a plain nomination is legal", check.legal)
        // The Goblin CHOICE row is offered for every seated nominee: that IS
        // the "Claims to be the Goblin" affordance, and it is data, not a
        // character-id branch in the screen.
        val goblin = check.triggers.firstOrNull { it.sourceId == "goblin" }
        assertNotNull("the goblin claim is offered: ${check.triggers.map { it.sourceId }}", goblin)
        assertEquals(TriggerKind.CHOICE, goblin!!.kind)
        assertTrue(
            "and it carries the options the card renders as buttons",
            goblin.options.any { it.id == DayRules.OPTION_APPLY },
        )
    }

    @Test
    fun `the nomination briefing is computed for the tapped pair, not the recorded one`() {
        var state = day()
        val bo = seat(state, "Bo")
        val cai = seat(state, "Cai")
        val fay = seat(state, "Fay")
        state = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = bo, nomineeId = cai),
        )

        // The slot default follows the LAST RECORDED pair …
        val slotDefault = Briefings.at(state, lookup, BriefingSlot.NOMINATION)
        // … while the ring asks about the pair under the thumb.
        val tapped = Briefings.forNomination(state, lookup, cai, fay)
        assertTrue(
            "the two must be able to differ, or the Virgin can never fire",
            slotDefault.items.map { it.key } != tapped.items.map { it.key } ||
                tapped.items.isEmpty(),
        )
    }

    // ------------------------------------------------------------------
    // Voting
    // ------------------------------------------------------------------

    @Test
    fun `the vote view uses the engine's weighted tally`() {
        var state = day()
        val fay = seat(state, "Fay")
        val bo = seat(state, "Bo")
        val ana = seat(state, "Ana")
        // A Bureaucrat's official token: this hand counts three times.
        state = Effects.addReminder(state, bo, PlacedReminder("bureaucrat", "3 Votes"))

        val view = NominationModel.voteView(state, lookup, ana, fay, setOf(bo, ana))
        assertEquals(4, view.tally)
        assertEquals(3, view.weights[bo])
        assertTrue("the reason is shown", view.reasons.any { it.contains("3 times") })
    }

    @Test
    fun `secret voting hides the tally and the block on the whole tab`() {
        var state = day()
        // A living, sober Organ Grinder switches the day into secret voting.
        state = Seats.assignCharacter(state, seat(state, "Dee"), "organgrinder")
        assertTrue(DayRules.secretVoting(state, lookup))

        val stats = DayModel.stats(state, lookup)
        assertTrue("the stat strip goes secret", stats.secret)
        assertFalse("and drops the tally to beat: '${stats.detail}'", stats.detail.contains("to beat"))

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            setOf(seat(state, "Bo")),
        )
        assertTrue(view.secret)
        assertEquals("Lock in silently", NominationModel.lockInLabel(view))
    }

    @Test
    fun `an exile is never secret and never spends a ghost vote`() {
        var state = day()
        state = Seats.assignCharacter(state, seat(state, "Dee"), "organgrinder")
        state = Seats.assignCharacter(state, seat(state, "Jo"), "beggar", isTraveller = true)

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Jo"),
            setOf(seat(state, "Bo")),
        )
        assertTrue("a Traveller nominee is an exile call", view.isExile)
        assertFalse("abilities do not apply to an exile", view.secret)
        assertFalse(view.rules.spendsGhostVotes)
    }

    @Test
    fun `the lock-in label states the result, not the verb`() {
        val state = day()
        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            emptySet(),
        )
        assertTrue(
            "the button says what happens: '${NominationModel.lockInLabel(view)}'",
            NominationModel.lockInLabel(view).contains("SAFE"),
        )
    }

    @Test
    fun `a tie names names and the number to beat`() {
        var state = day()
        val fay = seat(state, "Fay")
        val gus = seat(state, "Gus")
        val voters = state.alivePlayers.take(6).map { it.id }
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = seat(state, "Ana"),
                nomineeId = fay,
                voterIds = voters,
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = seat(state, "Bo"),
                nomineeId = gus,
                voterIds = voters,
                result = NominationResult.TIED,
            ),
        )

        val line = DayModel.tieLine(state)
        assertTrue("the tie names both: '$line'", line.contains("Fay") && line.contains("Gus"))
        assertTrue("and the number to beat: '$line'", line.contains("to beat it"))
        assertNull("nobody is on the block after a tie", DayRules.aboutToDie(state))
    }

    // ------------------------------------------------------------------
    // The timeline and the dusk hand-off
    // ------------------------------------------------------------------

    @Test
    fun `the timeline is the five stages in the order the day is lived`() {
        val state = day()
        val rows = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
        assertEquals(
            listOf(
                DayStage.DAWN,
                DayStage.BRIEFING,
                DayStage.SAID,
                DayStage.NOMINATIONS,
                DayStage.DUSK,
            ),
            rows.map { it.stage },
        )
    }

    @Test
    fun `dusk asks about no execution while nobody is on the block`() {
        val state = day()
        val rows = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
        val dusk = rows.first { it.stage == DayStage.DUSK }

        assertFalse("the day is not closed yet", dusk.complete)
        assertEquals(StageTone.ACTION, dusk.tone)
        assertTrue("and it says so: '${dusk.summary}'", dusk.summary.contains("no execution", true))
    }

    @Test
    fun `a recorded no-execution closes the day`() {
        val state = Execution.noExecution(day())
        assertEquals(
            ExecutionOutcome.NO_EXECUTION,
            DayRules.executionToday(state)?.outcome,
        )
        val dusk = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
            .first { it.stage == DayStage.DUSK }
        assertTrue("dusk is done", dusk.complete)
        assertTrue(DayRules.nominationsClosed(state, lookup))
    }

    @Test
    fun `an execution that kills nobody still closes the day`() {
        val state = day()
        val fay = seat(state, "Fay")
        // "Executed — but they don't die", the first-class button.
        val after = Execution.execute(
            state,
            lookup,
            fay,
            outcome = ExecutionOutcome.SURVIVED,
        )
        val record = DayRules.executionToday(after)
        assertNotNull("the execution is recorded even though nobody died", record)
        assertEquals(ExecutionOutcome.SURVIVED, record!!.outcome)
        assertTrue("and the day is spent", DayRules.executionSpent(after))
        assertTrue(after.player(fay)!!.alive)
    }

    @Test
    fun `the auto-expanded stage is the first one still asking for something`() {
        val state = day()
        // Nothing owed anywhere: the day's business is nominations.
        val quiet = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
        assertEquals(DayStage.NOMINATIONS, DayModel.autoExpanded(quiet))

        // A statement the engine says is still uncollected pulls the day back
        // to "What was said" — the nudge the friction log asked for.
        val owed = Briefing(
            slot = BriefingSlot.DAY_START,
            cycle = state.cycle,
            items = listOf(
                BriefingItem(
                    key = "collect:gossip",
                    kind = BriefingKind.TODO_ASK,
                    text = "No Gossip statement recorded today.",
                    actionId = "${Briefings.ACTION_RECORD}gossip",
                ),
            ),
        )
        val rows = DayModel.stages(state, lookup, null, owed, emptySet())
        assertEquals(DayStage.BRIEFING, DayModel.autoExpanded(rows))
        assertFalse(rows.first { it.stage == DayStage.SAID }.complete)

        val closed = Execution.noExecution(state)
        val closedRows = DayModel.stages(closed, lookup, null, emptyBriefing(), emptySet())
        assertTrue(
            "a fully closed day settles on a stage rather than crashing",
            DayModel.autoExpanded(closedRows) in DayStage.entries,
        )
    }

    @Test
    fun `the stat strip carries the ghost-vote count the grimoire tab used to hide`() {
        var state = day()
        val cai = seat(state, "Cai")
        state = state.updatePlayer(cai) { it.copy(alive = false) }

        val stats = DayModel.stats(state, lookup)
        assertTrue("ghost votes are on the day tab: '${stats.detail}'", stats.detail.contains("ghost"))
        assertTrue(stats.detail.contains("alive"))
        assertEquals("Day ${state.cycle}", stats.headline)
    }

    // ------------------------------------------------------------------
    // Timer — finding 40 cannot regress
    // ------------------------------------------------------------------

    @Test
    fun `the timer deadline lives outside composition and survives`() {
        val timer = TimerState()
        assertTrue(timer.idle)
        timer.start(300)
        assertTrue(timer.running)
        // The whole point: nothing about a tab, a composable or a saver is
        // involved, so leaving composition cannot reset it.
        val remaining = timer.remainingMs(com.clocktower.engine.Time.epochMillis())
        assertTrue("about five minutes left: $remaining", remaining in 299_000..300_000)
    }

    @Test
    fun `pause, plus one minute and reset are four distinct actions`() {
        val timer = TimerState()
        timer.start(120)
        timer.pause()
        assertTrue(timer.paused)
        assertFalse(timer.running)

        timer.addSeconds(60)
        assertTrue("a paused timer still grows", timer.pausedRemainingMs > 150_000)

        timer.resume()
        assertTrue(timer.running)

        timer.reset()
        assertEquals(120, timer.lastPresetSec)

        timer.stop()
        assertTrue(timer.idle)
    }

    @Test
    fun `the clock reads down and ticks slowly until the last ten seconds`() {
        assertEquals("5:00", TimerFormat.clock(300_000))
        assertEquals("0:01", TimerFormat.clock(1))
        assertEquals("0:00", TimerFormat.clock(-5_000))
        assertEquals(1_000L, TimerFormat.tickMs(60_000))
        assertEquals(250L, TimerFormat.tickMs(9_000))
        assertEquals("1m", TimerFormat.presetLabel(60))
        assertEquals("8m", TimerFormat.presetLabel(480))
    }

    @Test
    fun `an expired timer reads TIME in the bottom bar`() {
        val timer = TimerState()
        timer.start(1)
        val past = com.clocktower.engine.Time.epochMillis() + 5_000
        assertEquals("TIME", TimerFormat.barLabel(timer, past))
    }

    // ------------------------------------------------------------------
    // The screen's own invariants
    // ------------------------------------------------------------------

    @Test
    fun `the day package names no character id`() {
        val ids = data.characters.map { it.id }.toSet()
        val offenders = mutableListOf<String>()
        for (relative in DAY_SOURCES) {
            val file = java.io.File(repoRoot(), relative)
            val text = file.takeIf { it.isFile }?.readText() ?: continue
            text.lines().withIndex()
                .filterNot { (_, line) ->
                    val t = line.trimStart()
                    t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                }
                .forEach { (index, line) ->
                    STRING_LITERAL.findAll(line)
                        .map { it.groupValues[1] }
                        .filter { it in ids }
                        .forEach { offenders += "$relative:${index + 1}  $it" }
                }
        }
        assertTrue(
            "per-character behaviour belongs in engine/rules/ (I1):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** The directory holding `settings.gradle.kts`; tests run from `tools/uicheck`. */
    private fun repoRoot(): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile.normalize()
        while (dir != null) {
            if (java.io.File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("no settings.gradle.kts above ${java.io.File(".").absolutePath}")
    }

    private companion object {
        val STRING_LITERAL = Regex("\"([^\"\\\\\\n]*)\"")
        val DAY_SOURCES = listOf(
            "app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayModel.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayCards.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/SaidModel.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/SaidSheet.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationModel.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/day/ExecutionSheet.kt",
            "app/src/main/java/com/clocktower/grimoire/ui/components/Timer.kt",
        )
    }
}
