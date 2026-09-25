package com.coparently.app.di

import com.coparently.app.BuildConfig
import com.coparently.app.data.ai.FunctionsAiAssistRepository
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The AI assist: the `aiAssist` callable's client, and whether this build offers it at all.
 *
 * The model runs server-side (SEC-1's rule since the Gemini subsystem was deleted): nothing here
 * holds a key, and [AiAssistAvailability] comes from `BuildConfig.AI_ASSIST_ENABLED` — off in a
 * release build until billing (MON-11) exists.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    /** The callable's client. */
    @Binds
    @Singleton
    abstract fun bindAiAssistRepository(impl: FunctionsAiAssistRepository): AiAssistRepository

    companion object {
        /** Whether this build shows any AI affordance. */
        @Provides
        @Singleton
        fun provideAiAssistAvailability(): AiAssistAvailability =
            AiAssistAvailability(enabled = BuildConfig.AI_ASSIST_ENABLED)
    }
}
