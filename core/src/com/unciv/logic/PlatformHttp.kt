package com.unciv.logic

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory

/** Platform-provided HTTP transport for targets whose JVM networking is incomplete. */
object PlatformHttp {
    var engineFactory: HttpClientEngineFactory<HttpClientEngineConfig>? = null
}
