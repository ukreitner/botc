package com.clocktower.engine

/**
 * Reads bundled data files from the JVM classpath. Non-JVM builds (the
 * web app) substitute this file with their own loader — everything else
 * in the engine is platform-neutral Kotlin.
 */
object BotcResources {
    fun read(path: String): String =
        readOrNull(path) ?: error("Missing bundled resource $path")

    fun readOrNull(path: String): String? =
        BotcResources::class.java.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
}

/** Wall-clock access; substituted on non-JVM builds. */
object Time {
    fun epochMillis(): Long = System.currentTimeMillis()
}
