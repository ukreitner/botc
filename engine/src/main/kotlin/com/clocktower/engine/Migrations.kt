package com.clocktower.engine

/** Current save schema. Bump only when a migration step is added below. */
const val SCHEMA_VERSION = 2

/**
 * Folds every legacy field into its modern home. Idempotent, pure, and called
 * exactly twice in the whole app: `SavedDataSerializer.readFrom` (Android) and
 * `WebStore.load` (PWA). Never call it from a screen.
 *
 * Steps, in order (ARCHITECTURE §5.1):
 *  1. `demonBluffIds` -> `bluffSets["demon"]`
 *  2. `fabledIds` -> `fabled`
 *  3. `Player.note` -> `Player.notes`
 *  4. `Player.alignmentFlipped` -> `Player.alignment`
 *  5. tokens -> effects (WP1 turns this on; unmatched tokens stay on the seat)
 *  6. `DeathRecord` -> `DeathEvent` (ids stamped, deprecated causes mapped)
 *  7. `nightStepsDone` needs no migration — `StepKey("poisoner").token == "poisoner"`
 *  8. stamp `GameState.id`
 */
fun GameState.migrated(lookup: (String) -> Character? = { null }): GameState {
    var next = this

    // 1. demonBluffIds -> bluffSets["demon"]
    if (next.legacyDemonBluffIds.isNotEmpty()) {
        val bluffSets = if (next.bluffSets.containsKey(BluffRequirement.DEMON_KEY)) {
            next.bluffSets
        } else {
            next.bluffSets + (BluffRequirement.DEMON_KEY to next.legacyDemonBluffIds)
        }
        next = next.copy(bluffSets = bluffSets, legacyDemonBluffIds = emptyList())
    }

    // 2. fabledIds -> fabled
    if (next.legacyFabledIds.isNotEmpty()) {
        val known = next.fabled.map { it.id }.toSet()
        next = next.copy(
            fabled = next.fabled + next.legacyFabledIds
                .filterNot { it in known }
                .map { FabledEntry(id = it) },
            legacyFabledIds = emptyList(),
        )
    }

    // 3 + 4. per-seat legacy fields.
    next = next.copy(
        players = next.players.map { player ->
            var p = player
            if (p.legacyNote.isNotBlank()) {
                p = p.copy(
                    notes = p.notes + SeatNote(
                        cycle = next.cycle,
                        phase = next.phase,
                        text = p.legacyNote,
                    ),
                    legacyNote = "",
                )
            }
            if (p.legacyAlignmentFlipped) {
                val naturallyEvil = p.characterId?.let { lookup(it) }?.team?.isEvil ?: false
                p = p.copy(
                    alignment = p.alignment
                        ?: if (naturallyEvil) Alignment.GOOD else Alignment.EVIL,
                    legacyAlignmentFlipped = false,
                )
            }
            // 5. A generic token must never carry an empty source (the
            //    permanent-poison bug); WP1 projects matched tokens into effects.
            val reminders = p.reminders.map { r ->
                if (r.sourceId.isBlank()) r.copy(sourceId = STORYTELLER_SOURCE_ID) else r
            }
            if (reminders != p.reminders) p = p.copy(reminders = reminders)
            p
        },
    )

    // 6. DeathRecord -> DeathEvent: stamp ids and map the deprecated causes.
    var nextDeathId = next.nextDeathId
    val deaths = next.deaths.map { death ->
        var d = death
        if (d.id == 0L) {
            d = d.copy(id = nextDeathId)
            nextDeathId += 1
        }
        @Suppress("DEPRECATION")
        val mapped = when (d.cause) {
            DeathCause.DEMON -> DeathCause.DEMON_KILL
            DeathCause.OTHER_NIGHT_DEATH ->
                if (d.killerCharacterId.isNotBlank() || d.atNight) {
                    DeathCause.DEMON_KILL
                } else {
                    DeathCause.STORYTELLER
                }
            else -> d.cause
        }
        if (mapped != d.cause) d = d.copy(cause = mapped)
        if (d.resurrected && d.resurrectedAtCycle == null) {
            d = d.copy(resurrectedAtCycle = d.day)
        }
        d
    }
    if (deaths != next.deaths || nextDeathId != next.nextDeathId) {
        next = next.copy(deaths = deaths, nextDeathId = nextDeathId)
    }

    // 8. Stable game id for the archive list.
    if (next.id.isBlank()) {
        next = next.copy(id = "g-" + (next.updatedAt.takeIf { it > 0L } ?: next.players.size.toLong()))
    }
    return next
}

/** The `sourceId` every storyteller-placed generic token carries. Never "". */
const val STORYTELLER_SOURCE_ID = "st"

/**
 * The lookup a load-time migration should use: the save's own script first
 * (homebrew characters), then the bundled dataset when it is already loaded.
 */
fun GameState.migrationLookup(data: GameData?): (String) -> Character? = { id ->
    script.customCharacters.find { it.id == id } ?: data?.character(id)
}
