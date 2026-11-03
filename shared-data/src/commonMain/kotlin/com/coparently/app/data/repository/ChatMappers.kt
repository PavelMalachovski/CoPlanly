package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.ConversationEntity
import com.coparently.app.data.local.entity.MessageEntity
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Mapping between the chat domain models, their Room entities and their Firestore
 * documents, plus the small arithmetic the read/delivery marks need.
 *
 * Extracted from `MessageRepositoryImpl` so the repository body reads as behaviour rather
 * than as translation, and so the legacy-conversation merge can reuse exactly the same
 * conversions instead of writing a second, subtly different set.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8b). The mappers a caller still in the app reads —
 * the export, the R8 probe and the messages wire contract — are public behind [InternalSharedApi]
 * until those callers move; the rest stay `internal` (the mark mappers again since the other
 * families' signals moved here, Task 8h).
 */

/** A read or delivery mark map: uid -> epoch millis. */
private val markMap = MapSerializer(String.serializer(), Long.serializer())

/** The stored row as a domain conversation, with both mark maps decoded. */
@InternalSharedApi
fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    participants = StoredJson.decodeOrNull(StoredJson.strings, participantsJson) ?: emptyList(),
    title = title,
    lastReadAt = StoredJson.decodeOrNull(markMap, lastReadAtJson) ?: emptyMap(),
    lastDeliveredAt = StoredJson.decodeOrNull(markMap, lastDeliveredAtJson) ?: emptyMap(),
    lastMessageAtMillis = lastMessageAtMillis,
    archived = archived,
    createdAt = createdAt,
    syncedToFirestore = syncedToFirestore
)

/** The domain conversation as a storable row, with both mark maps encoded. */
@InternalSharedApi
fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    participantsJson = StoredJson.encode(StoredJson.strings, participants),
    title = title,
    lastReadAtJson = StoredJson.encode(markMap, lastReadAt),
    lastDeliveredAtJson = StoredJson.encode(markMap, lastDeliveredAt),
    lastMessageAtMillis = lastMessageAtMillis,
    archived = archived,
    createdAt = createdAt,
    syncedToFirestore = syncedToFirestore
)

/**
 * The stored row as a domain message.
 *
 * An unrecognised status falls back to [MessageSendStatus.SENT]: rows written before the
 * column existed carry none, and a row is only ever asked what *this* device's own write
 * achieved.
 */
@InternalSharedApi
fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = senderName,
    content = content,
    sentAtMillis = sentAtMillis,
    // Guarded the same way the Firestore reader below is, and for the same reason: a row whose
    // type this build does not know must degrade to a text bubble carrying `content`, not throw
    // on the path that draws the thread. Unguarded, one such row killed the whole list.
    messageType = runCatching { MessageType.valueOf(messageType) }
        .getOrDefault(MessageType.TEXT),
    attachments = StoredJson.decodeOrNull(StoredJson.strings, attachmentsJson) ?: emptyList(),
    isRead = isRead,
    replyToMessageId = replyToMessageId,
    syncedToFirestore = syncedToFirestore,
    status = runCatching { MessageSendStatus.valueOf(status ?: MessageSendStatus.SENT.name) }
        .getOrDefault(MessageSendStatus.SENT),
    activity = activityJson
        // The stored shape of an activity payload: the same sub-map the Firestore document carries.
        ?.let { json -> runCatching { StoredJson.decodeObject(json) }.getOrNull() }
        ?.let(ActivityAnnouncement::fromMap)
)

/** The domain message as a storable row. */
@InternalSharedApi
fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = senderName,
    content = content,
    sentAtMillis = sentAtMillis,
    messageType = messageType.name,
    attachmentsJson = StoredJson.encode(StoredJson.strings, attachments),
    isRead = isRead,
    replyToMessageId = replyToMessageId,
    syncedToFirestore = syncedToFirestore,
    status = status.name,
    activityJson = activity?.let { StoredJson.encodeValue(it.toMap()) }
)

/**
 * The domain message as a Firestore document.
 *
 * `timestamp` is written as a **number** — epoch millis, the same unit as the conversation's
 * read/delivered marks. It used to be a naive ISO string with no offset, which two devices in
 * different timezones could not agree on. The field keeps its name so a build that predates
 * this change still finds it, and so the `messages` composite index still applies.
 *
 * The send status is deliberately absent: it describes this device's own write, and both
 * of its promoted values (`DELIVERED`, `READ`) are derived at render time from the
 * conversation's marks rather than stored anywhere.
 */
@InternalSharedApi
fun Message.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "conversationId" to conversationId,
    "senderId" to senderId,
    "senderName" to senderName,
    "content" to content,
    "timestamp" to sentAtMillis,
    "messageType" to messageType.name,
    "attachments" to attachments,
    "isRead" to isRead,
    "replyToMessageId" to (replyToMessageId ?: "")
) + (activity?.let { mapOf("activity" to it.toMap()) } ?: emptyMap())

