package com.coparently.app.domain.money

import com.coparently.app.domain.serialization.JvmNumberLayout

/** Kotlin/Native's digits in the JVM's layout (JDK 19's; see `JvmDoubleText`). */
internal actual fun platformDoubleText(value: Double): String = JvmNumberLayout.of(value.toString())
