package com.coparently.app.domain.repository

import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for event change requests. Room is the offline-first source of truth;
 * Firestore syncs requests between the two parents.
 */
interface ChangeRequestRepository {

    /** All change requests known locally, newest first. */
    fun getAllChangeRequests(): Flow<List<ChangeRequest>>

    /** Requests attached to a specific event, newest first. */
    fun getChangeRequestsForEvent(eventId: String): Flow<List<ChangeRequest>>

    suspend fun getChangeRequestById(id: String): ChangeRequest?

    /**
     * Creates the request locally, syncs it to Firestore and notifies the other parent.
     *
     * @param answerBy The deadline the requesting parent chose for the answer (phase 3a), or null for
     *   none, which is the request exactly as it was before deadlines: no clock, and the push says
     *   nothing of one. A pick is turned into a time when the request is sent, against the request's
     *   own cap ([com.coparently.app.domain.changerequests.RequestCap.forChange]). Once the request
     *   is on the server its clock is started, and the push that tells the co-parent says when the
     *   answer is due. A clock that cannot be written leaves the request as one without a clock, and
     *   a request that waits in the outbox and is sent later (see [flushOutbox]) goes without one.
     */
    suspend fun createChangeRequest(request: ChangeRequest, answerBy: AnswerByPick? = null)

    /**
     * Transitions [requestId] to [status] (accept/decline/cancel), stamps `respondedAt`,
     * syncs and notifies the counterparty. The caller is responsible for applying the
     * proposed time to the event when accepting.
     */
    suspend fun updateStatus(requestId: String, status: ChangeRequestStatus)

    /**
     * Mirrors the remote side into Room and **never returns** — it collects a snapshot
     * listener for as long as its caller's scope lives.
     *
     * Named for the shape rather than for the subject (CQ-10). It was `syncWithFirestore()`,
     * the same name the one-shot repositories use, which made it look safe to await from
     * `SyncService.performFullSync()`. It is not: that call would hang, `SyncWorker` would be
     * killed at WorkManager's ten-minute ceiling, and sync would stop entirely with no
     * exception and no log. Call it from a scope that is allowed to run forever.
     *
     * Contrast [flushOutbox], which is the one-pass upload half and does return — the two
     * together are why the shape had to be in the name.
     */
    suspend fun observeRemote()

    /**
     * Re-uploads every change request this device wrote locally but never got onto the server.
     *
     * One pass, no loop. Safe to call at any time and cheap when nothing is queued.
     */
    suspend fun flushOutbox()
}
