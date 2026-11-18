package com.example.warptuner

import android.content.Context
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class SimpleTunnel(private val tunnelName: String) : Tunnel {
    override fun getName(): String = tunnelName
    override fun onStateChange(newState: Tunnel.State) {}
    override fun isIpv4ResolutionPreferred(): Boolean = false
    override fun isMetered(): Boolean = false
}

/** The app does not run pre/post up/down scripts, so every hook is empty. */
class NoopHandler : TunnelActionHandler {
    override fun runPreUp(scripts: Collection<String?>?) {}

    override fun runPostUp(scripts: Collection<String?>?) {}

    override fun runPreDown(scripts: Collection<String?>?) {}

    override fun runPostDown(scripts: Collection<String?>?) {}
}

object Engine {
    private var backend: GoBackend? = null
    private val tunnel = SimpleTunnel("warptuner")

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext, NoopHandler()).also { backend = it }

    fun up(ctx: Context, text: String): String {
        val cfg = Config.parse(ByteArrayInputStream(text.trim().toByteArray()))
        return backend(ctx).setState(tunnel, Tunnel.State.UP, cfg).name
    }

    fun down(ctx: Context): String =
        backend(ctx).setState(tunnel, Tunnel.State.DOWN, null).name

    fun state(ctx: Context): String = backend(ctx).getState(tunnel).name

    fun trace(): String {
        val c = URL("https://www.cloudflare.com/cdn-cgi/trace")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().readText().lines()
                .filter { it.startsWith("warp=") || it.startsWith("ip=") || it.startsWith("loc=") }
                .joinToString("\n")
        } finally {
            c.disconnect()
        }
    }
}
