package com.coparently.app.di.koin

import com.coparently.app.presentation.changerequests.RequestsSource
import com.coparently.app.presentation.home.HomeIdentityDependencies
import com.coparently.app.presentation.home.MonthSpendDependencies
import com.coparently.app.presentation.review.MonthReviewAssist
import com.coparently.app.presentation.review.MonthReviewSources
import com.coparently.app.presentation.widget.TodayWidgetNames
import com.coparently.app.presentation.widget.TodayWidgetRefresher
import com.coparently.app.utils.NetworkMonitor
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The presentation layer's sources and helpers that are not ViewModels: shared flows several
 * screens read (a `single` each, as their Hilt `@Singleton` was) and per-screen dependency
 * bundles (a `factory` each, as they were unscoped). `ParentsSource`, `FamilyKindSource` and
 * `FamilyMembersSource` moved to `:shared-ui`'s `commonUiModule` with their classes (MON-24 phase 2,
 * Task 10), and `PendingCustodyConflict` and `PlanReferenceSource` to its `custodyUiModule`.
 */
val presentationModule = module {
    factoryOf(::RequestsSource)
    factoryOf(::HomeIdentityDependencies)
    factoryOf(::MonthSpendDependencies)
    factoryOf(::MonthReviewAssist)
    factoryOf(::MonthReviewSources)
    singleOf(::TodayWidgetNames)
    singleOf(::TodayWidgetRefresher)
    singleOf(::NetworkMonitor)
}
