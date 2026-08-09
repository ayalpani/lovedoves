package com.yalpani.lovedoves

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LovePaper = Color(0xFFFFF9F7)
internal val LoveInk = Color(0xFF2B2124)
internal val LoveRose = Color(0xFFC85C72)
internal val LoveBlush = Color(0xFFF7DFE4)
internal val LoveMist = Color(0xFFF1EBED)
internal val LoveSuccess = Color(0xFF2D7154)
internal val LoveError = Color(0xFFB3261E)

private val LoveDovesColors = lightColorScheme(
    primary = LoveRose,
    onPrimary = Color.White,
    primaryContainer = LoveBlush,
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

