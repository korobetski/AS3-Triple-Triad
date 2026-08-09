package com.tripletriad.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tripletriad.data.SaveRepository
import com.tripletriad.log.Log
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerStores
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TranscriptQueue
import com.tripletriad.net.serverConnection
import com.tripletriad.net.serverEntries
import com.tripletriad.ui.App
import kotlin.system.exitProcess

/**
 * Desktop entry point. Not a migration target — it exists so the shared Compose
 * UI can be built and run on a developer machine without an emulator or Xcode.
 */
fun main() {
    val settings = DesktopSettingsStore()
    // `SaveRepository.COLLECTION` rather than the literal "saves": the shared module owns the
    // directory name, so the two hosts cannot drift apart on where a profile lives.
    val documents = DesktopDocumentStore(SaveRepository.COLLECTION)
    val server = buildServerConnection()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Triple Triad",
            state = rememberWindowState(size = DpSize(560.dp, 640.dp)),
        ) {
            App(
                store = settings,
                documents = documents,
                clock = JvmClock,
                onQuit = ::exitApplication,
                server = server,
            )
        }
    }
    // `application {}` returning is not the process ending. Compose shuts the window down and the
    // main thread falls out of the block, but the JVM lives until its last non-daemon thread does
    // — and this app has some: the HTTP client's engine keeps a pool alive whether or not a request
    // is in flight, and it outlives the window that was using it. Quit left an invisible process
    // behind. Nothing is lost by ending it here: every save goes through `SaveRepository` at the
    // point of the change, so anything that had to reach disk did so before the window closed.
    exitProcess(0)
}

/**
 * The server connection, or null when no server is configured.
 *
 * ### Where the addresses come from
 *
 * `-Dtto.servers=…`, then `TTO_SERVERS`, then the local container. Comma-separated, each entry
 * either an address or `Label=address` — the parsing is `serverEntries` in `:shared`, so this host
 * and the Android one derive the same ids from the same text and a player switching devices stays
 * signed in to the same servers.
 *
 * The default is deliberate and deliberately temporary: this target exists so the shared UI can be
 * run on a developer machine, the container it points at is the one `tto-server` brings up, and
 * requiring a flag to exercise Phase 5 would mean it mostly went unexercised. Set `TTO_SERVERS` to
 * an empty value to turn it off.
 *
 * The queue, the session and the chosen server each get their **own** store — see [ServerStores]
 * for why none of them may be the saves directory.
 */
private fun buildServerConnection(): ServerConnection? {
    val configured = System.getProperty("tto.servers")
        ?: System.getenv("TTO_SERVERS")
        ?: DEFAULT_SERVERS
    val servers = serverEntries(configured)
    if (servers.isEmpty()) {
        Log.i(TAG) { "no server configured; this build plays offline only" }
        return null
    }
    Log.i(TAG) { "${servers.size} server(s): ${servers.joinToString { it.baseUrl }}" }
    return serverConnection(
        stores = ServerStores(
            queue = DesktopDocumentStore(TranscriptQueue.COLLECTION),
            session = DesktopDocumentStore(SessionStore.COLLECTION),
            directory = DesktopDocumentStore(ServerDirectory.COLLECTION),
        ),
        servers = servers,
        clock = JvmClock,
    )
}

/** The container `tto-server`'s `compose.yaml` publishes. */
private const val DEFAULT_SERVERS = "Local=http://127.0.0.1:8080"

private const val TAG = "Host"
