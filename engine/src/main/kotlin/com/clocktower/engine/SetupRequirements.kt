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

/** The data-driven setup checklist (WP4). Replaces `Setup.validateSetupState`. */
object SetupRequirements {

    /** Every requirement this game raises RIGHT NOW — re-checkable mid-game, not only at SETUP. */
    fun all(state: GameState, lookup: (String) -> Character?): List<SetupRequirement> = TODO("WP4")

    fun unmet(state: GameState, lookup: (String) -> Character?): List<SetupRequirement> =
        all(state, lookup).filterNot { it.satisfied(state, lookup) }

    /** Replaces `GameActions.validateSetupState`. */
    fun blockingProblems(state: GameState, lookup: (String) -> Character?): List<String> =
        unmet(state, lookup).filter { it.blocking }.map { it.problem }
}
