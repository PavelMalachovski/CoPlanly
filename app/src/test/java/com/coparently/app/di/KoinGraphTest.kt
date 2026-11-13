package com.coparently.app.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.coparently.app.data.remote.firebase.QRCodeService
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.di.koin.appModules
import com.coparently.app.di.koin.firebaseModule
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.presentation.platform.AppBuildValues
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * The check Hilt made at compile time, made here: every constructor parameter of every definition
 * in the app's Koin graph names a type the graph defines (MON-24 phase 2).
 *
 * Koin resolves at runtime, so a class whose new dependency nobody declared builds and then
 * crashes on the screen that first asks for it. `verify()` reads each definition's public
 * constructors and fails on a parameter no definition, and no type named below, provides.
 *
 * What it is told rather than shown, each for a reason:
 *  * `Context`, `SavedStateHandle` and `WorkerParameters` come from Android at resolution:
 *    `androidContext()`, the ViewModel's owner and the worker's run.
 *  * The Firebase SDK types are what `firebaseModule` defines. That module is verified apart:
 *    its definitions call `getInstance()`, and `verify()` would otherwise read the SDK classes'
 *    own constructors, which take the SDK's internals. [firebaseSdkTypes] is checked against the
 *    module, so the list cannot claim a type the module stopped defining.
 *  * [CustodyModelRepository]'s public primary constructor takes the `CoroutineScope` the tests
 *    hand in; the graph calls its secondary constructor, which supplies the scope itself.
 *  * [AiAssistAvailability] and [AppBuildValues] are built from `BuildConfig` in their
 *    definitions, not resolved.
 *  * Ktor's `HttpClient` is built by `shared-data`'s `httpClient`, which hands its public
 *    constructor the [HttpClientEngine] (OkHttp) itself; nothing resolves an engine.
 */
@OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
class KoinGraphTest {

    private val firebaseSdkTypes = listOf(
        FirebaseAuth::class,
        FirebaseFirestore::class,
        FirebaseStorage::class,
        FirebaseMessaging::class,
        FirebaseAnalytics::class,
        FirebaseCrashlytics::class,
        FirebaseFunctions::class,
        QRCodeService::class
    )

    @Test
    fun everyDefinitionResolves() {
        module { includes(appModules.filterNot { it === firebaseModule }) }.verify(
            extraTypes = listOf(Context::class, SavedStateHandle::class, WorkerParameters::class) +
                firebaseSdkTypes + HttpClientEngine::class,
            injections = injectedParameters(
                definition<CustodyModelRepository>(CoroutineScope::class),
                definition<AiAssistAvailability>(Boolean::class),
                definition<AppBuildValues>(String::class, Boolean::class)
            )
        )
    }

    @Test
    fun theFirebaseModuleDefinesExactlyTheTypesTheGraphCheckAssumes() {
        val defined = firebaseModule.mappings.values.map { it.beanDefinition.primaryType }.toSet()
        assertEquals(firebaseSdkTypes.toSet(), defined)
    }

    @Test
    fun everyAppModuleIsInTheGraphOnce() {
        assertEquals(appModules.size, appModules.toSet().size)
    }
}
