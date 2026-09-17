package com.clocktower.engine.rules

import com.clocktower.engine.*

/** Script-specific interactions. All mutations still use the shared effect/death/ledger funnels. */
object SaltAndLanternAutomation {
    private const val FERRY = SaltAndLantern.FERRYMAN_ID
    private const val CLOSED_NIGHT = "salt.harbour.closedNight"
    val infoTargets = mapOf("cartographer" to 0, "pearldiver" to 1, "navigator" to 2, "smuggler" to 1)

    fun active(script: Script): Boolean = script.isBuiltIn && script.id == SaltAndLantern.ID

    /** Old saved games embed the original manual-only script; they get the same rules as new games. */
    fun manualInstruction(script: Script, id: String): String? =
        if (active(script)) null else script.manualNightInstructions[id]

    fun informationSeats(state: GameState, lookup: (String) -> Character?, ability: String): List<Player> {
        if (!active(state.script) || ability !in setOf("chef", "empath", "cartographer", "navigator")) {
            return state.players
        }
        return state.seats.filterNot {
            it.characterId == "stowaway" && Status.hasAbility(state, lookup, it.id)
        }
    }

    fun diedAtNight(state: GameState, id: Long): Boolean =
        state.deaths.any { it.playerId == id && it.atNight && !it.registeredOnly }

    private fun ferryDeath(state: GameState, holder: Long?): DeathEvent? = state.deaths.lastOrNull {
        it.playerId == holder && !it.registeredOnly &&
            ((it.atNight && it.day == state.cycle) || (!it.atNight && it.day == state.cycle - if (state.phase == Phase.NIGHT) 1 else 0)) &&
            state.counters[crossingKey(it)] != 1
    }

    private fun crossingKey(death: DeathEvent) = "salt.crossing.${death.id}"

    private fun harbourSpent(state: GameState, lookup: (String) -> Character?, holder: Long) =
        Memory.isSpent(state, "harbourmaster", holder) || Status.live(state, lookup, holder, EffectKind.SPENT)
            .any { it.sourceCharacterId == "harbourmaster" }

    private fun dayChoice(state: GameState, death: DeathEvent): LedgerEntry? = state.ledger.lastOrNull {
        it.kind == LedgerKind.CHOICE && it.sourceId == FERRY && it.causeEventId == death.id && !it.atNight
    }

    fun ferryTargets(state: GameState, holder: Long): List<Long> {
        val death = ferryDeath(state, holder) ?: return emptyList()
        val atDeath = death.otherDeadIdsAtDeath
        return state.seats.filter { !it.alive && it.id != holder && (atDeath == null || it.id in atDeath) }.map { it.id }
    }

    /** Applies the Wrecker once, to the whole ordered choice. Never to a daytime Ferryman choice. */
    fun redirectTargets(
        state: GameState, lookup: (String) -> Character?, ability: String, holderId: Long?,
        targets: List<Long>, legal: (Long) -> Boolean = { true },
    ): List<Long> {
        if (!active(state.script) || state.phase != Phase.NIGHT) return targets
        val chooser = holderId?.let(state::player) ?: return targets
        if (chooser.isEvil(lookup)) return targets
        if (ability == FERRY && ferryDeath(state, holderId)?.atNight != true) return targets
        val wreckers = state.seats.filter { it.characterId == "wrecker" && Status.hasAbility(state, lookup, it.id) }
        // One pair per functioning source. Never compose a swap twice on the same submitted choice.
        val pair = wreckers.firstNotNullOfOrNull { wrecker ->
            state.seats.filter { target ->
                Status.live(state, lookup, target.id).any {
                    Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("wrecker", "Wrecked") &&
                        (it.sourcePlayerId == null || it.sourcePlayerId == wrecker.id)
                }
            }.map { it.id }.takeIf { it.size == 2 }
        } ?: return targets
        return targets.map { original ->
            if (ability == "pearldiver" && !diedAtNight(state, original)) return@map original
            if (original !in pair) return@map original
            val replacement = pair.first { it != original }
            val ferryException = ability == FERRY && replacement == holderId && !chooser.alive &&
                original != holderId && state.player(original)?.alive == false
            val scriptLegal = when (ability) {
                "lighthousekeeper" -> replacement != holderId
                "pearldiver" -> diedAtNight(state, replacement)
                FERRY -> state.player(replacement)?.alive == false && (replacement != holderId || ferryException)
                else -> true
            }
            if (scriptLegal && (legal(replacement) || ferryException)) replacement else original
        }
    }

