package com.coparently.app.data.remote.firebase

/**
 * This device's push registration beside the queue it pushes the co-parent through (MON-24 phase
 * 2, Task 8g): what the sync asks of Firebase Messaging, which stays in the app.
 *
 * `SyncService.syncUserData` keeps the signed-in account's `users/{uid}.fcmToken` naming this
 * device, and the sync's uploads queue their pushes ([PushQueue]). The app's `FcmService`, which
 * holds the token and the Settings switch, answers both on Android; the iPhone's push
 * registration will answer them there.
 */
interface PushDevice : PushQueue {

    /** This device's current push token, or null when none can be had (no push service, offline). */
    suspend fun getCurrentToken(): String?

    /**
     * Writes [token] as the signed-in account's `fcmToken`, unless the parent switched pushes off
     * in Settings. Best-effort: a failure is returned, never thrown.
     */
    suspend fun updateUserToken(token: String): Result<Unit>
}
