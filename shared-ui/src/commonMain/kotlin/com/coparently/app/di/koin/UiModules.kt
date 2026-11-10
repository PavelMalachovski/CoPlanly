package com.coparently.app.di.koin

import com.coparently.app.presentation.ai.AiConsentViewModel
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.auth.AuthViewModel
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
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.common.FamilyKindSource
import com.coparently.app.presentation.common.FamilyMembersSource
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.FamilyViewViewModel
import com.coparently.app.presentation.common.ParentPaletteViewModel
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.consent.HealthConsentViewModel
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.contacts.ContactsViewModel
import com.coparently.app.presentation.custody.CustodySetupViewModel
import com.coparently.app.presentation.custody.SeasonalScheduleViewModel
import com.coparently.app.presentation.documents.FamilyDocumentsViewModel
import com.coparently.app.presentation.event.EventOwnerViewModel
import com.coparently.app.presentation.event.EventViewModel
import com.coparently.app.presentation.expenses.ExpenseViewModel
import com.coparently.app.presentation.expenses.SettleUpViewModel
import com.coparently.app.presentation.export.ExportViewModel
import com.coparently.app.presentation.family.EarlierFamiliesViewModel
import com.coparently.app.presentation.family.FamilyViewModel
import com.coparently.app.presentation.family.UnassignedRecordsViewModel
import com.coparently.app.presentation.friends.FriendCalendarViewModel
import com.coparently.app.presentation.friends.FriendViewModel
import com.coparently.app.presentation.guests.GuestAcceptViewModel
import com.coparently.app.presentation.home.HomeIdentityDependencies
import com.coparently.app.presentation.home.HomeViewModel
import com.coparently.app.presentation.home.MonthSpendDependencies
import com.coparently.app.presentation.home.PutOffAsksViewModel
import com.coparently.app.presentation.journal.JournalEditorViewModel
import com.coparently.app.presentation.journal.JournalListViewModel
import com.coparently.app.presentation.onboarding.OnboardingViewModel
import com.coparently.app.presentation.pairing.CustodyConflictViewModel
import com.coparently.app.presentation.pairing.PairingViewModel
import com.coparently.app.presentation.pairing.PendingCustodyConflict
import com.coparently.app.presentation.parentingplan.ParentingPlanViewModel
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.professionals.ProfessionalCalendarViewModel
import com.coparently.app.presentation.professionals.ProfessionalPlanViewModel
import com.coparently.app.presentation.professionals.ProfessionalsViewModel
import com.coparently.app.presentation.profile.ProfileViewModel
import com.coparently.app.presentation.review.MonthReviewAssist
import com.coparently.app.presentation.review.MonthReviewSources
import com.coparently.app.presentation.review.MonthReviewViewModel
import com.coparently.app.presentation.school.ConnectSchoolViewModel
import com.coparently.app.presentation.school.SchoolImportViewModel
import com.coparently.app.presentation.settings.CalendarFeedViewModel
import com.coparently.app.presentation.settings.SettingsViewModel
import com.coparently.app.presentation.stickers.StickersViewModel
import com.coparently.app.presentation.support.SupportViewModel
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
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
 * came from, which Home's ViewModel ([homeUiModule]) resolves from here too.
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
 * [platformDataModule] stands in for on the iPhone. Beside them, since Task 11, the sync group's
 * ViewModel: the co-parent sync and Google Calendar's connection (`GoogleCalendarAccount`), whose
 * sign-in screen the shell runs.
 */
val settingsUiModule: Module = module {
    viewModelOf(::CalendarFeedViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SyncViewModel)
}

/**
 * Pairing (MON-24 phase 2, Task 10): the co-parent link — the invitation's code, its QR, the code
 * redeemed only after "Link with …?", an addressed invitation accepted or declined, the slot
 * re-stamp and the custody reconciliation after it, and the unpair of the family on screen. The QR
 * goes through `InviteQrCodes`, which the app binds on Android and [platformUiModule] stands in for
 * on the iPhone. (The choice between two patterns pairing can leave is [custodyUiModule]'s.)
 */
