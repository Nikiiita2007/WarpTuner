package com.example.warptuner

import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class I1SelectionTest {
    private fun input(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    @Test fun readsOnlyI1AndAcceptsUtf8Bom() {
        val text = "\uFEFF[Interface]\nPrivateKey = synthetic-secret\nI1 = <r 32>\n[Peer]\nEndpoint = example.invalid:2408"
        assertEquals("<r 32>", input(text).use { readSelectedI1(it) })
    }

    @Test fun rejectsDuplicateAndMissingI1() {
        for (text in listOf("[Interface]\nDNS = 1.1.1.1", "[Interface]\nI1 = a\nI1 = b")) {
            try {
                input(text).use { readSelectedI1(it) }
                fail("Expected rejection")
            } catch (e: I1SelectionException) { assertEquals(I1SelectionProblem.MISSING_I1, e.problem) }
        }
    }

    @Test fun rejectsOversizedFile() {
        try {
            ByteArrayInputStream(ByteArray(131073)).use { readSelectedI1(it) }
            fail("Expected size limit")
        } catch (e: I1SelectionException) { assertEquals(I1SelectionProblem.TOO_LARGE, e.problem) }
    }

    @Test fun cancellationPropagates() {
        val cancellation = CancellationException("synthetic cancellation")
        try {
            input("[Interface]\nI1 = a").use { readSelectedI1(it) { throw cancellation } }
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    @Test fun savedUiTokenContainsNoAccountJson() {
        val account = "synthetic-account-secret"
        val token = i1SelectionAccountToken(account)
        assertEquals(token, i1SelectionAccountToken(account))
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        assertFalse(token.contains(account))
        assertFalse(token == i1SelectionAccountToken("another-account"))
        assertEquals("", i1SelectionAccountToken(""))
    }
}
