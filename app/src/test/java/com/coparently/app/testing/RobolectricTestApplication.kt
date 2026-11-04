package com.coparently.app.testing

import android.app.Application
import android.content.Context

/**
 * The application every Robolectric test runs (`@Config(application = RobolectricTestApplication::class)`):
 * Android's own `Application`, plus the one thing a phone's start-up does that Robolectric does not.
 *
 * Compose Multiplatform's resource reader (MON-24 phase 2, Task 9) opens its files through the
 * application context that `AndroidContextProvider`, a content provider in the resources library's
 * manifest, records when the process starts. Robolectric installs no content provider from the
 * manifest (its `AndroidTestEnvironment` registers the broadcast receivers and its own fake media
 * provider, nothing else), so without this every `stringResource(Res.string.…)` a test composes —
 * the theme's colour names, the common components' own labels — throws "Android context is not
 * initialized". A phone, an emulator and the instrumented tests install the provider as usual.
 *
 * It is the plain `Application` the tests used before in every other respect: no Koin graph, no
 * Firebase, no `CoPlanlyApplication.onCreate`.
 */
class RobolectricTestApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installComposeResourcesContext(this)
    }
}

/**
 * Hands Compose Multiplatform's Android resource reader [context], as its `AndroidContextProvider`
 * does on a device, unless something already has.
 *
 * It reaches the library's internal field by reflection, and gives up quietly if that ever moves:
 * the reader then says "Android context is not initialized", which names the cause better than a
 * `NoSuchFieldException` from a test's set-up would.
 *
 * @param context The application context
 */
fun installComposeResourcesContext(context: Context) {
    runCatching {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        val field = provider.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
        if (field.get(null) == null) field.set(null, context.applicationContext ?: context)
    }
}
