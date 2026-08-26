package com.clocktower.grimoire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.EffectGroup
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
        Team.LORIC -> FabledGold
        Team.UNKNOWN -> FadedInk
    }

// ---------------------------------------------------------------------------
// Status palette (WP10). Owned here; WP8 (night) and WP9 (day) consume these
// names rather than inventing their own reds and greens.
//
// THE RULE, and it is the whole point of these eight colours:
//     fill  = what the effect DOES   (EffectGroup.color)
//     ring  = who put it there       (Team.color of the source character)
// Before WP10 the fill was the source's team, so a generic "Poisoned" and a
// generic "Protected" were the same BloodRed — the two statuses that decide
// whether a player lives were indistinguishable (grimoire-and-seats P0-4).
// Each colour also carries EffectGroup.glyph, so the coding survives
// deuteranopia and a dim room.
// ---------------------------------------------------------------------------

/** † a death is pending or already marked (Dead, Died Today, About To Die). */
val PendingDeathRed = Color(0xFFC93B3B)

/** ! the seat's ability is not working (Poisoned, Drunk, No Ability). */
val PoisonGreen = Color(0xFF6FA84E)

/** + something is stopping a death (Safe, Cannot Die, Survives Execution). */
val ShieldBlue = Color(0xFF4E8FD9)

/** M madness and vote/nomination compulsion (Mad, Master, Fear). */
val MadnessViolet = Color(0xFFA46FD1)

/** = the seat is not what it looks like (Is The Drunk, Turns Evil, Twin). */
val IdentityGold = Color(0xFFE0B84F)

/** O an ability was spent, granted or removed (Used, Has Ability, Once). */
val SpentGrey = Color(0xFF8A8296)

/** i bookkeeping about information given (Know, Wrong, Red Herring). */
val InfoTeal = Color(0xFF3FB8AE)

/** · a mark with no rule behind it (Night 1, X, Visitor). */
val MarkerGrey = Color(0xFF6B6478)

/** Ring drawn around the seat that is currently on the block. */
val OnBlockGold = AgedGold

/** The shroud drape over a dead seat's token. */
val ShroudBlack = Color(0xE6151020)

/**
 * The fill colour for one status pip / token — what the effect DOES.
 * Provenance is the 2 dp ring in [Team.color], never the fill.
 */
val EffectGroup.color: Color
    get() = when (this) {
        EffectGroup.PENDING_DEATH -> PendingDeathRed
        EffectGroup.IMPAIRED -> PoisonGreen
        EffectGroup.PROTECTED -> ShieldBlue
        EffectGroup.MADNESS -> MadnessViolet
        EffectGroup.IDENTITY -> IdentityGold
        EffectGroup.ABILITY -> SpentGrey
        EffectGroup.INFO -> InfoTeal
        EffectGroup.MARKER -> MarkerGrey
    }

/** Storyteller-voice name for a filter chip or a section header. */
val EffectGroup.displayName: String
    get() = when (this) {
        EffectGroup.PENDING_DEATH -> "dying"
        EffectGroup.IMPAIRED -> "poisoned"
        EffectGroup.PROTECTED -> "protected"
        EffectGroup.MADNESS -> "mad"
        EffectGroup.IDENTITY -> "identity"
        EffectGroup.ABILITY -> "ability"
        EffectGroup.INFO -> "info"
        EffectGroup.MARKER -> "marks"
    }

/**
 * The hard floor for every piece of text in this app, in sp
 * (ARCHITECTURE §3.4 rule 7; grimoire-and-seats P0-3 — the circle used to
 * draw reminder labels at 6 sp). Nothing may render smaller, ever.
 */
const val MIN_TEXT_SP: Float = 11f

/** [MIN_TEXT_SP] as a [TextUnit], for call sites that want it directly. */
val MinText: TextUnit = MIN_TEXT_SP.sp

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

// Softer, rounder silhouettes than stock Material — closer to tokens and
// aged paper than to a settings app.
private val GrimoireShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun GrimoireTheme(content: @Composable () -> Unit) {
    // The grimoire is always a night-time artifact: one dark theme, tuned
    // for candlelight-adjacent play environments.
    isSystemInDarkTheme() // observed for future light theme support
    MaterialTheme(
        colorScheme = GrimoireColors,
        typography = GrimoireTypography,
        shapes = GrimoireShapes,
        content = content,
    )
}
