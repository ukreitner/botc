package com.clocktower.engine

import org.junit.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The CI gates of ARCHITECTURE §4 (WP12) — grep-style structural rules that no
 * unit test can express, run as JUnit so `./gradlew :engine:test` enforces them.
 *
 * Three invariants:
 *  1. **I1 / §3.4.3** — no character id in `NightScreen.kt` or `DayScreen.kt`.
 *     Per-character behaviour belongs in the `engine/rules/` registry.
 *  2. **D26 / §3.4.5** — `GameViewModel.kt` and `WebGameViewModel.kt` contain no
 *     `GameActions.` call; every verb goes through `GameActionsApi`.
 *  3. **D5 / §3.4.1** — no reminder label literal outside `engine/rules/` is
 *     compared with `==`; use `Tokens.key(sourceId, label)`.
 *
 * Each gate ships as a pair: the real gate, `@Ignore`d while the package that
 * owns the offending file has not landed, and a **live baseline** test that
 * fails the moment a NEW violation appears. The merger flips the `@Ignore`
 * off when the named package lands; the baseline test stops the count growing
 * in the meantime. The `@Ignore` reason records the census as of this commit.
 */
class SourceGatesTest {

    private val data = GameData.loadDefault()

    private val nightScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt"
    private val dayScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt"
    private val androidViewModel = "app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt"
    private val webViewModel = "web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt"

    /** Any double-quoted literal on a line. */
    private val stringLiteral = Regex("\"([^\"\\\\\\n]*)\"")

    /** `label == "…"` / `label!! == "…"`. */
    private val labelEqualsLiteral = Regex("\\blabel\\s*(?:!!)?\\s*==\\s*\"([^\"\\\\\\n]*)\"")

    /** `"…" == someExpression.label`. */
    private val literalEqualsLabel = Regex("\"([^\"\\\\\\n]*)\"\\s*==\\s*[\\w.!]*\\blabel\\b")

    /** One flagged line, in the form a reviewer can paste into an editor. */
    private data class Hit(val where: String, val line: Int, val what: String, val text: String) {
        override fun toString(): String = "$where:$line  [$what]  $text"
    }

    /**
     * Source lines with comments dropped, so a KDoc example that *describes*
     * the anti-pattern is not itself a violation.
     */
    private fun codeLines(text: String): List<IndexedValue<String>> =
        text.lines().withIndex().filterNot { (_, line) ->
            val t = line.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }

    // ------------------------------------------------------------------
    // Gate 1 — no character id in the night and day screens (I1, §3.4.3)
    // ------------------------------------------------------------------

    private fun characterIdLiterals(relative: String): List<Hit> {
        val text = assertNotNull(RepoFiles.textOrNull(relative), "missing source file $relative")
        val ids = data.characters.map { it.id }.toSet()
        return codeLines(text).flatMap { (index, line) ->
            stringLiteral.findAll(line)
                .map { it.groupValues[1] }
                .filter { it in ids }
                .map { Hit(relative, index + 1, it, line.trim()) }
                .toList()
        }
    }

    @Test
    fun `DayScreen branches on no character id`() {
        val hits = characterIdLiterals(dayScreen)
        assertTrue(hits.isEmpty(), "character ids in DayScreen.kt (I1):\n" + hits.joinToString("\n"))
    }

    @Test
    @Ignore("WP8 — NightScreen.kt still names 7 character ids (snakecharmer, fanggu x2, professor x2, imp, scarletwoman) in its four hard-coded resolvers; enable when WP8 lands the registry-driven screen")
    fun `NightScreen branches on no character id`() {
        val hits = characterIdLiterals(nightScreen)
        assertTrue(hits.isEmpty(), "character ids in NightScreen.kt (I1):\n" + hits.joinToString("\n"))
    }

    @Test
    fun `character ids in the night screen do not spread further`() {
        // Live baseline while WP8 is outstanding: the four legacy resolvers may
        // stay, but no NEW character id may appear.
        val known = setOf("snakecharmer", "fanggu", "professor", "imp", "scarletwoman")
        val hits = characterIdLiterals(nightScreen)
        val fresh = hits.filterNot { it.what in known }
        assertTrue(fresh.isEmpty(), "new character ids in NightScreen.kt (I1):\n" + fresh.joinToString("\n"))
        assertTrue(
            hits.size <= 7,
            "NightScreen.kt character-id count grew from 7 to ${hits.size}:\n" + hits.joinToString("\n"),
        )
    }

    // ------------------------------------------------------------------
    // Gate 2 — the view models call no engine verb directly (D26, §3.4.5)
    // ------------------------------------------------------------------

