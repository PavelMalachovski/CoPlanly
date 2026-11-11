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
import com.coparently.app.presentation.pairing.InviteQrCodes
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone beyond [platformDataModule]: the chat's
 * on-device dictation and the AI assist and an invitation's QR code (slice 10e), none of which the
 * iPhone has yet, as the stand-ins in `IosUiUnavailable.kt` — no microphone, no assist offered and
 * no QR drawn — and, since phase 3's Task 5, which reason a failed sign-in is ([IosAuthErrors]).
 * Since Task 6 it also binds the two wordings the data layer writes into stored data
 * ([IosPairingWording], [IosSchoolImportWordings]): data seams, but they read the app's strings,
 * which live in this module. (A record photo's storage is a data seam, so its stand-in is
 * [platformDataModule]'s.) The iPhone app's `IosApp.start` includes this beside
 * [platformDataModule]; each stand-in is replaced as the app gains the feature.
 */
actual val platformUiModule: Module = module {
    single<SpeechDictation> { NoSpeechDictation }
    single { AiAssistAvailability(enabled = false) }
    single<AiAssistRepository> { NoAiAssist }
    single<InviteQrCodes> { NoInviteQrCodes }
    single<AuthErrors> { IosAuthErrors }
    single<PairingWording> { IosPairingWording() }
    single<SchoolImportWordings> { IosSchoolImportWordings(get()) }
}
