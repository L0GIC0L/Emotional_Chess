package com.emotionalchess

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import com.emotionalchess.ui.App
import com.emotionalchess.ui.BrowserStorage
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.ui.JsonGameStore
import com.emotionalchess.ui.WebFonts
import com.emotionalchess.net.PeerJsLink
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response

private suspend fun fetchBytes(url: String): ByteArray {
    val response = window.fetch(url).await<Response>()
    val buffer = response.arrayBuffer().await<ArrayBuffer>()
    val bytes = Int8Array(buffer)
    return ByteArray(bytes.length) { bytes[it] }
}

/** An invitation link ends in #join=CODE; read it once, then tidy the address bar. */
private fun invitationCode(): String? {
    val hash = window.location.hash
    if (!hash.startsWith("#join=")) return null
    clearHash()
    return hash.removePrefix("#join=").filter(Char::isLetterOrDigit).uppercase().ifEmpty { null }
}

private fun clearHash(): Unit = js("history.replaceState(null, '', window.location.pathname + window.location.search)")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val game = GameViewModel(JsonGameStore(BrowserStorage("emotional-chess-game")), linkFactory = { PeerJsLink() })
    val joinCode = invitationCode()
    // Following an invitation replaces whatever game was open, unless this page is already in that room.
    joinCode?.let(game::acceptInvitation)
    ComposeViewport(document.body!!) {
        val fonts = LocalFontFamilyResolver.current
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // A canvas app gets no system fonts: bring the display font, and an emoji font that the
            // text falls back to for every feeling on the board.
            runCatching { WebFonts.display = FontFamily(Font("DMSerifDisplay", fetchBytes("fonts/dm_serif_display.ttf"))) }
            runCatching { fonts.preload(FontFamily(Font("NotoColorEmoji", fetchBytes("fonts/emoji.ttf")))) }
            runCatching { fonts.preload(FontFamily(Font("DejaVuSansSymbols", fetchBytes("fonts/symbols.ttf")))) }
            ready = true
        }
        if (ready) EmotionalChessTheme { App(game, joinCode) }
        else Box(Modifier.fillMaxSize().background(Color(0xFF140E1B)))
    }
}
