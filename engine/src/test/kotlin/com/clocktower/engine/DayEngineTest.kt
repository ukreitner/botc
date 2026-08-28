package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The day engine (WP3): nomination pre-flight, voting, the execution funnel and
 * the derived day-closed state (day-engine §B–§F; lead D2, D27, D30, D34, D44, D51).
 *
 * The load-bearing rule under all of it: **an execution that kills nobody is
 * still an execution**, and there is no stored "the day is over" boolean.
 */
class DayEngineTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    private fun newGame(players: Int = 8): GameState =
        GameActions.newGame(tb, (1..players).map { "P$it" })

    /** A day-1 state with [players] seats and no characters assigned. */
    private fun day1(players: Int = 8): GameState =
        GameActions.advancePhase(GameActions.advancePhase(newGame(players)))

    private fun assign(state: GameState, seat: Long, id: String, traveller: Boolean = false) =
        GameActions.assignCharacter(state, seat, id, traveller)

    private fun token(state: GameState, seat: Long, sourceId: String, label: String) =
        GameActions.addReminder(state, seat, PlacedReminder(sourceId, label, placedCycle = state.cycle))

    private fun nomination(state: GameState, nominator: Long, nominee: Long, voters: List<Long> = emptyList()) =
        Nomination(
            day = state.cycle,
            nominatorId = nominator,
            nomineeId = nominee,
            voterIds = voters,
        )

    // ==================================================================
    // Nomination
    // ==================================================================

    @Test
    fun `dead players may be nominated`() {
        var state = day1()
        state = Deaths.attempt(state, lookup, 3L, KillCause(DeathCause.STORYTELLER)).state
        assertFalse(assertNotNull(state.player(3)).alive)

        val check = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 3L)
        assertTrue(check.legal, "dead players may be executed: ${check.blockers}")
        assertTrue(check.blockers.isEmpty())
        assertTrue(check.cautions.any { "no ghost vote" in it })
    }

    @Test
    fun `dead players may not nominate`() {
        var state = day1()
        state = Deaths.attempt(state, lookup, 3L, KillCause(DeathCause.STORYTELLER)).state
        val check = DayRules.checkNomination(state, lookup, nominatorId = 3L, nomineeId = 0L)
        assertFalse(check.legal)
        assertTrue(check.blockers.any { "dead" in it })
    }

    @Test
    fun `one nomination each way, enforced in the engine`() {
        var state = day1()
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L))
        assertEquals(1, state.nominations.size)

        assertFalse(DayRules.checkNomination(state, lookup, 0L, 4L).legal, "0 already nominated")
        assertFalse(DayRules.checkNomination(state, lookup, 1L, 3L).legal, "3 was already nominated")

        val illegal = nomination(state, 0L, 4L)
        assertEquals(state, DayRules.record(state, lookup, illegal), "an illegal nomination is refused")
        val forced = DayRules.record(state, lookup, illegal, force = true)
        assertEquals(2, forced.nominations.size, "the storyteller always wins")
    }

    @Test
    fun `the Butcher may nominate a second time after the day's first execution`() {
        var state = day1()
        state = assign(state, 5L, "butcher", traveller = true)
        state = DayRules.record(state, lookup, nomination(state, 5L, 3L))
        assertFalse(DayRules.canNominate(state, lookup, 5L).allowed, "no execution yet")

        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(DayRules.executionSpent(state))
        assertTrue(DayRules.secondExecutionAllowed(state, lookup))
        assertTrue(
            DayRules.canNominate(state, lookup, 5L).allowed,
            "the Butcher gets one extra nomination after the first execution",
        )
        assertFalse(DayRules.nominationsClosed(state, lookup), "and the day is not over")
    }

    @Test
    fun `an awoken Banshee nominates twice and may nominate while dead`() {
        var state = day1()
        state = assign(state, 2L, "banshee")
        state = token(state, 2L, "banshee", "Has Ability")
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL)).state
        assertFalse(assertNotNull(state.player(2)).alive)

        assertTrue(DayRules.canNominate(state, lookup, 2L).allowed, "an awoken Banshee nominates dead")
        state = DayRules.record(state, lookup, nomination(state, 2L, 3L), force = true)
        assertTrue(DayRules.canNominate(state, lookup, 2L).allowed, "twice per day")
        state = DayRules.record(state, lookup, nomination(state, 2L, 4L), force = true)
        assertFalse(DayRules.canNominate(state, lookup, 2L).allowed, "but never three times")
    }

    @Test
    fun `a Bishop takes every nomination away from the players`() {
        var state = day1()
        state = assign(state, 6L, "bishop", traveller = true)
        for (seat in 0L..5L) {
            assertFalse(
                DayRules.canNominate(state, lookup, seat).allowed,
                "only the storyteller nominates while a Bishop is in play",
            )
        }
        // The storyteller's own nomination is not blocked by the player gate.
        val forced = DayRules.record(state, lookup, nomination(state, 0L, 3L), force = true)
        assertEquals(1, forced.nominations.size)
    }

    @Test
    fun `the Virgin fires on the first nomination ever, not the first today`() {
        var state = day1()
        state = assign(state, 3L, "virgin")
        state = assign(state, 0L, "chef")

        val first = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 3L)
        val trigger = assertNotNull(first.triggers.find { it.sourceId == "virgin" })
        assertEquals(TriggerKind.AUTO_EXECUTION, trigger.kind)
        assertEquals(3L, trigger.actorId)
        assertEquals(0L, trigger.targetId)

        // Day 2, having been nominated on day 1: no trigger.
        var later = DayRules.record(state, lookup, nomination(state, 0L, 3L))
        later = GameActions.advancePhase(GameActions.advancePhase(later)) // night 2, day 2
        val second = DayRules.checkNomination(later, lookup, nominatorId = 1L, nomineeId = 3L)
        assertNull(second.triggers.find { it.sourceId == "virgin" }, "only the first nomination ever")
    }

    @Test
    fun `the Virgin trigger executes the nominator and closes the day`() {
        var state = day1()
        state = assign(state, 3L, "virgin")
        state = assign(state, 0L, "chef")
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 0L, 3L).triggers.find { it.sourceId == "virgin" },
        )
        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_EXECUTE)

        val record = assertNotNull(DayRules.executionToday(state))
        assertEquals(ExecutionVia.VIRGIN, record.via)
        assertEquals(ExecutionOutcome.DIED, record.outcome)
        assertEquals(0L, record.playerId)
        assertFalse(assertNotNull(state.player(0)).alive)
        assertTrue(DayRules.executionSpent(state))
        assertTrue(DayRules.nominationsClosed(state, lookup))
        assertNull(DayRules.aboutToDie(state))
        assertTrue(Memory.isSpent(state, "virgin", 3L), "the ability is spent")
    }

    @Test
    fun `a Virgin ruled not to fire still spends the ability`() {
        var state = day1()
        state = assign(state, 3L, "virgin")
        state = assign(state, 0L, "spy")
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 0L, 3L).triggers.find { it.sourceId == "virgin" },
        )
        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_REGISTERS_GOOD)
        assertTrue(state.executions.isEmpty(), "nobody is executed")
        assertTrue(assertNotNull(state.player(0)).alive)
        assertTrue(Memory.isSpent(state, "virgin", 3L))
        assertTrue(state.ledger.any { it.kind == LedgerKind.RULING && it.sourceId == "virgin" })
    }

    @Test
    fun `the Witch curse kills the nominator at four alive and not at three`() {
        var state = day1(6)
        state = assign(state, 0L, "witch")
        state = token(state, 4L, "witch", "Cursed")
        val fourAlive = DayRules.checkNomination(state, lookup, nominatorId = 4L, nomineeId = 1L)
        val trigger = assertNotNull(fourAlive.triggers.find { it.sourceId == "witch" })
        assertEquals(TriggerKind.AUTO_DEATH, trigger.kind)

        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)
        assertFalse(assertNotNull(state.player(4)).alive, "the cursed nominator dies")
        assertFalse(DayRules.nominationsClosed(state, lookup), "and the vote continues")

        // Down to three alive: the Witch loses the ability.
        var small = day1(6)
        small = assign(small, 0L, "witch")
        small = token(small, 4L, "witch", "Cursed")
        for (seat in listOf(2L, 3L, 5L)) {
            small = Deaths.attempt(small, lookup, seat, KillCause(DeathCause.STORYTELLER)).state
        }
        assertEquals(3, small.aliveCountResidents)
        assertNull(
            DayRules.checkNomination(small, lookup, 4L, 1L).triggers.find { it.sourceId == "witch" },
            "the Witch's curse needs 4+ alive",
        )
    }

    @Test
    fun `the Golem kills the nominee and the vote continues`() {
        var state = day1()
        state = assign(state, 2L, "golem")
        state = assign(state, 5L, "chef")
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 2L, 5L).triggers.find { it.sourceId == "golem" },
        )
        assertEquals(TriggerKind.AUTO_DEATH, trigger.kind)
        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)

        assertFalse(assertNotNull(state.player(5)).alive)
        assertTrue(DayRules.hasToken(state, 2L, "golem", "May Not Nominate"))
        assertFalse(DayRules.nominationsClosed(state, lookup), "the day is not over")
        assertFalse(DayRules.canNominate(state, lookup, 2L).allowed, "and never again")
    }

    @Test
    fun `the Golem's nomination of the Demon kills nobody`() {
        var state = day1()
        state = assign(state, 2L, "golem")
        state = assign(state, 5L, "imp")
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 2L, 5L).triggers.find { it.sourceId == "golem" },
        )
        assertEquals(TriggerKind.WARN, trigger.kind)
        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)
        assertTrue(assertNotNull(state.player(5)).alive, "the Demon survives a Golem nomination")
        assertTrue(DayRules.hasToken(state, 2L, "golem", "May Not Nominate"))
    }

    @Test
    fun `the Fearmonger warning is nominator-specific`() {
        var state = day1()
        state = assign(state, 2L, "fearmonger")
        state = token(state, 5L, "fearmonger", "Fear")

        val ordinary = assertNotNull(
            DayRules.checkNomination(state, lookup, 1L, 5L).triggers.find { it.sourceId == "fearmonger" },
        )
        assertTrue("ordinary nomination" in ordinary.headline, ordinary.headline)

        val fatal = assertNotNull(
            DayRules.checkNomination(state, lookup, 2L, 5L).triggers.find { it.sourceId == "fearmonger" },
        )
        assertTrue("EVIL WINS" in fatal.headline, fatal.headline)
    }

    @Test
    fun `a Goblin claim is recorded as a statement, an announcement and a token`() {
        var state = day1()
        state = assign(state, 4L, "goblin")
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 1L, 4L).triggers.find { it.sourceId == "goblin" },
        )
        assertEquals(TriggerKind.CHOICE, trigger.kind)
        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)

        assertTrue(DayRules.hasToken(state, 4L, "goblin", "Claimed"))
        assertEquals(1, Memory.statementsOn(state, state.cycle, sourceId = "goblin").size)
        assertEquals(1, Memory.pendingAnnouncements(state).size)
    }

    @Test
    fun `withdrawn nominations are recordable and consume both rights`() {
        var state = day1()
        state = DayRules.record(
            state, lookup,
            nomination(state, 0L, 3L).copy(result = NominationResult.WITHDRAWN),
        )
        assertTrue(DayRules.hasNominatedToday(state, 0L))
        assertTrue(DayRules.hasBeenNominatedToday(state, 3L))
        assertEquals(0, DayRules.highestVotesToday(state))
        assertNull(DayRules.aboutToDie(state))
    }

    @Test
    fun `madness is surfaced as a caution on both sides of a nomination`() {
        var state = day1()
        state = assign(state, 1L, "cerenovus")
        state = Effects.place(
            state = state,
            target = 4L,
            kind = EffectKind.MAD,
            sourceCharacterId = "cerenovus",
            sourcePlayerId = 1L,
            until = Until.DUSK,
            label = "Mad",
            characterId = "empath",
        ).state
        val triggers = DayRules.checkNomination(state, lookup, nominatorId = 4L, nomineeId = 2L).triggers
        assertTrue(triggers.any { it.sourceId == "cerenovus" && it.kind == TriggerKind.WARN })
    }

    // ==================================================================
    // Voting
    // ==================================================================

    @Test
    fun `the Bureaucrat triples one vote and the Thief negates one`() {
        var state = day1()
        state = assign(state, 4L, "bureaucrat", traveller = true)
        state = token(state, 4L, "bureaucrat", "3 Votes")
        assertEquals(5, DayRules.tally(state, lookup, listOf(1L, 2L, 4L), isExile = false))

        var thief = day1()
        thief = assign(thief, 4L, "thief", traveller = true)
        thief = token(thief, 4L, "thief", "Negative Vote")
        assertEquals(1, DayRules.tally(thief, lookup, listOf(1L, 2L, 4L), isExile = false))
    }

    @Test
    fun `exiles ignore every vote modifier`() {
        var state = day1()
        state = assign(state, 4L, "bureaucrat", traveller = true)
        state = token(state, 4L, "bureaucrat", "3 Votes")
        assertEquals(3, DayRules.tally(state, lookup, listOf(1L, 2L, 4L), isExile = true))
        val rules = DayRules.voteRules(state, lookup, isExile = true)
        assertTrue(rules.weights.isEmpty())
        assertFalse(rules.spendsGhostVotes)
        assertTrue(rules.reasons.any { "abilities do not apply" in it })
        assertEquals(state.exileThreshold, rules.threshold)
    }

    @Test
    fun `an awoken Banshee votes twice`() {
        var state = day1()
        state = assign(state, 3L, "banshee")
        state = token(state, 3L, "banshee", "Has Ability")
        assertEquals(3, DayRules.tally(state, lookup, listOf(1L, 3L), isExile = false))
    }

    @Test
    fun `a sober Voudon sets the threshold to one and lets only the dead vote`() {
        var state = day1()
        state = assign(state, 7L, "voudon", traveller = true)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state

        val rules = DayRules.voteRules(state, lookup, isExile = false)
        assertEquals(1, rules.threshold)
        assertFalse(rules.spendsGhostVotes)
        assertTrue(7L in rules.eligibleVoterIds, "the Voudon may vote")
        assertTrue(2L in rules.eligibleVoterIds, "and so may the dead")
        assertFalse(1L in rules.eligibleVoterIds, "living non-Voudon players may not")

        // Recording under a Voudon spends no ghost vote.
        val recorded = DayRules.record(
            state, lookup,
            nomination(state, 7L, 3L, voters = listOf(2L, 7L)),
        )
        assertFalse(assertNotNull(recorded.player(2)).ghostVoteUsed)
    }

    @Test
    fun `ghost votes are spent exactly once and never on an exile`() {
        var state = day1()
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state
        assertFalse(assertNotNull(state.player(2)).ghostVoteUsed)

        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L, 2L)))
        assertTrue(assertNotNull(state.player(2)).ghostVoteUsed, "the ghost vote is spent")
        assertFalse(assertNotNull(state.player(1)).ghostVoteUsed, "living voters spend nothing")

        var exile = day1()
        exile = Deaths.attempt(exile, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state
        exile = DayRules.record(
            exile, lookup,
            nomination(exile, 0L, 3L, voters = listOf(2L)).copy(isExile = true),
            force = true,
        )
        assertFalse(assertNotNull(exile.player(2)).ghostVoteUsed, "an exile spends no ghost vote")
    }

    @Test
    fun `a Zealot must vote with five or more alive`() {
        var state = day1(6)
        state = assign(state, 3L, "zealot")
        assertEquals(listOf(3L), DayRules.mustVote(state, lookup))
        state = Deaths.attempt(state, lookup, 5L, KillCause(DeathCause.STORYTELLER)).state
        state = Deaths.attempt(state, lookup, 4L, KillCause(DeathCause.STORYTELLER)).state
        assertEquals(4, state.aliveCountWithTravellers)
        assertTrue(DayRules.mustVote(state, lookup).isEmpty(), "below 5 alive the Zealot is free")
    }

    @Test
    fun `an illegal Butler hand never counts, secret voting or not`() {
        // Playtest C-3: this used to be tallied on an ordinary day, so three
        // legal hands plus the Butler read "about to die" at 4 of 4 when the
        // legal total was 3 — SAFE. The wiki is unconditional: "you may only
        // vote if they are voting too".
        var state = day1()
        state = assign(state, 6L, "organgrinder")
        state = assign(state, 1L, "butler")
        state = token(state, 4L, "butler", "Master")
        assertTrue(DayRules.secretVoting(state, lookup))
        assertEquals(4L, DayRules.masterOf(state, 1L))
        assertTrue(DayRules.butlerVotingIllegally(state, lookup, 1L, listOf(1L, 2L)))
        assertEquals(1, DayRules.tally(state, lookup, listOf(1L, 2L), isExile = false))

        // The same day with no Organ Grinder: still one, not two.
        var open = day1()
        open = assign(open, 1L, "butler")
        open = token(open, 4L, "butler", "Master")
        assertFalse(DayRules.secretVoting(open, lookup))
        assertEquals(
            1,
            DayRules.tally(open, lookup, listOf(1L, 2L), isExile = false),
            "the Butler's Master is not voting — the hand does not count",
        )
        assertEquals(listOf(2L), DayRules.countedVoters(open, lookup, listOf(1L, 2L), isExile = false))

        // With the Master's hand up it counts, and so does the Master's.
        assertEquals(3, DayRules.tally(open, lookup, listOf(1L, 2L, 4L), isExile = false))
    }

    @Test
    fun `an ineligible hand can never move the tally`() {
        // Playtest C-1 (P0): the vote chips for ineligible voters were dimmed
        // and given a reason, then summed anyway.
        var spent = day1()
        spent = Deaths.attempt(spent, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state
        spent = spent.updatePlayer(2L) { it.copy(ghostVoteUsed = true) }
        assertFalse(2L in DayRules.voteRules(spent, lookup, false).eligibleVoterIds)
        assertEquals(
            1,
            DayRules.tally(spent, lookup, listOf(1L, 2L), isExile = false),
            "a spent ghost vote adds nothing",
        )

        // A sober Voudon: only the Voudon and the dead may vote, and the
        // threshold is 1 — the worst case in the finding, where one illegal
        // living hand put a player on the block.
        var voudon = day1()
        voudon = assign(voudon, 7L, "voudon", traveller = true)
        val rules = DayRules.voteRules(voudon, lookup, isExile = false)
        assertEquals(1, rules.threshold)
        assertEquals(0, DayRules.tally(voudon, lookup, listOf(1L), isExile = false))
        assertEquals(1, DayRules.tally(voudon, lookup, listOf(7L), isExile = false))

        // An exile is untouched: every hand counts once, abilities do not apply.
        assertEquals(2, DayRules.tally(voudon, lookup, listOf(1L, 3L), isExile = true))
    }

    @Test
    fun `an ineligible hand reaches neither the record nor the vote tokens`() {
        var state = day1()
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.STORYTELLER)).state
        state = state.updatePlayer(2L) { it.copy(ghostVoteUsed = true) }

        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L, 2L)))
        val recorded = assertNotNull(state.nominations.lastOrNull())
        assertEquals(1, recorded.votes, "the spent ghost vote is not in the tally")
        assertEquals(
            listOf(1L, 2L),
            recorded.voterIds,
            "the raw hands stay on the record — only the tally drops them",
        )
    }

    @Test
    fun `recording a nomination applies the same Butler exception as the tally`() {
        var state = day1()
        state = assign(state, 6L, "organgrinder")
        state = assign(state, 1L, "butler")
        state = token(state, 4L, "butler", "Master")
        val voters = listOf(1L, 2L)

        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = voters), force = true)
        val recorded = assertNotNull(state.nominations.lastOrNull())
        assertEquals(
            DayRules.tally(state, lookup, voters, isExile = false),
            recorded.votes,
            "the Butler's hand is down with its Master's — record must not count it",
        )
        assertEquals(1, recorded.votes)

        // Without the Organ Grinder record and tally still agree — at one.
        var open = day1()
        open = assign(open, 1L, "butler")
        open = token(open, 4L, "butler", "Master")
        open = DayRules.record(open, lookup, nomination(open, 0L, 3L, voters = voters), force = true)
        assertEquals(1, assertNotNull(open.nominations.lastOrNull()).votes)
    }

    @Test
    fun `Legion fails an execution when only evil voted`() {
        var state = day1()
        state = assign(state, 0L, "legion")
        state = assign(state, 1L, "legion")
        state = assign(state, 2L, "chef")
        assertTrue(DayRules.executionFailsOnlyEvilVoted(state, lookup, listOf(0L, 1L)))
        assertFalse(DayRules.executionFailsOnlyEvilVoted(state, lookup, listOf(0L, 2L)))
        assertFalse(DayRules.executionFailsOnlyEvilVoted(state, lookup, emptyList()))
    }

    @Test
    fun `the VoteRules snapshot is frozen on the nomination`() {
        var state = day1()
        state = assign(state, 7L, "voudon", traveller = true)
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L)))
        val frozen = assertNotNull(state.nominations.single().voteRules)
        assertEquals(1, frozen.threshold, "a sober Voudon was in play at the tally")

        // Exile the Voudon afterwards: history must not move.
        state = Execution.exile(state, lookup, 7L)
        assertFalse(assertNotNull(state.player(7)).alive)
        assertEquals(1, assertNotNull(state.nominations.single().voteRules).threshold)
        assertEquals(
            4,
            DayRules.voteRules(state, lookup, isExile = false).threshold,
            "the LIVE rules have moved on; the recorded ones have not",
        )
    }

    @Test
    fun `recording snapshots the nominator's registration`() {
        var state = day1()
        state = assign(state, 0L, "recluse")
        state = assign(state, 5L, "imp")
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L))
        val recorded = state.nominations.single()
        assertEquals("recluse", recorded.nominatorCharacterId)
        assertEquals(setOf(Team.OUTSIDER), recorded.nominatorTeams)
        assertEquals(listOf(5L), recorded.demonIdsAtRecord)

        // The grimoire changes; the snapshot does not (lead D51).
        val moved = assign(state, 5L, "chef")
        assertEquals(listOf(5L), moved.nominations.single().demonIdsAtRecord)
    }

    @Test
    fun `a weighted tally is computed from raw hands and a headcount is left alone`() {
        var state = day1()
        state = assign(state, 4L, "bureaucrat", traveller = true)
        state = token(state, 4L, "bureaucrat", "3 Votes")
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L, 4L)))
        assertEquals(4, state.nominations.single().votes, "1 + 3")

        var plain = day1()
        plain = DayRules.record(plain, lookup, Nomination(day = 1, nominatorId = 0, nomineeId = 3, votes = 5))
        assertEquals(5, plain.nominations.single().votes, "a bare headcount passes through")
    }

    // ==================================================================
    // Execution
    // ==================================================================

    @Test
    fun `an execution that kills nobody is still an execution`() {
        var state = day1()
        state = assign(state, 3L, "chef")
        state = Execution.execute(
            state, lookup, playerId = 3L,
            outcome = ExecutionOutcome.SURVIVED, preventedBy = "devilsadvocate",
        )
        assertTrue(state.deaths.isEmpty(), "nobody died")
        assertTrue(assertNotNull(state.player(3)).alive)
        val record = state.executions.single()
        assertEquals(ExecutionOutcome.SURVIVED, record.outcome)
        assertEquals("devilsadvocate", record.preventedBy)
        assertEquals(3L, record.playerId)
        assertTrue(DayRules.executionSpent(state), "and the day's execution is spent")
        assertTrue(DayRules.nominationsClosed(state, lookup), "further nominations are blocked")
    }

    @Test
    fun `the Devil's Advocate turns a real execution into a survival through the kill funnel`() {
        var state = day1()
        state = assign(state, 0L, "devilsadvocate")
        state = assign(state, 3L, "chef")
        state = Effects.place(
            state = state,
            target = 3L,
            kind = EffectKind.SURVIVES_EXECUTION,
            sourceCharacterId = "devilsadvocate",
            sourcePlayerId = 0L,
            until = Until.DUSK,
            label = "Survives Execution",
        ).state

        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(assertNotNull(state.player(3)).alive)
        val record = state.executions.single()
        assertEquals(ExecutionOutcome.SURVIVED, record.outcome)
        assertEquals("devilsadvocate", record.preventedBy)
        assertTrue(DayRules.executionSpent(state))
    }

    @Test
    fun `noExecution writes a NO_EXECUTION row and closes the day without spending it`() {
        var state = day1()
        state = Execution.noExecution(state)
        val record = state.executions.single()
        assertEquals(ExecutionOutcome.NO_EXECUTION, record.outcome)
        assertEquals(state.cycle, record.day)
        assertNull(record.playerId)
        assertFalse(DayRules.executionSpent(state), "no execution was spent")
        assertTrue(DayRules.nominationsClosed(state, lookup), "but the day is closed")
        assertTrue("No execution" in DayRules.nominationsClosedReason(state, lookup))

        // Idempotent.
        assertEquals(1, Execution.noExecution(state).executions.size)
    }

    @Test
    fun `an execution replaces a declared no-execution`() {
        var state = day1()
        state = Execution.noExecution(state)
        state = Execution.execute(state, lookup, playerId = 3L)
        assertEquals(1, state.executions.size)
        assertEquals(ExecutionOutcome.DIED, state.executions.single().outcome)
        assertTrue(DayRules.executionSpent(state))
    }

    @Test
    fun `only one execution per day unless a Butcher says otherwise`() {
        var state = day1()
        state = Execution.execute(state, lookup, playerId = 3L)
        val after = Execution.execute(state, lookup, playerId = 4L)
        assertEquals(state, after, "the second execution is refused")
        assertTrue(assertNotNull(after.player(4)).alive)

        assertEquals(
            2,
            Execution.execute(state, lookup, playerId = 4L, force = true).executions.size,
            "force is the storyteller's override",
        )

        var butcher = day1()
        butcher = assign(butcher, 5L, "butcher", traveller = true)
        butcher = Execution.execute(butcher, lookup, playerId = 3L)
        butcher = Execution.execute(butcher, lookup, playerId = 4L)
        assertEquals(2, butcher.executions.size, "the Butcher's second execution is legal")
        assertTrue(DayRules.nominationsClosed(butcher, lookup), "and now the day IS closed")
    }

    @Test
    fun `Travellers are exiled, never executed`() {
        var state = day1()
        state = assign(state, 5L, "scapegoat", traveller = true)
        assertEquals(state, Execution.execute(state, lookup, playerId = 5L))
        assertTrue(state.executions.isEmpty())

        val exiled = Execution.exile(state, lookup, 5L)
        assertFalse(assertNotNull(exiled.player(5)).alive)
        assertEquals(DeathCause.EXILE, exiled.deaths.single().cause)
        assertTrue(exiled.executions.isEmpty(), "an exile is never an execution")
        assertFalse(DayRules.executionSpent(exiled), "and the day's execution is still available")
    }

    @Test
    fun `the Scapegoat dies instead and the execution still belongs to the nominee`() {
        var state = day1()
        state = assign(state, 3L, "chef")
        state = assign(state, 5L, "scapegoat", traveller = true)
        state = Execution.execute(
            state, lookup, playerId = 3L, optionId = Deaths.OPTION_REDIRECT,
        )
        assertTrue(assertNotNull(state.player(3)).alive, "the nominee lives")
        assertFalse(assertNotNull(state.player(5)).alive, "the Scapegoat dies")
        assertEquals(DeathCause.EXECUTION, state.deaths.single().cause)
        val record = state.executions.single()
        assertEquals(3L, record.playerId, "the execution still belongs to the nominee")
        assertEquals(5L, record.diedInsteadId)
        assertEquals(ExecutionOutcome.DIED, record.outcome)
        assertTrue(DayRules.executionSpent(state))
    }

    @Test
    fun `an unanswered kill choice leaves the state untouched`() {
        var state = day1()
        state = assign(state, 3L, "chef")
        state = assign(state, 5L, "scapegoat", traveller = true)
        assertEquals(
            state,
            Execution.execute(state, lookup, playerId = 3L),
            "a Scapegoat's substitution is a question, and the app must ask it",
        )
    }

    @Test
    fun `an execution places Died Today on whoever actually died and dawn clears it`() {
        var state = day1()
        state = assign(state, 2L, "undertaker")
        state = assign(state, 3L, "chef")
        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(
            Status.effectsOn(state, lookup, 3L).any {
                Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("undertaker", "Died Today")
            },
            "the Undertaker's mark goes on the executed seat",
        )
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // night 2, day 2
        assertTrue(
            Status.effectsOn(state, lookup, 3L).none {
                Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("undertaker", "Died Today")
            },
            "and it is swept at dawn",
        )
    }

    @Test
    fun `the execution snapshots character, alignment, impairment, tally and threshold`() {
        var state = day1()
        state = assign(state, 3L, "recluse")
        state = token(state, 3L, "poisoner", "Poisoned")
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L, 2L, 4L, 5L)))
        state = Execution.execute(state, lookup, playerId = 3L, nominationIndex = 0)

        val record = state.executions.single()
        assertEquals("recluse", record.characterIdAtExecution)
        assertEquals(false, record.wasEvilAtExecution)
        assertEquals(true, record.abilityImpairedAtExecution)
        assertEquals(4, record.tally)
        assertEquals(4, record.threshold)
        assertEquals(0L, record.nominatorId, "the nominator comes from the nomination")
        assertEquals(state.deaths.single().id, record.deathEventId)
    }

    @Test
    fun `an execution by a storyteller ruling counts as the day's execution`() {
        var state = day1()
        state = assign(state, 3L, "mutant")
        state = Execution.execute(state, lookup, playerId = 3L, via = ExecutionVia.STORYTELLER)
        assertEquals(ExecutionVia.STORYTELLER, state.executions.single().via)
        assertTrue(DayRules.executionSpent(state), "lead D34: it is still the day's execution")
        assertFalse(assertNotNull(state.player(3)).alive)
    }

    @Test
    fun `consequences name every protection the storyteller must confirm`() {
        var state = day1(10)
        state = assign(state, 0L, "devilsadvocate")
        state = assign(state, 1L, "pacifist")
        state = assign(state, 2L, "sailor")
        state = assign(state, 3L, "tealady")
        state = assign(state, 4L, "fool")
        state = assign(state, 5L, "zombuul")
        state = assign(state, 6L, "vizier")
        state = assign(state, 7L, "psychopath")
        state = assign(state, 8L, "scapegoat", traveller = true)
        state = assign(state, 9L, "chef")

        fun sourcesFor(seat: Long): Set<String> {
            val record = ExecutionRecord(
                day = state.cycle,
                outcome = ExecutionOutcome.DIED,
                playerId = seat,
            )
            return Execution.consequences(state, lookup, record).map { it.sourceId }.toSet()
        }

        // A good nominee: the Pacifist and the good-aligned Scapegoat both apply.
        assertTrue("pacifist" in sourcesFor(9L), sourcesFor(9L).toString())
        assertTrue("scapegoat" in sourcesFor(9L), sourcesFor(9L).toString())
        assertTrue("sailor" in sourcesFor(2L), "the Sailor cannot die")
        assertTrue("tealady" in sourcesFor(2L), "the Tea Lady's good neighbours cannot die")
        assertTrue("fool" in sourcesFor(4L), "the Fool's first death")
        assertTrue("zombuul" in sourcesFor(5L), "the Zombuul registers dead")
        assertTrue("vizier" in sourcesFor(6L), "the Vizier cannot die by day")
        assertTrue("psychopath" in sourcesFor(7L), "roshambo")

        // The Devil's Advocate's protection is on its target, not on the DA.
        state = Effects.place(
            state = state,
            target = 9L,
            kind = EffectKind.SURVIVES_EXECUTION,
            sourceCharacterId = "devilsadvocate",
            sourcePlayerId = 0L,
            until = Until.DUSK,
            label = "Survives Execution",
        ).state
        assertTrue("devilsadvocate" in sourcesFor(9L))
    }

    @Test
    fun `consequences cover the day-end abilities an execution wakes up`() {
        var state = day1(10)
        state = assign(state, 0L, "undertaker")
        state = assign(state, 1L, "godfather")
        state = assign(state, 2L, "cannibal")
        state = assign(state, 3L, "mastermind")
        state = assign(state, 4L, "saint")
        state = assign(state, 5L, "imp")

        val saint = Execution.consequences(
            state, lookup,
            ExecutionRecord(state.cycle, ExecutionOutcome.DIED, playerId = 4L),
        )
        assertTrue(saint.any { it.sourceId == "saint" && "EVIL WINS" in it.headline })
        assertTrue(saint.any { it.sourceId == "undertaker" })
        assertTrue(saint.any { it.sourceId == "cannibal" })
        assertTrue(saint.any { it.sourceId == "godfather" }, "an Outsider died today")

        val demon = Execution.consequences(
            state, lookup,
            ExecutionRecord(state.cycle, ExecutionOutcome.DIED, playerId = 5L),
        )
        assertTrue(demon.any { it.sourceId == "mastermind" }, "the Demon died by execution")
    }

    // ==================================================================
    // Derived day state
    // ==================================================================

    @Test
    fun `no stored boolean anywhere carries the day-closed state`() {
        var state = day1()
        assertFalse(DayRules.nominationsClosed(state, lookup))
        assertEquals("", DayRules.nominationsClosedReason(state, lookup))

        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(DayRules.nominationsClosed(state, lookup))

        // The whole signal is one list. Drop it and the day reopens.
        val reopened = state.copy(executions = emptyList())
        assertFalse(DayRules.nominationsClosed(reopened, lookup))
        assertFalse(DayRules.executionSpent(reopened))
    }

    @Test
    fun `a new day reopens nominations without any explicit reset`() {
        var state = day1()
        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(DayRules.nominationsClosed(state, lookup))
        state = GameActions.advancePhase(GameActions.advancePhase(state)) // night 2, day 2
        assertEquals(2, state.cycle)
        assertFalse(DayRules.nominationsClosed(state, lookup))
        assertFalse(DayRules.executionSpent(state))
        assertNull(DayRules.executionToday(state))
        assertTrue(DayRules.canNominate(state, lookup, 0L).allowed, "day 1's nomination is spent")
    }

    @Test
    fun `nominations are refused once the day is closed`() {
        var state = day1()
        state = Execution.execute(state, lookup, playerId = 3L)
        val check = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 4L)
        assertFalse(check.legal)
        assertTrue(check.blockers.any { "closed" in it })
        assertEquals(state, DayRules.record(state, lookup, nomination(state, 0L, 4L)))
    }

    @Test
    fun `the Vizier cannot die during the day`() {
        var state = day1()
        state = assign(state, 3L, "vizier")
        assertTrue(DayRules.immuneToDayDeath(state, lookup, 3L))
        assertFalse(DayRules.immuneToDayDeath(state, lookup, 4L))
        state = Execution.execute(state, lookup, playerId = 3L)
        assertTrue(assertNotNull(state.player(3)).alive)
        assertEquals(ExecutionOutcome.SURVIVED, state.executions.single().outcome)
        assertEquals("vizier", state.executions.single().preventedBy)
    }

    // ==================================================================
    // The game log
    // ==================================================================

    @Test
    fun `the game log is a total order that names the voters`() {
        var state = GameActions.advancePhase(newGame()) // night 1
        state = Ledger.choice(state, "poisoner", actorId = 1L, targetIds = listOf(4L))
        state = Deaths.attempt(state, lookup, 6L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        state = GameActions.advancePhase(state) // day 1
        state = Ledger.statement(state, 2L, Ledger.Sources.CLAIM, "I am the Chef.")
        state = DayRules.record(state, lookup, nomination(state, 0L, 3L, voters = listOf(1L, 2L, 4L, 5L)))
        state = Execution.execute(state, lookup, playerId = 3L, nominationIndex = 0)

        val rows = GameLog.rows(state, lookup)
        assertEquals(rows.map { it.seq }, rows.indices.map { it.toLong() }, "seq is a dense total order")
        assertEquals(
            rows.sortedWith(compareBy({ it.cycle }, { !it.atNight })).map { it.seq },
            rows.map { it.seq },
            "night 1 precedes day 1",
        )
        val nominationRow = assertNotNull(rows.find { "nominates" in it.text })
        for (voter in listOf("P2", "P3", "P5", "P6")) {
            assertTrue(voter in nominationRow.text, "voters are named: ${nominationRow.text}")
        }
        assertTrue(rows.any { "P2 (Poisoner) chooses P5" in it.text }, rows.joinToString("\n") { it.text })
        assertTrue(rows.any { "I am the Chef." in it.text })
        assertTrue(rows.any { "is executed" in it.text })
        assertTrue(rows.any { "P7 dies" in it.text })

        val markdown = GameLog.toMarkdown(state, lookup)
        assertTrue("## Night 1" in markdown, markdown)
        assertTrue("## Day 1" in markdown, markdown)
        assertTrue(markdown.lines().count { it.startsWith("- ") } == rows.size)
    }

    @Test
    fun `the game log of an untouched game says so`() {
        val markdown = GameLog.toMarkdown(newGame(), lookup)
        assertTrue("Nothing has happened yet." in markdown, markdown)
        assertTrue(GameLog.rows(newGame(), lookup).isEmpty())
    }
}
