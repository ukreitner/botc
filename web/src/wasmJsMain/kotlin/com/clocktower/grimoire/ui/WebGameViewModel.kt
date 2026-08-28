package com.clocktower.grimoire.ui

import com.clocktower.engine.Character
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.NotesActions
import com.clocktower.engine.NotesState
import com.clocktower.engine.Script
import com.clocktower.engine.ScriptLink
import com.clocktower.engine.ScriptParser
import com.clocktower.engine.Time
import com.clocktower.grimoire.WebApp
import com.clocktower.grimoire.WebStore
import com.clocktower.grimoire.data.archiving
import com.clocktower.grimoire.data.discardingArchived
import com.clocktower.grimoire.data.remembering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Browser version of the app's single source of truth. Same public API
 * as the Android GameViewModel (which this file replaces in the web
 * build); persistence is synchronous localStorage.
 */
class GameViewModel : GameActionsApi {

    override val gameData: GameData get() = WebApp.gameData

    private var saved = WebStore.load()

    private val _game = MutableStateFlow(saved.game)
    val game: StateFlow<GameState?> = _game

    private val undoStack = ArrayDeque<GameState>()
    private val redoStack = ArrayDeque<GameState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    private val _importedScripts = MutableStateFlow(saved.importedScripts)
    val importedScripts: StateFlow<List<Script>> = _importedScripts

    /** Past games, newest first — WP11 archives instead of destroying. */
    private val _archivedGames = MutableStateFlow(saved.archivedGames)
    val archivedGames: StateFlow<List<GameState>> = _archivedGames

    /** Remembered tables, newest first — WP11's roster memory. */
    private val _recentRosters = MutableStateFlow(saved.recentRosters)
    val recentRosters: StateFlow<List<com.clocktower.grimoire.data.Roster>> = _recentRosters

    /** Data is loaded before the UI starts on web. */
    val ready: StateFlow<Boolean> = MutableStateFlow(true)

    // ---- Player-notes session (independent of the storyteller game) -----

    private val _notes = MutableStateFlow(saved.notes)
    val notes: StateFlow<NotesState?> = _notes

    private val notesUndoStack = ArrayDeque<NotesState>()
    private val notesRedoStack = ArrayDeque<NotesState>()
    private val _canUndoNotes = MutableStateFlow(false)
    val canUndoNotes: StateFlow<Boolean> = _canUndoNotes
    private val _canRedoNotes = MutableStateFlow(false)
    val canRedoNotes: StateFlow<Boolean> = _canRedoNotes

    private fun persist(mutate: (com.clocktower.grimoire.data.SavedData) -> com.clocktower.grimoire.data.SavedData) {
        saved = mutate(saved)
        // Quota failures raise WebStore.saveFailed, which the shell renders as
        // a persistent banner — never swallowed (ARCHITECTURE §5.4). A write
        // that only fitted after shedding the archive returns what it actually
        // wrote, so the app never lists games that are no longer on disk.
        WebStore.saveShedding(saved)?.let { saved = it }
        _archivedGames.value = saved.archivedGames
        _recentRosters.value = saved.recentRosters
    }

    // ---- Game lifecycle -------------------------------------------------

    /** WP11: the game in progress is ARCHIVED, never destroyed. */
    fun startGame(script: Script, playerNames: List<String>) {
        undoStack.clear()
        redoStack.clear()
        val previous = _game.value
        // Stamp the game id here as well as at load (Migrations step 8), so the
        // archive can tell two games apart before the first reload.
        val now = Time.epochMillis()
        val fresh = newGame(script, playerNames)
            .copy(id = "g" + now.toString(36), updatedAt = now)
        _game.value = fresh
        _canUndo.value = false
        _canRedo.value = false
        persist { it.archiving(previous).remembering(playerNames).copy(game = fresh) }
    }

    /** WP11: ending a game files it in the archive instead of deleting it. */
    fun endGame() {
        undoStack.clear()
        redoStack.clear()
        val finished = _game.value
        _game.value = null
        _canUndo.value = false
        _canRedo.value = false
        persist { it.archiving(finished).copy(game = null) }
    }

