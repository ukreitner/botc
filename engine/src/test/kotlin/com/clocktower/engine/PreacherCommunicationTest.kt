package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** https://wiki.bloodontheclocktower.com/Preacher and /Marionette. */
class PreacherCommunicationTest {
    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    private fun game(target: String = "poisoner", demon: String = "imp"): GameState {
        val roles = listOf("preacher", demon, target, "chef", "mayor")
        var state = Seats.newGame(Script("preacher-audit", "Preacher audit", characterIds = roles),
            roles.indices.map { "Player ${it + 1}" })
        roles.forEachIndexed { index, role -> state = Seats.assignCharacter(state, index.toLong(), role) }
        return GameActions.advancePhase(state)
    }

    private fun step(state: GameState) = assertNotNull(NightPlan.build(state, lookup).steps
        .firstOrNull { it.abilityId == "preacher" && it.holderId == 0L })

    private fun use(state: GameState, target: Long = 2L) =
        NightPlan.resolve(state, lookup, step(state).key, NightInput(playerIds = listOf(target)))

    private fun preached(state: GameState, target: Long) = state.effects.any {
        it.sourceCharacterId == "preacher" && it.targetId == target && it.kind == EffectKind.NO_ABILITY
    }

    @Test
    fun `Preacher choice has no self handout and includes every legal player`() {
        val row = step(game())
        assertTrue(row.infoId.isNullOrBlank())
        assertTrue(row.cards.isEmpty())
        val action = assertIs<ChoosePlayers>(row.action)
        assertFalse(TargetConstraint.MINION in action.constraints)
        assertFalse(action.allowNone)
        val next = use(game(), 3L)
        assertFalse(preached(next, 3L))
        assertTrue(next.prompts.none { it.sourceId == "preacher" })
        assertTrue(next.ledger.any { it.kind == LedgerKind.CHOICE && it.actorId == 0L && it.targetIds == listOf(3L) })
    }

    @Test
    fun `a chosen Minion receives a separately addressed character handout`() {
        val next = use(game())
        assertTrue(preached(next, 2L))
        assertFalse(Status.hasAbility(next, lookup, 2L))
        val prompt = next.prompts.single { it.sourceId == "preacher" }
        assertEquals(2L, prompt.subjectPlayerId)
        assertEquals(ShowCardSpec.CharacterCard("THIS CHARACTER SELECTED YOU", "preacher"), prompt.cards.single())
        assertTrue(next.ledger.none { it.actorId == 2L && it.kind == LedgerKind.TOLD })
    }

    @Test
    fun `poisoned choice does not leave a token that activates when Preacher sobers`() {
        val state = Effects.place(game(), 0L, EffectKind.POISONED, "poisoner", 2L,
            until = Until.DUSK, label = "Poisoned").state
        val next = use(state)
        assertFalse(preached(next, 2L))
        assertTrue(next.prompts.none { it.sourceId == "preacher" })
        assertTrue(Status.hasAbility(next, lookup, 2L))
    }

    @Test
    fun `a Goon choice is recorded and reacts rather than being discarded as no choice`() {
        val next = use(game(target = "goon"))
        assertTrue(Status.isImpaired(next, lookup, 0L))
        assertFalse(preached(next, 2L))
        assertTrue(next.prompts.none { it.sourceId == "preacher" })
    }

    @Test
    fun `Marionette loses ability without a revealing wake or handout`() {
        val next = use(game(target = "marionette"))
        assertTrue(preached(next, 2L))
        assertTrue(next.prompts.none { it.sourceId == "preacher" })
        assertTrue(next.ledger.none { it.actorId == 2L && it.kind in setOf(LedgerKind.TOLD, LedgerKind.WOKE) })
    }

    @Test
    fun `dead Minions remain legal recipients`() {
        val state = game().updatePlayer(2L) { it.copy(alive = false) }
        val next = use(state)
        assertTrue(preached(next, 2L))
        assertEquals(2L, next.prompts.single { it.sourceId == "preacher" }.subjectPlayerId)
    }

    @Test
    fun `Vortox changes only the information while the Minion still loses its ability`() {
        val next = use(game(demon = "vortox"))
        assertTrue(preached(next, 2L))
        assertFalse(Status.hasAbility(next, lookup, 2L))
        val prompt = next.prompts.single { it.sourceId == "preacher" }
        assertEquals(false, prompt.cardsTruthful)
        assertEquals(ShowCardSpec.Message("THE PREACHER DID NOT SELECT YOU"), prompt.cards.single())
    }
}
