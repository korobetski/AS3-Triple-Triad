package com.tripletriad.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point consumed by `iosApp/ContentView.swift`, which calls it as
 * `MainViewControllerKt.MainViewController()`.
 *
 * The name has to stay PascalCase: it is a factory returning a `UIViewController` and
 * Swift call sites read it as a type constructor, which is the convention the whole
 * Compose Multiplatform iOS template follows.
 */
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
