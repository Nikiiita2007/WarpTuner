package com.example.warptuner

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitRoutingRegressionTest {
    private val base = "[Interface]\nPrivateKey = X\n\n[Peer]\nEndpoint = example.test:2408"
    private val included = base.replace("[Interface]", "[Interface]\n  includedApplications = app.old")
    private val excluded = base.replace("[Interface]", "[Interface]\nExcludedApplications = app.old")

    @Test fun offRemovesImportedRules() {
        for (cfg in listOf(included, excluded)) {
            assertEquals(base, applySplit(cfg, "off", "app.other", "app.own"))
        }
    }

    @Test fun emptyExclusionListRemovesImportedRules() {
        for (cfg in listOf(included, excluded)) {
            assertEquals(base, applySplit(cfg, "exclude", "", "app.own"))
            assertEquals(base, applySplit(cfg, "exclude", "app.own", "app.own"))
        }
    }
}
