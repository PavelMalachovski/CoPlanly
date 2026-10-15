package com.coparently.app.presentation.changerequests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.utils.localizedDate
import java.time.LocalDate

/**
 * The dates a day swap covers, as the inbox and Home's feed name them: the day itself, or the
 * first day and how many more ("12 Oct and 2 more days").
 *
 * @param firstDate The offer's first day.
 * @param dayCount How many days it covers; one or more.
 */
@Composable
internal fun swapDatesText(firstDate: LocalDate, dayCount: Int): String {
    val first = firstDate.format(localizedDate("MMMd"))
    return if (dayCount <= 1) {
        first
    } else {
        pluralStringResource(R.plurals.request_swap_first_and_more, dayCount - 1, first, dayCount - 1)
    }
}

/** What a request was about, the line a history row leads with. */
@Composable
internal fun subjectTitle(subject: RequestSubject): String = when (subject) {
    is RequestSubject.NewTime -> stringResource(R.string.request_history_new_time, subject.eventTitle)
    is RequestSubject.DaySwap ->
        stringResource(R.string.request_history_day_swap, swapDatesText(subject.firstDate, subject.dayCount))
    is RequestSubject.NewEvent -> subject.eventTitle
    RequestSubject.CustodySchedule -> stringResource(R.string.request_history_custody)
}
