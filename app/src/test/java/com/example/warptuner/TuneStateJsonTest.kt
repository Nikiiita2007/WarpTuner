package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TuneStateJsonTest {
    private fun result(name: String, ep: String = "") = TuneResult(
        name = name,
        params = Params(4, 40, 70, ep),
        ok = true,
        rttMs = 120.5,
        loss = 0.25,
        mbps = 12.3,
        sites = mapOf("youtube" to true, "telegram" to false),
        score = 55.5,
        config = "[Interface]\nJc = 4",
        err = ""
    )

    @Test
    fun roundTripKeepsAllFields() {
        val outcome = TuneOutcome(
            ranked = listOf(result("v-c01", "h:1"), result("c02")),
            baseline = result("base"),
            rawMbps = 33.3,
            endpointNote = "Endpoint: note"
        )
        val back = outcomeFromJson(outcomeToJson(outcome))
        assertNotNull(back)
        assertEquals(outcome, back)
    }

    @Test
    fun roundTripWithoutBaseline() {
        val outcome = TuneOutcome(listOf(result("c01")), null)
        val back = outcomeFromJson(outcomeToJson(outcome))
        assertNotNull(back)
        assertNull(back!!.baseline)
        assertEquals(1, back.ranked.size)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(outcomeFromJson("not json"))
        assertNull(outcomeFromJson("{}"))
    }

    @Test
    fun oldSavedResultWithoutEndpointStillLoads() {
        val j = result("c01").toJson()
        j.remove("ep")
        assertEquals("", resultFromJson(j).params.endpoint)
    }
}
