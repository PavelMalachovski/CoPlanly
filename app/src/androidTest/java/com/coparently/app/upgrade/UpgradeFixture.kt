package com.coparently.app.upgrade

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.security.DatabaseKey
import com.coparently.app.data.local.security.EncryptedDatabase
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.di.buildCoPlanlyDatabase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.mockk
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest

/**
 * What the two halves of the upgrade check share: the phase switch, the production open path, the
 * marker the seed leaves for the verify, and the on-disk facts both read.
 *
 * **Every call into the app below runs against two different builds.** The test APK is compiled
 * against this branch, but in the seed phase the app process has the *base* build's classes
 * loaded — `tools/upgrade/run-upgrade-test.sh` installs that APK — so each constructor and method
 * named here must exist, with this signature, in the build the pull request is based on. The
 * surface is kept to the handful of calls the production open path itself needs:
 *
 *  * [buildCoPlanlyDatabase]`(Context, EncryptedDatabase, String)`;
 *  * `EncryptedDatabase(Context, DatabaseKey, CrashlyticsManager)`, `DatabaseKey(Context,
 *    EncryptionManager)`, `DatabaseKey.recover()`, `EncryptionManager(Context)` and
 *    `CrashlyticsManager(FirebaseCrashlytics)`;
 *  * `EncryptedPreferences(Context, EncryptionManager)` and its `putRefreshToken`, `putString`,
 *    `putDefaultCurrency` and `putBoolean`;
 *  * `RoomDatabase.openHelper` and the `androidx.sqlite` interface, which are the library's.
 *
 * Rows are written with SQL through that open helper rather than through the DAOs on purpose:
 * a DAO takes an entity, and an entity's constructor changes whenever a column is added — which
 * is exactly the pull request this check exists for, and it would fail with `NoSuchMethodError`
 * before reaching the database. See [insertAdaptively].
 *
 * **When a pull request changes one of the signatures above**, the seed phase fails with
 * `NoSuchMethodError` or `NoClassDefFoundError` naming it, because the base build does not have
 * the new shape yet. Adapt the seed rather than the production code: reach the old shape by
 * reflection for that one release, or — for anything in the row-writing path — use raw SQL over
 * the file. Once the change has merged the base has it and the adapter can go.
 *
 * **This writes the app's real database and preference store**, under their production names,
 * because the point is the upgrade of the real files. That is safe only on a device that exists
 * for this check: the seed refuses to run over an existing database, and nothing here runs
 * without the [PHASE_ARGUMENT] instrumentation argument (see [assumePhase]).
 */
internal object UpgradeFixture {

    /** The instrumentation argument that switches these tests on: `seed` or `verify`. */
    const val PHASE_ARGUMENT = "coplanlyUpgradePhase"

    const val PHASE_SEED = "seed"
    const val PHASE_VERIFY = "verify"

    /** `DatabaseModule.DATABASE_NAME`: the file that exists on real devices. */
    const val DATABASE_NAME = "coparently_database"

    /** `DatabaseKey`'s own preference file and key; renaming either should fail this check. */
    const val KEY_STORE_NAME = "database_key"
    const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"

    /** `EncryptedPreferences.STORE_FILE`, under `noBackupFilesDir`, and the pre-SEC-5 store. */
    const val SEALED_PREFERENCES_FILE = "secure_prefs.bin"
    const val LEGACY_PREFERENCES_NAME = "encrypted_prefs"

    /** Where Room exports the schema; `app/schemas` is an `androidTest` asset directory. */
    const val SCHEMA_ASSET_DIR = "com.coparently.app.data.local.CoPlanlyDatabase"

    /** A plain preference file of this check's own: what the seed leaves for the verify. */
    private const val MARKER_STORE = "coplanly_upgrade_check"
    const val MARKER_SEEDED = "seeded"
    const val MARKER_BASE_VERSION = "base_schema_version"
    const val MARKER_BASE_ENCRYPTED = "base_file_encrypted"
    const val MARKER_WRAPPED_PASSPHRASE = "wrapped_passphrase"
    const val MARKER_PASSPHRASE_SHA256 = "passphrase_sha256"
    const val MARKER_INSTALL_TIME = "first_install_time"
    const val MARKER_UPDATE_TIME = "last_update_time"

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** Skips the calling test unless this run was started for [phase]. */
    fun assumePhase(phase: String) {
        val requested = InstrumentationRegistry.getArguments().getString(PHASE_ARGUMENT)
        assumeTrue(
            "No -e $PHASE_ARGUMENT $phase: the upgrade check runs only in its own CI job",
            requested == phase
        )
    }

