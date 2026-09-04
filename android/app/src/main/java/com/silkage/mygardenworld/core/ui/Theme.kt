package com.silkage.mygardenworld.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 小云朵 web palette mirrored from web/src/app/globals.css. */
object CloudColors {
    val Coral = Color(0xFFFF6F61)
    val CoralDark = Color(0xFFFF8A75)
    val SkyBackground = Color(0xFFE9F8FF)
    val Ink = Color(0xFF17345F)
    val CardLight = Color(0xFFFBFDFF)
    val SecondaryLight = Color(0xFFE7F6FF)
    val SecondaryInk = Color(0xFF24527D)
    val MutedInk = Color(0xFF657B96)
    val Accent = Color(0xFFFFF1B8)
    val AccentInk = Color(0xFF714A00)
    val Destructive = Color(0xFFDF4F52)
    val BorderLight = Color(0xFFBFDDEC)
    val Ring = Color(0xFF2F87ED)

    val NightBackground = Color(0xFF101A31)
    val NightCard = Color(0xFF17223D)
    val NightSurfaceHigh = Color(0xFF1E2C4C)
    val NightInk = Color(0xFFEDF7FF)
    val NightMuted = Color(0xFF9AB0C9)
    val NightBorder = Color(0xFF2C3D5F)

    val Sky = Color(0xFF38BDF8)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF34D399)
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = CloudColors.Coral,
    onPrimary = Color(0xFFFFFAFA),
    primaryContainer = Color(0xFFFFE1DC),
    onPrimaryContainer = Color(0xFF5B1F19),
    secondary = CloudColors.SecondaryInk,
    onSecondary = Color.White,
    secondaryContainer = CloudColors.SecondaryLight,
    onSecondaryContainer = CloudColors.SecondaryInk,
    tertiary = CloudColors.AccentInk,
    tertiaryContainer = CloudColors.Accent,
    onTertiaryContainer = CloudColors.AccentInk,
    background = CloudColors.SkyBackground,
    onBackground = CloudColors.Ink,
    surface = CloudColors.CardLight,
    onSurface = CloudColors.Ink,
    surfaceVariant = Color(0xFFEAF6FB),
    onSurfaceVariant = CloudColors.MutedInk,
    surfaceContainer = Color(0xFFF1F9FD),
    surfaceContainerHigh = Color(0xFFE7F3FA),
    outline = CloudColors.BorderLight,
    outlineVariant = Color(0xFFD5E8F2),
    error = CloudColors.Destructive,
    onError = Color(0xFFFFF7F5),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = CloudColors.CoralDark,
    onPrimary = Color(0xFF3B120D),
    primaryContainer = Color(0xFF5B2A22),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFF9CCBFF),
    onSecondary = Color(0xFF0B2846),
    secondaryContainer = Color(0xFF243B60),
    onSecondaryContainer = Color(0xFFD6E8FF),
    tertiary = Color(0xFFFFD98A),
    tertiaryContainer = Color(0xFF4A3A0A),
    onTertiaryContainer = Color(0xFFFFEBB8),
    background = CloudColors.NightBackground,
    onBackground = CloudColors.NightInk,
    surface = CloudColors.NightCard,
    onSurface = CloudColors.NightInk,
    surfaceVariant = Color(0xFF223050),
    onSurfaceVariant = CloudColors.NightMuted,
    surfaceContainer = Color(0xFF1B2744),
    surfaceContainerHigh = CloudColors.NightSurfaceHigh,
    outline = CloudColors.NightBorder,
    outlineVariant = Color(0xFF243352),
    error = Color(0xFFFF7A7C),
    onError = Color(0xFF3B0A0B),
)

private val CloudShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun MyGardenWorldTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        shapes = CloudShapes,
        content = content,
    )
}
