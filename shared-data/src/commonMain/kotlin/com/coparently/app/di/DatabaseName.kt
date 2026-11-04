package com.coparently.app.di

/**
 * The database file name, on both platforms. Fixed forever: it names a file that exists on real
 * devices (Android's `databases/coparently_database`, which the `upgrade` job opens under this
 * name, and the iPhone's, in `IosDatabase.kt`).
 */
const val DATABASE_NAME = "coparently_database"
