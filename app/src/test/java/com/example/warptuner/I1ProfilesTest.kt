package com.example.warptuner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class I1ProfilesTest {
    private val config = "[Interface]\nPrivateKey = placeholder\nI1 = old\nDNS = 1.1.1.1\n[Peer]\nEndpoint = example.invalid:2408\n"

    @Test fun replacesOnlyInterfaceI1() {
        assertEquals(config.replace("I1 = old", "I1 = new"), profileWithI1(config, "new"))
        assertEquals("new", profileI1(profileWithI1(config, "new")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLineInjection() { profileWithI1(config, "new\nDNS = other") }

    @Test fun rejectsAmbiguousInputAndPreservesCrlf() {
        assertNull(profileI1(config.replace("I1 = old", "I1 = old\nI1 = second")))
        val crlf = config.replace("\n", "\r\n")
        assertEquals(crlf.replace("I1 = old", "I1 = new"), profileWithI1(crlf, "new"))
    }

    @Test fun onlyVerifiedBackupIsRemembered() = runBlocking {
        val saved = mutableListOf<String>()
        val selected = connectAndRememberProfile("original", "backup", { it == "backup" }, { true }, {}, { saved += it })
        assertEquals("backup", selected)
        assertEquals(listOf("backup"), saved)
    }

    @Test fun failedAttemptsDoNotPersist() = runBlocking {
        var saves = 0
        val selected = connectAndRememberProfile("original", "backup", { false }, { true }, {}, { saves++ })
        assertNull(selected)
        assertEquals(0, saves)
    }

    @Test fun cancellationCleansUpAndDoesNotRetry() = runBlocking {
        val calls = mutableListOf<String>()
        val cancellation = CancellationException("cancel")
        try {
            connectAndRememberProfile("original", "backup", { calls += it; throw cancellation },
                { calls += "down"; true }, { calls += "retry" }, { calls += "save" })
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        assertEquals(listOf("original", "down"), calls)
    }

    @Test fun persistenceFailureCleansUp() = runBlocking {
        var downs = 0
        try {
            connectAndRememberProfile("original", null, { true }, { downs++; true }, {}, { error("storage") })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(1, downs)
    }
}
