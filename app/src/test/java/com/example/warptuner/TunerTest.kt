package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerTest {
    private val cfg = """
        [Interface]
        PrivateKey = AAAA
        Address = 172.16.0.2/32
        Jc = 4
        Jmin = 40
        Jmax = 70

        [Peer]
        PublicKey = BBBB
        Endpoint = engage.cloudflareclient.com:2408
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @Test
    fun readParams_readsJunkValues() {
        assertEquals(Params(4, 40, 70), Tuner.readParams(cfg))
    }

    @Test
    fun readParams_returnsNullWhenMissing() {
        assertNull(Tuner.readParams("[Interface]\nPrivateKey = AAAA"))
    }

    @Test
    fun applyParams_replacesValuesAndKeepsOtherLines() {
        val out = Tuner.applyParams(cfg, Params(9, 80, 300))
        assertEquals(Params(9, 80, 300), Tuner.readParams(out))
        assertEquals(1, out.lines().count { it.trim().startsWith("Jc") })
        assertEquals("Jc = 9", out.lines()[1])
        assertTrue(out.contains("PrivateKey = AAAA"))
        assertTrue(out.contains("PublicKey = BBBB"))
    }

    @Test
    fun readEndpoint_findsValueOrNull() {
        assertEquals("engage.cloudflareclient.com:2408", Tuner.readEndpoint(cfg))
        assertNull(Tuner.readEndpoint("[Interface]\nJc = 1"))
    }

    @Test
    fun applyEndpoint_replacesOnlyEndpointLine() {
        val out = Tuner.applyEndpoint(cfg, "1.2.3.4:7281")
        assertEquals("1.2.3.4:7281", Tuner.readEndpoint(out))
        assertTrue(out.contains("PublicKey = BBBB"))
        assertTrue(out.contains("AllowedIPs = 0.0.0.0/0"))
    }

    @Test
    fun applyEndpoint_blankOrMissingEndpointLeavesConfigUnchanged() {
        assertEquals(cfg, Tuner.applyEndpoint(cfg, " "))
        val noEndpoint = "[Interface]\nJc = 1"
        assertEquals(noEndpoint, Tuner.applyEndpoint(noEndpoint, "x:1"))
    }

    @Test
    fun mutate_staysWithinBoundsAndKeepsEndpoint() {
        repeat(500) {
            val m = Tuner.mutate(Params(3, 8, 20, "a:1"))
            assertTrue(m.jc in 1..20)
            assertTrue(m.jmin in 8..400)
            assertTrue(m.jmax >= m.jmin + 10 && m.jmax <= 1000)
            assertEquals("a:1", m.endpoint)
        }
    }

    @Test
    fun randomParams_staysWithinDocumentedRanges() {
        repeat(500) {
            val p = Tuner.randomParams()
            assertTrue(p.jc in 3..10)
            assertTrue(p.jmin in 30..150)
            assertTrue(p.jmax - p.jmin in 20..400)
        }
    }

    @Test
    fun titleOf_buildsReadableNames() {
        assertEquals("текущий конфиг", Tuner.titleOf("base"))
        assertEquals("повтор: текущий конфиг", Tuner.titleOf("v-base"))
        assertEquals("вариант 3", Tuner.titleOf("c03"))
        assertEquals("эталон, круг 2", Tuner.titleOf("ref2"))
        assertEquals("endpoint №1, круг 2", Tuner.titleOf("ep1r2"))
    }
}
