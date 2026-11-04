package com.coparently.app.di.koin

import com.coparently.app.presentation.auth.AuthViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every ViewModel still in the app: since Task 10 (MON-24 phase 2) only the two whose sign-ins
 * run on an Android activity, which move with their screens in Task 11 — `SyncViewModel` went to
 * `:shared-ui`'s `settingsUiModule` in slice 11f, so `AuthViewModel` is left. Every other one is defined
 * in `:shared-ui`'s `uiModules`, and resolves the same way. `koinViewModel()` in a
 * composable and `by viewModel()` in `MainActivity` create
 * them in the caller's `ViewModelStoreOwner`, exactly as Hilt's ViewModel helpers did, and hand a
 * `SavedStateHandle` holding the destination's arguments to a constructor that asks for one
 * (`ViewModelScopeTest` holds both).
 */
val viewModelModule = module {
    viewModelOf(::AuthViewModel)
}
