package com.coparently.app.data.remote.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import dev.gitlive.firebase.firestore.invoke
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp as AndroidTimestamp
import com.google.firebase.firestore.DocumentSnapshot as AndroidDocumentSnapshot
import com.google.firebase.firestore.FieldValue as AndroidFieldValue
import com.google.firebase.firestore.FirebaseFirestore as AndroidFirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestore as GitLiveFirebaseFirestore

/**
 * The [FirestoreClient] over [firestore], the app's own Android instance: GitLive wraps it rather
 * than looking one up, so its settings, an emulator's host (the e2e parents' named apps) and an
 * instrumented test's replacement all reach the data sources.
 */
fun firestoreClientOf(firestore: AndroidFirebaseFirestore): FirestoreClient =
    GitLiveFirestoreClient(GitLiveFirebaseFirestore.invoke(firestore))

internal actual fun DocumentSnapshot.toStoredDocument(): StoredDocument {
    val snapshot = android
    return StoredDocument(
        id = snapshot.id,
        data = snapshot.data?.let(::plainMap),
        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
        isFromCache = snapshot.metadata.isFromCache,
        estimated = { field ->
            plainValue(
                snapshot.get(field, AndroidDocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    )
}

internal actual suspend fun DocumentReference.incrementInteger(field: String, by: Long) {
    android.update(field, AndroidFieldValue.increment(by)).await()
}

/** The SDK's map with its timestamps as [FirestoreTimestamp]s; everything else as it was. */
private fun plainMap(map: Map<*, *>): Map<String, Any?> =
    map.entries.associateTo(LinkedHashMap()) { (key, value) -> key.toString() to plainValue(value) }

private fun plainValue(value: Any?): Any? = when (value) {
    is AndroidTimestamp -> FirestoreTimestamp(value.seconds, value.nanoseconds)
    is Map<*, *> -> plainMap(value)
    is List<*> -> value.map(::plainValue)
    else -> value
}
