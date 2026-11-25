package com.emotionalchess.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

private val NoHaptics = Haptics { }

@Composable
actual fun rememberHaptics(): Haptics = NoHaptics

private object NoPermissionNeeded : LinkPermission {
    override val denied: String? = null
    override fun run(action: () -> Unit) = action()
}

@Composable
actual fun rememberLinkPermission(): LinkPermission = NoPermissionNeeded

@Composable
actual fun displayFontFamily(): FontFamily = remember { FontFamily(Font("fonts/dm_serif_display.ttf")) }

actual val hintBudgetMs: Long = 1800L

/** A save file, written through a temp file so a crash never leaves half a save. */
class FileStorage(private val file: File) : TextStorage {
    override fun read(): String? = file.takeIf { it.exists() }?.readText()

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** %APPDATA%\EmotionalChess on Windows, ~/.emotionalchess elsewhere. */
fun saveFile(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (appData != null) File(appData, "EmotionalChess") else File(System.getProperty("user.home"), ".emotionalchess")
    return File(dir, "game.json")
}
