package com.securechat.core.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class NetworkModule {
    private var httpClient: HttpClient? = null

    fun getClient(): HttpClient {
        return httpClient ?: createClient()
    }

    private fun createClient(): HttpClient {
        // OkHttp rather than the default Android engine: the realtime client
        // needs WebSocket support, which the Android (HttpURLConnection)
        // engine does not implement.
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }

            // Application-level pings so a half-open socket (NAT rebind, radio
            // drop) is detected rather than looking connected forever. The
            // server also pings every 30s, but only the client can notice that
            // its own sends have stopped landing.
            install(WebSockets) {
                pingIntervalMillis = 20_000
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("KtorClient", message)
                    }
                }
                // Full bodies (including Authorization headers) only in debug;
                // release logs nothing rather than risking token leakage.
                level = if (com.securechat.BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }

            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("User-Agent", "SecureChat/1.0 Android")
            }

            expectSuccess = false
        }

        httpClient = client
        return client
    }

    fun setAuthTokenProvider(provider: () -> String?) {
        // This would be used to add auth headers
        // In practice, we'd use a request interceptor
    }

    fun close() {
        httpClient?.close()
        httpClient = null
    }
}