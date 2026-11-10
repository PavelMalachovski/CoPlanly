package com.coparently.app.data.platform

/**
 * Logs a product event (MON-24 phase 2, Task 6).
 *
 * On Android it is `AnalyticsManager` over Firebase Analytics. It never turns collection on or off:
 * `TelemetryConsentApplier` is the one switch (CLAUDE.md, REL-5), and an event logged while it is
 * off is not collected. On the iPhone it is `IosAnalytics`, which collects nothing until phase 4
 * gives that platform a consent screen.
 */
interface Analytics {

    /**
     * Logs [event] with [params]. A value is a `String`, a whole number or a decimal; anything
     * else is sent as its `toString()`. Never a name, an address or anything a parent typed.
     */
    fun log(event: String, params: Map<String, Any> = emptyMap())
}
