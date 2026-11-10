package com.coparently.app.presentation.platform

/**
 * The clipboard, for text that is a bearer credential: an invitation code is one for the whole
 * family's calendar, chat and money.
 */
interface SensitiveClipboard : PlatformCapability {

    /**
     * Puts [text] on the clipboard, marked sensitive where the platform knows the mark (Android 13
     * and later keep the preview overlay blank for it).
     *
     * @param label A short, non-sensitive description of the clip for accessibility services
     */
    fun copySensitive(label: String, text: String)
}
