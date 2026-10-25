package com.coparently.app.data.stickers

import android.util.Log
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stickers between parents (October 2026, owner decisions): `stickers/{id}`, read by its receiver
 * and its sender only, never by a guest, a friend or a professional, and deleted with the family
 * (unpair) or with either account. A sent sticker also posts a card in the pair's thread (owner
 * decision, 25 October 2026: "the stickers are not visible in the chat"), which pushes nobody —
 * `sticker_received` is the push — and which the export leaves out.
 *
 * Firestore only, no Room table: a sticker is a small thing a parent looks at on Home, and a copy
 * on the phone would have to be cleared at unpair as the server clears its own.
 */
@Singleton
class StickerStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuthService,
    private val userRepository: UserRepository,
    private val fcmService: FcmService,
    private val activityAnnouncer: ActivityAnnouncer
) {

    /** The signed-in parent's uid, or null. */
    fun signedInUid(): String? = auth.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }

    /** Every sticker [uid] received, newest first; empty when they cannot be read. */
    fun observeReceived(uid: String): Flow<List<Sticker>> = observe("to", uid)

    /** Every sticker [uid] sent, newest first; empty when they cannot be read. */
    fun observeSent(uid: String): Flow<List<Sticker>> = observe("from", uid)

    // The rule admits a list only when it is keyed on the reader as receiver or as sender.
    private fun observe(field: String, uid: String): Flow<List<Sticker>> = callbackFlow {
        val registration = firestore.collection(COLLECTION).whereEqualTo(field, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Stickers could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val stickers = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.data?.let { StickerDocument.read(doc.id, it) } }
                    .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
                trySend(stickers)
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    /**
     * Sends [kind] to the other parent of [familyId] and pushes `sticker_received` to them.
     *
     * @return Whether the sticker was stored; the push is best-effort, as every push is.
     */
    suspend fun send(familyId: String, kind: StickerKind): Boolean {
        val me = signedInUid()
        val to = FamilyKey.membersOf(familyId)?.toList()?.firstOrNull { it != me }
        if (me == null || to == null || kind.fromApp) return false
        val id = firestore.collection(COLLECTION).document().id
        val stored = write(id, StickerDocument.parentSticker(me, to, familyId, kind, FieldValue.serverTimestamp()))
        if (stored) {
            val name = userRepository.getCurrentUser()?.name.orEmpty().take(ACTOR_MAX)
            fcmService.queueNotificationForUser(
                to,
                mapOf(PushPayload.TYPE to PushPayload.STICKER_RECEIVED, PushPayload.ACTOR to name)
            ).onFailure { Log.w(TAG, "The sticker's push could not be queued", it) }
            // In the thread of the family it was sent in, whichever family is on screen by now.
            activityAnnouncer.announce(cardFor(id, kind), senderName = name, partnerUid = to)
        }
        return stored
    }

    /** The chat card a sent sticker posts: its id, and its kind's wire value as the title. */
    internal fun cardFor(id: String, kind: StickerKind) = ActivityAnnouncement(
        kind = ActivityKind.STICKER_SENT,
        entityType = ActivityEntityType.STICKER,
        entityId = id,
        title = kind.wire
    )

    /** Records that the signed-in parent saw [sticker]; the server's clock, once. */
    suspend fun markSeen(sticker: Sticker) {
        if (sticker.seenAtMillis != null || sticker.toUid != signedInUid()) return
        try {
            firestore.collection(COLLECTION).document(sticker.id)
                .update("seenAt", FieldValue.serverTimestamp()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "A sticker could not be marked seen", e)
        }
    }

    private suspend fun write(id: String, data: Map<String, Any>): Boolean = try {
        firestore.collection(COLLECTION).document(id).set(data).await()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "A sticker could not be stored", e)
        false
    }

    private companion object {
        const val TAG = "StickerStore"
        const val COLLECTION = "stickers"
        const val ACTOR_MAX = 100
    }
}
