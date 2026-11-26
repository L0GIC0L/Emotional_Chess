package com.emotionalchess.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** What each platform provides: phones buzz and link over Nearby; desktop and web do neither. */

enum class Buzz { TICK, CLICK, HEAVY, CONFIRM }

fun interface Haptics {
    fun buzz(kind: Buzz)
}

@Composable
expect fun rememberHaptics(): Haptics

/** Runs a two-phone action, asking for the Nearby permission first where the platform needs it. */
interface LinkPermission {
    val denied: String?
    fun run(action: () -> Unit)
}

@Composable
expect fun rememberLinkPermission(): LinkPermission

/** DM Serif Display, loaded the way each platform loads fonts. */
@Composable
expect fun displayFontFamily(): FontFamily

/** How this platform links two players: phones in the same room, browsers online, or not at all. */
enum class LinkStyle { NONE, NEARBY, ONLINE }

expect val linkStyle: LinkStyle

/** A link that opens the game and joins room [code], or null where the platform has no links. */
expect fun joinLink(code: String): String?

/** Hands [text] to the share sheet, or copies it. Returns a word for what happened, or null if neither worked. */
expect fun shareText(text: String): String?

/** How long a hint may think. A browser has a single thread, so it thinks for less there. */
expect val hintBudgetMs: Long
