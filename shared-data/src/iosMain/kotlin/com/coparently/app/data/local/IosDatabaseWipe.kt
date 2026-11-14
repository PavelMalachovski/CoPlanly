package com.coparently.app.data.local

import androidx.room.Transactor
import androidx.room.useWriterConnection

/**
 * Deletes every row of every table Room made, in one write transaction: Room's `clearAllTables()`,
 * which Android's account switch and account deletion call, is Android-only in Room 2.7's
 * multiplatform API. The tables are read from `sqlite_master` rather than listed, so a table a later
 * schema adds is cleared too; Room's own `room_master_table` (the schema's identity) and SQLite's
 * internal tables are kept. The schema has no foreign keys, so the order does not matter. Room's
 * invalidation tracker sees the writes, so an open flow of a table hears that its rows are gone.
 *
 * The iPhone's account switch (`IosAccountSwitch`) and account deletion (`IosAccountDeletion`) both
 * call it (MON-24 phase 3).
 */
internal suspend fun CoPlanlyDatabase.deleteEveryRow() {
    useWriterConnection { connection ->
        connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
            val tables = usePrepared(ROOM_TABLES) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
            tables.forEach { table -> usePrepared("DELETE FROM `$table`") { it.step() } }
        }
    }
}

/** Room's tables: every table but its own identity table and SQLite's and Android's. */
internal const val ROOM_TABLES = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' " +
    "ESCAPE '\\' AND name NOT IN ('room_master_table', 'android_metadata')"
