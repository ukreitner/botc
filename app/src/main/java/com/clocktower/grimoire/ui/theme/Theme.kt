package com.clocktower.grimoire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clocktower.engine.Team

// A candlelit gothic palette: deep night purples, aged gold, parchment.
val NightSky = Color(0xFF0E0A14)
val Midnight = Color(0xFF161020)
val Twilight = Color(0xFF1E1630)
val VelvetPurple = Color(0xFF2A2040)
val AgedGold = Color(0xFFD9B45B)
val PaleGold = Color(0xFFEFD9A0)
val Parchment = Color(0xFFEFE6D0)
val FadedInk = Color(0xFFB9AECB)
val BloodRed = Color(0xFF9C2B2B)
// Bright enough to keep small warning text readable on every dark surface.
val EmberRed = Color(0xFFD96B6B)

// Token ring colors per team, matching physical token conventions.
val TownsfolkBlue = Color(0xFF4E8FD9)
val OutsiderTeal = Color(0xFF3FB8AE)
val MinionOrange = Color(0xFFD97B4E)
val DemonRed = Color(0xFFC93B3B)
val TravellerPurple = Color(0xFFA46FD1)
val FabledGold = Color(0xFFE0B84F)

val Team.color: Color
    get() = when (this) {
        Team.TOWNSFOLK -> TownsfolkBlue
        Team.OUTSIDER -> OutsiderTeal
        Team.MINION -> MinionOrange
        Team.DEMON -> DemonRed
        Team.TRAVELLER -> TravellerPurple
        Team.FABLED -> FabledGold
    }

private val GrimoireColors = darkColorScheme(
    primary = AgedGold,
    onPrimary = NightSky,
    primaryContainer = VelvetPurple,
    onPrimaryContainer = PaleGold,
    secondary = FadedInk,
    onSecondary = NightSky,
    secondaryContainer = Twilight,
    onSecondaryContainer = Parchment,
    tertiary = TravellerPurple,
    onTertiary = NightSky,
    background = NightSky,
    onBackground = Parchment,
    surface = Midnight,
    onSurface = Parchment,
    surfaceVariant = Twilight,
    onSurfaceVariant = FadedInk,
    surfaceContainer = Twilight,
    surfaceContainerHigh = VelvetPurple,
    surfaceContainerHighest = VelvetPurple,
    error = EmberRed,
    onError = NightSky,
    outline = Color(0xFF4A3E63),
    outlineVariant = Color(0xFF32294A),
)

@Composable
fun GrimoireTheme(content: @Composable () -> Unit) {
    // The grimoire is always a night-time artifact: one dark theme, tuned
    // for candlelight-adjacent play environments.
    isSystemInDarkTheme() // observed for future light theme support
    MaterialTheme(
        colorScheme = GrimoireColors,
        typography = GrimoireTypography,
        content = content,
    )
}
