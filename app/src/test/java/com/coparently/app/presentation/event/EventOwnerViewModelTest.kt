package com.coparently.app.presentation.event

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A new event's owner is the day's parent (21 October 2026): what the family's schedule says,
 * with an accepted swap first, and the parent's own slot when nothing answers.
 */
class EventOwnerViewModelTest {

    /** Week on, week off from a Monday: slot 1 has the first seven days. */
    private val model = CustodyModel(
        id = "m1",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 10, 19)
    )
    private val repository = mockk<CustodyModelRepository>()

    @Test
    fun `the day's parent comes from the schedule`() = runTest {
        coEvery { repository.storedCustody() } returns (model to emptyMap())
        val viewModel = EventOwnerViewModel(repository)

        assertEquals("mom", viewModel.custodyParentOn(LocalDate.of(2026, 10, 21)))
        assertEquals("dad", viewModel.custodyParentOn(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun `an accepted swap gives the day to the other parent`() = runTest {
        val swapped = DayOverride(
            toParent = "dad",
            requestedBy = "uid-mom",
            requestedAt = "2026-10-20T10:00:00",
            status = DayOverrideStatus.ACCEPTED,
            decidedBy = "uid-dad",
            decidedAt = "2026-10-20T11:00:00"
        )
        coEvery { repository.storedCustody() } returns (model to mapOf("2026-10-21" to swapped))

        assertEquals("dad", EventOwnerViewModel(repository).custodyParentOn(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun `without a schedule nothing answers, and the form keeps the parent's own slot`() = runTest {
        coEvery { repository.storedCustody() } returns (null to emptyMap())

        val custody = EventOwnerViewModel(repository).custodyParentOn(LocalDate.of(2026, 10, 21))

        assertNull(custody)
        assertEquals("dad", autoEventOwner(custody, mySlot = "dad"))
        assertEquals("mom", autoEventOwner("mom", mySlot = "dad"))
    }
}
