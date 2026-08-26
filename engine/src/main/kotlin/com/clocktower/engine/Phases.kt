package com.clocktower.engine

/**
 * The phase pipeline (ARCHITECTURE §2.15).
 *
 * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> ...
 *
 * NIGHT -> DAY, in this exact order (the ordering is the fix for "the Monk token
 * was already gone when the dawn report was computed"):
 *   1. dawn = Briefings.at(state, lookup, DAWN)          // BEFORE any sweep
 *   2. expire Until.DAWN effects and their tokens
 *   3. Tokens.advanceCountdowns(state, Until.DAWN)
 *   4. Effects.reconcile(...)
 *   5. copy(phase = DAY, lastDawn = dawn)
 *
 * DAY -> NIGHT:
 *   1. dusk = Briefings.at(state, lookup, DUSK)
 *   2. expire Until.DUSK effects; advance dusk countdowns
 *   3. Effects.reconcile(...)
 *   4. copy(phase = NIGHT, cycle = cycle + 1, nightStepsDone = emptySet(), lastDusk = dusk)
 */
object Phases {

    /**
     * How the briefing for a boundary is computed.
     *
     * Injectable so a test can assert what the grimoire looked like at the moment
     * the briefing ran — the acceptance criterion is that the Monk token is still
     * present then. [Briefings.at] is WP6's; until it lands, a `NotImplementedError`
     * degrades to "no briefing" instead of breaking the phase flow.
     */
    fun interface BriefingSource {
        fun at(state: GameState, lookup: (String) -> Character?, slot: BriefingSlot): Briefing?
    }

    /** The production source: WP6's pure `Briefings.at`. */
    val DEFAULT_BRIEFINGS: BriefingSource = BriefingSource { state, lookup, slot ->
        @Suppress("SwallowedException")
        try {
            Briefings.at(state, lookup, slot)
        } catch (e: NotImplementedError) {
            null // WP6 has not landed yet.
        }
    }

    /**
     * Removes every effect and every hand-placed token whose [TokenRule] retires
     * at [at]. Countdown steps are advanced instead, never swept.
     */
    private fun sweep(state: GameState, at: Until): GameState {
        /** A countdown step is advanced by [Tokens.advanceCountdowns], never swept. */
        fun isCountdown(sourceId: String, label: String): Boolean =
            label.isNotEmpty() && Tokens.rule(sourceId, label)?.let(Tokens::isCountdown) == true

        fun retires(sourceId: String, label: String): Boolean {
            if (label.isEmpty()) return false
            val rule = Tokens.rule(sourceId, label) ?: return false
            return rule.until == at && !Tokens.isCountdown(rule)
        }
        return state.copy(
            // Effects carry their own lifetime: an effect placed with an explicit
            // `until` is retired even when no token rule names it.
            effects = state.effects.filterNot {
                it.until == at && !isCountdown(it.sourceCharacterId, it.label)
            },
            players = state.players.map { p ->
                p.copy(reminders = p.reminders.filterNot { retires(it.sourceId, it.label) })
            },
            storytellerReminders = state.storytellerReminders.filterNot {
                retires(it.sourceId, it.label)
            },
        )
    }

    /**
     * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> DAY 2 -> ...
     * Day- and night-scoped effects and their tokens expire automatically at the
     * transition (undoable, and re-placeable by hand like everything else).
     */
    fun advancePhase(
        state: GameState,
        lookup: (String) -> Character? = { null },
        briefings: BriefingSource = DEFAULT_BRIEFINGS,
    ): GameState = when (state.phase) {
        Phase.SETUP ->
            Effects.reconcile(state, lookup)
                .copy(phase = Phase.NIGHT, cycle = 1, nightStepsDone = emptySet())

        Phase.NIGHT -> {
            // 1. The dawn briefing is computed while the grimoire still holds
            //    every token the night placed — "Bea was saved" needs the Monk's.
            val dawn = briefings.at(state, lookup, BriefingSlot.DAWN)
            var next = sweep(state, Until.DAWN)
            next = Tokens.advanceCountdowns(next, Until.DAWN)
            next = Effects.reconcile(next, lookup)
            next.copy(phase = Phase.DAY, lastDawn = dawn ?: next.lastDawn)
        }

        Phase.DAY -> {
            val dusk = briefings.at(state, lookup, BriefingSlot.DUSK)
            var next = sweep(state, Until.DUSK)
            next = Tokens.advanceCountdowns(next, Until.DUSK)
            next = Effects.reconcile(next, lookup)
            next.copy(
                phase = Phase.NIGHT,
                cycle = state.cycle + 1,
                nightStepsDone = emptySet(),
                lastDusk = dusk ?: next.lastDusk,
            )
        }
    }
}
