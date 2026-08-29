package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Bad Moon Rising registry (WP7-BMR).
 *
 * Every character in scope has a row, every token it names is in the official
 * data with the official copy count, and each P0 has at least one
 * Given/When/Then. The four cases the user reported are proved end to end,
 * through `NightPlan.build` / `NightPlan.resolve` / `Deaths.attempt` — never by
 * inspecting the registry row.
 */
class RulesBadMoonRisingTest {

    private val data = GameData.loadDefault()
    private val bmr = data.builtInScripts().first { it.id == "bmr" }
    private val lookup: (String) -> Character? = data::character

    /** Every BMR townsfolk/outsider/minion/demon — WP7-BMR's scope. */
    private val scope = listOf(
        "grandmother", "sailor", "chambermaid", "exorcist", "innkeeper", "gambler",
        "gossip", "courtier", "professor", "minstrel", "tealady", "pacifist", "fool",
        "goon", "lunatic", "tinker", "moonchild",
        "godfather", "devilsadvocate", "assassin", "mastermind",
        "zombuul", "pukka", "shabaloth", "po",
    )

    // ---- fixture helpers ---------------------------------------------------

    /** A seated BMR game in NIGHT 1, one character per seat, named P1..Pn. */
    private fun game(vararg roles: String): GameState {
        var state = GameActions.newGame(bmr, roles.indices.map { "P${it + 1}" })
        roles.forEachIndexed { i, id -> state = GameActions.assignCharacter(state, i.toLong(), id) }
        return Phases.advancePhase(state, lookup)
    }

    /** NIGHT -> DAY -> NIGHT, running the real sweeps and countdowns. */
    private fun nextNight(state: GameState): GameState =
        Phases.advancePhase(Phases.advancePhase(state, lookup), lookup)

    private fun step(state: GameState, abilityId: String): NightStep? =
        NightPlan.build(state, lookup).steps.firstOrNull { it.abilityId == abilityId }

    private fun require(state: GameState, abilityId: String): NightStep =
        assertNotNull(
            step(state, abilityId),
            "$abilityId has no night step: ${NightPlan.build(state, lookup).steps.map { it.key.token }}",
        )

    private fun resolve(state: GameState, abilityId: String, input: NightInput): GameState =
        NightPlan.resolve(state, lookup, require(state, abilityId).key, input)

    private fun seat(state: GameState, characterId: String): Long =
        assertNotNull(
            state.players.firstOrNull { it.characterId == characterId },
            "no $characterId seated",
        ).id

    private fun holds(state: GameState, playerId: Long, sourceId: String, label: String): Boolean {
        val key = Tokens.key(sourceId, label)
        val player = state.player(playerId) ?: return false
        return player.reminders.any { Tokens.key(it) == key } ||
            state.effects.any {
                it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
            }
    }

    private fun copiesHeld(state: GameState, playerId: Long, sourceId: String, label: String): Int {
        val key = Tokens.key(sourceId, label)
        return state.effects.count {
            it.targetId == playerId && Tokens.key(it.sourceCharacterId, it.label) == key
        }
    }

    // ==================================================================
    // Coverage and the token table
    // ==================================================================

    @Test
    fun `every Bad Moon Rising character in scope has a registry row`() {
        val missing = scope.filter { CharacterRules.all[it] == null }
        assertTrue(missing.isEmpty(), "no CharacterRule for: $missing")
        for (id in scope) {
            assertEquals(id, assertNotNull(CharacterRules.all[id]).id)
            val character = assertNotNull(data.character(id), id)
            assertEquals("bmr", character.edition, "$id is not a Bad Moon Rising character")
        }
        assertEquals(25, scope.size, "13 Townsfolk + 4 Outsiders + 4 Minions + 4 Demons")
    }

