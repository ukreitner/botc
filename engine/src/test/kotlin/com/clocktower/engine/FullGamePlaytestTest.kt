package com.clocktower.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Storyteller-level acceptance playtests.
 *
 * These are intentionally narrative fixtures rather than small unit tests:
 * every generated night-sheet row is accounted for, every nomination stores
 * its individual voters, and all deaths/reminders flow through the real
 * immutable engine actions. The companion human audit lives at
 * tools/playtests/full-game-storyteller-report.md.
 */
class FullGamePlaytestTest {

    private val data = GameData.loadDefault()

    private data class Seat(
        val name: String,
        val characterId: String,
        /** Token/identity shown to the player when it differs from reality. */
        val shownAs: String = characterId,
    )

    private enum class ChangeKind {
        ADD_REMINDER,
        MOVE_REMINDER,
        REMOVE_REMINDER,
        KILL,
        REVIVE,
        ASSIGN_CHARACTER,
        FLIP_ALIGNMENT,
        USE_GHOST_VOTE,
    }

    private data class Change(
        val kind: ChangeKind,
        val player: String,
        val sourceId: String = "",
        val label: String = "",
        val cause: DeathCause = DeathCause.STORYTELLER,
        val characterId: String = "",
    )

    private data class InfoCheck(
        val characterId: String,
        val holder: String?,
        val targets: List<String> = emptyList(),
        val engineHeadlineContains: String,
        /** Exact storyteller-facing signal/card used in this playthrough. */
        val informationShown: String,
        val caveatContains: String? = null,
    )

    private data class NightAction(
        val stepId: String,
        val actor: String,
        val targets: List<String> = emptyList(),
        val record: String,
        val changes: List<Change> = emptyList(),
        val info: InfoCheck? = null,
        /** A protected/triggering target whose pre-death warning is verified. */
        val deathWarningTarget: String? = null,
        val deathWarningContains: String? = null,
    )

    private data class Night(
        val cycle: Int,
        val actions: List<NightAction>,
        val deadAfterNight: Set<String>,
    )

    private data class NominationPlay(
        val nominator: String,
        val nominee: String,
        val voters: List<String>,
        val expectedResult: NominationResult,
        val record: String,
        val changesAfter: List<Change> = emptyList(),
    )

    private data class Day(
        val cycle: Int,
        val nominations: List<NominationPlay>,
        val execution: String?,
        val executionRecord: String,
        val postExecutionChanges: List<Change> = emptyList(),
        val deadAfterDay: Set<String>,
    )

    private data class Scenario(
        val scriptId: String,
        val title: String,
        val seats: List<Seat>,
        val bluffs: List<String>,
        val setupChanges: List<Change> = emptyList(),
        val nights: List<Night>,
        val days: List<Day>,
        val declaredGoodWin: Boolean,
        val outcome: String,
        /**
         * Engine advisory can intentionally differ from the declared result
         * when a manual end condition (notably Mastermind) owns the outcome.
         */
        val engineAdvisoryGoodWin: Boolean?,
        val engineCautionContains: String? = null,
    )

    @Test
    fun `15 player Trouble Brewing complete game`() {
        val result = replay(troubleBrewing())
        assertEquals(Phase.DAY, result.phase)
        assertEquals(4, result.cycle)
        assertFalse(assertNotNull(result.player(id(result, "Alice"))).alive)
    }

    @Test
    fun `15 player Sects and Violets complete game`() {
        val result = replay(sectsAndViolets())
        assertEquals(Phase.DAY, result.phase)
        assertEquals(4, result.cycle)
        assertEquals("barber", assertNotNull(result.player(id(result, "Nia"))).characterId)
    }

    @Test
    fun `15 player Bad Moon Rising complete game including Mastermind day`() {
        val result = replay(badMoonRising())
        assertEquals(Phase.DAY, result.phase)
        assertEquals(5, result.cycle)
        assertFalse(assertNotNull(result.player(id(result, "Mina"))).alive)
        // The engine remains advisory here; the fixture records the actual
        // Mastermind ruling (executed good player -> evil wins).
        assertTrue(
            WinCheck.check(result, data::character)?.cautions.orEmpty()
                .any { "Mastermind" in it },
        )
    }

    @Test
    fun `late game Witch warning stops below four alive and Saint execution ends game`() {
        val sv = data.builtInScripts().first { it.id == "sv" }
        var witchState = GameActions.newGame(sv, listOf("Demon", "Witch", "Cursed"))
        listOf("vigormortis", "witch", "dreamer").forEachIndexed { index, role ->
            witchState = GameActions.assignCharacter(witchState, index.toLong(), role)
        }
        witchState = GameActions.addReminder(
            witchState,
            2,
            PlacedReminder("witch", "Cursed"),
        )
        assertTrue(
            StatusEffects.nominationWarnings(
                witchState,
                data::character,
                nominatorId = 2,
                nomineeId = 0,
            ).none { "Witch-cursed" in it },
            "The Witch has no ability with only three players alive",
        )

        val tb = data.builtInScripts().first { it.id == "tb" }
        var saintState = GameActions.newGame(tb, listOf("Imp", "Saint", "Mayor", "Chef", "Spy"))
        listOf("imp", "saint", "mayor", "chef", "spy").forEachIndexed { index, role ->
            saintState = GameActions.assignCharacter(saintState, index.toLong(), role)
        }
        saintState = GameActions.advancePhase(GameActions.advancePhase(saintState))
        saintState = GameActions.kill(saintState, 1, DeathCause.EXECUTION)
        val saintLoss = assertNotNull(WinCheck.check(saintState, data::character))
        assertEquals(false, saintLoss.goodWins)
        assertTrue("Saint" in saintLoss.reason)

        var poisonedSaint = GameActions.newGame(tb, listOf("Imp", "Saint", "Mayor", "Chef", "Spy"))
        listOf("imp", "saint", "mayor", "chef", "spy").forEachIndexed { index, role ->
            poisonedSaint = GameActions.assignCharacter(poisonedSaint, index.toLong(), role)
        }
        poisonedSaint = GameActions.addReminder(
            poisonedSaint,
            1,
            PlacedReminder("poisoner", "Poisoned"),
        )
        poisonedSaint = GameActions.advancePhase(GameActions.advancePhase(poisonedSaint))
        poisonedSaint = GameActions.kill(poisonedSaint, 1, DeathCause.EXECUTION)
        assertEquals(
            null,
            WinCheck.check(poisonedSaint, data::character),
            "A poisoned Saint has no ability and must not lose the game",
        )
        poisonedSaint = GameActions.removeReminder(poisonedSaint, 1, 0)
        assertEquals(
            null,
            WinCheck.check(poisonedSaint, data::character),
            "Moving poison later must not reinterpret the historic execution",
        )
    }

