package com.coparently.app.di.koin

import com.coparently.app.presentation.calendar.CalendarViewModel
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.changerequests.RequestChangeViewModel
import com.coparently.app.presentation.changerequests.RequestsSource
import com.coparently.app.presentation.chat.ChatAttachmentViewModel
import com.coparently.app.presentation.chat.ChatEventCardViewModel
import com.coparently.app.presentation.chat.ChatSearchViewModel
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.chat.DictationViewModel
import com.coparently.app.presentation.chat.ReplySuggestionViewModel
import com.coparently.app.presentation.chat.SettleUpPaymentViewModel
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.common.FamilyKindSource
import com.coparently.app.presentation.common.FamilyMembersSource
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.FamilyViewViewModel
import com.coparently.app.presentation.common.ParentPaletteViewModel
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.custody.CustodySetupViewModel
import com.coparently.app.presentation.custody.SeasonalScheduleViewModel
import com.coparently.app.presentation.event.EventOwnerViewModel
import com.coparently.app.presentation.event.EventViewModel
import com.coparently.app.presentation.expenses.ExpenseViewModel
import com.coparently.app.presentation.expenses.SettleUpViewModel
import com.coparently.app.presentation.family.EarlierFamiliesViewModel
import com.coparently.app.presentation.family.FamilyViewModel
import com.coparently.app.presentation.family.UnassignedRecordsViewModel
import com.coparently.app.presentation.home.MonthSpendDependencies
import com.coparently.app.presentation.pairing.CustodyConflictViewModel
import com.coparently.app.presentation.pairing.PendingCustodyConflict
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.settings.CalendarFeedViewModel
import com.coparently.app.presentation.settings.SettingsViewModel
import com.coparently.app.presentation.support.SupportViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The shared state every screen's ViewModel reads (MON-24 phase 2, Task 10): who the two parents
 * are, which sections the family's answers open, and the children and pets a record can name. A
 * `single` each, as in the app's `presentationModule` they came from: [ParentsSource] and
 * [FamilyKindSource] share one upstream across every screen, which is the point of them. Beside
 * them, the ViewModels the whole tree shares from the activity's level: the offline banner's, the
 * family switcher's, the family view's and the parent palette's.
 */
val commonUiModule: Module = module {
    singleOf(::FamilyKindSource)
    singleOf(::FamilyMembersSource)
    // All three arguments from the graph, the family slots' source included, rather than its
    // default (`FamilySlotsSource.None`), which only a test relies on.
    single { ParentsSource(get(), get(), get()) }
    viewModelOf(::ConnectivityViewModel)
    viewModelOf(::FamilySwitcherViewModel)
    viewModelOf(::FamilyViewViewModel)
    viewModelOf(::ParentPaletteViewModel)
}

/**
 * The chat (MON-24 phase 2, Task 10): the thread and its conversations, a file's sending and
 * opening, the card that names an event, the search, dictation, the suggested reply and the
 * payment a settle-up card shows. What they take from the platform — the sealed preference store,
 * the signed-in account, on-device speech and the AI callable with whether the build offers it — is
 * bound by [platformDataModule] and the app's modules on Android, and by [platformDataModule] and
 * [platformUiModule] on the iPhone.
 */
val chatUiModule: Module = module {
    viewModelOf(::ChatAttachmentViewModel)
    viewModelOf(::ChatEventCardViewModel)
    viewModelOf(::ChatSearchViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::DictationViewModel)
    viewModelOf(::ReplySuggestionViewModel)
    viewModelOf(::SettleUpPaymentViewModel)
}

/**
 * The calendar (MON-24 phase 2, Task 10): the month, week and day grid's state — the custody it
 * draws, the swaps offered from it, the filters and the "schedule changed" banner. Its filters and
 * the dismissed change are kept in the sealed preference store [platformDataModule] binds.
 */
val calendarUiModule: Module = module {
    viewModelOf(::CalendarViewModel)
}

/**
 * The events (MON-24 phase 2, Task 10): the events every calendar surface shows with their
 * operations, an event's photo and the new-event draft, and whose day an event's start date is for
 * the form. An event's photo goes through the domain's `RecordPhotoStorage`, which the app binds on
 * Android and [platformDataModule] stands in for on the iPhone.
 */
val eventUiModule: Module = module {
    viewModelOf(::EventOwnerViewModel)
    viewModelOf(::EventViewModel)
}

