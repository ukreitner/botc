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
    /** The head-shake: "they pointed at nothing". Never runs [onResolve]. */
    val onNone: List<NightEffect> = emptyList(),
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
    /** The head-shake: "they pointed at nothing". Never runs [onResolve]. */
    val onNone: List<NightEffect> = emptyList(),
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

/** One branch of an [Options] step. */
@Serializable
data class ActionOption(
    /** Stable id, recorded in `NightInput.optionId`. */
    val id: String,
    val label: String,
    val detail: String = "",
    val effects: List<NightEffect> = emptyList(),
)

/**
 * An N-way choice: the Wizard's wish, the Al-Hadikhia's three independent
 * live/die answers. [YesNo] is the two-way case and stays as it is; this is
 * what a rule with three or more mutually exclusive outcomes needs.
 *
 * The chosen branch arrives as `NightInput.optionId`; an unrecognised id (or
 * none at all) applies [onNone], never a branch picked by position.
 */
@Serializable
data class Options(
    override val sourceId: String,
    override val prompt: String,
    val options: List<ActionOption>,
    val onNone: List<NightEffect> = emptyList(),
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

    /**
     * Alive by the RULES, not merely un-shrouded: a Zombuul on its first death
     * is stored dead and REGISTERS dead, so a Devil's Advocate may not pick it
     * even though the seat is still in the game.
     */
    NOT_REGISTERS_DEAD,

    /**
     * "…of a different character TYPE to last night" — the Balloonist. Reads the
     * ledger, so it survives the token sweep exactly as
     * [DIFFERENT_FROM_LAST_NIGHT] does.
     */
    DIFFERENT_TYPE_FROM_LAST_NIGHT,
}

/**
 * A yes/no question about one seat, asked at the moment an effect is applied.
 *
 * Deliberately a closed, serializable vocabulary rather than a lambda: a
 * [NightEffect] is data, and `NightStep` is `@Serializable`. Anything outside
 * this vocabulary belongs in the registry lambda that BUILDS the effect list —
 * a rule holding `NightContext` can already see the whole grimoire.
 */
@Serializable
enum class SeatPredicate {
    IS_TOWNSFOLK, IS_OUTSIDER, IS_MINION, IS_DEMON,
    IS_GOOD, IS_EVIL,
    IS_ALIVE, IS_DEAD,

    /** Registration, not the true team: a Recluse ruled evil answers yes (lead D32). */
    REGISTERS_MINION, REGISTERS_DEMON, REGISTERS_EVIL,

    /** The seat's own ability works right now. */
    HAS_ABILITY,

    /** The seat is drunk, poisoned or has no ability. */
    IS_IMPAIRED,

    /**
     * The seat has been drunk, poisoned or ability-less at ANY moment tonight —
     * `GameState.nightImpaired` (lead D72).
     *
     * "…if they ARE OR BECOME drunk or poisoned tonight" is a high-water mark,
     * not a point-in-time query: a Courtier's target who sobered up by the time
     * the step resolves still answers yes.
     */
    WAS_IMPAIRED_TONIGHT,

