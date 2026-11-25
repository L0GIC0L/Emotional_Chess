package com.emotionalchess

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Rules
import com.emotionalchess.ui.App
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.GameStore
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.ui.SaveData
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Renders the desktop app off-screen, as a smoke test and for the README. */
class DesktopRenderTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private fun vm(game: GameState?) = GameViewModel(
        object : GameStore {
            override fun load() = SaveData(game = game)
            override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean) = Unit
        },
        uiContext = Dispatchers.Unconfined,
    )

    private fun shot(name: String, width: Int, height: Int, game: GameViewModel) {
        val scene = ImageComposeScene(width, height, Density(1f)) { EmotionalChessTheme { App(game) } }
        // A few frames so animations and fonts settle.
        var image = scene.render(0)
        for (t in 1..20) image = scene.render(t * 50_000_000L)
        val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/desktop-shots").mkdirs()
        File("build/desktop-shots/$name.png").writeBytes(png)
        scene.close()
        assertTrue(png.size > 10_000)
    }

    private fun midGame(): GameState {
        var g = Rules.newGame(Mode.EMOTIONAL, "Ada", "Grace", seed = 1)
        for ((f, t) in listOf("e2" to "e4", "d7" to "d5", "e4" to "d5")) {
            g = Rules.play(g, Rules.legalMovesFrom(g, sq(f)).first { it.to == sq(t) })
        }
        g = Rules.applyRoll(g, 6)
        for ((f, t) in listOf("g8" to "f6", "b1" to "c3")) {
            g = Rules.play(g, Rules.legalMovesFrom(g, sq(f)).first { it.to == sq(t) })
        }
        return g
    }

    @Test fun newGame() = shot("new-game", 1180, 820, vm(null))

    @Test fun wideGame() = shot("game-wide", 1180, 820, vm(midGame()).also { it.onSquareTapped(sq("d5")) })

    @Test fun narrowGame() = shot("game-narrow", 440, 900, vm(midGame()))
}
