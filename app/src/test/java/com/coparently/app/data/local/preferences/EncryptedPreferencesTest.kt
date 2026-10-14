package com.coparently.app.data.local.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real [EncryptedPreferences] over a temporary directory (SEC-5), with a stand-in for the
 * Keystore cipher — the Android Keystore does not exist on a JVM, and what is under test here is
 * the store around it: what reaches the disk, what survives a restart, and what happens when the
 * sealed file cannot be opened or nothing can be sealed at all. The Keystore itself, and the
 * migration from the old `EncryptedSharedPreferences` file, run on the emulators in
 * `EncryptedPreferencesMigrationTest`.
 *
 * It also keeps [EncryptedPreferences.clear]'s one exemption pinned: a marker under
 * [PreferenceKeys.PARENT_SLOT_MARKER_PREFIX] survives whatever calls `clear()` — Sign out, an
 * account switch and account deletion do — because a device that loses it while holding history
 * stamped in the old slot takes `ParentSlotMigrator`'s "nothing to do" branch on the next sync.
 * A Google Calendar disconnect no longer calls `clear()` at all; it removes Google's own keys.
 */
class EncryptedPreferencesTest {

    private lateinit var dir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("prefs").toFile()
        context = mockk(relaxed = true)
        every { context.noBackupFilesDir } returns dir
        // No store from before SEC-5 on this "device".
        every { context.getSharedPreferences(any(), any()) } returns InMemorySharedPreferences()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `values survive a restart and never reach the disk in clear text`() {
        EncryptedPreferences(context, ReversingCipher).apply {
            putRefreshToken("refresh-secret-1")
            putTokenExpiry(1_234L)
            putBoolean("push_enabled", false)
            putChatDraft("alice_bob", "Pickup at 5")
        }

        val reopened = EncryptedPreferences(context, ReversingCipher)
        assertEquals("refresh-secret-1", reopened.getRefreshToken())
        assertEquals(1_234L, reopened.getTokenExpiry())
        assertFalse(reopened.getBoolean("push_enabled", true))
        assertEquals("Pickup at 5", reopened.getChatDraft("alice_bob"))

        val onDisk = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(onDisk.contains("refresh-secret-1"), "the token is on disk in clear text")
        assertFalse(onDisk.contains("refresh_token"), "a key name is on disk in clear text")
        assertFalse(dir.walkTopDown().any { it.name.endsWith(".part") }, "a partial write was left behind")
    }

    @Test
    fun `a sealed file that cannot be opened is replaced by an empty store`() {
        EncryptedPreferences(context, ReversingCipher).putRefreshToken("old")
        // The Keystore key is gone: nothing sealed before can be opened.
        val reopened = EncryptedPreferences(context, RefusingToOpen)

        assertNull(reopened.getRefreshToken())
        reopened.putRefreshToken("new")
        assertEquals("new", EncryptedPreferences(context, ReversingCipher).getRefreshToken())
    }

    @Test
    fun `a device that cannot seal keeps everything in memory and writes nothing`() {
        val preferences = EncryptedPreferences(context, RefusingToSeal)
        preferences.putRefreshToken("refresh-secret-2")

        assertEquals("refresh-secret-2", preferences.getRefreshToken())
        assertTrue(dir.walkTopDown().none { it.isFile }, "something was written without a cipher")
    }

    @Test
    fun `clear preserves parent-slot markers but wipes everything else`() {
        val preferences = EncryptedPreferences(context, ReversingCipher)
        val markerKey = "${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u1"
        preferences.putString(markerKey, "dad")
        preferences.putUserEmail("alice@example.test")
        preferences.putAccessToken("token-1")

        preferences.clear()

        assertEquals("dad", preferences.getString(markerKey))
        assertNull(preferences.getUserEmail())
        assertNull(preferences.getAccessToken())
        // And the cleared state is what a restart reads back.
        val reopened = EncryptedPreferences(context, ReversingCipher)
        assertEquals("dad", reopened.getString(markerKey))
        assertNull(reopened.getAccessToken())
    }

