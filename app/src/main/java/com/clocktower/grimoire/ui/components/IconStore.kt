package com.clocktower.grimoire.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Bridge to the platform image loader for bundled character art. The
 * Android side installs [load] at app start; when art is absent (or in
 * non-Android verification builds) tokens fall back to glyphs.
 */
object IconStore {
    /** Loads the icon for a normalized character id, or null if absent. */
    @Volatile
    var load: ((String) -> ImageBitmap?)? = null

    private val cache = HashMap<String, ImageBitmap?>()

    fun icon(id: String): ImageBitmap? {
        val loader = load ?: return null
        synchronized(cache) {
            return cache.getOrPut(id) { loader(id) }
        }
    }
}
