package com.clocktower.grimoire.uicheck

import com.clocktower.engine.Character
import com.clocktower.engine.GameData
import com.clocktower.engine.Script
import com.clocktower.engine.Team
import com.clocktower.grimoire.ui.screens.tokenPickerCharacters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playtest C-20: **a Traveller sitting at the table contributes reminder
 * tokens like everyone else.**
 *
 * `GameData.resolve(script)` walks `script.characterIds`, which never holds a
 * traveller — they come from `travellersFor(script)` — so the seat sheet's
 * token picker ended at the last Minion and a seated Bureaucrat's "3 Votes"
 * could only be placed by that traveller's own night step.
 *
 * `tokenPickerCharacters` is UI code under `app/`, which `:engine`'s test
 * source set cannot see; uicheck compiles every app source. Run with
 * `./gradlew -p tools/uicheck test`.
 */
class TokenPickerTest {

    private val data = GameData.loadDefault()
    private val lookup: (String) -> Character? = data::character
    private val bmr: Script = data.builtInScripts().first { it.id == "bmr" }

    @Test
    fun `a seated traveller's reminders reach the token picker`() {
        val onScript = data.resolve(bmr)
        val bureaucrat = data.character("bureaucrat")!!
        assertTrue(
            "the fixture needs a traveller that is NOT on the script",
            onScript.none { it.id == "bureaucrat" },
        )
        assertEquals(Team.TRAVELLER, bureaucrat.team)
        assertTrue("…and one that carries reminders", bureaucrat.allReminders.isNotEmpty())

        val offered = tokenPickerCharacters(
            onScript = onScript,
            seatedCharacterIds = onScript.take(3).map { it.id } + "bureaucrat",
            lookup = lookup,
        )
        assertTrue(
            "the seated Bureaucrat's tokens are offered: ${offered.map { it.id }.takeLast(3)}",
            offered.any { it.id == "bureaucrat" },
        )
        assertEquals("the script is unchanged and nothing is duplicated", onScript.size + 1, offered.size)
        assertEquals("every id appears once", offered.size, offered.map { it.id }.toSet().size)
    }

    @Test
    fun `a script with no off-script seats is offered exactly the script`() {
        val onScript = data.resolve(bmr)
        assertEquals(
            onScript,
            tokenPickerCharacters(onScript, onScript.map { it.id }, lookup),
        )
    }
}
