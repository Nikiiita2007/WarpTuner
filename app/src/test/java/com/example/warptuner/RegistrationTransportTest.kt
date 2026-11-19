package com.example.warptuner

import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class RegistrationTransportTest {
    private class FakeConnection(url: URL) : HttpsURLConnection(url) {
        override fun connect() = error("Tests must not connect")
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun configuresConnectionWithoutConnectingOrReplacingTlsValidation() {
        val url = URL("https://api.cloudflareclient.com/test")
        val connection = FakeConnection(url)
        val verifier = connection.hostnameVerifier
        val sockets = connection.sslSocketFactory
        var calls = 0
        val transport = HttpsRegistrationTransport(1234, 5678) { requested ->
            assertEquals(url, requested)
            calls++
            connection
        }
        assertSame(connection, transport.open(url))
        assertEquals(1, calls)
        assertEquals(1234, connection.connectTimeout)
        assertEquals(5678, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertSame(verifier, connection.hostnameVerifier)
        assertSame(sockets, connection.sslSocketFactory)
    }

    @Test fun rejectsPlaintextBeforeOpeningConnection() {
        val transport = HttpsRegistrationTransport { error("Must not open") }
        assertThrows(IllegalArgumentException::class.java) {
            transport.open(URL("http://api.cloudflareclient.com/test"))
        }
    }

    @Test fun rejectsUnboundedSocketTimeouts() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpsRegistrationTransport(connectTimeoutMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpsRegistrationTransport(readTimeoutMs = -1)
        }
    }
}
