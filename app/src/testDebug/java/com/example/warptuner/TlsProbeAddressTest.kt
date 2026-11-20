package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsProbeAddressTest {
    @Test fun sameAddressHasStableLabel() {
        val bytes = byteArrayOf(192.toByte(), 0, 2, 1)
        val label = tlsProbeAddressId(bytes)
        assertEquals(label, tlsProbeAddressId(bytes.copyOf()))
        assertTrue(label.matches(Regex("[0-9a-f]{16}")))
        assertFalse(label.contains("192.0.2.1"))
    }

    @Test fun sampleAddressesAndFamiliesHaveDifferentLabels() {
        val ipv4 = byteArrayOf(192.toByte(), 0, 2, 1)
        val another = byteArrayOf(192.toByte(), 0, 2, 2)
        val ipv6 = ByteArray(16).apply { this[15] = 1 }
        assertFalse(tlsProbeAddressId(ipv4) == tlsProbeAddressId(another))
        assertFalse(tlsProbeAddressId(ipv4) == tlsProbeAddressId(ipv6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidAddressLength() { tlsProbeAddressId(ByteArray(5)) }
}
