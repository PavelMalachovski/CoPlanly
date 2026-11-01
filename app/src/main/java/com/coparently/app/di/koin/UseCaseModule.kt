package com.coparently.app.di.koin

import com.coparently.app.domain.usecase.CreateEventUseCase
import com.coparently.app.domain.usecase.DeleteEventUseCase
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.EventValidator
import com.coparently.app.domain.usecase.GetEventsUseCase
import com.coparently.app.domain.usecase.UpdateEventUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * The event use cases. Each is a `factory`: under Hilt they were `@ViewModelScoped`, one instance
 * per ViewModel, and every one of them is stateless, so a new instance per resolution behaves the
 * same and needs no ViewModel scope (the school import's writer resolves them outside any
 * ViewModel, as it did through their constructors before).
 */
val useCaseModule = module {
    factoryOf(::EventValidator)
    factoryOf(::CreateEventUseCase)
    factoryOf(::UpdateEventUseCase)
    factoryOf(::DeleteEventUseCase)
    factoryOf(::GetEventsUseCase)
    factoryOf(::EventUseCases)
}
