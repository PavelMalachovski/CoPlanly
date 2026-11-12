package com.coparently.app.presentation.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.presentation.calendar.components.CalendarHeader
import com.coparently.app.presentation.calendar.components.DayAgendaCard
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import com.coparently.app.utils.createSampleEvent
import com.coparently.app.utils.previewParentNames
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/*
 * The calendar composables' previews. They stayed in the app when the composables moved to
 * `:shared-ui` (MON-24 phase 2, Task 11, slice 11c), as `DesignSystemPreviews.kt` and
 * `ChatPreviews.kt` did: the preview annotations and `PreviewWrapper` are Android Studio's.
 */

@LightDarkPreviews
@Composable
private fun CalendarHeaderTodayPreview() {
    PreviewWrapper {
        CalendarHeader(
            children = emptyList(),
            viewMode = CalendarViewMode.MONTH,
            onViewModeChange = {},
            onFiltersClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(name = "Week mode, filters active", showBackground = true)
@Composable
private fun CalendarHeaderWeekPreview() {
    PreviewWrapper {
        CalendarHeader(
            children = emptyList(),
            viewMode = CalendarViewMode.WEEK,
            onViewModeChange = {},
            onFiltersClick = {},
            filtersActive = true,
            onSettingsClick = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun DayAgendaCardPreview() {
    PreviewWrapper {
        DayAgendaCard(
            date = Clock.System.todayIn(TimeZone.currentSystemDefault()),
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
            date = Clock.System.todayIn(TimeZone.currentSystemDefault()),
            events = emptyList(),
            custody = "dad",
            parentNames = previewParentNames,
            onEventClick = {},
            modifier = Modifier.padding(Spacing.L)
        )
    }
}
