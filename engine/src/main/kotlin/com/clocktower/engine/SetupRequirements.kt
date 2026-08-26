package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What kind of answer a [SetupRequirement] wants. */
@Serializable
enum class RequirementKind {
    /** Pick a character token this player believes. */
    SHOWN_TOKEN,

    /** Place a token on some seat. */
    REMINDER,

    /** Set or flip a seat's alignment. */
    ALIGNMENT,

    /** Pick an ability the seat holds, or write a secret. */
    GRANT,

    /** Store an integer choice (Xaan's X, an Outsider branch). */
    NUMBER,

    /** Pick a partner seat (Evil Twin). */
    PAIR,

    /** A [BluffRequirement]. */
    BLUFFS,

    /** An adjacency / line constraint. */
    SEATING,

    /** "Show every Minion the Damsel token". */
    INFORM,

    /** Acknowledge a bag rule (Kazali's 0 Minions, Lil' Monsta's 0 Demons). */
    ACK,
}

/** One candidate answer offered for a [SetupRequirement]. */
data class Candidate(
    val id: String,
    val label: String,
    val playerId: Long? = null,
    val badge: String = "",
    val enabled: Boolean = true,
)

/** The storyteller's answer to a [SetupRequirement]. */
data class Selection(
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val number: Int? = null,
    val text: String = "",
)

/**
 * One row of the "Before the first night" checklist AND one clause of setup
 * validation. Ids are canonical (lead D48): "drunk.token", "lunatic.token",
 * "lunatic.minions", "lunatic.bluffs", "marionette.token", "marionette.seat",
 * "fortuneteller.herring", "puzzlemaster.drunk", "villageidiot.drunk", "pixie.mad",
 * "widow.know", "grandmother.grandchild", "balloonist.know", "eviltwin.twin",
 * "bountyhunter.evil", "snitch.bluffs:<seat>", "demon.bluffs", "summoner.bluffs",
 * "boffin.grant", "alchemist.grant", "xaan.X", "damsel.minions", "mezepheles.word",
 * "traveller.alignment:<seat>", "kazali.noMinions", "lilmonsta.noDemonSeat",
 * "setup.outsiderBranch".
 */
data class SetupRequirement(
    val id: String,
    val characterId: String,
    val kind: RequirementKind,
    /** Short checklist label. */
    val title: String,
    /** Storyteller-voice imperative for the prompt. */
    val prompt: String,
    /** Message when unmet; "" for advisory-only rows. */
    val problem: String = "",
    /** Blocks "Begin night" (with the existing "start anyway" escape). */
    val blocking: Boolean = true,
    val candidates: (GameState, (String) -> Character?) -> List<Candidate> = { _, _ -> emptyList() },
    val apply: (GameState, Selection) -> GameState = { s, _ -> s },
    val satisfied: (GameState, (String) -> Character?) -> Boolean,
)

/**
 * The data-driven setup checklist (WP4). Replaces `Setup.validateSetupState`.
 *
 * Every row is re-checkable AT ANY TIME, not only during SETUP: a Pit-Hag-created
 * Fortune Teller needs a red herring, a Kazali-created Widow needs a Know token,
 * a mid-game Snitch owes every Minion bluffs.
 */
object SetupRequirements {

    /** Storyteller ack that Lil' Monsta is in play as a centre token, on no seat. */
    const val LILMONSTA_NO_DEMON_SEAT = "lilmonsta.noDemonSeat"

    /** Storyteller ack that the Kazali's bag legally holds no Minion tokens. */
    const val KAZALI_NO_MINIONS = "kazali.noMinions"

    /** Storyteller ack that every Minion has been shown the Damsel token. */
    const val DAMSEL_MINIONS = "damsel.minions"

    /** Every requirement this game raises RIGHT NOW — re-checkable mid-game, not only at SETUP. */
    fun all(state: GameState, lookup: (String) -> Character?): List<SetupRequirement> {
        val rows = mutableListOf<SetupRequirement>()
        rows += bagRows(state, lookup)
        rows += seatRows(state, lookup)
        rows += bluffRows(state, lookup)
        rows += choiceRows(state, lookup)
        return rows
    }

