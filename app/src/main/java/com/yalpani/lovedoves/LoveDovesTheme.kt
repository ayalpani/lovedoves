package com.yalpani.lovedoves

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LovePaper = Color(0xFFF7F5F0)
internal val LoveInk = Color(0xFF18201C)
internal val LoveBlush = Color(0xFFF7DFE4)
internal val LoveMist = Color(0xFFE6E9EC)
internal val LoveError = Color(0xFFE53935)

private val LoveDovesColors = lightColorScheme(
    primary = LoveInk,
    onPrimary = Color.White,
    primaryContainer = LoveMist,
    onPrimaryContainer = LoveInk,
    background = LovePaper,
    onBackground = LoveInk,
    surface = LovePaper,
    onSurface = LoveInk,
    surfaceVariant = LoveMist,
    onSurfaceVariant = LoveInk.copy(alpha = 0.72f),
    error = LoveError,
)

@Composable
internal fun LoveDovesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoveDovesColors,
        content = content,
    )
}
