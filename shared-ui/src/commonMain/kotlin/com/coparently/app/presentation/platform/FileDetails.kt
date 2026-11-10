package com.coparently.app.presentation.platform

/**
 * What a screen says about a file it shows or is about to send: a picked file's name and size, and
 * a size as the reader's language writes it ("1.2 MB", "1,2 MB").
 *
 * On Android it is the `ContentResolver`'s display name and size, and
 * `Formatter.formatShortFileSize` in the Activity's language, as the chat's confirmation and the
 * attachment chip wrote them before they moved (MON-24 phase 2, Task 11).
 */
interface FileDetails : PlatformCapability {

    /**
     * The name and the formatted size of the file at [contentUri] (a picker's answer, as text), each
     * blank when the platform cannot say: the name then falls back to the address's last segment.
     */
    fun describe(contentUri: String): PickedFileDescription

    /** [bytes] as the reader's language writes a file size, short form. */
    fun formatSize(bytes: Long): String
}

/** A picked file as a confirmation names it: its display name and its formatted size. */
data class PickedFileDescription(val name: String, val size: String)
