package com.clocktower.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real table sequence: choosing someone is not the same as showing them a card.
 * Rules: https://wiki.bloodontheclocktower.com/Nightwatchman (including Vortox/drunk examples).
 */
class NightwatchmanCommunicationTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun game(demon: String = "imp", target: String = "chef"): GameState {
        val roles = listOf("nightwatchman", demon, "poisoner", target, "mayor")
        val script = Script("communication-audit", "Communication audit", characterIds = roles)
        var state = Seats.newGame(script, roles.indices.map { "Player ${it + 1}" })
        roles.forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    private fun row(state: GameState) = assertNotNull(
        NightPlan.build(state, lookup).steps.firstOrNull { it.abilityId == "nightwatchman" && it.holderId == 0L },
    )

    private fun use(state: GameState, target: Long = 3L): GameState =
        NightPlan.resolve(state, lookup, row(state).key, NightInput(playerIds = listOf(target)))

    private fun owed(state: GameState): List<Prompt> = Prompts.due(state, BriefingSlot.NOW)
        .filter { it.sourceId == "nightwatchman" }

    private fun spent(state: GameState) = state.effects.any {
        it.targetId == 0L && it.sourceCharacterId == "nightwatchman" && it.kind == EffectKind.SPENT
    }

    private fun restored(state: GameState): GameState =
        json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))

    @Test
    fun `choice row gives no information to the Nightwatchman themselves`() {
        val step = row(game())
        assertTrue(step.infoId.isNullOrBlank())
        assertTrue(step.cards.isEmpty(), "the chooser must not receive their own token as information")
    }

    @Test
    fun `choosing creates a separate named handout without pretending it has been shown`() {
        val next = use(game())
        assertTrue(spent(next))
        val prompt = owed(next).single()
        assertEquals(3L, prompt.subjectPlayerId)
        assertEquals(PromptKind.INFO, prompt.kind)
        val card = assertIs<ShowCardSpec.PointCard>(prompt.cards.single())
        assertEquals("nightwatchman", card.characterId)
        assertEquals(listOf("Player 1"), card.playerNames)
        assertEquals(listOf(1), card.seatNumbers)
        assertTrue(next.ledger.none { it.actorId == 3L && it.kind in setOf(LedgerKind.TOLD, LedgerKind.WOKE) },
            "a queued handout is neither a presentation nor a target wake")
        val afterRestart = restored(next)
        assertEquals(prompt, owed(afterRestart).single(), "the outstanding card must survive restart")
        assertTrue(owed(Prompts.resolve(afterRestart, prompt.id)).isEmpty())
    }

    @Test
    fun `passing and skipping survive restart without spending or waking the target`() {
        val state = game()
        for (next in listOf(
            NightPlan.resolve(state, lookup, row(state).key, NightInput(none = true)),
            NightPlan.markDone(state, row(state).key.token),
        )) {
            val saved = restored(next)
            assertFalse(spent(saved))
            assertTrue(owed(saved).isEmpty())
            val nextNight = saved.copy(cycle = 2, nightStepsDone = emptySet())
            assertEquals(StepGate.Fire, row(nextNight).gate)
        }
    }

    @Test
    fun `poisoned Nightwatchman spends but nobody else wakes or receives a card`() {
        val state = Effects.place(game(), 0L, EffectKind.POISONED, "poisoner", 2L,
            until = Until.DUSK, label = "Poisoned").state
        assertFalse(Status.hasAbility(state, lookup, 0L))
        val next = use(state)
        assertTrue(spent(next))
        assertTrue(owed(next).isEmpty())
        assertTrue(next.ledger.none { it.actorId == 3L && it.kind in setOf(LedgerKind.TOLD, LedgerKind.WOKE) })
    }

    @Test
    fun `choosing the Goon suppresses the handout after its reaction`() {
        val next = use(game(target = "goon"))
        assertTrue(Status.isImpaired(next, lookup, 0L))
        assertTrue(spent(next))
        assertTrue(owed(next).isEmpty())
    }

    @Test
    fun `dead recipients still get the separate handout`() {
        val state = game().updatePlayer(3L) { it.copy(alive = false) }
        assertEquals(3L, owed(use(state)).single().subjectPlayerId)
    }

    @Test
    fun `Vortox changes the player shown rather than the Nightwatchman character`() {
        val used = use(game(demon = "vortox"))
        val question = owed(used).single()
        assertEquals(PromptKind.CHOOSE_PLAYER, question.kind)
        assertEquals(3L, question.subjectPlayerId, "recipient remains the chosen player")
        assertFalse(0L in question.targetIds, "the true source cannot be the false answer")
        assertEquals(used, Prompts.answerWithPlayer(used, lookup, question.id, 0L))
        val saved = restored(used)
        val answered = Prompts.answerWithPlayer(saved, lookup, question.id, 4L)
        val handout = owed(answered).single()
        assertEquals(PromptKind.INFO, handout.kind)
        assertEquals(3L, handout.subjectPlayerId)
        assertEquals(false, handout.cardsTruthful)
        val card = assertIs<ShowCardSpec.PointCard>(handout.cards.single())
        assertEquals("nightwatchman", card.characterId)
        assertEquals(listOf("Player 5"), card.playerNames)
        assertEquals(listOf(5), card.seatNumbers)
        assertTrue(answered.ledger.none { it.actorId == 3L && it.kind == LedgerKind.TOLD })
        assertEquals(answered, Prompts.answerWithPlayer(answered, lookup, question.id, 4L), "answering twice must not duplicate the handout")
    }
}
