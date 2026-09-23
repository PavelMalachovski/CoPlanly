package com.coparently.app.presentation.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * User-facing text produced where there is no `Context` to look it up — a ViewModel, a state
 * class, a service result — and resolved where there is one (CQ-14).
 *
 * A ViewModel holds *which* string, and composition turns it into words. That split is the
 * whole point, and the two shortcuts it replaces are both wrong: a hardcoded English literal is
 * unreachable by the five locales, and an application `Context` injected into the ViewModel
 * resolves against the wrong configuration — with AppCompat's per-app locales it can answer in
 * the previous language on older APIs, while composition follows the Activity. So resolve with
 * [asString] in a composable, or with the Activity's `Context` (`LocalContext.current`) inside a
 * non-composable lambda such as a snackbar or a Toast.
 *
 * Prefer a typed code (an enum the screen maps to a resource) where the screen *branches* on the
 * outcome; a `UiText` is for text the screen only shows. Never compare one to decide behaviour —
 * `CalendarScreen` once branched on the English literal "Event rescheduled", which is exactly the
 * check localising that string would have silently broken (UX-12).
 */
sealed interface UiText {

    /**
     * A string resource. [args] may themselves be [UiText]; they are resolved first, so a
     * sentence can embed another localised fragment or a [Date].
     */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A quantity string. [args] default to the count itself, which is what most plurals print. */
    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count)
    ) : UiText

    /**
     * A calendar date, formatted at resolution time in the reader's locale (medium style). Kept
     * as a date rather than pre-formatted so a ViewModel never has to guess the locale.
     */
    data class Date(val date: LocalDate) : UiText

    /**
     * Text that is already what the user should read — a name they typed, an email address.
     * Never an English sentence: that is the defect this type exists to remove.
     */
    data class Raw(val value: String) : UiText
}

/** Resolves this text in composition, following the Activity's locale. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { arg -> resolveArg(arg) }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { arg -> resolveArg(arg) }.toTypedArray())
    is UiText.Date -> formatDate(date, LocalConfiguration.current.locales[0])
    is UiText.Raw -> value
}

/**
 * Resolves this text outside composition — a Toast, or a snackbar raised from a coroutine.
 * Pass the Activity's `Context` (`LocalContext.current`), never the application's.
 */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.map { arg -> resolveArg(arg, context) }.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(
        id,
        count,
        *args.map { arg -> resolveArg(arg, context) }.toTypedArray()
    )
    is UiText.Date -> formatDate(date, context.resources.configuration.locales[0])
    is UiText.Raw -> value
}

@Composable
private fun resolveArg(arg: Any): Any = if (arg is UiText) arg.asString() else arg

private fun resolveArg(arg: Any, context: Context): Any = if (arg is UiText) arg.asString(context) else arg

private fun formatDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
