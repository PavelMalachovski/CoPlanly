package com.coparently.app.data.local

import androidx.room.migration.Migration

/**
 * The migrations written in common code against `SQLiteConnection`, which both platforms add to
 * their builder: every migration from version 47 on goes here (CLAUDE.md, "Things that are easy to
 * get wrong" item 1). Android's `coPlanlyDatabaseBuilder` adds it after `ALL_MIGRATIONS`, and
 * the iPhone's `buildCoPlanlyDatabase` adds it. Empty today: the schema is at 46.
 */
val COMMON_MIGRATIONS: Array<Migration> = emptyArray()
