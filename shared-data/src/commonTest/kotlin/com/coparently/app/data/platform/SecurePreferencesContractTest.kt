package com.coparently.app.data.platform

import com.coparently.app.data.local.preferences.PreferenceKeys
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What every [SecurePreferences] does, whatever seals it (MON-24 phase 2, Task 6).
 *
 * Run here over [SealedSecurePreferences] in memory (`SealedSecurePreferencesContractTest`, the JVM
 * and the iOS simulator), over the real Keychain in an app-hosted process (`KeychainSecurePreferencesTest`,
 * ignored until the iPhone app's XCTest host exists in phase 3: a bare simulator test binary has no
 * Keychain),
 * and in the app over the real Android store under Robolectric (`EncryptedPreferencesContractTest`,
 * which repeats these cases because the app's tests cannot extend a class of this module's tests —
 * change the two together).
 *
 * Each case opens a store with [open] over storage [wipe] emptied first; opening again stands for
 * a restart. A school connection's refusal to another account is `SchoolConnectionStore`'s, which
 * reads the owner out of the stored text: what is held here is that the store keeps that text
 * whole, through [SecurePreferences.clear] and a restart, and the app's contract test runs the
 * refusal itself over the real store.
 */
abstract class SecurePreferencesContractTest {

    /** A new store over this test's storage — a second instance, or the same one after a restart. */
    protected abstract fun open(): SecurePreferences

    /** Empties this test's storage. */
    protected abstract fun wipe()

    @BeforeTest
    fun startEmpty() = wipe()

    @AfterTest
    fun leaveNothing() = wipe()

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

        // What `SchoolConnectionStore` reads when another account asks: the stored text, which
        // still names Alice, so its owner check refuses it to anybody else.
        val stored = open().getString(connection)
        assertEquals(CONNECTION_OF_ALICE, stored)
        assertTrue(stored.orEmpty().contains("\"owner\":\"alice\""))
        assertEquals(setOf(connection), open().keysWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX))
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

    private companion object {
        /** A stored school connection as `SchoolConnectionCodec` writes it, trimmed to its owner. */
        const val CONNECTION_OF_ALICE = "{\"v\":1,\"id\":\"conn1\",\"owner\":\"alice\",\"system\":\"bakalari\"}"
    }
}
