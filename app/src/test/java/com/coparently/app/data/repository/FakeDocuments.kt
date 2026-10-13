package com.coparently.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk

/**
 * A Firestore document as the SDK reports it: [fields] by name, every other field absent.
 *
 * `get` answers whatever is stored, and `getString` throws on a value that is not a string, as the
 * SDK's does. That second half is the point: a co-parent writes their own profile, and a reader of
 * it has to survive a field of the wrong type — a mock answering `getString` with whatever it was
 * given would pass a reader that crashes on a real phone.
 */
internal fun fakeDocument(fields: Map<String, Any?>): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>(relaxed = true)
    every { snapshot.get(any<String>()) } answers { fields[firstArg()] }
    every { snapshot.getString(any()) } answers {
        val field = firstArg<String>()
        when (val value = fields[field]) {
            null, is String -> value as String?
            else -> error("Field '$field' is not a java.lang.String")
        }
    }
    return snapshot
}