    private fun registrationNotes(state: GameState, lookup: (String) -> Character?, players: List<Player>) =
        players.filter { it.characterId in setOf("recluse", "spy") ||
            Status.live(state, lookup, it.id, EffectKind.REGISTERS_AS).isNotEmpty()
        }.map { "${it.name}: check the registration ruling." }

    fun information(
        state: GameState, lookup: (String) -> Character?, id: String, holderId: Long?, targets: List<Long>,
    ): InfoResult? {
        if (!active(state.script) || id !in infoTargets) return null
        val holder = holderId?.let(state::player)
        val seats = informationSeats(state, lookup, id)
        fun evil(p: Player) = Registration.registersEvil(state, lookup, p)
        fun missing(message: String) = InfoResult(Answer.Message("?"), message)
        return when (id) {
            "cartographer" -> {
                val index = seats.indexOfFirst { it.id == holderId }
                if (index < 0) return missing("Select the Cartographer's seat")
                val distance = seats.indices.filter { it != index && evil(seats[it]) }
                    .minOfOrNull { minOf((it - index + seats.size) % seats.size, (index - it + seats.size) % seats.size) }
                if (distance == null) InfoResult(Answer.Message("NO EVIL PLAYER"), "No other evil player in the measured ring")
                else InfoResult(Answer.Count(distance, 0, seats.size / 2), "Nearest evil player: $distance seats away",
                    detail = "The Stowaway's seat is omitted while their ability works.", caveats = registrationNotes(state, lookup, seats))
            }
            "navigator" -> {
                if (targets.size != 2 || targets.distinct().size != 2 || targets.any { state.player(it) == null }) {
                    return missing("Choose two different endpoints, in clockwise order")
                }
                // Endpoints stay at their physical positions, including a Stowaway endpoint.
                val physical = state.seats
                val start = physical.indexOfFirst { it.id == targets[0] }
                val end = physical.indexOfFirst { it.id == targets[1] }
                val between = (1 until physical.size).map { physical[(start + it) % physical.size] }
                    .takeWhile { it.id != physical[end].id }.filter { it.alive && it in seats }
                val count = between.count(::evil)
                InfoResult(Answer.Count(count, 0, between.size), "$count alive evil players clockwise between the endpoints",
                    detail = "${state.player(targets[0])!!.name} → ${state.player(targets[1])!!.name}; endpoints excluded.",
                    caveats = registrationNotes(state, lookup, between))
            }
            "pearldiver" -> {
                if (state.seats.none { diedAtNight(state, it.id) }) {
                    return InfoResult(Answer.YesNoAnswer(false), "Nobody has died at night — shake your head")
                }
                val target = targets.singleOrNull()?.let(state::player)
                if (target == null || !diedAtNight(state, target.id)) return missing("Choose a player who died at night")
                val character = Registration.registersAsCharacter(state, lookup, target, id)
                InfoResult(Answer.Characters(listOfNotNull(character)), "${target.name} is the ${character?.let(lookup)?.name ?: "?"}",
                    cardPrefix = "THIS PLAYER IS", caveats = registrationNotes(state, lookup, listOf(target)))
            }
            "smuggler" -> {
                val target = targets.singleOrNull()?.let(state::player) ?: return missing("Choose whose information to repeat")
                val told = state.ledger.filter { it.kind == LedgerKind.TOLD && it.actorId == target.id && it.atNight &&
                    it.cycle == state.cycle && it.sourceId !in NightMarkers.all &&
                    (it.shownCard != null || it.shown.isNotBlank()) }
                if (told.isEmpty()) InfoResult(Answer.YesNoAnswer(false), "${target.name} learned nothing tonight — shake your head")
                else InfoResult(Answer.Message(told.joinToString("; ") { it.shown }), "Repeat exactly what ${target.name} was shown",
                    detail = "Show every card in order, including false and custom information. Do not disclose their character or reminders.",
                    exactCards = told.map { it.shownCard ?: ShowCardSpec.Message(it.shown) })
            }
            else -> null
        }
    }