    /** Re-opens an archived game as the live one, archiving whatever is current. */
    fun resumeArchived(archived: GameState) {
        undoStack.clear()
        redoStack.clear()
        val previous = _game.value
        _game.value = archived
        _canUndo.value = false
        _canRedo.value = false
        persist { it.archiving(previous).discardingArchived(archived).copy(game = archived) }
    }

    fun discardArchived(archived: GameState) {
        persist { it.discardingArchived(archived) }
    }

    /** Applies a transition, recording history and persisting. */
    override fun update(transform: (GameState) -> GameState) {
        val current = _game.value ?: return
        val next = transform(current)
        if (next == current) return
        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        setGame(next)
    }

    fun undo() {
        val current = _game.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        setGame(previous)
    }

    fun redo() {
        val current = _game.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        setGame(next)
    }

    private fun setGame(state: GameState) {
        val stamped = state.copy(updatedAt = Time.epochMillis())
        _game.value = stamped
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        persist { it.copy(game = stamped) }
    }

    // ---- Player-notes lifecycle ----------------------------------------

    fun startNotes(script: Script, seatNames: List<String>) {
        notesUndoStack.clear()
        notesRedoStack.clear()
        setNotes(NotesActions.newNotes(script, seatNames))
    }

    fun endNotes() {
        notesUndoStack.clear()
        notesRedoStack.clear()
        _notes.value = null
        _canUndoNotes.value = false
        _canRedoNotes.value = false
        persist { it.copy(notes = null) }
    }

    fun updateNotes(transform: (NotesState) -> NotesState) {
        val current = _notes.value ?: return
        val next = transform(current)
        if (next == current) return
        notesUndoStack.addLast(current)
        if (notesUndoStack.size > MAX_HISTORY) notesUndoStack.removeFirst()
        notesRedoStack.clear()
        setNotes(next)
    }

    fun undoNotes() {
        val current = _notes.value ?: return
        val previous = notesUndoStack.removeLastOrNull() ?: return
        notesRedoStack.addLast(current)
        setNotes(previous)
    }

    fun redoNotes() {
        val current = _notes.value ?: return
        val next = notesRedoStack.removeLastOrNull() ?: return
        notesUndoStack.addLast(current)
        setNotes(next)
    }

    private fun setNotes(state: NotesState) {
        val stamped = state.copy(updatedAt = Time.epochMillis())
        _notes.value = stamped
        _canUndoNotes.value = notesUndoStack.isNotEmpty()
        _canRedoNotes.value = notesRedoStack.isNotEmpty()
        persist { it.copy(notes = stamped) }
    }

    /** Character lookup for notes mode (game may not exist). */
    fun notesCharacterById(id: String?): Character? = id?.let { charId ->
        _notes.value?.script?.customCharacters?.find { it.id == charId }
            ?: gameData.character(charId)
    }

    // ---- Engine verbs ---------------------------------------------------
    // Every wrapper is a default method on GameActionsApi (ARCHITECTURE §3.3).
    // Never add one here: add it to that interface's per-WP block instead.

    // ---- Scripts --------------------------------------------------------

    /** Parses and stores a script from pasted JSON or a script-tool share link. Returns error text or null. */
    fun importScript(text: String): String? = try {
        val jsonText = if (ScriptLink.isLink(text)) {
            ScriptLink.decode(text)
                ?: throw IllegalArgumentException("that looks like a link, but it has no readable ?script=… payload")
        } else {
            text
        }
        val script = ScriptParser.parse(jsonText)
        if (script.characterIds.isEmpty()) {
            "No characters found in that JSON."
        } else {
            persist { data ->
                val others = data.importedScripts.filterNot { it.id == script.id }
                data.copy(importedScripts = others + script)
            }
            _importedScripts.value = saved.importedScripts
            null
        }
    } catch (e: Exception) {
        "Couldn't import: ${e.message}"
    }

    fun deleteScript(scriptId: String) {
        persist { data ->
            data.copy(importedScripts = data.importedScripts.filterNot { it.id == scriptId })
        }
        _importedScripts.value = saved.importedScripts
    }

    override fun characterById(id: String?): Character? = id?.let { charId ->
        _game.value?.script?.customCharacters?.find { it.id == charId }
            ?: gameData.character(charId)
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}
