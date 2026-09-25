package com.coparently.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coparently.app.R

/**
 * The string resource naming a built-in event type in the reader's language, or null for a
 * type a parent added themselves.
 *
 * The stored `eventType` is an identifier (`"general"`, `"school"`, …) shared with the
 * co-parent's phone and with the school import, never a label. The event form and the
 * calendar's filter sheet both name types through this one mapping, so the two cannot drift:
 * the filter sheet used to print the raw English ids in every language.
 *
 * @param type the stored event type identifier
 * @return the id of the localized name, or null when [type] is not a built-in type
 */
@StringRes
fun eventTypeLabelRes(type: String): Int? = when (type) {
    "general" -> R.string.event_type_general
    "medical" -> R.string.event_type_medical
    "school" -> R.string.event_type_school
    "sports" -> R.string.event_type_sports
    "birthday" -> R.string.event_type_birthday
    else -> null
}

/**
 * The name of an event type as a chip shows it: a built-in type in the reader's language, a
 * parent's own type as they typed it, with its first letter capitalised.
 *
 * @param type the stored event type identifier
 * @return the label to render
 */
@Composable
fun eventTypeLabel(type: String): String =
    eventTypeLabelRes(type)?.let { stringResource(it) } ?: type.replaceFirstChar { it.uppercase() }
