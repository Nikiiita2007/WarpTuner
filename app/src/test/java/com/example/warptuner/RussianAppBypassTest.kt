package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianAppBypassTest {
    private val own = "com.example.warptuner"
    private val app = "com.vkontakte.android"
    private val base = "[Interface]\nPrivateKey = unchanged\nAddress = 10.0.0.2/32\n\n[Peer]\nAllowedIPs = 0.0.0.0/0, ::/0\nPublicKey = unchanged-peer"

    @Test fun fullTunnelGetsAutomaticExclusionsWithoutChangingPeer() {
        val result = mergeRussianAppBypass(base, setOf(app), own)
        assertTrue(result.contains("ExcludedApplications = $app"))
        assertTrue(result.contains("PrivateKey = unchanged"))
        assertEquals(base.substringAfter("[Peer]"), result.substringAfter("[Peer]"))
    }

    @Test fun manualExclusionsArePreservedAndDeduplicated() {
        val config = base.replace("[Interface]", "[Interface]\nExcludedApplications = manual.app, $app")
        val result = mergeRussianAppBypass(config, setOf(app), own)
        assertTrue(result.contains("ExcludedApplications = manual.app, $app"))
        assertEquals(1, result.lines().count { it.startsWith("ExcludedApplications") })
    }

    @Test fun includeModeRemovesBypassButKeepsOtherAppsAndProber() {
        val config = base.replace("[Interface]", "[Interface]\nIncludedApplications = $app, manual.app")
        val result = mergeRussianAppBypass(config, setOf(app), own)
        assertTrue(result.contains("IncludedApplications = manual.app, $own"))
        assertFalse(result.contains("ExcludedApplications"))
        assertFalse(result.contains(app))
    }

    @Test fun includeModeNeverWidensToFullTunnelWhenAllSelectedAppsBypass() {
        val config = base.replace("[Interface]", "[Interface]\nIncludedApplications = $app")
        val result = mergeRussianAppBypass(config, setOf(app), own)
        assertTrue(result.contains("IncludedApplications = $own"))
    }

    @Test fun emptyAutomaticSetLeavesOriginalConfigurationUntouched() {
        assertEquals(base, mergeRussianAppBypass(base, emptySet(), own))
    }

    @Test fun ownAppCannotBeAutomaticallyExcluded() {
        assertEquals(base, mergeRussianAppBypass(base, setOf(own), own))
    }

    @Test fun repeatedApplicationIsIdempotent() {
        val once = mergeRussianAppBypass(base, setOf(app), own)
        assertEquals(once, mergeRussianAppBypass(once, setOf(app), own))
    }
}
