package com.coparently.app.presentation.chat

import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * A message's send time as text, in the reading device's own timezone.
 *
 * `Message.sentAtMillis` is an instant and deliberately carries no zone, so that two parents in
 * different timezones agree on when a message was sent. The zone belongs here instead, at
 * render time: each parent should read a message in their own local time, whatever the clock on
 * the phone that sent it said.
 */

/** [sentAtMillis] (epoch millis) as the reading device's wall clock. */
internal fun sentAtLocal(sentAtMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(sentAtMillis).toLocalDateTime(TimeZone.currentSystemDefault())

/** A message's send time on the reader's clock: "15:30", "3:30 PM". */
internal fun formatSentAt(sentAtMillis: Long): String = ReaderDateText.time(sentAtLocal(sentAtMillis))

/**
 * A message's send date for [skeleton], then [separator], then its time on the reader's clock:
 * "Aug 12, 15:30".
 */
internal fun formatSentAt(sentAtMillis: Long, skeleton: String, separator: String): String =
    ReaderDateText.dateWithTime(sentAtLocal(sentAtMillis), skeleton, separator)
