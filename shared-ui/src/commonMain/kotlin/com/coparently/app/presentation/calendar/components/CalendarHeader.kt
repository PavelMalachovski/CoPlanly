package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.family.names
import com.coparently.app.presentation.common.AppHeaderRow
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.FamilyAvatarsAndGear
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.MemberFilter
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_filters_hiding
import com.coparently.app.resources.calendar_jump_to_date
import com.coparently.app.resources.calendar_legend_title
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * The calendar's header (calendar K1, November 2026): one row over the lavender ground that gives
 * the month grid the rest of the screen. The month on screen, always with its year, where the
 * wordmark was (a tap opens "Jump to date"); "?", which explains the colours and leads to Filters;
 * the children's and pets' avatars as the member filter; and the gear.
 *
 * It replaced the redesign's three rows (the wordmark, "Your family calendar", the Month/Week/Day
 * control with Filters): the heading lines went to Home
 * ([com.coparently.app.presentation.common.HeadingLines]), the view control to the grid card's bar
 * ([CalendarMonthBar]), Filters into the "?" sheet ([CalendarLegendSheet]), and the member strip
 * over the grid became the avatars here.
 *
 * One row at every font scale, in [AppHeaderRow] with Home's, so the avatars and the gear stand still
 * between the two tabs (owner, 24 November 2026; from font scale 1.3 they used to take a line under
 * the title, which read unevenly on the owner's phone): the title steps its size down instead, and
 * ends in an ellipsis only at its smallest.
 *
 * The iPhone draws it too (MON-24 phase 3, part C): its shell hides the navigation bar over the
 * Calendar's root ([CalendarTopBar]), so this header leads the screen there as on Android.
 *
 * @param state What the header shows
 * @param actions What it does
 */
@Composable
fun CalendarHeader(state: CalendarHeaderState, actions: CalendarHeaderActions) {
    AppHeaderRow(
        leading = {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                MonthTitle(
                    date = state.month,
                    onJumpToDate = actions.onJumpToDate,
                    modifier = Modifier.weight(1f, fill = false)
                )
                LegendButton(filtersActive = state.filtersActive, onClick = actions.onLegend)
            }
        },
        trailing = {
            FamilyAvatarsAndGear(
                members = state.members,
                onSettings = actions.onSettings,
                filter = MemberFilter(selected = state.selectedMembers, onToggle = actions.onToggleMember)
            )
        }
    )
}

/**
 * What the calendar's header shows.
 *
 * @property month The day whose month titles the screen: the first of the month in Month view, the
 *   selected day in Week and Day view
 * @property members Everyone the family cares for, the member filter's avatars; drawn from two on
 * @property selectedMembers The members the grid is narrowed to; none means everyone
 * @property filtersActive Whether a filter in the Filters sheet narrows the calendar, which "?" then
 *   marks with a dot, since Filters lives behind it
 */
data class CalendarHeaderState(
    val month: LocalDate,
    val members: List<FamilyMember>,
    val selectedMembers: List<FamilyMemberRef>,
    val filtersActive: Boolean
)

/**
 * What the calendar's header does.
 *
 * @property onJumpToDate Opens the date picker
 * @property onLegend Opens "What the colours mean", with the way to Filters under it
 * @property onToggleMember Adds a member to the filter or takes them out of it
 * @property onSettings Opens Settings, or null to draw no gear
 */
data class CalendarHeaderActions(
    val onJumpToDate: () -> Unit,
    val onLegend: () -> Unit,
    val onToggleMember: (FamilyMemberRef) -> Unit,
    val onSettings: (() -> Unit)?
)

/**
 * What the legend explains: whether there is a schedule to explain, and which of its marks the
 * calendar on screen draws.
 *
 * @property hasSchedule Whether the family has a custody schedule; without one the grid has no
 *   wash to explain, and the sheet says when it will
 * @property showContactWindows Whether the schedule has contact time, so its mark needs explaining
 * @property showPendingSwap Whether a day on screen waits for a swap's answer
 * @property filtersActive Whether a filter narrows the calendar, which the Filters row then says
 */
data class CalendarLegendState(
    val hasSchedule: Boolean,
    val showContactWindows: Boolean,
    val showPendingSwap: Boolean,
    val filtersActive: Boolean
)

/**
 * "November 2026 ▾": the month's name, then its year in a lighter weight and colour, and the arrow
 * that says a tap opens something. A button for TalkBack, labelled with where it leads.
 *
 * Always with the year (calendar K1, reversing D-3's "the year only outside this one"): the title is
 * the screen's heading now, and the full year never reads as a day beside a grid that holds a 26.
 */
@Composable
private fun MonthTitle(date: LocalDate, onJumpToDate: () -> Unit, modifier: Modifier) {
    val title = calendarMonthTitle(date)
    val style = MaterialTheme.typography.titleLargeEmphasized
    val yearColor = MaterialTheme.colorScheme.onSurfaceVariant
    val yearWeight = MaterialTheme.typography.titleLarge.fontWeight
    val text = buildAnnotatedString {
        append(title.month)
        append(' ')
        withStyle(SpanStyle(color = yearColor, fontWeight = yearWeight)) { append(title.year) }
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.calendar_jump_to_date),
                onClick = onJumpToDate
            )
            .defaultMinSize(minHeight = LayoutConstants.MIN_TOUCH_TARGET)
            .padding(end = Spacing.XXS)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A long month name ("Сентябрь 2027") steps its size down before it ends in an ellipsis.
        BasicText(
            text = text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SIZE, maxFontSize = style.fontSize),
            modifier = Modifier.weight(1f, fill = false)
        )
        AppIcon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSizes.Small)
        )
    }
}

/**
 * "?": opens "What the colours mean" and, under the legend, the way to Filters. A dot marks it
 * while a filter narrows the calendar, as the filled Filters button did before it moved there.
 */
@Composable
private fun LegendButton(filtersActive: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.calendar_legend_title)
    val hiding = stringResource(Res.string.calendar_filters_hiding)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { if (filtersActive) stateDescription = hiding }
    ) {
        BadgedBox(badge = { if (filtersActive) Badge() }) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The month title's two parts: the month's stand-alone name ("LLLL", `java.time`'s
 * `Month.getDisplayName(FULL_STANDALONE)` on Android) and its year, which is always shown.
 *
 * @property month "November"
 * @property year "2026"
 */
internal data class CalendarMonthTitle(val month: String, val year: String) {
    /** "November 2026", as TalkBack and the tests read it. */
    val text: String get() = "$month $year"
}

/** The header's title for [selectedDate]'s month: "November 2026", the year always included. */
internal fun calendarMonthTitle(selectedDate: LocalDate): CalendarMonthTitle {
    val yearMonth = selectedDate.yearMonth
    val monthName = ReaderDateText.pattern(yearMonth, "LLLL").replaceFirstChar { it.uppercase() }
    return CalendarMonthTitle(month = monthName, year = yearMonth.year.toString())
}

/**
 * Children's names as one phrase: "Emma", "Emma and Leo", "Emma, Leo and Max", or null for
 * none. Pure, so the joining is tested without a composition.
 *
 * @param names The names, in display order
 * @param and The reader's word for "and", from `calendar_names_and`
 */
internal fun childrenList(names: List<String>, and: String): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " $and " + names.last()
}

/** The smallest the month title steps down to before it ends in an ellipsis. */
private val MIN_TITLE_SIZE = 14.sp
