package com.example.warptuner

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private fun interfaceLines(lines: List<String>): IntRange {
    val starts = lines.indices.filter { lines[it].trim().equals("[Interface]", true) }
    require(starts.size == 1) { "Expected one Interface section" }
    val start = starts.single() + 1
    val end = (start until lines.size).firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
    return start until end
}

internal fun profileI1(config: String): String? {
    val lines = config.lines()
    val range = runCatching { interfaceLines(lines) }.getOrNull() ?: return null
    val values = range.filter { lines[it].substringBefore('=').trim().equals("I1", true) }
    return values.singleOrNull()?.let { lines[it].substringAfter('=', "").trim().takeIf(String::isNotBlank) }
}

internal fun profileWithI1(config: String, value: String): String {
    require(value.isNotBlank() && value.length <= 8192 && '\n' !in value && '\r' !in value)
    val newline = if ("\r\n" in config) "\r\n" else "\n"
    val lines = config.split(newline).toMutableList()
    val range = interfaceLines(lines)
    val matches = range.filter { lines[it].substringBefore('=').trim().equals("I1", true) }
    require(matches.size <= 1) { "Duplicate I1" }
    if (matches.isEmpty()) lines.add(range.first, "I1 = ${value.trim()}")
    else lines[matches.single()] = "I1 = ${value.trim()}"
    return lines.joinToString(newline)
}

/** Persistence runs only after verification. Cleanup also runs on cancellation. */
internal suspend fun connectAndRememberProfile(
    original: String,
    alternative: String?,
    verify: suspend (String) -> Boolean,
    disconnect: suspend () -> Boolean,
    retry: () -> Unit,
    remember: (String) -> Unit,
): String? {
    var retained = false
    var failure: Throwable? = null
    try {
        val selected = connectWithProfileFallback(original, alternative, verify, {
            check(disconnect()) { "Tunnel shutdown unconfirmed" }
            true
        }, retry) ?: return null
        currentCoroutineContext().ensureActive()
        remember(selected)
        retained = true
        return selected
    } catch (e: Throwable) {
        failure = e
        throw e
    } finally {
        if (!retained) {
            try {
                withContext(NonCancellable) { check(disconnect()) { "Tunnel shutdown unconfirmed" } }
            } catch (e: Exception) {
                val originalFailure = failure
                if (originalFailure != null) originalFailure.addSuppressed(e) else throw e
            }
        }
    }
}
