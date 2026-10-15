package com.coparently.app.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [RequestClockWork.uniqueName]: the name WorkManager keeps one job of a request's clock under.
 *
 * The jobs are enqueued with `ExistingWorkPolicy.KEEP`, so a name decides what counts as "the same
 * job". A name too coarse would let an old job swallow a new one; one too fine would run a step
 * twice. The scheduler itself needs WorkManager and is covered on a device; the naming is all that
 * can go wrong on the JVM.
 */
class WorkRequestClockSchedulerTest {

    private val clockId = "swap_uidA_g-1"

    @Test
    fun `each job has its own unique name, so a moved end does not replace the old one`() {
        // A last chance moves the end. The new end is another job; the old one stays and, when it
        // fires early, finds the end still ahead and does nothing. A name without the time would
        // make KEEP drop the new job instead.
        assertNotEquals(
            RequestClockWork.uniqueName(RequestClockWork.STEP_END, clockId, 1_000L),
            RequestClockWork.uniqueName(RequestClockWork.STEP_END, clockId, 2_000L)
        )
        assertNotEquals(
            RequestClockWork.uniqueName(RequestClockWork.STEP_REMINDER, clockId, 1_000L),
            RequestClockWork.uniqueName(RequestClockWork.STEP_REMINDER, clockId, 2_000L)
        )

        // The steps of one clock never share a name, even at the same moment.
        val sameMoment = listOf(
            RequestClockWork.STEP_REMINDER,
            RequestClockWork.STEP_DEADLINE,
            RequestClockWork.STEP_END
        ).map { RequestClockWork.uniqueName(it, clockId, 1_000L) }
        assertEquals(sameMoment.size, sameMoment.toSet().size)

        // Nor do two clocks.
        assertNotEquals(
            RequestClockWork.uniqueName(RequestClockWork.STEP_END, "swap_uidA_g-1", 1_000L),
            RequestClockWork.uniqueName(RequestClockWork.STEP_END, "swap_uidA_g-2", 1_000L)
        )
    }

    @Test
    fun `the names are the ones WorkManager already holds for a clock`() {
        // Persisted by WorkManager across app updates, so a rename would orphan a pending job.
        assertEquals(
            "request_reminder_swap_uidA_g-1_1000",
            RequestClockWork.uniqueName(RequestClockWork.STEP_REMINDER, clockId, 1_000L)
        )
        assertEquals(
            "request_end_swap_uidA_g-1_1000",
            RequestClockWork.uniqueName(RequestClockWork.STEP_END, clockId, 1_000L)
        )
        assertEquals(
            "the deadline never moves, so one job per clock",
            "request_deadline_swap_uidA_g-1",
            RequestClockWork.uniqueName(RequestClockWork.STEP_DEADLINE, clockId, 1_000L)
        )
        assertEquals(
            RequestClockWork.uniqueName(RequestClockWork.STEP_DEADLINE, clockId, 1_000L),
            RequestClockWork.uniqueName(RequestClockWork.STEP_DEADLINE, clockId, 2_000L)
        )
    }

    @Test
    fun `the tag and the input keys are the ones the jobs are cancelled and read by`() {
        assertEquals("request_clock", RequestClockWork.TAG)
        assertEquals("clock_id", RequestClockWork.KEY_CLOCK_ID)
        assertEquals("step", RequestClockWork.KEY_STEP)
        assertEquals("at_millis", RequestClockWork.KEY_AT_MILLIS)
        assertEquals("reminder", RequestClockWork.STEP_REMINDER)
        assertEquals("deadline", RequestClockWork.STEP_DEADLINE)
        assertEquals("end", RequestClockWork.STEP_END)
    }
}
