package com.example.warptuner

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Entry-point checks only: no Android context, native key generation or network. */
class WarpRegistrationCancellationTest {
    @Test fun cancellationBeforeFirstAttemptPropagatesUnchanged() {
        val expected = CancellationException("synthetic cancellation")
        var checks = 0
        val actual = assertThrows(CancellationException::class.java) {
            WarpApi.registerCheckingCancellation {
                checks++
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(1, checks)
    }

    @Test fun entryCheckFailureIsNotConvertedToNetworkFallback() {
        val expected = IllegalStateException("synthetic precondition failure")
        var checks = 0
        val actual = assertThrows(IllegalStateException::class.java) {
            WarpApi.registerCheckingCancellation {
                checks++
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(1, checks)
    }

    @Test fun fatalEntryFailureIsNotRetriedOrWrapped() {
        val expected = AssertionError("synthetic fatal failure")
        var checks = 0
        val actual = assertThrows(AssertionError::class.java) {
            WarpApi.registerCheckingCancellation {
                checks++
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(1, checks)
    }
}
