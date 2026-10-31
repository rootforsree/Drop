package com.rootdrop.share.picker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copies plain text, such as the sharing URL, to the system clipboard.
 * This is a small helper only; it holds no state and starts no sharing.
 */
fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard: ClipboardManager? = context.getSystemService(
        Context.CLIPBOARD_SERVICE,
    ) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
