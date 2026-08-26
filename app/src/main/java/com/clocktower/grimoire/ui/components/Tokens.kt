package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clocktower.engine.Character
import com.clocktower.engine.EffectGroup
import com.clocktower.grimoire.ui.theme.MIN_TEXT_SP
import com.clocktower.grimoire.ui.theme.Parchment
import com.clocktower.grimoire.ui.theme.color

/**
 * A circular character token: parchment face, team-colored ring, character
 * initials as its center mark (bundled art is not included), name below.
 */
@Composable
fun CharacterToken(
    character: Character?,
    size: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val ringColor = character?.team?.color ?: MaterialTheme.colorScheme.outline
    val face = if (dimmed) Parchment.copy(alpha = 0.35f) else Parchment
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(face, face.copy(alpha = 0.92f), ringColor.copy(alpha = 0.55f)),
                ),
            )
            .border(width = size / 22, color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Leaves along the top edge, like the physical tokens: one per night
        // the character acts (first night / other nights).
        if (character != null && (character.firstNightReminder.isNotBlank() || character.otherNightReminder.isNotBlank())) {
            Text(
                text = buildString {
                    if (character.firstNightReminder.isNotBlank()) append("")
                    if (character.otherNightReminder.isNotBlank()) append("")
                },
                fontSize = (size.value / 6.5f).sp,
                color = Color(0xFF3E5C2E).copy(alpha = if (dimmed) 0.35f else 0.85f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = size / 22),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = size / 10),
        ) {
            // Deliberately not remember()ed: the web build loads art
            // asynchronously behind a state-backed store, and a snapshot
            // read here lets tokens fill in as their art arrives.
            val icon = character?.let { IconStore.icon(it.id) }
            if (icon == null && character != null && character.image.isNotBlank()) {
                // Homebrew art lives at an external URL; kick off a
                // one-time background load, monogram in the meantime.
                androidx.compose.runtime.LaunchedEffect(character.id) {
                    IconStore.request(character.id, character.image)
                }
            }
            // The token face is art only — names render outside the token
            // (see TokenWithName) so they are never squeezed or truncated.
            if (icon != null && character != null) {
                Image(
                    bitmap = icon,
                    contentDescription = character.name,
                    contentScale = ContentScale.Fit,
                    alpha = if (dimmed) 0.4f else 1f,
                    modifier = Modifier.size(size * 0.8f),
                )
            } else {
                // No art available: a serif monogram, never an emoji.
                Text(
                    text = character?.let { tokenMonogram(it.name) } ?: "?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 2.8f).sp,
                    color = Color(0xFF241A38).copy(alpha = if (dimmed) 0.4f else 1f),
                )
            }
        }
    }
}

/**
 * A token with the character's full name beneath it — the name gets two
 * lines and shrinks before it ever truncates.
 */
@Composable
fun TokenWithName(
    character: Character?,
    size: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    nameColor: Color = Color(0xFFEFE6D0),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        CharacterToken(character = character, size = size, dimmed = dimmed)
        if (character != null) {
            Text(
                text = character.name,
                fontSize = (size.value / 6f).coerceIn(9f, 14f).sp,
                lineHeight = (size.value / 5.4f).coerceIn(10f, 15f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = nameColor.copy(alpha = if (dimmed) 0.5f else 0.95f),
            )
        }
    }
}

/** Two-letter monogram used as the token's center mark. */
fun tokenMonogram(name: String): String {
    val words = name.split(' ', '-', '\'').filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first().uppercase()}${words[1].first().uppercase()}"
        name.length >= 2 -> name.take(2).replaceFirstChar { it.uppercase() }
        else -> name.uppercase()
    }
}

// ---------------------------------------------------------------------------
// Status rendering (WP10)
//
// The organising idea of grimoire-and-seats: THE CIRCLE IS FOR RECOGNITION,
// THE BOARD IS FOR READING, THE SHEET IS FOR ACTING. Below ~36 dp a token
// cannot carry a real label — "Survives Execution" in a 22 dp disc was drawn
// at 6 sp and ellipsised to "Surv…" — so at those sizes we draw the
// EffectGroup GLYPH instead, never micro-text.
//
// The three functions below are deliberately pure (Float sp/dp, no Compose
// types) so `tools/uicheck`'s test source set can measure them.
// ---------------------------------------------------------------------------

/** The smallest size, in dp, at which a token may render its label as TEXT. */
const val TOKEN_LABEL_MIN_DP: Float = 36f

/**
 * Font size for a reminder token's label, in sp. Never below [MIN_TEXT_SP] —
 * that floor is the whole fix for grimoire-and-seats P0-3.
 */
fun reminderFontSp(sizeDp: Float): Float = (sizeDp / 4f).coerceAtLeast(MIN_TEXT_SP)

