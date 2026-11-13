@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSystem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** EduPage connections in [SchoolConnectionStore]: their own key, their secrets, both kinds side by side. */
class EdupageConnectionStoreTest {

    private val prefs = SealedSecurePreferences(MemoryTextStore())
    private val store = SchoolConnectionStore(prefs)

    private val secrets = EdupageSecrets(
        password = "hunter2",
        cookies = mapOf("PHPSESSID" to "abc", "edid" to "x y"),
        studentId = "-1234",
        deviceToken = "dev-1"
    )

    private fun record(id: String, edupage: EdupageSecrets? = null, uid: String = "alice") = StoredSchoolConnection(
        connection = SchoolConnection(
            id = id,
            baseUrl = "https://skola.edupage.org",
            username = "novak",
            schoolName = "School",
            studentName = "Anna",
            childId = "child-1",
            familyId = "alice__bob",
            status = SchoolConnectionStatus.OK,
            lastSuccessAtMillis = 42L,
            system = if (edupage != null) SchoolSystem.EDUPAGE else SchoolSystem.BAKALARI
        ),
        ownerUid = uid,
        student = SchoolStudent("u1", "Anna N", "X"),
        tokens = if (edupage != null) BakalariTokens("", "", 0L) else BakalariTokens("a", "r", 5L),
        ledger = mapOf("e1" to SchoolImportRecord(SchoolImportKind.entries.first(), LocalDate(2026, 11, 12), "fp")),
        edupage = edupage
    )

    @Test
    fun `an edupage record round-trips with its secrets`() {
        val edupage = record("ep1", secrets)
        store.put(edupage)

        assertEquals(edupage, store.get("alice", "ep1"))
        assertEquals(SchoolSystem.EDUPAGE, store.get("alice", "ep1")?.connection?.system)
        assertEquals(secrets, store.get("alice", "ep1")?.edupage)
    }

    @Test
    fun `an edupage record without a device token round-trips`() {
        val edupage = record("ep1", secrets.copy(deviceToken = null, cookies = emptyMap()))
        store.put(edupage)

        assertEquals(edupage, store.get("alice", "ep1"))
        assertNull(store.get("alice", "ep1")?.edupage?.deviceToken)
    }

    @Test
    fun `an edupage record is stored under its own prefix and not under the bakalari one`() {
        store.put(record("ep1", secrets))

        assertTrue(prefs.keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX).isEmpty())
        assertEquals(1, prefs.keysWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX).size)
    }

    @Test
    fun `all lists bakalari and edupage connections of the owner`() {
        store.put(record("b1"))
        store.put(record("ep1", secrets))
        store.put(record("ep2", secrets, uid = "carol"))

        assertEquals(listOf("b1", "ep1"), store.all("alice").map { it.id })
        assertNull(store.get("alice", "ep2"))
    }

    @Test
    fun `update and remove reach an edupage record`() {
        store.put(record("ep1", secrets))

        val updated = store.update("alice", "ep1") { it.copy(edupage = secrets.copy(cookies = mapOf("a" to "b"))) }
        assertEquals(mapOf("a" to "b"), updated?.edupage?.cookies)
        assertEquals(mapOf("a" to "b"), store.get("alice", "ep1")?.edupage?.cookies)

        store.remove("alice", "ep1")
        assertNull(store.get("alice", "ep1"))
        assertTrue(prefs.keysWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX).isEmpty())
    }

    @Test
    fun `clearAll removes both kinds`() {
        store.put(record("b1"))
        store.put(record("ep1", secrets))

        store.clearAll()

        assertTrue(store.all("alice").isEmpty())
        assertTrue(prefs.keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX).isEmpty())
        assertTrue(prefs.keysWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX).isEmpty())
    }

    @Test
    fun `the secrets never print the password`() {
        assertFalse("hunter2" in secrets.toString())
        assertFalse("PHPSESSID" in secrets.toString())
        assertEquals("EdupageSecrets(studentId=-1234)", secrets.toString())
    }

    @Test
    fun `a bakalari record is written byte for byte as before`() {
        val expected = """{"v":1,"id":"c1","owner":"alice","system":"bakalari",""" +
            """"baseUrl":"https://skola.example.cz","username":"novak","schoolName":"School","studentName":"Anna",""" +
            """"childId":"child-1","status":"ok",""" +
            """"userUid":"u1","fullName":"Anna N","classId":"X","accessToken":"a","refreshToken":"r",""" +
            """"expiresAt":5,"ledger":{}}"""
        val bakalari = record("c1").copy(
            connection = record("c1").connection.copy(
                baseUrl = "https://skola.example.cz",
                familyId = null,
                lastSuccessAtMillis = null
            ),
            ledger = emptyMap()
        )

        assertEquals(expected, SchoolConnectionCodec.encode(bakalari))
    }

    @Test
    fun `a bakalari reader does not take an edupage record`() {
        val text = EdupageConnectionCodec.encode(record("ep1", secrets))

        assertNull(EdupageConnectionCodec.decode(SchoolConnectionCodec.encode(record("b1"))))
        assertEquals("ep1", EdupageConnectionCodec.decode(text)?.id)
    }
}
