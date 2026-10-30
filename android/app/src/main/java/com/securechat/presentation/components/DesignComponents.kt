package com.securechat.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.securechat.presentation.theme.Skin
import com.securechat.presentation.theme.SkinType

/**
 * A filter chip from the inbox design: lime when active, ChipIdle otherwise.
 * `count` renders inside the label ("Unread 3") the way the design spells it.
 */
@Composable
fun FilterChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Skin.Signal else Skin.ChipIdle)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (active) SkinType.ChipActive else SkinType.Chip,
            color = if (active) Skin.OnSignal else Skin.InkMuted,
            maxLines = 1
        )
    }
}

/** Search field: Field surface, 13dp radius, dim placeholder. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Skin.Field)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Skin.InkGhost,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = SkinType.SearchPlaceholder, color = Skin.InkGhost)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = SkinType.SearchPlaceholder.copy(color = Skin.Ink),
                cursorBrush = SolidColor(Skin.Signal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The "TODAY" pill above a day's messages. */
@Composable
fun DatePill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Skin.ChipIdle)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label.uppercase(), style = SkinType.DatePill, color = Skin.InkFaint)
    }
}

/** Amber security notice, as used for "Security code changed — verify". */
@Composable
fun SecurityNotice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Skin.AmberSurface)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, style = SkinType.Notice, color = Skin.Amber)
    }
}

/** Three pulsing dots shown while the other side is typing. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val alphas = rememberPulse()
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(color.copy(alpha = alphas[index]), CircleShape)
            )
        }
    }
}

/** A gentle three-step pulse so the dots read as activity, not decoration. */
@Composable
private fun rememberPulse(): List<Float> {
    val infinite = rememberInfiniteTransition(label = "typing")
    return (0..2).map { index ->
        val alpha by infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 140),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$index"
        )
        alpha
    }
}

/** The lime circular action button (compose, send). */
@Composable
fun SignalIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Skin.Signal)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Skin.OnSignal,
            modifier = Modifier.size((size * 0.4f).dp)
        )
    }
}

/** Tabs that exist in the product. */
enum class HomeTab(val label: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Default.Chat),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Bottom tab bar.
 *
 * The design shows four tabs (Chats, Calls, Vault, Settings); this app has no
 * calls or vault feature, so only the two that lead somewhere are rendered.
 * A tab that opens nothing is worse than a missing one.
 */
@Composable
fun HomeTabBar(
    active: HomeTab,
    hasUnread: Boolean,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(Skin.Surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Skin.Line))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = tab == active
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 22.dp, vertical = 2.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) Skin.Signal else Skin.TabIdle,
                            modifier = Modifier.size(20.dp)
                        )
                        if (tab == HomeTab.CHATS && hasUnread) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(6.dp)
                                    .background(Skin.Signal, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tab.label,
                        style = if (selected) SkinType.TabLabelActive else SkinType.TabLabel,
                        color = if (selected) Skin.Signal else Skin.TabIdle
                    )
                }
            }
        }
    }
}

/** The section label above a group of rows ("PINNED", "EARLIER"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SkinType.Meta,
        color = Skin.InkFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
