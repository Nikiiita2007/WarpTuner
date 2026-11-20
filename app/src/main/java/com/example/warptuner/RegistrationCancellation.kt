package com.example.warptuner

import android.util.Log
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fixed event names only: never log connection objects, exceptions, or payloads. */
private fun registrationLifecycle(event: String) {
    // Diagnostics must not break registration, including in local JVM tests.
    runCatching { Log.w("WarpRegistration", "event=$event") }
}

/** One synchronous request at a time; disconnect must never run under the lock. */
internal class RegistrationConnections {
    private val lock = Any()
    private var stopped = false
    private var active: HttpsURLConnection? = null

    fun attach(connection: HttpsURLConnection) {
        val reject = synchronized(lock) {
            check(active == null) { "A registration connection is already active" }
            if (stopped) true else {
                active = connection
                false
            }
        }
        registrationLifecycle(if (reject) "connection_rejected" else "connection_attached")
        if (reject) {
            registrationLifecycle("rejected_disconnect_begin")
            val result = runCatching { connection.disconnect() }
            registrationLifecycle(if (result.isSuccess) "rejected_disconnect_returned" else "rejected_disconnect_failed")
        }
    }

    fun detach(connection: HttpsURLConnection) {
        synchronized(lock) {
            if (active === connection) active = null
        }
        registrationLifecycle("connection_detached")
    }

    fun cancel() {
        registrationLifecycle("cancel_requested")
        val connection = synchronized(lock) {
            stopped = true
            active.also { active = null }
        }
        if (connection == null) {
            registrationLifecycle("cancel_no_active_connection")
        } else {
            registrationLifecycle("cancel_disconnect_begin")
            val result = runCatching { connection.disconnect() }
            registrationLifecycle(if (result.isSuccess) "cancel_disconnect_returned" else "cancel_disconnect_failed")
        }
    }
}

internal val registrationConnections = ThreadLocal<RegistrationConnections>()

/** Best-effort disconnect, not a guarantee that platform DNS/I/O returns promptly. */
internal suspend fun <T> withRegistrationCancellation(
    deadline: RegistrationDeadline,
    block: () -> T,
): T = coroutineScope {
    registrationLifecycle("session_entered")
    val owner = currentCoroutineContext()
    owner.ensureActive()
    deadline.check()
    val connections = RegistrationConnections()
    val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            while (true) delay(deadline.remainingMillis())
        } catch (_: RegistrationDeadlineExceededException) {
            registrationLifecycle("deadline_expired")
            connections.cancel()
        } finally {
            if (!owner.isActive) {
                registrationLifecycle("owner_cancelled")
                connections.cancel()
            }
            registrationLifecycle("watcher_finished")
        }
    }
    try {
        withContext(Dispatchers.IO + registrationConnections.asContextElement(connections)) {
            registrationLifecycle("worker_entered")
            try {
                owner.ensureActive()
                deadline.check()
                block().also {
                    registrationLifecycle("worker_block_returned")
                    owner.ensureActive()
                    deadline.check()
                }
            } finally {
                registrationLifecycle("worker_exiting")
            }
        }
    } finally {
        registrationLifecycle("session_cleanup_entered")
        watcher.cancel()
    }
}
