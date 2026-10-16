package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.answerByText

/** Which side of a request the parent reading its card is on (phase 3a). */
enum class ClockRole {
    /** The parent who made the request and set its deadline. */
    PROPOSER,

    /** The parent whose answer is due. */
    RESPONDER
}

/**
 * A request's clock as one inbox card shows it (phase 3a, spec §6.2–§6.5): the clock at the
 * inbox's moment, which side the reader is on, and the two things the card can do with it.
 *
 * The cards read it from [LocalCardClock] rather than a parameter, so none of their signatures
 * moves — the detekt baseline keys findings on them, as `MessageItem` reads `LocalChatEventCard`
 * for the same reason.
 *
 * @property timed The request's clock, seen at the inbox's moment.
 * @property role Which side the reader is on.
 * @property otherName The other parent's name — never a role.
 * @property onSeen Records that the answering parent opened the request; null when there is
 *   nothing to record (the proposer, a request seen already, or one not waiting on them).
 * @property onGiveLastChance Gives the one last chance; null unless the reader is the proposer and
 *   the button is offered now.
 * @property lastChanceInFlight Whether that last chance is being given right now: the button stays
 *   on the card, disabled, so a second tap cannot ask for it twice.
 */
@Immutable
data class CardClock(
    val timed: TimedRequest,
    val role: ClockRole,
    val otherName: String,
    val onSeen: (() -> Unit)?,
    val onGiveLastChance: (() -> Unit)?,
    val lastChanceInFlight: Boolean = false
) {

    /**
     * Whether the card hides its answer buttons: the answering parent's request is past its
     * deadline and the next move is the proposer's (spec §4.1).
     */
    val hidesAnswers: Boolean
        get() = role == ClockRole.RESPONDER && timed.state == ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE

    /** Whether the card's title carries "No answer": the proposer's request past its deadline. */
    internal val showsNoAnswer: Boolean
        get() = role == ClockRole.PROPOSER && timed.state == ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE
}

/** The clock of the inbox card being drawn, or null for a card whose request has none. */
val LocalCardClock: ProvidableCompositionLocal<CardClock?> = staticCompositionLocalOf { null }

/**
 * The last-chance banner (spec §6.5), first in the answering parent's card while a last chance
 * runs: how long is left, and what happens without an answer. Nothing otherwise.
 */
@Composable
internal fun CardClockBanner() {
    val clock = LocalCardClock.current ?: return
    if (clock.role != ClockRole.RESPONDER || clock.timed.state != ClockState.LAST_CHANCE) return
    val until = RequestClock.endMillis(clock.timed.clock)
    InlineBanner(
        title = stringResource(
            R.string.request_last_chance_banner_title,
            RequestClockText.left(until - clock.timed.nowMillis).asString()
        ),
        text = stringResource(R.string.request_last_chance_banner_text, answerByText(until)),
        icon = Icons.Default.Schedule,
        tone = BannerTone.ATTENTION
    )
}

/**
 * "No answer" at the end of the proposer's title line once the deadline has passed (spec §6.4):
 * History's outlined, neutral chip — never the error colour, which is "Declined". Nothing otherwise.
 */
