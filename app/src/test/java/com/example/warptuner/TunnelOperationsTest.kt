package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class TunnelOperationsTest {
    private class FakeBackend : TunnelBackend {
        val calls = mutableListOf<String>()
        var current = "DOWN"
        var failure: RuntimeException? = null
        override fun up(config: String): String {
            calls += "up:$config"
            failure?.let { throw it }
            current = "UP"
            return current
        }
        override fun down(): String {
            calls += "down"
            failure?.let { throw it }
            current = "DOWN"
            return current
        }
        override fun state(): String {
            calls += "state"
            failure?.let { throw it }
            return current
        }
    }

    @Test fun delegatesConfigAndStateTransitions() {
        val fake = FakeBackend()
        val operations = TunnelOperations(fake)
        assertEquals("DOWN", operations.state())
        assertEquals("UP", operations.up("synthetic config"))
        assertEquals("UP", operations.state())
        assertEquals("DOWN", operations.down())
        assertEquals(listOf("state", "up:synthetic config", "state", "down"), fake.calls)
    }

    @Test fun backendFailuresPropagateWithoutRetriesOrFalseSuccess() {
        for (operation in listOf<(TunnelOperations) -> String>(
            { it.up("synthetic config") }, { it.down() }, { it.state() }
        )) {
            val expected = IllegalStateException("synthetic failure")
            val fake = FakeBackend().apply { failure = expected }
            try {
                operation(TunnelOperations(fake))
                fail("Backend failure must propagate")
            } catch (actual: IllegalStateException) {
                assertSame(expected, actual)
            }
            assertEquals(1, fake.calls.size)
            assertEquals("DOWN", fake.current)
        }
    }

    @Test fun instancesDoNotShareBackendState() {
        val first = TunnelOperations(FakeBackend())
        val second = TunnelOperations(FakeBackend())
        first.up("synthetic config")
        assertEquals("UP", first.state())
        assertEquals("DOWN", second.state())
    }
}
