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
        for (n in 5..15) {
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
    fun `marionette carries text but zero deltas`() {
        val mod = assertNotNull(Setup.modifierFor(assertNotNull(data.character("marionette"))))
        assertEquals(0, mod.outsiderDelta)
        assertEquals(0, mod.minionDelta)
        assertTrue(mod.text.isNotBlank())
    }
}
