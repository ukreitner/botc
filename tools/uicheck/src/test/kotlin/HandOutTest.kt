package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Bluffs
import com.clocktower.engine.Character
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.RequirementKind
import com.clocktower.engine.Script
import com.clocktower.engine.Seats
import com.clocktower.engine.SetupRequirements
import com.clocktower.engine.Alignment
import com.clocktower.engine.Player
import com.clocktower.engine.Selection
import com.clocktower.grimoire.ui.screens.HandOutPage
import com.clocktower.grimoire.ui.screens.handOutBlockers
import com.clocktower.grimoire.ui.screens.handOutCardCaption
import com.clocktower.grimoire.ui.screens.handOutPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playtest A-1: **the Drunk was handed the Drunk token.**
 *
 * The hand-out queued every seat that had a `characterId` and never asked
 * whether the seat's *believed* character had been chosen yet, so the default
 * path through the app ("Deal & hand out tokens" is the primary button) printed
 * the word "Drunk" on a card the Drunk's own player was holding — while the
 * same screen listed "The Drunk believes — … Which Townsfolk token do they
 * see?" as outstanding.
 *
 * `handOutBlockers` is the connection that was missing. It lives in `app/`
 * (`ui/screens/RevealFlow.kt`), so it is measured here rather than in
 * `:engine`, which cannot see app sources.
 */
class HandOutTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb: Script = data.builtInScripts().first { it.id == "tb" }

    /** A legal 7-seat Trouble Brewing table whose seat 5 holds the Drunk. */
    private val table = listOf(
        "Uri" to "washerwoman",
        "Dana" to "librarian",
        "Ari" to "chef",
        "Sam" to "drunk",
        "Mia" to "empath",
        "Jon" to "poisoner",
        "Lea" to "imp",
    )

    private fun seated(): GameState {
        var state = GameActions.newGame(tb, table.map { it.first })
        for ((name, characterId) in table) {
            state = Seats.assignCharacter(state, seat(state, name), characterId)
        }
        return Bluffs.setDemonBluffs(state, listOf("monk", "slayer", "soldier"))
    }

    private fun seat(state: GameState, name: String): Long =
        state.players.first { it.name == name }.id

    // ---- the gate ---------------------------------------------------------

    @Test
    fun `the Drunk's seat is unrevealable until its believes row is answered`() {
        val state = seated()
        val blocked = handOutBlockers(state, lookup)

        assertEquals(
            "exactly the Drunk's seat is blocked",
            setOf(seat(state, "Sam")),
            blocked.keys,
        )
        assertEquals(
            listOf("drunk.token:${seat(state, "Sam")}"),
            blocked.getValue(seat(state, "Sam")).map { it.id },
        )
    }

    /** Answering the row is what unblocks it — nothing else has to happen. */
    @Test
    fun `choosing the shown token releases the seat`() {
        val state = seated()
        val sam = seat(state, "Sam")
        val answered = Seats.setShownCharacter(state, sam, "chambermaid")

        assertTrue(
            "answering the row must clear the block, not just tick a box",
            handOutBlockers(answered, lookup).isEmpty(),
        )
    }

    /** A believed token that is illegal (an in-play Townsfolk) does NOT release it. */
    @Test
    fun `an illegal shown token leaves the seat blocked`() {
        val state = seated()
        val sam = seat(state, "Sam")
        // The Chef is in play, so it is not a legal thing for the Drunk to see.
        val bogus = Seats.setShownCharacter(state, sam, "chef")

        assertEquals(setOf(sam), handOutBlockers(bogus, lookup).keys)
    }

    /** Nobody else is caught: a legal table hands out with no gate at all. */
    @Test
    fun `an answered table blocks nobody`() {
        val state = Seats.setShownCharacter(seated(), seat(seated(), "Sam"), "monk")
        assertTrue(handOutBlockers(state, lookup).isEmpty())
    }

    // ---- what must NOT block ---------------------------------------------

    /**
     * Only the rows that change what the CARD says may hold a seat up. The
     * Demon's bluffs, the Fortune Teller's red herring and the Lunatic's fake
     * Minions are table actions — blocking on them would make the hand-out
     * unusable, which is not what A-1 asked for.
     */
    @Test
    fun `table-action rows never block a hand-over`() {
        var state = GameActions.newGame(tb, table.map { it.first })
        for ((name, characterId) in table) {
            val id = seat(state, name)
            state = Seats.assignCharacter(
                state,
                id,
                if (characterId == "drunk") "fortuneteller" else characterId,
            )
        }
        // No bluffs, no red herring: plenty unmet, none of it card-deciding.
        val unmet = SetupRequirements.unmet(state, lookup)
        assertTrue("the fixture must owe something", unmet.any { it.blocking })
        assertFalse(
            "the fixture must owe a red herring",
            unmet.none { it.id.startsWith("fortuneteller.herring") },
        )
        assertTrue(
            "blocked: " + handOutBlockers(state, lookup).values.flatten().map { it.id },
            handOutBlockers(state, lookup).isEmpty(),
        )
    }

    /** Every blocking row the gate uses names exactly one seat. */
    @Test
    fun `blocking rows carry the seat they decide for`() {
        val state = seated()
        for (row in SetupRequirements.all(state, lookup)) {
            if (row.kind != RequirementKind.SHOWN_TOKEN) continue
            assertTrue(
                "${row.id} decides one seat's card but names no seat",
                row.seatId != null && state.player(row.seatId!!) != null,
            )
            assertTrue(
                "${row.id} must end in the seat it names",
                row.id.endsWith(":${row.seatId}"),
            )
        }
    }

    // ---- A-6: the number under the card is the SEAT ------------------------

    /**
     * Observed with the roster Uri…Zoe: Max (seat 11) was announced as "seat 1
     * of 12" and traveller Gus (seat 13) as "seat 4 of 13" — the hand-out was
     * printing the shuffled queue position under the word "seat".
     */
    @Test
    fun `the card names the seat, not the queue position`() {
        assertEquals("seat 11 of 12", handOutCardCaption(11, 12, page = 0, pageCount = 1))
        assertEquals("seat 4 of 13", handOutCardCaption(4, 13, page = 0, pageCount = 1))
    }

    /** A two-card hand-over still says which card of the two this is. */
    @Test
    fun `a second card is numbered without touching the seat`() {
        assertEquals("seat 3 of 9 · card 1 of 2", handOutCardCaption(3, 9, 0, 2))
        assertEquals("seat 3 of 9 · card 2 of 2", handOutCardCaption(3, 9, 1, 2))
    }

    // ---- A-7: a Traveller is not told a side nobody chose -------------------

    private fun traveller(alignment: Alignment?): Player =
        Player(id = 7, name = "Gus", characterId = "beggar", isTraveller = true, alignment = alignment)

    /**
     * `joinTraveller` writes an alignment for every Traveller it seats — its
     * dialog pre-selects Good — so the seat carrying one proves nothing. Until
     * the row is answered the hand-out shows the character card and stops.
     */
    @Test
    fun `an unanswered traveller gets no alignment page`() {
        val beggar = data.character("beggar")
        val pages = handOutPages(traveller(Alignment.GOOD), beggar, alignmentAnswered = false)
        assertEquals(listOf<HandOutPage>(HandOutPage.CharacterPage), pages)
    }

    /** Answered, they get the page — and it says what was actually chosen. */
    @Test
    fun `an answered traveller gets the page they were given`() {
        val beggar = data.character("beggar")
        val good = handOutPages(traveller(Alignment.GOOD), beggar, alignmentAnswered = true)
        assertEquals(2, good.size)
        assertFalse((good[1] as HandOutPage.AlignmentPage).evil)

        val evil = handOutPages(traveller(Alignment.EVIL), beggar, alignmentAnswered = true)
        assertTrue((evil[1] as HandOutPage.AlignmentPage).evil)
    }

    /** And the unanswered Traveller's seat is one the roster refuses to deal. */
    @Test
    fun `an unanswered traveller's seat is blocked`() {
        var state = seated()
        state = Seats.addSeat(state, "Gus")
        val gus = state.players.last().id
        state = Seats.assignCharacter(state, gus, "beggar", isTraveller = true)
        state = Seats.setAlignment(state, gus, Alignment.GOOD)

        assertEquals(
            "an alignment nobody chose must not unblock the seat",
            setOf(gus),
            handOutBlockers(state, lookup).keys - seat(state, "Sam"),
        )

        val row = SetupRequirements.all(state, lookup)
            .first { it.id == "traveller.alignment:$gus" }
        state = row.apply(state, Selection(text = "evil"))
        assertTrue(gus !in handOutBlockers(state, lookup).keys)
    }

    /** The Ogre exception survives: never an alignment page, answered or not. */
    @Test
    fun `an Ogre is never told their side`() {
        val ogre = data.character("ogre") ?: return
        val seat = Player(id = 3, name = "Ivy", characterId = "ogre", alignment = Alignment.EVIL)
        assertEquals(
            listOf<HandOutPage>(HandOutPage.CharacterPage),
            handOutPages(seat, ogre, alignmentAnswered = true),
        )
    }
}
