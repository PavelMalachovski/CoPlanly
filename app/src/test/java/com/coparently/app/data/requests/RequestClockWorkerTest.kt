package com.coparently.app.data.requests

import com.coparently.app.data.notification.RequestClockWork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [finishesWithoutActing]: what a request clock's job does on this build (phase 3a; MON-25; iPhone
 * phase 4 part C).
 *
 * Every step is the server's now, so a reminder, a deadline or an end job an older build left
 * queued finishes as done without acting; a step this build does not know is not run at all, and
 * the worker fails the job.
 */
class RequestClockWorkerTest {

    @Test
    fun anOldReminderJobFinishesWithoutActing() {
        assertTrue(finishesWithoutActing(RequestClockWork.STEP_REMINDER))
    }

    @Test
    fun anOldDeadlineOrEndJobFinishesWithoutActing() {
        assertTrue(finishesWithoutActing(RequestClockWork.STEP_DEADLINE))
        assertTrue(finishesWithoutActing(RequestClockWork.STEP_END))
    }

    @Test
    fun `a step this build does not know is not run`() {
        assertFalse(finishesWithoutActing("from_a_newer_build"))
        assertFalse(finishesWithoutActing(null))
    }
}
