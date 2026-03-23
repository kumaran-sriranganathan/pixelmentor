package com.pixelmentor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Colour tokens ─────────────────────────────────────────────────────────────

private val CameraBlue = Color(0xFF1A73E8)
private val CameraBlueContainer = Color(0xFFD3E3FD)
private val DarkSurface = Color(0xFF1C1B1F)

private val LightColorScheme = lightColorScheme(
    primary = CameraBlue,
    onPrimary = Color.White,
    primaryContainer = CameraBlueContainer,
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF535F70),
    surface = Color(0xFFFAFCFF),
    background = Color(0xFFFAFCFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF23478A),
    surface = DarkSurface,
    background = DarkSurface,
)

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun PixelMentorTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
