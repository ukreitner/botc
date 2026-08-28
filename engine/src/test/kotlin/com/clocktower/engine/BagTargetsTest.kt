package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Playtest A-5: **the bag header and the validator disagreed.**
 *
 * The setup screen computed its "Need:" line and its four progress bars from
 * `allowedDistributions(playerCount, selected)` — with neither the Fabled ids
 * nor the setup acknowledgements — while `validateBag` was given both. So a
 * Sentinel game printed "Need: 5 townsfolk · 4 outsiders · …" and bars reading
 * `TF 6/5`, `OUT 3/4` over a bag the validator accepted and dealt; and a Lil'
 * Monsta game showed all four bars at target while the issue list underneath
 * said "Townsfolk: 4 in bag, expected 5".
 *
 * `Setup.bagTargets` is the one answer both now read. These tests assert the
 * property that matters: **a bag the targets accept has no team-count issue,
 * and a bag they reject has one.**
 */
class BagTargetsTest {

    private val data = GameData.loadDefault()

    private fun bag(vararg ids: String): List<Character> = ids.map { id ->
        data.character(id) ?: error("unknown character $id")
    }

    private fun teamIssues(issues: List<String>): List<String> =
        issues.filter { it.contains(" in bag, expected ") }

    /** Every team the targets accept ⇔ the validator raises no count issue. */
    private fun assertAgrees(
        bag: List<Character>,
        playerCount: Int,
        fabledIds: List<String> = emptyList(),
        inPlayIds: List<String> = emptyList(),
    ) {
        val targets = Setup.bagTargets(bag, playerCount, fabledIds, inPlayIds)
        val counts = Setup.bagCounts(bag, playerCount, inPlayIds)
        val issues = teamIssues(
            Setup.validateBag(
                bag = bag,
                playerCount = playerCount,
                fabledIds = fabledIds,
                inPlayIds = inPlayIds,
            ),
        )
        val unhappy = Setup.BAG_TEAMS.filterNot { team ->
            targets.getValue(team).accepts(counts[team] ?: 0)
        }
        assertEquals(
            unhappy.isEmpty(),
            issues.isEmpty(),
            "targets say $unhappy is wrong, the validator says $issues",
        )
    }

    // ---- the Sentinel, A-5's first repro ----------------------------------

    /** 12 players + Sentinel: 1, 2 or 3 Outsiders are all legal, and all say so. */
    @Test
    fun `the sentinel widens the outsider target the same way it widens the validator`() {
        val plain = Setup.bagTargets(emptyList(), 12)
        assertEquals(listOf(2), plain.getValue(Team.OUTSIDER).counts)

        val sentinel = Setup.bagTargets(emptyList(), 12, fabledIds = listOf("sentinel"))
        assertEquals(listOf(1, 2, 3), sentinel.getValue(Team.OUTSIDER).counts)
        assertEquals(listOf(6, 7, 8), sentinel.getValue(Team.TOWNSFOLK).counts)
        assertTrue(sentinel.getValue(Team.OUTSIDER).accepts(3))
        assertTrue(sentinel.getValue(Team.OUTSIDER).accepts(1))
        assertFalse(sentinel.getValue(Team.OUTSIDER).accepts(4))
    }

    /** The exact bag the tester built: 12 seats, Sentinel, 6/3/2/1. */
    @Test
    fun `a sentinel bag the validator accepts is not shown as incomplete`() {
        val bag = bag(
            "washerwoman", "librarian", "investigator", "chef", "empath", "fortuneteller",
            "butler", "drunk", "recluse",
            "poisoner", "baron",
            "imp",
        )
        // Baron is in it, so this is the Baron branch: 5/4/2/1 ± the Sentinel.
        assertAgrees(bag, playerCount = 12, fabledIds = listOf("sentinel"))
    }

    // ---- Lil' Monsta, A-5's second repro ----------------------------------

