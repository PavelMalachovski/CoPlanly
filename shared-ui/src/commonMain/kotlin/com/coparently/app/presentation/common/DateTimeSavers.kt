package com.coparently.app.presentation.common

import androidx.compose.runtime.saveable.Saver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * Savers that keep a kotlinx-datetime date or time in a form's saved state as its ISO text (11c
 * round 1 review, I1), as `AnswerByPickSaver` and `LocalDateSetSaver` keep theirs.
 *
 * Without one, `rememberSaveable` puts the value in the Bundle by Java serialization, which for
 * kotlinx-datetime's classes goes through a private `writeReplace`/`readObject` proxy
 * (`kotlinx.datetime.Ser`) that no R8 rule keeps: a minified build could fail to save or restore the
 * form. `java.time`'s classes, which the forms held before MON-24, are the platform's and R8 never
 * touches them. The text is what `toString()` writes and `parse` reads, so a value survives exactly.
 */

/** A [LocalDate] as its ISO text ("2026-05-13"). */
val LocalDateSaver: Saver<LocalDate, String> = Saver(
    save = { it.toString() },
    restore = { LocalDate.parse(it) }
)

/** A [LocalDate] that may be null, as its ISO text, or an empty text for none. */
val NullableLocalDateSaver: Saver<LocalDate?, String> = Saver(
    save = { it?.toString().orEmpty() },
    restore = { text -> if (text.isEmpty()) null else LocalDate.parse(text) }
)

/** A [LocalTime] as its ISO text ("09:00", seconds and a fraction only when they are not zero). */
val LocalTimeSaver: Saver<LocalTime, String> = Saver(
    save = { it.toString() },
    restore = { LocalTime.parse(it) }
)

/** A [LocalTime] that may be null, as its ISO text, or an empty text for none. */
val NullableLocalTimeSaver: Saver<LocalTime?, String> = Saver(
    save = { it?.toString().orEmpty() },
    restore = { text -> if (text.isEmpty()) null else LocalTime.parse(text) }
)
