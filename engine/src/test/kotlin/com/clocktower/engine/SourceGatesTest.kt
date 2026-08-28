package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The CI gates of ARCHITECTURE §4 (WP12) — grep-style structural rules that no
 * unit test can express, run as JUnit so `./gradlew :engine:test` enforces them.
 *
 * Five invariants:
 *  1. **I1 / §3.4.3** — no character id in `NightScreen.kt` or anywhere in
 *     the Day tab's package. Per-character behaviour belongs in `engine/rules/`.
 *  2. **D26 / §3.4.5** — `GameViewModel.kt` and `WebGameViewModel.kt` contain no
 *     `GameActions.` call; every verb goes through `GameActionsApi`.
 *  3. **D5 / §3.4.1** — no reminder label literal outside `engine/rules/` is
 *     compared with `==`; use `Tokens.key(sourceId, label)`.
 *  4. **§4.1 / WP1** — no UI source anywhere under `app/` or `web/` names the
 *     frozen `GameActions` façade or the deprecated `Deaths.kill`.
 *     `GameActionsApi.kt` is the ONE file allowed to, because being that
 *     boundary is its job. Gate 2 is the same rule for two files; this is it
 *     for the whole UI.
 *  5. **rules package hygiene** — no two files under `engine/rules/` declare a
 *     top-level helper of the same name. Every registry file shares one Kotlin
 *     package, so two same-signature helpers are a CONFLICTING_OVERLOADS build
 *     failure rather than a shadowing bug — which is exactly what WP7-SV hit
 *     with `seatsHolding` (FOLLOWUPS.md, WP7-SV merger note).
 *
 * Every gate is LIVE, and none of them may be `@Ignore`d again — the last test
 * in this file asserts that about this file. Gate 3 keeps a named baseline of
 * the sites that still stand, so a NEW violation fails immediately instead of
 * waiting for the cleanup that would let the strict form pass. Gate 4's
 * baseline is empty: its last two sites (`GameExtras.kt`) now go through
 * `GameActionsApi.moveSeat`, so the rule is enforced strictly.
 */
class SourceGatesTest {

    private val data = GameData.loadDefault()

    private val nightScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt"
    private val dayScreen = "app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt"
    private val androidViewModel = "app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt"
    private val webViewModel = "web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt"

    /** The one UI file allowed to name the engine's frozen verbs: being the boundary is its job. */
    private val gameActionsApi = "app/src/main/java/com/clocktower/grimoire/ui/GameActionsApi.kt"

    /** This file, so the last test can check the gates are still switched on. */
    private val thisFile = "engine/src/test/kotlin/com/clocktower/engine/SourceGatesTest.kt"

    /** One Kotlin package, nine files: every registry file sees every other file's helpers. */
    private val rulesDir = "engine/src/main/kotlin/com/clocktower/engine/rules"

    /** Every UI source root gate 4 polices. */
    private val uiRoots = listOf("app/src/main", "web/src")

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
    // Gate 4 — no UI source calls the frozen façade or the deprecated kill
    // ------------------------------------------------------------------

    /** `GameActions.`, however it is qualified, and the WP1-deprecated kill verb. */
    private val frozenFacade = Regex("\\bGameActions\\s*\\.")
    private val deprecatedKill = Regex("\\bDeaths\\s*\\.\\s*kill\\s*\\(")

    private fun facadeHits(): List<Hit> {
        val exempt = RepoFiles.file(gameActionsApi).canonicalPath
        val hits = mutableListOf<Hit>()
        for (root in uiRoots) {
            for (file in RepoFiles.kotlinSources(root)) {
                if (file.canonicalPath == exempt) continue
                val relative = file.relativeTo(RepoFiles.root).path
                for ((index, line) in codeLines(file.readText())) {
                    if (frozenFacade.containsMatchIn(line)) {
                        hits += Hit(relative, index + 1, "GameActions.", line.trim())
                    }
                    if (deprecatedKill.containsMatchIn(line)) {
                        hits += Hit(relative, index + 1, "Deaths.kill", line.trim())
                    }
                }
            }
        }
        return hits
    }

