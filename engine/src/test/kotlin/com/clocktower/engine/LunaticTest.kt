package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Lunatic, and every other seat running an ability it does not have.
 *
 * "You think you are a Demon, but you are not." `Identity.derivedGrants` REPLACES
 * the Lunatic's ability with the believed Demon's, at the Lunatic's own slot and
 * with `alwaysFalse = true` (lead D70) — so the row the storyteller runs is a
 * REAL Demon's registry row, on a good Outsider's seat.
 *
 * Before this file the neutralising was per-character: four Bad Moon Rising rows
 * checked `ActingRole.alwaysFalse` themselves and every other Demon in the game
 * did not, so a Lunatic who believed they were the Imp ran the real attack and
 * somebody died. The planner does it generically now, which is what these tests
 * prove — on a hand-rolled script that mixes editions, because a Lunatic and a
 * Sects & Violets Demon only ever meet on a custom script.
 */
class LunaticTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    /**
     * A homebrew script in the official script-tool format, carrying the Lunatic,
     * the Drunk and five Demons from four different editions. This is the only
     * way the pairs below can be dealt at all.
     */
    private val scriptJson = """
        [
          {"id": "_meta", "name": "The Asylum", "author": "The Storyteller"},
          "washerwoman", "chef", "empath", "monk", "undertaker", "soldier", "ravenkeeper",
          "lunatic", "drunk", "butler",
          "poisoner", "baron", "scarlet_woman",
          "imp", "fang_gu", "vigormortis", "shabaloth", "po"
        ]
    """.trimIndent()

    private val script by lazy { ScriptParser.parse(scriptJson) }

    // ---- fixture helpers ---------------------------------------------------

    /** A seated game on the custom script, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return Phases.advancePhase(state, lookup)
    }

    /** Night 2 with a clean sheet: every believed Demon but the Pukka acts there. */
    private fun secondNight(state: GameState): GameState =
        state.copy(cycle = 2, nightStepsDone = emptySet())

    private fun seat(state: GameState, characterId: String): Long =
        assertNotNull(
            state.players.firstOrNull { it.characterId == characterId },
            "no $characterId seated",
        ).id

    private fun carries(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val player = state.player(playerId) ?: return false
        return player.reminders.any { Tokens.key(it) == key } ||
            state.effects.any {
                it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
            }
    }

    /** The believer's row: their own night slot, running somebody else's ability. */
    private fun illusionRow(state: GameState, slotId: String, holderId: Long): NightStep =
        assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull {
                it.slotId == slotId && it.holderId == holderId
            },
            "no $slotId row for seat $holderId: " +
                NightPlan.build(state, lookup).steps.map { "${it.slotId}/${it.holderId}" },
        )

    /** A Lunatic believing [demonId], on night 2, and their row. */
    private fun believing(demonId: String, vararg roles: String): Pair<GameState, NightStep> {
        var state = game(*roles)
        val lunatic = seat(state, "lunatic")
        state = GameActions.setShownCharacter(state, lunatic, demonId)
        state = secondNight(state)
        return state to illusionRow(state, "lunatic", lunatic)
    }

    // ==================================================================
    // The believed Demon's picker, with every consequence removed
    // ==================================================================

    @Test
    fun `a Lunatic who believes they are the Imp kills nobody and marks their picks`() {
        val (start, row) = believing("imp", "lunatic", "imp", "poisoner", "monk", "chef", "soldier")
        var state = start

        assertEquals("imp", row.abilityId, "they run the Imp's own registry row (D70)")
        assertTrue(row.banner.isNotBlank(), "and the row says the whole thing is an illusion")
        val choose = assertIs<ChoosePlayers>(row.action, "the Imp's picker shape is kept")
        assertEquals(1, choose.max, "the Imp points at exactly one player")
        assertTrue(
            TargetConstraint.SELF_ALLOWED in choose.constraints,
            "including themselves — that is how a star pass is faked",
        )
        assertTrue(choose.perTarget.isEmpty(), "and it carries no effect at all: ${choose.perTarget}")

        val victim = seat(state, "chef")
        val aliveBefore = state.alivePlayers.size
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(victim)))

        assertEquals(aliveBefore, state.alivePlayers.size, "nobody dies")
        assertTrue(state.deaths.isEmpty())
        assertTrue(carries(state, victim, "lunatic", "Chosen"), "the Lunatic's own marker is drawn")
        assertFalse(carries(state, victim, "imp", "Dead"), "never the believed Demon's")
    }

    @Test
    fun `a Lunatic who believes they are the Imp star-passes nothing by picking themselves`() {
        val (start, row) = believing("imp", "lunatic", "imp", "poisoner", "monk", "chef", "soldier")
        var state = start
        val lunatic = seat(state, "lunatic")
        val minion = seat(state, "poisoner")

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(lunatic)))

        assertTrue(assertNotNull(state.player(lunatic)).alive, "the Lunatic survives their own attack")
        assertEquals("lunatic", state.player(lunatic)?.characterId, "and is still the Lunatic")
        assertEquals("poisoner", state.player(minion)?.characterId, "no Minion became the Imp")
        assertEquals("imp", state.player(seat(state, "imp"))?.characterId, "the real Imp is untouched")
        assertTrue(state.deaths.isEmpty())
    }

    @Test
    fun `a Lunatic who believes they are the Fang Gu makes no Outsider jump`() {
        val (start, row) = believing("fanggu", "lunatic", "fanggu", "baron", "butler", "chef", "monk")
        var state = start
        val outsider = seat(state, "butler")

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(outsider)))

        assertEquals("butler", state.player(outsider)?.characterId, "the Butler does not become a Fang Gu")
        assertFalse(assertNotNull(state.player(outsider)).isEvil(lookup), "and does not turn evil")
        assertTrue(assertNotNull(state.player(outsider)).alive)
        assertTrue(
            assertNotNull(state.player(seat(state, "lunatic"))).alive,
            "and the Lunatic does not die in their place",
        )
        assertTrue(
            state.prompts.none { Character.normalizeId(it.sourceId) == "fanggu" },
            "no jump is even offered: ${state.prompts.map { it.title }}",
        )
        assertTrue(carries(state, outsider, "lunatic", "Chosen"))
    }

    @Test
    fun `a Lunatic who believes they are the Vigormortis poisons nobody`() {
        val (start, row) = believing(
            "vigormortis", "lunatic", "vigormortis", "poisoner", "chef", "monk", "soldier",
        )
        var state = start
        val minion = seat(state, "poisoner")

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(minion)))

        assertTrue(assertNotNull(state.player(minion)).alive, "the Minion does not die")
        assertFalse(carries(state, minion, "vigormortis", "Has Ability"))
        for (player in state.players) {
            assertFalse(
                carries(state, player.id, "vigormortis", "Poisoned"),
                "${player.name} must not be poisoned by an illusion",
            )
        }
        assertTrue(
            state.effects.none { it.kind == EffectKind.POISONED },
            "an illusion poisons nobody at all: ${state.effects.map { it.label }}",
        )
        assertTrue(carries(state, minion, "lunatic", "Chosen"))
    }

    @Test
    fun `a Lunatic who believes they are the Shabaloth chooses two and kills neither`() {
        val (start, row) = believing(
            "shabaloth", "lunatic", "shabaloth", "baron", "chef", "monk", "soldier",
        )
        var state = start

        val choose = assertIs<ChoosePlayers>(row.action)
        assertEquals(2, choose.max, "the Shabaloth's two picks are kept")
        assertTrue(choose.perTarget.isEmpty(), "with neither of them a kill: ${choose.perTarget}")

        val picks = listOf(seat(state, "chef"), seat(state, "monk"))
        val aliveBefore = state.alivePlayers.size
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = picks))

        assertEquals(aliveBefore, state.alivePlayers.size, "nobody dies")
        for (pick in picks) {
            assertTrue(carries(state, pick, "lunatic", "Chosen"), "seat $pick is marked Chosen")
            assertFalse(carries(state, pick, "shabaloth", "Dead"))
        }
    }

    // ==================================================================
    // Every other believer — the Drunk, who owns no marker
    // ==================================================================

    @Test
    fun `a Drunk shown a Demon token is neutralised too, and leaves no Chosen marker`() {
        var state = game("drunk", "imp", "poisoner", "monk", "chef", "soldier")
        val drunk = seat(state, "drunk")
        state = GameActions.setShownCharacter(state, drunk, "imp")
        state = secondNight(state)

        // A Drunk keeps no slot of their own: they wake at the Imp's, beside the
        // real Imp, and each seat gets its own row.
        val row = illusionRow(state, "imp", drunk)
        assertEquals("drunk", row.sourceId)
        val choose = assertIs<ChoosePlayers>(row.action)
        assertTrue(choose.perTarget.none { it is NightEffect.Attack }, "no kill: ${choose.perTarget}")

        val victim = seat(state, "chef")
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(victim)))

        assertTrue(assertNotNull(state.player(victim)).alive, "a Drunk's attack kills nobody")
        assertTrue(state.deaths.isEmpty())
        assertFalse(
            carries(state, victim, "lunatic", "Chosen"),
            "and only the Lunatic owns an illusion marker",
        )
    }

    @Test
    fun `only the Lunatic declares an illusion token`() {
        val believers = listOf("lunatic", "drunk", "marionette")
        for (id in believers) {
            val rule = assertNotNull(CharacterRules.all[id], id)
            val expected = if (id == "lunatic") Tokens.key("lunatic", "Chosen") else null
            assertEquals(expected, rule.illusionToken?.key, "$id's illusion token")
        }
    }
}
