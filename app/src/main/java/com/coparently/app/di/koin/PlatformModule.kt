package com.coparently.app.di.koin

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.platform.Analytics
import com.coparently.app.data.platform.AndroidAppDirectories
import com.coparently.app.data.platform.AndroidDeviceRegion
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.Connectivity
import com.coparently.app.data.platform.CrashReporter
import com.coparently.app.data.platform.DeviceRegion
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.utils.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The platform services `shared-data` declares as interfaces (MON-24 phase 2, Task 6), bound to
 * the Android classes that were already doing the work, so nothing stored or sent moves.
 *
 * Each binding resolves the class's own definition, not a second instance: the preference store,
 * above all, must stay one per process (CLAUDE.md item 20). The logger is not here: it is
 * `shared-data`'s ambient `logger`, for the reason its KDoc gives.
 */
val platformModule = module {
    single<AppDirectories> { AndroidAppDirectories(androidContext()) }
    single<SecurePreferences> { get<EncryptedPreferences>() }
    single<CrashReporter> { get<CrashlyticsManager>() }
    single<Analytics> { get<AnalyticsManager>() }
    single<Connectivity> { get<NetworkMonitor>() }
    single<DeviceRegion> { AndroidDeviceRegion(androidContext()) }
}
