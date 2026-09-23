package com.coparently.app.data.export

import android.util.Log
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.repository.toDomain
import com.coparently.app.data.repository.toMessageOrNull
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.export.CommunicationRecordBuilder
import com.coparently.app.domain.export.CurrentEventInput
import com.coparently.app.domain.export.EventFacts
import com.coparently.app.domain.export.EventRevisionInput
import com.coparently.app.domain.export.MessageInput
import com.coparently.app.domain.export.RecordSources
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers what a communication record is built from (MON-3).
 *
 * The server first, this phone second, and the record says which it got. Revisions and messages
 * are read from Firestore because that is where neither parent can alter them; this phone's own
 * copies fill in what has not been uploaded yet, marked as such. When the server cannot be
 * reached the record is still produced — a parent in a lawyer's office with no signal needs it —
 * but [RecordSources.serverReached] is false and both formats print that on their face.
 *
 * What is **not** read here is as deliberate as what is: no child or pet record, so no medical
 * profile can reach a document meant for a court (`docs/DESIGN-court-record.md` §4); and no
 * private event, which never left this phone and must not leave it in a file either.
 */
@Singleton
class CommunicationRecordSource @Inject constructor(
    private val versions: FirestoreEventVersionDataSource,
    private val recorder: EventVersionRecorder,
    private val eventRepository: EventRepository,
    private val messages: FirestoreMessageDataSource,
    private val messageDao: MessageDao,
    private val expenseRepository: ExpenseRepository
) {

    /**
     * Everything the record for [from]…[to] may draw on, for the signed-in [myUid].
     *
     * @param conversationId The thread with the co-parent on screen, or null when unpaired.
     */
    suspend fun gather(
        myUid: String,
        conversationId: String?,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId
    ): RecordSources {
        val remoteRevisions = fromServer("revisions") { versions.readableBy(myUid) }
        val pending = recorder.undelivered(myUid).mapNotNull { row ->
            EventVersionKind.fromWire(row.kind)?.let { kind ->
                EventRevisionInput(
                    versionId = row.id,
                    eventId = row.eventId,
                    kind = kind,
                    editorUid = row.editorUid,
                    deviceTimeMillis = row.deviceTimeMillis,
                    recordedAtMillis = null,
                    familyId = row.familyId.orEmpty(),
                    facts = CommunicationRecordBuilder.factsOf(
                        EventVersionDocument.decodeSnapshot(row.snapshotJson)
                    )
                )
            }
        }
        val threadMessages = conversationId?.let { thread(it, from, to, zone) }
        return RecordSources(
            revisions = remoteRevisions.orEmpty().map { it.toInput() } + pending,
            currentEvents = eventRepository.getAllEvents().first()
                .filterNot { it.isPrivate }
                .map { it.toCurrentInput() },
            messages = threadMessages?.first.orEmpty(),
            expenses = expenseRepository.getExpensesForPeriod(from, to).first(),
            serverReached = remoteRevisions != null && threadMessages?.second != false
        )
    }

    /**
     * One thread's messages in the range: the server's, plus this phone's copies the server did
     * not return. The second value is whether the server answered.
     */
    private suspend fun thread(
        conversationId: String,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId
    ): Pair<List<MessageInput>, Boolean> {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val remote = fromServer("messages") { messages.fetchBetween(conversationId, start, end) }
            ?.mapNotNull { it.toMessageOrNull() }
            ?.map {
                MessageInput(
                    it.id,
                    it.senderId,
                    it.sentAtMillis,
                    it.content,
                    delivered = true,
                    attachments = ChatAttachmentCodec.attachmentsOf(it.attachments)
                )
            }
        val remoteIds = remote.orEmpty().map { it.messageId }.toSet()
        val local = messageDao.getMessagesOnce(conversationId)
            .map { it.toDomain() }
            .filter { it.id !in remoteIds }
            .map {
                MessageInput(
                    messageId = it.id,
                    senderUid = it.senderId,
                    sentAtMillis = it.sentAtMillis,
                    text = it.content,
                    delivered = it.syncedToFirestore && it.status != MessageSendStatus.ERROR,
                    attachments = ChatAttachmentCodec.attachmentsOf(it.attachments)
                )
            }
        return (remote.orEmpty() + local) to (remote != null)
    }

    /** [read] from the server, or null when it could not be reached or refused. */
    private suspend fun <T> fromServer(what: String, read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "Export could not read $what from the server; the record is marked incomplete", e)
        null
    }

    private fun EventVersionDocument.Parsed.toInput() = EventRevisionInput(
        versionId = versionId,
        eventId = eventId,
        kind = kind,
        editorUid = editorUid,
        deviceTimeMillis = deviceTimeMillis,
        recordedAtMillis = recordedAtMillis,
        familyId = familyId,
        facts = CommunicationRecordBuilder.factsOf(snapshot)
    )

    private fun Event.toCurrentInput() = CurrentEventInput(
        eventId = id,
        familyId = familyId.orEmpty(),
        isPrivate = isPrivate,
        creatorUid = createdByFirebaseUid,
        facts = EventFacts(
            title = title,
            description = description.orEmpty(),
            start = startDateTime,
            end = endDateTime,
            parentSlot = parentOwner,
            recurrence = if (isRecurring) recurrencePattern.orEmpty() else "",
            recurrenceEnd = recurrenceEndDate
        )
    )

    private companion object {
        const val TAG = "CommunicationRecord"
    }
}
