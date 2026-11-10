package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.domain.friends.FriendCalendarRead
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a failed read of the family's events tells a calendar friend (phase 2, spec §4.4): the
 * rules refusing her means her access ended — revoked, or lapsed — and anything else is a failure
 * she can retry. The failure arrives as its Firestore status, `FirestoreClient.errorCodeOf`'s
 * answer (MON-24 phase 2, Task 8d).
 */
class FriendCalendarReadTest {

    @Test
    fun `a refused read means access ended`() {
        assertEquals(
            FriendCalendarRead.AccessEnded,
            friendCalendarReadFor(FirestoreErrorCode.PERMISSION_DENIED)
        )
    }

    @Test
    fun `an unavailable server is a failure to retry`() {
        assertEquals(
            FriendCalendarRead.Failed,
            friendCalendarReadFor(FirestoreErrorCode.UNAVAILABLE)
        )
    }

    @Test
    fun `an error that is not Firestore's is a failure`() {
        // `errorCodeOf` answers null for a failure that is not Firestore's, a socket closing say.
        assertEquals(FriendCalendarRead.Failed, friendCalendarReadFor(null))
    }
}
