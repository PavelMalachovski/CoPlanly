package com.coparently.app.presentation.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.time.IsoText
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.pushwording.WordingStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * What a [WidgetSnapshot] is built from: the account, its family's rows and the moment of writing.
 *
 * One value rather than eight parameters, so that the iPhone's writer hands over what it read in
 * one place and the builder stays a pure function of it.
 *
 * @property today The first day of the snapshot, in the phone's zone.
 * @property writtenAtMillis When it is written, epoch milliseconds.
 * @property language The BCP 47 tag [WidgetSnapshotBuilder.build]'s strings and clock speak.
 * @property userId The signed-in account, which decides whose private events are shown; null when
 *   nobody is signed in, which builds the signed-out snapshot.
 * @property eventsOn A day's events, recurring series already expanded — what
 *   `EventRepository.getEventsByDateRange` returns for that day, as the Android widget reads it.
 * @property model The family on screen's active custody pattern, or null when there is none.
 * @property overrides The accepted one-off swaps, keyed by ISO date.
 * @property parents The names and colours to use, or null when none are known for this account.
 */
data class WidgetSnapshotInput(
    val today: LocalDate,
    val writtenAtMillis: Long,
    val language: String,
    val userId: String?,
    val eventsOn: (LocalDate) -> List<Event>,
    val model: CustodyModel?,
    val overrides: Map<String, DayOverride>,
    val parents: Parents?
)

/**
 * Builds the iPhone widget's [WidgetSnapshot]: seven days from today, each computed by
 * [TodayWidgetModel.of] and worded by [TodayWidgetText], the Android widget's own model and words
 * (CLAUDE.md design item 18: one computation and one wording).
 *
 * Every key read here is listed in `push-wording/push-keys.txt`, as [TodayWidgetText]'s are, so the
 * iPhone's strings carry it.
 */
object WidgetSnapshotBuilder {

    /** Days in a signed-in snapshot: the extension can draw a week without the app running. */
    const val DAYS = 7

    /** The snapshot for [input], worded with [strings] and [clock]. */
    fun build(input: WidgetSnapshotInput, strings: WordingStrings, clock: WidgetClock): WidgetSnapshot {
        val userId = input.userId
        val models = if (userId == null) {
            listOf(TodayWidgetModel.signedOut(input.today))
        } else {
            (0 until DAYS).map { offset ->
                val day = input.today.plus(offset, DateTimeUnit.DAY)
                TodayWidgetModel.of(day, userId, input.eventsOn(day), input.model, input.overrides)
            }
        }
        return WidgetSnapshot(
            writtenAtMillis = input.writtenAtMillis,
            language = input.language,
            signedIn = userId != null,
            days = models.map { model -> dayOf(model, input.parents, strings, clock) },
            staleLine = strings.string("widget_today_stale")
        )
    }

    private fun dayOf(
        model: TodayWidgetModel,
        parents: Parents?,
        strings: WordingStrings,
        clock: WidgetClock
    ): WidgetDay {
        val state = TodayWidgetText.contentState(strings, clock, model, parents)
        return WidgetDay(
            date = IsoText.date(model.today),
            compact = state.compact.toSnapshotLines(),
            compactLarge = state.compact.compactAtLargeText().toSnapshotLines(),
            tall = state.tall.toSnapshotLines()
        )
    }
}

/** These lines with every colour resolved to the Android widget's tones. */
internal fun TodayWidgetLines.toSnapshotLines(): WidgetDayLines = WidgetDayLines(
    title = title,
    custody = custody?.toSnapshotLine(),
    windows = windows.map { it.toSnapshotLine() },
    handover = handover?.toSnapshotLine(),
    events = events.map { event ->
        WidgetSnapshotEvent(time = event.time, title = event.title, mark = event.parent?.markColour())
    },
    footer = footer
)

private fun WidgetLine.toSnapshotLine(): WidgetSnapshotLine = WidgetSnapshotLine(text, parent?.textColour())

/** A line's text tone, as the Android widget draws it: [ParentColor.dark], and [ParentColor.light] on dark. */
internal fun ParentColor.textColour(): WidgetColour = WidgetColour(light = dark.hex(), dark = light.hex())

/** An event mark's full tone, as the Android widget draws it: [ParentColor.fill], [ParentColor.darkFill] on dark. */
internal fun ParentColor.markColour(): WidgetColour = WidgetColour(light = fill.hex(), dark = darkFill.hex())

/** "#RRGGBB", upper case, without the alpha: every parent tone is opaque. */
internal fun Color.hex(): String =
    "#" + (toArgb() and RGB_MASK).toString(HEX_RADIX).uppercase().padStart(HEX_DIGITS, '0')

private const val RGB_MASK = 0xFFFFFF
private const val HEX_RADIX = 16
private const val HEX_DIGITS = 6
