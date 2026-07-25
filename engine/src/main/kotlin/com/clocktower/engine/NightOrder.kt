package com.clocktower.engine

import kotlinx.serialization.Serializable

/** Special (non-character) entries in the night order. */
object NightMarkers {
    const val DUSK = "DUSK"
    const val MINION_INFO = "MINION_INFO"
    const val DEMON_INFO = "DEMON_INFO"
    const val DAWN = "DAWN"

    val all = setOf(DUSK, MINION_INFO, DEMON_INFO, DAWN)
}

/** One row of the night sheet for the current game. */
@Serializable
data class NightStep(
    /** Character id, or one of [NightMarkers]. */
    val id: String,
    val title: String,
    val detail: String,
    /** Players currently holding this character (empty for markers). */
    val playerIds: List<Long> = emptyList(),
)

/**
 * Builds the per-game night sheet from the canonical global wake order.
 */
class NightOrder(
    private val firstNightOrder: List<String>,
    private val otherNightOrder: List<String>,
) {

    fun firstNight(state: GameState, lookup: (String) -> Character?): List<NightStep> =
        build(state, lookup, firstNightOrder, isFirstNight = true)

    fun otherNight(state: GameState, lookup: (String) -> Character?): List<NightStep> =
        build(state, lookup, otherNightOrder, isFirstNight = false)

    private fun build(
        state: GameState,
        lookup: (String) -> Character?,
        order: List<String>,
        isFirstNight: Boolean,
    ): List<NightStep> {
        val inPlay: Map<String, List<Player>> = state.players
            .filter { it.characterId != null }
            .groupBy { it.characterId!! }
        val fabled = state.fabledIds.toSet()
        // Minion/demon info happens in games of 7+ players (total seats,
        // not alive count — a night-1 death must not remove the steps).
        val infoSteps = state.players.count { !it.isTraveller } >= 7

        val steps = mutableListOf<NightStep>()
        for (id in order) {
            when (id) {
                NightMarkers.DUSK -> steps += NightStep(id, "Dusk", "Everyone closes their eyes. Wait for quiet.")
                NightMarkers.DAWN -> steps += NightStep(id, "Dawn", "Wait a few seconds. Everyone opens their eyes. Announce who died.")
                NightMarkers.MINION_INFO -> if (isFirstNight && infoSteps) {
                    steps += NightStep(
                        id, "Minion info",
                        "Wake all Minions. They see each other and learn who the Demon is.",
                    )
                }
                NightMarkers.DEMON_INFO -> if (isFirstNight && infoSteps) {
                    steps += NightStep(
                        id, "Demon info",
                        "Wake the Demon. Show who the Minions are, then show 3 not-in-play good characters as bluffs.",
                    )
                }
                else -> {
                    val character = lookup(id) ?: continue
                    val holders = inPlay[id].orEmpty()
                    val isFabledActive = fabled.contains(id)
                    if (holders.isEmpty() && !isFabledActive) continue
                    val reminder =
                        if (isFirstNight) character.firstNightReminder else character.otherNightReminder
                    steps += NightStep(
                        id = id,
                        title = character.name,
                        detail = reminder.ifEmpty { character.ability },
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
                NightStep(
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
