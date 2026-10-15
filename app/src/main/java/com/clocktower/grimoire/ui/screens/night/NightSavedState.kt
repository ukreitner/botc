package com.clocktower.grimoire.ui.screens.night

/** Saved values carry their scope because rememberSaveable inputs do not validate restoration. */
internal fun saveNightGateAnswer(cycle: Int, stepToken: String, answer: Boolean): String =
    "${openRowKey(cycle, stepToken)}|$answer"

internal fun nightGateAnswer(saved: String, cycle: Int, stepToken: String): Boolean? {
    val prefix = "${openRowKey(cycle, stepToken)}|"
    return saved.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toBooleanStrictOrNull()
}