    fun closed(state: GameState): Boolean = state.phase == Phase.NIGHT && state.counters[CLOSED_NIGHT] == state.cycle

    fun closeHarbour(state: GameState, lookup: (String) -> Character?, holder: Player): GameState {
        if (state.phase != Phase.DAY || !holder.alive || harbourSpent(state, lookup, holder.id)) return state
        val works = Status.hasAbility(state, lookup, holder.id)
        var next = Effects.place(state, holder.id, EffectKind.SPENT, "harbourmaster", holder.id,
            Until.FOREVER, "Closed", endsWithSource = false).state
        next = Ledger.record(next, LedgerEntry(kind = LedgerKind.SPENT, sourceId = "harbourmaster", actorId = holder.id, impaired = !works))
        if (works) next = next.copy(counters = next.counters + (CLOSED_NIGHT to state.cycle + 1))
        return Ledger.record(next, LedgerEntry(kind = LedgerKind.STATEMENT, sourceId = "harbourmaster", actorId = holder.id,
            text = "Publicly closes the harbour for night ${state.cycle + 1}.", impaired = !works))
    }

    fun chooseDayCrossing(state: GameState, holder: Player, target: Long?): GameState {
        val death = ferryDeath(state, holder.id) ?: return state
        if (state.phase != Phase.DAY || death.atNight || target !in ferryTargets(state, holder.id) || dayChoice(state, death) != null) return state
        return Ledger.record(state, LedgerEntry(kind = LedgerKind.CHOICE, sourceId = FERRY, actorId = holder.id,
            targetIds = listOf(target!!), causeEventId = death.id, text = "Crossing chosen at death; resolves tonight."))
    }

    /** Capture the curse at execution, even when execution protection prevents death. */
    fun afterExecution(state: GameState, record: ExecutionRecord): GameState {
        if (!active(state.script) || record.characterIdAtExecution != "albatross" ||
            record.abilityImpairedAtExecution != false || record.outcome == ExecutionOutcome.NO_EXECUTION) return state
        val target = record.nominatorId ?: return state
        if (state.effects.any { it.sourceCharacterId == "albatross" && it.targetId == target && it.createdCycle == record.day }) return state
        return Effects.place(state, target, EffectKind.MARKER, "albatross", record.playerId,
            Until.DAWN, "Cursed", endsWithSource = false, note = "Dies at the start of night ${record.day + 1}.").state
    }

    /** Dusk effects run once, after entering the new night and before any waking character. */
    fun beginNight(state: GameState, lookup: (String) -> Character?): GameState {
        if (!active(state.script)) return state
        var next = state
        if (closed(next)) {
            for (seat in next.seats) next = Effects.place(next, seat.id, EffectKind.CANT_DIE_TONIGHT,
                "harbourmaster", null, Until.DAWN, "Harbour closed", endsWithSource = false).state
        }
        val cursed = next.effects.filter { it.sourceCharacterId == "albatross" && it.label == "Cursed" &&
            it.createdCycle == state.cycle - 1 && !it.createdAtNight }
        for (effect in cursed) {
            next = Deaths.attempt(next, lookup, effect.targetId, KillCause(DeathCause.GOOD_ABILITY, "albatross")).state
            next = Effects.remove(next, effect.id)
        }
        return Effects.reconcile(next, lookup)
    }

    private fun healthy(ctx: NightContext) = ctx.holder?.let { !Status.isImpaired(ctx.state, ctx.lookup, it.id) } == true
    private fun removeMarks(ctx: NightContext, source: String, label: String) = ctx.state.seats.map {
        NightEffect.RemoveToken(source, label, Ref.Seat(it.id))
    }

