package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What kind of line this is. */
@Serializable
enum class BriefingKind {
    /** Say this out loud, in this order. */
    ANNOUNCE,

    /** For the storyteller only — never say it. */
    PRIVATE,

    /** A standing fact that constrains today. */
    STANDING_FACT,

    /** Something the storyteller must still do or collect. */
    TODO_ASK,

    /** A token that was just swept off the grimoire. */
    SWEPT,
}

@Serializable
enum class BriefingSeverity { INFO, ACTION, ALERT }

@Serializable
data class BriefingItem(
    /** Stable key for the ticked-off set; survives recomposition and undo. */
    val key: String,
    val kind: BriefingKind,
    val severity: BriefingSeverity = BriefingSeverity.INFO,
    val sourceId: String = "",
    /** Imperative, storyteller voice, ready to read aloud. */
    val text: String,
    val playerId: Long? = null,
    /** The prompt or ledger entry this discharges, if any. */
    val promptId: Long? = null,
    val ledgerId: Long? = null,
    /**
     * One-tap follow-through, as a stable string the UI maps to a handler:
     * "open-seat:7", "rerun-first-night:7", "record:gossip", "show-card:<spec>",
     * "resolve-prompt:12", "mark-announced:9".
     */
    val actionId: String = "",
)

/** One briefing. Serialisable so `lastDawn` / `lastDusk` can be frozen on the state. */
@Serializable
data class Briefing(
    val slot: BriefingSlot,
    val cycle: Int,
    val items: List<BriefingItem> = emptyList(),
) {
    fun of(kind: BriefingKind): List<BriefingItem> = items.filter { it.kind == kind }

    val announce: List<BriefingItem> get() = of(BriefingKind.ANNOUNCE)
    val private: List<BriefingItem> get() = of(BriefingKind.PRIVATE)
    val standing: List<BriefingItem> get() = of(BriefingKind.STANDING_FACT)
    val todo: List<BriefingItem> get() = of(BriefingKind.TODO_ASK)
}

/**
 * Derived views over prompts + effects + ledger + deaths + executions (WP6).
 *
 * Five competing proposals (`DawnReport`, `DayBriefing`, `DuskBriefing`,
 * `Briefing.Line`, `BriefingItem`) collapse into ONE type and ONE function
 * parameterised by [BriefingSlot] (lead D35/D37, ARCHITECTURE §2.12).
 *
 * Nothing here is stored. [GameState.lastDawn] and [GameState.lastDusk] are
 * frozen snapshots taken by `Phases.advancePhase` BEFORE the token sweep, which
 * is the whole reason "Bea was attacked — the Monk saved her" can be computed at
 * all: the Monk's token is still on the grimoire at that instant.
 */
object Briefings {

    // ---- actionId prefixes the UI maps to handlers ----

    /** Open the seat sheet for a player. */
    const val ACTION_OPEN_SEAT: String = "open-seat:"

    /** Jump to the inserted first-night step for a resurrected seat. */
    const val ACTION_RERUN_FIRST_NIGHT: String = "rerun-first-night:"

    /** Open the recorder for a character's public statement / guess / question. */
    const val ACTION_RECORD: String = "record:"

    /** Tick off a pending ANNOUNCE ledger entry. */
    const val ACTION_MARK_ANNOUNCED: String = "mark-announced:"

    /** Resolve one deferred obligation. */
    const val ACTION_RESOLVE_PROMPT: String = "resolve-prompt:"

    /** Open the execution sheet for the player on the block. */
    const val ACTION_EXECUTE: String = "execute:"

    /** Text a resurrection announcement always contains — never a reason (lead D7). */
    internal const val ALIVE_AGAIN: String = "is alive again"

