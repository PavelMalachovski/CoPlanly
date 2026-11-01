package com.coparently.app.di.koin

import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.WorkManagerSchoolImportScheduler
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * The HTTP client the school import (MON-8) talks to school servers with.
 *
 * Qualified so it can never be picked up by something else by accident: it carries no
 * interceptor, no cache and no cookie jar, and the requests it makes carry a child's school
 * credentials. The name is the Hilt qualifier's it replaced.
 */
val SCHOOL_HTTP_CLIENT = named("SchoolHttpClient")

/** A school server that has not answered in this long is treated as unreachable. */
private const val SCHOOL_TIMEOUT_SECONDS = 20L

/** The school import (MON-8): its HTTP client and the daily job's scheduler. */
val schoolModule = module {
    // The daily import runs on WorkManager.
    single<SchoolImportScheduler> { get<WorkManagerSchoolImportScheduler>() }

    // The client for school servers and Bakaláři's directory.
    single<OkHttpClient>(SCHOOL_HTTP_CLIENT) {
        OkHttpClient.Builder()
            .connectTimeout(SCHOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SCHOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(SCHOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SCHOOL_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
    }
}
