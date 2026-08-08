package com.clocktower.grimoire.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Browser icon store: art arrives asynchronously over fetch, so the map
 * is snapshot-state — tokens composed before their art loaded fill in
 * automatically when it lands.
 */
object IconStore {
    val ready = mutableStateMapOf<String, ImageBitmap>()

    /** Kept for API parity with the Android store; unused on web. */
    var load: ((String) -> ImageBitmap?)? = null

    fun icon(id: String): ImageBitmap? = ready[id]
}
