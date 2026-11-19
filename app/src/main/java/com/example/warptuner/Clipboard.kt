package com.example.warptuner

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle

/** How long a copied config stays in the clipboard. */
internal const val CLIP_CLEAR_MS = 60_000L

/**
 * A clip that Android 13+ hides from the clipboard preview and keyboard suggestions.
 * Configs contain the WARP private key, so they must not be shown or kept in history.
 */
internal fun sensitiveClip(label: String, text: String): ClipData =
    ClipData.newPlainText(label, text).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
    }

/** Clears the clipboard only if it still holds [text], so newer user copies are kept. */
internal fun clearClipIfUnchanged(cm: ClipboardManager, text: String) {
    val current = try {
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    } catch (_: SecurityException) {
        null // Android 10+ denies reads while the app is in the background
    }
    if (current != text) return
    if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
}
