package com.example.warptuner

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
        if (reject) runCatching { connection.disconnect() }
    }

    fun detach(connection: HttpsURLConnection) {
        synchronized(lock) {
            if (active === connection) active = null
        }
    }

    fun cancel() {
        val connection = synchronized(lock) {
            stopped = true
            active.also { active = null }
        }
        connection?.let { runCatching { it.disconnect() } }
    }
}

internal val registrationConnections = ThreadLocal<RegistrationConnections>()

/** Best-effort disconnect, not a guarantee that platform DNS/I/O returns promptly. */
internal suspend fun <T> withRegistrationCancellation(
    deadline: RegistrationDeadline,
    block: () -> T,
): T = coroutineScope {
    val owner = currentCoroutineContext()
    owner.ensureActive()
    deadline.check()
    val connections = RegistrationConnections()
    val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            while (true) delay(deadline.remainingMillis())
        } catch (_: RegistrationDeadlineExceededException) {
            connections.cancel()
        } finally {
            if (!owner.isActive) connections.cancel()
        }
    }
    try {
        withContext(Dispatchers.IO + registrationConnections.asContextElement(connections)) {
            owner.ensureActive()
            deadline.check()
            block().also {
                owner.ensureActive()
                deadline.check()
            }
        }
    } finally {
        watcher.cancel()
    }
}