    @Test
    fun `nomination replay preserves clock order ties ghost votes exile and no execution`() {
        val tb = data.builtInScripts().first { it.id == "tb" }
        var state = GameActions.newGame(tb, listOf("A", "B", "C", "D", "E", "F", "G"))
        listOf("imp", "saint", "mayor", "chef", "spy", "recluse", "soldier")
            .forEachIndexed { index, role ->
                state = GameActions.assignCharacter(state, index.toLong(), role)
            }
        state = GameActions.addSeat(state, "Traveller")
        val travellerId = state.players.last().id
        state = GameActions.assignCharacter(state, travellerId, "beggar", isTraveller = true)
        state = GameActions.advancePhase(GameActions.advancePhase(state))
        state = GameActions.kill(state, 5, DeathCause.STORYTELLER)

        fun record(
            nominator: Long,
            nominee: Long,
            voterIdsInClockOrder: List<Long>,
            result: NominationResult,
            isExile: Boolean = false,
        ) {
            state = GameActions.recordNomination(
                state,
                Nomination(
                    day = state.cycle,
                    nominatorId = nominator,
                    nomineeId = nominee,
                    votes = voterIdsInClockOrder.size,
                    voterIds = voterIdsInClockOrder,
                    result = result,
                    isExile = isExile,
                ),
            )
            assertEquals(
                voterIdsInClockOrder,
                state.nominations.last().voterIds,
                "replay must retain clockwise voter order exactly",
            )
        }

        val firstClockOrder = listOf(2L, 3L, 5L, 7L)
        record(0, 1, firstClockOrder, NominationResult.ABOUT_TO_DIE)
        state = GameActions.toggleGhostVote(state, 5)
        assertTrue(assertNotNull(state.player(5)).ghostVoteUsed)

        val tieClockOrder = listOf(4L, 6L, 7L, 0L)
        record(2, 3, tieClockOrder, NominationResult.TIED)
        assertEquals(null, GameActions.aboutToDie(state))

        // A tied day advances with no execution death.
        val deathsBeforeDusk = state.deaths.toList()
        state = GameActions.advancePhase(state)
        assertEquals(Phase.NIGHT, state.phase)
        assertEquals(deathsBeforeDusk, state.deaths)

        state = GameActions.advancePhase(state)
        val exileClockOrder = listOf(0L, 2L, 4L, 6L)
        assertEquals(4, state.exileThreshold)
        record(1, travellerId, exileClockOrder, NominationResult.ABOUT_TO_DIE, isExile = true)
        assertEquals(null, GameActions.aboutToDie(state), "traveller exile never occupies the execution block")
        val exileOnlyFlowergirl = assertNotNull(
            InfoCalc.compute(data, state, "flowergirl", holderId = 2),
        )
        assertTrue(
            exileOnlyFlowergirl.headline.startsWith("NO"),
            "A Demon supporting an exile did not vote for Flowergirl purposes",
        )

        record(
            nominator = 2,
            nominee = 4,
            voterIdsInClockOrder = listOf(3L, 4L, 6L, 0L),
            result = NominationResult.ABOUT_TO_DIE,
        )
        val executionVoteFlowergirl = assertNotNull(
            InfoCalc.compute(data, state, "flowergirl", holderId = 2),
        )
        assertTrue(executionVoteFlowergirl.headline.startsWith("YES"))
        state = GameActions.kill(state, travellerId, DeathCause.EXILE)
        assertEquals(DeathCause.EXILE, state.deaths.last().cause)
    }

    private fun replay(scenario: Scenario): GameState {
        assertEquals(15, scenario.seats.size, "${scenario.title}: seat count")
        assertEquals(15, scenario.seats.map { it.name }.toSet().size, "${scenario.title}: unique names")
        assertEquals(scenario.nights.map { it.cycle }, scenario.days.map { it.cycle })
        assertTrue(scenario.outcome.isNotBlank())

        val script = data.builtInScripts().first { it.id == scenario.scriptId }
        val actualBag = scenario.seats.map { seat ->
            assertNotNull(data.character(seat.characterId), seat.characterId)
        }
        assertTrue(
            GameActions.validateBag(actualBag, scenario.seats.size).isEmpty(),
            "${scenario.title} illegal bag: ${GameActions.validateBag(actualBag, scenario.seats.size)}",
        )

        var state = GameActions.newGame(script, scenario.seats.map { it.name })
        scenario.seats.forEachIndexed { index, seat ->
            state = GameActions.assignCharacter(state, index.toLong(), seat.characterId)
            if (seat.shownAs != seat.characterId) {
                state = GameActions.setShownCharacter(state, index.toLong(), seat.shownAs)
            }
            assertEquals(
                seat.shownAs,
                assertNotNull(state.player(index.toLong())).characterShownToPlayerId,
                "${scenario.title}: ${seat.name}'s shown identity",
            )
        }
        state = GameActions.setBluffs(state, scenario.bluffs)
        state = applyChanges(state, scenario.setupChanges)

        for ((night, day) in scenario.nights.zip(scenario.days)) {
            state = GameActions.advancePhase(state)
            assertEquals(Phase.NIGHT, state.phase)
            assertEquals(night.cycle, state.cycle)

            val sheet = NightPlan.build(state, data::character).steps
            val recordedSheetIds = night.actions
                .filterNot { it.stepId.startsWith("MANUAL:") }
                .map { it.stepId }
            assertEquals(
                sheet.map { it.slotId },
                recordedSheetIds,
                "${scenario.title} night ${night.cycle}: every night-sheet row must be recorded in order",
            )

            for (action in night.actions) {
                assertTrue(action.actor.isNotBlank())
                assertTrue(action.record.isNotBlank())
                action.targets.forEach { target -> assertNotNull(state.players.find { it.name == target }) }

                if (action.deathWarningTarget != null) {
                    val notes = StatusEffects.deathNotes(
                        state,
                        data::character,
                        id(state, action.deathWarningTarget),
                    )
                    assertTrue(
                        notes.any { action.deathWarningContains.orEmpty() in it },
                        "${scenario.title} N${night.cycle} ${action.stepId}: $notes",
                    )
                }

                state = applyChanges(state, action.changes)
                action.info?.let { expected ->
                    val result = assertNotNull(
                        InfoCalc.compute(
                            data = data,
                            state = state,
                            characterId = expected.characterId,
                            holderId = expected.holder?.let { id(state, it) },
                            targets = expected.targets.map { id(state, it) },
                        ),
                        "${scenario.title} N${night.cycle}: ${expected.characterId}",
                    )
                    assertTrue(
                        expected.engineHeadlineContains in result.headline,
                        "${expected.characterId}: ${result.headline}",
                    )
                    assertTrue(expected.informationShown.isNotBlank())
                    expected.caveatContains?.let { caveat ->
                        assertTrue(
                            result.caveats.any { caveat in it },
                            "${expected.characterId} caveats: ${result.caveats}",
                        )
                    }
                }
                if (!action.stepId.startsWith("MANUAL:")) {
                    state = GameActions.toggleNightStep(state, action.stepId)
                }
            }
            assertEquals(
                sheet.map { it.slotId }.toSet(),
                state.nightStepsDone,
                "${scenario.title} N${night.cycle}: checklist completion",
            )
            assertEquals(night.deadAfterNight, deadNames(state))

            state = GameActions.advancePhase(state)
            assertEquals(Phase.DAY, state.phase)
            assertEquals(day.cycle, state.cycle)

            for (nomination in day.nominations) {
                assertFalse(
                    GameActions.hasNominatedToday(state, id(state, nomination.nominator)),
                    "${nomination.nominator} already nominated on day ${day.cycle}",
                )
                assertFalse(
                    GameActions.hasBeenNominatedToday(state, id(state, nomination.nominee)),
                    "${nomination.nominee} already nominated on day ${day.cycle}",
                )
                assertEquals(nomination.voters.size, nomination.voters.toSet().size)
                assertTrue(nomination.record.isNotBlank())

                for (voterName in nomination.voters) {
                    val voterId = id(state, voterName)
                    val voter = assertNotNull(state.player(voterId))
                    if (!voter.alive) {
                        assertFalse(voter.ghostVoteUsed, "$voterName tried to spend a second ghost vote")
                        state = GameActions.toggleGhostVote(state, voterId)
                    }
                }
                val computed = if (nomination.expectedResult == NominationResult.WITHDRAWN) {
                    NominationResult.WITHDRAWN
                } else {
                    Voting.outcome(
                        votes = nomination.voters.size,
                        threshold = state.executionThreshold,
                        currentHighest = GameActions.highestVotesToday(state),
                    )
                }
                assertEquals(
                    nomination.expectedResult,
                    computed,
                    "${scenario.title} D${day.cycle}: ${nomination.nominator} -> ${nomination.nominee}",
                )
                state = GameActions.recordNomination(
                    state,
                    Nomination(
                        day = day.cycle,
                        nominatorId = id(state, nomination.nominator),
                        nomineeId = id(state, nomination.nominee),
                        votes = nomination.voters.size,
                        voterIds = nomination.voters.map { id(state, it) },
                        result = computed,
                    ),
                )
                assertEquals(
                    nomination.voters.map { id(state, it) },
                    state.nominations.last().voterIds,
                    "${scenario.title} D${day.cycle}: voter replay order",
                )
                state = applyChanges(state, nomination.changesAfter)
            }

            if (day.execution != null) {
                assertEquals(
                    id(state, day.execution),
                    GameActions.aboutToDie(state),
                    "${scenario.title} D${day.cycle}: execution must match the block",
                )
                state = GameActions.kill(state, id(state, day.execution), DeathCause.EXECUTION)
            }
            assertTrue(day.executionRecord.isNotBlank())
            state = applyChanges(state, day.postExecutionChanges)
            assertEquals(day.deadAfterDay, deadNames(state))
        }

        val advisory = WinCheck.check(state, data::character)
        assertEquals(scenario.engineAdvisoryGoodWin, advisory?.goodWins, scenario.title)
        scenario.engineCautionContains?.let { expected ->
            assertTrue(advisory?.cautions.orEmpty().any { expected in it })
        }
        // The declared outcome is deliberately a separate assertion: some
        // endings require a storyteller ruling beyond the current state model.
        assertTrue(if (scenario.declaredGoodWin) "good" in scenario.outcome.lowercase() else "evil" in scenario.outcome.lowercase())
        return state
    }

