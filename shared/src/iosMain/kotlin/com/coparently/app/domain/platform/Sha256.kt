package com.coparently.app.domain.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update

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

    /**
     * Every run [feed] hands over, through one `CC_SHA256_CTX` that lives on [memScoped]'s arena,
     * so it is freed however [feed] ends.
     */
    actual fun digestOf(feed: (update: (bytes: ByteArray, offset: Int, length: Int) -> Unit) -> Unit): ByteArray =
        memScoped {
            val context = alloc<CC_SHA256_CTX>()
            CC_SHA256_Init(context.ptr)
            feed { bytes, offset, length ->
                // CommonCrypto reads whatever the pointer reaches, so a run outside the array is
                // refused here, as Android's `MessageDigest.update` refuses it.
                require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
                    "Run $offset+$length outside an array of ${bytes.size}"
                }
                // `addressOf` needs an index inside the array, which an empty run does not have.
                if (length > 0) {
                    bytes.usePinned { pinned ->
                        CC_SHA256_Update(context.ptr, pinned.addressOf(offset), length.toUInt())
                    }
                }
            }
            val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
            digest.usePinned { pinned -> CC_SHA256_Final(pinned.addressOf(0).reinterpret<UByteVar>(), context.ptr) }
            digest
        }
}