    /**
     * Pure. NOTHING here is stored; [GameState.lastDawn] is a frozen snapshot,
     * not a source.
     */
    fun at(state: GameState, lookup: (String) -> Character?, slot: BriefingSlot): Briefing {
        val items = when (slot) {
            BriefingSlot.NOW -> prompts(state, BriefingSlot.NOW)
            BriefingSlot.TONIGHT -> prompts(state, BriefingSlot.TONIGHT)
            BriefingSlot.DAWN -> dawn(state, lookup)
            BriefingSlot.DAY_START -> dayStart(state, lookup)
            BriefingSlot.NOMINATION -> nomination(state, lookup)
            BriefingSlot.EXECUTION -> execution(state, lookup)
            BriefingSlot.DUSK -> dusk(state, lookup)
        }
        // Keys are the ticked-off identity, so they must be unique within one
        // briefing even when two rules describe the same fact.
        return Briefing(slot = slot, cycle = state.cycle, items = items.distinctBy { it.key })
    }

    // -----------------------------------------------------------------------
    // DAWN
    // -----------------------------------------------------------------------

    /**
     * Deaths first, in seat order, then resurrections, then everything else the
     * storyteller still owes the table.
     *
     * A Zombuul's first death is announced as a REAL death — that is the whole
     * point of the character — and the truth goes in PRIVATE (lead D6).
     */
    @Suppress("LongMethod")
    private fun dawn(state: GameState, lookup: (String) -> Character?): List<BriefingItem> =
        buildList {
            val night = state.cycle
            val order = seatOrder(state)

            // 1. Deaths, in seat order.
            val died = state.deaths
                .filter { it.atNight && it.day == night && it.resurrectedAtCycle == null }
                .distinctBy { it.playerId }
                .sortedBy { order[it.playerId] ?: Int.MAX_VALUE }
            if (died.isEmpty()) {
                add(
                    BriefingItem(
                        key = "dawn:$night:nobody",
                        kind = BriefingKind.ANNOUNCE,
                        severity = BriefingSeverity.ACTION,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = "Announce: nobody died.",
                    ),
                )
            }
            for (death in died) {
                val who = nameOf(state, death.playerId)
                add(
                    BriefingItem(
                        key = "dawn:$night:death:${death.playerId}",
                        kind = BriefingKind.ANNOUNCE,
                        severity = BriefingSeverity.ACTION,
                        sourceId = death.killerCharacterId,
                        text = "Announce: $who died.",
                        playerId = death.playerId,
                        actionId = "$ACTION_OPEN_SEAT${death.playerId}",
                    ),
                )
                if (death.registeredOnly) {
                    add(
                        BriefingItem(
                            key = "dawn:$night:secretly-alive:${death.playerId}",
                            kind = BriefingKind.PRIVATE,
                            severity = BriefingSeverity.ALERT,
                            sourceId = death.characterIdAtDeath.orEmpty(),
                            text = "$who is secretly alive — they only register as dead. " +
                                "Do not shroud them.",
                            playerId = death.playerId,
                        ),
                    )
                }
            }

            // 2. Resurrections, AFTER the deaths and WITHOUT a reason (lead D7).
            val pending = Memory.pendingAnnouncements(state)
            val consumed = mutableSetOf<Long>()
            val back = state.deaths
                .filter { it.resurrectedAtCycle == night }
                .distinctBy { it.playerId }
                .sortedBy { order[it.playerId] ?: Int.MAX_VALUE }
            for (event in back) {
                val who = nameOf(state, event.playerId)
                val entry = pending.firstOrNull {
                    it.actorId == event.playerId && ALIVE_AGAIN in it.text
                }
                entry?.let { consumed += it.id }
                add(
                    BriefingItem(
                        key = "dawn:$night:alive-again:${event.playerId}",
                        kind = BriefingKind.ANNOUNCE,
                        severity = BriefingSeverity.ACTION,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = "Announce: $who is alive again.",
                        playerId = event.playerId,
                        ledgerId = entry?.id,
                        actionId = entry?.let { "$ACTION_MARK_ANNOUNCED${it.id}" }.orEmpty(),
                    ),
                )
                add(
                    BriefingItem(
                        key = "dawn:$night:alive-again-why:${event.playerId}",
                        kind = BriefingKind.PRIVATE,
                        severity = BriefingSeverity.INFO,
                        text = "Do not say why $who is alive again.",
                        playerId = event.playerId,
                    ),
                )
            }

            // 3. Everything else the storyteller still owes the table.
            for (entry in pending) {
                if (entry.id in consumed) continue
                add(announcement(entry))
            }

            // 4. The silent saves. A prevented death is a ledger RULING (lead D24),
            //    and the table must never hear it.
            for (entry in state.ledger) {
                if (entry.kind != LedgerKind.RULING) continue
                if (entry.cycle != night || !entry.atNight) continue
                if (entry.text.isBlank()) continue
                add(
                    BriefingItem(
                        key = "dawn:$night:ruling:${entry.id}",
                        kind = BriefingKind.PRIVATE,
                        severity = BriefingSeverity.INFO,
                        sourceId = entry.sourceId,
                        text = entry.text,
                        playerId = entry.actorId,
                        ledgerId = entry.id,
                    ),
                )
            }

            // 5. Obligations that come due now, and any first night still owed.
            addAll(prompts(state, BriefingSlot.DAWN))
            for (prompt in Prompts.due(state, BriefingSlot.TONIGHT)) {
                if (prompt.kind != PromptKind.RUN_FIRST_NIGHT) continue
                add(
                    BriefingItem(
                        key = "dawn:$night:rerun:${prompt.id}",
                        kind = BriefingKind.TODO_ASK,
                        severity = BriefingSeverity.ACTION,
                        sourceId = prompt.sourceId,
                        text = prompt.title,
                        playerId = prompt.subjectPlayerId,
                        promptId = prompt.id,
                        actionId = prompt.subjectPlayerId
                            ?.let { "$ACTION_RERUN_FIRST_NIGHT$it" }.orEmpty(),
                    ),
                )
            }

            // 6. What the sweep is about to take off the grimoire. Computed here
            //    because `Phases.advancePhase` runs this BEFORE it sweeps.
            addAll(swept(state, lookup, Until.DAWN, "dawn:$night"))
        }

