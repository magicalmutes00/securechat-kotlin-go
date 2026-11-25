package com.securechat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleRadius
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleSpring
import com.securechat.presentation.theme.AppleType

/* ------------------------------------------------------------------ */
/* Screen scaffold                                                     */
/* ------------------------------------------------------------------ */

/**
 * Root container for every screen: full-bleed background plus the vertical
 * rhythm iOS uses. Screens add their own nav bar as the first child.
 */
@Composable
fun AppleScreen(
    modifier: Modifier = Modifier,
    background: Color = AppleColor.SystemBackground,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        content = content
    )
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun AppleButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) AppleColor.SystemBlue else AppleColor.SystemGray5
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppleType.Button,
            color = Color.White
        )
    }
}

@Composable
fun AppleTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppleColor.SystemElevatedBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = AppleType.Button, color = AppleColor.SystemBlue)
    }
}

/* ------------------------------------------------------------------ */
/* Navigation                                                          */
/* ------------------------------------------------------------------ */

/**
 * Inline navigation bar: back chevron on the left, centred 17sp title,
 * trailing actions. Matches the iOS "pushed screen" chrome.
 */
@Composable
fun AppleTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleColor.SystemBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Text(
            text = title,
            style = AppleType.ListItemTitle,
            color = AppleColor.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 52.dp)
        )

        if (actions != null) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Root-screen header: large title with optional subline and trailing actions. */
@Composable
fun AppleLargeTitle(
    title: String,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppleType.LargeTitle, color = AppleColor.Label)
            subtitle?.let {
                Spacer(Modifier.height(AppleSpace.Xxs))
                Text(text = it, style = AppleType.Footnote, color = AppleColor.Label3)
            }
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppleSpace.Xxs),
                content = actions
            )
        }
    }
}

data class AppleTab(val label: String, val icon: ImageVector)

/** Translucent iOS tab bar. */
@Composable
fun AppleTabBar(
    tabs: List<AppleTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppleColor.Separator)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(AppleColor.SystemElevatedBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint = if (selected) AppleColor.SystemBlue else AppleColor.Label3
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(text = tab.label, style = AppleType.TabLabel, color = tint)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Grouped lists                                                       */
/* ------------------------------------------------------------------ */

/**
 * Inset grouped section: black page background, a single 10dp-radius card,
 * uppercase header above and muted footer below.
 */
@Composable
fun AppleGroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        header?.let {
            Text(
                text = it.uppercase(),
                style = AppleType.SectionTitle,
                color = AppleColor.Label3,
                modifier = Modifier.padding(
                    start = AppleSpace.Xxl,
                    top = AppleSpace.Xxl,
                    bottom = AppleSpace.Sm
                )
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = AppleSpace.Lg)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppleColor.SystemGroupedCell),
            content = content
        )
        footer?.let {
            Text(
                text = it,
                style = AppleType.Footnote,
                color = AppleColor.Label3,
                modifier = Modifier.padding(
                    start = AppleSpace.Xxl,
                    end = AppleSpace.Xxl,
                    top = AppleSpace.Sm
                )
            )
        }
    }
}

/**
 * A single grouped-list cell. `leading` is usually a 29dp icon tile,
 * `trailing` a value label or `AppleToggle`; chevrons are added by `showChevron`.
 */
@Composable
fun AppleCell(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    dividerInset: Dp = AppleSpace.Lg,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(
                if (pressed && enabled) AppleColor.SystemGroupedCellPressed else Color.Transparent
            )
            .clickable(
                enabled = enabled && onClick != null,
                interactionSource = interaction,
                indication = null,
                onClick = { onClick?.invoke() }
            )
            .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(AppleSpace.Md))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.ListItemTitle,
                color = when {
                    destructive -> AppleColor.SystemRed
                    enabled -> AppleColor.Label
                    else -> AppleColor.Label3
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Spacer(Modifier.height(AppleSpace.Xxxs))
                Text(
                    text = it,
                    style = AppleType.ListItemSubtitle,
                    color = AppleColor.Label3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        value?.let {
            Text(
                text = it,
                style = AppleType.ListItemSubtitle,
                color = AppleColor.Label3,
                maxLines = 1
            )
        }
        trailing?.let {
            Spacer(Modifier.width(AppleSpace.Sm))
            it()
        }
        if (showChevron) {
            Spacer(Modifier.width(AppleSpace.Xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppleColor.Chevron,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = dividerInset)
                .height(0.5.dp)
                .background(AppleColor.Separator)
        )
    }
}

/** Selectable row with a trailing checkmark (iOS single/multi select). */
@Composable
fun AppleSelectRow(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(
                if (pressed && enabled) AppleColor.SystemGroupedCellPressed else Color.Transparent
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.ListItemTitle,
                color = if (enabled) AppleColor.Label else AppleColor.Label3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Spacer(Modifier.height(AppleSpace.Xxxs))
                Text(
                    text = it,
                    style = AppleType.ListItemSubtitle,
                    color = AppleColor.Label3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = AppleColor.SystemBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppleSpace.Lg)
                .height(0.5.dp)
                .background(AppleColor.Separator)
        )
    }
}