    /**
     * With the acknowledgement ticked the shape pins the bag to 7/0/3/0 at ten
     * players — and the header must say "no demon", not "1 demon".
     */
    @Test
    fun `an acknowledged Lil Monsta pins the demon target to zero`() {
        val targets = Setup.bagTargets(
            bag = emptyList(),
            playerCount = 10,
            inPlayIds = listOf("lilmonsta"),
        )
        assertEquals(listOf(0), targets.getValue(Team.DEMON).counts)
        assertEquals(listOf(3), targets.getValue(Team.MINION).counts)
        assertEquals(listOf(7), targets.getValue(Team.TOWNSFOLK).counts)
    }

    /** And the two never contradict each other on a real bag. */
    @Test
    fun `a legal Lil Monsta bag agrees with the validator`() {
        val bag = bag(
            "washerwoman", "librarian", "investigator", "chef", "empath", "fortuneteller", "undertaker",
            "poisoner", "spy", "baron",
            "lilmonsta",
        )
        assertAgrees(bag, playerCount = 10, inPlayIds = listOf("lilmonsta"))
    }

    /** The centre token fills no seat, whoever is counting. */
    @Test
    fun `the seat-filling bag never includes a token the shape forbids`() {
        val bag = bag(
            "washerwoman", "librarian", "investigator", "chef", "empath", "fortuneteller", "undertaker",
            "poisoner", "spy", "baron",
            "lilmonsta",
        )
        val filling = Setup.seatFillingBag(bag, 10, inPlayIds = listOf("lilmonsta"))
        assertEquals(10, filling.size)
        assertTrue(filling.none { it.id == "lilmonsta" })
        // Without the acknowledgement it is STILL not a seat: the shape comes
        // from the token being in the bag at all. "IN THE BAG · 9 / 8" (A-8).
        assertTrue(Setup.seatFillingBag(bag, 10).none { it.id == "lilmonsta" })
    }

    // ---- the general property ---------------------------------------------

    /** An ordinary Trouble Brewing bag, right and wrong, agrees both ways. */
    @Test
    fun `plain bags agree`() {
        val legal = bag(
            "washerwoman", "librarian", "investigator", "chef", "empath",
            "butler",
            "poisoner",
            "imp",
        )
        assertAgrees(legal, playerCount = 8)

        // One Outsider too many.
        val illegal = bag(
            "washerwoman", "librarian", "investigator", "chef",
            "butler", "recluse",
            "poisoner",
            "imp",
        )
        assertAgrees(illegal, playerCount = 8)
        val targets = Setup.bagTargets(illegal, 8)
        assertFalse(targets.getValue(Team.OUTSIDER).accepts(2))
    }

    /** A free team is never rendered as incomplete: the storyteller chooses. */
    @Test
    fun `an open-ended bracket leaves its team free`() {
        val kazali = data.character("kazali")
        if (kazali == null) return
        val targets = Setup.bagTargets(listOf(kazali), 10)
        // The Kazali's "[-? to +? Outsiders]" is a choice, so nothing is pinned
        // to a single number the bar could report as missing.
        val outsider = targets.getValue(Team.OUTSIDER)
        assertTrue(
            outsider.free || outsider.counts.isNotEmpty(),
            "outsiders must be free or a range, got $outsider",
        )
        assertTrue(outsider.accepts(outsider.counts.firstOrNull() ?: 0))
    }

    /** `target(have)` never points at a number the validator would reject. */
    @Test
    fun `the bar always aims at a legal count`() {
        val targets = Setup.bagTargets(emptyList(), 12, fabledIds = listOf("sentinel"))
        val outsider = targets.getValue(Team.OUTSIDER)
        for (have in 0..6) {
            val aim = outsider.target(have) ?: continue
            assertTrue(outsider.accepts(aim), "aimed at $aim, legal are ${outsider.counts}")
        }
        assertEquals(3, outsider.target(5))
        assertEquals(1, outsider.target(0))
    }
}
