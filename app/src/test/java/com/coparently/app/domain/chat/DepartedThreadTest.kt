package com.coparently.app.domain.chat

import com.coparently.app.data.chat.DepartedThreadSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DepartedThread.fromDocument] — reading the marks `deleteAccount` leaves on a conversation it
 * kept for the parent who remains (GDPR review, September 2026).
 *
 * What is pinned is who may see a banner: only a participant, never the departed account itself,
 * and only until the deadline, whatever the sweep has or has not done yet.
 */
class DepartedThreadTest {

    private fun document(
        participants: List<String> = listOf(ME, GONE),
        departedUid: Any? = GONE,
        until: Any? = NOW + DAY,
        name: Any? = "Dana"
    ): Map<String, Any?> = mapOf(
        "participants" to participants,
        DepartedThread.DEPARTED_UID to departedUid,
        DepartedThread.RETAINED_UNTIL to until,
        DepartedThread.DEPARTED_NAME to name
    )

    @Test
    fun `a kept thread reads with its deadline and the departed parent's name`() {
        assertEquals(
            DepartedThread(THREAD, GONE, "Dana", NOW + DAY),
            DepartedThread.fromDocument(THREAD, document(), ME, NOW)
        )
    }

    @Test
    fun `the deadline arrives as whatever number Firestore hands back`() {
        val read = DepartedThread.fromDocument(THREAD, document(until = (NOW + DAY).toDouble()), ME, NOW)
        assertEquals(NOW + DAY, read?.retainedUntilMillis)
    }

    @Test
    fun `an ordinary thread, a past deadline or a stranger read as nothing`() {
        assertNull(DepartedThread.fromDocument(THREAD, document(departedUid = null, until = null), ME, NOW))
        assertNull(DepartedThread.fromDocument(THREAD, document(until = NOW), ME, NOW))
        assertNull(DepartedThread.fromDocument(THREAD, document(until = "2026-10-25"), ME, NOW))
        assertNull(DepartedThread.fromDocument(THREAD, document(), "someone-else", NOW))
        assertNull(DepartedThread.fromDocument(THREAD, document(departedUid = ""), ME, NOW))
    }

    @Test
    fun `the departed account is never shown its own banner`() {
        assertNull(DepartedThread.fromDocument(THREAD, document(), GONE, NOW))
    }

    @Test
    fun `a missing name reads as blank, for the screen's own fallback`() {
        assertEquals("", DepartedThread.fromDocument(THREAD, document(name = null), ME, NOW)?.departedName)
    }

    @Test
    fun `the source keeps the readable ones, soonest deadline first`() {
        val later = document(until = NOW + 2 * DAY) + ("id" to "later")
        val sooner = document(until = NOW + DAY) + ("id" to "sooner")
        val ordinary = document(departedUid = null) + ("id" to "ordinary")

        assertEquals(
            listOf("sooner", "later"),
            DepartedThreadSource.parse(listOf(later, ordinary, sooner), ME, NOW).map { it.conversationId }
        )
    }

    private companion object {
        const val ME = "user-a"
        const val GONE = "user-d"
        const val THREAD = "user-a__user-d"
        const val NOW = 1_790_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
