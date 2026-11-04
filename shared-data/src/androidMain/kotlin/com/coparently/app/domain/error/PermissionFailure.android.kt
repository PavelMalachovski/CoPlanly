package com.coparently.app.domain.error

/** A `java.lang.SecurityException`, as `ErrorHandler`'s `is SecurityException` read it. */
internal actual fun Throwable.isPermissionFailure(): Boolean = this is SecurityException
