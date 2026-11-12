package com.coparently.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.local.security.EncryptedDatabase
import com.coparently.app.di.buildCoPlanlyDatabase
import com.coparently.app.di.coPlanlyDatabaseBuilder
import com.coparently.app.testing.KoinTestRule
import com.coparently.app.testing.UsesFakeFirebase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The path every migration from version 47 on takes (MON-24 phase 2): a Room `Migration` written
 * in common code against `SQLiteConnection`, run by Room on Android over SQLCipher's open helper.
 *
 * Room for Kotlin Multiplatform keeps Android's `SupportSQLiteOpenHelper` as a compatibility mode,
 * which is how the app opens its encrypted file (`coPlanlyDatabaseBuilder`). In that mode a
 * migration is handed a connection that wraps the SQLCipher database, and the 41 legacy migrations
 * (`DatabaseMigrations`), which override `migrate(SupportSQLiteDatabase)`, are handed the database
 * itself. This test proves the first half on a device: the migration runs, its table lands in the
 * encrypted file, and the file is still ciphertext afterwards.
 *
 * **Why one version back rather than forward.** Room takes the version it opens at from
 * `@Database`, so opening one version ahead would need a second database class and Room's
 * processor in this module, which the move took away. Instead the encrypted file is written at the
 * current version through the production path, its `user_version` is stepped back one, and it is
 * reopened with a connection-only migration that replaces the real last step for this file (since
 * schema 47 that step is itself a common one, `MIGRATION_46_47`; Room keeps the migration added
 * last for a pair of versions). Room runs it through exactly the code the real step goes through:
 * the open helper sees an older version, Room finds the migration, hands it a connection,
 * validates the schema and records the new version. The real last step is
 * `CoPlanlyDatabaseMigrationTest`'s.
 *
 * Hermetic as `EncryptedDatabaseTest` is: a database name of its own, every file it could leave
 * deleted before and after, and `DatabaseKey`'s one fixed passphrase store snapshotted and
 * restored around the test.
 */
@UsesFakeFirebase
@RunWith(AndroidJUnit4::class)
class ConnectionMigrationOnSqlCipherTest {

    @get:Rule
    val koinRule = KoinTestRule()

    private lateinit var encryptedDatabase: EncryptedDatabase

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val keyStore = context.getSharedPreferences(KEY_STORE_NAME, Context.MODE_PRIVATE)

    private val databaseFile: File = context.getDatabasePath(DB_NAME)

    private var storedPassphrase: String? = null

    @Before
    fun setUp() {
        encryptedDatabase = koinRule.get()
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

    @Test
    fun aMigrationWrittenAgainstSQLiteConnection_runsOnTheEncryptedDatabase() {
        val current = buildCoPlanlyDatabase(context, encryptedDatabase, DB_NAME).let { database ->
            try {
                val writable = database.openHelper.writableDatabase
                val version = writable.version
                writable.version = version - 1
                version
            } finally {
                database.close()
            }
        }
        assertFalse("Precondition: the file is ciphertext", hasSqliteHeader(databaseFile))

        var handedTo: SQLiteConnection? = null
        val migration = object : Migration(current - 1, current) {
            override fun migrate(connection: SQLiteConnection) {
                handedTo = connection
                connection.execSQL("CREATE TABLE IF NOT EXISTS $PROBE_TABLE (id TEXT NOT NULL PRIMARY KEY)")
                connection.execSQL("INSERT INTO $PROBE_TABLE (id) VALUES ('$PROBE_ROW')")
            }
        }

        val migrated = coPlanlyDatabaseBuilder(context, encryptedDatabase, DB_NAME)
            .addMigrations(migration)
            .build()
        try {
            val writable = migrated.openHelper.writableDatabase
            assertTrue("Room did not run the connection migration", handedTo != null)
            assertEquals("The migration did not bring the file to the current version", current, writable.version)
            assertEquals(listOf(PROBE_ROW), probeRows(migrated))
        } finally {
            migrated.close()
        }

        assertFalse("The migrated file is plaintext SQLite", hasSqliteHeader(databaseFile))
        // Reopened through the production path alone, at the current version: the table the
        // migration wrote is in the encrypted file, and nothing runs again.
        val reopened = buildCoPlanlyDatabase(context, encryptedDatabase, DB_NAME)
        try {
            assertEquals(listOf(PROBE_ROW), probeRows(reopened))
        } finally {
            reopened.close()
        }
    }

    private fun probeRows(database: CoPlanlyDatabase): List<String> =
        database.openHelper.readableDatabase.query("SELECT id FROM $PROBE_TABLE").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_MAGIC)
    }

    private fun deleteTestFiles() {
        databaseFile.delete()
        SIDECARS.forEach { File(databaseFile.parentFile, databaseFile.name + it).delete() }
    }

    private companion object {
        const val DB_NAME = "connection_migration_test_database"

        /** `DatabaseKey`'s own preferences file and key, as `EncryptedDatabaseTest` names them. */
        const val KEY_STORE_NAME = "database_key"
        const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"

        const val PROBE_TABLE = "connection_migration_probe"
        const val PROBE_ROW = "probe"

        val SIDECARS = listOf("-wal", "-shm", "-journal")

        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
