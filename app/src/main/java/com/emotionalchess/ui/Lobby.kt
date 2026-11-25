package com.emotionalchess.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Before a two-phone game starts: the host waits, the guest picks a host. */
@Composable
fun LobbyScreen(vm: GameViewModel) {
    val o = vm.online ?: return
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Radar(Modifier.size(180.dp), emoji = if (o.role == Role.HOST) "📡" else "🔎")
            val (title, text) = when {
                o.role == Role.HOST -> "Waiting for your friend" to
                    "On the other phone, open Emotional Chess, choose Two phones and tap Join a friend. Keep Bluetooth and Wi-Fi on."
                o.phase == LinkPhase.CONNECTING -> "Joining…" to
                    "Waiting for the host to accept."
                else -> "Looking for games nearby" to
                    "Ask your friend to choose Two phones and tap Host on their phone."
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(text, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)

            if (o.role == Role.GUEST && o.code != null) PairingCode(o.code, "The same code should show on your friend's phone.")

            if (o.role == Role.GUEST && o.phase == LinkPhase.LOOKING) {
                for (e in o.found) {
                    Surface(
                        onClick = { vm.connectTo(e) },
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceContainer,
                        contentColor = scheme.onSurface,
                        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📱", fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Tap to join", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            o.error?.let { Text("⚠️ $it", color = scheme.error, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::leaveOnline) { Text("Cancel") }
        }
    }

    val request = o.request
    if (o.role == Role.HOST && request != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("${request.name} wants to join") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    PairingCode(o.code ?: "", "Check that their phone shows the same code.")
                }
            },
            confirmButton = { Button(onClick = { vm.answerJoinRequest(true) }) { Text("Let them in") } },
            dismissButton = { TextButton(onClick = { vm.answerJoinRequest(false) }) { Text("Decline") } },
        )
    }
}

@Composable
private fun PairingCode(code: String, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(code, style = MaterialTheme.typography.displayMedium, letterSpacing = 8.sp, color = MaterialTheme.colorScheme.tertiary)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Rings spreading out from the middle while the phones look for each other. */
@Composable
private fun Radar(modifier: Modifier, emoji: String) {
    val colour = MaterialTheme.colorScheme.primary
    val t by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "radar",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                drawCircle(colour.copy(alpha = (1f - p) * 0.6f), radius = size.minDimension / 2 * (0.25f + 0.75f * p), style = Stroke(3.dp.toPx()))
            }
        }
        Text(emoji, fontSize = 44.sp)
    }
}
