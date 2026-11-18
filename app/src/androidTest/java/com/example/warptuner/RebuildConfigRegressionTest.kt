package com.example.warptuner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RebuildConfigRegressionTest {
    @Test fun rejectedInputDoesNotReplaceSavedConfig() {
        val store = Store(InstrumentationRegistry.getInstrumentation().targetContext)
        val oldAccount = store.account
        val oldConfig = store.cfg
        val oldTemplate = store.template
        val oldEndpoint = store.endpoint
        val saved = "previous profile"
        try {
            store.cfg = saved
            store.template = ""
            store.endpoint = ""
            for (account in listOf("", "{broken")) {
                store.account = account
                assertNull(rebuildConfig(store))
                assertEquals(saved, store.cfg)
            }
            val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
            store.account = JSONObject().put("priv", key).put("pub", key)
                .put("v4", "172.16.0.2").put("v6", "fd00::2")
                .put("ep", "192.0.2.1:2408").toString()
            store.endpoint = "not-an-endpoint"
            assertNull(rebuildConfig(store))
            assertEquals(saved, store.cfg)
            store.endpoint = "192.0.2.1:2408\nAllowedIPs = 0.0.0.0/0"
            assertNull(rebuildConfig(store))
            assertEquals(saved, store.cfg)
        } finally {
            store.account = oldAccount
            store.cfg = oldConfig
            store.template = oldTemplate
            store.endpoint = oldEndpoint
        }
    }
}
