package com.coparently.app.data.local.security

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.di.buildCoPlanlyDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Runs the SEC-2 open path — SQLCipher, the Keystore-wrapped passphrase and the plaintext
 * conversion — on a real device or emulator (CQ-1's instrumented job).
 *
 * Until this existed the conversion of an existing plaintext database had run nowhere: the unit
 * suite covers [SqlCipherMigration.next] as a pure function, and the Hilt UI tests only ever open
 * a database that starts empty. Here every state that function names is built on disk and handed
 * to the production open path, [buildCoPlanlyDatabase] with the Hilt-provided [EncryptedDatabase]
 * and [DatabaseKey], so what is proven is the code the app runs rather than a copy of it.
 *
 * **Hermetic by construction.** The database lives under a name of its own ([DB_NAME]), never
 * `coparently_database`, and every file it could leave is deleted before and after each test.
 * The passphrase store cannot be renamed — [DatabaseKey] owns one fixed preferences file, which
 * the UI tests' real database also depends on — so the stored value is snapshotted before each
 * test and written back after it. A test that forgets or re-mints the passphrase therefore never
 * leaves the next test class holding a database it cannot open.
 *
 * What this cannot prove is stated in `docs/DEVICE-CHECKLIST.md` §2.1: the emulator's Keystore is
 * software-backed, and no database here was written by an *older build* of the app. The first
 * upgrade on a phone that holds real data stays an acceptance step.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var encryptedDatabase: EncryptedDatabase

    @Inject
    lateinit var databaseKey: DatabaseKey

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val keyStore = context.getSharedPreferences(KEY_STORE_NAME, Context.MODE_PRIVATE)

    private val databaseFile: File = context.getDatabasePath(DB_NAME)

    private val exportFile: File =
        File(databaseFile.parentFile, DB_NAME + SqlCipherMigration.EXPORT_SUFFIX)

    private var storedPassphrase: String? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        storedPassphrase = keyStore.getString(KEY_WRAPPED_PASSPHRASE, null)
        deleteTestFiles()
        databaseFile.parentFile?.mkdirs()
    }

    @After
    fun tearDown() {
        deleteTestFiles()
        val editor = keyStore.edit()
        val restoring = storedPassphrase
        if (restoring == null) {
            editor.remove(KEY_WRAPPED_PASSPHRASE)
        } else {
            editor.putString(KEY_WRAPPED_PASSPHRASE, restoring)
        }
        assertTrue("Could not restore the stored database passphrase", editor.commit())
    }

    /** A fresh install: nothing on disk, nothing stored. The file written must be ciphertext. */
    @Test
    fun freshInstall_writesAnEncryptedFileThatReopensWithItsRows() {
        forgetPassphrase()

        withProductionDatabase { insertRows(it) }

        assertEncryptedAtRest()
        val minted = databaseKey.recover()
        assertNotNull("The minted passphrase was not persisted", minted)
        assertEquals(ROW_IDS, withProductionDatabase { readRowIds(it) })
        assertEquals("A reopen must recover the passphrase, not mint one", minted, databaseKey.recover())
    }

    /**
     * The upgrade SEC-2 exists for: a plaintext database written by Room at the current schema is
     * converted in place, every row and the schema version survive, and nothing is left beside it.
     */
    @Test
    fun plaintextDatabase_isEncryptedInPlaceKeepingRowsAndVersion() {
        val passphrase = databaseKey.recover() ?: databaseKey.mint()
        val version = createPlaintextDatabase()
        assertTrue("Precondition: the database starts as plaintext", hasSqliteHeader(databaseFile))

        val (rows, reopenedVersion) = withProductionDatabase {
            readRowIds(it) to it.openHelper.readableDatabase.version
        }

        assertEquals(ROW_IDS, rows)
        assertEquals("user_version must survive the export", version, reopenedVersion)
        assertEncryptedAtRest()
        assertNoExportLeft()
        assertEquals("A recovered passphrase must be reused", passphrase, databaseKey.recover())
    }

    /** A plaintext database is readable without any passphrase, so a lost one is simply replaced. */
    @Test
    fun plaintextDatabase_withNoStoredPassphrase_isEncryptedUnderAFreshOne() {
        createPlaintextDatabase()
        forgetPassphrase()

        assertEquals(ROW_IDS, withProductionDatabase { readRowIds(it) })

        assertNotNull(databaseKey.recover())
        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /** An export beside a plaintext original is unverified by construction and is thrown away. */
    @Test
    fun plaintextDatabase_withAStaleExportBesideIt_isEncryptedAndTheExportDiscarded() {
        createPlaintextDatabase()
        exportFile.writeBytes(GARBAGE)
        sidecar(exportFile, JOURNAL).writeBytes(GARBAGE)

        assertEquals(ROW_IDS, withProductionDatabase { readRowIds(it) })

        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /**
     * Killed between deleting the verified original and renaming the export in: the export is
     * the only copy of the family's data, and the next open must finish the swap.
     */
    @Test
    fun exportWithoutOriginal_isSwappedIntoPlace() {
        withProductionDatabase { insertRows(it) }
        assertTrue(databaseFile.renameTo(exportFile))
        sidecar(exportFile, WAL).writeBytes(GARBAGE)

        assertEquals(ROW_IDS, withProductionDatabase { readRowIds(it) })

        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /** Killed after the swap but with an export left over: the database wins, the leftover goes. */
    @Test
    fun encryptedDatabase_withALeftoverExport_keepsItsRowsAndDropsTheLeftover() {
        withProductionDatabase { insertRows(it) }
        exportFile.writeBytes(GARBAGE)

        assertEquals(ROW_IDS, withProductionDatabase { readRowIds(it) })

        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /**
     * No passphrase opens the encrypted file any more. The documented outcome is an empty
     * encrypted database rather than a crash loop — the rows are lost, which this pins so the
     * loss can never become silent in some *other* state.
     */
    @Test
    fun encryptedDatabase_withItsPassphraseLost_startsEmptyAndEncrypted() {
        withProductionDatabase { insertRows(it) }
        forgetPassphrase()

        assertEquals(emptyList<String>(), withProductionDatabase { readRowIds(it) })

        assertNotNull(databaseKey.recover())
        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /** An export no passphrase can open is discarded with it, not swapped in to fail later. */
    @Test
    fun exportWithoutOriginal_andPassphraseLost_isDiscarded() {
        withProductionDatabase { insertRows(it) }
        assertTrue(databaseFile.renameTo(exportFile))
        forgetPassphrase()

        assertEquals(emptyList<String>(), withProductionDatabase { readRowIds(it) })

        assertEncryptedAtRest()
        assertNoExportLeft()
    }

    /**
     * The passphrase is stable: two recoveries, and a second [DatabaseKey] built the way Hilt
     * builds one, all answer the same value. And a freshly minted one is on disk the moment
     * `mint` returns — the `commit`, not `apply`, that `DatabaseKey` insists on.
     */
    @Test
    fun passphrase_isStableAcrossRecoveries_andOnDiskAsSoonAsMinted() {
        forgetPassphrase()
        val minted = databaseKey.mint()
        val wrapped = requireNotNull(keyStore.getString(KEY_WRAPPED_PASSPHRASE, null))
        val onDisk = File(context.applicationInfo.dataDir, "shared_prefs/$KEY_STORE_NAME.xml")
        assertTrue(
            "mint() returned before the wrapped passphrase reached disk",
            onDisk.readText().contains(wrapped.take(WRAPPED_PREFIX_LENGTH))
        )

        assertTrue("Expected 64 hex characters", PASSPHRASE_SHAPE.matches(minted))
        assertEquals(minted, databaseKey.recover())
        assertEquals(minted, databaseKey.recover())
        assertEquals(minted, DatabaseKey(context, EncryptionManager(context)).recover())
        assertNotEquals("Two mints must not repeat a passphrase", minted, databaseKey.mint())
    }

    // --- helpers ------------------------------------------------------------------------------

    private inline fun <T> withProductionDatabase(block: (CoPlanlyDatabase) -> T): T {
        val database = buildCoPlanlyDatabase(context, encryptedDatabase, DB_NAME)
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    /**
     * Writes [ROW_IDS] into a plaintext database at the current schema — Room with its default
     * framework open helper, which is what every build before SEC-2 wrote — and returns its
     * `user_version`.
     */
    private fun createPlaintextDatabase(): Int {
        val database = Room.databaseBuilder(context, CoPlanlyDatabase::class.java, DB_NAME).build()
        try {
            insertRows(database)
            return database.openHelper.readableDatabase.version
        } finally {
            database.close()
        }
    }

    private fun insertRows(database: CoPlanlyDatabase) {
        val writable = database.openHelper.writableDatabase
        ROW_IDS.forEachIndexed { index, id ->
            writable.execSQL(
                "INSERT INTO custody_schedules (id, parentOwner, dayOfWeek, isActive) " +
                    "VALUES (?, 'mom', ?, 1)",
                arrayOf<Any?>(id, index + 1)
            )
        }
    }

    private fun readRowIds(database: CoPlanlyDatabase): List<String> =
        database.openHelper.readableDatabase
            .query("SELECT id FROM custody_schedules ORDER BY id")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

    private fun forgetPassphrase() {
        assertTrue(keyStore.edit().remove(KEY_WRAPPED_PASSPHRASE).commit())
    }

    /**
     * The file on disk is not a SQLite file anyone can read: its header is not the plaintext
     * magic, and the platform's own SQLite refuses it. Checked independently of
     * [SqlCipherMigration.looksLikePlaintext], which is part of the code under test.
     */
    private fun assertEncryptedAtRest() {
        assertTrue("The database file is missing", databaseFile.exists())
        assertFalse("The database file is plaintext SQLite", hasSqliteHeader(databaseFile))
        assertThrows(SQLiteException::class.java) {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                KeepFile
            ).use { plain ->
                plain.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
        assertTrue("Probing the file must not delete it", databaseFile.exists())
    }

    private fun assertNoExportLeft() {
        val left = listOf(exportFile) + SIDECARS.map { sidecar(exportFile, it) }
        left.forEach { assertFalse("Left behind: ${it.name}", it.exists()) }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_MAGIC)
    }

    private fun sidecar(file: File, suffix: String) = File(file.parentFile, file.name + suffix)

    private fun deleteTestFiles() {
        listOf(databaseFile, exportFile).forEach { file ->
            file.delete()
            SIDECARS.forEach { sidecar(file, it).delete() }
        }
    }

    /** The framework's default handler deletes a file it cannot parse; this probe must not. */
    private object KeepFile : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase) = Unit
    }

    companion object {
        /** Never the production name: see the class KDoc. */
        private const val DB_NAME = "sec2_instrumented_test_database"

        /** `DatabaseKey`'s own preferences file and key; a rename there should fail this test. */
        private const val KEY_STORE_NAME = "database_key"
        private const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"

        private const val WAL = "-wal"
        private const val JOURNAL = "-journal"
        private val SIDECARS = listOf(WAL, "-shm", JOURNAL)

        private val ROW_IDS = listOf("row-a", "row-b", "row-c")

        private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** Not a database of any kind: a half-written export as a kill would leave it. */
        private val GARBAGE = ByteArray(4096) { (it * 31).toByte() }

        private val PASSPHRASE_SHAPE = Regex("[0-9a-f]{64}")

        /** Enough of the Base64 value to be unambiguous, and short of its first line break. */
        private const val WRAPPED_PREFIX_LENGTH = 40
    }
}
