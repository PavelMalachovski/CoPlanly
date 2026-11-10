package com.coparently.app.domain.serialization

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

/** `java.time.LocalDate.toString`, which wrote every stored medical profile and vaccination list. */
internal actual fun isoDateText(date: LocalDate): String = date.toJavaLocalDate().toString()

/** `java.time.LocalDate.parse`, which read them, so what failed to read still fails the same way. */
internal actual fun parseIsoDate(text: String): LocalDate = java.time.LocalDate.parse(text).toKotlinLocalDate()
