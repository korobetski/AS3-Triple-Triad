package com.tripletriad.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tripletriad.data.SaveRepository
import com.tripletriad.log.Log
import com.tripletriad.log.LogLevel
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerStores
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TranscriptQueue
import com.tripletriad.net.serverConnection
import com.tripletriad.net.serverEntries
import com.tripletriad.ui.App
import kotlin.system.exitProcess
import android.util.Log as AndroidLog

class MainActivity : ComponentActivity() {
    /**
     * Held so it can be released.
     *
     * `SoundPool` holds decoded PCM and `MediaPlayer` holds a codec; both are finite system
     * resources that outlive a garbage collection, so leaving them to the collector leaks them for
     * as long as the process lives. `lateinit` rather than nullable because `onCreate` always runs
     * before `onDestroy`.
     */
    private lateinit var audio: AndroidAudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        installLogcatSink()
        // The store is built here because this is where the `Context` is. `:shared` deliberately
        // has no platform file access of its own — see `SettingsStore`.
        val settings = AndroidSettingsStore(applicationContext)
        // `SaveRepository.COLLECTION` rather than the literal "saves": the shared module owns the
        // directory name, so the two hosts cannot drift apart on where a profile lives.
        val documents = AndroidDocumentStore(applicationContext, SaveRepository.COLLECTION)
        audio = AndroidAudioPlayer(applicationContext)
        val server = buildServerConnection()
        setContent {
            App(
                store = settings,
                documents = documents,
                clock = AndroidClock,
                audio = audio,
                onQuit = ::quit,
                server = server,
            )
        }
    }

    /**
     * Quit means quit: the task leaves the recents list and the process ends.
     *
     * `finish()` alone was not enough, and the difference is visible. It tears down the activity
     * but leaves the process — and the task — alive, so the app is still in recents, still holding
     * its `SoundPool` and its HTTP client, and reopening it lands on a *restored* activity rather
     * than a cold start. A player who chose Quit from the main menu did not ask for that.
     *
     * `finishAndRemoveTask` does the visible half and [exitProcess] the rest. Android's guidance is
     * to leave the process for the system to reclaim, and that guidance is right for an app the
     * user *navigates away from* — it is what makes the next launch fast. It does not apply to an
     * explicit Quit button, which the platform has no gesture for and which this game has because
     * the original did (`MenuScreen`'s `exitBtn`, `NativeApplication.exit()`).
     *
     * [onDestroy] still releases the audio, because `finishAndRemoveTask` runs the lifecycle: the
     * process ends after the codecs are handed back, not instead of.
     */
    private fun quit() {
        finishAndRemoveTask()
        exitProcess(0)
    }

    /**
     * The server connection, or null when this build has no server configured.
     *
     * The *list* comes from a resource — see `res/values/server.xml` — so a build can offer one
     * host, several, or none without a code change, and so the release build ships with none
     * compiled in. Which of them is in play is the player's, and is stored.
     *
     * The queue, the session and the chosen server each get **their own** store — see
     * `ServerStores`. The collection names come from `:shared`, so the two hosts cannot drift
     * apart on where anything lives.
     */
    // Named apart from the `serverConnection` it calls: a member function shadows a top-level one
    // of the same name outright in Kotlin, so sharing the name would not overload — it would hide.
    private fun buildServerConnection(): ServerConnection? {
        val servers = serverEntries(getString(R.string.servers))
        if (servers.isEmpty()) {
            Log.i(TAG) { "no server configured; this build plays offline only" }
            return null
        }
        Log.i(TAG) { "${servers.size} server(s): ${servers.joinToString { it.baseUrl }}" }
        return serverConnection(
            stores = ServerStores(
                queue = AndroidDocumentStore(applicationContext, TranscriptQueue.COLLECTION),
                session = AndroidDocumentStore(applicationContext, SessionStore.COLLECTION),
                directory = AndroidDocumentStore(applicationContext, ServerDirectory.COLLECTION),
            ),
            servers = servers,
            clock = AndroidClock,
        )
    }

    /**
     * Silences the music while the app is not in front, and brings it back when it is.
     *
     * Not in the original, which had no notion of being backgrounded — AIR on a desktop never was.
     * A game that keeps playing its theme over whatever the user switched to is a bug on a phone.
     *
     * **Pause, not stop.** Backgrounding does not change the composition, so the effect in `App`
     * that starts the music would not fire again on the way back and the match would return silent.
     * Pausing also keeps the position, so a return does not replay the sixteen-second intro.
     */
    override fun onStop() {
        super.onStop()
        audio.pauseMusic()
    }

    override fun onStart() {
        super.onStart()
        audio.resumeMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
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

    private companion object {
        const val TAG = "Host"
    }
}
