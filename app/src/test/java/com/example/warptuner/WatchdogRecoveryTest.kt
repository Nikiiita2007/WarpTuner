package com.example.warptuner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WatchdogRecoveryTest {
    @Test fun unverifiedCandidateIsDisconnectedAndNeverSaved() = runBlocking {
        val events = mutableListOf<String>()
        val result = watchdogTryCandidate(
            "last", { events += "down"; true }, { events += "up:$it"; true },
            { events += "verify"; false }, { events += "save:$it" },
        )
        assertFalse(result)
        assertEquals(listOf("down", "up:last", "verify", "down"), events)
    }

    @Test fun verifiedBackupIsSavedAfterVerificationAndStaysUp() = runBlocking {
        val events = mutableListOf<String>()
        val result = watchdogTryCandidate(
            "backup", { events += "down"; true }, { events += "up:$it"; true },
            { events += "verify"; true }, { events += "save:$it" },
        )
        assertTrue(result)
        assertEquals(listOf("down", "up:backup", "verify", "save:backup"), events)
    }

    @Test fun failedDisconnectDoesNotReportDownOrClearActiveProfile() {
        var active = "current"
        assertFalse(watchdogDisconnect({ "UP" }, { active = it }))
        assertFalse(watchdogDisconnect({ error("backend failure") }, { active = it }))
        assertEquals("current", active)
    }
}