    fun rules(): List<CharacterRule> = listOf(
        CharacterRule("cartographer", firstNight = NightRule(prompt = "Show the computed distance to the nearest evil player.")),
        CharacterRule("stowaway", keepsAbilityWhenDead = true),
        CharacterRule("albatross"),
        CharacterRule("lighthousekeeper", otherNight = NightRule(
            prompt = "Record their choice. The actual recipient is protected automatically; announce that recipient at dawn.",
            infoId = "",
            action = { ChoosePlayers("lighthousekeeper", "WHO DID THEY CHOOSE?", 1, 1, listOf(TargetConstraint.NOT_SELF)) },
            resolveEffects = { ctx, _, targets ->
                if (targets.size != 1) emptyList() else removeMarks(ctx, "lighthousekeeper", "Safe") +
                    listOf(NightEffect.PlaceToken("lighthousekeeper", "Safe", Ref.Target, EffectKind.SAFE_FROM_DEMON, Until.DAWN)) +
                    if (healthy(ctx)) listOf(NightEffect.Announce(BriefingSlot.DAWN,
                        "Last night, the lantern fell on ${ctx.state.player(targets[0])!!.name}."))
                    else listOf(NightEffect.QueuePrompt(BriefingSlot.DAWN, PromptKind.ANNOUNCE, "lighthousekeeper",
                        "Choose the impaired Keeper's lantern announcement.", Ref.Target))
            },
        ), tokens = listOf(TokenRule("lighthousekeeper", "Safe", effect = EffectKind.SAFE_FROM_DEMON, until = Until.DAWN))),
        CharacterRule("harbourmaster", tokens = listOf(TokenRule("harbourmaster", "Closed", effect = EffectKind.SPENT,
            until = Until.FOREVER, endsWithSource = false)), day = DayRule(ability = DayAbility(
            label = "Close harbour", oncePerGame = true,
            available = { s, lookup, p -> s.phase == Phase.DAY && p.alive && !harbourSpent(s, lookup, p.id) },
            confirmation = "Record the public declaration. The ability is spent even if drunk or poisoned. A working closure prevents deaths and Siren conversion on the following night.",
            resolve = { s, lookup, p, _ -> closeHarbour(s, lookup, p) },
        ))),
        CharacterRule("pearldiver", otherNight = NightRule(
            prompt = "Choose a player who died at night, on any night. Show the computed character; shake your head if there are none.",
            action = { ctx -> ShowInfo("pearldiver", "WHO DID THEY CHOOSE?",
                if (ctx.state.seats.any { diedAtNight(ctx.state, it.id) }) 1 else 0,
                listOf(TargetConstraint.DIED_AT_NIGHT)) },
        )),
        CharacterRule("navigator", otherNight = NightRule(
            gate = Gates.all(Gates.aliveHolder, Gates.notSpent(), WakePredicate { ctx ->
                if (Memory.isSpent(ctx.state, "navigator", ctx.holder?.id)) StepGate.Skip("Ability already used") else StepGate.Fire
            }),
            prompt = "They may pass. Otherwise record the two endpoints in order, show the computed clockwise count, then finish to spend the ability.",
            action = { ChoosePlayers("navigator", "CHOOSE THE START, THEN THE END", 2, 2, allowNone = true,
                noneLabel = "Save the ability") },
            resolveEffects = { _, input, targets -> if (!input.none && targets.size == 2) listOf(NightEffect.MarkSpent("navigator")) else emptyList() },
        ), tokens = listOf(TokenRule("navigator", "No ability", effect = EffectKind.SPENT, until = Until.FOREVER, endsWithSource = false))),
        ferryRule(),
        CharacterRule("wrecker", firstNight = wreckerNight(), otherNight = wreckerNight(),
            tokens = listOf(TokenRule("wrecker", "Wrecked", until = Until.DAWN, copies = 2))),
        CharacterRule("smuggler", firstNight = NightRule(prompt = "Choose a player and replay every card they were shown tonight, in order."),
            otherNight = NightRule(prompt = "Choose a player and replay every card they were shown tonight, in order.")),
        CharacterRule("siren", killCause = DeathCause.DEMON_KILL, otherNight = sirenNight(),
            tokens = listOf(TokenRule("siren", "Sung", until = Until.FOREVER, endsWithSource = false))),
        CharacterRule("kraken", killCause = DeathCause.DEMON_KILL, otherNight = NightRule(
            gate = Gates.all(Gates.aliveHolder, Gates.notExorcised), infoId = "",
            prompt = "Record one target. If a Minion actually died during the day, a second distinct target is optional. Each kill checks protection separately.",
            action = { ctx -> ChoosePlayers("kraken", "WHO DID THEY CHOOSE?", 1,
                if (ctx.state.deaths.any { !it.atNight && it.day == ctx.night - 1 && !it.registeredOnly && it.teamAtDeath == Team.MINION }) 2 else 1,
                perTarget = listOf(NightEffect.Attack(Ref.Target))) },
            resolveEffects = { ctx, _, _ -> removeMarks(ctx, "kraken", "Thrash") },
        ), tokens = listOf(TokenRule("kraken", "Thrash", until = Until.DAWN)), onDeath = listOf(DeathTrigger(
            gate = { s, lookup, event, holder -> !event.atNight && !event.registeredOnly && event.teamAtDeath == Team.MINION && Status.hasAbility(s, lookup, holder.id) },
            produce = { s, _, _, holder -> TriggerResult(effects = listOf(Effect(0, EffectKind.MARKER, holder.id,
                sourceCharacterId = "kraken", sourcePlayerId = holder.id, until = Until.DAWN, label = "Thrash",
                createdCycle = s.cycle, createdAtNight = false))) },
        ))),
    )