/**
 * A remote message document as a domain message, or `null` when it lacks a field the
 * domain requires — a malformed document is skipped rather than allowed to fail the
 * whole batch.
 */
@InternalSharedApi
fun Map<String, Any>.toMessageOrNull(): Message? {
    val id = this["id"] as? String
    val conversationId = this["conversationId"] as? String
    if (id == null || conversationId == null) return null

    val senderId = this["senderId"] as? String
    val sentAtMillis = sentAtMillisOrNull("timestamp")
    if (senderId == null || sentAtMillis == null) return null

    return Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = (this["senderName"] as? String).orEmpty(),
        content = (this["content"] as? String).orEmpty(),
        sentAtMillis = sentAtMillis,
        messageType = runCatching {
            MessageType.valueOf(this["messageType"] as? String ?: MessageType.TEXT.name)
        }.getOrDefault(MessageType.TEXT),
        attachments = stringList("attachments"),
        isRead = this["isRead"] as? Boolean ?: false,
        replyToMessageId = (this["replyToMessageId"] as? String)?.takeIf { it.isNotEmpty() },
        syncedToFirestore = true,
        // Everything read back from Firestore has, by definition, been sent.
        status = MessageSendStatus.SENT,
        activity = ActivityAnnouncement.fromMap(this["activity"] as? Map<*, *>)
    )
}

/** The document's [key] as a list of strings, empty when absent or of another shape. */
internal fun Map<String, Any>.stringList(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

/** The document's [key] as a `{uid: epochMillis}` map, dropping entries of another shape. */
internal fun Map<String, Any>.markMap(key: String): Map<String, Long> =
    (this[key] as? Map<*, *>)
        ?.mapNotNull { (uid, mark) ->
            val id = uid as? String ?: return@mapNotNull null
            val millis = (mark as? Number)?.toLong() ?: return@mapNotNull null
            id to millis
        }
        ?.toMap()
        .orEmpty()

/** The document's [key] as a Long, or `null` when absent or not a number. */
internal fun Map<String, Any>.longOrNull(key: String): Long? = (this[key] as? Number)?.toLong()

/** The document's [key] as an ISO local date-time, or `null` when absent or unparseable. */
internal fun Map<String, Any>.dateTimeOrNull(key: String): LocalDateTime? =
    (this[key] as? String)?.let {
        runCatching { IsoText.parseDateTime(it) }.getOrNull()
    }

/**
 * A message document's send time as epoch millis, in either wire format, or `null` when it
 * carries neither.
 *
 * A **number** is epoch millis and needs no interpretation — that is what this app writes now.
 * A **string** is the naive ISO local date-time the previous format used: it carries no offset,
 * so the reading device's own zone is the best available reading of it, and the same one the
 * app applied to these documents until now.
 *
 * Both branches are needed because both exist: documents written before this change, and
 * documents a co-parent's phone is still writing while it runs an older build.
 *
 * The legacy branch read with `java.time` until MON-24 phase 2 (Task 8b): [IsoText] parses the
 * text `ISO_LOCAL_DATE_TIME` accepted, and kotlinx-datetime places the wall clock in the device's
 * zone. On Android that is `atZone(ZoneId.systemDefault())` itself, because kotlinx-datetime's JVM
 * implementation delegates to it. On every platform, `ChatMappersLegacyTimeTest` (commonTest, so
 * the iOS simulator runs it too) holds the two cases a wall clock cannot name an instant for, in
 * Europe/Prague through the overload below: a time in the spring gap moves forward by the gap, and
 * one in the autumn overlap takes the earlier offset — `atZone`'s answers. `ChatMappersWireFormatTest`
 * holds the device's zone being used, at a fixed offset.
 */
internal fun Map<String, Any>.sentAtMillisOrNull(key: String): Long? =
    sentAtMillisOrNull(key, TimeZone.currentSystemDefault())

/** [sentAtMillisOrNull] with a legacy string read in [zone] rather than the device's zone. */
internal fun Map<String, Any>.sentAtMillisOrNull(key: String, zone: TimeZone): Long? = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> runCatching {
        IsoText.parseDateTime(value)
            .toInstant(zone)
            .toEpochMilliseconds()
    }.getOrNull()
    else -> null
}

/**
 * The date-time as an ISO local date-time string, matching what the documents carry:
 * [IsoText.dateTime] writes what `ISO_LOCAL_DATE_TIME` wrote here before (MON-24).
 */
internal fun LocalDateTime.toIsoString(): String = IsoText.dateTime(this)

/** The larger of two nullable Longs, or whichever one is present. */
internal fun maxOfNullable(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}
