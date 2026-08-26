package com.clocktower.grimoire.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clocktower.engine.Character
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.NotesActions
import com.clocktower.engine.NotesState
import com.clocktower.engine.Script
import com.clocktower.engine.ScriptLink
import com.clocktower.engine.ScriptParser
import com.clocktower.grimoire.GrimoireApp
import com.clocktower.grimoire.data.Roster
import com.clocktower.grimoire.data.archiving
import com.clocktower.grimoire.data.discardingArchived
import com.clocktower.grimoire.data.remembering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for the running game. All mutations flow through
 * [update], which snapshots history for undo and persists asynchronously.
 */
class GameViewModel(application: Application) :
    AndroidViewModel(application),
    GameActionsApi {

    private val app = application as GrimoireApp
    override val gameData: GameData get() = app.gameData

    private val _game = MutableStateFlow<GameState?>(null)
    val game: StateFlow<GameState?> = _game

    private val undoStack = ArrayDeque<GameState>()
    private val redoStack = ArrayDeque<GameState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    val importedScripts: StateFlow<List<Script>> = app.dataStore.data
        .map { it.importedScripts }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Past games, newest first — WP11 archives instead of destroying. */
    val archivedGames: StateFlow<List<GameState>> = app.dataStore.data
        .map { it.archivedGames }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Remembered tables, newest first — WP11's roster memory. */
    val recentRosters: StateFlow<List<Roster>> = app.dataStore.data
        .map { it.recentRosters }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the initial DataStore read completes (splash gate). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    // ---- Player-notes session (independent of the storyteller game) -----

    private val _notes = MutableStateFlow<NotesState?>(null)
    val notes: StateFlow<NotesState?> = _notes

    private val notesUndoStack = ArrayDeque<NotesState>()
    private val notesRedoStack = ArrayDeque<NotesState>()
    private val _canUndoNotes = MutableStateFlow(false)
    val canUndoNotes: StateFlow<Boolean> = _canUndoNotes
    private val _canRedoNotes = MutableStateFlow(false)
    val canRedoNotes: StateFlow<Boolean> = _canRedoNotes

    init {
        viewModelScope.launch {
            // Adopt the persisted game once at startup; afterwards this
            // ViewModel is the source of truth and only writes back.
            val saved = app.dataStore.data.first()
            if (_game.value == null && saved.game != null) {
                _game.value = saved.game
            }
            if (_notes.value == null && saved.notes != null) {
                _notes.value = saved.notes
            }
            _ready.value = true
        }
    }

    // ---- Game lifecycle -------------------------------------------------

    /**
     * WP11: the game in progress is ARCHIVED, never destroyed, and the roster
     * is remembered. One `updateData` call does all three so the archive can
     * never race the first save of the new game.
     */
    fun startGame(script: Script, playerNames: List<String>) {
        undoStack.clear()
        redoStack.clear()
        val previous = _game.value
        // Stamp the game id here as well as at load (Migrations step 8), so the
        // archive can tell two games apart before the first reload.
        val now = System.currentTimeMillis()
        val fresh = GameActions.newGame(script, playerNames)
            .copy(id = "g" + now.toString(36), updatedAt = now)
        _game.value = fresh
        _canUndo.value = false
        _canRedo.value = false
        viewModelScope.launch {
            app.dataStore.updateData {
                it.archiving(previous).remembering(playerNames).copy(game = fresh)
            }
        }
    }

    /** WP11: ending a game files it in the archive instead of deleting it. */
    fun endGame() {
        undoStack.clear()
        redoStack.clear()
        val finished = _game.value
        _game.value = null
        _canUndo.value = false
        _canRedo.value = false
        viewModelScope.launch {
            app.dataStore.updateData { it.archiving(finished).copy(game = null) }
        }
    }

    /** Re-opens an archived game as the live one, archiving whatever is current. */
    fun resumeArchived(archived: GameState) {
        undoStack.clear()
        redoStack.clear()
        val previous = _game.value
        _game.value = archived
        _canUndo.value = false
        _canRedo.value = false
        viewModelScope.launch {
            app.dataStore.updateData {
                it.archiving(previous).discardingArchived(archived).copy(game = archived)
            }
        }
    }

    fun discardArchived(archived: GameState) {
        viewModelScope.launch {
            app.dataStore.updateData { it.discardingArchived(archived) }
        }
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
        val stamped = state.copy(updatedAt = System.currentTimeMillis())
        _game.value = stamped
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        viewModelScope.launch {
            app.dataStore.updateData { it.copy(game = stamped) }
        }
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
        viewModelScope.launch {
            app.dataStore.updateData { it.copy(notes = null) }
        }
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
        val stamped = state.copy(updatedAt = System.currentTimeMillis())
        _notes.value = stamped
        _canUndoNotes.value = notesUndoStack.isNotEmpty()
        _canRedoNotes.value = notesRedoStack.isNotEmpty()
        viewModelScope.launch {
            app.dataStore.updateData { it.copy(notes = stamped) }
        }
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
            viewModelScope.launch {
                app.dataStore.updateData { saved ->
                    val others = saved.importedScripts.filterNot { it.id == script.id }
                    saved.copy(importedScripts = others + script)
                }
            }
            null
        }
    } catch (e: Exception) {
        "Couldn't import: ${e.message}"
    }

    fun deleteScript(scriptId: String) {
        viewModelScope.launch {
            app.dataStore.updateData { saved ->
                saved.copy(importedScripts = saved.importedScripts.filterNot { it.id == scriptId })
            }
        }
    }

    override fun characterById(id: String?): Character? = id?.let { charId ->
        _game.value?.script?.customCharacters?.find { it.id == charId }
            ?: gameData.character(charId)
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}
