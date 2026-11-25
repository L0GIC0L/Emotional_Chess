package com.emotionalchess.ui

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
import com.emotionalchess.engine.Mood

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A1B9A),
    secondary = Color(0xFF7B5E57),
    tertiary = Color(0xFF00796B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7A8F0),
    secondary = Color(0xFFD7C0B8),
    tertiary = Color(0xFF80CBC4),
)

@Composable
fun EmotionalChessTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed mood colours so the rings read the same on every device theme. */
fun moodColor(mood: Mood): Color? = when (mood) {
    Mood.CALM -> null
    Mood.UNAFFECTED -> Color(0xFF43A047)
    Mood.GUILTY -> Color(0xFFFF6D00)
    Mood.DEVASTATED -> Color(0xFF1E88E5)
    Mood.PSYCHOPATH -> Color(0xFFAA00FF)
}

val LightSquare = Color(0xFFF0D9B5)
val DarkSquare = Color(0xFFB58863)
