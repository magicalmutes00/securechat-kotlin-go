package com.securechat.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R

/**
 * Design tokens taken from the SecureChat.figcraft source file.
 *
 * The palette, type scale, radii and spacing here are not invented: each value
 * was read out of the design document (fills, `style.font_*`, `corners.radii`).
 * Keeping them in one place is what lets every screen agree — the previous theme
 * mixed Material defaults with ad-hoc `Color(0xFF...)` literals in individual
 * screens, which is why no two surfaces matched.
 */
object Skin {
    /** Page background — the deepest surface. */
    val Void = Color(0xFF05060A)

    /** Raised surface: tab bar, sheets. */
    val Surface = Color(0xFF08090D)

    /** Cards and rows that need to sit above the page. */
    val SurfaceCard = Color(0xFF0C0F14)

    /** Inputs, search field, composer. */
    val Field = Color(0xFF10141A)

    /** Idle chip background. */
    val ChipIdle = Color(0xFF12161D)

    /** Selected conversation row. */
    val RowSelected = Color(0xFF161B24)

    /** Hairline dividers. */
    val Line = Color(0xFF1B212A)

    /** Primary text. */
    val Ink = Color(0xFFEDF1F7)

    /** Conversation names in the list (a touch softer than Ink). */
    val InkSoft = Color(0xFFDCE3EC)

    /** Chip labels, secondary controls. */
    val InkMuted = Color(0xFFA9B5C4)

    /** Snippets, sublines, timestamps that must still read. */
    val InkDim = Color(0xFF8B97A8)

    /** Timestamps and meta. */
    val InkFaint = Color(0xFF5A6678)

    /** Placeholder text. */
    val InkGhost = Color(0xFF5F6B7C)

    /** Inactive tab labels. */
    val TabIdle = Color(0xFF6C7889)

    /** The home-indicator pill. */
    val HomeIndicator = Color(0xFF3A4350)

    /** The single accent: send, compose, unread badges, active tab. */
    val Signal = Color(0xFFC8FF3D)

    /** Text/glyph colour on Signal. */
    val OnSignal = Color(0xFF0A0C10)

    /** Incoming bubbles and primary actions. */
    val Primary = Color(0xFF2457FF)

    /** Text on Primary bubbles. */
    val OnPrimary = Color(0xFFFFFFFF)

    /** Meta text inside an incoming bubble (timestamps, file size). */
    val OnPrimarySoft = Color(0xFFC9D7FF)

    /** Security warnings. */
    val Amber = Color(0xFFE8B85C)

    /** Warning pill background. */
    val AmberSurface = Color(0xFF191308)

    val Error = Color(0xFFFF6B6B)
    val ErrorSurface = Color(0xFF1F0D0D)

    // Spacing scale from the design's system sheet.
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space4 = 16.dp
    val Space8 = 32.dp

    // Radii from the design's system sheet.
    val Radius4 = RoundedCornerShape(4.dp)
    val Radius8 = RoundedCornerShape(8.dp)
    val Radius14 = RoundedCornerShape(14.dp)
    val Radius22 = RoundedCornerShape(22.dp)

    /** Incoming bubble: squared at the top-left, where the tail would be. */
    val BubbleIn = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

    /** Outgoing bubble: squared at the bottom-right. */
    val BubbleOut = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)

    /** Group avatar stacks overlap by this much. */
    val AvatarStackOverlap = 12.dp
}

/** Inter, bundled in `res/font`. Weights match the design's three families. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

/**
 * Named text styles from the design's type scale, plus the sizes the screens use
 * beyond it (snippets, timestamps, tab labels). Sizes are the design's px values
 * rendered as sp, so the layout holds its proportions at the default scale.
 */
