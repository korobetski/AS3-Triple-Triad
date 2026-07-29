package com.tripletriad.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tripletriad.log.Log
import com.tripletriad.log.LogLevel
import com.tripletriad.ui.App
import android.util.Log as AndroidLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        installLogcatSink()
        // The store is built here because this is where the `Context` is. `:shared` deliberately
        // has no platform file access of its own — see `SettingsStore`.
        val settings = AndroidSettingsStore(applicationContext)
        // `finish()` and not `finishAffinity()` or `exitProcess`: this is the only activity, and
        // Android's own guidance is to leave the process alive for the system to reclaim. The
        // system back gesture is handled inside `App` and does not reach here except from the menu.
        setContent { App(store = settings, onQuit = { finish() }) }
    }

    /**
     * Points [Log] at logcat.
     *
     * This is the whole Android side of the logging story, and the reason the plan's Napier
     * dependency was not taken: forwarding to `android.util.Log` is four lines, and doing it here
     * keeps `:shared` free of any Android import. Release builds are held at [LogLevel.INFO] so
     * the debug lines cost nothing in the field — the lambda is never invoked, so the strings are
     * never built.
     *
     * The debuggable flag comes from `ApplicationInfo` rather than from `BuildConfig.DEBUG`,
     * because `BuildConfig` is not generated: AGP 9 makes it opt-in via
     * `buildFeatures.buildConfig`, and enabling a code-generation feature to read one boolean that
     * the manifest already carries is the wrong trade.
     */
    private fun installLogcatSink() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Log.install(
            minLevel = if (debuggable) LogLevel.DEBUG else LogLevel.INFO,
            sink = { level, tag, message, error ->
                when (level) {
                    LogLevel.DEBUG -> AndroidLog.d(tag, message, error)
                    LogLevel.INFO -> AndroidLog.i(tag, message, error)
                    LogLevel.WARN -> AndroidLog.w(tag, message, error)
                    LogLevel.ERROR -> AndroidLog.e(tag, message, error)
                }
            },
        )
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
