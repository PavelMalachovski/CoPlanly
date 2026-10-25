package com.coparently.app.data.support

import android.util.Log
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** What [SupportAgreementStore.observe] knows of a family's child support. */
sealed interface SupportRead {
    /** The document could not be read. */
    data object Unavailable : SupportRead

    /** The family has agreed nothing and proposed nothing yet. */
    data object None : SupportRead

    /** The agreement and any proposal. */
    data class Known(val agreement: SupportAgreement) : SupportRead
}

/**
 * A family's child support (25 October 2026): `support_agreements/{familyId}`, Firestore only.
 *
 * Every change is a proposal the other parent answers, never an overwrite: the first terms too.
 * Each step posts a card in the family's thread, pushed like any message — no push type of its
 * own — and the months paid are `payments` of kind `support` (`PaymentStore`).
 */
@Singleton
class SupportAgreementStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuthService,
    private val userRepository: UserRepository,
    private val activityAnnouncer: ActivityAnnouncer
) {

    private fun signedInUid(): String? = auth.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }

    /** [familyId]'s child support, live. */
    fun observe(familyId: String): Flow<SupportRead> = callbackFlow {
        val registration = firestore.collection(COLLECTION).document(familyId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        Log.w(TAG, "Child support could not be read", error)
                        trySend(SupportRead.Unavailable)
                    }
                    snapshot == null || !snapshot.exists() -> trySend(SupportRead.None)
                    else -> trySend(
                        snapshot.data?.let { SupportAgreementDocument.read(familyId, it) }
                            ?.let { SupportRead.Known(it) } ?: SupportRead.Unavailable
                    )
                }
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    /** [familyId]'s child support as the server holds it now, or null when it cannot be read. */
    suspend fun readFromServer(familyId: String): SupportRead? = try {
        val snapshot = firestore.collection(COLLECTION).document(familyId).get(Source.SERVER).await()
        if (!snapshot.exists()) {
            SupportRead.None
        } else {
            snapshot.data?.let { SupportAgreementDocument.read(familyId, it) }?.let { SupportRead.Known(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "Child support could not be read from the server", e)
        null
    }

    /**
     * Proposes [terms] for [familyId] — the first terms or a change — creating the document when
     * the family has none. Refused while the co-parent's own proposal waits: answer it first.
     *
     * @param existing What the screen showed, which says whether the document exists.
     * @return Whether the proposal was stored.
     */
    @Suppress("ReturnCount") // one early answer per thing that makes a proposal impossible
    suspend fun propose(familyId: String, terms: SupportTerms, existing: SupportAgreement?): Boolean {
        val me = signedInUid() ?: return false
        if (existing?.proposal?.let { it.proposedBy != me } == true) return false
        val members = FamilyKey.membersOf(familyId)?.toList()?.sorted() ?: return false
        val proposal = SupportProposal(terms, me, System.currentTimeMillis())
        val ref = firestore.collection(COLLECTION).document(familyId)
        val stored = write {
            if (existing == null) {
                ref.set(
                    mapOf(
                        "participants" to members,
                        "proposal" to SupportAgreementDocument.proposalMap(proposal),
                        "lastModifiedBy" to me
                    )
                ).await()
            } else {
                ref.update(
                    mapOf("proposal" to SupportAgreementDocument.proposalMap(proposal), "lastModifiedBy" to me)
                ).await()
            }
        }
        if (stored) announce(familyId, ActivityKind.SUPPORT_PROPOSED, terms)
        return stored
    }

    /** Accepts the co-parent's proposal in [agreement], as it stands. */
    suspend fun accept(agreement: SupportAgreement): Boolean {
        val me = signedInUid() ?: return false
        val proposal = agreement.proposal?.takeIf { it.proposedBy != me } ?: return false
        val stored = write {
            firestore.collection(COLLECTION).document(agreement.familyId).update(
                mapOf(
                    "agreement" to SupportAgreementDocument.termsMap(proposal.terms),
                    "proposal" to FieldValue.delete(),
                    "lastModifiedBy" to me
                )
            ).await()
        }
        if (stored) announce(agreement.familyId, ActivityKind.SUPPORT_ACCEPTED, proposal.terms)
        return stored
    }

    /** Declines the co-parent's proposal, or withdraws this parent's own; the agreement stays. */
    suspend fun clearProposal(agreement: SupportAgreement): Boolean {
        val me = signedInUid() ?: return false
        val proposal = agreement.proposal ?: return false
        val stored = write {
            firestore.collection(COLLECTION).document(agreement.familyId)
                .update(mapOf("proposal" to FieldValue.delete(), "lastModifiedBy" to me)).await()
        }
        if (stored) {
            val kind = if (proposal.proposedBy == me) ActivityKind.SUPPORT_WITHDRAWN else ActivityKind.SUPPORT_DECLINED
            announce(agreement.familyId, kind, proposal.terms)
        }
        return stored
    }

    private suspend fun announce(familyId: String, kind: ActivityKind, terms: SupportTerms) {
        val me = signedInUid() ?: return
        val other = FamilyKey.membersOf(familyId)?.toList()?.singleOrNull { it != me } ?: return
        activityAnnouncer.announce(
            cardFor(familyId, kind, terms),
            senderName = userRepository.getCurrentUser()?.name.orEmpty(),
            partnerUid = other
        )
    }

    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "Child support could not be stored", e)
        false
    }

    /** The collection, and the chat card each step posts. */
    companion object {
        /** The collection's name, which `firestore.rules` matches. */
        const val COLLECTION = "support_agreements"
        private const val TAG = "SupportAgreementStore"

        /** The card of [kind] about [terms]: the day of the month as `title`, the monthly amount. */
        internal fun cardFor(familyId: String, kind: ActivityKind, terms: SupportTerms) = ActivityAnnouncement(
            kind = kind,
            entityType = ActivityEntityType.SUPPORT_AGREEMENT,
            entityId = familyId,
            title = terms.dayOfMonth.toString(),
            amount = PaymentQr.amountText(terms.amount),
            currency = terms.currency
        )
    }
}
