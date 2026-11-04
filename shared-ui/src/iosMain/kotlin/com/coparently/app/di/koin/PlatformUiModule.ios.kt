package com.coparently.app.di.koin

import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.dictation.SpeechDictation
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone beyond [platformDataModule]: so far the
 * chat's on-device dictation and the AI assist, neither of which the iPhone has yet, as the
 * stand-ins in `IosUiUnavailable.kt` — no microphone and no assist offered. (A record photo's
 * storage is a data seam, so its stand-in is [platformDataModule]'s.) Nothing starts Koin
 * there yet; the app that does, in phase 3, includes this beside [platformDataModule] and replaces
 * each stand-in as it gains the feature.
 */
actual val platformUiModule: Module = module {
    single<SpeechDictation> { NoSpeechDictation }
    single { AiAssistAvailability(enabled = false) }
    single<AiAssistRepository> { NoAiAssist }
}
