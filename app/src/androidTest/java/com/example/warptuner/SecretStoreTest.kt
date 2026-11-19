package com.example.warptuner

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Store encrypts secrets with the real Android Keystore and migrates legacy plaintext. */
@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
    private val secret = "[Interface]\nPrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=\n"
    private var saved: Map<String, *> = emptyMap<String, Any>()

    @Before
    fun backup() { saved = prefs.all.toMap() }

    @After
    fun restore() {
        prefs.edit(commit = true) {
            clear()
            saved.forEach { (k, v) -> if (v is String) putString(k, v) }
        }
    }

    @Test
    fun secretsAreEncryptedAtRest() {
        val store = Store(ctx)
        store.cfg = secret
        store.activeCfg = secret
        store.account = """{"priv":"abc"}"""
        store.lastOutcome = """{"ranked":[]}"""

        for (key in listOf("cfg", "active_cfg", "account", "outcome")) {
            val raw = prefs.getString(key, "")!!
            assertTrue("$key must be encrypted", SecretCodec.isEncrypted(raw))
            assertFalse(raw.contains("PrivateKey"))
        }
        val fresh = Store(ctx)
        assertEquals(secret, fresh.cfg)
        assertEquals(secret, fresh.activeCfg)
        assertEquals("""{"priv":"abc"}""", fresh.account)
    }

    @Test
    fun legacyPlaintextIsMigrated() {
        prefs.edit(commit = true) { putString("cfg", secret) }
        val store = Store(ctx)
        assertEquals(secret, store.cfg)
        assertTrue(SecretCodec.isEncrypted(prefs.getString("cfg", "")!!))
        assertEquals(secret, Store(ctx).cfg)
    }

    @Test
    fun emptyValueStaysEmpty() {
        val store = Store(ctx)
        store.cfg = ""
        assertEquals("", prefs.getString("cfg", null))
        assertEquals("", store.cfg)
    }

    @Test
    fun nonSecretSettingsStayPlain() {
        val store = Store(ctx)
        store.tuneMode = "FAST"
        assertEquals("FAST", prefs.getString("tune_mode", null))
    }
}
