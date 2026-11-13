@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.newMonitor
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Everything the phone keeps about one school connection (MON-8).
 *
 * @property connection What the screens show.
 * @property ownerUid The Firebase account it belongs to. The store refuses it to any other.
 * @property student The child as the school describes them, to match its events.
 * @property tokens The latest token pair. **Never a password.**
 * @property ledger What the connection has imported, by event id; see `SchoolImportRecord`.
 * @property edupage The password and session an EduPage connection signs in with, or null for a
 *   Bakaláři one. A record with this set is stored under its own key prefix; [tokens] is blank then.
 */
data class StoredSchoolConnection(
    val connection: SchoolConnection,
    val ownerUid: String,
    val student: SchoolStudent,
    val tokens: BakalariTokens,
    val ledger: Map<String, SchoolImportRecord> = emptyMap(),
    val edupage: EdupageSecrets? = null
) {
    /** The connection's id. */
    val id: String get() = connection.id
}

/**
 * What an EduPage sign-in needs and the school never gives back as a token: the parent's password,
 * kept (sealed) because EduPage's session cannot be refreshed without it, the session's cookies, the
 * child's id there and the device token the app registered, if any.
 *
 * [toString] prints the student id alone, so a log line or a crash report never carries the
 * password or a cookie.
 */
data class EdupageSecrets(
    val password: String,
    val cookies: Map<String, String>,
    val studentId: String,
    val deviceToken: String? = null
) {
    override fun toString() = "EdupageSecrets(studentId=$studentId)"
}

/**
 * The school connections, sealed in [SecurePreferences] (SEC-5) under
 * [PreferenceKeys.SCHOOL_CONNECTION_PREFIX] — one key per Bakaláři connection — and
 * [PreferenceKeys.EDUPAGE_CONNECTION_PREFIX] for EduPage's, written by [EdupageConnectionCodec].
 *
 * **Scoped to the signed-in account, in the style of `TodayWidgetNames`**: every record names its
 * owner's uid, and every read takes the uid and refuses a record written for anybody else. The
 * three places that forget the connections — sign-out, an account switch and account deletion —
 * call [clearAll]; `EncryptedPreferences.clear()` deliberately keeps them, because a Google
 * Calendar disconnect reaches it too.
 *
 * Every method runs under one reentrant monitor (`synchronized` on Android, an `NSRecursiveLock` on
 * the iPhone; before MON-24, `@Synchronized` on each method), so a read-modify-write in [update]
 * cannot interleave with another, and every write reaches the sealed file before the method returns
 * (`InMemorySharedPreferences` persists each commit under its lock) — which is what lets
 * `BakalariAuthorizer` write a rotated refresh token down **before** it uses it.
 *
 * The value is JSON written and read by hand ([SchoolConnectionCodec]), never a serializer over a
 * data class, so the stored text depends on no class's shape.
 */
class SchoolConnectionStore(
    private val preferences: SecurePreferences
) {
    private val version = MutableStateFlow(0L)
    private val monitor = newMonitor()

    /** The connections of [uid], re-read whenever this store writes. */
    fun observe(uid: String): Flow<List<StoredSchoolConnection>> = version.map { all(uid) }

    /** The connections of [uid], in the order they were made. */
    fun all(uid: String): List<StoredSchoolConnection> = monitor.locked {
        val bakalari = preferences.keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
            .mapNotNull { key -> preferences.getString(key)?.let(SchoolConnectionCodec::decode) }
        val edupage = preferences.keysWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX)
            .mapNotNull { key -> preferences.getString(key)?.let(EdupageConnectionCodec::decode) }
        (bakalari + edupage)
            .filter { it.ownerUid == uid }
            .sortedBy { it.connection.id }
    }

    /** The connection [id] of [uid], or null — also for one that belongs to another account. */
    fun get(uid: String, id: String): StoredSchoolConnection? = monitor.locked {
        val bakalari = preferences.getString(key(id))?.let(SchoolConnectionCodec::decode)
        val found = bakalari ?: preferences.getString(edupageKey(id))?.let(EdupageConnectionCodec::decode)
        found?.takeIf { it.ownerUid == uid }
    }

    /** Stores [connection], replacing one with the same id. */
    fun put(connection: StoredSchoolConnection) {
        monitor.locked {
            if (connection.edupage != null) {
                preferences.putString(edupageKey(connection.id), EdupageConnectionCodec.encode(connection))
            } else {
                preferences.putString(key(connection.id), SchoolConnectionCodec.encode(connection))
            }
            version.value++
        }
    }

    /**
     * Rewrites the connection [id] of [uid] with [change], atomically with respect to every other
     * call here.
     *
     * @return The stored result, or null when there is no such connection for [uid].
     */
    fun update(
        uid: String,
        id: String,
        change: (StoredSchoolConnection) -> StoredSchoolConnection
    ): StoredSchoolConnection? = monitor.locked {
        get(uid, id)?.let(change)?.also(::put)
    }

    /** Forgets the connection [id] of [uid]. Its imported events stay in the calendar. */
    fun remove(uid: String, id: String) {
        monitor.locked {
            if (get(uid, id) != null) {
                preferences.remove(key(id))
                preferences.remove(edupageKey(id))
                version.value++
            }
        }
    }

    /** Forgets every connection on this device: sign-out, an account switch, account deletion. */
    fun clearAll() {
        monitor.locked {
            preferences.removeWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
            preferences.removeWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX)
            version.value++
        }
    }

    private fun key(id: String) = PreferenceKeys.SCHOOL_CONNECTION_PREFIX + id

    private fun edupageKey(id: String) = PreferenceKeys.EDUPAGE_CONNECTION_PREFIX + id
}

