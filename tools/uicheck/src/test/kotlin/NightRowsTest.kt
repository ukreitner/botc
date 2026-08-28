package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Answer
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DeathCause
import com.clocktower.engine.KillOutcome
import com.clocktower.engine.NightEffect
import com.clocktower.engine.NightPlan
import com.clocktower.engine.NightStep
import com.clocktower.engine.Ref
import com.clocktower.engine.ShowInfo
import com.clocktower.engine.StepGate
import com.clocktower.engine.StepKey
import com.clocktower.engine.TargetConstraint
import com.clocktower.engine.TargetSort
import com.clocktower.engine.Team
import com.clocktower.engine.Until
import com.clocktower.engine.WakeStyle
import com.clocktower.engine.YesNo
import com.clocktower.grimoire.ui.screens.night.DIM_LEVELS
import com.clocktower.grimoire.ui.screens.night.HOLD_CONFIRM_MILLIS
import com.clocktower.grimoire.ui.screens.night.NIGHT_MIN_SP
import com.clocktower.grimoire.ui.screens.night.PickState
import com.clocktower.grimoire.ui.screens.night.RowMark
import com.clocktower.grimoire.ui.screens.night.SeatOption
import com.clocktower.grimoire.ui.screens.night.Tone
import com.clocktower.grimoire.ui.screens.night.actionEffects
import com.clocktower.grimoire.ui.screens.night.answerLabel
import com.clocktower.grimoire.ui.screens.night.askSummary
import com.clocktower.grimoire.ui.screens.night.blockedBecause
import com.clocktower.grimoire.ui.screens.night.deathHeadline
import com.clocktower.grimoire.ui.screens.night.dimAlpha
import com.clocktower.grimoire.ui.screens.night.gateBadge
import com.clocktower.grimoire.ui.screens.night.isDestructive
import com.clocktower.grimoire.ui.screens.night.nextDimLevel
import com.clocktower.grimoire.ui.screens.night.nightSp
import com.clocktower.grimoire.ui.screens.night.placedLabels
import com.clocktower.grimoire.ui.screens.night.pointPrefix
import com.clocktower.grimoire.ui.screens.night.primaryEnabled
import com.clocktower.grimoire.ui.screens.night.primaryLabel
import com.clocktower.grimoire.ui.screens.night.progress
import com.clocktower.grimoire.ui.screens.night.rowMark
import com.clocktower.grimoire.ui.screens.night.rowRight
import com.clocktower.grimoire.ui.screens.night.rowTone
import com.clocktower.grimoire.ui.screens.night.rowViews
import com.clocktower.grimoire.ui.screens.night.screenBrightness
import com.clocktower.grimoire.ui.screens.night.segmentTones
import com.clocktower.grimoire.ui.screens.night.sortOptions
import com.clocktower.grimoire.ui.screens.night.togglePick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WP8's measured half: the night screen's presentation logic is plain Kotlin in
 * `ui/screens/night/`, so every acceptance criterion that can be stated as a
 * value — what the primary button promises, which rows are grey and pre-ticked,
 * what the right-hand column says, and that nothing renders below 14 sp — is a
 * test rather than a judgement made at the table at one in the morning.
 *
 * Lives in `tools/uicheck` because the logic is UI code under `app/`, which the
 * `:engine` test source set cannot see. Run with
 * `./gradlew -p tools/uicheck test`.
 */
class NightRowsTest {

    private fun step(
        ability: String = "step",
        holder: Long? = 1L,
        gate: StepGate = StepGate.Fire,
        action: com.clocktower.engine.NightAction? = null,
        slot: String = ability,
    ) = NightStep(
        key = StepKey(ability, holder),
        slotId = slot,
        order = 0.0,
        title = ability,
        detail = "",
        style = WakeStyle.OTHER_NIGHT,
        gate = gate,
        action = action,
    )

    private fun choose(
        min: Int = 1,
        max: Int = 1,
        constraints: List<TargetConstraint> = emptyList(),
        perTarget: List<NightEffect> = emptyList(),
        allowNone: Boolean = false,
    ) = ChoosePlayers(
        sourceId = "step",
        prompt = "WHO DID THEY CHOOSE?",
        min = min,
        max = max,
        constraints = constraints,
        perTarget = perTarget,
        allowNone = allowNone,
    )

    private fun seat(
        id: Long = 1L,
        alive: Boolean = true,
        self: Boolean = false,
        team: Team? = Team.TOWNSFOLK,
        evil: Boolean = false,
        traveller: Boolean = false,
        lastNight: Boolean = false,
        before: Boolean = false,
        neighbour: Boolean = false,
    ) = SeatOption(
        id = id,
        seat = id.toInt(),
        name = "P$id",
        alive = alive,
        self = self,
        team = team,
        evil = evil,
        traveller = traveller,
        chosenLastNight = lastNight,
        chosenBefore = before,
        neighbour = neighbour,
    )

