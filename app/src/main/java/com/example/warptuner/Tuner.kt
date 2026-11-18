package com.example.warptuner

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
data class Params(val jc: Int, val jmin: Int, val jmax: Int, val endpoint: String = "")


data class TuneResult(
    val name: String,
    val params: Params,
    val ok: Boolean,
    val rttMs: Double,
    val loss: Double,
    val mbps: Double,
    val sites: Map<String, Boolean>,
    val score: Double,
    val config: String,
    val err: String = ""
)

data class TuneOutcome(
    val ranked: List<TuneResult>,
    val baseline: TuneResult?,
    val rawMbps: Double = 0.0,
    val endpointNote: String = ""
)

class TuneCallbacks(
    val status: (String) -> Unit,
    val progress: (Int, Int) -> Unit,
    val result: (TuneResult) -> Unit
)
enum class TuneMode(
    val label: String, val random: Int, val tops: Int,
    val mutations: Int, val finalists: Int, val epRounds: Int
) {
    FAST("Быстро", 5, 2, 2, 2, 2),
    NORMAL("Обычно", 9, 3, 3, 3, 3),
    THOROUGH("Тщательно", 14, 3, 4, 3, 3)
}

object Prober {
    val SITES = linkedMapOf(
        "youtube" to "https://www.youtube.com/generate_204",
        "telegram" to "https://api.telegram.org/",
        "tg_web" to "https://web.telegram.org/"
    )

    fun traceWarp(): Boolean {
        val c = URL("https://www.cloudflare.com/cdn-cgi/trace")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        return try {
            val body = c.inputStream.bufferedReader().readText()
            body.contains("warp=on") || body.contains("warp=plus")
        } finally {
            c.disconnect()
        }
    }

    suspend fun waitReady(maxMs: Long): Boolean {
        val end = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < end) {
            try {
                if (traceWarp()) return true
            } catch (_: Exception) {
            }
            delay(1000)
        }
        return false
    }

    fun reach(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.instanceFollowRedirects = false
        c.responseCode
        c.disconnect()
        true
    } catch (_: Exception) {
        false
    }

    fun speed(): Double = try {
        val c = URL("https://speed.cloudflare.com/__down?bytes=10000000")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        val t = System.nanoTime()
        var n = 0L
        c.inputStream.use { s ->
            val buf = ByteArray(65536)
            while (true) {
                val r = s.read(buf)
                if (r < 0) break
                n += r
                if (System.nanoTime() - t > 5_000_000_000L) break
            }
        }
        c.disconnect()
        n * 8 / 1e6 / ((System.nanoTime() - t) / 1e9).coerceAtLeast(0.01)
    } catch (_: Exception) {
        0.0
    }

    fun score(
        ok: Boolean,
        rtt: Double,
        loss: Double,
        mbps: Double,
        sites: Map<String, Boolean>
    ): Double {
        if (!ok) return 0.0
        val frac = if (sites.isEmpty()) 0.0 else sites.values.count { it }.toDouble() / sites.size
        val m = mbps.coerceAtMost(50.0) / 50.0
        val lat = (1 - rtt / 1000.0).coerceAtLeast(0.0)
        val v = 100 * frac * (0.4 * m + 0.3 * lat + 0.3 * (1 - loss))
        return Math.round(v * 100) / 100.0
    }

    suspend fun measure(name: String, p: Params, cfg: String): TuneResult = coroutineScope {
        val traces = (1..4).map {
            async(Dispatchers.IO) {
                val t = System.nanoTime()
                val ok = try {
                    traceWarp()
                } catch (_: Exception) {
                    false
                }
                if (ok) (System.nanoTime() - t) / 1e6 else -1.0
            }
        }
        val siteJobs = SITES.map { (k, u) -> k to async(Dispatchers.IO) { reach(u) } }
        val rtts = traces.awaitAll().filter { it >= 0 }
        if (rtts.isEmpty()) {
            siteJobs.forEach { it.second.cancel() }
            return@coroutineScope TuneResult(
                name,
                p,
                false,
                0.0,
                1.0,
                0.0,
                emptyMap(),
                0.0,
                cfg,
                "нет warp=on"
            )
        }
        val rtt = rtts.sorted()[rtts.size / 2]
        val loss = (4 - rtts.size) / 4.0
        val sites = siteJobs.associate { (k, j) -> k to j.await() }
        val mbps = if (sites.values.none { it }) 0.0 else withContext(Dispatchers.IO) { speed() }
        TuneResult(name, p, true, rtt, loss, mbps, sites, score(true, rtt, loss, mbps, sites), cfg)
    }
}
private fun merge(a: TuneResult, b: TuneResult): TuneResult = a.copy(
    ok = a.ok && b.ok,
    rttMs = (a.rttMs + b.rttMs) / 2,
    loss = (a.loss + b.loss) / 2,
    mbps = (a.mbps + b.mbps) / 2,
    sites = a.sites.mapValues { (k, v) -> v && (b.sites[k] ?: false) },
    score = Math.round((a.score + b.score) / 2 * 100) / 100.0,
    err = (a.err + " " + b.err).trim()
)

