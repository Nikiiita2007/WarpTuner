package com.example.warptuner

import java.security.MessageDigest

/** Stable metadata label, not a promise of IP anonymization or collision freedom. */
internal fun tlsProbeAddressId(bytes: ByteArray): String {
    require(bytes.size == 4 || bytes.size == 16)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = "0123456789abcdef"
    return buildString {
        for (byte in hash.take(8)) {
            val value = byte.toInt() and 255
            append(hex[value ushr 4])
            append(hex[value and 15])
        }
    }
}
