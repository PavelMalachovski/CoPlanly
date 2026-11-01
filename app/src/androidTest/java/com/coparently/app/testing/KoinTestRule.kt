package com.coparently.app.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.di.instrumentedTestOverrides
import com.coparently.app.di.koin.appModules
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

/**
 * Starts the app's Koin graph for one instrumented test and stops it after the test, which is
 * what `HiltAndroidRule` did before MON-24 phase 2.
 *
 * The test application (`KoinTestApplication`) starts nothing, so `CoPlanlyApplication.onCreate`
 * — the chat mirror, the telemetry applier, WorkManager's sync — never runs in a test. This rule
 * loads [appModules] and then [instrumentedTestOverrides], whose definitions replace the app's of
 * the same type: the Firebase SDKs (`fakeFirebaseModule`: relaxed mocks, or the emulators' real
 * SDKs in the `e2e` job), the install referrer, and the test-only classes. A test that uses this
 * rule therefore carries [UsesFakeFirebase].
 *
 * Make it the outermost rule (`@get:Rule(order = 0)`), so the graph exists before the compose
 * rule launches `MainActivity` and is stopped only after the activity is gone. Each test gets a new
 * graph — new singletons, a new Room instance on the same file — as each got a new Hilt component.
 *
 * @param graph What to start; the app's modules with the instrumented overrides by default.
 */
class KoinTestRule(
    private val graph: List<Module> = appModules + instrumentedTestOverrides
) : TestWatcher() {

    override fun starting(description: Description) {
        // A graph a crashed test left behind would make `startKoin` throw.
        stopKoin()
        startKoin {
            androidContext(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
            modules(graph)
        }
    }

    override fun finished(description: Description) {
        stopKoin()
    }

    /** Resolves [T] from the running graph, as a field `@Inject` did; call it from the test body. */
    inline fun <reified T : Any> get(): T = GlobalContext.get().get()
}
