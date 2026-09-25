package com.coparently.app.data.school

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import com.coparently.app.domain.school.SchoolStudent
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the phone keeps about one school connection (MON-8).
 *
 * @property connection What the screens show.
 * @property ownerUid The Firebase account it belongs to. The store refuses it to any other.
 * @property student The child as the school describes them, to match its events.
 * @property tokens The latest token pair. **Never a password.**
 * @property ledger What the connection has imported, by event id; see `SchoolImportRecord`.
 */
data class StoredSchoolConnection(
    val connection: SchoolConnection,
    val ownerUid: String,
    val student: SchoolStudent,
    val tokens: BakalariTokens,
    val ledger: Map<String, SchoolImportRecord> = emptyMap()
) {
    /** The connection's id. */
    val id: String get() = connection.id
}

/**
 * The school connections, sealed in [EncryptedPreferences] (SEC-5) under
 * [PreferenceKeys.SCHOOL_CONNECTION_PREFIX] — one key per connection.
 *
 * **Scoped to the signed-in account, in the style of `TodayWidgetNames`**: every record names its
 * owner's uid, and every read takes the uid and refuses a record written for anybody else. The
 * three places that forget the connections — sign-out, an account switch and account deletion —
 * call [clearAll]; `EncryptedPreferences.clear()` deliberately keeps them, because a Google
 * Calendar disconnect reaches it too.
 *
 * Every method is synchronised, so a read-modify-write in [update] cannot interleave with another,
 * and every write reaches the sealed file before the method returns (`InMemorySharedPreferences`
 * persists each commit under its lock) — which is what lets `BakalariAuthorizer` write a rotated
 * refresh token down **before** it uses it.
 *
 * The value is JSON written and read by hand ([SchoolConnectionCodec]), never Gson over a data
 * class, which R8 would rename.
 */
@Singleton
class SchoolConnectionStore @Inject constructor(
    private val preferences: EncryptedPreferences
) {
    private val version = MutableStateFlow(0L)

    /** The connections of [uid], re-read whenever this store writes. */
    fun observe(uid: String): Flow<List<StoredSchoolConnection>> = version.map { all(uid) }

    /** The connections of [uid], in the order they were made. */
    @Synchronized
    fun all(uid: String): List<StoredSchoolConnection> =
        preferences.keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
            .mapNotNull { key -> preferences.getString(key)?.let(SchoolConnectionCodec::decode) }
            .filter { it.ownerUid == uid }
            .sortedBy { it.connection.id }

    /** The connection [id] of [uid], or null — also for one that belongs to another account. */
    @Synchronized
    fun get(uid: String, id: String): StoredSchoolConnection? =
        preferences.getString(key(id))?.let(SchoolConnectionCodec::decode)?.takeIf { it.ownerUid == uid }

    /** Stores [connection], replacing one with the same id. */
    @Synchronized
    fun put(connection: StoredSchoolConnection) {
        preferences.putString(key(connection.id), SchoolConnectionCodec.encode(connection))
        version.value++
    }

    /**
     * Rewrites the connection [id] of [uid] with [change], atomically with respect to every other
     * call here.
     *
     * @return The stored result, or null when there is no such connection for [uid].
     */
    @Synchronized
    fun update(
        uid: String,
        id: String,
        change: (StoredSchoolConnection) -> StoredSchoolConnection
    ): StoredSchoolConnection? {
        val current = get(uid, id) ?: return null
        val next = change(current)
        put(next)
        return next
    }

    /** Forgets the connection [id] of [uid]. Its imported events stay in the calendar. */
    @Synchronized
    fun remove(uid: String, id: String) {
        if (get(uid, id) == null) return
        preferences.remove(key(id))
        version.value++
    }

    /** Forgets every connection on this device: sign-out, an account switch, account deletion. */
    @Synchronized
    fun clearAll() {
        preferences.removeWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
        version.value++
    }

    private fun key(id: String) = PreferenceKeys.SCHOOL_CONNECTION_PREFIX + id
}

/**
 * The stored form of a [StoredSchoolConnection]: a JSON object built and read field by field.
 *
 * Version 1. A record that cannot be read — another version, a damaged value — decodes to null
 * and is treated as absent; the parent connects again.
 */
internal object SchoolConnectionCodec {

    private const val VERSION = 1

    /** [stored] as its JSON text. */
    fun encode(stored: StoredSchoolConnection): String {
        val connection = stored.connection
        val root = JsonObject()
        root.addProperty("v", VERSION)
        root.addProperty("id", connection.id)
        root.addProperty("owner", stored.ownerUid)
        root.addProperty("system", "bakalari")
        root.addProperty("baseUrl", connection.baseUrl)
        root.addProperty("username", connection.username)
        root.addProperty("schoolName", connection.schoolName)
        root.addProperty("studentName", connection.studentName)
        root.addProperty("childId", connection.childId)
        connection.familyId?.let { root.addProperty("familyId", it) }
        root.addProperty("status", connection.status.stored)
        connection.lastSuccessAtMillis?.let { root.addProperty("lastSuccessAt", it) }
        root.addProperty("userUid", stored.student.userUid)
        root.addProperty("fullName", stored.student.fullName)
        root.addProperty("classId", stored.student.classId)
        root.addProperty("accessToken", stored.tokens.accessToken)
        root.addProperty("refreshToken", stored.tokens.refreshToken)
        root.addProperty("expiresAt", stored.tokens.expiresAtMillis)
        val ledger = JsonObject()
        stored.ledger.forEach { (eventId, record) ->
            ledger.addProperty(eventId, "${record.kind.stored}|${record.lastDate}|${record.fingerprint}")
        }
        root.add("ledger", ledger)
        return root.toString()
    }

    /** The record [text] holds, or null when it cannot be read. */
    fun decode(text: String): StoredSchoolConnection? = try {
        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject?.let(::read)
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalStateException) {
        null
    } catch (e: UnsupportedOperationException) {
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
            ledger = readLedger(root.get("ledger")?.takeIf { it.isJsonObject }?.asJsonObject)
        )
    }

    private fun readLedger(ledger: JsonObject?): Map<String, SchoolImportRecord> =
        ledger?.entrySet().orEmpty().mapNotNull { (eventId, value) ->
            val parts = value.takeIf { it.isJsonPrimitive }?.asString?.split('|') ?: return@mapNotNull null
            if (parts.size != LEDGER_PARTS) return@mapNotNull null
            val kind = SchoolImportKind.of(parts[0]) ?: return@mapNotNull null
            val date = try {
                LocalDate.parse(parts[1])
            } catch (e: DateTimeParseException) {
                return@mapNotNull null
            }
            eventId to SchoolImportRecord(kind, date, parts[2])
        }.toMap()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.number(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private const val LEDGER_PARTS = 3
}
