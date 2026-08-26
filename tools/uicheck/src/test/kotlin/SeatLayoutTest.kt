package com.clocktower.grimoire.uicheck

import com.clocktower.engine.EffectGroup
import com.clocktower.grimoire.ui.components.labelCopies
import com.clocktower.grimoire.ui.components.pipGlyphSp
import com.clocktower.grimoire.ui.components.reminderFontSp
import com.clocktower.grimoire.ui.components.visiblePips
import com.clocktower.grimoire.ui.screens.SeatGeometry
import com.clocktower.grimoire.ui.theme.MIN_TEXT_SP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The measured half of WP10's acceptance: no seat card may overlap its
 * neighbour or be clipped, and no text may render below 11 sp.
 *
 * These are the numbers from grimoire-and-seats "Seat rendering: measured
 * geometry" — an iPhone 15 Pro in portrait leaves a 393 x 666 dp circle area
 * after the top bar (64), the nav bar (80) and the circle's own padding. That
 * is the phone the app is judged on, so it is the phone the test measures.
 *
 * Lives in `tools/uicheck` rather than `engine/src/test` because the allocator
 * is UI code under `app/`, which the `:engine` test source set cannot see;
 * uicheck already compiles every app source, so the pure functions are on its
 * test compile classpath. Run with `./gradlew -p tools/uicheck test`.
 */
class SeatLayoutTest {

    private val w = 393f
    private val h = 666f

    /** The four seat counts WP10's acceptance criterion names. */
    private val acceptanceCounts = listOf(7, 12, 15, 20)

    // ---- no overlap ---------------------------------------------------

