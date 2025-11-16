package com.coparently.app.utils

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.theme.CoParentlyTheme
import java.time.LocalDateTime
import java.util.UUID

/**
 * Preview parameter providers and wrapper composables for consistent previews.
 * Simplifies creating Compose Previews with sample data.
 */

// ==================== Preview Providers ====================

/**
 * Provides sample events for Compose Previews.
 * Returns multiple event list scenarios: empty, single event, and multiple events.
 */
class SampleEventsProvider : PreviewParameterProvider<List<Event>> {
    override val values = sequenceOf(
        // Empty list scenario
        emptyList(),

        // Single event scenario
        listOf(
            Event(
                id = UUID.randomUUID().toString(),
                title = "Soccer Practice",
                description = "Weekly soccer practice at the park",
                startDateTime = LocalDateTime.now().plusDays(1).withHour(15).withMinute(0),
                endDateTime = LocalDateTime.now().plusDays(1).withHour(17).withMinute(0),
                eventType = "sports",
                parentOwner = "dad",
                isRecurring = true,
                recurrencePattern = "weekly",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                syncedToFirestore = true,
                createdByFirebaseUid = "user123",
                sharedWith = listOf("user456"),
                lastModifiedBy = "user123",
                permissions = "read_write"
            )
        ),

        // Multiple events scenario
        listOf(
            Event(
                id = UUID.randomUUID().toString(),
                title = "Soccer Practice",
                description = "Weekly soccer practice at the park",
                startDateTime = LocalDateTime.now().plusDays(1).withHour(15).withMinute(0),
                endDateTime = LocalDateTime.now().plusDays(1).withHour(17).withMinute(0),
                eventType = "sports",
                parentOwner = "dad",
                isRecurring = true,
                recurrencePattern = "weekly",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                syncedToFirestore = true,
                createdByFirebaseUid = "user123",
                sharedWith = listOf("user456"),
                lastModifiedBy = "user123",
                permissions = "read_write"
            ),
            Event(
                id = UUID.randomUUID().toString(),
                title = "Doctor Appointment",
                description = "Annual checkup at pediatrician",
                startDateTime = LocalDateTime.now().plusDays(3).withHour(10).withMinute(30),
                endDateTime = LocalDateTime.now().plusDays(3).withHour(11).withMinute(30),
                eventType = "medical",
                parentOwner = "mom",
                isRecurring = false,
                recurrencePattern = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                syncedToFirestore = true,
                createdByFirebaseUid = "user456",
                sharedWith = listOf("user123"),
                lastModifiedBy = "user456",
                permissions = "read_write"
            ),
            Event(
                id = UUID.randomUUID().toString(),
                title = "School Pickup",
                description = "Pick up from school at 3:30 PM",
                startDateTime = LocalDateTime.now().withHour(15).withMinute(30),
                endDateTime = LocalDateTime.now().withHour(16).withMinute(0),
                eventType = "school",
                parentOwner = "mom",
                isRecurring = true,
                recurrencePattern = "daily",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                syncedToFirestore = false,
                createdByFirebaseUid = "user456",
                sharedWith = listOf("user123"),
                lastModifiedBy = "user456",
                permissions = "read_only"
            ),
            Event(
                id = UUID.randomUUID().toString(),
                title = "Birthday Party",
                description = "Emma's 8th birthday celebration",
                startDateTime = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0),
                endDateTime = LocalDateTime.now().plusDays(5).withHour(17).withMinute(0),
                eventType = "birthday",
                parentOwner = "dad",
                isRecurring = false,
                recurrencePattern = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                syncedToFirestore = true,
                createdByFirebaseUid = "user123",
                sharedWith = listOf("user456"),
                lastModifiedBy = "user123",
                permissions = "read_write"
            )
        )
    )
}

/**
 * Provides sample single events for Compose Previews.
 * Returns various event types for testing different scenarios.
 */
class SampleEventProvider : PreviewParameterProvider<Event> {
    override val values = sequenceOf(
        Event(
            id = UUID.randomUUID().toString(),
            title = "Soccer Practice",
            description = "Weekly soccer practice at the park",
            startDateTime = LocalDateTime.now().plusDays(1).withHour(15).withMinute(0),
            endDateTime = LocalDateTime.now().plusDays(1).withHour(17).withMinute(0),
            eventType = "sports",
            parentOwner = "dad",
            isRecurring = true,
            recurrencePattern = "weekly",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        ),
        Event(
            id = UUID.randomUUID().toString(),
            title = "Doctor Appointment",
            description = "Annual checkup",
            startDateTime = LocalDateTime.now().plusDays(3).withHour(10).withMinute(30),
            endDateTime = LocalDateTime.now().plusDays(3).withHour(11).withMinute(30),
            eventType = "medical",
            parentOwner = "mom",
            isRecurring = false,
            recurrencePattern = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        ),
        Event(
            id = UUID.randomUUID().toString(),
            title = "Birthday Party",
            description = null,
            startDateTime = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0),
            endDateTime = LocalDateTime.now().plusDays(5).withHour(17).withMinute(0),
            eventType = "birthday",
            parentOwner = "mom",
            isRecurring = false,
            recurrencePattern = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    )
}

