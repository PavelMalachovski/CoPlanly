package com.coparently.app.data.local.preferences

/**
 * Shared preference keys used across features.
 * Kept in one place so different ViewModels read/write the same entries.
 */
object PreferenceKeys {
    /** Pipe-separated set of event types hidden in the calendar. */
    const val HIDDEN_EVENT_TYPES = "calendar_hidden_event_types"

    /** Pipe-separated list of user-defined event types. */
    const val CUSTOM_EVENT_TYPES = "calendar_custom_event_types"

    /** Whether Czech holidays and school vacations are shown in the calendar. */
    const val SHOW_HOLIDAYS = "calendar_show_holidays"

    /** Separator for multi-value string preferences. */
    const val LIST_SEPARATOR = "|"
}
