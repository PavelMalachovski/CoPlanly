@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.logger
import com.coparently.app.di.DATABASE_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey

/**
 * Opens the app's database on the iPhone (MON-24 phase 2): the same [CoPlanlyDatabase], entities
 * and schema as Android, on Room's bundled SQLite driver.
 *
 * **Encrypted by iOS, not by SQLCipher** (owner decision, iPhone design §9.1). The file and its
 * `-wal` and `-shm` siblings carry Data Protection's `NSFileProtectionComplete`, so the system
 * encrypts them with a key it discards while the phone is locked. SQLCipher stays Android's
 * (SEC-2). The cost, accepted with the decision: nothing on the iPhone can read the database while
 * the phone is locked, so a background sync waits for the next unlock.
 *
 * The file lives in [AppDirectories.noBackup], which is excluded from iCloud and computer
 * backups, as Android excludes its database from cloud backup and device transfer: the private
 * journal (CLAUDE.md item 34) never leaves the phone, a backup included.
 *
 * Always created at the current version: an iPhone never held an older schema, so the legacy
 * migration chain (`DatabaseMigrations`, Android's) is not added. A migration from version 47 on is
 * written in common code against `SQLiteConnection` and added on both platforms.
 *
 * @param directories Where the app keeps its files; a test passes a temporary folder.
 */
fun buildCoPlanlyDatabase(directories: AppDirectories): CoPlanlyDatabase {
    val folder = Path(directories.noBackup, DATABASE_FOLDER).toString()
    val path = Path(folder, DATABASE_NAME).toString()
    DatabaseFileProtection.prepare(folder, path)
    return Room.databaseBuilder<CoPlanlyDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Room's default for a driver, said out loud: the `-wal` and `-shm` files are part of the
        // database, and what [DatabaseFileProtection] protects.
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    DatabaseFileProtection.protect(path)
                }
            }
        )
        .build()
}

/**
 * `NSFileProtectionComplete` on the database's folder and on the three files SQLite keeps there.
 *
 * Three steps, because SQLite creates the files itself and may delete and re-create the `-wal`
 * and `-shm` ones when the last connection closes: the folder carries the class, which a file
 * created in it inherits; the database file is created with it before SQLite first opens it; and
 * each file found when Room opens the database is given it again.
 */
internal object DatabaseFileProtection {

    /** The suffixes of the files SQLite keeps beside the database in WAL mode. */
    val SIBLING_SUFFIXES = listOf("", "-wal", "-shm")

    /** Creates [folder] and an empty database file at [path], both protected, if missing. */
    fun prepare(folder: String, path: String) {
        val files = NSFileManager.defaultManager
        val created = files.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = COMPLETE,
            error = null
        )
        if (!created) logger.w(TAG, "Could not create the database folder")
        if (!files.setAttributes(COMPLETE, ofItemAtPath = folder, error = null)) {
            logger.w(TAG, "Could not protect the database folder")
        }
        if (!files.fileExistsAtPath(path) && !files.createFileAtPath(path, contents = null, attributes = COMPLETE)) {
            logger.w(TAG, "Could not create the database file")
        }
        protect(path)
    }

    /** Gives the database at [path], and each sibling that exists, `NSFileProtectionComplete`. */
    fun protect(path: String) {
        val files = NSFileManager.defaultManager
        SIBLING_SUFFIXES.map { path + it }.filter { files.fileExistsAtPath(it) }.forEach { file ->
            if (!files.setAttributes(COMPLETE, ofItemAtPath = file, error = null)) {
                logger.w(TAG, "Could not protect ${file.substringAfterLast('/')}")
            }
        }
    }

    private val COMPLETE: Map<Any?, Any?> = mapOf(NSFileProtectionKey to NSFileProtectionComplete)

    private const val TAG = "IosDatabase"
}

/** The folder under [AppDirectories.noBackup] that holds the database, as Android's `databases/`. */
private const val DATABASE_FOLDER = "databases"