// ==================== Preview Wrappers ====================

/**
 * Preview wrapper that applies CoParently theme and surface.
 * Use this to wrap all composable previews for consistent styling.
 *
 * @param darkTheme Whether to use dark theme, default false (light theme)
 * @param content The composable content to preview
 *
 * @sample
 * ```kotlin
 * @Preview
 * @Composable
 * private fun MyComposablePreview() {
 *     PreviewWrapper {
 *         MyComposable()
 *     }
 * }
 * ```
 */
@Composable
fun PreviewWrapper(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    CoParentlyTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

// ==================== Common Preview Annotations ====================

/**
 * Standard preview annotation for light and dark themes.
 * Use as a replacement for individual @Preview annotations.
 *
 * @sample
 * ```kotlin
 * @LightDarkPreviews
 * @Composable
 * private fun MyComposablePreview() {
 *     PreviewWrapper {
 *         MyComposable()
 *     }
 * }
 * ```
 */
@Preview(
    name = "Light Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class LightDarkPreviews

/**
 * Preview annotation for different screen sizes.
 * Tests compact, medium, and expanded layouts.
 */
@Preview(
    name = "Phone (Compact)",
    showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Preview(
    name = "Foldable (Medium)",
    showBackground = true,
    device = "spec:width=673dp,height=841dp"
)
@Preview(
    name = "Tablet (Expanded)",
    showBackground = true,
    device = "spec:width=1280dp,height=800dp"
)
annotation class DevicePreviews

/**
 * Comprehensive preview annotation combining themes and devices.
 * Use for important screens that need thorough preview coverage.
 */
@Preview(
    name = "Phone - Light",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Phone - Dark",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Tablet - Light",
    showBackground = true,
    device = "spec:width=1280dp,height=800dp",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Tablet - Dark",
    showBackground = true,
    device = "spec:width=1280dp,height=800dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class CompletePreviews

// ==================== Sample Data Generators ====================

/**
 * Generate a list of sample events for testing.
 *
 * @param count Number of events to generate
 * @param startDate Starting date for events (default: now)
 * @return List of sample events
 */
fun generateSampleEvents(
    count: Int = 5,
    startDate: LocalDateTime = LocalDateTime.now()
): List<Event> {
    val eventTypes = listOf("general", "medical", "school", "sports", "birthday")
    val parentOwners = listOf("mom", "dad")
    val titles = listOf(
        "Soccer Practice",
        "Doctor Appointment",
        "School Pickup",
        "Piano Lesson",
        "Birthday Party",
        "Parent-Teacher Meeting",
        "Swimming Class",
        "Dentist Checkup",
        "Homework Time",
        "Playdate"
    )

    return List(count) { index ->
        Event(
            id = UUID.randomUUID().toString(),
            title = titles[index % titles.size],
            description = "Sample event description for ${titles[index % titles.size]}",
            startDateTime = startDate.plusDays(index.toLong()).withHour(9 + index % 12),
            endDateTime = startDate.plusDays(index.toLong()).withHour(10 + index % 12),
            eventType = eventTypes[index % eventTypes.size],
            parentOwner = parentOwners[index % parentOwners.size],
            isRecurring = index % 2 == 0,
            recurrencePattern = if (index % 2 == 0) "weekly" else null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            syncedToFirestore = index % 3 == 0,
            createdByFirebaseUid = "user${index % 2}",
            sharedWith = listOf("user${(index + 1) % 2}"),
            lastModifiedBy = "user${index % 2}",
            permissions = if (index % 3 == 0) "read_only" else "read_write"
        )
    }
}

/**
 * Generate a single sample event for testing.
 *
 * @param title Event title
 * @param parentOwner Parent owner ("mom" or "dad")
 * @param eventType Event type
 * @return Sample event
 */
fun createSampleEvent(
    title: String = "Sample Event",
    parentOwner: String = "mom",
    eventType: String = "general"
): Event {
    return Event(
        id = UUID.randomUUID().toString(),
        title = title,
        description = "Sample event description",
        startDateTime = LocalDateTime.now().plusDays(1),
        endDateTime = LocalDateTime.now().plusDays(1).plusHours(1),
        eventType = eventType,
        parentOwner = parentOwner,
        isRecurring = false,
        recurrencePattern = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}

