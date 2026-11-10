package com.coparently.app.presentation.settings

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.coparently.app.presentation.platform.AppLanguages
import com.coparently.app.utils.AppLocale

/**
 * The language picker's Android half (MON-24 phase 2, slice 11f): AppCompat's per-app locale, as
 * `AppLanguage.current()` and `AppLanguage.apply` read and set it before the enum moved to
 * `:shared-ui`. [context] is the screen's (the Activity, through `ProvideAndroidPlatform`).
 */
class AndroidAppLanguages(private val context: Context) : AppLanguages {

    override val available: Boolean = true

    /** The language currently selected in the picker (persisted by AppCompat). */
    override fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        val language = locales.get(0)?.language ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.tag == language } ?: AppLanguage.SYSTEM
    }

    /**
     * Applies [language] as the per-app locale. AppCompat recreates started activities
     * and (thanks to the `autoStoreLocales` manifest service) persists the choice across
     * process restarts; on Android 13+ it is also mirrored to the system per-app
     * language setting. Below Android 13 the choice is mirrored for the pushes and workers
     * at once (`AppLocale.rememberChoice`, so the mirror never lags it), and "System
     * default" over a choice starts the app again in a fresh process
     * ([SystemDefaultRelaunch]), so the owner's rule speaks there rather than Android's own
     * resolution.
     */
    override fun apply(language: AppLanguage) {
        val hadChoice = !AppCompatDelegate.getApplicationLocales().isEmpty
        val plan = SystemDefaultRelaunch.plan(Build.VERSION.SDK_INT, language, hadChoice)
        if (plan == SystemDefaultRelaunch.Plan.NOTHING) return
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        AppLocale.rememberChoice(context)
        if (plan == SystemDefaultRelaunch.Plan.SET_AND_RELAUNCH) SystemDefaultRelaunch.relaunch(context)
    }
}
