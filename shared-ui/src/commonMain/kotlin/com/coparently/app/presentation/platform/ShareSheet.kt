package com.coparently.app.presentation.platform

/**
 * The system's share sheet: an invitation's text, a calendar link, an exported record.
 *
 * On Android it is `Intent.ACTION_SEND` in `Intent.createChooser`, as the screens built it before
 * they moved.
 */
interface ShareSheet : PlatformCapability {

    /**
     * Offers [text] as plain text to whatever app the parent picks.
     *
     * @param chooserTitle The sheet's title, from the calling feature's strings
     */
    fun shareText(text: String, chooserTitle: String)

    /**
     * Offers the file at [contentUri] (the form this phone's own file provider hands out, as text)
     * with a one-off read grant, and nothing else.
     *
     * @param mimeType The file's type, as the receiving app should see it
     * @param chooserTitle The sheet's title
     */
    fun shareFile(contentUri: String, mimeType: String, chooserTitle: String)
}
