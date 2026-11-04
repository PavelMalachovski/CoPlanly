package com.coparently.app.di.koin

import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.domain.repository.RecordPhotoStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [uiModules] takes from the platform on the iPhone beyond [platformDataModule]: so far the
 * chat's on-device dictation and the AI assist, neither of which the iPhone has yet, and the
 * storage of a record's photos (the event form's, slice 10b), as the stand-ins in
 * `IosUiUnavailable.kt` — no microphone, no assist offered, and no photo stored. Nothing starts Koin
 * there yet; the app that does, in phase 3, includes this beside [platformDataModule] and replaces
 * each stand-in as it gains the feature.
 */
actual val platformUiModule: Module = module {
    single<SpeechDictation> { NoSpeechDictation }
    single { AiAssistAvailability(enabled = false) }
    single<AiAssistRepository> { NoAiAssist }
    single<RecordPhotoStorage> { NoRecordPhotoStorage }
}
