package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Identity.actingRoles` reproduces the whole §2.10b table, `changeCharacter`
 * is the single funnel, and `starPass` leaves exactly one live Demon seat.
 */
class IdentityTest {

    private val data = GameData.loadDefault()
    private val script = data.builtInScripts().first { it.id == "tb" }
    private val lookup: (String) -> Character? = data::character

    private fun game(vararg characterIds: String?): GameState {
        var state = Seats.newGame(script, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { index, id ->
            if (id != null) state = Seats.assignCharacter(state, index.toLong(), id)
        }
        return state
    }

    private fun rolesOf(state: GameState, playerId: Long): List<ActingRole> =
        Identity.actingRoles(state, lookup, assertNotNull(state.player(playerId)))

    // ---- the acting-roles table ----

    @Test
    fun `a plain seat acts as itself`() {
        val roles = rolesOf(game("empath", "imp"), 0)
        assertEquals(1, roles.size)
        assertEquals("empath", roles.single().abilityId)
        assertEquals("empath", roles.single().slotId)
        assertNull(roles.single().sourceId)
        assertTrue(!roles.single().alwaysFalse)
    }

    @Test
    fun `the Drunk acts as the character they believe, and it never works`() {
        var state = game("drunk", "imp", "empath")
        state = Seats.setShownCharacter(state, 0, "chef")
        val roles = rolesOf(state, 0)
        assertEquals(1, roles.size, "the Drunk's own row is REPLACED")
        assertEquals("chef", roles.single().abilityId)
        assertEquals("chef", roles.single().slotId)
        assertEquals("drunk", roles.single().sourceId)
        assertTrue(roles.single().alwaysFalse)
        // The truth never moves.
        assertEquals("drunk", Identity.registersAs(assertNotNull(state.player(0))))
        assertEquals("chef", Identity.believedCharacterId(assertNotNull(state.player(0))))
    }

    @Test
    fun `two Empath rows exist when a Drunk believes they are the Empath`() {
        var state = game("drunk", "empath", "imp")
        state = Seats.setShownCharacter(state, 0, "empath")
        val empathRoles = Identity.allActingRoles(state, lookup).filter { it.abilityId == "empath" }
        assertEquals(2, empathRoles.size)
        assertEquals(listOf(0L, 1L), empathRoles.map { it.playerId })
        assertEquals(listOf(true, false), empathRoles.map { it.alwaysFalse })
    }

    @Test
    fun `the Lunatic wakes at the lunatic slot as the Demon they believe`() {
        var state = game("lunatic", "imp", "chef")
        state = Identity.applyLunaticTokenSwap(state, lookup)
        val roles = rolesOf(state, 0)
        assertEquals(1, roles.size)
        assertEquals("imp", roles.single().abilityId, "run the Imp's rules")
        assertEquals("lunatic", roles.single().slotId, "…at the Lunatic's own slot")
        assertEquals("lunatic", roles.single().sourceId)
        assertTrue(roles.single().alwaysFalse)

        // The token swap is cosmetic; the real Demon's truth is untouched.
        assertEquals("lunatic", state.player(1)?.characterShownToPlayerId)
        assertEquals("imp", state.player(1)?.characterId)
        val revealed = Identity.revealTrueIdentity(state, 1)
        assertEquals("imp", revealed.player(1)?.characterShownToPlayerId)
        assertEquals("imp", revealed.player(1)?.characterId)
    }

    @Test
    fun `the Marionette acts as the good character they believe`() {
        var state = game("marionette", "imp", "chef")
        state = Seats.setShownCharacter(state, 0, "monk")
        val roles = rolesOf(state, 0)
        assertEquals("monk", roles.single().abilityId)
        assertEquals("marionette", roles.single().sourceId)
        assertTrue(roles.single().alwaysFalse)
    }

    @Test
    fun `a Philosopher's grant replaces their own row and not their identity`() {
        var state = game("philosopher", "imp", "chef")
        state = state.updatePlayer(0) {
            it.copy(
                grants = listOf(
                    AbilityGrant("empath", "philosopher", GrantMode.REPLACE),
                ),
            )
        }
        val roles = rolesOf(state, 0)
        assertEquals(listOf("empath"), roles.map { it.abilityId })
        assertEquals("philosopher", roles.single().sourceId)
        assertEquals("philosopher", Identity.registersAs(assertNotNull(state.player(0))))
        assertEquals(false, state.player(0)?.isEvil(lookup))
    }

    @Test
    fun `an Alchemist keeps their good alignment while acting as a Minion`() {
        var state = game("alchemist", "imp", "chef")
        state = state.updatePlayer(0) {
            it.copy(grants = listOf(AbilityGrant("poisoner", "alchemist", GrantMode.REPLACE)))
        }
        assertEquals(listOf("poisoner"), rolesOf(state, 0).map { it.abilityId })
        assertEquals(false, state.player(0)?.isEvil(lookup))
    }

    @Test
    fun `the Boffin's Demon acts as both, and the grant works while impaired`() {
        var state = game("imp", "boffin", "chef", "empath")
        state = state.copy(
            floatingGrants = listOf(
                FloatingGrant("chambermaid", "boffin", GrantHolder.ALIVE_DEMON, worksWhileImpaired = true),
            ),
        )
        val roles = rolesOf(state, 0)
        assertEquals(listOf("imp", "chambermaid"), roles.map { it.abilityId })
        val granted = roles.last()
        assertEquals("boffin", granted.sourceId)
        assertTrue(granted.worksWhileImpaired)
        assertTrue(!granted.alwaysFalse)
        // Nobody else picks it up.
        assertEquals(listOf("boffin"), rolesOf(state, 1).map { it.abilityId })
    }

    @Test
    fun `the Boffin's grant follows a new Demon after a star pass`() {
        var state = game("imp", "poisoner", "boffin", "chef", "empath")
        state = state.copy(
            floatingGrants = listOf(
                FloatingGrant("chambermaid", "boffin", GrantHolder.ALIVE_DEMON),
            ),
        )
        state = Identity.starPass(state, lookup, demonPlayerId = 0, heirPlayerId = 1)
        assertTrue(rolesOf(state, 0).none { it.sourceId == "boffin" }, "the corpse loses it")
        assertTrue(rolesOf(state, 1).any { it.abilityId == "chambermaid" && it.sourceId == "boffin" })
    }

    @Test
    fun `a Hermit acts as every Outsider on the script`() {
        val state = game("hermit", "imp", "chef")
        val roles = rolesOf(state, 0)
        val scriptOutsiders = data.resolve(script)
            .filter { it.team == Team.OUTSIDER && it.id != "hermit" }
            .map { it.id }
        assertEquals(listOf("hermit") + scriptOutsiders, roles.map { it.abilityId })
        assertTrue(roles.drop(1).all { it.sourceId == "hermit" })
    }

    @Test
    fun `a Cannibal acts at the executee's slot`() {
        var state = game("cannibal", "imp", "empath", "chef")
        // The Empath was executed and the Cannibal ate them.
        state = Deaths.kill(state, 2, DeathCause.EXECUTION, lookup)
        state = Effects.addReminder(state, 2, PlacedReminder("cannibal", "Lunch"))
        val roles = rolesOf(state, 0)
        assertEquals(1, roles.size, "the Cannibal's own row is replaced")
        assertEquals("empath", roles.single().abilityId)
        assertEquals("empath", roles.single().slotId)
        assertEquals("cannibal", roles.single().sourceId)
    }

    // ---- star pass ----

    @Test
    fun `star pass leaves exactly one live seat holding the demon character`() {
        val state = game("imp", "poisoner", "chef", "empath", "mayor")
        val after = Identity.starPass(state, lookup, demonPlayerId = 0, heirPlayerId = 1)

        assertEquals(false, after.player(0)?.alive)
        assertEquals("poisoner", after.player(0)?.characterId, "the corpse takes the heir's token")
        assertEquals("imp", after.player(1)?.characterId)
        assertEquals(true, after.player(1)?.isEvil(lookup))
        assertEquals("imp", after.deaths.last().characterIdAtDeath)
        assertEquals(
            1,
            after.alivePlayers.count { it.characterId == "imp" },
            "exactly one live Imp",
        )
        assertEquals(emptyList(), Identity.duplicateLiveCharacterIds(after, lookup))
        assertTrue(
            after.player(0)!!.reminders.any { Tokens.key(it) == Tokens.key("imp", "Dead") },
            "the corpse carries the Imp's Dead token",
        )
    }

    @Test
    fun `a Marionette heir loses their Marionette token and belief note`() {
        var state = game("imp", "marionette", "chef", "empath", "mayor")
        state = Seats.setShownCharacter(state, 1, "monk")
        state = Effects.addReminder(state, 1, PlacedReminder("marionette", "Is The Marionette"))
        state = Seats.setNote(state, 1, "Believes they are the Monk")

        val after = Identity.starPass(state, lookup, demonPlayerId = 0, heirPlayerId = 1)
        val heir = assertNotNull(after.player(1))
        assertEquals("imp", heir.characterId)
        assertNull(heir.shownCharacterId)
        assertTrue(heir.reminders.none { it.sourceId == "marionette" })
        assertTrue(heir.notes.none { it.text.startsWith("Believes they are") })
    }

    @Test
    fun `star pass refuses a dead or missing heir`() {
        var state = game("imp", "poisoner", "chef")
        state = Deaths.kill(state, 1, DeathCause.EXECUTION, lookup)
        assertEquals(state, Identity.starPass(state, lookup, 0, 1))
        assertEquals(state, Identity.starPass(state, lookup, 0, 99))
        assertEquals(state, Identity.starPass(state, lookup, 0, 0))
    }

    @Test
    fun `duplicateLiveCharacterIds finds two live demons`() {
        val state = game("imp", "imp", "chef")
        assertEquals(listOf("imp"), Identity.duplicateLiveCharacterIds(state, lookup))
        // Two Chefs are legal enough — only Demon-class ids are policed.
        assertEquals(
            emptyList(),
            Identity.duplicateLiveCharacterIds(game("chef", "chef", "imp"), lookup),
        )
    }

    // ---- changeCharacter ----

    @Test
    fun `changeCharacter preserves alignment unless told otherwise`() {
        val state = game("chef", "imp", "empath")
        val after = Identity.changeCharacter(state, lookup, 0, "eviltwin", ChangeReason.PIT_HAG)
        assertEquals("eviltwin", after.player(0)?.characterId)
        assertEquals(false, after.player(0)?.isEvil(lookup), "the Pit-Hag rule")
        assertEquals(Alignment.GOOD, after.player(0)?.alignment)

        val evil = Identity.changeCharacter(
            state, lookup, 0, "eviltwin", ChangeReason.PIT_HAG, newEvil = true,
        )
        assertEquals(true, evil.player(0)?.isEvil(lookup))
        assertNull(evil.player(0)?.alignment, "natural alignment needs no override")
    }

    @Test
    fun `changeCharacter removes only the abandoned character's tokens`() {
        var state = game("drunk", "imp", "poisoner", "chef")
        state = Seats.setShownCharacter(state, 0, "chef")
        state = Effects.addReminder(state, 0, PlacedReminder("drunk", "Is The Drunk"))
        state = Effects.addReminder(state, 0, PlacedReminder("poisoner", "Poisoned"))
        state = Seats.setNote(state, 0, "Believes they are the Chef")

        val after = Identity.changeCharacter(state, lookup, 0, "undertaker", ChangeReason.PIT_HAG)
        val seat = assertNotNull(after.player(0))
        assertNull(seat.shownCharacterId)
        assertTrue(seat.reminders.none { it.sourceId == "drunk" }, "own tokens go")
        assertTrue(seat.reminders.any { it.sourceId == "poisoner" }, "foreign tokens stay")
        assertTrue(seat.notes.isEmpty())
    }

    @Test
    fun `changeCharacter queues a reveal and a first-night re-run`() {
        val state = game("chef", "imp", "empath")
        val after = Identity.changeCharacter(state, lookup, 0, "grandmother", ChangeReason.PIT_HAG)

        val record = after.identityLog.last()
        assertEquals("chef", record.fromCharacterId)
        assertEquals("grandmother", record.toCharacterId)
        assertTrue(record.pendingReveal)
        assertTrue(record.pendingFirstNightRerun, "the Grandmother starts knowing")
        assertEquals(listOf(0L), Identity.pendingReveals(after).map { it.playerId })
        assertEquals(listOf(0L), Identity.pendingFirstNightReruns(after).map { it.playerId })
        assertTrue(after.prompts.any { it.kind == PromptKind.RUN_FIRST_NIGHT })
        assertTrue(after.prompts.any { it.kind == PromptKind.INFO })

        assertEquals(emptyList(), Identity.pendingReveals(Identity.markRevealed(after, 0)))
        assertEquals(
            emptyList(),
            Identity.pendingFirstNightReruns(Identity.markRerunDone(after, 0)),
        )
    }

    @Test
    fun `changeCharacter says square brackets do not apply mid-game`() {
        val state = game("chef", "imp", "empath", "monk", "mayor")
        val after = Identity.changeCharacter(state, lookup, 0, "baron", ChangeReason.PIT_HAG)
        assertTrue(
            after.identityLog.last().notes.any { "square brackets" in it },
            after.identityLog.last().notes.toString(),
        )
    }

    @Test
    fun `changeCharacter clears the old character's once-per-game mark`() {
        var state = game("philosopher", "imp", "chef")
        state = Effects.addReminder(state, 0, PlacedReminder("philosopher", "Is The Philosopher"))
        state = state.updatePlayer(0) {
            it.copy(grants = listOf(AbilityGrant("empath", "philosopher", GrantMode.REPLACE, spent = true)))
        }
        val after = Identity.changeCharacter(state, lookup, 0, "undertaker", ChangeReason.PIT_HAG)
        assertTrue(after.player(0)!!.reminders.isEmpty())
        assertTrue(after.player(0)!!.grants.isEmpty(), "the Philosopher's grant goes with it")
    }

    @Test
    fun `changeCharacter on an unknown seat is a no-op`() {
        val state = game("chef", "imp")
        assertEquals(state, Identity.changeCharacter(state, lookup, 99, "empath", ChangeReason.STORYTELLER))
    }

    // ---- dealing and seat editing ----

    @Test
    fun `dealing places the declared identity tokens and queues every reveal`() {
        val state = Seats.newGame(script, (1..5).map { "P$it" })
        val dealt = Seats.deal(
            state,
            listOf("imp", "poisoner", "drunk", "chef", "empath"),
            kotlin.random.Random(4),
            lookup,
        )
        val drunkSeat = assertNotNull(dealt.players.first { it.characterId == "drunk" })
        assertTrue(
            drunkSeat.reminders.any { Tokens.key(it) == Tokens.key("drunk", "Is The Drunk") },
            "official Title Case, straight out of characters.json",
        )
        assertEquals(5, Identity.pendingReveals(dealt).size)
        assertEquals(
            setOf(ChangeReason.DEAL),
            dealt.identityLog.map { it.reason }.toSet(),
        )
    }

    @Test
    fun `re-assigning a seat drops the old character's tokens and belief note`() {
        var state = game("drunk", "imp", "chef")
        state = Seats.setShownCharacter(state, 0, "monk")
        state = Effects.addReminder(state, 0, PlacedReminder("drunk", "Is The Drunk"))
        state = Effects.addReminder(state, 0, PlacedReminder("poisoner", "Poisoned"))
        state = Seats.setNote(state, 0, "Believes they are the Monk")

        state = Seats.assignCharacter(state, 0, "undertaker")
        val seat = assertNotNull(state.player(0))
        assertNull(seat.shownCharacterId)
        assertTrue(seat.reminders.none { it.sourceId == "drunk" })
        assertTrue(seat.reminders.any { it.sourceId == "poisoner" }, "foreign tokens stay")
        assertTrue(seat.notes.isEmpty())
    }

    @Test
    fun `swapCharacters moves characters but never the believed token`() {
        var state = game("imp", "mayor", "chef")
        state = Seats.setShownCharacter(state, 1, "chef")
        val after = Identity.swapCharacters(state, lookup, 0, 1)
        assertEquals("mayor", after.player(0)?.characterId)
        assertEquals("imp", after.player(1)?.characterId)
        assertNull(after.player(0)?.shownCharacterId)
        assertNull(after.player(1)?.shownCharacterId)
    }
}
