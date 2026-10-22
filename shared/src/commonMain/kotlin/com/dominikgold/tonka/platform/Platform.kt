package com.dominikgold.tonka.platform

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import okio.Path

// Holds the Android Context; empty on the other platforms
expect class PlatformContext

expect fun createSqlDriver(context: PlatformContext): SqlDriver

expect fun appDataDirectory(context: PlatformContext): Path

expect fun createHttpClient(): HttpClient

interface SecretStore {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String?)
}

data class ProcessedImage(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
)

interface ImageProcessor {
    // Returns null when the bytes are not a decodable image
    suspend fun toJpeg(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        cropAspectRatio: Float? = null,
    ): ProcessedImage?
}

const val DATABASE_FILE_NAME = "tonka.db"

// A scan request can take minutes, far beyond the engines' default read timeouts
fun HttpClientConfig<*>.installDefaults() {
    install(HttpRedirect)
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val REQUEST_TIMEOUT_MILLIS = 5 * 60_000L
