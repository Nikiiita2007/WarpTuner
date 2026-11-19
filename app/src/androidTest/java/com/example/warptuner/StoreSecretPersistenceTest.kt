package com.example.warptuner

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreSecretPersistenceTest {
    @Test
    fun encryptionFailureDoesNotOverwriteStoredConfig() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        val original = preferences.getString("cfg", null)
        val existing = "enc1:existing-test-value"
        val store = Store(context, SecretCodec { error("Test Keystore unavailable") })

        try {
            preferences.edit().putString("cfg", existing).commit()
            runCatching { store.cfg = "synthetic-test-config" }
            assertEquals(existing, preferences.getString("cfg", null))
        } finally {
            preferences.edit().putString("cfg", original).commit()
        }
    }
}
