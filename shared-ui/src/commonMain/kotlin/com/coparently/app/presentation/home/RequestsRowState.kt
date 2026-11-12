package com.coparently.app.presentation.home

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.time.minus
import com.coparently.app.presentation.common.UiText
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_requests_answer_accepted
import com.coparently.app.resources.home_requests_answer_declined
import com.coparently.app.resources.home_requests_day_today
import com.coparently.app.resources.home_requests_day_yesterday
import com.coparently.app.resources.home_requests_none
import com.coparently.app.resources.home_requests_waiting_on_them
import com.coparently.app.resources.home_requests_waiting_on_you
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * What Home's Requests row says (phase 2, spec §6.1).
 *
 * @property waitingOnMe What waits on this parent's answer, the custody proposal included.
 * @property waitingOnThem What this parent sent that the co-parent has not answered.
 * @property latestAnswer The co-parent's newest answer to one of this parent's requests, from the
 *   last seven days ([RequestsRowText.latestAnswer]).
 */
data class RequestsRowState(
    val waitingOnMe: Int = 0,
    val waitingOnThem: Int = 0,
    val latestAnswer: HistoryEntry? = null
)

/**
 * The words of Home's Requests row: its parts, joined by the screen with " · ".
 *
 * The row is always there while the account is paired, so its line has to say something even
 * when nothing waits — "No requests yet" — and has to say only what is true: a part with nothing
 * to report is left out rather than printed as zero.
 */
object RequestsRowText {

    /** How far back the row mentions the co-parent's latest answer. */
    private val ANSWER_WINDOW: Duration = 7.days

    /** The last day named by a weekday rather than a date. */
    private const val WEEKDAY_DAYS = 6

    /**
     * The co-parent's newest ACCEPTED/DECLINED answer to one of this parent's requests, decided
     * within seven days of [now]. A withdrawal is not an answer, nor is "No answer" (phase 3a) —
     * the row would otherwise say they declined it — and a custody or expense split decision has its
     * own card and banner.
     */
    fun latestAnswer(history: List<HistoryEntry>, scope: RequestScope, now: LocalDateTime): HistoryEntry? {
        val since = now.minus(ANSWER_WINDOW)
        return history
            .filter { entry ->
                entry.askedBy == scope.myUid &&
                    entry.decidedBy == scope.coParentUid &&
                    entry.outcome.isAnswer &&
                    entry.subject != RequestSubject.CustodySchedule &&
                    entry.subject != RequestSubject.SplitRatio &&
                    entry.decidedAt >= since
            }
            .maxByOrNull { it.decidedAt }
    }

    /**
     * The row's line, part by part: what waits on this parent, what waits on the co-parent, and
     * the co-parent's latest answer, each only when it has something to say; "No requests yet"
     * when none has. The answer's day leads, as a label, so no language needs a preposition or a
     * case for a weekday inside the sentence.
     */
    fun parts(state: RequestsRowState, coParentName: String, today: LocalDate): List<UiText> {
        val parts = buildList {
            if (state.waitingOnMe > 0) {
                add(UiText.Plural(Res.plurals.home_requests_waiting_on_you, state.waitingOnMe))
            }
            if (state.waitingOnThem > 0) {
                add(
                    UiText.Plural(
                        Res.plurals.home_requests_waiting_on_them,
                        state.waitingOnThem,
                        listOf(state.waitingOnThem, coParentName)
                    )
                )
            }
            state.latestAnswer?.let { answer ->
                val words = if (answer.outcome == RequestOutcome.ACCEPTED) {
                    Res.string.home_requests_answer_accepted
                } else {
                    Res.string.home_requests_answer_declined
                }
                val day = relativeDay(answer.decidedAt.date, today)
                add(UiText.Res(words, listOf(day, coParentName)))
            }
        }
        return parts.ifEmpty { listOf(UiText.Res(Res.string.home_requests_none)) }
    }

    /** Today, Yesterday, a weekday for two to six days ago, else the date. */
    fun relativeDay(date: LocalDate, today: LocalDate): UiText {
        val daysAgo = date.daysUntil(today)
        return when (daysAgo) {
            0 -> UiText.Res(Res.string.home_requests_day_today)
            1 -> UiText.Res(Res.string.home_requests_day_yesterday)
            in 2..WEEKDAY_DAYS -> UiText.Weekday(date.dayOfWeek)
            else -> UiText.Date(date)
        }
    }
}
