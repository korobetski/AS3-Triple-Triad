package com.tripletriad.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.log.Log
import java.awt.Desktop
import java.net.URI

/**
 * The desktop's own browser, via AWT.
 *
 * `isDesktopSupported` is checked rather than assumed: a headless JVM has no browser to hand off
 * to, and this game runs in CI as well as on a desktop.
 */
@Composable
actual fun rememberUrlOpener(): (String) -> Unit = remember {
    { url -> browse(url) }
}

@Suppress("TooGenericExceptionCaught")
private fun browse(url: String) {
    try {
        val desktop = Desktop.getDesktop().takeIf {
            Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.BROWSE)
        }
        if (desktop == null) {
            Log.w(TAG) { "this JVM cannot open a browser" }
            return
        }
        desktop.browse(URI(url))
    } catch (failure: Exception) {
        // A malformed URL, a sandbox, a desktop with no handler. None is worth an error in front
        // of a player who has already been told what to do.
        Log.w(TAG, failure) { "could not open the link" }
    }
}

private const val TAG = "OpenUrl"
