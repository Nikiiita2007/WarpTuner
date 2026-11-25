package com.example.warptuner

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationTimeoutDiagnosticsTest {
    @Test fun timeoutHasSpecificSafeMessage() {
        val message = registrationErrorMessage(RegistrationDeadlineExceededException())
        assertTrue(message.startsWith("Время ожидания регистрации истекло."))
        assertTrue(message.contains("не подтверждает блокировку"))
        assertTrue(message.contains("Не удаляйте"))
        assertFalse(message.contains("Registration time budget exhausted"))
    }

    @Test fun frameFormatContainsOnlyCodeLocation() {
        val frame = StackTraceElement("example.Client", "connect", "sensitive-file-marker", 42)
        assertEquals("example.Client#connect:42", registrationStackFrame(frame))
        assertFalse(registrationStackFrame(frame).contains("sensitive-file-marker"))
    }

    @Test fun cancellationStillPropagatesWithoutDisplayingItsMessage() {
        val cancellation = CancellationException("sensitive-error-marker")
        try {
            registrationErrorMessage(cancellation)
            throw AssertionError("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertTrue(actual === cancellation)
        }
    }
}
