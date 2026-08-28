package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The CI gates of ARCHITECTURE §4 (WP12) — grep-style structural rules that no
 * unit test can express, run as JUnit so `./gradlew :engine:test` enforces them.
 *
 * Three invariants:
 *  1. **I1 / §3.4.3** — no character id in `NightScreen.kt` or anywhere in
 *     the Day tab's package. Per-character behaviour belongs in `engine/rules/`.
 *  2. **D26 / §3.4.5** — `GameViewModel.kt` and `WebGameViewModel.kt` contain no
 *     `GameActions.` call; every verb goes through `GameActionsApi`.
 *  3. **D5 / §3.4.1** — no reminder label literal outside `engine/rules/` is
 *     compared with `==`; use `Tokens.key(sourceId, label)`.
 *
 * Every gate is LIVE as of W6A: the last `@Ignore` came off gate 2 when
 * `GameActionsApi.newGame` landed and both view models stopped naming
 * `GameActions` at all. Gate 3 keeps its live baseline test alongside the real
 * one, because three tolerated `label ==` sites still stand (lead D5); a new
 * one fails the baseline immediately rather than waiting for a cleanup.
 */
class SourceGatesTest {

    private val data = GameData.loadDefault()

    private val nightScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt"
    private val dayScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt"
    private val androidViewModel = "app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt"
    private val webViewModel = "web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt"

    /** Everything the Day tab is made of (WP9 split it up); `DayScreen.kt` leads. */
    private val daySources = listOf(
        dayScreen,
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayModel.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayCards.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/SaidModel.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/SaidSheet.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationModel.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/screens/day/ExecutionSheet.kt",
        "app/src/main/java/com/clocktower/grimoire/ui/components/Timer.kt",
    )

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
    fun `NightScreen branches on no character id`() {
        val hits = characterIdLiterals(nightScreen)
        assertTrue(hits.isEmpty(), "character ids in NightScreen.kt (I1):\n" + hits.joinToString("\n"))
    }

    /**
     * The Day tab is more than one file: WP9 split the composer, the models,
     * the nomination panel and the execution sheet out of `DayScreen.kt`, and
     * the shared timer moved to `components/Timer.kt`. The gate follows them,
     * so "no character id in the day screen" cannot be satisfied by moving the
     * `when (characterId)` one directory down. Promoted here from
     * `tools/uicheck` (WP9) so `./gradlew :engine:test` is the one place the
     * structural rules run.
     */
    @Test
    fun `the day package names no character id`() {
        val hits = daySources.flatMap { characterIdLiterals(it) }
        assertTrue(
            hits.isEmpty(),
            "per-character behaviour belongs in engine/rules/ (I1):\n" + hits.joinToString("\n"),
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
    fun `view models contain no GameActions call`() {
        val hits = gameActionsCalls(androidViewModel) + gameActionsCalls(webViewModel)
        assertTrue(
            hits.isEmpty(),
            "the view models must call engine verbs through GameActionsApi (D26):\n" +
                hits.joinToString("\n"),
        )
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
        for (relative in listOf(nightScreen, androidViewModel, webViewModel) + daySources) {
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