@Composable
internal fun CardClockChip() {
    if (LocalCardClock.current?.showsNoAnswer != true) return
    PillChip(
        label = stringResource(R.string.request_clock_no_answer),
        container = null,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * What the card says about its clock (spec §6.2–§6.4), after its content and before its buttons:
 * the answering parent's deadline, or the proposer's status rows and, past the deadline, the
 * last-chance button. Records the answering parent's first look, once, when it is drawn.
 */
@Composable
internal fun CardClockDetails() {
    val clock = LocalCardClock.current ?: return
    clock.onSeen?.let { seen -> LaunchedEffect(clock.timed.clock.id) { seen() } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        when (clock.role) {
            ClockRole.RESPONDER -> ResponderRows(clock)
            ClockRole.PROPOSER -> ProposerRows(clock)
        }
    }
}

@Composable
private fun ResponderRows(clock: CardClock) {
    val timed = clock.timed
    when (timed.state) {
        ClockState.OPEN -> AnswerByRow(timed, MaterialTheme.colorScheme.primary)
        ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE -> Text(
            text = stringResource(
                R.string.request_clock_responder_unanswered,
                clock.otherName,
                answerByText(RequestClock.buttonUntilMillis(timed.clock))
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The banner says it; a request that is over is in History, not on a card.
        ClockState.LAST_CHANCE, ClockState.NO_ANSWER -> Unit
    }
}

@Composable
private fun ProposerRows(clock: CardClock) {
    val timed = clock.timed
    when (timed.state) {
        ClockState.OPEN -> if (timed.clock.deliveredAtMillis == null) {
            ClockRow(
                Icons.Default.Schedule,
                stringResource(R.string.request_clock_sent, answerByText(timed.clock.answerByMillis))
            )
        } else {
            OpenedRow(clock)
            RemindersRow(timed)
            AnswerByRow(timed, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ClockState.LAST_CHANCE -> {
            OpenedRow(clock)
            val until = RequestClock.endMillis(timed.clock)
            ClockRow(
                Icons.Default.Schedule,
                stringResource(
                    R.string.request_clock_last_chance_until,
                    answerByText(until),
                    RequestClockText.left(until - timed.nowMillis).asString()
                )
            )
        }
        ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE -> DeadlinePassedRows(clock)
        // Over: the request is in History, not on a card.
        ClockState.NO_ANSWER -> Unit
    }
}

/** The proposer's card past the deadline (spec §6.4), down to the button and what it gives. */
@Composable
private fun DeadlinePassedRows(clock: CardClock) {
    val timed = clock.timed
    ClockRow(
        Icons.Default.Schedule,
        stringResource(R.string.request_clock_deadline_passed, answerByText(timed.clock.answerByMillis))
    )
    OpenedRow(clock)
    RemindersRow(timed)
    Text(
        text = stringResource(R.string.request_clock_calendar_unchanged),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val window = timed.lastChanceWindowMillis
    val give = clock.onGiveLastChance
    if (window != null && give != null) {
        val until = timed.nowMillis + window
        // The label promises 24 hours only when it can give them (ruling 3).
        Button(onClick = give, enabled = !clock.lastChanceInFlight) {
            Text(
                if (window == RequestClock.LAST_CHANCE_MILLIS) {
                    stringResource(R.string.request_clock_give_last_chance)
                } else {
                    stringResource(R.string.request_clock_give_last_chance_until, answerByText(until))
                }
            )
        }
        Text(
            text = stringResource(R.string.request_clock_last_chance_once, clock.otherName, answerByText(until)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Answer by <time> · <left>", with the clock icon. */
@Composable
private fun AnswerByRow(timed: TimedRequest, color: Color) {
    ClockRow(
        Icons.Default.Schedule,
        stringResource(
            R.string.request_clock_answer_by,
            answerByText(timed.clock.answerByMillis),
            RequestClockText.left(timed.clock.answerByMillis - timed.nowMillis).asString()
        ),
        color
    )
}

/**
 * Whether the other parent opened it, once their phone has received it — before then the card
 * claims nothing beyond "Sent" (spec §4.5).
 */
@Composable
private fun OpenedRow(clock: CardClock) {
    val seenAt = clock.timed.clock.seenAtMillis
    when {
        seenAt != null -> ClockRow(
            Icons.Default.Visibility,
            stringResource(R.string.request_clock_opened, clock.otherName, answerByText(seenAt))
        )
        clock.timed.clock.deliveredAtMillis != null -> ClockRow(
            Icons.Default.VisibilityOff,
            stringResource(R.string.request_clock_not_opened, clock.otherName)
        )
    }
}

/** The answering phone's reminders: how many it showed of how many it planned, and the next. */
@Composable
private fun RemindersRow(timed: TimedRequest) {
    val planned = timed.clock.remindersPlanned ?: 0
    if (planned == 0) return
    val shown = timed.clock.remindersShown
    val next = timed.nextReminderMillis
    ClockRow(
        Icons.Default.Notifications,
        if (next != null) {
            stringResource(R.string.request_clock_reminders_next, shown, planned, answerByText(next))
        } else {
            stringResource(R.string.request_clock_reminders, shown, planned)
        }
    )
}

/** One status row: a small icon and its line. The icon is decorative; the line says it all. */
@Composable
private fun ClockRow(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSizes.Small))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    }
}
