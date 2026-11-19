package com.example.warptuner

/** Backend operations may block and must be invoked off the main thread. */
internal interface TunnelBackend {
    fun up(config: String): String
    fun down(): String
    fun state(): String
}

/** Instance-local injection keeps tests away from the process-wide VPN singleton. */
internal class TunnelOperations(private val backend: TunnelBackend) {
    fun up(config: String): String = backend.up(config)
    fun down(): String = backend.down()
    fun state(): String = backend.state()
}
