package com.coparently.app.domain.error

/**
 * Nothing yet: Kotlin/Native has no `SecurityException`, and the iPhone's own permission failures
 * reach the data layer as the SDKs' errors, which [ErrorHandler] reads as unknown until one is named.
 */
internal actual fun Throwable.isPermissionFailure(): Boolean = false
