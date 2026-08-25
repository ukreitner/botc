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
)

/** The one deferred-obligation queue (WP1). */
object Prompts {

    fun queue(state: GameState, prompt: Prompt): GameState = TODO("WP1")

    fun resolve(state: GameState, id: Long): GameState = TODO("WP1")

    fun dismiss(state: GameState, id: Long): GameState = TODO("WP1")

    fun due(state: GameState, slot: BriefingSlot): List<Prompt> = TODO("WP1")

    /** Prompts that must become night steps tonight. Consumed by NightPlan. */
    fun forTonight(state: GameState): List<Prompt> = TODO("WP1")
}
