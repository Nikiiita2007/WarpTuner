package com.example.warptuner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigurationPathsRegressionTest {
    private lateinit var app: Application
    private lateinit var store: Store
    private lateinit var vm: MainViewModel
    private lateinit var previousConfig: String
    private lateinit var previousAccount: String
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val account get() = WarpAccount(key, "172.16.0.2", "fd00::2", key, "192.0.2.1:2408")
    private class MemorySettings : SettingsStore {
        override var onboarded = true
        override var watchdog = false
        override var tuneMode = TuneMode.NORMAL.name
    }

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        store = Store(app)
        previousConfig = store.cfg
        previousAccount = store.account
        assertFalse("Tests require idle tuning", TuneState.ui.value.running)
        vm = MainViewModel(app, store, MainPreferences(MemorySettings()))
    }

    @After fun restore() {
        store.cfg = previousConfig
        store.account = previousAccount
    }

    @Test fun rejectedManualTextPreservesConfigAccountAndUi() {
        val original = buildConfig(account, "", "")
        store.cfg = original
        store.account = "synthetic-account-marker"
        vm.setCfg(original)
        for (invalid in listOf("", "   ", "vpn://unsupported", "not a configuration")) {
            assertNotNull(vm.saveManualConfig(invalid))
            assertEquals(original, store.cfg)
            assertEquals("synthetic-account-marker", store.account)
            assertEquals(original, vm.state.value.cfg)
        }
    }

    @Test fun manualAndImportNormalizeAndPersistIdentically() {
        val raw = "  \n" + buildConfig(account, "", "") + "\n  "
        assertNull(tryImport(raw, store))
        val imported = Store(app).cfg
        store.cfg = "previous"
        store.account = "synthetic-account-marker"
        assertNull(vm.saveManualConfig(raw))
        assertEquals(imported, Store(app).cfg)
        assertEquals(imported, vm.state.value.cfg)
        assertEquals("", Store(app).account)
        assertNull(validateConfig(imported))
    }

    @Test fun generatedDefaultConfigParsesAndSurvivesPairedPersistence() {
        val generated = buildConfig(account, DEFAULT_TEMPLATE, "")
        assertNull(validateConfig(generated))
        val encodedAccount = JSONObject()
            .put("priv", account.privateKey).put("v4", account.v4).put("v6", account.v6)
            .put("pub", account.peerKey).put("ep", account.endpoint).toString()
        store.saveRegistration(encodedAccount, generated) {}
        val reloaded = Store(app)
        assertEquals(encodedAccount, reloaded.account)
        assertEquals(generated, reloaded.cfg)
        assertNull(validateConfig(reloaded.cfg))
        assertTrue(reloaded.cfg.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
        assertTrue(reloaded.cfg.contains("Endpoint = 192.0.2.1:2408"))
    }
}
