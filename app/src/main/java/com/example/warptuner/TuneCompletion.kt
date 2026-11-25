package com.example.warptuner

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Do not release ownership while cancelled tuning is still cleaning up. */
internal suspend fun finishTuneOperation(
    cleanup: suspend () -> Unit,
    finished: suspend () -> Unit,
) = withContext(NonCancellable) {
    try {
        cleanup()
    } finally {
        finished()
    }
}
