@file:JvmName("DatabaseModuleKt")

package com.coparently.app.di

import android.content.Context
import androidx.room.Room
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.security.EncryptedDatabase

/** The database file name. Fixed forever: it names a file that exists on real devices. */
const val DATABASE_NAME = "coparently_database"

/** Schema versions older than the start of the migration chain (5->6). */
private val PRE_MIGRATION_CHAIN_SCHEMAS = intArrayOf(1, 2, 3, 4)

/**
 * Builds the Room database the way the app opens it: through [EncryptedDatabase], with the whole
 * migration chain and the pre-chain wipe.
 *
 * A function of its own, outside the Koin module, so that `EncryptedDatabaseTest` can open a
 * database under a name of its own through *this* path rather than through a copy of it — a copy
 * would prove the copy. Production has exactly one caller, `databaseModule`, and one name,
 * [DATABASE_NAME].
 *
 * The file keeps the JVM class name it had as `DatabaseModule.kt` (`@file:JvmName`): the `upgrade`
 * job runs this branch's test APK against the base build's classes, and `UpgradeFixture` calls
 * this function there, so its owner class must not move.
 *
 * @param name The database file name, resolved under the app's database directory.
 */
fun buildCoPlanlyDatabase(
    context: Context,
    encryptedDatabase: EncryptedDatabase,
    name: String
): CoPlanlyDatabase {
    return Room.databaseBuilder(context, CoPlanlyDatabase::class.java, name)
        .apply {
            encryptedDatabase.openHelperFactory(name)?.let { openHelperFactory(it) }
        }
        .addMigrations(*com.coparently.app.data.local.DatabaseMigrations.ALL_MIGRATIONS)
        // The migration chain starts at 5->6 and is complete up to the current version;
        // installs older than schema v5 have no upgrade path (a v3 install crashed with
        // "migration from 3 to 9 required but not found" during the 2026-07 review), so
        // wipe ONLY those pre-chain schemas explicitly.
        //
        // Deliberately NOT using the blanket fallbackToDestructiveMigration()/
        // ...OnDowngrade(): with real family data on the device, a missing migration or a
        // downgrade must fail loudly (and be fixed with a proper migration) rather than
        // silently wiping the user's calendar, expenses and messages.
        .fallbackToDestructiveMigrationFrom(
            dropAllTables = true,
            *PRE_MIGRATION_CHAIN_SCHEMAS
        )
        .build()
}
