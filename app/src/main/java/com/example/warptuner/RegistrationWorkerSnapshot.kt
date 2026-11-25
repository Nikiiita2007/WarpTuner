package com.example.warptuner

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/** Metadata only: intentionally omit filenames, exception messages, and local variables. */
internal fun registrationStackFrame(frame: StackTraceElement): String =
    "${frame.className}#${frame.methodName}:${frame.lineNumber}"

/** A best-effort sample, not proof of the underlying network failure. */
internal class RegistrationWorkerSnapshot {
    private val worker = AtomicReference<Thread?>(null)

    fun enter() { worker.set(Thread.currentThread()) }
    fun exit() { worker.compareAndSet(Thread.currentThread(), null) }

    fun logAtDeadline() {
        // Never let unavailable platform diagnostics prevent connection cancellation.
        runCatching {
            val thread = worker.get()
            if (thread == null) {
                Log.w("WarpRegistration", "event=deadline_stack_unavailable")
            } else {
                val frames = thread.stackTrace.take(32)
                if (worker.get() !== thread) {
                    Log.w("WarpRegistration", "event=deadline_stack_worker_finished")
                } else {
                    Log.w("WarpRegistration", "event=deadline_stack_sample frames=${frames.size}")
                    frames.forEachIndexed { index, frame ->
                        Log.w("WarpRegistration", "event=deadline_stack index=$index frame=${registrationStackFrame(frame)}")
                    }
                }
            }
        }
    }
}
