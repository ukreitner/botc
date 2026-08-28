package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WP7-TRAV — the 18 Traveller registry rows, driven through the real pipeline:
 * `NightPlan.build` / `NightPlan.resolve`, `DayRules`, `Execution`, `Deaths`
 * and `Status`. Nothing here pokes at a lambda directly except the `DayAbility`
 * availability predicates, which have no other consumer yet.
 */
class RulesTravellersTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val tb = data.builtInScripts().first { it.id == "tb" }
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val sv = data.builtInScripts().first { it.id == "sv" }

    /** The ids in this package's scope, read from the data rather than hard-coded. */
    private val travellerIds: List<String> = data.characters
        .filter { it.team == Team.TRAVELLER }
        .map { Character.normalizeId(it.id) }

    // ==================================================================
    // helpers
    // ==================================================================

    /** A seated game in NIGHT 1, named P1..Pn. Residents unless [travellers] says otherwise. */
    private fun game(script: Script, vararg roles: String): GameState {
        var state = GameActions.newGame(script, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id ->
            val isTraveller = lookup(id)?.team == Team.TRAVELLER
            state = GameActions.assignCharacter(state, i.toLong(), id, isTraveller)
        }
        return GameActions.advancePhase(state, lookup)
    }

    private fun seatOf(state: GameState, characterId: String): Player =
        assertNotNull(
            state.seats.firstOrNull {
                it.characterId?.let(Character::normalizeId) == Character.normalizeId(characterId)
            },
            "no seat holds $characterId",
        )

    private fun plan(state: GameState) = NightPlan.build(state, lookup)

    private fun step(state: GameState, abilityId: String): NightStep? =
        plan(state).steps.firstOrNull { it.abilityId == Character.normalizeId(abilityId) }

    private fun resolve(state: GameState, abilityId: String, input: NightInput): GameState {
        val row = assertNotNull(step(state, abilityId), "no $abilityId step tonight")
        return NightPlan.resolve(state, lookup, row.key, input)
    }

    /** Runs a whole day so the next night is night [n]. */
    private fun toNight(start: GameState, n: Int): GameState {
        var state = start
        while (state.cycle < n || state.phase != Phase.NIGHT) {
            state = GameActions.advancePhase(state, lookup)
        }
        return state
    }

    private fun setEvil(state: GameState, playerId: Long, evil: Boolean): GameState =
        Seats.setAlignment(state, playerId, if (evil) Alignment.EVIL else Alignment.GOOD)

    private fun mark(state: GameState, playerId: Long, sourceId: String, label: String): GameState =
        Effects.reconcile(
            Effects.addReminder(state, playerId, PlacedReminder(sourceId, label)),
            lookup,
        )

    // ==================================================================
    // Table tests (acceptance criteria)
    // ==================================================================

    @Test
    fun `every traveller in the data has a registry rule`() {
        assertEquals(18, travellerIds.size, "the scope is the 18 travellers in characters.json")
        val missing = travellerIds.filter { it !in CharacterRules.all }
        assertTrue(missing.isEmpty(), "travellers with no CharacterRule: $missing")
    }

    @Test
    fun `every traveller token names a real reminder with the official copy count`() {
        val problems = mutableListOf<String>()
        for (rule in Tokens.all) {
            val source = Character.normalizeId(rule.sourceId)
            if (source !in travellerIds) continue
            val character = lookup(source)
            if (character == null) {
                problems += "$source is not a character"
                continue
            }
            val copies = character.allReminders.count { it.trim().equals(rule.label.trim(), true) }
            if (copies == 0) {
                problems += "$source/${rule.label} — data has ${character.allReminders}"
            } else if (copies != rule.copies) {
                problems += "$source/${rule.label}: rule says ${rule.copies}, data has $copies"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the traveller tokens this package adds to the registry are reachable`() {
        assertNotNull(Tokens.rule("bishop", "Nominate Good"))
        assertNotNull(Tokens.rule("bishop", "Nominate Evil"))
        assertNotNull(Tokens.rule("gnome", "Amigo"))
        assertEquals(Until.FOREVER, Tokens.rule("gnome", "Amigo")!!.until)
        assertEquals(2, Tokens.rule("harlot", "Dead")!!.copies)
        assertEquals(2, Tokens.rule("barista", "?")!!.copies)
        assertEquals(EffectKind.SPENT, Tokens.rule("bonecollector", "No Ability")!!.effect)
    }

    // ==================================================================
    // Trouble Brewing
    // ==================================================================

    @Test
    fun `a poisoned Beggar is sober and healthy anyway`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "beggar")
        val beggar = seatOf(state, "beggar").id
        val monk = seatOf(state, "monk").id

        state = mark(state, beggar, "poisoner", "Poisoned")
        state = mark(state, monk, "poisoner", "Poisoned")

        assertTrue(Status.impairment(state, lookup, beggar).isEmpty(), "the Beggar is sober & healthy")
        assertTrue(Status.hasAbility(state, lookup, beggar))
        assertFalse(Status.impairment(state, lookup, monk).isEmpty(), "the Monk is genuinely poisoned")
        // The innate rule must not sustain itself: that would trip the in-flight
        // guard and report a paradox on a board that has none.
        assertTrue(Status.paradoxSeats(state, lookup).isEmpty())
        assertTrue(state.prompts.none { it.kind == PromptKind.DECIDE && it.sourceId == "status" })

        // A dead Beggar has no ability, so the token bites again.
        val dead = GameActions.kill(state, beggar, DeathCause.EXILE, lookup)
        assertFalse(Status.hasAbility(dead, lookup, beggar))
    }

    @Test
    fun `the Bureaucrat marks a seat and that seat's vote counts three`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "bureaucrat")
        val bureaucrat = seatOf(state, "bureaucrat").id
        val target = seatOf(state, "washerwoman").id

        state = resolve(state, "bureaucrat", NightInput(playerIds = listOf(target)))
        assertTrue(DayRules.hasToken(state, target, "bureaucrat", "3 Votes"))

        state = GameActions.advancePhase(state, lookup) // NIGHT 1 -> DAY 1
        assertEquals(3, DayRules.voteRules(state, lookup, isExile = false).weightOf(target))
        assertEquals(1, DayRules.voteRules(state, lookup, isExile = false).weightOf(bureaucrat))
        // "Exiles are not affected by abilities."
        assertTrue(DayRules.voteRules(state, lookup, isExile = true).weights.isEmpty())

        state = GameActions.advancePhase(state, lookup) // DAY 1 -> NIGHT 2: the token expires
        assertFalse(DayRules.hasToken(state, target, "bureaucrat", "3 Votes"))
    }

    @Test
    fun `the Bureaucrat cannot mark themselves and does not act while dead`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "bureaucrat")
        val bureaucrat = seatOf(state, "bureaucrat").id

        val self = NightPlan.resolve(
            state,
            lookup,
            assertNotNull(step(state, "bureaucrat")).key,
            NightInput(playerIds = listOf(bureaucrat)),
        )
        assertFalse(DayRules.hasToken(self, bureaucrat, "bureaucrat", "3 Votes"), "NOT_SELF")

        state = GameActions.kill(state, bureaucrat, DeathCause.EXILE, lookup)
        val gate = assertNotNull(step(state, "bureaucrat")).gate
        assertTrue(gate is StepGate.Skip, "a dead Bureaucrat has no ability: $gate")
    }

    @Test
    fun `the Thief marks a seat whose vote then counts minus one`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "thief")
        val target = seatOf(state, "monk").id

        state = resolve(state, "thief", NightInput(playerIds = listOf(target)))
        state = GameActions.advancePhase(state, lookup)

        assertEquals(-1, DayRules.voteRules(state, lookup, isExile = false).weightOf(target))
        // For an exile their support counts +1 like anyone's.
        assertEquals(1, DayRules.voteRules(state, lookup, isExile = true).weightOf(target))
    }

    @Test
    fun `a marked player carrying both marks counts minus one, and the mark is repeatable`() {
        var state = game(tb, "imp", "poisoner", "bureaucrat", "washerwoman", "thief")
        val target = seatOf(state, "washerwoman").id

        state = resolve(state, "bureaucrat", NightInput(playerIds = listOf(target)))
        state = resolve(state, "thief", NightInput(playerIds = listOf(target)))
        state = GameActions.advancePhase(state, lookup)
        assertEquals(-1, DayRules.voteRules(state, lookup, isExile = false).weightOf(target))

        // Repeat targets are legal — deliberately no DIFFERENT_FROM_LAST_NIGHT.
        state = GameActions.advancePhase(state, lookup) // -> NIGHT 2
        val row = assertNotNull(step(state, "thief"))
        val action = assertNotNull(row.action) as ChoosePlayers
        assertFalse(TargetConstraint.DIFFERENT_FROM_LAST_NIGHT in action.constraints)
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(target)))
        assertTrue(DayRules.hasToken(state, target, "thief", "Negative Vote"))
    }

    @Test
    fun `the vote mark stops working the moment the Bureaucrat is exiled`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "bureaucrat")
        val bureaucrat = seatOf(state, "bureaucrat").id
        val target = seatOf(state, "washerwoman").id
        state = resolve(state, "bureaucrat", NightInput(playerIds = listOf(target)))
        state = GameActions.advancePhase(state, lookup)

        fun liveMark(s: GameState) = Status.live(s, lookup, target)
            .any { Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("bureaucrat", "3 Votes") }
        assertTrue(liveMark(state))

        val exiled = GameActions.kill(state, bureaucrat, DeathCause.EXILE, lookup)
        assertFalse(liveMark(exiled), "the triple vote ends immediately with its source")

        val poisoned = mark(state, bureaucrat, "poisoner", "Poisoned")
        assertFalse(liveMark(poisoned), "and while the Bureaucrat's ability is not working")
    }

    @Test
    fun `the Gunslinger's window opens on the day's first execution vote, never on an exile`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "gunslinger")
        val gunslinger = seatOf(state, "gunslinger")
        val rule = assertNotNull(CharacterRules.all["gunslinger"]?.day?.ability)
        state = GameActions.advancePhase(state, lookup) // -> DAY 1

        assertFalse(rule.available(state, lookup, gunslinger), "no vote has been tallied yet")

        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = 2L, nomineeId = gunslinger.id, isExile = true),
        )
        assertFalse(rule.available(state, lookup, gunslinger), "an exile never opens the window")

        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = 2L, nomineeId = 0L, voterIds = listOf(2L, 3L)),
        )
        assertTrue(rule.available(state, lookup, gunslinger))
        assertEquals(DeathCause.DAY_ABILITY, CharacterRules.all["gunslinger"]!!.killCause)
    }

    @Test
    fun `a good Scapegoat is offered for a good executee and not for an evil one`() {
        var state = game(tb, "imp", "poisoner", "monk", "washerwoman", "scapegoat")
        val scapegoat = seatOf(state, "scapegoat")
        val good = seatOf(state, "washerwoman").id
        val evil = seatOf(state, "poisoner").id
        state = setEvil(state, scapegoat.id, false)
        state = GameActions.advancePhase(state, lookup)

        val forGood = Deaths.killOutcome(state, lookup, good, KillCause(DeathCause.EXECUTION))
        val choice = assertTrue(forGood is KillOutcome.Choice, "a Scapegoat makes it a choice: $forGood")
        assertTrue(
            (forGood as KillOutcome.Choice).options.any { it.id == Deaths.OPTION_REDIRECT },
            "the Scapegoat may die instead",
        )
        assertNotNull(choice)

        val forEvil = Deaths.killOutcome(state, lookup, evil, KillCause(DeathCause.EXECUTION))
        assertFalse(
            forEvil is KillOutcome.Choice &&
                forEvil.options.any { it.id == Deaths.OPTION_REDIRECT },
            "a GOOD Scapegoat is not offered for an evil executee: $forEvil",
        )
    }

    // ==================================================================
    // Bad Moon Rising
    // ==================================================================

    @Test
    fun `a good Apprentice is offered Townsfolk and an evil one Minions`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "apprentice")
        val apprentice = seatOf(state, "apprentice").id

        state = setEvil(state, apprentice, false)
        val good = assertNotNull(step(state, "apprentice")?.action) as ChooseCharacter
        assertEquals(CharacterPool.TOWNSFOLK, good.pool)

        state = setEvil(state, apprentice, true)
        val evil = assertNotNull(step(state, "apprentice")?.action) as ChooseCharacter
        assertEquals(CharacterPool.MINION, evil.pool)
    }

    @Test
    fun `the Apprentice keeps their token, gains Is The Apprentice, and stops waking once granted`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "apprentice")
        val seat = seatOf(state, "apprentice").id
        state = setEvil(state, seat, false)

        state = resolve(state, "apprentice", NightInput(characterIds = listOf("washerwoman")))
        assertTrue(DayRules.hasToken(state, seat, "apprentice", "Is The Apprentice"))
        assertEquals("apprentice", state.player(seat)?.characterId, "they do NOT become the character")
        assertTrue(assertNotNull(state.player(seat)).isTraveller)
        // W7E: the grant is REAL now — `NightEffect.GrantAbility` writes it, so
        // the row no longer has to ask the storyteller to add it by hand.
        val grant = assertNotNull(state.player(seat)).grants.single()
        assertEquals("washerwoman", grant.abilityId)
        assertEquals("apprentice", grant.sourceId)

        // And the Apprentice's own row is auto-ticked from now on, while the
        // granted one appears. The grant is ADD, not REPLACE: the Apprentice has
        // no ability of its own to displace, so the row stays and says why.
        val again = assertNotNull(step(state, "apprentice"))
        assertTrue(again.gate is StepGate.Skip, "spent: ${again.gate}")
        val washerwoman = assertNotNull(step(state, "washerwoman"), "the granted ability wakes")
        assertEquals(seat, washerwoman.holderId)
    }

    @Test
    fun `while a Bishop has their ability only the storyteller nominates`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "bishop")
        val bishop = seatOf(state, "bishop")
        state = setEvil(state, bishop.id, false)
        state = GameActions.advancePhase(state, lookup)

        val sailor = seatOf(state, "sailor").id
        assertFalse(DayRules.canNominate(state, lookup, sailor).allowed)
        assertTrue(DayRules.canNominate(state, lookup, sailor).reason.contains("Bishop"))

        val check = DayRules.checkNomination(state, lookup, nominatorId = null, nomineeId = sailor)
        val warn = assertNotNull(
            check.triggers.firstOrNull { it.sourceId == "bishop" },
            "the daily obligation is surfaced: ${check.triggers.map { it.sourceId }}",
        )
        assertEquals(TriggerKind.WARN, warn.kind)
        assertTrue(warn.headline.contains("evil"), "a good Bishop still owes an evil nominee")

        // Nominating an evil player discharges it.
        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = bishop.id, nomineeId = seatOf(state, "poisoner").id),
        )
        val after = DayRules.checkNomination(state, lookup, null, sailor)
            .triggers.first { it.sourceId == "bishop" }
        assertTrue(after.headline.contains("already discharged"), after.headline)
    }

    @Test
    fun `a dead Bishop stops blocking nominations`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "bishop")
        val bishop = seatOf(state, "bishop").id
        state = GameActions.advancePhase(state, lookup)
        state = GameActions.kill(state, bishop, DeathCause.EXILE, lookup)

        val sailor = seatOf(state, "sailor").id
        assertTrue(DayRules.canNominate(state, lookup, sailor).allowed)
        assertTrue(
            DayRules.checkNomination(state, lookup, sailor, seatOf(state, "tealady").id)
                .triggers.none { it.sourceId == "bishop" },
        )
    }

    @Test
    fun `the Judge may only rule on somebody else's nomination, once`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "judge")
        val judge = seatOf(state, "judge")
        val sailor = seatOf(state, "sailor").id
        val rule = assertNotNull(CharacterRules.all["judge"]?.day?.ability)
        assertTrue(rule.oncePerGame)
        state = GameActions.advancePhase(state, lookup)

        assertFalse(rule.available(state, lookup, judge), "nothing has been nominated yet")

        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = judge.id, nomineeId = sailor),
        )
        assertFalse(rule.available(state, lookup, judge), "not on their own nomination")

        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = sailor, nomineeId = judge.id),
        )
        assertTrue(rule.available(state, lookup, judge))

        // The official spend mark retires it, through Character.spentLabel.
        assertEquals("No Ability", lookup("judge")?.spentLabel)
        val spent = mark(state, judge.id, "judge", "No Ability")
        assertFalse(rule.available(spent, lookup, spent.player(judge.id)!!))
    }

    @Test
    fun `the Matron offers seat swaps while alive and unimpaired`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "matron")
        val matron = seatOf(state, "matron")
        val rule = assertNotNull(CharacterRules.all["matron"]?.day?.ability)
        state = GameActions.advancePhase(state, lookup)

        assertTrue(rule.available(state, lookup, matron))
        val poisoned = mark(state, matron.id, "poisoner", "Poisoned")
        assertFalse(rule.available(poisoned, lookup, poisoned.player(matron.id)!!))
    }

    @Test
    fun `a sober Voudon rewrites the vote and does not touch exiles`() {
        var state = game(bmr, "pukka", "poisoner", "sailor", "tealady", "voudon")
        val voudon = seatOf(state, "voudon").id
        val sailor = seatOf(state, "sailor").id
        state = GameActions.advancePhase(state, lookup)
        state = GameActions.kill(state, seatOf(state, "tealady").id, DeathCause.EXECUTION, lookup)
        val dead = seatOf(state, "tealady").id

        val rules = DayRules.voteRules(state, lookup, isExile = false)
        assertEquals(1, rules.threshold)
        assertFalse(rules.spendsGhostVotes)
        assertTrue(voudon in rules.eligibleVoterIds && dead in rules.eligibleVoterIds)
        assertFalse(sailor in rules.eligibleVoterIds, "living players' hands stay down")

        val exile = DayRules.voteRules(state, lookup, isExile = true)
        assertTrue(sailor in exile.eligibleVoterIds, "exiles are not affected by abilities")
        assertEquals(Voting.exileThreshold(state.seats.size), exile.threshold)

        // The registry must NOT shadow WP3's vote regime with a day row.
        assertNull(CharacterRules.all["voudon"]?.day?.onNomination)
    }

    // ==================================================================
    // Sects & Violets
    // ==================================================================

    @Test
    fun `the Barista sobers a poisoned player and never wakes for themselves`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "barista")
        val chef = seatOf(state, "chef").id
        state = mark(state, chef, "poisoner", "Poisoned")
        assertFalse(Status.impairment(state, lookup, chef).isEmpty())

        val row = assertNotNull(step(state, "barista"))
        assertEquals(WakeCount.NONE, row.wakeCounts, "the Barista learns nothing")

        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(chef), yes = true))
        assertTrue(DayRules.hasToken(state, chef, "barista", "Sober & Healthy"))
        assertTrue(Status.impairment(state, lookup, chef).isEmpty(), "SOBER & HEALTHY wins outright")

        // Until dusk: still there in the day, gone at the next night.
        state = GameActions.advancePhase(state, lookup)
        assertTrue(DayRules.hasToken(state, chef, "barista", "Sober & Healthy"))
        state = GameActions.advancePhase(state, lookup)
        assertFalse(DayRules.hasToken(state, chef, "barista", "Sober & Healthy"))
    }

    @Test
    fun `a dead Barista stops sobering their target and stops waking`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "barista")
        val chef = seatOf(state, "chef").id
        val barista = seatOf(state, "barista").id
        state = mark(state, chef, "poisoner", "Poisoned")
        state = resolve(state, "barista", NightInput(playerIds = listOf(chef), yes = true))
        assertTrue(Status.impairment(state, lookup, chef).isEmpty())

        state = GameActions.kill(state, barista, DeathCause.EXILE, lookup)
        assertFalse(
            Status.impairment(state, lookup, chef).isEmpty(),
            "SOBER & HEALTHY ends with its source",
        )
        state = toNight(state, 2)
        assertTrue(assertNotNull(step(state, "barista")).gate is StepGate.Skip)
    }

    @Test
    fun `only one Barista token exists at a time`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "barista")
        val chef = seatOf(state, "chef").id
        val saint = seatOf(state, "saint").id

        state = resolve(state, "barista", NightInput(playerIds = listOf(chef), yes = true))
        state = toNight(state, 2)
        state = resolve(state, "barista", NightInput(playerIds = listOf(saint), yes = false))

        assertFalse(DayRules.hasToken(state, chef, "barista", "Sober & Healthy"), "moved off the Chef")
        assertFalse(DayRules.hasToken(state, chef, "barista", "Acts Twice"))
        assertTrue(DayRules.hasToken(state, saint, "barista", "Acts Twice"))
        assertFalse(DayRules.hasToken(state, saint, "barista", "Sober & Healthy"))
    }

    @Test
    fun `the Bone Collector needs a dead player, spends once, and restores an ability`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "bonecollector")
        state = GameActions.advancePhase(state, lookup) // -> DAY 1
        state = toNight(state, 2)
        assertTrue(
            assertNotNull(step(state, "bonecollector")).gate is StepGate.Skip,
            "nobody is dead — nothing to restore",
        )

        val chef = seatOf(state, "chef").id
        state = GameActions.kill(state, chef, DeathCause.DEMON_KILL, lookup)
        assertEquals(StepGate.Fire, assertNotNull(step(state, "bonecollector")).gate)

        // Shaking their head no does not spend the ability.
        val declined = resolve(state, "bonecollector", NightInput(none = true))
        assertFalse(Memory.isSpent(declined, "bonecollector"))

        state = resolve(state, "bonecollector", NightInput(playerIds = listOf(chef)))
        assertTrue(DayRules.hasToken(state, chef, "bonecollector", "Has Ability"))
        assertTrue(Memory.isSpent(state, "bonecollector"))
        assertTrue(
            state.prompts.any { it.sourceId == "bonecollector" && it.subjectPlayerId == chef },
            "the restored ability is raised as an obligation",
        )
        // The player stays dead.
        assertFalse(assertNotNull(state.player(chef)).alive)

        state = toNight(state, 3)
        assertTrue(
            assertNotNull(step(state, "bonecollector")).gate is StepGate.Skip,
            "spent — this ability is once per game",
        )
        assertFalse(
            DayRules.hasToken(state, chef, "bonecollector", "Has Ability"),
            "Has Ability retires at dusk",
        )
    }

    @Test
    fun `a restored ability ends the moment the Bone Collector dies`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "bonecollector")
        state = toNight(state, 2)
        val chef = seatOf(state, "chef").id
        val collector = seatOf(state, "bonecollector").id
        state = GameActions.kill(state, chef, DeathCause.DEMON_KILL, lookup)
        state = resolve(state, "bonecollector", NightInput(playerIds = listOf(chef)))

        assertTrue(Status.hasAbility(state, lookup, chef), "dead, but their ability is back")
        val ended = GameActions.kill(state, collector, DeathCause.EXILE, lookup)
        assertFalse(
            Status.hasAbility(ended, lookup, chef),
            "the restored ability ends with the Bone Collector",
        )
    }

    @Test
    fun `an execution offers the Butcher a second nomination and an exile does not`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "butcher")
        val butcher = seatOf(state, "butcher").id
        val saint = seatOf(state, "saint").id
        state = GameActions.advancePhase(state, lookup) // -> DAY 1

        state = DayRules.recordNomination(
            state,
            Nomination(day = state.cycle, nominatorId = butcher, nomineeId = saint),
        )
        assertFalse(DayRules.canNominate(state, lookup, butcher).allowed, "one nomination so far")

        state = Execution.execute(state, lookup, saint, nominatorId = butcher)
        val record = assertNotNull(DayRules.executionToday(state))
        assertTrue(DayRules.executionSpent(state))

        val consequences = Execution.consequences(state, lookup, record)
        assertTrue(
            consequences.any { it.sourceId == "butcher" },
            "the extra nomination is offered: ${consequences.map { it.sourceId }}",
        )
        assertTrue(
            DayRules.canNominate(state, lookup, butcher).allowed,
            "the Butcher may nominate again even though they already have",
        )
        assertTrue(
            DayRules.canBeNominated(state, lookup, seatOf(state, "chef").id).allowed,
        )
    }

    @Test
    fun `a funny Deviant survives an exile but nothing else`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "deviant")
        val deviant = seatOf(state, "deviant").id
        state = GameActions.advancePhase(state, lookup)

        val exile = Deaths.killOutcome(state, lookup, deviant, KillCause(DeathCause.EXILE))
        assertTrue(exile is KillOutcome.Choice, "the storyteller may declare they live: $exile")
        assertTrue((exile as KillOutcome.Choice).question.contains("funny", ignoreCase = true))

        val shot = Deaths.killOutcome(state, lookup, deviant, KillCause(DeathCause.DAY_ABILITY, "gangster"))
        assertFalse(
            shot is KillOutcome.Choice && shot.question.contains("funny", ignoreCase = true),
            "the Deviant is protected against EXILE only: $shot",
        )
    }

    @Test
    fun `the Harlot kills both or neither, never as a Demon`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "harlot")
        state = toNight(state, 2)
        val harlot = seatOf(state, "harlot").id
        val chef = seatOf(state, "chef").id

        // Refused: no deaths at all.
        val refused = resolve(state, "harlot", NightInput(none = true))
        assertTrue(refused.deaths.isEmpty())

        // "Neither dies" is a real answer too.
        val neither = resolve(state, "harlot", NightInput(playerIds = listOf(chef), yes = false))
        assertTrue(neither.deaths.isEmpty())

        val both = resolve(state, "harlot", NightInput(playerIds = listOf(chef), yes = true))
        assertFalse(assertNotNull(both.player(chef)).alive)
        assertFalse(assertNotNull(both.player(harlot)).alive)
        assertTrue(
            both.deaths.all { it.cause == DeathCause.TRAVELLER_ABILITY },
            "never a Demon kill: ${both.deaths.map { it.cause }}",
        )
        assertTrue(both.deaths.all { it.killerCharacterId == "harlot" })
    }

    @Test
    fun `a dead Harlot gets no step and a Monk's Safe does not stop the pair dying`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "harlot")
        state = toNight(state, 2)
        val harlot = seatOf(state, "harlot").id
        val chef = seatOf(state, "chef").id
        val protectedChef = mark(state, chef, "monk", "Safe")

        val both = resolve(protectedChef, "harlot", NightInput(playerIds = listOf(chef), yes = true))
        assertFalse(
            assertNotNull(both.player(chef)).alive,
            "the Monk protects against the DEMON, not a Harlot",
        )

        val dead = GameActions.kill(state, harlot, DeathCause.EXILE, lookup)
        assertTrue(assertNotNull(step(dead, "harlot")).gate is StepGate.Skip)
    }

    // ==================================================================
    // Experimental
    // ==================================================================

    @Test
    fun `the Cacklejack hands out a new character and asks for the alignment ruling`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "cacklejack")
        state = toNight(state, 2)
        val chef = seatOf(state, "chef").id
        val cacklejack = seatOf(state, "cacklejack").id

        state = mark(state, cacklejack, "cacklejack", "Not Me")
        val row = assertNotNull(step(state, "cacklejack"))
        assertTrue(row.action is ChoosePlayerAndCharacter)
        assertTrue(
            assertNotNull(row.action).prompt.contains(assertNotNull(state.player(cacklejack)).name),
            "the seat marked Not Me is named on the step: ${row.action?.prompt}",
        )

        state = NightPlan.resolve(
            state,
            lookup,
            row.key,
            NightInput(playerIds = listOf(chef), characterIds = listOf("undertaker")),
        )
        assertTrue(
            state.prompts.any {
                it.sourceId == "cacklejack" &&
                    it.kind == PromptKind.DECIDE &&
                    it.subjectPlayerId == chef
            },
            "the character change and its alignment are raised as a DECIDE obligation",
        )
        assertTrue(
            state.ledger.any {
                it.kind == LedgerKind.CHOICE &&
                    it.sourceId == "cacklejack" &&
                    it.characterIds == listOf("undertaker")
            },
            "the chosen character is recorded",
        )
    }

    @Test
    fun `the Gangster needs two living neighbours`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "gangster")
        val gangster = seatOf(state, "gangster")
        val rule = assertNotNull(CharacterRules.all["gangster"]?.day?.ability)
        state = GameActions.advancePhase(state, lookup)
        assertTrue(rule.available(state, lookup, gangster))
        assertEquals(DeathCause.DAY_ABILITY, CharacterRules.all["gangster"]!!.killCause)

        // Dead seats are skipped, not counted: the ring still yields two.
        val oneNeighbourDead = GameActions.kill(state, seatOf(state, "chef").id, DeathCause.EXECUTION, lookup)
        assertTrue(rule.available(oneNeighbourDead, lookup, gangster))

        // Only one other player alive: the ability cannot be used as written.
        var alone = oneNeighbourDead
        for (id in listOf("saint", "pithag")) {
            alone = GameActions.kill(alone, seatOf(alone, id).id, DeathCause.EXECUTION, lookup)
        }
        assertFalse(rule.available(alone, lookup, assertNotNull(alone.player(gangster.id))))
    }

    @Test
    fun `nominating the Gnome's Amigo offers the kill, but an exile call never does`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "gnome", "butcher")
        val gnome = seatOf(state, "gnome").id
        val amigo = seatOf(state, "chef").id
        val nominator = seatOf(state, "saint").id
        state = setEvil(state, gnome, false)
        state = GameActions.advancePhase(state, lookup)
        state = mark(state, amigo, "gnome", "Amigo")

        val triggers = DayRules.checkNomination(state, lookup, nominator, amigo)
            .triggers.filter { it.sourceId == "gnome" }
        assertEquals(1, triggers.size, "exactly one Gnome row — the registry replaces the built-in")
        assertEquals(TriggerKind.CHOICE, triggers.single().kind)
        assertEquals(nominator, triggers.single().targetId)
        assertTrue(triggers.single().options.first { it.id == DayRules.OPTION_SKIP }.isDefault)

        // Applying it kills the nominator as a TRAVELLER_ABILITY, not an execution.
        val killed = DayRules.applyTrigger(state, lookup, triggers.single(), DayRules.OPTION_APPLY)
        assertFalse(assertNotNull(killed.player(nominator)).alive)
        assertEquals(DeathCause.TRAVELLER_ABILITY, killed.deaths.last().cause)
        assertFalse(DayRules.executionSpent(killed), "a Gnome kill is not an execution")

        // An exile call against a traveller Amigo must not offer the kill.
        val travellerAmigo = seatOf(state, "butcher").id
        val exileState = mark(state, travellerAmigo, "gnome", "Amigo")
        val onExile = DayRules.checkNomination(exileState, lookup, nominator, travellerAmigo)
            .triggers.filter { it.sourceId == "gnome" }
        assertEquals(1, onExile.size, "one row, and it is this package's — never WP3's built-in")
        assertEquals(TriggerKind.WARN, onExile.single().kind, "exile calls are not nominations")
        assertNull(onExile.single().targetId, "nothing to kill, so applyTrigger is a no-op")
        assertEquals(
            exileState,
            DayRules.applyTrigger(exileState, lookup, onExile.single(), DayRules.OPTION_APPLY),
        )
    }

    @Test
    fun `a dead Gnome does nothing unless a Bone Collector restored them`() {
        var state = game(sv, "vortox", "pithag", "saint", "chef", "gnome")
        val gnome = seatOf(state, "gnome").id
        val amigo = seatOf(state, "chef").id
        val nominator = seatOf(state, "saint").id
        state = GameActions.advancePhase(state, lookup)
        state = mark(state, amigo, "gnome", "Amigo")
        state = GameActions.kill(state, gnome, DeathCause.EXILE, lookup)

        val dead = DayRules.checkNomination(state, lookup, nominator, amigo)
            .triggers.filter { it.sourceId == "gnome" }
        assertEquals(1, dead.size, "one row, and it is this package's — never WP3's built-in")
        assertEquals(TriggerKind.WARN, dead.single().kind, "a dead Gnome kills nobody")
        assertNull(dead.single().targetId)

        val restored = mark(state, gnome, "bonecollector", "Has Ability")
        val offered = DayRules.checkNomination(restored, lookup, nominator, amigo)
            .triggers.single { it.sourceId == "gnome" }
        assertEquals(TriggerKind.CHOICE, offered.kind, "a Bone-Collector-restored Gnome still may kill")
        assertEquals(nominator, offered.targetId)
    }
}
