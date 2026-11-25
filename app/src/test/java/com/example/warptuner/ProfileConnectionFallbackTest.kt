package com.example.warptuner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileConnectionFallbackTest {
    private fun verify(working: String?, canDisconnect: Boolean, expected: String?, vararg steps: String) = runBlocking {
        val calls = mutableListOf<String>()
        val actual = connectWithProfileFallback(
            original = "original",
            alternative = "backup",
            connectAndVerify = { calls += it; it == working },
            disconnect = { calls += "down"; canDisconnect },
            onRetry = { calls += "retry" },
        )
        assertEquals(expected, actual)
        assertEquals(steps.toList(), calls)
    }

    @Test fun workingOriginalDoesNotRetry() = verify("original", true, "original", "original")

    @Test fun successfulBackupIsReturned() =
        verify("backup", true, "backup", "original", "down", "retry", "backup")

    @Test fun twoFailuresReturnNoProfileAndDisconnect() =
        verify(null, true, null, "original", "down", "retry", "backup", "down")

    @Test fun unconfirmedDisconnectPreventsRetry() = verify(null, false, null, "original", "down")
}
