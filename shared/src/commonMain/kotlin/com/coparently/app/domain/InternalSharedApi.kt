package com.coparently.app.domain

/**
 * Marks a declaration that is public only because a caller still in the app module needs it
 * (MON-24, phase 1).
 *
 * Kotlin's `internal` stops at the module, and the domain moves to `:shared` a piece at a time, so
 * a helper that belongs to the domain can have a caller left behind in `:app` for a while. Such a
 * helper is public, which also puts it in the iPhone framework's headers; this marker makes every
 * use outside the declaration ask for it with `@OptIn(InternalSharedApi::class)`, so a new caller
 * is a visible decision rather than an accident.
 *
 * Remove the marker, and make the declaration `internal` again, once its last `:app` caller moves.
 * The current list of marked declarations and the callers each waits for is kept in one place,
 * CLAUDE.md's paragraph on `@InternalSharedApi` (under "Where a `data/…` path in this file
 * lives"), so that it is not stale in two.
 */
@RequiresOptIn(
    message = "Public only for a caller still in the app module (MON-24); not API for new callers.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalSharedApi
