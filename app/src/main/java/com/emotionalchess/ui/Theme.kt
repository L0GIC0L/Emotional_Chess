package com.emotionalchess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.emotionalchess.R
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood

// A single candle-lit dark theme: the app lives next to a real board, often in the evening.
private val Colors = darkColorScheme(
    primary = Color(0xFFFF7096),
    onPrimary = Color(0xFF3E0618),
    primaryContainer = Color(0xFF5E1B35),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFC9B0FF),
    onSecondary = Color(0xFF2B1755),
    secondaryContainer = Color(0xFF3B2D57),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFFFC66E),
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF5A4110),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFF5D62),
    errorContainer = Color(0xFF6B1A20),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF110C17),
    onBackground = Color(0xFFEDE5F4),
    surface = Color(0xFF17111F),
    onSurface = Color(0xFFEDE5F4),
    surfaceVariant = Color(0xFF2C2238),
    onSurfaceVariant = Color(0xFFB9ACC9),
    surfaceContainerLowest = Color(0xFF0D0912),
    surfaceContainerLow = Color(0xFF1A1323),
    surfaceContainer = Color(0xFF211929),
    surfaceContainerHigh = Color(0xFF2A2134),
    surfaceContainerHighest = Color(0xFF342A40),
    outline = Color(0xFF6A5C7C),
    outlineVariant = Color(0xFF3E3350),
)

val DisplayFont = FontFamily(Font(R.font.dm_serif_display))

private val Type = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = DisplayFont),
        displayMedium = t.displayMedium.copy(fontFamily = DisplayFont),
        displaySmall = t.displaySmall.copy(fontFamily = DisplayFont),
        headlineLarge = t.headlineLarge.copy(fontFamily = DisplayFont),
        headlineMedium = t.headlineMedium.copy(fontFamily = DisplayFont),
        headlineSmall = t.headlineSmall.copy(fontFamily = DisplayFont),
        titleLarge = t.titleLarge.copy(fontFamily = DisplayFont, fontSize = 24.sp),
    )
}

@Composable
fun EmotionalChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type) {
        // Screens draw on a transparent backdrop, so set the default text colour explicitly.
        CompositionLocalProvider(LocalContentColor provides Colors.onBackground, content = content)
    }
}

/** Background behind every screen: a faint warm glow fading into ink. */
val BackdropBrush = Brush.verticalGradient(listOf(Color(0xFF241631), Color(0xFF140E1B), Color(0xFF0D0912)))

val LightSquare = Color(0xFFEADCC8)
val DarkSquare = Color(0xFF9479B0)
val LastMoveLight = Color(0xFFF6E7A8)
val LastMoveDark = Color(0xFFC59BC4)

/** Fixed mood colours so the glows read the same everywhere. */
fun moodColor(mood: Mood): Color? = when (mood) {
    Mood.CALM -> null
    Mood.UNAFFECTED -> Color(0xFF4CD58A)
    Mood.GUILTY -> Color(0xFFFF8A3D)
    Mood.DEVASTATED -> Color(0xFF4DA3FF)
    Mood.PSYCHOPATH -> Color(0xFFC04DFF)
    Mood.INFECTED -> Color(0xFF9BD53A)
    Mood.IMMUNE -> Color(0xFF3CCFCF)
}

fun moodEmoji(mood: Mood, mode: Mode): String = when (mood) {
    Mood.CALM -> if (mode == Mode.EMOTIONAL) "😇" else "🙂"
    Mood.UNAFFECTED -> "😌"
    Mood.GUILTY -> if (mode == Mode.EMOTIONAL) "😰" else "😔"
    Mood.DEVASTATED -> "😭"
    Mood.PSYCHOPATH -> "😈"
    Mood.INFECTED -> "🤢"
    Mood.IMMUNE -> "💪"
}
