package com.emotionalchess

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emotionalchess.ui.App
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.FileGameStore
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.net.NearbyPeerLink
import java.io.File

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
                val vm: GameViewModel = viewModel {
                    GameViewModel(
                        FileGameStore(File(applicationContext.filesDir, "game.json")),
                        linkFactory = { NearbyPeerLink(applicationContext) },
                    )
                }
                App(vm)
            }
        }
    }
}
