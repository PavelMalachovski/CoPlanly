package com.coparently.app.upgrade

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.domain.telemetry.TelemetryConsent
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The second half of the upgrade check (see [UpgradeSeedTest]): runs inside **this branch's**
 * build, installed with `adb install -r` over the data the base build wrote, and asserts that
 * nothing a parent had is gone.
 *
 *  * The app was really replaced, not reinstalled: `lastUpdateTime` moved and `firstInstallTime`
 *    did not.
 *  * The database opens through the production builder — SQLCipher, the Keystore-wrapped
 *    passphrase, and every Room migration from the base build's schema version to this one.
 *  * The passphrase was **recovered, not re-minted**: its wrapped form on disk is byte-for-byte
 *    the one the base build stored, and it unwraps to the same value. A re-mint would also have
 *    discarded the database, which the row checks would see; this says why.
 *  * The file is still ciphertext at rest, and is at the newest schema this branch exports, with
 *    Room's identity hash matching it.
 *  * Every seeded row is there, read both by SQL and through this build's own DAOs and type
 *    converters — a migration that mangles a stored form fails here, not on a phone.
 *  * The sealed preference store (SEC-5) opens with the refresh token and settings in it, and the
 *    telemetry question stays answered, so the consent screen is not shown again.
 *
 * Skips itself without `-e coplanlyUpgradePhase verify`; the upgrade job fails on a skip.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeVerifyTest {

    private val context: Context = UpgradeFixture.context()

    /** Everything above, in the order a failure is easiest to read. */
    @Test
    fun verify_theNewBuildOpensWhatTheBaseBuildWrote() {
        UpgradeFixture.assumePhase(UpgradeFixture.PHASE_VERIFY)
        val marker = UpgradeFixture.marker(context)
        assertTrue(
            "No seed marker: the seed phase did not run, or the install did not keep the app's data",
            marker.getBoolean(UpgradeFixture.MARKER_SEEDED, false)
        )
        assertTheAppWasReplaced()
        assertPassphraseRecovered()

        val databaseFile = UpgradeFixture.databaseFile(context)
        val baseVersion = marker.getInt(UpgradeFixture.MARKER_BASE_VERSION, -1)
        val version = UpgradeFixture.withProductionDatabase(context) { database ->
            val readable = database.openHelper.readableDatabase
            assertSchemaIsCurrent(readable)
            assertRowsSurvived(readable)
            assertDaosReadTheRows(database)
            readable.version
        }
        assertTrue("The schema went backwards: v$baseVersion → v$version", version >= baseVersion)
        assertEncryptedAtRest(databaseFile)
        // Opening once more must recover the same passphrase again, not mint a second one.
        assertPassphraseRecovered()
        assertPreferencesSurvived()

        val encryptedBefore = marker.getBoolean(UpgradeFixture.MARKER_BASE_ENCRYPTED, true)
        Log.i(
            TAG,
            "Upgrade verified: schema v$baseVersion → v$version, " +
                (if (encryptedBefore) "encrypted → encrypted" else "plaintext → encrypted")
        )
    }

    private fun assertTheAppWasReplaced() {
        val marker = UpgradeFixture.marker(context)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(
            "firstInstallTime changed: the app was uninstalled between the phases, not upgraded",
            marker.getLong(UpgradeFixture.MARKER_INSTALL_TIME, -1),
            packageInfo.firstInstallTime
        )
        assertTrue(
            "lastUpdateTime did not move: this build was not installed over the seeded one",
            packageInfo.lastUpdateTime > marker.getLong(UpgradeFixture.MARKER_UPDATE_TIME, Long.MAX_VALUE)
        )
    }

    private fun assertPassphraseRecovered() {
        val marker = UpgradeFixture.marker(context)
        assertEquals(
            "The wrapped database passphrase changed: it was re-minted, and the base build's " +
                "database could no longer be opened",
            marker.getString(UpgradeFixture.MARKER_WRAPPED_PASSPHRASE, null),
            UpgradeFixture.keyStore(context).getString(UpgradeFixture.KEY_WRAPPED_PASSPHRASE, null)
        )
        val recovered = UpgradeFixture.databaseKey(context).recover()
        assertNotNull("The database passphrase could not be recovered after the upgrade", recovered)
        assertEquals(
            "The passphrase unwraps to a different value after the upgrade",
            marker.getString(UpgradeFixture.MARKER_PASSPHRASE_SHA256, null),
            UpgradeFixture.sha256(checkNotNull(recovered))
        )
    }

    /** The newest schema this branch exports (an `androidTest` asset), and Room's hash of it. */
    private fun assertSchemaIsCurrent(database: SupportSQLiteDatabase) {
        val assets = UpgradeFixture.testContext().assets
        val latest = assets.list(UpgradeFixture.SCHEMA_ASSET_DIR).orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .maxOrNull()
        assertNotNull("No exported Room schema in the test APK's assets", latest)
        assertEquals(
            "The upgraded database is not at the newest exported schema",
            latest,
            database.version
        )
        val exported = assets.open("${UpgradeFixture.SCHEMA_ASSET_DIR}/$latest.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database").getString("identityHash") }
        val stored = database.query("SELECT identity_hash FROM room_master_table WHERE id = 42")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        assertEquals("Room's identity hash does not match the exported schema v$latest", exported, stored)
    }

    private fun assertRowsSurvived(database: SupportSQLiteDatabase) {
        UpgradeRows.ALL.forEach { row ->
            val probe = database.query(
                "SELECT `${row.probeColumn}` FROM `${row.table}` WHERE id = ?",
                arrayOf(row.id)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            assertEquals("${row.table}/${row.id} did not survive the upgrade", row.probe, probe)
        }
    }

    /** Reads the rows through this build's DAOs, so its entities and converters parse them. */
    private fun assertDaosReadTheRows(database: CoPlanlyDatabase) = runBlocking {
        val event = database.eventDao().getEventById(UpgradeRows.EVENT_ID)
        assertEquals(UpgradeRows.EVENT_TITLE, event?.title)
        assertEquals(LocalDateTime.parse(UpgradeRows.EVENT_START), event?.startDateTime)
        val privateEvent = database.eventDao().getEventById(UpgradeRows.PRIVATE_EVENT_ID)
        assertEquals("The private event lost its flag", true, privateEvent?.isPrivate)

        val expense = database.expenseDao().getExpenseById(UpgradeRows.EXPENSE_ID)
        assertEquals(UpgradeRows.EXPENSE_AMOUNT, expense?.amount ?: Double.NaN, 0.0)
        assertEquals(LocalDate.parse(UpgradeRows.EXPENSE_DATE), expense?.date)

        assertEquals(
            UpgradeRows.CHILD_NAME,
            database.childInfoDao().getChildInfoById(UpgradeRows.CHILD_ID)?.childName
        )
        assertEquals(
            listOf(UpgradeRows.MESSAGE_TEXT),
            database.messageDao().getMessagesOnce(UpgradeRows.CONVERSATION_ID).map { it.content }
        )
        assertEquals(
            UpgradeRows.CUSTODY_CYCLE,
            database.custodyModelDao().getModelById(UpgradeRows.CUSTODY_ID)?.patternDays
        )
        assertEquals(UpgradeRows.USER_NAME, database.userDao().getUserById(UpgradeRows.USER_ID)?.name)
    }

    /**
     * Not a SQLite file anyone can read: no plaintext header, and the platform's own SQLite
     * refuses it. Independent of `SqlCipherMigration.looksLikePlaintext`, which is under test.
     */
    private fun assertEncryptedAtRest(file: File) {
        assertTrue("The database file is missing after the upgrade", file.exists())
        assertFalse("The database is plaintext SQLite after the upgrade", UpgradeFixture.hasSqliteHeader(file))
        assertThrows(SQLiteException::class.java) {
            SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                KeepFile
            ).use { plain ->
                plain.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
        assertTrue("Probing the file must not delete it", file.exists())
    }

    private fun assertPreferencesSurvived() {
        val sealed = File(context.noBackupFilesDir, UpgradeFixture.SEALED_PREFERENCES_FILE)
        assertTrue("The sealed preference store (SEC-5) is missing after the upgrade", sealed.exists())
        assertFalse(
            "The sealed preference store holds the refresh token in clear text",
            sealed.readText().contains(UpgradeRows.REFRESH_TOKEN)
        )
        val preferences = UpgradeFixture.preferences(context)
        assertEquals("The Google refresh token was lost", UpgradeRows.REFRESH_TOKEN, preferences.getRefreshToken())
        assertEquals(UpgradeRows.DEFAULT_CURRENCY, preferences.getDefaultCurrency())
        assertTrue(preferences.getBoolean(PreferenceKeys.CHAT_PAUSE_BEFORE_SENDING, false))
        assertEquals(
            "The telemetry answer was lost: the consent screen would ask again",
            TelemetryConsent.DENIED,
            TelemetryConsent.fromStored(preferences.getString(PreferenceKeys.TELEMETRY_CONSENT, null))
        )
        val legacy = File(context.applicationInfo.dataDir, "shared_prefs/${UpgradeFixture.LEGACY_PREFERENCES_NAME}.xml")
        assertFalse("The pre-SEC-5 preference store was written again", legacy.exists())
    }

    /** The framework's default handler deletes a file it cannot parse; this probe must not. */
    private object KeepFile : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase) = Unit
    }

    private companion object {
        const val TAG = "UpgradeCheck"
    }
}
