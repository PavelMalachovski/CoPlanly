package com.coparently.app.data.requests

import android.util.Log
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.repository.CustodyModelRepository
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
 * The change request and the event are read from Room, where this phone keeps them; the custody
 * document from Firestore, which answers from its cache offline. A read that fails is
 * [RecordStatus.UNKNOWN], never "gone": a step must not close a request it could not see.
 */
@Singleton
class RequestRecords @Inject constructor(
    private val changeRequestRepository: ChangeRequestRepository,
    private val eventRepository: EventRepository,
    private val custodyDataSource: FirestoreCustodyDataSource,
    private val custodyModelRepository: CustodyModelRepository,
    private val userRepository: UserRepository
) {

    /** Where the request [clock] times stands now, and what its cards name. */
    suspend fun look(clock: RequestDeadline): RecordLook = try {
        when (familyIsLive(clock)) {
            null -> UNKNOWN_LOOK
            false -> GONE_LOOK
            true -> lookInLiveFamily(clock)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Firestore, Room or anything under them: the step reads it as "try again".
        Log.w(TAG, "Could not read a request's record", e)
        UNKNOWN_LOOK
    }

    private suspend fun lookInLiveFamily(clock: RequestDeadline): RecordLook = when (clock.kind) {
        RequestKind.CHANGE -> lookChange(clock)
        RequestKind.EVENT -> lookEvent(clock)
        RequestKind.SWAP -> custodyDataSource.getCustody(clock.familyId)?.let { lookSwap(it, clock) } ?: GONE_LOOK
        RequestKind.PROPOSAL ->
            custodyDataSource.getCustody(clock.familyId)?.let { lookProposal(it, clock) } ?: GONE_LOOK
    }

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

    private suspend fun lookChange(clock: RequestDeadline): RecordLook {
        val request = changeRequestRepository.getChangeRequestById(clock.ref)
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
