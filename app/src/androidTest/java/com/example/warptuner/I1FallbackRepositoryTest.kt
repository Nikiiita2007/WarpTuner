package com.example.warptuner

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class I1FallbackRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var store: Store
    private lateinit var repository: I1FallbackRepository
    private lateinit var previous: Map<String, String?>
    private val keys = listOf("cfg", "tpl", "account", "active_cfg", "i1_fallback")
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val backup = "<r 32>"
    private lateinit var original: String

    @Before fun prepare() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        previous = keys.associateWith { prefs.getString(it, null) }
        store = Store(ctx)
        repository = I1FallbackRepository(ctx)
        original = buildConfig(WarpAccount(key, "172.16.0.2", "fd00::2", key, "192.0.2.1:2408"), "I1 = <r 16>", "")
        repository.clear()
        store.account = "synthetic-account-a"
        store.cfg = original
        store.template = "I1 = <r 16>"
        store.activeCfg = ""
    }

    @After fun restore() {
        if (!::prefs.isInitialized || !::previous.isInitialized) return
        prefs.edit().apply {
            previous.forEach { (key, value) ->
                if (value == null) remove(key) else putString(key, value)
            }
        }.commit()
    }

    @Test fun backupIsEncryptedBoundAndDoesNotReplaceProfile() {
        repository.save(store.account, backup)
        assertEquals(backup, repository.read("synthetic-account-a"))
        assertNull(repository.read("synthetic-account-b"))
        assertNull(repository.read(""))
        val raw = prefs.getString("i1_fallback", "").orEmpty()
        assertTrue(SecretCodec.isEncrypted(raw))
        assertFalse(raw.contains(backup))
        assertFalse(raw.contains("synthetic-account-a"))
        assertEquals(original, store.cfg)
        assertEquals("", store.activeCfg)
    }

    @Test fun verifiedAlternativeUpdatesConfigTemplateAndActiveProfile() {
        val candidate = profileWithI1(original, backup)
        repository.remember(store, store.account, original, candidate)
        val reloaded = Store(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(candidate, reloaded.cfg)
        assertEquals(candidate, reloaded.activeCfg)
        assertEquals(extractTemplate(candidate), reloaded.template)
        assertEquals("synthetic-account-a", reloaded.account)
        for (name in listOf("cfg", "active_cfg")) {
            assertTrue(SecretCodec.isEncrypted(prefs.getString(name, "").orEmpty()))
        }
    }

    @Test fun unchangedWinnerDoesNotRewriteConfigOrTemplate() {
        val rawConfig = prefs.getString("cfg", null)
        val template = store.template
        repository.remember(store, store.account, original, original)
        assertEquals(rawConfig, prefs.getString("cfg", null))
        assertEquals(template, store.template)
        assertEquals(original, store.activeCfg)
    }

    @Test fun staleConfigCannotOverwriteNewerProfile() {
        store.cfg = "newer-profile-marker"
        val before = prefs.all.toMap()
        try {
            repository.remember(store, store.account, original, profileWithI1(original, backup))
            fail("Expected stale profile rejection")
        } catch (_: IllegalStateException) { }
        assertEquals(before, prefs.all)
    }

    @Test fun staleAccountCannotOverwriteCurrentProfile() {
        store.account = "synthetic-account-b"
        val before = prefs.all.toMap()
        try {
            repository.remember(store, "synthetic-account-a", original, profileWithI1(original, backup))
            fail("Expected stale account rejection")
        } catch (_: IllegalStateException) { }
        assertEquals(before, prefs.all)
    }

    @Test fun realImportDisablesAccountBoundFallback() {
        repository.save(store.account, backup)
        assertNull(tryImport(original, store))
        assertEquals("", store.account)
        assertNull(repository.read(store.account))
        assertEquals(original.trim(), store.cfg)
    }

    @Test fun clearingBackupLeavesCurrentProfileUntouched() {
        repository.save(store.account, backup)
        val profile = store.cfg
        val account = store.account
        val template = store.template
        repository.clear()
        assertNull(repository.read(account))
        assertEquals(profile, store.cfg)
        assertEquals(account, store.account)
        assertEquals(template, store.template)
    }
}
