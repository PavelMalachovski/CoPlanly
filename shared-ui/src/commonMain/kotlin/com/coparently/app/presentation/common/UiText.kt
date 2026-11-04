package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource

/**
 * User-facing text produced where there is no `Context` to look it up — a ViewModel, a state
 * class, a service result — and resolved where there is one (CQ-14).
 *
 * A ViewModel holds *which* string, and composition turns it into words. That split is the
 * whole point, and the two shortcuts it replaces are both wrong: a hardcoded English literal is
 * unreachable by the five locales, and an application `Context` injected into the ViewModel
 * resolves against the wrong configuration — with AppCompat's per-app locales it can answer in
 * the previous language on older APIs, while composition follows the Activity. So resolve with
 * [asString] in a composable, or, inside a non-composable lambda such as a snackbar raised from a
 * coroutine, with a [UiTextResolver] the composable remembered ([rememberUiTextResolver]).
 *
 * The strings are Compose Multiplatform's (`Res.string`, `Res.plurals`; MON-24 phase 2, slice
 * 9c), so the same type serves both apps. Their language is Compose's `Locale.current`, which on
 * Android `ResolvedAppLocale` makes the language `R.string` resolved; the dates follow the
 * reader's locale as before (on Android the configuration's first locale).
 *
 * Prefer a typed code (an enum the screen maps to a resource) where the screen *branches* on the
 * outcome; a `UiText` is for text the screen only shows. Never compare one to decide behaviour —
 * `CalendarScreen` once branched on the English literal "Event rescheduled", which is exactly the
 * check localising that string would have silently broken (UX-12).
 */
sealed interface UiText {

    /**
     * A string resource. [args] may themselves be [UiText]; they are resolved first, so a
     * sentence can embed another localised fragment or a [Date]. Any other argument is printed
     * with `toString()`, which is all Compose Multiplatform's `%<n>$s` and `%<n>$d` do.
     */
    data class Res(val id: StringResource, val args: List<Any> = emptyList()) : UiText

    /** A quantity string. [args] default to the count itself, which is what most plurals print. */
    data class Plural(
        val id: PluralStringResource,
        val count: Int,
        val args: List<Any> = listOf(count)
    ) : UiText

    /**
     * A calendar date, formatted at resolution time in the reader's locale (medium style). Kept
     * as a date rather than pre-formatted so a ViewModel never has to guess the locale.
     */
    data class Date(val date: LocalDate) : UiText

    /**
     * A day of the week by name ("Sunday", "neděle"), in the reader's locale at resolution time,
     * its first letter capitalised — it leads a line ("Sunday: Pavel accepted your request").
     */
    data class Weekday(val day: DayOfWeek) : UiText

    /**
     * Text that is already what the user should read — a name they typed, an email address.
     * Never an English sentence: that is the defect this type exists to remove.
     */
    data class Raw(val value: String) : UiText
}

/** Resolves this text in composition, in the reader's language. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { arg -> resolveArg(arg) }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { arg -> resolveArg(arg) }.toTypedArray())
    is UiText.Date -> rememberReaderDates().date(date)
    is UiText.Weekday -> rememberReaderDates().weekday(day)
    is UiText.Raw -> value
}

/**
 * Resolves this text outside composition — a Toast, or a snackbar raised from a coroutine — in
 * the language and locale of the composition that remembered [resolver], exactly as [asString]
 * would have there.
 */
suspend fun UiText.asString(resolver: UiTextResolver): String = resolver.resolve(this)

/**
 * What a composable hands a coroutine or a callback so it can word a [UiText] there: the
 * composition's resource environment (its language) and its reader's dates. It replaces passing
 * the Activity's `Context` into such a lambda; it never reads the application's.
 */
@Stable
class UiTextResolver internal constructor(
    private val environment: ResourceEnvironment,
    private val dates: ReaderDates
) {

    internal suspend fun resolve(text: UiText): String = when (text) {
        is UiText.Res -> getString(environment, text.id, *resolveArgs(text.args))
        is UiText.Plural -> getPluralString(environment, text.id, text.count, *resolveArgs(text.args))
        is UiText.Date -> dates.date(text.date)
        is UiText.Weekday -> dates.weekday(text.day)
        is UiText.Raw -> text.value
    }

    private suspend fun resolveArgs(args: List<Any>): Array<Any> =
        args.map { arg -> if (arg is UiText) resolve(arg) else arg }.toTypedArray()
}

/** A [UiTextResolver] for this composition, renewed when its language or locale changes. */
@Composable
fun rememberUiTextResolver(): UiTextResolver {
    val environment = rememberResourceEnvironment()
    val dates = rememberReaderDates()
    return remember(environment, dates) { UiTextResolver(environment, dates) }
}

/**
 * How the reader's locale words a [UiText.Date] and a [UiText.Weekday]: `java.time`'s medium date
 * and full day name on Android, Foundation's on the iPhone.
 */
internal interface ReaderDates {

    /** [date] in the medium style ("16 May 2026", "16. 5. 2026"). */
    fun date(date: LocalDate): String

    /** [day]'s full name, its first letter capitalised. */
    fun weekday(day: DayOfWeek): String
}

/**
 * The reader's dates for this composition: on Android in the configuration's first locale, which
 * `UiText` has always formatted in; on the iPhone in the current locale.
 */
@Composable
internal expect fun rememberReaderDates(): ReaderDates

@Composable
private fun resolveArg(arg: Any): Any = if (arg is UiText) arg.asString() else arg
