package com.example.warptuner

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class DownloadSample(val bytes: Long, val elapsedNanos: Long) {
    init { require(bytes > 0 && elapsedNanos > 0) }
    val mbps: Double get() = bytes.toDouble() * 8000.0 / elapsedNanos
}

/** Cooperative budget: a blocking read is additionally bounded by the socket timeout. */
internal fun measureDownload(
    input: InputStream,
    maxBytes: Long = 5_000_000,
    budgetNanos: Long = 5_000_000_000,
    nanoTime: () -> Long = System::nanoTime
): DownloadSample {
    require(maxBytes > 0 && budgetNanos > 0)
    val started = nanoTime()
    val buffer = ByteArray(16_384)
    var bytes = 0L
    while (bytes < maxBytes && nanoTime() - started < budgetNanos) {
        if (Thread.currentThread().isInterrupted) throw IOException("Measurement interrupted")
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - bytes).toInt())
        if (count < 0) break
        if (count == 0) throw IOException("No download progress")
        bytes += count
    }
    if (bytes == 0L) throw IOException("Empty speed response")
    return DownloadSample(bytes, (nanoTime() - started).coerceAtLeast(1))
}

internal fun downloadSpeedSummary(): String {
    val connection = URL("https://speed.cloudflare.com/__down?bytes=5000000")
        .openConnection() as HttpURLConnection
    connection.connectTimeout = 6000
    connection.readTimeout = 2000
    connection.instanceFollowRedirects = false
    connection.useCaches = false
    connection.setRequestProperty("Accept-Encoding", "identity")
    connection.setRequestProperty("Cache-Control", "no-cache")
    return try {
        if (connection.responseCode != 200) throw IOException("Speed endpoint unavailable")
        val sample = connection.inputStream.use { measureDownload(it) }
        String.format(Locale.getDefault(), "Скорость загрузки: %.2f Мбит/с", sample.mbps) +
            "\nИзмерен текущий сетевой путь приложения; замер не подтверждает маршрут через WARP."
    } catch (_: IOException) {
        "Скорость загрузки: измерение не выполнено (сеть, тайм-аут или сервис замера)."
    } finally {
        connection.disconnect()
    }
}
