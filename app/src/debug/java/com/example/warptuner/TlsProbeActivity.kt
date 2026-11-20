package com.example.warptuner

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Direct sockets only: no HTTP, account creation, Store access, or VPN changes. */
class TlsProbeActivity : Activity() {
    private val host = "api.cloudflareclient.com"
    private val closed = AtomicBoolean(false)
    private val active = AtomicReference<Socket?>(null)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WarpTlsProbe").apply { isDaemon = true }
    }
    @Volatile private var runId = 0L
    private lateinit var output: TextView
    private lateinit var start: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = "TLS comparison: api.cloudflareclient.com:443\n" +
                "All unique DNS addresses, both TLS modes on each address. No HTTP or registration.\n" +
                "Address hashes are stable comparison labels, not guaranteed anonymization.\n" +
                "Certificate and hostname validation remain enabled.\n" +
                "TCP connect and socket reads each have an 8-second timeout. " +
                "DNS and the entire test do not have a hard time limit. " +
                "Multiple unreachable addresses can take over a minute.\n" +
                "Keep the same network throughout each run. This is not a full HttpsURLConnection test."
        })
        start = Button(this).apply {
            text = "Run TLS comparison"
            setOnClickListener { runComparison() }
        }
        layout.addView(start)
        output = TextView(this).apply { setTextIsSelectable(true) }
        layout.addView(output)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun emit(message: String) {
        val line = "run=$runId $message"
        Log.w("WarpTlsProbe", line)
        runOnUiThread { if (!closed.get()) output.append(line + "\n") }
    }

    private fun runComparison() {
        start.isEnabled = false
        output.text = ""
        runId = SystemClock.elapsedRealtime()
        executor.execute {
            try {
                emit("event=dns_begin selection=all_resolved")
                val addresses = InetAddress.getAllByName(host).distinct()
                if (closed.get()) return@execute
                val ipv4 = addresses.count { it.address.size == 4 }
                emit("event=dns_returned address_count=${addresses.size} ipv4_count=$ipv4 ipv6_count=${addresses.size - ipv4}")
                for ((index, address) in addresses.withIndex()) {
                    if (closed.get()) break
                    val id = tlsProbeAddressId(address.address)
                    val family = if (address.address.size == 4) "ipv4" else "ipv6"
                    emit("event=address_begin address=$id family=$family index=$index")
                    probe(address, id, family, false)
                    if (!closed.get()) probe(address, id, family, true)
                    emit("event=address_finished address=$id family=$family")
                }
            } catch (e: Exception) {
                emit("event=comparison_failed kind=${kind(e)}")
            } finally {
                emit(if (closed.get()) "event=comparison_stopped" else "event=comparison_finished")
                runOnUiThread { if (!closed.get()) start.isEnabled = true }
            }
        }
    }

    private fun probe(address: InetAddress, id: String, family: String, tls12Only: Boolean) {
        val mode = if (tls12Only) "tls12" else "default"
        val target = "address=$id family=$family mode=$mode"
        var phase = "tcp"
        val started = SystemClock.elapsedRealtime()
        val raw = Socket()
        active.set(raw)
        try {
            if (closed.get()) return
            emit("$target event=tcp_begin")
            raw.connect(InetSocketAddress(address, 443), 8_000)
            raw.soTimeout = 8_000
            phase = "tls_setup"
            val factory = HttpsURLConnection.getDefaultSSLSocketFactory()
            (factory.createSocket(raw, host, 443, true) as SSLSocket).use { ssl ->
                ssl.useClientMode = true
                ssl.soTimeout = 8_000
                val parameters = ssl.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                parameters.serverNames = listOf(SNIHostName(host))
                ssl.sslParameters = parameters
                if (tls12Only) {
                    if ("TLSv1.2" !in ssl.supportedProtocols) {
                        emit("$target event=unsupported")
                        return
                    }
                    ssl.enabledProtocols = arrayOf("TLSv1.2")
                }
                phase = "tls_handshake"
                emit("$target event=handshake_begin")
                ssl.startHandshake()
                emit("$target event=handshake_ok protocol=${ssl.session.protocol} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            }
        } catch (e: Exception) {
            emit("$target event=failed phase=$phase kind=${kind(e)} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        } finally {
            active.compareAndSet(raw, null)
            runCatching { raw.close() }
        }
    }

    /** Never expose exception messages, peer addresses, certificates, or payloads. */
    private fun kind(error: Exception): String = when (error) {
        is UnknownHostException -> "dns"
        is SocketTimeoutException -> "timeout"
        is ConnectException -> "connect"
        is SSLException -> "tls"
        is java.io.IOException -> "io"
        else -> "unexpected"
    }

    override fun onDestroy() {
        closed.set(true)
        active.getAndSet(null)?.let { socket ->
            Thread { runCatching { socket.close() } }.start()
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
