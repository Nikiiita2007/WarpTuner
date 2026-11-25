package com.example.warptuner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TunnelOperationsConcurrencyTest {
    @Test fun downCannotEnterBackendWhileUpIsExecuting() {
        val upEntered = CountDownLatch(1)
        val releaseUp = CountDownLatch(1)
        val downRequested = CountDownLatch(1)
        val downEntered = CountDownLatch(1)
        val backend = object : TunnelBackend {
            override fun up(config: String): String {
                upEntered.countDown()
                check(releaseUp.await(5, TimeUnit.SECONDS))
                return "UP"
            }
            override fun down(): String {
                downEntered.countDown()
                return "DOWN"
            }
            override fun state(): String = "DOWN"
        }
        val operations = TunnelOperations(backend)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val up = pool.submit<String> { operations.up("synthetic config") }
            assertTrue(upEntered.await(5, TimeUnit.SECONDS))
            val down = pool.submit<String> {
                downRequested.countDown()
                operations.down()
            }
            assertTrue(downRequested.await(5, TimeUnit.SECONDS))
            assertFalse(downEntered.await(200, TimeUnit.MILLISECONDS))
            releaseUp.countDown()
            assertEquals("UP", up.get(5, TimeUnit.SECONDS))
            assertEquals("DOWN", down.get(5, TimeUnit.SECONDS))
            assertEquals(0L, downEntered.count)
        } finally {
            releaseUp.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun backendExceptionReleasesLockForAnotherThread() {
        val expected = IllegalStateException("synthetic failure")
        val operations = TunnelOperations(object : TunnelBackend {
            override fun up(config: String): String = throw expected
            override fun down(): String = "DOWN"
            override fun state(): String = "DOWN"
        })
        try {
            operations.up("synthetic config")
            fail("Failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        val pool = Executors.newSingleThreadExecutor()
        try {
            assertEquals("DOWN", pool.submit<String> { operations.down() }.get(5, TimeUnit.SECONDS))
            assertEquals("DOWN", pool.submit<String> { operations.state() }.get(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }
}
