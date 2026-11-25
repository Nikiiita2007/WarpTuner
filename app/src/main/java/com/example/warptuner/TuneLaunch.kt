package com.example.warptuner

import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal val tunnelOperationCoordinator = TunnelOperationCoordinator()
internal val tuneLaunchHandoff = TuneLaunchHandoff(tunnelOperationCoordinator)

/** Pending reservations expire; claimed reservations belong to the service until cleanup. */
internal class TuneLaunchHandoff(private val coordinator: TunnelOperationCoordinator) {
    private var pending: Pair<String, Any>? = null

    @Synchronized fun reserve(): String? {
        val id = UUID.randomUUID().toString()
        val token = coordinator.tryAcquire() ?: return null
        pending = id to token
        return id
    }

    @Synchronized fun claim(id: String): Any? {
        val reservation = pending?.takeIf { it.first == id } ?: return null
        pending = null
        return reservation.second
    }

    @Synchronized fun cancel(id: String) {
        val reservation = pending?.takeIf { it.first == id } ?: return
        pending = null
        coordinator.release(reservation.second)
    }

    @Synchronized fun cancelPending() {
        val reservation = pending ?: return
        pending = null
        coordinator.release(reservation.second)
    }
}

internal fun requestTuneLaunch(
    handoff: TuneLaunchHandoff,
    scheduleExpiry: (String) -> Unit,
    saveProfile: () -> Unit,
    stopWatchdog: () -> Unit,
    startService: (String) -> Unit,
): Boolean {
    val id = handoff.reserve() ?: return false
    var submitted = false
    try {
        scheduleExpiry(id)
        saveProfile()
        stopWatchdog()
        startService(id)
        submitted = true
        return true
    } finally {
        if (!submitted) handoff.cancel(id)
    }
}

internal class WatchdogRecoveryGate(private val coordinator: TunnelOperationCoordinator) {
    fun snapshot(): Any? = coordinator.idleSnapshot()

    suspend fun tryRecover(snapshot: Any, recover: suspend () -> Unit): Boolean {
        val token = coordinator.tryAcquire(snapshot) ?: return false
        return try {
            currentCoroutineContext().ensureActive()
            recover()
            true
        } finally {
            coordinator.release(token)
        }
    }
}