    // ---- the primary button states the OUTCOME, never the verb -------------

    @Test
    fun `the primary button names the death it is about to cause`() {
        assertEquals(
            "FAY DIES",
            primaryLabel(picked = listOf("Fay"), deathLine = deathHeadline(KillOutcome.Dies(""), "Fay")),
        )
    }

    @Test
    fun `a prevented death is stated as a survival, not as a kill`() {
        val outcome = KillOutcome.Prevented(by = null, reason = "The Monk is protecting her.", announce = "")
        assertEquals(
            "EVE SURVIVES — NOBODY DIES",
            primaryLabel(picked = listOf("Eve"), deathLine = deathHeadline(outcome, "Eve")),
        )
    }

    @Test
    fun `an information step names the answer and the player`() {
        assertEquals("SHOW “1” TO BEN", primaryLabel(answer = "1", holder = "Ben"))
    }

    @Test
    fun `a token placement is stated as its outcome`() {
        assertEquals(
            "BEN — POISONED",
            primaryLabel(picked = listOf("Ben"), places = listOf("Poisoned")),
        )
    }

    @Test
    fun `chose nobody is a real answer with its own label`() {
        assertEquals("THEY CHOSE NOBODY", primaryLabel(picked = listOf("Ben"), none = true))
    }

    @Test
    fun `the last card of the sheet opens the day`() {
        assertEquals("OPEN THE DAY →", primaryLabel(dawn = true))
    }

    @Test
    fun `a gated-off row offers to run anyway`() {
        assertEquals("RUN IT ANYWAY", primaryLabel(skipped = true))
    }

    @Test
    fun `a choice cannot be promised as an outcome`() {
        // KillOutcome.Choice goes to the shared KillSheet instead: the button
        // must never promise a death the funnel has not decided on.
        val choice = KillOutcome.Choice(question = "Which of them dies?", options = emptyList())
        assertEquals("", deathHeadline(choice, "Fay"))
    }

    // ---- destructive primaries need a hold ---------------------------------

    @Test
    fun `killing resurrecting and rewriting a character all need a hold`() {
        val destructive = listOf(
            NightEffect.Attack(Ref.Target),
            NightEffect.Resurrect(Ref.Target),
            NightEffect.MarkSpent("step"),
            NightEffect.SwapCharacters(Ref.Source, Ref.Target),
        )
        for (effect in destructive) {
            assertTrue("$effect must be held to confirm", isDestructive(listOf(effect)))
        }
        assertTrue(HOLD_CONFIRM_MILLIS >= 400)
    }

    @Test
    fun `placing a reminder token is a single tap`() {
        val place = NightEffect.PlaceToken("step", "Poisoned", Ref.Target, until = Until.DUSK)
        assertFalse(isDestructive(listOf(place)))
    }

    @Test
    fun `every effect of an action is inspected, including a nested sequence`() {
        val action = com.clocktower.engine.Sequence(
            sourceId = "step",
            prompt = "",
            stages = listOf(choose(perTarget = listOf(NightEffect.Attack(Ref.Target)))),
        )
        assertTrue(isDestructive(actionEffects(action)))
    }

    @Test
    fun `the token labels an action places become the button's outcome`() {
        val action = choose(perTarget = listOf(NightEffect.PlaceToken("step", "Safe", Ref.Target)))
        assertEquals(listOf("Safe"), placedLabels(actionEffects(action)))
    }

    // ---- gates -------------------------------------------------------------

    @Test
    fun `a skipped row is grey, pre-ticked and carries its reason`() {
        val skipped = step(gate = StepGate.Skip("nobody was executed today"))
        val mark = rowMark(skipped, done = emptySet(), current = false, forced = false)
        assertEquals(RowMark.SKIPPED, mark)
        assertEquals(Tone.MUTED, rowTone(mark, skipped.gate))
        assertEquals("skipped", rowRight(skipped, mark, result = ""))
        assertTrue(gateBadge(skipped.gate)!!.text.contains("nobody was executed today"))
    }

    @Test
    fun `run anyway un-skips the row without changing the engine's mind`() {
        val skipped = step(gate = StepGate.Skip("dead — no ability"))
        assertEquals(
            RowMark.CURRENT,
            rowMark(skipped, done = emptySet(), current = true, forced = true),
        )
    }

    @Test
    fun `a reduced row is ember, never grey and never removed`() {
        val reduced = step(gate = StepGate.Reduced("the Exorcist silenced them", setOf("pending")))
        val mark = rowMark(reduced, done = emptySet(), current = false, forced = false)
        assertEquals(RowMark.PENDING, mark)
        assertEquals(Tone.ALERT, rowTone(mark, reduced.gate))
        assertEquals(Tone.ALERT, gateBadge(reduced.gate)!!.tone)
    }

