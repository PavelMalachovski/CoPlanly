package com.coparently.app.presentation.changerequests

import androidx.compose.runtime.Composable
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.resources.Res
import com.coparently.app.resources.request_history_custody
import com.coparently.app.resources.request_history_day_swap
import com.coparently.app.resources.request_history_new_time
import com.coparently.app.resources.request_history_split
import com.coparently.app.resources.request_swap_first_and_more
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The dates a day swap covers, as the inbox and Home's feed name them: the day itself, or the
 * first day and how many more ("12 Oct and 2 more days").
 *
 * @param firstDate The offer's first day.
 * @param dayCount How many days it covers; one or more.
 */
@Composable
fun swapDatesText(firstDate: LocalDate, dayCount: Int): String {
    val first = ReaderDateText.date(firstDate, "MMMd")
    return if (dayCount <= 1) {
        first
    } else {
        pluralStringResource(Res.plurals.request_swap_first_and_more, dayCount - 1, first, dayCount - 1)
    }
}

/** What a request was about, the line a history row leads with. */
@Composable
internal fun subjectTitle(subject: RequestSubject): String = when (subject) {
    is RequestSubject.NewTime -> stringResource(Res.string.request_history_new_time, subject.eventTitle)
    is RequestSubject.DaySwap ->
        stringResource(Res.string.request_history_day_swap, swapDatesText(subject.firstDate, subject.dayCount))
    is RequestSubject.NewEvent -> subject.eventTitle
    RequestSubject.CustodySchedule -> stringResource(Res.string.request_history_custody)
    RequestSubject.SplitRatio -> stringResource(Res.string.request_history_split)
}
