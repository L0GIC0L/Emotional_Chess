package com.emotionalchess

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emotionalchess.net.NearbyPeerLink
import com.emotionalchess.ui.App
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.FileStorage
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.ui.JsonGameStore
import java.io.File

/** Keeps the game alive across rotations and folding. */
class GameHolder(context: Context) : ViewModel() {
    val game = GameViewModel(
        JsonGameStore(FileStorage(File(context.filesDir, "game.json"))),
        linkFactory = { NearbyPeerLink(context) },
    )

    override fun onCleared() = game.close()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so always use light system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Keep the screen awake while the phone sits next to the board.
            val view = LocalView.current
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            EmotionalChessTheme {
                val holder: GameHolder = viewModel { GameHolder(applicationContext) }
                App(holder.game)
            }
        }
    }
}
