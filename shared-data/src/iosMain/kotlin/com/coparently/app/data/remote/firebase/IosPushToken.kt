package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * This iPhone's Firebase Messaging token (iPhone phase 4 part C), which the app's Swift side answers
 * (`PushTokenBridge.swift`, over `Messaging.messaging()`), as it answers Google's sheet through
 * `GoogleSignInBridge`. The APNs token reaches Firebase Messaging there (`AppDelegate`); the shared
 * code only ever sees the FCM token, which is what `users/{uid}.fcmToken` holds on both platforms.
 *
 * Callbacks rather than `suspend` functions, as the other bridges are: each is called once, on any
 * thread. [IosPushRegistration] waits on them ([awaitCurrent], [awaitDelete]).
 */
interface IosPushToken {

    /**
     * Asks Firebase Messaging for this device's token and calls [completion] once with it, or with a
     * null token and an `error` for the log when there is none (no APNs token yet, offline, or a
     * build on the Firebase emulators, which never asks Apple or Google for one).
     */
    fun current(completion: (token: String?, error: String?) -> Unit)

    /**
     * Deletes this device's token at Firebase Messaging, so it stops naming this phone wherever it
     * is still stored (CLAUDE.md item 23), and calls [completion] once, with an `error` for the log
     * when that failed.
     */
    fun delete(completion: (error: String?) -> Unit)
}

/** [IosPushToken.current], waited on; null when there is none. */
internal suspend fun IosPushToken.awaitCurrent(): String? = suspendCancellableCoroutine { continuation ->
    current { token, error ->
        if (error != null) logger.w(TAG, "No push token: $error")
        if (continuation.isActive) continuation.resume(token?.takeIf { it.isNotBlank() })
    }
}

/** [IosPushToken.delete], waited on; a failure is logged, never thrown. */
internal suspend fun IosPushToken.awaitDelete(): Unit = suspendCancellableCoroutine { continuation ->
    delete { error ->
        if (error != null) logger.w(TAG, "Could not delete the push token: $error")
        if (continuation.isActive) continuation.resume(Unit)
    }
}

private const val TAG = "IosPushToken"
