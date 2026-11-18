package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Test

class ProberScoreTest {
    private val allSites = mapOf("youtube" to true, "telegram" to true, "tg_web" to true)

    @Test
    fun failedCandidateScoresZero() {
        assertEquals(0.0, Prober.score(false, 10.0, 0.0, 50.0, allSites), 0.0)
    }

    @Test
    fun perfectCandidateScores100() {
        assertEquals(100.0, Prober.score(true, 0.0, 0.0, 50.0, allSites), 0.001)
    }

    @Test
    fun speedAbove50MbpsIsCapped() {
        val capped = Prober.score(true, 100.0, 0.0, 500.0, allSites)
        val atCap = Prober.score(true, 100.0, 0.0, 50.0, allSites)
        assertEquals(atCap, capped, 0.0)
    }

    @Test
    fun noReachableSitesOrNoSitesScoresZero() {
        val none = mapOf("youtube" to false, "telegram" to false)
        assertEquals(0.0, Prober.score(true, 10.0, 0.0, 50.0, none), 0.0)
        assertEquals(0.0, Prober.score(true, 10.0, 0.0, 50.0, emptyMap()), 0.0)
    }

    @Test
    fun partialResultMatchesFormula() {
        val sites = mapOf("youtube" to true, "telegram" to true, "tg_web" to false)
        assertEquals(46.33, Prober.score(true, 100.0, 0.25, 25.0, sites), 0.001)
    }

    @Test
    fun latencyAboveOneSecondContributesNothing() {
        assertEquals(70.0, Prober.score(true, 2000.0, 0.0, 50.0, allSites), 0.001)
    }
}
