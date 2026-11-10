package com.coparently.app.di.koin

import com.coparently.app.presentation.auth.AndroidAuthErrors
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.pairing.AndroidInviteQrCodes
import com.coparently.app.presentation.pairing.InviteQrCodes
import com.coparently.app.presentation.widget.TodayWidgetNames
import com.coparently.app.presentation.widget.TodayWidgetRefresher
import com.coparently.app.utils.NetworkMonitor
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The presentation layer's sources and helpers that are not ViewModels: shared flows several
 * screens read (a `single` each, as their Hilt `@Singleton` was) and per-screen dependency
 * bundles (a `factory` each, as they were unscoped). `ParentsSource`, `FamilyKindSource` and
 * `FamilyMembersSource` moved to `:shared-ui`'s `commonUiModule` with their classes (MON-24 phase 2,
 * Task 10), `PendingCustodyConflict` and `PlanReferenceSource` to its `custodyUiModule`,
 * `RequestsSource` to its `requestsUiModule`, `MonthSpendDependencies` to its `familyUiModule`,
 * `HomeIdentityDependencies` to its `homeUiModule`, and `MonthReviewAssist` and `MonthReviewSources`
 * to its `reviewUiModule`.
 */
val presentationModule = module {
    singleOf(::TodayWidgetNames)
    singleOf(::TodayWidgetRefresher)
    singleOf(::NetworkMonitor)
    // The invitation's QR code, which the pairing ViewModel in `:shared-ui` asks for.
    single<InviteQrCodes> { AndroidInviteQrCodes(get()) }
    // Which sign-in failure an SDK's exception is, which AuthViewModel in `:shared-ui` asks for.
    single<AuthErrors> { AndroidAuthErrors }
}
