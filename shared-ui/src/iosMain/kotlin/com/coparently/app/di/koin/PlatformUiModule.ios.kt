package com.coparently.app.di.koin

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone. Nothing yet: everything the moved state
 * reads is [dataModules]' and [platformDataModule]'s. Nothing starts Koin there yet; the app that
 * does, in phase 3, includes this beside [platformDataModule].
 */
actual val platformUiModule: Module = module { }
