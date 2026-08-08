package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/**
 * Darwin, over `NSURLSession` — the only engine iOS will let a background request survive on, and
 * the one that inherits the system's proxy, certificate and ATS configuration rather than
 * reimplementing it.
 *
 * Compiled only on macOS hosts; see the target declarations in `shared/build.gradle.kts`.
 */
internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin
