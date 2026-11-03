package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.Flow

/**
 * The Firestore operations the data sources make, and nothing else (MON-24 phase 2, Task 8a).
 *
 * The data sources in this package used to call the Android SDK; they now call this, and
 * [GitLiveFirestoreClient] answers it through GitLive's firebase-kotlin-sdk on both platforms. It
 * is the seam three things needed:
 * - **No Firestore type in a data source.** A document is a [StoredDocument] of plain values (see
 *   [FirestoreValues]), a write takes plain maps with [ServerValue]s for what the server fills in,
 *   and a failure is classified by [errorCodeOf]. The exception itself is the SDK's own, unchanged,
 *   so a caller that catches it still can.
 * - **The semantics stay what the Android SDK gave.** Every call names the operation it is:
 *   [update] is Firestore's update (a merge into an existing document, refused on a missing one),
 *   never a [set]; [get] with [ReadSource.SERVER] never answers from the cache; a snapshot says
 *   whether it holds this phone's unconfirmed write. `FirestoreSemanticsTest` holds the data
 *   sources to it.
 * - **A test needs no Firebase.** A data source's tests hand it a recording client.
 */
// One function per Firestore operation the data sources make; splitting it would split the seam.
@Suppress("TooManyFunctions")
interface FirestoreClient {

    /** One read of [path]. A document that does not exist is a [StoredDocument] with no data. */
    suspend fun get(path: DocumentPath, source: ReadSource = ReadSource.DEFAULT): StoredDocument

    /**
     * [path], live: every snapshot, starting with the cached one. The flow fails with the
     * listener's error. Metadata-only changes are not delivered, as the Android SDK's default.
     */
    fun observe(path: DocumentPath): Flow<StoredDocument>

    /** Writes [data] to [path]: the whole document, or merged as [merge] says. */
    suspend fun set(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge = SetMerge.Overwrite)

    /**
     * Updates [fields] of the existing document at [path]. A key is a dotted field path, as the
     * SDK's `update(Map)` reads it. Fails with [FirestoreErrorCode.NOT_FOUND] on a missing document.
     */
    suspend fun update(path: DocumentPath, fields: Map<String, Any?>)

    /** Updates one field, named by a dotted path (`lastReadAt.<uid>`). */
    suspend fun update(path: DocumentPath, field: String, value: Any?)

    /** Updates one field, named segment by segment, so no segment is read as a path. */
    suspend fun update(path: DocumentPath, field: FieldPath, value: Any?)

    /**
     * Adds [by] to the integer at [field] (missing counts as zero), as one update. An integer
     * increment: the stored value stays an integer.
     */
    suspend fun increment(path: DocumentPath, field: String, by: Long)

    /** Deletes the document at [path]. */
    suspend fun delete(path: DocumentPath)

    /** One read of [query]'s documents, in its order. */
    suspend fun get(query: DocumentQuery, source: ReadSource = ReadSource.DEFAULT): List<StoredDocument>

    /** [query]'s documents, live. The flow fails with the listener's error. */
    fun observe(query: DocumentQuery): Flow<List<StoredDocument>>

    /**
     * Runs [block] as one Firestore transaction: its reads come from the server, and Firestore runs
     * it again when a document it read changed before the commit, so it must be pure.
     */
    suspend fun <T> transaction(block: suspend FirestoreTransaction.() -> T): T

    /** The Firestore status of [failure], or null when it is not a Firestore failure. */
    fun errorCodeOf(failure: Throwable): FirestoreErrorCode?
}

/**
 * The values a Firestore read hands the data sources, and a write takes from them.
 *
 * - text as [String], booleans as [Boolean];
 * - an integer as [Long] and a floating number as [Double] on the way in (Firestore has one of
 *   each), and `Int`, `Long` or `Double` on the way out;
 * - an array as a [List], a map as a [Map] with text keys, null as null;
 * - a timestamp as a [FirestoreTimestamp];
 * - and, on the way out only, a [ServerValue].
 *
 * They are the values the Android SDK's own `DocumentSnapshot.getData()` returned, except the
 * timestamp, which was its `com.google.firebase.Timestamp`; so a mapper written against that map
 * reads this one the same, `as?` by `as?`. The iPhone's half turns the iOS SDK's values into the
 * same ones.
 */
object FirestoreValues

/** The reads and writes of one transaction. */
interface FirestoreTransaction {

    /** Reads [path] inside the transaction. */
    suspend fun get(path: DocumentPath): StoredDocument

    /** Replaces the document at [path] with [data] when the transaction commits. */
    fun set(path: DocumentPath, data: Map<String, Any?>)
}

/**
 * A document's place: a top-level collection and an id. Every collection this app writes is
 * top-level.
 */
data class DocumentPath(val collection: String, val id: String)

/** Where a one-shot read may be answered from. */
enum class ReadSource {
    /** The server, or the cache when the server cannot be reached. */
    DEFAULT,

    /** The server only: the read fails offline rather than answer from the cache. */
    SERVER
}