    @Test
    fun `disconnecting Google Calendar removes Google's keys and nothing else`() {
        // "Sign out from Calendar sync" used to reach `clear()`, and with it the push opt-out, the
        // telemetry answer, the family on screen, the sync cursors and the calendar filters.
        val preferences = EncryptedPreferences(context, ReversingCipher)
        preferences.putAccessToken("access-1")
        preferences.putRefreshToken("refresh-1")
        preferences.putTokenExpiry(1_234L)
        preferences.putUserEmail("alice@example.test")
        preferences.putCalendarId("work")
        preferences.putGoogleIdToken("id-token")
        preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")
        preferences.putString(PreferenceKeys.TELEMETRY_CONSENT, "GRANTED")
        preferences.putString("${PreferenceKeys.SELECTED_FAMILY_PREFIX}u1", "u1__u2")
        preferences.putString("${PreferenceKeys.EVENT_SYNC_CURSOR_PREFIX}u1", "1700000000000")
        preferences.putString(PreferenceKeys.HIDDEN_EVENT_TYPES, "school")
        preferences.putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, true)

        preferences.removeGoogleCalendarAccount()

        val reopened = EncryptedPreferences(context, ReversingCipher)
        assertNull(reopened.getAccessToken())
        assertNull(reopened.getRefreshToken())
        assertNull(reopened.getTokenExpiry())
        assertNull(reopened.getUserEmail())
        assertNull(reopened.getGoogleIdToken())
        // Its getter falls back to "primary"; the stored value is what has to go.
        assertEquals("primary", reopened.getCalendarId())
        assertEquals("false", reopened.getString(PreferenceKeys.PUSH_ENABLED))
        assertEquals("GRANTED", reopened.getString(PreferenceKeys.TELEMETRY_CONSENT))
        assertEquals("u1__u2", reopened.getString("${PreferenceKeys.SELECTED_FAMILY_PREFIX}u1"))
        assertEquals("1700000000000", reopened.getString("${PreferenceKeys.EVENT_SYNC_CURSOR_PREFIX}u1"))
        assertEquals("school", reopened.getString(PreferenceKeys.HIDDEN_EVENT_TYPES))
        // The switch is the parent's choice, not part of the connection: reconnecting finds it as left.
        assertTrue(reopened.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false))
    }

    @Test
    fun `a refused connection loses its tokens and keeps its address`() {
        // The address is what tells "reconnect" apart from "never connected".
        val preferences = EncryptedPreferences(context, ReversingCipher)
        preferences.putAccessToken("access-1")
        preferences.putRefreshToken("refresh-1")
        preferences.putTokenExpiry(1_234L)
        preferences.putUserEmail("alice@example.test")

        preferences.removeGoogleTokens()

        assertNull(preferences.getAccessToken())
        assertNull(preferences.getRefreshToken())
        assertNull(preferences.getTokenExpiry())
        assertEquals("alice@example.test", preferences.getUserEmail())
    }

    @Test
    fun `clear preserves markers for every uid that has one`() {
        // Two accounts have signed into this device over time: a sign-out by either must not
        // cost the other one its history.
        val preferences = EncryptedPreferences(context, ReversingCipher)
        preferences.putString("${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u1", "dad")
        preferences.putString("${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u2", "mom")

        preferences.clear()

        assertEquals("dad", preferences.getString("${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u1"))
        assertEquals("mom", preferences.getString("${PreferenceKeys.PARENT_SLOT_MARKER_PREFIX}u2"))
    }

    /** Not encryption — just enough of a transform that clear text on disk would show. */
    private object ReversingCipher : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String =
            Base64.getEncoder().encodeToString(plain.reversed().toByteArray())

        override fun open(sealed: String): String = String(Base64.getDecoder().decode(sealed)).reversed()
    }

    private object RefusingToOpen : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String = ReversingCipher.seal(plain)
        override fun open(sealed: String): String = throw IllegalStateException("key invalidated")
    }

    private object RefusingToSeal : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String = throw IllegalStateException("no Keystore")
        override fun open(sealed: String): String = throw IllegalStateException("no Keystore")
    }
}