val pairingUiModule: Module = module {
    viewModelOf(::PairingViewModel)
}

/**
 * Onboarding (MON-24 phase 2, Task 10): the four-step wizard — the link and what it brought back,
 * the notification question, the profile with the country guessed from the phone's own region, and
 * the custody step's state beside the schedule editor [custodyUiModule] holds.
 */
val onboardingUiModule: Module = module {
    viewModelOf(::OnboardingViewModel)
}

/**
 * Signing in (MON-24 phase 2, slice 11f): the sign-in screen's form, its four ways in and why the
 * last one failed. The account calls go through `AccountSignIn` and their failures' wording through
 * [AuthErrors], which the app binds on Android and the iPhone's modules stand in for; the credential
 * sheets are the shell's (`CredentialSignIn`), handed to each call.
 */
val authUiModule: Module = module {
    viewModelOf(::AuthViewModel)
}

/**
 * The session (MON-24 phase 2, Task 10): whether anybody is signed in and still owes the wizard —
 * the start destination's one source — and sign-out's undoing of this phone's push token, school
 * tokens, scheduled steps and files, in that order, before the session ends. The session, the token
 * and the files go through `AuthSession`, `PushRegistration` and `AccountTraces`, which the app binds
 * on Android and [platformDataModule] stands in for on the iPhone. (Google Calendar's connection is
 * [settingsUiModule]'s since Task 11.)
 */
val sessionUiModule: Module = module {
    viewModelOf(::AuthStateViewModel)
}

/**
 * Profiles (MON-24 phase 2, Task 10): the signed-in parent's own record, edited, and the co-parent
 * of the family on screen's, read-only.
 */
val profileUiModule: Module = module {
    viewModelOf(::ProfileViewModel)
}

/**
 * Home (MON-24 phase 2, Task 10): the dashboard — the next handover and each child's day, today
 * and the week, the month's money, the unread count, the co-parent's changes and the Requests row
 * with what Home asks — and which of those asks this parent put off with "Later", kept per family
 * in the sealed preference store [platformDataModule] binds. Beside them the bundle of who the
 * family is, a `factory` as in the app's `presentationModule` it came from.
 */
val homeUiModule: Module = module {
    factoryOf(::HomeIdentityDependencies)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PutOffAsksViewModel)
}

/**
 * The month in review (MON-24 phase 2, Task 10): a month in figures and, when the build offers it
 * and the parent agrees, the AI-written summary of those figures. Beside it its two bundles, a
 * `factory` each as in the app's `presentationModule` they came from. The callable and whether the
 * build offers it are the platform's, as for the chat's suggested reply.
 */
val reviewUiModule: Module = module {
    factoryOf(::MonthReviewAssist)
    factoryOf(::MonthReviewSources)
    viewModelOf(::MonthReviewViewModel)
}

/**
 * Stickers between the parents (MON-24 phase 2, Task 10; CLAUDE.md item 47): the ones received and
 * sent in the family on screen, sending one, and the parent's own answer to "dad, mom or parent".
 */
val stickersUiModule: Module = module {
    viewModelOf(::StickersViewModel)
}

/**
 * The consents (MON-24 phase 2, Task 10): the child-health consent the child's medical section is
 * locked behind (Legal item 4), asked and withdrawn, and the telemetry question (REL-5).
 */
val consentUiModule: Module = module {
    viewModelOf(::HealthConsentViewModel)
    viewModelOf(::TelemetryConsentViewModel)
}

/**
 * The AI assist's consent (MON-24 phase 2, Task 10; CLAUDE.md item 36), as Settings shows and
 * withdraws it, and whether the build offers the assist at all, which is the platform's.
 */
val aiUiModule: Module = module {
    viewModelOf(::AiConsentViewModel)
}

/**
 * A child's record (MON-24 phase 2, Task 10): the list, the editor with its photographs behind the
 * health consent, the delete, and a guest's access to one child. The photographs go through the
 * domain's `RecordPhotoStorage`, and the events and failures through `Analytics` and
 * `CrashReporter`, which the platform binds.
 */
val childInfoUiModule: Module = module {
    viewModelOf(::ChildInfoViewModel)
}

