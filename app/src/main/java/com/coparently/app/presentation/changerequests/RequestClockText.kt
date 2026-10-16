package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.presentation.common.UiText
import com.coparently.app.utils.answerByText
import java.time.Instant
import java.time.ZoneId

/**
 * The words a request's clock is said in on its card and in History (phase 3a, spec §6.2–§6.6),
 * as [UiText] so a pure caller can build them and composition resolves them in the reader's
 * language.
 */
object RequestClockText {

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** From two days left the time is said in days; "47 h" reads better than "1 day". */
    private const val DAYS_FROM_MILLIS = 2 * DAY_MILLIS

    /**
     * How long is left, [millis], in the largest unit that still reads well: whole days from 48
     * hours, whole hours from one hour, else whole minutes — never fewer than one, so a request in
     * its last seconds does not say "0 min" while it can still be answered.
     */
    fun left(millis: Long): UiText = when {
        millis >= DAYS_FROM_MILLIS -> UiText.Plural(R.plurals.request_left_days, (millis / DAY_MILLIS).toInt())
        millis >= HOUR_MILLIS -> UiText.Plural(R.plurals.request_left_hours, (millis / HOUR_MILLIS).toInt())
        else -> UiText.Plural(R.plurals.request_left_minutes, (millis / MINUTE_MILLIS).toInt().coerceAtLeast(1))
    }

    /**
     * The meta line of a "No answer" row (spec §6.6), part by part and in order, each present only
     * when the clock knows it: the deadline's date; when the last chance ran to, if one was given;
     * the date it was opened, if it was; how many reminders were shown, once the answering phone had
     * planned them; and that the calendar did not change, which is always true of a request nobody
     * answered. The caller joins them with " · ".
     *
     * @param zone The zone the dates and the time fall in: the device's.
     */
    fun historyMeta(clock: RequestDeadline, zone: ZoneId = ZoneId.systemDefault()): List<UiText> = buildList {
        add(UiText.Res(R.string.request_history_meta_deadline, listOf(dateOf(clock.answerByMillis, zone))))
        clock.lastChanceUntilMillis?.let {
            add(UiText.Res(R.string.request_history_meta_last_chance, listOf(UiText.Raw(answerByText(it, zone)))))
        }
        clock.seenAtMillis?.let { add(UiText.Res(R.string.request_history_meta_opened, listOf(dateOf(it, zone)))) }
        if (clock.remindersPlanned != null) {
            add(UiText.Plural(R.plurals.request_history_meta_reminders, clock.remindersShown))
        }
        add(UiText.Res(R.string.request_history_meta_unchanged))
    }

    private fun dateOf(millis: Long, zone: ZoneId): UiText =
        UiText.Date(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
}
