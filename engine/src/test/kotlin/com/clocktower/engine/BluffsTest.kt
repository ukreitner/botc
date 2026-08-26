package com.clocktower.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bluffs are a LIST of source-qualified requirements (lead D20/D38), so one
 * seat can hold two sets and the Snitch can owe every Minion three of their own.
 */
class BluffsTest {

    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character
    private val allCharacters = data.characters.filter { it.edition in setOf("tb", "bmr", "sv") }

    private fun game(vararg characterIds: String): GameState {
        var state = Seats.newGame(script, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { index, id ->
            state = Seats.assignCharacter(state, index.toLong(), id)
        }
        return state
    }

    /** A 10-seat game: the named characters, padded with plain Townsfolk. */
    private fun tenPlayer(vararg characterIds: String): GameState {
        val padding = data.characters
            .filter { it.team == Team.TOWNSFOLK && it.edition == "tb" && it.id !in characterIds }
            .map { it.id }
        return game(*(characterIds.toList() + padding).take(10).toTypedArray())
    }

    @Test
    fun `a snitch owes every minion an independent set`() {
        val state = tenPlayer("imp", "poisoner", "baron", "snitch")
        val reqs = Bluffs.requirements(state, lookup)
        assertEquals(3, reqs.size, reqs.map { it.key }.toString())
        assertEquals(BluffRequirement.DEMON_KEY, reqs.first().key)
        assertEquals(listOf("snitch:1", "snitch:2"), reqs.drop(1).map { it.key })
        assertTrue(reqs.all { it.size == 3 })
        assertTrue(reqs.drop(1).all { it.stepSlotId == "snitch" })
        assertTrue(reqs.drop(1).none { it.allowInPlay }, "a sober Snitch gives not-in-play bluffs")
    }

    @Test
    fun `the snitch never gives the marionette a set`() {
        val state = tenPlayer("imp", "marionette", "poisoner", "snitch")
        val reqs = Bluffs.requirements(state, lookup)
        val marionetteSeat = assertNotNull(state.players.first { it.characterId == "marionette" })
        assertTrue(reqs.none { it.recipientId == marionetteSeat.id })
        // The retired "+3" jinx must not come back: the Demon still gets 3.
        assertEquals(3, reqs.first { it.key == BluffRequirement.DEMON_KEY }.size)
    }

    @Test
    fun `a legion seat under a snitch carries two independent sets`() {
        val state = tenPlayer("legion", "legion", "snitch")
        val reqs = Bluffs.requirements(state, lookup)
        val legionSeats = state.players.filter { it.characterId == "legion" }.map { it.id }
        assertTrue(reqs.any { it.key == BluffRequirement.DEMON_KEY })
        for (seat in legionSeats) {
            assertEquals(1, reqs.count { it.key == "snitch:$seat" }, "one Snitch set per Legion seat")
        }
        // "Bluffs are optional" with Legion in play.
        assertEquals(false, reqs.first { it.key == BluffRequirement.DEMON_KEY }.required)
    }

    @Test
    fun `a lunatic gets their own set and it may name in-play characters`() {
        var state = tenPlayer("imp", "lunatic", "poisoner", "empath")
        state = Identity.applyLunaticTokenSwap(state, lookup)
        val reqs = Bluffs.requirements(state, lookup)
        val lunaticSeat = state.players.first { it.characterId == "lunatic" }.id
        val lunaticReq = assertNotNull(reqs.firstOrNull { it.key == "lunatic:$lunaticSeat" })
        assertTrue(lunaticReq.allowInPlay)
        assertEquals("lunatic", lunaticReq.stepSlotId)

        val candidates = Bluffs.candidates(state, allCharacters, lunaticReq)
        assertTrue(candidates.any { it.character.id == "empath" && it.inPlay }, "in play is allowed")
        assertTrue(
            candidates.none { it.character.id == "imp" },
            "never the Demon they believe they are",
        )
    }

    @Test
    fun `a demon set never offers an in-play character`() {
        val state = tenPlayer("imp", "poisoner", "empath")
        val demonReq = Bluffs.requirements(state, lookup).first { it.key == BluffRequirement.DEMON_KEY }
        val candidates = Bluffs.candidates(state, allCharacters, demonReq)
        assertTrue(candidates.none { it.character.id == "empath" })
        assertTrue(candidates.none { it.character.team.isEvil })
    }

    @Test
    fun `lil monsta and atheist games hand out no bluffs at all`() {
        assertEquals(emptyList(), Bluffs.requirements(tenPlayer("lilmonsta", "poisoner"), lookup))
        assertEquals(emptyList(), Bluffs.requirements(tenPlayer("atheist"), lookup))
    }

    @Test
    fun `a summoner game gives the summoner the set while there is no demon`() {
        val state = tenPlayer("summoner", "poisoner")
        val reqs = Bluffs.requirements(state, lookup)
        val summonerSeat = state.players.first { it.characterId == "summoner" }.id
        assertEquals(listOf("summoner:$summonerSeat"), reqs.map { it.key })
        assertEquals("summoner", reqs.single().stepSlotId)
        assertTrue(reqs.single().required)

        // Jinx: the Alchemist-Summoner does not get bluffs.
        val jinxed = tenPlayer("summoner", "alchemist")
        assertEquals(false, Bluffs.requirements(jinxed, lookup).single().required)
    }

    @Test
    fun `a poppy grower moves the demon's bluffs to their own step`() {
        val state = tenPlayer("imp", "poisoner", "poppygrower")
        val req = Bluffs.requirements(state, lookup).first { it.key == BluffRequirement.DEMON_KEY }
        assertEquals(NightMarkers.DEMON_BLUFFS_ONLY, req.stepSlotId)
    }

    @Test
    fun `teensyville games get no demon bluffs unless a poppy grower or summoner is in play`() {
        assertEquals(emptyList(), Bluffs.requirements(game("imp", "poisoner", "chef", "empath", "monk"), lookup))
        val withPoppy = game("imp", "poisoner", "poppygrower", "empath", "monk")
        assertEquals(
            listOf(BluffRequirement.DEMON_KEY),
            Bluffs.requirements(withPoppy, lookup).map { it.key },
        )
    }

    @Test
    fun `an impaired snitch may hand out in-play characters`() {
        var state = tenPlayer("imp", "poisoner", "snitch")
        state = Effects.addReminder(state, 2, PlacedReminder("poisoner", "Poisoned"))
        val snitchReqs = Bluffs.requirements(state, lookup).filter { it.sourceId == "snitch" }
        assertTrue(snitchReqs.isNotEmpty())
        assertTrue(snitchReqs.all { it.allowInPlay })
    }

    // ---- storage, suggestion and conflicts ----

    @Test
    fun `sets round-trip under their own keys and a six-bluff set is accepted`() {
        var state = tenPlayer("imp", "poisoner", "snitch")
        state = Bluffs.set(state, BluffRequirement.DEMON_KEY, listOf("chef", "monk", "saint"))
        state = Bluffs.set(state, "snitch:1", listOf("virgin", "slayer", "butler", "fool", "tinker", "gossip"))
        assertEquals(listOf("chef", "monk", "saint"), state.demonBluffIds)
        assertEquals(6, state.bluffSets["snitch:1"]?.size, "the engine never truncates to 3")
        assertEquals(emptyList(), Bluffs.clear(state, "snitch:1").bluffSets["snitch:1"].orEmpty())
    }

    @Test
    fun `suggest draws two townsfolk and an outsider that are not in play`() {
        val state = tenPlayer("imp", "poisoner", "empath")
        val req = Bluffs.requirements(state, lookup).first { it.key == BluffRequirement.DEMON_KEY }
        val picks = Bluffs.suggest(state, allCharacters, req, Random(11))
        assertEquals(3, picks.size)
        assertEquals(3, picks.distinct().size)
        val teams = picks.map { assertNotNull(data.character(it)).team }
        assertEquals(2, teams.count { it == Team.TOWNSFOLK })
        assertEquals(1, teams.count { it == Team.OUTSIDER })
        assertTrue(picks.none { it in state.players.mapNotNull { p -> p.characterId } })
    }

    @Test
    fun `a lunatic suggestion includes an in-play character`() {
        var state = tenPlayer("imp", "lunatic", "poisoner", "empath")
        state = Identity.applyLunaticTokenSwap(state, lookup)
        val lunaticSeat = state.players.first { it.characterId == "lunatic" }.id
        val req = Bluffs.requirements(state, lookup).first { it.key == "lunatic:$lunaticSeat" }
        val picks = Bluffs.suggest(state, allCharacters, req, Random(3))
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        assertEquals(3, picks.size)
        assertTrue(picks.any { it in inPlay }, "that is the Lunatic's tell: $picks")
    }

    @Test
    fun `candidates annotate rather than hide characters already spoken for`() {
        var state = game(
            "imp", "drunk", "poisoner", "washerwoman", "librarian",
            "investigator", "empath", "fortuneteller", "undertaker", "ravenkeeper",
        )
        state = Seats.setShownCharacter(state, 1, "chef")
        state = Decisions.set(state, Decisions.BOFFIN_GRANT, "monk")
        val req = Bluffs.requirements(state, lookup).first { it.key == BluffRequirement.DEMON_KEY }
        val candidates = Bluffs.candidates(state, allCharacters, req)
        assertEquals(
            "the Drunk believes this",
            candidates.first { it.character.id == "chef" }.inUseBy,
        )
        assertEquals(
            "the Boffin gave the Demon this",
            candidates.first { it.character.id == "monk" }.inUseBy,
        )
    }

    @Test
    fun `conflicts report a bluff that has come into play`() {
        var state = tenPlayer("imp", "poisoner", "empath")
        state = Bluffs.set(state, BluffRequirement.DEMON_KEY, listOf("empath", "monk", "saint"))
        val conflicts = Bluffs.conflicts(state, lookup)
        assertTrue(conflicts.any { "Empath" in it && "now in play" in it }, conflicts.toString())

        // A Lunatic's set is allowed to hold in-play characters.
        var lunaticGame = tenPlayer("imp", "lunatic", "poisoner", "empath")
        lunaticGame = Identity.applyLunaticTokenSwap(lunaticGame, lookup)
        val seat = lunaticGame.players.first { it.characterId == "lunatic" }.id
        lunaticGame = Bluffs.set(lunaticGame, "lunatic:$seat", listOf("empath", "monk", "saint"))
        assertEquals(emptyList(), Bluffs.conflicts(lunaticGame, lookup))
    }
}
