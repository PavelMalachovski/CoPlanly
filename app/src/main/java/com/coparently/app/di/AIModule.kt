package com.coparently.app.di

import android.content.Context
import com.coparently.app.BuildConfig
import com.coparently.app.data.remote.ai.AIService
import com.coparently.app.data.remote.ai.GeminiAIService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Dagger Hilt module for AI-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApiKey(@ApplicationContext context: Context): String {
        // In production, this should come from BuildConfig or secure storage
        // For now, using a placeholder - replace with actual API key
        return BuildConfig.GEMINI_API_KEY.ifEmpty {
            // Fallback to resources or environment variable
            "YOUR_GEMINI_API_KEY_HERE"
        }
    }

    @Provides
    @Singleton
    fun provideAIService(
        apiKey: String,
        gson: Gson
    ): AIService {
        return GeminiAIService(apiKey, gson)
    }
}
