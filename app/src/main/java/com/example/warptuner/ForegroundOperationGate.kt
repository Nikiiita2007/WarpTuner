package com.example.warptuner

import java.util.concurrent.atomic.AtomicBoolean

/** Reject duplicate operations rather than queueing them. */
internal class ForegroundOperationGate {
    private val held = AtomicBoolean(false)
    val busy: Boolean get() = held.get()
    fun tryAcquire(): Boolean = held.compareAndSet(false, true)
    fun release() { held.set(false) }
}