    // -----------------------------------------------------------------------
    // DAY_START
    // -----------------------------------------------------------------------

    /**
     * Everything that constrains today, computed — never remembered (friction §6).
     * Standing protections, madness AND what they are mad about, clocks, and the
     * collect list: one TODO_ASK per statement the day still owes.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun dayStart(state: GameState, lookup: (String) -> Character?): List<BriefingItem> =
        buildList {
            val day = state.cycle

            // Anything still unsaid from dawn stays on the card until it is said.
            for (entry in Memory.pendingAnnouncements(state)) add(announcement(entry))

            for (seat in state.seats) {
                // Madness, and the character they are mad about (§2.12).
                for (effect in Status.live(state, lookup, seat.id, EffectKind.MAD)) {
                    add(
                        BriefingItem(
                            key = "day:$day:mad:${seat.id}:${effect.id}",
                            kind = BriefingKind.PRIVATE,
                            severity = BriefingSeverity.ALERT,
                            sourceId = effect.sourceCharacterId,
                            text = madnessText(seat, effect, lookup),
                            playerId = seat.id,
                            actionId = "$ACTION_OPEN_SEAT${seat.id}",
                        ),
                    )
                }
                // Standing protections: "Ben survives execution today (Devil's Advocate)."
                for (effect in Status.protections(state, lookup, seat.id)) {
                    val text = protectionText(seat, effect, lookup) ?: continue
                    add(
                        BriefingItem(
                            key = "day:$day:protected:${seat.id}:${effect.id}",
                            kind = BriefingKind.STANDING_FACT,
                            severity = BriefingSeverity.INFO,
                            sourceId = effect.sourceCharacterId,
                            text = text,
                            playerId = seat.id,
                        ),
                    )
                }
                // Abilities lost today.
                if (seat.alive) {
                    Status.impairment(state, lookup, seat.id).firstOrNull()?.let { reason ->
                        add(
                            BriefingItem(
                                key = "day:$day:impaired:${seat.id}",
                                kind = BriefingKind.STANDING_FACT,
                                severity = BriefingSeverity.INFO,
                                sourceId = reason.effect.sourceCharacterId,
                                text = "${seat.name}'s ability does not work — ${reason.text}.",
                                playerId = seat.id,
                            ),
                        )
                    }
                }
            }

            // Voting and nomination rules in force today.
            if (DayRules.secretVoting(state, lookup)) {
                add(
                    BriefingItem(
                        key = "day:$day:secret-voting",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.ACTION,
                        sourceId = "organgrinder",
                        text = "Eyes closed for every vote today — the tally is secret.",
                    ),
                )
            }
            DayRules.vizier(state, lookup)?.let { vizier ->
                add(
                    BriefingItem(
                        key = "day:$day:vizier",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.ACTION,
                        sourceId = "vizier",
                        text = "${vizier.name} (Vizier) may execute immediately if any good " +
                            "player votes.",
                        playerId = vizier.id,
                    ),
                )
            }
            DayRules.holderOfWithAbility(state, lookup, "psychopath")?.let { psycho ->
                add(
                    BriefingItem(
                        key = "day:$day:psychopath",
                        kind = BriefingKind.TODO_ASK,
                        severity = BriefingSeverity.ACTION,
                        sourceId = "psychopath",
                        text = "Ask ${psycho.name} (Psychopath) before you open nominations.",
                        playerId = psycho.id,
                        actionId = "$ACTION_OPEN_SEAT${psycho.id}",
                    ),
                )
            }
            DayRules.nominationsClosedReason(state, lookup).takeIf { it.isNotBlank() }?.let {
                add(
                    BriefingItem(
                        key = "day:$day:nominations-closed",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.INFO,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = it,
                    ),
                )
            }

            addAll(clocks(state, lookup, "day:$day"))
            addAll(collectList(state))
            addAll(prompts(state, BriefingSlot.DAY_START))
        }

    /** Leviathan / Riot day counters, the Mayor's three and the Vortox's demand. */
    private fun clocks(
        state: GameState,
        lookup: (String) -> Character?,
        prefix: String,
    ): List<BriefingItem> = buildList {
        val leviathan = DayRules.leviathanDay(state)
        if (leviathan > 0) {
            add(
                BriefingItem(
                    key = "$prefix:leviathan",
                    kind = BriefingKind.STANDING_FACT,
                    severity = if (leviathan >= 5) BriefingSeverity.ALERT else BriefingSeverity.INFO,
                    sourceId = "leviathan",
                    text = "Leviathan: this is day $leviathan of 5.",
                ),
            )
        }
        val riot = DayRules.riotDay(state)
        if (riot > 0) {
            add(
                BriefingItem(
                    key = "$prefix:riot",
                    kind = BriefingKind.STANDING_FACT,
                    severity = if (riot >= 3) BriefingSeverity.ALERT else BriefingSeverity.INFO,
                    sourceId = "riot",
                    text = "Riot: this is day $riot of 3 — a nominee dies immediately.",
                ),
            )
        }
        if (DayRules.holderOfWithAbility(state, lookup, "mayor") != null &&
            state.aliveCountWithTravellers == 3
        ) {
            add(
                BriefingItem(
                    key = "$prefix:mayor",
                    kind = BriefingKind.STANDING_FACT,
                    severity = BriefingSeverity.ALERT,
                    sourceId = "mayor",
                    text = "Three players are alive — if nobody is executed today, " +
                        "the Mayor wins for good.",
                ),
            )
        }
        if (DayRules.holderOfWithAbility(state, lookup, "vortox") != null) {
            add(
                BriefingItem(
                    key = "$prefix:vortox",
                    kind = BriefingKind.STANDING_FACT,
                    severity = BriefingSeverity.ALERT,
                    sourceId = "vortox",
                    text = "Someone must be executed today, or the Vortox wins for evil.",
                ),
            )
        }
    }

