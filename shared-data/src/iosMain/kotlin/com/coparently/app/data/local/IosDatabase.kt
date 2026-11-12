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
import kotlin.concurrent.Volatile

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
    DatabaseFileProtection.prepareForBuild(folder, path)
    return Room.databaseBuilder<CoPlanlyDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        // The migrations written in common code, from 47 on — never the legacy chain.
        .addMigrations(*COMMON_MIGRATIONS)
        .setQueryCoroutineContext(Dispatchers.IO)
        // Room's default for a driver, said out loud: the `-wal` and `-shm` files are part of the
        // database, and what [DatabaseFileProtection] protects.
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    DatabaseFileProtection.protectOnOpen(path)
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
 *
 * **Each step reports what it did** ([prepare]'s result, [protect]'s map, and the last of each the
 * builder ran, [lastPrepared] and [lastOpened]), so a test can see that every
 * `setAttributes(NSFileProtectionComplete)` succeeded. That is what can be checked everywhere: the
 * simulator accepts the attribute but reports no protection class when it is read back, so only a
 * device shows that the class stuck (`IosDatabaseTest`, ROADMAP MON-24's phase-3 list).
 */
internal object DatabaseFileProtection {

    /** The suffixes of the files SQLite keeps beside the database in WAL mode. */
    val SIBLING_SUFFIXES = listOf("", "-wal", "-shm")

    /** What the builder's [prepare] answered for the database it last built; null before any. */
    @Volatile
    var lastPrepared: Boolean? = null
        private set

    /** What [protect] answered when Room last opened a database: each file and whether it took. */
    @Volatile
    var lastOpened: Map<String, Boolean> = emptyMap()
        private set

    /** Records [prepare]'s answer for the builder. */
    fun prepareForBuild(folder: String, path: String) {
        lastPrepared = prepare(folder, path)
    }

    /** Records [protect]'s answer for Room's open callback. */
    fun protectOnOpen(path: String) {
        lastOpened = protect(path)
    }

    /**
     * Creates [folder] and an empty database file at [path], both protected, if missing.
     *
     * @return whether the folder and the file exist and every protection attribute was set.
     */
    fun prepare(folder: String, path: String): Boolean {
        val files = NSFileManager.defaultManager
        val created = files.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = COMPLETE,
            error = null
        )
        if (!created) logger.w(TAG, "Could not create the database folder")
        val folderProtected = files.setAttributes(COMPLETE, ofItemAtPath = folder, error = null)
        if (!folderProtected) logger.w(TAG, "Could not protect the database folder")
        val fileReady = files.fileExistsAtPath(path) ||
            files.createFileAtPath(path, contents = null, attributes = COMPLETE)
        if (!fileReady) logger.w(TAG, "Could not create the database file")
        val fileStates = protect(path)
        return created && folderProtected && fileReady && fileStates[path] == true && fileStates.values.all { it }
    }

    /**
     * Gives the database at [path], and each sibling that exists, `NSFileProtectionComplete`.
     *
     * @return each file that exists, by path, and whether its attribute was set.
     */
    fun protect(path: String): Map<String, Boolean> {
        val files = NSFileManager.defaultManager
        return SIBLING_SUFFIXES.map { path + it }.filter { files.fileExistsAtPath(it) }.associateWith { file ->
            files.setAttributes(COMPLETE, ofItemAtPath = file, error = null).also { set ->
                if (!set) logger.w(TAG, "Could not protect ${file.substringAfterLast('/')}")
            }
        }
    }

    private val COMPLETE: Map<Any?, Any?> = mapOf(NSFileProtectionKey to NSFileProtectionComplete)

    private const val TAG = "IosDatabase"
}

/** The folder under [AppDirectories.noBackup] that holds the database, as Android's `databases/`. */
private const val DATABASE_FOLDER = "databases"
