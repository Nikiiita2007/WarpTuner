package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneLaunchTest {
    @Test fun rejectedRequestHasNoSideEffects() {
        val coordinator = TunnelOperationCoordinator()
        val handoff = TuneLaunchHandoff(coordinator)
        val probe = checkNotNull(coordinator.idleSnapshot())
        val recovery = checkNotNull(coordinator.tryAcquire(probe))
        val effects = mutableListOf<String>()
        try {
            val accepted = requestTuneLaunch(handoff,
                scheduleExpiry = { effects += "expiry" },
                saveProfile = { effects += "save" },
                stopWatchdog = { effects += "stop" },
                startService = { effects += "start" },
            )
            assertFalse(accepted)
            assertTrue(effects.isEmpty())
        } finally {
            coordinator.release(recovery)
        }
    }

    @Test fun reservationPrecedesEffectsAndTransfersOnlyOnce() {
        val coordinator = TunnelOperationCoordinator()
        val handoff = TuneLaunchHandoff(coordinator)
        val effects = mutableListOf<String>()
        var id: String? = null
        assertTrue(requestTuneLaunch(handoff,
            scheduleExpiry = { id = it },
            saveProfile = {
                assertNull(coordinator.tryAcquire())
                effects += "save"
            },
            stopWatchdog = { effects += "stop" },
            startService = { assertEquals(id, it); effects += "start" },
        ))
        assertEquals(listOf("save", "stop", "start"), effects)
        val reservation = checkNotNull(id)
        val token = checkNotNull(handoff.claim(reservation))
        try {
            assertNull(handoff.claim(reservation))
            handoff.cancel(reservation)
            assertNull(coordinator.tryAcquire())
        } finally {
            coordinator.release(token)
        }
        assertNotNull(coordinator.idleSnapshot())
    }

    @Test fun synchronousFailuresReleaseReservation() {
        for (failureAt in 0..3) {
            val coordinator = TunnelOperationCoordinator()
            val handoff = TuneLaunchHandoff(coordinator)
            val effects = mutableListOf<Int>()
            var caught = false
            fun step(index: Int) {
                effects += index
                if (index == failureAt) throw IllegalStateException("synthetic launch failure")
            }
            try {
                requestTuneLaunch(handoff,
                    scheduleExpiry = { step(0) },
                    saveProfile = { step(1) },
                    stopWatchdog = { step(2) },
                    startService = { step(3) },
                )
            } catch (_: IllegalStateException) {
                caught = true
            }
            assertTrue(caught)
            assertEquals((0..failureAt).toList(), effects)
            assertNotNull(coordinator.idleSnapshot())
        }
    }

    @Test fun expiredReservationCannotBeClaimedOrReleaseNewOwner() {
        val coordinator = TunnelOperationCoordinator()
        val handoff = TuneLaunchHandoff(coordinator)
        val id = checkNotNull(handoff.reserve())
        handoff.cancel(id)
        assertNull(handoff.claim(id))
        val next = checkNotNull(handoff.reserve())
        handoff.cancel(id)
        assertNull(coordinator.tryAcquire())
        handoff.cancel(next)
        assertNotNull(coordinator.idleSnapshot())
    }

    @Test fun stopCancelsUnclaimedReservation() {
        val coordinator = TunnelOperationCoordinator()
        val handoff = TuneLaunchHandoff(coordinator)
        val id = checkNotNull(handoff.reserve())
        handoff.cancelPending()
        assertNull(handoff.claim(id))
        assertNotNull(coordinator.idleSnapshot())
    }
}
