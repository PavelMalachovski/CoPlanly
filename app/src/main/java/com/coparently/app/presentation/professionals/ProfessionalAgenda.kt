package com.coparently.app.presentation.professionals

import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * One day of a professional's read-only calendar (MON-18).
 *
 * @property date The day.
 * @property custodySlot Whose day it is, as the `"mom"`/`"dad"` slot id, or null when no schedule
 *   answers. Resolved through [CustodyResolver], the one custody lookup every view goes through,
 *   so an accepted swap moves the day here exactly as it does on the parents' grid.
 * @property events The family's shared events overlapping the day, earliest first.
 */
data class ProfessionalDay(
    val date: LocalDate,
    val custodySlot: String?,
    val events: List<Event>
)

/** Builds the agenda a professional reads — a list, not a grid. */
object ProfessionalAgenda {

    /**
     * [dayCount] consecutive days from [start].
     *
     * The legacy per-parent schedule is deliberately not consulted: it is Room-only and never
     * leaves a parent's phone, so the professional has only the shared pattern and its swaps.
     */
    fun days(
        start: LocalDate,
        dayCount: Int,
        events: List<Event>,
        custody: SharedCustody?
    ): List<ProfessionalDay> {
        val model = custody?.model?.takeIf { it.isActive }
        val overrides = custody?.dayOverrides.orEmpty()
        return (0 until dayCount).map { offset ->
            val date = start.plusDays(offset.toLong())
            ProfessionalDay(
                date = date,
                custodySlot = CustodyResolver.custodyFor(model, overrides, { null }, date),
                events = events.filter { it.overlaps(date) }.sortedBy { it.startDateTime }
            )
        }
    }

    private fun Event.overlaps(date: LocalDate): Boolean {
        val first = startDateTime.toLocalDate()
        val last = (endDateTime ?: startDateTime).toLocalDate()
        return !date.isBefore(first) && !date.isAfter(last)
    }
}
