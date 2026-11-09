package com.coparently.app.data.remote.firebase

/**
 * The push the sync sends when it uploads a child's record: a type and its facts, never a
 * sentence (SEC-3; [PushPayload]).
 *
 * `FcmService.createChildInfoNotificationPayload` until MON-24 phase 2 (Task 8g), unchanged; its
 * one caller, `SyncService`, moved to `:shared-data`. The receiving phone writes the sentence from
 * its own string resources (`PushNotifier`). An uploaded event pushes nothing since the
 * notification audit of 9 November 2026: the save's chat card announces it.
 */
object RecordPushes {

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
