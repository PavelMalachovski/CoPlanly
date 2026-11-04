@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.remote.firebase

import cocoapods.FirebaseCore.FIRTimestamp
import cocoapods.FirebaseFirestoreInternal.FIRServerTimestampBehavior
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.ios
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNull

internal actual fun DocumentSnapshot.toStoredDocument(): StoredDocument {
    val snapshot = ios
    return StoredDocument(
        id = snapshot.documentID,
        data = snapshot.dataWithServerTimestampBehavior(FIRServerTimestampBehavior.FIRServerTimestampBehaviorNone)
            ?.let(::plainMap),
        hasPendingWrites = snapshot.metadata.pendingWrites,
        isFromCache = snapshot.metadata.fromCache,
        estimated = { field ->
            plainValue(snapshot.valueForField(field, FIRServerTimestampBehavior.FIRServerTimestampBehaviorEstimate))
        }
    )
}

/** GitLive's iOS `increment(Int)` is the SDK's integer increment, which is what this asks for. */
internal actual suspend fun DocumentReference.incrementInteger(field: String, by: Long) {
    require(by in Int.MIN_VALUE..Int.MAX_VALUE) { "An increment of $by does not fit" }
    val increment = FieldValue.increment(by.toInt())
    updateFields { field to increment }
}

/**
 * The iOS SDK's values as [FirestoreValues]: `NSNull` as null, `FIRTimestamp` as a
 * [FirestoreTimestamp], maps and arrays element by element. Kotlin/Native already hands an
 * `NSNumber` over as a Kotlin number or boolean and an `NSString` as text, as GitLive's own
 * decoder relies on.
 */
private fun plainValue(value: Any?): Any? = when (value) {
    null, is NSNull -> null
    is FIRTimestamp -> FirestoreTimestamp(value.seconds, value.nanoseconds)
    is Map<*, *> -> plainMap(value)
    is List<*> -> value.map(::plainValue)
    else -> value
}

private fun plainMap(map: Map<*, *>): Map<String, Any?> =
    map.entries.associateTo(LinkedHashMap()) { (key, value) -> key.toString() to plainValue(value) }
