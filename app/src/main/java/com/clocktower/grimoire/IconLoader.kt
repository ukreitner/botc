package com.clocktower.grimoire

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.clocktower.grimoire.ui.components.IconStore

/**
 * Installs the asset-backed character icon loader. Icons live in
 * assets/icons/<id>.png|webp, fetched at build time by tools/fetch-icons.sh;
 * original homebrew art is committed under assets/homebrew/salt-and-lantern.
 * absent files simply mean the token shows its glyph instead.
 *
 * This file is Android-only and excluded from the JVM typecheck build.
 */
fun installIconLoader(context: Context) {
    val assets = context.assets
    val available = buildMap<String, String> {
        for (directory in listOf("icons", "homebrew/salt-and-lantern")) {
            val names = try { assets.list(directory).orEmpty() } catch (e: Exception) { emptyArray() }
            for (name in names) {
                if (name.endsWith(".png") || name.endsWith(".webp")) {
                    put(name.substringBeforeLast('.'), "$directory/$name")
                }
            }
        }
    }
    IconStore.load = loader@{ id ->
        val path = available[id] ?: return@loader null
        try {
            assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
    // Homebrew art from external URLs (script tool "image" field):
    // best-effort background download, monogram until (and unless) it lands.
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    IconStore.remoteLoader = { _, url, done ->
        Thread {
            val bitmap = try {
                java.net.URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
            mainHandler.post { done(bitmap) }
        }.start()
    }
}
