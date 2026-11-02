package com.coparently.app.data.platform

/**
 * Where the data layer writes its diagnostic lines (MON-24 phase 2, Task 6).
 *
 * The four levels the data layer used `android.util.Log` at, each with the tag and the message it
 * always had: on Android [logger] hands them to `android.util.Log` unchanged, so logcat reads as
 * before; on the iPhone they go to the process's standard output, which Xcode's console shows.
 *
 * **What goes in a line is unchanged too**: ids, counts and outcomes — never what a parent typed,
 * a token or a child's details (CLAUDE.md, the Crashlytics rule).
 */
interface Logger {

    /** A failure the app recovered from or reported; [error] is printed with its stack. */
    fun e(tag: String, message: String, error: Throwable? = null)

    /** Something unexpected that did not stop the operation; [error] is printed with its stack. */
    fun w(tag: String, message: String, error: Throwable? = null)

    /** A step worth seeing in a device log: a sync's counts, a migration's outcome. */
    fun i(tag: String, message: String)

    /** Detail for a developer at the device. */
    fun d(tag: String, message: String)
}

/**
 * The process's [Logger]: `android.util.Log` on Android, standard output on the iPhone.
 *
 * **One ambient logger rather than a constructor parameter**, on purpose. Three classes that log
 * cannot take one: `EncryptedPreferences`, `DatabaseKey` and `EncryptedDatabase` are constructed by
 * the `upgrade` job's seed against the *previous* build's classes, so their constructors are
 * frozen (CLAUDE.md, the `upgrade` job). A pattern those three must break would not be one
 * pattern. And nothing about logging varies per instance or is asserted by a test: what a line
 * says is the call site's, where it goes is the platform's.
 */
val logger: Logger = platformLogger()

/** The platform's own log; see [logger]. */
internal expect fun platformLogger(): Logger