    /**
     * `GameActions` was gutted to a façade in WP0 and frozen; `Deaths.kill`
     * was deprecated in WP1 because it skips protections entirely. Both are
     * reachable from every UI file, and a screen that reaches for one of them
     * silently bypasses the kill funnel or the view model's undo label.
     *
     * `GameActionsApi.kt` is exempt on purpose — it is the boundary, and it is
     * the file the other 60-odd are supposed to go through.
     */
    @Test
    fun `no app or web source calls the frozen facade or the deprecated kill`() {
        // No baseline: the last two tolerated sites (GameExtras.kt's seat
        // reordering) now call `GameActionsApi.moveSeat`, so every UI file
        // outside the boundary is held to the rule with no exceptions. A new
        // violation is fixed by routing the verb through the API — never by
        // re-introducing a `known` set here.
        val hits = facadeHits()
        assertTrue(
            hits.isEmpty(),
            "call engine verbs through GameActionsApi, and kills through Deaths.attempt " +
                "with a real KillCause (§4.1, WP1):\n" + hits.joinToString("\n"),
        )
    }

    /**
     * The exemption has to be pointing at something. If `GameActionsApi.kt`
     * stopped naming the engine's verbs entirely, gate 4 would be exempting a
     * file that needs no exemption, and the next reader would not know it could
     * be deleted.
     */
    @Test
    fun `the exempt boundary file is the one that really holds the engine calls`() {
        val api = assertNotNull(RepoFiles.textOrNull(gameActionsApi), "missing $gameActionsApi")
        val lines = codeLines(api)
        assertTrue(
            lines.any { (_, line) -> frozenFacade.containsMatchIn(line) } ||
                lines.any { (_, line) -> deprecatedKill.containsMatchIn(line) },
            "GameActionsApi.kt no longer needs its exemption — delete it from gate 4",
        )
    }

    // ------------------------------------------------------------------
    // Gate 5 — engine/rules/ helper names never collide across files
    // ------------------------------------------------------------------

    /** A top-level `private`/`internal` `fun` or `val` declaration. */
    private val topLevelHelper =
        Regex("^(private|internal)\\s+(?:inline\\s+)?(?:fun|val)\\s+(?:<[^>]*>\\s*)?([A-Za-z0-9_]+)")

    /** Helper name -> the rules files that declare it, with its visibility. */
    private fun rulesHelpers(): Map<String, List<Pair<String, String>>> {
        val found = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for (file in RepoFiles.kotlinSources(rulesDir)) {
            for ((_, line) in codeLines(file.readText())) {
                val match = topLevelHelper.find(line) ?: continue
                found.getOrPut(match.groupValues[2]) { mutableListOf() }
                    .add(file.name to match.groupValues[1])
            }
        }
        return found
    }

    /**
     * The `seatsHolding` regression (FOLLOWUPS.md, WP7-SV merger note).
     *
     * All nine registry files live in `com.clocktower.engine.rules`, so a
     * top-level helper is visible to every other file in the package —
     * `internal` ones by design, `private` ones because a same-signature
     * duplicate is a CONFLICTING_OVERLOADS compile error, not a shadow. WP7-FAB
     * exported `internal fun seatsHolding`; WP7-SV wrote a `private fun` of the
     * same name and signature and the module stopped compiling. The fix was to
     * edition-prefix it (`svSeatsHolding`), and that is the rule this gate
     * keeps: a helper that is not obviously unique gets its edition's prefix.
     *
     * Three names are shared today and compile only because their signatures
     * differ. They are named here so the set cannot grow quietly; anything new
     * is edition-prefixed instead.
     */
    @Test
    fun `rules files never share a top-level helper name`() {
        val shared = setOf("demonAttack", "hasToken", "isSpent")
        val helpers = rulesHelpers()
        val collisions = helpers
            .filterValues { it.map { (file, _) -> file }.distinct().size > 1 }
            .mapValues { (_, v) -> v.sortedBy { it.first } }

        val fresh = collisions.filterKeys { it !in shared }
        assertTrue(
            fresh.isEmpty(),
            "two rules files declare the same top-level helper. One package, one " +
                "namespace: prefix it with the edition (svSeatsHolding, bmrHasToken):\n" +
                fresh.entries.joinToString("\n") { (name, where) ->
                    "  $name  ${where.joinToString(", ") { "${it.second} in ${it.first}" }}"
                },
        )
        assertTrue(
            collisions.keys.size <= shared.size,
            "shared helper names grew beyond the known ${shared.sorted()}: ${collisions.keys.sorted()}",
        )

        // An `internal` helper is a deliberate package-wide export, so it may
        // never be shadowed by anything, in any file, under any visibility.
        val exported = helpers.filterValues { list -> list.any { it.second == "internal" } }
        val shadowed = exported.filterValues { it.map { (file, _) -> file }.distinct().size > 1 }
        assertTrue(
            shadowed.isEmpty(),
            "an internal rules helper is redeclared elsewhere in the package:\n" +
                shadowed.entries.joinToString("\n") { (name, where) ->
                    "  $name  ${where.joinToString(", ") { "${it.second} in ${it.first}" }}"
                },
        )
    }

