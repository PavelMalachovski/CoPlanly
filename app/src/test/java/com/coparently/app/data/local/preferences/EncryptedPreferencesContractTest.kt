package com.coparently.app.data.local.preferences

import android.content.Context
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolTestSupport
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `shared-data`'s `SecurePreferencesContractTest`, case for case, over the real Android store
 * (MON-24 phase 2, Task 6): [EncryptedPreferences] under Robolectric, with the device's own
 * `noBackupFilesDir` and preference files, sealed by a stand-in for the Keystore, which Robolectric
 * does not have. The iPhone's Keychain store passes the same cases there; **change the two
 * together**, because the app's tests cannot extend a class of that module's tests.
 *
 * It adds what the common contract can only state: a school connection written for one account is
 * refused to another by [SchoolConnectionStore] over the real store, through `clear()` and a
 * restart (CLAUDE.md item 35).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class EncryptedPreferencesContractTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A new store over this test's file: a second instance, or the same one after a restart. */
    private fun open(): SecurePreferences = openStore()

    private fun openStore(): EncryptedPreferences =
        EncryptedPreferences(context, ReversingCipher, fileName = FILE_NAME, legacyName = LEGACY_NAME)

    @Before
    fun startEmpty() = wipe()

    @After
    fun leaveNothing() = wipe()

    private fun wipe() {
        File(context.noBackupFilesDir, FILE_NAME).delete()
        File(context.noBackupFilesDir, "$FILE_NAME.part").delete()
    }

    @Test
    fun `every value type reads back after a restart`() {
        open().apply {
            putAccessToken("access-1")
            putRefreshToken("refresh-1")
            putTokenExpiry(1_700_000_000_123L)
            putCalendarId("work")
            putGoogleIdToken("id-1")
            putUserEmail("alice@example.test")
            putDarkTheme(false)
            putBoolean("push_enabled", false)
            putString("note", "Vyzvednu ji v 17:00 — ключ ü")
            putEventDraft("alice__bob", "{\"title\":\"Dentist\"}")
            putEventDraft(null, "{\"title\":\"No family\"}")
            putChatDraft("alice_bob", "Pickup at 5")
            putSplitRatioBasisPoints(6_000)
            putSplitRatioSlot("dad")
            putFamilySplitRatioBasisPoints("alice__bob", 0)
            putDefaultCurrency("CZK")
        }

        val reopened = open()
        assertEquals("access-1", reopened.getAccessToken())
        assertEquals("refresh-1", reopened.getRefreshToken())
        assertEquals(1_700_000_000_123L, reopened.getTokenExpiry())
        assertEquals("work", reopened.getCalendarId())
        assertEquals("id-1", reopened.getGoogleIdToken())
        assertEquals("alice@example.test", reopened.getUserEmail())
        assertEquals(false, reopened.getDarkTheme())
        assertFalse(reopened.getBoolean("push_enabled", true))
        assertEquals("Vyzvednu ji v 17:00 — ключ ü", reopened.getString("note"))
        assertEquals("{\"title\":\"Dentist\"}", reopened.getEventDraft("alice__bob"))
        assertEquals("{\"title\":\"No family\"}", reopened.getEventDraft(null))
        assertEquals("{\"title\":\"No family\"}", reopened.getEventDraft(""))
        assertNull(reopened.getEventDraft("alice__carol"), "a draft is never another family's")
        assertEquals("Pickup at 5", reopened.getChatDraft("alice_bob"))
        assertEquals(6_000, reopened.getSplitRatioBasisPoints())
        assertEquals("dad", reopened.getSplitRatioSlot())
        assertEquals(0, reopened.getFamilySplitRatioBasisPoints("alice__bob"))
        assertNull(reopened.getFamilySplitRatioBasisPoints("alice__carol"))
        assertEquals("CZK", reopened.getDefaultCurrency())
    }

    @Test
    fun `an empty store answers with each getter's own default`() {
        val store = open()
        assertNull(store.getAccessToken())
        assertNull(store.getTokenExpiry())
        assertEquals("primary", store.getCalendarId())
        assertNull(store.getDarkTheme(), "no choice is the system's theme")
        assertTrue(store.getBoolean("missing", true))
        assertNull(store.getString("missing"))
        assertEquals("fallback", store.getString("missing", "fallback"))
        assertEquals("", store.getChatDraft("alice_bob"))
        assertNull(store.getSplitRatioBasisPoints())
        assertNull(store.getSplitRatioSlot())
        assertNull(store.getDefaultCurrency())
    }

    @Test
    fun `removals and prefixes are written through`() {
        open().apply {
            putChatDraft("a_b", "typed")
            putChatDraft("a_b", "")
            putDarkTheme(true)
            clearDarkTheme()
            putSplitRatioSlot("mom")
            putSplitRatioSlot(null)
            putEventDraft("f1", "draft")
            clearEventDraft("f1")
            putString("cursor_u1", "1")
            putString("cursor_u2", "2")
            putString("other", "3")
            removeWithPrefix("cursor_")
            putString("gone", "x")
            remove("gone")
        }

        val reopened = open()
        assertEquals("", reopened.getChatDraft("a_b"))
        assertTrue(reopened.keysWithPrefix(PreferenceKeys.CHAT_DRAFT_PREFIX).isEmpty(), "an empty draft keeps no row")
        assertNull(reopened.getDarkTheme())
        assertNull(reopened.getSplitRatioSlot())
        assertNull(reopened.getEventDraft("f1"))
        assertEquals(emptySet(), reopened.keysWithPrefix("cursor_"))
        assertEquals(setOf("other"), reopened.keysWithPrefix("oth"))
        assertNull(reopened.getString("gone"))
    }

    @Test
    fun `clear keeps the slot markers and the school and EduPage connections and nothing else`() {
        val marker = "${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u1"
        val otherMarker = "${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u2"
        val connection = "${PreferenceKeys.SCHOOL_CONNECTION_PREFIX}conn1"
        val edupage = "${PreferenceKeys.EDUPAGE_CONNECTION_PREFIX}conn2"
        open().apply {
            putString(edupage, CONNECTION_OF_ALICE)
            putString(marker, "dad")
            putString(otherMarker, "mom")
            putString(connection, CONNECTION_OF_ALICE)
            putAccessToken("token-1")
            putUserEmail("alice@example.test")
            putString(PreferenceKeys.TELEMETRY_CONSENT, "GRANTED")
            putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, true)
            clear()
        }

        val reopened = open()
        assertEquals("dad", reopened.getString(marker))
        assertEquals("mom", reopened.getString(otherMarker))
        assertEquals(CONNECTION_OF_ALICE, reopened.getString(connection), "the record and its owner kept whole")
        assertEquals(CONNECTION_OF_ALICE, reopened.getString(edupage), "an EduPage connection is kept too")
        assertNull(reopened.getAccessToken())
        assertNull(reopened.getUserEmail())
        assertNull(reopened.getString(PreferenceKeys.TELEMETRY_CONSENT))
        assertFalse(reopened.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false))
    }

    @Test
    fun `a school connection is held for its owner alone across a restart`() {
        val connection = "${PreferenceKeys.SCHOOL_CONNECTION_PREFIX}conn1"
        open().putString(connection, CONNECTION_OF_ALICE)

        val stored = open().getString(connection)
        assertEquals(CONNECTION_OF_ALICE, stored)
        assertTrue(stored.orEmpty().contains("\"owner\":\"alice\""))
        assertEquals(setOf(connection), open().keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX))
    }

    @Test
    fun `a store opened for another account is refused the school connection`() {
        SchoolConnectionStore(openStore()).put(SchoolTestSupport.stored(uid = "alice", id = "conn1"))
        open().clear()

        // A restart, then another account asks (item 35).
        val store = SchoolConnectionStore(openStore())
        assertNull(store.get("bob", "conn1"))
        assertTrue(store.all("bob").isEmpty())
        assertNotNull(store.get("alice", "conn1"), "the owner still has it after clear()")
    }

    @Test
    fun `disconnecting Google Calendar removes its keys and nothing else`() {
        open().apply {
            putAccessToken("access-1")
            putRefreshToken("refresh-1")
            putTokenExpiry(1_234L)
            putUserEmail("alice@example.test")
            putCalendarId("work")
            putGoogleIdToken("id-token")
            putString(PreferenceKeys.PUSH_ENABLED, "false")
            putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, true)
            removeGoogleCalendarAccount()
        }

        val reopened = open()
        assertNull(reopened.getAccessToken())
        assertNull(reopened.getRefreshToken())
        assertNull(reopened.getTokenExpiry())
        assertNull(reopened.getUserEmail())
        assertNull(reopened.getGoogleIdToken())
        assertEquals("primary", reopened.getCalendarId())
        assertEquals("false", reopened.getString(PreferenceKeys.PUSH_ENABLED))
        assertTrue(reopened.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false))
    }

    @Test
    fun `a refused connection loses its tokens and keeps its address`() {
        open().apply {
            putAccessToken("access-1")
            putRefreshToken("refresh-1")
            putTokenExpiry(1_234L)
            putUserEmail("alice@example.test")
            removeGoogleTokens()
        }

        val reopened = open()
        assertNull(reopened.getAccessToken())
        assertNull(reopened.getRefreshToken())
        assertNull(reopened.getTokenExpiry())
        assertEquals("alice@example.test", reopened.getUserEmail())
    }

    @Test
    fun `a second instance opened after a commit sees it`() {
        val first = open()
        first.putRefreshToken("refresh-1")

        val second = open()
        assertEquals("refresh-1", second.getRefreshToken())

        second.putRefreshToken("refresh-2")
        assertEquals("refresh-2", open().getRefreshToken())
    }

    /** Not encryption — enough of a transform that the store must go through it. */
    private object ReversingCipher : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String = plain.reversed()
        override fun open(sealed: String): String = sealed.reversed()
    }

    private companion object {
        const val FILE_NAME = "contract_prefs.bin"
        const val LEGACY_NAME = "contract_legacy_prefs"

        /** A stored school connection as `SchoolConnectionCodec` writes it, trimmed to its owner. */
        const val CONNECTION_OF_ALICE = "{\"v\":1,\"id\":\"conn1\",\"owner\":\"alice\",\"system\":\"bakalari\"}"
    }
}
