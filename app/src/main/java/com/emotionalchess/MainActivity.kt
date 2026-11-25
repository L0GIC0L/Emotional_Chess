package com.emotionalchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emotionalchess.ui.App
import com.emotionalchess.ui.EmotionalChessTheme
import com.emotionalchess.ui.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Keep the screen awake while the phone sits next to the board.
            val view = LocalView.current
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            EmotionalChessTheme {
                val vm: GameViewModel = viewModel()
                App(vm)
            }
        }
    }
}