    /** This seat is the one holding the step (Barber's self-swap whitelist). */
    IS_SOURCE,
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
        /**
         * Character payload: the Cerenovus's mad character, the Pixie's believed
         * one, the team a Faux Paw registers as. `Effects.place` has always
         * carried it; before wave 7 no night effect could supply it, so a
         * Cerenovus's Mad token lost the character it was mad about.
         */
        val characterId: String? = null,
        /** Seat payload: the Harpy's 2nd, the Grandmother's grandchild. */
        val linkedPlayerId: Ref? = null,
        /** The token outlives the source seat's ability (Sweetheart, Puzzlemaster). */
        val endsWithSource: Boolean? = null,
        /** Storyteller-visible explanation, shown on tap. */
        val note: String = "",
        /**
         * `DEMON_CANNOT_KILL` only: how far the suppression reaches (lead D68).
         * The Exorcist SILENCES and a deferred kill still lands; the
         * Lycanthrope, the Princess and the Toymaker's final night stop it.
         */
        val suppression: KillSuppression = KillSuppression.SILENCED,
    ) : NightEffect

    @Serializable
    data class RemoveToken(val sourceId: String, val label: String, val from: Ref) : NightEffect

    @Serializable
    data class Attack(
        val on: Ref,
        val cause: DeathCause = DeathCause.DEMON_KILL,
        /** false => unstoppable (the Pukka's poisoning itself, Fabled effects). */
        val respectProtection: Boolean = true,
        /**
         * This death resolves an attack made on an EARLIER night (the Pukka's
         * standing victim), so tonight's "the Demon cannot kill" suppression does
         * not reach it: *"the Pukka does not wake to attack tonight, but a player
         * still dies because of the Pukka's attack during the previous night"*.
         * Everything else — protection, triggers, attribution — is unchanged.
         */
        val deferred: Boolean = false,
    ) : NightEffect

    @Serializable
    data class Resurrect(val on: Ref) : NightEffect

    /**
     * One seat becomes a different character.
     *
     * An empty [characterId] means "the character the storyteller picked on this
     * step" ([NightInput.characterIds]). It NEVER means "no character": with no
     * pick to fall back on the effect does nothing rather than wiping the seat.
     */
    @Serializable
    data class BecomeCharacter(
        val on: Ref,
        val characterId: String,
        /**
         * Null = keep the seat's current alignment (lead D67). This is the
         * default for every rule whose text does not say otherwise: a Pit-Hag's
         * victim, a Cacklejack's, an Engineer's rebuild, a Hatter's swap. Set it
         * only where the character's own text names the new side — a Kazali's
         * created Minion, a Fang Gu's jump, a Riot conversion.
         */
        val evil: Boolean? = null,
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

    /**
     * Changes which side a seat plays for, and nothing else — the Ogre's copy,
     * the Mezepheles's word, a Cult Leader joining their neighbour.
     *
     * NOT a character change: writing one through [BecomeCharacter] appended a
     * bogus `IdentityRecord` ("new character") and re-ran the seat's first
     * night. This writes `Player.alignment` and one ledger RULING.
     */
    @Serializable
    data class SetAlignment(val on: Ref, val evil: Boolean, val note: String = "") : NightEffect

    /**
     * Gives a seat a second (or replacement) ability — the Philosopher's chosen
     * good character, the Apprentice's, the Plague Doctor's gift to the
     * storyteller.
     *
     * An empty [abilityId] means "the character picked on this step", exactly as
     * for [BecomeCharacter]; with no pick to fall back on the effect is inert.
     * [on] = null makes it a `GameState.floatingGrant` instead of a seat grant.
     */
    @Serializable
    data class GrantAbility(
        val abilityId: String,
        val sourceId: String,
        val on: Ref? = Ref.Target,
        val mode: GrantMode = GrantMode.ADD,
        /** Night-order slot to wake at; null = the granted ability's own slot. */
        val slotId: String? = null,
        val worksWhileImpaired: Boolean = false,
        /** Only when [on] is null: who ends up exercising the floating grant. */
        val floatingHolder: GrantHolder = GrantHolder.STORYTELLER,
    ) : NightEffect

    /**
     * Applies [then] to the seats [on] addresses that answer [predicate] yes, and
     * [otherwise] to the rest.
     *
     * This is the one thing the WP7 registry agents asked for most: the Snake
     * Charmer only acts on a Demon, the Fang Gu jumps instead of killing an
     * Outsider, the Vigormortis preserves a MINION and poisons a neighbour, the
     * Professor only resurrects a Townsfolk. Every one of those was a prompt
     * that changed nothing.
     */
    @Serializable
    data class When(
        val predicate: SeatPredicate,
        val on: Ref = Ref.Target,
        val then: List<NightEffect> = emptyList(),
        val otherwise: List<NightEffect> = emptyList(),
    ) : NightEffect

    /**
     * Marks a ledger entry resolved — a Gossip statement the night step has just
     * acted on, a Moonchild's public choice. Without it a consumed record is
     * offered again the next night.
     */
    @Serializable
    data class MarkConsumed(val ledgerId: Long) : NightEffect

    /**
     * Writes one [GameState.counters] entry (lead D72).
     *
     * A tally the night spends has to be zeroed by the step that spends it —
     * the Yaggababble's "for each time you said it publicly TODAY". The key is
     * supplied by the registry row, so no character id ever appears here.
     */
    @Serializable
    data class SetCounter(val key: String, val value: Int = 0) : NightEffect
}
