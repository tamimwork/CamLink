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
    primary = CyanPrimary,
    onPrimary = NavyDeep,
    primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = CyanAccent,
    secondary = CyanAccent,
    onSecondary = NavyDeep,
    secondaryContainer = NavySurface,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    tertiaryContainer = NavySurfaceVariant,
    onTertiaryContainer = EmeraldLive,
    background = NavyDeep,
    onBackground = TextPrimary,
    surface = NavyDark,
    onSurface = TextPrimary,
    surfaceVariant = NavySurface,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder,
    outlineVariant = NavySurfaceVariant,
    error = RoseDanger,
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
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = RoseDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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
