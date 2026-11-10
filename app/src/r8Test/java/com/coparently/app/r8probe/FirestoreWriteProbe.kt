@file:OptIn(InternalSharedApi::class)

package com.coparently.app.r8probe

import com.coparently.app.data.remote.firebase.FirestoreTimestamp
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.domain.InternalSharedApi
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import dev.gitlive.firebase.internal.android
import dev.gitlive.firebase.internal.encodeAsObject

/**
 * A Firestore write's values through GitLive's encoder in the minified build (MON-24 phase 2,
 * Task 8a): the map `GitLiveFirestoreClient` hands `set()` and `update()`, encoded as the data
 * sources' writes are, and the map the Android SDK then receives, value by value.
 *
 * The encoder looks a serializer up for every value at runtime — the builtins for text and
 * numbers, a companion's `serializer()` for GitLive's `FieldValue` and `Timestamp` — which is the
 * reflection a shrinker could cut without the release build failing to compile. No Firestore is
 * reached: the SDK's own map is what is checked.
 */
@Suppress("MagicNumber") // The fixed values of the one fixture.
internal object FirestoreWriteProbe {

    /** The label the report uses; `tools/check-r8-probe.js` requires a passing case for it. */
    const val WRITE = "Firestore write values through GitLiveFirestoreClient and the GitLive encoder"

    /** Every case; never throws. */
    fun run(): List<ProbeCase> = guarded(listOf(WRITE), "GitLiveFirestoreClient.plainToGitLive -> encodeAsObject") {
        val plain = mapOf(
            "title" to "Dentist",
            "patternDays" to 14,
            "deviceTimeMillis" to 1_778_000_000_000L,
            "share" to 0.5,
            "isPrivate" to false,
            "absent" to null,
            "sharedWith" to listOf("uidA", "uidB"),
            "momDayIndices" to listOf(0, 1, 2),
            "nested" to mapOf("lastReadAt" to mapOf("uidA" to 5L)),
            "recordedAt" to ServerValue.ServerTimestamp,
            "allergies" to ServerValue.Delete,
            "cursor" to FirestoreTimestamp(1_778_000_000L, 5)
        )
        val native = encodeAsObject(GitLiveFirestoreClient.plainToGitLive(plain)).android
        val described = native.entries.sortedBy { it.key }
            .joinToString(",") { (key, value) -> "$key=${describe(value)}" }
        value(WRITE, plain.toString(), EXPECTED, described)
    }

    /** A value as the SDK will take it: its Java class family and content. */
    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is String -> "String($value)"
        is Int -> "Int($value)"
        is Long -> "Long($value)"
        is Double -> "Double($value)"
        is Boolean -> "Boolean($value)"
        is List<*> -> value.joinToString(";", "List(", ")") { describe(it) }
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }
            .joinToString(";", "Map(", ")") { (key, entry) -> "$key:${describe(entry)}" }
        is FieldValue -> describeSentinel(value)
        is Timestamp -> "Timestamp(${value.seconds},${value.nanoseconds})"
        else -> "other"
    }

    /** The SDK's sentinel, by which one it is. */
    private fun describeSentinel(value: FieldValue): String = when (value) {
        FieldValue.serverTimestamp() -> "FieldValue(serverTimestamp)"
        FieldValue.delete() -> "FieldValue(delete)"
        else -> "FieldValue(other)"
    }

    private const val EXPECTED =
        "absent=null,allergies=FieldValue(delete),cursor=Timestamp(1778000000,5)," +
            "deviceTimeMillis=Long(1778000000000),isPrivate=Boolean(false)," +
            "momDayIndices=List(Int(0);Int(1);Int(2)),nested=Map(lastReadAt:Map(uidA:Long(5)))," +
            "patternDays=Int(14),recordedAt=FieldValue(serverTimestamp),share=Double(0.5)," +
            "sharedWith=List(String(uidA);String(uidB)),title=String(Dentist)"
}
