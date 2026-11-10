package com.coparently.app.presentation.platform

/**
 * Hands something to another app on the phone: a web page, a file in a viewer, the dialler, a mail
 * draft, this app's page in the system settings.
 *
 * Every call answers whether an app took it, because a tap that silently does nothing looks
 * exactly like a broken button: the screen says so instead (a tablet with no dialler, a work
 * profile with no browser).
 */
interface OpenUrl : PlatformCapability {

    /**
     * Opens [url] in the browser.
     *
     * @return false when no installed app can open it
     */
    fun open(url: String): Boolean

    /**
     * Opens a shared file (MON-23) in whatever viewer the phone has, with a one-off read grant —
     * never a download URL (CLAUDE.md item 31).
     *
     * @param path The file's local path, as `SharedFileCache` placed it
     * @param contentType Its type, which decides the viewer
     * @return false when no installed app can open [contentType]
     */
    fun openFile(path: String, contentType: String): Boolean

    /**
     * Opens the dialler with [number] filled in.
     *
     * @return false when the phone has no dialler
     */
    fun dial(number: String): Boolean

    /**
     * Opens a mail draft to [address], with [subject] and [body] filled in (the chat's report).
     *
     * @return false when no mail app can take it
     */
    fun composeEmail(address: String, subject: String, body: String): Boolean

    /**
     * Opens this app's page in the system settings, where a permission refused for good can still
     * be allowed.
     *
     * @return false when the settings could not be opened
     */
    fun openAppSettings(): Boolean
}