/** Icon tile used at the leading edge of grouped rows (29dp, iOS settings). */
@Composable
fun AppleIconTile(
    icon: ImageVector,
    tint: Color = Color.White,
    background: Color = AppleColor.SystemBlue,
    size: Int = 29
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.24f).dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.6f).dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* Forms                                                               */
/* ------------------------------------------------------------------ */

@Composable
fun AppleFormField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it.uppercase(),
                style = AppleType.SectionTitle,
                color = AppleColor.Label3,
                modifier = Modifier.padding(bottom = AppleSpace.Sm)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppleColor.SystemSecondaryBackground)
                .padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Md),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = AppleType.Body.copy(color = AppleColor.Label),
                cursorBrush = SolidColor(AppleColor.SystemBlue),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = AppleType.Body,
                                color = AppleColor.Label4
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Controls                                                            */
/* ------------------------------------------------------------------ */

@Composable
fun AppleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbX by animateFloatAsState(
        targetValue = if (checked) 22f else 2f,
        animationSpec = AppleSpring.Fast,
        label = "toggleThumb"
    )

    Box(
        modifier = modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) AppleColor.SystemGreen else AppleColor.SystemGray5
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX.dp)
                .size(27.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun AppleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppleColor.SystemSecondaryBackground)
            .padding(horizontal = AppleSpace.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = AppleColor.Label4,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(AppleSpace.Xs))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = AppleType.Body2, color = AppleColor.Label4)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppleType.Body2.copy(color = AppleColor.Label),
                cursorBrush = SolidColor(AppleColor.SystemBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = "Clear",
                style = AppleType.Subhead,
                color = AppleColor.SystemBlue,
                modifier = Modifier
                    .clickable { onValueChange("") }
                    .padding(start = AppleSpace.Sm)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Avatars                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun AppleAvatar(
    url: String?,
    name: String,
    size: Int = 40,
    presence: Boolean = false,
    modifier: Modifier = Modifier
) {
    val diameter = size.dp
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            val gradient = makeGradient(name)
            Box(
                modifier = Modifier.fillMaxSize().background(gradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(name),
                    color = Color.White,
                    style = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (size / 3).sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (presence) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size((size / 4).coerceAtLeast(6).dp)
                    .background(AppleColor.SystemBackground, CircleShape)
                    .border(1.5.dp, AppleColor.SystemGreen, CircleShape)
            )
        }
    }
}

private fun makeGradient(name: String): androidx.compose.ui.graphics.Brush {
    val pairs = listOf(
        Color(0xFF007AFF) to Color(0xFF34A8FF),
        Color(0xFF30D158) to Color(0xFF5CEB7E),
        Color(0xFFFF9F0A) to Color(0xFFFFB44A),
        Color(0xFFFF375F) to Color(0xFFFF6B84),
        Color(0xFFBF5AF2) to Color(0xFFD27BFF),
        Color(0xFF64D2CA) to Color(0xFF8EE4DC),
        Color(0xFF5E5CE6) to Color(0xFF8A89FF),
        Color(0xFFFFD60A) to Color(0xFFFFE34A)
    )
    val pair = pairs[kotlin.math.abs(name.lowercase().hashCode()) % pairs.size]
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(pair.first, pair.second))
}

private fun initials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0][0]}${words[1][0]}".uppercase()
        words.size == 1 && words[0].length >= 2 -> words[0].substring(0, 2).uppercase()
        words.isNotEmpty() -> words[0].substring(0, 1).uppercase()
        else -> "?"
    }
}

/* ------------------------------------------------------------------ */
/* Chat                                                                */
/* ------------------------------------------------------------------ */

/**
 * iMessage bubble. The last bubble in a run carries a squared corner on the
 * tail side; earlier bubbles keep all four corners round.
 */
@Composable
fun AppleChatBubble(
    text: String,
    isOwn: Boolean,
    meta: String? = null,
    tail: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (tail) {
        if (isOwn) {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
        } else {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
        }
    } else {
        RoundedCornerShape(18.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(
                    if (isOwn) AppleColor.BubbleOutgoing else AppleColor.BubbleIncoming
                )
                .padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Sm)
        ) {
            Text(
                text = text,
                style = AppleType.Bubble,
                color = if (isOwn) Color.White else AppleColor.Label
            )
        }
        meta?.let {
            Spacer(Modifier.height(AppleSpace.Xxs))
            Text(text = it, style = AppleType.Caption2, color = AppleColor.Label3)
        }
    }
}

/** Round translucent action used over media, and for the compose affordance. */
@Composable
fun AppleCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 36,
    tint: Color = Color.White,
    background: Color = Color.Black.copy(alpha = 0.35f)
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* Overlays                                                            */
/* ------------------------------------------------------------------ */

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp, 5.dp)
            .clip(AppleRadius.Tiny)
            .background(AppleColor.Label4.copy(alpha = 0.4f))
    )
}

