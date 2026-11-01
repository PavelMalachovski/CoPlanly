package com.coparently.app.data.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Each client waits as the library it replaced waited (MON-24 fix round): the school import's
 * OkHttp client was built with 20-second connect, read and write timeouts and a 40-second
 * `callTimeout` (`SchoolModule`, `d935524c`); google-http-client 1.42.3's `HttpRequest` has a
 * 20-second connect and read timeout, no write timeout and no limit on the call, and the Calendar
 * import never changed them.
 */
class HttpTimeoutsTest {

    @Test
    fun `the school client waits as its OkHttp client did`() {
        assertEquals(20_000L, HttpTimeouts.SCHOOL.connectMillis)
        assertEquals(20_000L, HttpTimeouts.SCHOOL.socketMillis)
        assertEquals(40_000L, HttpTimeouts.SCHOOL.callMillis)
    }

    @Test
    fun `the Calendar client waits as google-http-client did - with no limit on the call`() {
        assertEquals(20_000L, HttpTimeouts.GOOGLE_CALENDAR.connectMillis)
        assertEquals(20_000L, HttpTimeouts.GOOGLE_CALENDAR.socketMillis)
        assertNull(HttpTimeouts.GOOGLE_CALENDAR.callMillis)
    }
}
