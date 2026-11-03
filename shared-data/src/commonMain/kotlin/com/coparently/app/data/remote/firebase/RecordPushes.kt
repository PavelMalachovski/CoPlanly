package com.coparently.app.data.remote.firebase

/**
 * The pushes the sync sends when it uploads an event or a child's record: a type and its facts,
 * never a sentence (SEC-3; [PushPayload]).
 *
 * `FcmService.createEventNotificationPayload` and `createChildInfoNotificationPayload` until
 * MON-24 phase 2 (Task 8g), unchanged; their one caller, `SyncService`, moved to `:shared-data`.
 * The receiving phone writes the sentence from its own string resources (`PushNotifier`).
 */
object RecordPushes {

    /**
     * The payload for an event being created, updated or deleted.
     *
     * @param eventId The event, for the deep link
     * @param eventTitle The event's title, as the co-parent typed it
     * @param action `created`, `updated` or `deleted`
     * @param performedBy Display name of the parent who did it
     * @return The payload, or null when [action] is one nothing announces — see
     *   [PushPayload.eventType].
     */
    fun event(
        eventId: String,
        eventTitle: String,
        action: String,
        performedBy: String
    ): Map<String, String>? {
        val type = PushPayload.eventType(action) ?: return null
        return mapOf(
            PushPayload.TYPE to type,
            PushPayload.EVENT_ID to eventId,
            PushPayload.SUBJECT to eventTitle,
            PushPayload.ACTOR to performedBy
        )
    }

    /**
     * The payload for a child's information being updated.
     *
     * @param childInfoId The record, for the deep link
     * @param childName The child's name
     * @param updatedBy Display name of the parent who made the change
     */
    fun childInfo(
        childInfoId: String,
        childName: String,
        updatedBy: String
    ): Map<String, String> = mapOf(
        PushPayload.TYPE to PushPayload.CHILD_INFO_UPDATED,
        PushPayload.CHILD_INFO_ID to childInfoId,
        PushPayload.SUBJECT to childName,
        PushPayload.ACTOR to updatedBy
    )
}
