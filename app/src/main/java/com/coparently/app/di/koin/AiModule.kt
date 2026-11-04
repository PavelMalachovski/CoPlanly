package com.coparently.app.di.koin

import com.coparently.app.BuildConfig
import com.coparently.app.data.ai.FunctionsAiAssistRepository
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import org.koin.dsl.module

/**
 * The AI assist: the `aiAssist` callable's client, and whether this build offers it at all.
 *
 * The model runs server-side (SEC-1's rule since the Gemini subsystem was deleted): nothing here
 * holds a key, and [AiAssistAvailability] comes from `BuildConfig.AI_ASSIST_ENABLED` — off in a
 * release build until billing (MON-11) exists.
 */
val aiModule = module {
    single<AiAssistRepository> { get<FunctionsAiAssistRepository>() }
    single { AiAssistAvailability(enabled = BuildConfig.AI_ASSIST_ENABLED) }
}
