package com.coparently.app.domain.error

/**
 * Whether [this] is the platform's refusal of a permission: `java.lang.SecurityException` on
 * Android, which [ErrorHandler] has always classified as an [AppError.PermissionError] (MON-24
 * phase 2, Task 8f). Common Kotlin has no such type, so each platform names its own.
 */
internal expect fun Throwable.isPermissionFailure(): Boolean
