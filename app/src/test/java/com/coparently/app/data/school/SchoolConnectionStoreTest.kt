package com.coparently.app.data.school

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a school connection's tokens live (MON-8): the sealed preference store, per account, and
 * never the password — over the real [com.coparently.app.data.local.preferences.EncryptedPreferences]
 * with a stand-in cipher.
 */
class SchoolConnectionStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("school-store").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a connection round-trips, record and tokens included, and survives a restart`() {
        val stored = SchoolTestSupport.stored().copy(
            ledger = mapOf(
                "school-abc" to SchoolImportRecord(SchoolImportKind.DAY_OFF, LocalDate.of(2026, 10, 9), "0123abcd")
            ),
            tokens = BakalariTokens("access-9", "refresh-9", 1_700_000_000_000L)
        )
        SchoolConnectionStore(SchoolTestSupport.preferences(dir)).put(stored)

        val reopened = SchoolConnectionStore(SchoolTestSupport.preferences(dir))

        assertEquals(stored, reopened.get("alice", "conn1"))
        assertEquals(listOf(stored), reopened.all("alice"))
    }

    @Test
    fun `another account is refused the connection`() {
        val store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
        store.put(SchoolTestSupport.stored(uid = "alice"))

        assertNull(store.get("carol", "conn1"))
        assertTrue(store.all("carol").isEmpty())
        store.remove("carol", "conn1")
        assertNotNull(store.get("alice", "conn1"), "another account cannot remove it either")
    }

    @Test
    fun `nothing stored holds a password field, and the file is not clear text`() {
        SchoolConnectionStore(SchoolTestSupport.preferences(dir)).put(SchoolTestSupport.stored())

        val onDisk = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(onDisk.contains("refresh-1"), "the refresh token is on disk in clear text")
        val json = SchoolConnectionCodec.encode(SchoolTestSupport.stored())
        assertFalse(json.contains("password", ignoreCase = true))
    }

    @Test
    fun `an update rewrites the record it read`() {
        val store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
        store.put(SchoolTestSupport.stored())

        val updated = store.update("alice", "conn1") {
            it.copy(connection = it.connection.copy(status = SchoolConnectionStatus.NEEDS_PASSWORD))
        }

        assertEquals(SchoolConnectionStatus.NEEDS_PASSWORD, updated?.connection?.status)
        assertEquals(SchoolConnectionStatus.NEEDS_PASSWORD, store.get("alice", "conn1")?.connection?.status)
        assertNull(store.update("alice", "missing") { it })
    }

    @Test
    fun `a Google Calendar disconnect keeps the connections, clearAll forgets them`() {
        val preferences = SchoolTestSupport.preferences(dir)
        val store = SchoolConnectionStore(preferences)
        store.put(SchoolTestSupport.stored())
        preferences.putString("user_email", "alice@example.com")

        preferences.clear()

        assertNull(preferences.getString("user_email"))
        assertNotNull(store.get("alice", "conn1"))

        store.clearAll()

        assertNull(store.get("alice", "conn1"))
        assertTrue(preferences.keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX).isEmpty())
    }

    @Test
    fun `a record that cannot be read is absent, not a crash`() {
        val preferences = SchoolTestSupport.preferences(dir)
        preferences.putString(PreferenceKeys.SCHOOL_CONNECTION_PREFIX + "broken", "{not json")
        preferences.putString(PreferenceKeys.SCHOOL_CONNECTION_PREFIX + "future", """{"v":2,"id":"future"}""")
        val store = SchoolConnectionStore(preferences)

        assertNull(store.get("alice", "broken"))
        assertNull(store.get("alice", "future"))
        assertTrue(store.all("alice").isEmpty())
    }
}