    /** The app's own context — the process under test, whichever build it runs. */
    fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    /** The test APK's own context, which carries the exported schemas as assets. */
    fun testContext(): Context = InstrumentationRegistry.getInstrumentation().context

    /** The production database file. */
    fun databaseFile(context: Context): File = context.getDatabasePath(DATABASE_NAME)

    /** The marker store. */
    fun marker(context: Context): SharedPreferences =
        context.getSharedPreferences(MARKER_STORE, Context.MODE_PRIVATE)

    /** The passphrase store `DatabaseKey` owns, read without unwrapping. */
    fun keyStore(context: Context): SharedPreferences =
        context.getSharedPreferences(KEY_STORE_NAME, Context.MODE_PRIVATE)

    /** A [DatabaseKey] built the way Hilt builds one. */
    fun databaseKey(context: Context): DatabaseKey = DatabaseKey(context, EncryptionManager(context))

    /** The production preference store, built the way Hilt builds it. */
    fun preferences(context: Context): EncryptedPreferences =
        EncryptedPreferences(context, EncryptionManager(context))

    /**
     * Opens the real database through [buildCoPlanlyDatabase] — the builder `DatabaseModule`
     * calls — hands it to [block] and closes it.
     *
     * Crashlytics is a relaxed mock: `EncryptedDatabase` only reports through it on a failure
     * path, and a debug build without `google-services.json` has no Firebase app to get one from.
     */
    fun <T> withProductionDatabase(context: Context, block: (CoPlanlyDatabase) -> T): T {
        val crashlytics = CrashlyticsManager(mockk<FirebaseCrashlytics>(relaxed = true))
        val encryptedDatabase = EncryptedDatabase(context, databaseKey(context), crashlytics)
        val database = buildCoPlanlyDatabase(context, encryptedDatabase, DATABASE_NAME)
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    /** Whether [file] starts with the plaintext SQLite header. */
    fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_MAGIC)
    }

    /** Lower-case hex SHA-256 of [text]. Only a digest of the passphrase is ever kept. */
    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * Inserts one row into [table], adapting to the columns the open database actually has.
 *
 * The seed runs against the base build's schema, which a pull request that changes the schema
 * does not match. So [values] names the columns the fixture cares about; a value whose column
 * the table does not have is dropped (the pull request added it), and a `NOT NULL` column
 * without a default that [values] does not name gets a neutral value of its affinity (the pull
 * request removed it). Neither happens while the two builds agree.
 *
 * @return the names of the [values] that were dropped, for the log.
 */
internal fun SupportSQLiteDatabase.insertAdaptively(table: String, values: Map<String, Any?>): List<String> {
    val columns = columnsOf(table)
    check(columns.isNotEmpty()) { "The table $table does not exist in this build's database" }
    val row = LinkedHashMap<String, Any?>()
    columns.forEach { column ->
        when {
            values.containsKey(column.name) -> row[column.name] = values[column.name]
            column.notNull && !column.hasDefault -> row[column.name] = neutralValue(column.type)
        }
    }
    val names = row.keys.joinToString(", ") { "`$it`" }
    val slots = row.keys.joinToString(", ") { "?" }
    execSQL("INSERT INTO `$table` ($names) VALUES ($slots)", row.values.toTypedArray())
    return values.keys.filterNot { key -> columns.any { it.name == key } }
}

private fun SupportSQLiteDatabase.columnsOf(table: String): List<Column> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        val type = cursor.getColumnIndexOrThrow("type")
        val notNull = cursor.getColumnIndexOrThrow("notnull")
        val default = cursor.getColumnIndexOrThrow("dflt_value")
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Column(
                        name = cursor.getString(name),
                        type = cursor.getString(type).orEmpty().uppercase(),
                        notNull = cursor.getInt(notNull) != 0,
                        hasDefault = !cursor.isNull(default)
                    )
                )
            }
        }
    }

private fun neutralValue(type: String): Any = when {
    type.contains("INT") -> 0L
    type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> 0.0
    type.contains("BLOB") -> ByteArray(0)
    else -> ""
}

/** One row of `PRAGMA table_info`. */
private data class Column(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val hasDefault: Boolean
)
