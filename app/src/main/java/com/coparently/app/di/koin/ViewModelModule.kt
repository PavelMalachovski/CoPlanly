package com.coparently.app.di.koin

import com.coparently.app.presentation.auth.AuthViewModel
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.contacts.ContactsViewModel
import com.coparently.app.presentation.documents.FamilyDocumentsViewModel
import com.coparently.app.presentation.export.ExportViewModel
import com.coparently.app.presentation.friends.FriendCalendarViewModel
import com.coparently.app.presentation.friends.FriendViewModel
import com.coparently.app.presentation.guests.GuestAcceptViewModel
import com.coparently.app.presentation.journal.JournalEditorViewModel
import com.coparently.app.presentation.journal.JournalListViewModel
import com.coparently.app.presentation.parentingplan.ParentingPlanViewModel
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.professionals.ProfessionalCalendarViewModel
import com.coparently.app.presentation.professionals.ProfessionalPlanViewModel
import com.coparently.app.presentation.professionals.ProfessionalsViewModel
import com.coparently.app.presentation.school.ConnectSchoolViewModel
import com.coparently.app.presentation.school.SchoolImportViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every ViewModel still in the app; the ones that have moved to `:shared-ui` are defined in its
 * `uiModules` (MON-24 phase 2, Task 10), and resolve the same way. `koinViewModel()` in a
 * composable and `by viewModel()` in `MainActivity` create
 * them in the caller's `ViewModelStoreOwner`, exactly as Hilt's ViewModel helpers did, and hand a
 * `SavedStateHandle` holding the destination's arguments to a constructor that asks for one
 * (`ViewModelScopeTest` holds both).
 */
val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::ChildInfoViewModel)
    viewModelOf(::ContactsViewModel)
    viewModelOf(::FamilyDocumentsViewModel)
    viewModelOf(::ExportViewModel)
    viewModelOf(::FriendCalendarViewModel)
    viewModelOf(::FriendViewModel)
    viewModelOf(::GuestAcceptViewModel)
    viewModelOf(::JournalEditorViewModel)
    viewModelOf(::JournalListViewModel)
    viewModelOf(::ParentingPlanViewModel)
    viewModelOf(::PetsViewModel)
    viewModelOf(::ProfessionalCalendarViewModel)
    viewModelOf(::ProfessionalPlanViewModel)
    viewModelOf(::ProfessionalsViewModel)
    viewModelOf(::ConnectSchoolViewModel)
    viewModelOf(::SchoolImportViewModel)
    viewModelOf(::SyncViewModel)
}
