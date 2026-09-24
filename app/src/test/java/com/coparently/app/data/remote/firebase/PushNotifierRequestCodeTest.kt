package com.coparently.app.data.remote.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A notification's PendingIntent is identified by its request code and intent, never by extras,
 * so [PushNotifier.requestCode] is what keeps two families' taps apart (M-8). The code a push
 * with no family gets is the one it has always had, so a notification already in the tray from
 * an older build keeps its tap target.
 */
class PushNotifierRequestCodeTest {

    @Test
    fun `a push naming no family keeps the type-only code`() {
        assertEquals(PushPayload.EVENT_CREATED.hashCode(), PushNotifier.requestCode(PushPayload.EVENT_CREATED, null))
        assertEquals(0, PushNotifier.requestCode(null, null))
    }

    @Test
    fun `two families of one type get two codes`() {
        assertNotEquals(
            PushNotifier.requestCode(PushPayload.EVENT_CREATED, "a__b"),
            PushNotifier.requestCode(PushPayload.EVENT_CREATED, "a__c")
        )
        assertEquals(
            listOf(PushPayload.EVENT_CREATED, "a__b").hashCode(),
            PushNotifier.requestCode(PushPayload.EVENT_CREATED, "a__b")
        )
    }
}
