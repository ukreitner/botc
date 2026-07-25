package com.clocktower.grimoire

import android.app.Application
import androidx.datastore.core.DataStore
import com.clocktower.engine.GameData
import com.clocktower.grimoire.data.SavedData
import com.clocktower.grimoire.data.createDataStore

class GrimoireApp : Application() {

    /** Bundled character/jinx/night-order dataset, loaded once. */
    val gameData: GameData by lazy { GameData.loadDefault() }

    val dataStore: DataStore<SavedData> by lazy { createDataStore(this) }
}