    @Test
    fun `no seat card is taller than the gap to its neighbour`() {
        for (n in acceptanceCounts) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats a card of ${a.cardHeightDp} dp sits in a ${a.spacingDp} dp slot",
                a.cardHeightDp <= a.spacingDp + 0.01f,
            )
        }
    }

    @Test
    fun `budget never exceeds 96 percent of the spacing at any table size`() {
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats budgetH ${a.budgetHDp} > spacing ${a.spacingDp} * 0.96",
                a.budgetHDp <= a.spacingDp * 0.96f + 0.01f,
            )
        }
    }

    // ---- no clipping --------------------------------------------------

    @Test
    fun `every seat card fits the height it is measured with`() {
        // `CircleLayout` measures each seat with maxHeight = budgetH. A card
        // taller than that is the 13-16 seat bug: Column hands the leftover
        // (0 dp) to its LAST child, so the reminder row silently vanished.
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats the card is ${a.cardHeightDp} dp but is measured with ${a.budgetHDp} dp",
                a.cardHeightDp <= a.budgetHDp + 0.01f,
            )
        }
    }

    @Test
    fun `every seat card fits the width it is measured with`() {
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats the card is ${a.cardWidthDp} dp wide but is measured with ${a.budgetWDp} dp",
                a.cardWidthDp <= a.budgetWDp + 0.01f,
            )
        }
    }

    @Test
    fun `the pip row is never squeezed to nothing`() {
        // Either it has its own row, or the pips are drawn ON the token.
        // What must never happen again is "no row and no overlay" — which is
        // how tokens disappeared at 13-16 seats with no "+N" and no ellipsis.
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats the pips have nowhere to go",
                a.pipRowDp >= 16f || a.pipsOverlayToken,
            )
            assertTrue("at $n seats no pips are budgeted", a.pips >= 1)
            assertTrue("at $n seats the pip is ${a.pipDp} dp", a.pipDp >= 16f)
        }
    }

    // ---- the token stays recognisable ---------------------------------

    @Test
    fun `token size stays inside its floor and cap`() {
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats the token is ${a.tokenDp} dp",
                a.tokenDp >= SeatGeometry.TOKEN_MIN_DP - 0.01f &&
                    a.tokenDp <= SeatGeometry.TOKEN_MAX_DP + 0.01f,
            )
        }
    }

    @Test
    fun `the character name line is dropped before it can be squeezed`() {
        for (n in 5..20) {
            val a = SeatGeometry.allocate(n, w, h)
            if (a.showCharacterName) {
                val slack = a.budgetHDp - (a.nameDp + a.tokenDp + a.pipRowDp + SeatGeometry.GAP_DP)
                assertTrue(
                    "at $n seats the character name is shown with only $slack dp of room",
                    slack >= a.characterNameDp - 0.01f,
                )
            }
        }
    }

    @Test
    fun `the allocation reproduces the audited spacing table`() {
        // The audit measured these on the same 393 x 666 dp area. If the
        // ellipse or the inset ever drifts, this catches it.
        val expected = mapOf(7 to 185.3f, 12 to 113.9f, 15 to 94.5f, 20 to 70.9f)
        for ((n, spacing) in expected) {
            val a = SeatGeometry.allocate(n, w, h)
            assertTrue(
                "at $n seats spacing is ${a.spacingDp}, audited as $spacing",
                kotlin.math.abs(a.spacingDp - spacing) < 1.0f,
            )
        }
    }

    @Test
    fun `a wider or shorter viewport is still overlap-free`() {
        // Landscape phone, small phone, tablet — the allocator is not tuned
        // to one screen.
        val viewports = listOf(393f to 666f, 852f to 300f, 320f to 560f, 800f to 1100f)
        for ((vw, vh) in viewports) {
            for (n in 5..20) {
                val a = SeatGeometry.allocate(n, vw, vh)
                assertTrue(
                    "$n seats on ${vw}x$vh: card ${a.cardHeightDp} dp vs budget ${a.budgetHDp} dp",
                    a.cardHeightDp <= a.budgetHDp + 0.01f,
                )
            }
        }
    }

    // ---- text floors ---------------------------------------------------

    @Test
    fun `no reminder token renders below 11 sp`() {
        // The old circle drew labels at max(size / 5, 6) sp: 6.0 sp at both
        // 22 dp and 26 dp, roughly half Material's smallest text style.
        var d = 12f
        while (d <= 96f) {
            assertTrue("a $d dp token would render at ${reminderFontSp(d)} sp", reminderFontSp(d) >= MIN_TEXT_SP)
            assertTrue("a $d dp pip would render at ${pipGlyphSp(d)} sp", pipGlyphSp(d) >= MIN_TEXT_SP)
            d += 1f
        }
    }

    @Test
    fun `the 22 dp circle token is the case the audit measured`() {
        assertEquals(MIN_TEXT_SP, reminderFontSp(22f), 0.001f)
    }

    // ---- pip ordering --------------------------------------------------

    @Test
    fun `pips are chosen by priority, not by placement order`() {
        // Placement order: "Is the Drunk" (IDENTITY), "Know" (INFO),
        // "Poisoned" (IMPAIRED), "Master" (MADNESS). With a 2-pip budget the
        // old takeLast(2) showed Poisoned + Master and hid the two tokens a
        // storyteller must never forget.
        val placed = listOf(
            EffectGroup.IDENTITY,
            EffectGroup.INFO,
            EffectGroup.IMPAIRED,
            EffectGroup.MADNESS,
        )
        val row = visiblePips(placed, budget = 2)
        assertEquals(listOf(EffectGroup.IMPAIRED), row.shown)
        assertEquals(3, row.hidden)

        val wider = visiblePips(placed, budget = 4)
        assertEquals(
            listOf(EffectGroup.IMPAIRED, EffectGroup.MADNESS, EffectGroup.IDENTITY, EffectGroup.INFO),
            wider.shown,
        )
        assertEquals(0, wider.hidden)
    }

    @Test
    fun `pending death always outranks everything else`() {
        val row = visiblePips(listOf(EffectGroup.MARKER, EffectGroup.PENDING_DEATH), budget = 1)
        assertEquals(listOf(EffectGroup.PENDING_DEATH), row.shown)
        assertEquals(1, row.hidden)
        val two = visiblePips(listOf(EffectGroup.MARKER, EffectGroup.PENDING_DEATH), budget = 2)
        assertEquals(listOf(EffectGroup.PENDING_DEATH, EffectGroup.MARKER), two.shown)
    }

    // ---- multi-copy token labels --------------------------------------

    @Test
    fun `repeated labels collapse to one chip with a copy count`() {
        // characters.json lists an N-copy reminder N times (the green leaves
        // on the physical token) — the Pukka owns two "Poisoned" and one
        // "Dead" (FOLLOWUPS, WP10/WP8).
        val copies = labelCopies(listOf("Poisoned", "Poisoned", "Dead"))
        assertEquals(2, copies.size)
        assertEquals("Poisoned", copies[0].label)
        assertEquals(2, copies[0].copies)
        assertEquals("Dead", copies[1].label)
        assertEquals(1, copies[1].copies)
    }

    @Test
    fun `copy counting is case-insensitive and keeps the official spelling`() {
        // Lead D5: comparisons are case-insensitive, and the first spelling
        // seen is the official Title Case one from the data file.
        val copies = labelCopies(listOf("No Ability", "no ability", "  No Ability  "))
        assertEquals(1, copies.size)
        assertEquals("No Ability", copies[0].label)
        assertEquals(3, copies[0].copies)
    }
}
