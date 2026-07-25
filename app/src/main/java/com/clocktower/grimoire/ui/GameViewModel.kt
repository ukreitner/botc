package com.clocktower.grimoire.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clocktower.engine.Character
import com.clocktower.engine.DeathCause
import com.clocktower.engine.GameActions
import com.clocktower.engine.GameData
import com.clocktower.engine.GameState
import com.clocktower.engine.Nomination
import com.clocktower.engine.PlacedReminder
import com.clocktower.engine.Script
import com.clocktower.engine.ScriptParser
import com.clocktower.grimoire.GrimoireApp
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
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GrimoireApp
    val gameData: GameData get() = app.gameData

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

    /** False until the initial DataStore read completes (splash gate). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    init {
        viewModelScope.launch {
            // Adopt the persisted game once at startup; afterwards this
            // ViewModel is the source of truth and only writes back.
            val saved = app.dataStore.data.first().game
            if (_game.value == null && saved != null) {
                _game.value = saved
            }
            _ready.value = true
        }
    }

    // ---- Game lifecycle -------------------------------------------------

    fun startGame(script: Script, playerNames: List<String>) {
        undoStack.clear()
        redoStack.clear()
        setGame(GameActions.newGame(script, playerNames))
    }

    fun endGame() {
        undoStack.clear()
        redoStack.clear()
        _game.value = null
        _canUndo.value = false
        _canRedo.value = false
        viewModelScope.launch {
            app.dataStore.updateData { it.copy(game = null) }
        }
    }

    /** Applies a transition, recording history and persisting. */
    fun update(transform: (GameState) -> GameState) {
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

    // ---- Convenience wrappers over GameActions --------------------------

    fun addSeat(name: String) = update { GameActions.addSeat(it, name) }
    fun removeSeat(playerId: Long) = update { GameActions.removeSeat(it, playerId) }
    fun rename(playerId: Long, name: String) = update { GameActions.rename(it, playerId, name) }
    fun assign(playerId: Long, characterId: String?, isTraveller: Boolean = false) =
        update { GameActions.assignCharacter(it, playerId, characterId, isTraveller) }

    fun setShownCharacter(playerId: Long, characterId: String?) =
        update { GameActions.setShownCharacter(it, playerId, characterId) }

    fun flipAlignment(playerId: Long) = update { GameActions.flipAlignment(it, playerId) }
    fun setNote(playerId: Long, note: String) = update { GameActions.setNote(it, playerId, note) }
    fun kill(playerId: Long, cause: DeathCause) =
        update { GameActions.kill(it, playerId, cause, ::characterById) }
    fun revive(playerId: Long) = update { GameActions.revive(it, playerId) }
    fun toggleGhostVote(playerId: Long) = update { GameActions.toggleGhostVote(it, playerId) }
    fun addReminder(playerId: Long, reminder: PlacedReminder) =
        update { GameActions.addReminder(it, playerId, reminder) }

    fun removeReminder(playerId: Long, index: Int) =
        update { GameActions.removeReminder(it, playerId, index) }

    fun setBluffs(ids: List<String>) = update { GameActions.setBluffs(it, ids) }
    fun setFabled(ids: List<String>) = update { GameActions.setFabled(it, ids) }
    fun advancePhase() = update { GameActions.advancePhase(it) }
    fun toggleNightStep(stepId: String) = update { GameActions.toggleNightStep(it, stepId) }
    fun recordNomination(nomination: Nomination) = update { GameActions.recordNomination(it, nomination) }
    fun setStorytellerNotes(notes: String) = update { it.copy(storytellerNotes = notes) }

    // ---- Scripts --------------------------------------------------------

    /** Parses and stores a script from pasted/loaded JSON. Returns error text or null. */
    fun importScript(jsonText: String): String? = try {
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
        "Couldn't parse script JSON: ${e.message}"
    }

    fun deleteScript(scriptId: String) {
        viewModelScope.launch {
            app.dataStore.updateData { saved ->
                saved.copy(importedScripts = saved.importedScripts.filterNot { it.id == scriptId })
            }
        }
    }

    fun characterById(id: String?): Character? = id?.let { charId ->
        _game.value?.script?.customCharacters?.find { it.id == charId }
            ?: gameData.character(charId)
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}
