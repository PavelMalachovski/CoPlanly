package com.coparently.app.upgrade

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.domain.telemetry.TelemetryConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first half of "install the new build over the previous one" (DEVICE-CHECKLIST §2.1): runs
 * inside the **base** build's app process and leaves behind the state a parent's phone would
 * hold — an encrypted database with rows in the tables a family's history lives in, the sealed
 * preference store with a Google refresh token and the telemetry answer, and the passphrase that
 * opens the database.
 *
 * The CI `upgrade` job (`tools/upgrade/run-upgrade-test.sh`) installs the base build's APK and
 * this branch's test APK, runs this class with `-e coplanlyUpgradePhase seed`, installs this
 * branch's APK over it with `adb install -r` — which keeps the app's data, as Play does — and
 * then runs [UpgradeVerifyTest]. Without the argument the test skips itself, so the ordinary
 * `instrumented` job never writes the real database.
 *
 * **Everything here runs against the base build's classes**, not this branch's; the calls it may
 * make, and how to adapt when a pull request changes one of them, are listed on
 * [UpgradeFixture]. Rows go in by SQL through the production open helper, never through a DAO.
 *
 * The seed does not create the database by launching the app: a first launch opens it through
 * the same builder, lazily, on the first DAO injection, so opening it here is what a first launch
 * does to the file. What the app's own first-launch code does beyond that — the splash, the
 * consent screen, a sync — is not the upgrade and is not reproduced.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeSeedTest {

    /** Writes the fixture through the base build and records what the verify must find again. */
    @Test
    fun seed_writesAFamilysStateThroughTheBaseBuild() {
        UpgradeFixture.assumePhase(UpgradeFixture.PHASE_SEED)
        val context = UpgradeFixture.context()
        val databaseFile = UpgradeFixture.databaseFile(context)
        assertFalse(
            "The seed writes the real database and must start from a fresh install; " +
                "${databaseFile.path} already exists",
            databaseFile.exists()
        )

        val baseVersion = UpgradeFixture.withProductionDatabase(context) { database ->
            val writable = database.openHelper.writableDatabase
            UpgradeRows.ALL.forEach { row ->
                val dropped = writable.insertAdaptively(row.table, row.values)
                if (dropped.isNotEmpty()) {
                    Log.i(TAG, "${row.table}: the base build has no column for $dropped")
                }
            }
            UpgradeRows.ALL.forEach { row ->
                val count = writable.query("SELECT count(*) FROM `${row.table}` WHERE id = ?", arrayOf(row.id))
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                assertEquals("The base build did not keep ${row.table}/${row.id}", 1, count)
            }
            writable.version
        }
        assertTrue("The database file was not written", databaseFile.exists())

        writePreferences(context)
        writeMarker(context, baseVersion)
    }

    private fun writePreferences(context: Context) {
        val preferences = UpgradeFixture.preferences(context)
        preferences.putRefreshToken(UpgradeRows.REFRESH_TOKEN)
        preferences.putDefaultCurrency(UpgradeRows.DEFAULT_CURRENCY)
        preferences.putBoolean(PreferenceKeys.CHAT_PAUSE_BEFORE_SENDING, true)
        // The answer to the consent screen: an upgrade that loses it asks the question again.
        preferences.putString(PreferenceKeys.TELEMETRY_CONSENT, TelemetryConsent.DENIED.stored)
    }

    /**
     * Leaves the facts the verify compares against in a plain preference file of this check's
     * own. Only a SHA-256 of the passphrase is kept, and only on the job's throwaway emulator.
     */
    private fun writeMarker(context: Context, baseVersion: Int) {
        val wrapped = UpgradeFixture.keyStore(context)
            .getString(UpgradeFixture.KEY_WRAPPED_PASSPHRASE, null)
        assertNotNull("The base build stored no database passphrase", wrapped)
        val passphrase = UpgradeFixture.databaseKey(context).recover()
        assertNotNull("The base build's passphrase could not be recovered", passphrase)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val baseEncrypted = !UpgradeFixture.hasSqliteHeader(UpgradeFixture.databaseFile(context))

        val written = UpgradeFixture.marker(context).edit()
            .putBoolean(UpgradeFixture.MARKER_SEEDED, true)
            .putInt(UpgradeFixture.MARKER_BASE_VERSION, baseVersion)
            .putBoolean(UpgradeFixture.MARKER_BASE_ENCRYPTED, baseEncrypted)
            .putString(UpgradeFixture.MARKER_WRAPPED_PASSPHRASE, wrapped)
            .putString(UpgradeFixture.MARKER_PASSPHRASE_SHA256, UpgradeFixture.sha256(checkNotNull(passphrase)))
            .putLong(UpgradeFixture.MARKER_INSTALL_TIME, packageInfo.firstInstallTime)
            .putLong(UpgradeFixture.MARKER_UPDATE_TIME, packageInfo.lastUpdateTime)
            .commit()
        assertTrue("The seed marker could not be written", written)
        Log.i(TAG, "Seeded schema v$baseVersion (encrypted at rest: $baseEncrypted)")
    }

    private companion object {
        const val TAG = "UpgradeCheck"
    }
}
