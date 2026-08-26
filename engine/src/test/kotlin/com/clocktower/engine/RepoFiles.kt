package com.clocktower.engine

import java.io.File

/**
 * Locates files in the working copy from inside a unit test (WP12).
 *
 * The data-parity test reads the vendored official source under `tools/`, and
 * the CI gates of ARCHITECTURE §4 WP12 grep app/ and web/ sources — neither is
 * on the test classpath, so both have to be found on disk. Gradle runs tests
 * with the module directory as the working directory, so walk up until the
 * settings file that marks the repository root appears.
 */
object RepoFiles {

    /** The repository root, i.e. the directory holding `settings.gradle.kts`. */
    val root: File by lazy {
        var dir: File? = File(".").absoluteFile.normalize()
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
            dir = dir.parentFile
        }
        error("no settings.gradle.kts above ${File(".").absolutePath} — cannot locate the repo root")
    }

    /** A repo-relative path, whether or not it exists. */
    fun file(relative: String): File = File(root, relative)

    /**
     * The text of a repo-relative file, or `null` when it does not exist —
     * a gate whose target file a later work package has not created yet
     * reports "not present" rather than failing the whole suite.
     */
    fun textOrNull(relative: String): String? = file(relative).takeIf { it.isFile }?.readText()

    /** The text of a repo-relative file; fails loudly when it is missing. */
    fun text(relative: String): String =
        textOrNull(relative) ?: error("missing repo file: $relative (root = $root)")

    /** Every `*.kt` under a repo-relative directory, or empty when it is absent. */
    fun kotlinSources(relativeDir: String): List<File> =
        file(relativeDir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
