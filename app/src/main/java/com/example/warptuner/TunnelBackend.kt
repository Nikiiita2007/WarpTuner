package com.example.warptuner

/** Backend operations may block and must be invoked off the main thread. */
internal interface TunnelBackend {
    fun up(config: String): String
    fun down(): String
    fun state(): String
}

/**
 * Instance-local injection keeps tests away from the process-wide VPN singleton.
 * Serialize individual native calls from UI, tuning and watchdog callers.
 * This does not grant ownership of a multi-call connect/probe/disconnect sequence.
 */
internal class TunnelOperations(private val backend: TunnelBackend) {
    private val backendLock = Any()
    fun up(config: String): String = synchronized(backendLock) { backend.up(config) }
    fun down(): String = synchronized(backendLock) { backend.down() }
    fun state(): String = synchronized(backendLock) { backend.state() }
}
