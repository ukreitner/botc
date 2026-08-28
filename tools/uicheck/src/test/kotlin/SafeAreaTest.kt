package com.clocktower.grimoire.uicheck

import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.components.ActionRowHeight
import com.clocktower.grimoire.ui.components.BottomActionMargin
import com.clocktower.grimoire.ui.components.bottomActionClearance
import com.clocktower.grimoire.ui.components.bottomActionPadding
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom safe area, measured.
 *
 * A storyteller could not close a full-screen show card on a phone: its FLIP /
 * HOLD TO CLOSE row was pinned to the *physical* bottom of the screen, so the
 * home indicator covered all but a sliver of both controls. Overlay layers
 * (`Dialog`, `Popup`, `ModalBottomSheet`) are hosted above the shell's own
 * inset padding, so each has to apply the safe area itself —
 * `ui/components/SafeArea.kt` is where that lives.
 *
 * Half of this file measures the pure arithmetic; the other half reads the UI
 * sources, because the thing that actually broke was a call site forgetting to
 * ask. Lives in `tools/uicheck` because the helpers are `app/` code, which
 * `:engine`'s test source set cannot see.
 */
class SafeAreaTest {

    // ---- the pure helpers -------------------------------------------------

    /** A phone with no indicator gets exactly the margin, and nothing shifts. */
    @Test
    fun `no inset leaves the historical padding untouched`() {
        assertEquals(BottomActionMargin, bottomActionPadding(0.dp))
        // 96 dp is what FullScreenShow's card body reserved before the fix.
        assertEquals(96.dp, bottomActionClearance(0.dp))
    }

    /** The whole point: a pinned row clears the indicator by a usable margin. */
    @Test
    fun `a pinned row clears the indicator by at least the margin`() {
        for (safeBottom in listOf(0.dp, 8.dp, 21.dp, 34.dp, 48.dp)) {
            val padding = bottomActionPadding(safeBottom)
            assertTrue(
                "a $safeBottom inset left only ${padding - safeBottom} above the indicator",
                padding - safeBottom >= BottomActionMargin,
            )
            assertTrue("padding must clear the inset itself", padding > safeBottom)
        }
        assertEquals(58.dp, bottomActionPadding(34.dp))
    }

    /** The body is pushed up far enough that the row can never cover it. */
    @Test
    fun `the body clears the row that is pinned below it`() {
        for (safeBottom in listOf(0.dp, 21.dp, 34.dp, 48.dp)) {
            val clearance = bottomActionClearance(safeBottom)
            val rowTop = bottomActionPadding(safeBottom) + ActionRowHeight
            assertTrue(
                "body clearance $clearance does not reach the row top $rowTop",
                clearance > rowTop,
            )
        }
    }

    /** Both grow with the inset — a bigger indicator never gets less room. */
    @Test
    fun `padding grows with the inset`() {
        val insets = listOf(0.dp, 8.dp, 21.dp, 34.dp, 48.dp)
        for ((smaller, bigger) in insets.zipWithNext()) {
            assertTrue(bottomActionPadding(bigger) > bottomActionPadding(smaller))
            assertTrue(bottomActionClearance(bigger) > bottomActionClearance(smaller))
        }
    }

    /** A caller may widen the margin, never silently lose the inset. */
    @Test
    fun `a custom margin still adds the inset`() {
        assertEquals(74.dp, bottomActionPadding(34.dp, margin = 40.dp))
    }

    // ---- the call sites ---------------------------------------------------