object Tuner {
    private val rnd = Random(System.currentTimeMillis())

    private val EXTRA_ENDPOINTS = listOf(
        "engage.cloudflareclient.com:7281",
        "engage.cloudflareclient.com:4198"
    )
    private const val EP_ROUNDS = 3
    private const val EP_WIN = 1.15

    fun applyParams(base: String, p: Params): String {
        val kept = base.lines().filterNot { l ->
            val k = l.substringBefore("=").trim().lowercase()
            k == "jc" || k == "jmin" || k == "jmax"
        }
        val out = mutableListOf<String>()
        var done = false
        for (l in kept) {
            out += l
            if (!done && l.trim().equals("[Interface]", ignoreCase = true)) {
                out += "Jc = ${p.jc}"
                out += "Jmin = ${p.jmin}"
                out += "Jmax = ${p.jmax}"
                done = true
            }
        }
        return out.joinToString("\n")
    }

    fun readEndpoint(base: String): String? =
        Regex("(?im)^\\s*Endpoint\\s*=\\s*(\\S+)").find(base)?.groupValues?.get(1)

    fun applyEndpoint(cfg: String, ep: String): String {
        if (ep.isBlank()) return cfg
        val re = Regex("(?im)^(\\s*Endpoint\\s*=\\s*).*$")
        return if (re.containsMatchIn(cfg)) cfg.replace(re) { m -> m.groupValues[1] + ep } else cfg
    }

    fun readParams(base: String): Params? {
        fun num(k: String) = Regex("(?im)^\\s*$k\\s*=\\s*(\\d+)")
            .find(base)?.groupValues?.get(1)?.toIntOrNull()

        val jc = num("Jc") ?: return null
        val jmin = num("Jmin") ?: return null
        val jmax = num("Jmax") ?: return null
        return Params(jc, jmin, jmax)
    }

    fun randomParams(): Params {
        val jmin = rnd.nextInt(30, 151)
        return Params(rnd.nextInt(3, 11), jmin, jmin + rnd.nextInt(20, 401))
    }

    fun mutate(p: Params): Params {
        val jc = (p.jc + rnd.nextInt(-2, 3)).coerceIn(1, 20)
        val jmin = (p.jmin + rnd.nextInt(-20, 21)).coerceIn(8, 400)
        val jmax = (p.jmax + rnd.nextInt(-40, 41)).coerceIn(jmin + 10, 1000)
        return p.copy(jc = jc, jmin = jmin, jmax = jmax)
    }

    private fun median(l: List<Double>): Double =
        if (l.isEmpty()) 0.0 else l.sorted()[l.size / 2]

    fun fmt(r: TuneResult): String {
        val s = r.sites.entries.joinToString(" ") { (k, v) -> k + if (v) "+" else "-" }
        val ep = if (r.params.endpoint.isNotBlank()) " ep=${r.params.endpoint}" else ""
        return "${r.name} Jc=${r.params.jc} Jmin=${r.params.jmin} Jmax=${r.params.jmax}$ep " +
                "score=${r.score} rtt=${"%.0f".format(r.rttMs)}мс " +
                "loss=${"%.0f".format(r.loss * 100)}% ${"%.1f".format(r.mbps)}Мбит/с [$s] ${r.err}"
    }

    fun titleOf(name: String): String = when {
        name == "base" -> "текущий конфиг"
        name == "base2" -> "текущий конфиг (повтор)"
        name.startsWith("v-") -> "повтор: " + titleOf(name.drop(2))
        name.startsWith("ref") -> "эталон, круг " + name.drop(3)
        name.startsWith("ep") -> "endpoint №" + name.drop(2).replace("r", ", круг ")
        name.startsWith("c") -> "вариант " + (name.drop(1).toIntOrNull() ?: name)
        else -> name
    }

