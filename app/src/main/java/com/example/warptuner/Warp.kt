package com.example.warptuner

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import org.json.JSONException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

private const val REGISTRATION_BUDGET_MS = 45_000L
private val registrationRoute = ThreadLocal<String>()
private val registrationDeadline = ThreadLocal<RegistrationDeadline>()

internal class RegistrationNetworkException(message: String) : RuntimeException(message)

private suspend fun createOnRoute(store: Store, route: String, deadline: RegistrationDeadline): String {
    val context = currentCoroutineContext()
    context.ensureActive()
    deadline.check()
    return withRegistrationCancellation(deadline) {
        val previous = registrationRoute.get()
        val previousDeadline = registrationDeadline.get()
        registrationRoute.set(route)
        registrationDeadline.set(deadline)
        try {
            createAccount(store) {
                context.ensureActive()
                deadline.check()
            }
        } finally {
            if (previous == null) registrationRoute.remove() else registrationRoute.set(previous)
            if (previousDeadline == null) registrationDeadline.remove() else registrationDeadline.set(previousDeadline)
        }
    }
}

/** Uses existing default routing; never starts or stops a tunnel for registration. */
@Suppress("UNUSED_PARAMETER")
suspend fun createAccountSmart(ctx: Context, store: Store): String {
    val deadline = RegistrationDeadline(REGISTRATION_BUDGET_MS)
    // Automatic saved-tunnel fallback is disabled until exclusive ownership exists.
    // In particular, a network error must not replace or stop another operation's VPN.
    return withContext(Dispatchers.IO) { createOnRoute(store, "direct", deadline) }
}

internal class SecretStorageException :
    IllegalStateException("Не удалось зашифровать данные для сохранения")

class Store internal constructor(
    ctx: Context,
    private val codec: SecretCodec,
) {
    constructor(ctx: Context) : this(ctx, SecretCodec(KeystoreSecrets::key))

    private val p = ctx.getSharedPreferences("warptuner", Context.MODE_PRIVATE)
    private fun s(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit { putString(k, v) }

    /**
     * Legacy plaintext remains readable if encryption is temporarily unavailable.
     * Migration is retried on a later read.
     */
    private fun secret(k: String): String {
        val raw = s(k)
        if (raw.isEmpty()) return ""
        if (!SecretCodec.isEncrypted(raw)) {
            try {
                putSecret(k, raw)
            } catch (_: SecretStorageException) {
                // Leave the existing legacy value untouched.
            }
            return raw
        }
        return codec.decrypt(raw).orEmpty()
    }

    private fun putSecret(k: String, v: String) {
        if (v.isEmpty()) return put(k, "")
        val enc = try {
            codec.encrypt(v)
        } catch (_: Exception) {
            throw SecretStorageException()
        }
        put(k, enc)
    }

    /**
     * Prepare both ciphertexts before publishing either preference. One editor apply
     * publishes the pair together; it is not a durable database transaction.
     * Cancellation after publication cannot roll this operation back.
     */
    internal fun saveRegistration(account: String, config: String, checkActive: () -> Unit) {
        checkActive()
        val encrypted = try {
            codec.encrypt(account) to codec.encrypt(config)
        } catch (_: Exception) {
            throw SecretStorageException()
        }
        checkActive()
        p.edit {
            putString("account", encrypted.first)
            putString("cfg", encrypted.second)
        }
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
    private val transport: RegistrationTransport = HttpsRegistrationTransport()
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

    private fun request(
        v: Variant, path: String, method: String, body: JSONObject?, token: String?,
        deadline: RegistrationDeadline, checkActive: () -> Unit,
    ): JSONObject {
        checkActive()
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
            transport.open(URL("https://api.cloudflareclient.com/${v.version}/$path"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RegistrationDeadlineExceededException) {
            throw e
        } catch (e: Exception) {
            checkActive()
            throw failed(failureKind(e), failureDetail(e))
        }
        val connections = registrationConnections.get()
        try {
            connections?.attach(c)
            checkActive()
            c.requestMethod = method
            c.setRequestProperty("User-Agent", v.ua)
            c.setRequestProperty("CF-Client-Version", v.client)
            c.setRequestProperty("Content-Type", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) c.doOutput = true

            phase = "connect"
            checkActive()
            c.connectTimeout = deadline.socketTimeoutMillis(15000)
            c.readTimeout = deadline.socketTimeoutMillis(30000)
            c.connect()
            checkActive()
            if (body != null) {
                phase = "send_request"
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                checkActive()
            }

            phase = "await_headers"
            c.readTimeout = deadline.socketTimeoutMillis(30000)
            val code = c.responseCode
            checkActive()
            status = code.takeIf { it >= 100 }
            if (code !in 200..299) throw failed("http")

            phase = "read_body"
            val text = c.inputStream.bufferedReader().use { reader ->
                buildString {
                    val buffer = CharArray(4096)
                    while (true) {
                        checkActive()
                        c.readTimeout = deadline.socketTimeoutMillis(30000)
                        val count = reader.read(buffer)
                        if (count < 0) break
                        append(buffer, 0, count)
                    }
                }
            }
            checkActive()
            phase = "parse_json"
            return JSONObject(text).also { checkActive() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RegistrationDeadlineExceededException) {
            throw e
        } catch (e: RegistrationFailure) {
            throw e
        } catch (e: Exception) {
            checkActive()
            throw failed(failureKind(e), failureDetail(e))
        } finally {
            connections?.detach(c)
            c.disconnect()
        }
    }

    private fun registerWith(
        v: Variant, deadline: RegistrationDeadline, checkActive: () -> Unit,
    ): WarpAccount {
        checkActive()
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
        val reg = request(v, "reg", "POST", body, null, deadline, checkActive)
        checkActive()
        val full = if (reg.has("config")) reg
        else request(v, "reg/" + reg.getString("id"), "GET", null, reg.getString("token"), deadline, checkActive)
        checkActive()
        val cfg = full.getJSONObject("config")
        val addr = cfg.getJSONObject("interface").getJSONObject("addresses")
        val peer = cfg.getJSONArray("peers").getJSONObject(0)
        val ep = peer.getJSONObject("endpoint")
        val host = ep.optString("host").ifBlank { ep.optString("v4").substringBefore(":") + ":2408" }
        checkActive()
        return WarpAccount(priv, addr.getString("v4"), addr.getString("v6"), peer.getString("public_key"), host)
    }

    fun register(): WarpAccount = registerCheckingCancellation {}

    internal fun registerCheckingCancellation(checkActive: () -> Unit): WarpAccount {
        val deadline = registrationDeadline.get() ?: RegistrationDeadline(REGISTRATION_BUDGET_MS)
        fun check() {
            checkActive()
            deadline.check()
        }
        val errors = mutableListOf<String>()
        var networkFailuresOnly = true
        for (v in variants) {
            check()
            try {
                return registerWith(v, deadline, ::check)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RegistrationDeadlineExceededException) {
                throw e
            } catch (e: Exception) {
                check()
                val failure = e as? RegistrationFailure ?: RegistrationFailure(
                    stage = if (e is JSONException) "parse_config" else "prepare_registration",
                    kind = failureKind(e),
                    detail = failureDetail(e)
                )
                networkFailuresOnly = networkFailuresOnly &&
                    failure.kind in setOf("dns", "timeout", "connect", "io")
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
        check()
        val message = errors.joinToString(" | ")
        if (networkFailuresOnly) throw RegistrationNetworkException(message)
        throw RuntimeException(message)
    }
}
