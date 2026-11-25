package com.example.warptuner

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ForegroundOperationGateTest {
    @Test fun duplicateIsRejectedAndReleaseAllowsRetry() {
        val gate = ForegroundOperationGate()
        assertFalse(gate.busy)
        assertTrue(gate.tryAcquire())
        assertTrue(gate.busy)
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.busy)
        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test fun onlyOneConcurrentCallerAcquiresGate() {
        val gate = ForegroundOperationGate()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map {
                pool.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    gate.tryAcquire()
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            gate.release()
            assertTrue(gate.tryAcquire())
        } finally {
            gate.release()
            pool.shutdownNow()
        }
    }
}
