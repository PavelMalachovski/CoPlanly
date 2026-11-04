package com.coparently.app.di.koin

import com.coparently.app.presentation.common.FamilyKindSource
import com.coparently.app.presentation.common.FamilyMembersSource
import com.coparently.app.presentation.common.ParentsSource
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The shared state every screen's ViewModel reads (MON-24 phase 2, Task 10): who the two parents
 * are, which sections the family's answers open, and the children and pets a record can name. A
 * `single` each, as in the app's `presentationModule` they came from: [ParentsSource] and
 * [FamilyKindSource] share one upstream across every screen, which is the point of them.
 */
val commonUiModule: Module = module {
    singleOf(::FamilyKindSource)
    singleOf(::FamilyMembersSource)
    // All three arguments from the graph, the family slots' source included, rather than its
    // default (`FamilySlotsSource.None`), which only a test relies on.
    single { ParentsSource(get(), get(), get()) }
}

/**
 * Every module of the UI both apps run, grouped by feature as [dataModules] is. The app's
 * `appModules` includes them, with [platformUiModule]; the iPhone app's will (phase 3). The
 * ViewModels move here group by group (Task 10's slices); a screen still in the app resolves a moved
 * one with `koinViewModel()` exactly as before, since the definition is the same.
 */
val uiModules: List<Module> = listOf(
    commonUiModule
)

/**
 * What [uiModules] takes from the platform beyond what [platformDataModule] binds. Nothing on
 * Android, where the app's own modules bind it; on the iPhone, stand-ins that answer "not on this
 * phone yet" for what it does not have, so [uiModules] resolves there as [dataModules] does.
 */
expect val platformUiModule: Module
