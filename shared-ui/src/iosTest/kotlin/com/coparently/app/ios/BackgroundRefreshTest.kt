package com.coparently.app.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The background refresh's chain (phase 4 part D, spec §7.4), called directly as the simulator
 * cannot start a `BGAppRefreshTask` without a debugger: a locked phone or a signed-out account ends
 * it at once with success and touches nothing, a failed sync still writes the widget, and a
 * cancelled run reports no success, once.
 */
class BackgroundRefreshTest {

    private val calls = mutableListOf<String>()

    private fun refresh(
        unlocked: Boolean = true,
        signedIn: Boolean = true,
        sync: suspend () -> Unit = { calls += "sync" },
    ) = BackgroundRefresh(
        protectedDataAvailable = { unlocked },
        signedIn = { signedIn },
        sync = sync,
        writeWidget = { calls += "widget" },
    )

    @Test
    fun lockedPhoneDoesNothingAndSucceeds() = runTest {
        assertTrue(refresh(unlocked = false).run())
        assertEquals(emptyList(), calls)
    }

    @Test
    fun signedOutDoesNothing() = runTest {
        assertTrue(refresh(signedIn = false).run())
        assertEquals(emptyList(), calls)
    }

    @Test
    fun aFullRunSyncsThenWritesTheWidget() = runTest {
        assertTrue(refresh().run())
        assertEquals(listOf("sync", "widget"), calls)
    }

    @Test
    fun aFailedSyncStillWritesTheWidget() = runTest {
        val ran = refresh(sync = { error("offline") }).run()

        assertTrue(ran)
        assertEquals(listOf("widget"), calls)
    }

    @Test
    fun cancelledRunReportsNoSuccess() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        var thrown: Throwable? = null
        val chain = refresh(sync = {
            calls += "sync"
            awaitCancellation()
        })
        val reports = mutableListOf<Boolean>()
        val run = startBackgroundRun(
            scope,
            run = {
                try {
                    chain.run()
                } catch (e: CancellationException) {
                    thrown = e
                    throw e
                }
            },
            finished = { reports += it },
        )
        runCurrent()
        run.cancel()
        runCurrent()

        assertTrue(thrown is CancellationException, "the run ended by a cancellation")
        assertEquals(listOf("sync"), calls, "the widget is not written after a cancellation")
        assertEquals(listOf(false), reports)
        scope.cancel()
    }

    @Test
    fun aRunCancelledBeforeItStartsStillReportsOnce() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val reports = mutableListOf<Boolean>()
        val run = startBackgroundRun(scope, run = { refresh().run() }, finished = { reports += it })

        run.cancel()
        runCurrent()

        assertEquals(listOf(false), reports)
        assertEquals(emptyList(), calls)
        scope.cancel()
    }

    @Test
    fun aFinishedRunReportsSuccessOnce() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val reports = mutableListOf<Boolean>()
        startBackgroundRun(scope, run = { refresh().run() }, finished = { reports += it })

        runCurrent()

        assertEquals(listOf(true), reports)
        scope.cancel()
    }
}
