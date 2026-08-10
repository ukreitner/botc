package com.clocktower.grimoire.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Browser icon store: bundled art arrives asynchronously over fetch and
 * homebrew art over [remoteLoader] — both land in snapshot state, so
 * tokens composed before their art loaded fill in automatically.
 */
object IconStore {
    val ready = mutableStateMapOf<String, ImageBitmap>()

    /** Kept for API parity with the Android store; unused on web. */
    var load: ((String) -> ImageBitmap?)? = null

    /** Installed by Main: fetches a URL and decodes it via skia. */
    var remoteLoader: ((id: String, url: String, done: (ImageBitmap?) -> Unit) -> Unit)? = null

    private val requested = HashSet<String>()

    fun icon(id: String): ImageBitmap? = ready[id]

    /** Starts (once) an async load of [url] as the art for [id]. */
    fun request(id: String, url: String) {
        if (url.isBlank() || !requested.add(id)) return
        remoteLoader?.invoke(id, url) { bitmap ->
            if (bitmap != null) ready[id] = bitmap
        }
    }
}
