package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercise the input the night UI actually sends, rather than filling in hidden picks. */
class OptionalAbilityInteractionTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val optional = listOf(
        "philosopher", "courtier", "seamstress", "professor", "assassin",
        "huntsman", "nightwatchman", "engineer", "bonecollector",
    )

    private fun game(role: String): GameState {
        val roles = listOf("imp", "poisoner", role, "chef", "gambler", "fool")
        val script = Script("optional-audit", "Optional ability audit", characterIds = roles + listOf("empath", "monk", "seamstress"))
        var state = GameActions.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { index, id ->
            state = GameActions.assignCharacter(state, index.toLong(), id)
        }
        if (role == "bonecollector") state = state.updatePlayer(4L) { it.copy(alive = false) }
        return Phases.advancePhase(state, lookup).copy(cycle = 2)
    }

    private fun step(state: GameState, role: String) = assertNotNull(
        NightPlan.build(state, lookup).steps.firstOrNull { it.abilityId == role && it.holderId == 2L },
        "Missing $role night row",
    )

    private fun resolve(state: GameState, role: String, input: NightInput) =
        NightPlan.resolve(state, lookup, step(state, role).key, input)

    private fun restore(state: GameState) = json.decodeFromString(
        GameState.serializer(), json.encodeToString(GameState.serializer(), state),
    )

    private fun nextNight(state: GameState) =
        Phases.advancePhase(Phases.advancePhase(restore(state), lookup), lookup)

    private fun poison(state: GameState) = Effects.place(
        state = state, target = 2L, kind = EffectKind.POISONED,
        sourceCharacterId = Tokens.STORYTELLER_SOURCE, sourcePlayerId = null,
        until = Until.FOREVER, label = "Poisoned",
    ).state

    private fun sober(state: GameState) = Effects.reconcile(
        restore(state).copy(effects = state.effects.filterNot {
            it.sourceCharacterId == Tokens.STORYTELLER_SOURCE && it.kind == EffectKind.POISONED
        }), lookup,
    )

    @Test
    fun `passing or merely completing each optional row survives save and next night unspent`() {
        for (role in optional) {
            val state = game(role)
            assertTrue(step(state, role).gate is StepGate.Fire, role)
            val outcomes = listOf(
                "pass" to resolve(state, role, NightInput(none = true)),
                "empty input" to resolve(state, role, NightInput()),
                "skip or tick" to NightPlan.markDone(state, step(state, role).key.token),
            )
            for ((path, result) in outcomes) {
                assertFalse(Memory.isSpent(result, role, 2L), "$role: $path")
                assertTrue(result.player(2L)!!.grants.isEmpty(), "$role: $path grants")
                assertTrue(step(nextNight(result), role).gate is StepGate.Fire, "$role: $path next night")
            }
        }
    }

    @Test
    fun `a Philosopher pass wins over a previous character selection and stays unspent`() {
        val state = game("philosopher")
        val passed = resolve(state, "philosopher", NightInput(none = true, characterIds = listOf("chef")))
        assertFalse(Memory.isSpent(passed, "philosopher", 2L))
        assertTrue(passed.player(2L)!!.grants.isEmpty())
        val choice = passed.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "philosopher" }
        assertEquals(NightPlan.NO_CHOICE, choice.text)
        assertTrue(choice.characterIds.isEmpty(), "a pass must never persist as a gained ability")
        assertTrue(step(nextNight(passed), "philosopher").gate is StepGate.Fire)
    }

    @Test
    fun `incomplete or invalid Philosopher choices never spend or complete the row`() {
        val state = game("philosopher")
        for (characters in listOf(emptyList(), listOf(""), listOf("missing-character"), listOf("imp"), listOf("chef", "empath"))) {
            assertEquals(state, resolve(state, "philosopher", NightInput(characterIds = characters)), characters.toString())
        }
        val used = resolve(state, "philosopher", NightInput(characterIds = listOf("chef")))
        assertTrue(Memory.isSpent(used, "philosopher", 2L))
        assertEquals("chef", used.player(2L)!!.grants.single().abilityId)
        assertEquals(1, used.ledger.count { it.kind == LedgerKind.SPENT && it.sourceId == "philosopher" })
    }

    @Test
    fun `Seamstress needs two distinct valid picks to spend`() {
        val state = game("seamstress")
        for (targets in listOf(listOf(3L), listOf(3L, 3L), listOf(3L, 999L), listOf(3L, 4L, 5L))) {
            assertEquals(state, resolve(state, "seamstress", NightInput(playerIds = targets)), targets.toString())
        }
        val used = resolve(state, "seamstress", NightInput(playerIds = listOf(3L, 4L)))
        assertTrue(Memory.isSpent(used, "seamstress", 2L))
    }

    @Test
    fun `Courtier character-only picker input drunks the actual holder`() {
        val state = game("courtier")
        val used = resolve(state, "courtier", NightInput(characterIds = listOf("chef")))
        assertTrue(Memory.isSpent(used, "courtier", 2L))
        assertTrue(Status.isImpaired(used, lookup, 3L), "the UI never supplies a playerId for a character picker")
        assertFalse(Status.isImpaired(used, lookup, 4L))
        assertTrue(Status.isImpaired(nextNight(used), lookup, 3L))

        val outOfPlay = resolve(state, "courtier", NightInput(characterIds = listOf("empath")))
        assertTrue(Memory.isSpent(outOfPlay, "courtier", 2L), "a valid out-of-play character still spends")
        assertTrue(outOfPlay.effects.none { it.kind == EffectKind.DRUNK })

        val passed = resolve(state, "courtier", NightInput(none = true, characterIds = listOf("chef")))
        assertFalse(Memory.isSpent(passed, "courtier", 2L))
        assertFalse(Status.isImpaired(passed, lookup, 3L))
    }

    @Test
    fun `Courtier poisoned use stays spent and never springs to life after recovery`() {
        val failed = resolve(poison(game("courtier")), "courtier", NightInput(characterIds = listOf("chef")))
        assertTrue(Memory.isSpent(failed, "courtier", 2L))
        assertFalse(Status.isImpaired(failed, lookup, 3L))
        assertFalse(Status.isImpaired(sober(failed), lookup, 3L))
        assertTrue(failed.effects.none { it.sourceCharacterId == "courtier" && it.kind == EffectKind.DRUNK })

        val succeeded = resolve(game("courtier"), "courtier", NightInput(characterIds = listOf("chef")))
        assertFalse(Status.isImpaired(poison(succeeded), lookup, 3L), "later poisoning suspends a successful effect")
        assertTrue(Status.isImpaired(sober(poison(succeeded)), lookup, 3L), "a successful effect resumes after recovery")
    }

    @Test
    fun `Courtier explicitly chooses one duplicate and only that copy becomes drunk`() {
        val state = game("courtier").updatePlayer(4L) { it.copy(characterId = "chef") }
        val waiting = resolve(state, "courtier", NightInput(characterIds = listOf("chef")))
        assertTrue(Memory.isSpent(waiting, "courtier", 2L))
        assertTrue(waiting.effects.none { it.kind == EffectKind.DRUNK })
        val prompt = Prompts.due(restore(waiting), BriefingSlot.NOW).single { it.sourceId == "courtier" }
        assertEquals(PromptKind.CHOOSE_PLAYER, prompt.kind)
        assertEquals(listOf(3L, 4L), prompt.targetIds)
        assertEquals(waiting, Prompts.answerWithPlayer(waiting, lookup, prompt.id, 5L), "another character is not a candidate")
        val answered = Prompts.answerWithPlayer(restore(waiting), lookup, prompt.id, 4L)
        assertFalse(Status.isImpaired(answered, lookup, 3L))
        assertTrue(Status.isImpaired(answered, lookup, 4L))
        assertEquals(1, answered.effects.count { it.sourceCharacterId == "courtier" && it.kind == EffectKind.DRUNK })
        assertEquals(answered, Prompts.answerWithPlayer(answered, lookup, prompt.id, 3L), "a repeated tap cannot move it")
    }

    @Test
    fun `Philosopher impaired use spends but keeps only a permanent believed ability`() {
        val failed = resolve(poison(game("philosopher")), "philosopher", NightInput(characterIds = listOf("empath")))
        assertTrue(Memory.isSpent(failed, "philosopher", 2L))
        val recovered = sober(failed)
        assertFalse(Status.isImpaired(recovered, lookup, 2L), "the poison itself has ended")
        val grant = recovered.player(2L)!!.grants.single()
        assertEquals("empath", grant.abilityId)
        assertTrue(grant.alwaysFalse, "the chosen ability was never gained")
        val role = Identity.actingRoles(recovered, lookup, recovered.player(2L)!!).single()
        assertFalse(Status.roleWorks(recovered, lookup, role))
        val info = assertNotNull(InfoCalc.compute(recovered, lookup, "empath", 2L))
        assertTrue(info.abilityMalfunctions)
        assertEquals(InfoObligation.MAY_LIE, info.obligation)
        assertTrue(NightPlan.build(nextNight(recovered), lookup).steps.any {
            it.holderId == 2L && it.abilityId == "empath" && it.abilityImpaired
        }, "continue the believed wake without giving a real ability")
        assertTrue(recovered.effects.none { it.sourceCharacterId == "philosopher" && it.kind == EffectKind.DRUNK })

        val succeeded = resolve(game("philosopher"), "philosopher", NightInput(characterIds = listOf("empath")))
        val healthy = sober(poison(succeeded))
        val realRole = Identity.actingRoles(healthy, lookup, healthy.player(2L)!!).single()
        assertTrue(Status.roleWorks(healthy, lookup, realRole), "a previously successful gain still resumes")
    }

    @Test
    fun `recovered Philosopher false Monk can never protect anyone`() {
        val chosen = resolve(poison(game("philosopher")), "philosopher", NightInput(characterIds = listOf("monk")))
        val recovered = nextNight(sober(chosen))
        val fakeMonk = step(recovered, "monk")
        val protected = NightPlan.resolve(recovered, lookup, fakeMonk.key, NightInput(playerIds = listOf(3L)))
        assertTrue(Status.protections(protected, lookup, 3L).isEmpty())
        val killed = Deaths.attempt(protected, lookup, 3L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertFalse(killed.player(3L)!!.alive, "a fake borrowed Monk must not create real Safe effects after recovery")
    }

    @Test
    fun `recovered Philosopher false Seamstress spends only its simulated use`() {
        val chosen = resolve(poison(game("philosopher")), "philosopher", NightInput(characterIds = listOf("seamstress")))
        val recovered = nextNight(sober(chosen))
        val passed = resolve(recovered, "seamstress", NightInput(none = true))
        assertTrue(step(nextNight(passed), "seamstress").gate is StepGate.Fire)
        val used = resolve(recovered, "seamstress", NightInput(playerIds = listOf(3L, 4L)))
        assertTrue(Memory.isSpent(used, "seamstress", 2L))
        assertTrue(step(nextNight(used), "seamstress").gate is StepGate.Skip)
        assertTrue(used.player(2L)!!.grants.single().alwaysFalse)
    }
}
