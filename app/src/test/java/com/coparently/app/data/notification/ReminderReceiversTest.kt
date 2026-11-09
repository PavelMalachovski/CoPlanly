package com.coparently.app.data.notification

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlinx.datetime.LocalDateTime
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A reminder receiver in a process with no Koin graph drops the broadcast rather than throwing
 * (9 November 2026). Installing the test APK delivered `MY_PACKAGE_REPLACED` into an
 * instrumentation process whose test application had no graph yet (`HiltTestApplication` then,
 * `KoinTestApplication` since MON-24 phase 2, which starts none), and the exception thrown out of
 * `onReceive` crashed it before the first test — CI's 16 KB leg ran nothing.
 *
 * A plain [Application], with Koin stopped, stands in for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderReceiversTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun noGraph() {
        // Another test in this JVM may have left a graph behind.
        stopKoin()
    }

    @Test
    fun `a re-arm broadcast with no graph to act on is dropped, not thrown`() {
        ReminderRearmReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `a due reminder with no graph to act on is dropped, not thrown`() {
        val due = DueReminder("e1", LocalDateTime(2026, 11, 9, 10, 0), "10:00")

        ReminderAlarmReceiver().onReceive(context, ReminderAlarmReceiver.intentFor(context, due))
    }
}
