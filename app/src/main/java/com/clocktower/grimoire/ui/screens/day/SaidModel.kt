package com.clocktower.grimoire.ui.screens.day

import com.clocktower.engine.Character
import com.clocktower.engine.GameState
import com.clocktower.engine.Ledger
import com.clocktower.engine.LedgerEntry
import com.clocktower.engine.LedgerKind
import com.clocktower.engine.Verdict

/**
 * "What was said" — the capture model behind the user's headline request,
 * *"make it easy to write down all the gossips even if Gossip isn't in play"*
 * (I3, ux/day-screen §C, friction-log).
 *
 * Everything here works with NOTHING in play: the default source is
 * [Ledger.Sources.CLAIM], the text alone is a complete entry, and the verdict
 * chips appear only for the sources where a rule will later read the verdict.
 */
data class SaidRow(
    val entryId: Long,
    val speakerId: Long?,
    val speaker: String,
    /** The whole row in one line: `Bo » "Fay is the Imp"`. */
    val line: String,
    /**
     * The words as recorded, with none of the row's framing — what the edit
     * dialog opens with, so a mistyped statement can be corrected rather than
     * left standing (C2-8).
     */
    val text: String,
    val sourceId: String,
    /** Character name for the source, or "" for a plain claim / free note. */
    val sourceName: String,
    val verdict: Verdict,
    /** Verdict chips render only where a rule needs the answer. */
    val wantsVerdict: Boolean,
    val kind: LedgerKind,
    /** ANNOUNCE rows the storyteller still owes the table. */
    val announcePending: Boolean,
)

object SaidModel {

    /** Kinds the "What was said" stage renders. `IMPAIRMENT_SPAN` is never one. */
    val KINDS: List<LedgerKind> = listOf(
        LedgerKind.STATEMENT,
        LedgerKind.PRIVATE,
        LedgerKind.ANNOUNCE,
        LedgerKind.RULING,
        LedgerKind.NOTE,
    )

    /** Every recorded line for [day], newest last. */
    fun rows(state: GameState, lookup: (String) -> Character?, day: Int): List<SaidRow> =
        state.ledger
            .filter { it.cycle == day && it.kind in KINDS }
            .map { row(state, lookup, it) }

    fun row(state: GameState, lookup: (String) -> Character?, entry: LedgerEntry): SaidRow {
        val speaker = entry.actorId?.let { state.player(it)?.name }
        val sourceName = characterName(lookup, entry.sourceId)
        return SaidRow(
            entryId = entry.id,
            speakerId = entry.actorId,
            speaker = speaker ?: storytellerLabel(entry.kind),
            line = line(speaker, sourceName, entry),
            text = entry.text.ifBlank { entry.shown },
            sourceId = entry.sourceId,
            sourceName = sourceName,
            verdict = entry.verdict,
            wantsVerdict = wantsVerdict(entry, sourceName),
            kind = entry.kind,
            announcePending = entry.announcePending,
        )
    }

    /**
     * A verdict is asked for only where a rule will read it: a statement made
     * *as* a character the app knows (a Gossip's claim, a Juggler's guess, a
     * Savant's pair). A plain claim is neither true nor false until someone
     * dies for it, and a storyteller ruling or announcement is a fact.
     */
    fun wantsVerdict(entry: LedgerEntry, sourceName: String): Boolean =
        entry.kind == LedgerKind.STATEMENT &&
            sourceName.isNotEmpty() &&
            entry.sourceId != Ledger.Sources.CLAIM

    /** `Bo » "Fay is the Imp"` / `Ana » Gossip: "Two Outsiders have died"`. */
    fun line(speaker: String?, sourceName: String, entry: LedgerEntry): String {
        val who = speaker ?: storytellerLabel(entry.kind)
        val prefix = when {
            entry.kind == LedgerKind.PRIVATE && sourceName.isNotEmpty() -> "$sourceName, privately: "
            entry.kind == LedgerKind.PRIVATE -> "privately: "
            entry.kind == LedgerKind.RULING -> "ruling: "
            entry.kind == LedgerKind.ANNOUNCE -> "announce: "
            sourceName.isNotEmpty() && entry.sourceId != Ledger.Sources.CLAIM -> "$sourceName: "
            else -> ""
        }
        val body = entry.text.ifBlank { entry.shown }
        return "$who » $prefix${quoted(body)}"
    }

    private fun quoted(text: String): String =
        if (text.isBlank()) "(nothing recorded)" else "“$text”"

    private fun storytellerLabel(kind: LedgerKind): String =
        if (kind == LedgerKind.NOTE) "Note" else "Storyteller"

    /** The character's display name for a source id, or "" for a pseudo-source. */
    fun characterName(lookup: (String) -> Character?, sourceId: String): String {
        if (sourceId.isBlank()) return ""
        if (sourceId in PSEUDO_SOURCES) return ""
        return lookup(sourceId)?.name.orEmpty()
    }

    /**
     * The zero-typing path: with a speaker picked and the field empty, "Add"
     * becomes "Claims…" and a character grid finishes the sentence in one tap.
     * This is the most common statement in every game and must never need a
     * keyboard (§C).
     */
    fun claimText(characterName: String): String = "Claims to be the $characterName"

    /** Characters offered in the "Claims…" grid: in play first, then the rest of the script. */
    fun claimCandidates(state: GameState, lookup: (String) -> Character?): List<Character> {
        val scriptIds = state.script.characterIds
        val inPlay = state.seats.mapNotNull { it.shownCharacterId ?: it.characterId }
            .map(Character::normalizeId)
            .toSet()
        val all = scriptIds.mapNotNull { lookup(it) }
        return all.sortedWith(
            compareByDescending<Character> { Character.normalizeId(it.id) in inPlay }
                .thenBy { it.team.ordinal }
                .thenBy { it.name },
        )
    }

    /**
     * The smart default source for a new statement (§C): if the engine's collect
     * list is still waiting on this speaker's own ability, record it as that;
     * otherwise a plain claim. Derived from data — never a character-id branch.
     */
    fun defaultSource(state: GameState, speakerId: Long?, collect: List<String>): String {
        val seat = speakerId?.let { state.player(it) } ?: return Ledger.Sources.CLAIM
        val held = (seat.characterId ?: return Ledger.Sources.CLAIM).let(Character::normalizeId)
        return collect.firstOrNull { Character.normalizeId(it) == held } ?: Ledger.Sources.CLAIM
    }

    /** The next living seat clockwise — what "Add & another" advances to. */
    fun nextSpeaker(state: GameState, speakerId: Long?): Long? {
        val seats = state.seats
        if (seats.isEmpty()) return null
        val start = seats.indexOfFirst { it.id == speakerId }
        val order = if (start < 0) seats else (1..seats.size).map { seats[(start + it) % seats.size] }
        return (order.firstOrNull { it.alive } ?: order.firstOrNull())?.id
    }

    /** Chip label for a source id: the character's name, or "Claim". */
    fun sourceLabel(lookup: (String) -> Character?, sourceId: String): String =
        characterName(lookup, sourceId).ifEmpty { "Claim" }

    private val PSEUDO_SOURCES = setOf(
        Ledger.Sources.CLAIM,
        Ledger.Sources.MISREGISTER,
        Ledger.Sources.MALFUNCTION,
        Ledger.Sources.NOTE,
        Ledger.Sources.STORYTELLER,
        Ledger.Sources.STATUS,
    )
}
