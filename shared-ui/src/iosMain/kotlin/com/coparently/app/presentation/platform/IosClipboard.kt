package com.coparently.app.presentation.platform

import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionLocalOnly

/**
 * The iPhone's clipboard for a bearer credential (MON-24 phase 4 part E1, Task 2): the text goes
 * on the general pasteboard as plain text and **local only**, so Universal Clipboard does not carry
 * an invitation code to the parent's other devices. iOS has no clip label, so the label is not kept.
 */
internal object IosClipboard : SensitiveClipboard {

    override val available: Boolean = true

    override fun copySensitive(label: String, text: String) {
        UIPasteboard.generalPasteboard.setItems(
            listOf(mapOf<Any?, Any?>("public.utf8-plain-text" to text)),
            mapOf<Any?, Any?>(UIPasteboardOptionLocalOnly to true)
        )
    }
}