    @Test
    fun `a firing row has no badge at all`() {
        assertNull(gateBadge(StepGate.Fire))
    }

    // ---- the collapsed sheet: result if done, ask if pending ---------------

    @Test
    fun `the collapsed list shows the result of a done row and the ask of a pending one`() {
        val poisoner = step(ability = "a", action = choose())
        val teller = step(ability = "b", action = choose(min = 2, max = 2))
        val plan = NightPlan(cycle = 2, isFirstNight = false, steps = listOf(poisoner, teller))
        val rows = rowViews(
            plan = plan,
            done = setOf(poisoner.key.token),
            activeToken = teller.key.token,
            forced = emptySet(),
            holderNames = { "Gus" },
            results = { if (it === poisoner) "→ Ben" else "" },
        )
        assertEquals("→ Ben", rows[0].right)
        assertEquals(RowMark.DONE, rows[0].mark)
        assertEquals("2 picks", rows[1].right)
        assertEquals(RowMark.CURRENT, rows[1].mark)
    }

    @Test
    fun `the ask summary reads in the storyteller's words`() {
        assertEquals("1 pick", askSummary(choose()))
        assertEquals("yes / no", askSummary(YesNo("s", "", "Yes", "No")))
        assertEquals("show them", askSummary(ShowInfo("s", "")))
        assertEquals("", askSummary(null))
    }

    @Test
    fun `progress counts the sheet, not the storyteller's memory`() {
        val a = step(ability = "a")
        val b = step(ability = "b", gate = StepGate.Skip("nothing to do"))
        val c = step(ability = "c")
        val plan = NightPlan(2, false, listOf(a, b, c))
        val p = progress(plan, done = setOf(a.key.token), activeToken = c.key.token)
        assertEquals(3, p.ordinal)
        assertEquals(3, p.total)
        assertEquals(1, p.done)
        assertEquals(1, p.skipped)
        assertEquals("step 3 / 3", p.label)
        assertEquals(3, segmentTones(plan, setOf(a.key.token), c.key.token, emptySet()).size)
    }

    // ---- the one picker ----------------------------------------------------

    @Test
    fun `last night's target is blocked with the reason, never silently missing`() {
        val option = seat(lastNight = true)
        assertEquals(
            "chosen last night",
            blockedBecause(option, listOf(TargetConstraint.DIFFERENT_FROM_LAST_NIGHT)),
        )
    }

    @Test
    fun `the dead, the self and the wrong team each say why they cannot be picked`() {
        assertEquals("dead", blockedBecause(seat(alive = false), listOf(TargetConstraint.ALIVE)))
        assertEquals("themselves", blockedBecause(seat(self = true), listOf(TargetConstraint.NOT_SELF)))
        assertEquals(
            "not a Townsfolk",
            blockedBecause(seat(team = Team.MINION), listOf(TargetConstraint.TOWNSFOLK)),
        )
        assertNull(blockedBecause(seat(), listOf(TargetConstraint.ALIVE, TargetConstraint.NOT_SELF)))
    }

    @Test
    fun `seat order is the default and is left alone`() {
        val seats = listOf(seat(1), seat(2, alive = false), seat(3))
        assertEquals(seats, sortOptions(seats, TargetSort.SEAT_ORDER))
        assertEquals(listOf(1L, 3L, 2L), sortOptions(seats, TargetSort.ALIVE_FIRST).map { it.id })
    }

    @Test
    fun `a tap always lands - the oldest pick is dropped once the limit is reached`() {
        assertEquals(listOf(1L), togglePick(emptyList(), 1L, max = 2))
        assertEquals(listOf(1L, 2L), togglePick(listOf(1L), 2L, max = 2))
        assertEquals(listOf(2L, 3L), togglePick(listOf(1L, 2L), 3L, max = 2))
        assertEquals(listOf(3L), togglePick(listOf(1L), 3L, max = 1))
        assertEquals(emptyList<Long>(), togglePick(listOf(1L), 1L, max = 2))
    }

