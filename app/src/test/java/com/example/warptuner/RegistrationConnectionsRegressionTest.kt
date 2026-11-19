package com.example.warptuner

import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationConnectionsRegressionTest {
    private class FakeConnection(private val failDisconnect: Boolean = false) :
        HttpsURLConnection(URL("https://example.invalid")) {
        val disconnects = AtomicInteger()
        override fun connect() = error("Tests must not use the network")
        override fun disconnect() {
            disconnects.incrementAndGet()
            if (failDisconnect) throw IllegalStateException("Fake disconnect failure")
        }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test(timeout = 15000) fun concurrentAttachAndCancelDoNotLoseConnection() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val connections = RegistrationConnections()
                val connection = FakeConnection()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val attach = pool.submit {
                    ready.countDown()
                    check(start.await(3, TimeUnit.SECONDS))
                    connections.attach(connection)
                }
                val cancel = pool.submit {
                    ready.countDown()
                    check(start.await(3, TimeUnit.SECONDS))
                    connections.cancel()
                }
                try {
                    assertTrue(ready.await(3, TimeUnit.SECONDS))
                    start.countDown()
                    attach.get(3, TimeUnit.SECONDS)
                    cancel.get(3, TimeUnit.SECONDS)
                    connections.cancel()
                    assertEquals(1, connection.disconnects.get())
                } finally {
                    start.countDown()
                }
            }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun staleDetachDoesNotRemoveReplacementConnection() {
        val connections = RegistrationConnections()
        val first = FakeConnection()
        val second = FakeConnection()
        connections.attach(first)
        connections.detach(first)
        connections.attach(second)
        connections.detach(first)
        connections.cancel()
        assertEquals(0, first.disconnects.get())
        assertEquals(1, second.disconnects.get())
    }

    @Test fun cancellingOneSessionDoesNotDisconnectAnother() {
        val first = RegistrationConnections()
        val second = RegistrationConnections()
        val firstConnection = FakeConnection()
        val secondConnection = FakeConnection()
        first.attach(firstConnection)
        second.attach(secondConnection)
        first.cancel()
        assertEquals(1, firstConnection.disconnects.get())
        assertEquals(0, secondConnection.disconnects.get())
        second.cancel()
        assertEquals(1, secondConnection.disconnects.get())
    }

    @Test fun disconnectFailureDoesNotReopenCancelledSession() {
        val connections = RegistrationConnections()
        val broken = FakeConnection(failDisconnect = true)
        val late = FakeConnection()
        connections.attach(broken)
        connections.cancel()
        connections.cancel()
        connections.attach(late)
        assertEquals(1, broken.disconnects.get())
        assertEquals(1, late.disconnects.get())
    }
}
