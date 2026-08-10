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

/** Small circular reminder token. */
@Composable
fun ReminderToken(
    label: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.9f))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = (size.value / 5f).coerceAtLeast(6f).sp,
            lineHeight = (size.value / 4.6f).coerceAtLeast(7f).sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(2.dp),
        )
    }
}
