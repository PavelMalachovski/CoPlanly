package com.coparently.app.presentation.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.coparently.app.presentation.MainActivity

/**
 * What "System default" in Settings → Language needs below Android 13, beyond telling AppCompat.
 *
 * There AppCompat keeps the per-app choice in a static field for the life of the process, and
 * `setApplicationLocales` with the empty list leaves it holding an *empty* request rather than
 * none. From then on AppCompat hands every activity the application's own configuration — the
 * language Android resolved against everything the APK translates — on top of the locale the
 * owner's rule chose (`AppLocale`), while `AppLocale.follow` keeps the process's defaults on the
 * rule's: two languages, and two layout directions, on one screen until the process ends. No
 * AppCompat API takes the request back to none, so the app starts again in a fresh process, whose
 * AppCompat holds no request and leaves the rule's locale standing.
 *
 * Android 13 and later keep the choice in the system (`LocaleManager`) and need none of this.
 */
internal object SystemDefaultRelaunch {

    /**
     * The file AppCompat's `autoStoreLocales` keeps the choice in below Android 13
     * (`androidx.core.app.AppLocalesStorageHelper`), which it deletes to store "no choice".
     * AppCompat writes it on a background thread, so [relaunch] deletes it itself before the
     * process ends, and the next process cannot restore the choice just undone.
     * `SystemDefaultRelaunchTest` holds the name to the library's.
     */
    internal const val APPCOMPAT_CHOICE_FILE =
        "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"

    /** What choosing a language does, beyond nothing. */
    enum class Plan {
        /** Nothing changes: "System default" again, below Android 13, where no choice stands. */
        NOTHING,

        /** Hand the choice to AppCompat, which recreates the activities in it. */
        SET,

        /** Hand it to AppCompat, then start the app again in a fresh process (see the object). */
        SET_AND_RELAUNCH
    }

    /**
     * The plan for choosing [chosen] on [sdkInt] when a per-app choice [hadChoice] stood before.
     * Android 13 and later always [Plan.SET], as they did. Below that, "System default" over a
     * choice relaunches, and over no choice does nothing at all — AppCompat would otherwise take
     * the empty list as a request and leave it in place. A relaunched process finds no choice, so
     * nothing it does relaunches again.
     */
    fun plan(sdkInt: Int, chosen: AppLanguage, hadChoice: Boolean): Plan = when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU || chosen != AppLanguage.SYSTEM -> Plan.SET
        hadChoice -> Plan.SET_AND_RELAUNCH
        else -> Plan.NOTHING
    }

    /**
     * Stores "no choice" where AppCompat reads it at the next cold start, opens `MainActivity` on a
     * fresh task, and ends this process ([endProcess]; a test passes its own). The start is handed
     * to the system before the process ends, which then launches it in a new one.
     */
    fun relaunch(context: Context, endProcess: () -> Unit = ::endThisProcess) {
        context.applicationContext.deleteFile(APPCOMPAT_CHOICE_FILE)
        val restart = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
        context.startActivity(restart)
        endProcess()
    }

    private fun endThisProcess() {
        Runtime.getRuntime().exit(0)
    }
}
