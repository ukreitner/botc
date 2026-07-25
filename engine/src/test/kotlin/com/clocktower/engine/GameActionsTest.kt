package com.clocktower.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameActionsTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }

    private fun newGame(players: Int = 8): GameState =
        GameActions.newGame(tb, (1..players).map { "P$it" })

    @Test
    fun `kill records death and grants ghost vote`() {
        var state = GameActions.advancePhase(newGame()) // night 1
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        val dead = assertNotNull(state.player(2))
        assertFalse(dead.alive)
        assertFalse(dead.ghostVoteUsed)
        assertEquals(1, state.deaths.size)
        assertTrue(state.deaths.first().atNight)
        // killing again is a no-op
        assertEquals(state, GameActions.kill(state, 2, DeathCause.EXECUTION))
    }

    @Test
    fun `revive clears same-cycle death record`() {
        var state = GameActions.advancePhase(newGame())
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        state = GameActions.revive(state, 2)
        assertTrue(assertNotNull(state.player(2)).alive)
        assertTrue(state.deaths.isEmpty())
    }

    @Test
    fun `phase cycle advances night day night`() {
        var state = newGame()
        assertEquals(Phase.SETUP, state.phase)
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(1, state.cycle)
        state = GameActions.advancePhase(state)
        assertEquals(Phase.DAY, state.phase)
        assertEquals(1, state.cycle)
        state = GameActions.toggleNightStep(state, "poisoner")
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(2, state.cycle)
        assertTrue(state.nightStepsDone.isEmpty(), "night checklist resets each night")
    }

    @Test
    fun `execution threshold is half alive rounded up`() {
        assertEquals(4, Voting.executionThreshold(8))
        assertEquals(4, Voting.executionThreshold(7))
        assertEquals(3, Voting.executionThreshold(5))
        assertEquals(2, Voting.executionThreshold(3))
    }

    @Test
    fun `vote outcome logic`() {
        // Below threshold: safe.
        assertEquals(NominationResult.SAFE, Voting.outcome(votes = 3, threshold = 4, currentHighest = 0))
        // Meets threshold, beats nothing: about to die.
        assertEquals(NominationResult.ABOUT_TO_DIE, Voting.outcome(4, 4, 0))
        // Ties the current highest: nobody dies.
        assertEquals(NominationResult.TIED, Voting.outcome(5, 4, 5))
        // Below the current highest: safe.
        assertEquals(NominationResult.SAFE, Voting.outcome(4, 4, 5))
        // Beats the current highest: new about-to-die.
        assertEquals(NominationResult.ABOUT_TO_DIE, Voting.outcome(6, 4, 5))
    }

    @Test
    fun `random bag matches distribution for trouble brewing`() {
        val characters = data.resolve(tb)
        for (count in 5..15) {
            val bag = assertNotNull(
                GameActions.randomBag(characters, count, Random(count)),
                "no bag for $count players",
            )
            assertEquals(count, bag.size)
            assertTrue(GameActions.validateBag(bag, count).isEmpty(), "invalid bag for $count players")
        }
    }

    @Test
    fun `bag with baron balances outsiders`() {
        val characters = data.resolve(tb)
        // Force many attempts; whenever the Baron lands in a bag it must carry +2 outsiders.
        var seenBaron = false
        for (seed in 0..60) {
            val bag = GameActions.randomBag(characters, 10, Random(seed)) ?: continue
            if (bag.any { it.id == "baron" }) {
                seenBaron = true
                assertEquals(2, bag.count { it.team == Team.OUTSIDER })
                assertEquals(5, bag.count { it.team == Team.TOWNSFOLK })
            }
        }
        assertTrue(seenBaron, "baron never appeared across 60 seeds")
    }

    @Test
    fun `deal skips travellers and assigns everyone else`() {
        var state = newGame(6)
        state = GameActions.assignCharacter(state, 5, "beggar", isTraveller = true)
        val bag = listOf("imp", "poisoner", "washerwoman", "chef", "butler")
        state = GameActions.deal(state, bag, Random(1))
        assertEquals("beggar", state.player(5)?.characterId)
        val assigned = state.players.filter { !it.isTraveller }.mapNotNull { it.characterId }
        assertEquals(bag.toSet(), assigned.toSet())
    }

    @Test
    fun `deal rejects partial bags instead of retaining stale assignments`() {
        var state = newGame(6)
        repeat(6) { i ->
            state = GameActions.assignCharacter(state, i.toLong(), "old$i")
        }

        assertFailsWith<IllegalArgumentException> {
            GameActions.deal(state, listOf("imp", "poisoner", "chef"), Random(1))
        }
        assertFailsWith<IllegalArgumentException> {
            GameActions.deal(state, List(7) { "role$it" }, Random(1))
        }
        assertEquals(List(6) { "old$it" }, state.players.map { it.characterId })
    }

    @Test
    fun `nomination bookkeeping`() {
        var state = GameActions.advancePhase(GameActions.advancePhase(newGame())) // day 1
        state = GameActions.recordNomination(
            state,
            Nomination(day = 1, nominatorId = 0, nomineeId = 3, votes = 5, result = NominationResult.ABOUT_TO_DIE),
        )
        assertTrue(GameActions.hasNominatedToday(state, 0))
        assertFalse(GameActions.hasNominatedToday(state, 3))
        assertTrue(GameActions.hasBeenNominatedToday(state, 3))
        assertEquals(5, GameActions.highestVotesToday(state))
    }

    @Test
    fun `night order for an in-play tb game`() {
        var state = newGame(8)
        val assignments = listOf("imp", "poisoner", "washerwoman", "empath", "fortuneteller", "butler", "drunk", "mayor")
        assignments.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state)

        val first = data.nightOrder.firstNight(state, data::character)
        val ids = first.map { it.id }
        // Dusk, minion info, demon info present; poisoner before washerwoman; imp does not act night one.
        assertTrue(ids.contains(NightMarkers.DUSK))
        assertTrue(ids.contains(NightMarkers.MINION_INFO))
        assertTrue(ids.contains(NightMarkers.DEMON_INFO))
        assertTrue(ids.indexOf("poisoner") < ids.indexOf("washerwoman"))
        assertFalse(ids.contains("imp"))
        assertFalse(ids.contains("mayor"))

        val other = data.nightOrder.otherNight(state, data::character)
        val otherIds = other.map { it.id }
        assertTrue(otherIds.contains("imp"))
        assertTrue(otherIds.indexOf("poisoner") < otherIds.indexOf("imp"))
        assertTrue(otherIds.indexOf("imp") < otherIds.indexOf("empath"))
    }

    @Test
    fun `minion and demon info omitted in teensyville games`() {
        var state = newGame(5)
        listOf("imp", "poisoner", "washerwoman", "empath", "mayor").forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        state = GameActions.advancePhase(state)
        val ids = data.nightOrder.firstNight(state, data::character).map { it.id }
        assertFalse(ids.contains(NightMarkers.MINION_INFO))
        assertFalse(ids.contains(NightMarkers.DEMON_INFO))
    }

    @Test
    fun `about to die follows the nomination sequence including ties`() {
        var state = GameActions.advancePhase(GameActions.advancePhase(newGame(8))) // day 1
        fun nominate(nominator: Long, nominee: Long, votes: Int) {
            val result = Voting.outcome(votes, state.executionThreshold, GameActions.highestVotesToday(state))
            state = GameActions.recordNomination(
                state,
                Nomination(state.cycle, nominator, nominee, votes, emptyList(), result),
            )
        }
        nominate(0, 3, 5)
        assertEquals(3L, GameActions.aboutToDie(state))
        nominate(1, 4, 5) // tie — block clears
        assertEquals(null, GameActions.aboutToDie(state))
        nominate(2, 5, 6) // beats the tally — new block
        assertEquals(5L, GameActions.aboutToDie(state))
        nominate(3, 6, 4) // below the tally — no change
        assertEquals(5L, GameActions.aboutToDie(state))
    }

    @Test
    fun `revive drops only the most recent death record`() {
        var state = GameActions.advancePhase(newGame())
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        state = GameActions.kill(state, 3, DeathCause.OTHER_NIGHT_DEATH)
        state = GameActions.revive(state, 2)
        assertEquals(listOf(3L), state.deaths.map { it.playerId })
        // Kill, revive, kill again in one cycle keeps exactly one record.
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        state = GameActions.revive(state, 2)
        state = GameActions.kill(state, 2, DeathCause.DEMON)
        assertEquals(2, state.deaths.size)
    }

    @Test
    fun `random bag handles lil monsta as the only demon`() {
        val pool = data.resolve(data.builtInScripts().first { it.id == "bmr" })
            .filter { it.team != Team.DEMON } + listOf(assertNotNull(data.character("lilmonsta")))
        val bag = assertNotNull(GameActions.randomBag(pool, 10, Random(7)))
        assertEquals(10, bag.size)
        assertEquals(3, bag.count { it.team == Team.MINION }, "+1 Minion applied")
        assertEquals(1, bag.count { it.id == "lilmonsta" })
        assertTrue(GameActions.validateBag(bag, 10).isEmpty())
    }

    @Test
    fun `random bag with summoner has no demon`() {
        val tbChars = data.resolve(tb).filter { it.team != Team.MINION }
        val pool = tbChars + listOf(assertNotNull(data.character("summoner")))
        val bag = assertNotNull(GameActions.randomBag(pool, 8, Random(3)))
        assertEquals(8, bag.size)
        assertEquals(0, bag.count { it.team == Team.DEMON })
        assertTrue(GameActions.validateBag(bag, 8).isEmpty())
    }

    @Test
    fun `random bag with huntsman always includes the damsel`() {
        val pool = data.resolve(tb).filter { it.team != Team.TOWNSFOLK } +
            listOf("huntsman", "damsel").map { assertNotNull(data.character(it)) } +
            data.resolve(tb).filter { it.team == Team.TOWNSFOLK }.take(5)
        var seenHuntsman = false
        for (seed in 0..40) {
            val bag = GameActions.randomBag(pool, 8, Random(seed)) ?: continue
            assertTrue(GameActions.validateBag(bag, 8).isEmpty(), "seed $seed invalid: ${GameActions.validateBag(bag, 8)}")
            if (bag.any { it.id == "huntsman" }) {
                seenHuntsman = true
                assertTrue(bag.any { it.id == "damsel" }, "huntsman without damsel (seed $seed)")
            }
        }
        assertTrue(seenHuntsman, "huntsman never drawn")
    }

    @Test
    fun `vigormortis bag validates at zero outsider counts`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        val chars = data.resolve(sv)
        val bag = chars.filter { it.team == Team.TOWNSFOLK }.take(7) +
            chars.filter { it.id == "pithag" || it.id == "cerenovus" } +
            chars.filter { it.id == "vigormortis" }
        assertEquals(10, bag.size)
        assertTrue(GameActions.validateBag(bag, 10).isEmpty(), GameActions.validateBag(bag, 10).toString())
    }

    @Test
    fun `15 player vigormortis bag requires one outsider compensated by ten townsfolk`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        val chars = data.resolve(sv)
        val townsfolk = chars.filter { it.team == Team.TOWNSFOLK }
        val outsiders = chars.filter { it.team == Team.OUTSIDER }
        val minions = chars.filter { it.team == Team.MINION }
        val vigormortis = assertNotNull(chars.find { it.id == "vigormortis" })

        val uncompensated = townsfolk.take(9) + outsiders.take(2) + minions.take(3) + vigormortis
        val invalidIssues = GameActions.validateBag(uncompensated, 15)
        assertTrue(invalidIssues.any { it == "Townsfolk: 9 in bag, expected 10" }, invalidIssues.toString())
        assertTrue(invalidIssues.any { it == "Outsider: 2 in bag, expected 1" }, invalidIssues.toString())

        val compensated = townsfolk.take(10) + outsiders.take(1) + minions.take(3) + vigormortis
        assertEquals(15, compensated.size)
        assertTrue(
            GameActions.validateBag(compensated, 15).isEmpty(),
            GameActions.validateBag(compensated, 15).toString(),
        )
    }

    @Test
    fun `godfather setup choice accepts either outsider count and compensating townsfolk`() {
        fun characters(vararg ids: String): List<Character> =
            ids.map { assertNotNull(data.character(it), it) }

        val noOutsider = characters("washerwoman", "chef", "empath", "godfather", "imp")
        val plusOneOutsider = characters("washerwoman", "chef", "recluse", "godfather", "imp")

        assertTrue(
            GameActions.validateBag(noOutsider, 5).isEmpty(),
            GameActions.validateBag(noOutsider, 5).toString(),
        )
        assertTrue(
            GameActions.validateBag(plusOneOutsider, 5).isEmpty(),
            GameActions.validateBag(plusOneOutsider, 5).toString(),
        )
    }

    @Test
    fun `godfather setup choice rejects outsider counts outside its two options`() {
        val bmr = data.builtInScripts().first { it.id == "bmr" }
        val chars = data.resolve(bmr)
        val townsfolk = chars.filter { it.team == Team.TOWNSFOLK }
        val outsiders = chars.filter { it.team == Team.OUTSIDER }
        val godfather = assertNotNull(chars.find { it.id == "godfather" })
        val minions = listOf(godfather) +
            chars.filter { it.team == Team.MINION && it.id != "godfather" }.take(2)
        val demon = assertNotNull(chars.find { it.team == Team.DEMON })

        fun bag(townsfolkCount: Int, outsiderCount: Int): List<Character> =
            townsfolk.take(townsfolkCount) + outsiders.take(outsiderCount) + minions + demon

        val unchanged = bag(townsfolkCount = 9, outsiderCount = 2)
        val issues = GameActions.validateBag(unchanged, 15)
        assertTrue(
            issues.any { it == "Outsider: 2 in bag, expected 1 or 3" },
            issues.toString(),
        )
        for (legal in listOf(
            bag(townsfolkCount = 10, outsiderCount = 1),
            bag(townsfolkCount = 8, outsiderCount = 3),
        )) {
            assertEquals(15, legal.size)
            assertTrue(
                GameActions.validateBag(legal, 15).isEmpty(),
                GameActions.validateBag(legal, 15).toString(),
            )
        }
    }

    @Test
    fun `Village Idiot allows at most two extra copies`() {
        val villageIdiot = assertNotNull(data.character("villageidiot"))
        val washerwoman = assertNotNull(data.character("washerwoman"))
        val chef = assertNotNull(data.character("chef"))
        val poisoner = assertNotNull(data.character("poisoner"))
        val imp = assertNotNull(data.character("imp"))

        val legal = List(3) { villageIdiot } + washerwoman + chef + poisoner + imp
        assertTrue(
            GameActions.validateBag(legal, 7).isEmpty(),
            GameActions.validateBag(legal, 7).toString(),
        )

        val tooMany = List(4) { villageIdiot } + washerwoman + poisoner + imp
        val issues = GameActions.validateBag(tooMany, 7)
        assertTrue(issues.any { "maximum 3" in it }, issues.toString())
    }

    @Test
    fun `setup state requires hidden information and Marionette seating`() {
        var drunkState = newGame(5)
        listOf("imp", "baron", "drunk", "recluse", "chef").forEachIndexed { i, id ->
            drunkState = GameActions.assignCharacter(drunkState, i.toLong(), id)
        }
        assertTrue(
            GameActions.validateSetupState(drunkState, data::character)
                .any { "show the Drunk" in it },
        )
        drunkState = GameActions.setShownCharacter(drunkState, 2, "washerwoman")
        assertTrue(
            GameActions.validateSetupState(drunkState, data::character).isEmpty(),
            GameActions.validateSetupState(drunkState, data::character).toString(),
        )

        var fortuneState = newGame(5)
        listOf("imp", "poisoner", "fortuneteller", "chef", "mayor").forEachIndexed { i, id ->
            fortuneState = GameActions.assignCharacter(fortuneState, i.toLong(), id)
        }
        assertTrue(
            GameActions.validateSetupState(fortuneState, data::character)
                .any { "red herring" in it },
        )
        fortuneState = GameActions.addReminder(
            fortuneState,
            4,
            PlacedReminder("fortuneteller", "Red herring"),
        )
        assertTrue(
            GameActions.validateSetupState(fortuneState, data::character).isEmpty(),
            GameActions.validateSetupState(fortuneState, data::character).toString(),
        )

        var marionetteState = newGame(5)
        listOf("imp", "chef", "marionette", "empath", "mayor").forEachIndexed { i, id ->
            marionetteState = GameActions.assignCharacter(marionetteState, i.toLong(), id)
        }
        marionetteState = GameActions.setShownCharacter(marionetteState, 2, "washerwoman")
        assertTrue(
            GameActions.validateSetupState(marionetteState, data::character)
                .any { "neighbor the Demon" in it },
        )
        marionetteState = GameActions.moveSeat(marionetteState, 2, -1)
        assertTrue(
            GameActions.validateSetupState(marionetteState, data::character).isEmpty(),
            GameActions.validateSetupState(marionetteState, data::character).toString(),
        )
    }

    @Test
    fun `atheist bag with no evil validates`() {
        val chars = data.resolve(tb) + listOf(assertNotNull(data.character("atheist")))
        val bag = chars.filter { it.team == Team.TOWNSFOLK }.take(6) +
            chars.filter { it.id == "atheist" }
        assertEquals(7, bag.size)
        assertTrue(GameActions.validateBag(bag, 7).isEmpty(), GameActions.validateBag(bag, 7).toString())
    }

    @Test
    fun `add and remove seats keep ids unique`() {
        var state = newGame(6)
        state = GameActions.addSeat(state, "Traveller Tim")
        assertEquals(7, state.players.size)
        val newId = state.players.last().id
        assertEquals(1, state.players.count { it.id == newId })
        state = GameActions.removeSeat(state, newId)
        assertEquals(6, state.players.size)
        // Adding after a removal never reuses a live id.
        state = GameActions.addSeat(state, "Again", afterId = 2)
        assertEquals(state.players.map { it.id }.toSet().size, state.players.size)
        assertEquals("Again", state.players[3].name)
    }

    @Test
    fun `move seat wraps around the circle`() {
        var state = newGame(5)
        state = GameActions.moveSeat(state, 0, +1)
        assertEquals(listOf(1L, 0L, 2L, 3L, 4L), state.players.map { it.id })
        state = GameActions.moveSeat(state, 0, -1)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), state.players.map { it.id })
        // Moving backwards from the first seat wraps to the end.
        state = GameActions.moveSeat(state, 0, -1)
        assertEquals(listOf(1L, 2L, 3L, 4L, 0L), state.players.map { it.id })
    }

    @Test
    fun `win check advises when demon dies or two remain`() {
        var state = newGame(8)
        val assignments = listOf("imp", "scarletwoman", "washerwoman", "empath", "chef", "recluse", "soldier", "mayor")
        assignments.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = GameActions.advancePhase(state)
        assertEquals(null, WinCheck.check(state, data::character))

        val demonDead = GameActions.kill(state, 0, DeathCause.EXECUTION)
        val advisory = assertNotNull(WinCheck.check(demonDead, data::character))
        assertEquals(true, advisory.goodWins)
        assertTrue(advisory.cautions.any { "Scarlet Woman" in it })

        var lastTwo = state
        for (id in listOf(1L, 2L, 3L, 4L, 5L, 6L)) {
            lastTwo = GameActions.kill(lastTwo, id, DeathCause.DEMON)
        }
        val evilWin = assertNotNull(WinCheck.check(lastTwo, data::character))
        assertEquals(false, evilWin.goodWins)
        assertTrue(evilWin.cautions.any { "Mayor" in it })
    }

    @Test
    fun `swap characters exchanges tokens only`() {
        var state = newGame(5)
        state = GameActions.assignCharacter(state, 0, "imp")
        state = GameActions.assignCharacter(state, 1, "mayor")
        state = GameActions.setShownCharacter(state, 1, "chef")
        state = GameActions.swapCharacters(state, 0, 1)
        assertEquals("mayor", state.player(0)?.characterId)
        assertEquals("chef", state.player(0)?.shownCharacterId)
        assertEquals("imp", state.player(1)?.characterId)
        assertEquals(null, state.player(1)?.shownCharacterId)
    }

    @Test
    fun `Drunk shown identity drives wake row while truth drives impairment`() {
        var state = newGame(5)
        state = GameActions.assignCharacter(state, 0, "drunk")
        state = GameActions.assignCharacter(state, 1, "imp")
        state = GameActions.assignCharacter(state, 2, "poisoner")
        state = GameActions.setShownCharacter(state, 0, "empath")

        val player = assertNotNull(state.player(0))
        assertEquals("drunk", player.characterId)
        assertEquals("empath", player.characterShownToPlayerId)
        assertEquals("empath", player.nightRoleId)

        val empathStep = assertNotNull(
            data.nightOrder.firstNight(state, data::character).find { it.id == "empath" },
        )
        assertEquals(listOf(0L), empathStep.playerIds)
        val result = assertNotNull(InfoCalc.compute(data, state, "empath", 0))
        assertTrue(result.caveats.any { "IS the Drunk" in it })

        state = GameActions.assignCharacter(state, 0, "chef")
        assertEquals(null, state.player(0)?.shownCharacterId)
    }

    @Test
    fun `Marionette wakes as shown character but stays out of Minion info`() {
        var state = newGame(8)
        val assignments = listOf(
            "imp",
            "poisoner",
            "marionette",
            "washerwoman",
            "empath",
            "fortuneteller",
            "butler",
            "mayor",
        )
        assignments.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        state = GameActions.setShownCharacter(state, 2, "chef")
        state = GameActions.advancePhase(state)

        val first = data.nightOrder.firstNight(state, data::character)
        val minionInfo = assertNotNull(first.find { it.id == NightMarkers.MINION_INFO })
        assertEquals(listOf(1L), minionInfo.playerIds)
        assertFalse("P3" in minionInfo.detail)

        val demonInfo = assertNotNull(first.find { it.id == NightMarkers.DEMON_INFO })
        assertTrue("Marionette (P3)" in demonInfo.detail)

        val chefStep = assertNotNull(first.find { it.id == "chef" })
        assertEquals(listOf(2L), chefStep.playerIds)
        val result = assertNotNull(InfoCalc.compute(data, state, "chef", 2))
        assertTrue(result.caveats.any { "IS the Marionette" in it })
    }

    @Test
    fun `teensyville Demon still receives Marionette info`() {
        var state = newGame(5)
        listOf("imp", "chef", "marionette", "empath", "mayor").forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        state = GameActions.setShownCharacter(state, 2, "washerwoman")
        state = GameActions.advancePhase(state)

        val first = data.nightOrder.firstNight(state, data::character)
        assertFalse(first.any { it.id == NightMarkers.DEMON_INFO })
        val marionetteInfo = assertNotNull(first.find { it.id == "marionette" })
        assertEquals(listOf(0L), marionetteInfo.playerIds)
        assertTrue("P3" in marionetteInfo.detail)
    }

    @Test
    fun `suggest bluffs picks three not in play good characters`() {
        var state = newGame(8)
        listOf("imp", "poisoner", "washerwoman", "empath", "chef", "recluse", "butler", "mayor")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        val available = data.resolve(tb)
        val bluffs = GameActions.suggestBluffs(available, state, Random(1))
        assertEquals(3, bluffs.size)
        val inPlay = state.players.mapNotNull { it.characterId }.toSet()
        for (id in bluffs) {
            assertTrue(id !in inPlay, "$id is in play")
            val team = data.character(id)?.team
            assertTrue(team == Team.TOWNSFOLK || team == Team.OUTSIDER)
        }
    }

    @Test
    fun `game state survives serialization round trip`() {
        var state = newGame(7)
        state = GameActions.assignCharacter(state, 0, "imp")
        state = GameActions.setShownCharacter(state, 0, "po")
        state = GameActions.addReminder(state, 0, PlacedReminder("imp", "Dead"))
        state = GameActions.advancePhase(state)
        state = GameActions.kill(state, 1, DeathCause.DEMON)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(GameState.serializer(), state)
        val back = json.decodeFromString(GameState.serializer(), text)
        assertEquals(state, back)
    }
}
