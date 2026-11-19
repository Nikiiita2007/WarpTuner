package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretCodecTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val codec = SecretCodec { key }

    private val config = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 172.16.0.2/32
    """.trimIndent()

    @Test
    fun roundTripRestoresPlaintext() {
        val enc = codec.encrypt(config)
        assertTrue(SecretCodec.isEncrypted(enc))
        assertEquals(config, codec.decrypt(enc))
    }

    @Test
    fun ciphertextDoesNotContainSecret() {
        val enc = codec.encrypt(config)
        assertFalse(enc.contains("PrivateKey"))
        assertFalse(enc.contains("yAnz5TF"))
    }

    @Test
    fun encryptionIsRandomized() {
        assertNotEquals(codec.encrypt(config), codec.encrypt(config))
    }

    @Test
    fun unicodeAndEmptyValuesSurvive() {
        val text = "Конфиг №1 ✓"
        assertEquals(text, codec.decrypt(codec.encrypt(text)))
        assertEquals("", codec.decrypt(codec.encrypt("")))
    }

    @Test
    fun wrongKeyReturnsNull() {
        val other = SecretCodec { newKey() }
        assertNull(other.decrypt(codec.encrypt(config)))
    }

    @Test
    fun tamperedValueReturnsNull() {
        val enc = codec.encrypt(config)
        val last = enc.last()
        val tampered = enc.dropLast(1) + (if (last == 'A') 'B' else 'A')
        assertNull(codec.decrypt(tampered))
    }

    @Test
    fun malformedValuesReturnNull() {
        assertNull(codec.decrypt("plain text"))
        assertNull(codec.decrypt("enc1:"))
        assertNull(codec.decrypt("enc1:%%%not-base64%%%"))
        assertNull(codec.decrypt("enc1:AAAA"))
    }
}
