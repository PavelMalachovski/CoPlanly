package com.coparently.app.di.koin

import com.coparently.app.data.http.httpClient
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.WorkManagerSchoolImportScheduler
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The HTTP client the school import (MON-8) talks to school servers and Bakaláři's directory with,
 * on Ktor since MON-24 (`shared-data`'s `httpClient()`: OkHttp on Android, 20-second timeouts).
 *
 * Qualified so it can never be picked up by something else by accident: it carries no logging,
 * no cache and no cookies, and the requests it makes carry a child's school credentials. The name
 * is the Hilt qualifier's it replaced.
 */
val SCHOOL_HTTP_CLIENT = named("SchoolHttpClient")

/** The school import (MON-8): its HTTP client and the daily job's scheduler. */
val schoolModule = module {
    // The daily import runs on WorkManager.
    single<SchoolImportScheduler> { get<WorkManagerSchoolImportScheduler>() }

    // The client for school servers and Bakaláři's directory.
    single<HttpClient>(SCHOOL_HTTP_CLIENT) { httpClient() }
}
