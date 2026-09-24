package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.CoPlanlyColors
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.presentation.theme.labelMediumEmphasized
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import com.coparently.app.utils.createSampleEvent
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.previewParentNames
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tint strength of the inline banners above the grid. */
private const val BANNER_TINT_ALPHA = 0.14f

/** Font scale from which [ChangeRequestBanner]'s action sits under its text (design item 15). */
private const val STACK_BANNER_ACTION_FONT_SCALE = 1.3f

/**
 * Pending change requests, as a labelled row above the grid.
 *
 * Replaces a badged `swap_horiz` icon in the header — an unlabelled glyph that also appeared in
 * chat meaning something adjacent but different, and whose badge said only "1" with no hint of
 * what one of. A banner can say what is waiting and offer the action inline.
 *
 * @param pendingCount Number of pending incoming requests; the caller hides this at zero
 * @param onReview Opens the change-requests inbox
 * @param modifier Modifier for the banner
 * @param message When set, replaces the pluralised "N requests" line — used for the
 *   custody-proposal banner, which names the proposer rather than counting rows.
 * @param detail One short secondary line under [message], or null for none — the parenting-plan
 *   answer a custody proposal cites (MON-21). Still one banner, a line taller; never a second one.
 */
@Composable
fun ChangeRequestBanner(
    pendingCount: Int,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    detail: String? = null
) {
    // From 1.3x the action goes under the text rather than beside it: beside it, a German
    // "2 Änderungsanfragen" at 2.0x had a third of the row and broke inside the word.
    val stacked = LocalDensity.current.fontScale >= STACK_BANNER_ACTION_FONT_SCALE
    val review: @Composable (Modifier) -> Unit = { reviewModifier ->
        Text(
            text = stringResource(R.string.calendar_change_requests_review),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = reviewModifier
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            // A button to TalkBack, and a 48dp target: the padded row was about 36dp.
            .clickable(role = Role.Button, onClick = onReview)
            .heightIn(min = LayoutConstants.MIN_TOUCH_TARGET)
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSizes.Small)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message ?: pluralStringResource(
                    R.plurals.calendar_change_requests_banner,
                    pendingCount,
                    pendingCount
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Neither line is capped: "2 change requests from your co-pare…" cut the one fact
            // the banner exists for, in English at the default size (docs/AUDIT-2026-10-design.md
            // D-3). The count now leaves out who sent them — the one co-parent this calendar is
            // shared with — so it fits one line in all five languages, and a larger font size
            // costs the grid a line instead of the banner its meaning.
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (stacked) {
                review(Modifier.align(Alignment.End).padding(top = Spacing.XXS))
            }
        }
        if (!stacked) {
            review(Modifier)
        }
    }
}

/**
 * School vacation stated once for the whole month.
 *
 * In July and August every single cell used to carry a teal strip, which is per-day noise for a
 * month-level fact: the strip stopped distinguishing anything precisely when it was most
 * visible. One banner says the same thing and gives the grid its bottom edge back.
 *
 * **Not rendered by the calendar any more**: a banner present only in some months changed the
 * grid's height mid-swipe, so `CalendarScreen` dropped it, and the month grid now marks each
 * vacation day with a thin neutral line instead (`DayCellFill.schoolVacation`, MON-13) — neutral
 * where the old strip was teal, which is now the calendar friend's colour. Kept for the screenshot
 * suite and for a surface that does not page.
 *
 * @param label Vacation name, or a range description
 * @param modifier Modifier for the banner
 */
@Composable
fun VacationBanner(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(CoPlanlyColors.VacationTint.copy(alpha = BANNER_TINT_ALPHA))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(3.dp)
                .clip(CoPlanlyCorners.Mark)
                .background(CoPlanlyColors.VacationTint)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Announces that the shared custody schedule changed under this device — last-write-wins with
 * no consent step, so the one thing that must not happen is the change landing silently. Shown
 * only for a change this device did not make itself; see
 * `CalendarViewModel.custodyChangeAnnouncement` for that decision.
 *
 * The closest shape in this file is [VacationBanner] — a tinted `Row` with a coloured dash and a
 * `Text` — copied here rather than invented anew. Unlike [VacationBanner] and
 * [ChangeRequestBanner], this banner reports something that already happened rather than
 * something waiting on the user, so it is the one banner in this file with something to
 * acknowledge: a plain trailing `IconButton`, not a second visual language for dismissal.
 *
 * @param byName The name of whoever changed it, already resolved by the caller via
 *   `ParentNames.labelForUid` — the uid
 *   ([com.coparently.app.domain.custody.SharedCustody.lastModifiedBy]) resolved directly against
 *   the known parents, never through a slot: a pair sharing one slot before migration would
 *   otherwise have the co-parent's write reported as the signed-in parent's own. This parameter
 *   is that already-safe result.
 * @param onDismiss Persists the dismissal, keyed by the caller on the change's own
 *   instant so a later change is announced again.
 * @param modifier Modifier for the banner
 */
@Composable
fun CustodyChangedBanner(byName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = BANNER_TINT_ALPHA))
            .padding(start = 11.dp, top = 5.dp, bottom = 5.dp, end = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(3.dp)
                .clip(CoPlanlyCorners.Mark)
                .background(MaterialTheme.colorScheme.secondary)
        )
        Text(
            text = stringResource(R.string.calendar_custody_changed_by, byName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizes.Inline)
            )
        }
    }
}