/**
 * The stored form of a [StoredSchoolConnection]: a JSON object built and read field by field.
 *
 * Version 1. A record that cannot be read — another version, a damaged value — decodes to null
 * and is treated as absent; the parent connects again.
 *
 * Public, behind [InternalSharedApi], only for the app's `GsonParityTest`, which holds it to the
 * text Gson wrote.
 */
@InternalSharedApi
object SchoolConnectionCodec {

    private const val VERSION = 1

    /**
     * [stored] as its JSON text: the text Gson's `JsonObject.toString()` wrote before MON-24, which
     * [StoredJson.printTree] prints byte for byte.
     */
    fun encode(stored: StoredSchoolConnection): String {
        val connection = stored.connection
        val root = buildJsonObject {
            put("v", VERSION)
            put("id", connection.id)
            put("owner", stored.ownerUid)
            put("system", "bakalari")
            put("baseUrl", connection.baseUrl)
            put("username", connection.username)
            put("schoolName", connection.schoolName)
            put("studentName", connection.studentName)
            put("childId", connection.childId)
            connection.familyId?.let { put("familyId", it) }
            put("status", connection.status.stored)
            connection.lastSuccessAtMillis?.let { put("lastSuccessAt", it) }
            put("userUid", stored.student.userUid)
            put("fullName", stored.student.fullName)
            put("classId", stored.student.classId)
            put("accessToken", stored.tokens.accessToken)
            put("refreshToken", stored.tokens.refreshToken)
            put("expiresAt", stored.tokens.expiresAtMillis)
            putJsonObject("ledger") {
                stored.ledger.forEach { (eventId, record) ->
                    put(eventId, "${record.kind.stored}|${IsoText.date(record.lastDate)}|${record.fingerprint}")
                }
            }
        }
        return StoredJson.printTree(root)
    }

    /** The record [text] holds, or null when it cannot be read. */
    fun decode(text: String): StoredSchoolConnection? = try {
        (StoredJson.parse(text) as? JsonObject)?.let(::read)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    // One guard per field a record cannot do without; a record missing any of them is not one.
    @Suppress("ReturnCount")
    private fun read(root: JsonObject): StoredSchoolConnection? {
        if (root.number("v") != VERSION.toLong()) return null
        val connection = SchoolConnection(
            id = root.string("id") ?: return null,
            baseUrl = root.string("baseUrl") ?: return null,
            username = root.string("username").orEmpty(),
            schoolName = root.string("schoolName").orEmpty(),
            studentName = root.string("studentName").orEmpty(),
            childId = root.string("childId") ?: return null,
            familyId = root.string("familyId"),
            status = SchoolConnectionStatus.of(root.string("status")),
            lastSuccessAtMillis = root.number("lastSuccessAt")
        )
        return StoredSchoolConnection(
            connection = connection,
            ownerUid = root.string("owner") ?: return null,
            student = SchoolStudent(
                userUid = root.string("userUid").orEmpty(),
                fullName = root.string("fullName").orEmpty(),
                classId = root.string("classId").orEmpty()
            ),
            tokens = BakalariTokens(
                accessToken = root.string("accessToken").orEmpty(),
                refreshToken = root.string("refreshToken").orEmpty(),
                expiresAtMillis = root.number("expiresAt") ?: 0L
            ),
            ledger = readLedger(root["ledger"] as? JsonObject)
        )
    }

    internal fun readLedger(ledger: JsonObject?): Map<String, SchoolImportRecord> =
        ledger.orEmpty().mapNotNull { (eventId, value) ->
            val parts = value.textOrNull()?.split('|') ?: return@mapNotNull null
            if (parts.size != LEDGER_PARTS) return@mapNotNull null
            val kind = SchoolImportKind.of(parts[0]) ?: return@mapNotNull null
            // `LocalDate.parse`'s grammar, which this read until MON-24.
            val date = try {
                IsoText.parseDate(parts[1])
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            eventId to SchoolImportRecord(kind, date, parts[2])
        }.toMap()

    private fun JsonObject.string(name: String): String? = get(name)?.textOrNull()

    /** A number written as a number, as Gson's `isNumber` asked; a quoted one is not. */
    private fun JsonObject.number(name: String): Long? =
        (get(name) as? JsonPrimitive)?.takeIf { it !== JsonNull && !it.isString }?.longOrNull

    /** A string, number or boolean as its text — Gson's `getAsString` of a primitive; null else. */
    private fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content

    private const val LEDGER_PARTS = 3
}