/**
 * Custody (MON-24 phase 2, Task 10): the schedule editor, which onboarding's schedule step shares,
 * the seasonal layers with the holiday-fairness card, and the choice between two patterns that
 * pairing can leave. Beside them, a `single` each as in the app's `presentationModule` they came
 * from: the parenting plan's answers as the custody editors quote and cite them, and the pending
 * pattern conflict, which pairing sets and the conflict screen reads.
 */
val custodyUiModule: Module = module {
    singleOf(::PendingCustodyConflict)
    singleOf(::PlanReferenceSource)
    viewModelOf(::CustodyConflictViewModel)
    viewModelOf(::CustodySetupViewModel)
    viewModelOf(::SeasonalScheduleViewModel)
}

/**
 * Requests (MON-24 phase 2, Task 10): the inbox of what waits on either parent — change requests,
 * day swaps, events waiting for an answer, the custody and split proposals and the agreements beside
 * them — with its answers and withdrawals, and the form that asks to move an event. Beside them the
 * one source Home's Requests row, the calendar's banner and the inbox count from, a `factory` as in
 * the app's `presentationModule` it came from.
 */
val requestsUiModule: Module = module {
    factoryOf(::RequestsSource)
    viewModelOf(::ChangeRequestViewModel)
    viewModelOf(::RequestChangeViewModel)
}

/**
 * The family (MON-24 phase 2, Task 10): the Family tab's hub with its month's money, Family →
 * Earlier families, and "Records without a family" with Home's one question about them. Beside them
 * the bundle the month's money is computed from, a `factory` as in the app's `presentationModule` it
 * came from, which Home's ViewModel, still in the app, resolves from here too.
 */
val familyUiModule: Module = module {
    factoryOf(::MonthSpendDependencies)
    viewModelOf(::EarlierFamiliesViewModel)
    viewModelOf(::FamilyViewModel)
    viewModelOf(::UnassignedRecordsViewModel)
}

/**
 * Expenses (MON-24 phase 2, Task 10): the Expenses screen and the Add Expense form — the family on
 * screen's expenses, the month's figures, the split and its proposals, a receipt's photo and its
 * on-device scan — and the settle-up dialog's card and IBAN. The receipt's photo and its text go
 * through the domain's `RecordPhotoStorage` and `ReceiptTextRecognizer`, which the app binds on
 * Android and [platformDataModule] stands in for on the iPhone.
 */
val expensesUiModule: Module = module {
    viewModelOf(::ExpenseViewModel)
    viewModelOf(::SettleUpViewModel)
}

/**
 * Payments between the parents (MON-24 phase 2, Task 10): "I paid" and "Money arrived" for a
 * month's settle-up and for child support, in the family on screen.
 */
val paymentsUiModule: Module = module {
    viewModelOf(::PaymentsViewModel)
}

/**
 * Child support (MON-24 phase 2, Task 10): the agreement, its proposal and answer, each month's
 * payment, and the payer's reminder, whose job goes through `SupportReminderScheduler` — WorkManager
 * on Android, and nothing yet on the iPhone, where [platformDataModule] stands in for it.
 */
val supportUiModule: Module = module {
    viewModelOf(::SupportViewModel)
}

/**
 * Settings (MON-24 phase 2, Task 10): the app's and the account's settings — the push switch, the
 * theme, the currency, the country and region, the family's answers and split, the account's
 * erasure — and the read-only calendar links a parent gives an iPhone. The push switch and the
 * erasure go through `PushRegistration` and `AccountDeletion`, which the app binds on Android and
 * [platformDataModule] stands in for on the iPhone.
 */
val settingsUiModule: Module = module {
    viewModelOf(::CalendarFeedViewModel)
    viewModelOf(::SettingsViewModel)
}

/**
 * Every module of the UI both apps run, grouped by feature as [dataModules] is. The app's
 * `appModules` includes them, with [platformUiModule]; the iPhone app's will (phase 3). The
 * ViewModels move here group by group (Task 10's slices); a screen still in the app resolves a moved
 * one with `koinViewModel()` exactly as before, since the definition is the same.
 */
val uiModules: List<Module> = listOf(
    commonUiModule,
    chatUiModule,
    calendarUiModule,
    eventUiModule,
    custodyUiModule,
    requestsUiModule,
    familyUiModule,
    expensesUiModule,
    paymentsUiModule,
    supportUiModule,
    settingsUiModule
)

/**
 * What [uiModules] takes from the platform beyond what [platformDataModule] binds. Nothing on
 * Android, where the app's own modules bind it; on the iPhone, stand-ins that answer "not on this
 * phone yet" for what it does not have, so [uiModules] resolves there as [dataModules] does.
 */
expect val platformUiModule: Module
