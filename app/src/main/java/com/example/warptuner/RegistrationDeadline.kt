package com.example.warptuner

internal class RegistrationDeadlineExceededException :
    RuntimeException("Registration time budget exhausted")

/**
 * One instance per registration session, shared across requests and API variants.
 * Cooperative accounting only: this class cannot interrupt DNS or blocking I/O.
 */
internal class RegistrationDeadline(
    timeoutMs: Long,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val durationNanos: Long
    private val startedNanos: Long

    init {
        require(timeoutMs > 0 && timeoutMs <= Long.MAX_VALUE / 1_000_000L)
        durationNanos = timeoutMs * 1_000_000L
        startedNanos = nanoTime()
    }

    fun check() {
        remainingMillis()
    }

    /** Rounds positive sub-millisecond time up; never returns zero (infinite socket timeout). */
    fun remainingMillis(): Long {
        val elapsed = nanoTime() - startedNanos
        if (elapsed < 0 || elapsed >= durationNanos) {
            throw RegistrationDeadlineExceededException()
        }
        val remaining = durationNanos - elapsed
        return 1L + (remaining - 1L) / 1_000_000L
    }

    /** Must be called again at each phase; does not enforce an overall call timeout. */
    fun socketTimeoutMillis(configuredMs: Int): Int {
        require(configuredMs > 0)
        return minOf(configuredMs.toLong(), remainingMillis()).toInt()
    }
}
