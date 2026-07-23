package com.coparently.app.domain.repository

/**
 * Abstraction over remote binary storage for event photos.
 *
 * Mirrors [ReceiptStorage] for calendar events: a locally picked image (referenced
 * by a content URI string so the domain layer stays free of Android types) is
 * uploaded and a shareable download URL is returned for the co-parent to load.
 */
interface EventImageStorage {

    /**
     * Uploads the image behind [localUri] as the photo for [eventId].
     *
     * @param eventId Event the image belongs to; used as the remote object name
     *                so re-uploading replaces the previous image.
     * @param localUri Content URI string of the picked image on this device.
     * @return Download URL of the uploaded image.
     */
    suspend fun uploadEventImage(eventId: String, localUri: String): String

    /**
     * Deletes the remote image for [eventId], if any. Safe to call when none exists.
     */
    suspend fun deleteEventImage(eventId: String)
}
