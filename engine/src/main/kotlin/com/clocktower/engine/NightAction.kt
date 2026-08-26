package com.clocktower.engine

import kotlinx.serialization.Serializable

/** What the storyteller is asked on one night step. Declared by the registry (WP2). */
@Serializable
sealed interface NightAction {
    val sourceId: String

    /** Storyteller-voice imperative: "WHO DID HAL CHOOSE?" */
    val prompt: String
}

@Serializable
data class ChoosePlayers(
    override val sourceId: String,
    override val prompt: String,
    val min: Int,
    val max: Int,
    val constraints: List<TargetConstraint> = emptyList(),
    val sort: TargetSort = TargetSort.SEAT_ORDER,
    val allowNone: Boolean = false,
    val noneLabel: String = "They chose nobody",
    /** Applied per target, IN PICK ORDER, re-deriving state between each. */
    val perTarget: List<NightEffect> = emptyList(),
    val onResolve: List<NightEffect> = emptyList(),
    val onNone: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ChooseCharacter(
    override val sourceId: String,
    override val prompt: String,
    val pool: CharacterPool,
    val allowNone: Boolean = true,
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

/** Pit-Hag, Summoner, Cerenovus, Engineer, Kazali. */
@Serializable
data class ChoosePlayerAndCharacter(
    override val sourceId: String,
    override val prompt: String,
    val playerConstraints: List<TargetConstraint> = emptyList(),
    val pool: CharacterPool,
    val requireNotInPlay: Boolean = false,
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

/** Organ Grinder, Po head-shake, Professor pass. */
@Serializable
data class YesNo(
    override val sourceId: String,
    override val prompt: String,
    val yesLabel: String,
    val noLabel: String,
    val onYes: List<NightEffect> = emptyList(),
    val onNo: List<NightEffect> = emptyList(),
) : NightAction

/** Pure info steps: delegates to InfoCalc. */
@Serializable
data class ShowInfo(
    override val sourceId: String,
    override val prompt: String,
    val targetsNeeded: Int = 0,
    val constraints: List<TargetConstraint> = emptyList(),
) : NightAction

/** Al-Hadikhia: 3 picks, then live/die per pick. */
@Serializable
data class Sequence(
    override val sourceId: String,
    override val prompt: String,
    val stages: List<NightAction>,
) : NightAction

/** Who may be picked. */
@Serializable
enum class TargetConstraint {
    ALIVE, DEAD, ANY_LIVING_STATE,
    NOT_SELF, SELF_ALLOWED,
    NOT_TRAVELLER, TOWNSFOLK, OUTSIDER, MINION, DEMON, NOT_DEMON, GOOD, EVIL,

    /** Reads `Memory.forbiddenTargets` — survives token expiry. */
    DIFFERENT_FROM_LAST_NIGHT,

    /** Reads `Memory.everChosen` — "cannot learn the same evil player twice". */
    NOT_CHOSEN_BEFORE,
    NEIGHBOUR_OF_SOURCE,
}

@Serializable
enum class TargetSort {
    SEAT_ORDER, ALIVE_FIRST, DEAD_FIRST, DEMON_FIRST, MINION_FIRST, OUTSIDER_FIRST, TOWNSFOLK_FIRST
}

@Serializable
enum class CharacterPool { SCRIPT, GOOD, EVIL, TOWNSFOLK, OUTSIDER, MINION, DEMON, NOT_IN_PLAY }

/** Where an effect lands. [TargetN] addresses one specific pick (lead D33). */
@Serializable
sealed interface Ref {
    @Serializable data object Source : Ref

    @Serializable data object Target : Ref

    @Serializable data object AllTargets : Ref

    /**
     * The seat this ability chose on its previous wake, read from the ledger —
     * the Pukka's standing victim, the Shabaloth's regurgitation candidates.
     */
    @Serializable data object PreviousTarget : Ref

    @Serializable data class TargetN(val index: Int) : Ref

    @Serializable data object TownsfolkNeighbourOfTarget : Ref

    /**
     * One concrete seat, resolved by the registry lambda that built the effect
     * (WP2 addition). A rule holding `NightContext` can already see the whole
     * grimoire, so "the seat carrying my token" needs no new sealed member.
     */
    @Serializable data class Seat(val playerId: Long) : Ref
}

/** What a night action does once it resolves. Interpreted by `NightPlan.resolve`. */
@Serializable
sealed interface NightEffect {

    @Serializable
    data class PlaceToken(
        val sourceId: String,
        val label: String,
        val on: Ref,
        val kind: EffectKind? = null,
        val until: Until = Until.FOREVER,
    ) : NightEffect

    @Serializable
    data class RemoveToken(val sourceId: String, val label: String, val from: Ref) : NightEffect

    @Serializable
    data class Attack(
        val on: Ref,
        val cause: DeathCause = DeathCause.DEMON_KILL,
        /** false => unstoppable (the Pukka's poisoning itself, Fabled effects). */
        val respectProtection: Boolean = true,
    ) : NightEffect

    @Serializable
    data class Resurrect(val on: Ref) : NightEffect

    @Serializable
    data class BecomeCharacter(
        val on: Ref,
        val characterId: String,
        val evil: Boolean,
        val reason: ChangeReason,
    ) : NightEffect

    /** The Barber's swap (lead D33). */
    @Serializable
    data class SwapCharacters(val a: Ref, val b: Ref) : NightEffect

    @Serializable
    data class MarkSpent(val sourceId: String) : NightEffect

    @Serializable
    data class RecordChoice(val slot: String = "target") : NightEffect

    @Serializable
    data class QueuePrompt(
        val at: BriefingSlot,
        val kind: PromptKind,
        val sourceId: String,
        val title: String,
        val on: Ref? = null,
        val stepSlotId: String = "",
    ) : NightEffect

    @Serializable
    data class Announce(val at: BriefingSlot, val text: String) : NightEffect

    @Serializable
    data class NoteMalfunction(val on: Ref, val reason: String) : NightEffect

    @Serializable
    data class ShowCardTo(val on: Ref, val card: String) : NightEffect
}
