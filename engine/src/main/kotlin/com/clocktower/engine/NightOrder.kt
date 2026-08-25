package com.clocktower.engine

import kotlinx.serialization.Serializable

/** One row of the night sheet for the current game. */
@Serializable
data class NightOrderStep(
    /** Character id, or one of [NightMarkers]. */
    val id: String,
    val title: String,
    val detail: String,
    /** Players currently holding this character (empty for markers). */
    val playerIds: List<Long> = emptyList(),
)

/**
 * Builds the per-game night sheet from the canonical global wake order.
 *
 * SUPERSEDED by [NightPlan] — WP2 deletes this file. Its row type was renamed
 * to [NightOrderStep] in WP0 so the new [NightStep] could take the name.
 */
class NightOrder(
    private val firstNightOrder: List<String>,
    private val otherNightOrder: List<String>,
) {

    fun firstNight(state: GameState, lookup: (String) -> Character?): List<NightOrderStep> =
        build(state, lookup, firstNightOrder, isFirstNight = true)

    fun otherNight(state: GameState, lookup: (String) -> Character?): List<NightOrderStep> =
        build(state, lookup, otherNightOrder, isFirstNight = false)

    private fun build(
        state: GameState,
        lookup: (String) -> Character?,
        order: List<String>,
        isFirstNight: Boolean,
    ): List<NightOrderStep> {
        val inPlay: Map<String, List<Player>> = state.players
            .filter { it.nightRoleId != null }
            .groupBy { it.nightRoleId!! }
        val fabled = state.fabledIds.toSet()
        // Minion/demon info happens in games of 7+ players (total seats,
        // not alive count — a night-1 death must not remove the steps).
        val infoSteps = state.players.count { !it.isTraveller } >= 7
        val actualMarionettes = state.players.filter { it.characterId == "marionette" }

        val steps = mutableListOf<NightOrderStep>()
        for (id in order) {
            when (id) {
                NightMarkers.DUSK -> steps += NightOrderStep(id, "Dusk", "Everyone closes their eyes. Wait for quiet.")
                NightMarkers.DAWN -> steps += NightOrderStep(id, "Dawn", "Wait a few seconds. Everyone opens their eyes. Announce who died.")
                NightMarkers.MINION_INFO -> if (isFirstNight && infoSteps) {
                    val minions = state.players.filter { p ->
                        p.characterId != "marionette" &&
                            p.characterId?.let(lookup)?.team == Team.MINION
                    }
                    val demon = state.players.filter { p ->
                        p.characterId?.let(lookup)?.team == Team.DEMON
                    }
                    steps += NightOrderStep(
                        id = id,
                        title = "Minion info",
                        detail = buildString {
                            append("Wake all Minions")
                            if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
                            append(". They see each other, then point out the Demon")
                            if (demon.isNotEmpty()) append(" (${demon.joinToString { it.name }})")
                            append(".")
                        },
                        playerIds = minions.map { it.id },
                    )
                }
                NightMarkers.DEMON_INFO -> if (isFirstNight && infoSteps) {
                    val minions = state.players.filter { p ->
                        p.characterId != "marionette" &&
                            p.characterId?.let(lookup)?.team == Team.MINION
                    }
                    val demon = state.players.filter { p ->
                        p.characterId?.let(lookup)?.team == Team.DEMON
                    }
                    val lunatics = state.players.filter { it.characterId == "lunatic" }
                    val bluffs = state.demonBluffIds.mapNotNull { lookup(it)?.name }
                    steps += NightOrderStep(
                        id = id,
                        title = "Demon info",
                        detail = buildString {
                            append("Wake the Demon")
                            if (demon.isNotEmpty()) append(" (${demon.joinToString { it.name }})")
                            append(". Point out the Minions")
                            if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
                            if (actualMarionettes.isNotEmpty()) {
                                append(". Point out the Marionette")
                                append(" (${actualMarionettes.joinToString { it.name }})")
                            }
                            append(", then show 3 not-in-play good characters as bluffs")
                            if (bluffs.isNotEmpty()) {
                                append(": ${bluffs.joinToString()}")
                            } else {
                                append(" — no bluffs chosen yet! Pick them from the menu")
                            }
                            append(".")
                            if (lunatics.isNotEmpty()) {
                                append(
                                    " Also show the Demon who the LUNATIC is " +
                                        "(${lunatics.joinToString { it.name }}) — the Demon can mirror their fake kills.",
                                )
                            }
                        },
                        playerIds = demon.map { it.id },
                    )
                }
                else -> {
                    if (id == "marionette") {
                        if (isFirstNight && !infoSteps && actualMarionettes.isNotEmpty()) {
                            val demons = state.players.filter { player ->
                                player.characterId?.let(lookup)?.team == Team.DEMON
                            }
                            steps += NightOrderStep(
                                id = id,
                                title = "Marionette info",
                                detail = buildString {
                                    append("Wake the Demon")
                                    if (demons.isNotEmpty()) {
                                        append(" (${demons.joinToString { it.name }})")
                                    }
                                    append(". Show the “This player is” and Marionette tokens, then point to")
                                    append(" ${actualMarionettes.joinToString { it.name }}.")
                                },
                                playerIds = demons.map { it.id },
                            )
                        }
                        continue
                    }
                    val character = lookup(id) ?: continue
                    val holders = inPlay[id].orEmpty()
                    val isFabledActive = fabled.contains(id)
                    if (holders.isEmpty() && !isFabledActive) continue
                    val reminder =
                        if (isFirstNight) character.firstNightReminder else character.otherNightReminder
                    var detail = reminder.ifEmpty { character.ability }
                    // The Exorcist silences a chosen Demon for the night.
                    if (character.team == Team.DEMON &&
                        holders.any { h -> h.reminders.any { it.sourceId == "exorcist" && it.label.equals("Chosen", true) } }
                    ) {
                        detail += " — EXORCIST chose them: the Demon does not act tonight."
                    }
                    // A real Demon in a Lunatic game hears what the Lunatic
                    // "did" so they can fake matching deaths.
                    if (character.team == Team.DEMON && !isFirstNight) {
                        val lunaticAttacks = state.players.filter { p ->
                            p.reminders.any { it.sourceId == "lunatic" }
                        }
                        val lunaticSeat = state.players.find { it.characterId == "lunatic" }
                        if (lunaticSeat != null) {
                            detail += if (lunaticAttacks.isNotEmpty()) {
                                " LUNATIC (${lunaticSeat.name}) chose: " +
                                    lunaticAttacks.joinToString { it.name } +
                                    " — show the Demon those choices first."
                            } else {
                                " LUNATIC (${lunaticSeat.name}) is in play — wake them first for their fake attack, " +
                                    "mark their choices, then show the Demon."
                            }
                        }
                    }
                    steps += NightOrderStep(
                        id = id,
                        title = character.name,
                        detail = detail,
                        playerIds = holders.map { it.id },
                    )
                }
            }
        }

        // Custom (homebrew) characters aren't on the canonical order lists;
        // slot the ones that act tonight in before dawn, sorted by the night
        // position their script JSON declared.
        val known = order.toSet()
        val customs = inPlay.keys
            .filter { it !in known }
            .mapNotNull { id -> lookup(id)?.let { id to it } }
            .filter { (_, c) ->
                (if (isFirstNight) c.firstNightReminder else c.otherNightReminder).isNotBlank() ||
                    (if (isFirstNight) c.firstNight else c.otherNight) > 0
            }
            .sortedBy { (_, c) -> if (isFirstNight) c.firstNight else c.otherNight }
            .map { (id, c) ->
                val reminder = if (isFirstNight) c.firstNightReminder else c.otherNightReminder
                NightOrderStep(
                    id = id,
                    title = "${c.name} (homebrew)",
                    detail = reminder.ifEmpty { c.ability },
                    playerIds = inPlay[id].orEmpty().map { it.id },
                )
            }
        if (customs.isNotEmpty()) {
            val dawnIndex = steps.indexOfFirst { it.id == NightMarkers.DAWN }
            if (dawnIndex >= 0) steps.addAll(dawnIndex, customs) else steps.addAll(customs)
        }
        return steps
    }
}
