package com.emotionalchess.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.emotionalchess.R
import com.emotionalchess.net.NearbyPeerLink
import java.io.File

private class ViewHaptics(private val view: View) : Haptics {
    override fun buzz(kind: Buzz) {
        view.performHapticFeedback(
            when (kind) {
                Buzz.TICK -> HapticFeedbackConstants.CLOCK_TICK
                Buzz.CLICK -> HapticFeedbackConstants.VIRTUAL_KEY
                Buzz.HEAVY -> HapticFeedbackConstants.LONG_PRESS
                Buzz.CONFIRM ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            }
        )
    }
}

@Composable
actual fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { ViewHaptics(view) }
}

private class AndroidLinkPermission(private val request: ((Boolean) -> Unit) -> Unit) : LinkPermission {
    override var denied by mutableStateOf<String?>(null)
    override fun run(action: () -> Unit) = request { granted ->
        if (granted) { denied = null; action() } else denied = "Two-phone play needs the Nearby devices permission."
    }
}

@Composable
actual fun rememberLinkPermission(): LinkPermission {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    // Previews and screenshot tests have no activity to ask through; there we just go ahead.
    val launcher = if (LocalActivityResultRegistryOwner.current != null) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.invoke(result.values.all { it })
            pending = null
        }
    } else null
    return remember(launcher) {
        AndroidLinkPermission { done ->
            if (launcher == null || NearbyPeerLink.hasPermissions(context)) done(true)
            else { pending = done; launcher.launch(NearbyPeerLink.permissions()) }
        }
    }
}

@Composable
actual fun displayFontFamily(): FontFamily = remember { FontFamily(Font(R.font.dm_serif_display)) }

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

actual val linkStyle: LinkStyle = LinkStyle.NEARBY

actual fun joinLink(code: String): String? = null

actual fun shareText(text: String): String? = null
