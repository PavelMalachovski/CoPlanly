package com.coparently.app.data.requests

import com.coparently.app.data.notification.RequestClockWork
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [runRequestClockStep]: which step a request clock's job runs (phase 3a; MON-25).
 *
 * A reminder runs through [RequestClockSteps]. The deadline and the end are the server's since
 * MON-25, so a job an older build left queued for either finishes as done without touching the
 * steps; a step this build does not know is not run at all, and the worker fails the job.
 */
class RequestClockWorkerTest {

    private val steps = mockk<RequestClockSteps>()

    @Test
    fun `a reminder job runs the reminder step`() = runTest {
        coEvery { steps.reminder(CLOCK_ID, AT) } returns StepResult.RETRY

        assertEquals(StepResult.RETRY, runRequestClockStep(steps, RequestClockWork.STEP_REMINDER, CLOCK_ID, AT))

        coVerify(exactly = 1) { steps.reminder(CLOCK_ID, AT) }
    }

    @Test
    fun anOldDeadlineOrEndJobFinishesWithoutActing() = runTest {
        assertEquals(StepResult.DONE, runRequestClockStep(steps, RequestClockWork.STEP_DEADLINE, CLOCK_ID, AT))
        assertEquals(StepResult.DONE, runRequestClockStep(steps, RequestClockWork.STEP_END, CLOCK_ID, AT))

        confirmVerified(steps)
    }

    @Test
    fun `a step this build does not know is not run`() = runTest {
        assertNull(runRequestClockStep(steps, "from_a_newer_build", CLOCK_ID, AT))
        assertNull(runRequestClockStep(steps, null, CLOCK_ID, AT))

        confirmVerified(steps)
    }

    private companion object {
        const val CLOCK_ID = "change_uidA_cr-1"
        const val AT = 1_778_000_000_000L
    }
}