/** Bottom sheet. Slides in over a dimmed backdrop, dismisses on backdrop tap. */
@Composable
fun AppleSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(AppleColor.SystemSheetBackground)
                    .clickable(enabled = false) {}
                    .padding(top = AppleSpace.Sm, bottom = AppleSpace.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

data class AppleSheetAction(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/** iOS action sheet: title/message plus a stacked list of actions. */
@Composable
fun AppleActionSheet(
    visible: Boolean,
    actions: List<AppleSheetAction>,
    onDismiss: () -> Unit,
    title: String? = null,
    message: String? = null
) {
    AppleSheet(visible = visible, onDismiss = onDismiss) {
        if (title != null || message != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = AppleType.Footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = AppleColor.Label,
                        textAlign = TextAlign.Center
                    )
                }
                message?.let {
                    Spacer(Modifier.height(AppleSpace.Xxs))
                    Text(
                        text = it,
                        style = AppleType.Footnote,
                        color = AppleColor.Label3,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = AppleSpace.Sm)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppleColor.SystemGroupedCell)
        ) {
            actions.forEachIndexed { index, action ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable {
                            onDismiss()
                            action.onClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action.label,
                        style = AppleType.Body,
                        color = if (action.destructive) AppleColor.SystemRed else AppleColor.SystemBlue
                    )
                }
                if (index != actions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(AppleColor.Separator)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppleSpace.Sm))

        Box(
            modifier = Modifier
                .padding(horizontal = AppleSpace.Sm)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppleColor.SystemGroupedCell)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Cancel",
                style = AppleType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = AppleColor.SystemBlue
            )
        }
    }
}

/** Frameless iOS alert: 270dp card, hairline-separated actions. */
@Composable
fun AppleAlertDialog(
    title: String,
    message: String? = null,
    confirmLabel: String = "OK",
    dismissLabel: String? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppleColor.SystemSheetBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AppleSpace.Lg,
                    vertical = AppleSpace.Xl
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = AppleType.ListItemTitle,
                    color = AppleColor.Label,
                    textAlign = TextAlign.Center
                )
                message?.let {
                    Spacer(Modifier.height(AppleSpace.Xs))
                    Text(
                        text = it,
                        style = AppleType.Footnote,
                        color = AppleColor.Label2,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppleColor.Separator)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                if (dismissLabel != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dismissLabel,
                            style = AppleType.Body,
                            color = AppleColor.SystemBlue
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(44.dp)
                            .background(AppleColor.Separator)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmLabel,
                        style = AppleType.Body.copy(
                            fontWeight = if (destructive) FontWeight.Normal else FontWeight.SemiBold
                        ),
                        color = if (destructive) AppleColor.SystemRed else AppleColor.SystemBlue
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Feedback                                                            */
/* ------------------------------------------------------------------ */

@Composable
fun AppleProgress(
    modifier: Modifier = Modifier,
    size: Int = 20
) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        color = AppleColor.SystemBlue,
        strokeWidth = 2.dp
    )
}

@Composable
fun AppleEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpace.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppleColor.Label4,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(AppleSpace.Lg))
        Text(
            text = title,
            style = AppleType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = AppleColor.Label,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppleSpace.Xs))
        Text(
            text = message,
            style = AppleType.Footnote,
            color = AppleColor.Label3,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AppleSpace.Xxl))
            AppleButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 220.dp)
            )
        }
    }
}

@Composable
fun AppleSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppleType.SectionTitle,
        color = AppleColor.Label3,
        modifier = modifier.padding(vertical = AppleSpace.Xs)
    )
}

@Composable
fun AppleTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = AppleColor.SystemBlue,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = AppleType.Body.copy(fontWeight = FontWeight.SemiBold),
        color = if (enabled) color else AppleColor.Label4,
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppleSpace.Sm, vertical = AppleSpace.Sm)
    )
}

/** 44dp square icon target used in nav bars. */
@Composable
fun AppleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppleColor.SystemBlue,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else AppleColor.Label4,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AppleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppleColor.SystemBlue
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppleColor.SystemFill)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun AppleInlineError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = AppleType.Footnote,
        color = AppleColor.SystemRed,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppleColor.SystemRedSurface)
            .padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Sm)
    )
}

@Composable
fun AppleDateSeparator(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppleType.Caption1,
        color = AppleColor.Label3,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppleSpace.Md)
    )
}

@Composable
fun AppleTypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 180),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(7.dp)
                    .background(AppleColor.Label3.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Legacy row used by HomeScreen's compose sheet                       */
/* ------------------------------------------------------------------ */

@Composable
fun AppleListRow(
    leading: @Composable () -> Unit = {},
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Xs)
            .background(
                Color.Transparent,
                AppleRadius.Pill
            )
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.ListItemTitle,
                color = if (enabled) AppleColor.Label else AppleColor.Label3
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = AppleType.ListItemSubtitle,
                    color = AppleColor.Label3,
                    modifier = Modifier.padding(top = AppleSpace.Xxs)
                )
            }
        }

        trailing()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(start = AppleSpace.Lg)
            .background(AppleColor.Separator)
    )
}

/** Multi-select indicator: a ticked blue disc, or an empty ring. */
@Composable
fun AppleSelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) AppleColor.SystemBlue else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) AppleColor.SystemBlue else AppleColor.SystemGray,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
