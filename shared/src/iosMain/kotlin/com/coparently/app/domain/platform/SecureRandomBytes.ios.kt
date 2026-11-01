package com.coparently.app.domain.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** `errSecSuccess`: the status `SecRandomCopyBytes` returns when it filled the buffer. */
private const val SEC_SUCCESS = 0

/**
 * [size] bytes from `SecRandomCopyBytes`, the system's cryptographically secure generator.
 *
 * Throws when the system refuses: a pairing code must never fall back to a weaker source.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must not be negative: $size" }
    val bytes = ByteArray(size)
    // `addressOf(0)` throws on an empty array, and there is nothing to fill.
    if (size == 0) return bytes
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    check(status == SEC_SUCCESS) { "SecRandomCopyBytes failed with status $status" }
    return bytes
}
