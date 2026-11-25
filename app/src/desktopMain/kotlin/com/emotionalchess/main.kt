package com.emotionalchess

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.emotionalchess.ui.App
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.FileStorage
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.ui.JsonGameStore
import com.emotionalchess.ui.saveFile

private fun appIcon(): BitmapPainter? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use { it.readBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

fun main() = application {
    val game = remember { GameViewModel(JsonGameStore(FileStorage(saveFile()))) }
    val icon = remember { appIcon() }
    Window(
        onCloseRequest = { game.close(); exitApplication() },
        title = "Emotional Chess",
        icon = icon,
        state = rememberWindowState(size = DpSize(1180.dp, 820.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(420, 640)
        EmotionalChessTheme { App(game) }
    }
}
