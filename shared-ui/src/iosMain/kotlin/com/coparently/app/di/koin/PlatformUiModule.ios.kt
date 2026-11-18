package com.coparently.app.di.koin

import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.school.SchoolImportWordings
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.ios.IosPairingWording
import com.coparently.app.ios.IosSchoolImportWordings
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.auth.IosAuthErrors
import com.coparently.app.presentation.chat.AppleSpeechRecognizerSource
import com.coparently.app.presentation.chat.IosSpeechDictation
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone beyond [platformDataModule]: the chat's
 * on-device dictation ([IosSpeechDictation] over Apple's Speech framework, since phase 4 part E1), the
 * AI assist (slice 10e), which the iPhone does not have yet, as the stand-in [NoAiAssist] — no
 * assist offered — and, since phase 3's Task 5, which reason a failed sign-in is ([IosAuthErrors]).
 * The `AppBuildValues` and the invitation's QR are bound by the iPhone app's own module
 * (`iosAppModule`), from the one set of build values the shell shares.
 * Since Task 6 it also binds the two wordings the data layer writes into stored data
 * ([IosPairingWording], [IosSchoolImportWordings]): data seams, but they read the app's strings,
 * which live in this module. (A record photo's storage is a data seam, so its stand-in is
 * [platformDataModule]'s.) The iPhone app's `IosApp.start` includes this beside
 * [platformDataModule]; each stand-in is replaced as the app gains the feature.
 */
actual val platformUiModule: Module = module {
    single<SpeechDictation> { IosSpeechDictation(AppleSpeechRecognizerSource) }
    single { AiAssistAvailability(enabled = false) }
    single<AiAssistRepository> { NoAiAssist }
    single<AuthErrors> { IosAuthErrors }
    single<PairingWording> { IosPairingWording() }
    single<SchoolImportWordings> { IosSchoolImportWordings(get()) }
}
