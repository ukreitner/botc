package com.clocktower.engine

import kotlinx.serialization.Serializable

/** Where an obligation or briefing item surfaces. One enum for prompts, briefings and effects. */
@Serializable
enum class BriefingSlot { NOW, TONIGHT, DAWN, DAY_START, NOMINATION, EXECUTION, DUSK }

/** What kind of obligation a [Prompt] is. */
@Serializable
enum class PromptKind {
    ANNOUNCE, CHOOSE_PLAYER, CHOOSE_CHARACTER, PLACE_EFFECT,
    RESOLVE_KILL, RUN_FIRST_NIGHT, RUN_STEP, INFO, DECIDE,
}

/** A deferred obligation the engine created. `resolved` retires it. */
@Serializable
data class Prompt(
    val id: Long,
    val at: BriefingSlot,
    val kind: PromptKind,
    /** Character whose ability this is. */
    val sourceId: String,
    val subjectPlayerId: Long? = null,
    val targetIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    /** Imperative, storyteller voice, ready to read or act on. */
    val title: String,
    val detail: String = "",
    /** Cycle it comes due; null = the next occurrence of [at]. */
    val dueCycle: Int? = null,
    /** For `at = TONIGHT`: which night-order slot to insert the step at. */
    val stepSlotId: String = "",
    /** The DeathEvent / action that created it, so `revive` can roll it back exactly. */
    val causeEventId: Long? = null,
    val optional: Boolean = false,
    val resolved: Boolean = false,
    val resolvedCycle: Int? = null,
    /**
     * The character the seat chosen for this prompt BECOMES — the Imp's
     * star-pass heir. Blank when answering the prompt changes nobody's
     * character, which is the common case.
     *
     * It is what lets a screen render "a Minion becomes the Imp" as a picker
     * without knowing a single rule: [targetIds] are the legal answers, this is
     * what the answer does, and [Prompts.answerWithPlayer] applies it.
     */
    val becomesCharacterId: String = "",
    /** How that change is recorded in the identity log. */
    val becomesReason: ChangeReason = ChangeReason.STORYTELLER,
)

/**
 * The one deferred-obligation queue (WP1).
 *
 * Everything the storyteller still owes lives here: re-run a first night,
 * resolve a deferred kill, choose a Sweetheart victim, settle a paradox.
 * Things they must *say* are ledger ANNOUNCE entries instead (lead D6).
 */
object Prompts {

    /** Appends [prompt], stamping it with the next id. */
    fun queue(state: GameState, prompt: Prompt): GameState {
        val id = state.nextPromptId
        return state.copy(
            prompts = state.prompts + prompt.copy(id = id),
            nextPromptId = id + 1,
        )
    }

    /**
     * Answers a CHOOSE_PLAYER prompt with the seat the storyteller picked, in
     * ONE state change: the character change the prompt names (if any), the
     * ledger row that survives the token sweep, and the prompt retired.
     *
     * One update means one undo — the storyteller who mis-taps the heir of an
     * Imp star pass presses undo once, not three times.
     */
    fun answerWithPlayer(
        state: GameState,
        lookup: (String) -> Character?,
        promptId: Long,
        playerId: Long,
    ): GameState {
        val prompt = state.prompts.firstOrNull { it.id == promptId && !it.resolved } ?: return state
        var next = state
        if (prompt.becomesCharacterId.isNotBlank()) {
            next = Identity.changeCharacter(
                state = next,
                lookup = lookup,
                playerId = playerId,
                newCharacterId = prompt.becomesCharacterId,
                reason = prompt.becomesReason,
            )
        }
        next = Ledger.choice(
            state = next,
            sourceId = prompt.sourceId,
            actorId = prompt.subjectPlayerId,
            targetIds = listOf(playerId),
            characterIds = listOfNotNull(prompt.becomesCharacterId.ifBlank { null }),
            byStoryteller = true,
        )
        return resolve(next, promptId)
    }

    /** Retires a prompt as done, keeping it in the list for the log and undo. */
    fun resolve(state: GameState, id: Long): GameState = state.copy(
        prompts = state.prompts.map {
            if (it.id == id) it.copy(resolved = true, resolvedCycle = state.cycle) else it
        },
    )

    /** Drops a prompt entirely — the storyteller decided it never applied. */
    fun dismiss(state: GameState, id: Long): GameState =
        state.copy(prompts = state.prompts.filterNot { it.id == id })

    /** Unresolved prompts that have come due at [slot], oldest first. */
    fun due(state: GameState, slot: BriefingSlot): List<Prompt> = state.prompts
        .filter { !it.resolved && it.at == slot && (it.dueCycle == null || it.dueCycle <= state.cycle) }
        .sortedBy { it.id }

    /** Prompts that must become night steps tonight. Consumed by NightPlan. */
    fun forTonight(state: GameState): List<Prompt> = due(state, BriefingSlot.TONIGHT)
}
