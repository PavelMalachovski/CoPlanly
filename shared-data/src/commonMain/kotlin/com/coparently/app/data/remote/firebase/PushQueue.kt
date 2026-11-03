package com.coparently.app.data.remote.firebase

/**
 * Where a repository leaves a push for the co-parent's phone: the `notification_queue` a Cloud
 * Function sends from (MON-24 phase 2, Task 8b).
 *
 * The payload is a type and its facts, never a sentence (SEC-3; [PushPayload]). The app's
 * `FcmService`, which also holds this device's token and stays in the app with Firebase Messaging,
 * is the Android implementation and stamps every push with the pair's family (M-8); the moved
 * repositories call only this.
 */
interface PushQueue {

    /**
     * Queues [notificationData] for [targetUserId]. Best-effort: a failure is returned, never
     * thrown, because the change it announces is already saved.
     */
    suspend fun queueNotificationForUser(targetUserId: String, notificationData: Map<String, String>): Result<Unit>
}