    @Test
    fun `every reminder these characters own is a declared token with the official copy count`() {
        val problems = mutableListOf<String>()
        for (id in scope) {
            val character = assertNotNull(data.character(id), id)
            for (label in character.allReminders.distinctBy { it.trim().lowercase() }) {
                val rule = Tokens.rule(id, label)
                if (rule == null) {
                    problems += "$id/$label has no TokenRule"
                    continue
                }
                val official = character.allReminders.count { it.trim().equals(label.trim(), true) }
                if (rule.copies != official) {
                    problems += "$id/$label: rule says ${rule.copies}, characters.json has $official"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the N-copy Bad Moon Rising tokens carry their official label and count`() {
        assertEquals(2, assertNotNull(Tokens.rule("innkeeper", "Safe")).copies)
        assertEquals(3, assertNotNull(Tokens.rule("lunatic", "Chosen")).copies)
        assertEquals(2, assertNotNull(Tokens.rule("tealady", "Cannot Die")).copies)
        assertEquals(2, assertNotNull(Tokens.rule("pukka", "Poisoned")).copies)
        assertEquals(3, assertNotNull(Tokens.rule("po", "Dead")).copies)
        assertEquals(2, assertNotNull(Tokens.rule("shabaloth", "Dead")).copies)
        assertNotNull(Tokens.rule("devilsadvocate", "Survives Execution"))
        assertNotNull(Tokens.rule("minstrel", "Everyone Is Drunk"))
        // The Pukka's poison is consumed at the Pukka's OWN next step, never by
        // the clock — the whole reason the standing victim survives dawn (D4).
        assertEquals(Until.ON_SOURCE_STEP, assertNotNull(Tokens.rule("pukka", "Poisoned")).until)
        // The DA's token dies at dusk; the ledger is what remembers the choice.
        assertEquals(Until.DUSK, assertNotNull(Tokens.rule("devilsadvocate", "Survives Execution")).until)
        // WP7-BMR additions/refinements.
        assertEquals(Until.DAWN, assertNotNull(Tokens.rule("tinker", "Dead")).until)
        assertEquals(Until.FOREVER, assertNotNull(Tokens.rule("professor", "Alive")).until)
        assertEquals(Until.FOREVER, assertNotNull(Tokens.rule("shabaloth", "Alive")).until)
    }

    @Test
    fun `the five Bad Moon Rising characters that never wake have no night rule`() {
        for (id in listOf("minstrel", "tealady", "pacifist", "fool", "goon", "mastermind")) {
            val rule = assertNotNull(CharacterRules.all[id], id)
            assertNull(rule.firstNight, "$id must never wake")
            assertNull(rule.otherNight, "$id must never wake")
        }
    }

    // ==================================================================
    // W7I — the Goon, the one REACTIVE character in the registry
    // ==================================================================

    @Test
    fun `the first ability to choose the Goon goes drunk, and the Goon takes their side`() {
        // Given a Goon and a Poisoner, on a night where the Poisoner acts first
        var state = game("goon", "poisoner", "pukka", "monk", "chambermaid", "fool")
        val goon = seat(state, "goon")
        val poisoner = seat(state, "poisoner")
        assertFalse(assertNotNull(state.player(goon)).isEvil(lookup), "the Goon starts good")

        // When the Poisoner points at the Goon…
        state = resolve(state, "poisoner", NightInput(playerIds = listOf(goon)))

        // Then the POISONER is drunk until dusk (W7I: `CharacterRule.onChosen`
        // — before wave 7 the Goon had no behaviour at all)…
        assertTrue(holds(state, poisoner, "goon", "Drunk"))
        assertTrue(Status.isImpaired(state, lookup, poisoner))
        // …and the Goon has taken their alignment, keeping their character.
        assertTrue(assertNotNull(state.player(goon)).isEvil(lookup))
        assertEquals("goon", assertNotNull(state.player(goon)).characterId)
        assertTrue(state.identityLog.none { it.playerId == goon }, "not a character change")

        // "The 1ST player to choose you": a second chooser tonight does nothing.
        val pukka = seat(state, "pukka")
        val again = resolve(state, "pukka", NightInput(playerIds = listOf(goon)))
        assertFalse(holds(again, pukka, "goon", "Drunk"), "only the first chooser")
    }

    @Test
    fun `an impaired Goon reacts to nobody`() {
        var state = game("goon", "poisoner", "pukka", "monk", "chambermaid", "fool")
        val goon = seat(state, "goon")
        state = Effects.place(
            state, goon, EffectKind.POISONED, "storyteller", null, Until.DUSK, "Poisoned",
        ).state
        state = resolve(state, "poisoner", NightInput(playerIds = listOf(goon)))
        assertFalse(holds(state, seat(state, "poisoner"), "goon", "Drunk"))
        assertFalse(assertNotNull(state.player(goon)).isEvil(lookup))
    }

    @Test
    fun `the Moonchild keeps its ability while dead`() {
        val moonchild = assertNotNull(CharacterRules.all["moonchild"])
        assertTrue(moonchild.keepsAbilityWhenDead, "status-model lists moonchild")
        assertTrue(moonchild.actsWhileDead, "and the resolution step runs while dead")
    }

    // ==================================================================
    // User report 1 — the Pukka (lead D4, D24, D36, D63)
    // ==================================================================

    @Test
    fun `the Pukka poisons on night one and offers no kill`() {
        // Given a Pukka on night 1
        val state = game("pukka", "sailor", "gossip", "chambermaid", "professor", "fool")
        val victim = seat(state, "sailor")

        // When its step is built
        val night1 = require(state, "pukka")
        val choose = assertIs<ChoosePlayers>(night1.action)

        // Then it is a POISON step, not a kill step
        assertEquals(1, choose.max)
        assertTrue(
            choose.perTarget.none { it is NightEffect.Attack },
            "the reported bug: night 1 must offer no kill — ${choose.perTarget}",
        )
        val after = NightPlan.resolve(state, lookup, night1.key, NightInput(playerIds = listOf(victim)))
        assertTrue(Status.isImpaired(after, lookup, victim), "the chosen player is poisoned")
        assertTrue(assertNotNull(after.player(victim)).alive, "and nobody dies on the Pukka's first night")
        assertTrue(after.deaths.isEmpty())
    }

    @Test
    fun `the Pukka places the new poison first, so the standing victim dies still poisoned`() {
        // Given the Gossip poisoned on night 1
        var state = game("pukka", "gossip", "chambermaid", "professor", "gambler", "courtier")
        val pukka = seat(state, "pukka")
        val first = seat(state, "gossip")
        val second = seat(state, "chambermaid")
        state = resolve(state, "pukka", NightInput(playerIds = listOf(first)))
        state = nextNight(state)
        assertTrue(
            Status.isImpaired(state, lookup, first),
            "the Pukka's poison survives dawn and dusk",
        )

        // When the Pukka poisons a NEW player on night 2
        state = resolve(state, "pukka", NightInput(playerIds = listOf(second)))

        // Then the previous victim dies — and dies POISONED (lead D4)
        val death = assertNotNull(
            state.deaths.lastOrNull { it.playerId == first },
            "the previously poisoned player dies",
        )
        assertEquals(DeathCause.DEMON_KILL, death.cause)
        assertEquals("pukka", death.killerCharacterId)
        assertEquals(pukka, death.killerPlayerId)
        assertEquals(
            true,
            death.abilityImpairedAtDeath,
            "the second Poisoned token goes down BEFORE the first victim dies (lead D4)",
        )
        // …then becomes healthy, and the new target is poisoned instead.
        assertFalse(holds(state, first, "pukka", "Poisoned"), "and then becomes healthy")
        assertTrue(Status.isImpaired(state, lookup, second), "the new target is poisoned")
        assertEquals(1, copiesHeld(state, second, "pukka", "Poisoned"))
    }

    @Test
    fun `the Pukka's row names the standing victim, and dawn announces the death`() {
        // Playtest D P1-9: the deferred kill lives in `pending`, so nothing on
        // the card mentioned the victim — the storyteller tapped the poison
        // button and somebody died silently.
        var state = game("pukka", "gossip", "chambermaid", "professor", "gambler", "courtier")
        val victim = seat(state, "gossip")
        val victimName = assertNotNull(state.player(victim)).name

        assertEquals("", require(state, "pukka").banner, "night 1 has nobody standing")
        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)

        val row = require(state, "pukka")
        assertTrue(victimName in row.banner, "the row names them: '${row.banner}'")
        assertTrue("dies now" in row.banner, "and says they die: '${row.banner}'")

        state = resolve(state, "pukka", NightInput(playerIds = listOf(seat(state, "chambermaid"))))
        assertFalse(assertNotNull(state.player(victim)).alive)
        val dawn = Briefings.at(state, lookup, BriefingSlot.DAWN)
        assertTrue(
            dawn.announce.any { victimName in it.text },
            "and dawn announces it out loud: ${dawn.announce.map { it.text }}",
        )
    }

    @Test
    fun `the Pukka's row carries the standing death as data, so the button can say it`() {
        // Playtest D P1-9, second half: the banner said it, but the PRIMARY
        // BUTTON still read `DEV — POISONED`. The deferred kill is declared in
        // `pending`, which the card cannot see, so the step now carries the
        // seats that die tonight with nobody choosing them.
        var state = game("pukka", "gossip", "chambermaid", "professor", "gambler", "courtier")
        val victim = seat(state, "gossip")
        assertEquals(emptyList(), require(state, "pukka").deferredDeaths, "night 1 kills nobody")

        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)

        val row = require(state, "pukka")
        assertEquals(
            // `deferred` says the attack was made on an EARLIER night, so the
            // preview scopes tonight's suppression the way the resolution does
            // (lead D63/D68; playtest D2-1).
            listOf(DeferredDeath(victim, DeathCause.DEMON_KILL, deferred = true)),
            row.deferredDeaths,
            "the standing victim, with the cause the registry declared",
        )
        // Tonight's pick is not on the list — it is the ACTION's business and
        // is previewed from the picker.
        state = resolve(state, "pukka", NightInput(playerIds = listOf(seat(state, "chambermaid"))))
        assertFalse(assertNotNull(state.player(victim)).alive)
        assertEquals(
            listOf(seat(state, "chambermaid")),
            require(nextNight(state), "pukka").deferredDeaths.map { it.playerId },
        )
    }

    @Test
    fun `the Grandmother's first night opens on the marked Grandchild`() {
        // Playtest D P2-19: the card says "show the MARKED Grandchild's
        // character token" and the picker opened with nothing selected and a
        // disabled primary.
        var state = game("pukka", "grandmother", "gossip", "chambermaid", "gambler", "courtier")
        val granny = seat(state, "grandmother")
        val child = seat(state, "gossip")

        val unmarked = assertIs<ShowInfo>(require(state, "grandmother").action)
        assertEquals(emptyList(), unmarked.preselect, "nothing marked, nothing pre-selected")

        state = Effects.placeExclusiveReminder(state, child, PlacedReminder("grandmother", "Grandchild"))
        val marked = assertIs<ShowInfo>(require(state, "grandmother").action)
        assertEquals(listOf(child), marked.preselect)
        assertEquals(1, marked.targetsNeeded)

        // Never the Grandmother herself, even if the token ends up on her seat.
        val onHerself = Effects.placeExclusiveReminder(
            state, granny, PlacedReminder("grandmother", "Grandchild"),
        )
        assertEquals(
            emptyList(),
            assertIs<ShowInfo>(require(onHerself, "grandmother").action).preselect,
        )
    }

    @Test
    fun `the Grandmother is not offered as her own Grandchild`() {
        // Fix-E residual: the picker listed the Grandmother herself, one row
        // away from the seat that was actually marked. "You start knowing a
        // GOOD PLAYER & their character" is somebody else, and D81 already
        // bars the holder from the `grandmother.grandchild` setup requirement.
        var state = game("pukka", "grandmother", "gossip", "chambermaid", "gambler", "courtier")
        val granny = seat(state, "grandmother")
        val child = seat(state, "gossip")
        state = Effects.placeExclusiveReminder(state, child, PlacedReminder("grandmother", "Grandchild"))

        val action = assertIs<ShowInfo>(require(state, "grandmother").action)
        assertTrue(
            TargetConstraint.NOT_SELF in action.constraints,
            "the picker greys her own seat out: ${action.constraints}",
        )

        // End to end: even a hand-built input naming her is dropped, so the
        // CHOICE can never record the Grandmother as her own grandchild.
        val resolved = NightPlan.resolve(
            state,
            lookup,
            require(state, "grandmother").key,
            NightInput(playerIds = listOf(granny)),
        )
        assertEquals(
            emptyList(),
            Memory.lastChoice(resolved, "grandmother", granny)?.targetIds.orEmpty(),
            "her own seat is filtered out of the answer: ${resolved.ledger}",
        )
    }

    @Test
    fun `the Grandmother's row names her own death before the button lands it`() {
        // The same shape: `pending` kills the holder, so the card must be able
        // to promise it.
        var state = game("pukka", "grandmother", "gossip", "chambermaid", "gambler", "courtier")
        val granny = seat(state, "grandmother")
        val child = seat(state, "gossip")
        state = Effects.placeExclusiveReminder(state, child, PlacedReminder("grandmother", "Grandchild"))
        state = nextNight(state)

        assertEquals(emptyList(), require(state, "grandmother").deferredDeaths)
        state = Deaths.attempt(
            state, lookup, child, KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
        ).state
        assertEquals(
            listOf(DeferredDeath(granny, DeathCause.GOOD_ABILITY)),
            require(state, "grandmother").deferredDeaths,
        )
    }

    @Test
    fun `the Grandmother's death row asks nothing — it is not a second reveal`() {
        // Playtest B2-3: the row has no action and no infoId, so the planner
        // fell back to the CHARACTER's own calculation and grew a
        // "WHO DID THEY CHOOSE?" picker; answering it replaced "ERIN DIES" on
        // the button with "SHOW “SAILOR” TO ERIN". A WakeCount.NONE row wakes
        // nobody, so there is nobody to show anything to.
        var state = game("pukka", "grandmother", "gossip", "chambermaid", "gambler", "courtier")
        val child = seat(state, "gossip")
        state = Effects.placeExclusiveReminder(state, child, PlacedReminder("grandmother", "Grandchild"))
        state = nextNight(state)
        state = Deaths.attempt(
            state, lookup, child, KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
        ).state

        val row = require(state, "grandmother")
        assertEquals(StepGate.Fire, row.gate, "the row fires — the Grandmother dies")
        assertNull(row.action, "and asks for nothing: ${row.action}")
        assertEquals("", row.infoId)
        assertTrue(row.cards.isEmpty(), "no card either: ${row.cards.map { it.label }}")

        // The first night's row is untouched: that one really does show a token.
        val firstNight = require(game("pukka", "grandmother", "gossip", "chambermaid"), "grandmother")
        assertEquals("grandmother", firstNight.infoId)
        assertIs<ShowInfo>(firstNight.action)
    }

    @Test
    fun `an Exorcised Pukka still names the victim its silenced row will kill`() {
        var state = game("pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = seat(state, "pukka")
        val victim = seat(state, "gossip")
        val victimName = assertNotNull(state.player(victim)).name
        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)
        state = resolve(state, "exorcist", NightInput(playerIds = listOf(pukka)))

        val row = require(state, "pukka")
        val reduced = assertIs<StepGate.Reduced>(row.gate)
        assertTrue(
            row.banner.startsWith(reduced.reason),
            "the reason the ability is cut back still comes first: '${row.banner}'",
        )
        assertTrue(
            victimName in row.banner,
            "and the death it still carries is not hidden behind it: '${row.banner}'",
        )

        // Playtest D2-1: the PREVIEW dropped `Attack.deferred`, so the screen
        // re-ran the funnel as an ordinary attack, hit the un-narrowed veto and
        // put "DEV SURVIVES — NOBODY DIES" on the button of a card that said the
        // opposite — and holding it killed them. The step now carries the flag
        // and the funnel scopes the veto (lead D63/D68).
        assertEquals(
            listOf(DeferredDeath(victim, DeathCause.DEMON_KILL, respectProtection = true, deferred = true)),
            row.deferredDeaths,
        )
        val standing = row.deferredDeaths.single()
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(
                state,
                lookup,
                standing.playerId,
                KillCause(
                    cause = standing.cause,
                    sourceCharacterId = row.abilityId,
                    sourcePlayerId = row.holderId,
                    ignoresProtection = !standing.respectProtection,
                    deferred = standing.deferred,
                ),
            ),
            "the preview must promise what the button does",
        )

        // A Lycanthrope-style suppression on the same seat stops it (D68).
        val noKill = Effects.place(
            state = state,
            target = pukka,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "lycanthrope",
            sourcePlayerId = seat(state, "professor"),
            until = Until.DAWN,
            label = "Faux Paw",
            suppression = KillSuppression.NO_KILL_TONIGHT,
        ).state
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(
                noKill,
                lookup,
                standing.playerId,
                KillCause(DeathCause.DEMON_KILL, row.abilityId, row.holderId, deferred = true),
            ),
        )
    }

    @Test
    fun `a protected Pukka victim lives and still becomes healthy`() {
        var state = game("pukka", "gossip", "chambermaid", "innkeeper", "professor", "gambler")
        val victim = seat(state, "gossip")
        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)
        // The Innkeeper makes them safe tonight, before the Pukka's step.
        state = resolve(
            state,
            "innkeeper",
            NightInput(playerIds = listOf(victim, seat(state, "professor"))),
        )

        state = resolve(state, "pukka", NightInput(playerIds = listOf(seat(state, "chambermaid"))))

        assertTrue(assertNotNull(state.player(victim)).alive, "the Innkeeper stopped the death")
        assertFalse(
            holds(state, victim, "pukka", "Poisoned"),
            "but the poison is consumed anyway — the wiki's own Innkeeper ruling",
        )
    }

    @Test
    fun `an Exorcised Pukka places no poison and still kills its standing victim`() {
        // Given a Pukka that poisoned the Gossip last night
        var state = game("pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = seat(state, "pukka")
        val victim = seat(state, "gossip")
        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)

        // When the Exorcist chooses the Pukka
        state = resolve(state, "exorcist", NightInput(playerIds = listOf(pukka)))
        val silenced = require(state, "pukka")

        // Then the Pukka is REDUCED, never skipped (lead D24) …
        val gate = silenced.gate
        assertIs<StepGate.Reduced>(gate)
        assertFalse(StepGate.CHOOSE in gate.allow, "the choice half is suppressed")
        assertTrue(StepGate.PENDING in gate.allow, "the deferred half still runs")

        // … and its standing victim still dies (lead D63)
        state = NightPlan.resolve(state, lookup, silenced.key, NightInput(none = true))
        assertFalse(assertNotNull(state.player(victim)).alive, "the deferred death still happens")
        assertEquals(DeathCause.DEMON_KILL, assertNotNull(state.deaths.lastOrNull()).cause)
        assertTrue(
            state.effects.none { Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("pukka", "Poisoned") },
            "and no new poison was placed anywhere",
        )
    }

    @Test
    fun `a NO_KILL_TONIGHT suppression stops the Pukka's standing victim too`() {
        // Given the same standing Pukka victim…
        var state = game("pukka", "exorcist", "gossip", "chambermaid", "professor", "fool")
        val pukka = seat(state, "pukka")
        val victim = seat(state, "gossip")
        state = resolve(state, "pukka", NightInput(playerIds = listOf(victim)))
        state = nextNight(state)

        // …but this time the suppression is a Princess's, not an Exorcist's.
        // Lead D68: "the Demon doesn't kill tonight" reaches a DEFERRED kill,
        // which the wiki's own Lycanthrope example spells out.
        state = Effects.place(
            state = state,
            target = pukka,
            kind = EffectKind.DEMON_CANNOT_KILL,
            sourceCharacterId = "princess",
            sourcePlayerId = null,
            until = Until.DAWN,
            label = "Doesn't Kill",
        ).state
        assertEquals(
            KillSuppression.NO_KILL_TONIGHT,
            Status.live(state, lookup, pukka, EffectKind.DEMON_CANNOT_KILL).single().suppression,
            "the Princess's token declares the wider scope",
        )

        val silenced = require(state, "pukka")
        state = NightPlan.resolve(state, lookup, silenced.key, NightInput(none = true))
        assertTrue(
            assertNotNull(state.player(victim)).alive,
            "nobody dies to this Demon tonight, standing victim included",
        )
        assertTrue(
            state.ledger.any { it.kind == LedgerKind.RULING && it.actorId == victim },
            "and the prevented death is still recorded",
        )
    }

    // ==================================================================
    // User report 2 — the Devil's Advocate (lead D1/D3)
    // ==================================================================

    @Test
    fun `the Devil's Advocate token clears at dusk while the choice survives`() {
        // Given a night-1 pick of the Godfather
        var state = game("devilsadvocate", "godfather", "pukka", "sailor", "fool", "gossip")
        val da = seat(state, "devilsadvocate")
        val chosen = seat(state, "godfather")
        state = resolve(state, "devilsadvocate", NightInput(playerIds = listOf(chosen)))
        assertTrue(holds(state, chosen, "devilsadvocate", "Survives Execution"))

        // When dawn, the whole day and dusk pass
        state = nextNight(state)

        // Then the TOKEN is gone and the CHOICE is not
        assertFalse(
            holds(state, chosen, "devilsadvocate", "Survives Execution"),
            "the token expires at dusk",
        )
        val recorded = assertNotNull(
            Memory.lastChoice(state, "devilsadvocate", da),
            "the ledger remembers it after the token expired: ${state.ledger}",
        )
        assertEquals(listOf(chosen), recorded.targetIds)
        assertTrue(chosen in Memory.forbiddenTargets(state, "devilsadvocate", da))
    }

    @Test
    fun `the Devil's Advocate cannot pick the same seat two nights running`() {
        var state = game("devilsadvocate", "godfather", "pukka", "sailor", "fool", "gossip")
        val chosen = seat(state, "godfather")
        val other = seat(state, "sailor")
        state = resolve(state, "devilsadvocate", NightInput(playerIds = listOf(chosen)))
        state = nextNight(state)

        val night2 = require(state, "devilsadvocate")
        val choose = assertIs<ChoosePlayers>(night2.action)
        assertTrue(
            TargetConstraint.DIFFERENT_FROM_LAST_NIGHT in choose.constraints,
            "a constraint, never a token: ${choose.constraints}",
        )
        // W7C: and the row SAYS who was chosen, rather than only hiding them.
        assertTrue(
            "P2" in night2.banner && "last night" in night2.banner,
            "banner: ${night2.banner}",
        )
        assertEquals("", require(game("devilsadvocate", "godfather", "sailor"), "devilsadvocate").banner)

        // The forbidden seat is dropped AT RESOLVE TIME, not just hidden.
        val repeated = NightPlan.resolve(state, lookup, night2.key, NightInput(playerIds = listOf(chosen)))
        assertFalse(
            holds(repeated, chosen, "devilsadvocate", "Survives Execution"),
            "last night's seat cannot be chosen again",
        )

        // Anyone else is legal.
        val fresh = NightPlan.resolve(state, lookup, night2.key, NightInput(playerIds = listOf(other)))
        assertTrue(holds(fresh, other, "devilsadvocate", "Survives Execution"))
    }

    @Test
    fun `a Devil's Advocate protected player survives execution`() {
        var state = game("devilsadvocate", "godfather", "pukka", "sailor", "fool", "gossip")
        val chosen = seat(state, "godfather")
        state = resolve(state, "devilsadvocate", NightInput(playerIds = listOf(chosen)))
        state = Phases.advancePhase(state, lookup) // day 1

        val outcome = Deaths.killOutcome(state, lookup, chosen, KillCause(DeathCause.EXECUTION))
        val prevented = assertIs<KillOutcome.Prevented>(outcome)
        assertEquals("devilsadvocate", prevented.by?.sourceCharacterId)
        assertTrue("remains alive" in prevented.announce, prevented.announce)
    }

    // ==================================================================
    // User report 3 — the Gossip (invariant I3, lead D1)
    // ==================================================================

    @Test
    fun `a Gossip statement recorded by day is consumed at the night step and kills`() {
        // Given a true statement made on day 1
        var state = game("gossip", "pukka", "chambermaid", "professor", "gambler", "courtier")
        val gossip = seat(state, "gossip")
        val victim = seat(state, "chambermaid")
        state = Phases.advancePhase(state, lookup) // day 1
        state = Ledger.statement(
            state,
            speakerId = gossip,
            sourceId = "gossip",
            text = "There is no Minion sitting next to me.",
        )
        val entry = assertNotNull(Memory.statementsOn(state, day = 1, sourceId = "gossip").singleOrNull())
        assertEquals(Verdict.UNJUDGED, entry.verdict, "the storyteller judges it later")
        state = Ledger.setVerdict(state, entry.id, Verdict.TRUE)

        // When night 2 runs
        state = Phases.advancePhase(state, lookup) // night 2
        val night = require(state, "gossip")
        assertEquals(StepGate.Fire, night.gate, "a true statement arms the step")
        assertEquals(WakeCount.NONE, night.wakeCounts, "the Gossip is never woken for this")
        assertTrue(
            "no Minion" in assertIs<ChoosePlayers>(night.action).prompt,
            "the step quotes the statement back: ${night.action}",
        )
        // W7C: the step itself quotes the evidence, in the banner AND the detail,
        // so the Gossip is never asked "what did you say?" again.
        assertTrue("no Minion" in night.banner, "banner: ${night.banner}")
        assertTrue("no Minion" in night.detail, "detail: ${night.detail}")
        assertTrue("P1" in night.detail, "the detail names the speaker: ${night.detail}")
        state = NightPlan.resolve(state, lookup, night.key, NightInput(playerIds = listOf(victim)))

        // Then the chosen player dies by a GOOD ability — never a Demon kill
        val death = assertNotNull(state.deaths.lastOrNull { it.playerId == victim })
        assertEquals(DeathCause.GOOD_ABILITY, death.cause)
        assertEquals("gossip", death.killerCharacterId)
        assertTrue(holds(state, victim, "gossip", "Dead"))

        // W7E: the statement is CONSUMED, so it is never offered a second time.
        assertNotNull(
            state.ledger.first { it.id == entry.id }.resolvedCycle,
            "the statement the step acted on is resolved",
        )
        val night3 = nextNight(state)
        val gate = assertIs<StepGate.Skip>(require(night3, "gossip").gate)
        assertTrue("no Gossip statement" in gate.reason, gate.reason)
    }

    @Test
    fun `the Gossip step is skipped with no statement and with a false one`() {
        var state = game("gossip", "pukka", "sailor", "chambermaid", "professor", "fool")
        val gossip = seat(state, "gossip")
        state = Phases.advancePhase(state, lookup) // day 1

        val quiet = Phases.advancePhase(state, lookup) // night 2, nothing said
        val skipped = assertIs<StepGate.Skip>(require(quiet, "gossip").gate)
        assertTrue("statement" in skipped.reason, skipped.reason)

        var lied = Ledger.statement(state, gossip, "gossip", "I am the Chambermaid.")
        lied = Ledger.setVerdict(lied, assertNotNull(lied.ledger.lastOrNull()).id, Verdict.FALSE)
        lied = Phases.advancePhase(lied, lookup) // night 2
        val falseGate = assertIs<StepGate.Skip>(require(lied, "gossip").gate)
        assertTrue("false" in falseGate.reason, falseGate.reason)
    }

    @Test
    fun `an unjudged Gossip statement asks the storyteller instead of guessing`() {
        var state = game("gossip", "pukka", "sailor", "chambermaid", "professor", "fool")
        state = Phases.advancePhase(state, lookup) // day 1
        state = Ledger.statement(state, seat(state, "gossip"), "gossip", "Nobody died last night.")
        state = Phases.advancePhase(state, lookup) // night 2

        val gate = assertIs<StepGate.Conditional>(require(state, "gossip").gate)
        assertTrue("Nobody died last night." in gate.question, gate.question)
    }

    // ==================================================================
    // User report 4 — the Professor (lead D7)
    // ==================================================================

    @Test
    fun `the Professor resurrects a dead Townsfolk, announcing at dawn and re-running night one`() {
        // Given a dead Grandmother
        var state = game("professor", "grandmother", "pukka", "sailor", "fool", "gossip")
        val professor = seat(state, "professor")
        val dead = seat(state, "grandmother")
        state = Deaths.attempt(state, lookup, dead, KillCause(DeathCause.EXECUTION)).state
        assertFalse(assertNotNull(state.player(dead)).alive)
        state = nextNight(state)

        // When the Professor points at them
        state = resolve(state, "professor", NightInput(playerIds = listOf(dead)))

        // Then they are alive, the ability is spent, and both obligations exist
        assertTrue(assertNotNull(state.player(dead)).alive, "a dead Townsfolk comes back")
        assertTrue(holds(state, dead, "professor", "Alive"))
        assertTrue(Memory.isSpent(state, "professor", professor), "once per game")
        assertTrue(holds(state, professor, "professor", "No Ability"))

        val rerun = assertNotNull(
            Prompts.forTonight(state).firstOrNull {
                it.kind == PromptKind.RUN_FIRST_NIGHT && it.subjectPlayerId == dead
            },
            "a RUN_FIRST_NIGHT prompt for the resurrected seat: ${state.prompts}",
        )
        assertEquals("grandmother", rerun.stepSlotId)
        assertTrue(
            Memory.pendingAnnouncements(state).any { "alive again" in it.text },
            "the storyteller still owes the table the dawn announcement: ${state.ledger}",
        )

        // And tonight's sheet grows the inserted FIRST-night step, in place.
        val inserted = assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull {
                it.key.abilityId == "grandmother" && it.key.variant == StepVariant.FIRST
            },
            "the Grandmother's first night is re-run: " +
                NightPlan.build(state, lookup).steps.map { it.key.token },
        )
        assertEquals(WakeStyle.FIRST_NIGHT, inserted.style)
    }

    @Test
    fun `a Professor pointed at a dead Outsider spends the ability and raises nobody`() {
        var state = game("professor", "lunatic", "pukka", "sailor", "fool", "gossip")
        val professor = seat(state, "professor")
        val lunatic = seat(state, "lunatic")
        state = Deaths.attempt(state, lookup, lunatic, KillCause(DeathCause.EXECUTION)).state
        state = nextNight(state)

        // W7b: the pick is LEGAL — "choose a dead player: IF they are a
        // Townsfolk" — so the picker no longer hides an Outsider.
        val choose = assertIs<ChoosePlayers>(require(state, "professor").action)
        assertTrue(TargetConstraint.DEAD in choose.constraints)
        assertFalse(TargetConstraint.TOWNSFOLK in choose.constraints, "a wasted use is still a use")

        val after = resolve(state, "professor", NightInput(playerIds = listOf(lunatic)))
        assertFalse(assertNotNull(after.player(lunatic)).alive, "a dead Outsider stays dead")
        assertFalse(holds(after, lunatic, "professor", "Alive"))
        assertTrue(Memory.isSpent(after, "professor", professor), "the ability is spent anyway")
        assertTrue(assertIs<StepGate.Skip>(require(nextNight(after), "professor").gate).reason.isNotEmpty())
    }

    @Test
    fun `a Professor who shakes their head spends nothing`() {
        var state = game("professor", "grandmother", "pukka", "sailor", "fool", "gossip")
        val professor = seat(state, "professor")
        val dead = seat(state, "grandmother")
        state = Deaths.attempt(state, lookup, dead, KillCause(DeathCause.EXECUTION)).state
        state = nextNight(state)

        state = resolve(state, "professor", NightInput(none = true))
        assertFalse(Memory.isSpent(state, "professor", professor))
        assertEquals(StepGate.Fire, require(nextNight(state), "professor").gate)
    }

    @Test
    fun `a dead Spy ruled to register as a Townsfolk comes back`() {
        var state = game("professor", "spy", "pukka", "sailor", "fool", "gossip")
        val spy = seat(state, "spy")
        state = Deaths.attempt(state, lookup, spy, KillCause(DeathCause.EXECUTION)).state
        state = nextNight(state)

        // Untouched, the Spy registers Minion and the pick is wasted.
        val wasted = resolve(state, "professor", NightInput(playerIds = listOf(spy)))
        assertFalse(assertNotNull(wasted.player(spy)).alive)

        // Ruled to register as a Townsfolk (lead D10/D32), they come back.
        val ruled = Effects.place(
            state = state,
            target = spy,
            kind = EffectKind.REGISTERS_AS,
            sourceCharacterId = "spy",
            sourcePlayerId = spy,
            until = Until.FOREVER,
            characterId = "townsfolk",
        ).state
        val back = resolve(ruled, "professor", NightInput(playerIds = listOf(spy)))
        assertTrue(assertNotNull(back.player(spy)).alive, "registration, not the true team")
        assertTrue(holds(back, spy, "professor", "Alive"))
    }

    @Test
    fun `a spent Professor is skipped on later nights`() {
        var state = game("professor", "grandmother", "pukka", "sailor", "fool", "gossip")
        val dead = seat(state, "grandmother")
        state = Deaths.attempt(state, lookup, dead, KillCause(DeathCause.EXECUTION)).state
        state = nextNight(state)
        state = resolve(state, "professor", NightInput(playerIds = listOf(dead)))
        state = nextNight(state)

        val gate = assertIs<StepGate.Skip>(require(state, "professor").gate)
        assertTrue("once per game" in gate.reason, gate.reason)
    }

    // ==================================================================
    // The Lunatic — the real Demon's step is what is informed
    // ==================================================================

    @Test
    fun `a Lunatic who believes they are the Po kills nobody`() {
        var state = game("lunatic", "po", "godfather", "sailor", "fool", "gossip")
        val lunatic = seat(state, "lunatic")
        state = GameActions.setShownCharacter(state, lunatic, "po")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        // The Lunatic's row is the believed Demon's ability, at the Lunatic's slot.
        val row = assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull { it.slotId == "lunatic" },
            "the Lunatic keeps a wake row of their own",
        )
        assertEquals(lunatic, row.holderId)
        assertTrue(row.banner.isNotBlank(), "with a banner saying nothing they do has any effect")
        val choose = assertIs<ChoosePlayers>(row.action)
        assertTrue(
            choose.perTarget.none { it is NightEffect.Attack },
            "a Lunatic's choices never kill: ${choose.perTarget}",
        )

        val aliveBefore = state.alivePlayers.size
        val victim = seat(state, "sailor")
        state = NightPlan.resolve(state, lookup, row.key, NightInput(playerIds = listOf(victim)))

        assertEquals(aliveBefore, state.alivePlayers.size, "nobody dies")
        assertTrue(state.deaths.isEmpty())
        assertTrue(holds(state, victim, "lunatic", "Chosen"), "only the official Chosen marker")
        assertFalse(Status.isImpaired(state, lookup, victim), "and no poison, ever")
        // The real Po still has its own, independent row.
        assertNotNull(
            NightPlan.build(state, lookup).steps.firstOrNull {
                it.slotId == "po" && it.holderId == seat(state, "po")
            },
            "the real Demon acts separately",
        )
    }

    // ==================================================================
    // The rest of the P0s
    // ==================================================================

    @Test
    fun `the Innkeeper protects both picks and drunks the second`() {
        var state = game("innkeeper", "pukka", "sailor", "chambermaid", "fool", "gossip")
        val safe = seat(state, "chambermaid")
        val drunk = seat(state, "gossip")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        state = resolve(state, "innkeeper", NightInput(playerIds = listOf(safe, drunk)))

        assertTrue(holds(state, safe, "innkeeper", "Safe"), "BOTH picks are safe — two official copies")
        assertTrue(holds(state, drunk, "innkeeper", "Safe"))
        assertTrue(holds(state, drunk, "innkeeper", "Drunk"), "the second pick is the drunk one")
        assertFalse(holds(state, safe, "innkeeper", "Drunk"))

        // The protection blocks EVERY night death, not just the Demon's (lead D57).
        val outcome = Deaths.killOutcome(
            state,
            lookup,
            safe,
            KillCause(DeathCause.EVIL_ABILITY, "godfather", seat(state, "pukka")),
        )
        assertIs<KillOutcome.Prevented>(outcome)
    }

    @Test
    fun `an Innkeeper can make themselves the drunk one`() {
        var state = game("innkeeper", "pukka", "sailor", "chambermaid", "fool", "gossip")
        val innkeeper = seat(state, "innkeeper")
        val other = seat(state, "chambermaid")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        // The Innkeeper is the SECOND pick, so the Innkeeper is the drunk one.
        state = resolve(state, "innkeeper", NightInput(playerIds = listOf(other, innkeeper)))

        assertTrue(holds(state, innkeeper, "innkeeper", "Drunk"), "the Drunk token is on the Innkeeper")
        assertTrue(holds(state, other, "innkeeper", "Safe"), "and the other pick is still marked Safe")

        // Lead D69 (user-confirmed) settles the self-protection trap this test
        // used to leave open: BOTH effects stand. The drunkenness was placed
        // while the ability worked, so the Innkeeper IS drunk…
        assertTrue(Status.isImpaired(state, lookup, innkeeper), "the Innkeeper is drunk")
        // …and because their ability is now impaired, BOTH Safe effects are
        // inert tonight — including the one on their own seat.
        assertTrue(Status.protections(state, lookup, other).isEmpty(), "the other pick is not safe")
        assertTrue(Status.protections(state, lookup, innkeeper).isEmpty(), "nor is the Innkeeper")
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(
                state,
                lookup,
                other,
                KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
            ),
        )
        // And it is not a paradox: nothing here is for the storyteller to settle.
        assertTrue(Status.paradoxSeats(state, lookup).isEmpty(), "no DECIDE prompt is owed")
    }

    @Test
    fun `an Innkeeper who drunks somebody else still protects both picks`() {
        var state = game("innkeeper", "pukka", "sailor", "chambermaid", "fool", "gossip")
        val first = seat(state, "chambermaid")
        val second = seat(state, "fool")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = resolve(state, "innkeeper", NightInput(playerIds = listOf(first, second)))

        assertFalse(Status.isImpaired(state, lookup, seat(state, "innkeeper")))
        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(
                state,
                lookup,
                first,
                KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
            ),
        )
    }

    @Test
    fun `the Sailor drunks their target and cannot die while sober`() {
        var state = game("sailor", "pukka", "innkeeper", "chambermaid", "fool", "gossip")
        val sailor = seat(state, "sailor")
        val target = seat(state, "chambermaid")

        state = resolve(state, "sailor", NightInput(playerIds = listOf(target)))
        assertTrue(holds(state, target, "sailor", "Drunk"))
        assertTrue(Status.isImpaired(state, lookup, target))

        assertIs<KillOutcome.Prevented>(
            Deaths.killOutcome(state, lookup, sailor, KillCause(DeathCause.EXECUTION)),
        )
        // A drunk Sailor is mortal — the standing rule ends with its source.
        val drunkSailor = Effects.place(
            state, sailor, EffectKind.POISONED, "poisoner", null, Until.DUSK, "Poisoned",
        ).state
        assertIs<KillOutcome.Dies>(
            Deaths.killOutcome(drunkSailor, lookup, sailor, KillCause(DeathCause.EXECUTION)),
        )
    }

    @Test
    fun `the Exorcist must choose a different player and silences the Demon`() {
        var state = game("exorcist", "zombuul", "sailor", "chambermaid", "fool", "gossip")
        val demon = seat(state, "zombuul")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        assertNull(step(game("exorcist", "zombuul", "sailor", "chambermaid", "fool", "gossip"), "exorcist"))

        val choose = assertIs<ChoosePlayers>(require(state, "exorcist").action)
        assertTrue(TargetConstraint.DIFFERENT_FROM_LAST_NIGHT in choose.constraints)

        state = resolve(state, "exorcist", NightInput(playerIds = listOf(demon)))
        assertTrue(holds(state, demon, "exorcist", "Chosen"))
        val gate = require(state, "zombuul").gate
        assertIs<StepGate.Reduced>(gate)
        assertTrue("Exorcist" in gate.reason, gate.reason)
    }

    @Test
    fun `the Courtier's and the Exorcist's answers are for the storyteller and never a card`() {
        var state = game("courtier", "pukka", "sailor", "exorcist", "fool", "gossip")
        val courtier = seat(state, "courtier")
        val exorcist = seat(state, "exorcist")
        val demon = seat(state, "pukka")

        // The Courtier names a character and learns NOTHING: the answer is the
        // storyteller's crib of who holds what, so it may not become a card
        // (playtest B2-2, D2-2).
        val courtierInfo = assertNotNull(InfoCalc.compute(state, lookup, "courtier", courtier))
        assertEquals(InfoAudience.STORYTELLER, courtierInfo.audience)
        assertTrue(
            NightPlan.cardsFor(state, courtierInfo).isEmpty(),
            "no card offer: ${NightPlan.cardsFor(state, courtierInfo).map { it.label }}",
        )
        assertTrue(
            require(state, "courtier").cards.isEmpty(),
            "and none on the row: ${require(state, "courtier").cards.map { it.label }}",
        )

        // The Exorcist is not told either way — the DEMON is the one who learns
        // something (playtest B2-2, D2-3).
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        val exorcistInfo =
            assertNotNull(InfoCalc.compute(state, lookup, "exorcist", exorcist, listOf(demon)))
        assertEquals(Answer.YesNoAnswer(true), exorcistInfo.answer, "the answer is still computed")
        assertEquals(InfoAudience.STORYTELLER, exorcistInfo.audience)
        assertTrue(NightPlan.cardsFor(state, exorcistInfo).isEmpty())
    }

    @Test
    fun `the Courtier spends on any named character and drunks an in-play one for three days`() {
        var state = game("courtier", "pukka", "sailor", "chambermaid", "fool", "gossip")
        val courtier = seat(state, "courtier")
        val target = seat(state, "chambermaid")

        state = resolve(
            state,
            "courtier",
            NightInput(playerIds = listOf(target), characterIds = listOf("chambermaid")),
        )
        assertTrue(Memory.isSpent(state, "courtier", courtier))
        assertTrue(holds(state, target, "courtier", "Drunk 1"))
        assertTrue(Status.isImpaired(state, lookup, target))

        // Drunk 1 -> Drunk 2 -> Drunk 3 -> gone, one step per dusk (lead D14).
        state = nextNight(state)
        assertTrue(holds(state, target, "courtier", "Drunk 2"), "one dusk later")
        state = nextNight(state)
        assertTrue(holds(state, target, "courtier", "Drunk 3"))
        assertTrue(Status.isImpaired(state, lookup, target), "three nights and three days")
        state = nextNight(state)
        assertFalse(Status.isImpaired(state, lookup, target), "and then it is over")

        val gate = assertIs<StepGate.Skip>(require(state, "courtier").gate)
        assertTrue("once per game" in gate.reason, gate.reason)
    }

    @Test
    fun `the Godfather is armed by a day Outsider death and not by a night one`() {
        val base = game("godfather", "pukka", "tinker", "chambermaid", "professor", "gossip")
        val outsider = seat(base, "tinker")

        // Given the Outsider is executed on day 1
        var day = Phases.advancePhase(base, lookup)
        day = Deaths.attempt(day, lookup, outsider, KillCause(DeathCause.EXECUTION)).state
        val armed = Phases.advancePhase(day, lookup) // night 2
        assertEquals(StepGate.Fire, require(armed, "godfather").gate, "an Outsider died today")

        // Given instead the Outsider dies at NIGHT
        var night = Deaths.attempt(base, lookup, outsider, KillCause(DeathCause.DEMON_KILL, "pukka")).state
        night = nextNight(night)
        val gate = assertIs<StepGate.Skip>(require(night, "godfather").gate)
        assertTrue("died today" in gate.reason, gate.reason)

        // And the kill is an EVIL ability, which a Monk/Soldier does not block.
        val victim = seat(armed, "chambermaid")
        val after = resolve(armed, "godfather", NightInput(playerIds = listOf(victim)))
        val death = assertNotNull(after.deaths.lastOrNull { it.playerId == victim })
        assertEquals(DeathCause.EVIL_ABILITY, death.cause)
        assertTrue(holds(after, victim, "godfather", "Dead"))
    }

    @Test
    fun `the Godfather's gate question reads "an Outsider", not "a Outsider"`() {
        // Playtest D P2-14.
        var state = game("godfather", "pukka", "tinker", "chambermaid", "professor", "gossip")
        // Somebody died today, but nobody who registered as an Outsider: the
        // storyteller is asked rather than overruled.
        var day = Phases.advancePhase(state, lookup)
        day = Deaths.attempt(
            day,
            lookup,
            seat(day, "chambermaid"),
            KillCause(DeathCause.EXECUTION),
        ).state
        state = Phases.advancePhase(day, lookup)

        val gate = assertIs<StepGate.Conditional>(require(state, "godfather").gate)
        assertEquals("Did an Outsider die today?", gate.question)
    }

    @Test
    fun `the Godfather learns the Outsiders on the first night and never again`() {
        // Playtest D P0-4: the "these Outsiders are in play" block, and its four
        // SHOW buttons, were rendered again on night 2 and night 3.
        val base = game("godfather", "pukka", "tinker", "chambermaid", "professor", "gossip")
        val first = require(base, "godfather")
        assertTrue(
            first.cards.any { it.truthful && "TINKER" in it.label.uppercase() },
            "night 1 shows the Outsider tokens: ${first.cards.map { it.label }}",
        )
        assertTrue(
            NightPlan.givesInfoTonight(base, lookup, "godfather", first.holderId),
            "and the engine says the row gives information tonight",
        )

        // Day 1: an Outsider is executed, so night 2's row does fire — with the
        // kill only, and not one word about which Outsiders are in play.
        var day = Phases.advancePhase(base, lookup)
        day = Deaths.attempt(day, lookup, seat(base, "tinker"), KillCause(DeathCause.EXECUTION)).state
        val armed = Phases.advancePhase(day, lookup)
        val second = require(armed, "godfather")
        assertEquals(StepGate.Fire, second.gate)
        assertTrue(
            second.cards.isEmpty(),
            "no Outsider tokens to show a second time: ${second.cards.map { it.label }}",
        )
        assertFalse(
            NightPlan.givesInfoTonight(armed, lookup, "godfather", second.holderId),
            "and the screen is told not to compute the block either",
        )

        // Night 3, with nobody dead today, is skipped — and still says nothing.
        val third = require(nextNight(armed), "godfather")
        assertIs<StepGate.Skip>(third.gate)
        assertTrue(third.cards.isEmpty(), "${third.cards.map { it.label }}")
    }

    @Test
    fun `the Assassin kills through every protection, once`() {
        var state = game("assassin", "pukka", "innkeeper", "chambermaid", "professor", "gossip")
        val assassin = seat(state, "assassin")
        val victim = seat(state, "chambermaid")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = resolve(state, "innkeeper", NightInput(playerIds = listOf(victim, seat(state, "gossip"))))
        assertTrue(holds(state, victim, "innkeeper", "Safe"))

        state = resolve(state, "assassin", NightInput(playerIds = listOf(victim)))

        assertFalse(assertNotNull(state.player(victim)).alive, "even if for some reason they could not")
        assertEquals(DeathCause.EVIL_ABILITY, assertNotNull(state.deaths.lastOrNull()).cause)
        assertTrue(Memory.isSpent(state, "assassin", assassin))

        state = nextNight(state)
        val gate = assertIs<StepGate.Skip>(require(state, "assassin").gate)
        assertTrue("once per game" in gate.reason, gate.reason)
    }

    @Test
    fun `a head-shaking Assassin spends nothing`() {
        var state = game("assassin", "pukka", "innkeeper", "chambermaid", "professor", "gossip")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = resolve(state, "assassin", NightInput(none = true))

        assertFalse(Memory.isSpent(state, "assassin", seat(state, "assassin")))
        assertEquals(StepGate.Fire, require(nextNight(state), "assassin").gate)
    }

    @Test
    fun `the Zombuul wakes only when nobody died today`() {
        val base = game("zombuul", "sailor", "tealady", "fool", "gossip", "professor")
        val quiet = base.copy(cycle = 2, nightStepsDone = emptySet())
        assertEquals(StepGate.Fire, require(quiet, "zombuul").gate, "nobody died today")

        var day = Phases.advancePhase(base, lookup)
        day = Deaths.attempt(day, lookup, seat(day, "gossip"), KillCause(DeathCause.EXECUTION)).state
        val blocked = Phases.advancePhase(day, lookup)
        val gate = assertIs<StepGate.Skip>(require(blocked, "zombuul").gate)
        assertTrue("died today" in gate.reason, gate.reason)

        // The kill itself is a Demon kill.
        val victim = seat(quiet, "gossip")
        val after = resolve(quiet, "zombuul", NightInput(playerIds = listOf(victim)))
        assertEquals(DeathCause.DEMON_KILL, assertNotNull(after.deaths.lastOrNull()).cause)
    }

    @Test
    fun `a Zombuul that registers as dead keeps acting`() {
        var state = game("zombuul", "sailor", "tealady", "fool", "gossip", "professor")
        val zombuul = seat(state, "zombuul")
        val attempt = Deaths.attempt(state, lookup, zombuul, KillCause(DeathCause.DEMON_KILL, "imp"))
        assertIs<KillOutcome.RegistersDead>(attempt.outcome)
        state = attempt.state.copy(cycle = 2, nightStepsDone = emptySet())

        assertFalse(assertNotNull(state.player(zombuul)).alive, "shrouded")
        assertTrue(state.isTrulyAlive(zombuul), "but alive by the rules (lead D6)")
        assertEquals(StepGate.Fire, require(state, "zombuul").gate, "and it still attacks")
    }

    @Test
    fun `the Po charges on a head shake and spends the charge on three kills`() {
        var state = game("po", "gossip", "chambermaid", "professor", "gambler", "courtier")
        val po = seat(state, "po")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        // Choosing no-one charges — even the wording says so.
        state = resolve(state, "po", NightInput(none = true))
        assertTrue(holds(state, po, "po", "3 Attacks"))
        assertTrue(state.deaths.isEmpty())

        state = nextNight(state)
        val charged = assertIs<ChoosePlayers>(require(state, "po").action)
        assertEquals(3, charged.min)
        assertEquals(3, charged.max)

        val victims = listOf(seat(state, "gossip"), seat(state, "chambermaid"), seat(state, "professor"))
        state = resolve(state, "po", NightInput(playerIds = victims))
        assertEquals(victims.toSet(), state.deaths.map { it.playerId }.toSet(), "all three, in order")
        assertFalse(holds(state, po, "po", "3 Attacks"), "the charge is spent")
    }

    @Test
    fun `the Shabaloth kills two and is asked about the regurgitation next night`() {
        var state = game("shabaloth", "gossip", "chambermaid", "professor", "gambler", "courtier")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        val first = seat(state, "gossip")
        val second = seat(state, "professor")

        state = resolve(state, "shabaloth", NightInput(playerIds = listOf(first, second)))
        assertFalse(assertNotNull(state.player(first)).alive)
        assertFalse(assertNotNull(state.player(second)).alive)
        assertTrue(holds(state, first, "shabaloth", "Dead"))
        assertTrue(holds(state, second, "shabaloth", "Dead"))

        state = nextNight(state)
        state = resolve(
            state,
            "shabaloth",
            NightInput(playerIds = listOf(seat(state, "gambler"), seat(state, "courtier"))),
        )
        val ask = assertNotNull(
            state.prompts.lastOrNull { it.sourceId == "shabaloth" && it.kind == PromptKind.DECIDE },
            "the regurgitation is a storyteller decision, never a silent one: ${state.prompts}",
        )
        assertTrue("Regurgitate" in ask.title, ask.title)
    }

    @Test
    fun `the Grandmother dies when the Demon kills her grandchild, and not otherwise`() {
        val base = game("grandmother", "pukka", "gossip", "chambermaid", "gambler", "courtier")
            .let {
                GameActions.addReminder(
                    it,
                    seat(it, "chambermaid"),
                    PlacedReminder("grandmother", "Grandchild"),
                )
            }
        val grandmother = seat(base, "grandmother")
        val grandchild = seat(base, "chambermaid")

        // Given the grandchild is killed by the Demon tonight
        var demon = base.copy(cycle = 2, nightStepsDone = emptySet())
        demon = Deaths.attempt(
            demon,
            lookup,
            grandchild,
            KillCause(DeathCause.DEMON_KILL, "pukka", seat(demon, "pukka")),
        ).state
        assertEquals(StepGate.Fire, require(demon, "grandmother").gate)
        demon = resolve(demon, "grandmother", NightInput())
        assertFalse(assertNotNull(demon.player(grandmother)).alive, "the Grandmother dies too")
        val death = assertNotNull(demon.deaths.lastOrNull { it.playerId == grandmother })
        assertEquals(DeathCause.GOOD_ABILITY, death.cause, "never a Demon kill — Sage/Choirboy must not fire")
        assertTrue(holds(demon, grandmother, "grandmother", "Dead"))

        // Given instead a GOOD ability killed them
        var good = base.copy(cycle = 2, nightStepsDone = emptySet())
        good = Deaths.attempt(good, lookup, grandchild, KillCause(DeathCause.GOOD_ABILITY, "gossip")).state
        assertIs<StepGate.Skip>(require(good, "grandmother").gate)
        assertTrue(assertNotNull(good.player(grandmother)).alive)
    }

    @Test
    fun `a resurrected grandchild leaves the Grandmother alive`() {
        var state = game("grandmother", "pukka", "professor", "chambermaid", "gambler", "courtier")
        state = GameActions.addReminder(
            state,
            seat(state, "chambermaid"),
            PlacedReminder("grandmother", "Grandchild"),
        )
        val grandchild = seat(state, "chambermaid")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        state = Deaths.attempt(
            state,
            lookup,
            grandchild,
            KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
        ).state
        // The Professor sits at other-night 64, the Grandmother at 72.
        state = resolve(state, "professor", NightInput(playerIds = listOf(grandchild)))

        assertIs<StepGate.Skip>(require(state, "grandmother").gate)
        assertTrue(assertNotNull(state.player(seat(state, "grandmother"))).alive, "the ordering test")
    }

    @Test
    fun `the Moonchild's death arms a public choice that kills a good player that night`() {
        var state = game("moonchild", "pukka", "gossip", "gambler", "courtier", "chambermaid")
        val moonchild = seat(state, "moonchild")
        val victim = seat(state, "chambermaid")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        // Given the Moonchild dies at night
        state = Deaths.attempt(
            state,
            lookup,
            moonchild,
            KillCause(DeathCause.DEMON_KILL, "pukka", seat(state, "pukka")),
        ).state
        val armed = assertNotNull(
            state.prompts.firstOrNull { it.sourceId == "moonchild" },
            "the curse is armed by the DeathEvent: ${state.prompts}",
        )
        assertEquals(BriefingSlot.DAWN, armed.at, "they learn of a night death at dawn")
        assertEquals(PromptKind.CHOOSE_PLAYER, armed.kind)
        // Nothing to resolve while no choice has been recorded.
        assertIs<StepGate.Skip>(require(state, "moonchild").gate)

        // When the choice is made publicly, by day
        state = Phases.advancePhase(state, lookup) // day 2
        state = Ledger.statement(
            state,
            speakerId = moonchild,
            sourceId = "moonchild",
            text = "I choose P4.",
            targetIds = listOf(victim),
        )
        state = Phases.advancePhase(state, lookup) // night 3

        // Then the night step resolves it, and a good target dies
        assertEquals(StepGate.Fire, require(state, "moonchild").gate)
        state = resolve(state, "moonchild", NightInput(playerIds = listOf(victim)))
        val death = assertNotNull(state.deaths.lastOrNull { it.playerId == victim })
        assertEquals(DeathCause.GOOD_ABILITY, death.cause, "the Monk does not stop this curse")
        assertTrue(holds(state, victim, "moonchild", "Dead"))
    }

    @Test
    fun `a prevented execution does not arm the Moonchild`() {
        var state = game("moonchild", "devilsadvocate", "pukka", "sailor", "fool", "gossip")
        val moonchild = seat(state, "moonchild")
        state = resolve(state, "devilsadvocate", NightInput(playerIds = listOf(moonchild)))
        state = Phases.advancePhase(state, lookup) // day 1

        state = Deaths.attempt(state, lookup, moonchild, KillCause(DeathCause.EXECUTION)).state
        assertTrue(assertNotNull(state.player(moonchild)).alive)
        assertTrue(
            state.prompts.none { it.sourceId == "moonchild" },
            "no DeathEvent, no curse: ${state.prompts}",
        )
    }

    @Test
    fun `the Tinker dies only when the storyteller says so, by a good ability`() {
        var state = game("tinker", "pukka", "gossip", "chambermaid", "gambler", "courtier")
        val tinker = seat(state, "tinker")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())

        val row = require(state, "tinker")
        assertEquals(WakeCount.NONE, row.wakeCounts, "the Tinker never wakes")
        // The default answer never kills.
        val untouched = NightPlan.resolve(state, lookup, row.key, NightInput())
        assertTrue(assertNotNull(untouched.player(tinker)).alive)

        state = NightPlan.resolve(state, lookup, row.key, NightInput(yes = true))
        assertFalse(assertNotNull(state.player(tinker)).alive)
        val death = assertNotNull(state.deaths.lastOrNull())
        assertEquals(DeathCause.GOOD_ABILITY, death.cause, "never a Demon kill")
        assertTrue(holds(state, tinker, "tinker", "Dead"))
    }

    @Test
    fun `the Gambler dies on a wrong guess and lives on a right one`() {
        var state = game("gambler", "pukka", "gossip", "chambermaid", "professor", "courtier")
        val gambler = seat(state, "gambler")
        state = state.copy(cycle = 2, nightStepsDone = emptySet())
        val row = require(state, "gambler")
        assertIs<Sequence>(row.action)

        val right = NightPlan.resolve(
            state,
            lookup,
            row.key,
            NightInput(playerIds = listOf(seat(state, "gossip")), characterIds = listOf("gossip")),
        )
        assertTrue(assertNotNull(right.player(gambler)).alive, "a correct guess costs nothing")

        val wrong = NightPlan.resolve(
            state,
            lookup,
            row.key,
            NightInput(
                playerIds = listOf(seat(state, "gossip")),
                characterIds = listOf("chambermaid"),
                yes = true,
            ),
        )
        assertFalse(assertNotNull(wrong.player(gambler)).alive)
        val death = assertNotNull(wrong.deaths.lastOrNull())
        assertEquals(DeathCause.GOOD_ABILITY, death.cause)
        assertEquals(gambler, death.playerId, "the Gambler kills themselves, never the target")
        assertTrue(holds(wrong, gambler, "gambler", "Dead"))
    }

    @Test
    fun `the Chambermaid needs two other living players and counts own-ability wakes`() {
        val state = game("chambermaid", "pukka", "gossip", "sailor", "fool", "professor")
        val row = require(state, "chambermaid")
        val info = assertIs<ShowInfo>(row.action)
        assertEquals(2, info.targetsNeeded)
        assertTrue(TargetConstraint.NOT_SELF in info.constraints, "not yourself")
        assertTrue(TargetConstraint.ALIVE in info.constraints)

        val lonely = game("chambermaid", "pukka").copy(cycle = 2, nightStepsDone = emptySet())
        val gate = assertIs<StepGate.Skip>(require(lonely, "chambermaid").gate)
        assertTrue("fewer than 2" in gate.reason, gate.reason)
    }
}
