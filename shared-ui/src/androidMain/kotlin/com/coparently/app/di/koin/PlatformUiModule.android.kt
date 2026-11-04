package com.coparently.app.di.koin

import org.koin.core.module.Module
import org.koin.dsl.module

/** Nothing: on Android the app's own modules bind what [uiModules] takes from the platform. */
actual val platformUiModule: Module = module { }
