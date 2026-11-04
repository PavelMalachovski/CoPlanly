package com.coparently.app.data.sync

/**
 * Whether this is Android's `NetworkOnMainThreadException`, which `CalendarSyncRepository` reports
 * apart from any other failure (MON-24 phase 2, Task 8g). Never true on the iPhone, which has no
 * such exception.
 */
internal expect fun Throwable.isNetworkOnMainThread(): Boolean
