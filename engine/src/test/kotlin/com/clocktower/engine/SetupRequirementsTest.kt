package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The declarative setup checklist. It reproduces every clause the old
 * `validateSetupState` `when` block did, adds the rest of the 26-row table,
 * and answers AT ANY TIME — a Pit-Hag-created Fortune Teller needs a red
 * herring on night 3 just as much as on setup.
 */
class SetupRequirementsTest {

    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    private fun game(vararg characterIds: String): GameState {
        var state = Seats.newGame(script, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { index, id ->
            state = Seats.assignCharacter(state, index.toLong(), id)
        }
        return state
    }

    private fun ids(state: GameState): List<String> =
        SetupRequirements.unmet(state, lookup).map { it.id }

    private fun problems(state: GameState): List<String> =
        SetupRequirements.blockingProblems(state, lookup)

    // ---- the clauses the old validateSetupState had ----

    @Test
    fun `the Drunk must believe a not-in-play Townsfolk`() {
        var state = game("imp", "baron", "drunk", "recluse", "chef")
        assertTrue("drunk.token:2" in ids(state))
        assertTrue(problems(state).any { "show the Drunk" in it })

        // An IN-play Townsfolk is not a legal token.
        state = Seats.setShownCharacter(state, 2, "chef")
        assertTrue("drunk.token:2" in ids(state))

        state = Seats.setShownCharacter(state, 2, "washerwoman")
        assertEquals(emptyList(), problems(state), problems(state).toString())
    }

    @Test
    fun `the Fortune Teller needs exactly one good red herring`() {
        var state = game("imp", "poisoner", "fortuneteller", "chef", "mayor")
        assertTrue(problems(state).any { "red herring" in it })

        // The label is official Title Case now; matching stays case-insensitive.
        state = Effects.addReminder(state, 4, PlacedReminder("fortuneteller", "Red herring"))
        assertEquals(emptyList(), problems(state), problems(state).toString())

        val official = Effects.placeExclusiveReminder(
            state, 4, PlacedReminder("fortuneteller", "Red Herring"),
        )
        assertEquals(emptyList(), problems(official))

        // …and it must be a GOOD player.
        val onTheDemon = Effects.placeExclusiveReminder(
            state, 0, PlacedReminder("fortuneteller", "Red Herring"),
        )
        assertTrue("fortuneteller.herring:2" in ids(onTheDemon))
    }

    @Test
    fun `the Marionette must neighbour the Demon`() {
        var state = game("imp", "chef", "marionette", "empath", "mayor")
        state = Seats.setShownCharacter(state, 2, "washerwoman")
        assertTrue(problems(state).any { "neighbor the Demon" in it })

        state = Seats.moveSeat(state, 2, -1)
        assertEquals(emptyList(), problems(state), problems(state).toString())
    }

    @Test
    fun `the Marionette neighbours the Summoner or a Minion instead, per the jinxes`() {
        // Summoner jinx: no Demon exists yet, so the Summoner is the neighbour.
        var summoner = game("summoner", "marionette", "chef", "empath", "mayor", "monk", "virgin")
        summoner = Seats.setShownCharacter(summoner, 1, "washerwoman")
        assertTrue(
            SetupRequirements.marionetteNeighbourOk(summoner, lookup, summoner.players[1]),
        )

        // Lil' Monsta jinx: any Minion neighbour will do.
        var lilMonsta = game("poisoner", "marionette", "chef", "empath", "mayor", "monk", "virgin")
        lilMonsta = Seats.setShownCharacter(lilMonsta, 1, "washerwoman")
        lilMonsta = Decisions.set(lilMonsta, SetupRequirements.LILMONSTA_NO_DEMON_SEAT, "true")
        assertTrue(
            SetupRequirements.marionetteNeighbourOk(lilMonsta, lookup, lilMonsta.players[1]),
        )
    }

    @Test
    fun `the Lunatic must be shown a Demon token`() {
        var state = game("imp", "poisoner", "lunatic", "chef", "mayor")
        assertTrue("lunatic.token:2" in ids(state))
        state = Seats.setShownCharacter(state, 2, "chef")
        assertTrue("lunatic.token:2" in ids(state), "a Townsfolk token is not a Demon token")
        state = Seats.setShownCharacter(state, 2, "imp")
        assertTrue("lunatic.token:2" !in ids(state))
    }

    @Test
    fun `the bag is one blocking row per problem, and the Sentinel relaxes it`() {
        var state = game(
            "washerwoman", "librarian", "investigator", "chef", "empath", "monk",
            "poisoner", "imp",
        )
        assertTrue(problems(state).any { "Outsider" in it }, problems(state).toString())
        assertTrue(SetupRequirements.unmet(state, lookup).any { it.id.startsWith("bag.") })

        state = Bluffs.setFabled(state, listOf("sentinel"))
        assertTrue(problems(state).none { "Outsider" in it || "Townsfolk" in it }, problems(state).toString())
    }

    // ---- rows the old validator never had ----

    @Test
    fun `every Traveller must be given an alignment`() {
        var state = game("imp", "poisoner", "chef", "empath", "mayor")
        state = Seats.addSeat(state, "Tam")
        val travellerId = state.players.last().id
        state = Seats.assignCharacter(state, travellerId, "beggar", isTraveller = true)

        assertTrue("traveller.alignment:$travellerId" in ids(state))

        // Playtest A-7 retires "any alignment counts as an answer": the
        // traveller-join dialog pre-selects Good and writes it, so a seat can
        // carry an alignment nobody chose — and the hand-out was telling that
        // player "YOU ARE GOOD" on the strength of it. Only an ANSWER counts.
        state = Seats.setAlignment(state, travellerId, Alignment.EVIL)
        assertTrue("traveller.alignment:$travellerId" in ids(state))

        val row = SetupRequirements.all(state, lookup)
            .first { it.id == "traveller.alignment:$travellerId" }
        state = row.apply(state, Selection(text = "evil"))
        assertTrue("traveller.alignment:$travellerId" !in ids(state))
        assertEquals(Alignment.EVIL, state.player(travellerId)?.alignment)
    }

    @Test
    fun `the Bounty Hunter turns exactly one Townsfolk evil and marks one evil player`() {
        var state = game("imp", "poisoner", "bountyhunter", "chef", "empath")
        assertTrue("bountyhunter.evil:2" in ids(state))
        assertTrue("bountyhunter.know:2" in ids(state))

        state = Seats.setAlignment(state, 3, Alignment.EVIL)
        assertTrue("bountyhunter.evil:2" !in ids(state))

        state = Effects.placeExclusiveReminder(state, 1, PlacedReminder("bountyhunter", "Know"))
        assertTrue("bountyhunter.know:2" !in ids(state))

        // The Know token has to sit on an EVIL seat.
        val misplaced = Effects.placeExclusiveReminder(
            state, 4, PlacedReminder("bountyhunter", "Know"),
        )
        assertTrue("bountyhunter.know:2" in ids(misplaced))
    }

    @Test
    fun `two Village Idiots need exactly one Drunk mark and a lone one needs none`() {
        val lone = game("imp", "poisoner", "villageidiot", "chef", "empath")
        assertTrue("villageidiot.drunk" !in ids(lone))

        var pair = game("imp", "poisoner", "villageidiot", "villageidiot", "empath")
        assertTrue("villageidiot.drunk" in ids(pair))
        pair = Effects.placeExclusiveReminder(pair, 2, PlacedReminder("villageidiot", "Drunk"))
        assertTrue("villageidiot.drunk" !in ids(pair))
    }

    @Test
    fun `the Evil Twin's twin must be a good seat`() {
        var state = game("imp", "eviltwin", "chef", "empath", "mayor")
        assertTrue("eviltwin.twin:1" in ids(state))
        state = Effects.placeExclusiveReminder(state, 2, PlacedReminder("eviltwin", "Twin"))
        assertTrue("eviltwin.twin:1" !in ids(state))

        val onTheDemon = Effects.placeExclusiveReminder(state, 0, PlacedReminder("eviltwin", "Twin"))
        assertTrue("eviltwin.twin:1" in ids(onTheDemon))
    }

    @Test
    fun `stored choices block until they are made`() {
        var state = game("imp", "boffin", "chef", "empath", "mayor")
        assertTrue("boffin.grant:1" in ids(state))
        state = Decisions.set(state, Decisions.BOFFIN_GRANT, "chambermaid")
        assertTrue("boffin.grant:1" !in ids(state))

        var mez = game("imp", "mezepheles", "chef", "empath", "mayor")
        assertTrue("mezepheles.word:1" in ids(mez))
        mez = Decisions.set(mez, Decisions.MEZEPHELES_WORD, "clocktower")
        assertTrue("mezepheles.word:1" !in ids(mez))

        var xaan = game("imp", "xaan", "chef", "empath", "mayor")
        assertTrue("xaan.X:1" in ids(xaan))
        assertTrue(problems(xaan).any { "choose X" in it })
        xaan = Decisions.set(xaan, Decisions.XAAN_X, "1")
        assertTrue("xaan.X:1" !in ids(xaan))
    }

    @Test
    fun `bluff requirements appear as checklist rows`() {
        var state = Seats.newGame(script, (1..10).map { "P$it" })
        val roles = listOf(
            "imp", "poisoner", "snitch", "washerwoman", "librarian",
            "investigator", "chef", "empath", "fortuneteller", "monk",
        )
        roles.forEachIndexed { index, id ->
            state = Seats.assignCharacter(state, index.toLong(), id)
        }
        val rows = SetupRequirements.all(state, lookup).filter { it.kind == RequirementKind.BLUFFS }
        assertEquals(listOf("demon.bluffs", "snitch.bluffs:1"), rows.map { it.id })

        // Answering a row through its own `apply` satisfies it.
        val demonRow = rows.first()
        val answered = demonRow.apply(state, Selection(characterIds = listOf("saint", "slayer", "virgin")))
        assertTrue(demonRow.satisfied(answered, lookup))
        assertEquals(listOf("saint", "slayer", "virgin"), answered.demonBluffIds)
    }

    @Test
    fun `Lil' Monsta and Kazali raise an explicit acknowledgement`() {
        // A Kazali bag legally holds no Minion tokens.
        var kazali = game("kazali", "chef", "empath", "mayor", "monk")
        assertTrue(SetupRequirements.KAZALI_NO_MINIONS !in ids(kazali), "no Minions: already true")
        kazali = Seats.assignCharacter(kazali, 1, "poisoner")
        assertTrue(SetupRequirements.KAZALI_NO_MINIONS in ids(kazali))

        // Lil' Monsta is on the script with no Demon seat: ask, never assume.
        var lilMonsta = Seats.newGame(
            script.copy(characterIds = script.characterIds + "lilmonsta"),
            (1..10).map { "P$it" },
        )
        listOf(
            "poisoner", "spy", "scarletwoman", "washerwoman", "librarian",
            "investigator", "chef", "empath", "soldier", "undertaker",
        ).forEachIndexed { index, id ->
            lilMonsta = Seats.assignCharacter(lilMonsta, index.toLong(), id)
        }
        assertTrue(SetupRequirements.LILMONSTA_NO_DEMON_SEAT in ids(lilMonsta))
        // Once acknowledged, the 7 / 0 / 3 / 0 bag is legal.
        lilMonsta = Decisions.set(lilMonsta, SetupRequirements.LILMONSTA_NO_DEMON_SEAT, "true")
        assertEquals(emptyList(), problems(lilMonsta), problems(lilMonsta).toString())
    }

    @Test
    fun `a granted setup bracket folds into the bag even with no token for it`() {
        // 9 players, base 5 / 2 / 1 / 1. The Alchemist has the Baron's ability,
        // so the legal bag is 3 / 4 / 1 / 1 — "made during setup, as normal".
        var state = Seats.newGame(script, (1..9).map { "P$it" })
        listOf(
            "alchemist", "washerwoman", "librarian", "butler", "drunk",
            "recluse", "saint", "poisoner", "imp",
        ).forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }
        state = Seats.setShownCharacter(state, 4, "chef")
        assertTrue(problems(state).any { "Outsider" in it }, "5/2/1/1 is expected without the grant")

        state = Decisions.set(state, Decisions.ALCHEMIST_GRANT, "baron")
        assertTrue(problems(state).none { "Outsider" in it }, problems(state).toString())
    }

