package com.coparently.app.di.koin

import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.pairing.InviteQrCodes
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone beyond [platformDataModule]: so far the
 * chat's on-device dictation and the AI assist, an invitation's QR code (slice 10e) and a sign-in
 * failure's wording (slice 11f), none of which the iPhone has yet, as the stand-ins in
 * `IosUiUnavailable.kt` — no microphone, no assist offered, no QR drawn and every failure unknown.
 * (A record photo's storage is a data seam, so its stand-in is [platformDataModule]'s.) Nothing starts Koin
 * there yet; the app that does, in phase 3, includes this beside [platformDataModule] and replaces
 * each stand-in as it gains the feature.
 */
actual val platformUiModule: Module = module {
    single<SpeechDictation> { NoSpeechDictation }
    single { AiAssistAvailability(enabled = false) }
    single<AiAssistRepository> { NoAiAssist }
    single<InviteQrCodes> { NoInviteQrCodes }
    single<AuthErrors> { NoAuthErrors }
}
