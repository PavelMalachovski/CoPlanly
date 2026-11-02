@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.local

import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.di.DATABASE_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iPhone's database as `buildCoPlanlyDatabase` opens it (MON-24 phase 2): a fresh file on the
 * bundled SQLite driver keeps a row in every table, and the file and its `-wal` and `-shm`
 * siblings carry `NSFileProtectionComplete` (iPhone design §9.1, the owner's answer to SQLCipher).
 *
 * It needs no Keychain, which a bare simulator test process does not have (see
 * `KeychainSecurePreferencesTest`): the database is protected by the file system, not by a key the
 * app holds. Each test works in a folder of its own under the temporary directory.
 */
class IosDatabaseTest {

    private val root = NSTemporaryDirectory() + "coplanly-database-" + NSUUID().UUIDString

    private val directories = object : AppDirectories {
        override val files: Path = Path(root, "files")
        override val cache: Path = Path(root, "cache")
        override val noBackup: Path = Path(root, "no_backup")
    }

    private val databasePath = Path(directories.noBackup, "databases", DATABASE_NAME).toString()

    @AfterTest
    fun removeTheFolder() {
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun `a fresh database keeps one row in every table`() {
        val database = buildCoPlanlyDatabase(directories)
        try {
            runBlocking { EveryTableRoundTrip.assertEveryTableRoundTrips(database) }
        } finally {
            database.close()
        }
    }

    @Test
    fun `the database and its wal and shm files are protected while the phone is locked`() {
        val database = buildCoPlanlyDatabase(directories)
        try {
            // Rows written and read: SQLite has created the -wal and -shm files by now, and Room
            // has opened the database, which is when the protection is applied to them.
            runBlocking { EveryTableRoundTrip.assertEveryTableRoundTrips(database) }
            DatabaseFileProtection.SIBLING_SUFFIXES.forEach { suffix ->
                val file = databasePath + suffix
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(file), "$file exists")
                val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(file, error = null)
                assertEquals<Any?>(
                    NSFileProtectionComplete,
                    attributes?.get(NSFileProtectionKey),
                    "the data protection class of ${file.substringAfterLast('/')}"
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `the database lives in the folder that is never backed up`() {
        val database = buildCoPlanlyDatabase(directories)
        try {
            runBlocking { database.knownFamilyDao().get("none") }
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(databasePath), databasePath)
        } finally {
            database.close()
        }
    }
}
