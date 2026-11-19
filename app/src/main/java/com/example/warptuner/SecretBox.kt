package com.example.warptuner

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts short secrets (private key, account JSON, configs) with AES-256-GCM.
 * Stored format: `enc1:` + Base64(iv || ciphertext+tag). Values without the prefix are
 * treated as legacy plaintext so existing installs can be migrated on first read.
 *
 * The key is supplied by [keyProvider]; on Android it lives in the Keystore
 * ([KeystoreSecrets]), in JVM tests a software key is used.
 */
internal class SecretCodec(private val keyProvider: () -> SecretKey) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + body)
    }

    /** Returns the plaintext, or null if the value is corrupted or the key is gone. */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return null
        return try {
            val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (raw.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, keyProvider(),
                GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES)
            )
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PREFIX = "enc1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        fun isEncrypted(value: String) = value.startsWith(PREFIX)
    }
}

/** AES key kept in the Android Keystore; it never leaves secure hardware where available. */
internal object KeystoreSecrets {
    private const val ALIAS = "warptuner_secrets"
    private const val PROVIDER = "AndroidKeyStore"

    @Volatile private var cached: SecretKey? = null

    @Synchronized
    fun key(): SecretKey {
        cached?.let { return it }
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // The watchdog runs in the background, so no user authentication.
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
        cached = key
        return key
    }
}
