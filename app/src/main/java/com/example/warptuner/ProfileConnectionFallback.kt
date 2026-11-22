package com.example.warptuner

/** Caller owns the tunnel for the whole sequence and persists only a returned profile. */
internal suspend fun connectWithProfileFallback(
    original: String,
    alternative: String?,
    connectAndVerify: suspend (String) -> Boolean,
    disconnect: suspend () -> Boolean,
    onRetry: () -> Unit,
): String? {
    if (connectAndVerify(original)) return original
    if (!disconnect()) return null
    if (alternative == null || alternative == original) return null
    onRetry()
    if (connectAndVerify(alternative)) return alternative
    check(disconnect()) { "Could not confirm tunnel shutdown" }
    return null
}