/** How a [FirestoreClient.set] treats what the document already holds. */
sealed interface SetMerge {
    /** Replaces the whole document: a key left out is removed. */
    data object Overwrite : SetMerge

    /** Merges into it, recursively: a key left out is kept. */
    data object Merge : SetMerge

    /** Replaces exactly [fields] and keeps everything else. */
    data class Fields(val fields: List<FieldPath>) : SetMerge
}

/** A field named segment by segment; a segment is never split at its dots. */
data class FieldPath(val segments: List<String>) {
    constructor(vararg segments: String) : this(segments.toList())
}

/**
 * A value the server writes in place of the one sent. Put it in a map handed to
 * [FirestoreClient.set], [FirestoreClient.update] or a transaction.
 */
sealed interface ServerValue {
    /** The server's clock at the moment it applies the write (`request.time` in the rules). */
    data object ServerTimestamp : ServerValue

    /** Removes the field. */
    data object Delete : ServerValue
}

/**
 * A Firestore timestamp, as a read hands it over and a write or a query takes it.
 *
 * The same two numbers as the SDK's own `Timestamp`, and the same conversions:
 * [toEpochMillis] is `toDate().time`, [fromEpochMillis] is `Timestamp(Date(millis))`.
 */
data class FirestoreTimestamp(val seconds: Long, val nanoseconds: Int) : Comparable<FirestoreTimestamp> {

    /** The instant as epoch milliseconds, the sub-millisecond part dropped. */
    fun toEpochMillis(): Long = seconds * MILLIS_PER_SECOND + nanoseconds / NANOS_PER_MILLI

    override fun compareTo(other: FirestoreTimestamp): Int =
        compareValuesBy(this, other, FirestoreTimestamp::seconds, FirestoreTimestamp::nanoseconds)

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000

        /** The timestamp of [millis] epoch milliseconds. */
        fun fromEpochMillis(millis: Long): FirestoreTimestamp = FirestoreTimestamp(
            seconds = millis.floorDiv(MILLIS_PER_SECOND),
            nanoseconds = (millis.mod(MILLIS_PER_SECOND) * NANOS_PER_MILLI).toInt()
        )
    }
}

/**
 * One document as a read returns it.
 *
 * @property id The document id.
 * @property data Its fields as plain values ([FirestoreValues]), or null when it does not exist. A
 *   server timestamp this phone wrote and the server has not confirmed reads as null, as the SDK's
 *   default does.
 * @property hasPendingWrites Whether it holds a write of this phone's the server has not confirmed.
 * @property isFromCache Whether it was answered from the local cache.
 * @property estimated A top-level field with a pending server timestamp estimated from this phone's
 *   clock (the SDK's `ServerTimestampBehavior.ESTIMATE`); otherwise the field as in [data].
 */
class StoredDocument(
    val id: String,
    val data: Map<String, Any?>?,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false,
    private val estimated: (String) -> Any? = { field -> data?.get(field) }
) {
    /** Whether the document exists. */
    val exists: Boolean get() = data != null

    /** [field] with a pending server timestamp estimated; see the class KDoc. */
    fun estimated(field: String): Any? = estimated.invoke(field)

    /**
     * The timestamp in [field], or null when the field is absent or null. Throws when it holds
     * something else, as the SDK's `getTimestamp` does.
     */
    fun timestamp(field: String): FirestoreTimestamp? = when (val value = data?.get(field)) {
        null -> null
        is FirestoreTimestamp -> value
        else -> throw IllegalStateException("Field '$field' is not a Timestamp")
    }

    /**
     * The text in [field], or null when the field is absent or null. Throws when it holds
     * something else, as the SDK's `getString` does, with the SDK's own message (a caller that
     * wraps a failure's message into what it reports gets the same words).
     */
    // The SDK's own exception type, so a caller's `catch` and its message stay what they were.
    @Suppress("TooGenericExceptionThrown")
    fun string(field: String): String? = when (val value = data?.get(field)) {
        null -> null
        is String -> value
        // The SDK names the JVM class; this is that text, not a JVM call.
        else -> throw RuntimeException("Field '$field' is not a java.lang.String")
    }
}

/** A Firestore status code, by the names both SDKs use. */
enum class FirestoreErrorCode {
    OK,
    CANCELLED,
    UNKNOWN,
    INVALID_ARGUMENT,
    DEADLINE_EXCEEDED,
    NOT_FOUND,
    ALREADY_EXISTS,
    PERMISSION_DENIED,
    RESOURCE_EXHAUSTED,
    FAILED_PRECONDITION,
    ABORTED,
    OUT_OF_RANGE,
    UNIMPLEMENTED,
    INTERNAL,
    UNAVAILABLE,
    DATA_LOSS,
    UNAUTHENTICATED;

    companion object {
        /** The code named [name], or null for a name this build does not know. */
        fun named(name: String): FirestoreErrorCode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * This map typed as the data sources' older signatures declare it. Unchecked, as the SDK's own
 * `Map<String, Object>` was: a Firestore null stays null inside it.
 */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.asFieldMap(): Map<String, Any> = this as Map<String, Any>