    suspend fun tune(ctx: Context, base: String, cb: TuneCallbacks, mode: TuneMode = TuneMode.NORMAL): TuneOutcome =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<TuneResult>()
            runCatching { Engine.down(ctx) }
            delay(500)
            cb.status("Измеряю скорость без VPN…")
            val raw = Prober.speed()
            val baseParams = readParams(base)
            val refParams = baseParams ?: Params(8, 55, 140)
            val baseEp = readEndpoint(base).orEmpty()
            val candidates = EXTRA_ENDPOINTS.filter { !it.equals(baseEp, ignoreCase = true) }
            val epEvals = if (candidates.isEmpty()) 0 else EP_ROUNDS * (1 + candidates.size)
            val total = (if (baseParams != null) 2 else 0) + epEvals + 9 + 9 + 3
            var done = 0
            var n = 0
            var ep = ""
            var epNote = ""

            suspend fun eval(p: Params, label: String? = null): TuneResult {
                currentCoroutineContext().ensureActive()
                val name = label ?: "c%02d".format(n++)
                val epInfo = if (p.endpoint.isNotBlank()) ", ${p.endpoint}" else ""
                cb.status("Проверяю ${titleOf(name)}: Jc=${p.jc}, Jmin=${p.jmin}, Jmax=${p.jmax}$epInfo")
                val text = applyEndpoint(applyParams(base, p), p.endpoint)
                val r = try {
                    Engine.up(ctx, text)
                    if (!Prober.waitReady(8000)) {
                        TuneResult(
                            name,
                            p,
                            false,
                            0.0,
                            1.0,
                            0.0,
                            emptyMap(),
                            0.0,
                            text,
                            "туннель не поднялся за 15 с"
                        )
                    } else {
                        Prober.measure(name, p, text)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    TuneResult(name, p, false, 0.0, 1.0, 0.0, emptyMap(), 0.0, text, e.toString())
                } finally {
                    withContext(NonCancellable) {
                        runCatching { Engine.down(ctx) }
                        delay(500)
                    }
                }
                done++
                cb.progress(done, total)
                cb.result(r)
                return r
            }

            if (candidates.isNotEmpty()) {
                val ratios = candidates.associateWith { mutableListOf<Double>() }
                val okCount = candidates.associateWith { 0 }.toMutableMap()
                for (round in 1..EP_ROUNDS) {
                    val ref = eval(refParams, "ref$round")
                    candidates.forEachIndexed { i, c ->
                        val r = eval(refParams.copy(endpoint = c), "ep${i + 1}r$round")
                        if (r.ok) okCount[c] = okCount.getValue(c) + 1
                        if (ref.mbps > 0.1) ratios.getValue(c) += r.mbps / ref.mbps
                    }
                }
                val lines = candidates.map { c ->
                    "$c: ×${"%.2f".format(median(ratios.getValue(c)))} " +
                            "(успешно ${okCount.getValue(c)}/$EP_ROUNDS)"
                }
                val winner = candidates.maxByOrNull { median(ratios.getValue(it)) }
                val ratio = winner?.let { median(ratios.getValue(it)) } ?: 0.0
                val head = if (winner != null && ratio >= EP_WIN && okCount.getValue(winner) >= 2) {
                    ep = winner
                    "Endpoint: выбран $winner (быстрее текущего в ${"%.2f".format(ratio)} раза)."
                } else {
                    "Endpoint: смена не даёт заметного выигрыша, оставлен текущий."
                }
                epNote = head + "\n" + lines.joinToString("\n")
            }

            val baseline1 = baseParams?.let { eval(it, "base") }
            if (baseline1 != null) results += baseline1
            repeat(9) { results += eval(randomParams().copy(endpoint = ep)) }
            val top = results.sortedByDescending { it.score }.take(3)
            for (t in top) repeat(3) { results += eval(mutate(t.params).copy(endpoint = ep)) }

            cb.status("Подтверждаю лучшие результаты повторным замером…")
            val finalists = results.sortedByDescending { it.score }.take(3)
            val confirmed = finalists.map { f -> merge(f, eval(f.params, "v-" + f.name)) }
            val baseline = if (baseline1 != null && baseParams != null) {
                merge(baseline1, eval(baseParams, "base2"))
            } else {
                null
            }
            TuneOutcome(confirmed.sortedByDescending { it.score }, baseline, raw, epNote)
        }
    }