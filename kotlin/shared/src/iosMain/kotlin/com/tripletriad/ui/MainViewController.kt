package com.tripletriad.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point consumed by `iosApp/ContentView.swift`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
