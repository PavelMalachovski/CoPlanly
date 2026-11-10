package com.coparently.app.domain.serialization

import kotlinx.datetime.LocalDate

/** kotlinx-datetime's ISO text: the iPhone has no stored dates older than this build. */
internal actual fun isoDateText(date: LocalDate): String = date.toString()

/** kotlinx-datetime's ISO parser. */
internal actual fun parseIsoDate(text: String): LocalDate = LocalDate.parse(text)
