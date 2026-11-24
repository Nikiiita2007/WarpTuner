package com.example.warptuner

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal enum class I1SelectionProblem { TOO_LARGE, MISSING_I1, ACCOUNT_CHANGED, BUSY, INVALID_CONFIG }
internal class I1SelectionException(val problem: I1SelectionProblem) : Exception()

/** A comparison token only; never persist account JSON in UI saved state. */
internal fun i1SelectionAccountToken(account: String): String =
    if (account.isBlank()) "" else MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

/** Caller owns and closes input. No source contents are logged or persisted. */
internal fun readSelectedI1(input: InputStream, checkActive: () -> Unit = {}): String {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (bytes.size() + count > 131072) throw I1SelectionException(I1SelectionProblem.TOO_LARGE)
        bytes.write(buffer, 0, count)
    }
    checkActive()
    return profileI1(bytes.toString("UTF-8").removePrefix("\uFEFF"))
        ?: throw I1SelectionException(I1SelectionProblem.MISSING_I1)
}
