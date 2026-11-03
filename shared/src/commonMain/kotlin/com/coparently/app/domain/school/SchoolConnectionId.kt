package com.coparently.app.domain.school

import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import com.coparently.app.domain.text.Utf8

/**
 * The id a school connection is stored under on the phone (MON-8): the first 16 lower-case hex
 * digits of the SHA-256 of `uid|baseUrl|username`.
 *
 * Its key in the sealed preference store is `school_connection_` and this id, so it must never
 * move: the bytes hashed are the JVM's `toByteArray(UTF_8)` ([Utf8.encode], an unpaired surrogate
 * as `?`) on every platform, which `SchoolImportParityTest` holds to the `MessageDigest` code that
 * computed it before MON-24.
 */
object SchoolConnectionId {

    private const val LENGTH = 16

    /** The id of [username]'s connection to [baseUrl] on the account [uid]. */
    fun of(uid: String, baseUrl: String, username: String): String =
        Sha256.digest(Utf8.encode("$uid|$baseUrl|$username")).toHex().take(LENGTH)
}
