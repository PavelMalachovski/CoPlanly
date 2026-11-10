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
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iPhone's database as `buildCoPlanlyDatabase` opens it (MON-24 phase 2): a fresh file on the
 * bundled SQLite driver keeps a row in every table, and the file and its `-wal` and `-shm`
 * siblings are given `NSFileProtectionComplete` (iPhone design §9.1, the owner's answer to
 * SQLCipher).
 *
 * **What runs where.** Everywhere, the simulator included: the three files exist, and every
 * `setAttributes(NSFileProtectionComplete)` the builder made — when it prepared the file, when Room
 * opened it, and once more here — succeeded ([DatabaseFileProtection] reports each). Only on a
 * device: reading the protection class back, because the simulator accepts the attribute and then
 * reports none (`NSFileProtectionKey` is null there, which made this test red on the hosted macOS
 * job). That read-back runs in phase 3, when the iPhone app's test host runs this on a real phone
 * (ROADMAP MON-24, "Before phase 3"), beside the check that a locked phone cannot open the file.
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
    fun `every protection step on the database and its wal and shm files succeeds`() {
        val database = buildCoPlanlyDatabase(directories)
        try {
            assertEquals(true, DatabaseFileProtection.lastPrepared, "protecting the folder and the new file")
            // Rows written and read: Room has opened the database, which is when the protection is
            // applied to every file that exists, and SQLite has created the -wal and -shm files.
            runBlocking { EveryTableRoundTrip.assertEveryTableRoundTrips(database) }
            val opened = DatabaseFileProtection.lastOpened
            assertEquals(true, opened[databasePath], "protecting the file on open")
            assertTrue(opened.values.all { it }, "on open: $opened")
            val files = DatabaseFileProtection.SIBLING_SUFFIXES.map { databasePath + it }
            files.forEach { file ->
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(file), "$file exists")
            }
            assertEquals(files.associateWith { true }, DatabaseFileProtection.protect(databasePath))
        } finally {
            database.close()
        }
    }

    @Test
    fun `on a device the database and its wal and shm files read back as complete protection`() {
        if (onSimulator()) {
            // Not a pass: the simulator cannot answer this (see the class KDoc). The step above
            // still checked every attribute was set; a device answers this in phase 3.
            println("IosDatabaseTest: the protection class is read back on a device only")
            return
        }
        val database = buildCoPlanlyDatabase(directories)
        try {
            runBlocking { EveryTableRoundTrip.assertEveryTableRoundTrips(database) }
            DatabaseFileProtection.SIBLING_SUFFIXES.forEach { suffix ->
                val file = databasePath + suffix
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

    /**
     * Whether this runs on the simulator, which sets `SIMULATOR_UDID` and `SIMULATOR_DEVICE_NAME` in
     * every process it starts. Should neither be set there, the device-only test runs and fails,
     * which is visible, rather than passing on a simulator by mistake.
     */
    private fun onSimulator(): Boolean {
        val environment = NSProcessInfo.processInfo.environment
        return environment["SIMULATOR_UDID"] != null || environment["SIMULATOR_DEVICE_NAME"] != null
    }
}
