package com.coparently.app.presentation.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Puts [text] on the clipboard, marked sensitive.
 *
 * For anything that is a bearer credential — the pairing invite code is one for the whole
 * family's calendar, chat and money. Android 13+ previews clipboard contents in an overlay and
 * lets other apps read them; the sensitive flag keeps the preview blank, which is the only
 * defence the platform offers. Below 13 the flag does not exist and the copy is an ordinary one.
 *
 * @param label A short, non-sensitive description of the clip for accessibility services
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
