package com.coparently.app.data.remote.firebase

/**
 * This device's push token as the iPhone's [FirestorePushQueue] reads and keeps it (iPhone phase 4
 * part C): the half of [PushDevice] that Android's `FcmService` answers with Firebase Messaging,
 * answered on the iPhone by `IosPushRegistration` over Apple's service. Common, because the queue
 * is; the only implementation is the iPhone's.
 */
interface PushTokenKeeper {

    /** This device's current push token, or null when none can be had. */
    suspend fun currentToken(): String?

    /**
     * Writes [token] as the signed-in account's `users/{uid}.fcmToken`, unless pushes are off on
     * this phone. Best-effort: a failure is returned, never thrown.
     */
    suspend fun keepToken(token: String): Result<Unit>
}
