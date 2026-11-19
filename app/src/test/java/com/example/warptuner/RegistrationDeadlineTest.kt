package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RegistrationDeadlineTest {
    @Test fun sharedBudgetDoesNotResetBetweenRequests() {
        var now = 0L
        val deadline = RegistrationDeadline(100) { now }
        assertEquals(100L, deadline.remainingMillis())
        now = 40_000_000L
        assertEquals(60L, deadline.remainingMillis())
        now = 75_000_000L
        assertEquals(25L, deadline.remainingMillis())
        now = 100_000_000L
        assertThrows(RegistrationDeadlineExceededException::class.java) { deadline.check() }
    }

    @Test fun subMillisecondRemainderNeverBecomesInfiniteSocketTimeout() {
        var now = 0L
        val deadline = RegistrationDeadline(1) { now }
        now = 999_999L
        assertEquals(1L, deadline.remainingMillis())
        assertEquals(1, deadline.socketTimeoutMillis(15_000))
        now++
        assertThrows(RegistrationDeadlineExceededException::class.java) {
            deadline.socketTimeoutMillis(15_000)
        }
    }

    @Test fun socketTimeoutIsCappedByBothConfigurationAndRemainingBudget() {
        var now = 0L
        val deadline = RegistrationDeadline(30_000) { now }
        assertEquals(15_000, deadline.socketTimeoutMillis(15_000))
        now = 29_000_000_000L
        assertEquals(1_000, deadline.socketTimeoutMillis(15_000))
    }

    @Test fun monotonicCounterWraparoundPreservesShortElapsedDuration() {
        var now = Long.MAX_VALUE - 500_000L
        val deadline = RegistrationDeadline(2) { now }
        now += 1_000_000L
        assertEquals(1L, deadline.remainingMillis())
    }

    @Test fun rejectsInvalidBudgetsAndInfiniteSocketTimeouts() {
        assertThrows(IllegalArgumentException::class.java) { RegistrationDeadline(0) }
        assertThrows(IllegalArgumentException::class.java) { RegistrationDeadline(-1) }
        assertThrows(IllegalArgumentException::class.java) { RegistrationDeadline(Long.MAX_VALUE) }
        val deadline = RegistrationDeadline(100) { 0L }
        assertThrows(IllegalArgumentException::class.java) { deadline.socketTimeoutMillis(0) }
    }

    @Test fun independentSessionsHaveIndependentStartTimes() {
        var now = 0L
        val first = RegistrationDeadline(100) { now }
        now = 50_000_000L
        val second = RegistrationDeadline(100) { now }
        assertEquals(50L, first.remainingMillis())
        assertEquals(100L, second.remainingMillis())
    }
}
