package com.coparently.app.di.koin

import com.coparently.app.data.dictation.OnDeviceSpeechDictation
import com.coparently.app.domain.dictation.SpeechDictation
import org.koin.dsl.module

/**
 * The chat composer's voice dictation — the on-device recognizer and nothing that could send
 * audio off the phone (see [OnDeviceSpeechDictation]).
 */
val dictationModule = module {
    single<SpeechDictation> { get<OnDeviceSpeechDictation>() }
}