    private fun applyChanges(initial: GameState, changes: List<Change>): GameState {
        var state = initial
        for (change in changes) {
            val playerId = id(state, change.player)
            state = when (change.kind) {
                ChangeKind.ADD_REMINDER -> GameActions.addReminder(
                    state,
                    playerId,
                    PlacedReminder(change.sourceId, change.label),
                )
                ChangeKind.MOVE_REMINDER -> GameActions.placeExclusiveReminder(
                    state,
                    playerId,
                    PlacedReminder(change.sourceId, change.label),
                )
                ChangeKind.REMOVE_REMINDER -> state.updatePlayer(playerId) { player ->
                    player.copy(
                        reminders = player.reminders.filterNot {
                            it.sourceId == change.sourceId && it.label.equals(change.label, ignoreCase = true)
                        },
                    )
                }
                ChangeKind.KILL -> GameActions.kill(state, playerId, change.cause)
                ChangeKind.REVIVE -> GameActions.revive(state, playerId)
                ChangeKind.ASSIGN_CHARACTER -> GameActions.assignCharacter(state, playerId, change.characterId)
                ChangeKind.FLIP_ALIGNMENT -> GameActions.flipAlignment(state, playerId)
                ChangeKind.USE_GHOST_VOTE -> GameActions.toggleGhostVote(state, playerId)
            }
        }
        return state
    }

    private fun id(state: GameState, name: String): Long =
        assertNotNull(state.players.find { it.name == name }, name).id

    private fun deadNames(state: GameState): Set<String> =
        state.players.filterNot { it.alive }.map { it.name }.toSet()

    private fun add(player: String, source: String, label: String) =
        Change(ChangeKind.ADD_REMINDER, player, source, label)

    private fun move(player: String, source: String, label: String) =
        Change(ChangeKind.MOVE_REMINDER, player, source, label)

    private fun remove(player: String, source: String, label: String) =
        Change(ChangeKind.REMOVE_REMINDER, player, source, label)

    private fun kill(player: String, cause: DeathCause) =
        Change(ChangeKind.KILL, player, cause = cause)

    private fun revive(player: String) = Change(ChangeKind.REVIVE, player)

    private fun assign(player: String, characterId: String) =
        Change(ChangeKind.ASSIGN_CHARACTER, player, characterId = characterId)

    private fun action(
        step: String,
        actor: String,
        record: String,
        targets: List<String> = emptyList(),
        changes: List<Change> = emptyList(),
        info: InfoCheck? = null,
        warningTarget: String? = null,
        warningContains: String? = null,
    ) = NightAction(step, actor, targets, record, changes, info, warningTarget, warningContains)

    private fun nomination(
        nominator: String,
        nominee: String,
        voters: List<String>,
        result: NominationResult,
        record: String,
        changes: List<Change> = emptyList(),
    ) = NominationPlay(nominator, nominee, voters, result, record, changes)

