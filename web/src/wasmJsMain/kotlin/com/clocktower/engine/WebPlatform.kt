package com.clocktower.engine

/**
 * Browser substitutes for the engine's platform seam: bundled data files
 * are fetched by Main before the UI starts and served from memory here.
 */
object BotcResources {
    val preloaded = mutableMapOf<String, String>()

    fun read(path: String): String =
        preloaded[path] ?: error("Resource $path not preloaded")

    fun readOrNull(path: String): String? = preloaded[path]
}

private fun jsDateNow(): Double = js("Date.now()")

/** Wall-clock access backed by the browser's Date.now(). */
object Time {
    fun epochMillis(): Long = jsDateNow().toLong()
}