/**
 * One day's agenda: date, whose custody day it is, and the day's events.
 *
 * Born as the card under the calendar's month grid — the other half of replacing event chips
 * with dots: the dots say *how many*, and this says *what*. It now renders on the home screen
 * as the "today" card instead, so the month grid can fill its screen; the composable stayed
 * here so the calendar could take it back without the two ever growing separate anatomies.
 *
 * @param date The selected day
 * @param events That day's events, in start order
 * @param custody The day's custody slot, or null when no custody model applies
 * @param parentNames Resolves a slot to that parent's name
 * @param onEventClick Opens an event
 * @param modifier Modifier for the card
 * @param contactWindows The day's contact windows (MON-6b), earliest first — already filtered by
 *   `CustodyResolver.contactWindowsResolver`, the lookup the calendar grid draws its bands from,
 *   so this card and the grid never disagree about an afternoon. Empty draws nothing.
 */
@Composable
// header, custody line and event rows are one card, not three; the parameters are the
// card's API surface, one per thing it displays
@Suppress("LongMethod", "LongParameterList")
fun DayAgendaCard(
    date: LocalDate,
    events: List<Event>,
    custody: String?,
    parentNames: ParentNames,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contactWindows: List<ContactWindow> = emptyList()
) {
    val dateFormatter = remember(Locale.getDefault()) { localizedDate("MMMEEEd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        // Two lines, not one (UX-8). Whose day it is used to be a suffix on the date, at
        // `labelMedium` in `onSurfaceVariant` and in no parent colour at all — the smallest,
        // greyest text on a screen where the *next* handover is rendered at 26sp bold. The
        // hierarchy was inverted: the future event shouted over the present fact the app is
        // opened to answer.
        //
        // The date stays small and muted, because it is the context. The custody line is
        // promoted and carries the parent's own colour, through `ParentColors.text` — the
        // text-grade member of the pair, never the raw fill, which fails AA as a foreground.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (custody != null) {
                Text(
                    text = stringResource(
                        R.string.calendar_agenda_custody_day,
                        parentNames.labelFor(custody)
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = ParentColors.text(custody)
                )
            }
            // An afternoon with the other parent, under the line that says whose day it is:
            // the day stays theirs (item 24), and this says who has the child in between.
            contactWindows.forEach { window ->
                ContactWindowLine(window, parentNames, timeFormatter)
            }
        }

        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_agenda_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onEventClick(event.id) }
                        .heightIn(min = LayoutConstants.MIN_TOUCH_TARGET),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(CoPlanlyCorners.Mark)
                            .background(ParentColors.fill(event.parentOwner))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Same mark as the home timeline, same description. An exclamation
                            // mark on its own does not say "the co-parent is expected" to
                            // anyone who cannot see it.
                            if (event.isImportant) {
                                Icon(
                                    imageVector = Icons.Default.PriorityHigh,
                                    contentDescription = stringResource(
                                        R.string.event_important_mark_description
                                    ),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(IconSizes.Inline)
                                )
                            }
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = agendaTime(event, timeFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * One contact window as a line of the agenda card: "15:00–19:00 · contact with Alex".
 *
 * The marker is the window parent's full hue and the text its text-grade partner — the same
 * two strengths of one hue the grid's band and edge use, through [ParentColors] so the family's
 * chosen palette applies. The name comes from [ParentNames], never a role word.
 */
@Composable
private fun ContactWindowLine(
    window: ContactWindow,
    parentNames: ParentNames,
    formatter: DateTimeFormatter
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(3.dp)
                .clip(CoPlanlyCorners.Mark)
                .background(ParentColors.fill(window.parent))
        )
        Text(
            text = stringResource(
                R.string.calendar_agenda_contact_window,
                window.start.format(formatter),
                window.end.format(formatter),
                parentNames.labelFor(window.parent)
            ),
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = ParentColors.text(window.parent),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "14:00–15:30", or just the start when the event has no end. */
@Composable
private fun agendaTime(event: Event, formatter: DateTimeFormatter): String {
    val start = event.startDateTime.format(formatter)
    val end = event.endDateTime?.format(formatter)
    return if (end != null) {
        stringResource(R.string.calendar_agenda_time_range, start, end)
    } else {
        start
    }
}

@LightDarkPreviews
@Composable
private fun DayAgendaCardPreview() {
    PreviewWrapper {
        DayAgendaCard(
            date = LocalDate.now(),
            events = listOf(
                createSampleEvent(title = "School pickup", parentOwner = "mom"),
                createSampleEvent(title = "Dentist", parentOwner = "dad")
            ),
            custody = "mom",
            parentNames = previewParentNames,
            onEventClick = {},
            modifier = Modifier.padding(Spacing.L),
            contactWindows = listOf(
                ContactWindow(
                    dayIndex = 2,
                    start = LocalTime.parse("15:00"),
                    end = LocalTime.parse("19:00"),
                    parent = "dad"
                )
            )
        )
    }
}

@LightDarkPreviews
@Composable
private fun DayAgendaCardEmptyPreview() {
    PreviewWrapper {
        DayAgendaCard(
            date = LocalDate.now(),
            events = emptyList(),
            custody = "dad",
            parentNames = previewParentNames,
            onEventClick = {},
            modifier = Modifier.padding(Spacing.L)
        )
    }
}
