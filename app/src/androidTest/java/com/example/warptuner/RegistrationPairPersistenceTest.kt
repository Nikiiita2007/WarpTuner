package com.example.warptuner

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationPairPersistenceTest {
    private fun isolatedContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "registration_pair_test_${UUID.randomUUID()}"
        return object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(file, mode)
        }
    }

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun successPublishesBothEncryptedValues() {
        val ctx = isolatedContext()
        val prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        val secretKey = key()
        val codec = SecretCodec { secretKey }
        try {
            val store = Store(ctx, codec)
            store.saveRegistration("new-account", "new-config") {}
            val account = requireNotNull(prefs.getString("account", null))
            val config = requireNotNull(prefs.getString("cfg", null))
            assertTrue(SecretCodec.isEncrypted(account))
            assertTrue(SecretCodec.isEncrypted(config))
            assertEquals("new-account", codec.decrypt(account))
            assertEquals("new-config", codec.decrypt(config))
        } finally {
            check(prefs.edit().clear().commit())
        }
    }

    @Test fun secondEncryptionFailureLeavesBothPreviousValuesUntouched() {
        val ctx = isolatedContext()
        val prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        val secretKey = key()
        var encryptions = 0
        val store = Store(ctx, SecretCodec {
            encryptions++
            check(encryptions != 2) { "Synthetic second encryption failure" }
            secretKey
        })
        check(prefs.edit().putString("account", "old-account").putString("cfg", "old-config").commit())
        try {
            assertThrows(SecretStorageException::class.java) {
                store.saveRegistration("new-account", "new-config") {}
            }
            assertEquals("old-account", prefs.getString("account", null))
            assertEquals("old-config", prefs.getString("cfg", null))
        } finally {
            check(prefs.edit().clear().commit())
        }
    }

    @Test fun cancellationAfterEncryptionLeavesBothPreviousValuesUntouched() {
        val ctx = isolatedContext()
        val prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
        val secretKey = key()
        var encryptions = 0
        val store = Store(ctx, SecretCodec { encryptions++; secretKey })
        val cancelled = CancellationException("Synthetic cancellation before publication")
        check(prefs.edit().putString("account", "old-account").putString("cfg", "old-config").commit())
        try {
            val actual = assertThrows(CancellationException::class.java) {
                store.saveRegistration("new-account", "new-config") {
                    if (encryptions == 2) throw cancelled
                }
            }
            assertSame(cancelled, actual)
            assertEquals("old-account", prefs.getString("account", null))
            assertEquals("old-config", prefs.getString("cfg", null))
        } finally {
            check(prefs.edit().clear().commit())
        }
    }
}
