package com.example.warptuner

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreFailureTest {
    @Test
    fun failedEncryptionRejectsNewConfigWithoutPersistingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        val previous = prefs.getString("cfg", null)
        check(prefs.edit().remove("cfg").commit())
        try {
            val store = Store(context, SecretCodec { error("Test Keystore unavailable") })
            assertThrows(SecretStorageException::class.java) {
                store.cfg = "synthetic-test-config"
            }
            assertFalse(prefs.contains("cfg"))
        } finally {
            check(prefs.edit().putString("cfg", previous).commit())
        }
    }
}
