package ir.sanbuk.sdk.internal

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** What came back, or why nothing did. Never an exception. */
internal data class HttpResult(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/**
 * The smallest HTTP client that does the job.
 *
 * HttpURLConnection rather than OkHttp on purpose. An ad SDK's weight is a
 * publisher's weight, and a networking library is the single largest thing we
 * could add — for two GET requests. It also avoids the version conflict every
 * publisher who already ships OkHttp would otherwise inherit from us.
 *
 * Callers are responsible for being off the main thread; see [Worker].
 */
internal object Http {

    fun get(url: String, timeoutMs: Int = TIMEOUT_MS): HttpResult = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            // Redirects are the click path's business, and the click path is a
            // browser. Nothing here should ever chase one: following the click
            // redirect ourselves would spend the click and leave the visitor
            // on a page we opened rather than the one the tracker chose.
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var read = input.read(chunk)
                var total = 0
                while (read >= 0 && total < MAX_BODY_BYTES) {
                    buffer.write(chunk, 0, read)
                    total += read
                    read = input.read(chunk)
                }
                buffer.toString("UTF-8")
            }.orEmpty()

            HttpResult(status, body)
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrElse { HttpResult(status = 0, body = "") }

    /** Same rules, for a creative image rather than a decision. */
    fun getBytes(url: String, timeoutMs: Int = TIMEOUT_MS): ByteArray = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        try {
            if (connection.responseCode !in 200..299) return@runCatching ByteArray(0)
            connection.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read = input.read(chunk)
                var total = 0
                while (read >= 0 && total < MAX_IMAGE_BYTES) {
                    buffer.write(chunk, 0, read)
                    total += read
                    read = input.read(chunk)
                }
                buffer.toByteArray()
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrElse { ByteArray(0) }

    private const val TIMEOUT_MS = 8_000

    /** A banner that does not fit in this is a mistake upstream, not an image. */
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    /** An ad decision is a few hundred bytes; anything larger is not ours. */
    private const val MAX_BODY_BYTES = 64 * 1024
}
