package com.example.warptuner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneCompletionTest {
    @Test fun releasesOnlyAfterCleanup() = runBlocking {
        val events = mutableListOf<String>()
        finishTuneOperation(
            cleanup = { events += "cleanup" },
            finished = { events += "finished" },
        )
        assertEquals(listOf("cleanup", "finished"), events)
    }

    @Test fun cancellationDoesNotReleaseWhileCleanupIsSuspended() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val worker = launch {
            try {
                awaitCancellation()
            } finally {
                finishTuneOperation(
                    cleanup = {
                        events += "cleanup"
                        entered.complete(Unit)
                        release.await()
                        events += "cleaned"
                    },
                    finished = { events += "finished" },
                )
            }
        }
        yield()
        worker.cancel()
        entered.await()
        try {
            assertEquals(listOf("cleanup"), events)
            assertTrue(!worker.isCompleted)
        } finally {
            release.complete(Unit)
            worker.join()
        }
        assertEquals(listOf("cleanup", "cleaned", "finished"), events)
    }

    @Test fun cleanupFailureStillRunsCompletionAndIsPropagated() = runBlocking {
        val expected = IllegalStateException("synthetic cleanup failure")
        var finished = false
        var caught: IllegalStateException? = null
        try {
            finishTuneOperation(
                cleanup = { throw expected },
                finished = { finished = true },
            )
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertTrue("Completion must run even when cleanup throws", finished)
        assertEquals(expected.message, caught?.message)
        // Coroutine stack trace recovery may rethrow a copy with the original as its cause.
        assertTrue("The original cleanup failure must be propagated", caught === expected || caught?.cause === expected)
    }
}
