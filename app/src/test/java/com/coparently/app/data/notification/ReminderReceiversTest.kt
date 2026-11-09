package com.coparently.app.data.notification

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * A reminder receiver in a process whose application has no Hilt graph drops the broadcast rather
 * than throwing (9 November 2026). Installing the test APK delivered `MY_PACKAGE_REPLACED` into an
 * instrumentation process whose `HiltTestApplication` had no component yet, and the exception thrown
 * out of `onReceive` crashed it before the first test — CI's 16 KB leg ran nothing.
 *
 * A plain [Application] stands in for it: `EntryPointAccessors` refuses both with the same
 * `IllegalStateException`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderReceiversTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a re-arm broadcast with no graph to act on is dropped, not thrown`() {
        ReminderRearmReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `a due reminder with no graph to act on is dropped, not thrown`() {
        val due = DueReminder("e1", LocalDateTime.of(2026, 11, 9, 10, 0), "10:00")

        ReminderAlarmReceiver().onReceive(context, ReminderAlarmReceiver.intentFor(context, due))
    }
}
