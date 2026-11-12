package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.home.TodayAgenda
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelMediumEmphasized
import com.coparently.app.presentation.theme.titleSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_agenda_custody_day
import com.coparently.app.resources.calendar_agenda_custody_day_you
import com.coparently.app.resources.calendar_agenda_empty
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.calendar_today_card_children_with_parent
import com.coparently.app.resources.calendar_today_card_children_with_you
import com.coparently.app.resources.calendar_today_card_event
import com.coparently.app.resources.calendar_today_card_nothing_more
import com.coparently.app.resources.calendar_today_card_open
import com.coparently.app.resources.calendar_today_card_title
import com.coparently.app.resources.event_all_day
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the calendar's Today card says: the day, whose day it is with the children's names, and
 * the next event. Built from [TodayAgenda], the value `HomeWeek.todayOf` gives Home's today card
 * and the widget, so the three never disagree about today (design item 18).
 *
 * @property date Today
 * @property dayParent The slot whose day it is, or null when no arrangement answers
 * @property childNames The children to name with that parent; empty on a day they are apart
 *   (FAM-4), when the card says whose day it is instead of claiming they are together
 * @property nextEvent The first event of today that has not ended, a timed one before an all-day
 *   one; null when nothing is left
 * @property hasEvents Whether today held any event at all, to tell "nothing scheduled" from
 *   "nothing more today"
 */
data class TodayModel(
    val date: LocalDate,
    val dayParent: String?,
    val childNames: List<String>,
    val nextEvent: Event?,
    val hasEvents: Boolean
) {
    companion object {
        /**
         * @param agenda Today's agenda, from `HomeWeek.todayOf`
         * @param childNames The family's children, in display order
         * @param childrenApart Whether a child's own schedule puts the children with different
         *   parents today (`ChildrenToday`)
         * @param now The moment the card is drawn
         */
        fun of(
            agenda: TodayAgenda,
            childNames: List<String>,
            childrenApart: Boolean,
            now: LocalDateTime
        ): TodayModel {
            val ahead = agenda.events.filter { (it.endDateTime ?: it.startDateTime) >= now }
            return TodayModel(
                date = agenda.date,
                dayParent = agenda.dayParent,
                childNames = if (childrenApart) emptyList() else childNames,
                nextEvent = ahead.firstOrNull { !AllDayEvent.isAllDay(it) } ?: ahead.firstOrNull(),
                hasEvents = agenda.events.isNotEmpty()
            )
        }
    }
}

/**
 * The card over the tab bar (redesign, November 2026): "Today · Thu 12 Nov", "Emma and Leo are
 * with you" and the next event. A tap opens Day view on today. The screen leaves it out on a
 * wide window, where two panes have room for neither.
 *
 * @param today What to say
 * @param parentNames Resolves a slot to that parent's name, or "you"
 * @param onOpen Opens Day view on today
 * @param modifier Modifier for the card
 */
@Composable
fun TodayCard(
    today: TodayModel,
    parentNames: ParentNames,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.calendar_today_card_open),
                onClick = onOpen
            )
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Box(
            modifier = Modifier
                .size(DISC_SIZE)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Today,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        TodayLines(today, parentNames, Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayLines(today: TodayModel, parentNames: ParentNames, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.calendar_today_card_title, ReaderDateText.date(today.date, "MMMEEEd")),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.primary
        )
        headline(today, parentNames)?.let { line ->
            Text(text = line, style = MaterialTheme.typography.titleSmallEmphasized)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            if (today.nextEvent != null) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSizes.Inline)
                )
            }
            Text(
                text = eventLine(today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // A title, not money: it may end in an ellipsis (design item 15).
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "Emma and Leo are with you", "Emma is with Bob", or, with no children to name, "Your day" /
 * "Bob's day" as the agenda card says it. Null when no arrangement answers for today.
 */
@Composable
private fun headline(today: TodayModel, parentNames: ParentNames): String? {
    val slot = today.dayParent ?: return null
    val names = childrenList(today.childNames, stringResource(Res.string.calendar_names_and))
    val count = today.childNames.size
    return when {
        names == null && parentNames.isReader(slot) ->
            stringResource(Res.string.calendar_agenda_custody_day_you)
        names == null ->
            stringResource(Res.string.calendar_agenda_custody_day, parentNames.labelFor(slot))
        parentNames.isReader(slot) ->
            pluralStringResource(Res.plurals.calendar_today_card_children_with_you, count, names)
        else ->
            pluralStringResource(
                Res.plurals.calendar_today_card_children_with_parent,
                count,
                names,
                parentNames.labelFor(slot)
            )
    }
}

/** "16:00 – Dancing class", "All day – Birthday", or what is left when nothing is. */
@Composable
private fun eventLine(today: TodayModel): String {
    val event = today.nextEvent ?: return stringResource(
        if (today.hasEvents) Res.string.calendar_today_card_nothing_more else Res.string.calendar_agenda_empty
    )
    val time = if (AllDayEvent.isAllDay(event)) {
        stringResource(Res.string.event_all_day)
    } else {
        ReaderDateText.time(event.startDateTime)
    }
    return stringResource(Res.string.calendar_today_card_event, time, event.title)
}

/** The tonal square the calendar glyph sits on. */
private val DISC_SIZE = 40.dp