    fun unmet(state: GameState, lookup: (String) -> Character?): List<SetupRequirement> =
        all(state, lookup).filterNot { it.satisfied(state, lookup) }

    /** Replaces `GameActions.validateSetupState`. */
    fun blockingProblems(state: GameState, lookup: (String) -> Character?): List<String> =
        unmet(state, lookup).filter { it.blocking }.map { it.problem }.filter { it.isNotBlank() }
            .distinct()

    // ---- the bag ------------------------------------------------------------

    /** Each bag problem becomes one unsatisfiable ACK row, so the checklist shows it. */
    private fun bagRows(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<SetupRequirement> {
        val residents = state.seats.filterNot { it.isTraveller }
        if (residents.isEmpty()) return emptyList()
        val characters = residents.mapNotNull { it.characterId?.let(lookup) }
        val issues = Setup.validateBag(
            bag = characters,
            playerCount = residents.size,
            fabledIds = state.fabledIds,
            inPlayIds = Setup.seatlessInPlayIds(state),
            state = state,
        )
        return issues.mapIndexed { index, issue ->
            SetupRequirement(
                id = "bag.$index",
                characterId = "",
                kind = RequirementKind.ACK,
                title = "The bag is not legal yet",
                prompt = issue,
                problem = issue,
                blocking = true,
                satisfied = { _, _ -> false },
            )
        }
    }

    // ---- per-seat rows ------------------------------------------------------

    private fun seatRows(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<SetupRequirement> {
        val rows = mutableListOf<SetupRequirement>()
        val inPlay = inPlayIds(state)
        val residents = state.seats.filterNot { it.isTraveller }

        for (seat in residents) {
            when (seat.characterId?.let(Character::normalizeId)) {
                "drunk" -> rows += shownTokenRow(
                    id = "drunk.token",
                    characterId = "drunk",
                    seat = seat,
                    title = "The Drunk believes",
                    prompt = "${seat.name} is the Drunk. Which Townsfolk token do they see?",
                    problem = "${seat.name}: choose a not-in-play Townsfolk token to show the Drunk",
                    accepts = { c -> c.team == Team.TOWNSFOLK && c.id !in inPlay },
                )

                "lunatic" -> {
                    rows += shownTokenRow(
                        id = "lunatic.token",
                        characterId = "lunatic",
                        seat = seat,
                        title = "The Lunatic believes",
                        prompt = "Which Demon token does the Lunatic see?",
                        problem = "${seat.name}: choose the Demon token shown to the Lunatic",
                        accepts = { c -> c.team == Team.DEMON },
                    )
                    rows += lunaticMinionsRow(seat)
                }

                "marionette" -> {
                    rows += shownTokenRow(
                        id = "marionette.token",
                        characterId = "marionette",
                        seat = seat,
                        title = "The Marionette believes",
                        prompt = "Which not-in-play good token does the Marionette see?",
                        problem = "${seat.name}: choose a not-in-play good token to show the Marionette",
                        accepts = { c ->
                            !c.team.isEvil && c.team.isTownResident && c.id !in inPlay
                        },
                    )
                    rows += SetupRequirement(
                        id = "marionette.seat",
                        characterId = "marionette",
                        kind = RequirementKind.SEATING,
                        title = "The Marionette's seat",
                        prompt = "Seat ${seat.name} next to the Demon.",
                        problem = "${seat.name}: the Marionette must neighbor the Demon",
                        satisfied = { s, l ->
                            s.player(seat.id)?.let { marionetteNeighbourOk(s, l, it) } ?: true
                        },
                    )
                }

                "fortuneteller" -> rows += exclusiveTokenRow(
                    id = "fortuneteller.herring",
                    characterId = "fortuneteller",
                    label = "Red Herring",
                    title = "Fortune Teller red herring",
                    prompt = "Choose the good player who registers as the Demon.",
                    problem = "Fortune Teller: choose exactly one good red herring",
                    candidateFilter = { s, l -> s.seats.filterNot { it.isEvil(l) } },
                    holderOk = { _, l, holder -> !holder.isEvil(l) },
                )

                "puzzlemaster" -> rows += exclusiveTokenRow(
                    id = "puzzlemaster.drunk",
                    characterId = "puzzlemaster",
                    label = "Drunk",
                    title = "Puzzlemaster's drunk",
                    prompt = "Choose the player the Puzzlemaster makes drunk.",
                    problem = "Puzzlemaster: choose exactly one player to be drunk",
                )

                "pixie" -> rows += exclusiveTokenRow(
                    id = "pixie.mad",
                    characterId = "pixie",
                    label = "Mad",
                    title = "Pixie's madness",
                    prompt = "Mark the in-play Townsfolk the Pixie is mad about.",
                    problem = "Pixie: mark the in-play Townsfolk they are mad about",
                    candidateFilter = { s, l ->
                        s.seats.filter { it.characterId?.let(l)?.team == Team.TOWNSFOLK }
                    },
                )

                "widow" -> rows += exclusiveTokenRow(
                    id = "widow.know",
                    characterId = "widow",
                    label = "Know",
                    title = "Who knows about the Widow",
                    prompt = "Mark the good player who knows a Widow is in play.",
                    problem = "Widow: mark the good player who knows a Widow is in play",
                    candidateFilter = { s, l -> s.seats.filterNot { it.isEvil(l) } },
                )

                "grandmother" -> rows += exclusiveTokenRow(
                    id = "grandmother.grandchild",
                    characterId = "grandmother",
                    label = "Grandchild",
                    title = "The Grandchild",
                    prompt = "Mark the good player the Grandmother knows.",
                    problem = "Grandmother: mark the Grandchild",
                    candidateFilter = { s, l -> s.seats.filterNot { it.isEvil(l) } },
                )

                "balloonist" -> rows += exclusiveTokenRow(
                    id = "balloonist.know",
                    characterId = "balloonist",
                    label = "Know",
                    title = "Balloonist's first player",
                    prompt = "Mark the first player the Balloonist learns.",
                    problem = "Balloonist: mark the first player they learn",
                )

                "eviltwin" -> rows += SetupRequirement(
                    id = "eviltwin.twin",
                    characterId = "eviltwin",
                    kind = RequirementKind.PAIR,
                    title = "The good twin",
                    prompt = "Choose ${seat.name}'s good twin.",
                    problem = "Evil Twin: choose exactly one good twin",
                    candidates = { s, l ->
                        s.seats.filter { !it.isEvil(l) && !it.isTraveller && it.id != seat.id }
                            .map { Candidate(it.id.toString(), it.name, playerId = it.id) }
                    },
                    apply = { s, sel ->
                        sel.playerIds.firstOrNull()?.let {
                            Effects.placeExclusiveReminder(s, it, PlacedReminder("eviltwin", "Twin"))
                        } ?: s
                    },
                    satisfied = { s, l ->
                        val holders = seatsHolding(s, "eviltwin", "Twin")
                        holders.size == 1 && !holders.single().isEvil(l)
                    },
                )

                "bountyhunter" -> {
                    rows += SetupRequirement(
                        id = "bountyhunter.evil",
                        characterId = "bountyhunter",
                        kind = RequirementKind.ALIGNMENT,
                        title = "The evil Townsfolk",
                        prompt = "Turn one Townsfolk evil, and mark an evil player Know.",
                        problem = "Bounty Hunter: turn one Townsfolk evil",
                        candidates = { s, l ->
                            s.seats.filter { it.characterId?.let(l)?.team == Team.TOWNSFOLK }
                                .map { Candidate(it.id.toString(), it.name, playerId = it.id) }
                        },
                        apply = { s, sel ->
                            sel.playerIds.firstOrNull()
                                ?.let { Seats.setAlignment(s, it, Alignment.EVIL) } ?: s
                        },
                        satisfied = { s, l ->
                            s.seats.count {
                                it.characterId?.let(l)?.team == Team.TOWNSFOLK && it.isEvil(l)
                            } == 1
                        },
                    )
                    rows += exclusiveTokenRow(
                        id = "bountyhunter.know",
                        characterId = "bountyhunter",
                        label = "Know",
                        title = "The evil player they know",
                        prompt = "Mark the evil player the Bounty Hunter starts knowing.",
                        problem = "Bounty Hunter: mark the evil player they start knowing",
                        candidateFilter = { s, l -> s.seats.filter { it.isEvil(l) } },
                        holderOk = { _, l, holder -> holder.isEvil(l) },
                    )
                }

                "boffin" -> rows += decisionRow(
                    id = "boffin.grant",
                    characterId = "boffin",
                    key = Decisions.BOFFIN_GRANT,
                    kind = RequirementKind.GRANT,
                    title = "The Boffin's gift",
                    prompt = "Choose the not-in-play good ability the Demon also has.",
                    problem = "Boffin: choose the good ability the Demon has",
                    candidates = { s, l ->
                        scriptCharacters(s, l)
                            .filter { !it.team.isEvil && it.team.isTownResident && it.id !in inPlayIds(s) }
                            .map { Candidate(it.id, it.name) }
                    },
                )

                "alchemist" -> rows += decisionRow(
                    id = "alchemist.grant",
                    characterId = "alchemist",
                    key = Decisions.ALCHEMIST_GRANT,
                    kind = RequirementKind.GRANT,
                    title = "The Alchemist's Minion ability",
                    prompt = "Choose the Minion ability the Alchemist has.",
                    problem = "Alchemist: choose the Minion ability they have",
                    candidates = { s, l ->
                        scriptCharacters(s, l)
                            .filter { it.team == Team.MINION }
                            .map { Candidate(it.id, it.name) }
                    },
                )

                "mezepheles" -> rows += decisionRow(
                    id = "mezepheles.word",
                    characterId = "mezepheles",
                    key = Decisions.MEZEPHELES_WORD,
                    kind = RequirementKind.GRANT,
                    title = "The Mezepheles' secret word",
                    prompt = "Write the secret word, then show it to the Mezepheles.",
                    problem = "Mezepheles: write the secret word",
                )

                "xaan" -> rows += SetupRequirement(
                    id = "xaan.X",
                    characterId = "xaan",
                    kind = RequirementKind.NUMBER,
                    title = "Xaan's X",
                    prompt = "Choose X — the Outsider count, and the night the Xaan poisons.",
                    problem = "Xaan: choose X",
                    candidates = { s, _ ->
                        (0..s.seats.count { !it.isTraveller }).map {
                            Candidate(it.toString(), "$it Outsiders")
                        }
                    },
                    apply = { s, sel ->
                        sel.number?.let { Decisions.set(s, Decisions.XAAN_X, it.toString()) } ?: s
                    },
                    satisfied = { s, _ -> Decisions.int(s, Decisions.XAAN_X) != null },
                )

                "damsel" -> rows += ackRow(
                    id = DAMSEL_MINIONS,
                    characterId = "damsel",
                    kind = RequirementKind.INFORM,
                    title = "Show the Damsel token",
                    prompt = "Show every Minion the Damsel token.",
                    problem = "Damsel: show every Minion the Damsel token",
                )

                "villageidiot" -> Unit // handled once, below
                else -> Unit
            }
        }

        // Exactly one of two-or-more Village Idiots is drunk; a lone one is sober.
        val villageIdiots = residents.count { it.characterId == "villageidiot" }
        if (villageIdiots >= 2) {
            rows += exclusiveTokenRow(
                id = "villageidiot.drunk",
                characterId = "villageidiot",
                label = "Drunk",
                title = "The drunk Village Idiot",
                prompt = "Mark one Village Idiot Drunk.",
                problem = "Village Idiot: mark exactly one of them Drunk",
                candidateFilter = { s, _ -> s.seats.filter { it.characterId == "villageidiot" } },
            )
        }

        // Travellers are good or evil by choice — always asked, on arrival too.
        for (traveller in state.seats.filter { it.isTraveller }) {
            rows += SetupRequirement(
                id = "traveller.alignment:${traveller.id}",
                characterId = traveller.characterId.orEmpty(),
                kind = RequirementKind.ALIGNMENT,
                title = "${traveller.name}'s alignment",
                prompt = "Is ${traveller.name} good or evil?",
                problem = "${traveller.name}: set the Traveller's alignment",
                candidates = { _, _ ->
                    listOf(Candidate("good", "Good"), Candidate("evil", "Evil"))
                },
                apply = { s, sel ->
                    Seats.setAlignment(
                        s,
                        traveller.id,
                        if (sel.text == "evil") Alignment.EVIL else Alignment.GOOD,
                    )
                },
                satisfied = { s, _ -> s.player(traveller.id)?.alignment != null },
            )
        }
        return rows
    }

    // ---- bluffs -------------------------------------------------------------

    private fun bluffRows(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<SetupRequirement> = Bluffs.requirements(state, lookup).map { req ->
        SetupRequirement(
            id = when (req.sourceId) {
                "snitch" -> "snitch.bluffs:${req.recipientId}"
                "summoner" -> "summoner.bluffs"
                "lunatic" -> "lunatic.bluffs"
                else -> "demon.bluffs"
            },
            characterId = req.sourceId,
            kind = RequirementKind.BLUFFS,
            title = req.label,
            prompt = "Choose ${req.size} bluffs. ${req.reason}".trim(),
            problem = "${req.label}: choose ${req.size} bluffs",
            blocking = req.required,
            candidates = { s, l ->
                Bluffs.candidates(s, scriptCharacters(s, l), req).map {
                    Candidate(
                        id = it.character.id,
                        label = it.character.name,
                        badge = it.inUseBy ?: if (it.inPlay) "in play" else "",
                    )
                }
            },
            apply = { s, sel -> Bluffs.set(s, req.key, sel.characterIds.take(req.size)) },
            satisfied = { s, _ -> s.bluffSets[req.key].orEmpty().size >= req.size },
        )
    }

    // ---- whole-game choices -------------------------------------------------

    private fun choiceRows(
        state: GameState,
        lookup: (String) -> Character?,
    ): List<SetupRequirement> {
        val rows = mutableListOf<SetupRequirement>()
        val residents = state.seats.filterNot { it.isTraveller }
        val inPlay = inPlayIds(state)

        // "You choose which players are which Minions" — a 0-Minion bag is legal.
        if ("kazali" in inPlay || "lordoftyphon" in inPlay) {
            rows += ackRow(
                id = KAZALI_NO_MINIONS,
                characterId = if ("kazali" in inPlay) "kazali" else "lordoftyphon",
                kind = RequirementKind.ACK,
                title = "No Minions in the bag",
                prompt = "The Minions are created on the first night. Confirm the bag holds none.",
                problem = "Confirm the bag legally holds no Minion tokens",
                satisfied = { s, l ->
                    Decisions.bool(s, KAZALI_NO_MINIONS) ||
                        s.seats.none { it.characterId?.let(l)?.team == Team.MINION }
                },
            )
        }

        // Lil' Monsta is a centre token, not a seat: the bag has no Demon.
        val scriptHasLilMonsta = state.script.characterIds.any {
            Character.normalizeId(it) == "lilmonsta"
        }
        val demonSeats = residents.count { it.characterId?.let(lookup)?.team == Team.DEMON }
        if (scriptHasLilMonsta && demonSeats == 0 && residents.isNotEmpty()) {
            rows += ackRow(
                id = LILMONSTA_NO_DEMON_SEAT,
                characterId = "lilmonsta",
                kind = RequirementKind.ACK,
                title = "Lil' Monsta is in play",
                prompt = "Lil' Monsta is a token, not a seat: one extra Minion, no Demon in the bag.",
                problem = "Confirm Lil' Monsta is in play as a centre token (no Demon seat)",
            )
        }

        // Record which branch of a choice bracket is being played.
        val choiceBrackets = residents
            .mapNotNull { it.characterId?.let(lookup) }
            .mapNotNull(Setup::modifierFor)
            .filter { it.choice }
        if (choiceBrackets.isNotEmpty()) {
            rows += SetupRequirement(
                id = "setup.outsiderBranch",
                characterId = choiceBrackets.first().characterId,
                kind = RequirementKind.NUMBER,
                title = "Which setup branch you chose",
                prompt = "Record the Outsider count you chose: " +
                    choiceBrackets.joinToString(", ") { "${it.characterId} [${it.text}]" },
                problem = "",
                blocking = false,
                apply = { s, sel ->
                    sel.number?.let {
                        Decisions.set(s, Decisions.OUTSIDER_BRANCH, it.toString())
                    } ?: s
                },
                satisfied = { s, _ -> Decisions.int(s, Decisions.OUTSIDER_BRANCH) != null },
            )
        }
        return rows
    }

    // ---- predicates ---------------------------------------------------------

    /**
     * "[You neighbor the Demon]" — with the jinxes the wiki actually publishes:
     * a Summoner game seats them next to the Summoner, a Lil' Monsta game next
     * to any Minion, and a Kazali game creates them on the first night.
     */
    fun marionetteNeighbourOk(
        state: GameState,
        lookup: (String) -> Character?,
        seat: Player,
    ): Boolean {
        val neighbours = state.seatNeighbours(seat.id)
        if (neighbours.isEmpty()) return true
        val inPlay = inPlayIds(state)
        return when {
            "summoner" in inPlay -> neighbours.any { it.characterId == "summoner" }
            "lilmonsta" in inPlay || Decisions.bool(state, LILMONSTA_NO_DEMON_SEAT) ->
                neighbours.any { it.characterId?.let(lookup)?.team == Team.MINION }
            "kazali" in inPlay -> true
            state.seats.none { it.characterId?.let(lookup)?.team == Team.DEMON } -> true
            else -> neighbours.any { it.characterId?.let(lookup)?.team == Team.DEMON }
        }
    }

    /**
     * "[Evil characters are in a line. You are in the middle.]" — checked after
     * the Lord of Typhon's first-night conversions, never before them.
     */
    fun lordOfTyphonLineOk(state: GameState, lookup: (String) -> Character?): Boolean {
        val seats = state.seats
        val typhon = seats.indexOfFirst { it.characterId == "lordoftyphon" }
        if (typhon < 0 || seats.size < 3) return true
        val evil = seats.indices.filter { seats[it].isEvil(lookup) }
        if (evil.size <= 1) return true
        // Walk out from the Typhon in both directions; every evil seat must be
        // contiguous with it, and the two halves must be equal (the middle).
        var before = 0
        while (before < seats.size && seats[(typhon - before - 1 + seats.size) % seats.size].isEvil(lookup)) {
            before++
        }
        var after = 0
        while (after < seats.size && seats[(typhon + after + 1) % seats.size].isEvil(lookup)) {
            after++
        }
        return before + after + 1 == evil.size && before == after
    }

    // ---- row builders -------------------------------------------------------

    private fun shownTokenRow(
        id: String,
        characterId: String,
        seat: Player,
        title: String,
        prompt: String,
        problem: String,
        accepts: (Character) -> Boolean,
    ): SetupRequirement = SetupRequirement(
        id = id,
        characterId = characterId,
        kind = RequirementKind.SHOWN_TOKEN,
        title = title,
        prompt = prompt,
        problem = problem,
        candidates = { s, l ->
            scriptCharacters(s, l).filter(accepts).map { Candidate(it.id, it.name) }
        },
        apply = { s, sel ->
            sel.characterIds.firstOrNull()?.let { Seats.setShownCharacter(s, seat.id, it) } ?: s
        },
        satisfied = { s, l ->
            val shown = s.player(seat.id)?.shownCharacterId?.let(l)
            shown != null && accepts(shown)
        },
    )

    private fun exclusiveTokenRow(
        id: String,
        characterId: String,
        label: String,
        title: String,
        prompt: String,
        problem: String,
        candidateFilter: (GameState, (String) -> Character?) -> List<Player> = { s, _ -> s.seats },
        /** Extra clause the single holder must also satisfy (good, evil, in play…). */
        holderOk: (GameState, (String) -> Character?, Player) -> Boolean = { _, _, _ -> true },
    ): SetupRequirement = SetupRequirement(
        id = id,
        characterId = characterId,
        kind = RequirementKind.REMINDER,
        title = title,
        prompt = prompt,
        problem = problem,
        candidates = { s, l ->
            candidateFilter(s, l).map { Candidate(it.id.toString(), it.name, playerId = it.id) }
        },
        apply = { s, sel ->
            sel.playerIds.firstOrNull()?.let {
                Effects.placeExclusiveReminder(s, it, PlacedReminder(characterId, label))
            } ?: s
        },
        satisfied = { s, l ->
            val holders = seatsHolding(s, characterId, label)
            holders.size == 1 && holderOk(s, l, holders.single())
        },
    )

    /**
     * The Lunatic is pointed at as many "Minions" as the game really has.
     * Advisory: `"Fake Minion"` is not an official token, so it never blocks.
     */
    private fun lunaticMinionsRow(seat: Player): SetupRequirement = SetupRequirement(
        id = "lunatic.minions",
        characterId = "lunatic",
        kind = RequirementKind.REMINDER,
        title = "The Lunatic's \"Minions\"",
        prompt = "Point out players as ${seat.name}'s Minions.",
        problem = "Lunatic: point out their fake Minions",
        blocking = false,
        candidates = { s, _ ->
            s.seats.filter { it.id != seat.id }
                .map { Candidate(it.id.toString(), it.name, playerId = it.id) }
        },
        apply = { s, sel ->
            sel.playerIds.fold(s) { acc, id ->
                Effects.addReminder(acc, id, PlacedReminder("lunatic", "Fake Minion"))
            }
        },
        satisfied = { s, l ->
            val real = s.seats.count { it.characterId?.let(l)?.team == Team.MINION }
            seatsHolding(s, "lunatic", "Fake Minion").size >= real
        },
    )

    private fun decisionRow(
        id: String,
        characterId: String,
        key: String,
        kind: RequirementKind,
        title: String,
        prompt: String,
        problem: String,
        candidates: (GameState, (String) -> Character?) -> List<Candidate> = { _, _ -> emptyList() },
    ): SetupRequirement = SetupRequirement(
        id = id,
        characterId = characterId,
        kind = kind,
        title = title,
        prompt = prompt,
        problem = problem,
        candidates = candidates,
        apply = { s, sel ->
            val value = sel.characterIds.firstOrNull() ?: sel.text
            if (value.isBlank()) s else Decisions.set(s, key, value)
        },
        satisfied = { s, _ -> !s.decisions[key].isNullOrBlank() },
    )

    private fun ackRow(
        id: String,
        characterId: String,
        kind: RequirementKind,
        title: String,
        prompt: String,
        problem: String,
        satisfied: (GameState, (String) -> Character?) -> Boolean = { s, _ -> Decisions.bool(s, id) },
    ): SetupRequirement = SetupRequirement(
        id = id,
        characterId = characterId,
        kind = kind,
        title = title,
        prompt = prompt,
        problem = problem,
        apply = { s, _ -> Decisions.set(s, id, "true") },
        satisfied = satisfied,
    )

    // ---- helpers ------------------------------------------------------------

    /** Seats carrying a `(sourceId, label)` token, compared case-insensitively. */
    fun seatsHolding(state: GameState, sourceId: String, label: String): List<Player> {
        val key = Tokens.key(sourceId, label)
        return state.seats.filter { seat -> seat.reminders.any { Tokens.key(it) == key } }
    }

    /** Normalised ids of every character actually held by a seat. */
    fun inPlayIds(state: GameState): Set<String> =
        state.seats.mapNotNull { it.characterId?.let(Character::normalizeId) }.toSet()

    /** The characters on this game's script, custom ones included. */
    fun scriptCharacters(state: GameState, lookup: (String) -> Character?): List<Character> {
        val custom = state.script.customCharacters.associateBy { it.id }
        return state.script.characterIds.mapNotNull { custom[it] ?: lookup(it) }
    }
}
