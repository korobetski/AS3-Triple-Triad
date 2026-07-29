package com.tripletriad.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tripletriad.ui.App

/**
 * Desktop entry point. Not a migration target — it exists so the shared Compose
 * UI can be built and run on a developer machine without an emulator or Xcode.
 */
fun main() {
    val settings = DesktopSettingsStore()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Triple Triad",
            state = rememberWindowState(size = DpSize(480.dp, 420.dp)),
        ) {
            App(store = settings, onQuit = ::exitApplication)
        }
    }
}
