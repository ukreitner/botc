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

    private val bmr by lazy { data.builtInScripts().first { it.id == "bmr" } }

    // ---- fixture helpers ---------------------------------------------------

    /** A seated game on the custom script, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState = gameOn(script, *roles)

    /** The same, on the published Bad Moon Rising script. */
    private fun bmrGame(vararg roles: String): GameState = gameOn(bmr, *roles)

    private fun gameOn(on: Script, vararg roles: String): GameState {
        var state = GameActions.newGame(on, roles.indices.map { "P${it + 1}" })
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

    // ==================================================================
    // "The Demon knows … who you choose at night"
    // ==================================================================

    private fun name(state: GameState, playerId: Long): String =
        assertNotNull(state.player(playerId)).name

    /** The sentence the Demon's row owes tonight. */
    private fun expected(state: GameState, lunatic: Long, chosen: Long?): String {
        val who = if (chosen == null) "nobody tonight" else name(state, chosen)
        return "The Lunatic (${name(state, lunatic)}) chose $who."
    }

    private fun toldAbout(state: GameState, demon: Long): List<LedgerEntry> = state.ledger.filter {
        it.kind == LedgerKind.TOLD && it.sourceId == "lunatic" && it.actorId == demon
    }

    @Test
    fun `the Po is shown what the Lunatic chose tonight, and it goes in the ledger`() {
        var state = bmrGame("lunatic", "po", "godfather", "sailor", "fool", "gossip", "chambermaid")
        val lunatic = seat(state, "lunatic")
        val po = seat(state, "po")
        state = GameActions.setShownCharacter(state, lunatic, "po")
        state = secondNight(state)

        // Before the Lunatic acts the Demon's row promises nothing.
        val before = illusionRow(state, "po", po)
        assertFalse(expected(state, lunatic, null) in before.banner, before.banner)
        assertTrue(toldAbout(state, po).isEmpty())

        val victim = seat(state, "sailor")
        state = NightPlan.resolve(
            state,
            lookup,
            illusionRow(state, "lunatic", lunatic).key,
            NightInput(playerIds = listOf(victim)),
        )

        // The real Po's own row now names the seat the Lunatic pointed at.
        val row = illusionRow(state, "po", po)
        val sentence = expected(state, lunatic, victim)
        assertTrue(sentence in row.banner, "the Po's banner: ${row.banner}")
        assertTrue(sentence in row.detail, "the Po's detail: ${row.detail}")

        // …and ticking it records what the Demon was actually told.
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(victim)))
        val told = toldAbout(state, po)
        assertEquals(1, told.size, "one TOLD row: $told")
        assertEquals(listOf(victim), told.single().targetIds)
        assertEquals(sentence, told.single().text)
    }

    @Test
    fun `an Imp on a custom script is told the Lunatic's choice too`() {
        var state = game("lunatic", "imp", "poisoner", "monk", "chef", "soldier")
        val lunatic = seat(state, "lunatic")
        val imp = seat(state, "imp")
        state = GameActions.setShownCharacter(state, lunatic, "imp")
        state = secondNight(state)

        val victim = seat(state, "chef")
        state = NightPlan.resolve(
            state,
            lookup,
            illusionRow(state, "lunatic", lunatic).key,
            NightInput(playerIds = listOf(victim)),
        )

        val row = illusionRow(state, "imp", imp)
        assertTrue(expected(state, lunatic, victim) in row.banner, "the Imp's banner: ${row.banner}")

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(victim)))
        assertEquals(1, toldAbout(state, imp).size)
    }

    @Test
    fun `a Lunatic who chose nobody is reported as having chosen nobody`() {
        var state = bmrGame("lunatic", "po", "godfather", "sailor", "fool", "gossip", "chambermaid")
        val lunatic = seat(state, "lunatic")
        val po = seat(state, "po")
        state = GameActions.setShownCharacter(state, lunatic, "po")
        state = secondNight(state)

        state = NightPlan.resolve(
            state,
            lookup,
            illusionRow(state, "lunatic", lunatic).key,
            NightInput(none = true),
        )

        val row = illusionRow(state, "po", po)
        assertTrue(expected(state, lunatic, null) in row.banner, "the Po's banner: ${row.banner}")
    }

    @Test
    fun `on night one the Demon info row carries the choice the Lunatic already made`() {
        // The Lunatic's first-night slot (16) comes BEFORE DEMON_INFO (17), and a
        // believed Pukka is one of the Demons that does wake on night 1.
        var state = bmrGame(
            "lunatic", "po", "godfather", "assassin",
            "sailor", "fool", "gossip", "chambermaid",
        )
        val lunatic = seat(state, "lunatic")
        val po = seat(state, "po")
        state = GameActions.setShownCharacter(state, lunatic, "pukka")

        val order = NightPlan.build(state, lookup).steps.map { it.slotId }
        assertTrue(
            order.indexOf("lunatic") < order.indexOf(NightMarkers.DEMON_INFO),
            "the Lunatic acts before the Demon is woken: $order",
        )

        val victim = seat(state, "sailor")
        state = NightPlan.resolve(
            state,
            lookup,
            illusionRow(state, "lunatic", lunatic).key,
            NightInput(playerIds = listOf(victim)),
        )
        assertTrue(assertNotNull(state.player(victim)).alive, "a believed Pukka poisons nobody")

        val info = assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull { it.slotId == NightMarkers.DEMON_INFO },
        )
        val sentence = expected(state, lunatic, victim)
        assertTrue(sentence in info.detail, "the Demon info row: ${info.detail}")

        state = NightPlan.resolve(state, lookup, info.key, NightInput())
        assertEquals(1, toldAbout(state, po).size, "the real Demon's ledger records it")
    }

    @Test
    fun `nobody but the informed team is briefed`() {
        var state = bmrGame("lunatic", "po", "godfather", "sailor", "fool", "gossip", "chambermaid")
        val lunatic = seat(state, "lunatic")
        state = GameActions.setShownCharacter(state, lunatic, "po")
        state = secondNight(state)

        val victim = seat(state, "sailor")
        state = NightPlan.resolve(
            state,
            lookup,
            illusionRow(state, "lunatic", lunatic).key,
            NightInput(playerIds = listOf(victim)),
        )

        val sentence = expected(state, lunatic, victim)
        for (step in NightPlan.build(state, lookup).steps) {
            val team = step.holderId
                ?.let { state.player(it) }
                ?.characterId
                ?.let(lookup)
                ?.team
            if (team == Team.DEMON) continue
            assertFalse(
                sentence in step.banner || sentence in step.detail,
                "${step.slotId} is not on the Demon's team and must not be told: $step",
            )
        }
        // The Lunatic's own row least of all.
        val own = illusionRow(state, "lunatic", lunatic)
        assertFalse(sentence in own.banner || sentence in own.detail, own.banner + own.detail)
    }
}
