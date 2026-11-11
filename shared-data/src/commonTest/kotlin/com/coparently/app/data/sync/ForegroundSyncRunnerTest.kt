package com.coparently.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iPhone's sync requester (MON-24 phase 3, Task 6), on virtual time: one run at a time, a
 * request that lands during a run appends exactly one more run — the rule `SyncWorker.syncNow`
 * keeps on Android (CLAUDE.md item 22) — the Google import runs after each sync and its failure
 * never stops the next run, and a cancelled scope ends it cleanly.
 */
class ForegroundSyncRunnerTest {

    /** A sync that takes [SYNC_MILLIS] and counts its runs and how many ran at once. */
    private class SlowSync {
        var runs = 0
        var running = 0
        var mostAtOnce = 0

        suspend fun run(): Result<Unit> {
            runs++
            running++
            mostAtOnce = maxOf(mostAtOnce, running)
            delay(SYNC_MILLIS)
            running--
            return Result.success(Unit)
        }
    }

    @Test
    fun `two requests during one run produce exactly one more run`() = runTest {
        val sync = SlowSync()
        val runner = ForegroundSyncRunner(sync = sync::run, afterSync = {}, scope = backgroundScope)

        runner.requestSyncNow()
        advanceTimeBy(SYNC_MILLIS / 2)
        runner.requestSyncNow()
        runner.requestSyncNow()
        advanceUntilIdle()

        assertEquals(2, sync.runs)
        assertEquals(1, sync.mostAtOnce)
    }

    @Test
    fun `three requests while idle run once with at most one follow-up and never overlap`() = runTest {
        val sync = SlowSync()
        val runner = ForegroundSyncRunner(sync = sync::run, afterSync = {}, scope = backgroundScope)

        runner.requestSyncNow()
        runner.requestSyncNow()
        runner.requestSyncNow()
        advanceUntilIdle()

        assertEquals(1, sync.mostAtOnce)
        assertTrue(sync.runs in 1..2, "ran ${sync.runs} times")
    }

    @Test
    fun `a request after a run has ended starts a new run`() = runTest {
        val sync = SlowSync()
        val runner = ForegroundSyncRunner(sync = sync::run, afterSync = {}, scope = backgroundScope)

        runner.requestSyncNow()
        advanceUntilIdle()
        runner.requestSyncNow()
        advanceUntilIdle()

        assertEquals(2, sync.runs)
    }

    @Test
    fun `the import runs after each sync and its failure does not stop the next run`() = runTest {
        val sync = SlowSync()
        val order = mutableListOf<String>()
        val runner = ForegroundSyncRunner(
            sync = { sync.run().also { order += "sync" } },
            afterSync = {
                order += "import"
                throw IllegalStateException("Google said no")
            },
            scope = backgroundScope
        )

        runner.requestSyncNow()
        advanceUntilIdle()
        runner.requestSyncNow()
        advanceUntilIdle()

        assertEquals(listOf("sync", "import", "sync", "import"), order)
    }

    @Test
    fun `a failed or throwing sync still lets the next request run`() = runTest {
        var runs = 0
        val runner = ForegroundSyncRunner(
            sync = {
                runs++
                if (runs == 1) throw IllegalStateException("offline")
                Result.failure(IllegalStateException("denied"))
            },
            afterSync = {},
            scope = backgroundScope
        )

        runner.requestSyncNow()
        advanceUntilIdle()
        runner.requestSyncNow()
        advanceUntilIdle()
        runner.requestSyncNow()
        advanceUntilIdle()

        assertEquals(3, runs)
    }

    @Test
    fun `a cancelled scope stops the runner and later requests do nothing`() {
        val test = TestScope()
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job())
        val sync = SlowSync()
        val runner = ForegroundSyncRunner(sync = sync::run, afterSync = {}, scope = scope)

        runner.requestSyncNow()
        test.advanceTimeBy(SYNC_MILLIS / 2)
        scope.cancel()
        runner.requestSyncNow()
        test.advanceUntilIdle()
        test.runCurrent()

        assertEquals(1, sync.runs)
        assertEquals(1, sync.running, "the cancelled run never reached its end")
    }

    private companion object {
        const val SYNC_MILLIS = 1_000L
    }
}
