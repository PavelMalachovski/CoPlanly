package com.coparently.app.data.local.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.security.EncryptionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * SEC-5 on a device: the old `EncryptedSharedPreferences` store is read once through the real
 * library and the real Keystore, its contents land in the new sealed file, and the old file is
 * gone — the step every existing install takes on its first launch of this build.
 *
 * Runs under file and store names of its own, never the app's: `SignedInSession` and the Hilt UI
 * tests read the real store, and a test that rewrote it would change what they see. Not a Hilt
 * test, so it also runs on the 16 KB-page leg, where the Keystore path is a different image.
 *
 * What it cannot prove is a store an *older build* wrote on a phone's hardware-backed Keystore;
 * that is `docs/DEVICE-CHECKLIST.md`'s sign-in check after the update.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val legacyName = "sec5-legacy-$id"
    private val fileName = "sec5-$id.bin"
    private val sealedFile = File(context.noBackupFilesDir, fileName)

    @After
    fun tearDown() {
        context.deleteSharedPreferences(legacyName)
        sealedFile.delete()
    }

    @Test
    fun theOldStoreIsCopiedIntoTheSealedFileAndThenDeleted() {
        legacyStore().edit()
            .putString("refresh_token", "refresh-secret-legacy")
            .putLong("token_expiry", 1_750_000_000_000L)
            .putString("parent_slot_marker_u1", "dad")
            .putBoolean("push_enabled", false)
            .commit()

        val migrated = open()

        assertEquals("refresh-secret-legacy", migrated.getRefreshToken())
        assertEquals(1_750_000_000_000L, migrated.getTokenExpiry())
        assertEquals("dad", migrated.getString("parent_slot_marker_u1"))
        assertFalse(migrated.getBoolean("push_enabled", true))

        assertTrue("the old store is still there", plain(legacyName).all.isEmpty())
        assertTrue(sealedFile.exists())
        val onDisk = sealedFile.readText()
        assertFalse("the token is on disk in clear text", onDisk.contains("refresh-secret-legacy"))
        assertFalse("a key name is on disk in clear text", onDisk.contains("refresh_token"))

        // A second launch reads the sealed file alone.
        val reopened = open()
        assertEquals("refresh-secret-legacy", reopened.getRefreshToken())
    }

    @Test
    fun aFreshInstallStartsEmptyAndKeepsWhatItIsGiven() {
        val fresh = open()
        assertNull(fresh.getRefreshToken())

        fresh.putRefreshToken("refresh-secret-new")
        fresh.putChatDraft("a_b", "Vyzvednu ji v 17:00")

        val reopened = open()
        assertEquals("refresh-secret-new", reopened.getRefreshToken())
        assertEquals("Vyzvednu ji v 17:00", reopened.getChatDraft("a_b"))
        assertFalse(sealedFile.readText().contains("refresh-secret-new"))
    }

    private fun open() = EncryptedPreferences(
        context = context,
        cipher = EncryptedPreferences.KeystoreCipher(EncryptionManager(context)),
        fileName = fileName,
        legacyName = legacyName
    )

    /** The store as the builds before SEC-5 wrote it. */
    private fun legacyStore() = EncryptedSharedPreferences.create(
        context,
        legacyName,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun plain(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
}