object SkinType {
    private fun style(weight: FontWeight, size: TextUnit, letterSpacing: TextUnit = 0.sp) =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = weight,
            fontSize = size,
            letterSpacing = letterSpacing
        )

    // System sheet: display / title / body / meta.
    val Display = style(FontWeight.SemiBold, 28.sp, (-0.28).sp)
    val Title = style(FontWeight.SemiBold, 18.sp)
    val Body = style(FontWeight.Normal, 14.sp)
    val Meta = style(FontWeight.Medium, 10.sp, 0.8.sp)

    // Screens.
    val ScreenTitle = style(FontWeight.SemiBold, 26.sp)
    val Subline = style(FontWeight.Normal, 11.sp)
    val RowName = style(FontWeight.SemiBold, 13.sp)
    val RowSnippet = style(FontWeight.Normal, 11.5.sp)
    val RowTime = style(FontWeight.Medium, 9.5.sp)
    val Badge = style(FontWeight.SemiBold, 9.5.sp)

    val Chip = style(FontWeight.Medium, 11.5.sp)
    val ChipActive = style(FontWeight.SemiBold, 11.5.sp)
    val SearchPlaceholder = style(FontWeight.Normal, 12.5.sp)

    val HeaderName = style(FontWeight.SemiBold, 14.sp)
    val HeaderStatus = style(FontWeight.Medium, 10.sp)
    val HeaderMeta = style(FontWeight.Normal, 10.sp)
    val DatePill = style(FontWeight.Medium, 8.sp, 0.6.sp)

    val BubbleIn = style(FontWeight.Normal, 13.sp)
    val BubbleOut = style(FontWeight.Medium, 13.sp)
    val BubbleMetaIn = style(FontWeight.Normal, 9.5.sp)
    val BubbleMetaOut = style(FontWeight.Medium, 9.sp)
    val FileName = style(FontWeight.SemiBold, 11.5.sp)
    val FileMeta = style(FontWeight.Normal, 9.5.sp)

    val Notice = style(FontWeight.Medium, 10.5.sp)
    val Typing = style(FontWeight.Normal, 9.5.sp)
    val TabLabel = style(FontWeight.Medium, 9.sp)
    val TabLabelActive = style(FontWeight.SemiBold, 9.sp)
    val ComposePlaceholder = style(FontWeight.Normal, 12.5.sp)
    val AvatarInitials = style(FontWeight.SemiBold, 12.sp)
}

/**
 * The design defines a single dark palette, so there is no light variant to
 * switch to: every surface is Void/Surface/Ink. `darkTheme` is accepted (and
 * ignored) to keep the call sites that pass a system-derived flag compiling.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Theme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecureChatColorScheme,
        typography = SecureChatTypography,
        shapes = SecureChatShapes,
        content = content
    )
}

private val SecureChatColorScheme = darkColorScheme(
    primary = Skin.Signal,
    onPrimary = Skin.OnSignal,
    primaryContainer = Skin.RowSelected,
    onPrimaryContainer = Skin.Signal,
    secondary = Skin.Primary,
    onSecondary = Skin.OnPrimary,
    secondaryContainer = Skin.Primary,
    onSecondaryContainer = Skin.OnPrimary,
    tertiary = Skin.Amber,
    onTertiary = Skin.OnSignal,
    background = Skin.Void,
    onBackground = Skin.Ink,
    surface = Skin.Void,
    onSurface = Skin.Ink,
    surfaceVariant = Skin.Field,
    onSurfaceVariant = Skin.InkDim,
    outline = Skin.Line,
    outlineVariant = Skin.Line,
    error = Skin.Error,
    onError = Skin.OnSignal,
    errorContainer = Skin.ErrorSurface,
    onErrorContainer = Skin.Error,
    scrim = Color.Black
)

/**
 * Material's slots are mapped onto the design scale so screens that were written
 * against Material defaults (settings, profile, dialogs) inherit the new look
 * without being rewritten.
 */
private val SecureChatTypography = Typography(
    displayLarge = SkinType.Display,
    displayMedium = SkinType.Display,
    displaySmall = SkinType.Display,
    headlineLarge = SkinType.ScreenTitle,
    headlineMedium = SkinType.ScreenTitle,
    headlineSmall = SkinType.Title,
    titleLarge = SkinType.Title,
    titleMedium = SkinType.Title,
    titleSmall = SkinType.RowName,
    bodyLarge = SkinType.Body,
    bodyMedium = SkinType.Body,
    bodySmall = SkinType.RowSnippet,
    labelLarge = SkinType.ChipActive,
    labelMedium = SkinType.Chip,
    labelSmall = SkinType.Meta
)

private val SecureChatShapes = androidx.compose.material3.Shapes(
    extraSmall = Skin.Radius4,
    small = Skin.Radius8,
    medium = Skin.Radius14,
    large = Skin.Radius22,
    extraLarge = RoundedCornerShape(28.dp)
)
