package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.logic.UncivKtor
import com.unciv.utils.Log
import com.unciv.utils.debug
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private typealias SendRequestCallback = (success: Boolean, result: String, code: Int?)->Unit

object SimpleHttp {
    fun sendGetRequest(url: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        sendRequest(Net.HttpMethods.GET, url, "", timeout, header, action)
    }

    fun sendRequest(method: String, url: String, content: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        val requestUrl = try {
            val uri = URI(url)
            if (uri.host == null) "http://$url" else url
        } catch (t: Throwable) {
            Log.debug("Bad URL", t)
            action(false, "Bad URL", null)
            return
        }

        try {
            val (success, text, responseCode) = runBlocking {
                withTimeout(timeout.toLong()) {
                    val response = UncivKtor.client.request(requestUrl) {
                        this.method = HttpMethod(method)
                        timeout {
                            requestTimeoutMillis = timeout.toLong()
                            connectTimeoutMillis = timeout.toLong()
                            socketTimeoutMillis = timeout.toLong()
                        }
                        headers {
                            set(HttpHeaders.UserAgent, UncivGame.getUserAgent("Turn-Checker"))
                            set(HttpHeaders.ContentType, "text/plain")
                            for ((key, value) in header.orEmpty()) set(key, value)
                        }
                        if (content.isNotEmpty()) setBody(content)
                    }
                    Triple(response.status.isSuccess(), response.bodyAsText(), response.status.value)
                }
            }
            action(success, text, responseCode)
        } catch (t: Throwable) {
            debug("Error during HTTP request", t)
            val errorMessageToReturn = t.message ?: t.toString()
            debug("Returning error message [%s]", errorMessageToReturn)
            action(false, errorMessageToReturn, null)
        }
    }

    fun getIpAddress(): String? {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            return socket.localAddress.hostAddress
        }
    }
}