    private fun troubleBrewing(): Scenario {
        val seats = listOf(
            Seat("Alice", "imp"),
            Seat("Ben", "empath"),
            Seat("Cara", "poisoner"),
            Seat("Diego", "fortuneteller"),
            Seat("Eve", "drunk", shownAs = "washerwoman"),
            Seat("Finn", "investigator"),
            Seat("Grace", "baron"),
            Seat("Hugo", "butler"),
            Seat("Iris", "virgin"),
            Seat("Jonas", "spy"),
            Seat("Kira", "monk"),
            Seat("Liam", "recluse"),
            Seat("Maya", "undertaker"),
            Seat("Noah", "saint"),
            Seat("Opal", "mayor"),
        )
        val setup = listOf(
            add("Eve", "drunk", "Is The Drunk"),
            add("Opal", "fortuneteller", "Red Herring"),
        )

        val n1 = Night(
            1,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Eyes closed; 15 players accounted for."),
                action(
                    NightMarkers.MINION_INFO,
                    "Grace, Cara, Jonas",
                    "Cara/Grace/Jonas saw one another and were shown Alice as the Imp.",
                    targets = listOf("Alice"),
                ),
                action(
                    NightMarkers.DEMON_INFO,
                    "Alice",
                    "Alice saw all three minions and bluffs Chef, Soldier, Librarian.",
                    targets = listOf("Cara", "Grace", "Jonas"),
                ),
                action(
                    "poisoner",
                    "Cara",
                    "Cara poisoned Ben for night 1.",
                    targets = listOf("Ben"),
                    changes = listOf(move("Ben", "poisoner", "Poisoned")),
                ),
                action(
                    "washerwoman",
                    "Eve",
                    "Eve believes she is the Washerwoman; she was shown Monk and pointed to Kira plus Opal.",
                    targets = listOf("Kira", "Opal"),
                    info = InfoCheck(
                        "washerwoman",
                        "Eve",
                        engineHeadlineContains = "1 of 2 players is the",
                        informationShown = "Monk: Kira or Opal",
                        caveatContains = "IS the Drunk",
                    ),
                ),
                action(
                    "investigator",
                    "Finn",
                    "Finn was shown Poisoner and pointed to Cara plus Kira.",
                    targets = listOf("Cara", "Kira"),
                    info = InfoCheck("investigator", "Finn", engineHeadlineContains = "1 of 2 players is the", informationShown = "Poisoner: Cara or Kira"),
                ),
                action(
                    "empath",
                    "Ben",
                    "True count is 2 (Imp and Poisoner neighbours); poisoned Ben was shown 0.",
                    targets = listOf("Alice", "Cara"),
                    info = InfoCheck("empath", "Ben", engineHeadlineContains = "2 of", informationShown = "0", caveatContains = "POISONED"),
                ),
                action(
                    "fortuneteller",
                    "Diego",
                    "Diego chose Alice and red-herring Opal and received YES.",
                    targets = listOf("Alice", "Opal"),
                    info = InfoCheck("fortuneteller", "Diego", listOf("Alice", "Opal"), "YES", "YES"),
                ),
                action(
                    "butler",
                    "Hugo",
                    "Hugo selected Maya as Master.",
                    targets = listOf("Maya"),
                    changes = listOf(move("Maya", "butler", "Master")),
                ),
                action("spy", "Jonas", "Jonas reviewed the full grimoire."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: nobody died on the first night."),
            ),
            emptySet(),
        )
        val d1 = Day(
            1,
            listOf(
                nomination(
                    "Ben",
                    "Iris",
                    emptyList(),
                    NominationResult.WITHDRAWN,
                    "Ben nominated virgin Iris. Her ability fired: Ben was executed immediately before voting.",
                    listOf(
                        kill("Ben", DeathCause.EXECUTION),
                        add("Iris", "virgin", "No Ability"),
                    ),
                ),
            ),
            execution = null,
            executionRecord = "The Virgin trigger was the day's execution; nominations closed.",
            deadAfterDay = setOf("Ben"),
        )

        val n2 = Night(
            2,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 2 began; Ben remained dead with an unused ghost vote."),
                action(
                    "poisoner",
                    "Cara",
                    "Cara moved poison from Ben to undertaker Maya.",
                    targets = listOf("Maya"),
                    changes = listOf(move("Maya", "poisoner", "Poisoned")),
                ),
                action(
                    "monk",
                    "Kira",
                    "Kira protected Noah from the Demon.",
                    targets = listOf("Noah"),
                    changes = listOf(move("Noah", "monk", "Safe")),
                ),
                action(
                    "imp",
                    "Alice",
                    "Alice attacked Kira; Kira died.",
                    targets = listOf("Kira"),
                    changes = listOf(kill("Kira", DeathCause.DEMON)),
                ),
                action("empath", "Ben", "Ben is dead; skipped the Empath row."),
                action(
                    "fortuneteller",
                    "Diego",
                    "Diego chose Cara and Jonas. True answer NO; Spy/Recluse caveat was visible.",
                    targets = listOf("Cara", "Jonas"),
                    info = InfoCheck("fortuneteller", "Diego", listOf("Cara", "Jonas"), "NO", "NO", "Spy"),
                ),
                action(
                    "undertaker",
                    "Maya",
                    "True token was Empath for executed Ben; poisoned Maya was instead shown Chef.",
                    targets = listOf("Ben"),
                    info = InfoCheck("undertaker", "Maya", engineHeadlineContains = "Empath", informationShown = "Chef", caveatContains = "POISONED"),
                ),
                action(
                    "butler",
                    "Hugo",
                    "Hugo changed Master to Diego.",
                    targets = listOf("Diego"),
                    changes = listOf(move("Diego", "butler", "Master")),
                ),
                action("spy", "Jonas", "Jonas reviewed the grimoire, including Maya's poison."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Kira's death."),
            ),
            setOf("Ben", "Kira"),
        )
        val d2 = Day(
            2,
            listOf(
                nomination("Noah", "Grace", listOf("Noah", "Opal", "Liam", "Hugo", "Iris", "Finn"), NominationResult.SAFE, "6 votes; threshold 7."),
                nomination("Maya", "Cara", listOf("Maya", "Diego", "Eve", "Finn", "Hugo", "Iris", "Noah", "Opal"), NominationResult.ABOUT_TO_DIE, "8 votes put Cara on the block."),
                nomination("Jonas", "Alice", listOf("Alice", "Cara", "Grace", "Jonas", "Liam", "Maya", "Noah", "Opal"), NominationResult.TIED, "8 votes tied the high tally and cleared the block."),
                nomination("Liam", "Jonas", listOf("Ben", "Liam", "Maya", "Noah", "Opal", "Diego", "Eve", "Finn", "Hugo"), NominationResult.ABOUT_TO_DIE, "9 votes; Ben spent his sole ghost vote."),
            ),
            execution = "Jonas",
            executionRecord = "Jonas was executed and revealed only through the storyteller's own notes as the Spy.",
            deadAfterDay = setOf("Ben", "Kira", "Jonas"),
        )

        val n3 = Night(
            3,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 3 began with three dead players."),
                action(
                    "poisoner",
                    "Cara",
                    "Cara moved poison to fortune teller Diego.",
                    targets = listOf("Diego"),
                    changes = listOf(move("Diego", "poisoner", "Poisoned")),
                ),
                action("monk", "Kira", "Kira is dead; skipped."),
                action(
                    "imp",
                    "Alice",
                    "Alice attacked investigator Finn; Finn died.",
                    targets = listOf("Finn"),
                    changes = listOf(kill("Finn", DeathCause.DEMON)),
                ),
                action("empath", "Ben", "Ben is dead; skipped."),
                action(
                    "fortuneteller",
                    "Diego",
                    "Diego chose Alice and Noah. True YES was falsified to NO because Diego was poisoned.",
                    targets = listOf("Alice", "Noah"),
                    info = InfoCheck("fortuneteller", "Diego", listOf("Alice", "Noah"), "YES", "NO", "POISONED"),
                ),
                action(
                    "undertaker",
                    "Maya",
                    "Maya learned that yesterday's executee Jonas was the Spy.",
                    targets = listOf("Jonas"),
                    info = InfoCheck("undertaker", "Maya", engineHeadlineContains = "Spy", informationShown = "Spy"),
                ),
                action("butler", "Hugo", "Hugo kept Diego as Master.", targets = listOf("Diego"), changes = listOf(move("Diego", "butler", "Master"))),
                action("spy", "Jonas", "Jonas is dead; skipped."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Finn's death."),
            ),
            setOf("Ben", "Kira", "Jonas", "Finn"),
        )
        val d3 = Day(
            3,
            listOf(
                nomination("Cara", "Noah", listOf("Cara", "Grace", "Alice", "Hugo", "Iris", "Liam"), NominationResult.ABOUT_TO_DIE, "6 votes met threshold."),
                nomination("Opal", "Cara", listOf("Opal", "Maya", "Diego", "Eve", "Hugo", "Iris", "Liam"), NominationResult.ABOUT_TO_DIE, "7 votes beat the high tally."),
            ),
            execution = "Cara",
            executionRecord = "Cara the Poisoner was executed; her poison must end.",
            deadAfterDay = setOf("Ben", "Kira", "Jonas", "Finn", "Cara"),
        )

        val n4 = Night(
            4,
            listOf(
                action(
                    NightMarkers.DUSK,
                    "Storyteller",
                    "Removed dead Poisoner's lingering token before night actions.",
                    changes = listOf(remove("Diego", "poisoner", "Poisoned")),
                ),
                action("poisoner", "Cara", "Cara is dead; skipped and applied no poison."),
                action("monk", "Kira", "Kira is dead; skipped."),
                action(
                    "imp",
                    "Alice",
                    "Alice attacked undertaker Maya; Maya died before her row.",
                    targets = listOf("Maya"),
                    changes = listOf(kill("Maya", DeathCause.DEMON)),
                ),
                action("empath", "Ben", "Ben is dead; skipped."),
                action(
                    "fortuneteller",
                    "Diego",
                    "Now sober, Diego chose Alice and Noah and received the true YES.",
                    targets = listOf("Alice", "Noah"),
                    info = InfoCheck("fortuneteller", "Diego", listOf("Alice", "Noah"), "YES", "YES"),
                ),
                action("undertaker", "Maya", "Maya died earlier tonight; skipped."),
                action("butler", "Hugo", "Hugo selected Opal as Master.", targets = listOf("Opal"), changes = listOf(move("Opal", "butler", "Master"))),
                action("spy", "Jonas", "Jonas is dead; skipped."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Maya's death."),
            ),
            setOf("Ben", "Kira", "Jonas", "Finn", "Cara", "Maya"),
        )
        val d4 = Day(
            4,
            listOf(
                nomination("Noah", "Alice", listOf("Noah", "Opal", "Diego", "Eve", "Hugo"), NominationResult.ABOUT_TO_DIE, "5 votes met the 9-alive threshold."),
                nomination("Alice", "Noah", listOf("Alice", "Grace", "Liam", "Iris"), NominationResult.SAFE, "4 votes did not tie the block."),
            ),
            execution = "Alice",
            executionRecord = "Alice the Imp was executed. With no Scarlet Woman and all Demons dead, good won.",
            deadAfterDay = setOf("Alice", "Ben", "Kira", "Jonas", "Finn", "Cara", "Maya"),
        )

        return Scenario(
            scriptId = "tb",
            title = "Trouble Brewing — The poisoned circle",
            seats = seats,
            bluffs = listOf("chef", "soldier", "librarian"),
            setupChanges = setup,
            nights = listOf(n1, n2, n3, n4),
            days = listOf(d1, d2, d3, d4),
            declaredGoodWin = true,
            outcome = "Good wins on day 4 when Alice the Imp is executed.",
            engineAdvisoryGoodWin = true,
        )
    }

    private fun sectsAndViolets(): Scenario {
        val seats = listOf(
            Seat("Ada", "vigormortis"),
            Seat("Beau", "dreamer"),
            Seat("Cleo", "witch"),
            Seat("Dax", "clockmaker"),
            Seat("Esme", "cerenovus"),
            Seat("Farah", "oracle"),
            Seat("Gio", "pithag"),
            Seat("Hana", "flowergirl"),
            Seat("Ivan", "sweetheart"),
            Seat("Juno", "towncrier"),
            Seat("Kai", "sage"),
            Seat("Luz", "seamstress"),
            Seat("Milo", "snakecharmer"),
            Seat("Nia", "savant"),
            Seat("Otis", "mathematician"),
        )

        val n1 = Night(
            1,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Eyes closed; Vigormortis bag verified as 10/1/3/1."),
                action(NightMarkers.MINION_INFO, "Cleo, Esme, Gio", "Three minions saw one another and Ada."),
                action(NightMarkers.DEMON_INFO, "Ada", "Ada saw all minions and the three bluffs Artist, Juggler, Mutant."),
                action("snakecharmer", "Milo", "Milo selected Farah; no swap.", targets = listOf("Farah")),
                action("witch", "Cleo", "Cleo cursed Juno.", targets = listOf("Juno"), changes = listOf(move("Juno", "witch", "Cursed"))),
                action("cerenovus", "Esme", "Esme made Ivan mad as the Mutant.", targets = listOf("Ivan"), changes = listOf(move("Ivan", "cerenovus", "Mad"))),
                action(
                    "clockmaker",
                    "Dax",
                    "Dax learned 2 steps to the nearest Minion (Cleo).",
                    targets = listOf("Cleo"),
                    info = InfoCheck("clockmaker", "Dax", engineHeadlineContains = "2 steps", informationShown = "2"),
                ),
                action(
                    "dreamer",
                    "Beau",
                    "Beau chose Ada and was shown Vigormortis plus Artist.",
                    targets = listOf("Ada"),
                    info = InfoCheck("dreamer", "Beau", listOf("Ada"), "Vigormortis", "Vigormortis + Artist"),
                ),
                action(
                    "seamstress",
                    "Luz",
                    "Luz used her ability on Beau and Cleo and learned NO (different alignments).",
                    targets = listOf("Beau", "Cleo"),
                    changes = listOf(add("Luz", "seamstress", "No Ability")),
                    info = InfoCheck("seamstress", "Luz", listOf("Beau", "Cleo"), "NO", "NO"),
                ),
                action(
                    "mathematician",
                    "Otis",
                    "No abilities had malfunctioned; Otis was shown 0.",
                    info = InfoCheck("mathematician", "Otis", engineHeadlineContains = "malfunctioned since dawn", informationShown = "0"),
                ),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: no deaths."),
            ),
            emptySet(),
        )
        val d1 = Day(
            1,
            listOf(
                nomination(
                    "Juno",
                    "Ivan",
                    listOf("Ada", "Beau", "Dax", "Farah", "Hana", "Kai"),
                    NominationResult.SAFE,
                    "Cursed Juno died immediately after nominating; the nomination continued and received 6 votes.",
                    listOf(kill("Juno", DeathCause.STORYTELLER)),
                ),
                nomination("Ivan", "Cleo", listOf("Ivan", "Ada", "Beau", "Dax", "Farah", "Hana", "Kai"), NominationResult.ABOUT_TO_DIE, "7 votes met the post-Witch-death threshold."),
                nomination("Cleo", "Beau", listOf("Cleo", "Esme", "Gio", "Luz", "Milo", "Nia"), NominationResult.SAFE, "6 votes was below threshold."),
                nomination("Dax", "Esme", listOf("Dax", "Beau", "Farah", "Hana", "Kai", "Luz"), NominationResult.SAFE, "6 votes was below threshold."),
            ),
            execution = "Cleo",
            executionRecord = "Cleo the Witch was executed; good had found one minion.",
            deadAfterDay = setOf("Juno", "Cleo"),
        )

        val n2 = Night(
            2,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 2 began with Witch dead and Cerenovus still alive."),
                action("snakecharmer", "Milo", "Milo selected Hana; no swap.", targets = listOf("Hana")),
                action("witch", "Cleo", "Cleo is dead and has no Vigormortis reminder; skipped."),
                action("cerenovus", "Esme", "Esme made Kai mad as the Artist.", targets = listOf("Kai"), changes = listOf(move("Kai", "cerenovus", "Mad"))),
                action("pithag", "Gio", "Gio chose no character change."),
                action(
                    "vigormortis",
                    "Ada",
                    "Ada killed minion Esme. Esme retained Cerenovus; Farah was marked as the poisoned Townsfolk neighbour.",
                    targets = listOf("Esme", "Farah"),
                    changes = listOf(
                        kill("Esme", DeathCause.DEMON),
                        add("Esme", "vigormortis", "Has Ability"),
                        move("Farah", "vigormortis", "Poisoned"),
                    ),
                ),
                action("sweetheart", "Ivan", "Ivan is alive; Sweetheart row skipped."),
                action("sage", "Kai", "Kai did not die tonight; Sage row skipped."),
                action(
                    "dreamer",
                    "Beau",
                    "Beau chose Gio and was shown Pit-Hag plus Savant.",
                    targets = listOf("Gio"),
                    info = InfoCheck("dreamer", "Beau", listOf("Gio"), "Pit-Hag", "Pit-Hag + Savant"),
                ),
                action(
                    "flowergirl",
                    "Hana",
                    "Ada voted on day 1, so Hana learned YES.",
                    info = InfoCheck("flowergirl", "Hana", engineHeadlineContains = "YES", informationShown = "YES"),
                ),
                action(
                    "towncrier",
                    "Juno",
                    "Juno is dead; the engine can calculate YES because minion Cleo nominated, but the player did not wake.",
                    info = InfoCheck("towncrier", "Juno", engineHeadlineContains = "YES", informationShown = "SKIPPED (dead)", caveatContains = "dead"),
                ),
                action(
                    "oracle",
                    "Farah",
                    "Two dead players were evil (Cleo, Esme); poisoned Farah was shown 1.",
                    targets = listOf("Cleo", "Esme"),
                    info = InfoCheck("oracle", "Farah", engineHeadlineContains = "2 dead", informationShown = "1", caveatContains = "POISONED"),
                ),
                action("seamstress", "Luz", "Luz had spent her once-per-game ability; skipped."),
                action("mathematician", "Otis", "One malfunction (poisoned Oracle) was tracked; Otis was shown 1.", info = InfoCheck("mathematician", "Otis", engineHeadlineContains = "malfunctioned since dawn", informationShown = "1")),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Esme's death."),
            ),
            setOf("Juno", "Cleo", "Esme"),
        )
        val d2 = Day(
            2,
            listOf(
                nomination("Kai", "Ivan", listOf("Kai", "Beau", "Dax", "Farah", "Hana", "Luz"), NominationResult.ABOUT_TO_DIE, "6 votes put Ivan on the block."),
                nomination("Hana", "Ada", listOf("Hana", "Ada", "Beau", "Dax", "Milo"), NominationResult.SAFE, "5 votes was below threshold."),
                nomination("Gio", "Kai", listOf("Gio", "Esme", "Farah", "Luz", "Nia"), NominationResult.SAFE, "5 votes was below threshold; dead Esme spent a ghost vote."),
            ),
            execution = "Ivan",
            executionRecord = "Ivan the Sweetheart was executed, requiring a permanent drunk choice that night.",
            deadAfterDay = setOf("Juno", "Cleo", "Esme", "Ivan"),
        )

