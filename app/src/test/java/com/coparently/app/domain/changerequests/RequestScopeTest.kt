package com.coparently.app.domain.changerequests

import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which requests and events belong to the family on screen (phase 2, spec §6.3): a parent in two
 * families sees one family's requests at a time, and an ex-partner's in none.
 */
class RequestScopeTest {

    private val at = LocalDateTime.of(2026, 10, 14, 9, 0)
    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)

    private fun request(from: String, to: String) = ChangeRequest(
        id = "r-$from-$to",
        eventId = "e1",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = at,
        proposedStartDateTime = at.plusDays(1),
        createdAt = at
    )

    private fun event(creator: String?, familyId: String?) = Event(
        id = "e1",
        title = "Swimming",
        startDateTime = at,
        eventType = "other",
        parentOwner = "mom",
        createdAt = at,
        updatedAt = at,
        createdByFirebaseUid = creator,
        familyId = familyId
    )

    @Test
    fun `no scope without both parents`() {
        assertNull(RequestScope.of("", "u2", setOf("u2")))
        assertNull(RequestScope.of("u1", null, setOf("u2")))
        assertNull(RequestScope.of("u1", "u1", setOf("u1")))
    }

    @Test
    fun `a scope names the family and whether it is the only one`() {
        assertEquals(
            RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = true),
            RequestScope.of("u1", "u2", setOf("u2"))
        )
        assertEquals(false, RequestScope.of("u1", "u3", setOf("u2", "u3"))?.soleFamily)
    }

    @Test
    fun `a request is covered in either direction and only between the two`() {
        assertTrue(scope.covers(request(from = "u1", to = "u2")))
        assertTrue(scope.covers(request(from = "u2", to = "u1")))
        assertFalse(scope.covers(request(from = "u1", to = "u3")))
        assertFalse(scope.covers(request(from = "u3", to = "u1")))
    }

    @Test
    fun `an event of this family is covered and one of the other family is not`() {
        assertTrue(scope.covers(event(creator = "u2", familyId = "u1__u2")))
        assertFalse(scope.covers(event(creator = "u1", familyId = "u1__u3")))
    }

    @Test
    fun `an unstamped event is covered only while there is one co-parent`() {
        assertTrue(scope.copy(soleFamily = true).covers(event(creator = "u2", familyId = null)))
        assertTrue(scope.copy(soleFamily = true).covers(event(creator = "u1", familyId = "")))
        assertFalse(scope.covers(event(creator = "u2", familyId = null)))
    }

    @Test
    fun `an event created outside the pair is never covered`() {
        assertFalse(scope.covers(event(creator = "u9", familyId = "u1__u2")))
        assertFalse(scope.copy(soleFamily = true).covers(event(creator = null, familyId = null)))
    }
}
