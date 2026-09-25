package com.coparently.app.di

import com.coparently.app.data.dictation.OnDeviceSpeechDictation
import com.coparently.app.domain.dictation.SpeechDictation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing the chat composer's voice dictation — the on-device recognizer and
 * nothing that could send audio off the phone (see [OnDeviceSpeechDictation]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DictationModule {

    /** Provides the on-device speech dictation. */
    @Binds
    @Singleton
    abstract fun bindSpeechDictation(onDeviceSpeechDictation: OnDeviceSpeechDictation): SpeechDictation
}
