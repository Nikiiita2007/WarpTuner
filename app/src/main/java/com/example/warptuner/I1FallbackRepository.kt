package com.example.warptuner

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

internal class I1FallbackRepository(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
    private val codec = SecretCodec(KeystoreSecrets::key)
    private fun binding(account: String): String = MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun encrypted(value: String): String = try { codec.encrypt(value) }
        catch (_: Exception) { throw SecretStorageException() }

    fun read(account: String): String? {
        if (account.isBlank()) return null
        val stored = prefs.getString("i1_fallback", "").orEmpty()
        return runCatching {
            val json = JSONObject(codec.decrypt(stored) ?: return null)
            if (json.getString("binding") == binding(account)) json.getString("value") else null
        }.getOrNull()
    }

    fun save(account: String, value: String) {
        require(account.isNotBlank())
        require(value.isNotBlank() && value.length <= 8192 && '\n' !in value && '\r' !in value)
        val data = JSONObject().put("binding", binding(account)).put("value", value).toString()
        prefs.edit().putString("i1_fallback", encrypted(data)).apply()
    }

    fun clear() { prefs.edit().remove("i1_fallback").apply() }

    /** Prepare all ciphertext before one publication; not a durable disk transaction. */
    fun remember(store: Store, account: String, original: String, selected: String) {
        check(store.account == account && store.cfg.trim() == original.trim()) { "Profile changed" }
        val active = encrypted(selected)
        val changed = selected != original
        require(!changed || account.isNotBlank())
        val config = if (changed) encrypted(selected) else null
        val template = if (changed) extractTemplate(selected) else null
        check(store.account == account && store.cfg.trim() == original.trim()) { "Profile changed" }
        prefs.edit().apply {
            putString("active_cfg", active)
            if (config != null && template != null) {
                putString("cfg", config)
                putString("tpl", template)
            }
        }.apply()
    }
}
