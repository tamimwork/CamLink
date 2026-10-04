package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FixedCyanPrimary,
    onPrimary = FixedNavyDeep,
    primaryContainer = FixedNavySurfaceVariant,
    onPrimaryContainer = FixedCyanAccent,
    secondary = FixedCyanAccent,
    onSecondary = FixedNavyDeep,
    secondaryContainer = FixedNavySurface,
    onSecondaryContainer = FixedTextPrimary,
    tertiary = FixedEmeraldLive,
    onTertiary = Color.White,
    tertiaryContainer = FixedNavySurfaceVariant,
    onTertiaryContainer = FixedEmeraldLive,
    background = FixedNavyDeep,
    onBackground = FixedTextPrimary,
    surface = FixedNavyDark,
    onSurface = FixedTextPrimary,
    surfaceVariant = FixedNavySurface,
    onSurfaceVariant = FixedTextSecondary,
    outline = FixedNavyBorder,
    outlineVariant = FixedNavySurfaceVariant,
    error = FixedRoseDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F9FF),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CamLinkPalette.isDark = darkTheme

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
