package com.example.warptuner

import android.content.Context
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
const val DEFAULT_TEMPLATE = """Jc = 8
Jmin = 55
Jmax = 140
S1 = 0
S2 = 0
H1 = 1
H2 = 2
H3 = 3
H4 = 4
I1 = <r 40><rc 30><rd 10><t>"""

suspend fun createAccountSmart(ctx: Context, store: Store): String {
    try {
        return withContext(Dispatchers.IO) { createAccount(store) }
    } catch (e: Throwable) {
        val boot = store.cfg
        if (boot.isBlank()) throw e
        withContext(Dispatchers.IO) { Engine.up(ctx, boot) }
        try {
            val ready = withContext(Dispatchers.IO) { Prober.waitReady(15000) }
            if (!ready) throw RuntimeException("текущий конфиг не поднял туннель: ${e.message}")
            return withContext(Dispatchers.IO) { createAccount(store) }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { Engine.down(ctx) } }
        }
    }
}
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
    private fun s(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()
    var lastOutcome: String
        get() = s("outcome")
        set(v) = put("outcome", v)
    var cfg: String
        get() = s("cfg")
        set(v) = put("cfg", v)
    var template: String
        get() = s("tpl", DEFAULT_TEMPLATE)
        set(v) = put("tpl", v)
    var endpoint: String
        get() = s("endpoint")
        set(v) = put("endpoint", v)
    var account: String
        get() = s("account")
        set(v) = put("account", v)
    var splitMode: String
        get() = s("split_mode", "off")
        set(v) = put("split_mode", v)
    var splitApps: String
        get() = s("split_apps")
        set(v) = put("split_apps", v)
    var onboarded: Boolean
        get() = s("onboarded") == "1"
        set(v) = put("onboarded", if (v) "1" else "0")

    var hideBatteryHint: Boolean
        get() = s("hide_battery") == "1"
        set(v) = put("hide_battery", if (v) "1" else "0")
    var activeCfg: String
        get() = s("active_cfg")
        set(v) = put("active_cfg", v)

    var watchdog: Boolean
        get() = s("watchdog", "1") == "1"
        set(v) = put("watchdog", if (v) "1" else "0")

    var tuneMode: String
        get() = s("tune_mode", "NORMAL")
        set(v) = put("tune_mode", v)
}


data class WarpAccount(
    val privateKey: String, val v4: String, val v6: String,
    val peerKey: String, val endpoint: String
)

object WarpApi {
    private data class Variant(val version: String, val ua: String, val client: String)

    private val variants = listOf(
        Variant("v0a5641", "1.1.1.1/6.38.9-5641 (Android 16.0.0)", "a-6.38.9-5641"),
        Variant("v0a1922", "okhttp/3.12.1", "a-6.3-1922")
    )

    private fun request(v: Variant, path: String, method: String, body: JSONObject?, token: String?): JSONObject {
        val c = URL("https://api.cloudflareclient.com/${v.version}/$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", v.ua)
            c.setRequestProperty("CF-Client-Version", v.client)
            c.setRequestProperty("Content-Type", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw RuntimeException("HTTP $code на $path ${text.take(100)}")
            return JSONObject(text)
        } catch (e: java.net.SocketTimeoutException) {
            throw RuntimeException("таймаут на $path")
        } finally {
            c.disconnect()
        }
    }

    private fun registerWith(v: Variant): WarpAccount {
        val kp = KeyPair()
        val priv = kp.privateKey.toBase64()
        val pub = kp.publicKey.toBase64()
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = JSONObject()
            .put("key", pub).put("install_id", "").put("fcm_token", "")
            .put("tos", ts).put("type", "Android").put("model", "PC")
            .put("locale", "en_US").put("os_version", "16.0.0")
            .put("key_type", "curve25519").put("tunnel_type", "wireguard")
        val reg = request(v, "reg", "POST", body, null)
        val full = if (reg.has("config")) reg
        else request(v, "reg/" + reg.getString("id"), "GET", null, reg.getString("token"))
        val cfg = full.getJSONObject("config")
        val addr = cfg.getJSONObject("interface").getJSONObject("addresses")
        val peer = cfg.getJSONArray("peers").getJSONObject(0)
        val ep = peer.getJSONObject("endpoint")
        val host = ep.optString("host").ifBlank { ep.optString("v4").substringBefore(":") + ":2408" }
        return WarpAccount(priv, addr.getString("v4"), addr.getString("v6"), peer.getString("public_key"), host)
    }

    fun register(): WarpAccount {
        val errors = mutableListOf<String>()
        for (v in variants) {
            try {
                return registerWith(v)
            } catch (e: Exception) {
                errors += "${v.version}: ${e.message}"
            }
        }
        throw RuntimeException(errors.joinToString(" | "))
    }

}