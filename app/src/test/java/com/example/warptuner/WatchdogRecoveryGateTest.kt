package com.example.warptuner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogRecoveryGateTest {
    @Test fun staleProbeNeverInvokesDownAfterManualConnect() = runBlocking {
        val coordinator = TunnelOperationCoordinator()
        val watchdog = WatchdogRecoveryGate(coordinator)
        val probe = checkNotNull(watchdog.snapshot())
        val manual = checkNotNull(coordinator.tryAcquire())
        var downCalls = 0
        try {
            assertFalse(watchdog.tryRecover(probe) { downCalls++ })
        } finally {
            coordinator.release(manual)
        }
        assertFalse(watchdog.tryRecover(probe) { downCalls++ })
        assertEquals(0, downCalls)
        assertTrue(watchdog.tryRecover(checkNotNull(watchdog.snapshot())) { downCalls++ })
        assertEquals(1, downCalls)
    }

    @Test fun cancellationReleasesOnlyAfterRecoveryCleanup() = runBlocking {
        withTimeout(5000) {
            val coordinator = TunnelOperationCoordinator()
            val watchdog = WatchdogRecoveryGate(coordinator)
            val entered = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val worker = launch {
                watchdog.tryRecover(checkNotNull(watchdog.snapshot())) {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        assertNull(coordinator.tryAcquire())
                        events += "cleanup"
                    }
                }
            }
            entered.await()
            worker.cancel()
            worker.join()
            assertEquals(listOf("cleanup"), events)
            assertNotNull(coordinator.idleSnapshot())
        }
    }
}
