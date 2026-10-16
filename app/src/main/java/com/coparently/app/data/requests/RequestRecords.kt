package com.coparently.app.data.requests

import android.util.Log
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreEventDataSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.sync.EventDocument
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** What a request's own record says when a step of its clock looks (phase 3a). */
enum class RecordStatus {
    /** Still waiting for the answering parent. */
    PENDING,

    /** Decided: accepted, declined or withdrawn by its author. */
    ANSWERED,

    /**
     * No longer there, replaced by a newer request, not the clock's to time — or in a family this
     * parent is no longer in, whose requests nothing acts on any more.
     */
    GONE,

    /** The record could not be read; whoever asked tries again. */
    UNKNOWN
}

/**
 * What a request's chat cards name, so the "Last chance" and "No answer" cards go where the
 * request's other cards go (see `ActivityKind.REQUEST_LAST_CHANCE`).
 *
 * @property entityType The request's own kind of card.
 * @property entityId What a tap on the card opens.
 * @property title The name the card shows; blank for a proposal, which the reader words.
 */
data class CardSubject(val entityType: ActivityEntityType, val entityId: String, val title: String)

/**
 * One look at a request's record.
 *
 * @property status Where the record stands.
 * @property card What the request's cards name; null when the record was not found.
 */
data class RecordLook(val status: RecordStatus, val card: CardSubject?)

/**
 * Reads the record a clock times, for the steps that run from it (phase 3a, spec §4.3–§4.5).
 *
 * A step never trusts the moment it was scheduled: a request answered, withdrawn or replaced since
 * finds nothing to do. **The family is the clock's** (spec §5.6): a swap or a proposal is read from
 * the custody document of the clock's `familyId`, never the family on screen, and a family this
 * parent is no longer in reads as [RecordStatus.GONE] before anything else is read (CLAUDE.md items
 * 38 and 41). A record is the clock's only when it names the clock's proposer as its author, as
 * `RequestClock.governs` asks of every reader.
 *
 * **Two ways to look.** [look] reads what this phone holds — the change request and the event from
 * Room, the custody document from Firestore, which answers from its cache offline — and is enough
 * for a reminder, where a wrong answer costs one notice. [lookOnServer] reads each record from the
 * server, past every cache, for the steps whose answer cannot be taken back: the deadline notice
 * and the end, which writes "No answer" into the clock, the chat and the record a court may read.
 * The proposer's copy of a change request is refreshed only while the inbox listens, and an event
 * only by the periodic sync, so the co-parent's answer in time may not have reached it.
 *
 * A read that fails — offline, for [lookOnServer] — is [RecordStatus.UNKNOWN], never "gone": a step
 * must not close a request it could not see.
 */