    /**
     * Every bottom sheet in the app ends its content with the helper. `24.dp`
     * was the app-wide convention and it is exactly the margin the helper adds
     * on top of the inset, so a literal left behind is a sheet whose last
     * button sits on the home indicator in the PWA.
     */
    @Test
    fun `no sheet content pads its bottom with a bare literal`() {
        val offenders = uiSources()
            .filter { it.readText().contains("padding(bottom = 24.dp)") }
            .map { it.name }
        assertEquals(
            "these files still pad a sheet's bottom with a literal instead of " +
                "overlayBottomPadding(), so the home indicator covers their last row",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * A full-screen `Dialog` — `usePlatformDefaultWidth = false` — is hosted
     * above the shell's inset padding on every platform, so it MUST apply the
     * safe area itself. A new one that forgets fails here rather than at a
     * table, mid-game, with a card that cannot be closed.
     */
    @Test
    fun `every full-screen dialog applies the safe area`() {
        val helpers = listOf(
            "overlaySafeAreaPadding",
            "bottomActionPadding",
            "bottomActionClearance",
            "overlayBottomPadding",
        )
        val offenders = uiSources()
            .map { it to it.readText() }
            .filter { (_, text) -> text.contains("usePlatformDefaultWidth = false") }
            .filterNot { (_, text) -> helpers.any { text.contains(it) } }
            .map { (file, _) -> file.name }
        assertTrue(
            "a full-screen Dialog must be found somewhere — the grep is wrong",
            uiSources().any { it.readText().contains("usePlatformDefaultWidth = false") },
        )
        assertEquals(
            "these files host a full-screen Dialog without applying the safe " +
                "area from ui/components/SafeArea.kt",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The show card specifically: its action row is pinned with
     * `align(Alignment.BottomCenter)`, and the padding underneath it must come
     * from the helper rather than a literal.
     */
    @Test
    fun `the show card's pinned row is padded by the helper`() {
        val lines = uiSource("ShowCards.kt").readLines()
        val pinned = lines.withIndex().filter { it.value.contains("align(Alignment.Bottom") }
        assertEquals("FullScreenShow pins exactly one action row", 1, pinned.size)
        val window = lines.subList(pinned[0].index, minOf(pinned[0].index + 6, lines.size))
        assertTrue(
            "the pinned row at line ${pinned[0].index + 1} does not use bottomActionPadding: $window",
            window.any { it.contains("bottomActionPadding(") },
        )
    }

    /**
     * The read-only grimoire is the one screen a PLAYER holds the phone for.
     * Its action row sat entirely under the gesture inset — a 4 px sliver — and
     * the dialog refused back-press, so whoever was handed the phone was stuck
     * (playtest D, P1-7). Both stages pin their row with the helper, the column
     * is bounded so the row cannot be clipped off the bottom, and back works.
     */
    @Test
    fun `the read-only grimoire dialog can be left, and its buttons are reachable`() {
        val text = uiSource("GameShell.kt").readText()
        assertTrue(
            "GameShell hosts the read-only grimoire dialog",
            text.contains("usePlatformDefaultWidth = false"),
        )
        assertTrue(
            "the dialog a player holds must accept back-press",
            text.contains("dismissOnBackPress = true") &&
                !text.contains("dismissOnBackPress = false"),
        )
        assertEquals(
            "both stages of the read-only grimoire pin their action row with " +
                "overlayBottomPadding()",
            2,
            Regex("""padding\(bottom = overlayBottomPadding\(\)\)""").findAll(text).count(),
        )
        assertTrue(
            "the dialog's column must be bounded, or its last row is clipped away",
            text.contains(".fillMaxSize()") && text.contains(".overlaySafeAreaPadding()"),
        )
        assertTrue(
            "the dialog window's own offset must be measured in the shell's " +
                "composition and padded for — inside the dialog every inset is zero",
            text.contains("val dialogBottomFix = dialogWindowBottomFix()") &&
                text.contains("padding(bottom = dialogBottomFix)"),
        )
    }

    // ---- source lookup ----------------------------------------------------

    private fun uiSource(name: String): File =
        uiSources().firstOrNull { it.name == name } ?: error("no UI source named $name")

    private fun uiSources(): List<File> = uiRoot()
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
        .also { assertTrue("no UI sources found under ${uiRoot()}", it.isNotEmpty()) }

    /** `tools/uicheck` is the test's working directory; the app is two up. */
    private fun uiRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, UI_PATH)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not find $UI_PATH above ${File("").absolutePath}")
    }

    private companion object {
        const val UI_PATH = "app/src/main/java/com/clocktower/grimoire/ui"
    }
}
