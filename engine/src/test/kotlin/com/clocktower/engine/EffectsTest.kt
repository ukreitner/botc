package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The status-model tests: the `abilityWorks` recursion, standing rules,
 * expiry and the effect-to-token rendering (ARCHITECTURE §2.3, §2.4).
 */
class EffectsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** A seated game with the given characters, one per seat, at night 1. */
    private fun game(vararg characterIds: String): GameState {
        val names = characterIds.indices.map { "P$it" }
        var state = GameActions.newGame(tb, names)
        characterIds.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        return Phases.advancePhase(state, lookup)
    }

    private fun GameState.seat(characterId: String): Long =
        players.first { it.characterId == characterId }.id

    private fun GameState.poison(
        source: String,
        target: Long,
        label: String = "Poisoned",
        kind: EffectKind = EffectKind.POISONED,
    ): GameState = Effects.place(
        state = this,
        target = target,
        kind = kind,
        sourceCharacterId = source,
        sourcePlayerId = players.firstOrNull { it.characterId == source }?.id,
        until = Until.FOREVER,
        label = label,
    ).state

    // ---- 1-5: the recursion ------------------------------------------------

    @Test
    fun `a poisoner killed mid-night un-poisons its victim`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val poisoner = state.seat("poisoner")
        val empath = state.seat("empath")
        state = state.poison("poisoner", empath)

        assertTrue(Status.isImpaired(state, lookup, empath), "poisoned while the Poisoner lives")

        state = Deaths.attempt(state, lookup, poisoner, KillCause(DeathCause.EXECUTION)).state
        assertTrue(
            Status.impairment(state, lookup, empath).isEmpty(),
            "the Poisoner lost their ability, so the poison ended",
        )
    }

    @Test
    fun `the widow-innkeeper chain is reversible`() {
        var state = game("widow", "empath", "innkeeper", "imp", "chef")
        val widow = state.seat("widow")
        val empath = state.seat("empath")
        val innkeeper = state.seat("innkeeper")
        state = state.poison("widow", empath)
        assertTrue(Status.isImpaired(state, lookup, empath))

        // The Innkeeper makes the Widow drunk: the Empath is healthy again.
        state = Effects.place(
            state, widow, EffectKind.DRUNK, "innkeeper", innkeeper, Until.FOREVER, "Drunk",
        ).state
        assertTrue(Status.impairment(state, lookup, empath).isEmpty(), "a drunk Widow poisons nobody")

        // The Innkeeper dies: the Widow is sober again, so the Empath is poisoned again.
        state = Deaths.attempt(state, lookup, innkeeper, KillCause(DeathCause.EXECUTION)).state
        assertTrue(Status.isImpaired(state, lookup, empath), "the chain is reversible")
    }

    @Test
    fun `the second poisoner wins and the reason names them`() {
        // Julian poisons Amy; Evin poisons Julian.
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val julian = state.seat("poisoner")
        val amy = state.seat("empath")
        val evin = state.seat("imp")
        state = state.poison("poisoner", amy)
        state = Effects.place(
            state, julian, EffectKind.POISONED, "imp", evin, Until.FOREVER, "Poisoned",
        ).state

        assertTrue(Status.impairment(state, lookup, amy).isEmpty(), "Amy is no longer poisoned")
        val julianReasons = Status.impairment(state, lookup, julian)
        assertEquals(1, julianReasons.size)
        assertTrue("Imp" in julianReasons.single().text, julianReasons.single().text)
    }

    @Test
    fun `a drunk poisoner places a token that does nothing`() {
        var state = game("poisoner", "chef", "imp", "empath", "mayor")
        val poisoner = state.seat("poisoner")
        val chef = state.seat("chef")
        state = Effects.place(
            state, poisoner, EffectKind.DRUNK, "sailor", null, Until.FOREVER, "Drunk",
        ).state
        state = state.poison("poisoner", chef)

        assertTrue(Status.impairment(state, lookup, chef).isEmpty(), "the Chef's ability still works")
        assertTrue(
            state.effects.any { it.targetId == chef && it.kind == EffectKind.POISONED },
            "the token is still on the board",
        )
    }

    @Test
    fun `mutual poison with equal ids resolves as both active and is reported`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val a = state.seat("poisoner")
        val b = state.seat("empath")
        // Equal ids are only reachable from two derived effects created in one swap.
        val shared = 7L
        state = state.copy(
            effects = listOf(
                Effect(shared, EffectKind.POISONED, a, sourceCharacterId = "empath", sourcePlayerId = b, until = Until.FOREVER, createdCycle = 1, createdAtNight = true),
                Effect(shared, EffectKind.POISONED, b, sourceCharacterId = "poisoner", sourcePlayerId = a, until = Until.FOREVER, createdCycle = 1, createdAtNight = true),
            ),
            nextEffectId = shared + 1,
        )

        assertTrue(Status.isImpaired(state, lookup, a), "resolved as both active")
        assertTrue(Status.isImpaired(state, lookup, b), "resolved as both active")
        assertEquals(setOf(a, b), Status.paradoxSeats(state, lookup))

        val reconciled = Effects.reconcile(state, lookup)
        assertTrue(
            reconciled.prompts.any { it.kind == PromptKind.DECIDE && "Paradox" in it.title },
            "the storyteller is asked to settle it",
        )
    }

    // ---- 7-8: the No Dashii ------------------------------------------------

    @Test
    fun `a soldier is not poisoned by the no dashii`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        listOf("nodashii", "soldier", "imp", "chef", "empath")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = Phases.advancePhase(state, lookup)

        val soldier = state.seat("soldier")
        assertTrue(
            Status.impairment(state, lookup, soldier).isEmpty(),
            "SAFE_FROM_DEMON blocks non-kill Demon harm too",
        )
        // The other direction still lands on a Townsfolk.
        assertTrue(Status.isImpaired(state, lookup, state.seat("empath")))
    }

    @Test
    fun `a drunk no dashii poisons neither neighbour`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        listOf("nodashii", "chef", "imp", "mayor", "empath")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = Phases.advancePhase(state, lookup)
        val demon = state.seat("nodashii")
        state = Effects.place(
            state, demon, EffectKind.DRUNK, "sailor", null, Until.FOREVER, "Drunk",
        ).state

        assertTrue(StatusEffects.derivedPoison(state, lookup).isEmpty())
    }

    // ---- 9-11: anti-impairment and effects that outlive their source -------

    @Test
    fun `barista sober and healthy beats poison until dusk`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val empath = state.seat("empath")
        state = state.poison("poisoner", empath)
        state = Effects.place(
            state, empath, EffectKind.SOBER_HEALTHY, "barista", null, Until.DUSK, "Sober & Healthy",
        ).state
        assertTrue(Status.impairment(state, lookup, empath).isEmpty(), "the Barista wins outright")

        state = Phases.advancePhase(state, lookup) // day 1
        assertTrue(Status.impairment(state, lookup, empath).isEmpty(), "still sober during the day")
        state = Phases.advancePhase(state, lookup) // night 2, past dusk
        assertTrue(Status.isImpaired(state, lookup, empath), "poisoned again after dusk")
    }

    @Test
    fun `the puzzlemaster's drunk survives the puzzlemaster's death`() {
        var state = game("puzzlemaster", "empath", "imp", "chef", "mayor")
        val pm = state.seat("puzzlemaster")
        val victim = state.seat("empath")
        state = Effects.place(
            state, victim, EffectKind.DRUNK, "puzzlemaster", pm, Until.FOREVER, "Drunk",
            endsWithSource = false,
        ).state

        state = Deaths.attempt(state, lookup, pm, KillCause(DeathCause.EXECUTION)).state
        assertTrue(Status.isImpaired(state, lookup, victim), "\"even if you die\"")
    }

    @Test
    fun `the sweetheart's drunk outlives the sweetheart`() {
        var state = game("sweetheart", "empath", "imp", "chef", "mayor")
        val sweetheart = state.seat("sweetheart")
        val victim = state.seat("empath")
        state = Deaths.attempt(state, lookup, sweetheart, KillCause(DeathCause.EXECUTION)).state
        state = Effects.place(
            state, victim, EffectKind.DRUNK, "sweetheart", sweetheart, Until.FOREVER, "Drunk",
            endsWithSource = false,
        ).state
        assertTrue(Status.isImpaired(state, lookup, victim), "drunk from now on")
    }

    // ---- 12-13: multi-day timers -------------------------------------------

    @Test
    fun `the courtier drunks for three nights and three days`() {
        var state = game("courtier", "imp", "empath", "chef", "mayor")
        val courtier = state.seat("courtier")
        val imp = state.seat("imp")
        state = Effects.place(
            state, imp, EffectKind.DRUNK, "courtier", courtier, Until.DUSK, "Drunk 1",
        ).state

        // Nights 1-3 and days 1-3.
        repeat(3) { round ->
            assertTrue(Status.isImpaired(state, lookup, imp), "night ${round + 1}")
            state = Phases.advancePhase(state, lookup) // day
            assertTrue(Status.isImpaired(state, lookup, imp), "day ${round + 1}")
            state = Phases.advancePhase(state, lookup) // next night
        }
        assertFalse(Status.isImpaired(state, lookup, imp), "sober from dusk of day 3")
        assertTrue(state.effects.none { it.sourceCharacterId == "courtier" })
    }

    @Test
    fun `the minstrel drunks the town until dusk tomorrow`() {
        var state = game("minstrel", "poisoner", "imp", "chef", "mayor")
        state = Phases.advancePhase(state, lookup) // day 1
        state = Phases.advancePhase(state, lookup) // night 2
        state = Phases.advancePhase(state, lookup) // day 2
        val minstrel = state.seat("minstrel")

        // A Minion is executed on day 2: everyone else is drunk until dusk tomorrow.
        for (p in state.players.filter { it.id != minstrel }) {
            state = Effects.place(
                state, p.id, EffectKind.DRUNK, "minstrel", minstrel, Until.DUSK_AFTER_N_DAYS,
                label = "", endsWithSource = false,
            ).state.let { s ->
                s.copy(
                    effects = s.effects.map {
                        if (it.id == s.nextEffectId - 1) it.copy(untilCycle = s.cycle + 1) else it
                    },
                )
            }
        }
        val chef = state.seat("chef")
        assertTrue(Status.isImpaired(state, lookup, chef), "day 2")
        assertFalse(Status.isImpaired(state, lookup, minstrel), "the Minstrel is not drunk")

        state = Phases.advancePhase(state, lookup) // night 3
        assertTrue(Status.isImpaired(state, lookup, chef), "night 3")
        state = Phases.advancePhase(state, lookup) // day 3
        assertTrue(Status.isImpaired(state, lookup, chef), "day 3")
        state = Phases.advancePhase(state, lookup) // night 4 — past dusk of day 3
        assertFalse(Status.isImpaired(state, lookup, chef), "sober at dusk of day 3")
    }

    // ---- 15-16: Xaan, and the seat that never had an ability ---------------

    @Test
    fun `xaan poisons every townsfolk on night X only, by true team`() {
        var state = game("xaan", "chef", "empath", "drunk", "imp")
        state = Decisions.set(state, Decisions.XAAN_X, "2")
        val drunkSeat = state.seat("drunk")
        state = GameActions.setShownCharacter(state, drunkSeat, "mayor")

        assertFalse(Status.isImpaired(state, lookup, state.seat("chef")), "night 1 is not night X")

        state = Phases.advancePhase(state, lookup) // day 1
        state = Phases.advancePhase(state, lookup) // night 2 = night X
        assertTrue(Status.isImpaired(state, lookup, state.seat("chef")))
        assertTrue(Status.isImpaired(state, lookup, state.seat("empath")))
        // The Drunk is an Outsider by TRUE team, so Xaan does not poison them...
        assertTrue(
            Status.impairment(state, lookup, drunkSeat).none { it.effect.sourceCharacterId == "xaan" },
            "the Drunk is an Outsider",
        )

        state = Phases.advancePhase(state, lookup) // day 2 — still poisoned until dusk
        assertTrue(Status.isImpaired(state, lookup, state.seat("chef")))
        state = Phases.advancePhase(state, lookup) // night 3 — past dusk of day 2
        assertFalse(Status.isImpaired(state, lookup, state.seat("chef")))
    }

    @Test
    fun `a dead xaan poisons nobody`() {
        var state = game("xaan", "chef", "empath", "mayor", "imp")
        state = Decisions.set(state, Decisions.XAAN_X, "2")
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup) // night 2
        state = Deaths.attempt(state, lookup, state.seat("xaan"), KillCause(DeathCause.EXECUTION)).state
        assertFalse(Status.isImpaired(state, lookup, state.seat("chef")))
    }

    @Test
    fun `the drunk and the marionette never have an ability`() {
        var state = game("drunk", "marionette", "imp", "chef", "mayor")
        state = GameActions.setShownCharacter(state, state.seat("drunk"), "empath")
        assertTrue(Status.isImpaired(state, lookup, state.seat("drunk")))
        assertTrue(Status.isImpaired(state, lookup, state.seat("marionette")))
        assertFalse(Status.hasAbility(state, lookup, state.seat("drunk")))
    }

    // ---- the Tea Lady ------------------------------------------------------

    @Test
    fun `tea lady protects the nearest alive neighbours, not the corpse`() {
        val bmr = data.builtInScripts().first { it.id == "bmr" }
        var state = GameActions.newGame(bmr, listOf("A", "B", "C", "D", "E"))
        // Seats: 0 chef, 1 mayor(dead), 2 tealady, 3 empath, 4 imp
        listOf("chef", "mayor", "tealady", "empath", "imp")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = Phases.advancePhase(state, lookup)
        // Seat 1 starts protected by the Tea Lady, so only an Assassin can kill it.
        state = Deaths.attempt(
            state, lookup, 1L,
            KillCause(DeathCause.EVIL_ABILITY, "assassin", ignoresProtection = true),
        ).state
        assertFalse(state.players.first { it.id == 1L }.alive)

        val protectedIds = state.seats
            .filter { p -> Status.protections(state, lookup, p.id).any { it.kind == EffectKind.CANT_DIE } }
            .map { it.id }
            .toSet()
        assertEquals(setOf(0L, 3L), protectedIds, "skips the dead seat 1, protects 0 and 3")
    }

    @Test
    fun `a poisoned tea lady protects nobody`() {
        val bmr = data.builtInScripts().first { it.id == "bmr" }
        var state = GameActions.newGame(bmr, listOf("A", "B", "C", "D", "E"))
        listOf("chef", "tealady", "empath", "mayor", "imp")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = Phases.advancePhase(state, lookup)
        state = state.poison("imp", state.seat("tealady"))

        assertTrue(
            state.seats.none { p ->
                Status.protections(state, lookup, p.id).any { it.kind == EffectKind.CANT_DIE }
            },
        )
    }

    @Test
    fun `a two seat circle does not throw`() {
        val bmr = data.builtInScripts().first { it.id == "bmr" }
        var state = GameActions.newGame(bmr, listOf("A", "B"))
        listOf("tealady", "imp").forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        state = Phases.advancePhase(state, lookup)
        assertTrue(Status.protections(state, lookup, 1L).none { it.kind == EffectKind.CANT_DIE })
    }

    // ---- rendering and reconcile -------------------------------------------

    @Test
    fun `a hand placed token obeys the same recursion as an engine placed one`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val empath = state.seat("empath")
        state = Effects.addReminder(state, empath, PlacedReminder("poisoner", "Poisoned"))
        assertTrue(Status.isImpaired(state, lookup, empath), "projected through the token registry")

        state = Deaths.attempt(state, lookup, state.seat("poisoner"), KillCause(DeathCause.EXECUTION)).state
        assertTrue(
            Status.impairment(state, lookup, empath).isEmpty(),
            "a dead Poisoner's hand-placed token stops working too",
        )
    }

    @Test
    fun `no code path can place a token with an empty source`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        state = Effects.addReminder(state, 1L, PlacedReminder("", "Poisoned"))
        state = Effects.placeExclusiveReminder(state, 2L, PlacedReminder("", "Drunk"))
        state = Effects.addCentreReminder(state, PlacedReminder("", "Everyone Is Drunk"))

        val everySource = state.players.flatMap { it.reminders }.map { it.sourceId } +
            state.storytellerReminders.map { it.sourceId }
        assertTrue(everySource.isNotEmpty())
        assertTrue(everySource.none { it.isBlank() }, everySource.toString())
        assertTrue(everySource.all { it == Tokens.STORYTELLER_SOURCE })
    }

    @Test
    fun `a generic storyteller poison expires at dusk instead of lasting forever`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        state = Effects.addReminder(state, 1L, PlacedReminder(Tokens.STORYTELLER_SOURCE, "Poisoned"))
        assertTrue(Status.isImpaired(state, lookup, 1L))

        state = Phases.advancePhase(state, lookup) // day 1
        assertTrue(Status.isImpaired(state, lookup, 1L), "tonight and tomorrow day")
        state = Phases.advancePhase(state, lookup) // night 2
        assertFalse(Status.isImpaired(state, lookup, 1L), "swept at dusk — the permanent-poison bug")
        assertTrue(state.players.first { it.id == 1L }.reminders.isEmpty())
    }

    @Test
    fun `rendered tokens carry their group, expiry and derived flag`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        var state = GameActions.newGame(sv, listOf("A", "B", "C", "D", "E"))
        listOf("nodashii", "chef", "imp", "mayor", "empath")
            .forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        state = Phases.advancePhase(state, lookup)
        // The Monk goes on seat 3, which the No Dashii does not reach — a token on
        // seat 1 would block the Demon's poison and there would be nothing to draw.
        state = Effects.place(
            state, 3L, EffectKind.SAFE_FROM_DEMON, "monk", null, Until.DAWN, "Safe",
        ).state

        val safe = Effects.rendered(state, lookup, 3L).first { it.label == "Safe" }
        assertEquals(EffectGroup.PROTECTED, safe.group)
        assertEquals("expires at dawn", safe.expiryText)
        assertFalse(safe.derived)

        val poison = Effects.rendered(state, lookup, 1L).first { it.label == "Poisoned" }
        assertEquals(EffectGroup.IMPAIRED, poison.group)
        assertTrue(poison.derived, "the No Dashii's poison has no physical token")
    }

    @Test
    fun `reconcile records an impairment span and closes it`() {
        var state = game("poisoner", "empath", "imp", "chef", "mayor")
        val empath = state.seat("empath")
        state = Effects.reconcile(state.poison("poisoner", empath), lookup)

        val open = state.ledger.filter {
            it.kind == LedgerKind.IMPAIRMENT_SPAN && it.actorId == empath
        }
        assertEquals(1, open.size)
        assertEquals(null, open.single().resolvedCycle, "still open")

        state = Effects.reconcile(Effects.remove(state, state.effects.last().id), lookup)
        val closed = state.ledger.first { it.kind == LedgerKind.IMPAIRMENT_SPAN && it.actorId == empath }
        assertEquals(state.cycle, closed.resolvedCycle, "closed when the seat became healthy")
    }

    @Test
    fun `place honours copies and reports what it displaced`() {
        var state = game("innkeeper", "empath", "imp", "chef", "mayor")
        val innkeeper = state.seat("innkeeper")
        fun safe(target: Long) = Effects.place(
            state, target, EffectKind.CANT_DIE_TONIGHT, "innkeeper", innkeeper, Until.DAWN, "Safe",
        )
        // The Innkeeper owns exactly two SAFE tokens.
        var p = safe(1L)
        state = p.state
        p = safe(2L)
        state = p.state
        assertEquals(null, p.displaced, "two copies fit")

        p = safe(3L)
        state = p.state
        assertEquals(1L, p.displaced?.targetId, "the oldest copy is displaced, not lost")
        assertEquals(2, state.effects.count { it.label == "Safe" })
    }
}
