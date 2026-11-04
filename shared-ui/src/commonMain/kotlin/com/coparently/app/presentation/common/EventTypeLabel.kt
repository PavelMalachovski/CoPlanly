package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import com.coparently.app.resources.Res
import com.coparently.app.resources.event_type_birthday
import com.coparently.app.resources.event_type_general
import com.coparently.app.resources.event_type_medical
import com.coparently.app.resources.event_type_school
import com.coparently.app.resources.event_type_sports
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
 * @return the localized name's resource, or null when [type] is not a built-in type
 */
fun eventTypeLabelRes(type: String): StringResource? = when (type) {
    "general" -> Res.string.event_type_general
    "medical" -> Res.string.event_type_medical
    "school" -> Res.string.event_type_school
    "sports" -> Res.string.event_type_sports
    "birthday" -> Res.string.event_type_birthday
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
