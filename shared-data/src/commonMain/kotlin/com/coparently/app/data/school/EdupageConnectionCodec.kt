@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school

import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSystem
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
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
 * The stored form of an EduPage [StoredSchoolConnection]: a JSON object built and read field by
 * field, like [SchoolConnectionCodec] (which it leaves untouched), under
 * `PreferenceKeys.EDUPAGE_CONNECTION_PREFIX`.
 *
 * Version 1, `"system":"edupage"`. It carries the password and the session cookies of
 * [EdupageSecrets]; the store is sealed, and nothing here may log the text. A record that cannot be
 * read — another version or system, a damaged value, no secrets — decodes to null and is treated as
 * absent; the parent connects again.
 */
@InternalSharedApi
object EdupageConnectionCodec {

    private const val VERSION = 1

    /** [stored] as its JSON text; [stored] must carry [StoredSchoolConnection.edupage]. */
    fun encode(stored: StoredSchoolConnection): String {
        val secrets = requireNotNull(stored.edupage) { "not an EduPage connection" }
        val connection = stored.connection
        val root = buildJsonObject {
            put("v", VERSION)
            put("id", connection.id)
            put("owner", stored.ownerUid)
            put("system", SchoolSystem.EDUPAGE.stored)
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
            put("password", secrets.password)
            putJsonObject("cookies") {
                secrets.cookies.forEach { (name, value) -> put(name, value) }
            }
            put("studentId", secrets.studentId)
            secrets.deviceToken?.let { put("deviceToken", it) }
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
        if (root.string("system") != SchoolSystem.EDUPAGE.stored) return null
        val connection = SchoolConnection(
            id = root.string("id") ?: return null,
            baseUrl = root.string("baseUrl") ?: return null,
            username = root.string("username").orEmpty(),
            schoolName = root.string("schoolName").orEmpty(),
            studentName = root.string("studentName").orEmpty(),
            childId = root.string("childId") ?: return null,
            familyId = root.string("familyId"),
            status = SchoolConnectionStatus.of(root.string("status")),
            lastSuccessAtMillis = root.number("lastSuccessAt"),
            system = SchoolSystem.EDUPAGE
        )
        val secrets = EdupageSecrets(
            password = root.string("password") ?: return null,
            cookies = (root["cookies"] as? JsonObject).orEmpty()
                .mapNotNull { (name, value) -> value.textOrNull()?.let { name to it } }
                .toMap(),
            studentId = root.string("studentId") ?: return null,
            deviceToken = root.string("deviceToken")
        )
        return StoredSchoolConnection(
            connection = connection,
            ownerUid = root.string("owner") ?: return null,
            student = SchoolStudent(
                userUid = root.string("userUid").orEmpty(),
                fullName = root.string("fullName").orEmpty(),
                classId = root.string("classId").orEmpty()
            ),
            tokens = BakalariTokens("", "", 0L),
            ledger = SchoolConnectionCodec.readLedger(root["ledger"] as? JsonObject),
            edupage = secrets
        )
    }

    private fun JsonObject.string(name: String): String? = get(name)?.textOrNull()

    private fun JsonObject.number(name: String): Long? =
        (get(name) as? JsonPrimitive)?.takeIf { it !== JsonNull && !it.isString }?.longOrNull

    private fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content
}
