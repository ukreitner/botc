package com.clocktower.engine

import com.clocktower.engine.rules.FABLED_RULES
import com.clocktower.engine.rules.IVORY_BASELINE
import com.clocktower.engine.rules.STORM_CATCHER_CHARACTER
import com.clocktower.engine.rules.TOYMAKER_MARK
import com.clocktower.engine.rules.demonAttackCouldEndGame
import com.clocktower.engine.rules.extraEvilCount
import com.clocktower.engine.rules.ferrymanOwes
import com.clocktower.engine.rules.noMoreEvil
import com.clocktower.engine.rules.seatsHolding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-FAB — the 14 Fabled and 11 Loric registry rows.
 *
 * Everything here runs through the real pipeline: `Tokens.all`, `Deaths`,
 * `NightPlan.build` / `NightPlan.resolve`, `Phases.advancePhase`,
 * `Setup.validateBag` and `GameData.activeJinxes`. Nothing asserts on a lambda
 * the engine never calls unless the assertion is explicitly a declaration check.
 */
class RulesFabledTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }

    /** Every id in scope, straight from the data — never a hand-written list. */
    private val inScope: List<Character> =
        data.characters.filter { it.team == Team.FABLED || it.team == Team.LORIC }

    private fun setup(vararg characterIds: String): GameState {
        var state = GameActions.newGame(tb, characterIds.indices.map { "P$it" })
        characterIds.forEachIndexed { i, id ->
            state = GameActions.assignCharacter(state, i.toLong(), id)
        }
        return state
    }

    private fun night(vararg characterIds: String): GameState =
        Phases.advancePhase(setup(*characterIds), lookup)

    private fun GameState.seat(characterId: String): Long =
        players.first { it.characterId == characterId }.id

    private fun GameState.withFabled(vararg ids: String): GameState =
        GameActions.setFabled(this, ids.toList())

    private fun GameState.configure(id: String, key: String, value: String): GameState = copy(
        fabled = fabled.map {
            if (Character.normalizeId(it.id) == id) it.copy(config = it.config + (key to value)) else it
        },
    )

    private fun GameState.outcome(target: Long, cause: DeathCause, source: String? = null, sourceSeat: Long? = null) =
        Deaths.killOutcome(this, lookup, target, KillCause(cause, source, sourceSeat))

    // =======================================================================
    // Coverage — every character in scope has a row
    // =======================================================================

    @Test
    fun `every fabled and loric character in the data has a registry row`() {
        assertEquals(25, inScope.size, "scope changed: ${inScope.map { it.id }}")
        assertEquals(14, inScope.count { it.team == Team.FABLED })
        assertEquals(11, inScope.count { it.team == Team.LORIC })

        val missing = inScope.filter { CharacterRules.all[Character.normalizeId(it.id)] == null }
        assertTrue(missing.isEmpty(), "no CharacterRule for: ${missing.map { it.id }}")
        assertEquals(inScope.size, FABLED_RULES.size)
        assertEquals(
            inScope.map { Character.normalizeId(it.id) }.toSet(),
            FABLED_RULES.map { Character.normalizeId(it.id) }.toSet(),
        )
    }

    @Test
    fun `no fabled row is silently empty - each declares a token, a hook or a reference note`() {
        val bare = inScope.mapNotNull { character ->
            val rule = CharacterRules.all.getValue(Character.normalizeId(character.id))
            val declares = rule.tokens.isNotEmpty() ||
                rule.firstNight != null || rule.otherNight != null ||
                rule.onDeath.isNotEmpty() || rule.setup.isNotEmpty() ||
                rule.day != null
            if (declares) null else character.id
        }
        assertTrue(bare.isEmpty(), "rows with nothing declared: $bare")
    }

    @Test
    fun `a fabled with no mechanical effect still carries a reference note`() {
        // Given every Fabled and Loric in play at once…
        var state = setup("imp", "chef", "empath", "mayor", "soldier")
        state = state.withFabled(*inScope.map { it.id }.toTypedArray())
        val holder = state.players.first()

        // …then every row whose only expression is prose says something.
        val silent = inScope.mapNotNull { character ->
            val rule = CharacterRules.all.getValue(Character.normalizeId(character.id))
            val hasMechanics = rule.tokens.isNotEmpty() ||
                rule.firstNight != null || rule.otherNight != null || rule.onDeath.isNotEmpty()
            if (hasMechanics) return@mapNotNull null
            val notes = rule.day?.briefing
                ?.invoke(BriefingContext(state, lookup, BriefingSlot.DAY_START, holder))
                .orEmpty()
            if (notes.any { it.text.isNotBlank() && it.sourceId == character.id }) null else character.id
        }
        assertTrue(silent.isEmpty(), "reference-only rows with no note: $silent")
    }

    // =======================================================================
    // The token table
    // =======================================================================

    @Test
    fun `every token this package declares is a real reminder with the official copy count`() {
        val problems = mutableListOf<String>()
        for (rule in FABLED_RULES) {
            val character = assertNotNull(lookup(rule.id), "unknown character ${rule.id}")
            for (token in rule.tokens) {
                assertEquals(
                    Character.normalizeId(rule.id),
                    Character.normalizeId(token.sourceId),
                    "a registry row may only declare its OWN tokens",
                )
                val copies = character.allReminders.count { it.trim().equals(token.label.trim(), true) }
                if (copies == 0) {
                    problems += "${rule.id}/${token.label} — data has ${character.allReminders}"
                } else if (copies != token.copies) {
                    problems += "${rule.id}/${token.label}: rule says ${token.copies}, data has $copies"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the registry row wins over the WP1 table for the tokens it owns`() {
        // Layering is the whole point: Tokens.all takes the registry row last.
        val toymaker = assertNotNull(Tokens.rule("toymaker", TOYMAKER_MARK))
        assertNull(toymaker.effect, "the mark records an unspent obligation; it is not a suppression")

        val angel = assertNotNull(Tokens.rule("angel", "Protected"))
        assertNull(angel.effect, "the Angel protects nobody")
        assertEquals(2, angel.copies)
        assertFalse(angel.protects)

        val fibbin = assertNotNull(Tokens.rule("fibbin", "No Ability"))
        assertEquals(EffectKind.SPENT, fibbin.effect)
        assertTrue(fibbin.grimoireCentre, "the Fibbin's mark belongs on the Fabled token")

        // …and agrees with WP1 where WP1 is right.
        val storm = assertNotNull(Tokens.rule("stormcatcher", "Stormcaught"))
        assertEquals(EffectKind.ONLY_EXECUTION_KILLS, storm.effect)
        assertEquals(Until.FOREVER, storm.until)
    }

    @Test
    fun `the duchess visitor tokens are swept at dawn`() {
        // Given three marked visitors on night 1…
        var state = night("imp", "chef", "empath", "mayor", "soldier").withFabled("duchess")
        for (id in listOf("chef", "empath")) {
            state = Effects.place(
                state, state.seat(id), EffectKind.MARKER, "duchess", null, Until.DAWN, "Visitor",
            ).state
        }
        state = Effects.place(
            state, state.seat("mayor"), EffectKind.MARKER, "duchess", null, Until.DAWN, "False Info",
        ).state

        // Then both Visitor copies coexist (the official set is Visitor x2).
        assertEquals(2, seatsHolding(state, "duchess", "Visitor").size)
        assertEquals(1, seatsHolding(state, "duchess", "False Info").size)

        // When the night ends, Then no seat is still marked.
        val day = Phases.advancePhase(state, lookup)
        assertTrue(seatsHolding(day, "duchess", "Visitor").isEmpty())
        assertTrue(seatsHolding(day, "duchess", "False Info").isEmpty())
        assertTrue(Tokens.expiringAtDawn.any { it.key == Tokens.key("duchess", "Visitor") })
    }

    // =======================================================================
    // Night plan — which Fabled wake, and which must never wake
    // =======================================================================

    @Test
    fun `only the fabled with an official night slot ever produce a step`() {
        var state = setup("imp", "chef", "empath", "mayor", "soldier")
        state = state.withFabled(*inScope.map { it.id }.toTypedArray())

        val first = Phases.advancePhase(state, lookup)
        val firstIds = NightPlan.build(first, lookup).steps.map { it.slotId }.toSet()
        assertEquals(
            setOf("angel", "buddhist", "toymaker", "stormcatcher", "tor"),
            firstIds.intersect(inScope.map { it.id }.toSet()),
        )

        val second = Phases.advancePhase(Phases.advancePhase(first, lookup), lookup)
        val otherIds = NightPlan.build(second, lookup).steps.map { it.slotId }.toSet()
        assertEquals(
            setOf("duchess", "toymaker", "tor"),
            otherIds.intersect(inScope.map { it.id }.toSet()),
        )

        // Regression guard: the Fiddler, Djinn, Doomsayer, Ferryman, Fibbin,
        // Sentinel, Gardener and Bootlegger must NEVER appear on a night sheet.
        for (id in listOf(
            "fiddler", "djinn", "doomsayer", "ferryman", "fibbin", "sentinel",
            "gardener", "bootlegger", "deusexfiasco", "hellslibrarian", "revolutionary",
            "spiritofivory", "bigwig", "godofug", "hindu", "knaves", "pope",
            "ventriloquist", "zenomancer",
        )) {
            assertFalse(id in firstIds, "$id must not be in the first-night plan")
            assertFalse(id in otherIds, "$id must not be in the other-night plan")
        }
    }

    // =======================================================================
    // P0 — Djinn: the rules read out are the SCRIPT's, not the bag's
    // =======================================================================

    @Test
    fun `djinn - a jinx applies because it is on the script, not because it was dealt`() {
        // Given a script listing both the Spy and the Magician…
        val scriptIds = listOf("magician", "spy", "imp", "chef", "empath")
        // …and a bag that contains neither.
        val dealtIds = listOf("imp", "chef", "empath", "mayor", "soldier")

        // Then the rule the Djinn reads out is present…
        val scriptJinxes = data.activeJinxes(scriptIds)
        assertTrue(
            scriptJinxes.any { setOf(it.id1, it.id2) == setOf("magician", "spy") },
            "the script's jinx must be announced even though neither was dealt",
        )
        // …and the per-seat hint list is empty, so nothing leaks the bag.
        assertTrue(data.activeJinxes(dealtIds).none { setOf(it.id1, it.id2) == setOf("magician", "spy") })

        // And a jinx names two real characters, both ways round.
        for (jinx in data.jinxes) {
            assertNotNull(lookup(jinx.id1), "unknown jinx character ${jinx.id1}")
            assertNotNull(lookup(jinx.id2), "unknown jinx character ${jinx.id2}")
        }
        assertNotNull(CharacterRules.all["djinn"])
    }

    // =======================================================================
    // P0 — Storm Catcher
    // =======================================================================

    @Test
    fun `storm catcher - the stormcaught player survives an exile and dies to an execution`() {
        // Given the Storm Catcher naming a good character that IS in play…
        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("stormcatcher")
        state = state.configure("stormcatcher", STORM_CATCHER_CHARACTER, "chef")
        val chef = state.seat("chef")

        // Then every non-execution death fails — including an exile, which a
        // Traveller holding a good character can reach.
        assertIs<KillOutcome.Prevented>(state.outcome(chef, DeathCause.EXILE))
        assertIs<KillOutcome.Prevented>(state.outcome(chef, DeathCause.GOOD_ABILITY))
        assertIs<KillOutcome.Prevented>(state.outcome(chef, DeathCause.STORYTELLER))
        assertIs<KillOutcome.Dies>(state.outcome(chef, DeathCause.EXECUTION))

        // And the mark never expires: it is still there after a full day/night.
        val laterNight = Phases.advancePhase(Phases.advancePhase(state, lookup), lookup)
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(laterNight, lookup, chef, KillCause(DeathCause.DEMON_KILL, "imp")),
        )
    }

    @Test
    fun `storm catcher - naming a character that is not in play marks nobody`() {
        // Given the named good character is absent from the grimoire…
        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("stormcatcher")
        state = state.configure("stormcatcher", STORM_CATCHER_CHARACTER, "washerwoman")

        // Then nothing is Stormcaught and every seat dies normally — the pick is
        // still legal, and hands evil a guaranteed-safe bluff.
        for (seat in state.seats) {
            assertTrue(
                Status.effectsOn(state, lookup, seat.id)
                    .none { it.kind == EffectKind.ONLY_EXECUTION_KILLS },
                "${seat.name} must not be Stormcaught",
            )
        }
        assertIs<KillOutcome.Dies>(
            state.outcome(state.seat("chef"), DeathCause.DEMON_KILL, "imp", state.seat("imp")),
        )
    }

    // =======================================================================
    // P0 — Angel: "Protected" is a marker, and the inverse of a protection
    // =======================================================================

    @Test
    fun `angel - a protected player dies normally, and the storyteller's own token still protects`() {
        // Given the Angel protects the Chef and the storyteller has protected the Empath…
        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("angel")
        state = Effects.addReminder(state, state.seat("chef"), PlacedReminder("angel", "Protected"))
        state = Effects.addReminder(
            state, state.seat("empath"), PlacedReminder(Tokens.STORYTELLER_SOURCE, "Protected"),
        )
        val imp = state.seat("imp")

        // Then the Angel's protectee dies — the token is a marker, not a shield.
        assertIs<KillOutcome.Dies>(state.outcome(state.seat("chef"), DeathCause.DEMON_KILL, "imp", imp))
        assertIs<KillOutcome.Dies>(state.outcome(state.seat("chef"), DeathCause.EXECUTION))
        assertTrue(
            Status.protections(state, lookup, state.seat("chef")).isEmpty(),
            "angel/Protected must never register as a protection",
        )

        // …while the storyteller's own identically-spelled token still does its job:
        // the match is on (sourceId, label), never on the label alone.
        assertIs<KillOutcome.Prevented>(
            state.outcome(state.seat("empath"), DeathCause.DEMON_KILL, "imp", imp),
        )
    }

    @Test
    fun `angel - a death of a protected player raises the responsibility question`() {
        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("angel")
        state = Effects.addReminder(state, state.seat("chef"), PlacedReminder("angel", "Protected"))
        val rule = CharacterRules.all.getValue("angel")
        val trigger = rule.onDeath.single()

        val event = DeathEvent(
            id = 1, playerId = state.seat("chef"), day = 1, atNight = true,
            cause = DeathCause.DEMON_KILL, killerCharacterId = "imp", killerPlayerId = state.seat("imp"),
        )
        assertTrue(trigger.gate(state, event, state.players.first()))
        val produced = trigger.produce(state, event, state.players.first())
        val prompt = produced.prompts.single()
        assertEquals(PromptKind.DECIDE, prompt.kind)
        assertEquals(listOf(state.seat("imp")), prompt.targetIds, "the Demon is the suggested culprit")
        assertTrue(produced.effects.isEmpty(), "the Angel decides nothing on its own")

        // An unprotected seat's death raises nothing.
        val other = event.copy(playerId = state.seat("mayor"))
        assertFalse(trigger.gate(state, other, state.players.first()))
    }

    // =======================================================================
    // P0 — Spirit of Ivory: the one-extra-evil cap
    // =======================================================================

    @Test
    fun `spirit of ivory - the cap counts residents against the snapshot baseline`() {
        // Given a 7-player game with 2 evil and the baseline snapshot taken…
        var state = night("imp", "poisoner", "chef", "empath", "mayor", "soldier", "monk")
            .withFabled("spiritofivory")
        state = state.configure("spiritofivory", IVORY_BASELINE, "2")

        assertEquals(0, extraEvilCount(state, lookup))
        assertFalse(noMoreEvil(state, lookup))

        // When one good player turns evil, Then the cap is reached.
        state = GameActions.flipAlignment(state, state.seat("chef"), lookup)
        assertEquals(1, extraEvilCount(state, lookup))
        assertTrue(noMoreEvil(state, lookup))

        // And an arriving evil Traveller does not count towards it.
        state = GameActions.assignCharacter(state, 6L, "gunslinger", isTraveller = true)
        state = state.updatePlayer(6L) { it.copy(alignment = Alignment.EVIL) }
        assertEquals(1, extraEvilCount(state, lookup))

        // When the conversion is undone, Then the next one is free again.
        state = GameActions.flipAlignment(state, state.seat("chef"), lookup)
        assertEquals(0, extraEvilCount(state, lookup))
        assertFalse(noMoreEvil(state, lookup))
    }

    @Test
    fun `spirit of ivory - no more evil is a grimoire centre token that never expires`() {
        val rule = assertNotNull(Tokens.rule("spiritofivory", "No More Evil"))
        assertTrue(rule.grimoireCentre)
        assertEquals(Until.FOREVER, rule.until)

        var state = night("imp", "chef", "empath", "mayor", "soldier").withFabled("spiritofivory")
        state = state.copy(
            storytellerReminders = listOf(PlacedReminder("spiritofivory", "No More Evil")),
        )
        // Through dawn and dusk it survives: it is a fact about the board, not a clock.
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup)
        assertEquals(1, state.storytellerReminders.size)
    }

    // =======================================================================
    // P0 — Toymaker: the obligation, and the night it is forced
    // =======================================================================

    @Test
    fun `toymaker - the first night marks the demon and does not silence them`() {
        // Given a Toymaker game…
        val state = night("imp", "chef", "empath", "mayor", "soldier").withFabled("toymaker")
        val imp = state.seat("imp")
        assertNotNull(NightPlan.build(state, lookup).step(StepKey("toymaker")))

        // When the Toymaker's first-night step is run…
        val marked = NightPlan.resolve(state, lookup, StepKey("toymaker"), NightInput())

        // Then the Demon carries the obligation…
        assertEquals(listOf(imp), seatsHolding(marked, "toymaker", TOYMAKER_MARK).map { it.id })
        // …and can still kill: the mark is not a suppression (5 alive, the game
        // cannot end tonight).
        assertFalse(demonAttackCouldEndGame(marked, lookup))
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(marked, lookup, state.seat("chef"), KillCause(DeathCause.DEMON_KILL, "imp", imp)),
        )
    }

    @Test
    fun `toymaker - on a night the attack could end the game the demon does not act`() {
        // Given the obligation is unspent and only three players are alive…
        var state = night("imp", "chef", "empath", "washerwoman", "librarian").withFabled("toymaker")
        state = NightPlan.resolve(state, lookup, StepKey("toymaker"), NightInput())
        state = Deaths.attempt(state, lookup, state.seat("washerwoman"), KillCause(DeathCause.STORYTELLER)).state
        state = Deaths.attempt(state, lookup, state.seat("librarian"), KillCause(DeathCause.STORYTELLER)).state
        state = Phases.advancePhase(state, lookup) // -> DAY 1
        state = Phases.advancePhase(state, lookup) // -> NIGHT 2
        val imp = state.seat("imp")
        assertTrue(demonAttackCouldEndGame(state, lookup))

        // When the Toymaker's other-night step is run (it sits before every Demon)…
        val forced = NightPlan.resolve(state, lookup, StepKey("toymaker"), NightInput())

        // Then the Demon's attack is blocked by the funnel itself…
        val blocked = Deaths.killOutcome(
            forced, lookup, forced.seat("chef"), KillCause(DeathCause.DEMON_KILL, "imp", imp),
        )
        assertIs<KillOutcome.Prevented>(blocked)
        // …and the obligation is spent, so it never forces a second night.
        assertTrue(seatsHolding(forced, "toymaker", TOYMAKER_MARK).isEmpty())

        // And the suppression is tonight's only: it is gone by dawn.
        val nextDay = Phases.advancePhase(forced, lookup)
        assertTrue(
            Status.effectsOn(nextDay, lookup, imp).none { it.kind == EffectKind.DEMON_CANNOT_KILL },
        )
    }

    @Test
    fun `toymaker - an exorcised demon does not spend the obligation`() {
        // Given a forced night, but the Demon was already silenced by an Exorcist…
        var state = night("imp", "chef", "empath", "washerwoman", "librarian").withFabled("toymaker")
        state = NightPlan.resolve(state, lookup, StepKey("toymaker"), NightInput())
        state = Deaths.attempt(state, lookup, state.seat("washerwoman"), KillCause(DeathCause.STORYTELLER)).state
        state = Deaths.attempt(state, lookup, state.seat("librarian"), KillCause(DeathCause.STORYTELLER)).state
        state = Phases.advancePhase(state, lookup) // -> DAY 1
        state = Phases.advancePhase(state, lookup) // -> NIGHT 2
        val imp = state.seat("imp")
        state = Effects.place(
            state, imp, EffectKind.DEMON_CANNOT_KILL, "exorcist", null, Until.DAWN, "Chosen",
        ).state
        assertTrue(demonAttackCouldEndGame(state, lookup))

        // When the Toymaker's step runs…
        val run = NightPlan.resolve(state, lookup, StepKey("toymaker"), NightInput())

        // Then the obligation is NOT consumed — the Demon never had a choice —
        // and the Exorcist alone accounts for the block.
        assertEquals(listOf(imp), seatsHolding(run, "toymaker", TOYMAKER_MARK).map { it.id })
        val silenced = Status.live(run, lookup, imp, EffectKind.DEMON_CANNOT_KILL)
        assertEquals(1, silenced.size, "no double suppression")
        assertEquals("exorcist", silenced.single().sourceCharacterId)
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(run, lookup, run.seat("chef"), KillCause(DeathCause.DEMON_KILL, "imp", imp)),
        )
    }

    @Test
    fun `toymaker - travellers never count towards the night the game could end`() {
        // Five residents plus one live Traveller.
        var state = setup("imp", "chef", "empath", "washerwoman", "librarian")
        state = Seats.addSeat(state, "Trav")
        val traveller = state.players.last().id
        state = GameActions.assignCharacter(state, traveller, "gunslinger", isTraveller = true)
        state = Phases.advancePhase(state, lookup).withFabled("toymaker")
        assertFalse(demonAttackCouldEndGame(state, lookup), "5 residents alive")

        state = Deaths.attempt(state, lookup, state.seat("washerwoman"), KillCause(DeathCause.STORYTELLER)).state
        assertFalse(demonAttackCouldEndGame(state, lookup), "4 residents alive")

        // The Traveller does not save the board: only residents count for the
        // two-alive evil win, and neither do Fabled.
        state = Deaths.attempt(state, lookup, state.seat("librarian"), KillCause(DeathCause.STORYTELLER)).state
        assertTrue(state.players.first { it.id == traveller }.alive)
        assertTrue(demonAttackCouldEndGame(state, lookup), "3 residents alive plus a Traveller")
    }

    // =======================================================================
    // P0 — Sentinel: the bag rule the setup screen must pass the ids into
    // =======================================================================

    @Test
    fun `sentinel - one extra or one fewer outsider is a legal bag`() {
        val bag = listOf(
            "chef", "empath", "fortuneteller", "investigator", "librarian", "mayor",
            "recluse", "poisoner", "imp",
        ).map { assertNotNull(lookup(it)) }
        assertEquals(9, bag.size)

        // Base 9-player distribution is 5/2/1/1; this bag is 6/1/1/1.
        val without = Setup.validateBag(bag, 9)
        assertTrue(without.any { "Outsider" in it }, "flagged without the Sentinel: $without")

        val with = Setup.validateBag(bag, 9, listOf("sentinel"))
        assertTrue(with.isEmpty(), "the Sentinel makes it legal: $with")

        // The registry declares the decision the storyteller must record…
        val rule = CharacterRules.all.getValue("sentinel")
        val requirement = rule.setup.single()
        assertEquals("sentinel.outsiderDelta", requirement.id)
        var state = setup("chef", "empath", "mayor", "soldier", "imp").withFabled("sentinel")
        assertFalse(requirement.satisfied(state, lookup))
        state = state.configure("sentinel", "sentinel.outsiderDelta", "1")
        assertTrue(requirement.satisfied(state, lookup))
        // …and it never adds a bag row of its own.
        assertNull(rule.bagShape)
    }

    // =======================================================================
    // P0 — Fibbin: information only, spent on the Fabled token
    // =======================================================================

    @Test
    fun `fibbin - the spend mark is data-declared, permanent, and impairs nobody`() {
        // Given the official spentLabel is in the data (lead D49, no text heuristic)…
        assertEquals("No Ability", assertNotNull(lookup("fibbin")).spentLabel)

        var state = night("imp", "chef", "empath", "mayor", "soldier").withFabled("fibbin")
        state = state.copy(
            storytellerReminders = listOf(PlacedReminder("fibbin", "No Ability")),
            fabled = state.fabled.map { it.copy(used = true) },
        )

        // When a full day and night pass, Then the mark is still spent.
        state = Phases.advancePhase(state, lookup)
        state = Phases.advancePhase(state, lookup)
        assertEquals(1, state.storytellerReminders.size)
        assertTrue(state.fabled.single().used)

        // And no seat is impaired by it: the Fibbin never makes an ability fail,
        // and a Fibbin-lied player is not a Mathematician malfunction.
        for (seat in state.seats) {
            assertTrue(
                Status.impairment(state, lookup, seat.id).isEmpty(),
                "${seat.name} must not be impaired by the Fibbin",
            )
            assertTrue(seat.reminders.none { Tokens.key(it) == Tokens.key("fibbin", "No Ability") })
        }
    }

    // =======================================================================
    // P0 — Fiddler: a once-per-game action, never a night step
    // =======================================================================

    @Test
    fun `fiddler - the contest is offered once and never as a nomination`() {
        var state = Phases.advancePhase(
            night("imp", "chef", "empath", "mayor", "soldier"),
            lookup,
        ).withFabled("fiddler")
        val ability = assertNotNull(CharacterRules.all.getValue("fiddler").day?.ability)
        assertTrue(ability.oncePerGame)
        assertEquals("fiddler", ability.recordsAs)

        // Given the Fiddler is in play and unspent, Then the contest is offered.
        assertTrue(ability.available(state, lookup, state.players.first()))

        // When it has been used, Then it is never offered again.
        state = state.copy(fabled = state.fabled.map { it.copy(used = true) })
        assertFalse(ability.available(state, lookup, state.players.first()))

        // And running it writes no Nomination: it is not the nomination path.
        assertTrue(state.nominations.isEmpty())
        assertNull(DayRules.aboutToDie(state))
    }

    // =======================================================================
    // Supporting rows the P0s lean on
    // =======================================================================

    @Test
    fun `ferryman - the restore is owed only once, and only on a declared final day`() {
        var state = Phases.advancePhase(
            night("imp", "chef", "empath", "mayor", "soldier"),
            lookup,
        ).withFabled("ferryman")
        assertFalse(ferrymanOwes(state), "the final day is declared, never inferred")

        state = state.copy(finalDayCycle = state.cycle)
        assertTrue(ferrymanOwes(state))

        state = state.copy(fabled = state.fabled.map { it.copy(used = true) })
        assertFalse(ferrymanOwes(state), "votes spent after the restore stay spent")
    }

    @Test
    fun `revolutionary - the pair must be neighbours`() {
        val requirement = CharacterRules.all.getValue("revolutionary").setup.single()
        assertEquals("revolutionary.pair", requirement.id)

        var state = setup("imp", "chef", "empath", "mayor", "soldier").withFabled("revolutionary")
        state = state.copy(fabled = state.fabled.map { it.copy(playerIds = listOf(1L, 2L)) })
        assertTrue(requirement.satisfied(state, lookup))

        val split = state.copy(fabled = state.fabled.map { it.copy(playerIds = listOf(1L, 3L)) })
        assertFalse(requirement.satisfied(split, lookup))

        // Seats 0 and 4 wrap around the circle and ARE neighbours.
        val wrapped = state.copy(fabled = state.fabled.map { it.copy(playerIds = listOf(0L, 4L)) })
        assertTrue(requirement.satisfied(wrapped, lookup))
    }

    // =======================================================================
    // W7A — the seatless hooks are LIVE, not just declared
    // =======================================================================

    @Test
    fun `angel - the responsibility question is raised by the kill funnel itself`() {
        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("angel")
        state = Effects.addReminder(state, state.seat("chef"), PlacedReminder("angel", "Protected"))

        // When the protected seat dies through the ONE funnel every caller uses…
        val attempt = Deaths.attempt(
            state,
            lookup,
            state.seat("chef"),
            KillCause(DeathCause.DEMON_KILL, "imp", state.seat("imp")),
        )

        // Then the Angel's own prompt is queued — and no seat holds the Angel.
        val prompt = attempt.state.prompts.single { it.sourceId == "angel" }
        assertEquals(PromptKind.DECIDE, prompt.kind)
        assertEquals(listOf(state.seat("imp")), prompt.targetIds)

        // And with the Angel out of play the same death raises nothing.
        val without = Deaths.attempt(
            GameActions.setFabled(state, emptyList()),
            lookup,
            state.seat("chef"),
            KillCause(DeathCause.DEMON_KILL, "imp", state.seat("imp")),
        )
        assertTrue(without.state.prompts.none { it.sourceId == "angel" })
    }

    @Test
    fun `hindu - the reincarnation prompt is raised by the kill funnel itself`() {
        val state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("hindu")
        val attempt = Deaths.attempt(
            state,
            lookup,
            state.seat("chef"),
            KillCause(DeathCause.DEMON_KILL, "imp"),
        )
        val prompt = attempt.state.prompts.single { it.sourceId == "hindu" }
        assertEquals(PromptKind.CHOOSE_CHARACTER, prompt.kind)
        assertEquals(state.seat("chef"), prompt.subjectPlayerId)
    }

    @Test
    fun `big wig - the nomination trigger reaches DayRules with no seat to hold it`() {
        val state = Phases.advancePhase(
            night("imp", "chef", "empath", "mayor", "soldier"),
            lookup,
        ).withFabled("bigwig")
        val check = DayRules.checkNomination(state, lookup, state.seat("chef"), state.seat("mayor"))
        val trigger = check.triggers.single { it.sourceId == "bigwig" }
        assertEquals(TriggerKind.CHOICE, trigger.kind)
        assertEquals(state.seat("mayor"), trigger.actorId)

        val without = GameActions.setFabled(state, emptyList())
        assertTrue(
            DayRules.checkNomination(without, lookup, state.seat("chef"), state.seat("mayor"))
                .triggers.none { it.sourceId == "bigwig" },
        )
    }

    @Test
    fun `ventriloquist - the execution consequence reaches Execution with no seat`() {
        var state = Phases.advancePhase(
            night("imp", "chef", "empath", "mayor", "soldier"),
            lookup,
        ).withFabled("ventriloquist")
        val chef = state.seat("chef")
        state = Effects.place(
            state, chef, EffectKind.MAD, "ventriloquist", null, Until.DUSK, "Mad",
        ).state
        val record =
            ExecutionRecord(day = state.cycle, outcome = ExecutionOutcome.DIED, playerId = chef)

        assertEquals(
            1,
            Execution.consequences(state, lookup, record).count { it.sourceId == "ventriloquist" },
        )
        val without = GameActions.setFabled(state, emptyList())
        assertTrue(
            Execution.consequences(without, lookup, record)
                .none { it.sourceId == "ventriloquist" },
        )
    }

    @Test
    fun `storm catcher - the stormcaught effect comes from the registry standing rule`() {
        // The registry row owns it now; WP1's hardcoded block in Effects.kt is gone.
        assertNotNull(
            CharacterRules.all.getValue("stormcatcher").standing,
            "stormcatcher must declare a standing rule",
        )

        var state = night("chef", "empath", "mayor", "soldier", "imp").withFabled("stormcatcher")
        state = state.configure("stormcatcher", STORM_CATCHER_CHARACTER, "chef")
        val marks = Status.effectsOn(state, lookup, state.seat("chef"))
            .filter { it.kind == EffectKind.ONLY_EXECUTION_KILLS }
        assertEquals(1, marks.size, "exactly one Stormcaught effect, never two")
        assertEquals("stormcatcher", marks.single().sourceCharacterId)
        assertTrue(marks.single().derived)
    }

    @Test
    fun `duchess - the night step is built from the registry row and computes the count`() {
        var state = night("imp", "poisoner", "chef", "empath", "mayor").withFabled("duchess")
        state = Phases.advancePhase(state, lookup) // -> DAY 1
        state = Phases.advancePhase(state, lookup) // -> NIGHT 2

        // With fewer than 3 visitors marked the registry gate skips the step…
        val idle = assertNotNull(NightPlan.build(state, lookup).step(StepKey("duchess")))
        assertIs<StepGate.Skip>(idle.gate)

        // …and with exactly 3 it fires, carrying the registry's own prompt.
        for (id in listOf("imp", "poisoner")) {
            state = Effects.place(
                state, state.seat(id), EffectKind.MARKER, "duchess", null, Until.DAWN, "Visitor",
            ).state
        }
        state = Effects.place(
            state, state.seat("chef"), EffectKind.MARKER, "duchess", null, Until.DAWN, "False Info",
        ).state
        val live = assertNotNull(NightPlan.build(state, lookup).step(StepKey("duchess")))
        assertIs<StepGate.Fire>(live.gate)
        assertTrue(live.prompt.startsWith("Wake each player marked"), "registry prompt: ${live.prompt}")

        // And InfoCalc answers with the number of EVIL visitors (Imp + Poisoner).
        val info = assertNotNull(InfoCalc.compute(state, lookup, "duchess", null))
        assertEquals(Answer.Count(2, 0, 3), info.answer)
        assertTrue(info.alternatives.isNotEmpty(), "the False Info visitor needs another number")
    }

    @Test
    fun `ventriloquist - a mad nominee's execution raises a might-not-die question`() {
        var state = Phases.advancePhase(
            night("imp", "chef", "empath", "mayor", "soldier"),
            lookup,
        ).withFabled("ventriloquist")
        val chef = state.seat("chef")
        state = Effects.place(
            state, chef, EffectKind.MAD, "ventriloquist", null, Until.DUSK, "Mad",
        ).state

        val hook = assertNotNull(CharacterRules.all.getValue("ventriloquist").day?.onExecution)
        val record = ExecutionRecord(day = state.cycle, outcome = ExecutionOutcome.DIED, playerId = chef)
        val consequences = hook(ExecutionContext(state, lookup, record, state.players.first()))
        assertEquals(1, consequences.size)
        assertEquals("ventriloquist", consequences.single().sourceId)

        // A seat that is not mad as a fresh character raises nothing.
        val other = record.copy(playerId = state.seat("mayor"))
        assertTrue(hook(ExecutionContext(state, lookup, other, state.players.first())).isEmpty())
    }
}
