package com.coparently.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for CoParently database.
 * Each migration handles schema changes between versions.
 */
object DatabaseMigrations {

    /**
     * Migration from version 5 to 6.
     * Adds indexes to events table for improved query performance.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create indexes for frequently queried columns
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_events_startDateTime ON events(startDateTime)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_events_parentOwner ON events(parentOwner)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_events_parentOwner_startDateTime ON events(parentOwner, startDateTime)"
            )
        }
    }

    /**
     * Migration from version 6 to 7.
     * Adds status field to messages table for message send status tracking.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add status column to messages table with default value 'SENT' for existing messages
            // SQLite doesn't support adding NOT NULL columns directly, so we:
            // 1. Add nullable column with DEFAULT
            // 2. Update all NULL values to 'SENT'
            // 3. Since Room expects NOT NULL, we need to ensure all values are set
            database.execSQL(
                "ALTER TABLE messages ADD COLUMN status TEXT DEFAULT 'SENT'"
            )
            // Ensure all existing messages have status set
            database.execSQL(
                "UPDATE messages SET status = 'SENT' WHERE status IS NULL"
            )
        }
    }

    /**
     * Migration from version 7 to 8.
     * Creates custody_models table for advanced custody pattern configuration.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custody_models (
                    id TEXT PRIMARY KEY NOT NULL,
                    modelType TEXT NOT NULL,
                    patternDays INTEGER NOT NULL,
                    momDaysPattern TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    repeatYearly INTEGER NOT NULL DEFAULT 1,
                    createdAt TEXT NOT NULL,
                    lastModifiedAt TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * List of all migrations in order.
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )
}
