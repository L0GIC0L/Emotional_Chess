package com.emotionalchess.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import kotlinx.browser.localStorage

private val NoHaptics = Haptics { }

@Composable
actual fun rememberHaptics(): Haptics = NoHaptics

private object NoPermissionNeeded : LinkPermission {
    override val denied: String? = null
    override fun run(action: () -> Unit) = action()
}

@Composable
actual fun rememberLinkPermission(): LinkPermission = NoPermissionNeeded

/** Fonts are fetched before the first frame; see main.kt. */
object WebFonts {
    var display: FontFamily? = null
}

@Composable
actual fun displayFontFamily(): FontFamily = WebFonts.display ?: FontFamily.Serif

/** The browser runs the hint search on its only thread, so keep the pause short. */
actual val hintBudgetMs: Long = 700L

/** The save lives in the browser's local storage. */
class BrowserStorage(private val key: String) : TextStorage {
    override fun read(): String? = localStorage.getItem(key)
    override fun write(text: String) = localStorage.setItem(key, text)
}
