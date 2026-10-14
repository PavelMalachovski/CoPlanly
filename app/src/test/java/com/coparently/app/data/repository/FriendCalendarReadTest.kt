package com.coparently.app.data.repository

import com.coparently.app.domain.friends.FriendCalendarRead
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

/**
 * What a failed read of the family's events tells a calendar friend (phase 2, spec §4.4): the
 * rules refusing her means her access ended — revoked, or lapsed — and anything else is a failure
 * she can retry.
 */
class FriendCalendarReadTest {

    @Test
    fun `a refused read means access ended`() {
        assertEquals(
            FriendCalendarRead.AccessEnded,
            friendCalendarReadFor(
                FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
        )
    }

    @Test
    fun `an unavailable server is a failure to retry`() {
        assertEquals(
            FriendCalendarRead.Failed,
            friendCalendarReadFor(
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            )
        )
    }

    @Test
    fun `an error that is not Firestore's is a failure`() {
        assertEquals(FriendCalendarRead.Failed, friendCalendarReadFor(IOException("socket closed")))
    }
}
