package com.coparently.app.presentation.event

import androidx.lifecycle.ViewModel
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.custody.CustodyResolver
import kotlinx.datetime.LocalDate

/**
 * Whose day a date is, for the event form's owner (21 October 2026, Zuzana's test: the
 * "Assigned to" choice is gone and a new event takes the day's parent — see the event form's
 * `autoEventOwner`).
 *
 * Its own ViewModel rather than one more dependency of [EventViewModel], which every calendar
 * surface shares: only the form asks this, once per start date.
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 10), where the day is a kotlinx-datetime date; the form,
 * still on `java.time`, converts its start date at the call.
 */
class EventOwnerViewModel(
    private val custodyModelRepository: CustodyModelRepository
) : ViewModel() {

    /**
     * The slot the family on screen's schedule gives [date] to, an accepted swap first, or null
     * when no schedule answers. Read from this phone's stored schedule, as the Today widget reads
     * it; the calendar draws the same answer through the same [CustodyResolver].
     */
    suspend fun custodyParentOn(date: LocalDate): String? {
        val (model, swaps) = custodyModelRepository.storedCustody()
        return CustodyResolver.custodyFor(model, swaps, legacy = { null }, date = date)
    }
}
