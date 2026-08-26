package com.clocktower.grimoire

import com.clocktower.engine.Character
import com.clocktower.engine.GameData
import com.clocktower.grimoire.data.SavedData
import com.clocktower.grimoire.data.migratedSavedData
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** Browser-global app state: the dataset, loaded once by Main. */
object WebApp {
    lateinit var gameData: GameData

    /** Null until Main has fetched the dataset — the load migration degrades gracefully. */
    val gameDataOrNull: GameData? get() = if (::gameData.isInitialized) gameData else null
}

/** `navigator.storage.persist()` — best effort, and never throws. */
private fun requestPersistentStorageJs(): Unit =
    js(
        """{ try {
               if (navigator.storage && navigator.storage.persist) {
                 navigator.storage.persisted().then(function (already) {
                   if (already) { window.__storagePersisted = true; return; }
                   navigator.storage.persist().then(function (granted) {
                     window.__storagePersisted = !!granted;
                   });
                 });
               } else { window.__storagePersisted = true; }
             } catch (e) { window.__storagePersisted = true; } }""",
    )

private fun storagePersistedJs(): Boolean = js("(window.__storagePersisted !== false)")

/** localStorage-backed persistence with the same SavedData shape. */
object WebStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private const val KEY = "clocktower-grimoire"

    /**
     * True once a write has failed — quota exhausted, or the browser refusing
     * storage in a private window. ARCHITECTURE §5.4: this must NEVER be
     * swallowed; the shell renders a persistent banner from it.
     */
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed

    /** The message the banner shows, when [saveFailed] is true. */
    private val _saveError = MutableStateFlow("")
    val saveError: StateFlow<String> = _saveError

    /**
     * True until the browser tells us the origin's storage is evictable.
     * iOS caps a non-installed PWA at 7 days of non-use, so a false here is
     * the cue for the "Add to Home Screen" hint.
     */
    private val _storagePersisted = MutableStateFlow(true)
    val storagePersisted: StateFlow<Boolean> = _storagePersisted

    /** Called once at boot: ask the browser to keep this origin's storage. */
    fun requestPersistence() {
        requestPersistentStorageJs()
    }

    /** Re-reads the browser's answer to [requestPersistence]; cheap, poll-safe. */
    fun refreshPersistence() {
        val persisted = try {
            storagePersistedJs()
        } catch (e: Throwable) {
            true
        }
        if (_storagePersisted.value != persisted) _storagePersisted.value = persisted
    }

    fun load(): SavedData = try {
        val saved = localStorage.getItem(KEY)
            ?.let { json.decodeFromString(SavedData.serializer(), it) }
            ?: SavedData()
        // The ONE migration entry point on the web (ARCHITECTURE §5.1), plus
        // WP11's SavedData-wrapper step (schemaVersion, archived games).
        saved.migratedSavedData(lookupFor(saved))
    } catch (e: Exception) {
        SavedData()
    }

    private fun lookupFor(saved: SavedData): (String) -> Character? {
        val custom = (listOfNotNull(saved.game) + saved.archivedGames)
            .flatMap { it.script.customCharacters }
            .associateBy { it.id }
        return { id -> custom[id] ?: WebApp.gameDataOrNull?.character(id) }
    }

    /**
     * Writes the save. Returns false when the browser refused it — the caller
     * keeps playing, but the storyteller is told, loudly and permanently, that
     * nothing is being saved (ARCHITECTURE §5.4).
     */
    fun save(data: SavedData): Boolean = try {
        localStorage.setItem(KEY, json.encodeToString(SavedData.serializer(), data))
        if (_saveFailed.value) {
            _saveFailed.value = false
            _saveError.value = ""
        }
        true
    } catch (e: Throwable) {
        _saveFailed.value = true
        _saveError.value = e.message ?: "browser storage refused the write"
        false
    }

    /**
     * A last-ditch write that drops the archive first: when quota is the
     * problem, the live game matters more than ten finished ones.
     */
    fun saveShedding(data: SavedData): Boolean =
        save(data) || save(data.copy(archivedGames = emptyList()))
}
