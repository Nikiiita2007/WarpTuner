package com.example.warptuner

import android.annotation.SuppressLint
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

/** The app does not run pre/post up/down scripts. */
class NoopHandler : TunnelActionHandler {
    override fun runPreUp(scripts: Collection<String?>?) {}
    override fun runPostUp(scripts: Collection<String?>?) {}
    override fun runPreDown(scripts: Collection<String?>?) {}
    override fun runPostDown(scripts: Collection<String?>?) {}
}

internal class GoTunnelBackend(ctx: Context) : TunnelBackend {
    private val applicationContext = ctx.applicationContext
    // Parse invalid configs before initializing the native backend, as before the refactor.
    private val backend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GoBackend(applicationContext, NoopHandler())
    }
    private val tunnel = SimpleTunnel("warptuner")

    override fun up(config: String): String {
        val parsed = Config.parse(ByteArrayInputStream(config.trim().toByteArray()))
        return backend.setState(tunnel, Tunnel.State.UP, parsed).name
    }

    override fun down(): String = backend.setState(tunnel, Tunnel.State.DOWN, null).name
    override fun state(): String = backend.getState(tunnel).name
}

object Engine {
    // The adapter retains only application context.
    @SuppressLint("StaticFieldLeak")
    @Volatile private var operations: TunnelOperations? = null

    // Initialization is synchronized; operation sequences are NOT serialized here.
    private fun operations(ctx: Context): TunnelOperations =
        operations ?: synchronized(this) {
            operations ?: TunnelOperations(GoTunnelBackend(ctx.applicationContext)).also {
                operations = it
            }
        }

    fun up(ctx: Context, text: String): String = operations(ctx).up(text)
    fun down(ctx: Context): String = operations(ctx).down()
    fun state(ctx: Context): String = operations(ctx).state()

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
