package com.example.warptuner

import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Creates connections only; callers own request execution and disconnect in finally. */
internal fun interface RegistrationTransport {
    fun open(url: URL): HttpsURLConnection
}

/** Socket timeouts are not an overall registration deadline, nor a DNS deadline. */
internal class HttpsRegistrationTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val connectionFactory: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    },
) : RegistrationTransport {
    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
    }

    override fun open(url: URL): HttpsURLConnection {
        require(url.protocol == "https") { "Registration requires HTTPS" }
        return connectionFactory(url).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            useCaches = false
        }
    }
}
