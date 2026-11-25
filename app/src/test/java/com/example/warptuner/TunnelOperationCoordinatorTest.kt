package com.example.warptuner

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TunnelOperationCoordinatorTest {
    @Test fun watchdogCannotActOnProbeAfterManualOperation() {
        val gate = TunnelOperationCoordinator()
        val probe = checkNotNull(gate.idleSnapshot())
        val manual = checkNotNull(gate.tryAcquire())
        assertNull(gate.tryAcquire(probe))
        gate.release(manual)
        assertNull(gate.tryAcquire(probe))
        assertNotNull(gate.tryAcquire(checkNotNull(gate.idleSnapshot())))
    }

    @Test fun oldReleaseCannotUnlockCurrentOperation() {
        val gate = TunnelOperationCoordinator()
        val old = checkNotNull(gate.tryAcquire())
        gate.release(old)
        val current = checkNotNull(gate.tryAcquire())
        gate.release(old)
        assertNull(gate.idleSnapshot())
        assertNull(gate.tryAcquire())
        gate.release(current)
        assertNotNull(gate.idleSnapshot())
    }
}
