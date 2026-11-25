package com.example.warptuner

internal class TunnelOperationCoordinator {
    private var generation = Any()
    private var owner: Any? = null

    @Synchronized fun idleSnapshot(): Any? = generation.takeIf { owner == null }

    @Synchronized fun tryAcquire(snapshot: Any? = null): Any? {
        if (owner != null || (snapshot != null && snapshot !== generation)) return null
        val token = Any()
        generation = token
        owner = token
        return token
    }

    @Synchronized fun release(token: Any) {
        if (owner === token) owner = null
    }
}
