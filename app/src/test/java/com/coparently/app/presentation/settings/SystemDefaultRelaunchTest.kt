package com.coparently.app.presentation.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import com.coparently.app.presentation.MainActivity
import com.coparently.app.presentation.settings.SystemDefaultRelaunch.Plan
import com.coparently.app.presentation.settings.SystemDefaultRelaunch.plan
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "System default" below Android 13 (review I2 of slice 9d): AppCompat would keep an empty request
 * for the rest of the process and hand every activity Android's own resolution, so the app starts
 * again in a fresh process — only then, never on Android 13 and later, and never when nothing
 * changed.
 *
 * On SDK 34, the one Robolectric SDK CI fetches: the plan takes the API level as an argument, and
 * the relaunch is the same calls on every level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class SystemDefaultRelaunchTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `below Android 13 system default over a choice relaunches`() {
        assertEquals(Plan.SET_AND_RELAUNCH, plan(Build.VERSION_CODES.S_V2, AppLanguage.SYSTEM, hadChoice = true))
        assertEquals(Plan.SET_AND_RELAUNCH, plan(Build.VERSION_CODES.O, AppLanguage.SYSTEM, hadChoice = true))
    }

    @Test
    fun `below Android 13 system default over no choice changes nothing`() {
        assertEquals(Plan.NOTHING, plan(Build.VERSION_CODES.S_V2, AppLanguage.SYSTEM, hadChoice = false))
    }

    @Test
    fun `a language is set and never relaunches`() {
        for (sdk in listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.S_V2, Build.VERSION_CODES.TIRAMISU)) {
            for (hadChoice in listOf(true, false)) {
                assertEquals(Plan.SET, plan(sdk, AppLanguage.GERMAN, hadChoice))
            }
        }
    }

    @Test
    fun `Android 13 and later set system default as before`() {
        for (sdk in listOf(Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            assertEquals(Plan.SET, plan(sdk, AppLanguage.SYSTEM, hadChoice = true))
            assertEquals(Plan.SET, plan(sdk, AppLanguage.SYSTEM, hadChoice = false))
        }
    }

    @Test
    fun `the relaunch opens the app on a fresh task and then ends the process`() {
        var ended = false
        SystemDefaultRelaunch.relaunch(context) { ended = true }

        assertTrue(ended)
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNotNull(started)
        assertEquals(MainActivity::class.java.name, started.component?.className)
        val freshTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(freshTask, started.flags and freshTask)
    }

    /**
     * The next process reads AppCompat's storage before any activity starts, so the relaunch must
     * leave it saying "no choice" — whatever AppCompat's background write has done by then. Read
     * and written through the library's own helper (core's, and AppCompat's older copy where the
     * build still carries one), so a renamed file turns this red.
     */
    @Test
    fun `the relaunch leaves AppCompat's storage with no choice`() {
        val helpers = STORAGE_HELPERS.mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
        assertTrue("no AppCompat locale storage helper on the classpath", helpers.isNotEmpty())
        for (helper in helpers) {
            call(helper, "persistLocales", context, "de")
            assertEquals(helper.name, "de", call(helper, "readLocales", context))

            SystemDefaultRelaunch.relaunch(context) {}

            assertEquals(helper.name, "", call(helper, "readLocales", context))
        }
    }

    private fun call(helper: Class<*>, name: String, vararg args: Any): Any? {
        val types = args.map { if (it is Context) Context::class.java else it.javaClass }.toTypedArray()
        val method = helper.getDeclaredMethod(name, *types).apply { isAccessible = true }
        return method.invoke(null, *args)
    }

    private companion object {
        val STORAGE_HELPERS = listOf(
            "androidx.core.app.AppLocalesStorageHelper",
            "androidx.appcompat.app.AppLocalesStorageHelper"
        )
    }
}
