package com.securechat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.presentation.theme.Skin
import com.securechat.presentation.theme.SkinType
import kotlin.math.absoluteValue

/** Presence states from the design's system sheet. */
enum class Presence { ONLINE, AWAY, OFFLINE }

/**
 * Avatar from the design: a two-stop diagonal gradient per identity, with dark
 * initials knocked out of it.
 *
 * The gradient is chosen from the name so a person keeps the same colour on
 * every screen and across restarts, which is what makes a group list scannable.
 * The eight pairs are the design's own avatar palette, not generated hues.
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 46,
    presence: Presence? = null,
    initialsSize: Int = 12
) {
    val diameter = size.dp
    val gradient = gradientFor(name)
    val initials = getInitials(name)

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(gradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Skin.OnSignal,
                    style = SkinType.AvatarInitials,
                    fontSize = initialsSize.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (presence != null) {
            // Ring in the page colour so the dot reads as separate from the
            // avatar on any background.
            val dot = (size / 5).coerceAtLeast(8).dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(dot)
                    .background(Skin.Void, CircleShape)
                    .border(2.dp, Skin.Void, CircleShape)
                    .background(presenceColor(presence), CircleShape)
            )
        }
    }
}

private fun presenceColor(presence: Presence): Color = when (presence) {
    Presence.ONLINE -> Skin.Signal
    Presence.AWAY -> Skin.Amber
    Presence.OFFLINE -> Skin.TabIdle
}

/** The design's eight avatar gradients, indexed by a stable name hash. */
internal fun gradientFor(name: String): Brush {
    val pairs = listOf(
        Color(0xFF2FA98F) to Color(0xFF6FE3C4), // teal
        Color(0xFF4B5B7C) to Color(0xFF2E3950), // slate
        Color(0xFFD08A3E) to Color(0xFFF2C879), // amber
        Color(0xFFC4546E) to Color(0xFFF09AAB), // rose
        Color(0xFF7C6BC9) to Color(0xFFB9A7F0), // violet
        Color(0xFF4E7B9E) to Color(0xFF8BC2D8), // blue
        Color(0xFF57795F) to Color(0xFF9CC7A5), // green
        Color(0xFF8A7A5E) to Color(0xFFD6C6A8)  // sand
    )
    val pair = pairs[name.lowercase().hashCode().absoluteValue % pairs.size]
    return Brush.linearGradient(listOf(pair.first, pair.second))
}

internal fun getInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0][0]}${words[1][0]}".uppercase()
        words.size == 1 && words[0].length >= 2 -> words[0].substring(0, 2).uppercase()
        words.isNotEmpty() -> words[0].substring(0, 1).uppercase()
        else -> "?"
    }
}
