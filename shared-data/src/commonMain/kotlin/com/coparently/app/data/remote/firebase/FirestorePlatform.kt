package com.coparently.app.data.remote.firebase

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot

/**
 * This snapshot as a [StoredDocument]: the SDK's own data map, turned into [FirestoreValues] by
 * the platform half — GitLive's common API only decodes into `@Serializable` types.
 */
internal expect fun DocumentSnapshot.toStoredDocument(): StoredDocument

/**
 * Adds [by] to the integer [field] in one update. A platform half because GitLive's Android
 * `FieldValue.increment(Int)` asks the SDK for a double increment, which would store the counter
 * as a double; Android calls the SDK's `increment(long)`.
 */
internal expect suspend fun DocumentReference.incrementInteger(field: String, by: Long)
