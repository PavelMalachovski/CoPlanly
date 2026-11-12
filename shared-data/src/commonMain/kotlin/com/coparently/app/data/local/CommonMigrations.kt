package com.coparently.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 46 → 47: a child's portrait (`child_info.photo`, a `RecordPhotoCodec` reference or null).
 *
 * **The first migration written in common code** (CLAUDE.md, "Things that are easy to get wrong"
 * item 1): against `SQLiteConnection`, never `SupportSQLiteDatabase`, so the iPhone's bundled
 * driver and Android's SQLCipher open helper (Room's compatibility mode, which hands it a
 * connection over the encrypted database — `ConnectionMigrationOnSqlCipherTest`) run the same
 * statement. A nullable column with no default: every child stored before it has no portrait.
 */
val MIGRATION_46_47: Migration = object : Migration(46, 47) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE child_info ADD COLUMN photo TEXT")
    }
}

/**
 * Every migration written in common code, from version 47 on, in order. Both builders add it:
 * Android's `coPlanlyDatabaseBuilder` after the legacy chain (`DatabaseMigrations`), and the
 * iPhone's `buildCoPlanlyDatabase` on its own — an iPhone database created at 46 by a build of
 * this branch's predecessor is brought up the same way.
 */
val COMMON_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_46_47)
