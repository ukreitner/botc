package com.clocktower.engine

import com.clocktower.engine.rules.EXP_TOWNSFOLK_RULES
import com.clocktower.engine.rules.SUPPRESSED_INFO_IDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-EXP-T — the thirty experimental Townsfolk, exercised through the REAL
 * pipeline: `NightPlan.build` / `NightPlan.resolve`, `Deaths.attempt`,
 * `DayRules.checkNomination` and `Execution.consequences`. Nothing here pokes
 * at a rule lambda directly.
 *
 * Every character the digests mark P0 has at least one Given/When/Then below;
 * the registry-wide coverage and token-table cases come first.
 */
class RulesExpTownsfolkTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character

    /** Every experimental Townsfolk, straight from the data — never hand-listed. */
    private val scope: List<Character> = data.characters
        .filter { it.edition == "exp" && it.team == Team.TOWNSFOLK }

    /** A script that contains whatever the test seats, so setup helpers behave. */
    private fun script(vararg roles: String) = Script(
        id = "exp-test",
        name = "Experimental test script",
        characterIds = roles.map(Character::normalizeId).distinct(),
    )

    /** A seated game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(script(*roles), roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return GameActions.advancePhase(state)
    }

    /** Straight to night [n], without walking the days. */
    private fun atNight(state: GameState, n: Int): GameState = state.copy(cycle = n)

    private fun day(state: GameState, n: Int): GameState =
        state.copy(cycle = n, phase = Phase.DAY)

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String, holderId: Long? = null): NightStep? =
        plan(state).steps.firstOrNull {
            it.abilityId == abilityId && (holderId == null || it.holderId == holderId)
        }

    private fun gate(state: GameState, abilityId: String, holderId: Long? = null): StepGate? =
        step(state, abilityId, holderId)?.gate

    /** Runs a step through `NightPlan.resolve`, exactly as the night screen does. */
    private fun run(
        state: GameState,
        abilityId: String,
        input: NightInput,
        holderId: Long? = null,
    ): GameState {
        val row = assertNotNull(step(state, abilityId, holderId), "no $abilityId step in the plan")
        return NightPlan.resolve(state, lookup, row.key, input)
    }

    private fun has(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val seat = state.player(playerId) ?: return false
        return seat.reminders.any { Tokens.key(it) == key } ||
            state.effects.any { it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key }
    }

    private fun kill(
        state: GameState,
        targetId: Long,
        cause: DeathCause,
        sourceCharacterId: String,
        sourcePlayerId: Long? = null,
    ): GameState = Deaths.attempt(
        state, lookup, targetId,
        KillCause(cause, sourceCharacterId, sourcePlayerId),
    ).state

    // ==================================================================
    // Registry coverage
    // ==================================================================

    @Test
    fun `every experimental Townsfolk has a registry row`() {
        assertEquals(30, scope.size, "the scope changed: ${scope.map { it.id }}")
        val missing = scope.map { Character.normalizeId(it.id) }
            .filterNot { it in CharacterRules.all }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
    }

    @Test
    fun `the registry rows are exactly the scope, normalised and unique`() {
        val ids = EXP_TOWNSFOLK_RULES.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids in EXP_TOWNSFOLK_RULES: $ids")
        assertEquals(
            scope.map { Character.normalizeId(it.id) }.sorted(),
            ids.map(Character::normalizeId).sorted(),
        )
        // `CharacterRules.of` must find each one rather than falling back to the
        // generic row built from `characters.json`.
        for (id in ids) {
            assertEquals(
                CharacterRules.all[id],
                CharacterRules.of(id, lookup(id)),
                "$id fell through to the generic rule",
            )
        }
    }

    @Test
    fun `the three keeps-ability-when-dead rows survive the stopgap being deleted`() {
        // `CharacterRules.all` wins over `StatusQuery.KEEPS_ABILITY_WHEN_DEAD` (lead D64),
        // so these three must carry the flag themselves.
        for (id in listOf("atheist", "banshee", "poppygrower")) {
            assertTrue(
                CharacterRules.all.getValue(id).keepsAbilityWhenDead,
                "$id must keep its ability when dead",
            )
        }
    }

    @Test
    fun `every info id these rows name has a calculator, and none is a placeholder`() {
        val rules = EXP_TOWNSFOLK_RULES.flatMap { listOfNotNull(it.firstNight, it.otherNight) }
        // W7H: a NAMED id must be one `InfoCalc` really implements. Naming an
        // unsupported id used to be how a row suppressed the planner's fallback;
        // `infoId = ""` says that outright now, so a name here is a promise.
        val named = rules.mapNotNull { it.infoId }.filter { it.isNotEmpty() }.distinct()
        val unsupported = named.filterNot { InfoCalc.supports(it) }
        assertTrue(unsupported.isEmpty(), "no calculator for: $unsupported")

        // And the rows that compute nothing say so explicitly, never by omission.
        val suppressed = EXP_TOWNSFOLK_RULES
            .filter { rule ->
                listOfNotNull(rule.firstNight, rule.otherNight).any { it.infoId == "" }
            }
            .map { it.id }
        assertEquals(SUPPRESSED_INFO_IDS.sorted(), suppressed.sorted())

        // `null` — the default — still falls back to the ability's own id.
        for (id in listOf(
            "balloonist", "bountyhunter", "cultleader", "king", "knight",
            "noble", "shugenja", "steward", "villageidiot",
        )) {
            assertTrue(InfoCalc.supports(id), "$id lost its calculator")
        }
    }

    @Test
    fun `a suppressed info id offers no picker and no cards`() {
        // The Acrobat learns nothing: `infoId = ""` must stop the planner
        // inventing a `ShowInfo` out of the ability id (W7H).
        val state = atNight(game("acrobat", "imp", "poisoner", "chef", "mayor"), 2)
        val row = assertNotNull(step(state, "acrobat"))
        assertIs<ChoosePlayers>(row.action, "its own action still stands")
        assertTrue(row.cards.isEmpty(), "and nothing is pre-filled: ${row.cards.map { it.label }}")
        assertNull(InfoCalc.compute(state, lookup, "acrobat", 0L))
    }

    // ==================================================================
    // The token table
    // ==================================================================

    @Test
    fun `every token this package declares matches the official data`() {
        val wrong = mutableListOf<String>()
        for (rule in EXP_TOWNSFOLK_RULES.flatMap { it.tokens }) {
            val character = lookup(rule.sourceId)
            if (character == null) {
                wrong += "${rule.sourceId} is not a character"
                continue
            }
            val copies = character.allReminders.count { it.trim().equals(rule.label.trim(), true) }
            if (copies == 0) {
                wrong += "${rule.sourceId}/${rule.label} is not a reminder — has ${character.allReminders}"
            } else if (copies != rule.copies) {
                wrong += "${rule.sourceId}/${rule.label}: rule says ${rule.copies}, data has $copies"
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `the declared tokens win over the base table and are never swept`() {
        val expected = mapOf(
            ("amnesiac" to "?") to 3,
            ("balloonist" to "Know") to 1,
            ("bountyhunter" to "Know") to 1,
            ("knight" to "Know") to 2,
            ("noble" to "Know") to 3,
            ("preacher" to "No Ability") to 3,
            ("steward" to "Know") to 1,
            ("villageidiot" to "Drunk") to 1,
        )
        val dawn = Tokens.expiringAtDawn.map { it.key }
        val dusk = Tokens.expiringAtDusk.map { it.key }
        for ((pair, copies) in expected) {
            val rule = assertNotNull(Tokens.rule(pair.first, pair.second), "${pair.first}/${pair.second}")
            assertEquals(copies, rule.copies, "${pair.first}/${pair.second} copies")
            assertFalse(rule.key in dawn, "${pair.first}/${pair.second} must never be swept at dawn")
            assertFalse(rule.key in dusk, "${pair.first}/${pair.second} must never be swept at dusk")
        }
        // The suppression and the setup drunk are real rules, not bare markers.
        assertEquals(EffectKind.NO_ABILITY, Tokens.rule("preacher", "No Ability")!!.effect)
        assertEquals(EffectKind.DRUNK, Tokens.rule("villageidiot", "Drunk")!!.effect)
        assertFalse(
            Tokens.rule("villageidiot", "Drunk")!!.endsWithSource,
            "the setup mark outlives every Village Idiot leaving play",
        )
        // Rows this package does NOT own must keep their WP1 lifetimes.
        assertEquals(Until.DAWN, Tokens.rule("poppygrower", "Evil Wakes")!!.until)
        assertEquals(Until.DAWN, Tokens.rule("princess", "Doesn't Kill")!!.until)
    }

    // ==================================================================
    // acrobat
    // ==================================================================

    @Test
    fun `acrobat marks the chosen seat and owes a dawn ruling`() {
        // Given an Acrobat on night 2 (they never act on the first night)
        val state = atNight(game("acrobat", "imp", "poisoner", "chef", "mayor"), 2)
        assertNull(step(game("acrobat", "imp", "poisoner", "chef", "mayor"), "acrobat"))
        assertEquals(StepGate.Fire, gate(state, "acrobat"))

        // When they point at the seat the Poisoner is about to poison
        val next = run(state, "acrobat", NightInput(playerIds = listOf(3L)))

        // Then the seat is marked, and the death is a dawn obligation, not a guess
        assertTrue(has(next, 3L, "acrobat", "Chosen"))
        val owed = next.prompts.single { it.sourceId == "acrobat" }
        assertEquals(BriefingSlot.DAWN, owed.at)
        assertEquals(PromptKind.RESOLVE_KILL, owed.kind)
        assertEquals(0L, owed.subjectPlayerId, "the Acrobat is the one who dies")
    }

    // ==================================================================
    // alchemist
    // ==================================================================

    @Test
    fun `an Alchemist wakes at their own slot and again at the granted Minion's`() {
        // Given an Alchemist holding the Poisoner ability
        var state = game("alchemist", "imp", "chef", "empath", "mayor")
        state = state.updatePlayer(0L) {
            it.copy(grants = listOf(AbilityGrant("poisoner", "alchemist", GrantMode.ADD)))
        }

        // When the first night is planned
        val first = plan(state).steps.filter { it.holderId == 0L }

        // Then the seat has both rows, and stays a good Townsfolk
        assertEquals(listOf("alchemist", "poisoner"), first.map { it.abilityId })
        assertFalse(state.player(0L)!!.isEvil(lookup))
        assertEquals(Team.TOWNSFOLK, state.player(0L)!!.team(lookup))

        // And on later nights only the Minion ability wakes.
        val later = plan(atNight(state, 2)).steps.filter { it.holderId == 0L }
        assertEquals(listOf("poisoner"), later.map { it.abilityId })
    }

    // ==================================================================
    // alsaahir / fisherman — day-only, never a night row
    // ==================================================================

    @Test
    fun `the day-only characters never appear on a night sheet`() {
        for (id in listOf("alsaahir", "fisherman", "atheist")) {
            val state = game(id, "imp", "poisoner", "chef", "mayor")
            assertNull(step(state, id), "$id must not wake on the first night")
            assertNull(step(atNight(state, 3), id), "$id must not wake on later nights")
            assertNotNull(
                CharacterRules.all.getValue(id).day,
                "$id owes the day tab a row",
            )
        }
    }

    // ==================================================================
    // amnesiac
    // ==================================================================

    @Test
    fun `an Amnesiac with no written ability asks instead of blocking dawn`() {
        // Given the storyteller has not written the invented ability yet
        val state = game("amnesiac", "imp", "poisoner", "chef", "mayor")

        // Then the row asks rather than silently demanding a tick
        val asked = assertNotNull(gate(state, "amnesiac"))
        assertTrue(asked is StepGate.Conditional, "expected a question, got $asked")

        // When the ability is written down
        val written = Decisions.set(state, Decisions.AMNESIAC_ABILITY, "You know when a good player dies.")

        // Then it fires, and each '?' token is one of exactly three
        assertEquals(StepGate.Fire, gate(written, "amnesiac"))
        val next = run(written, "amnesiac", NightInput(playerIds = listOf(2L, 3L)))
        assertTrue(has(next, 2L, "amnesiac", "?"))
        assertTrue(has(next, 3L, "amnesiac", "?"))
        assertEquals(3, Tokens.rule("amnesiac", "?")!!.copies)
    }

    // ==================================================================
    // atheist
    // ==================================================================

    @Test
    fun `executing the Storyteller with an Atheist in play is a good win, even dead`() {
        // Given a DEAD Atheist
        var state = day(game("atheist", "imp", "poisoner", "chef", "mayor"), 2)
        state = kill(state, 0L, DeathCause.EXECUTION, "")
        assertFalse(state.player(0L)!!.alive)

        // When the Storyteller is executed
        val consequences = Execution.consequences(
            state, lookup,
            ExecutionRecord(state.cycle, ExecutionOutcome.DIED, playerId = GameState.STORYTELLER_SEAT_ID),
        )

        // Then good wins is stated outright
        val row = assertNotNull(consequences.firstOrNull { it.sourceId == "atheist" })
        assertTrue("GOOD WINS" in row.headline, row.headline)
    }

    // ==================================================================
    // balloonist
    // ==================================================================

    @Test
    fun `the Balloonist's Know token moves rather than accumulating`() {
        // Given a Balloonist who learned seat 2 on night 1
        var state = game("balloonist", "imp", "poisoner", "chef", "mayor")
        state = run(state, "balloonist", NightInput(playerIds = listOf(2L)))
        assertTrue(has(state, 2L, "balloonist", "Know"))

        // When they learn seat 3 on night 2
        state = atNight(state, 2)
        state = run(state, "balloonist", NightInput(playerIds = listOf(3L)))

        // Then exactly one Know exists, on the new seat, and both nights are recorded
        assertFalse(has(state, 2L, "balloonist", "Know"), "the token moves, it does not stack")
        assertTrue(has(state, 3L, "balloonist", "Know"))
        assertEquals(
            listOf(listOf(2L), listOf(3L)),
            Memory.by(state, LedgerKind.CHOICE, "balloonist").map { it.targetIds },
        )
    }

    @Test
    fun `the Balloonist cannot be shown the same character type two nights running`() {
        // Given a Balloonist shown the Poisoner (a MINION) on night 1,
        var state = game("balloonist", "imp", "poisoner", "baron", "chef", "mayor")
        state = run(state, "balloonist", NightInput(playerIds = listOf(2L)))
        state = atNight(state, 2)

        // W7E: the constraint is on the picker AND enforced at resolve time.
        val choose = assertIs<ChoosePlayers>(assertNotNull(step(state, "balloonist")).action)
        assertTrue(TargetConstraint.DIFFERENT_TYPE_FROM_LAST_NIGHT in choose.constraints)

        // Then another MINION is refused…
        val repeat = run(state, "balloonist", NightInput(playerIds = listOf(3L)))
        assertFalse(has(repeat, 3L, "balloonist", "Know"), "the Baron is a Minion too")

        // …and a Townsfolk is not.
        val fresh = run(state, "balloonist", NightInput(playerIds = listOf(4L)))
        assertTrue(has(fresh, 4L, "balloonist", "Know"))
    }

    // ==================================================================
    // banshee
    // ==================================================================

    @Test
    fun `a sober Banshee killed by the Demon awakens and gains day rights`() {
        // Given a sober Banshee
        var state = atNight(game("banshee", "imp", "poisoner", "chef", "mayor", "monk"), 2)
        assertTrue(gate(state, "banshee") is StepGate.Skip, "no announcement while they live")

        // When the Demon kills them
        state = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)

        // Then the ability lands at once and the dawn announcement is owed
        assertTrue(has(state, 0L, "banshee", "Has Ability"))
        assertEquals(StepGate.Fire, gate(state, "banshee"))
        val owed = state.prompts.single { it.sourceId == "banshee" }
        assertEquals(BriefingSlot.DAWN, owed.at)
        assertEquals(PromptKind.ANNOUNCE, owed.kind)

        // And the dead Banshee may nominate twice and vote twice.
        val today = day(state, 2)
        assertTrue(DayRules.canNominate(today, lookup, 0L).allowed, "an awoken Banshee nominates while dead")
        assertEquals(2, DayRules.voteRules(today, lookup, isExile = false).weightOf(0L))
    }

    @Test
    fun `a poisoned Banshee and a non-Demon kill trigger nothing`() {
        // Given a POISONED Banshee
        var state = atNight(game("banshee", "imp", "poisoner", "chef", "mayor", "monk"), 2)
        state = Effects.addReminder(state, 0L, PlacedReminder("poisoner", "Poisoned"))
        assertTrue(Status.isImpaired(state, lookup, 0L))

        // When the Demon kills them
        state = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)

        // Then nothing happens — say nothing (wiki Ex 2)
        assertFalse(has(state, 0L, "banshee", "Has Ability"))
        assertTrue(gate(state, "banshee") is StepGate.Skip)

        // And a Lycanthrope kill on a sober Banshee is equally silent (wiki Ex 3).
        var other = atNight(game("banshee", "imp", "lycanthrope", "chef", "mayor", "monk"), 2)
        other = kill(other, 0L, DeathCause.GOOD_ABILITY, "lycanthrope", 2L)
        assertFalse(has(other, 0L, "banshee", "Has Ability"))
        assertTrue(gate(other, "banshee") is StepGate.Skip)
    }

    // ==================================================================
    // bountyhunter
    // ==================================================================

    @Test
    fun `the Bounty Hunter wakes only once the player they know has died`() {
        // Given the Known player alive on night 3
        var state = atNight(game("bountyhunter", "imp", "poisoner", "chef", "mayor"), 3)
        state = Effects.addReminder(state, 2L, PlacedReminder("bountyhunter", "Know"))
        val skipped = assertNotNull(gate(state, "bountyhunter"))
        assertTrue(skipped is StepGate.Skip, "expected a skip, got $skipped")
        assertFalse(step(state, "bountyhunter")!!.required, "a skipped row never blocks dawn")

        // When the Known player dies tonight
        state = kill(state, 2L, DeathCause.DEMON_KILL, "imp", 1L)

        // Then the Bounty Hunter learns a new evil player, and the token moves
        assertEquals(StepGate.Fire, gate(state, "bountyhunter"))
        state = run(state, "bountyhunter", NightInput(playerIds = listOf(1L)))
        assertFalse(has(state, 2L, "bountyhunter", "Know"))
        assertTrue(has(state, 1L, "bountyhunter", "Know"))
    }

    // ==================================================================
    // cannibal
    // ==================================================================

    @Test
    fun `the Cannibal wakes at the executee's slot, never at one of its own`() {
        // Given a Cannibal who ate an executed Empath
        var state = game("cannibal", "imp", "empath", "chef", "mayor")
        state = day(state, 1)
        state = kill(state, 2L, DeathCause.EXECUTION, "")
        state = Effects.addReminder(state, 2L, PlacedReminder("cannibal", "Lunch"))
        state = atNight(state, 2)

        // Then the plan wakes the CANNIBAL'S seat at the Empath's index
        val row = assertNotNull(step(state, "empath"), "no borrowed step")
        assertEquals(0L, row.holderId, "the Cannibal's seat, not the dead Empath's")
        assertEquals("cannibal", row.sourceId)
        assertNull(step(state, "cannibal"), "the Cannibal has no slot of its own")
    }

    // ==================================================================
    // choirboy
    // ==================================================================

    @Test
    fun `the Choirboy row exists only when the Demon killed the King`() {
        // Given a living King on night 2
        var state = atNight(game("choirboy", "king", "imp", "assassin", "chef", "mayor"), 2)
        assertTrue(gate(state, "choirboy") is StepGate.Skip, "no row while the King lives")

        // When an Assassin kills the King
        val byAssassin = kill(state, 1L, DeathCause.EVIL_ABILITY, "assassin", 3L)
        assertTrue(gate(byAssassin, "choirboy") is StepGate.Skip, "a Minion kill does not count")

        // When the Demon kills the King
        state = kill(state, 1L, DeathCause.DEMON_KILL, "imp", 2L)

        // Then the Choirboy learns the Demon
        assertEquals(StepGate.Fire, gate(state, "choirboy"))
        // And never on the first night.
        assertNull(step(game("choirboy", "king", "imp", "assassin", "chef", "mayor"), "choirboy"))
    }

    // ==================================================================
    // W7G — the registry slots that had no consumer
    // ==================================================================

    @Test
    fun `the King's Leviathan jinx applies because the Leviathan is on the SCRIPT`() {
        // A script that LISTS the Leviathan, with a Baron dealt in its place: a
        // jinx applies from the script, never from the bag (lead D19, the Djinn).
        val jinxed = atNight(game("king", "leviathan", "imp", "chef", "mayor", "monk"), 2)
            .let { s ->
                s.copy(players = s.players.map { if (it.id == 1L) it.copy(characterId = "baron") else it })
            }
        val row = assertNotNull(step(jinxed, "king"))
        assertTrue(row.badges.any { "jinx" in it }, "the row says why: ${row.badges}")
        // The King's own prompt survives: a jinx overrides only what it declares.
        assertTrue(row.prompt.startsWith("Show the King"), row.prompt)

        // The jinx drops the threshold from "dead >= alive" to "at least 1 dead".
        val oneDead = kill(jinxed, 5L, DeathCause.DEMON_KILL, "imp", 2L)
        assertIs<StepGate.Fire>(assertNotNull(step(oneDead, "king")).gate)

        // Without the Leviathan on the script, one death is not enough.
        val plain = atNight(game("king", "imp", "chef", "mayor", "monk"), 2)
        val plainOneDead = kill(plain, 4L, DeathCause.DEMON_KILL, "imp", 1L)
        assertTrue(assertNotNull(step(plainOneDead, "king")).gate is StepGate.Skip)
    }

    @Test
    fun `a preached Minion's step is skipped, while a poisoned one still wakes`() {
        var state = atNight(game("preacher", "imp", "poisoner", "chef", "mayor"), 2)
        assertIs<StepGate.Fire>(assertNotNull(step(state, "poisoner")).gate)

        // W7G: an ability TAKEN AWAY is not an impairment — there is nothing left
        // to wake for, so the row is auto-ticked with the reason.
        val preached = run(state, "preacher", NightInput(playerIds = listOf(2L)))
        val gate = assertIs<StepGate.Skip>(assertNotNull(step(preached, "poisoner")).gate)
        assertTrue("taken away" in gate.reason, gate.reason)

        // Poison is different: the seat still wakes and is lied to.
        state = Effects.place(
            state, 2L, EffectKind.POISONED, "storyteller", null, Until.DAWN, "Poisoned",
        ).state
        assertIs<StepGate.Fire>(assertNotNull(step(state, "poisoner")).gate)
    }

    @Test
    fun `a day ability is offered through DayAbilities, greyed rather than dropped`() {
        val state = day(game("fisherman", "alsaahir", "imp", "poisoner", "chef", "mayor"), 2)
        val offered = DayAbilities.forState(state, lookup)
        assertEquals(
            setOf("fisherman", "alsaahir"),
            offered.map { it.sourceId }.toSet(),
            "the strip had no consumer at all before wave 7",
        )
        val fisherman = assertNotNull(offered.firstOrNull { it.sourceId == "fisherman" })
        assertEquals(0L, fisherman.holderId)
        assertTrue(fisherman.available)
        assertTrue(fisherman.ability.label.isNotBlank())

        // A dead holder is still LISTED, greyed with a reason (lead D37).
        val dead = kill(state, 0L, DeathCause.EXECUTION, "")
        val after = assertNotNull(
            DayAbilities.forState(dead, lookup).firstOrNull { it.sourceId == "fisherman" },
        )
        assertFalse(after.available)
        assertTrue(after.reason.isNotBlank(), "greyed with no reason")
        assertTrue(DayAbilities.availableIn(dead, lookup).none { it.sourceId == "fisherman" })
    }

    // ==================================================================
    // cultleader
    // ==================================================================

    @Test
    fun `the Cult Leader is asked for the outcome every night while alive`() {
        // Given a living Cult Leader
        var state = game("cultleader", "imp", "poisoner", "chef", "mayor")

        // Then both nights offer the ST the OUTCOME, not a target. W7E made it a
        // three-way [Options] — "no change" is a real answer and wakes nobody.
        for (night in listOf(1, 2)) {
            val row = assertNotNull(step(atNight(state, night), "cultleader"), "night $night")
            assertEquals(StepGate.Fire, row.gate)
            val options = assertIs<Options>(row.action).options
            assertEquals(listOf("none", "evil", "good"), options.map { it.id })
        }

        // When the storyteller says they join the evil neighbour…
        val flipped = run(atNight(state, 2), "cultleader", NightInput(optionId = "evil"))
        // …the alignment REALLY changes, and it is not a character change.
        assertTrue(assertNotNull(flipped.player(0L)).isEvil(lookup))
        assertEquals("cultleader", assertNotNull(flipped.player(0L)).characterId)
        assertTrue(flipped.identityLog.none { it.playerId == 0L })

        // "No change" changes nothing.
        val same = run(atNight(state, 2), "cultleader", NightInput(optionId = "none"))
        assertFalse(assertNotNull(same.player(0L)).isEvil(lookup))

        // When the Cult Leader dies
        state = kill(atNight(state, 2), 0L, DeathCause.DEMON_KILL, "imp", 1L)

        // Then there is no row at all
        assertTrue(gate(state, "cultleader") is StepGate.Skip)
    }

    // ==================================================================
    // engineer
    // ==================================================================

    @Test
    fun `the Engineer spends once and then leaves the night sheet`() {
        // Given an unspent Engineer
        var state = atNight(game("engineer", "imp", "poisoner", "baron", "chef", "mayor"), 2)
        assertEquals(StepGate.Fire, gate(state, "engineer"))

        // When they decline
        val declined = run(state, "engineer", NightInput(none = true))
        assertFalse(has(declined, 0L, "engineer", "No Ability"), "a decline never spends")
        assertEquals(StepGate.Fire, gate(atNight(declined, 3), "engineer"))

        // When they rebuild the Minions
        state = run(state, "engineer", NightInput(playerIds = listOf(2L, 3L)))

        // Then the ability is spent and the row is gone for good
        assertTrue(has(state, 0L, "engineer", "No Ability"))
        assertTrue(Memory.isSpent(state, "engineer", 0L))
        assertTrue(gate(atNight(state, 3), "engineer") is StepGate.Skip)
    }

    // ==================================================================
    // farmer
    // ==================================================================

    @Test
    fun `a Farmer killed at night passes the character on, an executed one does not`() {
        // Given a Farmer executed on day 2
        var executed = day(game("farmer", "imp", "poisoner", "chef", "mayor"), 2)
        executed = kill(executed, 0L, DeathCause.EXECUTION, "")
        assertTrue(gate(atNight(executed, 3), "farmer") is StepGate.Skip, "executed — no new Farmer")

        // Given a sober Farmer killed at night
        var state = atNight(game("farmer", "imp", "poisoner", "chef", "mayor"), 2)
        state = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)
        assertEquals(StepGate.Fire, gate(state, "farmer"))

        // When a living good player is chosen
        state = run(state, "farmer", NightInput(playerIds = listOf(3L)))

        // Then they ARE the Farmer, and get no first-night information
        assertEquals("farmer", state.player(3L)!!.characterId)
        assertTrue(
            plan(state).steps.none { it.holderId == 3L && it.key.variant == StepVariant.FIRST },
            "new Farmers do not receive first-night information",
        )
    }

    @Test
    fun `a poisoned Farmer's death still shows the row, with the rule applied`() {
        // Given a poisoned Farmer
        var state = atNight(game("farmer", "imp", "poisoner", "chef", "mayor"), 2)
        state = Effects.addReminder(state, 0L, PlacedReminder("poisoner", "Poisoned"))

        // When the Demon kills them
        state = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)

        // Then nobody becomes a Farmer, and the storyteller is told why
        val skipped = assertNotNull(gate(state, "farmer"))
        assertTrue(skipped is StepGate.Skip && "poisoned" in skipped.reason, "$skipped")
    }

    // ==================================================================
    // general
    // ==================================================================

    @Test
    fun `the General is offered three judgement cards, not an alignment reveal`() {
        // Given a living General
        val state = game("general", "imp", "poisoner", "chef", "mayor")

        // Then every night offers the three answers, one tap each
        for (night in listOf(1, 3)) {
            val row = assertNotNull(step(atNight(state, night), "general"), "night $night")
            val subtitles = row.cards.map { it.card }.filterIsInstance<ShowCardSpec.Message>()
                .map { it.subtitle }
            assertEquals(
                listOf("GOOD IS WINNING", "EVIL IS WINNING", "NEITHER TEAM IS WINNING"),
                subtitles,
                "the General learns who is winning, never 'YOU ARE GOOD'",
            )
        }

        // And a dead General has no row at all.
        val dead = kill(atNight(state, 2), 0L, DeathCause.DEMON_KILL, "imp", 1L)
        assertTrue(gate(dead, "general") is StepGate.Skip)
    }

    // ==================================================================
    // lycanthrope
    // ==================================================================

    @Test
    fun `a Lycanthrope kill also silences the Demon for the rest of the night`() {
        // Given a sober Lycanthrope on night 2 (no Mayor: a bounce is a Redirect,
        // which would hide whether the kill was blocked)
        var state = atNight(game("lycanthrope", "imp", "poisoner", "chef", "butler", "monk"), 2)

        // When they choose a good player
        state = run(state, "lycanthrope", NightInput(playerIds = listOf(3L)))

        // Then that player dies to the LYCANTHROPE, not to the Demon
        assertFalse(state.player(3L)!!.alive)
        val event = state.deaths.last { it.playerId == 3L }
        assertEquals(DeathCause.GOOD_ABILITY, event.cause)
        assertEquals("lycanthrope", event.killerCharacterId)
        assertTrue(has(state, 3L, "lycanthrope", "Dead"))

        // And the Demon's own kill is blocked through the ordinary funnel — the
        // Demon still wakes and still chooses, and must never learn it failed.
        val outcome = Deaths.killOutcome(
            state, lookup, 4L,
            KillCause(DeathCause.DEMON_KILL, "imp", 1L),
        )
        assertTrue(outcome is KillOutcome.Prevented, "$outcome")
        assertEquals(EffectKind.DEMON_CANNOT_KILL, (outcome as KillOutcome.Prevented).by?.kind)
        // A non-Demon night kill is untouched.
        assertTrue(
            Deaths.killOutcome(
                state, lookup, 4L,
                KillCause(DeathCause.EVIL_ABILITY, "poisoner", 2L),
            ) is KillOutcome.Dies,
        )
        // Lead D68: the Lycanthrope's clause is NO_KILL_TONIGHT, not the
        // Exorcist's SILENCED — the difference is whether a kill the Demon set up
        // on an EARLIER night still lands.
        assertEquals(
            KillSuppression.NO_KILL_TONIGHT,
            Status.live(state, lookup, 1L, EffectKind.DEMON_CANNOT_KILL).single().suppression,
        )
    }

    @Test
    fun `Faux Paw makes one good player register evil while the Lycanthrope works`() {
        // Given a Faux Paw on a good seat
        var state = atNight(game("lycanthrope", "imp", "poisoner", "chef", "mayor", "monk"), 2)
        state = Effects.addReminder(state, 3L, PlacedReminder("lycanthrope", "Faux Paw"))

        // Then that player registers evil, though they still win with good
        assertTrue(Registration.registersEvil(state, lookup, state.player(3L)!!))
        assertFalse(state.player(3L)!!.isEvil(lookup), "their TRUE alignment is unchanged")

        // When the Lycanthrope dies, the misregistration ends with them
        state = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)
        assertFalse(Registration.registersEvil(state, lookup, state.player(3L)!!))
    }

    // ==================================================================
    // magician
    // ==================================================================

    @Test
    fun `the Magician gets an informational row and never wakes`() {
        // Given a Magician
        val state = game("magician", "imp", "poisoner", "baron", "chef", "mayor", "monk", "butler")

        // Then their own row exists on the first night, and counts as no wake
        val row = assertNotNull(step(state, "magician"))
        assertEquals(StepGate.Fire, row.gate)
        assertEquals(WakeCount.NONE, row.wakeCounts)
        assertNull(row.action, "the Magician chooses nothing")

        // And they never wake again.
        assertNull(step(atNight(state, 2), "magician"))
    }

    @Test
    fun `the Magician is interleaved into both info rows and hides the Marionette`() {
        // W7I: the ability is a CONTENT TRANSFORM of the two shared info rows,
        // which `NightInfo` owns. Before wave 7 those rows told the storyteller
        // to do the exact opposite of this character.
        var state = game(
            "magician", "imp", "poisoner", "baron", "marionette",
            "chef", "mayor", "monk",
        )
        val plan = NightPlan.build(state, lookup)
        val minionInfo = assertNotNull(plan.steps.firstOrNull { it.slotId == "MINION_INFO" })
        val demonInfo = assertNotNull(plan.steps.firstOrNull { it.slotId == "DEMON_INFO" })

        // "Minions think you are a Demon": the Magician is shown beside the Imp.
        assertTrue("MAGICIAN" in minionInfo.detail, minionInfo.detail)
        assertTrue("P1" in minionInfo.detail, "the Magician's seat is named: ${minionInfo.detail}")

        // "The Demon thinks you are a Minion": the Magician is in the Minion list,
        // and the Marionette clause is SUPPRESSED — otherwise the Demon could
        // subtract the Marionette and find the Magician.
        assertTrue("MAGICIAN" in demonInfo.detail, demonInfo.detail)
        assertTrue(
            "Do not point out the Marionette" in demonInfo.detail,
            demonInfo.detail,
        )

        // A DRUNK Magician confuses nobody: both rows go back to the truth.
        state = Effects.place(
            state, 0L, EffectKind.POISONED, "storyteller", null, Until.DUSK, "Poisoned",
        ).state
        val sober = NightPlan.build(state, lookup)
        val demonAgain = assertNotNull(sober.steps.firstOrNull { it.slotId == "DEMON_INFO" })
        assertFalse("MAGICIAN" in demonAgain.detail, demonAgain.detail)
        assertTrue("Point out the Marionette" in demonAgain.detail, demonAgain.detail)
    }

    @Test
    fun `a Vizier tells the Demon who they are, so the Magician row says the jinx`() {
        val state = game(
            "magician", "imp", "vizier", "baron", "chef", "mayor", "monk", "butler",
        )
        val demonInfo = assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull { it.slotId == "DEMON_INFO" },
        )
        assertTrue("JINX" in demonInfo.detail, demonInfo.detail)
        assertTrue("Vizier" in demonInfo.detail, demonInfo.detail)
    }

    // ==================================================================
    // nightwatchman / huntsman — once per game
    // ==================================================================

    @Test
    fun `the Nightwatchman shows themselves once and never appears again`() {
        // Given an unspent Nightwatchman
        var state = atNight(game("nightwatchman", "imp", "poisoner", "chef", "mayor"), 2)
        assertEquals(StepGate.Fire, gate(state, "nightwatchman"))

        // When they decline, nothing is spent
        val declined = run(state, "nightwatchman", NightInput(none = true))
        assertFalse(has(declined, 0L, "nightwatchman", "No Ability"))

        // When they choose a (dead) player
        state = kill(state, 4L, DeathCause.DEMON_KILL, "imp", 1L)
        state = run(state, "nightwatchman", NightInput(playerIds = listOf(4L)))

        // Then the target was told, and the ability is gone from the sheet
        assertTrue(has(state, 0L, "nightwatchman", "No Ability"))
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.TOLD && it.actorId == 4L },
            "a dead target is a legal target and is told",
        )
        assertTrue(gate(atNight(state, 3), "nightwatchman") is StepGate.Skip)
    }

    @Test
    fun `the Huntsman's guess is spent whether or not it found the Damsel`() {
        var state = game("huntsman", "damsel", "imp", "poisoner", "chef", "mayor")
        assertEquals(StepGate.Fire, gate(state, "huntsman"))
        state = run(state, "huntsman", NightInput(playerIds = listOf(4L)))
        assertTrue(has(state, 0L, "huntsman", "No Ability"))
        assertTrue(gate(atNight(state, 2), "huntsman") is StepGate.Skip)
    }

    // ==================================================================
    // king
    // ==================================================================

    @Test
    fun `night one is the Demon's step and later nights need the dead to catch up`() {
        // Given a King on night 1
        val state = game("king", "imp", "poisoner", "chef", "monk", "butler")
        val first = assertNotNull(step(state, "king"))

        // Then the row is about the DEMON: it fires regardless, and is not a King wake
        assertEquals(StepGate.Fire, first.gate)
        assertEquals(WakeCount.INFORMED, first.wakeCounts)
        assertTrue(
            first.cards.any { it.card == ShowCardSpec.CharacterCard("THIS PLAYER IS", "king") },
            first.cards.map { it.label }.toString(),
        )
        // W7H: `king.demon` has a calculator now, so the row also pre-fills the
        // card that POINTS at the King — and the lies beside it.
        val point = assertNotNull(
            first.cards.map { it.card }.filterIsInstance<ShowCardSpec.PointCard>().firstOrNull(),
        )
        assertEquals(listOf(1), point.seatNumbers, "seat 1 holds the King")
        assertEquals("king", point.characterId)
        assertTrue(first.cards.any { !it.truthful }, "and a lie is offered")

        // Given 1 dead of 6 on night 2, the King does not wake
        var later = atNight(state, 2)
        later = kill(later, 5L, DeathCause.DEMON_KILL, "imp", 1L)
        assertTrue(gate(later, "king") is StepGate.Skip, "1 dead vs 5 alive")

        // When the dead EQUAL the living, they do
        for (seat in listOf(4L, 3L)) later = kill(later, seat, DeathCause.DEMON_KILL, "imp", 1L)
        assertEquals(3, later.seats.count { !it.alive })
        assertEquals(3, later.seats.count { it.alive })
        assertEquals(StepGate.Fire, gate(later, "king"), "equality qualifies")
    }

    // ==================================================================
    // knight / noble / steward / shugenja — first-night info
    // ==================================================================

    @Test
    fun `the start-knowing rows fire on the first night only`() {
        for (id in listOf("knight", "noble", "steward", "shugenja")) {
            val state = game(id, "imp", "poisoner", "chef", "mayor", "monk")
            val row = assertNotNull(step(state, id), "$id has no first-night row")
            assertEquals(StepGate.Fire, row.gate, id)
            assertTrue(row.action is ShowInfo, "$id shows, never chooses: ${row.action}")
            assertNull(step(atNight(state, 2), id), "$id must not wake again")
        }
    }

    // ==================================================================
    // pixie
    // ==================================================================

    @Test
    fun `the Pixie is asked to judge the madness when their character dies`() {
        // Given a Pixie made mad that they are the Empath
        var state = game("pixie", "empath", "imp", "poisoner", "chef", "mayor")
        state = run(state, "pixie", NightInput(characterIds = listOf("empath")))
        assertTrue(has(state, 0L, "pixie", "Mad"))

        // When the Empath dies
        state = kill(atNight(state, 2), 1L, DeathCause.DEMON_KILL, "imp", 2L)

        // Then the storyteller is asked, never silently granted
        val owed = state.prompts.single { it.sourceId == "pixie" }
        assertEquals(PromptKind.PLACE_EFFECT, owed.kind)
        assertEquals(listOf("empath"), owed.characterIds)
        assertEquals(0L, owed.subjectPlayerId)
        assertFalse(has(state, 0L, "pixie", "Has Ability"), "granting is a judgement, not automatic")
    }

    @Test
    fun `a Pixie who already gained the ability is never re-prompted`() {
        var state = game("pixie", "empath", "imp", "poisoner", "chef", "mayor")
        state = run(state, "pixie", NightInput(characterIds = listOf("empath")))
        state = Effects.addReminder(state, 0L, PlacedReminder("pixie", "Has Ability"))
        state = kill(atNight(state, 2), 1L, DeathCause.DEMON_KILL, "imp", 2L)
        assertTrue(state.prompts.none { it.sourceId == "pixie" })
    }

    // ==================================================================
    // poppygrower
    // ==================================================================

    @Test
    fun `a living Poppy Grower keeps evil apart and owes no reveal`() {
        // Given an 8-player game with a Poppy Grower
        val state = game(
            "poppygrower", "imp", "poisoner", "baron", "chef", "mayor", "monk", "butler",
        )

        // Then Minion info is a visible, auto-ticked skip, never a silent removal
        val minions = assertNotNull(
            plan(state).steps.firstOrNull { it.slotId == NightMarkers.MINION_INFO },
        )
        val skipped = minions.gate
        assertTrue(skipped is StepGate.Skip && "Poppy Grower" in skipped.reason, "$skipped")
        assertFalse(minions.required, "a skipped row never blocks dawn")
        // And the Demon's row becomes a bluffs-only row.
        assertNotNull(plan(state).steps.firstOrNull { it.slotId == NightMarkers.DEMON_BLUFFS_ONLY })
        assertNull(plan(state).steps.firstOrNull { it.slotId == NightMarkers.DEMON_INFO })

        // And no reveal is owed while they live.
        assertTrue(gate(atNight(state, 3), "poppygrower") is StepGate.Skip)
    }

    @Test
    fun `a Poppy Grower executed today owes the reveal tonight, once`() {
        // Given a Poppy Grower executed on day 2
        var state = day(game("poppygrower", "imp", "poisoner", "baron", "chef", "mayor", "monk", "butler"), 2)
        state = kill(state, 0L, DeathCause.EXECUTION, "")

        // When night 3 is planned
        state = atNight(state, 3)
        assertEquals(StepGate.Fire, gate(state, "poppygrower"), "evil meet tonight")

        // Then running it records the reveal, and night 4 owes nothing
        state = run(state, "poppygrower", NightInput())
        assertTrue(has(state, 0L, "poppygrower", "Evil Wakes"))
        assertTrue(gate(atNight(state, 4), "poppygrower") is StepGate.Skip)
    }

    @Test
    fun `a Poppy Grower impaired at death triggers no reveal at all`() {
        var state = day(game("poppygrower", "imp", "poisoner", "baron", "chef", "mayor", "monk", "butler"), 2)
        state = Effects.addReminder(state, 0L, PlacedReminder("poisoner", "Poisoned"))
        state = kill(state, 0L, DeathCause.EXECUTION, "")
        val skipped = assertNotNull(gate(atNight(state, 3), "poppygrower"))
        assertTrue(skipped is StepGate.Skip && "poisoned" in skipped.reason, "$skipped")
    }

    // ==================================================================
    // preacher
    // ==================================================================

    @Test
    fun `a preached Minion loses their ability and gets it back with the Preacher`() {
        // Given a sober Preacher and a Poisoner
        var state = game("preacher", "imp", "poisoner", "chef", "mayor")

        // When the Preacher chooses the Poisoner
        state = run(state, "preacher", NightInput(playerIds = listOf(2L)))

        // Then the Minion has no ability
        assertTrue(has(state, 2L, "preacher", "No Ability"))
        assertFalse(Status.hasAbility(state, lookup, 2L))

        // When the Preacher is poisoned by the storyteller, the suppression lapses
        // (endsWithSource does the whole job — no extra code anywhere).
        val poisoned = Effects.addReminder(
            state, 0L, PlacedReminder(Tokens.STORYTELLER_SOURCE, "Poisoned"),
        )
        assertTrue(Status.hasAbility(poisoned, lookup, 2L), "the Preacher's suppression is dormant")

        // And it ends outright when the Preacher dies.
        val dead = kill(state, 0L, DeathCause.DEMON_KILL, "imp", 1L)
        assertTrue(Status.hasAbility(dead, lookup, 2L), "a dead Preacher frees the Minion")

        // And it survives dawn and dusk while the Preacher is well.
        var swept = state
        repeat(2) { swept = Phases.advancePhase(swept, lookup) }
        assertTrue(has(swept, 2L, "preacher", "No Ability"))
    }

    @Test
    fun `preaching a non-Minion places nothing`() {
        var state = game("preacher", "imp", "poisoner", "chef", "mayor")
        state = run(state, "preacher", NightInput(playerIds = listOf(3L)))
        assertFalse(has(state, 3L, "preacher", "No Ability"))
        assertTrue(Status.hasAbility(state, lookup, 3L))
    }

    // ==================================================================
    // princess
    // ==================================================================

    @Test
    fun `the Princess warns on her first day and blocks the Demon after the execution`() {
        // Given a Princess nominating on day 1
        var state = day(game("princess", "imp", "poisoner", "chef", "mayor"), 1)
        val check = DayRules.checkNomination(state, lookup, nominatorId = 0L, nomineeId = 3L)
        val warning = assertNotNull(check.triggers.firstOrNull { it.sourceId == "princess" })
        assertEquals(TriggerKind.WARN, warning.kind)

        // When that nomination is executed — even if the nominee SURVIVES
        state = state.copy(nominations = listOf(Nomination(day = 1, nominatorId = 0L, nomineeId = 3L)))
        val consequences = Execution.consequences(
            state, lookup,
            ExecutionRecord(1, ExecutionOutcome.SURVIVED, playerId = 3L, nominatorId = 0L),
        )
        assertNotNull(consequences.firstOrNull { it.sourceId == "princess" })

        // And a nomination by someone else raises nothing.
        val other = DayRules.checkNomination(state, lookup, nominatorId = 4L, nomineeId = 3L)
        assertTrue(other.triggers.none { it.sourceId == "princess" })

        // W7G: confirming the consequence PLACES the suppression, with the wider
        // NO_KILL_TONIGHT scope (lead D68) — the row no longer just says so.
        val record = ExecutionRecord(1, ExecutionOutcome.SURVIVED, playerId = 3L, nominatorId = 0L)
        assertTrue(Status.live(state, lookup, 1L, EffectKind.DEMON_CANNOT_KILL).isEmpty())
        val applied = Execution.applyConsequence(state, lookup, record, "princess")
        assertEquals(
            KillSuppression.NO_KILL_TONIGHT,
            Status.live(applied, lookup, 1L, EffectKind.DEMON_CANNOT_KILL).single().suppression,
        )
        assertTrue(
            Deaths.killOutcome(
                applied, lookup, 3L, KillCause(DeathCause.DEMON_KILL, "imp", 1L),
            ) is KillOutcome.Prevented,
        )
    }

    @Test
    fun `the Princess night row exists only while the Demon actually carries the block`() {
        // Given no qualifying execution
        var state = atNight(game("princess", "imp", "poisoner", "chef", "mayor"), 2)
        assertTrue(gate(state, "princess") is StepGate.Skip, "no row, so the ST never ticks blindly")

        // When the token is on the Demon
        state = Effects.addReminder(state, 1L, PlacedReminder("princess", "Doesn't Kill"))
        assertEquals(StepGate.Fire, gate(state, "princess"))

        // Then the Demon still chooses, but the kill is blocked
        val outcome = Deaths.killOutcome(state, lookup, 3L, KillCause(DeathCause.DEMON_KILL, "imp", 1L))
        assertTrue(outcome is KillOutcome.Prevented, "$outcome")
    }

    // ==================================================================
    // villageidiot
    // ==================================================================

    @Test
    fun `three Village Idiots are three rows and only the marked one is drunk`() {
        // Given three Village Idiots, the third marked Drunk at setup
        var state = atNight(
            game("villageidiot", "villageidiot", "villageidiot", "imp", "poisoner", "chef"), 2,
        )
        state = Effects.addReminder(state, 2L, PlacedReminder("villageidiot", "Drunk"))

        // Then each has their own row, their own token and their own caveat
        val rows = plan(state).steps.filter { it.abilityId == "villageidiot" }
        assertEquals(listOf(0L, 1L, 2L), rows.map { it.holderId })
        assertEquals(3, rows.map { it.key.token }.toSet().size)
        assertFalse(Status.isImpaired(state, lookup, 0L))
        assertFalse(Status.isImpaired(state, lookup, 1L))
        assertTrue(Status.isImpaired(state, lookup, 2L), "the drunk one is seat 3, not seat 1")

        // And the setup mark never moves and never expires.
        var swept = state
        repeat(4) { swept = Phases.advancePhase(swept, lookup) }
        assertTrue(has(swept, 2L, "villageidiot", "Drunk"))
        assertTrue(Status.isImpaired(swept, lookup, 2L))
    }

    // ==================================================================
    // highpriestess — a judgement row on every night
    // ==================================================================

    @Test
    fun `the High Priestess picks any seat, alive or dead, every night`() {
        var state = atNight(game("highpriestess", "imp", "poisoner", "chef", "mayor"), 2)
        state = kill(state, 4L, DeathCause.DEMON_KILL, "imp", 1L)
        val action = assertNotNull(step(state, "highpriestess")?.action) as ChoosePlayers
        assertTrue(TargetConstraint.ANY_LIVING_STATE in action.constraints, "dead seats are legal")
        state = run(state, "highpriestess", NightInput(playerIds = listOf(4L)))
        assertEquals(
            listOf(4L),
            assertNotNull(Memory.by(state, LedgerKind.CHOICE, "highpriestess").lastOrNull()).targetIds,
        )
    }
}
