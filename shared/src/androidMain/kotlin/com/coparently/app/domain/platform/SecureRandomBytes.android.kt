package com.coparently.app.domain.platform

import java.security.SecureRandom

/** One generator for the process: `SecureRandom` is thread-safe, and seeding it is the costly part. */
private val secureRandom: SecureRandom by lazy { SecureRandom() }

/** [size] bytes from `SecureRandom`, the source `InviteCodeGenerator` draws from today. */
actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must not be negative: $size" }
    return ByteArray(size).also { secureRandom.nextBytes(it) }
}