    @Test
    fun `the primary is armed only once every ask has an answer`() {
        val action = choose(min = 2, max = 2)
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(1L))))
        assertTrue(primaryEnabled(action, PickState(playerIds = listOf(1L, 2L))))
        assertTrue(primaryEnabled(action, PickState(none = true)))
        assertFalse(primaryEnabled(YesNo("s", "", "Y", "N"), PickState()))
        assertTrue(primaryEnabled(YesNo("s", "", "Y", "N"), PickState(yes = false)))
        assertTrue(primaryEnabled(null, PickState()))
    }

    // ---- answers -----------------------------------------------------------

    @Test
    fun `a typed answer is rendered without string-matching a headline`() {
        assertEquals("2", answerLabel(Answer.Count(2)))
        assertEquals("YES", answerLabel(Answer.YesNoAnswer(true)))
        assertEquals("NO", answerLabel(Answer.YesNoAnswer(false)))
        assertEquals(
            "Poisoner",
            answerLabel(Answer.Characters(listOf("poisoner")), characterName = { "Poisoner" }),
        )
        assertEquals("Ben", answerLabel(Answer.Players(listOf(4L)), playerName = { "Ben" }))
    }

    @Test
    fun `a point card names the players and says whether a token goes above them`() {
        assertEquals("ONE OF THESE PLAYERS IS THE", pointPrefix(withCharacter = true, names = 2))
        assertEquals("THIS PLAYER IS THE", pointPrefix(withCharacter = true, names = 1))
        assertEquals("THESE PLAYERS", pointPrefix(withCharacter = false, names = 3))
        assertEquals("THIS PLAYER", pointPrefix(withCharacter = false, names = 1))
    }

    // ---- the dim control ---------------------------------------------------

    @Test
    fun `the dim control cycles three levels and persists as an int`() {
        assertEquals(1, nextDimLevel(0))
        assertEquals(2, nextDimLevel(1))
        assertEquals(0, nextDimLevel(2))
        assertEquals(DIM_LEVELS, 3)
        assertEquals(0f, dimAlpha(0), 0.001f)
        assertTrue(dimAlpha(1) > 0f && dimAlpha(2) > dimAlpha(1))
        assertNull(screenBrightness(0))
        assertTrue(screenBrightness(2)!! < screenBrightness(1)!!)
    }

    // ---- the 14 sp floor, measured over the real sources --------------------

    @Test
    fun `nightSp never returns a size below the floor`() {
        assertEquals(NIGHT_MIN_SP, nightSp(6f), 0.001f)
        assertEquals(NIGHT_MIN_SP, nightSp(NIGHT_MIN_SP), 0.001f)
        assertEquals(18f, nightSp(18f), 0.001f)
        assertTrue(NIGHT_MIN_SP >= 14f)
    }

    @Test
    fun `no night-screen source renders text below 14 sp`() {
        val literal = Regex("""(\d+(?:\.\d+)?)\.sp""")
        val offenders = mutableListOf<String>()
        for (file in nightSources()) {
            for ((index, line) in file.readLines().withIndex()) {
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*")) continue
                for (match in literal.findAll(code)) {
                    val size = match.groupValues[1].toFloat()
                    // Letter spacing and stroke widths are not text sizes; only
                    // fontSize / lineHeight are measured here.
                    if (!code.contains("fontSize") && !code.contains("lineHeight")) continue
                    if (size < 14f) offenders += "${file.name}:${index + 1}  $code"
                }
            }
        }
        assertTrue("text below 14 sp on the night screen:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `no night-screen source uses a typography style smaller than 14 sp`() {
        val banned = listOf("typography.bodySmall", "typography.labelSmall", "typography.labelMedium")
        val offenders = mutableListOf<String>()
        for (file in nightSources()) {
            for ((index, line) in file.readLines().withIndex()) {
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*")) continue
                if (banned.any { code.contains(it) }) offenders += "${file.name}:${index + 1}  $code"
            }
        }
        assertTrue("sub-14 sp typography on the night screen:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** `NightScreen.kt` plus the whole `ui/screens/night/` package. */
    private fun nightSources(): List<File> {
        val app = File(repoRoot(), "app/src/main/java/com/clocktower/grimoire/ui/screens")
        return (File(app, "night").listFiles()?.toList().orEmpty() + File(app, "NightScreen.kt"))
            .filter { it.isFile && it.extension == "kt" }
    }

    /** Walks up from the working directory to the repository root. */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "repository root not found from ${File(".").absolutePath}" }
    }

    // ---- the night screen names no character ------------------------------

    @Test
    fun `the deleted step aliases are gone`() {
        // WP2 left `NightStep.id` and `NightStep.playerIds` as deprecated
        // aliases for WP8 to delete; `wakes` is the replacement.
        val getters = NightStep::class.java.methods.map { it.name }
        assertFalse("NightStep.id must be deleted", "getId" in getters)
        assertFalse("NightStep.playerIds must be deleted", "getPlayerIds" in getters)
        assertTrue("getWakes" in getters)
        assertEquals(listOf(1L), step(holder = 1L).wakes)
        assertEquals(emptyList<Long>(), step(holder = null).wakes)
    }

    @Test
    fun `a kill cause reaches the kill sheet from the registry, not from the screen`() {
        val action = choose(perTarget = listOf(NightEffect.Attack(Ref.Target, DeathCause.EVIL_ABILITY)))
        val attack = actionEffects(action).filterIsInstance<NightEffect.Attack>().single()
        assertEquals(DeathCause.EVIL_ABILITY, attack.cause)
    }
}