/** A pet's record (MON-24 phase 2, Task 10): the list, the editor with its photographs and the delete. */
val petsUiModule: Module = module {
    viewModelOf(::PetsViewModel)
}

/** The contacts gathered from the children's and pets' records (MON-24 phase 2, Task 10). */
val contactsUiModule: Module = module {
    viewModelOf(::ContactsViewModel)
}

/**
 * The family's documents (MON-24 phase 2, Task 10; CLAUDE.md item 31): the vault of the family on
 * screen or, read-only, of an earlier one, a file added and one opened. The files go through the
 * data layer's `StorageBucket` and `PickedFiles`, which the platform binds.
 */
val documentsUiModule: Module = module {
    viewModelOf(::FamilyDocumentsViewModel)
}

/**
 * The private journal (MON-24 phase 2, Task 10; CLAUDE.md item 34): this parent's entries and the
 * editor of one, on this phone alone.
 */
val journalUiModule: Module = module {
    viewModelOf(::JournalEditorViewModel)
    viewModelOf(::JournalListViewModel)
}

/**
 * Family friends (MON-24 phase 2, Task 10; CLAUDE.md's calendar-friend rule): the parents' list
 * and invitation, the friend's own grants, profile and redemption, and the friend's read-only
 * calendar of one family, read live and never kept in Room.
 */
val friendsUiModule: Module = module {
    viewModelOf(::FriendCalendarViewModel)
    viewModelOf(::FriendViewModel)
}

/** A child's guest (MON-24 phase 2, Task 10): an invitation code redeemed for one child's record. */
val guestsUiModule: Module = module {
    viewModelOf(::GuestAcceptViewModel)
}

/**
 * Professionals (MON-24 phase 2, Task 10; CLAUDE.md item 29): the parents' grants with their
 * consents, the professional's own, and the professional's read-only calendar and parenting plan
 * of one family.
 */
val professionalsUiModule: Module = module {
    viewModelOf(::ProfessionalCalendarViewModel)
    viewModelOf(::ProfessionalPlanViewModel)
    viewModelOf(::ProfessionalsViewModel)
}

/**
 * The parenting plan (MON-24 phase 2, Task 10; CLAUDE.md items 21 and 32): both parents' answers
 * in the family on screen, this parent's half written, agreements ticked, and an agreed schedule
 * answer offered as a proposal.
 */
val parentingPlanUiModule: Module = module {
    viewModelOf(::ParentingPlanViewModel)
}

/**
 * The communication record (MON-24 phase 2, Task 10; CLAUDE.md item 26): a period exported as CSV
 * or PDF, registered for verification, of the family on screen, an earlier one or a kept thread.
 * The file is rendered and saved through `RecordFileWriter`, which the app binds on Android
 * (`PdfDocument`) and [platformDataModule] stands in for on the iPhone.
 */
val exportUiModule: Module = module {
    viewModelOf(::ExportViewModel)
}

/**
 * The school import (MON-24 phase 2, Task 10; CLAUDE.md item 35): connecting a Bakaláři account
 * for one child in one family, and the connections with their imports.
 */
val schoolUiModule: Module = module {
    viewModelOf(::ConnectSchoolViewModel)
    viewModelOf(::SchoolImportViewModel)
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
    settingsUiModule,
    pairingUiModule,
    onboardingUiModule,
    authUiModule,
    sessionUiModule,
    profileUiModule,
    homeUiModule,
    reviewUiModule,
    stickersUiModule,
    consentUiModule,
    aiUiModule,
    childInfoUiModule,
    petsUiModule,
    contactsUiModule,
    documentsUiModule,
    journalUiModule,
    friendsUiModule,
    guestsUiModule,
    professionalsUiModule,
    parentingPlanUiModule,
    exportUiModule,
    schoolUiModule
)

/**
 * What [uiModules] takes from the platform beyond what [platformDataModule] binds. Nothing on
 * Android, where the app's own modules bind it; on the iPhone, stand-ins that answer "not on this
 * phone yet" for what it does not have, so [uiModules] resolves there as [dataModules] does.
 */
expect val platformUiModule: Module
