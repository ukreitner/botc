package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The kill funnel: the precedence table of ARCHITECTURE §2.6, first match wins,
 * and the resurrect / revive rules of lead D7.
 */
class DeathsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** A seated game, one character per seat, advanced to night 1. */
    private fun night(vararg characterIds: String): GameState {
        var state = GameActions.newGame(tb, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        return Phases.advancePhase(state, lookup)
    }

    private fun day(vararg characterIds: String): GameState =
        Phases.advancePhase(night(*characterIds), lookup)

    private fun GameState.seat(characterId: String): Long =
        players.first { it.characterId == characterId }.id

    private fun GameState.outcome(target: Long, cause: DeathCause, source: String? = null) =
        Deaths.killOutcome(this, lookup, target, KillCause(cause, source))

    private val demonKill = KillCause(DeathCause.DEMON_KILL, "imp")

    // ---- 0-1: the ends of the table ----------------------------------------

    @Test
    fun `a dead player cannot die again`() {
        var state = night("imp", "chef", "empath", "mayor", "soldier")
        state = Deaths.attempt(state, lookup, 1L, KillCause(DeathCause.EXECUTION)).state
        assertEquals(KillOutcome.AlreadyDead, state.outcome(1L, DeathCause.EXECUTION))
    }

    @Test
    fun `the assassin beats every protection and does not spend the fool`() {
        var state = night("fool", "monk", "innkeeper", "imp", "tealady")
        val fool = state.seat("fool")
        state = Effects.place(
            state, fool, EffectKind.SAFE_FROM_DEMON, "monk", state.seat("monk"), Until.DAWN, "Safe",
        ).state
        state = Effects.place(
            state, fool, EffectKind.CANT_DIE_TONIGHT, "innkeeper", state.seat("innkeeper"),
            Until.DAWN, "Safe",
        ).state

        val result = Deaths.attempt(
            state, lookup, fool,
            KillCause(DeathCause.EVIL_ABILITY, "assassin", ignoresProtection = true),
        )
        assertIs<KillOutcome.Dies>(result.outcome)
        assertFalse(result.state.players.first { it.id == fool }.alive)
        assertTrue(
            result.state.effects.none { it.kind == EffectKind.SPENT },
            "the Fool's ability is NOT spent",
        )
    }

    // ---- 2: the source is silenced -----------------------------------------

    @Test
    fun `a silenced demon cannot kill, even with a deferred death`() {
        var state = night("imp", "chef", "empath", "mayor", "soldier")
        val imp = state.seat("imp")
        state = Effects.place(
            state, imp, EffectKind.DEMON_CANNOT_KILL, "exorcist", null, Until.DAWN, "Chosen",
        ).state

        val outcome = Deaths.killOutcome(
            state, lookup, 1L, KillCause(DeathCause.DEMON_KILL, "imp", sourcePlayerId = imp),
        )
        assertIs<KillOutcome.Prevented>(outcome)
        // A non-Demon kill from the same seat is unaffected.
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(
                state, lookup, 1L,
                KillCause(DeathCause.EVIL_ABILITY, "imp", sourcePlayerId = imp),
            ),
        )
    }

    // ---- 3-9: the deterministic blocks, in order ---------------------------

    @Test
    fun `the lleech dies only when its host is dead`() {
        var state = night("lleech", "chef", "empath", "mayor", "soldier")
        val lleech = state.seat("lleech")
        state = Effects.addReminder(state, 1L, PlacedReminder("lleech", "Poisoned"))

        assertIs<KillOutcome.Prevented>(state.outcome(lleech, DeathCause.EXECUTION))

        state = Deaths.attempt(
            state, lookup, 1L,
            KillCause(DeathCause.EVIL_ABILITY, "assassin", ignoresProtection = true),
        ).state
        assertIs<KillOutcome.Dies>(state.outcome(lleech, DeathCause.EXECUTION))
    }

    @Test
    fun `the vizier cannot die by day but dies at night`() {
        val dayState = day("vizier", "chef", "empath", "mayor", "imp")
        assertIs<KillOutcome.Prevented>(dayState.outcome(dayState.seat("vizier"), DeathCause.EXECUTION))

        val nightState = night("vizier", "chef", "empath", "mayor", "imp")
        assertIs<KillOutcome.Dies>(nightState.outcome(nightState.seat("vizier"), DeathCause.DEMON_KILL))
    }

    @Test
    fun `storm catcher's player can only die by execution`() {
        var state = night("chef", "empath", "mayor", "soldier", "imp")
        state = state.copy(
            fabled = listOf(
                FabledEntry("stormcatcher", config = mapOf("stormcatcher.favouredCharacterId" to "chef")),
            ),
        )
        val chef = state.seat("chef")
        assertIs<KillOutcome.Prevented>(state.outcome(chef, DeathCause.DEMON_KILL))
        assertIs<KillOutcome.Prevented>(state.outcome(chef, DeathCause.EVIL_ABILITY))
        assertIs<KillOutcome.Dies>(state.outcome(chef, DeathCause.EXECUTION))
    }

    @Test
    fun `a sober sailor cannot die, a poisoned one can`() {
        var state = day("sailor", "chef", "empath", "mayor", "imp")
        val sailor = state.seat("sailor")
        assertIs<KillOutcome.Prevented>(state.outcome(sailor, DeathCause.EXECUTION))

        state = Effects.place(
            state, sailor, EffectKind.POISONED, "poisoner", null, Until.FOREVER, "Poisoned",
        ).state
        assertIs<KillOutcome.Dies>(state.outcome(sailor, DeathCause.EXECUTION))
    }

    @Test
    fun `the innkeeper blocks any night death but never an execution`() {
        var state = night("innkeeper", "chef", "empath", "mayor", "imp")
        val innkeeper = state.seat("innkeeper")
        state = Effects.place(
            state, 1L, EffectKind.CANT_DIE_TONIGHT, "innkeeper", innkeeper, Until.DAWN, "Safe",
        ).state

        assertIs<KillOutcome.Prevented>(state.outcome(1L, DeathCause.DEMON_KILL))
        assertIs<KillOutcome.Prevented>(
            state.outcome(1L, DeathCause.EVIL_ABILITY, "godfather"),
        )
        val dayState = Phases.advancePhase(state, lookup)
        assertIs<KillOutcome.Dies>(dayState.outcome(1L, DeathCause.EXECUTION))
    }

    @Test
    fun `the soldier is safe from the demon only`() {
        val state = day("soldier", "chef", "empath", "mayor", "imp")
        val soldier = state.seat("soldier")
        assertIs<KillOutcome.Prevented>(state.outcome(soldier, DeathCause.DEMON_KILL))
        assertIs<KillOutcome.Dies>(state.outcome(soldier, DeathCause.EXECUTION))
        assertIs<KillOutcome.Dies>(state.outcome(soldier, DeathCause.EVIL_ABILITY, "godfather"))
    }

    @Test
    fun `a monk protected imp self-kill produces no death and no star pass`() {
        var state = night("imp", "monk", "chef", "empath", "mayor")
        val imp = state.seat("imp")
        state = Effects.place(
            state, imp, EffectKind.SAFE_FROM_DEMON, "monk", state.seat("monk"), Until.DAWN, "Safe",
        ).state

        val result = Deaths.attempt(
            state, lookup, imp, KillCause(DeathCause.DEMON_KILL, "imp", sourcePlayerId = imp),
        )
        assertIs<KillOutcome.Prevented>(result.outcome)
        assertTrue(result.state.players.first { it.id == imp }.alive)
        assertEquals(null, result.event, "no death was recorded")
        assertTrue(result.state.deaths.isEmpty())
    }

    @Test
    fun `the devil's advocate survives an execution but not the night`() {
        var state = day("chef", "empath", "mayor", "soldier", "imp")
        state = Effects.place(
            state, 0L, EffectKind.SURVIVES_EXECUTION, "devilsadvocate", null, Until.DUSK,
            "Survives Execution",
        ).state
        assertIs<KillOutcome.Prevented>(state.outcome(0L, DeathCause.EXECUTION))
        assertIs<KillOutcome.Dies>(state.outcome(0L, DeathCause.DEMON_KILL))
    }

    @Test
    fun `the devil's advocate token is swept at dusk`() {
        var state = day("chef", "empath", "mayor", "soldier", "imp")
        state = Effects.addReminder(state, 0L, PlacedReminder("devilsadvocate", "Survives Execution"))
        assertIs<KillOutcome.Prevented>(state.outcome(0L, DeathCause.EXECUTION))

        state = Phases.advancePhase(state, lookup) // night 2 — past dusk
        assertTrue(
            state.players.first { it.id == 0L }.reminders.isEmpty(),
            "the regression the user reported: the DA token must clear itself",
        )
    }

    // ---- 10-13: the choices ------------------------------------------------

    @Test
    fun `the pacifist offers a choice for a good player only`() {
        var state = day("pacifist", "chef", "empath", "imp", "poisoner")
        val chef = state.seat("chef")
        val choice = state.outcome(chef, DeathCause.EXECUTION)
        assertIs<KillOutcome.Choice>(choice)
        assertEquals(2, choice.options.size)

        // Evil players are never offered.
        assertIs<KillOutcome.Dies>(state.outcome(state.seat("poisoner"), DeathCause.EXECUTION))

        // A poisoned Pacifist offers nothing.
        state = Effects.place(
            state, state.seat("pacifist"), EffectKind.POISONED, "poisoner", null,
            Until.FOREVER, "Poisoned",
        ).state
        assertIs<KillOutcome.Dies>(state.outcome(chef, DeathCause.EXECUTION))
    }

    @Test
    fun `answering the pacifist choice applies exactly that option`() {
        val state = day("pacifist", "chef", "empath", "imp", "poisoner")
        val chef = state.seat("chef")

        val lives = Deaths.attempt(
            state, lookup, chef, KillCause(DeathCause.EXECUTION), Deaths.OPTION_LIVES,
        )
        assertIs<KillOutcome.Prevented>(lives.outcome)
        assertTrue(lives.state.players.first { it.id == chef }.alive)

        val dies = Deaths.attempt(
            state, lookup, chef, KillCause(DeathCause.EXECUTION), Deaths.OPTION_DIES,
        )
        assertIs<KillOutcome.Dies>(dies.outcome)
        assertFalse(dies.state.players.first { it.id == chef }.alive)
    }

    @Test
    fun `a monk protected mayor gives nobody dies, not a redirect`() {
        var state = night("mayor", "monk", "chef", "empath", "imp")
        val mayor = state.seat("mayor")
        state = Effects.place(
            state, mayor, EffectKind.SAFE_FROM_DEMON, "monk", state.seat("monk"), Until.DAWN, "Safe",
        ).state
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(state, lookup, mayor, demonKill),
        )
    }

    @Test
    fun `an unprotected mayor offers the bounce`() {
        val state = night("mayor", "monk", "chef", "empath", "imp")
        val choice = Deaths.killOutcome(state, lookup, state.seat("mayor"), demonKill)
        assertIs<KillOutcome.Choice>(choice)

        val redirect = Deaths.attempt(
            state, lookup, state.seat("mayor"), demonKill, Deaths.OPTION_REDIRECT,
        )
        assertIs<KillOutcome.Redirect>(redirect.outcome)
        assertTrue(redirect.state.players.first { it.id == state.seat("mayor") }.alive)
        // The bounce OFFERS every other seat; it must not kill them all.
        assertEquals(4, (redirect.outcome as KillOutcome.Redirect).to.size)
        assertTrue(redirect.state.deaths.isEmpty(), "the storyteller still has to pick one")
        assertEquals(state.alivePlayers.size, redirect.state.alivePlayers.size)

        // Naming the seat settles it.
        val settled = Deaths.attempt(state, lookup, 2L, demonKill)
        assertIs<KillOutcome.Dies>(settled.outcome)
        assertEquals(1, settled.state.deaths.size)
    }

    @Test
    fun `the scapegoat may be executed instead, and it is still the day's execution`() {
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E"))
        listOf("chef", "empath", "mayor", "imp", "scapegoat")
            .forEachIndexed { i, id ->
                state = GameActions.assignCharacter(state, i.toLong(), id, isTraveller = id == "scapegoat")
            }
        state = Seats.setAlignment(state, 4L, Alignment.GOOD)
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup) // day 1

        val choice = state.outcome(0L, DeathCause.EXECUTION)
        assertIs<KillOutcome.Choice>(choice)

        val applied = Deaths.attempt(
            state, lookup, 0L, KillCause(DeathCause.EXECUTION), Deaths.OPTION_REDIRECT,
        )
        assertIs<KillOutcome.Redirect>(applied.outcome)
        assertTrue(applied.state.players.first { it.id == 0L }.alive, "the nominee lives")
        assertFalse(applied.state.players.first { it.id == 4L }.alive, "the Scapegoat dies")
    }

    // ---- 14-15: the two that come last -------------------------------------

    @Test
    fun `the zombuul's first death registers dead, the second is real`() {
        var state = day("zombuul", "chef", "empath", "mayor", "soldier")
        val zombuul = state.seat("zombuul")
        val first = Deaths.attempt(state, lookup, zombuul, KillCause(DeathCause.EXECUTION))
        assertIs<KillOutcome.RegistersDead>(first.outcome)
        state = first.state
        assertTrue(state.isTrulyAlive(zombuul), "the game is not over")
        assertEquals(true, state.deaths.single().registeredOnly)

        val second = Deaths.attempt(state, lookup, zombuul, KillCause(DeathCause.EXECUTION))
        assertIs<KillOutcome.Dies>(second.outcome)
        assertFalse(second.state.isTrulyAlive(zombuul))
    }

    @Test
    fun `a poisoned zombuul just dies`() {
        var state = day("zombuul", "chef", "empath", "mayor", "soldier")
        val zombuul = state.seat("zombuul")
        state = Effects.place(
            state, zombuul, EffectKind.POISONED, "poisoner", null, Until.FOREVER, "Poisoned",
        ).state
        assertIs<KillOutcome.Dies>(state.outcome(zombuul, DeathCause.EXECUTION))
    }

    @Test
    fun `the fool survives once, then dies`() {
        var state = day("fool", "chef", "empath", "mayor", "imp")
        val fool = state.seat("fool")
        val first = Deaths.attempt(state, lookup, fool, KillCause(DeathCause.EXECUTION))
        assertIs<KillOutcome.Spends>(first.outcome)
        state = first.state
        assertTrue(state.players.first { it.id == fool }.alive)
        assertEquals(
            1,
            state.effects.count { it.targetId == fool && it.kind == EffectKind.SPENT },
        )
        assertEquals("No Ability", state.effects.first { it.kind == EffectKind.SPENT }.label)

        assertIs<KillOutcome.Dies>(state.outcome(fool, DeathCause.EXECUTION))
    }

    @Test
    fun `a poisoned fool dies and the ability is not spent by the funnel`() {
        var state = day("fool", "chef", "empath", "mayor", "imp")
        val fool = state.seat("fool")
        state = Effects.place(
            state, fool, EffectKind.POISONED, "poisoner", null, Until.FOREVER, "Poisoned",
        ).state
        assertIs<KillOutcome.Dies>(state.outcome(fool, DeathCause.EXECUTION))
    }

    // ---- what the funnel records -------------------------------------------

    @Test
    fun `a death snapshots the character, team and impairment at the time`() {
        var state = night("imp", "chef", "empath", "mayor", "soldier")
        val chef = state.seat("chef")
        state = Effects.place(
            state, chef, EffectKind.POISONED, "poisoner", null, Until.FOREVER, "Poisoned",
        ).state

        val result = Deaths.attempt(
            state, lookup, chef, KillCause(DeathCause.DEMON_KILL, "imp", sourcePlayerId = 0L),
        )
        val event = assertNotNull(result.event)
        assertEquals("chef", event.characterIdAtDeath)
        assertEquals(Team.TOWNSFOLK, event.teamAtDeath)
        assertEquals(false, event.evilAtDeath)
        assertEquals(true, event.abilityImpairedAtDeath)
        assertEquals("imp", event.killerCharacterId)
        assertEquals(0L, event.killerPlayerId)
    }

    @Test
    fun `a prevented death is recorded as a ruling, not as a death`() {
        var state = day("sailor", "chef", "empath", "mayor", "imp")
        val sailor = state.seat("sailor")
        val result = Deaths.attempt(state, lookup, sailor, KillCause(DeathCause.EXECUTION))

        assertIs<KillOutcome.Prevented>(result.outcome)
        assertTrue(result.state.deaths.isEmpty(), "nobody died")
        val ruling = result.state.ledger.single { it.kind == LedgerKind.RULING }
        assertEquals(sailor, ruling.actorId)
        assertTrue("remains alive" in ruling.shown, ruling.shown)
    }

    // ---- resurrect and revive ----------------------------------------------

    @Test
    fun `resurrect restores a spent ability and queues the first night rerun`() {
        var state = night("professor", "slayer", "imp", "chef", "mayor")
        val slayer = state.seat("slayer")
        state = Effects.place(
            state, slayer, EffectKind.SPENT, "slayer", slayer, Until.FOREVER, "No Ability",
        ).state
        state = Deaths.attempt(state, lookup, slayer, KillCause(DeathCause.DEMON_KILL, "imp")).state

        state = Deaths.resurrect(state, lookup, slayer)
        assertTrue(state.players.first { it.id == slayer }.alive)
        assertTrue(
            state.effects.none { it.targetId == slayer && it.kind == EffectKind.SPENT },
            "the once-per-game comes back (Glossary)",
        )
        assertEquals(1, state.deaths.size)
        assertTrue(state.deaths.single().resurrected)
        assertTrue(
            state.prompts.any { it.kind == PromptKind.RUN_FIRST_NIGHT && it.subjectPlayerId == slayer },
        )
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.ANNOUNCE && it.announcePending },
            "the storyteller still owes the table the announcement",
        )
    }

    @Test
    fun `resurrect keeps the virgin's first nomination spent`() {
        var state = night("virgin", "professor", "imp", "chef", "mayor")
        val virgin = state.seat("virgin")
        state = Effects.place(
            state, virgin, EffectKind.SPENT, "virgin", virgin, Until.FOREVER, "No Ability",
        ).state
        state = Deaths.attempt(state, lookup, virgin, KillCause(DeathCause.DEMON_KILL, "imp")).state
        state = Deaths.resurrect(state, lookup, virgin)

        assertTrue(
            state.effects.any { it.targetId == virgin && it.kind == EffectKind.SPENT },
            "the 1st nomination is a historical fact (lead D7)",
        )
    }

    @Test
    fun `revive drops the death and everything it created`() {
        var state = night("sweetheart", "chef", "imp", "empath", "mayor")
        val sweetheart = state.seat("sweetheart")
        state = Deaths.toggleGhostVote(state, sweetheart)
        val result = Deaths.attempt(state, lookup, sweetheart, KillCause(DeathCause.DEMON_KILL, "imp"))
        state = result.state
        val eventId = assertNotNull(result.event).id

        // The Sweetheart's on-death drunk, stamped with the death that caused it.
        state = state.copy(
            effects = state.effects + Effect(
                id = state.nextEffectId,
                kind = EffectKind.DRUNK,
                targetId = 1L,
                sourceCharacterId = "sweetheart",
                sourcePlayerId = sweetheart,
                until = Until.FOREVER,
                endsWithSource = false,
                label = "Drunk",
                createdCycle = state.cycle,
                createdAtNight = true,
                causeEventId = eventId,
            ),
            nextEffectId = state.nextEffectId + 1,
        )
        assertTrue(Status.isImpaired(state, lookup, 1L))

        state = Deaths.revive(state, sweetheart)
        assertTrue(state.players.first { it.id == sweetheart }.alive)
        assertTrue(state.deaths.isEmpty(), "as if it never happened")
        assertFalse(Status.isImpaired(state, lookup, 1L), "the effect it caused is rolled back")
    }

    @Test
    fun `the protects table is cause-filtered`() {
        assertEquals(
            setOf(DeathCause.EXECUTION),
            Deaths.PROTECTS[EffectKind.SURVIVES_EXECUTION],
        )
        assertTrue(DeathCause.DEMON_KILL in Deaths.PROTECTS.getValue(EffectKind.SAFE_FROM_DEMON))
        assertFalse(DeathCause.EXECUTION in Deaths.PROTECTS.getValue(EffectKind.SAFE_FROM_DEMON))
        // A Gunslinger shot (a day ability) is blocked by the Tea Lady, not the Monk.
        assertTrue(DeathCause.DAY_ABILITY in Deaths.PROTECTS.getValue(EffectKind.CANT_DIE))
        assertFalse(DeathCause.DAY_ABILITY in Deaths.PROTECTS.getValue(EffectKind.SAFE_FROM_DEMON))
        assertFalse(DeathCause.DAY_ABILITY in Deaths.PROTECTS.getValue(EffectKind.CANT_DIE_TONIGHT))
    }
}
