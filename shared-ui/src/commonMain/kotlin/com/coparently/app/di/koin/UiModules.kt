package com.coparently.app.di.koin

import com.coparently.app.presentation.calendar.CalendarViewModel
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
import com.coparently.app.presentation.event.EventOwnerViewModel
import com.coparently.app.presentation.event.EventViewModel
import org.koin.core.module.Module
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
 * Android and [platformUiModule] stands in for on the iPhone.
 */
val eventUiModule: Module = module {
    viewModelOf(::EventOwnerViewModel)
    viewModelOf(::EventViewModel)
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
    eventUiModule
)

/**
 * What [uiModules] takes from the platform beyond what [platformDataModule] binds. Nothing on
 * Android, where the app's own modules bind it; on the iPhone, stand-ins that answer "not on this
 * phone yet" for what it does not have, so [uiModules] resolves there as [dataModules] does.
 */
expect val platformUiModule: Module
