package com.example.warptuner

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DownloadMeasurementTest {
    @Test fun convertsBytesAndNanosecondsToMegabitsPerSecond() {
        assertEquals(8.0, DownloadSample(1_000_000, 1_000_000_000).mbps, 0.000001)
    }

    @Test fun neverReadsBeyondByteLimit() {
        val input = ByteArrayInputStream(ByteArray(200))
        var time = 0L
        val sample = measureDownload(input, maxBytes = 100, nanoTime = { time += 1_000_000; time })
        assertEquals(100L, sample.bytes)
        assertEquals(100, input.available())
    }

    @Test fun stopsAtCooperativeTimeBudget() {
        val input = ByteArrayInputStream(ByteArray(100_000))
        var time = 0L
        val sample = measureDownload(input, budgetNanos = 3_000_000_000, nanoTime = { time += 1_000_000_000; time })
        assertTrue(sample.bytes > 0)
        assertTrue(sample.bytes < 100_000)
        assertTrue(input.available() > 0)
    }

    @Test(expected = IOException::class)
    fun emptyResponseIsAnErrorNotZeroSpeed() {
        measureDownload(ByteArrayInputStream(ByteArray(0)))
    }
}
