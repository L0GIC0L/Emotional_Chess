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

actual val linkStyle: LinkStyle = LinkStyle.ONLINE

private fun pageAddress(): String = js("window.location.origin + window.location.pathname")

actual fun joinLink(code: String): String? = pageAddress() + "#join=" + code

private fun canShare(): Boolean = js("typeof navigator.share === 'function' && /Android|iPhone|iPad/i.test(navigator.userAgent)")

private fun share(text: String): Unit = js("{ navigator.share({ url: text }).catch(function () {}); }")

private fun canCopy(): Boolean = js("!!(navigator.clipboard && navigator.clipboard.writeText)")

private fun copy(text: String): Unit = js("{ navigator.clipboard.writeText(text).catch(function () {}); }")

/** Phones get the share sheet; computers get the link copied. */
actual fun shareText(text: String): String? = when {
    canShare() -> { share(text); "Shared" }
    canCopy() -> { copy(text); "Link copied" }
    else -> null
}
