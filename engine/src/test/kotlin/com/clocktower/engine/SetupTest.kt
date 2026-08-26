package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupTest {

    private val data = GameData.loadDefault()

    private companion object {
        val EDITIONS = setOf("tb", "bmr", "sv")
    }

    @Test
    fun `official distribution table`() {
        assertEquals(Distribution(3, 0, 1, 1), Setup.distributionFor(5))
        assertEquals(Distribution(3, 1, 1, 1), Setup.distributionFor(6))
        assertEquals(Distribution(5, 0, 1, 1), Setup.distributionFor(7))
        assertEquals(Distribution(5, 1, 1, 1), Setup.distributionFor(8))
        assertEquals(Distribution(5, 2, 1, 1), Setup.distributionFor(9))
        assertEquals(Distribution(7, 0, 2, 1), Setup.distributionFor(10))
        assertEquals(Distribution(7, 1, 2, 1), Setup.distributionFor(11))
        assertEquals(Distribution(7, 2, 2, 1), Setup.distributionFor(12))
        assertEquals(Distribution(9, 0, 3, 1), Setup.distributionFor(13))
        assertEquals(Distribution(9, 1, 3, 1), Setup.distributionFor(14))
        assertEquals(Distribution(9, 2, 3, 1), Setup.distributionFor(15))
        assertEquals(Distribution(11, 0, 4, 1), Setup.distributionFor(16))
        assertEquals(Distribution(11, 1, 4, 1), Setup.distributionFor(17))
        assertEquals(Distribution(11, 2, 4, 1), Setup.distributionFor(18))
        assertEquals(Distribution(13, 0, 5, 1), Setup.distributionFor(19))
        assertEquals(Distribution(13, 1, 5, 1), Setup.distributionFor(20))
        for (n in Setup.MIN_PLAYERS..Setup.MAX_PLAYERS) {
            assertEquals(n, Setup.distributionFor(n).total)
        }
    }

    @Test
    fun `baron adds two outsiders from townsfolk`() {
        val baron = assertNotNull(data.character("baron"))
        val mod = assertNotNull(Setup.modifierFor(baron))
        assertEquals(2, mod.outsiderDelta)
        val dist = Setup.distributionFor(10) + mod
        assertEquals(Distribution(5, 2, 2, 1), dist)
        assertEquals(10, dist.total)
    }

    @Test
    fun `fang gu adds one outsider and vigormortis removes one`() {
        val fangGu = assertNotNull(Setup.modifierFor(assertNotNull(data.character("fanggu"))))
        assertEquals(1, fangGu.outsiderDelta)
        val vigor = assertNotNull(Setup.modifierFor(assertNotNull(data.character("vigormortis"))))
        assertEquals(-1, vigor.outsiderDelta)
    }

    @Test
    fun `combined setup modifiers are applied before clamping regardless of order`() {
        val baron = assertNotNull(data.character("baron"))
        val vigor = assertNotNull(data.character("vigormortis"))
        val expected = Distribution(6, 1, 2, 1)

        assertEquals(expected, Setup.adjustedDistribution(10, listOf(baron, vigor)))
        assertEquals(expected, Setup.adjustedDistribution(10, listOf(vigor, baron)))
    }

    @Test
    fun `lil monsta swaps the demon slot for a minion`() {
        // Lead D18/D54: 10 players -> 7 / 0 / 3 / 0, and Lil' Monsta is a
        // token in the centre, never a seat.
        val shape = assertNotNull(
            Setup.bagShapeFor("lilmonsta", Setup.distributionFor(10), 10),
        )
        assertEquals(7..7, shape.townsfolk)
        assertEquals(0..0, shape.outsiders)
        assertEquals(3..3, shape.minions)
        assertEquals(0..0, shape.demons)
        assertEquals(setOf("lilmonsta"), shape.forbidInBag)
    }

    @Test
    fun `godfather choice defaults applied but flagged as choice`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("godfather"))))
        assertTrue(mod.choice)
    }

    @Test
    fun `non setup characters have no modifier`() {
        assertNull(Setup.modifierFor(assertNotNull(data.character("washerwoman"))))
        assertNull(Setup.modifierFor(assertNotNull(data.character("imp"))))
    }

    @Test
    fun `negative outsider modifier clamps at zero`() {
        // 10 players base has 0 outsiders; Vigormortis's -1 must not go
        // negative or steal an extra townsfolk.
        val vigor = assertNotNull(Setup.modifierFor(assertNotNull(data.character("vigormortis"))))
        val dist = Setup.distributionFor(10) + vigor
        assertEquals(Distribution(7, 0, 2, 1), dist)
        // With outsiders present it applies normally: 14p is 9/1/3/1.
        assertEquals(Distribution(10, 0, 3, 1), Setup.distributionFor(14) + vigor)
    }

    @Test
    fun `summoner removes the demon from setup`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("summoner"))))
        assertEquals(-1, mod.demonDelta)
        val dist = Setup.distributionFor(7) + mod
        assertEquals(Distribution(6, 0, 1, 0), dist)
        assertEquals(7, dist.total)
    }

    /**
     * Retired with lead D28: `TEAM_WARPING_IDS` relaxed every count for
     * Atheist / Legion / Riot, which let a 4-Minion Riot bag through. Each of
     * them is now an explicit [BagShape] row — or, for the Riot, none at all.
     */
    @Test
    fun `team warping characters carry explicit bag shapes instead`() {
        val base = Setup.distributionFor(10)

        val atheist = assertNotNull(Setup.bagShapeFor("atheist", base, 10))
        assertEquals(0..0, atheist.minions)
        assertEquals(0..0, atheist.demons)

        val legion = assertNotNull(Setup.bagShapeFor("legion", base, 10))
        assertEquals(0..0, legion.minions, "Legion's 0 Minions is firm")
        assertTrue(legion.advisory, "the Legion ratio only warns")

        // Riot is an ordinary Demon in an ordinary bag.
        assertNull(Setup.bagShapeFor("riot", base, 10))
        assertNull(Setup.modifierFor(assertNotNull(data.character("riot"))))
        assertTrue("riot" !in Setup.DUPLICABLE)
    }

    @Test
    fun `huntsman adds an outsider slot and requires the damsel`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("huntsman"))))
        assertEquals(1, mod.outsiderDelta)
        assertEquals("damsel", mod.requiredCompanionId)
        assertEquals("king", Setup.modifierFor(assertNotNull(data.character("choirboy")))?.requiredCompanionId)
    }

    @Test
    fun `marionette carries text but zero deltas`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("marionette"))))
        assertEquals(0, mod.outsiderDelta)
        assertEquals(0, mod.minionDelta)
        assertTrue(mod.text.isNotBlank())
    }

    // ---- bag shapes (lead D18, D28) ----

    private fun chars(vararg ids: String): List<Character> =
        ids.map { assertNotNull(data.character(it), it) }

    /** [n] plain (no setup bracket) official characters of one team. */
    private fun teamPool(team: Team, n: Int): List<Character> =
        data.characters
            .filter { it.team == team && it.edition in EDITIONS && !it.setup }
            .take(n)
            .also { assertEquals(n, it.size, "not enough plain $team characters") }

    @Test
    fun `lil monsta validates seven zero three zero with no token in the bag`() {
        val bag = teamPool(Team.TOWNSFOLK, 7) + teamPool(Team.MINION, 3)
        assertEquals(10, bag.size)
        assertEquals(
            emptyList(),
            Setup.validateBag(bag, 10, inPlayIds = listOf("lilmonsta")),
        )
    }

    @Test
    fun `a lil monsta token counted as a seat is reported`() {
        val bag = teamPool(Team.TOWNSFOLK, 7) + teamPool(Team.MINION, 2) +
            chars("lilmonsta")
        val issues = Setup.validateBag(bag, 10)
        assertTrue(issues.any { "token, not a seat" in it }, issues.toString())
    }

    @Test
    fun `kazali and lord of typhon accept a bag with no minions`() {
        for (demon in listOf("kazali", "lordoftyphon")) {
            val bag = teamPool(Team.TOWNSFOLK, 9) + chars(demon)
            assertEquals(emptyList(), Setup.validateBag(bag, 10), demon)
        }
    }

    @Test
    fun `summoner accepts a bag with no demon`() {
        val bag = teamPool(Team.TOWNSFOLK, 6) + teamPool(Team.OUTSIDER, 1) + chars("summoner")
        assertEquals(emptyList(), Setup.validateBag(bag, 8))
    }

    @Test
    fun `atheist rejects a bag containing a demon`() {
        val legal = teamPool(Team.TOWNSFOLK, 6) + chars("atheist")
        assertEquals(emptyList(), Setup.validateBag(legal, 7))

        val withDemon = teamPool(Team.TOWNSFOLK, 5) + chars("atheist", "imp")
        val issues = Setup.validateBag(withDemon, 7)
        assertTrue(issues.any { it.startsWith("Demon:") }, issues.toString())
    }

    @Test
    fun `legion blocks minions but only warns about the ratio`() {
        val bag = teamPool(Team.TOWNSFOLK, 8) + chars("legion", "legion")
        assertEquals(emptyList(), Setup.validateBag(bag, 10), "the ratio never blocks")
        assertTrue(
            Setup.bagWarnings(bag, 10).any { "most players should be legion" in it },
            Setup.bagWarnings(bag, 10).toString(),
        )

        val withMinion = teamPool(Team.TOWNSFOLK, 7) + teamPool(Team.MINION, 1) +
            chars("legion", "legion")
        assertTrue(
            Setup.validateBag(withMinion, 10).any { it.startsWith("Minion:") },
            Setup.validateBag(withMinion, 10).toString(),
        )
    }

    @Test
    fun `riot is an ordinary demon in an ordinary bag`() {
        val legal = teamPool(Team.TOWNSFOLK, 7) + teamPool(Team.MINION, 2) + chars("riot")
        assertEquals(emptyList(), Setup.validateBag(legal, 10))

        // Before D28 the team-warping bracket let this through unchecked.
        val fourMinions = teamPool(Team.TOWNSFOLK, 5) + teamPool(Team.MINION, 4) + chars("riot")
        assertTrue(
            Setup.validateBag(fourMinions, 10).any { it.startsWith("Minion:") },
            Setup.validateBag(fourMinions, 10).toString(),
        )
    }

    @Test
    fun `marionette removes one minion token only in three minion games`() {
        assertNull(Setup.bagShapeFor("marionette", Setup.distributionFor(10), 10))
        val shape = assertNotNull(
            Setup.bagShapeFor("marionette", Setup.distributionFor(14), 14),
        )
        assertEquals(2..2, shape.minions)
    }

    @Test
    fun `randomBag draws from the shape, not the base distribution`() {
        val pool = data.resolve(data.builtInScripts().first { it.id == "tb" })
            .filter { it.team != Team.DEMON } + chars("kazali")
        val bag = assertNotNull(Setup.randomBag(pool, 10, kotlin.random.Random(5)))
        assertEquals(10, bag.size)
        assertEquals(0, bag.count { it.team == Team.MINION }, "the Kazali makes them on night 1")
        assertEquals(1, bag.count { it.id == "kazali" })
        assertEquals(emptyList(), Setup.validateBag(bag, 10))
    }

    @Test
    fun `randomBag still fills ordinary scripts at every player count`() {
        for (script in data.builtInScripts()) {
            val pool = data.resolve(script)
            for (n in listOf(5, 7, 10, 12, 15)) {
                val bag = assertNotNull(Setup.randomBag(pool, n, kotlin.random.Random(n.toLong())), "${script.id} $n")
                assertEquals(n, bag.size, "${script.id} $n")
                assertEquals(emptyList(), Setup.validateBag(bag, n), "${script.id} $n")
            }
        }
    }

    @Test
    fun `xaan pins the outsider count once X is chosen`() {
        val base = Setup.distributionFor(12)
        assertNull(Setup.bagShapeFor("xaan", base, 12))
        var state = Seats.newGame(data.builtInScripts().first { it.id == "tb" }, (1..12).map { "P$it" })
        state = Decisions.set(state, Decisions.XAAN_X, "3")
        val shape = assertNotNull(Setup.bagShapeFor("xaan", base, 12, state))
        assertEquals(3..3, shape.outsiders)
    }
}