        val n3 = Night(
            3,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 3 began; dead Esme still had a Vigormortis 'Has ability' marker."),
                action("snakecharmer", "Milo", "Milo selected Dax; no swap.", targets = listOf("Dax")),
                action("witch", "Cleo", "Cleo is dead without retained ability; skipped."),
                action("cerenovus", "Esme", "Dead-but-retained Esme made Beau mad as the Artist.", targets = listOf("Beau"), changes = listOf(move("Beau", "cerenovus", "Mad"))),
                action(
                    "pithag",
                    "Gio",
                    "Gio changed Nia from Savant into the out-of-play Barber.",
                    targets = listOf("Nia"),
                    changes = listOf(assign("Nia", "barber")),
                ),
                action(
                    "vigormortis",
                    "Ada",
                    "Ada killed Kai the Sage.",
                    targets = listOf("Kai"),
                    changes = listOf(kill("Kai", DeathCause.DEMON)),
                    warningTarget = "Kai",
                    warningContains = "Sage",
                ),
                action(
                    "sweetheart",
                    "Ivan",
                    "Because Ivan died today, Beau became permanently drunk.",
                    targets = listOf("Beau"),
                    changes = listOf(add("Beau", "sweetheart", "Drunk")),
                ),
                action("sage", "Kai", "Kai woke on death and was shown Ada plus Milo, one of whom was the Demon.", targets = listOf("Ada", "Milo")),
                action(
                    "dreamer",
                    "Beau",
                    "Drunk Beau chose Ada; true pair was Vigormortis + a good role, but was shown Witch + Clockmaker.",
                    targets = listOf("Ada"),
                    info = InfoCheck("dreamer", "Beau", listOf("Ada"), "Vigormortis", "Witch + Clockmaker", "DRUNK"),
                ),
                action("flowergirl", "Hana", "Ada voted on day 2; Hana learned YES.", info = InfoCheck("flowergirl", "Hana", engineHeadlineContains = "YES", informationShown = "YES")),
                action("towncrier", "Juno", "Juno remained dead; true result YES because Gio nominated.", info = InfoCheck("towncrier", "Juno", engineHeadlineContains = "YES", informationShown = "SKIPPED (dead)", caveatContains = "dead")),
                action("oracle", "Farah", "Two dead evil players remained; poisoned Farah was shown 3.", info = InfoCheck("oracle", "Farah", engineHeadlineContains = "2 dead", informationShown = "3", caveatContains = "POISONED")),
                action("seamstress", "Luz", "Spent ability; skipped."),
                action("mathematician", "Otis", "Dreamer and Oracle both malfunctioned; Otis was shown 2.", info = InfoCheck("mathematician", "Otis", engineHeadlineContains = "malfunctioned since dawn", informationShown = "2")),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Kai's death."),
            ),
            setOf("Juno", "Cleo", "Esme", "Ivan", "Kai"),
        )
        val d3 = Day(
            3,
            listOf(
                nomination("Beau", "Gio", listOf("Beau", "Ada", "Dax", "Farah", "Hana"), NominationResult.ABOUT_TO_DIE, "5 votes met threshold."),
                nomination("Ada", "Beau", listOf("Ada", "Gio", "Hana", "Milo"), NominationResult.SAFE, "4 votes was below threshold."),
                nomination("Milo", "Ada", listOf("Milo", "Luz", "Nia", "Otis"), NominationResult.SAFE, "4 votes was below threshold."),
            ),
            execution = "Gio",
            executionRecord = "Gio the Pit-Hag was executed; Nia remained the Barber.",
            deadAfterDay = setOf("Juno", "Cleo", "Esme", "Ivan", "Kai", "Gio"),
        )

        val n4 = Night(
            4,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 4 began; three minions were dead, only Cerenovus retained an ability."),
                action("snakecharmer", "Milo", "Milo selected Otis; no swap.", targets = listOf("Otis")),
                action("witch", "Cleo", "Dead without retained ability; skipped."),
                action("cerenovus", "Esme", "Retained Cerenovus made Farah mad as the Artist.", targets = listOf("Farah"), changes = listOf(move("Farah", "cerenovus", "Mad"))),
                action("pithag", "Gio", "Dead without retained ability; skipped."),
                action(
                    "vigormortis",
                    "Ada",
                    "Ada killed Hana the Flowergirl before her information row.",
                    targets = listOf("Hana"),
                    changes = listOf(kill("Hana", DeathCause.DEMON)),
                ),
                action("barber", "Nia", "Nia was alive; no Barber death trigger, skipped."),
                action("sweetheart", "Ivan", "Sweetheart already resolved; skipped."),
                action("sage", "Kai", "Sage death already resolved; skipped."),
                action("dreamer", "Beau", "Drunk Beau chose Milo; true Snake Charmer was replaced with a false pair.", targets = listOf("Milo"), info = InfoCheck("dreamer", "Beau", listOf("Milo"), "Snake Charmer", "Witch + Oracle", "DRUNK")),
                action("flowergirl", "Hana", "Hana died earlier tonight; skipped."),
                action("towncrier", "Juno", "No Minion nominated on day 3; dead Juno would have learned NO.", info = InfoCheck("towncrier", "Juno", engineHeadlineContains = "NO", informationShown = "SKIPPED (dead)", caveatContains = "dead")),
                action("oracle", "Farah", "Three dead players were evil; poisoned Farah was shown 2.", info = InfoCheck("oracle", "Farah", engineHeadlineContains = "3 dead", informationShown = "2", caveatContains = "POISONED")),
                action("seamstress", "Luz", "Spent ability; skipped."),
                action("mathematician", "Otis", "Dreamer and Oracle malfunctioned; Otis was shown 2.", info = InfoCheck("mathematician", "Otis", engineHeadlineContains = "malfunctioned since dawn", informationShown = "2")),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Hana's death."),
            ),
            setOf("Juno", "Cleo", "Esme", "Ivan", "Kai", "Gio", "Hana"),
        )
        val d4 = Day(
            4,
            listOf(
                nomination("Farah", "Ada", listOf("Farah", "Beau", "Dax", "Luz"), NominationResult.ABOUT_TO_DIE, "4 votes met threshold with 8 alive."),
                nomination("Ada", "Farah", listOf("Ada", "Milo", "Otis"), NominationResult.SAFE, "3 votes was below threshold."),
            ),
            execution = "Ada",
            executionRecord = "Ada the Vigormortis was executed. With no living Demon, good won.",
            deadAfterDay = setOf("Ada", "Juno", "Cleo", "Esme", "Ivan", "Kai", "Gio", "Hana"),
        )

        return Scenario(
            scriptId = "sv",
            title = "Sects & Violets — The dead Cerenovus",
            seats = seats,
            bluffs = listOf("artist", "juggler", "mutant"),
            nights = listOf(n1, n2, n3, n4),
            days = listOf(d1, d2, d3, d4),
            declaredGoodWin = true,
            outcome = "Good wins on day 4 by executing Ada the Vigormortis.",
            engineAdvisoryGoodWin = true,
        )
    }

    private fun badMoonRising(): Scenario {
        val seats = listOf(
            Seat("Aurora", "po"),
            Seat("Blake", "lunatic", shownAs = "po"),
            Seat("Cora", "devilsadvocate"),
            Seat("Dorian", "chambermaid"),
            Seat("Elena", "assassin"),
            Seat("Felix", "courtier"),
            Seat("Greta", "grandmother"),
            Seat("Hector", "mastermind"),
            Seat("Inez", "innkeeper"),
            Seat("Jasper", "moonchild"),
            Seat("Kendra", "exorcist"),
            Seat("Leo", "fool"),
            Seat("Mina", "gambler"),
            Seat("Nate", "gossip"),
            Seat("Olive", "professor"),
        )

        val n1 = Night(
            1,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Eyes closed; 9/2/3/1 bag verified."),
                action(NightMarkers.MINION_INFO, "Cora, Elena, Hector", "All minions saw one another and Aurora."),
                action("lunatic", "Blake", "Blake was falsely shown Cora, Elena, Hector as minions and Chef/Empath/Mayor as bluffs.", targets = listOf("Cora", "Elena", "Hector")),
                action(NightMarkers.DEMON_INFO, "Aurora", "Aurora saw the true minions, learned Blake was the Lunatic, and received Tea Lady/Sailor/Pacifist bluffs.", targets = listOf("Blake", "Cora", "Elena", "Hector")),
                action("courtier", "Felix", "Felix chose Assassin; Elena became drunk for three nights.", targets = listOf("Elena"), changes = listOf(add("Elena", "courtier", "Drunk 3"))),
                action("devilsadvocate", "Cora", "Cora protected Hector from tomorrow's execution.", targets = listOf("Hector"), changes = listOf(move("Hector", "devilsadvocate", "Survives Execution"))),
                action(
                    "grandmother",
                    "Greta",
                    "Greta learned Mina was her Gambler grandchild.",
                    targets = listOf("Mina"),
                    changes = listOf(add("Mina", "grandmother", "Grandchild")),
                    info = InfoCheck("grandmother", "Greta", listOf("Mina"), "Gambler", "Mina + Gambler"),
                ),
                action(
                    "chambermaid",
                    "Dorian",
                    "Dorian chose Cora and Leo and learned 1 woke due to an ability.",
                    targets = listOf("Cora", "Leo"),
                    info = InfoCheck("chambermaid", "Dorian", listOf("Cora", "Leo"), "1 of the 2", "1", "own-ability wakes only"),
                ),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: no deaths."),
            ),
            emptySet(),
        )
        val d1 = Day(
            1,
            listOf(
                nomination("Blake", "Cora", listOf("Blake", "Aurora", "Dorian", "Felix", "Greta", "Inez", "Jasper"), NominationResult.SAFE, "7 votes; threshold was 8."),
                nomination("Cora", "Jasper", listOf("Cora", "Aurora", "Elena", "Hector", "Inez", "Kendra", "Leo", "Nate"), NominationResult.ABOUT_TO_DIE, "8 votes put Jasper on the block."),
                nomination("Mina", "Elena", listOf("Mina", "Blake", "Dorian", "Felix", "Greta", "Inez", "Olive", "Nate"), NominationResult.TIED, "8 tied and cleared the block."),
                nomination("Greta", "Mina", listOf("Greta", "Dorian", "Felix", "Inez", "Jasper", "Kendra", "Leo", "Nate", "Olive"), NominationResult.ABOUT_TO_DIE, "9 votes put Mina on the block."),
            ),
            execution = "Mina",
            executionRecord = "Mina the Gambler was executed; Professor had a legal resurrection target.",
            deadAfterDay = setOf("Mina"),
        )

        val n2 = Night(
            2,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 2 began with Mina dead."),
                action("courtier", "Felix", "Assassin drunkenness ticked from 3 to 2.", changes = listOf(remove("Elena", "courtier", "Drunk 3"), add("Elena", "courtier", "Drunk 2"))),
                action("innkeeper", "Inez", "Inez protected Greta and Olive; Greta was made drunk.", targets = listOf("Greta", "Olive"), changes = listOf(add("Greta", "innkeeper", "Safe"), add("Olive", "innkeeper", "Safe"), add("Greta", "innkeeper", "Drunk"))),
                action("gambler", "Mina", "Mina was dead at her row; skipped."),
                action("devilsadvocate", "Cora", "Cora moved protection to Aurora.", targets = listOf("Aurora"), changes = listOf(move("Aurora", "devilsadvocate", "Survives Execution"))),
                action("lunatic", "Blake", "Blake, believing Po, chose nobody and believed he charged; no Chosen token was placed."),
                action("exorcist", "Kendra", "Kendra chose Hector, not the Demon.", targets = listOf("Hector"), changes = listOf(move("Hector", "exorcist", "Chosen"))),
                action("po", "Aurora", "Aurora chose nobody and charged for three attacks.", changes = listOf(add("Aurora", "po", "3 Attacks"))),
                action("assassin", "Elena", "Elena was Courtier-drunk; she declined to use Assassin."),
                action("gossip", "Nate", "Nate's true day-1 gossip killed Dorian.", targets = listOf("Dorian"), changes = listOf(kill("Dorian", DeathCause.OTHER_NIGHT_DEATH))),
                action("professor", "Olive", "Olive revived Townsfolk Mina and spent the Professor ability.", targets = listOf("Mina"), changes = listOf(revive("Mina"), add("Olive", "professor", "No Ability"))),
                action("moonchild", "Jasper", "Jasper was alive; skipped."),
                action("grandmother", "Greta", "Grandchild Mina was not Demon-killed; skipped."),
                action("chambermaid", "Dorian", "Dorian died earlier tonight; skipped."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Dorian's death; Mina returned alive."),
            ),
            setOf("Dorian"),
        )
        val d2 = Day(
            2,
            listOf(
                nomination("Olive", "Jasper", listOf("Olive", "Aurora", "Blake", "Felix", "Greta", "Inez", "Kendra"), NominationResult.ABOUT_TO_DIE, "7 votes met threshold."),
                nomination("Aurora", "Cora", listOf("Aurora", "Elena", "Hector", "Inez", "Leo", "Mina"), NominationResult.SAFE, "6 votes was below threshold."),
                nomination("Hector", "Kendra", listOf("Hector", "Blake", "Felix", "Greta", "Nate", "Olive"), NominationResult.SAFE, "6 votes was below threshold."),
            ),
            execution = "Jasper",
            executionRecord = "Jasper the Moonchild was executed and publicly selected good Fool Leo.",
            postExecutionChanges = listOf(add("Leo", "moonchild", "Dead")),
            deadAfterDay = setOf("Dorian", "Jasper"),
        )

        val n3 = Night(
            3,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 3 began; Po had a charge and Moonchild had selected Leo."),
                action("courtier", "Felix", "Assassin drunkenness ticked from 2 to 1.", changes = listOf(remove("Elena", "courtier", "Drunk 2"), add("Elena", "courtier", "Drunk 1"))),
                action("innkeeper", "Inez", "Old protection cleared; Kendra and Olive became protected, Olive drunk.", targets = listOf("Kendra", "Olive"), changes = listOf(remove("Greta", "innkeeper", "Safe"), remove("Olive", "innkeeper", "Safe"), remove("Greta", "innkeeper", "Drunk"), add("Kendra", "innkeeper", "Safe"), add("Olive", "innkeeper", "Safe"), add("Olive", "innkeeper", "Drunk"))),
                action("gambler", "Mina", "Mina guessed Greta as Grandmother correctly and survived.", targets = listOf("Greta")),
                action("devilsadvocate", "Cora", "Cora protected Aurora again for the coming day.", targets = listOf("Aurora"), changes = listOf(move("Aurora", "devilsadvocate", "Survives Execution"))),
                // Official Lunatic reminders are "Chosen" x3 — one per player the
                // Lunatic picked, swept at dawn like any other night marker.
                action("lunatic", "Blake", "Blake spent his supposed Po charge on Cora, Greta and Nate.", targets = listOf("Cora", "Greta", "Nate"), changes = listOf(add("Cora", "lunatic", "Chosen"), add("Greta", "lunatic", "Chosen"), add("Nate", "lunatic", "Chosen"))),
                action("exorcist", "Kendra", "Kendra chose Cora, not the Demon.", targets = listOf("Cora"), changes = listOf(move("Cora", "exorcist", "Chosen"))),
                action(
                    "po",
                    "Aurora",
                    "Aurora attacked Greta, Nate and protected Kendra; Greta and Nate died, Kendra did not.",
                    targets = listOf("Greta", "Nate", "Kendra"),
                    changes = listOf(remove("Aurora", "po", "3 Attacks"), kill("Greta", DeathCause.DEMON), kill("Nate", DeathCause.DEMON)),
                    warningTarget = "Kendra",
                    warningContains = "Safe",
                ),
                action("assassin", "Elena", "Elena remained Courtier-drunk and declined to use Assassin."),
                action("gossip", "Nate", "Nate died earlier tonight; skipped."),
                action("professor", "Olive", "Professor ability was already spent; skipped."),
                action(
                    "moonchild",
                    "Jasper",
                    "Moonchild target Leo was good, but Fool's first-death protection prevented the death and was spent.",
                    targets = listOf("Leo"),
                    changes = listOf(add("Leo", "fool", "No Ability")),
                    warningTarget = "Leo",
                    warningContains = "Fool",
                ),
                action("grandmother", "Greta", "Greta died to the Demon, but her grandchild Mina did not; no extra death."),
                action("chambermaid", "Dorian", "Dorian remained dead; skipped."),
                action(
                    NightMarkers.DAWN,
                    "Storyteller",
                    "Dawn: announced Greta and Nate dead; Kendra and Leo survived; the Lunatic's Chosen tokens were swept.",
                    changes = listOf(
                        remove("Cora", "lunatic", "Chosen"),
                        remove("Greta", "lunatic", "Chosen"),
                        remove("Nate", "lunatic", "Chosen"),
                    ),
                ),
            ),
            setOf("Dorian", "Jasper", "Greta", "Nate"),
        )
        val d3 = Day(
            3,
            listOf(
                nomination("Kendra", "Cora", listOf("Kendra", "Aurora", "Blake", "Felix", "Inez", "Mina"), NominationResult.ABOUT_TO_DIE, "6 votes met threshold."),
                nomination("Elena", "Olive", listOf("Elena", "Hector", "Inez", "Leo", "Mina"), NominationResult.SAFE, "5 votes was below threshold."),
                nomination("Hector", "Aurora", listOf("Hector", "Blake", "Felix", "Leo", "Olive"), NominationResult.SAFE, "5 votes was below threshold."),
            ),
            execution = "Cora",
            executionRecord = "Cora the Devil's Advocate was executed.",
            deadAfterDay = setOf("Cora", "Dorian", "Jasper", "Greta", "Nate"),
        )

        val n4 = Night(
            4,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Night 4 began with ten alive; dead Devil's Advocate protection expired.", changes = listOf(remove("Aurora", "devilsadvocate", "Survives Execution"))),
                action("courtier", "Felix", "Courtier's third night expired; Assassin became sober.", changes = listOf(remove("Elena", "courtier", "Drunk 1"), add("Felix", "courtier", "No Ability"))),
                action("innkeeper", "Inez", "Inez moved protection to Blake and Leo, making Blake drunk.", targets = listOf("Blake", "Leo"), changes = listOf(remove("Kendra", "innkeeper", "Safe"), remove("Olive", "innkeeper", "Safe"), remove("Olive", "innkeeper", "Drunk"), add("Blake", "innkeeper", "Safe"), add("Leo", "innkeeper", "Safe"), add("Blake", "innkeeper", "Drunk"))),
                action("gambler", "Mina", "Mina guessed Inez as Innkeeper correctly and survived.", targets = listOf("Inez")),
                action("devilsadvocate", "Cora", "Cora was dead; skipped."),
                action("lunatic", "Blake", "Drunk Blake selected Olive as a supposed Po target.", targets = listOf("Olive")),
                action("exorcist", "Kendra", "Kendra selected Aurora; the Po was marked Chosen and silenced.", targets = listOf("Aurora"), changes = listOf(move("Aurora", "exorcist", "Chosen"))),
                action("po", "Aurora", "Exorcist chose Aurora; the engine annotated the Po row and Aurora did not act."),
                action("assassin", "Elena", "Now sober, Elena spent Assassin to kill Professor Olive.", targets = listOf("Olive"), changes = listOf(kill("Olive", DeathCause.OTHER_NIGHT_DEATH), add("Elena", "assassin", "No Ability"))),
                action("gossip", "Nate", "Nate was dead; skipped."),
                action("professor", "Olive", "Olive died earlier tonight and had spent the ability; skipped."),
                action("moonchild", "Jasper", "Moonchild ability already resolved; skipped."),
                action("grandmother", "Greta", "Greta was dead; skipped."),
                action("chambermaid", "Dorian", "Dorian was dead; skipped."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: announced Olive's death."),
            ),
            setOf("Cora", "Dorian", "Jasper", "Greta", "Nate", "Olive"),
        )
        val d4 = Day(
            4,
            listOf(
                nomination("Mina", "Aurora", listOf("Mina", "Blake", "Felix", "Inez", "Kendra"), NominationResult.ABOUT_TO_DIE, "5 votes met threshold."),
                nomination("Aurora", "Mina", listOf("Aurora", "Elena", "Hector", "Leo"), NominationResult.SAFE, "4 votes was below threshold."),
                nomination("Hector", "Elena", listOf("Hector", "Blake", "Felix", "Inez"), NominationResult.SAFE, "4 votes was below threshold."),
            ),
            execution = "Aurora",
            executionRecord = "Aurora the Po was executed. Mastermind Hector was alive, so play continued for one final day.",
            deadAfterDay = setOf("Aurora", "Cora", "Dorian", "Jasper", "Greta", "Nate", "Olive"),
        )

        val n5 = Night(
            5,
            listOf(
                action(NightMarkers.DUSK, "Storyteller", "Mastermind continuation night began; engine still displayed a good-win advisory with a caution."),
                action("courtier", "Felix", "Courtier spent; skipped."),
                action("innkeeper", "Inez", "Inez protected Mina and Kendra, making Kendra drunk.", targets = listOf("Mina", "Kendra"), changes = listOf(remove("Blake", "innkeeper", "Safe"), remove("Leo", "innkeeper", "Safe"), remove("Blake", "innkeeper", "Drunk"), add("Mina", "innkeeper", "Safe"), add("Kendra", "innkeeper", "Safe"), add("Kendra", "innkeeper", "Drunk"))),
                action("gambler", "Mina", "Mina guessed Hector as Mastermind correctly and survived.", targets = listOf("Hector")),
                action("devilsadvocate", "Cora", "Cora was dead; skipped."),
                action("lunatic", "Blake", "Blake selected Elena as a supposed Po target; there was no living Demon to receive it.", targets = listOf("Elena")),
                action("exorcist", "Kendra", "Drunk Kendra selected Blake; no effect.", targets = listOf("Blake"), changes = listOf(move("Blake", "exorcist", "Chosen"))),
                action("po", "Aurora", "Aurora was dead; skipped."),
                action("assassin", "Elena", "Assassin ability spent; skipped."),
                action("gossip", "Nate", "Nate was dead; skipped."),
                action("professor", "Olive", "Olive was dead; skipped."),
                action("moonchild", "Jasper", "Jasper was dead and resolved; skipped."),
                action("grandmother", "Greta", "Greta was dead; skipped."),
                action("chambermaid", "Dorian", "Dorian was dead; skipped."),
                action(NightMarkers.DAWN, "Storyteller", "Dawn: no deaths; final Mastermind day opened."),
            ),
            setOf("Aurora", "Cora", "Dorian", "Jasper", "Greta", "Nate", "Olive"),
        )
        val d5 = Day(
            5,
            listOf(
                nomination("Hector", "Mina", listOf("Hector", "Blake", "Elena", "Felix"), NominationResult.ABOUT_TO_DIE, "4 votes met threshold."),
                nomination("Mina", "Hector", listOf("Mina", "Inez", "Kendra"), NominationResult.SAFE, "3 votes was below threshold."),
            ),
            execution = "Mina",
            executionRecord = "Mina was executed on the Mastermind day. Her good team lost, so evil won despite the Demon already being dead.",
            deadAfterDay = setOf("Aurora", "Cora", "Dorian", "Jasper", "Greta", "Mina", "Nate", "Olive"),
        )

        return Scenario(
            scriptId = "bmr",
            title = "Bad Moon Rising — The Mastermind's extra day",
            seats = seats,
            bluffs = listOf("tealady", "sailor", "pacifist"),
            nights = listOf(n1, n2, n3, n4, n5),
            days = listOf(d1, d2, d3, d4, d5),
            declaredGoodWin = false,
            outcome = "Evil wins on day 5 when the good Gambler is executed during the Mastermind continuation.",
            engineAdvisoryGoodWin = true,
            engineCautionContains = "Mastermind",
        )
    }
}
