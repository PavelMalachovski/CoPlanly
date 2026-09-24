package com.coparently.app.testing

import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk

/**
 * A [FirebaseMessaging] that behaves like a device without FCM: a token cannot be had, and the
 * delete and topic calls simply finish.
 *
 * Not a plain relaxed mock: a relaxed mock's `Task` never completes, so every `await()` on one
 * waits for ever — PR #103's on-screen e2e test hung for its whole time limit in
 * `SyncService.syncUserData`, on `FcmService.getCurrentToken()`. Shared by `FakeFirebaseModule`
 * and the e2e phones, which build a real `FcmService` over it so that what a repository *queues*
 * in `notification_queue` is the production write, and only the delivery (which has no emulator)
 * is missing.
 */
object NoFcm {

    /** A new stand-in; each caller gets its own, as each phone would have its own SDK. */
    fun messaging(): FirebaseMessaging = mockk(relaxed = true) {
        every { token } returns Tasks.forException(IllegalStateException("No FCM in instrumented tests"))
        every { deleteToken() } returns Tasks.forResult(null)
        every { subscribeToTopic(any()) } returns Tasks.forResult(null)
        every { unsubscribeFromTopic(any()) } returns Tasks.forResult(null)
    }
}
