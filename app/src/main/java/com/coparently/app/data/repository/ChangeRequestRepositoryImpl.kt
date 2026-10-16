package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangeRequestRepositoryImpl @Inject constructor(
    private val changeRequestDao: ChangeRequestDao,
    private val firestoreDataSource: FirestoreChangeRequestDataSource,
    private val firebaseAuthService: FirebaseAuthService,
    private val fcmService: FcmService,
    private val requestDeadlines: RequestDeadlineRepository
) : ChangeRequestRepository {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override fun getAllChangeRequests(): Flow<List<ChangeRequest>> {
        return changeRequestDao.getAllChangeRequests().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getChangeRequestsForEvent(eventId: String): Flow<List<ChangeRequest>> {
        return changeRequestDao.getChangeRequestsForEvent(eventId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChangeRequestById(id: String): ChangeRequest? {
        return changeRequestDao.getChangeRequestById(id)?.toDomain()
    }

    override suspend fun fetchFromServer(id: String): ChangeRequest? =
        firestoreDataSource.getFromServer(id)?.toDomain()

    override suspend fun createChangeRequest(request: ChangeRequest, answerBy: AnswerByPick?) {
        // Unlike an event or an expense, a change request already names both adults, so its
        // family is a fact about the record rather than something to look up: no `UserDao`
        // read, and no window in which pairing state and the stamp disagree.
        val owned = request.copy(
            familyId = request.familyId
                ?: FamilyKey.orNull(request.requestedBy, request.requestedTo)
        )
        changeRequestDao.insertChangeRequest(owned.toEntity())
        if (firebaseAuthService.getCurrentUser() == null) return
        publish(owned, targetUserId = owned.requestedTo, action = "created", answerBy = answerBy)
    }

    override suspend fun updateStatus(requestId: String, status: ChangeRequestStatus) {
        val request = changeRequestDao.getChangeRequestById(requestId)?.toDomain() ?: return
        // Not sent until [publish] says so. The row being answered is almost always the mirror's
        // copy of the co-parent's request, which is synced; copied as it was, an answer given
        // offline read as sent at once, and the History never said "Queued" (CQ-20).
        val updated = request.copy(status = status, respondedAt = LocalDateTime.now(), syncedToFirestore = false)
        changeRequestDao.insertChangeRequest(updated.toEntity())

        if (firebaseAuthService.getCurrentUser() == null) return
        // Cancellation notifies the addressee; accept/decline notify the requester.
        val target = if (status == ChangeRequestStatus.CANCELLED) {
            updated.requestedTo
        } else {
            updated.requestedBy
        }
        // An answer is not a request: nobody waits on it, so it starts no clock.
        publish(updated, targetUserId = target, action = status.name.lowercase(), answerBy = null)
    }

    /**
     * Uploads one request and announces it, leaving it queued rather than throwing on failure.
     *
     * Both callers run inside a bare `viewModelScope.launch`, so an uncaught `PERMISSION_DENIED`
     * here did not merely fail the sync — it took the process down. The same guard
     * `ExpenseRepositoryImpl` already carries, for the same reason.
     *
     * A failure leaves `syncedToFirestore = false`, which is what [flushOutbox] selects on.
     *
     * A new request given a deadline ([answerBy]) starts its clock between writing and announcing: once
     * the request is on the server and filed as sent, and before the co-parent is told, so that the
     * push can say when the answer is due (phase 3a, spec §5.1, §5.4). The clock never fails the
     * request: one that is not written leaves it as a request without a clock, and the push then
     * says nothing of a deadline (ruling 8).
     *
     * **From the server's acknowledgement on, nothing here can be cancelled**: filing the request as
     * sent, starting its clock and queueing the push. The clock waits for the server for up to ten
     * seconds (ruling 14) and the form's up arrow stays enabled while it does, so a caller cancelled in
     * that wait would leave the request on the server and filed as sent — which is what keeps
     * [flushOutbox] from ever sending it again — with its push never queued. Before the
     * acknowledgement the caller is cancelled as ever: the request is still unsent in Room and the
     * outbox sends it later.
     */
    private suspend fun publish(
        request: ChangeRequest,
        targetUserId: String,
        action: String,
        answerBy: AnswerByPick?
    ) {
        try {
            firestoreDataSource.setChangeRequest(request.id, request.toFirestoreMap())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Change request ${request.id} stays queued", e)
            return
        }
        withContext(NonCancellable) {
            changeRequestDao.insertChangeRequest(request.copy(syncedToFirestore = true).toEntity())
            val answerByMillis = startClock(request, answerBy)
            notifyCounterparty(request, targetUserId = targetUserId, action = action, answerByMillis = answerByMillis)
        }
    }

    /**
     * Writes the clock of a new request (phase 3a, spec §5.1), if it has one.
     *
     * @return The deadline the clock was written with, or null when there is none: none was asked for,
     *   it is too soon for one, or the write was refused. The push then says nothing of a deadline,
     *   and the request works as one without a clock (ruling 8).
     */
    private suspend fun startClock(request: ChangeRequest, answerBy: AnswerByPick?): Long? {
        val start = changeClock(request, answerBy) ?: return null
        return try {
            requestDeadlines.start(start)?.answerByMillis
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // `start` reports a refusal as null and is not meant to throw. If it does, the request is
            // already on the server and its co-parent still has to be told, so the clock is given up.
            Log.w(TAG, "Change request ${request.id} goes out without its clock", e)
            null
        }
    }

    override suspend fun flushOutbox() {
        val userId = firebaseAuthService.getCurrentUser()?.uid ?: return
        for (entity in changeRequestDao.getUnsyncedChangeRequests()) {
            val request = entity.toDomain()
            // Only this device's own writes. A request mirrored down from the co-parent that
            // failed to parse its synced flag is not ours to re-publish.
            if (request.requestedBy != userId && request.requestedTo != userId) continue
            val action = if (request.status == ChangeRequestStatus.PENDING) {
                "created"
            } else {
                request.status.name.lowercase()
            }
            val target = when {
                request.status == ChangeRequestStatus.PENDING -> request.requestedTo
                request.status == ChangeRequestStatus.CANCELLED -> request.requestedTo
                else -> request.requestedBy
            }
            // Sent without a deadline: the pick was a choice in a form and Room does not keep it, so a
            // request that waited in the outbox goes out as one without a clock (ruling 8).
            publish(request, targetUserId = target, action = action, answerBy = null)
        }
    }

    override suspend fun observeRemote() {
        val userId = firebaseAuthService.getCurrentUser()?.uid ?: return
        firestoreDataSource.observeChangeRequestsForUser(userId)
            // Offline-first: a Firestore failure (missing index, denied read, no network)
            // must not crash the app — Room stays the source of truth. Swallow and log.
            .catch { e ->
                Log.w(TAG, "Change request sync failed", e)
            }
            .collect { documents ->
                documents.forEach { data ->
                    runCatching { data.toDomain() }.getOrNull()?.let { remote ->
                        // Firestore's local echo of this phone's own write arrives before the
                        // server has it: still queued, and [publish] marks it sent once it lands.
                        val sent = data[FirestoreChangeRequestDataSource.HAS_PENDING_WRITES] != true
                        changeRequestDao.insertChangeRequest(
                            remote.copy(syncedToFirestore = sent).toEntity()
                        )
                    }
                }
            }
    }

    /**
     * Queues the push that tells [targetUserId] about [request].
     *
     * @param answerByMillis The deadline of the request's clock, when one was written (phase 3a): the
     *   first push of a request that waits for an answer says when it is due. An older build ignores
     *   the key.
     */
    private suspend fun notifyCounterparty(
        request: ChangeRequest,
        targetUserId: String,
        action: String,
        answerByMillis: Long?
    ) {
        if (targetUserId.isEmpty() || targetUserId == firebaseAuthService.getCurrentUser()?.uid) return
        // Null for an action nothing announces — see `FcmService.createEventNotificationPayload`.
        val type = PushPayload.changeRequestType(action) ?: return
        // Empty rather than "Your co-parent": the receiving device holds a translated fallback
        // for a missing name, and an English one written here would win over it.
        val performedBy = firebaseAuthService.getCurrentUser()?.displayName.orEmpty()
        val payload = buildMap {
            put(PushPayload.TYPE, type)
            put(PushPayload.CHANGE_REQUEST_ID, request.id)
            put(PushPayload.EVENT_ID, request.eventId)
            put(PushPayload.SUBJECT, request.eventTitle)
            put(PushPayload.ACTOR, performedBy)
            answerByMillis?.let { put(PushPayload.ANSWER_BY, it.toString()) }
        }
        // Notification delivery is best-effort; the request itself is already synced.
        fcmService.queueNotificationForUser(targetUserId, payload)
    }

    private fun ChangeRequest.toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "eventId" to eventId,
        "eventTitle" to eventTitle,
        "requestedBy" to requestedBy,
        "requestedTo" to requestedTo,
        "currentStartDateTime" to currentStartDateTime.format(dateTimeFormatter),
        "currentEndDateTime" to (currentEndDateTime?.format(dateTimeFormatter) ?: ""),
        "proposedStartDateTime" to proposedStartDateTime.format(dateTimeFormatter),
        "proposedEndDateTime" to (proposedEndDateTime?.format(dateTimeFormatter) ?: ""),
        "note" to (note ?: ""),
        "status" to status.name,
        "createdAt" to createdAt.format(dateTimeFormatter),
        "respondedAt" to (respondedAt?.format(dateTimeFormatter) ?: ""),
        "familyId" to (familyId ?: "")
    )

    private fun Map<String, Any>.toDomain(): ChangeRequest = ChangeRequest(
        id = this["id"] as String,
        eventId = this["eventId"] as String,
        eventTitle = this["eventTitle"] as? String ?: "",
        requestedBy = this["requestedBy"] as String,
        requestedTo = this["requestedTo"] as String,
        currentStartDateTime = LocalDateTime.parse(this["currentStartDateTime"] as String, dateTimeFormatter),
        currentEndDateTime = (this["currentEndDateTime"] as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { LocalDateTime.parse(it, dateTimeFormatter) },
        proposedStartDateTime = LocalDateTime.parse(this["proposedStartDateTime"] as String, dateTimeFormatter),
        proposedEndDateTime = (this["proposedEndDateTime"] as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { LocalDateTime.parse(it, dateTimeFormatter) },
        note = (this["note"] as? String)?.takeIf { it.isNotEmpty() },
        status = ChangeRequestStatus.valueOf(this["status"] as String),
        createdAt = LocalDateTime.parse(this["createdAt"] as String, dateTimeFormatter),
        respondedAt = (this["respondedAt"] as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { LocalDateTime.parse(it, dateTimeFormatter) },
        // Derived when absent, which is what a document written before the field existed looks
        // like. Safe here and nowhere else: the pair is recorded in the document itself, so
        // this reconstructs the stamp rather than guessing it from current pairing state.
        familyId = (this["familyId"] as? String)?.takeIf { it.isNotEmpty() }
            ?: FamilyKey.orNull(this["requestedBy"] as? String, this["requestedTo"] as? String)
    )

    private fun ChangeRequestEntity.toDomain(): ChangeRequest = ChangeRequest(
        id = id,
        eventId = eventId,
        eventTitle = eventTitle,
        requestedBy = requestedBy,
        requestedTo = requestedTo,
        currentStartDateTime = currentStartDateTime,
        currentEndDateTime = currentEndDateTime,
        proposedStartDateTime = proposedStartDateTime,
        proposedEndDateTime = proposedEndDateTime,
        note = note,
        status = ChangeRequestStatus.valueOf(status),
        createdAt = createdAt,
        respondedAt = respondedAt,
        syncedToFirestore = syncedToFirestore,
        familyId = familyId
    )

    private fun ChangeRequest.toEntity(): ChangeRequestEntity = ChangeRequestEntity(
        id = id,
        eventId = eventId,
        eventTitle = eventTitle,
        requestedBy = requestedBy,
        requestedTo = requestedTo,
        currentStartDateTime = currentStartDateTime,
        currentEndDateTime = currentEndDateTime,
        proposedStartDateTime = proposedStartDateTime,
        proposedEndDateTime = proposedEndDateTime,
        note = note,
        status = status.name,
        createdAt = createdAt,
        respondedAt = respondedAt,
        syncedToFirestore = syncedToFirestore,
        familyId = familyId
    )

    private companion object {
        const val TAG = "ChangeRequestRepo"
    }
}

/**
 * The clock a new change request starts (phase 3a, spec §4.2, §5.1), or null when it starts none: no
 * [answerBy] was chosen, the request names no family to put a clock in, or what it would change is
 * too near for a deadline.
 *
 * The cap is the earlier of the event's current and proposed start that is still ahead of now
 * ([RequestCap.forChange]), so a change to a recurring series — whose stored start is its long-past
 * first occurrence — is capped at the proposed one, and a change that moves nothing ahead has no cap.
 * The deadline and the cap are worked out here, when the request is sent, in the zone of the phone
 * that sends it, which is the zone its form showed them in. The clock belongs to the request's own
 * family ([ChangeRequest.familyId]), and the parent who must answer is the one it was addressed to.
 */
private fun changeClock(request: ChangeRequest, answerBy: AnswerByPick?): RequestStart? {
    val familyId = request.familyId
    if (answerBy == null || familyId == null) return null
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val cap = RequestCap.forChange(request.currentStartDateTime, request.proposedStartDateTime, now, zone)
    val deadline = AnswerBy.resolve(answerBy, now, cap, zone) ?: return null
    return RequestStart(
        kind = RequestKind.CHANGE,
        ref = request.id,
        familyId = familyId,
        responderUid = request.requestedTo,
        answerByMillis = deadline,
        capMillis = cap
    )
}
