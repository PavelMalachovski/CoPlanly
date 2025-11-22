package com.coparently.app.domain.model

/**
 * Enum representing different calendar UI variants for A/B testing.
 */
enum class CalendarVariant {
    /**
     * Original calendar UI (control group).
     */
    CONTROL,

    /**
     * Variant A: Calendar with enhanced animations and transitions.
     */
    A,

    /**
     * Variant B: Calendar with different layout and interaction patterns.
     */
    B
}
