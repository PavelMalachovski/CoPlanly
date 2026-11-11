package com.coparently.app.data.remote.firebase

import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * The iPhone's [PushDevice] (MON-24 phase 3, Task 6): the co-parent is told through the same
 * `notification_queue` the Cloud Function sends from, with the document the app's
 * `FcmService.queueNotificationForUser` writes, key for key and type for type:
 * - `targetUserId`, the addressee;
 * - `data`, the payload — a type and its facts, never a sentence (CLAUDE.md item 15) — stamped with
 *   the pair's family ([PushPayload.FAMILY_ID]) unless it names one already;
 * - `createdAt`, this phone's clock in epoch milliseconds, a `Long`;
 * - `status`, "pending";
 * - and nothing else.
 *
 * `firestore.rules` validates the create (the addressee is the writer or a live co-parent, a type
 * the client may produce, no `title` or `body`, every free-text key bounded, and the family one
 * both are in), so this writes no key of its own. A new id is made on the phone and the document
 * written whole, which is the create `collection.add()` makes on Android.
 *
 * This phone has **no push token yet** (the iPhone registers with Apple's service in phase 4), so
 * [getCurrentToken] answers null and [updateUserToken] writes nothing: the sync then leaves the
 * profile's `fcmToken` as it was, and the iPhone receives no push until it has one. Android keeps
 * `FcmService`.
 *
 * @property firestore Where the queue document is written.
 * @property signedInUser Who sends it: the family is the sender's with the addressee.
 */
class FirestorePushQueue(
    private val firestore: FirestoreClient,
    private val signedInUser: SignedInUser
) : PushDevice {

    /**
     * Queues [notificationData] for [targetUserId], as `FcmService` does. Best-effort: a failure is
     * returned, never thrown, because the change it announces is already saved.
     */
    override suspend fun queueNotificationForUser(
        targetUserId: String,
        notificationData: Map<String, String>
    ): Result<Unit> = try {
        val familyId = FamilyKey.orNull(signedInUser.getCurrentUid(), targetUserId)
        val stamped = if (familyId == null || PushPayload.FAMILY_ID in notificationData) {
            notificationData
        } else {
            notificationData + (PushPayload.FAMILY_ID to familyId)
        }
        val document = mapOf(
            "targetUserId" to targetUserId,
            "data" to stamped,
            "createdAt" to Clock.System.now().toEpochMilliseconds(),
            "status" to "pending"
        )
        firestore.set(DocumentPath(COLLECTION, firestore.newDocumentId(COLLECTION)), document)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Result.failure(e)
    }

    /** No push token on the iPhone yet (phase 4 registers with Apple's service). */
    override suspend fun getCurrentToken(): String? = null

    /** Nothing to keep: there is no token, so nothing is written and nothing fails. */
    override suspend fun updateUserToken(token: String): Result<Unit> = Result.success(Unit)

    private companion object {
        /** The queue the Cloud Function `sendNotification` reads. */
        const val COLLECTION = "notification_queue"
    }
}
