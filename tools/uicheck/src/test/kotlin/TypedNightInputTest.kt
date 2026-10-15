package com.clocktower.grimoire.uicheck

import com.clocktower.engine.*
import com.clocktower.grimoire.ui.screens.night.*
import org.junit.Assert.*
import org.junit.Test

/** The UI's typed answers must survive all the way to the real night resolver. */
class TypedNightInputTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> com.clocktower.engine.Character? = data::character

    private fun game(vararg roles: String): GameState {
        val script = Script("typed-input-test", "Typed input test", characterIds = roles.distinct())
        var state = GameActions.newGame(script, roles.indices.map { "Player ${it + 1}" })
        roles.forEachIndexed { index, role -> state = GameActions.assignCharacter(state, index.toLong(), role) }
        return GameActions.advancePhase(state).copy(cycle = 2)
    }

    private fun step(state: GameState, id: String): NightStep =
        NightPlan.build(state, lookup).steps.first { it.abilityId == id }

    private fun resolve(state: GameState, row: NightStep, pick: PickState): GameState =
        NightPlan.resolve(state, lookup, row.key, pick.toNightInput())

    @Test fun `Gambler cannot finish until the player character and judgment have all been entered`() {
        val state = game("gambler", "imp", "chef", "empath", "butler")
        val action = step(state, "gambler").action as Sequence
        assertFalse(primaryEnabled(action, PickState()))
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(2))))
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(2), characterIds = listOf("chef"))))
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(2), yes = false)))
        assertTrue(primaryEnabled(action, PickState(playerIds = listOf(2), characterIds = listOf("chef"), yes = false)))
    }

    @Test fun `Gambler judgment editing preserves the guess and only the chosen outcome is previewed`() {
        val state = game("gambler", "imp", "chef", "empath", "butler")
        val row = step(state, "gambler")
        val action = row.action as Sequence
        val guessed = updateSequenceStagePick(action, PickState(), 0,
            PickState(playerIds = listOf(2), characterIds = listOf("chef")))
        val correct = updateSequenceStagePick(action, guessed, 1, PickState(yes = false))
        assertEquals(listOf(2L), correct.playerIds)
        assertEquals(listOf("chef"), correct.characterIds)
        assertFalse(pickedActionEffects(action, correct).any { it is NightEffect.Attack })
        assertTrue(resolve(state, row, correct).player(0)!!.alive)

        val wrong = updateSequenceStagePick(action, correct, 1, PickState(yes = true))
        assertEquals(listOf(2L), sequenceStagePick(action, wrong, 0).playerIds)
        assertEquals(listOf("chef"), sequenceStagePick(action, wrong, 0).characterIds)
        assertTrue(pickedActionEffects(action, wrong).any { it is NightEffect.Attack })
        val after = resolve(state, row, wrong)
        assertFalse(after.player(0)!!.alive)
        assertTrue("The guessed player must not be killed", after.player(2)!!.alive)
    }

    @Test fun `Al-Hadikhia keeps independent answers attached to the three ordered picks`() {
        val state = game("alhadikhia", "chef", "empath", "undertaker", "butler")
        val row = step(state, "alhadikhia")
        val action = row.action as Sequence
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(3), optionIds = listOf("die", "live", "live"))))
        assertFalse(primaryEnabled(action, PickState(playerIds = listOf(3, 1), optionIds = listOf("die", "live", "live"))))
        var pick = updateSequenceStagePick(action, PickState(), 0, PickState(playerIds = listOf(3, 1, 2)))
        assertFalse(primaryEnabled(action, pick))
        pick = updateSequenceStagePick(action, pick, 1, PickState(optionId = "die"))
        pick = updateSequenceStagePick(action, pick, 2, PickState(optionId = "live"))
        assertFalse("Two answers are not a complete ritual", primaryEnabled(action, pick))
        pick = updateSequenceStagePick(action, pick, 3, PickState(optionId = "live"))
        assertTrue(primaryEnabled(action, pick))
        assertEquals(listOf(3L, 1L, 2L), pick.toNightInput().playerIds)
        assertEquals(listOf("die", "live", "live"), pick.toNightInput().optionIds)
        assertEquals("die", sequenceStagePick(action, pick, 1).optionId)
        assertEquals("live", sequenceStagePick(action, pick, 2).optionId)
        assertEquals("live", sequenceStagePick(action, pick, 3).optionId)
        val attacks = pickedActionEffects(action, pick).filterIsInstance<NightEffect.Attack>()
        assertEquals(listOf(Ref.TargetN(0)), attacks.map { it.on })
        val after = resolve(state, row, pick)
        assertFalse(after.player(3)!!.alive)
        assertTrue(after.player(1)!!.alive)
        assertTrue(after.player(2)!!.alive)
    }

    @Test fun `Al-Hadikhia final all-live outcome applies to all picks without replacing earlier answers`() {
        val state = game("alhadikhia", "chef", "empath", "undertaker", "butler")
        val row = step(state, "alhadikhia")
        val action = row.action as Sequence
        val pick = PickState(playerIds = listOf(3, 1, 2), optionIds = listOf("live", "live", "alllive"))
        assertTrue(primaryEnabled(action, pick))
        assertEquals(listOf(Ref.AllTargets), pickedActionEffects(action, pick)
            .filterIsInstance<NightEffect.Attack>().map { it.on })
        val after = resolve(state, row, pick)
        assertTrue(listOf(1L, 2L, 3L).all { !after.player(it)!!.alive })
        assertTrue(after.player(0)!!.alive)
    }

    @Test fun `Changing sequence targets or guess requires fresh follow-up answers`() {
        val gambler = step(game("gambler", "imp", "chef", "empath", "butler"), "gambler").action as Sequence
        val judged = PickState(playerIds = listOf(2), characterIds = listOf("chef"), yes = false)
        val changedGuess = updateSequenceStagePick(gambler, judged, 0,
            PickState(playerIds = listOf(2), characterIds = listOf("empath")))
        assertNull(changedGuess.yes)
        assertFalse(primaryEnabled(gambler, changedGuess))

        val ritual = step(game("alhadikhia", "chef", "empath", "undertaker", "butler"), "alhadikhia").action as Sequence
        val answered = PickState(playerIds = listOf(1, 2, 3), optionIds = listOf("live", "die", "live"))
        val reordered = updateSequenceStagePick(ritual, answered, 0, PickState(playerIds = listOf(2, 1, 3)))
        assertTrue(reordered.optionIds.isEmpty())
        assertFalse(primaryEnabled(ritual, reordered))
    }

    @Test fun `Passing clears stale sequence effects before they can kill anyone`() {
        val state = game("alhadikhia", "chef", "empath", "undertaker", "butler")
        val row = step(state, "alhadikhia")
        val action = row.action as Sequence
        val stale = PickState(playerIds = listOf(1, 2, 3), characterIds = listOf("imp"),
            assignments = listOf(1L to "imp"), yes = true, optionId = "die",
            optionIds = listOf("die", "die", "alllive"), none = true)
        assertTrue(primaryEnabled(action, stale))
        assertEquals(NightInput(none = true), stale.toNightInput())
        assertFalse(pickedActionEffects(action, stale).any { it is NightEffect.Attack })
        assertTrue(resolve(state, row, stale).seats.all { it.alive })
    }

    @Test fun `Harlot refusal clears a previously selected death outcome`() {
        val state = game("harlot", "imp", "chef", "empath", "butler")
        val row = step(state, "harlot")
        val action = row.action as Sequence
        val chosen = PickState(playerIds = listOf(2), yes = true)
        assertTrue(primaryEnabled(action, chosen))
        val refused = updateSequenceStagePick(action, chosen, 0, PickState(none = true))
        assertTrue(primaryEnabled(action, refused))
        assertEquals(NightInput(none = true), refused.toNightInput())
        assertFalse(pickedActionEffects(action, refused).any { it is NightEffect.Attack })
        assertTrue(resolve(state, row, refused).seats.all { it.alive })
    }

    @Test fun `Barista second branch is an explicit effect and is not mistaken for a pass`() {
        val state = game("barista", "imp", "chef", "empath", "butler")
        val row = step(state, "barista")
        val action = row.action as Sequence
        val pick = PickState(playerIds = listOf(2), yes = false)
        assertTrue(primaryEnabled(action, pick))
        val placed = pickedActionEffects(action, pick).filterIsInstance<NightEffect.PlaceToken>()
        assertTrue(placed.any { it.label == "Acts Twice" })
        assertFalse(placed.any { it.label == "Sober & Healthy" })
        val after = resolve(state, row, pick)
        assertTrue(after.effects.any { it.targetId == 2L && it.label == "Acts Twice" })
        assertFalse(after.effects.any { it.targetId == 2L && it.label == "Sober & Healthy" })
    }

    @Test fun `Engineer assignments preserve each seat's character when reordered edited and removed`() {
        var pick = setAssignmentPlayers(PickState(), listOf(2, 3))
        pick = setAssignedCharacter(pick, 2, "godfather")
        pick = setAssignedCharacter(pick, 3, "spy")
        assertEquals(listOf(2L to "godfather", 3L to "spy"), pick.assignments)
        pick = setAssignmentPlayers(pick, listOf(3, 2))
        assertEquals(listOf(3L to "spy", 2L to "godfather"), pick.assignments)
        pick = setAssignedCharacter(pick, 3, "baron")
        assertEquals(listOf(3L to "baron", 2L to "godfather"), pick.assignments)
        pick = setAssignmentPlayers(pick, listOf(2))
        pick = setAssignmentPlayers(pick, listOf(2, 3))
        assertEquals(listOf(2L to "godfather", 3L to ""), pick.assignments)
    }

    @Test fun `Engineer incomplete duplicate and oversized assignments cannot arm the action`() {
        val state = game("engineer", "imp", "poisoner", "baron", "chef", "empath")
        val action = step(state, "engineer").action as ChoosePlayersAndCharacters
        assertFalse(primaryEnabled(action, PickState()))
        assertFalse(primaryEnabled(action, PickState(assignments = listOf(2L to ""))))
        assertFalse(primaryEnabled(action, PickState(assignments = listOf(2L to "spy", 2L to "godfather"))))
        assertFalse(primaryEnabled(action, PickState(assignments = listOf(1L to "imp", 2L to "spy", 3L to "baron", 4L to "godfather"))))
        assertTrue(primaryEnabled(action, PickState(assignments = listOf(2L to "godfather", 3L to "spy"))))
        assertTrue(primaryEnabled(action, PickState(none = true)))
    }

    @Test fun `Engineer sends paired assignments to the real resolver and spends only after a rebuild`() {
        val state = game("engineer", "imp", "poisoner", "baron", "chef", "empath")
        val row = step(state, "engineer")
        val pick = PickState(assignments = listOf(2L to "godfather", 3L to "spy"))
        val rebuilt = resolve(state, row, pick)
        assertEquals("godfather", rebuilt.player(2)!!.characterId)
        assertEquals("spy", rebuilt.player(3)!!.characterId)
        assertTrue(Memory.isSpent(rebuilt, "engineer", 0))
        val passed = resolve(state, row, PickState(none = true))
        assertEquals("poisoner", passed.player(2)!!.characterId)
        assertEquals("baron", passed.player(3)!!.characterId)
        assertFalse(Memory.isSpent(passed, "engineer", 0))
    }

    @Test fun `Engineer ordinary assignments reject mixed groups and character type changes`() {
        val state = game("engineer", "imp", "poisoner", "baron", "chef", "empath")
        val action = step(state, "engineer").action
        assertNotNull(assignmentProblem(action,
            PickState(assignments = listOf(1L to "vortox", 2L to "godfather", 3L to "spy")), state, lookup))
        assertNotNull(assignmentProblem(action,
            PickState(assignments = listOf(2L to "vortox", 3L to "spy")), state, lookup))
        assertNull(assignmentProblem(action,
            PickState(assignments = listOf(2L to "godfather", 3L to "spy")), state, lookup))
        assertNull(assignmentProblem(action,
            PickState(assignments = listOf(1L to "vortox")), state, lookup))
    }

    @Test fun `Engineer assignment override requires explicit storyteller intent and reaches the resolver input`() {
        val state = game("engineer", "imp", "poisoner", "baron", "chef", "empath")
        val action = step(state, "engineer").action
        val invalid = PickState(assignments = listOf(2L to "vortox", 3L to "spy"))
        assertNotNull(assignmentProblem(action, invalid, state, lookup))
        val overridden = invalid.copy(overrideConstraints = true)
        assertTrue(overridden.toNightInput().overrideConstraints)
        assertTrue(overridden.toNightInput(overrideConstraints = false).overrideConstraints)
        assertNull(assignmentProblem(action, overridden, state, lookup))
        assertTrue(invalid.toNightInput(overrideConstraints = true).overrideConstraints)
    }

    @Test fun `Ordinary Engineer picker edits cannot silently enable the assignment override`() {
        val state = game("engineer", "imp", "poisoner", "baron", "chef", "empath")
        val action = step(state, "engineer").action
        var pick = setAssignmentPlayers(PickState(), listOf(2, 3))
        pick = setAssignedCharacter(pick, 2, "vortox")
        pick = setAssignedCharacter(pick, 3, "spy")
        assertFalse(pick.overrideConstraints)
        assertFalse(pick.toNightInput().overrideConstraints)
        assertNotNull(assignmentProblem(action, pick, state, lookup))
        pick = setAssignedCharacter(pick, 2, "godfather")
        assertFalse(pick.overrideConstraints)
        assertNull(assignmentProblem(action, pick, state, lookup))
    }

    @Test fun `Options require an actual branch and none branch is not an empty input`() {
        val state = game("wizard", "imp", "chef", "empath", "butler")
        val row = step(state, "wizard")
        val action = row.action as Options
        assertFalse(primaryEnabled(action, PickState()))
        assertFalse(primaryEnabled(action, PickState(optionId = "not-an-option")))
        val declined = PickState(optionId = "none")
        assertTrue(primaryEnabled(action, declined))
        assertFalse(declined.toNightInput().none)
        assertEquals("none", declined.toNightInput().optionId)
        assertFalse(Memory.isSpent(resolve(state, row, declined), "wizard", 0))
        val granted = PickState(optionId = "granted")
        assertTrue(pickedActionEffects(action, granted).any { it is NightEffect.MarkSpent })
        assertFalse(pickedActionEffects(action, declined).any { it is NightEffect.MarkSpent })
        assertTrue(Memory.isSpent(resolve(state, row, granted), "wizard", 0))
    }

    @Test fun `High Priestess branch retains its seat metadata without a duplicate player picker`() {
        val state = game("highpriestess", "imp", "chef", "empath", "butler")
        val row = step(state, "highpriestess")
        val pick = PickState(optionId = "seat:2")
        assertTrue(primaryEnabled(row.action, pick))
        assertTrue(pick.toNightInput().playerIds.isEmpty())
        val after = resolve(state, row, pick)
        assertEquals(listOf(2L), after.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "highpriestess" }.targetIds)
    }

    @Test fun `Pixie branch retains its character payload without a duplicate character picker`() {
        val state = game("pixie", "imp", "chef", "empath", "butler").copy(cycle = 1)
        val row = step(state, "pixie")
        val pick = PickState(optionId = "chef")
        assertTrue(primaryEnabled(row.action, pick))
        assertTrue(pick.toNightInput().characterIds.isEmpty())
        val after = resolve(state, row, pick)
        assertTrue(after.effects.any { it.targetId == 0L && it.label == "Mad" && it.characterId == "chef" })
    }

    @Test fun `Unsupported nested or colliding sequences cannot silently share answers`() {
        val yes = YesNo("test", "First?", "Yes", "No")
        val nested = Sequence("test", "Nested", listOf(Sequence("test", "Inner", listOf(yes))))
        assertFalse(primaryEnabled(nested, PickState(yes = true)))
        val duplicateYes = Sequence("test", "Two independent questions", listOf(yes, yes.copy(prompt = "Second?")))
        assertFalse(primaryEnabled(duplicateYes, PickState(yes = true)))
        val twoPlayers = Sequence("test", "Two independent targets", listOf(
            ChoosePlayers("test", "First player?", 1, 1), ChoosePlayers("test", "Second player?", 1, 1)))
        assertFalse(primaryEnabled(twoPlayers, PickState(playerIds = listOf(1))))
    }

    @Test fun `Conversion preserves every typed field and the explicit storyteller override`() {
        val pick = PickState(playerIds = listOf(3, 1), characterIds = listOf("chef"),
            assignments = listOf(2L to "spy"), yes = false, optionId = "good", optionIds = listOf("die", "live"))
        assertEquals(NightInput(playerIds = listOf(3, 1), characterIds = listOf("chef"),
            assignments = listOf(2L to "spy"), yes = false, optionId = "good", optionIds = listOf("die", "live"),
            overrideConstraints = true), pick.toNightInput(overrideConstraints = true))
    }

    @Test fun `Attack previews name the real source and ordered target recipients`() {
        val picks = listOf(3L, 1L, 2L)
        assertEquals(listOf(0L), attackTargetIds(Ref.Source, 0, picks))
        assertEquals(listOf(3L), attackTargetIds(Ref.TargetN(0), 0, picks))
        assertEquals(listOf(1L), attackTargetIds(Ref.TargetN(1), 0, picks))
        assertEquals(picks, attackTargetIds(Ref.AllTargets, 0, picks))
        assertEquals(listOf(4L), attackTargetIds(Ref.Seat(4), 0, picks))
        assertTrue(attackTargetIds(Ref.TargetN(3), 0, picks).isEmpty())
        assertTrue(attackTargetIds(Ref.Source, null, picks).isEmpty())
    }
}