    @Test
    fun `a Boffin's Choirboy grant requires the King in the bag`() {
        var state = game("imp", "boffin", "chef", "empath", "mayor")
        state = Decisions.set(state, Decisions.BOFFIN_GRANT, "choirboy")
        assertTrue(
            problems(state).any { "king" in it },
            problems(state).toString(),
        )
    }

    // ---- re-checkable mid-game ----

    @Test
    fun `a Pit-Hag-created Fortune Teller needs a red herring on night three`() {
        var state = game("imp", "poisoner", "chef", "empath", "mayor")
        state = state.copy(phase = Phase.NIGHT, cycle = 3)
        assertTrue("fortuneteller.herring:2" !in ids(state))

        state = Identity.changeCharacter(state, lookup, 2, "fortuneteller", ChangeReason.PIT_HAG)
        assertTrue(
            "fortuneteller.herring:2" in ids(state),
            "the checklist is not a SETUP-only screen",
        )
        state = Effects.placeExclusiveReminder(state, 4, PlacedReminder("fortuneteller", "Red Herring"))
        assertTrue("fortuneteller.herring:2" !in ids(state))
    }

    @Test
    fun `a mid-game Snitch owes every Minion bluffs`() {
        var state = Seats.newGame(script, (1..10).map { "P$it" })
        listOf(
            "imp", "poisoner", "baron", "washerwoman", "librarian",
            "investigator", "chef", "empath", "fortuneteller", "monk",
        ).forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }
        state = state.copy(phase = Phase.NIGHT, cycle = 2)
        assertTrue(ids(state).none { it.startsWith("snitch.bluffs") })

