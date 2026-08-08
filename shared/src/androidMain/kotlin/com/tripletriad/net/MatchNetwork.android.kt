package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * OkHttp, which is what Android has: it is what the platform's own networking libraries are built
 * on, and using anything else would mean a second connection pool and a second TLS stack in the
 * same process.
 */
internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
