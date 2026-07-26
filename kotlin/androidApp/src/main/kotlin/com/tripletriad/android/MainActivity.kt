package com.tripletriad.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tripletriad.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        setContent { App() }
    }

    /**
     * Hides the status bar, the navigation bar and the clock/battery/signal row.
     *
     * Nine card-sized tiles plus two hands need every dp on a phone, and the AS3 original was
     * `fullScreen` too (`application.xml`). `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps the
     * bars reachable with an edge swipe, so nothing is actually taken away from the user —
     * `BEHAVIOR_DEFAULT` would let the first tap anywhere near an edge bring them back and stay
     * back.
     *
     * `setDecorFitsSystemWindows(false)` is what stops Compose from reserving the (now hidden)
     * bar space; without it the bars vanish and the app keeps a blank strip where they were.
     */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