    // ------------------------------------------------------------------
    // The gates are only worth anything if they can see the sources.
    // ------------------------------------------------------------------

    @Test
    fun `the gates can find every file they police`() {
        val named = listOf(nightScreen, androidViewModel, webViewModel, gameActionsApi, thisFile) +
            daySources
        for (relative in named) {
            assertNotNull(RepoFiles.textOrNull(relative), "gate target not found: $relative")
        }
        for (root in scannedRoots + uiRoots) {
            assertTrue(
                RepoFiles.kotlinSources(root).isNotEmpty(),
                "no Kotlin sources under $root — a gate over it would pass vacuously",
            )
        }

        // Gate 4 sees the whole UI, not just the handful of files named above.
        val uiSources = uiRoots.flatMap { RepoFiles.kotlinSources(it) }
        assertTrue(uiSources.size >= 40, "gate 4 sees only ${uiSources.size} UI sources")
        for (relative in named - thisFile) {
            assertTrue(
                uiSources.any { it.canonicalPath == RepoFiles.file(relative).canonicalPath } ||
                    !relative.startsWith("app/") && !relative.startsWith("web/"),
                "$relative is not reached by the UI walk",
            )
        }

        // Gate 5 sees all nine registry files and their helpers.
        val ruleFiles = RepoFiles.kotlinSources(rulesDir)
        assertTrue(ruleFiles.size >= 9, "only ${ruleFiles.size} files under $rulesDir")
        assertTrue(
            rulesHelpers().size >= 100,
            "gate 5 found only ${rulesHelpers().size} top-level helpers — the regex has rotted",
        )

        // Gate 1 has ids to look for and gate 3 has labels to look for.
        assertTrue(data.characters.size >= 150, "only ${data.characters.size} characters loaded")
        assertTrue(
            data.characters.flatMap { it.allReminders }.isNotEmpty(),
            "no reminder labels loaded — the label gate would pass vacuously",
        )
    }

    /**
     * The one failure mode a CI gate cannot catch about itself: being switched
     * off. Eight `@Ignore`s came off this file and the fixtures over WP12's two
     * passes, each naming the package that would deliver it. There are no
     * packages left to wait for, so there is no reason left to skip a gate.
     *
     * The needle is assembled from pieces so this test is not itself a hit.
     */
    @Test
    fun `no gate in this file is switched off`() {
        val source = assertNotNull(RepoFiles.textOrNull(thisFile), "cannot read $thisFile")
        val ignore = "@" + "Ignore"
        val hits = codeLines(source)
            .filter { (_, line) -> ignore in line }
            .map { (index, line) -> Hit(thisFile, index + 1, ignore, line.trim()) }
        assertTrue(
            hits.isEmpty(),
            "every CI gate is live as of WP12 pass 2 — fix the violation, do not skip the gate:\n" +
                hits.joinToString("\n"),
        )
        // And the gates are all still here.
        for (name in listOf(
            "NightScreen branches on no character id",
            "the day package names no character id",
            "view models contain no GameActions call",
            "no reminder label literal outside engine rules is compared with equals",
            "no app or web source calls the frozen facade or the deprecated kill",
            "rules files never share a top-level helper name",
        )) {
            assertTrue("`$name`" in source, "gate `$name` has gone missing")
        }
    }
}
