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
                "Direct sockets, same resolved address for both modes. No HTTP or registration.\n" +
                "Certificate and hostname validation remain enabled.\n" +
                "TCP connect and socket reads each have an 8-second timeout. " +
                "DNS and the entire test do not have a hard time limit.\n" +
                "Keep the same network throughout. This is not a full HttpsURLConnection test."
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
        Log.w("WarpTlsProbe", message)
        runOnUiThread { if (!closed.get()) output.append(message + "\n") }
    }

    private fun runComparison() {
        start.isEnabled = false
        output.text = ""
        executor.execute {
            try {
                emit("event=dns_begin")
                val addresses = InetAddress.getAllByName(host)
                if (closed.get()) return@execute
                val address = addresses.first()
                emit("event=dns_returned address_count=${addresses.size} family=${if (address.address.size == 4) "ipv4" else "ipv6"}")
                probe(address, false)
                if (!closed.get()) probe(address, true)
            } catch (e: Exception) {
                emit("event=comparison_failed kind=${kind(e)}")
            } finally {
                emit("event=comparison_finished")
                runOnUiThread { if (!closed.get()) start.isEnabled = true }
            }
        }
    }

    private fun probe(address: InetAddress, tls12Only: Boolean) {
        val mode = if (tls12Only) "tls12" else "default"
        var phase = "tcp"
        val started = SystemClock.elapsedRealtime()
        val raw = Socket()
        active.set(raw)
        try {
            if (closed.get()) return
            emit("mode=$mode event=tcp_begin")
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
                        emit("mode=$mode event=unsupported")
                        return
                    }
                    ssl.enabledProtocols = arrayOf("TLSv1.2")
                }
                phase = "tls_handshake"
                emit("mode=$mode event=handshake_begin")
                ssl.startHandshake()
                emit("mode=$mode event=handshake_ok protocol=${ssl.session.protocol} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            }
        } catch (e: Exception) {
            emit("mode=$mode event=failed phase=$phase kind=${kind(e)} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
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
