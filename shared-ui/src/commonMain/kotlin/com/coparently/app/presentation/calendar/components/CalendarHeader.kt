package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.family.names
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.ChildAvatar
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_filter_member_toggle
import com.coparently.app.resources.calendar_filters_hiding
import com.coparently.app.resources.calendar_jump_to_date
import com.coparently.app.resources.calendar_legend_title
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.calendar_settings
import com.coparently.app.resources.calendar_together_for
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
 * From font scale 1.3 the title keeps the first line with "?", and the avatars and gear take a line
 * under it; before that the title steps its size down rather than end in an ellipsis.
 *
 * The iPhone draws it too (MON-24 phase 3, part C): its shell hides the navigation bar over the
 * Calendar's root ([CalendarTopBar]), so this header leads the screen there as on Android.
 *
 * @param state What the header shows
 * @param actions What it does
 */
@Composable
fun CalendarHeader(state: CalendarHeaderState, actions: CalendarHeaderActions) {
    val trailing: @Composable () -> Unit = {
        MemberFilterAvatars(
            members = state.members,
            selected = state.selectedMembers,
            onToggle = actions.onToggleMember
        )
        actions.onSettings?.let { onClick ->
            IconButton(onClick = onClick) {
                AppIcon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.calendar_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val leading: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            MonthTitle(
                date = state.month,
                onJumpToDate = actions.onJumpToDate,
                modifier = Modifier.weight(1f, fill = false)
            )
            LegendButton(filtersActive = state.filtersActive, onClick = actions.onLegend)
        }
    }
    // Tight on purpose (tester feedback, 12 November 2026): every dp the header gives up goes to
    // the month grid under it. The gear's own padding stands in for the end margin.
    val padding = Modifier
        .fillMaxWidth()
        .padding(start = Spacing.L, end = Spacing.XS, top = Spacing.XXS, bottom = Spacing.XXS)
    if (stacksAtLargeFont()) {
        Column(modifier = padding) {
            leading(Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) { trailing() }
        }
    } else {
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            leading(Modifier.weight(1f))
            trailing()
        }
    }
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
 * The member filter as avatars: one per child and pet, a tap toggling that member in the same
 * filter the strip over the grid drove before calendar K1. A chosen member wears a ring in
 * `primary`. Renders nothing below two members, as the strip did (FAM-1: a picker for a set of one
 * is design item 8 in miniature). More than three scroll within the room three take, so the month
 * keeps the row. A target is 40 dp, as the strip's chips were (tester feedback, 12 November 2026).
 */
@Composable
private fun MemberFilterAvatars(
    members: List<FamilyMember>,
    selected: List<FamilyMemberRef>,
    onToggle: (FamilyMemberRef) -> Unit
) {
    if (members.size < 2) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides AVATAR_TARGET) {
        Row(
            modifier = Modifier
                .widthIn(max = AVATAR_TARGET * VISIBLE_AVATARS)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            members.forEach { member ->
                MemberFilterAvatar(
                    member = member,
                    chosen = selected.names(member.ref),
                    onToggle = { onToggle(member.ref) }
                )
            }
        }
    }
}

/** One avatar of [MemberFilterAvatars]: a toggle named for the member, ringed while chosen. */
@Composable
private fun MemberFilterAvatar(member: FamilyMember, chosen: Boolean, onToggle: () -> Unit) {
    val description = stringResource(Res.string.calendar_filter_member_toggle, member.name)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = RING_WIDTH,
                    color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
                .padding(RING_WIDTH)
        ) { MemberAvatar(member) }
    }
}

/** A child's own avatar, or a pet's initial on a tonal disc. */
@Composable
private fun MemberAvatar(member: FamilyMember) {
    val child = member.child
    if (child != null) {
        ChildAvatar(child, AVATAR_SIZE)
    } else {
        Box(
            modifier = Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
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

/** "Together, for Emma and Leo", or null for a family without children. */
@Composable
internal fun togetherFor(childNames: List<String>): String? {
    val names = childrenList(childNames, stringResource(Res.string.calendar_names_and)) ?: return null
    return stringResource(Res.string.calendar_together_for, names)
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

/** An avatar of the member filter, and the ring a chosen one wears. */
private val AVATAR_SIZE = 30.dp
private val RING_WIDTH = 2.dp

/** One avatar's touch target, as the member strip's chips had it. */
private val AVATAR_TARGET = 40.dp

/** How many avatars the row shows before the rest scroll. */
private const val VISIBLE_AVATARS = 3