    private fun wreckerNight() = NightRule(infoId = "", prompt = "Choose two different players without an actual Demon character, even dead. Other living or dead players are legal; use actual character, not alignment or possible registration. The Ferryman exception is unchanged. Their marks and legal redirects are applied automatically for this night.",
        action = { ChoosePlayers("wrecker", "WHICH TWO NON-DEMON PLAYERS?", 2, 2, listOf(TargetConstraint.NOT_DEMON)) },
        resolveEffects = { ctx, _, targets -> if (targets.size != 2) emptyList() else removeMarks(ctx, "wrecker", "Wrecked") +
            targets.map { NightEffect.PlaceToken("wrecker", "Wrecked", Ref.Seat(it), until = Until.DAWN) } },
    )

    private fun ferryRule() = CharacterRule(FERRY, actsWhileDead = true, otherNight = NightRule(
        infoId = "", gate = WakePredicate { ctx ->
            when {
                ferryDeath(ctx.state, ctx.holder?.id) == null -> StepGate.Skip("No unresolved death tonight or during the preceding day")
                ferryTargets(ctx.state, ctx.holder!!.id).isEmpty() -> StepGate.Skip("Nobody else was available to choose — no crossing")
                else -> StepGate.Fire
            }
        },
        prompt = "Resolve the crossing after the Demon. A daytime choice is retained and never redirected; a good nighttime choice can be Wrecked back to the dead Ferryman. Check health at death and the final target's current alignment.",
        action = { ctx ->
            val death = ferryDeath(ctx.state, ctx.holder?.id)
            val choice = death?.let { dayChoice(ctx.state, it) }
            when {
                death == null || ferryTargets(ctx.state, ctx.holder!!.id).isEmpty() -> null
                choice != null -> Options(FERRY, "RESOLVE THE CHOICE MADE AT DEATH", listOf(ActionOption("cross", "Resolve crossing",
                    detail = choice.targetIds.mapNotNull { ctx.state.player(it)?.name }.joinToString(), targetIds = choice.targetIds)))
                else -> ChoosePlayers(FERRY, "WHICH OTHER DEAD PLAYER DID THEY CHOOSE?", 1, 1,
                    listOf(TargetConstraint.DEAD, TargetConstraint.NOT_SELF, TargetConstraint.DEAD_WHEN_SOURCE_DIED))
            }
        },
        resolveEffects = { ctx, input, targets ->
            val death = ferryDeath(ctx.state, ctx.holder?.id)
            val target = targets.singleOrNull()?.let(ctx.state::player)
            val directSelf = input.playerIds.singleOrNull() == ctx.holder?.id
            val original = input.playerIds.singleOrNull()
            val illegalOriginal = original != null && ctx.holder?.let { original !in ferryTargets(ctx.state, it.id) } == true
            if (death == null || target == null || target.alive || directSelf || illegalOriginal) emptyList()
            else {
                val directDayLegal = death.atNight || target.id in (death.otherDeadIdsAtDeath ?: ferryTargets(ctx.state, death.playerId))
                if (!directDayLegal) emptyList() else listOf(NightEffect.SetCounter(crossingKey(death), 1)) +
                    if (death.abilityImpairedAtDeath == false && !target.isEvil(ctx.lookup)) listOf(NightEffect.Resurrect(Ref.Target)) else emptyList()
            }
        },
    ), day = DayRule(ability = DayAbility(
        label = "Choose crossing", targets = { s, _, p -> ferryTargets(s, p.id) },
        available = { s, _, p -> s.phase == Phase.DAY && ferryDeath(s, p.id)?.let { !it.atNight && dayChoice(s, it) == null } == true && ferryTargets(s, p.id).isNotEmpty() },
        confirmation = "Record the other dead player chosen when the Ferryman died. The crossing resolves after the Demon tonight, without a Wrecker redirect.",
        resolve = { s, _, p, target -> chooseDayCrossing(s, p, target) },
    )))

