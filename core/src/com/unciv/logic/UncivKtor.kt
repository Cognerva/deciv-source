package com.unciv.logic

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object UncivKtor {
    /** Use the RoboVM-compatible transport only on iOS; desktop/server keep the JVM engine. */
    fun createClient(config: HttpClientConfig<*>.() -> Unit = {}) = HttpClient(
        if (Gdx.app?.type == Application.ApplicationType.iOS) {
            PlatformHttp.engineFactory ?: error("The iOS HTTP engine was not installed")
        } else CIO,
        config
    )

    val client = createClient {
        followRedirects = true
        install(HttpTimeout) {
            // Keep user-initiated network actions from waiting forever when a
            // platform transport cannot report a socket failure promptly.
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnException()
        }
        install(BodyProgress)

        defaultRequest {
            userAgent(UncivGame.getUserAgent())
        }
    }

    /**
     * Wrapper for [client.get][HttpClient.get] that returns `null` on failure
     *
     * @return [HttpResponse] on success and `null` on failure
     */
    suspend fun getOrNull(url: String, block: HttpRequestBuilder.() -> Unit = {}) = try {
        val resp = client.get(url, block)
        if (resp.status.isSuccess()) resp else null
    } catch (_: Throwable) {
        null
    }
}
