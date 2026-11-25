package com.securechat.presentation.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.securechat.R

object AppleColor {
    /* Backgrounds */
    val SystemBackground = Color(0xFF000000)
    val SystemGroupedBackground = Color(0xFF0F0F11)
    val SystemSecondaryBackground = Color(0xFF1A1A1D)
    val SystemTertiaryBackground = Color(0xFF2A2A2E)

    /* Semantic fills */
    val SystemBlue = Color(0xFF007AFF)
    val SystemBlueSurface = Color(0x1A007AFF)
    val SystemGreen = Color(0xFF30D158)
    val SystemRed = Color(0xFFFF453A)
    val SystemOrange = Color(0xFFFF9F0A)
    val SystemYellow = Color(0xFFFFD60A)
    val SystemPurple = Color(0xFFBF5AF2)
    val SystemPink = Color(0xFFFF375F)
    val SystemTeal = Color(0xFF64D2CA)
    val SystemIndigo = Color(0xFF5E5CE6)

    /* Text */
    val Label = Color(0xFFFFFFFF)
    val Label2 = Color(0xFFEBEBF5)
    val Label3 = Color(0xFF8E8E93)
    val Label4 = Color(0xFF636366)
    val Link = Color(0xFF0A84FF)

    /* Dividers & hairlines */
    val Separator = Color(0xFF2C2C2E)
    val SeparatorOpaque = Color(0xFF38383A)

    /* Surface & chrome */
    val SystemFill = Color(0xFF2C2C2E)
    val SystemFillSecondary = Color(0xFF1C1C1E)
    val SystemGray = Color(0xFF48484A)
    val SystemGray2 = Color(0xFF3A3A3C)
    val SystemGray3 = Color(0xFF2C2C2E)
    val SystemGray4 = Color(0xFF28282A)
    val SystemGray5 = Color(0xFF202021)
    val SystemGray6 = Color(0xFF151516)

    /* Error surface */
    val SystemRedSurface = Color(0xFF2B0A0A)

    /* Grouped list cells and sheets (iOS elevated surfaces) */
    val SystemElevatedBackground = Color(0xFF1C1C1E)
    val SystemSheetBackground = Color(0xFF1C1C1E)
    val SystemGroupedCell = Color(0xFF1C1C1E)
    val SystemGroupedCellPressed = Color(0xFF2C2C2E)

    /* Chat bubbles */
    val BubbleIncoming = Color(0xFF26262A)
    val BubbleOutgoing = SystemBlue

    /* Controls */
    val ControlBackground = Color(0xFF2C2C2E)
    val Chevron = Color(0xFF5C5C61)
    val SystemBluePressed = Color(0xFF0058D6)
}

object AppleSpace {
    val Xxxs = 2.dp
    val Xxs = 4.dp
    val Xs = 6.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val Huge = 40.dp
}

