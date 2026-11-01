package com.coparently.app.domain.platform

import java.security.MessageDigest

/** SHA-256 through `MessageDigest`, the call the app's domain made before it moved here. */
actual object Sha256 {

    /** The 32-byte SHA-256 digest of [bytes]. */
    actual fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
