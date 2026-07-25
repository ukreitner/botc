package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupTest {

    private val data = GameData.loadDefault()

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
    fun `lil monsta adds a minion`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("lilmonsta"))))
        assertEquals(1, mod.minionDelta)
        val dist = Setup.distributionFor(10) + mod
        assertEquals(10, dist.total)
        assertEquals(3, dist.minions)
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

    @Test
    fun `team warping brackets relax all counts`() {
        for (id in listOf("atheist", "legion", "riot")) {
            val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character(id))), id)
            assertEquals(Team.entries.toSet(), mod.choiceTeams, id)
        }
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
}