object AppleRadius {
    val Pill = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(22.dp)
    val Sheet = RoundedCornerShape(28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Field = RoundedCornerShape(13.dp)
    val Tiny = RoundedCornerShape(6.dp)
}

/** Inter, bundled in `res/font`. Stands in for SF Pro, which cannot be redistributed. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

object AppleType {
    private fun style(
        weight: FontWeight,
        size: TextUnit,
        leading: TextUnit = 1.0.em,
        tracking: TextUnit = 0.sp
    ) = TextStyle(
        /* SF Pro is not licensable for redistribution. Inter is the closest
           freely-licensed neo-grotesque and is already bundled in res/font. */
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = size,
        lineHeight = leading,
        letterSpacing = tracking
    )

    val LargeTitle = style(FontWeight.Bold, 34.sp, 1.0.em, (-0.02).em)
    val Title1 = style(FontWeight.Bold, 28.sp, 1.02.em, (-0.02).em)
    val Title2 = style(FontWeight.Bold, 22.sp, 1.05.em, (-0.015).em)
    val Title3 = style(FontWeight.SemiBold, 20.sp, 1.08.em, (-0.01).em)

    val Body = style(FontWeight.Normal, 17.sp, 1.2.em, 0.sp)
    val Body2 = style(FontWeight.Normal, 15.sp, 1.2.em, 0.sp)
    val Callout = style(FontWeight.Normal, 16.sp, 1.25.em, 0.sp)
    val Subhead = style(FontWeight.Normal, 15.sp, 1.2.em, 0.sp)
    val Footnote = style(FontWeight.Normal, 13.sp, 1.2.em, 0.sp)
    val Caption1 = style(FontWeight.Normal, 12.sp, 1.2.em, 0.02.em)
    val Caption2 = style(FontWeight.Normal, 11.sp, 1.2.em, 0.02.em)

    val ScreenTitle = style(FontWeight.Bold, 28.sp, 1.02.em, (-0.02).em)
    val SectionTitle = style(FontWeight.SemiBold, 13.sp, 1.2.em, 0.08.em)
    val ListItemTitle = style(FontWeight.SemiBold, 17.sp, 1.2.em, 0.sp)
    val ListItemSubtitle = style(FontWeight.Normal, 15.sp, 1.2.em, 0.sp)
    val Bubble = style(FontWeight.Normal, 15.sp, 1.15.em, 0.sp)
    val BubbleMeta = style(FontWeight.Normal, 12.sp, 1.2.em, 0.02.em)
    val TabLabel = style(FontWeight.Medium, 10.sp, 1.2.em, 0.04.em)
    val Button = style(FontWeight.SemiBold, 17.sp, 1.2.em, 0.sp)
}

object AppleSpring {
    val Default = spring<Float>(dampingRatio = 1.0f, stiffness = 220f)
    val Fast = spring<Float>(dampingRatio = 1.0f, stiffness = 500f)
    val Standard = spring<Float>(dampingRatio = 1.0f, stiffness = 300f)
    val Slow = spring<Float>(dampingRatio = 1.0f, stiffness = 150f)
    val Bouncy = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
}

@Composable
fun AppleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppleColorScheme,
        typography = AppleTypography,
        shapes = AppleShapes,
        content = content
    )
}

private val AppleColorScheme = darkColorScheme(
    primary = AppleColor.SystemBlue,
    onPrimary = Color.White,
    primaryContainer = AppleColor.SystemBlueSurface,
    onPrimaryContainer = AppleColor.SystemBlue,
    secondary = AppleColor.SystemGray2,
    onSecondary = AppleColor.Label,
    secondaryContainer = AppleColor.SystemFill,
    onSecondaryContainer = AppleColor.Label,
    tertiary = AppleColor.SystemTeal,
    onTertiary = Color.White,
    background = AppleColor.SystemBackground,
    onBackground = AppleColor.Label,
    surface = AppleColor.SystemBackground,
    onSurface = AppleColor.Label,
    surfaceVariant = AppleColor.SystemSecondaryBackground,
    onSurfaceVariant = AppleColor.Label2,
    outline = AppleColor.Separator,
    outlineVariant = AppleColor.SeparatorOpaque,
    error = AppleColor.SystemRed,
    onError = Color.White,
    errorContainer = AppleColor.SystemRed,
    onErrorContainer = Color.White,
    scrim = Color(0x80000000)
)

private val AppleTypography = androidx.compose.material3.Typography(
    displayLarge = AppleType.LargeTitle,
    displayMedium = AppleType.Title1,
    displaySmall = AppleType.Title2,
    headlineLarge = AppleType.ScreenTitle,
    headlineMedium = AppleType.Title2,
    headlineSmall = AppleType.Title3,
    titleLarge = AppleType.ListItemTitle,
    titleMedium = AppleType.ListItemTitle,
    titleSmall = AppleType.Subhead,
    bodyLarge = AppleType.Body,
    bodyMedium = AppleType.Body2,
    bodySmall = AppleType.Callout,
    labelLarge = AppleType.Caption1,
    labelMedium = AppleType.Caption2,
    labelSmall = AppleType.Footnote
)

private val AppleShapes = androidx.compose.material3.Shapes(
    extraSmall = AppleRadius.Tiny,
    small = AppleRadius.Field,
    medium = AppleRadius.Pill,
    large = AppleRadius.Card,
    extraLarge = AppleRadius.Sheet
)
