package com.coparently.app.di.koin

import com.coparently.app.presentation.ai.AiConsentViewModel
import com.coparently.app.presentation.auth.AuthViewModel
import com.coparently.app.presentation.calendar.CalendarViewModel
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.changerequests.RequestChangeViewModel
import com.coparently.app.presentation.chat.ChatAttachmentViewModel
import com.coparently.app.presentation.chat.ChatEventCardViewModel
import com.coparently.app.presentation.chat.ChatSearchViewModel
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.chat.DictationViewModel
import com.coparently.app.presentation.chat.ReplySuggestionViewModel
import com.coparently.app.presentation.chat.SettleUpPaymentViewModel
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.FamilyViewViewModel
import com.coparently.app.presentation.common.ParentPaletteViewModel
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
import com.coparently.app.presentation.home.HomeViewModel
import com.coparently.app.presentation.home.PutOffAsksViewModel
import com.coparently.app.presentation.journal.JournalEditorViewModel
import com.coparently.app.presentation.journal.JournalListViewModel
import com.coparently.app.presentation.onboarding.OnboardingViewModel
import com.coparently.app.presentation.pairing.CustodyConflictViewModel
import com.coparently.app.presentation.pairing.PairingViewModel
import com.coparently.app.presentation.parentingplan.ParentingPlanViewModel
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.professionals.ProfessionalCalendarViewModel
import com.coparently.app.presentation.professionals.ProfessionalPlanViewModel
import com.coparently.app.presentation.professionals.ProfessionalsViewModel
import com.coparently.app.presentation.profile.ProfileViewModel
import com.coparently.app.presentation.review.MonthReviewViewModel
import com.coparently.app.presentation.school.ConnectSchoolViewModel
import com.coparently.app.presentation.school.SchoolImportViewModel
import com.coparently.app.presentation.settings.CalendarFeedViewModel
import com.coparently.app.presentation.settings.SettingsViewModel
import com.coparently.app.presentation.stickers.StickersViewModel
import com.coparently.app.presentation.support.SupportViewModel
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every ViewModel. `koinViewModel()` in a composable and `by viewModel()` in `MainActivity` create
 * them in the caller's `ViewModelStoreOwner`, exactly as Hilt's ViewModel helpers did, and hand a
 * `SavedStateHandle` holding the destination's arguments to a constructor that asks for one
 * (`ViewModelScopeTest` holds both).
 */
val viewModelModule = module {
    viewModelOf(::AiConsentViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::CalendarViewModel)
    viewModelOf(::ChangeRequestViewModel)
    viewModelOf(::RequestChangeViewModel)
    viewModelOf(::ChatAttachmentViewModel)
    viewModelOf(::ChatEventCardViewModel)
    viewModelOf(::ChatSearchViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::DictationViewModel)
    viewModelOf(::ReplySuggestionViewModel)
    viewModelOf(::SettleUpPaymentViewModel)
    viewModelOf(::ChildInfoViewModel)
    viewModelOf(::ConnectivityViewModel)
    viewModelOf(::FamilySwitcherViewModel)
    viewModelOf(::FamilyViewViewModel)
    viewModelOf(::ParentPaletteViewModel)
    viewModelOf(::HealthConsentViewModel)
    viewModelOf(::TelemetryConsentViewModel)
    viewModelOf(::ContactsViewModel)
    viewModelOf(::CustodySetupViewModel)
    viewModelOf(::SeasonalScheduleViewModel)
    viewModelOf(::FamilyDocumentsViewModel)
    viewModelOf(::EventOwnerViewModel)
    viewModelOf(::EventViewModel)
    viewModelOf(::ExpenseViewModel)
    viewModelOf(::SettleUpViewModel)
    viewModelOf(::ExportViewModel)
    viewModelOf(::EarlierFamiliesViewModel)
    viewModelOf(::FamilyViewModel)
    viewModelOf(::UnassignedRecordsViewModel)
    viewModelOf(::FriendCalendarViewModel)
    viewModelOf(::FriendViewModel)
    viewModelOf(::GuestAcceptViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PutOffAsksViewModel)
    viewModelOf(::JournalEditorViewModel)
    viewModelOf(::JournalListViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::CustodyConflictViewModel)
    viewModelOf(::PairingViewModel)
    viewModelOf(::ParentingPlanViewModel)
    viewModelOf(::PaymentsViewModel)
    viewModelOf(::PetsViewModel)
    viewModelOf(::ProfessionalCalendarViewModel)
    viewModelOf(::ProfessionalPlanViewModel)
    viewModelOf(::ProfessionalsViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::MonthReviewViewModel)
    viewModelOf(::ConnectSchoolViewModel)
    viewModelOf(::SchoolImportViewModel)
    viewModelOf(::CalendarFeedViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StickersViewModel)
    viewModelOf(::SupportViewModel)
    viewModelOf(::AuthStateViewModel)
    viewModelOf(::SyncViewModel)
}
