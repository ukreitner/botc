package com.clocktower.grimoire.uicheck

import com.clocktower.grimoire.ui.screens.forcedNightTokens
import com.clocktower.grimoire.ui.screens.night.confirmAfterHold
import com.clocktower.grimoire.ui.screens.night.openRowKey
import com.clocktower.grimoire.ui.screens.night.nightGateAnswer
import com.clocktower.grimoire.ui.screens.night.saveNightGateAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightControlStateTest {
    @Test
    fun `early release and cancelled pointer gestures never commit`() = runBlocking {
        var confirmations = 0
        confirmAfterHold(100, { true }) { confirmations++ }
        confirmAfterHold(100, { false }) { confirmations++ }
        val waiting = CompletableDeferred<Unit>()
        val gesture = launch(start = CoroutineStart.UNDISPATCHED) {
            confirmAfterHold(1_000, {
                waiting.complete(Unit)
                CompletableDeferred<Boolean>().await()
            }) { confirmations++ }
        }
        waiting.await()
        gesture.cancelAndJoin()
        assertEquals(0, confirmations)
    }

    @Test
    fun `holding beyond the threshold confirms exactly once`() = runBlocking {
        var confirmations = 0
        confirmAfterHold(10, { CompletableDeferred<Boolean>().await() }) { confirmations++ }
        assertEquals(1, confirmations)
    }

    @Test
    fun `run anyway survives reopening only in its original night`() {
        val saved = setOf(openRowKey(1, "courtier:11"), openRowKey(2, "widow:5"))
        assertEquals(setOf("courtier:11"), forcedNightTokens(saved, 1))
        assertEquals(setOf("widow:5"), forcedNightTokens(saved, 2))
        assertEquals(emptySet<String>(), forcedNightTokens(saved, 3))
        assertEquals(emptySet<String>(), forcedNightTokens(setOf("courtier:11"), 2))
    }

    @Test
    fun `a saved gate answer cannot bypass another night or player's gate`() {
        val yes = saveNightGateAnswer(1, "gambler:8", true)
        assertEquals(true, nightGateAnswer(yes, 1, "gambler:8"))
        assertNull(nightGateAnswer(yes, 2, "gambler:8"))
        assertNull(nightGateAnswer(yes, 1, "gambler:9"))
        assertNull(nightGateAnswer("", 1, "gambler:8"))
        assertNull(nightGateAnswer("true", 1, "gambler:8"))
        val no = saveNightGateAnswer(2, "gambler:8", false)
        assertEquals(false, nightGateAnswer(no, 2, "gambler:8"))
    }
}
