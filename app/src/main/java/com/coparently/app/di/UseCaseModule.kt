package com.coparently.app.di

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Dagger Hilt module providing use case dependencies.
 */
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideEventValidator(): EventValidator = EventValidator()

    @Provides
    @ViewModelScoped
    fun provideCreateEventUseCase(
        eventRepository: EventRepository,
        eventValidator: EventValidator,
        analyticsManager: AnalyticsManager,
        crashlyticsManager: CrashlyticsManager
    ): CreateEventUseCase = CreateEventUseCase(
        eventRepository, eventValidator, analyticsManager, crashlyticsManager
    )

    @Provides
    @ViewModelScoped
    fun provideUpdateEventUseCase(
        eventRepository: EventRepository,
        eventValidator: EventValidator,
        analyticsManager: AnalyticsManager,
        crashlyticsManager: CrashlyticsManager
    ): UpdateEventUseCase = UpdateEventUseCase(
        eventRepository, eventValidator, analyticsManager, crashlyticsManager
    )

    @Provides
    @ViewModelScoped
    fun provideDeleteEventUseCase(
        eventRepository: EventRepository,
        analyticsManager: AnalyticsManager,
        crashlyticsManager: CrashlyticsManager
    ): DeleteEventUseCase = DeleteEventUseCase(
        eventRepository, analyticsManager, crashlyticsManager
    )

    @Provides
    @ViewModelScoped
    fun provideGetEventsUseCase(
        eventRepository: EventRepository
    ): GetEventsUseCase = GetEventsUseCase(eventRepository)

    /**
     * Provides grouped event use cases for simplified injection.
     */
    @Provides
    @ViewModelScoped
    fun provideEventUseCases(
        createEvent: CreateEventUseCase,
        updateEvent: UpdateEventUseCase,
        deleteEvent: DeleteEventUseCase,
        getEvents: GetEventsUseCase
    ): EventUseCases = EventUseCases(
        createEvent, updateEvent, deleteEvent, getEvents
    )
}
