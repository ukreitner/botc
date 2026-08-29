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
import com.clocktower.engine.ExecutionConsequence
import com.clocktower.engine.ExecutionOutcome
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.HouseRules
import com.clocktower.engine.KillOutcome
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
import com.clocktower.engine.Voting
import com.clocktower.grimoire.ui.components.TimerFormat
import com.clocktower.grimoire.ui.components.TimerState
import com.clocktower.grimoire.ui.screens.day.DayModel
import com.clocktower.grimoire.ui.screens.day.DayStage
import com.clocktower.grimoire.ui.screens.day.NominationModel
import com.clocktower.grimoire.ui.screens.day.SaidModel
import com.clocktower.grimoire.ui.screens.day.SeatPick
import com.clocktower.grimoire.ui.screens.day.verdictLabel
import com.clocktower.grimoire.ui.screens.day.previewText
import com.clocktower.grimoire.ui.screens.day.visibleConsequences
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
        val w = PHONE_WIDTH_DP
        val h = NominationModel.ringHeightDp(12, w)
        val positions = (0 until 12).map { NominationModel.seatCentreDp(it, 12, w) }
        val (topX, topY) = positions.first()
        assertEquals(w / 2f, topX, 0.01f)
        assertTrue("seat 1 sits at the top: $topY", topY < h / 2f)
        // Clockwise: seat 4 of 12 is a quarter turn round, on the right.
        assertTrue("seat 4 is on the right: ${positions[3]}", positions[3].first > w / 2f)
        assertTrue(
            "every seat stays inside the box: $positions",
            positions.all { it.first in 0f..w && it.second in 0f..h },
        )
        // Degenerate table: never divide by zero.
        assertEquals(w / 2f, NominationModel.seatCentreDp(0, 0, w).first, 0.01f)
    }

    // ------------------------------------------------------------------
    // The measured half: the ring may not overlap anything (playtest D-6)
    // ------------------------------------------------------------------

    /** The reference phone the app is judged on, in dp. */
    private val PHONE_WIDTH_DP = 411f

    private fun hitRects(
        count: Int,
        w: Float = PHONE_WIDTH_DP,
        maxRy: Float = NominationModel.MAX_RADIUS_Y_DP,
    ): List<FloatArray> {
        val seatW = NominationModel.seatWidthDp(count, w, maxRy)
        return (0 until count).map { i ->
            val (cx, cy) = NominationModel.seatCentreDp(i, count, w, maxRy)
            floatArrayOf(
                cx - seatW / 2f,
                cy - NominationModel.SEAT_HIT_DP / 2f,
                cx + seatW / 2f,
                cy + NominationModel.SEAT_HIT_DP / 2f,
            )
        }
    }

    private fun overlaps(a: FloatArray, b: FloatArray): Boolean =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    @Test
    fun `no two seats on the nomination ring share a pixel of hit target`() {
        // `ui.py audit` reported ten overlapping clickable pairs on this
        // screen, the worst at 41 %: two 48 dp targets 38 dp apart. Tapping a
        // vote landed on the seat behind it and silently re-picked the nominee.
        for (n in 5..16) {
            val rects = hitRects(n)
            for (i in rects.indices) {
                for (j in i + 1 until rects.size) {
                    assertFalse(
                        "at $n seats, seat ${i + 1} ${rects[i].toList()} overlaps " +
                            "seat ${j + 1} ${rects[j].toList()}",
                        overlaps(rects[i], rects[j]),
                    )
                }
            }
        }
    }

    @Test
    fun `the ring keeps a clear strip between its lowest seat and the list below`() {
        for (n in 5..16) {
            val height = NominationModel.ringHeightDp(n, PHONE_WIDTH_DP)
            val lowest = hitRects(n).maxOf { it[3] }
            assertTrue(
                "at $n seats the lowest hit target ends at $lowest of a $height dp ring",
                lowest <= height - NominationModel.RING_GAP_DP + 0.01f,
            )
            val highest = hitRects(n).minOf { it[1] }
            assertTrue("at $n seats a seat is clipped off the top: $highest", highest >= -0.01f)
        }
    }

    @Test
    fun `the ring grows only as much as the table needs, and never owns the screen`() {
        val small = NominationModel.ringHeightDp(8, PHONE_WIDTH_DP)
        val large = NominationModel.ringHeightDp(15, PHONE_WIDTH_DP)
        assertTrue("a 15-seat ring needs more room than an 8-seat one: $small / $large", large > small)
        assertTrue("and a small table does not waste the screen: $small", small <= 260f)

        // On a short screen the ring is capped rather than pushing the vote
        // panel off the phone — the cap still keeps the strip below it.
        val cramped = NominationModel.maxRadiusYFor(500f)
        val capped = NominationModel.ringHeightDp(15, PHONE_WIDTH_DP, cramped)
        assertTrue("capped to its share of 500 dp: $capped", capped <= 500f * 0.56f)
        val lowest = hitRects(15, PHONE_WIDTH_DP, cramped).maxOf { it[3] }
        assertTrue(
            "and the clear strip survives the cap: $lowest of $capped",
            lowest <= capped - NominationModel.RING_GAP_DP + 0.01f,
        )
    }

    @Test
    fun `the ring shrinks its seats rather than overlapping them`() {
        val phone = 360f
        val seven = NominationModel.seatWidthDp(7, phone)
        val twelve = NominationModel.seatWidthDp(12, phone)
        val fifteen = NominationModel.seatWidthDp(15, phone)

        assertTrue("a bigger table means narrower seats", seven > twelve && twelve > fifteen)
        assertTrue("never below the text floor: $fifteen", fifteen >= NominationModel.MIN_SEAT_DP)
        assertTrue("never absurd at 7: $seven", seven <= NominationModel.MAX_SEAT_DP)
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
        var state = day()

        // C-2: the Goblin CHOICE row is data, not a character-id branch in the
        // screen — but it is only offered where a Goblin can actually claim.
        // On a plain Trouble Brewing table nobody can be one.
        val plain = DayRules.checkNomination(state, lookup, seat(state, "Bo"), seat(state, "Fay"))
        assertTrue("a plain nomination is legal", plain.legal)
        assertNull(
            "no Goblin, no question: ${plain.triggers.map { it.sourceId }}",
            plain.triggers.firstOrNull { it.sourceId == "goblin" },
        )

        state = Seats.assignCharacter(state, seat(state, "Kit"), "goblin")
        val check = DayRules.checkNomination(state, lookup, seat(state, "Bo"), seat(state, "Fay"))
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
    fun `a closed day locks the ring and never discards a vote in silence`() {
        // Playtest C-4: after a Virgin execution every card said "the day is
        // over" while the ring, the chips and a live "Lock in: … SAFE (0 of 5)"
        // stayed up — and locking in cleared the draft and recorded nothing.
        var state = day()
        val fay = seat(state, "Fay")
        state = Execution.execute(state, lookup, fay)
        assertTrue(DayRules.nominationsClosed(state, lookup))

        val reason = DayRules.nominationsClosedReason(state, lookup)
        assertTrue("the reason is in storyteller voice: '$reason'", reason.contains("the day is over"))
        assertTrue("the ring is dead", NominationModel.ringLocked(reason, reopened = false))
        assertFalse("until the ST reopens it", NominationModel.ringLocked(reason, reopened = true))

        val ana = seat(state, "Ana")
        val bo = seat(state, "Bo")
        val check = DayRules.checkNomination(state, lookup, ana, bo)
        assertFalse(check.legal)
        assertTrue(
            "and Lock in refuses rather than swallowing it",
            NominationModel.lockInRefused(check.blockers, force = false),
        )
        assertFalse(NominationModel.lockInRefused(check.blockers, force = true))

        // Why the button must refuse: the engine really does drop it.
        val dropped = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = ana, nomineeId = bo, voterIds = listOf(ana)),
        )
        assertEquals("nothing was recorded", state.nominations.size, dropped.nominations.size)
        val forced = DayRules.record(
            state,
            lookup,
            Nomination(day = state.cycle, nominatorId = ana, nomineeId = bo, voterIds = listOf(ana)),
            force = true,
        )
        assertEquals(state.nominations.size + 1, forced.nominations.size)
    }

    @Test
    fun `an ineligible hand is shown, is not counted, and cannot put anyone on the block`() {
        // Playtest C-1 (P0): the chip was dimmed with a reason AND summed.
        var state = day()
        state = Seats.assignCharacter(state, seat(state, "Jo"), "voudon", isTraveller = true)
        val ana = seat(state, "Ana")
        val jo = seat(state, "Jo")
        val fay = seat(state, "Fay")

        val illegal = NominationModel.voteView(state, lookup, ana, fay, setOf(ana))
        assertEquals("only the Voudon and the dead may vote", 1, illegal.threshold)
        assertTrue("the chip carries its reason", illegal.ineligible.containsKey(ana))
        assertEquals("and the hand adds nothing", 0, illegal.tally)
        assertEquals(NominationResult.SAFE, illegal.result)
        assertTrue(
            "the lock-in label cannot claim a block: '${NominationModel.lockInLabel(illegal)}'",
            NominationModel.lockInLabel(illegal).contains("SAFE"),
        )

        // The Voudon's own hand is the one that counts.
        val legal = NominationModel.voteView(state, lookup, ana, fay, setOf(jo))
        assertEquals(1, legal.tally)
        assertEquals(NominationResult.ABOUT_TO_DIE, legal.result)
    }

    @Test
    fun `an illegal Butler hand is listed as uncounted and left out of the tally`() {
        var state = day()
        val ivy = seat(state, "Ivy") // the Butler
        val dee = seat(state, "Dee")
        state = Effects.addReminder(state, dee, PlacedReminder("butler", "Master"))

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            setOf(ivy, seat(state, "Bo")),
        )
        assertEquals("the Master's hand is down: one hand counts", 1, view.tally)
        assertTrue("and the Butler's hand is called out", view.uncounted.containsKey(ivy))
        assertTrue(
            "in words that do not tell the storyteller to count it: '${view.uncounted[ivy]}'",
            view.uncounted.getValue(ivy).contains("does not count"),
        )
    }

    @Test
    fun `the may-not-vote lines are capped so a Voudon day stays readable`() {
        var state = day()
        state = Seats.assignCharacter(state, seat(state, "Jo"), "voudon", isTraveller = true)
        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            emptySet(),
        )
        val lines = NominationModel.ineligibleLines(view)
        assertTrue("eleven living seats may not vote", view.ineligible.size > 4)
        assertEquals(NominationModel.MAX_INELIGIBLE_LINES + 1, lines.size)
        assertTrue("the tail is summarised: '${lines.last()}'", lines.last().contains("more may not vote"))
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
    fun `the secret-vote house rule closes the same eyes with no Organ Grinder`() {
        // The Organ Grinder is on no base script, so this path only ever runs
        // for a table that turned the house rule on (ux/day-screen §F, C).
        val state = day().copy(houseRules = HouseRules(secretVotes = true))
        assertTrue(DayRules.secretVoting(state, lookup))

        val stats = DayModel.stats(state, lookup)
        assertTrue("the stat strip goes secret", stats.secret)
        assertFalse("no tally to beat is printed: '${stats.detail}'", stats.detail.contains("to beat"))
        assertTrue("and the strip says why: '${stats.voteNote}'", stats.voteNote.contains("house rule"))

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            setOf(seat(state, "Bo"), seat(state, "Cai")),
        )
        assertTrue(view.secret)
        assertEquals("Lock in silently", NominationModel.lockInLabel(view))
        assertEquals("the storyteller still counts the hands they saw", 2, view.tally)
        assertTrue(
            "the eyes-closed line does not invent an Organ Grinder: '${view.secretLine}'",
            view.secretLine.contains("House rule"),
        )
        assertFalse(view.secretLine.contains("Organ Grinder"))
    }

    @Test
    fun `an Organ Grinder still names itself, house rule or not`() {
        var state = day().copy(houseRules = HouseRules(secretVotes = false))
        state = Seats.assignCharacter(state, seat(state, "Dee"), "organgrinder")
        assertTrue("a rule switched off never opens the eyes", DayRules.secretVoting(state, lookup))

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Ana"),
            seat(state, "Fay"),
            emptySet(),
        )
        assertTrue(view.secretLine, view.secretLine.contains("an Organ Grinder is in play"))
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

        // C2-10: the strip printed the standing high-water ("· 5 to beat") one
        // line above the tie line's "6 to beat it". One meaning, one number.
        val toBeat = DayRules.votesToBeat(state)
        assertEquals(DayRules.highestVotesToday(state) + 1, toBeat)
        assertTrue("the tie line uses it: '$line'", line.contains("$toBeat to beat it"))
        val detail = DayModel.stats(state, lookup).detail
        assertTrue("and so does the strip: '$detail'", detail.contains("· $toBeat to beat"))
    }

    @Test
    fun `nothing standing prints no number to beat`() {
        val state = day()
        assertEquals(0, DayRules.votesToBeat(state))
        assertFalse(
            "'${DayModel.stats(state, lookup).detail}'",
            DayModel.stats(state, lookup).detail.contains("to beat"),
        )
    }

    @Test
    fun `the vote panel names both tied players before the tie is recorded`() {
        // Finding 21: in-panel it read "Tie at 4 — Player 10", the stat strip
        // read "Tie at 4 — Player 10 and Player 5". The nomination being
        // counted is not on the record yet, so the panel has to add it.
        var state = day()
        val fay = seat(state, "Fay")
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

        val view = NominationModel.voteView(
            state,
            lookup,
            seat(state, "Bo"),
            seat(state, "Gus"),
            voters.toSet(),
        )
        assertEquals(NominationResult.TIED, view.result)
        assertTrue(
            "both names, in the panel too: '${view.outcomeLine}'",
            view.outcomeLine.contains("Fay") && view.outcomeLine.contains("Gus"),
        )
    }

    @Test
    fun `the block line stops instructing an execution that already happened`() {
        // Finding 13: "On the block: Fay — 6 votes" survived Fay's execution
        // and stood for the rest of the day.
        var state = day()
        val fay = seat(state, "Fay")
        state = DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = seat(state, "Ana"),
                nomineeId = fay,
                voterIds = state.alivePlayers.take(6).map { it.id },
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
        assertTrue(DayModel.stats(state, lookup).blockLine.startsWith("On the block"))

        state = Execution.execute(state, lookup, fay)
        val after = DayModel.stats(state, lookup).blockLine
        assertFalse("the block is history: '$after'", after.startsWith("On the block"))
        assertTrue("and the strip says what happened: '$after'", after.contains("the day is over"))
    }

    @Test
    fun `an empty What was said is not ticked as done`() {
        // Finding 22: a ✓ on an empty list reads as "done", not "nothing owed".
        val state = day()
        val fresh = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
            .first { it.stage == DayStage.SAID }
        assertFalse("nothing has been said yet", fresh.complete)

        val said = Ledger.statement(state, seat(state, "Bo"), Ledger.Sources.CLAIM, "I am the Chef")
        val ticked = DayModel.stages(said, lookup, null, emptyBriefing(), emptySet())
            .first { it.stage == DayStage.SAID }
        assertTrue("one line recorded, nothing owed", ticked.complete)
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

    /** A sober Voudon rewrites the vote, so it must rewrite the strip too. */
    @Test
    fun `the stat strip reads the engine's vote rules under a Voudon`() {
        var state = day()
        // Ana takes a traveller seat as the Voudon; Cai is dead with a ghost
        // vote in hand — under a Voudon no ghost vote is ever spent.
        state = GameActions.assignCharacter(state, seat(state, "Ana"), "voudon", isTraveller = true)
        state = state.updatePlayer(seat(state, "Cai")) { it.copy(alive = false) }

        val rules = DayRules.voteRules(state, lookup, isExile = false)
        assertEquals("the engine says one vote is enough", 1, rules.threshold)
        assertFalse(rules.spendsGhostVotes)

        val stats = DayModel.stats(state, lookup)
        assertTrue("the strip shows the engine's threshold: '${stats.detail}'", stats.detail.contains("1 to execute"))
        assertFalse(
            "no ghost-vote count when none is spent: '${stats.detail}'",
            stats.detail.contains("ghost"),
        )
        assertTrue("and it says why: '${stats.voteNote}'", stats.voteNote.contains("Voudon"))
        assertTrue("naming the one living voter: '${stats.voteNote}'", stats.voteNote.contains("Ana"))
    }

    @Test
    fun `a poisoned Voudon leaves the stat strip on the ordinary threshold`() {
        var state = day()
        val ana = seat(state, "Ana")
        state = GameActions.assignCharacter(state, ana, "voudon", isTraveller = true)
        state = Effects.addReminder(state, ana, PlacedReminder("poisoner", "Poisoned", placedCycle = state.cycle))
        state = state.updatePlayer(seat(state, "Cai")) { it.copy(alive = false) }

        val ordinary = Voting.executionThreshold(state.aliveCountWithTravellers)
        val stats = DayModel.stats(state, lookup)
        assertEquals(
            "a poisoned Voudon changes nothing",
            ordinary,
            DayRules.voteRules(state, lookup, isExile = false).threshold,
        )
        assertTrue("the strip agrees: '${stats.detail}'", stats.detail.contains("$ordinary to execute"))
        assertTrue("and the ghost vote is countable again: '${stats.detail}'", stats.detail.contains("ghost"))
        assertFalse("with no Voudon note: '${stats.voteNote}'", stats.voteNote.contains("Voudon"))
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
    // A recorded line is editable (C2-8)
    // ------------------------------------------------------------------

    @Test
    fun `a recorded row carries the words the edit dialog opens with`() {
        val state = day()
        val bo = seat(state, "Bo")
        val after = Ledger.statement(state, bo, Ledger.Sources.CLAIM, "Fay is the Imp")
        val row = SaidModel.rows(after, lookup, after.cycle).single()
        // The formatted line is for reading; `text` is what gets edited.
        assertEquals("Fay is the Imp", row.text)
        assertTrue(row.line, row.line.contains("“Fay is the Imp”"))

        val edited = Ledger.edit(after, row.entryId) { it.copy(text = "Fay is the Poisoner") }
        assertEquals(
            "Fay is the Poisoner",
            SaidModel.rows(edited, lookup, edited.cycle).single().text,
        )
        assertTrue(Ledger.delete(edited, row.entryId).ledger.none { it.id == row.entryId })
    }

    @Test
    fun `the verdict chips read as words, and every verdict has a label`() {
        assertEquals("✓ true", verdictLabel(Verdict.TRUE))
        assertEquals("✗ false", verdictLabel(Verdict.FALSE))
        assertEquals("? not judged", verdictLabel(Verdict.UNJUDGED))
        for (verdict in Verdict.entries) {
            assertTrue(verdict.name, verdictLabel(verdict).isNotBlank())
        }
    }

    // ------------------------------------------------------------------
    // An exile the table voted for and nobody carried out (C2-3)
    // ------------------------------------------------------------------

    /** Day 1 with Jo as a Beggar, exiled by a passing vote nobody acted on. */
    private fun exileOwed(): GameState {
        var state = day()
        state = Seats.assignCharacter(state, seat(state, "Jo"), "beggar", isTraveller = true)
        return DayRules.record(
            state,
            lookup,
            Nomination(
                day = state.cycle,
                nominatorId = seat(state, "Ana"),
                nomineeId = seat(state, "Jo"),
                isExile = true,
                result = NominationResult.ABOUT_TO_DIE,
            ),
        )
    }

    @Test
    fun `the stat strip names an exile that has not happened`() {
        val plain = DayModel.stats(day(), lookup)
        assertEquals("", plain.exileLine)
        assertNull(plain.exileOwedId)

        val stats = DayModel.stats(exileOwed(), lookup)
        assertEquals(seat(exileOwed(), "Jo"), stats.exileOwedId)
        assertTrue(stats.exileLine, stats.exileLine.startsWith("Jo was exiled"))
        assertTrue(stats.exileLine, stats.exileLine.contains("has not left the game"))
        // The block line is untouched — these are two different obligations.
        assertNull(stats.onBlockId)
    }

    @Test
    fun `the DUSK row says so and does not read as complete`() {
        val state = exileOwed()
        val dusk = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
            .single { it.stage == DayStage.DUSK }
        assertTrue(dusk.summary, dusk.summary.contains("Jo was exiled and has not left the game."))
        assertEquals(StageTone.ALERT, dusk.tone)
        assertFalse("a day that still owes an exile is not finished", dusk.complete)
    }

    @Test
    fun `a recorded day still owes the exile`() {
        // An exile is not the day's execution: recording one does not clear it.
        val state = Execution.noExecution(exileOwed())
        val dusk = DayModel.stages(state, lookup, null, emptyBriefing(), emptySet())
            .single { it.stage == DayStage.DUSK }
        assertTrue(dusk.summary, dusk.summary.contains("No execution today"))
        assertTrue(dusk.summary, dusk.summary.contains("has not left the game"))
        assertFalse(dusk.complete)
    }

    // ------------------------------------------------------------------
    // Execution sheet — one fact, once (C2-5)
    // ------------------------------------------------------------------

    @Test
    fun `a consequence the verdict line already says is not printed again`() {
        val preview = KillOutcome.Prevented(
            by = null,
            reason = "Fay cannot die during the day.",
            announce = "Say: 'Fay was executed… and remains alive.' Do not say why.",
        )
        val rows = listOf(
            ExecutionConsequence(
                sourceId = "vizier",
                headline = "Say: 'Fay was executed… and remains alive.' Do not say why.",
                detail = "Credited to the Vizier.",
            ),
            ExecutionConsequence(sourceId = "vizier", headline = "Fay cannot die during the day."),
            ExecutionConsequence(sourceId = "undertaker", headline = "Gus learns Fay's character."),
        )
        val visible = visibleConsequences(preview, rows)
        assertEquals(visible.map { it.headline }.toString(), 1, visible.size)
        assertEquals("undertaker", visible.single().sourceId)
    }

    @Test
    fun `a verdict line that says nothing extra keeps every row`() {
        val preview = KillOutcome.Dies(reason = "Nothing stops it — they die.")
        val rows = listOf(
            ExecutionConsequence(sourceId = "saint", headline = "Jo was the Saint — EVIL WINS."),
        )
        assertEquals(rows, visibleConsequences(preview, rows))
        assertEquals("Nothing stops it — they die.", previewText(preview))
    }
}
