package com.coparently.app.domain.money

/** The runtime's own `Double.toString`, as `"$value"` wrote it before Task 8f. */
internal actual fun platformDoubleText(value: Double): String = value.toString()