    private fun gameActionsCalls(relative: String): List<Hit> {
        val text = assertNotNull(RepoFiles.textOrNull(relative), "missing source file $relative")
        return codeLines(text)
            .filter { (_, line) -> "GameActions." in line }
            .map { (index, line) -> Hit(relative, index + 1, "GameActions.", line.trim()) }
    }

    @Test
    @Ignore("WP0 — GameViewModel.kt:85 and WebGameViewModel.kt:67 each still call GameActions.newGame(...) (1 hit per file); expose newGame through GameActionsApi and enable")
    fun `view models contain no GameActions call`() {
        val hits = gameActionsCalls(androidViewModel) + gameActionsCalls(webViewModel)
        assertTrue(
            hits.isEmpty(),
            "the view models must call engine verbs through GameActionsApi (D26):\n" +
                hits.joinToString("\n"),
        )
    }

    @Test
    fun `view models add no new GameActions call`() {
        // Live baseline while the last WP0 leftover stands: exactly one call in
        // each file, and both are `newGame`.
        for (relative in listOf(androidViewModel, webViewModel)) {
            val hits = gameActionsCalls(relative)
            assertEquals(1, hits.size, "$relative GameActions. call count:\n" + hits.joinToString("\n"))
            assertTrue(
                hits.all { "GameActions.newGame(" in it.text },
                "$relative gained a GameActions call that is not newGame (D26):\n" +
                    hits.joinToString("\n"),
            )
        }
    }

    // ------------------------------------------------------------------
    // Gate 3 — no label literal compared with `==` outside engine/rules (D5)
    // ------------------------------------------------------------------

    /** Directories scanned for label equality; `engine/rules/` is exempt by design. */
    private val scannedRoots = listOf("engine/src/main/kotlin", "app/src/main", "web/src")

    private fun labelEqualityHits(): List<Hit> {
        val labels = data.characters
            .flatMap { it.allReminders }
            .map { it.lowercase() }
            .toSet()
        val hits = mutableListOf<Hit>()
        for (root in scannedRoots) {
            for (file in RepoFiles.kotlinSources(root)) {
                val relative = file.relativeTo(RepoFiles.root).path
                if ("${java.io.File.separator}rules${java.io.File.separator}" in file.path) continue
                for ((index, line) in codeLines(file.readText())) {
                    val literals = labelEqualsLiteral.findAll(line).map { it.groupValues[1] } +
                        literalEqualsLabel.findAll(line).map { it.groupValues[1] }
                    for (literal in literals) {
                        if (literal.lowercase() in labels) {
                            hits += Hit(relative, index + 1, literal, line.trim())
                        }
                    }
                }
            }
        }
        return hits
    }

    @Test
    @Ignore("WP2 + WP11 — 3 label literals are still compared with ==: InfoCalc.kt:147 \"no ability\" (WP2), GameExtras.kt:430 \"Is the Drunk\" and :496 \"Is the Marionette\" (WP11, and both are the pre-WP5 spelling); enable when they use Tokens.key")
    fun `no reminder label literal outside engine rules is compared with equals`() {
        val hits = labelEqualityHits()
        assertTrue(
            hits.isEmpty(),
            "compare labels with Tokens.key(sourceId, label), never == (lead D5, §3.4.1):\n" +
                hits.joinToString("\n"),
        )
    }

    @Test
    fun `label equality does not spread beyond the three known sites`() {
        // Live baseline while WP2 and WP11 are outstanding.
        val known = setOf(
            "engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt" to "no ability",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt" to "Is the Drunk",
            "app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt" to "Is the Marionette",
        )
        val hits = labelEqualityHits()
        val fresh = hits.filterNot { (it.where.replace('\\', '/') to it.what) in known }
        assertTrue(
            fresh.isEmpty(),
            "new label == comparisons (lead D5, §3.4.1):\n" + fresh.joinToString("\n"),
        )
        assertTrue(
            hits.size <= 3,
            "label == count grew from 3 to ${hits.size}:\n" + hits.joinToString("\n"),
        )
    }

    // ------------------------------------------------------------------
    // The gates are only worth anything if they can see the sources.
    // ------------------------------------------------------------------

    @Test
    fun `the gates can find every file they police`() {
        for (relative in listOf(nightScreen, dayScreen, androidViewModel, webViewModel)) {
            assertNotNull(RepoFiles.textOrNull(relative), "gate target not found: $relative")
        }
        for (root in scannedRoots) {
            assertTrue(
                RepoFiles.kotlinSources(root).isNotEmpty(),
                "no Kotlin sources under $root — the label gate would pass vacuously",
            )
        }
    }
}
