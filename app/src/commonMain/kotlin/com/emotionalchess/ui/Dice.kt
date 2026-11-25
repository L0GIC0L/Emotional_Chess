package com.emotionalchess.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Rules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
private const val TUMBLE_MS = 1150L

private val PIPS = mapOf(
    1 to listOf(0.5f to 0.5f),
    2 to listOf(0.27f to 0.27f, 0.73f to 0.73f),
    3 to listOf(0.27f to 0.27f, 0.5f to 0.5f, 0.73f to 0.73f),
    4 to listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.27f to 0.73f, 0.73f to 0.73f),
    5 to listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.5f to 0.5f, 0.27f to 0.73f, 0.73f to 0.73f),
    6 to listOf(0.27f to 0.25f, 0.73f to 0.25f, 0.27f to 0.5f, 0.73f to 0.5f, 0.27f to 0.75f, 0.73f to 0.75f),
)

/** An ivory die face drawn with soft shading. A 1 gets a red pip, as on real dice. */
@Composable
fun DieFace(value: Int, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val corner = CornerRadius(s * 0.2f)
        drawRoundRect(Color(0x55000000), topLeft = Offset(s * 0.03f, s * 0.06f), cornerRadius = corner)
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFFFFBF4), Color(0xFFE7DCCB)), Offset.Zero, Offset(s, s)),
            cornerRadius = corner,
        )
        drawRoundRect(Color(0x33000000), cornerRadius = corner, style = Stroke(s * 0.02f))
        val pip = if (value == 1) Color(0xFFD7263D) else Color(0xFF231629)
        val r = if (value == 1) s * 0.12f else s * 0.085f
        for ((x, y) in PIPS.getValue(value)) drawCircle(pip, r, Offset(x * s, y * s))
    }
}

/**
 * The whole roll: choose (tap the die / enter a real roll) → tumble → reveal.
 * [reveal] is null until a value has been chosen.
 */
@Composable
fun DiceDialog(game: GameState, reveal: RollReveal?, onRoll: (Int?) -> Unit, onDone: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        DiceCard(game, reveal, onRoll, onDone)
    }
}

@Composable
fun DiceCard(game: GameState, reveal: RollReveal?, onRoll: (Int?) -> Unit, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    var landed by remember(reveal) { mutableStateOf(reveal != null && !reveal.animated) }
    var face by remember(reveal) { mutableIntStateOf(reveal?.roll ?: 6) }
    val spin = remember(reveal) { Animatable(0f) }
    val bounce = remember(reveal) { Animatable(1f) }

    LaunchedEffect(reveal) {
        if (reveal == null) return@LaunchedEffect
        if (reveal.animated) {
            launch { spin.animateTo(1080f, tween(TUMBLE_MS.toInt(), easing = FastOutSlowInEasing)) }
            // Flicker through faces, slowing down like a real die, then settle on the roll.
            var pause = 45L
            var elapsed = 0L
            while (elapsed + pause < TUMBLE_MS) {
                face = (1..6).filter { it != face }.random()
                delay(pause)
                elapsed += pause
                pause += 7
            }
            face = reveal.roll
            delay(TUMBLE_MS - elapsed + 120)
            landed = true
        }
        haptics.buzz(Buzz.HEAVY)
        bounce.snapTo(1.22f)
        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    val idle by rememberInfiniteTransition(label = "idle").animateFloat(
        -6f, 6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "idle",
    )
    val mood = reveal?.mood
    val accent = mood?.let { moodColor(it) } ?: MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.border(1.dp, accent.copy(alpha = if (landed) 0.8f else 0.3f), RoundedCornerShape(28.dp)),
    ) {
        Column(
            Modifier
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = if (landed) 0.28f else 0.12f), Color.Transparent),
                        radius = 700f,
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val pieceName = reveal?.pieceName ?: game.pendingRoll?.let { game.board[it]?.name } ?: "The piece"
            Text(
                if (landed && mood != null) "$pieceName is…" else "How does it feel?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (!landed && reveal == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$pieceName just made its first capture on ${game.pendingRoll?.let(Rules::squareName) ?: ""}. Roll to find out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))

            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        rotationZ = if (reveal == null) idle else spin.value
                        scaleX = bounce.value
                        scaleY = bounce.value
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = reveal == null) { onRoll(null) },
                contentAlignment = Alignment.Center,
            ) { DieFace(face, 112.dp) }

            Spacer(Modifier.height(22.dp))

            AnimatedContent(
                targetState = landed,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.85f)) togetherWith fadeOut(tween(150)) },
                label = "phase",
            ) { done ->
                if (done && mood != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(moodEmoji(mood, game.mode), fontSize = 52.sp, lineHeight = 60.sp)
                        Text(
                            Rules.moodLabel(mood, game.mode),
                            style = MaterialTheme.typography.displaySmall,
                            color = accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(Rules.moodDescription(mood, game.mode), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF140E1A)),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Continue", fontSize = 17.sp) }
                    }
                } else if (reveal == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { onRoll(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("🎲  Roll for me", fontSize = 17.sp)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Rolled a real die? Tap what it shows:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (n in 1..6) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onRoll(n) }
                                        .padding(2.dp)
                                ) { DieFace(n, 38.dp) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OddsRow(game.mode)
                    }
                } else {
                    Text(
                        "Rolling…", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OddsRow(mode: Mode) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, mood) in listOf("1" to Rules.moodForRoll(1), "2–3" to Rules.moodForRoll(2), "4–5" to Rules.moodForRoll(4), "6" to Rules.moodForRoll(6))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(3.dp))
                Text(moodEmoji(mood, mode), fontSize = 15.sp)
            }
        }
    }
}
