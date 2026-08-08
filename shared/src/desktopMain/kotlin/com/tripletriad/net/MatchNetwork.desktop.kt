package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

/**
 * CIO — Ktor's own coroutine-based engine.
 *
 * Chosen over OkHttp on the desktop because it is pure Kotlin and pulls in nothing: this target
 * exists so the shared UI can be run without an emulator, and it should not carry a JVM HTTP stack
 * to do it.
 */
internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = CIO
