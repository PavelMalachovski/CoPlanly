package com.coparently.app.di.koin

import com.coparently.app.data.http.httpClient
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.CredentialProviderImpl
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The HTTP client Google Calendar's one call (`GoogleCalendarApi.listEvents`) is made with, on Ktor
 * since MON-24. Its own client, as google-api-client had its own transport: the school import's
 * client never carries a Google token, and this one never a school's credentials.
 */
val GOOGLE_CALENDAR_HTTP_CLIENT = named("GoogleCalendarHttpClient")

/** Google services: the credential provider, bound to its interface, and the Calendar client. */
val googleModule = module {
    single<CredentialProvider> { get<CredentialProviderImpl>() }
    single<HttpClient>(GOOGLE_CALENDAR_HTTP_CLIENT) { httpClient() }
}
