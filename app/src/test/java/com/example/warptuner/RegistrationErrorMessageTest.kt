package com.example.warptuner

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RegistrationErrorMessageTest {
    @Test fun networkClassificationDoesNotDependOnMessageKeywords() {
        val expected = registrationErrorMessage(RegistrationNetworkException("dns"))
        for (kind in listOf("connect", "io", "timeout")) {
            assertEquals(expected, registrationErrorMessage(RegistrationNetworkException(kind)))
        }
        assertTrue(expected.contains("не подтверждение блокировки"))
    }

    @Test fun arbitraryTimeoutTextDoesNotBecomeNetworkDiagnosisOrLeakToUi() {
        val error = IllegalStateException("timeout synthetic-private-value")
        val message = registrationErrorMessage(error)
        assertFalse(message.contains("synthetic-private-value"))
        assertFalse(message.contains("доверенный VPN"))
    }

    @Test fun storageFailureHasLocalExplanation() {
        assertTrue(registrationErrorMessage(SecretStorageException()).contains("локального шифрования"))
    }

    @Test fun cancellationPropagatesUnchanged() {
        val expected = CancellationException("synthetic cancellation")
        val actual = assertThrows(CancellationException::class.java) {
            registrationErrorMessage(expected)
        }
        assertSame(expected, actual)
    }
}
