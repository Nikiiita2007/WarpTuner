package com.example.warptuner

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
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

private val registrationRoute = ThreadLocal<String>()

private fun createOnRoute(store: Store, route: String): String {
    val previous = registrationRoute.get()
    registrationRoute.set(route)
    return try {
        createAccount(store)
    } finally {
        if (previous == null) registrationRoute.remove() else registrationRoute.set(previous)
    }
}

suspend fun createAccountSmart(ctx: Context, store: Store): String {
    try {
        return withContext(Dispatchers.IO) { createOnRoute(store, "direct") }
    } catch (e: Throwable) {
        val boot = store.cfg
        if (boot.isBlank()) throw e
        withContext(Dispatchers.IO) { Engine.up(ctx, boot) }
        try {
            val ready = withContext(Dispatchers.IO) { Prober.waitReady(15000) }
            if (!ready) throw RuntimeException("текущий конфиг не поднял туннель: ${e.message}")
            return withContext(Dispatchers.IO) { createOnRoute(store, "saved_tunnel") }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { Engine.down(ctx) } }
        }
    }
}

class Store internal constructor(
    ctx: Context,
    private val codec: SecretCodec,
) {
    constructor(ctx: Context) : this(ctx, SecretCodec(KeystoreSecrets::key))

    private val p = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
    private fun s(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit { putString(k, v) }

    /**
     * Secrets (private key, account, configs) are encrypted at rest. Legacy plaintext
     * values are returned as is and re-saved encrypted. If the Keystore is unusable on a
     * device, the value is kept as plaintext so the app keeps working.
     */
    private fun secret(k: String): String {
        val raw = s(k)
        if (raw.isEmpty()) return ""
        if (!SecretCodec.isEncrypted(raw)) {
            putSecret(k, raw)
            return raw
        }
        return codec.decrypt(raw).orEmpty()
    }

    private fun putSecret(k: String, v: String) {
        if (v.isEmpty()) return put(k, "")
        val enc = try { codec.encrypt(v) } catch (_: Exception) { v }
        put(k, enc)
    }

    var lastOutcome: String
        get() = secret("outcome")
        set(v) = putSecret("outcome", v)
    var cfg: String
        get() = secret("cfg")
        set(v) = putSecret("cfg", v)
    var template: String
        get() = s("tpl", DEFAULT_TEMPLATE)
        set(v) = put("tpl", v)
    var endpoint: String
        get() = s("endpoint")
        set(v) = put("endpoint", v)
    var account: String
        get() = secret("account")
        set(v) = putSecret("account", v)
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
        get() = secret("active_cfg")
        set(v) = putSecret("active_cfg", v)

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

    private class RegistrationFailure(
        val stage: String,
        val kind: String,
        val status: Int? = null,
        val phase: String? = null,
        val elapsedMs: Long? = null,
        val detail: String? = null
    ) : RuntimeException("$stage: $kind" + (status?.let { " HTTP $it" } ?: ""))

    private fun failureKind(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "dns"
        is java.net.SocketTimeoutException -> "timeout"
        is SSLException -> "tls"
        is java.net.ConnectException -> "connect"
        is JSONException -> "invalid_response"
        is java.io.IOException -> "io"
        else -> "unexpected"
    }

    private fun failureDetail(e: Exception): String? {
        if (e !is SSLException) return null

        val causes = generateSequence<Throwable>(e) { it.cause }.take(16).toList()
        return when {
            causes.any { it is CertificateException } -> "certificate"
            causes.any { it is SSLPeerUnverifiedException } -> "peer_unverified"
            causes.any { it is SSLProtocolException } -> "protocol"
            causes.any { it is SSLHandshakeException } -> "handshake"
            else -> "other"
        }
    }

    private fun request(v: Variant, path: String, method: String, body: JSONObject?, token: String?): JSONObject {
        val stage = if (method == "POST") "register" else "fetch_config"
        val started = SystemClock.elapsedRealtime()
        var phase = "prepare"
        var status: Int? = null
        fun failed(kind: String, detail: String? = null) = RegistrationFailure(
            stage = stage,
            kind = kind,
            status = status,
            phase = phase,
            elapsedMs = SystemClock.elapsedRealtime() - started,
            detail = detail
        )
        val c = try {
            URL("https://api.cloudflareclient.com/${v.version}/$path").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw failed(failureKind(e), failureDetail(e))
        }
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", v.ua)
            c.setRequestProperty("CF-Client-Version", v.client)
            c.setRequestProperty("Content-Type", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) c.doOutput = true

            phase = "connect"
            c.connect()
            if (body != null) {
                phase = "send_request"
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }

            phase = "await_headers"
            val code = c.responseCode
            status = code.takeIf { it >= 100 }
            if (code !in 200..299) throw failed("http")

            phase = "read_body"
            val text = c.inputStream.bufferedReader().use { it.readText() }
            phase = "parse_json"
            return JSONObject(text)
        } catch (e: RegistrationFailure) {
            throw e
        } catch (e: Exception) {
            throw failed(failureKind(e), failureDetail(e))
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
                val failure = e as? RegistrationFailure ?: RegistrationFailure(
                    stage = if (e is JSONException) "parse_config" else "prepare_registration",
                    kind = failureKind(e),
                    detail = failureDetail(e)
                )
                val route = registrationRoute.get() ?: "unspecified"
                val status = failure.status?.let { " status=$it" }.orEmpty()
                val phase = failure.phase?.let { " phase=$it" }.orEmpty()
                val elapsed = failure.elapsedMs?.let { " elapsed_ms=$it" }.orEmpty()
                val detail = failure.detail?.let { " detail=$it" }.orEmpty()
                Log.w(
                    "WarpRegistration",
                    "route=$route stage=${failure.stage} kind=${failure.kind}$status$phase$elapsed$detail"
                )
                errors += "${v.version}: ${failure.message}"
            }
        }
        throw RuntimeException(errors.joinToString(" | "))
    }
}