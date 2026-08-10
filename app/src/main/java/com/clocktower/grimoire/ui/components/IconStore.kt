package com.clocktower.grimoire.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Bridge to the platform image loaders for character art. Bundled art
 * comes through [load] (installed at app start); homebrew characters
 * with external image URLs load asynchronously through [remoteLoader]
 * into snapshot state, so tokens fill in when the download lands.
 * Absent art falls back to the monogram token.
 */
object IconStore {
    /** Loads bundled art for a normalized character id, or null if absent. */
    @Volatile
    var load: ((String) -> ImageBitmap?)? = null

    /** Fetches an external URL and calls back with the decoded bitmap. */
    @Volatile
    var remoteLoader: ((id: String, url: String, done: (ImageBitmap?) -> Unit) -> Unit)? = null

    private val cache = HashMap<String, ImageBitmap?>()

    /** Remote results — snapshot state so tokens recompose on arrival. */
    val ready = mutableStateMapOf<String, ImageBitmap>()

    private val requested = HashSet<String>()

    fun icon(id: String): ImageBitmap? {
        ready[id]?.let { return it }
        val loader = load ?: return null
        synchronized(cache) {
            return cache.getOrPut(id) { loader(id) }
        }
    }

    /** Starts (once) an async load of [url] as the art for [id]. */
    fun request(id: String, url: String) {
        if (url.isBlank()) return
        synchronized(requested) {
            if (!requested.add(id)) return
        }
        remoteLoader?.invoke(id, url) { bitmap ->
            if (bitmap != null) ready[id] = bitmap
        }
    }
}
