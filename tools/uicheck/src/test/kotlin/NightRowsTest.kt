package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Answer
import com.clocktower.engine.ChoosePlayers
import com.clocktower.engine.DeathCause
import com.clocktower.engine.DeferredDeath
import com.clocktower.engine.InfoObligation
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
import com.clocktower.grimoire.ui.screens.night.mustNotShowTruth
import com.clocktower.grimoire.ui.screens.night.nextToken
import com.clocktower.grimoire.ui.screens.night.nightSp
import com.clocktower.grimoire.ui.screens.night.offerAnswerText
import com.clocktower.grimoire.ui.screens.night.openRowKey
import com.clocktower.grimoire.ui.screens.night.openRowToken
import com.clocktower.grimoire.ui.screens.night.openingToken
import com.clocktower.grimoire.ui.screens.night.lastPickEffects
import com.clocktower.grimoire.ui.screens.night.placedLabels
import com.clocktower.grimoire.ui.screens.night.sharedEffects
import com.clocktower.grimoire.ui.screens.night.pointPrefix
import com.clocktower.grimoire.ui.screens.night.preselected
import com.clocktower.grimoire.ui.screens.night.primaryEnabled
import com.clocktower.grimoire.ui.screens.night.primaryLabel
import com.clocktower.grimoire.ui.screens.night.progress
import com.clocktower.grimoire.ui.screens.night.promptBelongsTo
import com.clocktower.grimoire.ui.screens.night.promptDoneLabel
import com.clocktower.grimoire.ui.screens.night.promptPrimaryLabel
import com.clocktower.grimoire.ui.screens.night.promptedToken
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
    fun `a token that lands on the last pick only is stated separately`() {
        // Playtest B2-14: "PLAYER 1, PLAYER 3 — SAFE + DRUNK" reads as though
        // both were safe AND drunk. The card's prompt already says the SECOND
        // one tapped is the drunk one; the button threw that away.
        val innkeeper = ChoosePlayers(
            sourceId = "step",
            prompt = "WHICH TWO?",
            min = 2,
            max = 2,
            perTarget = listOf(NightEffect.PlaceToken("step", "Safe", Ref.Target)),
            onResolve = listOf(NightEffect.PlaceToken("step", "Drunk", Ref.Target)),
        )
        assertEquals(listOf("Drunk"), placedLabels(lastPickEffects(innkeeper)))
        assertEquals(listOf("Safe"), placedLabels(sharedEffects(innkeeper)))
        assertEquals(
            "P1, P3 — SAFE · P3 — DRUNK",
            primaryLabel(
                picked = listOf("P1", "P3"),
                places = placedLabels(sharedEffects(innkeeper)),
                lastPickPlaces = placedLabels(lastPickEffects(innkeeper)),
            ),
        )
        // A single-pick action keeps every token on the one name.
        val monk = choose(perTarget = listOf(NightEffect.PlaceToken("step", "Safe", Ref.Target)))
        assertEquals(emptyList<NightEffect>(), lastPickEffects(monk))
    }

    @Test
    fun `an impaired ability's placement states that it does nothing`() {
        // Playtest B2-5: a poisoned Monk's primary read "PLAYER 1 — SAFE",
        // flat and enabled, under the card's own IMPAIRED banner — and the
        // Imp killed Player 1 two steps later.
        assertEquals(
            "PLAYER 1 — “SAFE” — NO EFFECT (ABILITY NOT WORKING)",
            primaryLabel(
                picked = listOf("Player 1"),
                places = listOf("Safe"),
                abilityImpaired = true,
            ),
        )
        // The Innkeeper's two tokens, same rule.
        assertEquals(
            "P1, P3 — “SAFE” + “DRUNK” — NO EFFECT (ABILITY NOT WORKING)",
            primaryLabel(
                picked = listOf("P1", "P3"),
                places = listOf("Safe", "Drunk"),
                abilityImpaired = true,
            ),
        )
        // A working ability is unchanged.
        assertEquals(
            "PLAYER 1 — SAFE",
            primaryLabel(picked = listOf("Player 1"), places = listOf("Safe")),
        )
    }

    @Test
    fun `chose nobody is a real answer with its own label`() {
        assertEquals("THEY CHOSE NOBODY", primaryLabel(picked = listOf("Ben"), none = true))
    }

    @Test
    fun `a standing death is on the button beside tonight's own outcome`() {
        // The Pukka: Dev is poisoned tonight, Ben — poisoned LAST night — dies
        // now. The button used to read `DEV — POISONED` and Ben's name appeared
        // nowhere on the card (playtest D, P1-9).
        val dies = deathHeadline(KillOutcome.Dies(""), "Ben")
        assertEquals(
            "DEV — POISONED · BEN DIES",
            primaryLabel(picked = listOf("Dev"), places = listOf("Poisoned"), deferredLine = dies),
        )
        // An Exorcised Pukka asks nothing at all — and still kills Ben.
        assertEquals("BEN DIES", primaryLabel(deferredLine = dies))
        // "They chose nobody" is a real answer, and Ben still dies.
        assertEquals(
            "THEY CHOSE NOBODY · BEN DIES",
            primaryLabel(picked = listOf("Dev"), none = true, deferredLine = dies),
        )
        // The Grandmother: nothing is picked, she dies.
        assertEquals(
            "GRAN SURVIVES — NOBODY DIES",
            primaryLabel(
                deferredLine = deathHeadline(
                    KillOutcome.Prevented(by = null, reason = "protected", announce = ""),
                    "Gran",
                ),
            ),
        )
        // No standing death: nothing is appended.
        assertEquals("DEV — POISONED", primaryLabel(picked = listOf("Dev"), places = listOf("Poisoned")))
    }

    @Test
    fun `a step whose answer is already on the board opens with it picked and armed`() {
        val marked = ShowInfo("grandmother", "WHICH PLAYER IS THE GRANDCHILD?", 1, preselect = listOf(4L))
        assertEquals(listOf(4L), preselected(marked))
        assertTrue(primaryEnabled(marked, PickState(playerIds = preselected(marked))))

        // Nothing marked: the picker opens empty and the primary stays disabled.
        val bare = ShowInfo("grandmother", "WHICH PLAYER IS THE GRANDCHILD?", 1)
        assertEquals(emptyList<Long>(), preselected(bare))
        assertFalse(primaryEnabled(bare, PickState(playerIds = preselected(bare))))

        // Never more picks than the step accepts, and never on another shape.
        val over = ShowInfo("s", "", targetsNeeded = 1, preselect = listOf(4L, 5L))
        assertEquals(listOf(4L), preselected(over))
        assertEquals(emptyList<Long>(), preselected(choose()))
        assertEquals(emptyList<Long>(), preselected(null))
    }

    @Test
    fun `the engine names the standing deaths, the screen never guesses them`() {
        assertEquals(emptyList<Long>(), step().deferredDeaths.map { it.playerId })
        val standing = step().copy(
            deferredDeaths = listOf(DeferredDeath(7L, DeathCause.GOOD_ABILITY, respectProtection = false)),
        )
        assertEquals(listOf(7L), standing.deferredDeaths.map { it.playerId })
        assertEquals(DeathCause.GOOD_ABILITY, standing.deferredDeaths.single().cause)
        assertFalse(standing.deferredDeaths.single().respectProtection)
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

    @Test
    fun `an impaired holder's primary is not the true answer`() {
        // The card says "! Eve is POISONED (Poisoner) — give false info."; the
        // one gold button under it must not read SHOW "1" TO EVE.
        assertTrue(mustNotShowTruth(InfoObligation.MUST_LIE, malfunctions = false))
        assertTrue(mustNotShowTruth(InfoObligation.MAY_LIE, malfunctions = true))
        assertFalse(mustNotShowTruth(InfoObligation.TRUTH, malfunctions = false))
        assertFalse("a misregistration caveat is not the holder being impaired",
            mustNotShowTruth(InfoObligation.MAY_LIE, malfunctions = false))

        assertEquals(
            "PICK WHAT TO SHOW — EVE IS IMPAIRED",
            primaryLabel(answer = "", holder = "Eve", impairedHolder = "Eve"),
        )
        // Once a card is chosen the primary states THAT card, true or false.
        assertEquals("SHOW “0” TO EVE", primaryLabel(answer = "0", holder = "Eve"))
    }

    @Test
    fun `the answer inside an offer label survives both prefixes`() {
        assertEquals("0", offerAnswerText("SHOW: 0"))
        assertEquals("0", offerAnswerText("LIE · SHOW 0"))
        assertEquals("BLUFFS", offerAnswerText("SHOW: BLUFFS"))
        assertEquals("Player 1, Player 2", offerAnswerText("LIE · SHOW Player 1, Player 2"))
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
    fun `a row that was run stays run when its holder dies later the same night`() {
        // Playtest D2-4: the Sailor resolved, then the Pukka killed the Sailor,
        // and the finished row re-drew as "⊘ skipped · dead — no ability" —
        // losing the recorded target and [Undo], and offering [Run anyway],
        // which would have placed a second Drunk.
        val resolved = step(gate = StepGate.Skip("dead — no ability"))
        val done = setOf(resolved.key.token)
        val mark = rowMark(resolved, done = done, current = false, forced = false)
        assertEquals(RowMark.DONE, mark)
        assertEquals("→ P4 drunk", rowRight(resolved, mark, result = "→ P4 drunk"))

        val plan = NightPlan(cycle = 2, isFirstNight = false, steps = listOf(resolved))
        val row = rowViews(
            plan = plan,
            done = done,
            activeToken = null,
            forced = emptySet(),
            holderNames = { "P1" },
            results = { "→ P4 drunk" },
        ).single()
        assertTrue("a resolved row keeps its [Undo]", row.undo)
        assertFalse("and never offers [Run anyway]", row.runAnyway)
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
    fun `only a done row offers the undo, and it is the only un-tick in the night UI`() {
        val poisoner = step(ability = "a", action = choose())
        val teller = step(ability = "b", action = choose())
        val gated = step(ability = "c", gate = StepGate.Skip("dead — no ability"))
        val plan = NightPlan(cycle = 2, isFirstNight = false, steps = listOf(poisoner, teller, gated))
        val rows = rowViews(
            plan = plan,
            done = setOf(poisoner.key.token),
            activeToken = teller.key.token,
            forced = emptySet(),
            holderNames = { "Gus" },
            results = { "" },
        )
        assertTrue("a ticked row can be put back on the sheet", rows[0].undo)
        assertFalse("nothing to undo on the open card", rows[1].undo)
        assertFalse("the engine auto-ticked it; [Run anyway] is its control", rows[2].undo)
        assertTrue(rows[2].runAnyway)

        // …and the card itself no longer carries an un-tick: pressing something
        // on the card that is DOING the step must never undo it (Fix-B).
        val card = File(
            appScreens(),
            "night/NightCard.kt",
        )
        assertTrue("NightCard.kt not found at ${card.absolutePath}", card.isFile)
        val code = card.readText().lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse("NightCard still un-ticks a step", code.contains("toggleNightStep"))
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

    // ---- which row a night opens on ---------------------------------------

    @Test
    fun `a fresh night opens on its first unfinished row, never on Dawn`() {
        val dusk = step(ability = "dusk")
        val poisoner = step(ability = "poisoner")
        val imp = step(ability = "imp")
        val dawn = step(ability = "dawn")
        val steps = listOf(dusk, poisoner, imp, dawn)

        assertEquals(dusk.key.token, openingToken(steps, done = emptySet()))
        assertEquals(imp.key.token, openingToken(steps, done = setOf(dusk.key.token, poisoner.key.token)))
    }

    @Test
    fun `Dawn opens only once every required row is done`() {
        val dusk = step(ability = "dusk")
        val skipped = step(ability = "butler", gate = StepGate.Skip("dead — no ability"))
        val dawn = step(ability = "dawn")
        val steps = listOf(dusk, skipped, dawn)

        // A gated-off row is done by definition and must not hold the sheet up.
        assertEquals(dawn.key.token, openingToken(steps, done = setOf(dusk.key.token, dawn.key.token)))
        assertEquals(dawn.key.token, openingToken(steps, done = setOf(dusk.key.token)))
        assertEquals(dusk.key.token, openingToken(steps, done = emptySet()))
        assertNull(openingToken(emptyList(), done = emptySet()))
    }

    @Test
    fun `finishing a step opens the next row BELOW it, never an earlier one`() {
        // The night-1 Bad Moon Rising sheet the report was driven on.
        val names = listOf(
            "dusk", "minioninfo", "lunatic", "demoninfo", "sailor",
            "godfather", "devilsadvocate", "pukka", "grandmother", "chambermaid", "dawn",
        )
        val steps = names.map { step(ability = it) }
        fun token(name: String) = steps.first { it.abilityId == name }.key.token

        // Steps 1-6 done, step 7 (Devil's Advocate) skipped over by hand: the
        // Godfather at step 6 threw the sheet back to step 4, and the night-3
        // Exorcist threw it back to step 1 (Fix-D; playtest D P2-20).
        val doneThroughSix = names.take(6).map(::token).toSet()
        assertEquals(token("devilsadvocate"), nextToken(steps, doneThroughSix, token("godfather")))

        // Jump ahead to the Pukka, leaving the Devil's Advocate owed, and
        // resolve it: the next row is the Grandmother, not the row above.
        val jumped = doneThroughSix + token("pukka")
        assertEquals(token("grandmother"), nextToken(steps, jumped, token("pukka")))

        // Nothing left below: wrap round to whatever is still owed above.
        val allButDa = names.filterNot { it == "devilsadvocate" }.map(::token).toSet()
        assertEquals(token("devilsadvocate"), nextToken(steps, allButDa, token("chambermaid")))

        // The closing card is never opened by "carry on" while anything else is
        // owed — its primary opens the day (playtest B P0 #2).
        val allButDaAndDawn = allButDa - token("dawn")
        assertEquals(
            token("devilsadvocate"),
            nextToken(steps, allButDaAndDawn, token("chambermaid")),
        )
        // …and it IS opened once it is the only thing left.
        val everythingButDawn = names.filterNot { it == "dawn" }.map(::token).toSet()
        assertEquals(token("dawn"), nextToken(steps, everythingButDawn, token("chambermaid")))

        // No current row (a fresh night, or one whose row the plan dropped):
        // fall back to the sheet's opening row.
        assertEquals(token("dusk"), nextToken(steps, emptySet(), after = null))
        assertEquals(token("dusk"), nextToken(steps, emptySet(), after = "no-such-token"))
        assertNull(nextToken(emptyList(), emptySet(), after = null))
    }

    @Test
    fun `the open row carries the night it belongs to, so it cannot leak into the next one`() {
        val saved = openRowKey(cycle = 1, token = "DAWN")
        assertEquals("DAWN", openRowToken(saved, cycle = 1))
        assertNull("night 1's DAWN is not night 2's open row", openRowToken(saved, cycle = 2))
        assertEquals("", openRowKey(cycle = 2, token = null))
        assertNull(openRowToken("", cycle = 2))
        assertNull("a bare token is not a saved row", openRowToken("DAWN", cycle = 2))
    }

    // ---- a question the engine is still owed --------------------------------

    private fun prompt(sourceId: String, becomes: String = "") = com.clocktower.engine.Prompt(
        id = 7,
        at = com.clocktower.engine.BriefingSlot.NOW,
        kind = com.clocktower.engine.PromptKind.CHOOSE_PLAYER,
        sourceId = sourceId,
        title = "a Minion becomes the Demon.",
        becomesCharacterId = becomes,
    )

    @Test
    fun `an unanswered obligation holds its own row open`() {
        val demon = step(ability = "demon")
        val dawn = step(ability = "dawn")
        val steps = listOf(demon, dawn)

        assertEquals(demon.key.token, promptedToken(steps, listOf(prompt("demon"))))
        assertNull(promptedToken(steps, emptyList()))
        assertNull("another row's question does not pin this one", promptedToken(steps, listOf(prompt("monk"))))
        assertTrue(promptBelongsTo(prompt("demon"), demon))
        assertFalse(promptBelongsTo(prompt("demon"), dawn))
    }

    @Test
    fun `the obligation's primary states what answering it does`() {
        assertEquals("PICK ONE", promptPrimaryLabel(picked = null, becomes = "Imp"))
        assertEquals("BEN BECOMES THE IMP", promptPrimaryLabel(picked = "Ben", becomes = "Imp"))
        assertEquals("BEN — CONFIRM", promptPrimaryLabel(picked = "Ben", becomes = null))
        // An obligation with nothing to choose is discharged, not answered.
        assertEquals("DONE — THEY HAVE SEEN IT", promptDoneLabel(hasCard = true))
        assertEquals("DONE — CARRY ON", promptDoneLabel(hasCard = false))
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
        val app = appScreens()
        val files = (File(app, "night").listFiles()?.toList().orEmpty() + File(app, "NightScreen.kt"))
            .filter { it.isFile && it.extension == "kt" }
        // A scan that found nothing is a green test that checks nothing.
        assertTrue("no night sources found under ${app.absolutePath}", files.size >= 5)
        return files
    }

    /**
     * `app/src/main/java/.../ui/screens`, found by walking up from the working
     * directory.
     *
     * This project nests a second `settings.gradle.kts` under `tools/uicheck`,
     * which is where Gradle runs these tests from — so "walk up to the first
     * settings.gradle.kts" stopped at `tools/uicheck` and every source-scanning
     * check here was silently reading an empty file list.
     */
    private fun appScreens(): File {
        val suffix = "app/src/main/java/com/clocktower/grimoire/ui/screens"
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, suffix).isDirectory) dir = dir.parentFile
        return File(
            requireNotNull(dir) { "app sources not found from ${File(".").absolutePath}" },
            suffix,
        )
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
