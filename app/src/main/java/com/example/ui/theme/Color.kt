package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Holds the current theme. MyApplicationTheme updates it, and every colour below
 * that is "dynamic" reads it, so all screens switch between dark and light.
 */
object CamLinkPalette {
    var isDark by mutableStateOf(true)
}

// ---------- Fixed dark palette (camera screens, always dark) ----------
val FixedNavyDeep = Color(0xFF070B14)
val FixedNavyDark = Color(0xFF0C1322)
val FixedNavySurface = Color(0xFF131D31)
val FixedNavySurfaceVariant = Color(0xFF1B2842)
val FixedNavyBorder = Color(0xFF273859)

val FixedCyanAccent = Color(0xFF00E5FF)
val FixedCyanPrimary = Color(0xFF38BDF8)
val FixedCyanGlow = Color(0x3300E5FF)

val FixedEmeraldLive = Color(0xFF10B981)
val FixedEmeraldGlow = Color(0x3310B981)

val FixedAmberWarning = Color(0xFFF59E0B)
val FixedRoseDanger = Color(0xFFF43F5E)

val FixedTextPrimary = Color(0xFFF1F5F9)
val FixedTextSecondary = Color(0xFF94A3B8)
val FixedTextMuted = Color(0xFF64748B)

// ---------- Light palette ----------
val LightBackground = Color(0xFFF1F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EEF5)
val LightBorder = Color(0xFFCBD5E1)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

// ---------- Dynamic palette (follows the app theme) ----------
val NavyDeep: Color get() = if (CamLinkPalette.isDark) FixedNavyDeep else LightBackground
val NavyDark: Color get() = if (CamLinkPalette.isDark) FixedNavyDark else LightSurface
val NavySurface: Color get() = if (CamLinkPalette.isDark) FixedNavySurface else LightSurface
val NavySurfaceVariant: Color get() = if (CamLinkPalette.isDark) FixedNavySurfaceVariant else LightSurfaceVariant
val NavyBorder: Color get() = if (CamLinkPalette.isDark) FixedNavyBorder else LightBorder

val CyanAccent: Color get() = if (CamLinkPalette.isDark) FixedCyanAccent else Color(0xFF0891B2)
val CyanPrimary: Color get() = if (CamLinkPalette.isDark) FixedCyanPrimary else Color(0xFF0284C7)
val CyanGlow: Color get() = if (CamLinkPalette.isDark) FixedCyanGlow else Color(0x220891B2)

val EmeraldLive: Color get() = if (CamLinkPalette.isDark) FixedEmeraldLive else Color(0xFF059669)
val EmeraldGlow: Color get() = if (CamLinkPalette.isDark) FixedEmeraldGlow else Color(0x22059669)

val AmberWarning: Color get() = if (CamLinkPalette.isDark) FixedAmberWarning else Color(0xFFD97706)
val RoseDanger: Color get() = if (CamLinkPalette.isDark) FixedRoseDanger else Color(0xFFE11D48)

val TextPrimary: Color get() = if (CamLinkPalette.isDark) FixedTextPrimary else LightTextPrimary
val TextSecondary: Color get() = if (CamLinkPalette.isDark) FixedTextSecondary else LightTextSecondary
val TextMuted: Color get() = if (CamLinkPalette.isDark) FixedTextMuted else Color(0xFF64748B)