    private fun sirenNight() = NightRule(gate = Gates.all(Gates.aliveHolder, Gates.notExorcised), infoId = "",
        prompt = "Choose a player. Protection is checked automatically. The first unprotected Outsider is converted; later eligible targets die. Complete the private conversion cards before continuing.",
        action = { ChoosePlayers("siren", "WHO DID THEY CHOOSE?", 1, 1) },
        resolveEffects = { ctx, _, targets ->
            val target = targets.singleOrNull()?.let(ctx.state::player)
            val holder = ctx.holder
            when {
                target == null || holder == null || !holder.alive || !healthy(ctx) || closed(ctx.state) ||
                    Status.demonHarmBlocked(ctx.state, ctx.lookup, target.id) -> emptyList()
                target.team(ctx.lookup) == Team.OUTSIDER && Status.live(ctx.state, ctx.lookup, holder.id).none {
                    Tokens.key(it.sourceCharacterId, it.label) == Tokens.key("siren", "Sung")
                } -> listOf(
                    NightEffect.PlaceToken("siren", "Sung", Ref.Source, until = Until.FOREVER, endsWithSource = false),
                    NightEffect.SetAlignment(Ref.Target, true, "Converted by the Siren"),
                    NightEffect.RevealTrueCharacter(Ref.Target),
                    NightEffect.QueuePrompt(BriefingSlot.NOW, PromptKind.INFO, "siren",
                        "Wake ${target.name}: show their true character, EVIL, and point at ${holder.name} as the Siren.", Ref.Target,
                        cards = listOfNotNull(target.characterId?.let { ShowCardSpec.CharacterCard("YOU ARE", it) },
                            ShowCardSpec.AlignmentCard(true), ShowCardSpec.PointCard("THIS PLAYER IS THE SIREN", listOf(holder.name),
                                listOf(ctx.state.seats.indexOfFirst { it.id == holder.id } + 1)))),
                    NightEffect.QueuePrompt(BriefingSlot.NOW, PromptKind.INFO, "siren", "Show ${holder.name} a thumbs-up: conversion succeeded.", Ref.Source,
                        cards = listOf(ShowCardSpec.Message("YES"))),
                )
                else -> listOf(NightEffect.Attack(Ref.Target))
            }
        },
    )
}
