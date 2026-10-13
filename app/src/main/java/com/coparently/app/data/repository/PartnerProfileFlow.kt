package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.domain.model.PartnerSummary
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [partnerId]'s profile as a [PartnerSummary], every time their `users/{uid}` document changes —
 * a rename, a new photo, a slot, a colour.
 *
 * A listener rather than a read: the profile used to be read once, inside the combine of the
 * signed-in account's own listeners, so the co-parent's new colour or name reached this phone
 * only when something unrelated changed here — in practice when the app was opened again
 * (October 2026). `PairingRepositoryImpl.observePairingState` attaches it for as long as the link
 * names this co-parent.
 *
 * A failed listener keeps what was already read: the likeliest failure is a read denied a moment
 * before the account's own document reports an unpair, and blanking a name and a colour that
 * were on screen would be wrong for that moment and then put right again. Only when nothing has
 * been read yet does a failure report the bare partner — the link is real, the profile unknown —
 * which is what the one-shot read answered when it failed. The listener is then re-attached a few
 * times with a backoff, and after that the last answer stands until the link moves.
 *
 * @param partnerId The co-parent's uid.
 * @param pairedAt When the link was made, carried into every summary unchanged.
 */
internal fun FirebaseFirestore.partnerProfile(partnerId: String, pairedAt: Long?): Flow<PartnerSummary> {
    // Whether anything has been sent — a profile or the bare partner. Shared across re-attachments
    // and set on the listener's own thread before the send, so an error arriving right behind a
    // snapshot cannot overwrite it, and a re-attachment that fails again says nothing new.
    val answered = AtomicBoolean(false)
    return callbackFlow {
        val registration = collection(USERS).document(partnerId)
            .addSnapshotListener { snapshot, error ->
                if (error == null) {
                    answered.set(true)
                    trySend(snapshot.toPartnerSummary(partnerId, pairedAt))
                    return@addSnapshotListener
                }
                // The UI renders a blank name identically whether the profile could not be read
                // or genuinely has none; the log is what tells the two apart after the fact.
                Log.w(TAG, "Partner profile listener failed", error)
                if (answered.compareAndSet(false, true)) trySend(bareSummary(partnerId, pairedAt))
                close(error)
            }
        awaitClose { registration.remove() }
    }
        .retryWhen { cause, attempt ->
            val retry = cause !is CancellationException && attempt < RETRY_ATTEMPTS
            if (retry) delay(RETRY_BASE_MILLIS shl attempt.toInt())
            retry
        }
        // Given up: the last profile read — or the bare partner — stands until the link moves.
        .catch { Log.w(TAG, "Partner profile listener gave up", it) }
}

/**
 * A co-parent's profile document as the pairing state carries it. A document that does not
 * exist — an offline first read with nothing cached — reads as the bare partner, every field
 * absent.
 */
internal fun DocumentSnapshot?.toPartnerSummary(partnerId: String, pairedAt: Long?): PartnerSummary =
    PartnerSummary(
        id = partnerId,
        name = this?.getString("name").orEmpty(),
        email = this?.getString("email").orEmpty(),
        pairedSinceMillis = pairedAt,
        // Written by the co-parent's own `ensureProfile`, so it stays null until their
        // phone runs a build that stores one — the card falls back to the initial.
        // Blank is normalized to null: the legacy full-profile write stores "" for a
        // missing photo, and an empty URL must read as "no photo", not as a broken one.
        photoUrl = this?.getString("profilePhotoUrl")?.takeIf { it.isNotBlank() },
        // The co-parent's slot, written by `assignSlots` when the pairing was accepted.
        // Room holds a `users` row for the signed-in user only, so this read is the app's
        // one source for which slot the other parent occupies — every screen that shows
        // their name depends on it. Blank normalizes to null for the same reason as the
        // photo: a pair created before slot assignment shipped has no slot here, and
        // "unknown" must not be dressed up as an answer.
        role = this?.getString("role")?.takeIf { it.isNotBlank() },
        // Their answer to "children, pets or both". Absent on a build that never wrote it,
        // which reads as "contributes nothing" rather than "everything" — one parent's real
        // answer must not be widened by the other's silence.
        caresFor = FamilyKind.fromStored(this?.getString("caresFor")),
        // The colour they chose for themselves — a preset's code or a custom hue. Blank
        // normalizes to null for the same reason the photo does: the legacy full-profile write
        // stores "" for a missing value, and "" must read as "has not chosen" rather than as an
        // unknown colour.
        colorCode = this?.getString("colorCode")?.takeIf { it.isNotBlank() }
    )

/** The partner known only by the link: no name, no photo, no slot, no colour. */
private fun bareSummary(partnerId: String, pairedAt: Long?) =
    PartnerSummary(id = partnerId, name = "", email = "", pairedSinceMillis = pairedAt)

private const val TAG = "PartnerProfile"
private const val USERS = "users"

/** Re-attachments of a failed listener before the last answer is left to stand. */
private const val RETRY_ATTEMPTS = 3L

/** The first wait before re-attaching; each attempt doubles it (2 s, 4 s, 8 s). */
private const val RETRY_BASE_MILLIS = 2_000L
