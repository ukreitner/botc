package com.clocktower.engine

import com.clocktower.engine.rules.puzzlemasterGuessIsCorrect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-EXP-O — the eleven experimental Outsiders, exercised through the real
 * pipeline (`NightPlan.build` / `NightPlan.resolve`, `Deaths.attempt`,
 * `DayRules.checkNomination`, `Execution.execute`, `WinCheck`), never by
 * poking the registry rows directly.
 *
 * At least one Given/When/Then per P0 in `docs/audit/digest/exp-outsiders.md`.
 */
class RulesExpOutsidersTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    /** Every character this package owns, straight out of the official data. */
    private val scope: List<Character> =
        data.characters.filter { it.edition == "exp" && it.team == Team.OUTSIDER }

    // ---- fixtures ---------------------------------------------------------

    /** A script holding [ids] plus whatever the test's seats need. */
    private fun script(vararg ids: String): Script = Script(
        id = "wp7expo",
        name = "WP7-EXP-O fixture",
        characterIds = ids.map(Character::normalizeId),
    )

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        val s = script(*roles)
        var state = GameActions.newGame(s, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state, lookup)
    }

    private fun atNight(state: GameState, n: Int): GameState =
        state.copy(cycle = n, phase = Phase.NIGHT)

    private fun atDay(state: GameState, n: Int): GameState =
        state.copy(cycle = n, phase = Phase.DAY)

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String, holderId: Long? = null): NightStep? =
        plan(state).steps.firstOrNull {
            it.abilityId == abilityId && (holderId == null || it.holderId == holderId)
        }

    private fun tokensOn(state: GameState, playerId: Long): List<String> =
        Status.effectsOn(state, lookup, playerId)
            .filter { it.label.isNotEmpty() }
            .map { Tokens.key(it.sourceCharacterId, it.label) } +
            state.player(playerId)!!.reminders.map { Tokens.key(it) }

    private fun hasToken(state: GameState, playerId: Long, sourceId: String, label: String): Boolean =
        Tokens.key(sourceId, label) in tokensOn(state, playerId)

    // ==================================================================
    // Coverage + the token table (acceptance criteria for every WP7 package)
    // ==================================================================

    @Test
    fun `every experimental Outsider has a registry row`() {
        assertEquals(11, scope.size, "the data must hold exactly 11 experimental Outsiders")
        val missing = scope.map { it.id }.filter { CharacterRules.all[it] == null }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
    }

    @Test
    fun `every token this package declares is an official reminder with the official copy count`() {
        val wrong = mutableListOf<String>()
        for (character in scope) {
            val rule = CharacterRules.all.getValue(character.id)
            for (token in rule.tokens) {
                val copies = character.allReminders.count { it.trim().equals(token.label, true) }
                if (copies == 0) wrong += "${token.sourceId}/${token.label} is not in characters.json"
                if (copies != token.copies) {
                    wrong += "${token.sourceId}/${token.label}: rule says ${token.copies}, data has $copies"
                }
                if (Character.normalizeId(token.sourceId) != character.id) {
                    wrong += "${token.sourceId}/${token.label} is filed under ${character.id}"
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        // And every reminder the official data lists for these characters has a rule.
        val uncovered = scope.flatMap { c -> c.allReminders.map { Tokens.key(c.id, it) } }
            .distinct()
            .filter { key -> Tokens.all.none { Tokens.key(it.sourceId, it.label) == key } }
        assertTrue(uncovered.isEmpty(), "official reminders with no TokenRule: $uncovered")
    }

    @Test
    fun `the registry wins over WP1's keeps-ability-when-dead stopgap`() {
        // Works while dead, verbatim from the cards.
        for (id in listOf("hatter", "heretic", "plaguedoctor", "politician", "puzzlemaster", "zealot")) {
            assertTrue(CharacterRules.all.getValue(id).keepsAbilityWhenDead, "$id keeps its ability")
        }
        // "A dead Damsel is safe" — and a dead Hermit keeps only the borrowed
        // abilities that carry the flag themselves.
        for (id in listOf("damsel", "hermit", "golem", "ogre")) {
            assertFalse(CharacterRules.all.getValue(id).keepsAbilityWhenDead, "$id loses its ability")
        }
    }

    // ==================================================================
    // Damsel — P0: the Minion hand-out, the Huntsman transformation, the guess
    // ==================================================================

    @Test
    fun `given a Damsel and Minions, the first night owes the Minion token until it is acked`() {
        val state = game("imp", "poisoner", "baron", "damsel", "chef", "empath", "mayor", "monk")
        val row = assertNotNull(step(state, "damsel"), "the Damsel has a first-night row")
        assertEquals(StepGate.Fire, row.gate)
        assertTrue(row.prompt.contains("DAMSEL token"), "the row says what to show: ${row.prompt}")
        assertEquals(WakeCount.INFORMED, row.wakeCounts, "this wake is not the Damsel's own ability")

        // When the storyteller ticks the setup requirement off...
        val acked = Decisions.set(state, SetupRequirements.DAMSEL_MINIONS, "true")
        val after = assertNotNull(step(acked, "damsel"))
        assertTrue(after.gate is StepGate.Skip, "already shown -> skipped, with a reason")
    }

    @Test
    fun `given a Damsel with no Huntsman choice tonight, the other-night row is skipped`() {
        val state = atNight(
            game("imp", "poisoner", "damsel", "huntsman", "chef", "empath", "mayor", "monk"),
            3,
        )
        val row = assertNotNull(step(state, "damsel"), "the Damsel sits on the other-night list")
        assertTrue(row.gate is StepGate.Skip, "no transformation is pending")
        assertTrue((row.gate as StepGate.Skip).reason.contains("Huntsman"))
    }

    @Test
    fun `given the Huntsman chose the Damsel tonight, the Damsel becomes the chosen Townsfolk`() {
        var state = atNight(
            game("imp", "poisoner", "damsel", "huntsman", "chef", "empath", "mayor", "monk"),
            3,
        )
        val damselSeat = 2L
        // The Huntsman's own step records a CHOICE naming the Damsel.
        state = Ledger.choice(state, sourceId = "huntsman", actorId = 3L, targetIds = listOf(damselSeat))

        val row = assertNotNull(step(state, "damsel", damselSeat))
        assertEquals(StepGate.Fire, row.gate, "the transformation is pending")
        val action = assertNotNull(row.action as? ChooseCharacter)
        assertEquals(CharacterPool.TOWNSFOLK, action.pool)

        state = NightPlan.resolve(state, lookup, row.key, NightInput(characterIds = listOf("undertaker")))
        assertEquals("undertaker", state.player(damselSeat)?.characterId)
        assertFalse(state.player(damselSeat)!!.isEvil(lookup), "the new character is good")
        assertTrue(
            state.identityLog.any {
                it.playerId == damselSeat && it.reason == ChangeReason.HUNTSMAN_DAMSEL
            },
            "the change is recorded as the Huntsman's",
        )
    }

    @Test
    fun `given an impaired Huntsman, the Damsel does not change`() {
        var state = atNight(
            game("imp", "poisoner", "damsel", "huntsman", "chef", "empath", "mayor", "monk"),
            3,
        )
        state = Ledger.choice(
            state,
            sourceId = "huntsman",
            actorId = 3L,
            targetIds = listOf(2L),
            impaired = true,
        )
        val row = assertNotNull(step(state, "damsel", 2L))
        assertTrue(row.gate is StepGate.Skip)
        assertTrue((row.gate as StepGate.Skip).reason.contains("spent"), row.gate.toString())
    }

    @Test
    fun `given a dead or spent Damsel, the guess ability is unavailable`() {
        val state = game("imp", "poisoner", "damsel", "chef", "empath", "mayor", "monk", "soldier")
        val damsel = state.player(2L)!!
        val ability = assertNotNull(CharacterRules.all.getValue("damsel").day?.ability)
        assertTrue(ability.oncePerGame)
        assertTrue(ability.available(state, lookup, damsel), "an alive sober Damsel may be guessed")

        val spent = Effects.place(
            state = state,
            target = damsel.id,
            kind = EffectKind.SPENT,
            sourceCharacterId = "damsel",
            sourcePlayerId = damsel.id,
            until = Until.FOREVER,
            label = "Guess Used",
        ).state
        assertFalse(ability.available(spent, lookup, spent.player(2L)!!), "one guess for the whole team")

        val dead = Deaths.attempt(state, lookup, damsel.id, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertFalse(ability.available(dead, lookup, dead.player(2L)!!), "a dead Damsel is safe")
    }

    // ==================================================================
    // Golem — P0: the once-per-game lock, the Demon branch, the kill
    // ==================================================================

    @Test
    fun `given a Golem nominating a non-Demon, the nominee dies by DAY_ABILITY and the lock lands`() {
        var state = atDay(
            game("imp", "poisoner", "golem", "chef", "empath", "mayor", "monk", "soldier"),
            1,
        )
        val golem = 2L
        val victim = 3L
        val check = DayRules.checkNomination(state, lookup, golem, victim)
        val trigger = assertNotNull(
            check.triggers.firstOrNull { Character.normalizeId(it.sourceId) == "golem" },
            "the Golem's nomination raises a trigger",
        )
        assertEquals(TriggerKind.AUTO_DEATH, trigger.kind)

        state = DayRules.applyTrigger(state, lookup, trigger, DayRules.OPTION_APPLY)
        assertFalse(state.player(victim)!!.alive, "the nominee dies")
        assertEquals(
            DeathCause.DAY_ABILITY,
            state.deaths.last { it.playerId == victim }.cause,
            "not a Demon kill — Monk and Soldier never apply",
        )
        assertTrue(hasToken(state, golem, "golem", "May Not Nominate"))
        assertFalse(DayRules.executionSpent(state), "an ability death is not the day's execution")

        // ...and the lock survives into the next day.
        val nextDay = atDay(state, 2)
        assertFalse(DayRules.canNominate(nextDay, lookup, golem).allowed, "once per GAME, not per day")
    }

    @Test
    fun `given a Golem nominating the Demon, nothing happens`() {
        val state = atDay(
            game("imp", "poisoner", "golem", "chef", "empath", "mayor", "monk", "soldier"),
            1,
        )
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 2L, 0L)
                .triggers.firstOrNull { Character.normalizeId(it.sourceId) == "golem" },
        )
        assertEquals(TriggerKind.WARN, trigger.kind, "the Demon does not die")
        assertTrue(trigger.headline.contains("Demon"), trigger.headline)
    }

    @Test
    fun `the Golem never wakes`() {
        val state = game("imp", "poisoner", "golem", "chef", "empath", "mayor", "monk", "soldier")
        assertNull(step(state, "golem"), "no first-night row")
        assertNull(step(atNight(state, 2), "golem"), "no other-night row")
    }

    // ==================================================================
    // Hatter — P0: the death token, the conditional row, the tea party
    // ==================================================================

    @Test
    fun `given an alive Hatter, there is no tea party row`() {
        val state = atNight(
            game("imp", "poisoner", "baron", "hatter", "chef", "empath", "mayor", "monk"),
            2,
        )
        val row = assertNotNull(step(state, "hatter"), "the Hatter is on the other-night list")
        assertTrue(row.gate is StepGate.Skip)
        assertTrue((row.gate as StepGate.Skip).reason.contains("has not died"), row.gate.toString())
    }

    @Test
    fun `given a Hatter executed on day 2, the token lands at once and the tea party runs that night`() {
        var state = atDay(
            game("imp", "poisoner", "baron", "hatter", "chef", "empath", "mayor", "monk"),
            2,
        )
        val hatter = 3L
        state = Execution.execute(state, lookup, hatter, force = true)
        assertFalse(state.player(hatter)!!.alive)
        assertTrue(hasToken(state, hatter, "hatter", "Tea Party Tonight"), "the token is placed on death")
        assertTrue(
            state.prompts.any { it.sourceId == "hatter" && it.at == BriefingSlot.DUSK },
            "and a dusk line is queued",
        )

        val night = atNight(state, 3)
        val row = assertNotNull(step(night, "hatter", hatter))
        assertEquals(StepGate.Fire, row.gate, "the row fires BECAUSE they are dead")
        assertTrue(row.badges.any { it.contains("dead") }, "badged positively: ${row.badges}")
        val action = assertNotNull(row.action as? ChoosePlayerAndCharacter)
        assertTrue(TargetConstraint.EVIL in action.playerConstraints)
        assertTrue(action.requireNotInPlay)

        // The Baron takes a new Minion character.
        val after = NightPlan.resolve(
            night,
            lookup,
            row.key,
            NightInput(playerIds = listOf(2L), characterIds = listOf("scarletwoman")),
        )
        assertEquals("scarletwoman", after.player(2L)?.characterId)
        assertTrue(after.player(2L)!!.isEvil(lookup), "they stay evil")
    }

    @Test
    fun `given a Hatter who died drunk or poisoned, nobody changes`() {
        var state = atDay(
            game("imp", "poisoner", "baron", "hatter", "chef", "empath", "mayor", "monk"),
            2,
        )
        val hatter = 3L
        state = Effects.place(
            state = state,
            target = hatter,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.DUSK,
            label = "Poisoned",
        ).state
        state = Execution.execute(state, lookup, hatter, force = true)
        assertFalse(
            hasToken(state, hatter, "hatter", "Tea Party Tonight"),
            "an impaired Hatter throws no tea party",
        )
        assertTrue(
            state.prompts.any { it.sourceId == "hatter" && it.title.contains("NOT change") },
            "and the storyteller is told why",
        )
        val row = assertNotNull(step(atNight(state, 3), "hatter", hatter))
        assertTrue(row.gate is StepGate.Skip)
    }

    @Test
    fun `given Legion in play, the Hatter has no ability`() {
        var state = atDay(
            game("legion", "poisoner", "baron", "hatter", "chef", "empath", "mayor", "monk"),
            2,
        )
        state = Execution.execute(state, lookup, 3L, force = true)
        val row = assertNotNull(step(atNight(state, 3), "hatter", 3L))
        assertTrue(row.gate is StepGate.Skip)
        assertTrue((row.gate as StepGate.Skip).reason.contains("Legion"), row.gate.toString())
    }

    // ==================================================================
    // Heretic — P0: the inversion, and the impaired exception
    // ==================================================================

    @Test
    fun `given a Heretic in play, every Demon dead is an EVIL win`() {
        var state = atDay(game("imp", "poisoner", "heretic", "chef", "empath", "mayor"), 2)
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(false, advisory.goodWins, "the Heretic reverses it")
        assertTrue(advisory.reason.contains("Heretic"), advisory.reason)
    }

    @Test
    fun `given a DEAD Heretic, the reversal still applies`() {
        var state = atDay(game("imp", "poisoner", "heretic", "chef", "empath", "mayor"), 2)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(false, advisory.goodWins, "'even if you are dead'")
    }

    @Test
    fun `given a POISONED Heretic, the result is not reversed`() {
        var state = atDay(game("imp", "poisoner", "heretic", "chef", "empath", "mayor"), 2)
        state = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.FOREVER,
            label = "Poisoned",
        ).state
        state = Deaths.attempt(state, lookup, 0L, KillCause(DeathCause.EXECUTION)).state
        val advisory = assertNotNull(WinCheck.check(state, lookup))
        assertEquals(true, advisory.goodWins, "a poisoned Heretic reverses nothing")
    }

    // ==================================================================
    // Hermit — P0: the borrowed rows, at the borrowed positions
    // ==================================================================

    @Test
    fun `given a script with a Hermit and a Butler, the Hermit acts at the Butler's position`() {
        val state = game("imp", "poisoner", "hermit", "butler", "chef", "empath", "mayor", "monk")
        val rows = plan(state).steps.filter { it.holderId == 2L }
        val butler = assertNotNull(
            rows.firstOrNull { it.abilityId == "butler" },
            "the Hermit gets a Butler row: ${rows.map { it.abilityId }}",
        )
        assertEquals("hermit", butler.sourceId, "the row says where the ability came from")
        assertEquals("butler", butler.slotId, "at the BUTLER's night position")
        assertTrue(butler.title.contains("via the Hermit"), butler.title)
    }

    @Test
    fun `given a script with a Hermit and an Ogre, the Hermit chooses a friend on night 1 only`() {
        var state = game("imp", "poisoner", "hermit", "ogre", "chef", "empath", "mayor", "monk")
        val hermit = 2L
        val row = assertNotNull(step(state, "ogre", hermit), "a first-night Ogre row for the Hermit")
        assertEquals(StepGate.Fire, row.gate)

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(1L)))
        assertTrue(hasToken(state, 1L, "ogre", "Friend"), "the Friend token lands on the chosen seat")

        val again = assertNotNull(step(state, "ogre", hermit))
        assertTrue(again.gate is StepGate.Skip, "the Ogre ability is a once-only choice")
    }

    @Test
    fun `the Hermit itself never occupies a night row`() {
        val state = game("imp", "poisoner", "hermit", "butler", "chef", "empath", "mayor", "monk")
        assertNull(step(state, "hermit"), "no `hermit` row on the first night")
        assertNull(step(atNight(state, 2), "hermit"), "nor on later nights")
    }

    // ==================================================================
    // Ogre — P0: the friend choice and the alignment consequence
    // ==================================================================

    @Test
    fun `given an Ogre on night 1, they choose an alive player who is not themself`() {
        val state = game("imp", "poisoner", "ogre", "chef", "empath", "mayor", "monk", "soldier")
        val row = assertNotNull(step(state, "ogre", 2L))
        assertEquals(StepGate.Fire, row.gate)
        val action = assertNotNull(row.action as? ChoosePlayers)
        assertTrue(TargetConstraint.ALIVE in action.constraints)
        assertTrue(TargetConstraint.NOT_SELF in action.constraints)
        assertFalse(action.allowNone, "'choose a player' is not optional")
    }

    @Test
    fun `given an Ogre who chose an evil friend, the token lands and the alignment is asked`() {
        var state = game("imp", "poisoner", "ogre", "chef", "empath", "mayor", "monk", "soldier")
        val ogre = 2L
        val poisoner = 1L
        val row = assertNotNull(step(state, "ogre", ogre))
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(poisoner)))

        assertTrue(hasToken(state, poisoner, "ogre", "Friend"))
        assertEquals(
            Team.OUTSIDER,
            state.player(ogre)!!.team(lookup),
            "the team never changes — only the alignment can",
        )
        // W7E: the flip is REAL. `NightEffect.When(REGISTERS_EVIL)` branches on the
        // seat they just pointed at and `SetAlignment` writes the side, without
        // inventing a character change.
        assertTrue(state.player(ogre)!!.isEvil(lookup), "an evil friend makes an evil Ogre")
        assertEquals(Alignment.EVIL, state.player(ogre)!!.alignment)
        assertTrue(state.identityLog.none { it.playerId == ogre })
        val prompt = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "ogre" && it.kind == PromptKind.DECIDE },
            "the storyteller may still overrule a misregistration: ${state.prompts}",
        )
        assertEquals(ogre, prompt.subjectPlayerId)
        // The choice is the memory the gate reads — it survives every token sweep.
        assertTrue(Memory.by(state, LedgerKind.CHOICE, "ogre", ogre).isNotEmpty())
        assertTrue(step(state, "ogre", ogre)!!.gate is StepGate.Skip, "only on their 1st night")
    }

    @Test
    fun `given a POISONED Ogre, the step still resolves`() {
        var state = game("imp", "poisoner", "ogre", "chef", "empath", "mayor", "monk", "soldier")
        state = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.FOREVER,
            label = "Poisoned",
        ).state
        val row = assertNotNull(step(state, "ogre", 2L))
        assertEquals(StepGate.Fire, row.gate, "'even if drunk or poisoned' is explicit")
    }

    // ==================================================================
    // Plague Doctor — P0: the death, the impaired exception, the safety net
    // ==================================================================

    @Test
    fun `given an alive Plague Doctor, there is no storyteller-ability row`() {
        val state = atNight(
            game("imp", "poisoner", "plaguedoctor", "chef", "empath", "mayor", "monk", "soldier"),
            2,
        )
        val row = assertNotNull(step(state, "plaguedoctor"))
        assertTrue(row.gate is StepGate.Skip)
        assertTrue((row.gate as StepGate.Skip).reason.contains("alive"), row.gate.toString())
    }

    @Test
    fun `given a sober Plague Doctor executed on day 1, the ability is gained at once`() {
        var state = atDay(
            game("imp", "poisoner", "plaguedoctor", "chef", "empath", "mayor", "monk", "soldier"),
            1,
        )
        val pd = 2L
        state = Execution.execute(state, lookup, pd, force = true)
        val prompt = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "plaguedoctor" },
            "gained at the INSTANT of death, not at the following night",
        )
        assertEquals(PromptKind.CHOOSE_CHARACTER, prompt.kind)
        assertEquals(BriefingSlot.NOW, prompt.at)

        val night = atNight(state, 2)
        val row = assertNotNull(step(night, "plaguedoctor", pd))
        assertEquals(StepGate.Fire, row.gate, "the safety net is armed")
        val action = assertNotNull(row.action as? ChooseCharacter)
        assertEquals(CharacterPool.MINION, action.pool)

        val after = NightPlan.resolve(night, lookup, row.key, NightInput(characterIds = listOf("poisoner")))
        assertTrue(hasToken(after, pd, "plaguedoctor", "Storyteller Ability"))
        // W7E: the grant is REAL — a `FloatingGrant` held by the storyteller, so
        // the gained ability wakes at that character's own night position.
        val floating = after.floatingGrants.single()
        assertEquals("poisoner", floating.abilityId)
        assertEquals("plaguedoctor", floating.sourceId)
        assertEquals(GrantHolder.STORYTELLER, floating.holder)
        assertTrue(step(after, "plaguedoctor", pd)!!.gate is StepGate.Skip, "taken once only")
    }

    @Test
    fun `given a good Ogre friend, the Ogre stays good and it is still recorded`() {
        var state = game("imp", "poisoner", "ogre", "chef", "empath", "mayor", "monk", "soldier")
        val ogre = 2L
        val row = assertNotNull(step(state, "ogre", ogre))
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(3L)))

        assertFalse(state.player(ogre)!!.isEvil(lookup), "a good friend leaves them good")
        assertEquals(Alignment.GOOD, state.player(ogre)!!.alignment)
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.RULING && it.actorId == ogre },
            "the ruling is on the record either way",
        )
    }

    @Test
    fun `given a Plague Doctor who died poisoned, no ability is gained and no row is offered`() {
        var state = atDay(
            game("imp", "poisoner", "plaguedoctor", "chef", "empath", "mayor", "monk", "soldier"),
            1,
        )
        val pd = 2L
        state = Effects.place(
            state = state,
            target = pd,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.FOREVER,
            label = "Poisoned",
        ).state
        state = Execution.execute(state, lookup, pd, force = true)
        val prompt = assertNotNull(state.prompts.firstOrNull { it.sourceId == "plaguedoctor" })
        assertEquals(PromptKind.ANNOUNCE, prompt.kind)
        assertTrue(prompt.title.contains("NO Minion ability"), prompt.title)
        val row = assertNotNull(step(atNight(state, 2), "plaguedoctor", pd))
        assertTrue(row.gate is StepGate.Skip)
    }

    // ==================================================================
    // Politician — P0: the blocking end-game question
    // ==================================================================

    @Test
    fun `given a Politician seat, the end game cannot be declared without answering their question`() {
        val state = atDay(game("imp", "poisoner", "politician", "chef", "empath", "mayor"), 2)
        val question = assertNotNull(
            WinCheck.endGameQuestions(state, lookup).firstOrNull { it.sourceId == "politician" },
        )
        assertTrue(question.question.contains("most responsible"), question.question)
        assertTrue(question.options.any { it.id == "yes" })
    }

    @Test
    fun `a dead Politician still gets the question`() {
        var state = atDay(game("imp", "poisoner", "politician", "chef", "empath", "mayor"), 2)
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertTrue(
            WinCheck.endGameQuestions(state, lookup).any { it.sourceId == "politician" },
            "'even if dead'",
        )
        assertTrue(CharacterRules.all.getValue("politician").keepsAbilityWhenDead)
    }

    // ==================================================================
    // Puzzlemaster — P0: the setup token, its survival, the guess trap
    // ==================================================================

    @Test
    fun `given a Puzzlemaster with no Drunk token, setup is blocked until exactly one is placed`() {
        var state = game("imp", "poisoner", "puzzlemaster", "chef", "empath", "mayor", "monk", "soldier")
        assertTrue(
            SetupRequirements.blockingProblems(state, lookup).any { it.contains("Puzzlemaster") },
            "a whole game of true info is the failure this prevents",
        )
        state = Effects.placeExclusiveReminder(state, 3L, PlacedReminder("puzzlemaster", "Drunk"))
        assertFalse(SetupRequirements.blockingProblems(state, lookup).any { it.contains("Puzzlemaster") })
        assertTrue(Status.isImpaired(state, lookup, 3L), "the marked seat is drunk")
        assertFalse(Status.isImpaired(state, lookup, 4L), "and nobody else is")
    }

    @Test
    fun `the Puzzlemaster's drunk survives their death and every sweep`() {
        var state = game("imp", "poisoner", "puzzlemaster", "chef", "empath", "mayor", "monk", "soldier")
        state = Effects.placeExclusiveReminder(state, 3L, PlacedReminder("puzzlemaster", "Drunk"))
        state = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertTrue(Status.isImpaired(state, lookup, 3L), "'1 player is drunk, even if you die'")

        state = Phases.advancePhase(state, lookup) // -> DAY 1
        state = Phases.advancePhase(state, lookup) // -> NIGHT 2
        assertTrue(hasToken(state, 3L, "puzzlemaster", "Drunk"), "never swept")
        assertTrue(Status.isImpaired(state, lookup, 3L))
    }

    @Test
    fun `a guess at a Sailor-drunk seat is WRONG and a guess at the marked seat is RIGHT`() {
        var state = game("imp", "poisoner", "puzzlemaster", "sailor", "empath", "mayor", "monk", "soldier")
        val marked = 4L
        val sailorDrunk = 5L
        state = Effects.placeExclusiveReminder(state, marked, PlacedReminder("puzzlemaster", "Drunk"))
        state = Effects.place(
            state = state,
            target = sailorDrunk,
            kind = EffectKind.DRUNK,
            sourceCharacterId = "sailor",
            sourcePlayerId = 3L,
            until = Until.DUSK,
            label = "Drunk",
        ).state
        // Both seats are impaired; only one is the Puzzlemaster's.
        assertTrue(Status.isImpaired(state, lookup, sailorDrunk))
        assertTrue(Status.isImpaired(state, lookup, marked))
        assertTrue(puzzlemasterGuessIsCorrect(state, marked), "the marked seat is the answer")
        assertFalse(puzzlemasterGuessIsCorrect(state, sailorDrunk), "a Sailor-drunk seat is a WRONG guess")
    }

    @Test
    fun `a dead Puzzlemaster cannot guess, and neither can a spent one`() {
        val state = game("imp", "poisoner", "puzzlemaster", "chef", "empath", "mayor", "monk", "soldier")
        val ability = assertNotNull(CharacterRules.all.getValue("puzzlemaster").day?.ability)
        assertTrue(ability.available(state, lookup, state.player(2L)!!))

        val dead = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertFalse(ability.available(dead, lookup, dead.player(2L)!!), "a dead Puzzlemaster cannot guess")

        val spent = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.SPENT,
            sourceCharacterId = "puzzlemaster",
            sourcePlayerId = 2L,
            until = Until.FOREVER,
            label = "Guess Used",
        ).state
        assertFalse(ability.available(spent, lookup, spent.player(2L)!!))
    }

    // ==================================================================
    // Snitch — P0: one independent 3-set per Minion, never the Marionette
    // ==================================================================

    @Test
    fun `given a Snitch and two Minions, each Minion gets their own bluff row`() {
        val state = game(
            "imp", "poisoner", "baron", "snitch", "chef", "empath", "mayor", "monk", "soldier", "virgin",
        )
        val requirements = Bluffs.requirements(state, lookup).filter { it.sourceId == "snitch" }
        assertEquals(setOf(1L, 2L), requirements.mapNotNull { it.recipientId }.toSet())
        assertTrue(requirements.all { it.size == 3 })

        val rows = plan(state).steps.filter { it.slotId == NightMarkers.MINION_BLUFFS }
        assertEquals(
            setOf(1L, 2L),
            rows.flatMap { it.holderIds }.toSet(),
            "the rows name the MINIONS, not the Snitch",
        )
        val snitchRow = assertNotNull(step(state, "snitch"))
        assertEquals(StepGate.Fire, snitchRow.gate)
        assertEquals(WakeCount.INFORMED, snitchRow.wakeCounts)
    }

    @Test
    fun `given a Snitch and a Marionette, the Marionette gets nothing and the Demon keeps three`() {
        val state = game(
            "imp", "marionette", "baron", "snitch", "chef", "empath", "mayor", "monk", "soldier", "virgin",
        )
        val requirements = Bluffs.requirements(state, lookup)
        val snitchSets = requirements.filter { it.sourceId == "snitch" }
        assertFalse(1L in snitchSets.mapNotNull { it.recipientId }, "the Marionette is never woken")
        assertEquals(listOf(2L), snitchSets.mapNotNull { it.recipientId })
        val demon = assertNotNull(requirements.firstOrNull { it.key == BluffRequirement.DEMON_KEY })
        assertEquals(3, demon.size, "the retired Snitch x Marionette '+3' jinx must stay retired")
    }

    @Test
    fun `given a Snitch with no Minion, the row is skipped`() {
        val state = game("imp", "snitch", "chef", "empath", "mayor", "monk", "soldier", "virgin")
        val row = assertNotNull(step(state, "snitch"))
        assertTrue(row.gate is StepGate.Skip)
        assertTrue((row.gate as StepGate.Skip).reason.contains("no Minion"), row.gate.toString())
    }

    // ==================================================================
    // The night-order rows WP6C added (FOLLOWUPS: the official sheet has none)
    // ==================================================================

    @Test
    fun `the mid-game Ogre Snitch and night-one Plague Doctor rows now render`() {
        // Before WP6C these three registry rows were unreachable: the official
        // nightsheet has no other-night Ogre or Snitch and no first-night Plague
        // Doctor, and `NightPlan.build` only emits what the order lists.
        val ogre = game("imp", "poisoner", "ogre", "chef", "empath", "mayor", "monk", "soldier")
        assertNotNull(step(atNight(ogre, 3), "ogre", 2L), "an other-night Ogre row")

        val snitch = game(
            "imp", "poisoner", "baron", "snitch", "chef", "empath", "mayor", "monk", "soldier", "virgin",
        )
        assertNotNull(step(atNight(snitch, 3), "snitch"), "an other-night Snitch row")

        val pd = game("imp", "poisoner", "plaguedoctor", "chef", "empath", "mayor", "monk", "soldier")
        val row = assertNotNull(step(pd, "plaguedoctor", 2L), "a first-night Plague Doctor row")
        // …and it is silent in an ordinary game: nobody is dead on night 1.
        assertTrue(row.gate is StepGate.Skip, "nothing to take yet: ${row.gate}")
    }

    // ==================================================================
    // Zealot — the vote obligation, surfaced at nomination time
    // ==================================================================

    @Test
    fun `given five or more alive, the Zealot must vote on every nomination`() {
        val state = atDay(game("imp", "poisoner", "zealot", "chef", "empath", "mayor"), 1)
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 3L, 4L)
                .triggers.firstOrNull { Character.normalizeId(it.sourceId) == "zealot" },
        )
        assertEquals(TriggerKind.WARN, trigger.kind)
        assertEquals(2L, trigger.actorId)
        assertTrue(trigger.headline.contains("must vote"), trigger.headline)
        assertTrue(DayRules.mustVote(state, lookup).contains(2L))
    }

    @Test
    fun `given fewer than five alive, the Zealot is free`() {
        var state = atDay(game("imp", "poisoner", "zealot", "chef", "empath", "mayor"), 2)
        state = Deaths.attempt(state, lookup, 5L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        state = Deaths.attempt(state, lookup, 4L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertEquals(4, state.aliveCountWithTravellers)
        assertTrue(
            DayRules.checkNomination(state, lookup, 3L, 0L)
                .triggers.none { Character.normalizeId(it.sourceId) == "zealot" },
        )
    }

    @Test
    fun `a dead Zealot has no obligation and an exile call is exempt`() {
        var state = atDay(game("imp", "poisoner", "zealot", "chef", "empath", "mayor"), 2)
        val dead = Deaths.attempt(state, lookup, 2L, KillCause(DeathCause.DEMON_KILL, "imp", 0L)).state
        assertTrue(
            DayRules.checkNomination(dead, lookup, 3L, 4L)
                .triggers.none { Character.normalizeId(it.sourceId) == "zealot" },
            "an ordinary unconstrained ghost vote",
        )

        state = GameActions.addSeat(state, "Trav")
        val travellerId = state.players.last().id
        state = GameActions.assignCharacter(state, travellerId, "scapegoat", isTraveller = true)
        assertTrue(
            DayRules.checkNomination(state, lookup, 3L, travellerId)
                .triggers.none { Character.normalizeId(it.sourceId) == "zealot" },
            "exiles are exempt",
        )
    }

    @Test
    fun `a poisoned Zealot is still obliged, with a footnote`() {
        var state = atDay(game("imp", "poisoner", "zealot", "chef", "empath", "mayor"), 1)
        state = Effects.place(
            state = state,
            target = 2L,
            kind = EffectKind.POISONED,
            sourceCharacterId = "poisoner",
            sourcePlayerId = 1L,
            until = Until.FOREVER,
            label = "Poisoned",
        ).state
        val trigger = assertNotNull(
            DayRules.checkNomination(state, lookup, 3L, 4L)
                .triggers.firstOrNull { Character.normalizeId(it.sourceId) == "zealot" },
            "they do not know their ability is off — expect the hand anyway",
        )
        assertTrue(trigger.impaired)
        assertTrue(trigger.detail.contains("do not know"), trigger.detail)
    }
}
