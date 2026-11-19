package com.example.warptuner

import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RegistrationCancellationTest {
    private class FakeConnection : HttpsURLConnection(URL("https://example.invalid")) {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val disconnects = AtomicInteger()
        override fun connect() {
            entered.countDown()
            check(released.await(3, TimeUnit.SECONDS)) { "Disconnect did not release fake I/O" }
        }
        override fun disconnect() {
            disconnects.incrementAndGet()
            released.countDown()
        }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun cancellationDisconnectsAttachedConnectionOnce() {
        val connections = RegistrationConnections()
        val connection = FakeConnection()
        connections.attach(connection)
        connections.cancel()
        connections.cancel()
        assertEquals(1, connection.disconnects.get())
    }

    @Test fun lateAttachmentIsDisconnectedAfterCancellation() {
        val connections = RegistrationConnections()
        val connection = FakeConnection()
        connections.cancel()
        connections.attach(connection)
        assertEquals(1, connection.disconnects.get())
    }

    @Test fun detachedConnectionIsNotDisconnectedByLaterCancellation() {
        val connections = RegistrationConnections()
        val connection = FakeConnection()
        connections.attach(connection)
        connections.detach(connection)
        connections.cancel()
        assertEquals(0, connection.disconnects.get())
    }

    @Test(timeout = 10000) fun coroutineCancellationReleasesBlockedFakeIo() = runBlocking {
        val connection = FakeConnection()
        val task = async(Dispatchers.Default) {
            withRegistrationCancellation(RegistrationDeadline(60000)) {
                val connections = requireNotNull(registrationConnections.get())
                connections.attach(connection)
                try { connection.connect() } finally { connections.detach(connection) }
            }
        }
        try {
            assertTrue(connection.entered.await(3, TimeUnit.SECONDS))
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
            assertEquals(1, connection.disconnects.get())
        } finally {
            connection.released.countDown()
            task.cancelAndJoin()
        }
    }

    @Test(timeout = 10000) fun deadlineDisconnectsBlockedFakeIoAndRejectsSuccess() = runBlocking {
        val connection = FakeConnection()
        var now = 0L
        val deadline = RegistrationDeadline(1) { now }
        try {
            withRegistrationCancellation(deadline) {
                val connections = requireNotNull(registrationConnections.get())
                connections.attach(connection)
                try {
                    now = 1_000_000L
                    connection.connect()
                } finally {
                    connections.detach(connection)
                }
            }
            fail("Expired registration must not return success")
        } catch (_: RegistrationDeadlineExceededException) {
            assertEquals(1, connection.disconnects.get())
        } finally {
            connection.released.countDown()
        }
    }
}