/** Font size for a status pip's glyph, in sp. Also floored at [MIN_TEXT_SP]. */
fun pipGlyphSp(sizeDp: Float): Float = (sizeDp / 2.2f).coerceAtLeast(MIN_TEXT_SP)

/** True when a token of [sizeDp] has room for its label rather than a glyph. */
fun tokenShowsLabel(sizeDp: Float): Boolean = sizeDp >= TOKEN_LABEL_MIN_DP

/**
 * Which pips a seat shows, and how many are hidden.
 *
 * Ordered by [EffectGroup.priority], NEVER by placement order: the tokens that
 * must never be forgotten ("Is The Drunk", "Red Herring", "No Ability") are
 * the OLDEST ones, and the old `takeLast(2)` hid exactly those
 * (grimoire-and-seats P0-5). Ties keep the order they came in, so a seat with
 * two IMPAIRED effects shows the first-placed one.
 */
fun visiblePips(groups: List<EffectGroup>, budget: Int): PipRow {
    if (budget <= 0) return PipRow(emptyList(), groups.size)
    val ordered = groups.sortedBy { it.priority }
    if (ordered.size <= budget) return PipRow(ordered, 0)
    // Keep budget - 1 slots for real pips so the "+N" itself has room.
    val shown = ordered.take(budget - 1)
    return PipRow(shown, ordered.size - shown.size)
}

/** The result of [visiblePips]: the pips to draw and the lower-priority overflow. */
data class PipRow(val shown: List<EffectGroup>, val hidden: Int)

/**
 * Collapses a character's reminder list to distinct labels with copy counts.
 *
 * `characters.json` lists an N-copy token N times (the green leaves on the
 * physical token), so a raw render shows "Poisoned Poisoned" for the Pukka.
 * Pickers show one chip reading `Poisoned ×2` instead (FOLLOWUPS, WP10/WP8).
 * Matching is case-insensitive per lead D5; the first spelling seen wins, and
 * that is the official Title Case one from the data file.
 */
fun labelCopies(labels: List<String>): List<TokenCopies> {
    val out = LinkedHashMap<String, TokenCopies>()
    for (raw in labels) {
        val label = raw.trim()
        if (label.isEmpty()) continue
        val key = label.lowercase()
        val seen = out[key]
        out[key] = if (seen == null) TokenCopies(label, 1) else seen.copy(copies = seen.copies + 1)
    }
    return out.values.toList()
}

/** One distinct token label and how many physical copies the character owns. */
data class TokenCopies(val label: String, val copies: Int)

/**
 * A status pip: kind-coloured disc, team-coloured provenance ring, glyph in
 * white at >= 11 sp.
 *
 * [suspended] renders the physical "turn the token upside-down" state — hollow
 * fill, dimmed glyph — which the wiki recommends over removing a token whose
 * owner has gone drunk or poisoned. [derived] (No Dashii's neighbours) has no
 * physical token, so its ring is dotted rather than solid.
 */
@Composable
fun StatusPip(
    group: EffectGroup,
    modifier: Modifier = Modifier,
    ringColor: Color = Color.Transparent,
    size: Dp = 18.dp,
    suspended: Boolean = false,
    derived: Boolean = false,
) {
    val fill = group.color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (suspended) Color.Transparent else fill)
            .border(
                width = if (derived) 1.dp else 2.dp,
                color = when {
                    suspended -> fill.copy(alpha = 0.7f)
                    ringColor == Color.Transparent -> fill.copy(alpha = 0.65f)
                    derived -> ringColor.copy(alpha = 0.55f)
                    else -> ringColor
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = group.glyph,
            fontSize = pipGlyphSp(size.value).sp,
            lineHeight = pipGlyphSp(size.value).sp,
            fontWeight = FontWeight.Bold,
            color = if (suspended) fill else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Small circular reminder token.
 *
 * [color] is the FILL — pass `group.color`, not the source team, so colour
 * means "what this does". [ringColor] is the provenance ring. Below
 * [TOKEN_LABEL_MIN_DP] the token draws [glyph] instead of [label]; the label
 * is never squeezed under [MIN_TEXT_SP].
 */
@Composable
fun ReminderToken(
    label: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = Color.White.copy(alpha = 0.5f),
    glyph: String? = null,
    suspended: Boolean = false,
    derived: Boolean = false,
) {
    val showsLabel = tokenShowsLabel(size.value) || glyph == null
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (suspended) color.copy(alpha = 0.18f) else color.copy(alpha = 0.9f))
            .border(
                width = if (derived) 1.dp else 1.5.dp,
                color = if (suspended) color else ringColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val content = if (showsLabel) label else glyph.orEmpty()
        val sp = if (showsLabel) reminderFontSp(size.value) else pipGlyphSp(size.value)
        Text(
            text = content,
            fontSize = sp.sp,
            lineHeight = (sp * 1.08f).sp,
            color = if (suspended) Color.White.copy(alpha = 0.65f) else Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