@Singleton
class RequestRecords @Inject constructor(
    private val changeRequestRepository: ChangeRequestRepository,
    private val eventRepository: EventRepository,
    private val eventDataSource: FirestoreEventDataSource,
    private val custodyDataSource: FirestoreCustodyDataSource,
    private val custodyModelRepository: CustodyModelRepository,
    private val userRepository: UserRepository
) {

    /** Where the request [clock] times stands by this phone's copy, and what its cards name. */
    suspend fun look(clock: RequestDeadline): RecordLook = lookAt(clock, fromServer = false)

    /**
     * Where the request [clock] times stands by the server's copy, and what its cards name;
     * [RecordStatus.UNKNOWN] when the server cannot be reached. Nothing read is stored.
     */
    suspend fun lookOnServer(clock: RequestDeadline): RecordLook = lookAt(clock, fromServer = true)

    private suspend fun lookAt(clock: RequestDeadline, fromServer: Boolean): RecordLook = try {
        when (familyIsLive(clock)) {
            null -> UNKNOWN_LOOK
            false -> GONE_LOOK
            true -> lookInLiveFamily(clock, fromServer)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Firestore, Room or anything under them: the step reads it as "try again".
        Log.w(TAG, "Could not read a request's record", e)
        UNKNOWN_LOOK
    }

    private suspend fun lookInLiveFamily(clock: RequestDeadline, fromServer: Boolean): RecordLook =
        when (clock.kind) {
            RequestKind.CHANGE -> lookChange(clock, fromServer)
            RequestKind.EVENT -> if (fromServer) lookEventOnServer(clock) else lookEvent(clock)
            RequestKind.SWAP -> custodyOf(clock, fromServer)?.let { lookSwap(it, clock) } ?: GONE_LOOK
            RequestKind.PROPOSAL -> custodyOf(clock, fromServer)?.let { lookProposal(it, clock) } ?: GONE_LOOK
        }

    /** The custody document of the clock's family, from the server when [fromServer] says so. */
    private suspend fun custodyOf(clock: RequestDeadline, fromServer: Boolean): SharedCustody? =
        custodyDataSource.getCustody(clock.familyId, if (fromServer) Source.SERVER else Source.DEFAULT)

    /**
     * Withdraws the proposal [clock] times, in the clock's family, when it is still the same
     * pending proposal (`CustodyModelRepository.withdrawUnanswered`).
     *
     * @return Whether it was withdrawn: false for any other kind, for a family this parent is no
     *   longer in, for a proposal answered or replaced meanwhile, and for a write that failed.
     */
    suspend fun withdrawUnansweredProposal(clock: RequestDeadline): Boolean =
        clock.kind == RequestKind.PROPOSAL &&
            familyIsLive(clock) == true &&
            custodyModelRepository.withdrawUnanswered(clock.familyId, clock.ref)

    /**
     * Whether [clock]'s family is one the signed-in parent is still in: they are one of its two
     * parents, the clock's `familyId` names exactly the two of them, and the other is still one of
     * their co-parents. Null when nobody's row can be read.
     */
    private suspend fun familyIsLive(clock: RequestDeadline): Boolean? {
        val me = userRepository.getCurrentUser() ?: return null
        val other = when (me.id) {
            clock.proposerUid -> clock.responderUid
            clock.responderUid -> clock.proposerUid
            else -> return false
        }
        val coParents = FamilyAudience.coParents(me.id, me.partnerIds, me.partnerId)
        return FamilyKey.membersOf(clock.familyId)?.toList()?.toSet() == setOf(me.id, other) && other in coParents
    }

    private suspend fun lookChange(clock: RequestDeadline, fromServer: Boolean): RecordLook {
        val stored = if (fromServer) {
            changeRequestRepository.fetchFromServer(clock.ref)
        } else {
            changeRequestRepository.getChangeRequestById(clock.ref)
        }
        val request = stored
            ?.takeIf { it.requestedBy == clock.proposerUid && it.requestedTo == clock.responderUid }
            ?: return GONE_LOOK
        val status = if (request.status == ChangeRequestStatus.PENDING) RecordStatus.PENDING else RecordStatus.ANSWERED
        return RecordLook(status, CardSubject(ActivityEntityType.CHANGE_REQUEST, request.eventId, request.eventTitle))
    }

    private suspend fun lookEvent(clock: RequestDeadline): RecordLook {
        val event = eventRepository.getEventById(clock.ref)
            ?.takeIf { it.createdByFirebaseUid == clock.proposerUid }
            ?: return GONE_LOOK
        val status = if (event.acceptance == EventAcceptance.PENDING) RecordStatus.PENDING else RecordStatus.ANSWERED
        return RecordLook(status, CardSubject(ActivityEntityType.EVENT, event.id, event.title))
    }

    /**
     * The event's document as the server holds it, read without touching Room: a tombstone, or an
     * event its proposer did not create, is gone. `EventDocument` says what an absent acceptance means.
     */
    private suspend fun lookEventOnServer(clock: RequestDeadline): RecordLook {
        val data = eventDataSource.getEventFromServer(clock.ref)
            ?.takeUnless { Tombstone.isDeleted(it) }
            ?.takeIf { it["createdByFirebaseUid"] == clock.proposerUid }
            ?: return GONE_LOOK
        val pending = EventDocument.acceptanceOf(data) == EventAcceptance.PENDING.name
        val status = if (pending) RecordStatus.PENDING else RecordStatus.ANSWERED
        return RecordLook(status, CardSubject(ActivityEntityType.EVENT, clock.ref, data["title"] as? String ?: ""))
    }

    /**
     * The offer is every day of the custody document in the clock's group, offered by its proposer.
     * None left means it was withdrawn, or each day offered again under another group.
     */
    private fun lookSwap(custody: SharedCustody, clock: RequestDeadline): RecordLook {
        val days = custody.dayOverrides.filterValues { it.groupId == clock.ref && it.requestedBy == clock.proposerUid }
        val firstDay = days.keys.minOrNull() ?: return GONE_LOOK
        val status = if (days.values.any { it.isPending }) RecordStatus.PENDING else RecordStatus.ANSWERED
        val date = clock.firstDate?.toString() ?: firstDay
        return RecordLook(status, CardSubject(ActivityEntityType.DAY_SWAP, date, date))
    }

    /**
     * Pending while the document's proposal is this one; answered when the last decision is about
     * it; gone otherwise — withdrawn, or replaced by a newer proposal.
     */
    private fun lookProposal(custody: SharedCustody, clock: RequestDeadline): RecordLook {
        val proposal = custody.proposal
        val status = when {
            proposal != null && proposal.proposedAt == clock.ref && proposal.proposedBy == clock.proposerUid ->
                RecordStatus.PENDING
            custody.lastDecision?.proposalAt == clock.ref -> RecordStatus.ANSWERED
            else -> RecordStatus.GONE
        }
        return RecordLook(status, CardSubject(ActivityEntityType.CUSTODY_PROPOSAL, clock.familyId, ""))
    }

    private companion object {
        const val TAG = "RequestRecords"
        val GONE_LOOK = RecordLook(RecordStatus.GONE, null)
        val UNKNOWN_LOOK = RecordLook(RecordStatus.UNKNOWN, null)
    }
}
