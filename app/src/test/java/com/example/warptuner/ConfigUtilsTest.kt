package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUtilsTest {
    private val account = WarpAccount(
        privateKey = "PRIV",
        v4 = "172.16.0.2",
        v6 = "2606:4700::2",
        peerKey = "PUB",
        endpoint = "engage.cloudflareclient.com:2408"
    )

    private val plainCfg = "[Interface]\nPrivateKey = X\n\n[Peer]\nEndpoint = a:1"

    @Test
    fun buildConfig_containsAccountFieldsAndTrimmedTemplate() {
        val lines = buildConfig(account, "Jc = 8\n\n  Jmin = 55 \n", "").lines()
        assertTrue("PrivateKey = PRIV" in lines)
        assertTrue("Address = 172.16.0.2/32, 2606:4700::2/128" in lines)
        assertTrue("Jc = 8" in lines)
        assertTrue("Jmin = 55" in lines)
        assertTrue("PublicKey = PUB" in lines)
        assertTrue("Endpoint = engage.cloudflareclient.com:2408" in lines)
        assertTrue(lines.indexOf("[Interface]") < lines.indexOf("[Peer]"))
    }

    @Test
    fun buildConfig_endpointOverrideReplacesAccountEndpoint() {
        val lines = buildConfig(account, "", "9.9.9.9:4198").lines()
        assertTrue("Endpoint = 9.9.9.9:4198" in lines)
        assertFalse("Endpoint = engage.cloudflareclient.com:2408" in lines)
    }

    @Test
    fun extractTemplate_keepsOnlyObfuscationLinesOfInterface() {
        val conf = """
            [Interface]
            PrivateKey = X
            Address = 1.2.3.4/32
            DNS = 1.1.1.1
            MTU = 1280
            Jc = 4
            Jmin = 40
            S1 = 0
            I1 = <r 40>

            [Peer]
            PublicKey = Y
            Endpoint = a:1
        """.trimIndent()
        assertEquals("Jc = 4\nJmin = 40\nS1 = 0\nI1 = <r 40>", extractTemplate(conf))
    }

    @Test
    fun applySplit_offLeavesConfigUnchanged() {
        assertEquals(plainCfg, applySplit(plainCfg, "off", "a.b", "own"))
    }

    @Test
    fun applySplit_excludeWritesListAndDropsOwnPackage() {
        val out = applySplit(plainCfg, "exclude", "a.b, c.d,own", "own")
        assertEquals("ExcludedApplications = a.b, c.d", out.lines()[1])
    }

    @Test
    fun applySplit_includeAddsOwnPackage() {
        val out = applySplit(plainCfg, "include", "a.b", "own")
        assertEquals("IncludedApplications = a.b, own", out.lines()[1])
    }

    @Test
    fun applySplit_replacesExistingApplicationLines() {
        val withOld = "[Interface]\nExcludedApplications = x\nPrivateKey = X"
        val out = applySplit(withOld, "include", "a.b", "own")
        assertFalse(out.contains("ExcludedApplications"))
        assertEquals(1, out.lines().count { it.startsWith("IncludedApplications") })
    }

    @Test
    fun applySplit_excludeWithoutOtherAppsLeavesConfigUnchanged() {
        assertEquals(plainCfg, applySplit(plainCfg, "exclude", "", "own"))
        assertEquals(plainCfg, applySplit(plainCfg, "exclude", "own", "own"))
    }

    @Test
    fun validateConfig_rejectsEmptyAndVpnLinks() {
        assertNotNull(validateConfig(""))
        assertNotNull(validateConfig("   "))
        val msg = validateConfig("vpn://abcdef")
        assertNotNull(msg)
        assertTrue(msg!!.contains("vpn://"))
    }

    @Test
    fun friendlyError_explainsNetworkFailures() {
        val msg = friendlyError(RuntimeException("таймаут на reg"))
        assertTrue(msg.contains("заблокирован доступ к API"))
    }

    @Test
    fun friendlyError_keepsOtherMessages() {
        assertEquals("Не удалось создать конфиг: HTTP 403", friendlyError(RuntimeException("HTTP 403")))
    }
}
