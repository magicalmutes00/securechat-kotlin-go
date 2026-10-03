package com.securechat.core.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
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
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }

            install(WebSockets)

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