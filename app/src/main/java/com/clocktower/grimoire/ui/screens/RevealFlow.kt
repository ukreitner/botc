package com.clocktower.grimoire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clocktower.engine.GameState
import com.clocktower.grimoire.ui.GameViewModel
import com.clocktower.grimoire.ui.components.CharacterToken
import com.clocktower.grimoire.ui.theme.AgedGold
import com.clocktower.grimoire.ui.theme.EmberRed
import com.clocktower.grimoire.ui.theme.Parchment

/**
 * "Pass the phone" secret reveal after dealing: for each seat, a hand-over
 * screen, then that player's character card, then on to the next seat.
 */
@Composable
fun RevealFlow(
    viewModel: GameViewModel,
    state: GameState,
    onDone: () -> Unit,
) {
    val seats = remember(state.players) { state.players.filter { it.characterId != null } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showing by rememberSaveable { mutableStateOf(false) }

    if (index >= seats.size) {
        onDone()
        return
    }
    val player = seats[index]
    val character = viewModel.characterById(player.characterId)
    val evil = player.isEvil(viewModel::characterById)

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    if (showing) {
                        showing = false
                        index += 1
                    } else {
                        showing = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!showing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Pass to",
                        fontSize = 28.sp,
                        color = Parchment,
                    )
                    Text(
                        player.name,
                        fontSize = 56.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Seat ${index + 1} of ${seats.size} · tap when only they can see",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("YOU ARE", fontSize = 34.sp, fontFamily = FontFamily.Serif, color = Parchment)
                    CharacterToken(character = character, size = 190.dp)
                    Text(
                        character?.name ?: "?",
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (evil) EmberRed else AgedGold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        character?.ability ?: "",
                        fontSize = 18.sp,
                        color = Parchment,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Tap to hide & pass on",
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}
