@file:Suppress("unused", "PackageDirectoryMismatch")

package com.clocktower.grimoire

import android.app.Application
import androidx.datastore.core.DataStore
import com.clocktower.engine.GameData
import com.clocktower.grimoire.data.SavedData
import com.clocktower.grimoire.data.createDataStore

// JVM stand-in for the real GrimoireApp (which is excluded from this build
// because it installs the Android-only asset icon loader).
class GrimoireApp : Application() {
    val gameData: GameData by lazy { GameData.loadDefault() }
    val dataStore: DataStore<SavedData> by lazy { createDataStore(this) }
}
