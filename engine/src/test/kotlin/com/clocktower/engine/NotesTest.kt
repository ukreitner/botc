package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesTest {

    private val data = GameData.loadDefault()
    private val tb = data.builtInScripts().first { it.id == "tb" }

    private fun fresh() = NotesActions.newNotes(tb, listOf("Ann", "Ben", "Cat", "Dan", "Eve"))

    @Test
    fun `new notes creates named seats with unique ids`() {
        val state = fresh()
        assertEquals(5, state.seats.size)
        assertEquals(listOf("Ann", "Ben", "Cat", "Dan", "Eve"), state.seats.map { it.name })
        assertEquals(state.seats.map { it.id }.distinct().size, state.seats.size)
    }

    @Test
    fun `claims append as history and skip repeats`() {
        var state = fresh()
        val ann = state.seats[0].id
        state = NotesActions.setClaim(state, ann, "empath")
        state = NotesActions.setClaim(state, ann, "empath")
        state = NotesActions.setDay(state, 2)
        state = NotesActions.setClaim(state, ann, "drunk")
        val seat = state.seat(ann)!!
        assertEquals(listOf("empath", "drunk"), seat.claims.map { it.characterId })
        assertEquals(listOf(1, 2), seat.claims.map { it.day })
        assertEquals("drunk", seat.currentClaimId)
    }

    @Test
    fun `undirected links normalize and dedupe`() {
        var state = fresh()
        val (a, b) = state.seats[0].id to state.seats[1].id
        state = NotesActions.addLink(state, b, a, LinkKind.SAME_TEAM)
        state = NotesActions.addLink(state, a, b, LinkKind.SAME_TEAM)
        assertEquals(1, state.links.size)
        assertEquals(a, state.links[0].fromSeatId)
        // Directed links in both directions are distinct.
        state = NotesActions.addLink(state, a, b, LinkKind.ACCUSES)
        state = NotesActions.addLink(state, b, a, LinkKind.ACCUSES)
        assertEquals(3, state.links.size)
    }

    @Test
    fun `self links and unknown seats are rejected`() {
        var state = fresh()
        val a = state.seats[0].id
        state = NotesActions.addLink(state, a, a, LinkKind.ACCUSES)
        state = NotesActions.addLink(state, a, 999L, LinkKind.ACCUSES)
        assertTrue(state.links.isEmpty())
    }

    @Test
    fun `removing a seat drops its links and my-seat marker`() {
        var state = fresh()
        val (a, b, c) = Triple(state.seats[0].id, state.seats[1].id, state.seats[2].id)
        state = NotesActions.addLink(state, a, b, LinkKind.ACCUSES)
        state = NotesActions.addLink(state, b, c, LinkKind.DEFENDS)
        state = NotesActions.setMySeat(state, b)
        state = NotesActions.removeSeat(state, b)
        assertTrue(state.links.isEmpty())
        assertNull(state.mySeatId)
        assertEquals(4, state.seats.size)
    }

    @Test
    fun `death records the day and mode and ghost vote resets`() {
        var state = fresh()
        val a = state.seats[0].id
        state = NotesActions.setDay(state, 3)
        state = NotesActions.markDead(state, a, executed = true)
        val dead = state.seat(a)!!
        assertTrue(!dead.alive)
        assertEquals(3, dead.deathDay)
        assertEquals(true, dead.executed)
        state = NotesActions.revive(state, a)
        assertTrue(state.seat(a)!!.alive)
        assertNull(state.seat(a)!!.deathDay)
    }

    @Test
    fun `claim matrix groups double claims`() {
        var state = fresh()
        state = NotesActions.setClaim(state, state.seats[0].id, "soldier")
        state = NotesActions.setClaim(state, state.seats[1].id, "soldier")
        state = NotesActions.setClaim(state, state.seats[2].id, "chef")
        val matrix = NotesActions.claimants(state)
        assertEquals(2, matrix["soldier"]?.size)
        assertEquals(1, matrix["chef"]?.size)
    }

    @Test
    fun `timeline is day ordered and readable`() {
        var state = fresh()
        val (a, b) = state.seats[0].id to state.seats[1].id
        state = NotesActions.setClaim(state, a, "empath")
        state = NotesActions.setDay(state, 2)
        state = NotesActions.addLink(state, a, b, LinkKind.ACCUSES, "thinks Ben is the Imp")
        state = NotesActions.markDead(state, b, executed = false)
        state = NotesActions.addInfo(state, "Empath said 1")
        val timeline = NotesActions.timeline(state, data::character)
        assertEquals(listOf(1, 2, 2, 2), timeline.map { it.first })
        assertTrue(timeline.any { it.second.contains("Ann claimed Empath") })
        assertTrue(timeline.any { it.second.contains("Ann accused Ben") })
        assertTrue(timeline.any { it.second.contains("Ben died in the night") })
    }

    @Test
    fun `moving seats wraps around the circle`() {
        var state = fresh()
        val first = state.seats[0].id
        state = NotesActions.moveSeat(state, first, -1)
        assertEquals(first, state.seats.last().id)
    }

    @Test
    fun `general notes persist`() {
        var state = fresh()
        state = NotesActions.setGeneralNotes(state, "demon is probably Ben or Cat")
        assertEquals("demon is probably Ben or Cat", state.generalNotes)
    }

    @Test
    fun `notes state round trips through json`() {
        var state = fresh()
        state = NotesActions.setGeneralNotes(state, "scratch")
        state = NotesActions.setClaim(state, state.seats[0].id, "empath")
        state = NotesActions.addLink(state, state.seats[0].id, state.seats[1].id, LinkKind.SAME_TEAM)
        state = NotesActions.setMyCharacter(state, "soldier")
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(NotesState.serializer(), state)
        val back = json.decodeFromString(NotesState.serializer(), text)
        assertEquals(state, back)
    }
}
