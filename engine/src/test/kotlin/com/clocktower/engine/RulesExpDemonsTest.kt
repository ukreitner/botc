package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The experimental Demon registry (WP7-EXP-D).
 *
 * Every P0 of the two digest cards, driven through the REAL pipeline —
 * `NightPlan.build` / `NightPlan.resolve` / `Deaths.attempt` /
 * `Execution.consequences` / `DayRules.checkNomination` / `WinCheck` — never by
 * poking at the rule object.
 */
class RulesExpDemonsTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** The ten `edition = "exp"`, `team = "demon"` ids of `characters.json`. */
    private val expDemons = listOf(
        "alhadikhia", "kazali", "legion", "leviathan", "lilmonsta",
        "lleech", "lordoftyphon", "ojo", "riot", "yaggababble",
    )

    // ---- fixtures ----------------------------------------------------------

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(tb, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return Phases.advancePhase(state, lookup)
    }

    private fun atNight(state: GameState, n: Int): GameState = state.copy(cycle = n)

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun steps(state: GameState, abilityId: String): List<NightStep> =
        plan(state).steps.filter { it.abilityId == abilityId }

    private fun step(state: GameState, abilityId: String): NightStep? =
        steps(state, abilityId).firstOrNull()

    private fun resolve(state: GameState, step: NightStep, input: NightInput): GameState =
        NightPlan.resolve(state, lookup, step.key, input)

    private fun tokenOn(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        return state.effects.any {
            it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
        } || state.player(playerId)?.reminders.orEmpty().any { Tokens.key(it) == key }
    }

    /** The one action a step offers, whatever wrapper it arrives in. */
    private fun actions(step: NightStep?): List<NightAction> = when (val a = step?.action) {
        null -> emptyList()
        is Sequence -> a.stages
        else -> listOf(a)
    }

    /** True when this step would let the storyteller kill somebody. */
    private fun offersAKill(step: NightStep?): Boolean = actions(step).any { action ->
        val effects = when (action) {
            is ChoosePlayers -> action.perTarget + action.onResolve + action.onNone
            is ChoosePlayerAndCharacter -> action.onResolve
            is ChooseCharacter -> action.onResolve
            is YesNo -> action.onYes + action.onNo
            else -> emptyList()
        }
        effects.any { it is NightEffect.Attack }
    }

    // =======================================================================
    // Coverage and the token table
    // =======================================================================

    @Test
    fun `every experimental Demon has a registry row`() {
        val missing = expDemons.filter { CharacterRules.all[it] == null }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
        // The row, not the generic fallback, is what the engine picks up.
        for (id in expDemons) {
            assertEquals(id, CharacterRules.of(id, lookup(id)).id)
        }
    }

    @Test
    fun `the data really does list these ten as experimental Demons`() {
        val fromData = data.characters
            .filter { it.edition == "exp" && it.team == Team.DEMON }
            .map { it.id }
            .sorted()
        assertEquals(expDemons.sorted(), fromData, "the scope of WP7-EXP-D moved")
    }

    @Test
    fun `every token this package declares is a real reminder with the right copy count`() {
        val ours = expDemons.flatMap { CharacterRules.all.getValue(it).tokens }
        assertTrue(ours.isNotEmpty(), "this package declares token rules")
        val problems = mutableListOf<String>()
        for (rule in ours) {
            val character = lookup(rule.sourceId)
            if (character == null) {
                problems += "${rule.sourceId} is not a character"
                continue
            }
            val copies = character.allReminders.count { it.trim().equals(rule.label.trim(), true) }
            if (copies == 0) {
                problems += "${rule.sourceId}/${rule.label} — has ${character.allReminders}"
            } else if (copies != rule.copies) {
                problems += "${rule.sourceId}/${rule.label}: rule says ${rule.copies}, data has $copies"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the token lifetimes this package refines are the ones in force`() {
        // Al-Hadikhia's 1|2|3 must survive dawn (the status report reads them) and
        // be gone before the next selection.
        for (label in listOf("1", "2", "3")) {
            assertEquals(
                Until.DUSK,
                assertNotNull(Tokens.rule("alhadikhia", label)).until,
                "alhadikhia/$label",
            )
            assertTrue(Tokens.rule("alhadikhia", label)!! in Tokens.expiringAtDusk)
        }
        // The babysitter token never expires: registration and the Psychopath /
        // Vizier jinxes are day-time rules.
        val babysitter = assertNotNull(Tokens.rule("lilmonsta", "Is The Demon"))
        assertEquals(Until.FOREVER, babysitter.until)
        assertFalse(babysitter in Tokens.expiringAtDawn)
        // WP1's countdowns and markers stay exactly as they are.
        assertEquals("leviathan.day", assertNotNull(Tokens.rule("leviathan", "Day 3")).exclusiveGroup)
        assertEquals("riot.day", assertNotNull(Tokens.rule("riot", "Day 3")).exclusiveGroup)
        assertEquals(3, assertNotNull(Tokens.rule("yaggababble", "Dead")).copies)
    }

    // =======================================================================
    // P0 — the shared defect: no kill panel on a night with no kill
    // =======================================================================

    @Test
    fun `no experimental Demon is offered a kill on a night it has none`() {
        val night1 = mapOf(
            "kazali" to game("kazali", "chef", "empath", "undertaker", "washerwoman"),
            "lleech" to game("lleech", "chef", "empath", "undertaker", "washerwoman"),
            "lordoftyphon" to game("lordoftyphon", "chef", "empath", "undertaker", "washerwoman"),
            "yaggababble" to game("yaggababble", "chef", "empath", "undertaker", "washerwoman"),
            "leviathan" to game("leviathan", "chef", "empath", "undertaker", "washerwoman"),
            "lilmonsta" to game("lilmonsta", "chef", "empath", "undertaker", "washerwoman"),
        )
        for ((id, state) in night1) {
            assertFalse(offersAKill(step(state, id)), "$id must not be offered a kill on night 1")
        }
        // Leviathan and Riot never kill at night at all.
        val leviathan = atNight(game("leviathan", "chef", "empath", "undertaker", "washerwoman"), 3)
        assertFalse(offersAKill(step(leviathan, "leviathan")), "the Leviathan never kills")
        val riot = atNight(game("riot", "poisoner", "chef", "empath", "undertaker"), 2)
        assertFalse(offersAKill(step(riot, "riot")), "Riot does not kill at night")
        // The Ojo has no first-night row whatsoever.
        val ojo = game("ojo", "chef", "empath", "undertaker", "washerwoman")
        assertTrue(steps(ojo, "ojo").isEmpty(), "the Ojo is absent from the first night")
    }

    // =======================================================================
    // P0 — Legion: one row for every holder
    // =======================================================================

    @Test
    fun `Legion wakes on a single group row that survives the death of one Legion`() {
        // Given three Legion and three good players on night 2,
        var state = atNight(
            game("legion", "legion", "legion", "chef", "empath", "undertaker"),
            2,
        )

        // When the sheet is built,
        val rows = steps(state, "legion")

        // Then there is exactly ONE row, and it names all three holders.
        assertEquals(1, rows.size, "Legion is a group step: ${rows.map { it.key.token }}")
        assertEquals(listOf(0L, 1L, 2L), rows.single().holderIds)
        assertIs<StepGate.Fire>(rows.single().gate)
        assertTrue(rows.single().required)

        // And when the lowest-seated Legion dies, the kill tool is still there —
        // the gate asks whether ANY Legion is alive.
        state = state.updatePlayer(0L) { it.copy(alive = false) }
        val after = assertNotNull(step(state, "legion"))
        assertIs<StepGate.Fire>(after.gate)
        val action = assertIs<ChoosePlayers>(after.action)
        assertTrue(action.allowNone, "'nobody dies tonight' is a recorded answer")
        assertEquals(0, action.min)

        // And "nobody dies" really is recorded, not merely offered.
        val resolved = resolve(state, after, NightInput(none = true))
        val recorded = resolved.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "legion" }
        assertEquals(NightPlan.NO_CHOICE, recorded.text)
        assertEquals(0, resolved.deaths.size)

        // But with every Legion dead the row goes away with a reason.
        val allDead = listOf(0L, 1L, 2L).fold(state) { acc, id ->
            acc.updatePlayer(id) { it.copy(alive = false) }
        }
        assertIs<StepGate.Skip>(assertNotNull(step(allDead, "legion")).gate)
    }

    @Test
    fun `a Legion kill goes through the funnel and is attributed to Legion`() {
        var state = atNight(game("legion", "legion", "chef", "empath", "undertaker", "monk"), 2)
        val row = assertNotNull(step(state, "legion"))
        state = resolve(state, row, NightInput(playerIds = listOf(2L)))
        val death = assertNotNull(state.deaths.lastOrNull())
        assertEquals(2L, death.playerId)
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("legion", death.killerCharacterId)
    }

    // =======================================================================
    // P0 — Lleech: the host chain
    // =======================================================================

    @Test
    fun `the Lleech chooses a host on night one and cannot die while it lives`() {
        // Given a first-night Lleech,
        var state = game("lleech", "chef", "empath", "undertaker", "washerwoman")
        val row = assertNotNull(step(state, "lleech"), "the Lleech acts on night 1")
        assertFalse(offersAKill(row), "no kill on night 1 — this is the host choice")

        // When it chooses P2 as its host,
        state = resolve(state, row, NightInput(playerIds = listOf(1L)))

        // Then the host is marked as the host AND poisoned — two separate facts
        // since WP6C, so the official Soldier ruling (host, not poisoned) can be
        // expressed by lifting the poison alone.
        assertTrue(tokenOn(state, 1L, "lleech", "Host"))
        assertTrue(tokenOn(state, 1L, "lleech", "Poisoned"))
        assertTrue(Status.isImpaired(state, lookup, 1L), "the host is poisoned")
        val blocked = Deaths.killOutcome(state, lookup, 0L, KillCause(DeathCause.EXECUTION))
        val prevented = assertIs<KillOutcome.Prevented>(blocked)
        assertTrue(prevented.reason.contains("P2"), "the block names the host: ${prevented.reason}")

        // And the choice is spent: rebuilding tonight's sheet skips the host step…
        val skip = assertIs<StepGate.Skip>(assertNotNull(step(state, "lleech")).gate)
        assertTrue(skip.reason.contains("already has a host"), skip.reason)
        // …while night 2 is the ordinary kill.
        assertTrue(offersAKill(step(atNight(state, 2), "lleech")), "night 2 is the ordinary kill")
    }

    @Test
    fun `killing the host kills the Lleech and hands the game to good`() {
        // Given a Lleech hosted on P2,
        var state = game("lleech", "chef", "empath", "undertaker", "washerwoman")
        state = resolve(state, assertNotNull(step(state, "lleech")), NightInput(playerIds = listOf(1L)))
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(state, lookup, 0L, KillCause(DeathCause.EXECUTION)),
        )

        // When the host is executed,
        val after = Deaths.attempt(state, lookup, 1L, KillCause(DeathCause.EXECUTION)).state

        // Then the storyteller is told, in the same action, that the Lleech dies now,
        val obligation = assertNotNull(
            after.prompts.lastOrNull {
                it.sourceId == "lleech" && it.kind == PromptKind.RESOLVE_KILL
            },
            "the host-death chain raises an obligation: ${after.prompts.map { it.title }}",
        )
        assertEquals(0L, obligation.subjectPlayerId)
        assertTrue(obligation.title.contains("GOOD WINS"))

        // and the block is gone, so the Lleech really can die.
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(after, lookup, 0L, KillCause(DeathCause.EXECUTION)),
        )
        val dead = Deaths.attempt(after, lookup, 0L, KillCause(DeathCause.EXECUTION)).state
        assertFalse(assertNotNull(dead.player(0L)).alive)
    }

    @Test
    fun `an impaired Lleech survives its host and says nothing`() {
        // Given a Lleech that is itself drunk,
        var state = game("lleech", "chef", "empath", "undertaker", "washerwoman")
        state = resolve(state, assertNotNull(step(state, "lleech")), NightInput(playerIds = listOf(1L)))
        state = Effects.place(
            state = state,
            target = 0L,
            kind = EffectKind.DRUNK,
            sourceCharacterId = "sailor",
            sourcePlayerId = null,
            until = Until.FOREVER,
            label = "Drunk",
        ).state

        // When the host dies,
        val after = Deaths.attempt(state, lookup, 1L, KillCause(DeathCause.EXECUTION)).state

        // Then nothing kills the Lleech and the note is an announcement, not a kill.
        assertNull(after.prompts.lastOrNull { it.sourceId == "lleech" && it.kind == PromptKind.RESOLVE_KILL })
        val note = assertNotNull(
            after.prompts.lastOrNull { it.sourceId == "lleech" && it.kind == PromptKind.ANNOUNCE },
        )
        assertTrue(note.title.contains("survives"), note.title)
    }

    @Test
    fun `the Mastermind jinx leaves the Lleech alive without its ability`() {
        // Given a living Mastermind and a hosted Lleech,
        var state = game("lleech", "chef", "mastermind", "undertaker", "washerwoman")
        state = resolve(state, assertNotNull(step(state, "lleech")), NightInput(playerIds = listOf(1L)))

        // When the host dies BY EXECUTION,
        val after = Deaths.attempt(state, lookup, 1L, KillCause(DeathCause.EXECUTION)).state

        // Then the Lleech lives and loses its ability instead.
        assertTrue(assertNotNull(after.player(0L)).alive)
        assertNull(after.prompts.lastOrNull { it.sourceId == "lleech" && it.kind == PromptKind.RESOLVE_KILL })
        assertTrue(
            after.effects.any { it.targetId == 0L && it.kind == EffectKind.NO_ABILITY },
            "the jinx places NO_ABILITY on the Lleech",
        )
        assertFalse(Status.hasAbility(after, lookup, 0L))
        // Effect ids are stamped by the funnel, never left at 0 (lead D64).
        assertTrue(after.effects.none { it.id == 0L })
    }

    // =======================================================================
    // P0 — Al-Hadikhia: the three-choice ritual
    // =======================================================================

    @Test
    fun `all three choose to live so all three die`() {
        // Given an Al-Hadikhia on night 2,
        var state = atNight(
            game("alhadikhia", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler"),
            2,
        )
        val row = assertNotNull(step(state, "alhadikhia"))
        assertIs<Sequence>(row.action)

        // When it chooses P2, P3, P4 and all three answer "live",
        state = resolve(state, row, NightInput(playerIds = listOf(1L, 2L, 3L), yes = true))

        // Then the 1 | 2 | 3 tokens are on those seats in pick order,
        assertTrue(tokenOn(state, 1L, "alhadikhia", "1"))
        assertTrue(tokenOn(state, 2L, "alhadikhia", "2"))
        assertTrue(tokenOn(state, 3L, "alhadikhia", "3"))

        // all three are dead, credited to the Al-Hadikhia,
        for (id in listOf(1L, 2L, 3L)) {
            assertFalse(assertNotNull(state.player(id)).alive, "P${id + 1} dies")
        }
        assertEquals(3, state.deaths.size)
        assertTrue(state.deaths.all { it.cause == DeathCause.DEMON_KILL })
        assertTrue(state.deaths.all { it.killerCharacterId == "alhadikhia" })

        // and the dawn announcement is owed.
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.ANNOUNCE && it.text.contains("Al-Hadikhia") },
        )
    }

    @Test
    fun `not everyone chose to live so the deaths are resolved one at a time`() {
        var state = atNight(
            game("alhadikhia", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler"),
            2,
        )
        val row = assertNotNull(step(state, "alhadikhia"))
        state = resolve(state, row, NightInput(playerIds = listOf(1L, 2L, 3L), yes = false))

        assertEquals(0, state.deaths.size, "the all-live rule did not fire")
        assertNotNull(
            state.prompts.lastOrNull {
                it.sourceId == "alhadikhia" && it.kind == PromptKind.RESOLVE_KILL
            },
            "each answer is one kill attempt, so protections are surfaced per victim",
        )
    }

    @Test
    fun `choosing no one places no tokens and owes no announcement`() {
        var state = atNight(
            game("alhadikhia", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler"),
            2,
        )
        state = resolve(state, assertNotNull(step(state, "alhadikhia")), NightInput(none = true))

        assertTrue(state.effects.none { Character.normalizeId(it.sourceCharacterId) == "alhadikhia" })
        assertEquals(0, state.deaths.size)
        assertTrue(
            state.ledger.none { it.kind == LedgerKind.ANNOUNCE && it.text.contains("Al-Hadikhia") },
            "no announcement at all when nobody was chosen",
        )
        assertEquals(
            NightPlan.NO_CHOICE,
            state.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "alhadikhia" }.text,
        )
    }

    // =======================================================================
    // P0 — Ojo: a CHARACTER pick, and the not-in-play redirect
    // =======================================================================

    @Test
    fun `the Ojo names a character and the storyteller chooses when it is not in play`() {
        // Given an Ojo on night 2 with no Slayer in play,
        var state = atNight(
            game("ojo", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler"),
            2,
        )
        val row = assertNotNull(step(state, "ojo"))
        val action = assertIs<ChoosePlayerAndCharacter>(row.action)
        assertEquals(CharacterPool.SCRIPT, action.pool, "the printed sheet, not an in-play filter")
        assertFalse(action.requireNotInPlay)

        // When the Ojo names the Slayer and the storyteller picks P5 to die,
        state = resolve(
            state,
            row,
            NightInput(playerIds = listOf(4L), characterIds = listOf("slayer")),
        )

        // Then P5 dies as a Demon kill,
        assertFalse(assertNotNull(state.player(4L)).alive)
        assertEquals(DeathCause.DEMON_KILL, assertNotNull(state.deaths.lastOrNull()).cause)

        // and the NAME is recorded as well as the victim — it is evidence either way.
        val recorded = state.ledger.last { it.kind == LedgerKind.CHOICE && it.sourceId == "ojo" }
        assertEquals(listOf("slayer"), recorded.characterIds)
        assertEquals(listOf(4L), recorded.targetIds)
    }

    @Test
    fun `a protected in-play holder means nobody dies and no substitute is offered`() {
        // Given a Monk-protected Soldier holding the named character,
        var state = atNight(
            game("ojo", "soldier", "monk", "undertaker", "washerwoman", "librarian", "butler"),
            2,
        )
        state = Effects.place(
            state = state,
            target = 1L,
            kind = EffectKind.SAFE_FROM_DEMON,
            sourceCharacterId = "monk",
            sourcePlayerId = 2L,
            until = Until.DAWN,
            label = "Safe",
        ).state

        // When the Ojo names the Soldier,
        state = resolve(
            state,
            assertNotNull(step(state, "ojo")),
            NightInput(playerIds = listOf(1L), characterIds = listOf("soldier")),
        )

        // Then nobody dies, the attempt is recorded, and the choice is spent.
        assertTrue(assertNotNull(state.player(1L)).alive)
        assertEquals(0, state.deaths.size)
        assertTrue(state.ledger.any { it.kind == LedgerKind.RULING })
    }

    // =======================================================================
    // P0 — Riot: night 3 converts the Minions; days 1 and 2 are normal
    // =======================================================================

    @Test
    fun `on night three every Minion becomes a Riot`() {
        // Given a Riot game with two Minions on night 3,
        var state = atNight(
            game("riot", "poisoner", "baron", "chef", "empath", "undertaker", "washerwoman"),
            3,
        )
        val row = assertNotNull(step(state, "riot"))
        assertIs<StepGate.Fire>(row.gate)
        assertFalse(offersAKill(row), "this is a conversion, never a kill")

        // When both Minions are converted,
        state = resolve(state, row, NightInput(playerIds = listOf(1L, 2L)))

        // Then both seats ARE Riot, evil, and their old character is on the record.
        for (id in listOf(1L, 2L)) {
            assertEquals("riot", assertNotNull(state.player(id)).characterId)
            assertTrue(assertNotNull(state.player(id)).isEvil(lookup))
        }
        assertTrue(
            state.identityLog.any { it.playerId == 1L && it.fromCharacterId == "poisoner" },
            "the previous character is kept: ${state.identityLog.map { it.fromCharacterId }}",
        )
        // Their Minion steps are gone from tonight's sheet.
        assertTrue(steps(state, "poisoner").isEmpty())
        // And all three Riot share EXACTLY ONE group row naming every holder.
        // W7B: `insertions` used to add a "new character" row per seat converted
        // tonight, so night 3 rendered one group row plus two duplicates.
        assertEquals(
            listOf(listOf(0L, 1L, 2L)),
            steps(state, "riot").map { it.holderIds },
            "one group row, never one per convert",
        )
    }

    @Test
    fun `Riot does not kill at night and says why`() {
        // Riot is absent from the first-night order entirely.
        val night1 = game("riot", "poisoner", "chef", "empath", "undertaker", "washerwoman")
        assertTrue(steps(night1, "riot").isEmpty(), "no first-night Riot row")

        val night2 = atNight(night1, 2)
        val row = assertNotNull(step(night2, "riot"), "night 2 lists the Riot")
        val skip = assertIs<StepGate.Skip>(row.gate)
        assertTrue(skip.reason.contains("does not kill at night"), skip.reason)
        assertTrue(skip.reason.contains("days 1 and 2 are completely normal"), skip.reason)
    }

    @Test
    fun `the day three chain still dies and the instant-win jinxes fire first`() {
        // Given day 3 of a Riot game with a Monk-protected nominee,
        var state = Phases.advancePhase(
            atNight(game("riot", "monk", "chef", "empath", "undertaker", "washerwoman"), 3),
            lookup,
        )
        state = Effects.addCentreReminder(state, PlacedReminder("riot", "Day 3"))
        assertEquals(3, DayRules.riotDay(state))
        state = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.SAFE_FROM_DEMON,
            sourceCharacterId = "monk",
            sourcePlayerId = 1L,
            until = Until.DAWN,
            label = "Safe",
        ).state

        // When P3 is nominated,
        val triggers = DayRules.checkNomination(state, lookup, nominatorId = 4L, nomineeId = 2L)
            .triggers
            .filter { it.sourceId == "riot" }

        // Then the good-win jinx is raised BEFORE the death,
        assertTrue(
            triggers.any { it.kind == TriggerKind.WARN && it.headline.contains("GOOD WINS") },
            "the four instant-win jinxes fire at nomination time: ${triggers.map { it.headline }}",
        )
        // and WP3's AUTO_DEATH row is still there — a registry row replaces the
        // built-in outright, so it must re-emit it (lead D61).
        val auto = assertNotNull(triggers.firstOrNull { it.kind == TriggerKind.AUTO_DEATH })
        assertEquals(2L, auto.targetId)
        assertTrue(auto.options.any { it.id == DayRules.OPTION_APPLY })

        // And on day 1 the day is completely ordinary.
        var day1 = Phases.advancePhase(
            game("riot", "monk", "chef", "empath", "undertaker", "washerwoman"),
            lookup,
        )
        day1 = Effects.addCentreReminder(day1, PlacedReminder("riot", "Day 1"))
        assertTrue(
            DayRules.checkNomination(day1, lookup, 4L, 2L).triggers.none { it.sourceId == "riot" },
            "days 1 and 2 are normal nominations",
        )
    }

    // =======================================================================
    // P0 — Leviathan: the counter
    // =======================================================================

    @Test
    fun `the Leviathan counts every executed good player and wins at two`() {
        // Given a day-1 Leviathan game,
        var state = Phases.advancePhase(
            game("leviathan", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler"),
            lookup,
        )
        val executed = ExecutionRecord(
            day = 1,
            outcome = ExecutionOutcome.SURVIVED,
            playerId = 1L,
            wasEvilAtExecution = false,
        )

        // When a good player is executed but does not die,
        val first = Execution.consequences(state, lookup, executed)
            .filter { it.sourceId == "leviathan" }

        // Then it still counts, and the row says so.
        val row = assertNotNull(first.firstOrNull(), "the Leviathan owns a consequence row")
        assertTrue(row.headline.contains("1 of 2"), row.headline)
        assertTrue(row.detail.contains("EVERY execution counts"), row.detail)
        assertEquals(1, first.size, "the registry row replaces WP3's built-in, never doubles it")

        // And an evil execution adds no mark — the row says so rather than going
        // quiet, so WP3's (wrong) built-in never leaks back in.
        val evil = Execution.consequences(state, lookup, executed.copy(wasEvilAtExecution = true))
            .filter { it.sourceId == "leviathan" }
        assertEquals(1, evil.size)
        assertTrue(evil.single().headline.contains("no 'Good Player Executed' token"), evil.single().headline)
        assertTrue(evil.single().headline.contains("0 of 2"), evil.single().headline)

        // With one mark on the board the row reads "2 of 2"…
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Good Player Executed"))
        assertTrue(
            Execution.consequences(state, lookup, executed)
                .first { it.sourceId == "leviathan" }
                .headline.contains("2 of 2"),
        )
        assertNull(WinCheck.check(state, lookup), "one is not enough")

        // …and the second mark ends the game.
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Good Player Executed"))
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(WinCheck.RULE_LEVIATHAN_TWO_GOOD, advisory.ruleId)
        assertEquals(false, advisory.goodWins)
    }

    @Test
    fun `the Leviathan announces itself on day one and keeps its ability when dead`() {
        var state = game("leviathan", "chef", "empath", "undertaker", "washerwoman")
        val row = assertNotNull(step(state, "leviathan"))
        assertTrue(row.prompt.contains("THE LEVIATHAN IS IN PLAY"), row.prompt)
        assertEquals(WakeCount.NONE, row.wakeCounts, "nobody is woken")
        assertTrue(row.cards.any { it.card is ShowCardSpec.Message })

        // The win rules do not require the Leviathan to be alive.
        state = state.updatePlayer(0L) { it.copy(alive = false) }
        assertTrue(Status.hasAbility(state, lookup, 0L))
        assertIs<StepGate.Fire>(assertNotNull(step(atNight(state, 3), "leviathan")).gate)
    }

    @Test
    fun `a Leviathan nomination of a protected player carries the jinx warning`() {
        var state = Phases.advancePhase(
            game("leviathan", "soldier", "chef", "empath", "undertaker", "washerwoman"),
            lookup,
        )
        state = Effects.addCentreReminder(state, PlacedReminder("leviathan", "Day 2"))

        val triggers = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 1L)
            .triggers
            .filter { it.sourceId == "leviathan" }
        assertTrue(
            triggers.any { it.headline.contains("GOOD WINS") },
            "the Soldier jinx: ${triggers.map { it.headline }}",
        )

        // Somebody else nominating the Soldier is an ordinary nomination.
        assertTrue(
            DayRules.checkNomination(state, lookup, nominatorId = 2L, nomineeId = 1L)
                .triggers.none { it.sourceId == "leviathan" },
        )
    }

    // =======================================================================
    // The remaining rows: Kazali, Lord of Typhon, Lil' Monsta, Yaggababble
    // =======================================================================

    @Test
    fun `the Kazali creates Minions on the first night and kills on the others`() {
        // Given a legal Kazali bag — one Demon, no Minions,
        var state = game("kazali", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler")
        val row = assertNotNull(step(state, "kazali"))
        val action = assertIs<ChoosePlayerAndCharacter>(row.action)
        assertEquals(CharacterPool.MINION, action.pool)
        assertTrue(action.requireNotInPlay, "each Minion token is unique")
        assertTrue(action.prompt.contains("MINIONS STILL TO CREATE"), action.prompt)

        // When P4 is made the Poisoner,
        state = resolve(
            state,
            row,
            NightInput(playerIds = listOf(3L), characterIds = listOf("poisoner")),
        )

        // Then they are an evil Poisoner whose original character is on the record,
        assertEquals("poisoner", assertNotNull(state.player(3L)).characterId)
        assertTrue(assertNotNull(state.player(3L)).isEvil(lookup))
        assertTrue(state.identityLog.any { it.playerId == 3L && it.fromCharacterId == "undertaker" })

        // and the other nights are the ordinary Demon kill.
        val night2 = assertNotNull(step(atNight(state, 2), "kazali"))
        assertTrue(offersAKill(night2))
    }

    @Test
    fun `a Kazali created mid-game does not choose new Minions`() {
        // Given a Poisoner turned into a Kazali on night 3,
        var state = atNight(
            game("imp", "poisoner", "chef", "empath", "undertaker", "washerwoman"),
            3,
        )
        state = Identity.changeCharacter(
            state = state,
            lookup = lookup,
            playerId = 1L,
            newCharacterId = "kazali",
            reason = ChangeReason.PIT_HAG,
            newEvil = true,
        )

        // Then only the kill runs; the first-night creation is skipped with a reason.
        val first = NightPlan.build(state.copy(cycle = 1), lookup).steps
            .firstOrNull { it.abilityId == "kazali" }
        assertIs<StepGate.Skip>(assertNotNull(first).gate)
        assertTrue(offersAKill(step(state, "kazali")))
    }

    @Test
    fun `the Lord of Typhon converts neighbours on night one and never itself`() {
        var state = game(
            "lordoftyphon", "chef", "empath", "undertaker", "washerwoman", "librarian", "butler",
        )
        val row = assertNotNull(step(state, "lordoftyphon"))
        assertFalse(offersAKill(row), "night 1 is the conversion")
        val action = assertIs<ChoosePlayerAndCharacter>(row.action)
        assertTrue(TargetConstraint.NOT_SELF in action.playerConstraints)
        assertTrue(action.prompt.contains("THE LINE GROWS OUT FROM"), action.prompt)

        state = resolve(
            state,
            row,
            NightInput(playerIds = listOf(1L), characterIds = listOf("baron")),
        )
        assertEquals("baron", assertNotNull(state.player(1L)).characterId)
        assertTrue(assertNotNull(state.player(1L)).isEvil(lookup))
    }

    @Test
    fun `Lil Monsta moves the babysitting token and takes the kill second`() {
        // Given a Lil' Monsta game on night 2 (the token seat stands in for the
        // centre token until NightPlan can emit a seatless group row),
        var state = atNight(
            game("lilmonsta", "poisoner", "baron", "chef", "empath", "undertaker"),
            2,
        )
        val row = assertNotNull(step(state, "lilmonsta"))
        assertEquals(WakeCount.INFORMED, row.wakeCounts, "the Minions wake, not for their own ability")

        // When the Minions hand the token to P2 and the storyteller kills P5,
        state = resolve(state, row, NightInput(playerIds = listOf(1L, 4L)))

        // Then P2 holds the token and P5 is dead.
        assertTrue(tokenOn(state, 1L, "lilmonsta", "Is The Demon"))
        assertFalse(assertNotNull(state.player(4L)).alive)
        assertEquals("lilmonsta", assertNotNull(state.deaths.lastOrNull()).killerCharacterId)

        // When the token moves to P3, exactly one copy is on the board.
        val night3 = atNight(state, 3)
        val next = resolve(
            night3,
            assertNotNull(step(night3, "lilmonsta")),
            NightInput(playerIds = listOf(2L)),
        )
        assertTrue(tokenOn(next, 2L, "lilmonsta", "Is The Demon"))
        assertEquals(
            1,
            next.effects.count {
                Tokens.key(it.sourceCharacterId, it.label) ==
                    Tokens.key("lilmonsta", "Is The Demon")
            },
            "the token moves, it does not accumulate",
        )

        // And the babysitter registers as the Demon while keeping their own team —
        // from the EFFECT the pipeline placed, with no hand-placed token at all
        // (W7B: `Registration` used to read `Player.reminders` only).
        val teams = Registration.registersAs(state, lookup, assertNotNull(state.player(1L)))
        assertTrue(Team.DEMON in teams, "the babysitter IS the Demon")
        assertTrue(Team.MINION in teams, "…and keeps their own character's team")

        // Both spellings mean the same thing: a hand-placed token still counts.
        val physical = Effects.addReminder(state, 3L, PlacedReminder("lilmonsta", "Is The Demon"))
        assertTrue(
            Team.DEMON in Registration.registersAs(physical, lookup, assertNotNull(physical.player(3L))),
        )
    }

    @Test
    fun `a Lil Monsta game with no Demon seat still gets a seatless group row`() {
        // Given a bag with no Demon at all — Lil' Monsta lives in the centre of
        // the grimoire and the Minions babysit it (lead D18 / D59).
        var state = game("poisoner", "baron", "chef", "empath", "undertaker", "washerwoman")
        state = Decisions.set(state, SetupRequirements.LILMONSTA_NO_DEMON_SEAT, "true")
        assertEquals(listOf("lilmonsta"), Setup.seatlessInPlayIds(state))
        assertTrue(
            state.seats.none { it.characterId?.let(lookup)?.team == Team.DEMON },
            "the point of the fixture is that no seat holds the Demon",
        )

        // Then the planner emits ONE row with no holder…
        val first = assertNotNull(step(state, "lilmonsta"), "night 1 must list Lil' Monsta")
        assertNull(first.key.holderId, "a seatless group step has no holder")
        assertIs<StepGate.Fire>(first.gate)
        assertEquals(WakeCount.INFORMED, first.wakeCounts, "the Minions wake, not for their own ability")
        assertTrue(first.prompt.startsWith("Wake every Minion"), "registry prompt: ${first.prompt}")

        // …and running it hands the token out and registers that seat as the Demon.
        val resolved = resolve(state, first, NightInput(playerIds = listOf(0L)))
        assertTrue(tokenOn(resolved, 0L, "lilmonsta", "Is The Demon"))
        assertTrue(
            Team.DEMON in Registration.registersAs(resolved, lookup, assertNotNull(resolved.player(0L))),
        )

        // With the decision unset, nothing seatless is emitted at all.
        val seated = Decisions.clear(state, SetupRequirements.LILMONSTA_NO_DEMON_SEAT)
        assertNull(step(seated, "lilmonsta"))
    }

    @Test
    fun `executing the Lil Monsta babysitter is called out`() {
        var state = Phases.advancePhase(
            atNight(game("lilmonsta", "poisoner", "baron", "chef", "empath", "undertaker"), 2),
            lookup,
        )
        state = Effects.addReminder(state, 1L, PlacedReminder("lilmonsta", "Is The Demon"))
        val rows = Execution.consequences(
            state,
            lookup,
            ExecutionRecord(day = 2, outcome = ExecutionOutcome.DIED, playerId = 1L),
        ).filter { it.sourceId == "lilmonsta" }
        assertTrue(rows.any { it.headline.contains("GOOD WINS") }, rows.map { it.headline }.toString())
        assertTrue(rows.any { it.detail.contains("Scarlet Woman") })
    }

    @Test
    fun `the Yaggababble shows a phrase on night one and kills up to its charges after`() {
        // Given a phrase and nothing said yet,
        var state = game("yaggababble", "chef", "empath", "undertaker", "washerwoman")
        state = Decisions.set(state, "yaggababble.phrase", "at the end of the day")
        val first = assertNotNull(step(state, "yaggababble"))
        assertFalse(offersAKill(first), "there has been no day, so the count is zero")
        assertTrue(
            first.cards.any { (it.card as? ShowCardSpec.Message)?.title == "at the end of the day" },
            "the phrase is a pre-filled card, not dialog-local state",
        )

        // With nothing said, the step is skipped with the reason.
        val quiet = atNight(state, 2)
        assertIs<StepGate.Skip>(assertNotNull(step(quiet, "yaggababble")).gate)

        // Said three times: up to three may die, and fewer is legal.
        var loud = Counters.set(quiet, Counters.YAGGABABBLE_SAID, 3)
        val row = assertNotNull(step(loud, "yaggababble"))
        val action = assertIs<ChoosePlayers>(row.action)
        assertEquals(3, action.max)
        assertEquals(0, action.min)
        assertTrue(action.allowNone)

        loud = resolve(loud, row, NightInput(playerIds = listOf(1L, 2L)))
        assertEquals(2, loud.deaths.size)
        assertTrue(loud.deaths.all { it.killerCharacterId == "yaggababble" })
        // The day's tally is SPENT at the step: it never carries into a second
        // night (lead D72).
        assertEquals(0, Counters.get(loud, Counters.YAGGABABBLE_SAID))

        // Poisoned RIGHT NOW: nobody dies, however often the phrase was said.
        val poisoned = Effects.place(
            state = Counters.set(quiet, Counters.YAGGABABBLE_SAID, 3),
            target = 0L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = null,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        val skip = assertIs<StepGate.Skip>(assertNotNull(step(poisoned, "yaggababble")).gate)
        assertTrue(skip.reason.isNotEmpty())
    }

    @Test
    fun `every public utterance bumps the Yaggababble's counter through its day ability`() {
        var state = game("yaggababble", "chef", "empath", "undertaker", "washerwoman")
        state = Decisions.set(state, "yaggababble.phrase", "at the end of the day")
        state = GameActions.advancePhase(state, lookup) // NIGHT 1 -> DAY 1

        val offer = DayAbilities.forState(state, lookup).first { it.sourceId == "yaggababble" }
        assertEquals(Counters.YAGGABABBLE_SAID, offer.ability.counterKey)
        assertTrue(offer.available)

        repeat(2) { state = DayAbilities.use(state, lookup, "yaggababble") }
        assertEquals(2, Counters.get(state, Counters.YAGGABABBLE_SAID))
        assertEquals(
            2,
            state.ledger.count { it.sourceId == "yaggababble" && it.kind == LedgerKind.STATEMENT },
        )

        // A dead Yaggababble says nothing that counts.
        val dead = GameActions.kill(state, 0L, DeathCause.EXECUTION, lookup)
        assertFalse(DayAbilities.forState(dead, lookup).first { it.sourceId == "yaggababble" }.available)
        assertEquals(
            2,
            Counters.get(DayAbilities.use(dead, lookup, "yaggababble"), Counters.YAGGABABBLE_SAID),
        )

        // The night step reads the tally back: two utterances, up to two victims.
        state = GameActions.advancePhase(state, lookup) // DAY 1 -> NIGHT 2
        val action = assertIs<ChoosePlayers>(assertNotNull(step(state, "yaggababble")).action)
        assertEquals(2, action.max)
    }
}
