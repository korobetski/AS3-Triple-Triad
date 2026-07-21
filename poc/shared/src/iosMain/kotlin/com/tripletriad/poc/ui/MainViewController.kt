package com.tripletriad.poc.ui

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Main ViewController for iOS app.
 * This creates a UIKit view controller that hosts our Compose Multiplatform UI.
 */
fun MainViewController() = ComposeUIViewController {
    App()
}
