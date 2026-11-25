package com.example.warptuner

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import org.amnezia.awg.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RussianAppBypassConfigTest {
    private val own = "com.example.warptuner.debug"
    private val bypass = "com.vkontakte.android"
    private val key = Base64.encodeToString(ByteArray(32) { (it + 1).toByte() }, Base64.NO_WRAP)

    private fun config(rule: String = ""): String =
        "[Interface] # imported profile\n" +
        "PrivateKey = $key\n" +
        "Address = 10.0.0.2/32\n" +
        "DNS = 1.1.1.1\n" + rule + "\n" +
        "[Peer]\n" +
        "PublicKey = $key\n" +
        "AllowedIPs = 0.0.0.0/0, ::/0\n" +
        "Endpoint = 192.0.2.1:2408\n"

    private fun parse(text: String) {
        Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    @Test fun commentedHeaderGetsParsableAutomaticExclusion() {
        val original = config()
        parse(original)
        val effective = mergeRussianAppBypass(original, setOf(bypass), own)
        parse(effective)
        assertTrue(effective.contains("ExcludedApplications = $bypass"))
        assertEquals(original.substringAfter("[Peer]"), effective.substringAfter("[Peer]"))
        assertTrue(original.startsWith("[Interface] # imported profile"))
    }

    @Test fun importedIncludeRuleRemainsNarrowedWithCommentedHeader() {
        val original = config("IncludedApplications = $bypass, example.other # user selection\n")
        parse(original)
        val effective = mergeRussianAppBypass(original, setOf(bypass), own)
        parse(effective)
        assertTrue(effective.contains("IncludedApplications = example.other, $own"))
        assertFalse(effective.contains("ExcludedApplications"))
        assertFalse(effective.contains(bypass))
        assertEquals(original.substringAfter("[Peer]"), effective.substringAfter("[Peer]"))
    }

    @Test fun importedExclusionsSurviveRepeatedTransformation() {
        val original = config("ExcludedApplications = example.manual # keep this application direct\n")
        parse(original)
        val effective = mergeRussianAppBypass(original, setOf(bypass), own)
        parse(effective)
        assertTrue(effective.contains("ExcludedApplications = example.manual, $bypass"))
        assertEquals(effective, mergeRussianAppBypass(effective, setOf(bypass), own))
    }
}
