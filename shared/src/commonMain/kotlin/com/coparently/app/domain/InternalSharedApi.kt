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
 * Today: `UnansweredRows` and `ClocksAt.governing`, for `RequestInbox` (it reads the payments'
 * `SupportProposal`, still `java.time`) and `OtherFamiliesSignals` (data layer, phase 2); and, in
 * `:shared-data`, `BakalariJson`, for the school import's three parsers, which build the
 * `java.time` school domain.
 */
@RequiresOptIn(
    message = "Public only for a caller still in the app module (MON-24); not API for new callers.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalSharedApi
