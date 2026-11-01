package com.coparently.app.domain.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/** SHA-256 through CommonCrypto's `CC_SHA256`, the standard digest Android's `MessageDigest` gives. */
@OptIn(ExperimentalForeignApi::class)
actual object Sha256 {

    /** The 32-byte SHA-256 digest of [bytes]. */
    actual fun digest(bytes: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        // `addressOf(0)` throws on an empty array, so empty input points at a byte it never reads.
        val input = if (bytes.isEmpty()) ByteArray(1) else bytes
        input.usePinned { pinnedInput ->
            digest.usePinned { pinnedDigest ->
                CC_SHA256(
                    pinnedInput.addressOf(0),
                    bytes.size.toUInt(),
                    pinnedDigest.addressOf(0).reinterpret<UByteVar>()
                )
            }
        }
        return digest
    }
}