        state = Identity.changeCharacter(state, lookup, 9, "snitch", ChangeReason.PIT_HAG)
        assertEquals(
            listOf("snitch.bluffs:1", "snitch.bluffs:2"),
            ids(state).filter { it.startsWith("snitch.bluffs") },
        )
    }

    @Test
    fun `advisory rows never block`() {
        val state = game("imp", "poisoner", "lunatic", "chef", "empath")
        val lunaticMinions = assertNotNull(
            SetupRequirements.all(state, lookup).firstOrNull { it.id == "lunatic.minions:2" },
        )
        assertEquals(false, lunaticMinions.blocking)
        assertTrue(problems(state).none { "fake Minions" in it })
    }

    // ---- per-seat row ids ----

    @Test
    fun `a row that belongs to one seat carries that seat's id`() {
        var state = Seats.newGame(script, (1..10).map { "P$it" })
        listOf(
            "imp", "poisoner", "drunk", "drunk", "lunatic",
            "lunatic", "villageidiot", "villageidiot", "chef", "empath",
        ).forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }

        val rowIds = SetupRequirements.all(state, lookup).map { it.id }
        assertEquals(
            rowIds.distinct(),
            rowIds,
            "checklist row ids must be unique — the UI keys a list by them",
        )
        // One row per SEAT, not one per character.
        assertTrue(listOf("drunk.token:2", "drunk.token:3").all { it in rowIds }, rowIds.toString())
        assertTrue(listOf("lunatic.token:4", "lunatic.token:5").all { it in rowIds }, rowIds.toString())
        assertTrue(
            listOf("lunatic.minions:4", "lunatic.minions:5").all { it in rowIds },
            rowIds.toString(),
        )
        assertTrue(
            listOf("lunatic.bluffs:4", "lunatic.bluffs:5").all { it in rowIds },
            rowIds.toString(),
        )
        // …but a whole-game row stays unsuffixed, however many seats raise it.
        assertEquals(listOf("villageidiot.drunk"), rowIds.filter { it.startsWith("villageidiot") })
        assertEquals(listOf("demon.bluffs"), rowIds.filter { it.startsWith("demon.") })
    }

    @Test
    fun `each seat's row answers for that seat alone`() {
        var state = game("imp", "poisoner", "drunk", "drunk", "empath")
        val rows = SetupRequirements.all(state, lookup).associateBy { it.id }
        val second = assertNotNull(rows["drunk.token:3"])

        state = second.apply(state, Selection(characterIds = listOf("washerwoman")))
        assertEquals("washerwoman", state.player(3)?.shownCharacterId)
        assertEquals(null, state.player(2)?.shownCharacterId, "the other Drunk was untouched")
        assertTrue("drunk.token:2" in ids(state))
        assertTrue("drunk.token:3" !in ids(state))
    }

    @Test
    fun `the Outsider branch offers the bracket's legal counts as chips`() {
        // 9 players: base 5 / 2 / 1 / 1, and the Godfather is "-1 or +1 Outsider".
        var state = Seats.newGame(script, (1..9).map { "P$it" })
        listOf(
            "godfather", "washerwoman", "librarian", "butler", "saint",
            "recluse", "chef", "empath", "imp",
        ).forEachIndexed { index, id -> state = Seats.assignCharacter(state, index.toLong(), id) }

        val row = assertNotNull(
            SetupRequirements.all(state, lookup).firstOrNull { it.id == "setup.outsiderBranch" },
        )
        assertEquals(RequirementKind.NUMBER, row.kind)
        assertEquals(listOf("1", "3"), row.candidates(state, lookup).map { it.id })
        assertEquals(
            listOf("1 Outsider", "3 Outsiders"),
            row.candidates(state, lookup).map { it.label },
        )

        // Answering a chip stores the branch and satisfies the row.
        assertTrue(row.id in ids(state))
        val answered = row.apply(state, Selection(number = 3, text = "3"))
        assertEquals(3, Decisions.int(answered, Decisions.OUTSIDER_BRANCH))
        assertTrue(row.satisfied(answered, lookup))
    }

    @Test
    fun `an open-ended bracket offers every count`() {
        val state = game("kazali", "chef", "empath", "mayor", "monk")
        val row = assertNotNull(
            SetupRequirements.all(state, lookup).firstOrNull { it.id == "setup.outsiderBranch" },
        )
        assertEquals(listOf("0", "1", "2", "3", "4", "5"), row.candidates(state, lookup).map { it.id })
    }

    // ==================================================================
    // W7G — `CharacterRule.setup` has a consumer
    // ==================================================================

    @Test
    fun `a registry setup row reaches the checklist, for a seat and for a Fabled`() {
        // A seated character's own row (the Lycanthrope's Faux Paw)…
        val lycanthrope = game("imp", "lycanthrope", "chef", "empath", "mayor")
        assertTrue("lycanthrope.fauxpaw" in ids(lycanthrope), ids(lycanthrope).toString())
        val marked = Effects.addReminder(
            lycanthrope,
            1L,
            PlacedReminder("lycanthrope", "Faux Paw"),
        )
        assertTrue("lycanthrope.fauxpaw" !in ids(marked), "marking one satisfies it")

        // …and a FABLED's, which holds no seat at all.
        val storm = GameActions.setFabled(
            game("imp", "chef", "empath", "mayor", "monk"),
            listOf("stormcatcher"),
        )
        assertTrue("stormcatcher.favouredCharacterId" in ids(storm), ids(storm).toString())
        val named = storm.copy(
            fabled = storm.fabled.map {
                it.copy(config = it.config + ("stormcatcher.favouredCharacterId" to "chef"))
            },
        )
        assertTrue("stormcatcher.favouredCharacterId" !in ids(named))

        // Nothing is added for a character that is not in play.
        assertTrue("stormcatcher.favouredCharacterId" !in ids(lycanthrope))
    }

    @Test
    fun `no two setup rows share an id`() {
        val state = GameActions.setFabled(
            game("imp", "lycanthrope", "chef", "empath", "mayor"),
            listOf("stormcatcher", "sentinel", "djinn"),
        )
        val all = SetupRequirements.all(state, lookup).map { it.id }
        assertEquals(all.size, all.toSet().size, "duplicate rows: $all")
    }

    @Test
    fun `the Lord of Typhon's evil line is checked around the middle seat`() {
        // poisoner · lordoftyphon · baron — a line with the Typhon in the middle.
        val ok = game("chef", "poisoner", "lordoftyphon", "baron", "empath", "monk", "mayor")
        assertTrue(SetupRequirements.lordOfTyphonLineOk(ok, lookup))

        // The same evil seats, but not adjacent to the Typhon.
        val broken = game("poisoner", "chef", "lordoftyphon", "baron", "empath", "monk", "mayor")
        assertTrue(!SetupRequirements.lordOfTyphonLineOk(broken, lookup))
    }
}