    /**
     * One row for every public thing the day still owes — the collect half of
     * *"make it easy to write down all the gossips"*. The free-text recorder
     * covers everything not on this table.
     */
    private data class Collect(
        val characterId: String,
        /** The ability is once per game: a SPENT row retires the reminder. */
        val oncePerGame: Boolean = false,
        val onlyOnDayOne: Boolean = false,
        val text: (String) -> String,
    )

    private val COLLECT: List<Collect> = listOf(
        Collect("gossip") { "Record $it's public statement today (Gossip)." },
        Collect("juggler", onlyOnDayOne = true) {
            "$it may guess up to 5 characters today (Juggler) — record every guess."
        },
        Collect("savant") { "$it may visit you today (Savant) — one true and one false statement." },
        Collect("artist", oncePerGame = true) {
            "$it may ask you one yes/no question today (Artist)."
        },
        Collect("fisherman", oncePerGame = true) { "$it may ask you for advice today (Fisherman)." },
        Collect("slayer", oncePerGame = true) { "$it may claim a Slayer shot today." },
        Collect("alsaahir") { "$it may guess the evil line-up today (Alsaahir)." },
    )

    private fun collectList(state: GameState): List<BriefingItem> = buildList {
        val day = state.cycle
        for (row in COLLECT) {
            if (row.onlyOnDayOne && day != 1) continue
            for (seat in state.seats) {
                if (Character.normalizeId(seat.characterId.orEmpty()) != row.characterId) continue
                if (!seat.alive) continue
                if (row.oncePerGame && Memory.isSpent(state, row.characterId, seat.id)) continue
                if (Memory.statementsOn(state, day, row.characterId, seat.id).isNotEmpty()) continue
                add(
                    BriefingItem(
                        key = "day:$day:collect:${row.characterId}:${seat.id}",
                        kind = BriefingKind.TODO_ASK,
                        severity = BriefingSeverity.ACTION,
                        sourceId = row.characterId,
                        text = row.text(seat.name),
                        playerId = seat.id,
                        actionId = "$ACTION_RECORD${row.characterId}",
                    ),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // NOMINATION
    // -----------------------------------------------------------------------

    /**
     * The `NominationTrigger` list for the pending pair. With no nomination
     * recorded yet today the check still runs with an empty pair, so the
     * standing warnings (Witch, Vizier, Bishop) are on the card before the
     * first chip is tapped.
     */
    private fun nomination(state: GameState, lookup: (String) -> Character?): List<BriefingItem> {
        val pair = state.nominations.lastOrNull { it.day == state.cycle && !it.isExile }
        val check = DayRules.checkNomination(
            state = state,
            lookup = lookup,
            nominatorId = pair?.nominatorId,
            nomineeId = pair?.nomineeId,
        )
        val prefix = "nomination:${state.cycle}:${pair?.nomineeId ?: "none"}"
        return buildList {
            check.blockers.forEachIndexed { index, blocker ->
                add(
                    BriefingItem(
                        key = "$prefix:blocker:$index",
                        kind = BriefingKind.TODO_ASK,
                        severity = BriefingSeverity.ALERT,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = blocker,
                    ),
                )
            }
            check.cautions.forEachIndexed { index, caution ->
                add(
                    BriefingItem(
                        key = "$prefix:caution:$index",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.INFO,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = caution,
                    ),
                )
            }
            check.triggers.forEachIndexed { index, trigger ->
                val kind = when (trigger.kind) {
                    TriggerKind.WARN, TriggerKind.VOTE_MODIFIER -> BriefingKind.STANDING_FACT
                    else -> BriefingKind.TODO_ASK
                }
                val severity = when (trigger.kind) {
                    TriggerKind.AUTO_DEATH, TriggerKind.AUTO_EXECUTION, TriggerKind.END_DAY ->
                        BriefingSeverity.ALERT

                    TriggerKind.CHOICE, TriggerKind.VOTE_MODIFIER -> BriefingSeverity.ACTION
                    TriggerKind.WARN -> BriefingSeverity.INFO
                }
                add(
                    BriefingItem(
                        key = "$prefix:trigger:${trigger.sourceId}:$index",
                        kind = kind,
                        severity = severity,
                        sourceId = trigger.sourceId,
                        text = trigger.headline +
                            if (trigger.impaired) " (their ability may not work)" else "",
                        playerId = trigger.targetId ?: trigger.actorId,
                    ),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // EXECUTION
    // -----------------------------------------------------------------------

    /** The `ExecutionConsequence` list for the execution recorded today. */
    private fun execution(state: GameState, lookup: (String) -> Character?): List<BriefingItem> {
        val record = DayRules.executionToday(state) ?: return emptyList()
        val prefix = "execution:${state.cycle}"
        return Execution.consequences(state, lookup, record).mapIndexed { index, consequence ->
            val kind = when {
                consequence.headline.startsWith("Say:") -> BriefingKind.ANNOUNCE
                consequence.options.isNotEmpty() -> BriefingKind.TODO_ASK
                else -> BriefingKind.STANDING_FACT
            }
            val severity = when {
                "WINS" in consequence.headline -> BriefingSeverity.ALERT
                consequence.options.isNotEmpty() || kind == BriefingKind.ANNOUNCE ->
                    BriefingSeverity.ACTION

                else -> BriefingSeverity.INFO
            }
            BriefingItem(
                key = "$prefix:${consequence.sourceId}:$index",
                kind = kind,
                severity = severity,
                sourceId = consequence.sourceId,
                text = consequence.headline +
                    if (consequence.impaired) " (their ability may not work)" else "",
                playerId = record.diedInsteadId ?: record.playerId,
            )
        }
    }

    // -----------------------------------------------------------------------
    // DUSK
    // -----------------------------------------------------------------------

    /**
     * What expires now, what will wake, the conditional wakes ("Nobody died
     * today — the Zombuul kills tonight"), the countdowns, and every
     * `WinCheck.duskCheck` advisory. Advisories are keyed on
     * [WinCheck.Advisory.ruleId], which is also the dismissal key (lead D21).
     */
    private fun dusk(state: GameState, lookup: (String) -> Character?): List<BriefingItem> =
        buildList {
            val day = state.cycle
            val prefix = "dusk:$day"

            for (advisory in WinCheck.dedupe(WinCheck.duskCheck(state, lookup))) {
                // `goodWins == null` is a briefing, not an ending: the Zombuul's
                // "nobody died today" row is the canonical example.
                val ending = advisory.goodWins != null || advisory.blocking
                add(
                    BriefingItem(
                        key = "$prefix:advisory:${advisory.ruleId}",
                        kind = if (ending) BriefingKind.TODO_ASK else BriefingKind.STANDING_FACT,
                        severity = if (ending) BriefingSeverity.ALERT else BriefingSeverity.INFO,
                        sourceId = advisory.ruleId,
                        text = advisory.reason,
                    ),
                )
                advisory.cautions.forEachIndexed { index, caution ->
                    add(
                        BriefingItem(
                            key = "$prefix:advisory:${advisory.ruleId}:caution:$index",
                            kind = BriefingKind.STANDING_FACT,
                            severity = BriefingSeverity.INFO,
                            sourceId = advisory.ruleId,
                            text = caution,
                        ),
                    )
                }
            }

            // Someone is on the block and has not been executed: the one thing
            // that must not be lost between the day and the night.
            DayRules.aboutToDie(state)?.let { onBlock ->
                if (state.player(onBlock)?.alive == true && !DayRules.executionSpent(state)) {
                    add(
                        BriefingItem(
                            key = "$prefix:on-the-block:$onBlock",
                            kind = BriefingKind.TODO_ASK,
                            severity = BriefingSeverity.ALERT,
                            sourceId = Ledger.Sources.STORYTELLER,
                            text = "${nameOf(state, onBlock)} is on the block and has not " +
                                "been executed.",
                            playerId = onBlock,
                            actionId = "$ACTION_EXECUTE$onBlock",
                        ),
                    )
                }
            }
            DayRules.nominationsClosedReason(state, lookup).takeIf { it.isNotBlank() }?.let {
                add(
                    BriefingItem(
                        key = "$prefix:nominations-closed",
                        kind = BriefingKind.STANDING_FACT,
                        severity = BriefingSeverity.INFO,
                        sourceId = Ledger.Sources.STORYTELLER,
                        text = it,
                    ),
                )
            }

            addAll(countdowns(state, lookup, prefix))
            addAll(swept(state, lookup, Until.DUSK, prefix))
            willWake(state, lookup)?.let(::add)
            addAll(prompts(state, BriefingSlot.DUSK))
        }

    /** "Courtier: Bo's Drunk 1 becomes Drunk 2 tonight." */
    private fun countdowns(
        state: GameState,
        lookup: (String) -> Character?,
        prefix: String,
    ): List<BriefingItem> = buildList {
        fun row(sourceId: String, label: String, playerId: Long?, id: String) {
            val rule = Tokens.rule(sourceId, label) ?: return
            if (!Tokens.isCountdown(rule) || rule.until != Until.DUSK) return
            val source = lookup(sourceId)?.name ?: sourceId
            val who = playerId?.let { "${nameOf(state, it)}'s " }.orEmpty()
            val next = Tokens.next(rule)
            add(
                BriefingItem(
                    key = "$prefix:countdown:$id",
                    kind = BriefingKind.STANDING_FACT,
                    severity = BriefingSeverity.INFO,
                    sourceId = sourceId,
                    text = if (next == null) {
                        "$source: $who$label ends tonight."
                    } else {
                        "$source: $who$label becomes ${next.label} tonight."
                    },
                    playerId = playerId,
                ),
            )
        }
        for (effect in state.effects) {
            if (effect.label.isEmpty()) continue
            row(effect.sourceCharacterId, effect.label, effect.targetId, "e${effect.id}")
        }
        for (seat in state.seats) {
            seat.reminders.forEachIndexed { index, reminder ->
                row(reminder.sourceId, reminder.label, seat.id, "p${seat.id}:$index")
            }
        }
        state.storytellerReminders.forEachIndexed { index, reminder ->
            row(reminder.sourceId, reminder.label, null, "c$index")
        }
    }

    /**
     * A one-line preview of tonight's sheet, computed on a probe state so the
     * storyteller sees who wakes before they commit to dusk. Advisory only —
     * `NightPlan.build` is the authority once the night actually starts.
     */
    private fun willWake(state: GameState, lookup: (String) -> Character?): BriefingItem? {
        val probe = state.copy(
            phase = Phase.NIGHT,
            cycle = state.cycle + 1,
            nightStepsDone = emptySet(),
        )
        val steps = NightPlan.build(probe, lookup).steps.filter { it.required }
        if (steps.isEmpty()) return null
        val titles = steps.take(WAKE_PREVIEW).joinToString(" · ") { it.title }
        val more = (steps.size - WAKE_PREVIEW).coerceAtLeast(0)
        return BriefingItem(
            key = "dusk:${state.cycle}:will-wake",
            kind = BriefingKind.STANDING_FACT,
            severity = BriefingSeverity.INFO,
            sourceId = Ledger.Sources.STORYTELLER,
            text = "Tonight, ${steps.size} steps: $titles" + if (more > 0) " · +$more more" else "",
        )
    }

    /** How many step titles the dusk preview spells out before it counts the rest. */
    private const val WAKE_PREVIEW: Int = 6

    // -----------------------------------------------------------------------
    // shared
    // -----------------------------------------------------------------------

    /** Unresolved obligations that come due at [slot]. */
    private fun prompts(state: GameState, slot: BriefingSlot): List<BriefingItem> =
        Prompts.due(state, slot).map { prompt ->
            BriefingItem(
                key = "prompt:${prompt.id}",
                kind = BriefingKind.TODO_ASK,
                severity = if (prompt.optional) BriefingSeverity.INFO else BriefingSeverity.ACTION,
                sourceId = prompt.sourceId,
                text = prompt.title,
                playerId = prompt.subjectPlayerId,
                promptId = prompt.id,
                actionId = "$ACTION_RESOLVE_PROMPT${prompt.id}",
            )
        }

    /** One still-owed sentence, in the voice it must be said in. */
    private fun announcement(entry: LedgerEntry): BriefingItem = BriefingItem(
        key = "announce:${entry.id}",
        kind = BriefingKind.ANNOUNCE,
        severity = BriefingSeverity.ACTION,
        sourceId = entry.sourceId,
        text = "Announce: ${entry.text}",
        playerId = entry.actorId,
        ledgerId = entry.id,
        actionId = "$ACTION_MARK_ANNOUNCED${entry.id}",
    )

    /**
     * Everything the sweep at [at] is about to remove. Mirrors `Phases.sweep`:
     * countdown steps advance rather than expire, so they are never listed here.
     */
    private fun swept(
        state: GameState,
        lookup: (String) -> Character?,
        at: Until,
        prefix: String,
    ): List<BriefingItem> = buildList {
        fun line(sourceId: String, label: String, playerId: Long?): String {
            val source = lookup(sourceId)?.name ?: sourceId
            val who = playerId?.let { " from ${nameOf(state, it)}" }.orEmpty()
            return "Removed: $label ($source)$who."
        }

        fun retires(sourceId: String, label: String): Boolean {
            if (label.isEmpty()) return false
            val rule = Tokens.rule(sourceId, label) ?: return false
            if (Tokens.isCountdown(rule)) return false
            if (rule.until == Until.DUSK_AFTER_N_DAYS) return at == Until.DUSK
            return rule.until == at
        }

        for (effect in state.effects) {
            if (effect.label.isEmpty() || effect.until != at) continue
            val rule = Tokens.rule(effect.sourceCharacterId, effect.label)
            if (rule != null && Tokens.isCountdown(rule)) continue
            add(
                BriefingItem(
                    key = "$prefix:swept:e${effect.id}",
                    kind = BriefingKind.SWEPT,
                    severity = BriefingSeverity.INFO,
                    sourceId = effect.sourceCharacterId,
                    text = line(effect.sourceCharacterId, effect.label, effect.targetId),
                    playerId = effect.targetId,
                ),
            )
        }
        for (seat in state.seats) {
            seat.reminders.forEachIndexed { index, reminder ->
                if (!retires(reminder.sourceId, reminder.label)) return@forEachIndexed
                add(
                    BriefingItem(
                        key = "$prefix:swept:p${seat.id}:$index",
                        kind = BriefingKind.SWEPT,
                        severity = BriefingSeverity.INFO,
                        sourceId = reminder.sourceId,
                        text = line(reminder.sourceId, reminder.label, seat.id),
                        playerId = seat.id,
                    ),
                )
            }
        }
    }

    /** "Cara is mad that they are the Empath (Cerenovus) — …" */
    private fun madnessText(seat: Player, effect: Effect, lookup: (String) -> Character?): String {
        val source = lookup(effect.sourceCharacterId)?.name ?: effect.sourceCharacterId
        val about = effect.characterId?.let { lookup(it)?.name ?: it }
        val what = if (about != null) "mad that they are the $about" else "mad"
        return "${seat.name} is $what ($source) — if they do not try to convince the group, " +
            "they may be executed today."
    }

    /** The standing fact one protective effect creates, or null when it says nothing today. */
    private fun protectionText(
        seat: Player,
        effect: Effect,
        lookup: (String) -> Character?,
    ): String? {
        val source = lookup(effect.sourceCharacterId)?.name ?: effect.sourceCharacterId
        val by = if (source.isBlank()) "" else " ($source)"
        return when (effect.kind) {
            EffectKind.SURVIVES_EXECUTION ->
                "${seat.name} survives execution today$by. Announce the execution, then that " +
                    "they live — never why."

            EffectKind.CANT_DIE -> "${seat.name} can't die$by."
            EffectKind.ONLY_EXECUTION_KILLS -> "${seat.name} can only die by execution$by."
            EffectKind.DAY_IMMUNE -> "${seat.name} cannot die during the day$by."
            EffectKind.DEATH_TIED_TO -> "${seat.name} dies only when their host dies$by."
            EffectKind.CANT_DIE_TONIGHT -> "${seat.name} cannot die tonight$by."
            EffectKind.SAFE_FROM_DEMON -> "${seat.name} is safe from the Demon$by."
            EffectKind.DEMON_CANNOT_KILL -> "${seat.name} cannot kill tonight$by."
            else -> null
        }
    }

    private fun seatOrder(state: GameState): Map<Long, Int> =
        state.players.withIndex().associate { (index, player) -> player.id to index }

    private fun nameOf(state: GameState, playerId: Long): String =
        state.player(playerId)?.name ?: "Someone"
}
